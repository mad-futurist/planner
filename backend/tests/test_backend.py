import json
import os
import subprocess
import sys
from datetime import date
from pathlib import Path
from uuid import uuid4
import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError
from sqlalchemy import func, select
from app.ai.provider import OpenAICompatibleProvider, ProviderUnavailable, RetryableProviderError
from app.config import Settings
from app.domain.validation import resolve_day
from app.jobs.worker import process_one
from app.main import create_app
from app.persistence.database import make_database
from app.persistence.models import Base, Correction, History, Job, Note, Record, now
from app.schemas import Extraction
from app.security.identity import issue_invite


@pytest.fixture
def system(tmp_path):
    engine, factory = make_database("sqlite:///" + str(tmp_path / "test.db"))
    Base.metadata.create_all(engine)
    client = TestClient(create_app(factory))
    def identity():
        with factory() as db: code = issue_invite(db)
        response = client.post("/api/v1/auth/enroll", json={"code": code, "deviceId": str(uuid4())})
        assert response.status_code == 200
        return {"Authorization": "Bearer " + response.json()["token"]}
    headers = identity()
    yield client, factory, headers, identity
    client.close(); engine.dispose()


def operation(headers):
    return headers | {"Idempotency-Key": str(uuid4())}


def capture(client, headers, content="Buy a lamp", **values):
    body = {"id": str(uuid4()), "content": content, "createdAt": "2026-10-13T09:00:00Z", "timezone": "Europe/Paris"} | values
    response = client.post("/api/v1/notes", headers=operation(headers), json=body)
    assert response.status_code == 200, response.text
    return body["id"]


class MockProvider:
    model = "test-fixture"
    def __init__(self, records=None, error=None, callback=None):
        self.records = records or [{"baseKind": "task", "title": "Buy lamp"}]
        self.error = error; self.callback = callback; self.context = None
    def extract(self, content, context):
        self.context = context
        if self.callback: self.callback()
        if self.error: raise self.error
        return Extraction.model_validate({"records": self.records})
    def develop(self, content, context):
        from app.schemas import Development
        return Development(steps=["Contact school", "Recruit tutors", "Schedule a pilot"])


def test_explicit_raw_only_capture_sync_does_not_enqueue_ai_or_create_events(system):
    client, factory, headers, _ = system
    note_id = capture(client, headers, content="dentist 16", manual=True, records=[])
    with factory() as db:
        assert db.get(Note, note_id).manual
        assert db.scalar(select(func.count()).select_from(Job)) == 0
        assert db.scalar(select(func.count()).select_from(Record)) == 0


def test_explicit_capture_clarification_is_preserved_and_constrains_ai(system):
    client, factory, headers, _ = system
    hints = {"schemaVersion": 1, "clarifications": [{"start": 8, "end": 10, "text": "16", "kind": "datetime", "date": "2026-10-13", "time": "16:00"}]}
    note_id = capture(client, headers, "dentist 16", interpretationHints=hints)
    provider = MockProvider([{"baseKind": "event", "title": "Dentist", "startAt": "2026-10-13T14:00:00Z", "evidence": {"startAt": "dentist 16"}}])
    assert process_one(factory, provider)
    note = client.get(f"/api/v1/notes/{note_id}", headers=headers).json()
    assert note["originalContent"] == "dentist 16" and note["interpretationHints"] == hints
    assert note["records"][0]["startAt"] == "2026-10-13T14:00:00.000Z"
    assert provider.context["interpretationHints"] == hints
    wrong_id = capture(client, headers, "dentist 16", interpretationHints=hints)
    assert process_one(factory, MockProvider([{"baseKind": "event", "title": "Dentist", "startAt": "2026-10-13T13:00:00Z", "evidence": {"startAt": "dentist 16"}}]))
    assert client.get(f"/api/v1/notes/{wrong_id}", headers=headers).json()["records"] == []


