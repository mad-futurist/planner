from uuid import UUID
from pydantic import Field
from app.schemas import DTO

class Enrollment(DTO):
    code: str = Field(min_length=20, max_length=200)
    deviceId: UUID

class VersionAction(DTO):
    expectedVersion: int = Field(ge=1)
    completedAt: str | None = None

class NoteEdit(DTO):
    expectedVersion: int = Field(ge=1)
    content: str = Field(min_length=1, max_length=20000)

class RuleDTO(DTO):
    id: UUID
    description: str = Field(max_length=1000)
    conditions: dict[str, str]
    action: dict[str, str]
    enabled: bool = True
    explicitlyApproved: bool = False
