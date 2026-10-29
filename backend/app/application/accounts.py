"""Public account workflows. Crypto, mail, Google and persistence are replaceable ports."""
import hashlib
import hmac
from datetime import timedelta, timezone
from urllib.parse import urlencode
from app.domain.entities import AccountToken, AuthRate, Device, ExternalIdentity, LoginFlow, User
from app.domain.time import now
from app.domain.errors import ApplicationError
from app.application.mutations import lock_user

class AccountService:
    def __init__(self, policy, security, identity):
        self.policy = policy
        self.security = security
        self.identity = identity

    def options(self):
        return {"emailEnabled": bool(self.policy.smtp_host and self.policy.smtp_from), "googleEnabled": bool(self.policy.google_client_id and self.policy.google_client_secret)}

    def delete_account(self, db, owner_id, password, authorization):
        owner = self.identity.authenticate(db, authorization)
        if str(owner_id) != owner:
            raise ApplicationError(403, "Account confirmation does not match")
        lock_user(db, owner)
        user = db.get(User, owner)
        if not self.security.valid_password(user, password):
            raise ApplicationError(401, "Confirm your password to delete the account. Google-only accounts can set a password through email recovery first.")
        db.delete_account_data(owner, user.dataset_synthetic)
        db.commit()
        return {"deleted": True}

    def expires(self, minutes):
        return (self.security.now() + timedelta(minutes=minutes)).isoformat(timespec='milliseconds').replace('+00:00', 'Z')

    def rate_limit(self, db, ip, action, limit=10):
        minute = self.security.now().strftime('%Y-%m-%dT%H:%M')
        key = hmac.new(self.policy.auth_rate_limit_secret.encode(), f'{ip}|{action}|{minute}'.encode(), hashlib.sha256).hexdigest()
        db.lock_bucket(key)
        row = db.get(AuthRate, key)
        if row is None:
            row = AuthRate(bucket=key, attempts=0, expires_at=self.expires(120))
            db.add(row)
        if row.attempts >= limit:
            raise ApplicationError(429, 'Too many attempts. Try again later.', headers={'Retry-After': '60'})
        row.attempts += 1
        db.commit()

    def issue_device(self, db, owner, device_id):
        token = self.security.token(48)
        db.add(Device(token_digest=self.security.digest(token), user_id=owner, device_id=str(device_id), expires_at=self.expires(90 * 24 * 60)))
        return {'token': token, 'userId': owner, 'expiresInDays': 90}

    def account_email_token(self, db, user, purpose):
        lock_user(db, user.id)
        threshold = self.expires((60 if purpose == 'reset' else 24 * 60) - 2)
        if db.recent_email_token(user.id, purpose, threshold):
            db.commit()
            return
        token = self.security.token(32)
        db.add(AccountToken(digest=self.security.digest(token), user_id=user.id, purpose=purpose, expires_at=self.expires(60 if purpose == 'reset' else 24 * 60)))
        db.commit()
        self.security.send_account_email(user.email, purpose, token)

    def consume_token(self, db, token, purpose):
        owner = db.token_owner(self.security.digest(token))
        if owner is None:
            raise ApplicationError(400, 'Invalid or expired link')
        lock_user(db, owner)
        row = db.locked_token(self.security.digest(token))
        if not row or row.used or row.purpose != purpose or (row.expires_at <= now()):
            raise ApplicationError(400, 'Invalid or expired link')
        row.used = True
        user = db.get(User, row.user_id)
        if user is None:
            raise ApplicationError(400, 'Invalid or expired link')
        return user

    def register(self, db, email, password):
        if not self.policy.smtp_host:
            raise ApplicationError(503, 'Email service is not configured')
        hashed = self.security.hash_password(password)
        user = db.user_by_email(email)
        if user is None:
            user = User(email=email, password_hash=hashed)
            db.add(user)
            db.flush()
            self.account_email_token(db, user, 'verify')
        elif not user.email_verified:
            self.account_email_token(db, user, 'verify')
        return {'message': 'If this address can be registered, a confirmation email has been sent.'}

    def login(self, db, email, password, device_id):
        user = db.user_by_email(email)
        if user:
            lock_user(db, user.id)
            user = db.locked_user(user.id)
        if not self.security.valid_password(user, password):
            raise ApplicationError(401, 'Invalid email or password')
        if not user.email_verified:
            raise ApplicationError(403, 'Confirm your email before signing in')
        if self.security.needs_rehash(user.password_hash):
            user.password_hash = self.security.hash_password(password)
        result = self.issue_device(db, user.id, device_id)
        db.commit()
        return result

    def verify_email(self, db, token):
        user = self.consume_token(db, token, 'verify')
        user.email_verified = True
        db.commit()
        return {'message': 'Email confirmed. You can sign in to Planner.'}

    def forgot(self, db, email):
        if not self.policy.smtp_host:
            raise ApplicationError(503, 'Email service is not configured')
        user = db.user_by_email(email)
        if user:
            self.account_email_token(db, user, 'reset')
        return {'message': 'If this account exists, a password reset email has been sent.'}

    def reset_password(self, db, token, password):
        user = self.consume_token(db, token, 'reset')
        lock_user(db, user.id)
        user.password_hash = self.security.hash_password(password)
        user.email_verified = True
        db.invalidate_sessions(user.id)
        db.commit()
        return {'message': 'Password changed. Sign in again on your devices.'}

    def google_start(self, db, challenge, authorization=None, password=None):
        if not self.policy.google_client_id or not self.policy.google_client_secret:
            raise ApplicationError(503, 'Google sign-in is not configured')
        owner = None
        if authorization:
            owner = self.identity.authenticate(db, authorization)
            lock_user(db, owner)
            db.expire_all()
            self.identity.authenticate(db, authorization)
            if not password or not self.security.valid_password(db.get(User, owner), password):
                raise ApplicationError(401, 'Confirm your current password to link Google')
        state, nonce = (self.security.token(32), self.security.token(32))
        db.add(LoginFlow(state_digest=self.security.digest(state), nonce=nonce, challenge=challenge, expires_at=self.expires(10), link_user_id=owner))
        db.commit()
        url = self.security.authorization_endpoint + '?' + urlencode({'client_id': self.policy.google_client_id, 'redirect_uri': self.policy.public_base_url.rstrip('/') + '/api/v1/auth/google/callback', 'response_type': 'code', 'scope': 'openid email', 'state': state, 'nonce': nonce, 'prompt': 'select_account'})
        return {'authorizationUrl': url, 'state': state, 'expiresInSeconds': 600}

    def google_callback(self, db, state, code):
        flow = db.locked_login_flow(self.security.digest(state))
        if not flow or flow.callback_used or flow.expires_at <= now():
            raise ApplicationError(400, 'Sign-in expired. Start again in Planner.')
        flow.callback_used = True
        db.commit()
        claims = self.security.google_claims(code)
        flow = db.get(LoginFlow, self.security.digest(state))
        if not flow or flow.expires_at <= now():
            raise ApplicationError(400, 'Sign-in expired')
        if not self.security.compare(claims.get('nonce', ''), flow.nonce):
            raise ApplicationError(401, 'Invalid sign-in nonce')
        email = str(claims['email']).strip().casefold()
        identity = db.get(ExternalIdentity, (self.security.issuer, claims['sub']))
        existing = db.user_by_email(email)
        owner_to_lock = flow.link_user_id or (identity.user_id if identity else None) or (existing.id if existing else None)
        if owner_to_lock:
            lock_user(db, owner_to_lock)
            if not db.user_exists(owner_to_lock):
                raise ApplicationError(400, 'Sign-in expired')
        flow = db.locked_login_flow(self.security.digest(state))
        if not flow or flow.expires_at <= now():
            raise ApplicationError(400, 'Sign-in expired')
        if identity:
            if flow.link_user_id and flow.link_user_id != identity.user_id:
                raise ApplicationError(409, 'This Google account is linked to another account')
            owner = identity.user_id
        else:
            if existing and flow.link_user_id != existing.id:
                raise ApplicationError(409, 'Sign in with your password, then explicitly link Google')
            if flow.link_user_id:
                owner = flow.link_user_id
            else:
                user = User(email=email, email_verified=True)
                db.add(user)
                db.flush()
                owner = user.id
            db.add(ExternalIdentity(issuer=self.security.issuer, subject=claims['sub'], user_id=owner))
        handoff = self.security.token(32)
        flow.user_id = owner
        flow.handoff_digest = self.security.digest(handoff)
        db.commit()
        return 'app.planner://auth?' + urlencode({'state': state, 'code': handoff})

    def google_exchange(self, db, state, handoff, verifier, device_id):
        owner = db.login_owner(self.security.digest(state))
        if owner is None:
            raise ApplicationError(401, 'Invalid or expired sign-in')
        lock_user(db, owner)
        flow = db.locked_login_flow(self.security.digest(state))
        challenge = self.security.challenge(verifier)
        if not flow or flow.used or (not flow.user_id) or (flow.expires_at <= now()) or (not self.security.compare(flow.handoff_digest or '', self.security.digest(handoff))) or (not self.security.compare(flow.challenge, challenge)):
            raise ApplicationError(401, 'Invalid or expired sign-in')
        flow.used = True
        result = self.issue_device(db, flow.user_id, device_id)
        db.commit()
        return result