def test_ai_preview_has_persistent_owner_budget_and_does_not_save_notes(system, monkeypatch):
    from app.config import settings
    from app.persistence.models import ProviderRequest
    monkeypatch.setattr(settings, "llm_api_key", "test-fixture-only")
    monkeypatch.setattr(settings, "llm_model", "test-fixture")
    monkeypatch.setattr(settings, "ai_daily_request_limit", 1)
    monkeypatch.setattr(OpenAICompatibleProvider, "extract", lambda self, content, context: Extraction.model_validate({"records": [{"baseKind": "task", "title": "Buy milk"}]}))
    client, factory, headers, another = system
    body = {"content": "Buy milk", "createdAt": "2026-10-27T09:00:00Z"}
    first = client.post("/api/v1/preview/ai", headers=headers, json=body)
    assert first.status_code == 200 and first.json()["previewOnly"]
    assert client.post("/api/v1/preview/ai", headers=headers, json=body).status_code == 429
    assert client.get("/api/v1/notes", headers=headers).json()["notes"] == []
    assert client.get("/api/v1/diagnostics", headers=headers).json()["aiRequestsToday"] == 1
    assert client.get("/api/v1/diagnostics", headers=another()).json()["aiRequestsToday"] == 0
    with factory() as db: assert db.scalar(select(func.count()).select_from(ProviderRequest)) == 1


def test_daily_budget_defers_jobs_without_losing_notes_or_consuming_attempts(system, monkeypatch):
    import app.jobs.worker as worker
    from app.config import settings
    monkeypatch.setattr(settings, "llm_api_key", "fixture")
    monkeypatch.setattr(settings, "llm_model", "fixture")
    monkeypatch.setattr(settings, "ai_daily_request_limit", 1)
    monkeypatch.setattr(worker, "OpenAICompatibleProvider", lambda settings: MockProvider())
    client, factory, headers, _ = system
    capture(client, headers, "Buy lamp")
    assert worker.process_one(factory)
    second = capture(client, headers, "Buy bread")
    assert worker.process_one(factory)
    with factory() as db:
        job = db.scalar(select(Job).where(Job.note_id == second))
        assert job.status == "queued" and job.last_error == "provider_daily_limit" and job.attempts == 0
        assert db.get(Note, second).content == "Buy bread"

def test_readiness_requires_recent_worker_heartbeat(system):
    from app.persistence.models import ServiceHeartbeat
    client, factory, _, _ = system
    assert client.get("/health").status_code == 200
    assert client.get("/ready").status_code == 503
    with factory() as db: db.add(ServiceHeartbeat(name="worker:fixture", seen_at=now())); db.commit()
    assert client.get("/ready").status_code == 200
    with factory() as db: db.get(ServiceHeartbeat, "worker:fixture").seen_at = "2000-01-01T00:00:00Z"; db.commit()
    assert client.get("/ready").status_code == 503


def test_health_and_openapi(system):
    client, _, headers, _ = system
    assert client.get("/health").json()["status"] == "ok"
    assert "/api/v1/notes" in client.get("/openapi.json").json()["paths"]
    assert client.get("/api/v1/config", headers=headers).status_code == 200


def test_enrollment_is_single_use_and_authorization_is_real(system):
    client, factory, headers, another = system
    with factory() as db: code = issue_invite(db)
    payload = {"code": code, "deviceId": str(uuid4())}
    assert client.post("/api/v1/auth/enroll", json=payload).status_code == 200
    assert client.post("/api/v1/auth/enroll", json=payload).status_code == 401
    note_id = capture(client, headers)
    assert client.get("/api/v1/notes/" + note_id).status_code == 401
    assert client.get("/api/v1/notes/" + note_id, headers=another()).status_code == 404
    assert client.post("/api/v1/auth/revoke", headers=headers).status_code == 200
    assert client.get("/api/v1/notes", headers=headers).status_code == 401


