import json
from pathlib import Path
from uuid import uuid4
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import event, func, select
from app.dataset import backfill, diverse_cases, export_rows, generate, import_report
from app.jobs.worker import process_one
from app.main import create_app
from app.persistence.database import make_database
from app.persistence.models import Base, History, LearningSample, Note, User
from app.schemas import Extraction
from argon2 import PasswordHasher
from app.security.identity import issue_invite

TEST_HASH = PasswordHasher(time_cost=1, memory_cost=8192, parallelism=1).hash("synthetic test password only")


@pytest.fixture
def system(tmp_path):
    engine, factory = make_database("sqlite:///" + str(tmp_path / "learning.db"))
    @event.listens_for(engine, "connect")
    def foreign_keys(connection, _): connection.execute("PRAGMA foreign_keys=ON")
    Base.metadata.create_all(engine)
    with TestClient(create_app(factory)) as client:
        def enroll(synthetic=False):
            with factory() as db:
                code = issue_invite(db)
            data = client.post("/api/v1/auth/enroll", json={"code": code, "deviceId": str(uuid4())}).json()
            with factory() as db:
                user = db.get(User, data["userId"])
                user.password_hash = TEST_HASH
                user.dataset_synthetic = synthetic
                db.commit()
            return data["userId"], {"Authorization": "Bearer " + data["token"]}
        owner, headers = enroll()
        yield client, factory, owner, headers, enroll
    engine.dispose()


def operation(headers): return headers | {"Idempotency-Key": str(uuid4())}

def test_alternate_corpus_report_import_keeps_expected_choices_and_capture_context(system, tmp_path):
    from app.benchmark import run
    _, factory, _, _, _ = system
    report = run(limit=1, corpus_path=Path(__file__).parents[1] / "app/domain/assets/noncalendar_choices_v1.json")
    path = tmp_path / "choices-report.json"
    path.write_text(json.dumps(report), encoding="utf-8")
    with factory() as db:
        assert import_report(db, path) == 1
        assert import_report(db, path) == 1
        db.commit()
        row = db.scalars(select(LearningSample)).one()
        assert row.data["input"]["content"] == "maybe find ai course"
        assert row.data["input"]["capturedAt"] == "2026-10-28T10:00:00.000Z"
        assert row.data["expected"]["kindChoices"] == [["task_deadline", "task_unscheduled", "idea"]]


def capture(client, headers, **values):
    data = {"id": str(uuid4()), "content": "Call the doctor tomorrow", "createdAt": "2026-10-27T09:00:00Z", "timezone": "Europe/Paris", "locale": "en"} | values
    response = client.post("/api/v1/notes", headers=operation(headers), json=data)
    assert response.status_code == 200, response.text
    return data["id"]


class Provider:
    model = "synthetic-fixture"
    attempts = [{"rawOutput": '{"records": []}', "requestContext": {"timezone": "Europe/Paris"}}]
    def extract(self, text, context):
        return Extraction.model_validate({"records": [{"baseKind": "task", "title": "Call the doctor", "plannedFor": "2026-10-28", "evidence": {"plannedFor": "tomorrow"}}]})


def test_capture_prediction_and_user_change_are_independent_immutable_samples(system):
    client, factory, owner, headers, _ = system
    note_id = capture(client, headers)
    assert process_one(factory, Provider())
    record = client.get("/api/v1/notes/" + note_id, headers=headers).json()["records"][0]
    changed = client.patch("/api/v1/records/" + record["id"], headers=operation(headers), json=record | {"title": "Call my dentist", "expectedVersion": record["version"]})
    assert changed.status_code == 200, changed.text
    with factory() as db:
        samples = db.scalars(select(LearningSample).where(LearningSample.user_id == owner)).all()
        assert len(samples) == 3
        predicted = next(s for s in samples if s.event_kind == "interpretation")
        edit = next(s for s in samples if s.event_kind == "record_edit")
        assert predicted.data["prediction"]["records"][0]["title"] == "Call the doctor"
        assert predicted.label_source == "model_prediction" and predicted.data["trainingTarget"] is False
        assert predicted.data["providerAttempts"] == Provider.attempts
        assert edit.data["before"]["title"] == "Call the doctor" and edit.data["after"]["title"] == "Call my dentist"
        assert any(c["path"] == "/title" and c["after"] == "Call my dentist" for c in edit.data["changes"])
        assert edit.data["interpretationId"] == predicted.data["interpretationId"]
        assert len({s.group_key for s in samples}) == 1
        assert db.get(Note, note_id).original_content == "Call the doctor tomorrow"


