"""Canonical business timestamps, independent of ORM and wire validation."""
from datetime import datetime, timezone
from uuid import uuid4

def uid() -> str:
    return str(uuid4())

def now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")

def instant(value: str | None) -> str | None:
    if value is None:
        return None
    dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if dt.tzinfo is None:
        raise ValueError("An explicit offset is required")
    return dt.astimezone(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")
