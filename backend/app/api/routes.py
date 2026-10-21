import base64
import json
from datetime import datetime, timedelta, timezone
from uuid import UUID
from fastapi import APIRouter, Body, Depends, Header, HTTPException, Query
from pydantic import Field
from sqlalchemy import and_, func, or_, select
from app.config import settings
from app.persistence.models import Change, Device, History, Job, Note, Record, Rule, TypeDefinition, now
from app.schemas import CorrectionDTO, DTO, NoteDTO, RecordDTO, RecordMutation, TypeDTO
from app.security.identity import authenticate, digest, enroll
from app.services import changed, history_wire, job_wire, lock_user, mutate, note_wire, owned, protect_correction, queue_job, save_record


class Enrollment(DTO):
    code: str = Field(min_length=20, max_length=200)
    deviceId: UUID


class VersionAction(DTO):
    expectedVersion: int = Field(ge=1)
    completedAt: str | None = None


class NoteEdit(DTO):
    expectedVersion: int = Field(ge=1)
    content: str = Field(min_length=1, max_length=20_000)


class RuleDTO(DTO):
    id: UUID
    description: str = Field(max_length=1000)
    conditions: dict[str, str]
    action: dict[str, str]
    enabled: bool = True
    explicitlyApproved: bool = False


def router_for(factory):
    router = APIRouter(prefix="/api/v1")

    def db_dependency():
        with factory() as db:
            yield db

    def user(db=Depends(db_dependency), authorization: str | None = Header(default=None)):
        return authenticate(db, authorization)

    def key(idempotency_key: str = Header(alias="Idempotency-Key")):
        return idempotency_key

    @router.post("/auth/enroll")
    def enroll_device(body: Enrollment, db=Depends(db_dependency)):
        return enroll(db, body.code, str(body.deviceId))

    @router.post("/auth/revoke")
    def revoke_device(user_id=Depends(user), authorization: str = Header(), db=Depends(db_dependency)):
        db.get(Device, digest(authorization[7:])).revoked = True
        db.commit()
        return {"revoked": True}

    @router.get("/config")
    def config(user_id=Depends(user)):
        return {"aiConfigured": settings.ai_available, "model": settings.llm_model if settings.ai_available else None}

    @router.post("/notes")
    def ingest(body: NoteDTO, operation_key=Depends(key), user_id=Depends(user), db=Depends(db_dependency)):
        def action():
            existing = db.get(Note, str(body.id))
            if existing:
                if existing.user_id != user_id or existing.content != body.content:
                    raise HTTPException(409, "Note ID unavailable or content differs")
                return note_wire(existing)
            since = (datetime.now(timezone.utc) - timedelta(minutes=1)).isoformat(timespec="milliseconds").replace("+00:00", "Z")
            if db.scalar(select(func.count()).select_from(Note).where(Note.user_id == user_id, Note.received_at >= since)) >= 60:
                raise HTTPException(429, "Capture rate limit; retry later", headers={"Retry-After": "60"})
            if body.records and not body.manual:
                raise HTTPException(422, "Initial records require explicit manual capture")
            note = Note(id=str(body.id), user_id=user_id, content=body.content, original_content=body.originalContent or body.content, created_at=body.createdAt, updated_at=body.createdAt, source=body.source, timezone=body.timezone, locale=body.locale, version=body.version, manual=body.manual, processing_status="organized" if body.manual else "pending", user_corrected=body.manual)
            db.add(note); db.flush()
            # Parent first in the incremental feed, including when a page ends mid-capture.
            changed(db, user_id, "note", note.id, note_wire(note))
            for record in body.records:
                if str(record.sourceNoteId) != note.id:
                    raise HTTPException(422, "Source note mismatch")
                save_record(db, user_id, record.model_copy(update={"isUserConfirmed": True}))
            if not body.manual:
                queue_job(db, note)
            return note_wire(note)
        return mutate(db, user_id, operation_key, "POST notes", body.model_dump(mode="json", exclude_unset=True), action)

    @router.get("/notes")
    def list_notes(cursor: str = Query(default="", max_length=200), limit: int = Query(default=50, ge=1, le=100), user_id=Depends(user), db=Depends(db_dependency)):
        query = select(Note).where(Note.user_id == user_id)
        if cursor:
            try:
                timestamp, entity_id = json.loads(base64.urlsafe_b64decode(cursor))
                query = query.where(or_(Note.created_at < timestamp, and_(Note.created_at == timestamp, Note.id < entity_id)))
            except (ValueError, TypeError):
                raise HTTPException(422, "Invalid cursor")
        rows = db.scalars(query.order_by(Note.created_at.desc(), Note.id.desc()).limit(limit + 1)).all()
        page = rows[:limit]
        next_cursor = base64.urlsafe_b64encode(json.dumps([page[-1].created_at, page[-1].id]).encode()).decode() if len(rows) > limit else None
        return {"notes": [note_wire(n) for n in page], "nextCursor": next_cursor}

    @router.get("/notes/{note_id}")
    def get_note(note_id: UUID, user_id=Depends(user), db=Depends(db_dependency)):
        note = owned(db, Note, str(note_id), user_id)
        records = db.scalars(select(Record).where(Record.source_note_id == note.id, Record.user_id == user_id)).all()
        return note_wire(note) | {"records": [r.data for r in records]}

    @router.patch("/notes/{note_id}")
    def edit_note(note_id: UUID, body: NoteEdit, operation_key=Depends(key), user_id=Depends(user), db=Depends(db_dependency)):
        def action():
            note = owned(db, Note, str(note_id), user_id, True)
            if note.version != body.expectedVersion:
                raise HTTPException(409, {"code": "version_conflict", "currentVersion": note.version})
            before = note_wire(note)
            note.content = body.content; note.version += 1; note.updated_at = now()
            protect_correction(db, user_id, note, before, note_wire(note))
            queue_job(db, note)
            return note_wire(note)
        return mutate(db, user_id, operation_key, f"PATCH notes/{note_id}", body.model_dump(mode="json", exclude_unset=True), action)

    @router.post("/notes/{note_id}/reprocess")
    def reprocess(note_id: UUID, body: dict = Body(default={}), operation_key=Depends(key), user_id=Depends(user), db=Depends(db_dependency)):
        def action():
            note = owned(db, Note, str(note_id), user_id, True)
            return job_wire(queue_job(db, note))
        return mutate(db, user_id, operation_key, f"POST notes/{note_id}/reprocess", body, action)

    @router.get("/notes/{note_id}/interpretation")
    def interpretation(note_id: UUID, user_id=Depends(user), db=Depends(db_dependency)):
        note = owned(db, Note, str(note_id), user_id)
        history = db.scalar(select(History).where(History.note_id == note.id).order_by(History.created_at.desc()).limit(1))
        return {"processingStatus": note.processing_status, "interpretation": history_wire(history) if history else None}

    @router.patch("/records/{record_id}")
    def update_record(record_id: UUID, body: RecordMutation, operation_key=Depends(key), user_id=Depends(user), db=Depends(db_dependency)):
        def action():
            record = owned(db, Record, str(record_id), user_id, True)
            note = owned(db, Note, record.source_note_id, user_id, True)
            if record.version != body.expectedVersion:
                raise HTTPException(409, {"code": "version_conflict", "currentVersion": record.version})
            if str(body.id) != record.id or str(body.sourceNoteId) != note.id:
                raise HTTPException(422, "Immutable record/source ID changed")
            before = record.data
            data = RecordDTO.model_validate(body.model_dump(exclude={"expectedVersion"})).model_copy(update={"isUserConfirmed": True})
            updated = save_record(db, user_id, data, record.version + 1)
            protect_correction(db, user_id, note, before, updated.data)
            return updated.data
        return mutate(db, user_id, operation_key, f"PATCH records/{record_id}", body.model_dump(mode="json", exclude_unset=True), action)

    def task_action(record_id, body, operation_key, user_id, db, undo):
        def action():
            record = owned(db, Record, str(record_id), user_id, True)
            note = owned(db, Note, record.source_note_id, user_id, True)
            if record.version != body.expectedVersion:
                raise HTTPException(409, {"code": "version_conflict", "currentVersion": record.version})
            if record.base_kind != "task":
                raise HTTPException(422, "Only tasks can be completed")
            value = record.data | {"state": "pending" if undo else "completed", "completedAt": None if undo else body.completedAt or now(), "isUserConfirmed": True}
            before = record.data
            updated = save_record(db, user_id, RecordDTO.model_validate(value), record.version + 1)
            protect_correction(db, user_id, note, before, updated.data)
            return updated.data
        return mutate(db, user_id, operation_key, f"task/{record_id}/{'undo' if undo else 'complete'}", body.model_dump(), action)

    @router.post("/records/{record_id}/complete")
    def complete(record_id: UUID, body: VersionAction, operation_key=Depends(key), user_id=Depends(user), db=Depends(db_dependency)):
        return task_action(record_id, body, operation_key, user_id, db, False)

    @router.post("/records/{record_id}/undo")
    def undo(record_id: UUID, body: VersionAction, operation_key=Depends(key), user_id=Depends(user), db=Depends(db_dependency)):
        return task_action(record_id, body, operation_key, user_id, db, True)

    def correct_note(note_id, body, operation_key, user_id, db):
        def action():
            note = owned(db, Note, str(note_id), user_id, True)
            if str(body.noteId) != note.id or note.version != body.expectedNoteVersion:
                raise HTTPException(409, {"code": "version_conflict", "currentVersion": note.version})
            previous = [r.data for r in db.scalars(select(Record).where(Record.source_note_id == note.id)).all()]
            saved = []
            for dto in body.records:
                if str(dto.sourceNoteId) != note.id:
                    raise HTTPException(422, "Source mismatch")
                existing = db.get(Record, str(dto.id))
                saved.append(save_record(db, user_id, dto.model_copy(update={"isUserConfirmed": True}), existing.version + 1 if existing else 1).data)
            protect_correction(db, user_id, note, {"records": previous}, {"records": saved})
            return {"records": saved}
        return mutate(db, user_id, operation_key, f"corrections/{note_id}", body.model_dump(mode="json", exclude_unset=True), action)

    @router.post("/notes/{note_id}/corrections")
    def correction(note_id: UUID, body: CorrectionDTO, operation_key=Depends(key), user_id=Depends(user), db=Depends(db_dependency)):
        return correct_note(note_id, body, operation_key, user_id, db)

    @router.post("/interpretations/{history_id}/corrections")
    def interpretation_correction(history_id: UUID, body: CorrectionDTO, operation_key=Depends(key), user_id=Depends(user), db=Depends(db_dependency)):
        history = owned(db, History, str(history_id), user_id)
        return correct_note(history.note_id, body, operation_key, user_id, db)

    @router.get("/types")
    def types(user_id=Depends(user), db=Depends(db_dependency)):
        return {"types": [row.data for row in db.scalars(select(TypeDefinition).where(TypeDefinition.user_id == user_id)).all()]}

    def put_type(body, operation_key, user_id, db, patch=False):
        def action():
            rows = db.scalars(select(TypeDefinition).where(TypeDefinition.id == str(body.id))).all()
            if any(row.user_id != user_id for row in rows):
                raise HTTPException(409, "Type ID unavailable")
            previous = max((row.schema_version for row in rows), default=0)
            if body.schemaVersion != previous + 1 or bool(previous) != patch:
                raise HTTPException(409, "Append the next schema version")
            value = body.model_dump(mode="json")
            db.add(TypeDefinition(id=str(body.id), schema_version=body.schemaVersion, user_id=user_id, data=value))
            changed(db, user_id, "type", str(body.id), value)
            return value
        return mutate(db, user_id, operation_key, f"type/{body.id}/{body.schemaVersion}", body.model_dump(mode="json", exclude_unset=True), action)

    @router.post("/types")
    def create_type(body: TypeDTO, operation_key=Depends(key), user_id=Depends(user), db=Depends(db_dependency)):
        return put_type(body, operation_key, user_id, db)

    @router.patch("/types/{type_id}")
    def edit_type(type_id: UUID, body: TypeDTO, operation_key=Depends(key), user_id=Depends(user), db=Depends(db_dependency)):
        if str(body.id) != str(type_id): raise HTTPException(422, "Type ID mismatch")
        return put_type(body, operation_key, user_id, db, True)

    @router.post("/rules")
    def put_rule(body: RuleDTO, operation_key=Depends(key), user_id=Depends(user), db=Depends(db_dependency)):
        def action():
            if set(body.conditions) != {"contains"} or not body.conditions["contains"].strip() or len(body.conditions["contains"]) > 200:
                raise HTTPException(422, "Only a nonempty contains condition is supported")
            if set(body.action) != {"baseKind"} or body.action["baseKind"] not in {"task", "event", "idea", "information"}:
                raise HTTPException(422, "Only base-kind rules are supported")
            row = db.get(Rule, str(body.id))
            if row and row.user_id != user_id: raise HTTPException(409, "Rule ID unavailable")
            value = body.model_dump(mode="json")
            db.merge(Rule(id=str(body.id), user_id=user_id, data=value))
            changed(db, user_id, "rule", str(body.id), value)
            return value
        return mutate(db, user_id, operation_key, f"rule/{body.id}", body.model_dump(mode="json", exclude_unset=True), action)

    @router.post("/ideas/{record_id}/develop")
    def develop(record_id: UUID, body: dict = Body(default={}), operation_key=Depends(key), user_id=Depends(user), db=Depends(db_dependency)):
        def action():
            record = owned(db, Record, str(record_id), user_id, True)
            if record.base_kind != "idea": raise HTTPException(422, "Only ideas can be developed")
            note = owned(db, Note, record.source_note_id, user_id, True)
            return job_wire(queue_job(db, note, "develop", record))
        return mutate(db, user_id, operation_key, f"develop/{record_id}", body, action)

    @router.get("/jobs/{job_id}")
    def get_job(job_id: UUID, user_id=Depends(user), db=Depends(db_dependency)):
        return job_wire(owned(db, Job, str(job_id), user_id))

    @router.get("/changes")
    def changes(cursor: int = Query(default=0, ge=0), limit: int = Query(default=50, ge=1, le=100), user_id=Depends(user), db=Depends(db_dependency)):
        rows = db.scalars(select(Change).where(Change.user_id == user_id, Change.sequence > cursor).order_by(Change.sequence).limit(limit + 1)).all()
        values, size = [], 0
        for row in rows[:limit]:
            value = {"sequence": row.sequence, "kind": row.kind, "entityId": row.entity_id, "payload": row.payload}
            encoded_size = len(json.dumps(value).encode())
            if values and size + encoded_size > 1_000_000: break
            values.append(value); size += encoded_size
        return {"changes": values, "nextCursor": str(values[-1]["sequence"] if values else cursor), "hasMore": len(rows) > len(values)}

    return router
