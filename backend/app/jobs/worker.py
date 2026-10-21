import random
import re
import time
import logging
import json
from sqlalchemy.exc import SQLAlchemyError
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from uuid import UUID, uuid5
from sqlalchemy import and_, or_, select, func
from app.ai.provider import OpenAICompatibleProvider, ProviderUnavailable, RetryableProviderError, ProviderContextError
from app.config import settings
from app.domain.validation import semantic_review
from app.persistence.database import SessionFactory
from app.persistence.models import Correction, History, Job, Note, Record, Rule, TypeDefinition, now, uid
from app.schemas import RecordDTO, TypeDTO
from app.services import changed, history_wire, job_wire, lock_user, note_wire, save_record


def claim(factory):
    with factory() as db:
        current = now()
        job = db.scalar(select(Job).where(or_(and_(Job.status == "queued", Job.next_attempt_at <= current), and_(Job.status == "running", Job.lease_until < current))).order_by(Job.next_attempt_at, Job.id).with_for_update(skip_locked=True).limit(1))
        if not job:
            return None
        job.status = "running"; job.attempts += 1; job.lease_token = uid()
        job.lease_until = (datetime.now(timezone.utc) + timedelta(minutes=2)).isoformat(timespec="milliseconds").replace("+00:00", "Z")
        db.commit()
        return job.id, job.lease_token


def relevant_corrections(db, note):
    words = set(re.findall(r"\w+", note.content.casefold()))
    recent = db.scalars(select(Correction).where(Correction.user_id == note.user_id).order_by(Correction.created_at.desc()).limit(50)).all()
    def relevance(c):
        return len(words & set(re.findall(r"\w+", (str(c.previous_interpretation) + str(c.corrected_interpretation)).casefold())))
    ranked = sorted((c for c in recent if relevance(c) > 0), key=relevance, reverse=True)
    return [{"before": c.previous_interpretation, "after": c.corrected_interpretation} for c in ranked[:5]]


