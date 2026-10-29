"""Structural business contracts. Neither HTTP DTOs nor ORM classes are required."""
from datetime import date
from typing import Any, Protocol
from uuid import UUID

class FieldDefinition(Protocol):
    type: str
    required: bool
    values: list[str]
    unit: str | None

class RecordData(Protocol):
    customTypeId: UUID | None
    baseKind: str
    fields: dict[str, Any]

class TypeData(Protocol):
    baseKind: str
    fieldDefinitions: dict[str, FieldDefinition]

class ProposalData(RecordData, Protocol):
    startAt: str | None
    endAt: str | None
    dueAt: str | None
    dueDate: date | None
    plannedFor: date | None
    remindAt: str | None
    evidence: dict[str, str]

class ExtractionData(Protocol):
    records: list[ProposalData]
    ambiguities: list[str]
