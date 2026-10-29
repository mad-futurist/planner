import base64
import hashlib
from urllib.parse import parse_qs, urlparse
from uuid import uuid4
from types import SimpleNamespace
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import func, select
from app.config import settings, Settings
from app.main import create_app
from app.persistence.database import make_database
from app.persistence.models import AccountToken, Base, Device, ExternalIdentity, LoginFlow, User
from app.security import accounts

@pytest.fixture
def system(tmp_path, monkeypatch):
    engine, factory = make_database("sqlite:///" + str(tmp_path / "accounts.db"))
    Base.metadata.create_all(engine)
    mail = []
    monkeypatch.setattr(settings, "smtp_host", "mail.test")
    monkeypatch.setattr(settings, "smtp_from", "noreply@example.com")
    monkeypatch.setattr(settings, "google_client_id", "client-fixture")
    monkeypatch.setattr(settings, "google_client_secret", "secret-fixture")
    monkeypatch.setattr(accounts, "send_account_email", lambda email, purpose, token: mail.append((email, purpose, token)))
    with TestClient(create_app(factory)) as client: yield client, factory, mail
    engine.dispose()

PASSWORD = "correct horse battery staple"
def register(client, email="person@example.com"):
    return client.post("/api/v1/auth/register", json={"email": email, "password": PASSWORD})
def login(client, email="person@example.com", password=PASSWORD):
    return client.post("/api/v1/auth/login", json={"email": email, "password": password, "deviceId": str(uuid4())})
def verified(system, email="person@example.com"):
    client, _, mail = system
    assert register(client, email).status_code == 202
    assert client.post("/api/v1/auth/verify-email", json={"token": mail[-1][2]}).status_code == 200
    value = login(client, email).json()
    return value, {"Authorization": "Bearer " + value["token"]}

def test_registration_verification_recovery_revoke_old_sessions(system):
    client, factory, mail = system
    assert register(client).status_code == 202
    assert login(client).status_code == 403
    token = mail[-1][2]
    with factory() as db:
        user = db.scalar(select(User)); stored = db.scalar(select(AccountToken))
        assert PASSWORD not in user.password_hash and user.password_hash.startswith("$argon2id$")
        assert stored.digest != token
    assert client.get("/api/v1/auth/verify-email", params={"token": token}).status_code == 200
    assert login(client).status_code == 403  # GET scanners do not confirm an account.
    assert client.post("/api/v1/auth/verify-email/confirm", data={"token": token}).status_code == 200
    assert client.post("/api/v1/auth/verify-email", json={"token": token}).status_code == 400
    old = login(client).json()
    assert client.post("/api/v1/auth/forgot-password", json={"email": "person@example.com"}).status_code == 202
    reset = mail[-1][2]
    new_password = "new strong password for account"
    assert client.post("/api/v1/auth/password-reset", data={"token": reset, "password": new_password}).status_code == 200
    assert client.get("/api/v1/config", headers={"Authorization": "Bearer " + old["token"]}).status_code == 401
    assert login(client).status_code == 401
    assert login(client, password=new_password).json()["userId"] == old["userId"]
    assert client.post("/api/v1/auth/reset-password", json={"token": reset, "password": PASSWORD}).status_code == 400

def test_duplicate_registration_does_not_replace_password_or_enumerate_accounts(system):
    client, factory, mail = system
    verified(system)
    message = register(client).json()
    assert message == register(client, "new@example.com").json()
    assert login(client).status_code == 200
    assert client.post("/api/v1/auth/forgot-password", json={"email": "missing@example.com"}).json() == client.post("/api/v1/auth/forgot-password", json={"email": "person@example.com"}).json()

