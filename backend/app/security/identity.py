import hashlib
import secrets
from datetime import datetime, timedelta, timezone
from sqlalchemy import select
from fastapi import HTTPException
from app.persistence.models import Device, Invitation, User, uid


def digest(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


def issue_invite(session, user_id: str | None = None) -> str:
    user = session.get(User, user_id) if user_id else None
    if user_id and user is None:
        raise ValueError("Existing owner not found; no new identity was created")
    if user is None:
        user = User(id=uid()); session.add(user); session.flush()
    code = secrets.token_urlsafe(32)
    session.add(Invitation(digest=digest(code), user_id=user.id, expires_at=(datetime.now(timezone.utc) + timedelta(hours=24)).isoformat(timespec="milliseconds").replace("+00:00", "Z")))
    session.commit()
    return code


def enroll(session, code: str, device_id: str) -> dict:
    invite = session.scalar(select(Invitation).where(Invitation.digest == digest(code)).with_for_update())
    current = datetime.now(timezone.utc)
    if not invite or invite.used or datetime.fromisoformat(invite.expires_at.replace("Z", "+00:00")) < current:
        raise HTTPException(401, "Invalid or expired invitation")
    token = secrets.token_urlsafe(48)
    invite.used = True
    session.add(Device(token_digest=digest(token), user_id=invite.user_id, device_id=device_id, expires_at=(current + timedelta(days=90)).isoformat(timespec="milliseconds").replace("+00:00", "Z")))
    session.commit()
    return {"token": token, "userId": invite.user_id, "expiresInDays": 90}


def authenticate(session, authorization: str | None) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Device enrollment required")
    device = session.get(Device, digest(authorization[7:]))
    if not device or device.revoked or datetime.fromisoformat(device.expires_at.replace("Z", "+00:00")) <= datetime.now(timezone.utc):
        raise HTTPException(401, "Invalid or expired credential")
    return device.user_id


if __name__ == "__main__":
    import argparse
    from app.persistence.database import SessionFactory
    parser = argparse.ArgumentParser(description="Issue one-time device invitation (24h).")
    parser.add_argument("--user-id", help="Existing identity for another device")
    args = parser.parse_args()
    with SessionFactory() as session:
        print(issue_invite(session, args.user_id))
