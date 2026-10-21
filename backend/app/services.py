import hashlib
import json
from uuid import UUID
from sqlalchemy import select, text
from fastapi import HTTPException
from app.persistence.models import Change, Correction, History, Job, Note, Operation, Record, TypeDefinition, now
from app.schemas import RecordDTO, TypeDTO
from app.domain.validation import validate_fields


def lock_user(db, user_id: str):
    # Serialize feed writers per identity: sequence allocation must follow commit order.
    if db.bind.dialect.name == "postgresql":
        key = int(hashlib.sha256(user_id.encode()).hexdigest()[:15], 16)
        db.execute(text("SELECT pg_advisory_xact_lock(:key)"), {"key": key})


def owned(db, cls, entity_id: str, user_id: str, lock: bool = False):
    query = select(cls).where(cls.id == entity_id, cls.user_id == user_id)
    if lock:
        query = query.with_for_update()
    row = db.scalar(query)
    if row is None:
        raise HTTPException(404, "Not found")
    return row


def note_wire(note: Note) -> dict:
    return {"id": note.id, "content": note.content, "originalContent": note.original_content, "createdAt": note.created_at, "updatedAt": note.updated_at, "source": note.source, "privacyMode": "CLOUD_ALLOWED", "processingStatus": note.processing_status, "syncStatus": "synced", "version": note.version, "locale": note.locale, "timezone": note.timezone, "manual": note.manual}


def history_wire(history: History) -> dict:
    return {"id": history.id, "noteId": history.note_id, "inputVersion": history.input_version, "schemaVersion": history.schema_version, "modelVersion": history.model_version, "proposedData": history.proposed_data, "validationResult": history.validation_result, "correctedByUser": history.corrected_by_user, "createdAt": history.created_at}


def job_wire(job: Job) -> dict:
    return {"id": job.id, "noteId": job.note_id, "recordId": job.record_id, "inputVersion": job.input_version, "recordVersion": job.record_version, "kind": job.kind, "status": job.status, "attempts": job.attempts, "lastError": job.last_error, "result": job.result}


def changed(db, user_id: str, kind: str, entity_id: str, payload: dict):
    db.add(Change(user_id=user_id, kind=kind, entity_id=entity_id, payload=payload))


def mutate(db, user_id: str, key: str, path: str, payload: dict, action):
    try:
        key = str(UUID(key))
    except ValueError:
        raise HTTPException(400, "Idempotency-Key must be a UUID")
    lock_user(db, user_id)
    fingerprint = hashlib.sha256((path + json.dumps(payload, sort_keys=True, separators=(",", ":"))).encode()).hexdigest()
    existing = db.get(Operation, (user_id, key))
    if existing:
        if existing.digest != fingerprint:
            raise HTTPException(409, "Idempotency key reused with different input")
        return existing.result
    result = action()
    db.add(Operation(user_id=user_id, key=key, digest=fingerprint, result=result))
    db.commit()
    return result


def record_type(db, user_id: str, record: RecordDTO):
    if not record.customTypeId:
        return None
    row = db.get(TypeDefinition, (str(record.customTypeId), record.typeSchemaVersion))
    if not row or row.user_id != user_id:
        raise ValueError("Unknown custom type version")
    return TypeDTO.model_validate(row.data)


def save_record(db, user_id: str, data: RecordDTO, version: int = 1):
    validate_fields(data, record_type(db, user_id, data))
    existing = db.get(Record, str(data.id))
    if existing and existing.user_id != user_id:
        raise HTTPException(409, "Record ID unavailable")
    if existing and existing.source_note_id != str(data.sourceNoteId):
        raise HTTPException(422, "Record provenance cannot be changed")
    row = existing or Record(id=str(data.id), user_id=user_id)
    wire = data.model_dump(mode="json")
    wire["version"] = version
    wire["updatedAt"] = now()
    row.source_note_id = str(data.sourceNoteId)
    row.base_kind = data.baseKind
    row.title = data.title
    row.planned_for = wire["plannedFor"]
    row.due_date = wire["dueDate"]
    row.start_at = data.startAt
    row.due_at = data.dueAt
    row.version = version
    row.confirmed = data.isUserConfirmed
    row.data = wire
    db.add(row)
    changed(db, user_id, "record", row.id, wire)
    return row


def protect_correction(db, user_id: str, note: Note, before: dict, after: dict):
    note.user_corrected = True
    note.processing_status = "organized"
    note.updated_at = now()
    db.add(Correction(user_id=user_id, source_note_id=note.id, previous_interpretation=before, corrected_interpretation=after))
    changed(db, user_id, "note", note.id, note_wire(note))


def queue_job(db, note: Note, kind: str = "extract", record: Record | None = None) -> Job:
    record_id = record.id if record else ""
    record_version = record.version if record else 0
    job = db.scalar(select(Job).where(Job.note_id == note.id, Job.input_version == note.version, Job.kind == kind, Job.record_id == record_id, Job.record_version == record_version))
    if not job:
        job = Job(user_id=note.user_id, note_id=note.id, input_version=note.version, kind=kind, record_id=record_id, record_version=record.version if record else 0)
        db.add(job); db.flush()
    elif job.status in {"failed", "unconfigured", "complete", "stale"}:
        job.status = "queued"; job.attempts = 0; job.next_attempt_at = now(); job.last_error = None; job.result = None
        job.record_version = record.version if record else 0
    if kind == "extract":
        note.processing_status = "pending"
        changed(db, note.user_id, "note", note.id, note_wire(note))
    changed(db, note.user_id, "job", job.id, job_wire(job))
    return job
