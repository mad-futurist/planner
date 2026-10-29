"""Organizer workflows. HTTP and SQL are adapters; transactions are explicit UoW operations."""
import base64
import json
from datetime import datetime, timedelta, timezone
from uuid import UUID
from app.domain.entities import Device, History, Job, Note, Record, Rule, TypeDefinition
from app.domain.time import now
from app.domain.errors import ApplicationError
from app.application.values import RecordDTO, TypeDTO, NoteDTO, PreviewRequest, Enrollment, NoteEdit, RecordMutation, VersionAction, CorrectionDTO, RuleDTO
from app.application.mutations import changed, history_wire, job_wire, lock_user, mutate, note_wire, owned, protect_correction, queue_job, save_record

class OrganizerService:
    def __init__(self, policy, provider_factory, identity):
        self.policy = policy
        self.provider_factory = provider_factory
        self.identity = identity

    def enroll_device(self, body: Enrollment, db):
        return self.identity.enroll(db, body.code, str(body.deviceId))

    def revoke_device(self, user_id, authorization: str, db):
        self.identity.revoke(db, authorization)
        return {'revoked': True}

    def config(self, user_id):
        return {'aiConfigured': self.policy.ai_available, 'model': self.policy.llm_model if self.policy.ai_available else None}

    def interpretation_preview(self, body: PreviewRequest, user_id):
        from app.domain.capture_preview import preview
        return preview(body.content, body.createdAt, body.timezone, body.interpretationHints.to_dict())

    def ai_preview(self, body: PreviewRequest, user_id, db):
        from app.application.ports import ProviderUnavailable, RetryableProviderError
        from app.application.ports import ProviderBudgetExceeded
        from app.domain.validation import semantic_review
        if not self.policy.ai_available:
            raise ApplicationError(503, 'AI provider is not configured')
        try:
            db.reserve_request(user_id, 'preview')
        except ProviderBudgetExceeded:
            raise ApplicationError(429, 'Daily AI request limit reached')
        from app.application.ai_context import context_for, apply_rules
        from types import SimpleNamespace
        from app.application.observations import snapshot
        from uuid import uuid4
        hints = body.interpretationHints.to_dict(mode='json')
        seed = SimpleNamespace(content=body.content, user_id=user_id, created_at=body.createdAt, timezone=body.timezone, locale=body.locale, interpretation_hints=hints)
        active, rules, context = context_for(db, seed)
        db.rollback()
        provider = self.provider_factory()
        sample_key = 'preview:' + str(uuid4())
        try:
            result = provider.extract(body.content, context)
        except Exception as error:
            db.prediction_observation(snapshot(seed), user_id, key=sample_key, kind='provider_error', provider=provider, status='failed', context=context, error=error)
            db.commit()
            raise ApplicationError(502, 'AI provider is temporarily unavailable' if isinstance(error, RetryableProviderError) else 'AI provider did not return a usable interpretation')
        raw_result = result
        result = apply_rules(result, rules, body.content)
        reasons = semantic_review(result, body.content, body.createdAt, body.timezone, hints)
        from app.domain.validation import validate_fields
        from uuid import uuid4
        for proposal in result.records:
            definition = active.get(str(proposal.customTypeId)) if proposal.customTypeId else None
            try:
                validate_fields(RecordDTO.from_mapping(proposal.to_dict(exclude={'evidence'}) | {'id': uuid4(), 'sourceNoteId': uuid4(), 'typeSchemaVersion': definition.schemaVersion if definition else None}), definition)
            except ValueError:
                reasons.append('Custom fields or record semantics need review')
        db.prediction_observation(snapshot(seed), user_id, key=sample_key, kind='ai_preview', provider=provider, result=raw_result, status='review' if reasons else 'valid', reasons=reasons, context=context, outcome={'applied': False, 'effectivePrediction': result.to_dict(mode='json')})
        db.commit()
        return {'schemaVersion': 1, 'engine': 'provider:' + self.policy.llm_model, 'records': result.to_dict(mode='json')['records'], 'reviewReasons': reasons, 'needsReview': bool(reasons), 'previewOnly': True}

    def diagnostics(self, user_id, db):
        since = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0).isoformat(timespec='milliseconds').replace('+00:00', 'Z')
        counts, oldest, used = db.diagnostics(user_id, since)
        return {'jobs': counts, 'oldestQueuedAt': oldest, 'aiRequestsToday': used, 'aiDailyRequestLimit': self.policy.ai_daily_request_limit}

    def ingest(self, body: NoteDTO, operation_key, user_id, db):

        def action():
            existing = db.get(Note, str(body.id))
            if existing:
                if existing.user_id != user_id or existing.content != body.content:
                    raise ApplicationError(409, 'Note ID unavailable or content differs')
                return note_wire(existing)
            since = (datetime.now(timezone.utc) - timedelta(minutes=1)).isoformat(timespec='milliseconds').replace('+00:00', 'Z')
            if db.recent_note_count(user_id, since) >= 60:
                raise ApplicationError(429, 'Capture rate limit; retry later', headers={'Retry-After': '60'})
            if body.records and (not body.manual):
                raise ApplicationError(422, 'Initial records require explicit manual capture')
            note = Note(id=str(body.id), user_id=user_id, content=body.content, original_content=body.originalContent or body.content, created_at=body.createdAt, updated_at=body.createdAt, source=body.source, timezone=body.timezone, locale=body.locale, version=body.version, manual=body.manual, processing_status='organized' if body.manual else 'pending', user_corrected=body.manual)
            db.add(note)
            db.flush()
            note.interpretation_hints = body.interpretationHints.to_dict(mode='json')
            changed(db, user_id, 'note', note.id, note_wire(note))
            for record in body.records:
                if str(record.sourceNoteId) != note.id:
                    raise ApplicationError(422, 'Source note mismatch')
                save_record(db, user_id, record.replace(update={'isUserConfirmed': True}))
            if not body.manual:
                queue_job(db, note)
            db.input_observation(note, [r.data for r in db.records_for_note(note.id)])
            return note_wire(note)
        return mutate(db, user_id, operation_key, 'POST notes', body.to_dict(mode='json', exclude_unset=True), action)

    def list_notes(self, cursor: str, limit: int, user_id, db):
        position = None
        if cursor:
            try:
                position = json.loads(base64.urlsafe_b64decode(cursor))
                timestamp, entity_id = position
            except (ValueError, TypeError):
                raise ApplicationError(422, 'Invalid cursor')
        rows = db.note_page(user_id, position, limit + 1)
        page = rows[:limit]
        next_cursor = base64.urlsafe_b64encode(json.dumps([page[-1].created_at, page[-1].id]).encode()).decode() if len(rows) > limit else None
        return {'notes': [note_wire(n) for n in page], 'nextCursor': next_cursor}

    def get_note(self, note_id: UUID, user_id, db):
        note = owned(db, Note, str(note_id), user_id)
        records = db.records_for_note(note.id, user_id)
        return note_wire(note) | {'records': [r.data for r in records]}

    def edit_note(self, note_id: UUID, body: NoteEdit, operation_key, user_id, db):

        def action():
            note = owned(db, Note, str(note_id), user_id, True)
            if note.version != body.expectedVersion:
                raise ApplicationError(409, {'code': 'version_conflict', 'currentVersion': note.version})
            before = note_wire(note)
            note.content = body.content
            note.interpretation_hints = {}
            note.version += 1
            note.updated_at = now()
            protect_correction(db, user_id, note, before, note_wire(note), 'raw_edit')
            queue_job(db, note)
            return note_wire(note)
        return mutate(db, user_id, operation_key, f'PATCH notes/{note_id}', body.to_dict(mode='json', exclude_unset=True), action)

    def reprocess(self, note_id: UUID, body: dict, operation_key, user_id, db):

        def action():
            note = owned(db, Note, str(note_id), user_id, True)
            return job_wire(queue_job(db, note))
        return mutate(db, user_id, operation_key, f'POST notes/{note_id}/reprocess', body, action)

    def interpretation(self, note_id: UUID, user_id, db):
        note = owned(db, Note, str(note_id), user_id)
        history = db.latest_history(note.id)
        return {'processingStatus': note.processing_status, 'interpretation': history_wire(history) if history else None}

    def update_record(self, record_id: UUID, body: RecordMutation, operation_key, user_id, db):

        def action():
            record = owned(db, Record, str(record_id), user_id, True)
            note = owned(db, Note, record.source_note_id, user_id, True)
            if record.version != body.expectedVersion:
                raise ApplicationError(409, {'code': 'version_conflict', 'currentVersion': record.version})
            if str(body.id) != record.id or str(body.sourceNoteId) != note.id:
                raise ApplicationError(422, 'Immutable record/source ID changed')
            before = record.data
            data = RecordDTO.from_mapping(body.to_dict(exclude={'expectedVersion'})).replace(update={'isUserConfirmed': True})
            updated = save_record(db, user_id, data, record.version + 1)
            semantic_fields = ('baseKind', 'customTypeId', 'typeSchemaVersion', 'title', 'fields', 'startAt', 'endAt', 'dueAt', 'dueDate', 'plannedFor', 'remindAt', 'timezone', 'recurrenceRule')
            semantic_edit = any((before.get(field) != updated.data.get(field) for field in semantic_fields))
            state_edit = any((before.get(field) != updated.data.get(field) for field in ('state', 'completedAt')))
            category = 'record_edit' if semantic_edit else 'task_state' if state_edit and record.base_kind == 'task' else 'record_action'
            protect_correction(db, user_id, note, before, updated.data, category)
            return updated.data
        return mutate(db, user_id, operation_key, f'PATCH records/{record_id}', body.to_dict(mode='json', exclude_unset=True), action)

    def task_action(self, record_id, body, operation_key, user_id, db, undo):

        def action():
            record = owned(db, Record, str(record_id), user_id, True)
            note = owned(db, Note, record.source_note_id, user_id, True)
            if record.version != body.expectedVersion:
                raise ApplicationError(409, {'code': 'version_conflict', 'currentVersion': record.version})
            if record.base_kind != 'task':
                raise ApplicationError(422, 'Only tasks can be completed')
            value = record.data | {'state': 'pending' if undo else 'completed', 'completedAt': None if undo else body.completedAt or now(), 'isUserConfirmed': True}
            before = record.data
            updated = save_record(db, user_id, RecordDTO.from_mapping(value), record.version + 1)
            protect_correction(db, user_id, note, before, updated.data, 'task_state')
            return updated.data
        return mutate(db, user_id, operation_key, f"task/{record_id}/{('undo' if undo else 'complete')}", body.to_dict(), action)

    def complete(self, record_id: UUID, body: VersionAction, operation_key, user_id, db):
        return self.task_action(record_id, body, operation_key, user_id, db, False)

    def undo(self, record_id: UUID, body: VersionAction, operation_key, user_id, db):
        return self.task_action(record_id, body, operation_key, user_id, db, True)

    def correct_note(self, note_id, body, operation_key, user_id, db):

        def action():
            note = owned(db, Note, str(note_id), user_id, True)
            if str(body.noteId) != note.id or note.version != body.expectedNoteVersion:
                raise ApplicationError(409, {'code': 'version_conflict', 'currentVersion': note.version})
            previous = [r.data for r in db.records_for_note(note.id)]
            saved = []
            for dto in body.records:
                if str(dto.sourceNoteId) != note.id:
                    raise ApplicationError(422, 'Source mismatch')
                existing = db.get(Record, str(dto.id))
                saved.append(save_record(db, user_id, dto.replace(update={'isUserConfirmed': True}), existing.version + 1 if existing else 1).data)
            protect_correction(db, user_id, note, {'records': previous}, {'records': saved}, 'interpretation_edit')
            return {'records': saved}
        return mutate(db, user_id, operation_key, f'corrections/{note_id}', body.to_dict(mode='json', exclude_unset=True), action)

    def correction(self, note_id: UUID, body: CorrectionDTO, operation_key, user_id, db):
        return self.correct_note(note_id, body, operation_key, user_id, db)

    def interpretation_correction(self, history_id: UUID, body: CorrectionDTO, operation_key, user_id, db):
        history = owned(db, History, str(history_id), user_id)
        return self.correct_note(history.note_id, body, operation_key, user_id, db)

    def types(self, user_id, db):
        return {'types': [row.data for row in db.owner_types(user_id)]}

    def put_type(self, body, operation_key, user_id, db, patch=False):

        def action():
            rows = db.type_versions(str(body.id))
            if any((row.user_id != user_id for row in rows)):
                raise ApplicationError(409, 'Type ID unavailable')
            previous = max((row.schema_version for row in rows), default=0)
            if body.schemaVersion != previous + 1 or bool(previous) != patch:
                raise ApplicationError(409, 'Append the next schema version')
            value = body.to_dict(mode='json')
            db.add(TypeDefinition(id=str(body.id), schema_version=body.schemaVersion, user_id=user_id, data=value))
            changed(db, user_id, 'type', str(body.id), value)
            return value
        return mutate(db, user_id, operation_key, f'type/{body.id}/{body.schemaVersion}', body.to_dict(mode='json', exclude_unset=True), action)

    def create_type(self, body: TypeDTO, operation_key, user_id, db):
        return self.put_type(body, operation_key, user_id, db)

    def edit_type(self, type_id: UUID, body: TypeDTO, operation_key, user_id, db):
        if str(body.id) != str(type_id):
            raise ApplicationError(422, 'Type ID mismatch')
        return self.put_type(body, operation_key, user_id, db, True)

    def put_rule(self, body: RuleDTO, operation_key, user_id, db):

        def action():
            if set(body.conditions) != {'contains'} or not body.conditions['contains'].strip() or len(body.conditions['contains']) > 200:
                raise ApplicationError(422, 'Only a nonempty contains condition is supported')
            if set(body.action) != {'baseKind'} or body.action['baseKind'] not in {'task', 'event', 'idea', 'information'}:
                raise ApplicationError(422, 'Only base-kind rules are supported')
            row = db.get(Rule, str(body.id))
            if row and row.user_id != user_id:
                raise ApplicationError(409, 'Rule ID unavailable')
            value = body.to_dict(mode='json')
            db.merge(Rule(id=str(body.id), user_id=user_id, data=value))
            changed(db, user_id, 'rule', str(body.id), value)
            return value
        return mutate(db, user_id, operation_key, f'rule/{body.id}', body.to_dict(mode='json', exclude_unset=True), action)

    def develop(self, record_id: UUID, body: dict, operation_key, user_id, db):

        def action():
            record = owned(db, Record, str(record_id), user_id, True)
            if record.base_kind != 'idea':
                raise ApplicationError(422, 'Only ideas can be developed')
            note = owned(db, Note, record.source_note_id, user_id, True)
            return job_wire(queue_job(db, note, 'develop', record))
        return mutate(db, user_id, operation_key, f'develop/{record_id}', body, action)

    def get_job(self, job_id: UUID, user_id, db):
        return job_wire(owned(db, Job, str(job_id), user_id))

    def changes(self, cursor: int, limit: int, user_id, db):
        rows = db.change_page(user_id, cursor, limit + 1)
        values, size = ([], 0)
        for row in rows[:limit]:
            value = {'sequence': row.sequence, 'kind': row.kind, 'entityId': row.entity_id, 'payload': row.payload}
            encoded_size = len(json.dumps(value).encode())
            if values and size + encoded_size > 1000000:
                break
            values.append(value)
            size += encoded_size
        return {'changes': values, 'nextCursor': str(values[-1]['sequence'] if values else cursor), 'hasMore': len(rows) > len(values)}