def test_ingestion_is_idempotent_and_conflicting_key_rejected(system):
    client, factory, headers, _ = system
    op = operation(headers)
    body = {"id": str(uuid4()), "content": "Capture", "createdAt": now()}
    first = client.post("/api/v1/notes", headers=op, json=body)
    assert first.status_code == 200
    assert client.post("/api/v1/notes", headers=op, json=body).json() == first.json()
    assert client.post("/api/v1/notes", headers=op | {"Idempotency-Key": op["Idempotency-Key"].upper()}, json=body).json() == first.json()
    assert client.post("/api/v1/notes", headers=op, json=body | {"content": "Different"}).status_code == 409
    with factory() as db:
        assert db.scalar(select(func.count()).select_from(Note)) == 1
        assert db.scalar(select(func.count()).select_from(Job)) == 1


def test_private_and_oversized_input_rejected(system):
    client, _, headers, _ = system
    body = {"id": str(uuid4()), "content": "Private", "createdAt": now(), "privacyMode": "LOCAL_ONLY"}
    assert client.post("/api/v1/notes", headers=operation(headers), json=body).status_code == 422
    assert client.post("/api/v1/notes", headers=operation(headers), content=b"x" * 128_001).status_code == 413


def test_multi_item_extraction_resolves_capture_date_and_type_separation(system):
    client, factory, headers, _ = system
    note_id = capture(client, headers, "Meet Nicolas Thursday 14:00 and prepare questions by Wednesday")
    provider = MockProvider([
        {"baseKind": "event", "title": "Meet Nicolas", "startAt": "2026-10-15T12:00:00Z", "timezone": "Europe/Paris", "evidence": {"startAt": "Thursday 14:00"}},
        {"baseKind": "task", "title": "Prepare questions", "dueDate": "2026-10-14", "evidence": {"dueDate": "Wednesday"}},
    ])
    assert process_one(factory, provider)
    response = client.get("/api/v1/notes/" + note_id, headers=headers).json()
    assert response["processingStatus"] == "organized"
    assert len(response["records"]) == 2
    task = next(r for r in response["records"] if r["baseKind"] == "task")
    assert task["dueDate"] == "2026-10-14" and task["startAt"] is None
    assert provider.context["capturedAt"].startswith("2026-10-13")


@pytest.mark.parametrize("expression,days", [("tomorrow", 1), ("demain", 1), ("завтра", 1), ("vendredi", 3), ("пятницу", 3)])
def test_multilingual_date_resolution(expression, days):
    assert (resolve_day(expression, date(2026, 10, 13)) - date(2026, 10, 13)).days == days


def test_ambiguous_or_invented_times_are_reviewed_without_actions(system):
    client, factory, headers, _ = system
    note_id = capture(client, headers, "Call doctor tomorrow")
    process_one(factory, MockProvider([{"baseKind": "event", "title": "Doctor", "startAt": "2026-10-14T09:00:00Z", "evidence": {"startAt": "tomorrow"}}]))
    data = client.get("/api/v1/notes/" + note_id, headers=headers).json()
    assert data["processingStatus"] == "review"
    assert data["records"] == []
    assert client.get(f"/api/v1/notes/{note_id}/interpretation", headers=headers).json()["interpretation"]["validationResult"] == "review"


def test_invalid_output_and_timeout_are_durable_failures(system):
    client, factory, headers, _ = system
    capture(client, headers)
    process_one(factory, MockProvider(error=ValueError("bad JSON")))
    with factory() as db: assert db.scalar(select(Job)).status == "failed"
    note_id = capture(client, headers, "Second note")
    process_one(factory, MockProvider(error=RetryableProviderError()), max_attempts=2)
    with factory() as db:
        job = db.scalar(select(Job).where(Job.note_id == note_id))
        assert job.status == "queued" and job.attempts == 1 and job.next_attempt_at > now()
        job.next_attempt_at = now(); db.commit()
    process_one(factory, MockProvider(error=RetryableProviderError()), max_attempts=2)
    with factory() as db:
        job = db.scalar(select(Job).where(Job.note_id == note_id))
        assert job.status == "failed" and job.attempts == 2


