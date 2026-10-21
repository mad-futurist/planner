import json
import httpx
import pytest
from app.ai.provider import OpenAICompatibleProvider, RetryableProviderError
from app.config import Settings


def provider_with_transport(monkeypatch, handler):
    original = httpx.Client
    monkeypatch.setattr(httpx, "Client", lambda **kwargs: original(transport=httpx.MockTransport(handler), **kwargs))
    return OpenAICompatibleProvider(Settings(llm_api_key="fixture-only", llm_model="fixture", llm_base_url="https://provider.invalid/v1", llm_response_format="json_object"))


def test_actual_adapter_classifies_transport_timeout_as_retryable(monkeypatch):
    def timeout(request): raise httpx.ReadTimeout("fixture", request=request)
    provider = provider_with_transport(monkeypatch, timeout)
    with pytest.raises(RetryableProviderError): provider.extract("Note", {})


def test_actual_adapter_classifies_rate_limit_as_retryable(monkeypatch):
    provider = provider_with_transport(monkeypatch, lambda request: httpx.Response(429, json={"error": "limited"}))
    with pytest.raises(RetryableProviderError): provider.extract("Note", {})


def test_actual_adapter_preserves_unicode_and_validates_response(monkeypatch):
    text = "ÐŸÐ¾Ð·Ð²Ð¾Ð½Ð¸Ñ‚ÑŒ Ð²Ñ€Ð°Ñ‡Ñƒ Ð·Ð°Ð²Ñ‚Ñ€Ð°"
    def response(request):
        body = json.loads(request.content)
        assert json.loads(body["messages"][1]["content"])["note"] == text
        return httpx.Response(200, json={"choices": [{"message": {"content": json.dumps({"records": [{"baseKind": "task", "title": "ÐŸÐ¾Ð·Ð²Ð¾Ð½Ð¸Ñ‚ÑŒ Ð²Ñ€Ð°Ñ‡Ñƒ"}]}, ensure_ascii=False)}}]})
    provider = provider_with_transport(monkeypatch, response)
    assert provider.extract(text, {}).records[0].title == "ÐŸÐ¾Ð·Ð²Ð¾Ð½Ð¸Ñ‚ÑŒ Ð²Ñ€Ð°Ñ‡Ñƒ"

def test_provider_request_budget_prevents_unbounded_context():
    from app.ai.provider import ProviderContextError
    provider = OpenAICompatibleProvider(Settings(llm_api_key="fixture", llm_model="fixture"))
    with pytest.raises(ProviderContextError):
        provider.extract("Saved", {"oversized": "x" * 140_000})


def test_strict_schema_closes_objects_and_requires_nullable_fields():
    from app.ai.structured import strict_schema
    from app.schemas import Extraction
    schema = strict_schema(Extraction)
    proposal = schema["$defs"]["Proposal"]
    assert proposal["additionalProperties"] is False
    assert set(proposal["required"]) == set(proposal["properties"])
    assert proposal["properties"]["fields"]["type"] == "array"
    assert proposal["properties"]["startAt"]["anyOf"][1] == {"type": "null"}


def test_strict_adapter_decodes_registered_primitives_and_temporal_evidence(monkeypatch):
    provider = provider_with_transport(monkeypatch, lambda req: httpx.Response(200, json={"choices": [{"finish_reason": "stop", "message": {"content": json.dumps({"records": [{"baseKind": "task", "title": "Read", "fields": [{"key": "minutes", "value": "20"}], "evidence": []}]})}}]}))
    provider.settings.llm_response_format = "json_schema"
    assert provider.extract("Read", {}).records[0].fields == {"minutes": 20}


@pytest.mark.parametrize("status,code,param,message,expected", [
    (400, "unsupported_response_format", None, "", 2),
    (400, None, "response_format", "json_schema is not supported", 2),
    (422, None, "response_format", "unsupported", 2),
    (400, "invalid_json_schema", "response_format", "Invalid schema", 1),
    (401, None, None, "Unauthorized", 1),
])
def test_auto_fallback_is_explicit_bounded_and_does_not_hide_other_errors(monkeypatch, status, code, param, message, expected):
    calls = []
    def handler(request):
        calls.append(json.loads(request.content))
        if len(calls) == 1:
            return httpx.Response(status, json={"error": {"code": code, "param": param, "message": message}})
        assert calls[-1]["response_format"]["type"] == "json_object"
        return httpx.Response(200, json={"choices": [{"message": {"content": '{"records": []}'}}]})
    provider = provider_with_transport(monkeypatch, handler)
    provider.settings.llm_response_format = "auto"
    if expected == 2: assert provider.extract("Note", {}).records == []
    else:
        with pytest.raises(httpx.HTTPStatusError): provider.extract("Note", {})
    assert len(calls) == expected


@pytest.mark.parametrize("message,finish", [({"refusal": "No", "content": None}, "stop"), ({"content": '{"records": []}'}, "length")])
def test_refused_or_truncated_output_is_never_accepted(monkeypatch, message, finish):
    provider = provider_with_transport(monkeypatch, lambda request: httpx.Response(200, json={"choices": [{"message": message, "finish_reason": finish}]}))
    with pytest.raises(ValueError): provider.extract("Saved", {})


def test_duplicate_strict_keys_are_rejected():
    from app.ai.structured import decode_strict
    with pytest.raises(ValueError): decode_strict({"records": [{"fields": [{"key": "x", "value": "1"}, {"key": "x", "value": "2"}], "evidence": []}]})
