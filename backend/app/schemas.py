from datetime import date, datetime, timezone
import re
from typing import Any, Literal
from uuid import UUID
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator
from app.domain.time import now, uid, instant

Kind = Literal["task", "event", "idea", "information"]


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


class CaptureHintDTO(DTO):
    start: int = Field(ge=0, le=40_000)
    end: int = Field(ge=1, le=40_000)
    text: str = Field(min_length=1, max_length=20_000)
    kind: Literal["date", "time", "datetime", "literal"]
    date: str | None = None
    time: str | None = None

    @model_validator(mode="after")
    def valid_choice(self):
        if self.date is not None: date.fromisoformat(self.date)
        if self.time is not None:
            from datetime import time
            if not re.fullmatch(r"[0-9]{2}:[0-9]{2}", self.time): raise ValueError("Use HH:mm")
            time.fromisoformat(self.time)
        required = {"date": (True, False), "time": (False, True), "datetime": (True, True), "literal": (False, False)}[self.kind]
        if (self.date is not None, self.time is not None) != required: raise ValueError("Clarification fields do not match its kind")
        return self


class InterpretationHintsDTO(DTO):
    schemaVersion: Literal[1] = 1
    clarifications: list[CaptureHintDTO] = Field(default_factory=list, max_length=24)


class PreviewRequest(DTO):
    content: str = Field(min_length=1, max_length=20_000)
    createdAt: str
    timezone: str = "UTC"
    locale: str = Field(default="en", max_length=32)
    interpretationHints: InterpretationHintsDTO = Field(default_factory=InterpretationHintsDTO)
    _dates = field_validator("createdAt")(instant)
    _zone = field_validator("timezone")(RecordDTO.valid_zone.__func__)

    @model_validator(mode="after")
    def matching_hints(self):
        from app.domain.capture_preview import valid_hints
        valid_hints(self.content, self.interpretationHints.model_dump())
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
    interpretationHints: InterpretationHintsDTO = Field(default_factory=InterpretationHintsDTO)
    records: list[RecordDTO] = Field(default_factory=list, max_length=12)
    _dates = field_validator("createdAt", "updatedAt")(instant)
    _zone = field_validator("timezone")(RecordDTO.valid_zone.__func__)

    @field_validator("content")
    @classmethod
    def nonblank(cls, value):
        if not value.strip():
            raise ValueError("Content must not be blank")
        return value

    @model_validator(mode="after")
    def matching_hints(self):
        from app.domain.capture_preview import valid_hints
        valid_hints(self.content, self.interpretationHints.model_dump())
        return self


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