def test_missing_provider_never_fabricates_a_result(system):
    client, factory, headers, _ = system
    note_id = capture(client, headers)
    process_one(factory, OpenAICompatibleProvider(Settings(llm_api_key="", llm_model="")))
    data = client.get("/api/v1/notes/" + note_id, headers=headers).json()
    assert data["processingStatus"] == "unconfigured" and not data["records"]


def test_user_correction_during_slow_ai_is_preserved(system):
    client, factory, headers, _ = system
    note_id = capture(client, headers, "Start a project")
    record_id = str(uuid4())
    def correct():
        body = {"noteId": note_id, "expectedNoteVersion": 1, "records": [{"id": record_id, "sourceNoteId": note_id, "baseKind": "idea", "title": "My corrected idea"}]}
        assert client.post(f"/api/v1/notes/{note_id}/corrections", headers=operation(headers), json=body).status_code == 200
    process_one(factory, MockProvider(callback=correct))
    data = client.get("/api/v1/notes/" + note_id, headers=headers).json()
    assert [r["title"] for r in data["records"]] == ["My corrected idea"]
    with factory() as db:
        assert db.scalar(select(Correction)) is not None
        assert db.scalar(select(History)).validation_result == "review"


def test_custom_type_added_without_breaking_existing_records(system):
    client, factory, headers, _ = system
    first_id = capture(client, headers); process_one(factory, MockProvider())
    type_id = str(uuid4())
    definition = {"id": type_id, "name": "Workout", "baseKind": "task", "fieldDefinitions": {"minutes": {"type": "duration", "required": True}}, "allowedCapabilities": ["complete"]}
    assert client.post("/api/v1/types", headers=operation(headers), json=definition).status_code == 200
    second_id = capture(client, headers, "Workout 30 minutes")
    process_one(factory, MockProvider([{"baseKind": "task", "customTypeId": type_id, "title": "Workout", "fields": {"minutes": 30}}]))
    data = client.get("/api/v1/notes/" + second_id, headers=headers).json()
    assert data["records"][0]["typeSchemaVersion"] == 1
    assert len(client.get("/api/v1/notes/" + first_id, headers=headers).json()["records"]) == 1
    assert client.patch("/api/v1/types/" + type_id, headers=operation(headers), json=definition | {"schemaVersion": 2, "enabled": False}).status_code == 200
    assert len(client.get("/api/v1/types", headers=headers).json()["types"]) == 2


def test_completion_undo_conflicts_and_incremental_changes(system):
    client, factory, headers, _ = system
    note_id = capture(client, headers); process_one(factory, MockProvider())
    record = client.get("/api/v1/notes/" + note_id, headers=headers).json()["records"][0]
    path = "/api/v1/records/" + record["id"]
    complete = client.post(path + "/complete", headers=operation(headers), json={"expectedVersion": 1})
    assert complete.status_code == 200 and complete.json()["completedAt"]
    assert client.post(path + "/undo", headers=operation(headers), json={"expectedVersion": 1}).status_code == 409
    undo = client.post(path + "/undo", headers=operation(headers), json={"expectedVersion": 2})
    assert undo.status_code == 200 and undo.json()["completedAt"] is None
    first = client.get("/api/v1/changes?limit=1", headers=headers).json()
    second = client.get("/api/v1/changes", params={"cursor": first["nextCursor"]}, headers=headers).json()
    assert second["changes"][0]["sequence"] > first["changes"][0]["sequence"]


def test_expired_job_lease_can_be_reclaimed(system):
    client, factory, headers, _ = system
    capture(client, headers)
    with factory() as db:
        job = db.scalar(select(Job)); job.status = "running"; job.lease_until = "2000-01-01T00:00:00Z"; job.lease_token = str(uuid4()); db.commit()
    assert process_one(factory, MockProvider())
    with factory() as db: assert db.scalar(select(Job)).status == "complete"