def test_auth_failures_are_rate_limited_and_passwords_are_not_echoed(system, monkeypatch):
    from datetime import datetime, timezone
    class FixedClock(datetime):
        @classmethod
        def now(cls, tz=None):
            return datetime(2026, 10, 27, 9, 0, tzinfo=timezone.utc).astimezone(tz)
    monkeypatch.setattr(accounts, "datetime", FixedClock)
    client, _, _ = system
    weak = client.post("/api/v1/auth/register", json={"email": "person@example.com", "password": "secret-weak"})
    assert weak.status_code == 422 and "secret-weak" not in weak.text
    for _ in range(10): assert login(client, password="not the password").status_code == 401
    last = login(client, password="not the password")
    assert last.status_code == 429 and last.headers["retry-after"] == "60"

def google_flow(client, headers=None, password=None):
    verifier = "x" * 43
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip("=")
    value = client.post("/api/v1/auth/google/start", headers=headers, json={"challenge": challenge, "password": password})
    assert value.status_code == 200, value.text
    return value.json()["state"], verifier

def google_callback(client, factory, state, monkeypatch, email="google@example.com", subject="google-subject", wrong_nonce=False):
    with factory() as db: nonce = db.get(LoginFlow, accounts.digest(state)).nonce
    monkeypatch.setattr(accounts, "google_claims", lambda code: {"sub": subject, "email": email, "email_verified": True, "nonce": "wrong" if wrong_nonce else nonce})
    return client.get("/api/v1/auth/google/callback", params={"state": state, "code": "fixture-authorization-code"}, follow_redirects=False)

def test_google_handoff_is_nonce_bound_pkce_bound_single_use_and_stable_owner(system, monkeypatch):
    client, factory, _ = system
    state, verifier = google_flow(client)
    response = google_callback(client, factory, state, monkeypatch)
    assert response.status_code == 303
    query = parse_qs(urlparse(response.headers["location"]).query)
    body = {"state": state, "code": query["code"][0], "verifier": "wrong" * 10, "deviceId": str(uuid4())}
    assert client.post("/api/v1/auth/google/exchange", json=body).status_code == 401
    body["verifier"] = verifier
    result = client.post("/api/v1/auth/google/exchange", json=body)
    assert result.status_code == 200
    assert client.post("/api/v1/auth/google/exchange", json=body).status_code == 401
    assert google_callback(client, factory, state, monkeypatch).status_code == 400
    state2, verifier2 = google_flow(client)
    location = google_callback(client, factory, state2, monkeypatch).headers["location"]
    second = client.post("/api/v1/auth/google/exchange", json={"state": state2, "code": parse_qs(urlparse(location).query)["code"][0], "verifier": verifier2, "deviceId": str(uuid4())})
    assert second.json()["userId"] == result.json()["userId"]

def test_google_does_not_silently_merge_by_email_and_requires_explicit_password_link(system, monkeypatch):
    client, factory, _ = system
    owner, headers = verified(system)
    state, _ = google_flow(client)
    assert google_callback(client, factory, state, monkeypatch, email="person@example.com").status_code == 409
    assert client.post("/api/v1/auth/google/start", headers=headers, json={"challenge": "a"*43, "password": "wrong"}).status_code == 401
    state, verifier = google_flow(client, headers, PASSWORD)
    response = google_callback(client, factory, state, monkeypatch, email="person@example.com")
    code = parse_qs(urlparse(response.headers["location"]).query)["code"][0]
    linked = client.post("/api/v1/auth/google/exchange", json={"state": state, "code": code, "verifier": verifier, "deviceId": str(uuid4())})
    assert linked.json()["userId"] == owner["userId"]

def test_google_bad_nonce_and_expired_flows_are_rejected(system, monkeypatch):
    client, factory, _ = system
    state, _ = google_flow(client)
    assert google_callback(client, factory, state, monkeypatch, wrong_nonce=True).status_code == 401
    state2, _ = google_flow(client)
    with factory() as db: db.get(LoginFlow, accounts.digest(state2)).expires_at = "2000-01-01T00:00:00Z"; db.commit()
    assert google_callback(client, factory, state2, monkeypatch).status_code == 400

