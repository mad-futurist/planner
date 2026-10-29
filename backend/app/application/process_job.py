"""Durable interpretation/development application workflow; provider I/O is outside UoWs."""
import random
import json
import logging
from datetime import datetime, timedelta, timezone
from uuid import UUID, uuid5
from app.domain.entities import History, Job, Note, Record
from app.domain.time import now
from app.domain.validation import semantic_review
from app.application.values import RecordDTO
from app.application.mutations import changed, history_wire, job_wire, note_wire, save_record, lock_user
from app.application.ai_context import context_for, apply_rules
from app.application.observations import snapshot
from app.application.ports import ProviderUnavailable, RetryableProviderError, ProviderContextError, ProviderBudgetExceeded

def next_budget_window():
    return (datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0) + timedelta(days=1)).isoformat(timespec="milliseconds").replace("+00:00", "Z")

def fenced_job(db, job_id, lease, lock_owner=lock_user):
    owner = db.job_owner(job_id)
    if owner is None:
        return None
    lock_owner(db, owner)
    job = db.locked_job(job_id)
    return job if job and job.status == "running" and job.lease_token == lease else None

class ProcessJob:
    def __init__(self, factory, provider, policy, lock_owner=lock_user):
        self.factory = factory
        self.provider = provider
        self.policy = policy
        self.lock_owner = lock_owner

    def execute(self, claimed, managed_provider, max_attempts=None):
        job_id, lease = claimed
        provider = self.provider
        note_input, context, result, attempt_number = None, {}, None, 0
        try:
            with self.factory() as db:
                job = db.get(Job, job_id); note = db.get(Note, job.note_id)
                if job.attempts > (max_attempts or self.policy.max_job_attempts):
                    raise RetryableProviderError("Durable job attempt limit reached")
                active, rules, context = context_for(db, note)
                if job.kind == "extract":
                    content = note.content
                else:
                    idea = db.get(Record, job.record_id)
                    content = json.dumps({"title": idea.title, "fields": idea.data.get("fields", {})}, ensure_ascii=False)
                input_version = job.input_version
                note_input, attempt_number, owner = snapshot(note), job.attempts, job.user_id
                if job.kind == "develop": note_input["developmentRecord"] = idea.data
            if managed_provider and self.policy.ai_available:
                with self.factory() as db: db.reserve_request(job.user_id, job.kind)
            result = provider.extract(content, context) if job.kind == "extract" else provider.develop(content, context)
            with self.factory() as db:
                job = fenced_job(db, job_id, lease, self.lock_owner)
                if job is None:
                    db.prediction_observation( note_input, owner, key=f"job:{job_id}:{lease}", kind="interpretation", provider=provider, result=result, status="obsolete_lease", context=context)
                    db.commit()
                    return True
                note = db.locked_note(job.note_id)
                stale = note.version != input_version
                if job.kind == "develop":
                    record = db.get(Record, job.record_id)
                    stale = stale or not record or record.version != job.record_version
                    job.result = result.to_dict(mode="json") if not stale else None
                else:
                    effective = apply_rules(result, rules, content)
                    reasons = semantic_review(effective, note.content, note.created_at, note.timezone, note.interpretation_hints)
                    review = bool(reasons) or note.user_corrected or stale
                    history = History(user_id=note.user_id, note_id=note.id, input_version=input_version, model_version=provider.model, proposed_data=result.to_dict(mode="json") | {"ambiguities": reasons, "riskLevel": "review_required" if review else "validated_low"}, validation_result="stale" if stale else "review" if review else "valid")
                    db.add(history); db.flush()
                    changed(db, note.user_id, "interpretation", history.id, history_wire(history))
                    if not review:
                        for index, proposal in enumerate(result.records):
                            definition = active.get(str(proposal.customTypeId)) if proposal.customTypeId else None
                            values = proposal.to_dict(mode="json", exclude={"evidence"})
                            for rule in rules:
                                if rule.get("conditions", {}).get("contains", "").casefold() in content.casefold():
                                    values.update(rule.get("action", {}))
                            record_id = str(uuid5(UUID(note.id), str(index)))
                            previous = db.get(Record, record_id)
                            values.update(id=record_id, sourceNoteId=note.id, typeSchemaVersion=definition.schemaVersion if definition else None, interpretationVersion=input_version, timezone=note.timezone)
                            record_dto = RecordDTO.from_mapping(values)
                            save_record(db, note.user_id, record_dto, previous.version + 1 if previous else 1)
                        active_ids = {str(uuid5(UUID(note.id), str(i))) for i in range(len(result.records))}
                        for previous in db.records_for_note(note.id, unconfirmed=True):
                            if previous.id not in active_ids:
                                archived = RecordDTO.from_mapping(previous.data).replace(update={"state": "archived", "completedAt": None})
                                save_record(db, note.user_id, archived, previous.version + 1)
                    note.processing_status = "review" if review else "organized"
                    changed(db, note.user_id, "note", note.id, note_wire(note))
                job.status = "stale" if stale else "complete"
                db.prediction_observation( note_input, job.user_id, key=f"job:{job_id}:{lease}", kind="development" if job.kind == "develop" else "interpretation", provider=provider,
                          result=result, status="stale" if stale else note.processing_status if job.kind == "extract" else "complete", context=context,
                          reasons=reasons if job.kind == "extract" else [], history_id=history.id if job.kind == "extract" else None,
                          outcome={"applied": not review, "recordsAfterProcessing": [r.data for r in db.records_for_note(note.id)]} if job.kind == "extract" else {"applied": False})
                job.lease_until = None
                changed(db, job.user_id, "job", job.id, job_wire(job))
                db.commit()
        except Exception as error:
            with self.factory() as db:
                job = fenced_job(db, job_id, lease, self.lock_owner)
                if job is None:
                    if note_input:
                        db.prediction_observation( note_input, owner, key=f"job:{job_id}:{lease}", kind="provider_error", provider=provider, result=result, status="obsolete_lease", context=context, error=error)
                        db.commit()
                    return True
                logging.getLogger(__name__).warning("Job failed: job_id=%s category=%s", job.id, type(error).__name__)
                if isinstance(error, ProviderBudgetExceeded):
                    job.status = "queued"; job.last_error = "provider_daily_limit"; job.attempts = max(0, job.attempts - 1); job.next_attempt_at = next_budget_window()
                elif isinstance(error, ProviderUnavailable):
                    job.status = "unconfigured"; job.last_error = "provider_unconfigured"
                elif isinstance(error, RetryableProviderError) and job.attempts < (max_attempts or self.policy.max_job_attempts):
                    job.status = "queued"; job.last_error = "provider_retryable"
                    delay = min(3600, 30 * 2 ** min(job.attempts - 1, 8)) * random.uniform(0.8, 1.2)
                    job.next_attempt_at = (datetime.now(timezone.utc) + timedelta(seconds=delay)).isoformat(timespec="milliseconds").replace("+00:00", "Z")
                else:
                    job.status = "failed"; job.last_error = "provider_context_too_large" if isinstance(error, ProviderContextError) else "provider_invalid_output" if isinstance(error, ValueError) else "provider_failure"
                job.lease_until = None
                if job.kind == "extract":
                    note = db.get(Note, job.note_id)
                    if note.version == job.input_version:
                        note.processing_status = "rate_limited" if isinstance(error, ProviderBudgetExceeded) else "pending" if job.status == "queued" else job.status
                        changed(db, note.user_id, "note", note.id, note_wire(note))
                changed(db, job.user_id, "job", job.id, job_wire(job))
                if note_input:
                    db.prediction_observation( note_input, job.user_id, key=f"job:{job_id}:{lease}", kind="provider_error", provider=provider, result=result, status=job.status, context=context, error=error)
                db.commit()
        return True