def test_schema_rejects_unbounded_or_unknown_output():
    with pytest.raises(ValidationError): Extraction.model_validate({"records": [{"baseKind": "unknown", "title": "Bad"}]})
    with pytest.raises(ValidationError): Extraction.model_validate({"records": [], "confidence": 0.99})


def test_development_after_idea_edit_gets_a_new_durable_job(system):
    client, factory, headers, _ = system
    note_id = capture(client, headers, "Volunteer idea")
    process_one(factory, MockProvider([{"baseKind": "idea", "title": "Volunteer program"}]))
    record = client.get("/api/v1/notes/" + note_id, headers=headers).json()["records"][0]
    first = client.post(f"/api/v1/ideas/{record['id']}/develop", headers=operation(headers), json={}).json()
    process_one(factory, MockProvider())
    updated = client.patch(f"/api/v1/records/{record['id']}", headers=operation(headers), json=record | {"expectedVersion": 1, "title": "Edited idea"})
    assert updated.status_code == 200
    second = client.post(f"/api/v1/ideas/{record['id']}/develop", headers=operation(headers), json={}).json()
    assert second["id"] != first["id"] and second["recordVersion"] == 2
    assert client.get("/api/v1/jobs/" + first["id"], headers=headers).json()["result"]["steps"]


def test_alembic_initial_upgrade_is_repeatable_and_preserves_data(tmp_path):
    url = "sqlite:///" + str(tmp_path / "migrated.db")
    env = os.environ | {"DATABASE_URL": url}
    command = [sys.executable, "-m", "alembic", "upgrade", "head"]
    root = Path(__file__).resolve().parents[1]
    assert subprocess.run(command, cwd=root, env=env, capture_output=True).returncode == 0
    engine, factory = make_database(url)
    with factory() as db: code = issue_invite(db)
    assert subprocess.run(command, cwd=root, env=env, capture_output=True).returncode == 0
    with factory() as db:
        from app.persistence.models import Invitation
        assert db.scalar(select(func.count()).select_from(Invitation)) == 1
    engine.dispose()

def test_ingestion_rate_limit_uses_server_time_for_offline_notes(system):
    client, _, headers, _ = system
    for _ in range(60):
        capture(client, headers, createdAt="2000-01-01T00:00:00Z")
    response = client.post("/api/v1/notes", headers=operation(headers), json={"id": str(uuid4()), "content": "Backdated", "createdAt": "2000-01-01T00:00:00Z"})
    assert response.status_code == 429
    assert response.headers["Retry-After"] == "60"

def test_extraction_cannot_change_an_explicit_clock_time(system):
    client, factory, headers, _ = system
    note_id = capture(client, headers, "Meeting tomorrow at 14:00")
    provider = MockProvider([{"baseKind": "event", "title": "Meeting", "startAt": "2026-10-14T13:00:00Z", "evidence": {"startAt": "tomorrow at 14:00"}}])
    process_one(factory, provider)
    result = client.get(f"/api/v1/notes/{note_id}/interpretation", headers=headers).json()
    assert result["processingStatus"] == "review"
    with factory() as db:
        assert db.scalar(select(func.count()).select_from(Record)) == 0
        history = db.scalar(select(History))
        assert history.proposed_data["riskLevel"] == "review_required"

def test_invitation_does_not_silently_create_owner_after_a_typo(system):
    _, factory, _, _ = system
    with factory() as db:
        with pytest.raises(ValueError): issue_invite(db, str(uuid4()))


