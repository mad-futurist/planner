"""Persistence-independent business snapshots, mapped explicitly by infrastructure."""
from dataclasses import dataclass, field
from app.domain.time import now, uid

@dataclass(kw_only=True)
class User:
    id: str = field(default_factory=uid)
    email: str | None = None
    password_hash: str | None = None
    email_verified: bool = False
    dataset_consent: bool = False
    dataset_consent_at: str | None = None
    dataset_synthetic: bool = False

@dataclass(kw_only=True)
class Invitation:
    digest: str = None
    user_id: str = None
    expires_at: str = None
    used: bool = False

@dataclass(kw_only=True)
class Device:
    token_digest: str = None
    user_id: str = None
    device_id: str = None
    expires_at: str = None
    revoked: bool = False

@dataclass(kw_only=True)
class Note:
    id: str = None
    user_id: str = None
    content: str = None
    original_content: str = None
    source: str = None
    created_at: str = None
    received_at: str = field(default_factory=now)
    updated_at: str = None
    timezone: str = None
    locale: str = None
    version: int = 1
    processing_status: str = 'pending'
    user_corrected: bool = False
    manual: bool = False
    interpretation_hints: dict = field(default_factory=dict)

@dataclass(kw_only=True)
class Record:
    id: str = None
    user_id: str = None
    source_note_id: str = None
    base_kind: str = None
    title: str = None
    planned_for: str | None = None
    due_date: str | None = None
    start_at: str | None = None
    due_at: str | None = None
    version: int = 1
    confirmed: bool = False
    data: dict = None

@dataclass(kw_only=True)
class History:
    id: str = field(default_factory=uid)
    user_id: str = None
    note_id: str = None
    input_version: int = None
    schema_version: int = 1
    model_version: str = None
    proposed_data: dict = None
    validation_result: str = None
    corrected_by_user: bool = False
    created_at: str = field(default_factory=now)

@dataclass(kw_only=True)
class Correction:
    id: str = field(default_factory=uid)
    user_id: str = None
    source_note_id: str = None
    previous_interpretation: dict = None
    corrected_interpretation: dict = None
    created_at: str = field(default_factory=now)

@dataclass(kw_only=True)
class TypeDefinition:
    id: str = None
    schema_version: int = None
    user_id: str = None
    data: dict = None

@dataclass(kw_only=True)
class Rule:
    id: str = None
    user_id: str = None
    data: dict = None

@dataclass(kw_only=True)
class Job:
    id: str = field(default_factory=uid)
    user_id: str = None
    note_id: str = None
    input_version: int = None
    kind: str = 'extract'
    record_id: str = ''
    record_version: int = 0
    status: str = 'queued'
    attempts: int = 0
    next_attempt_at: str = field(default_factory=now)
    lease_until: str | None = None
    lease_token: str | None = None
    last_error: str | None = None
    result: dict | None = None

@dataclass(kw_only=True)
class Change:
    sequence: int = None
    user_id: str = None
    kind: str = None
    entity_id: str = None
    payload: dict = None

@dataclass(kw_only=True)
class Operation:
    user_id: str = None
    key: str = None
    digest: str = None
    result: dict = None

@dataclass(kw_only=True)
class ProviderRequest:
    id: str = field(default_factory=uid)
    user_id: str = None
    kind: str = None
    created_at: str = field(default_factory=now)

@dataclass(kw_only=True)
class AccountToken:
    digest: str = None
    user_id: str = None
    purpose: str = None
    expires_at: str = None
    used: bool = False

@dataclass(kw_only=True)
class ExternalIdentity:
    issuer: str = None
    subject: str = None
    user_id: str = None

@dataclass(kw_only=True)
class LoginFlow:
    state_digest: str = None
    nonce: str = None
    challenge: str = None
    expires_at: str = None
    link_user_id: str | None = None
    user_id: str | None = None
    handoff_digest: str | None = None
    used: bool = False
    callback_used: bool = False

@dataclass(kw_only=True)
class AuthRate:
    bucket: str = None
    attempts: int = 0
    expires_at: str = None

@dataclass(kw_only=True)
class ServiceHeartbeat:
    name: str = None
    seen_at: str = None

@dataclass(kw_only=True)
class LearningSample:
    id: str = field(default_factory=uid)
    user_id: str | None = None
    event_key: str = None
    group_key: str = None
    origin: str = None
    event_kind: str = None
    label_source: str = None
    schema_version: int = 1
    created_at: str = field(default_factory=now)
    data: dict = None
