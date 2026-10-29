"""Compatibility path for crypto, SMTP and Google protocol adapters."""
import base64
import hashlib
import hmac
import secrets
import smtplib
import ssl
from datetime import datetime, timedelta, timezone
from email.message import EmailMessage
from urllib.parse import urlencode
import httpx
import jwt
from argon2 import PasswordHasher
from argon2.exceptions import VerificationError, InvalidHashError
from fastapi import HTTPException
from app.config import settings
from app.security.identity import authenticate, digest

PASSWORDS = PasswordHasher(time_cost=3, memory_cost=65536, parallelism=2)
DUMMY_HASH = PASSWORDS.hash(secrets.token_urlsafe(32))
GOOGLE_ISSUER = 'https://accounts.google.com'
GOOGLE_AUTH = 'https://accounts.google.com/o/oauth2/v2/auth'
GOOGLE_TOKEN = 'https://oauth2.googleapis.com/token'
GOOGLE_KEYS = jwt.PyJWKClient('https://www.googleapis.com/oauth2/v3/certs', cache_keys=True, lifespan=300, timeout=10)

def valid_password(user, password):
    try: return PASSWORDS.verify(user.password_hash if user and user.password_hash else DUMMY_HASH, password) and user is not None and user.password_hash is not None
    except (VerificationError, InvalidHashError): return False

def send_account_email(email, purpose, token):
    if not settings.smtp_host or not settings.smtp_from: raise HTTPException(503, "Email service is not configured")
    path = "verify-email" if purpose == "verify" else "password-reset"
    link = settings.public_base_url.rstrip("/") + "/api/v1/auth/" + path + "?" + urlencode({"token": token})
    message = EmailMessage()
    message["From"] = settings.smtp_from; message["To"] = email
    message["Subject"] = "Confirm your Planner email" if purpose == "verify" else "Reset your Planner password"
    message.set_content(f"Open this link to {'confirm your email' if purpose == 'verify' else 'choose a new password'}:\n\n{link}\n\nIf you did not request this, ignore this email.")
    try:
        with smtplib.SMTP(settings.smtp_host, settings.smtp_port, timeout=10) as smtp:
            if settings.smtp_starttls: smtp.starttls(context=ssl.create_default_context())
            if settings.smtp_username: smtp.login(settings.smtp_username, settings.smtp_password)
            smtp.send_message(message)
    except (OSError, smtplib.SMTPException): raise HTTPException(503, "Email delivery is temporarily unavailable")

def google_claims(code):
    try:
        with httpx.Client(timeout=15, follow_redirects=False) as client:
            result = client.post(GOOGLE_TOKEN, data={"code": code, "client_id": settings.google_client_id, "client_secret": settings.google_client_secret, "redirect_uri": settings.public_base_url.rstrip("/") + "/api/v1/auth/google/callback", "grant_type": "authorization_code"})
            result.raise_for_status()
            token = result.json()["id_token"]
        key = GOOGLE_KEYS.get_signing_key_from_jwt(token)
        claims = jwt.decode(token, key.key, algorithms=["RS256"], audience=settings.google_client_id, issuer=[GOOGLE_ISSUER, "accounts.google.com"], options={"require": ["exp", "iat", "sub", "nonce", "email", "email_verified"]})
        if claims.get("email_verified") is not True: raise ValueError()
        return claims
    except Exception: raise HTTPException(401, "Google identity could not be verified")
