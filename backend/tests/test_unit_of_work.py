from uuid import uuid4
from sqlalchemy import select
from app.domain.entities import Change, Note
from app.infrastructure.persistence.unit_of_work import SqlUnitOfWork
from app.persistence.database import make_database
from app.persistence.models import Base, Change as StoredChange, User


def test_autoflush_generated_feed_sequence_is_not_overwritten_by_snapshot(tmp_path):
    engine, factory = make_database("sqlite:///" + str(tmp_path / "mapping.db"))
    Base.metadata.create_all(engine)
    owner = str(uuid4())
    with factory() as session:
        session.add(User(id=owner))
        session.commit()
    with SqlUnitOfWork(factory()) as uow:
        change = Change(user_id=owner, kind="note", entity_id=str(uuid4()), payload={"original": "synthetic"})
        uow.add(change)
        # This read causes SQLAlchemy autoflush before our explicit application commit.
        assert uow.records_for_note(change.entity_id) == []
        uow.commit()
        assert change.sequence == 1
    with factory() as session:
        assert session.scalar(select(StoredChange)).sequence == 1
    engine.dispose()


def test_identity_mapping_retains_local_mutation_and_isolated_json_snapshots(tmp_path):
    engine, factory = make_database("sqlite:///" + str(tmp_path / "identity.db"))
    Base.metadata.create_all(engine)
    owner, note_id = str(uuid4()), str(uuid4())
    with factory() as session:
        session.add(User(id=owner))
        session.commit()
    with SqlUnitOfWork(factory()) as uow:
        note = Note(id=note_id, user_id=owner, content="synthetic", original_content="synthetic", source="APP", created_at="2026-10-28T09:00:00.000Z", updated_at="2026-10-28T09:00:00.000Z", timezone="UTC", locale="en")
        uow.add(note)
        uow.commit()
        note.interpretation_hints = {"schemaVersion": 1, "clarifications": []}
        assert uow.get(Note, note_id) is note
        uow.commit()
    with SqlUnitOfWork(factory()) as uow:
        loaded = uow.get(Note, note_id)
        assert loaded.interpretation_hints["schemaVersion"] == 1
        loaded.interpretation_hints["clarifications"].append({"kind": "literal"})
        uow.rollback()
    with SqlUnitOfWork(factory()) as uow:
        assert uow.get(Note, note_id).interpretation_hints["clarifications"] == []
    engine.dispose()
