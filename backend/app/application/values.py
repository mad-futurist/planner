"""Plain application values. Validation of untrusted JSON belongs to interface/provider adapters."""
from __future__ import annotations
from dataclasses import dataclass, field, fields, replace
from datetime import date
from typing import Any, Literal, get_args, get_origin, get_type_hints
from uuid import UUID
from app.domain.time import now, uid
from copy import deepcopy

Kind = Literal["task", "event", "idea", "information"]

class Value:
    @classmethod
    def from_mapping(cls, value):
        if isinstance(value, cls):
            return value
        hints = get_type_hints(cls)
        def decode(kind, item):
            if item is None: return None
            origin, args = get_origin(kind), get_args(kind)
            if origin is list: return [decode(args[0], v) for v in item]
            if origin is dict: return {k: decode(args[1], v) for k, v in item.items()}
            if isinstance(kind, type) and issubclass(kind, Value): return kind.from_mapping(item)
            if kind is date and isinstance(item, str): return date.fromisoformat(item)
            if kind is UUID and isinstance(item, str): return UUID(item)
            if args:
                for candidate in args:
                    if candidate is date and isinstance(item, str): return date.fromisoformat(item)
                    if candidate is UUID and isinstance(item, str): return UUID(item)
            return deepcopy(item)
        result = cls(**{k: decode(hints.get(k), v) for k, v in value.items()})
        if isinstance(result, RecordDTO):
            from app.domain.records import validate_record
            validate_record(result)
        result._provided = frozenset(value)
        return result

    def to_dict(self, mode="python", exclude=None, exclude_unset=False):
        def encode(v):
            if isinstance(v, Value): return v.to_dict(mode=mode, exclude_unset=exclude_unset)
            if isinstance(v, dict): return {k: encode(item) for k, item in v.items()}
            if isinstance(v, (list, tuple)): return [encode(item) for item in v]
            if mode == "json" and isinstance(v, (UUID, date)): return str(v)
            return v
        provided = getattr(self, "_provided", None)
        return {f.name: encode(getattr(self, f.name)) for f in fields(self) if f.name not in (exclude or ()) and (not exclude_unset or provided is None or f.name in provided)}

    def replace(self, update):
        value = replace(self, **update)
        value._provided = getattr(self, "_provided", frozenset(f.name for f in fields(self))) | update.keys()
        return value


@dataclass(kw_only=True)
class RecordDTO(Value):
    id: UUID
    sourceNoteId: UUID
    baseKind: Kind = 'task'
    customTypeId: UUID | None = None
    typeSchemaVersion: int | None = None
    title: str
    fields: dict[str, Any] = field(default_factory=dict)
    startAt: str | None = None
    endAt: str | None = None
    dueAt: str | None = None
    dueDate: date | None = None
    plannedFor: date | None = None
    remindAt: str | None = None
    timezone: str = 'UTC'
    recurrenceRule: str | None = None
    state: Literal['pending', 'completed', 'archived'] = 'pending'
    completedAt: str | None = None
    pinned: bool = False
    deferredUntil: date | None = None
    createdAt: str = field(default_factory=now)
    updatedAt: str = field(default_factory=now)
    interpretationVersion: int = 0
    isUserConfirmed: bool = False
    version: int = 1

@dataclass(kw_only=True)
class CaptureHintDTO(Value):
    start: int
    end: int
    text: str
    kind: Literal['date', 'time', 'datetime', 'literal']
    date: str | None = None
    time: str | None = None

@dataclass(kw_only=True)
class InterpretationHintsDTO(Value):
    schemaVersion: Literal[1] = 1
    clarifications: list[CaptureHintDTO] = field(default_factory=list)

@dataclass(kw_only=True)
class PreviewRequest(Value):
    content: str
    createdAt: str
    timezone: str = 'UTC'
    locale: str = 'en'
    interpretationHints: InterpretationHintsDTO = field(default_factory=InterpretationHintsDTO)

