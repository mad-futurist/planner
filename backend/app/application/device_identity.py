"""Device-session authorization/enrollment policy, independent of HTTP and SQL."""
from datetime import datetime, timedelta, timezone
from app.domain.entities import Device
from app.domain.errors import ApplicationError

class DeviceIdentityService:
    def __init__(self, security):
        self.security = security

    def authenticate(self, uow, authorization):
        if not authorization or not authorization.startswith("Bearer "):
            raise ApplicationError(401, "Device enrollment required")
        device = uow.get(Device, self.security.digest(authorization[7:]))
        if not device or device.revoked or datetime.fromisoformat(device.expires_at.replace("Z", "+00:00")) <= self.security.now():
            raise ApplicationError(401, "Invalid or expired credential")
        return device.user_id

    def enroll(self, uow, code, device_id):
        invite = uow.locked_invitation(self.security.digest(code))
        current = self.security.now()
        if not invite or invite.used or datetime.fromisoformat(invite.expires_at.replace("Z", "+00:00")) < current:
            raise ApplicationError(401, "Invalid or expired invitation")
        token = self.security.token(48)
        invite.used = True
        uow.add(Device(token_digest=self.security.digest(token), user_id=invite.user_id, device_id=device_id, expires_at=(current + timedelta(days=90)).isoformat(timespec="milliseconds").replace("+00:00", "Z")))
        uow.commit()
        return {"token": token, "userId": invite.user_id, "expiresInDays": 90}

    def revoke(self, uow, authorization):
        uow.get(Device, self.security.digest(authorization[7:])).revoked = True
        uow.commit()
