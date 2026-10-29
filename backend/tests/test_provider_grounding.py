"""Regressions from live inference, using synthetic transport only."""
import json
import httpx
import pytest
from app.ai.provider import OpenAICompatibleProvider
from app.config import Settings
from app.domain.validation import semantic_review

CAPTURED = "2026-10-27T09:00:00.000Z"
CONTEXT = {"capturedAt": CAPTURED, "timezone": "Europe/Paris", "locale": "en"}


def adapter(monkeypatch, handler, mode="json_schema"):
    original = httpx.Client
    monkeypatch.setattr(httpx, "Client", lambda **kw: original(transport=httpx.MockTransport(handler), **kw))
    return OpenAICompatibleProvider(Settings(llm_api_key="fixture-only", llm_model="fixture", llm_response_format=mode))


@pytest.mark.parametrize("text", ["Dentist tomorrow at 16:00", "Dentiste demain à 16h00", "Стоматолог завтра в 16:00"])
def test_winter_clock_is_code_resolved_and_evidence_is_field_named(monkeypatch, text):
    def respond(request):
        body = json.loads(request.content)
        proposal = body["response_format"]["json_schema"]["schema"]["$defs"]["Proposal"]["properties"]
        assert proposal["startAt"]["anyOf"][0]["enum"] == ["2026-10-28T15:00:00.000Z"]
        assert "note" not in proposal["evidence"]["items"]["properties"]["key"]["enum"]
        result = {"records": [{"baseKind": "event", "title": "Dentist", "startAt": "2026-10-28T15:00:00Z", "fields": [], "evidence": [{"key": "startAt", "value": text}]}]}
        return httpx.Response(200, json={"choices": [{"message": {"content": json.dumps(result)}}]})
    result = adapter(monkeypatch, respond).extract(text, CONTEXT)
    assert semantic_review(result, text, CAPTURED, CONTEXT["timezone"]) == []


def test_wrong_provider_clock_is_still_rejected_and_never_silently_rewritten(monkeypatch):
    text = "Dentist tomorrow at 16:00"
    def respond(request):
        result = {"records": [{"baseKind": "event", "title": "Dentist", "startAt": "2026-10-28T14:00:00Z", "fields": [], "evidence": [{"key": "startAt", "value": text}]}]}
        return httpx.Response(200, json={"choices": [{"message": {"content": json.dumps(result)}}]})
    result = adapter(monkeypatch, respond).extract(text, CONTEXT)
    assert result.records[0].startAt == "2026-10-28T14:00:00.000Z"
    assert "Time differs from original-text evidence" in semantic_review(result, text, CAPTURED, CONTEXT["timezone"])


@pytest.mark.parametrize("text", ["dentist 16", "Meeting next Friday at 14:00", "Meeting 2027-03-28 at 02:30"])
def test_ambiguous_dates_and_dst_gaps_do_not_become_grounded_instants(text):
    context = OpenAICompatibleProvider._grounded_context(text, CONTEXT)
    grounding = context["temporalGrounding"]
    assert grounding["doubts"]
    assert all(c["startAt"] is None for c in grounding["candidates"])


def test_explicit_utf16_choice_resolves_winter_instant_without_changing_original():
    text = "🦷 dentist 16"
    hints = {"schemaVersion": 1, "clarifications": [{"start": 11, "end": 13, "text": "16", "kind": "datetime", "date": "2026-11-16", "time": "09:00"}]}
    context = CONTEXT | {"interpretationHints": hints}
    enriched = OpenAICompatibleProvider._grounded_context(text, context)
    assert enriched["temporalGrounding"]["candidates"][0]["startAt"] == "2026-11-16T08:00:00.000Z"
    assert "temporalGrounding" not in context


def test_legacy_json_mode_keeps_object_field_values(monkeypatch):
    def respond(request):
        body = json.loads(request.content)
        assert body["response_format"]["type"] == "json_object"
        assert "fields and evidence use JSON objects" in body["messages"][0]["content"]
        return httpx.Response(200, json={"choices": [{"message": {"content": json.dumps({"records": [{"baseKind": "task", "title": "Read", "fields": {"minutes": 20}, "evidence": {}}]})}}]})
    assert adapter(monkeypatch, respond, "json_object").extract("Read", {}).records[0].fields == {"minutes": 20}