def test_replayed_capture_does_not_duplicate_dataset(system):
    client, factory, _, headers, _ = system
    identity = str(uuid4()); key = operation(headers)
    body = {"id": identity, "content": "dentist 16", "createdAt": "2026-10-27T09:00:00Z", "manual": True}
    for _ in range(2): assert client.post("/api/v1/notes", headers=key, json=body).status_code == 200
    with factory() as db: assert db.scalar(select(func.count()).select_from(LearningSample)) == 1


@pytest.mark.parametrize("transport", ["dedicated", "record_patch"])
def test_complete_is_action_not_a_semantic_training_label(system, transport):
    client, factory, _, headers, _ = system
    note_id = capture(client, headers); process_one(factory, Provider())
    record = client.get("/api/v1/notes/" + note_id, headers=headers).json()["records"][0]
    if transport == "dedicated":
        response = client.post("/api/v1/records/" + record["id"] + "/complete", headers=operation(headers), json={"expectedVersion": 1})
    else:
        response = client.patch("/api/v1/records/" + record["id"], headers=operation(headers), json=record | {"expectedVersion": 1, "state": "completed", "completedAt": "2026-10-28T09:00:00Z"})
    assert response.status_code == 200
    with factory() as db:
        sample = db.scalar(select(LearningSample).where(LearningSample.event_kind == "task_state"))
        assert sample.label_source == "user_action"


def test_failed_output_is_saved_without_becoming_a_target(system):
    client, factory, _, headers, _ = system
    capture(client, headers)
    class Invalid(Provider):
        attempts = [{"rawOutput": "{broken", "httpStatus": 200}]
        def extract(self, text, context): raise ValueError("Invalid synthetic JSON")
    process_one(factory, Invalid())
    with factory() as db:
        sample = db.scalar(select(LearningSample).where(LearningSample.event_kind == "provider_error"))
        assert sample.data["providerAttempts"][0]["rawOutput"] == "{broken"
        assert sample.data["errorClass"] == "ValueError" and sample.data["prediction"] is None


def test_contribution_is_explicit_revocable_and_owner_scoped(system, tmp_path):
    client, factory, owner, headers, enroll = system
    capture(client, headers)
    _, other = enroll()
    assert client.get("/api/v1/dataset/examples", headers=other).json()["examples"] == []
    assert client.get("/api/v1/dataset/summary", headers=headers).json()["contributionEnabled"] is False
    with factory() as db: assert export_rows(db, tmp_path / "off.jsonl", "contributed") == {}
    assert client.post("/api/v1/dataset/consent", headers=operation(headers), json={"enabled": True, "policyVersion": "wrong"}).status_code == 422
    for enabled, file in [(True, "yes.jsonl"), (False, "revoked.jsonl")]:
        assert client.post("/api/v1/dataset/consent", headers=operation(headers), json={"enabled": enabled, "policyVersion": "learning-v1"}).status_code == 200
        with factory() as db:
            counts = export_rows(db, tmp_path / file, "contributed")
            assert bool(counts) is enabled


@pytest.mark.parametrize("synthetic", [False, True])
def test_account_delete_erases_real_examples_but_archives_only_operator_synthetic_data(system, synthetic):
    client, factory, owner, headers, _ = system
    with factory() as db: db.get(User, owner).dataset_synthetic = synthetic; db.commit()
    capture(client, headers)
    response = client.request("DELETE", "/api/v1/auth/account", headers=headers, json={"ownerId": owner, "confirmation": "DELETE", "password": "synthetic test password only"})
    assert response.status_code == 200, response.text
    with factory() as db:
        rows = db.scalars(select(LearningSample)).all()
        assert len(rows) == int(synthetic)
        assert not rows or rows[0].user_id is None and rows[0].origin == "synthetic"
        assert db.get(User, owner) is None


