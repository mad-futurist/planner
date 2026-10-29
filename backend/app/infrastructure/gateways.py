"""Adapters for replaceable provider and existing account/token protocols."""
from app.application.values import Extraction, Development
from app.domain.errors import ApplicationError
from fastapi import HTTPException
from app.security import identity

class ProviderAdapter:
    def __init__(self, adapter):
        self.adapter = adapter
        self.model = adapter.model

    def extract(self, content, context):
        result = self.adapter.extract(content, context)
        return Extraction.from_mapping(result.model_dump())

    def develop(self, content, context):
        result = self.adapter.develop(content, context)
        return Development.from_mapping(result.model_dump())

class AccountSecurityAdapter:
    @property
    def authorization_endpoint(self):
        from app.security import accounts
        return accounts.GOOGLE_AUTH
    @property
    def issuer(self):
        from app.security import accounts
        return accounts.GOOGLE_ISSUER
    def now(self):
        from app.security import accounts
        from datetime import timezone
        return accounts.datetime.now(timezone.utc)
    def token(self, length):
        import secrets
        return secrets.token_urlsafe(length)
    def compare(self, first, second):
        import secrets
        return secrets.compare_digest(first, second)
    def digest(self, value):
        return identity.digest(value)
    def challenge(self, verifier):
        import base64, hashlib
        return base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip("=")
    def hash_password(self, password):
        from app.security import accounts
        return accounts.PASSWORDS.hash(password)
    def needs_rehash(self, hashed):
        from app.security import accounts
        return accounts.PASSWORDS.check_needs_rehash(hashed)
    def valid_password(self, user, password):
        from app.security import accounts
        return accounts.valid_password(user, password)
    def send_account_email(self, email, purpose, token):
        from app.security import accounts
        try: return accounts.send_account_email(email, purpose, token)
        except HTTPException as error: raise ApplicationError(error.status_code, error.detail, error.headers) from error
    def google_claims(self, code):
        from app.security import accounts
        try: return accounts.google_claims(code)
        except HTTPException as error: raise ApplicationError(error.status_code, error.detail, error.headers) from error
