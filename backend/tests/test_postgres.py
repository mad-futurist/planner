import os
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
