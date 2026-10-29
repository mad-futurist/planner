import os
import threading
from concurrent.futures import ThreadPoolExecutor
from uuid import uuid4
import pytest
from sqlalchemy import select
from sqlalchemy.schema import CreateSchema, DropSchema
from sqlalchemy.orm import sessionmaker
from app.jobs.worker import claim
from app.persistence.database import make_database
from app.persistence.models import Base, Job, Note, User, now


@pytest.mark.skipif(not os.getenv("PLANNER_TEST_POSTGRES_URL"), reason="Dedicated PostgreSQL test URL not provided")
def test_postgresql_concurrent_claims_are_unique_and_leased():
    engine, _ = make_database(os.environ["PLANNER_TEST_POSTGRES_URL"])
    schema = "planner_test_" + uuid4().hex
    with engine.begin() as connection: connection.execute(CreateSchema(schema))
    scoped = engine.execution_options(schema_translate_map={None: schema})
    factory = sessionmaker(scoped, expire_on_commit=False)
    try:
        Base.metadata.create_all(scoped)
        with factory() as db:
            user = User(id=str(uuid4())); db.add(user); db.flush()
            for index in range(8):
                note = Note(id=str(uuid4()), user_id=user.id, content=f"fixture {index}", original_content=f"fixture {index}", source="APP", created_at=now(), updated_at=now(), timezone="UTC", locale="en")
                db.add(note); db.flush()
                db.add(Job(user_id=user.id, note_id=note.id, input_version=1))
            db.commit()
        with ThreadPoolExecutor(max_workers=8) as pool: claims = list(pool.map(lambda _: claim(factory), range(8)))
        assert all(claims)
        assert len({job_id for job_id, _ in claims}) == 8
        with factory() as db:
            jobs = db.scalars(select(Job)).all()
            assert all(job.status == "running" and job.lease_token and job.lease_until > now() for job in jobs)
    finally:
        with engine.begin() as connection: connection.execute(DropSchema(schema, cascade=True))
        engine.dispose()


@pytest.mark.skipif(not os.getenv("PLANNER_TEST_POSTGRES_URL"), reason="Dedicated PostgreSQL test URL not provided")
@pytest.mark.parametrize("old_fails", [False, True])
def test_expired_worker_cannot_overwrite_current_completion_or_failure(monkeypatch, old_fails):
    import app.jobs.worker as worker
    from app.ai.provider import RetryableProviderError
    from app.schemas import Extraction
    from app.persistence.models import Record
    engine, _ = make_database(os.environ["PLANNER_TEST_POSTGRES_URL"])
    schema = "planner_test_" + uuid4().hex
    with engine.begin() as connection:
        connection.execute(CreateSchema(schema))
    scoped = engine.execution_options(schema_translate_map={None: schema})
    factory = sessionmaker(scoped, expire_on_commit=False)
    at_lock, release = threading.Event(), threading.Event()
    original_lock = worker.lock_user
    errors = []
    thread = None
    try:
        Base.metadata.create_all(scoped)
        with factory() as db:
            owner = User(id=str(uuid4())); db.add(owner); db.flush()
            note = Note(id=str(uuid4()), user_id=owner.id, content="Lease regression", original_content="Lease regression", source="APP", created_at=now(), updated_at=now(), timezone="UTC", locale="en")
            db.add(note); db.flush()
            job = Job(user_id=owner.id, note_id=note.id, input_version=1)
            db.add(job); db.commit(); job_id = job.id

        def delayed_lock(db, owner):
            if threading.current_thread().name == "expired-worker":
                at_lock.set()
                assert release.wait(20)
            original_lock(db, owner)
        monkeypatch.setattr(worker, "lock_user", delayed_lock)

        class Provider:
            model = "regression-fixture"
            def __init__(self, old=False): self.old = old
            def extract(self, content, context):
                if self.old and old_fails: raise RetryableProviderError()
                return Extraction.model_validate({"records": [{"baseKind": "task", "title": "expired" if self.old else "current"}]})

        def expired():
            try: worker.process_one(factory, Provider(True))
            except BaseException as error: errors.append(error)
        thread = threading.Thread(target=expired, name="expired-worker"); thread.start()
        assert at_lock.wait(20)
        with factory() as db:
            db.get(Job, job_id).lease_until = "2000-01-01T00:00:00.000Z"; db.commit()
        assert worker.process_one(factory, Provider())
        release.set(); thread.join(20)
        assert not thread.is_alive() and not errors
        with factory() as db:
            record = db.scalar(select(Record)); job = db.get(Job, job_id)
            assert record.title == "current" and record.version == 1
            assert job.status == "complete" and job.last_error is None and job.attempts == 2
    finally:
        release.set()
        if thread: thread.join(25)
        with engine.begin() as connection: connection.execute(DropSchema(schema, cascade=True))
        engine.dispose()
