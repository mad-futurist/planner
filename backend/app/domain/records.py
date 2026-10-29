"""Record invariants also apply to values assembled inside application workflows."""
from zoneinfo import ZoneInfo
from app.domain.time import instant


def validate_record(record):
    if record.baseKind not in {"task", "event", "idea", "information"}:
        raise ValueError("Invalid base kind")
    if not isinstance(record.title, str) or not 1 <= len(record.title) <= 1000:
        raise ValueError("Invalid title")
    if not isinstance(record.fields, dict) or len(record.fields) > 32:
        raise ValueError("Invalid fields")
    ZoneInfo(record.timezone)
    for name in ("startAt", "endAt", "dueAt", "remindAt", "completedAt", "createdAt", "updatedAt"):
        setattr(record, name, instant(getattr(record, name)))
    if record.startAt and record.endAt and record.endAt <= record.startAt:
        raise ValueError("End must follow start")
    if record.dueAt and record.dueDate:
        raise ValueError("Choose an instant or date-only deadline")
    if record.state not in {"pending", "completed", "archived"}:
        raise ValueError("Invalid state")
    if record.state == "completed" and (record.baseKind != "task" or not record.completedAt):
        raise ValueError("Completed tasks need a completion timestamp")
    if record.state != "completed" and record.completedAt:
        raise ValueError("Only completed tasks have completion timestamps")
    if record.customTypeId and not record.typeSchemaVersion:
        raise ValueError("Custom types require a pinned schema version")
    if record.version < 1 or record.interpretationVersion < 0:
        raise ValueError("Invalid version")
