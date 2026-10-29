"""Workflow acceptance against in-memory ports, without HTTP, SQL or provider clients."""
from dataclasses import replace
from types import SimpleNamespace
from uuid import uuid4
import pytest
from app.application.organizer import OrganizerService
from app.application.values import NoteDTO, RecordDTO
from app.domain.entities import Change, Note, Operation
from app.domain.errors import ApplicationError


class CaptureUnitOfWork:
    def __init__(self):
        self.rows = {}
        self.observations = []
        self.commits = 0

    def lock_owner(self, owner):
        pass

    def get(self, kind, identity):
        return self.rows.get((kind, identity))

    def add(self, row):
        identity = (row.user_id, row.key) if isinstance(row, Operation) else row.entity_id if isinstance(row, Change) else row.id
        self.rows[type(row), identity] = row

    def recent_note_count(self, owner, since):
        return 0

    def records_for_note(self, note_id):
        return []

    def flush(self):
        pass

    def input_observation(self, note, records):
        self.observations.append((note.original_content, records))

    def commit(self):
        self.commits += 1


def test_capture_replay_preserves_original_and_records_one_observation():
    uow = CaptureUnitOfWork()
    owner, key, note_id = str(uuid4()), str(uuid4()), str(uuid4())
    body = NoteDTO.from_mapping({"id": note_id, "content": "maybe find ai course", "manual": True, "createdAt": "2026-10-28T09:00:00.000Z"})
    service = OrganizerService(SimpleNamespace(ai_available=False), lambda: pytest.fail("Raw capture invoked provider"), None)
    first = service.ingest(body, key, owner, uow)
    assert service.ingest(body, key, owner, uow) == first
    assert uow.get(Note, note_id).original_content == body.content
    assert uow.observations == [(body.content, [])]
    assert uow.commits == 1
    assert sum(kind is Note for kind, _ in uow.rows) == 1
    assert sum(kind is Change for kind, _ in uow.rows) == 1
    with pytest.raises(ApplicationError) as error:
        service.ingest(body.replace({"content": "Different synthetic input"}), key, owner, uow)
    assert error.value.status == 409
    assert len(uow.observations) == 1


def test_internally_assembled_task_action_keeps_timestamp_validation():
    with pytest.raises(ValueError, match="offset"):
        RecordDTO.from_mapping({"id": str(uuid4()), "sourceNoteId": str(uuid4()), "title": "Synthetic task", "state": "completed", "completedAt": "2026-10-28T16:00:00"})


def test_note_command_preserves_legacy_nested_idempotency_payload_and_evaluated_defaults():
    from app.schemas import NoteDTO as NoteWire
    from app.api.mapping import command
    note_id = str(uuid4())
    wire = NoteWire.model_validate({"id": note_id, "content": "Synthetic manual task", "createdAt": "2026-10-28T09:00:00Z", "manual": True, "interpretationHints": {}, "records": [{"id": str(uuid4()), "sourceNoteId": note_id, "title": "Synthetic task"}]})
    mapped = command(wire)
    assert mapped.to_dict(mode="json", exclude_unset=True) == wire.model_dump(mode="json", exclude_unset=True)
    assert mapped.to_dict(mode="json") == wire.model_dump(mode="json")
    assert mapped.records[0].createdAt == wire.records[0].createdAt


def test_type_command_preserves_legacy_nested_field_spec_defaults():
    from app.schemas import TypeDTO as TypeWire
    from app.api.mapping import command
    wire = TypeWire.model_validate({"id": str(uuid4()), "name": "Study", "baseKind": "task", "fieldDefinitions": {"minutes": {"type": "number"}}})
    mapped = command(wire)
    assert mapped.to_dict(mode="json", exclude_unset=True) == wire.model_dump(mode="json", exclude_unset=True)
    assert mapped.to_dict(mode="json") == wire.model_dump(mode="json")
