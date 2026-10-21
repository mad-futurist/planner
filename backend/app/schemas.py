from datetime import date, datetime, timezone
from typing import Any, Literal
from uuid import UUID
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator
from app.persistence.models import now, uid

Kind = Literal["task", "event", "idea", "information"]


def instant(value: str | None) -> str | None:
    if value is None:
        return None
    dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if dt.tzinfo is None:
        raise ValueError("An explicit offset is required")
    return dt.astimezone(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


class DTO(BaseModel):
    model_config = ConfigDict(extra="forbid")


class RecordDTO(DTO):
    id: UUID
    sourceNoteId: UUID
    baseKind: Kind = "task"
    customTypeId: UUID | None = None
    typeSchemaVersion: int | None = Field(default=None, ge=1)
    title: str = Field(min_length=1, max_length=1000)
    fields: dict[str, Any] = Field(default_factory=dict, max_length=32)
    startAt: str | None = None
    endAt: str | None = None
    dueAt: str | None = None
    dueDate: date | None = None
    plannedFor: date | None = None
    remindAt: str | None = None
    timezone: str = "UTC"
    recurrenceRule: str | None = Field(default=None, max_length=500)
    state: Literal["pending", "completed", "archived"] = "pending"
    completedAt: str | None = None
    pinned: bool = False
    deferredUntil: date | None = None
    createdAt: str = Field(default_factory=now)
    updatedAt: str = Field(default_factory=now)
    interpretationVersion: int = Field(default=0, ge=0)
    isUserConfirmed: bool = False
    version: int = Field(default=1, ge=1)

    _instants = field_validator("startAt", "endAt", "dueAt", "remindAt", "completedAt", "createdAt", "updatedAt")(instant)

    @field_validator("timezone")
    @classmethod
    def valid_zone(cls, value):
        try:
            ZoneInfo(value)
        except (ZoneInfoNotFoundError, ValueError) as error:
            raise ValueError("Unknown timezone") from error
        return value

    @model_validator(mode="after")
    def semantics(self):
        if self.startAt and self.endAt and self.endAt <= self.startAt:
            raise ValueError("End must follow start")
        if self.dueAt and self.dueDate:
            raise ValueError("Choose an instant or date-only deadline")
        if self.state == "completed" and (self.baseKind != "task" or not self.completedAt):
            raise ValueError("Completed tasks need a completion timestamp")
        if self.state != "completed" and self.completedAt:
            raise ValueError("Only completed tasks have completion timestamps")
        if self.customTypeId and not self.typeSchemaVersion:
            raise ValueError("Custom types require a pinned schema version")
        return self


class NoteDTO(DTO):
    id: UUID
    content: str = Field(min_length=1, max_length=20_000)
    originalContent: str | None = Field(default=None, max_length=20_000)
    createdAt: str
    updatedAt: str | None = None
    source: Literal["APP", "OVERLAY"] = "APP"
    privacyMode: Literal["CLOUD_ALLOWED"] = "CLOUD_ALLOWED"
    processingStatus: str = "pending"
    syncStatus: str = "pending"
    version: int = Field(default=1, ge=1)
    locale: str = Field(default="en", max_length=32)
    timezone: str = "UTC"
    manual: bool = False
    records: list[RecordDTO] = Field(default_factory=list, max_length=12)
    _dates = field_validator("createdAt", "updatedAt")(instant)
    _zone = field_validator("timezone")(RecordDTO.valid_zone.__func__)

    @field_validator("content")
    @classmethod
    def nonblank(cls, value):
        if not value.strip():
            raise ValueError("Content must not be blank")
        return value


class RecordMutation(RecordDTO):
    expectedVersion: int = Field(ge=1)


class CorrectionDTO(DTO):
    noteId: UUID
    expectedNoteVersion: int = Field(ge=1)
    records: list[RecordDTO] = Field(min_length=1, max_length=12)


class FieldSpec(DTO):
    type: Literal["text", "number", "date", "datetime", "duration", "boolean", "enum", "URL", "reference", "quantity"]
    label: str = Field(default="", max_length=100)
    required: bool = False
    values: list[str] = Field(default_factory=list, max_length=100)
    unit: str | None = Field(default=None, max_length=32)


class TypeDTO(DTO):
    id: UUID
    schemaVersion: int = Field(default=1, ge=1)
    name: str = Field(min_length=1, max_length=100)
    baseKind: Kind
    fieldDefinitions: dict[str, FieldSpec] = Field(default_factory=dict, max_length=32)
    displayConfiguration: dict = Field(default_factory=dict)
    allowedCapabilities: list[Literal["complete", "schedule", "develop", "archive"]] = Field(default_factory=list)
    enabled: bool = True


class Proposal(DTO):
    baseKind: Kind
    customTypeId: UUID | None = None
    title: str = Field(min_length=1, max_length=1000)
    fields: dict[str, Any] = Field(default_factory=dict, max_length=32)
    startAt: str | None = None
    endAt: str | None = None
    dueAt: str | None = None
    dueDate: date | None = None
    plannedFor: date | None = None
    remindAt: str | None = None
    timezone: str = "UTC"
    evidence: dict[str, str] = Field(default_factory=dict, max_length=10)
    _times = field_validator("startAt", "endAt", "dueAt", "remindAt")(instant)
    _zone = field_validator("timezone")(RecordDTO.valid_zone.__func__)


class Extraction(DTO):
    schemaVersion: Literal[1] = 1
    records: list[Proposal] = Field(max_length=12)
    ambiguities: list[str] = Field(default_factory=list, max_length=12)


class Development(DTO):
    steps: list[str] = Field(min_length=3, max_length=5)
    @field_validator("steps")
    @classmethod
    def bounded_steps(cls, values):
        if any(not s.strip() or len(s) > 1000 for s in values):
            raise ValueError("Invalid step")
        return values