@dataclass(kw_only=True)
class NoteDTO(Value):
    id: UUID
    content: str
    originalContent: str | None = None
    createdAt: str
    updatedAt: str | None = None
    source: Literal['APP', 'OVERLAY'] = 'APP'
    privacyMode: Literal['CLOUD_ALLOWED'] = 'CLOUD_ALLOWED'
    processingStatus: str = 'pending'
    syncStatus: str = 'pending'
    version: int = 1
    locale: str = 'en'
    timezone: str = 'UTC'
    manual: bool = False
    interpretationHints: InterpretationHintsDTO = field(default_factory=InterpretationHintsDTO)
    records: list[RecordDTO] = field(default_factory=list)

@dataclass(kw_only=True)
class RecordMutation(RecordDTO):
    expectedVersion: int

@dataclass(kw_only=True)
class CorrectionDTO(Value):
    noteId: UUID
    expectedNoteVersion: int
    records: list[RecordDTO]

@dataclass(kw_only=True)
class FieldSpec(Value):
    type: Literal['text', 'number', 'date', 'datetime', 'duration', 'boolean', 'enum', 'URL', 'reference', 'quantity']
    label: str = ''
    required: bool = False
    values: list[str] = field(default_factory=list)
    unit: str | None = None

@dataclass(kw_only=True)
class TypeDTO(Value):
    id: UUID
    schemaVersion: int = 1
    name: str
    baseKind: Kind
    fieldDefinitions: dict[str, FieldSpec] = field(default_factory=dict)
    displayConfiguration: dict = field(default_factory=dict)
    allowedCapabilities: list[Literal['complete', 'schedule', 'develop', 'archive']] = field(default_factory=list)
    enabled: bool = True

@dataclass(kw_only=True)
class Proposal(Value):
    baseKind: Kind
    customTypeId: UUID | None = None
    title: str
    fields: dict[str, Any] = field(default_factory=dict)
    startAt: str | None = None
    endAt: str | None = None
    dueAt: str | None = None
    dueDate: date | None = None
    plannedFor: date | None = None
    remindAt: str | None = None
    timezone: str = 'UTC'
    evidence: dict[str, str] = field(default_factory=dict)

@dataclass(kw_only=True)
class Extraction(Value):
    schemaVersion: Literal[1] = 1
    records: list[Proposal]
    ambiguities: list[str] = field(default_factory=list)

@dataclass(kw_only=True)
class Development(Value):
    steps: list[str]

@dataclass(kw_only=True)
class Enrollment(Value):
    code: str
    deviceId: UUID

@dataclass(kw_only=True)
class VersionAction(Value):
    expectedVersion: int
    completedAt: str | None = None

@dataclass(kw_only=True)
class NoteEdit(Value):
    expectedVersion: int
    content: str

@dataclass(kw_only=True)
class RuleDTO(Value):
    id: UUID
    description: str
    conditions: dict[str, str]
    action: dict[str, str]
    enabled: bool = True
    explicitlyApproved: bool = False

@dataclass(kw_only=True)
class Credentials(Value):
    email: str
    password: str
    deviceId: UUID

@dataclass(kw_only=True)
class Registration(Value):
    email: str
    password: str

@dataclass(kw_only=True)
class EmailRequest(Value):
    email: str

@dataclass(kw_only=True)
class LinkToken(Value):
    token: str

@dataclass(kw_only=True)
class ResetPassword(Value):
    password: str

@dataclass(kw_only=True)
class GoogleStart(Value):
    challenge: str
    password: str | None = None

@dataclass(kw_only=True)
class GoogleExchange(Value):
    state: str
    code: str
    verifier: str
    deviceId: UUID

@dataclass(kw_only=True)
class DeleteAccount(Value):
    ownerId: UUID
    confirmation: Literal['DELETE']
    password: str

@dataclass(kw_only=True)
class Consent(Value):
    enabled: bool
    policyVersion: Literal['learning-v1']
