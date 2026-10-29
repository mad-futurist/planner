import json
from pathlib import Path
import pytest
from app.domain.capture_preview import preview, utf16_length
from app.schemas import PreviewRequest

CORPUS = json.loads((Path(__file__).parents[1] / "app/domain/assets/interpretation_benchmark_v1.json").read_text(encoding="utf-8"))
CHOICES = json.loads((Path(__file__).parents[1] / "app/domain/assets/noncalendar_choices_v1.json").read_text(encoding="utf-8"))

@pytest.mark.parametrize("case", CHOICES["cases"], ids=lambda c: c["id"])
def test_shared_noncalendar_choices(case):
    value = preview(case["text"], CHOICES["createdAt"], CHOICES["timezone"])
    assert [r["baseKind"] for r in value["records"]] == case["kinds"]
    assert [r["kindChoices"] for r in value["records"]] == case["kindChoices"]
    assert [r["calendarEligible"] for r in value["records"]] == case["calendar"]
    assert sorted(set(d["reason"] for d in value["doubts"])) == sorted(case["doubts"])
    if "plannedFor" in case: assert value["records"][0]["plannedFor"] == case["plannedFor"]

@pytest.mark.parametrize("case", CORPUS["cases"], ids=lambda c: c["id"])
def test_shared_interpretation_corpus(case):
    value = preview(case["text"], CORPUS["createdAt"], CORPUS["timezone"], case.get("hints"))
    assert [r["baseKind"] for r in value["records"]] == case["kinds"]
    assert [r["calendarEligible"] for r in value["records"]] == case["calendar"]
    assert sorted(set(d["reason"] for d in value["doubts"])) == sorted(case["doubts"])
    for key in ("startAt", "plannedFor", "dueDate"):
        if key in case: assert value["records"][0][key] == case[key]

def test_clarifications_cannot_be_reused_after_text_changes_or_split_surrogates():
    for hints in [
        {"clarifications": [{"start": 8, "end": 10, "text": "16", "kind": "time", "time": "16:00"}]},
        {"clarifications": [{"start": 1, "end": 2, "text": "x", "kind": "literal"}]},
    ]:
        with pytest.raises(ValueError): PreviewRequest(content="🦷 dentist 17", createdAt=CORPUS["createdAt"], interpretationHints=hints)

def test_preview_endpoint_does_not_persist_or_require_ai_configuration():
    from fastapi.testclient import TestClient
    from app.main import create_app
    from app.persistence.database import make_database
    from app.persistence.models import Base
    engine, factory = make_database("sqlite:///:memory:")
    Base.metadata.create_all(engine)
    from app.security.identity import issue_invite
    from uuid import uuid4
    with factory() as db: invite = issue_invite(db)
    with TestClient(create_app(factory)) as client:
        token = client.post("/api/v1/auth/enroll", json={"code": invite, "deviceId": str(uuid4())}).json()["token"]
        body = {"content": "dentist 16", "createdAt": CORPUS["createdAt"]}
        assert client.post("/api/v1/preview", json=body).status_code == 401
        value = client.post("/api/v1/preview", headers={"Authorization": "Bearer " + token}, json=body)
        assert value.status_code == 200 and value.json()["doubts"][0]["text"] == "16"
        assert client.get("/api/v1/notes", headers={"Authorization": "Bearer " + token}).json()["notes"] == []
    engine.dispose()