def test_only_approved_owner_rules_affect_interpretation(system):
    client, factory, headers, other = system
    rule = {"id": str(uuid4()), "description": "Pilot ideas", "conditions": {"contains": "pilot"}, "action": {"baseKind": "idea"}, "explicitlyApproved": False}
    assert client.post("/api/v1/rules", headers=operation(headers), json=rule).status_code == 200
    first = capture(client, headers, "Pilot project")
    provider = MockProvider([{ "baseKind": "information", "title": "Pilot project" }])
    process_one(factory, provider)
    assert client.get(f"/api/v1/notes/{first}", headers=headers).json()["records"][0]["baseKind"] == "information"
    rule["explicitlyApproved"] = True
    assert client.post("/api/v1/rules", headers=operation(headers), json=rule).status_code == 200
    second = capture(client, headers, "Pilot plan")
    process_one(factory, provider)
    assert client.get(f"/api/v1/notes/{second}", headers=headers).json()["records"][0]["baseKind"] == "idea"
    assert len(provider.context["rules"]) == 1
    assert client.post("/api/v1/rules", headers=operation(other()), json=rule).status_code == 409
    rule["action"] = {"baseKind": "event"}
    assert client.post("/api/v1/rules", headers=operation(headers), json=rule).status_code == 200
    third = capture(client, headers, "Pilot session")
    process_one(factory, provider)
    assert client.get(f"/api/v1/notes/{third}", headers=headers).json()["processingStatus"] == "review"

def test_manual_capture_change_feed_delivers_parent_before_record(system):
    client, _, headers, _ = system
    note_id, record_id = str(uuid4()), str(uuid4())
    capture(client, headers, "Manual", id=note_id, manual=True, records=[{"id": record_id, "sourceNoteId": note_id, "title": "Manual task"}])
    first = client.get("/api/v1/changes?limit=1", headers=headers).json()
    assert first["changes"][0]["kind"] == "note"
    next_page = client.get("/api/v1/changes", params={"cursor": first["nextCursor"]}, headers=headers).json()
    assert next_page["changes"][0]["kind"] == "record"


def test_irrelevant_correction_is_not_a_universal_context_example(system):
    client, factory, headers, _ = system
    note_id = capture(client, headers, "Mountain birds")
    record_id = str(uuid4())
    correction = {"noteId": note_id, "expectedNoteVersion": 1, "records": [{"id": record_id, "sourceNoteId": note_id, "title": "Mountain birds", "baseKind": "idea"}]}
    assert client.post(f"/api/v1/notes/{note_id}/corrections", headers=operation(headers), json=correction).status_code == 200
    capture(client, headers, "Buy groceries")
    process_one(factory, MockProvider())
    provider = MockProvider()
    process_one(factory, provider)
    assert provider.context["corrections"] == []


@pytest.mark.parametrize("text,evidence", [("Meet Thursday at 14", "Thursday at 14"), ("Rendez-vous jeudi à 14", "jeudi à 14"), ("Встреча в четверг в 14", "четверг в 14")])
def test_bare_hours_with_explicit_time_intro_are_valid(text, evidence):
    from app.domain.validation import semantic_review
    result = Extraction.model_validate({"records": [{"baseKind": "event", "title": "Meeting", "startAt": "2026-10-15T12:00:00Z", "evidence": {"startAt": evidence}}]})
    assert semantic_review(result, text, "2026-10-13T09:00:00Z", "Europe/Paris") == []


def test_bare_quantity_does_not_validate_an_invented_hour():
    from app.domain.validation import semantic_review
    result = Extraction.model_validate({"records": [{"baseKind": "task", "title": "Read", "dueAt": "2026-10-14T12:00:00Z", "evidence": {"dueAt": "Read 14 pages tomorrow"}}]})
    assert "A precise time was not supplied" in semantic_review(result, "Read 14 pages tomorrow", "2026-10-13T09:00:00Z", "Europe/Paris")


@pytest.mark.parametrize("text", ["Due at 2026-10-22", "Meet Thursday at 14:3", "Meet Thursday at 14.30"])
def test_partial_year_or_malformed_clock_is_not_an_explicit_hour(text):
    from app.domain.validation import semantic_review
    result = Extraction.model_validate({"records": [{"baseKind": "task", "title": "Saved", "dueAt": "2026-10-22T20:00:00Z", "evidence": {"dueAt": text}}]})
    assert "A precise time was not supplied" in semantic_review(result, text, "2026-10-21T09:00:00Z", "UTC")