def process_one(factory=SessionFactory, provider=None, max_attempts: int | None = None):
    claimed = claim(factory)
    if not claimed:
        return False
    job_id, lease = claimed
    provider = provider or OpenAICompatibleProvider(settings)
    try:
        with factory() as db:
            job = db.get(Job, job_id); note = db.get(Note, job.note_id)
            if job.attempts > (max_attempts or settings.max_job_attempts):
                raise RetryableProviderError("Durable job attempt limit reached")
            latest = select(TypeDefinition.id, func.max(TypeDefinition.schema_version).label("version")).where(TypeDefinition.user_id == note.user_id).group_by(TypeDefinition.id).subquery()
            type_rows = db.scalars(select(TypeDefinition).join(latest, and_(TypeDefinition.id == latest.c.id, TypeDefinition.schema_version == latest.c.version)).where(TypeDefinition.data["enabled"].as_boolean().is_(True)).order_by(TypeDefinition.id).limit(50)).all()
            active = {}
            for row in type_rows:
                if row.id not in active or row.schema_version > active[row.id].schemaVersion:
                    active[row.id] = TypeDTO.model_validate(row.data)
            active = {key: value for key, value in active.items() if value.enabled}
            rules = [r.data for r in db.scalars(select(Rule).where(Rule.user_id == note.user_id, Rule.data["enabled"].as_boolean().is_(True), Rule.data["explicitlyApproved"].as_boolean().is_(True)).order_by(Rule.id).limit(20)).all()]
            context = {"capturedAt": note.created_at, "timezone": note.timezone, "locale": note.locale, "types": [t.model_dump(mode="json") for t in active.values()], "rules": rules, "corrections": relevant_corrections(db, note)}
            if job.kind == "extract":
                content = note.content
            else:
                idea = db.get(Record, job.record_id)
                content = json.dumps({"title": idea.title, "fields": idea.data.get("fields", {})}, ensure_ascii=False)
            input_version = job.input_version
        result = provider.extract(content, context) if job.kind == "extract" else provider.develop(content, context)
        with factory() as db:
            job = db.get(Job, job_id)
            if job.lease_token != lease:
                return True
            lock_user(db, job.user_id)
            note = db.scalar(select(Note).where(Note.id == job.note_id).with_for_update())
            stale = note.version != input_version
            if job.kind == "develop":
                record = db.get(Record, job.record_id)
                stale = stale or not record or record.version != job.record_version
                job.result = result.model_dump(mode="json") if not stale else None
            else:
                effective = result.model_copy(update={"records": [proposal.model_copy(update={key: value for rule in rules if rule["conditions"]["contains"].casefold() in content.casefold() for key, value in rule["action"].items()}) for proposal in result.records]})
                reasons = semantic_review(effective, note.content, note.created_at, note.timezone)
                review = bool(reasons) or note.user_corrected or stale
                history = History(user_id=note.user_id, note_id=note.id, input_version=input_version, model_version=provider.model, proposed_data=result.model_dump(mode="json") | {"ambiguities": reasons, "riskLevel": "review_required" if review else "validated_low"}, validation_result="stale" if stale else "review" if review else "valid")
                db.add(history); db.flush()
                changed(db, note.user_id, "interpretation", history.id, history_wire(history))
                if not review:
                    for index, proposal in enumerate(result.records):
                        definition = active.get(str(proposal.customTypeId)) if proposal.customTypeId else None
                        values = proposal.model_dump(mode="json", exclude={"evidence"})
                        for rule in rules:
                            if rule.get("conditions", {}).get("contains", "").casefold() in content.casefold():
                                values.update(rule.get("action", {}))
                        record_id = str(uuid5(UUID(note.id), str(index)))
                        previous = db.get(Record, record_id)
                        values.update(id=record_id, sourceNoteId=note.id, typeSchemaVersion=definition.schemaVersion if definition else None, interpretationVersion=input_version, timezone=note.timezone)
                        record_dto = RecordDTO.model_validate(values)
                        save_record(db, note.user_id, record_dto, previous.version + 1 if previous else 1)
                    active_ids = {str(uuid5(UUID(note.id), str(i))) for i in range(len(result.records))}
                    for previous in db.scalars(select(Record).where(Record.source_note_id == note.id, Record.confirmed.is_(False))).all():
                        if previous.id not in active_ids:
                            archived = RecordDTO.model_validate(previous.data).model_copy(update={"state": "archived", "completedAt": None})
                            save_record(db, note.user_id, archived, previous.version + 1)
                note.processing_status = "review" if review else "organized"
                changed(db, note.user_id, "note", note.id, note_wire(note))
            job.status = "stale" if stale else "complete"
            job.lease_until = None
            changed(db, job.user_id, "job", job.id, job_wire(job))
            db.commit()
    except Exception as error:
        with factory() as db:
            job = db.get(Job, job_id)
            if job.lease_token != lease:
                return True
            lock_user(db, job.user_id)
            if isinstance(error, ProviderUnavailable):
                job.status = "unconfigured"; job.last_error = "provider_unconfigured"
            elif isinstance(error, RetryableProviderError) and job.attempts < (max_attempts or settings.max_job_attempts):
                job.status = "queued"; job.last_error = "provider_retryable"
                delay = min(3600, 30 * 2 ** min(job.attempts - 1, 8)) * random.uniform(0.8, 1.2)
                job.next_attempt_at = (datetime.now(timezone.utc) + timedelta(seconds=delay)).isoformat(timespec="milliseconds").replace("+00:00", "Z")
            else:
                job.status = "failed"; job.last_error = "provider_context_too_large" if isinstance(error, ProviderContextError) else "provider_invalid_output" if isinstance(error, ValueError) else "provider_failure"
            job.lease_until = None
            if job.kind == "extract":
                note = db.get(Note, job.note_id)
                if note.version == job.input_version:
                    note.processing_status = "pending" if job.status == "queued" else job.status
                    changed(db, note.user_id, "note", note.id, note_wire(note))
            changed(db, job.user_id, "job", job.id, job_wire(job))
            db.commit()
    return True


def run():
    # At most one in-flight claim per thread; multiple processes use SKIP LOCKED.
    def loop():
        while True:
            try:
                if not process_one():
                    time.sleep(1)
            except SQLAlchemyError:
                # No SQL parameters or note text in operational logs. Expired leases recover.
                logging.getLogger(__name__).warning("Database unavailable; retrying durable jobs")
                time.sleep(5)
    with ThreadPoolExecutor(max_workers=max(1, min(settings.worker_concurrency, 8))) as pool:
        futures = [pool.submit(loop) for _ in range(max(1, min(settings.worker_concurrency, 8)))]
        for future in futures:
            future.result()


if __name__ == "__main__":
    run()
