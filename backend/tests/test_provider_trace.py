import json
import httpx
import pytest
from app.ai.provider import OpenAICompatibleProvider
from app.config import Settings


@pytest.mark.parametrize("status", [200, 401])
def test_provider_trace_keeps_invalid_outputs_but_excludes_credentials_and_auth_error_bodies(monkeypatch, status):
    secret = "fixture-secret-not-for-export"
    original = httpx.Client
    response = {"choices": [{"message": {"content": "{broken"}}]} if status == 200 else {"error": {"message": "Invalid key " + secret}}
    monkeypatch.setattr(httpx, "Client", lambda **kw: original(transport=httpx.MockTransport(lambda _: httpx.Response(status, json=response)), **kw))
    provider = OpenAICompatibleProvider(Settings(llm_api_key=secret, llm_model="fixture"))
    with pytest.raises((ValueError, httpx.HTTPStatusError)):
        provider.extract("Synthetic note", {})
    trace = provider.attempts[0]
    assert trace["httpStatus"] == status
    assert len(trace["promptSha256"]) == len(trace["schemaSha256"]) == 64
    assert secret not in json.dumps(trace) and "Authorization" not in json.dumps(trace)
    if status == 200: assert trace["rawOutput"] == "{broken" and "rawResponse" in trace
    else: assert "rawResponse" not in trace
