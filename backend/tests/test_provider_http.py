import json
import httpx
import pytest
from app.ai.provider import OpenAICompatibleProvider, RetryableProviderError
from app.config import Settings


def provider_with_transport(monkeypatch, handler):
    original = httpx.Client
    monkeypatch.setattr(httpx, "Client", lambda **kwargs: original(transport=httpx.MockTransport(handler), **kwargs))
    return OpenAICompatibleProvider(Settings(llm_api_key="fixture-only", llm_model="fixture", llm_base_url="https://provider.invalid/v1"))


def test_actual_adapter_classifies_transport_timeout_as_retryable(monkeypatch):
    def timeout(request): raise httpx.ReadTimeout("fixture", request=request)
    provider = provider_with_transport(monkeypatch, timeout)
    with pytest.raises(RetryableProviderError): provider.extract("Note", {})


def test_actual_adapter_classifies_rate_limit_as_retryable(monkeypatch):
    provider = provider_with_transport(monkeypatch, lambda request: httpx.Response(429, json={"error": "limited"}))
    with pytest.raises(RetryableProviderError): provider.extract("Note", {})


def test_actual_adapter_preserves_unicode_and_validates_response(monkeypatch):
    text = "Позвонить врачу завтра"
    def response(request):
        body = json.loads(request.content)
        assert json.loads(body["messages"][1]["content"])["note"] == text
        return httpx.Response(200, json={"choices": [{"message": {"content": json.dumps({"records": [{"baseKind": "task", "title": "Позвонить врачу"}]}, ensure_ascii=False)}}]})
    provider = provider_with_transport(monkeypatch, response)
    assert provider.extract(text, {}).records[0].title == "Позвонить врачу"

def test_provider_request_budget_prevents_unbounded_context():
    from app.ai.provider import ProviderContextError
    provider = OpenAICompatibleProvider(Settings(llm_api_key="fixture", llm_model="fixture"))
    with pytest.raises(ProviderContextError):
        provider.extract("Saved", {"oversized": "x" * 140_000})