def test_account_deletion_is_owner_and_password_confirmed_and_isolated(system):
    client, factory, _ = system
    owner, headers = verified(system)
    other, other_headers = verified(system, "other@example.com")
    body = {"ownerId": other["userId"], "confirmation": "DELETE", "password": PASSWORD}
    assert client.request("DELETE", "/api/v1/auth/account", headers=headers, json=body).status_code == 403
    body["ownerId"] = owner["userId"]; body["password"] = "wrong"
    assert client.request("DELETE", "/api/v1/auth/account", headers=headers, json=body).status_code == 401
    body["password"] = PASSWORD
    note_id = str(uuid4())
    assert client.post("/api/v1/notes", headers=headers | {"Idempotency-Key": str(uuid4())}, json={"id": note_id, "content": "Synthetic account deletion fixture", "createdAt": "2026-10-27T09:00:00Z"}).status_code == 200
    assert client.request("DELETE", "/api/v1/auth/account", headers=headers, json=body).status_code == 200
    assert client.get("/api/v1/config", headers=headers).status_code == 401
    assert client.get("/api/v1/config", headers=other_headers).status_code == 200
    with factory() as db: assert db.scalar(select(func.count()).select_from(User)) == 1

def test_password_recovery_invalidates_pending_google_link_and_other_reset_tokens(system):
    client, factory, mail = system
    _, headers = verified(system)
    state, _ = google_flow(client, headers, PASSWORD)
    client.post("/api/v1/auth/forgot-password", json={"email": "person@example.com"})
    token = mail[-1][2]
    assert client.post("/api/v1/auth/reset-password", json={"token": token, "password": "a replacement long password"}).status_code == 200
    with factory() as db: assert db.get(LoginFlow, accounts.digest(state)) is None

@pytest.mark.parametrize("invalid", [None, "audience", "issuer", "expiry", "signature", "algorithm", "unverified-email"])
def test_google_jwt_signature_audience_issuer_expiry_and_email_are_verified(monkeypatch, invalid):
    import time
    import httpx
    import jwt
    from cryptography.hazmat.primitives.asymmetric import rsa
    private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    other = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    claims = {"iss": accounts.GOOGLE_ISSUER, "aud": "client-fixture", "sub": "subject-fixture", "iat": int(time.time()), "exp": int(time.time())+300, "nonce": "nonce", "email": "person@example.com", "email_verified": True}
    if invalid == "audience": claims["aud"] = "another-client"
    if invalid == "issuer": claims["iss"] = "https://attacker.invalid"
    if invalid == "expiry": claims["exp"] = int(time.time())-10
    if invalid == "unverified-email": claims["email_verified"] = False
    token = jwt.encode(claims, "fixture-secret-only-with-32-bytes-minimum" if invalid == "algorithm" else other if invalid == "signature" else private, algorithm="HS256" if invalid == "algorithm" else "RS256")
    class Client:
        def __init__(self, **kwargs): pass
        def __enter__(self): return self
        def __exit__(self, *args): pass
        def post(self, url, data): return httpx.Response(200, json={"id_token": token}, request=httpx.Request("POST", url))
    monkeypatch.setattr(accounts.httpx, "Client", Client)
    monkeypatch.setattr(accounts.GOOGLE_KEYS, "get_signing_key_from_jwt", lambda token: SimpleNamespace(key=private.public_key()))
    monkeypatch.setattr(settings, "google_client_id", "client-fixture")
    if invalid:
        from fastapi import HTTPException
        with pytest.raises(HTTPException): accounts.google_claims("fixture-code")
    else: assert accounts.google_claims("fixture-code")["sub"] == "subject-fixture"

def test_production_configuration_requires_tls_email_and_changed_secrets():
    with pytest.raises(ValueError): Settings(app_env="production")
    value = Settings(app_env="production", public_base_url="https://planner.example.com", smtp_host="smtp.example.com", smtp_from="noreply@example.com", auth_rate_limit_secret="x"*40, database_url="postgresql+psycopg://runtime:changed@db/planner")
    assert value.app_env == "production"
