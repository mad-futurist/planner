import hashlib
import json
from uuid import UUID
from app.domain.errors import ApplicationError
from app.domain.entities import Change, Correction, History, Job, Note, Operation, Record, TypeDefinition
from app.domain.time import now
from app.application.values import RecordDTO, TypeDTO
from app.domain.validation import validate_fields


def lock_user(db, user_id: str):
    db.lock_owner(user_id)


def owned(db, cls, entity_id: str, user_id: str, lock: bool = False):
    row = db.owned(cls, entity_id, user_id, lock)
    if row is None:
        raise ApplicationError(404, "Not found")
    return row


def note_wire(note: Note) -> dict:
    return {"id": note.id, "content": note.content, "originalContent": note.original_content, "createdAt": note.created_at, "updatedAt": note.updated_at, "source": note.source, "privacyMode": "CLOUD_ALLOWED", "processingStatus": note.processing_status, "syncStatus": "synced", "version": note.version, "locale": note.locale, "timezone": note.timezone, "manual": note.manual, "interpretationHints": note.interpretation_hints or {}}


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
        raise ApplicationError(400, "Idempotency-Key must be a UUID")
    lock_user(db, user_id)
    fingerprint = hashlib.sha256((path + json.dumps(payload, sort_keys=True, separators=(",", ":"))).encode()).hexdigest()
    existing = db.get(Operation, (user_id, key))
    if existing:
        if existing.digest != fingerprint:
            raise ApplicationError(409, "Idempotency key reused with different input")
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
    return TypeDTO.from_mapping(row.data)


def save_record(db, user_id: str, data: RecordDTO, version: int = 1):
    validate_fields(data, record_type(db, user_id, data))
    existing = db.get(Record, str(data.id))
    if existing and existing.user_id != user_id:
        raise ApplicationError(409, "Record ID unavailable")
    if existing and existing.source_note_id != str(data.sourceNoteId):
        raise ApplicationError(422, "Record provenance cannot be changed")
    row = existing or Record(id=str(data.id), user_id=user_id)
    wire = data.to_dict(mode="json")
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


def protect_correction(db, user_id: str, note: Note, before: dict, after: dict, kind="record_edit"):
    note.user_corrected = True
    note.processing_status = "organized"
    note.updated_at = now()
    correction = Correction(user_id=user_id, source_note_id=note.id, previous_interpretation=before, corrected_interpretation=after)
    db.add(correction); db.flush()
    db.correction_observation(note, correction, kind)
    changed(db, user_id, "note", note.id, note_wire(note))


def queue_job(db, note: Note, kind: str = "extract", record: Record | None = None) -> Job:
    record_id = record.id if record else ""
    record_version = record.version if record else 0
    job = db.find_job(note.id, note.version, kind, record_id, record_version)
    if not job:
        job = Job(user_id=note.user_id, note_id=note.id, input_version=note.version, kind=kind, record_id=record_id, record_version=record.version if record else 0)
        db.add(job); db.flush()
    elif job.status in {"failed", "unconfigured", "complete", "stale"}:
        job.status = "queued"; job.attempts = 0; job.next_attempt_at = now(); job.last_error = None; job.result = None
        job.record_version = record.version if record else 0
    if kind == "extract":
        note.processing_status = "rate_limited" if job.last_error == "provider_daily_limit" else "pending"
        changed(db, note.user_id, "note", note.id, note_wire(note))
    changed(db, note.user_id, "job", job.id, job_wire(job))
    return job