def test_deletion_during_inference_does_not_resurrect_dataset(system):
    client, factory, owner, headers, _ = system
    capture(client, headers)
    class DeleteDuringCall(Provider):
        def extract(self, text, context):
            assert client.request("DELETE", "/api/v1/auth/account", headers=headers, json={"ownerId": owner, "confirmation": "DELETE", "password": "synthetic test password only"}).status_code == 200
            return super().extract(text, context)
    assert process_one(factory, DeleteDuringCall())
    with factory() as db: assert db.scalar(select(func.count()).select_from(LearningSample)) == 0


def test_dataset_pagination_and_cross_owner_summary(system):
    client, _, _, headers, enroll = system
    for _ in range(3): capture(client, headers)
    first = client.get("/api/v1/dataset/examples?limit=2", headers=headers).json()
    second = client.get("/api/v1/dataset/examples", params={"cursor": first["nextCursor"]}, headers=headers).json()
    assert len(first["examples"]) == 2 and len(second["examples"]) == 1
    assert not ({s["id"] for s in first["examples"]} & {s["id"] for s in second["examples"]})
    _, other = enroll()
    assert client.get("/api/v1/dataset/summary", headers=other).json()["total"] == 0


def test_import_is_idempotent_and_model_observation_does_not_replace_expectations(system, tmp_path):
    _, factory, _, _, _ = system
    path = Path(__file__).resolve().parents[2] / "docs/assets/benchmarks/2026-10-28-gpt-4.1-mini.json"
    with factory() as db:
        import_report(db, path); db.commit(); import_report(db, path); db.commit()
        assert db.scalar(select(func.count()).select_from(LearningSample)) == 43
        row = next(r for r in db.scalars(select(LearningSample)) if r.data["caseId"] == "ru-quantity")
        assert row.data["expected"]["doubts"] == [] and row.data["observation"]["passed"] is False
        counts = export_rows(db, tmp_path / "synthetic.jsonl", "synthetic")
        assert counts["synthetic_expected"] == 43
        assert all(json.loads(line)["split"] == "regression" for line in (tmp_path / "synthetic.jsonl").read_text(encoding="utf-8").splitlines())


def test_diverse_cases_include_dst_quantities_choices_and_unlabeled_challenges():
    cases = list(diverse_cases(3))
    assert len({c["id"] for c in cases}) > 3500
    assert {c["input"]["locale"] for c in cases} == {"en", "fr", "ru"}
    assert len({c["input"]["timezone"] for c in cases}) == 6
    assert any(c["family"] == "exact_event" and c["expected"]["requiresClarification"] for c in cases)
    assert any(c["input"].get("interpretationHints") for c in cases)
    assert any(c["label"] == "unlabeled" and c["expected"] is None for c in cases)
    assert all("plannedFor" not in c["expected"] and "startAt" not in c["expected"] for c in cases if c["family"] == "quantity")


def test_historical_backfill_does_not_invent_an_old_input_snapshot(system):
    client, factory, owner, headers, _ = system
    note_id = capture(client, headers)
    with factory() as db:
        note = db.get(Note, note_id); note.content = "Changed later"; note.version = 2
        db.add(History(user_id=owner, note_id=note_id, input_version=1, model_version="old", proposed_data={"records": []}, validation_result="review")); db.commit()
        backfill(db); db.commit()
        row = db.scalar(select(LearningSample).where(LearningSample.event_kind == "historical_interpretation"))
        assert row.data["inputAvailable"] is False and row.data["input"] is None


def test_bulk_generation_is_repeatable_without_rewriting_or_duplicate_variants(system):
    _, factory, _, _, _ = system
    expected = len({c["id"] for c in diverse_cases(1)})
    with factory() as db:
        generate(db, 1)
        assert db.scalar(select(func.count()).select_from(LearningSample)) == expected
        first = db.scalar(select(LearningSample).order_by(LearningSample.id))
        initial = (first.id, first.created_at, first.data)
        generate(db, 1)
        assert db.scalar(select(func.count()).select_from(LearningSample)) == expected
        db.expire_all(); retained = db.get(LearningSample, first.id)
        assert (retained.id, retained.created_at, retained.data) == initial
