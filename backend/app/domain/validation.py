import re
from datetime import date, datetime, timedelta
from urllib.parse import urlparse
from uuid import UUID
from zoneinfo import ZoneInfo
from app.domain.contracts import ExtractionData, RecordData, TypeData
from app.domain.time import instant

WEEKDAYS = [
    ("monday", "lundi", "понедельник"), ("tuesday", "mardi", "вторник"),
    ("wednesday", "mercredi", "сред"), ("thursday", "jeudi", "четверг"),
    ("friday", "vendredi", "пятниц"), ("saturday", "samedi", "суббот"),
    ("sunday", "dimanche", "воскресень"),
]


def resolve_day(evidence: str, reference: date) -> date | None:
    text = evidence.casefold()
    match = re.search(r"\b\d{4}-\d{2}-\d{2}\b", text)
    if match:
        return date.fromisoformat(match.group())
    for words, delta in [(("tomorrow", "demain", "завтра"), 1), (("today", "aujourd", "сегодня"), 0), (("yesterday", "hier", "вчера"), -1)]:
        if any(word in text for word in words):
            return reference + timedelta(days=delta)
    if any(word in text for word in ("next", "prochain", "следующ")):
        return None  # Locale-dependent "next weekday" needs review.
    for weekday, words in enumerate(WEEKDAYS):
        if any(word in text for word in words):
            return reference + timedelta(days=(weekday - reference.weekday()) % 7)
    return None


def validate_fields(record: RecordData, definition: TypeData | None):
    if definition is None:
        if record.customTypeId:
            raise ValueError("Unknown type version")
        return
    if definition.baseKind != record.baseKind:
        raise ValueError("Custom type base kind mismatch")
    if set(record.fields) - set(definition.fieldDefinitions):
        raise ValueError("Unknown custom field")
    for key, spec in definition.fieldDefinitions.items():
        value = record.fields.get(key)
        if value is None:
            if spec.required:
                raise ValueError("Required custom field missing")
            continue
        kind = spec.type
        valid = True
        if kind in {"text", "URL", "reference", "date", "datetime", "enum"}:
            valid = isinstance(value, str) and len(value) <= 5000
        if kind in {"number", "duration"}:
            valid = isinstance(value, (int, float)) and not isinstance(value, bool)
        if kind == "boolean":
            valid = isinstance(value, bool)
        if kind == "quantity":
            valid = isinstance(value, dict) and set(value) == {"value", "unit"} and isinstance(value["value"], (int, float)) and not isinstance(value["value"], bool) and isinstance(value["unit"], str)
        if not valid:
            raise ValueError("Invalid custom field primitive")
        if kind == "date": date.fromisoformat(value)
        if kind == "datetime": instant(value)
        if kind == "URL" and urlparse(value).scheme not in {"https", "http"}: raise ValueError("Invalid URL")
        if kind == "reference": UUID(value)
        if kind == "enum" and value not in spec.values: raise ValueError("Invalid enum value")
        if kind == "quantity" and spec.unit and value["unit"] != spec.unit: raise ValueError("Unexpected quantity unit")


def semantic_review(extraction: ExtractionData, content: str, created_at: str, timezone: str, hints: dict | None = None) -> list[str]:
    from app.domain.capture_preview import preview, valid_hints
    clarifications = valid_hints(content, hints)
    reasons = list(extraction.ambiguities)
    if preview(content, created_at, timezone, hints)["doubts"]:
        reasons.append("Capture requires explicit date/time clarification")
    reference = datetime.fromisoformat(created_at.replace("Z", "+00:00")).astimezone(ZoneInfo(timezone)).date()
    for proposal in extraction.records:
        if proposal.baseKind == "event" and not proposal.startAt:
            reasons.append("Event has no confirmed start time")
        for field in ("startAt", "endAt", "dueAt", "dueDate", "plannedFor", "remindAt"):
            value = getattr(proposal, field)
            if value is None:
                continue
            evidence = proposal.evidence.get(field, "")
            if not evidence or evidence.casefold() not in content.casefold():
                reasons.append("Temporal field lacks original-text evidence")
                continue
            approved = [h for h in clarifications if h["text"].casefold() in evidence.casefold() and h["kind"] != "literal" and field in {"startAt", "dueAt", "dueDate", "plannedFor"}]
            if field.endswith("At"):
                clocks = set()
                for match in re.finditer(r"\b(\d{1,2})(?:[:h](\d{2}))?\s*(am|pm|h\b|heures?\b|час\w*)?(?![\d:h]|\.\d)", evidence.casefold()):
                    hour, minute, suffix = match.groups()
                    if minute is None and suffix is None:
                        # Bare hours are valid only after an explicit language-specific
                        # time introducer; a date, quantity or task number is not a clock.
                        prefix = evidence[max(0, match.start() - 10):match.start()].casefold()
                        if re.search(r"(?:\bat\s+|\bà\s+|\bв\s+)$", prefix) is None:
                            continue
                    hour, minute = int(hour), int(minute or 0)
                    if suffix in {"am", "pm"}:
                        if not 1 <= hour <= 12:
                            continue
                        hour = hour % 12 + (12 if suffix == "pm" else 0)
                    if hour < 24 and minute < 60:
                        clocks.add((hour, minute))
                local = datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(ZoneInfo(timezone))
                for hint in approved:
                    if hint.get("time"): clocks.add(tuple(map(int, hint["time"].split(":"))))
                if not clocks:
                    reasons.append("A precise time was not supplied")
                elif (local.hour, local.minute) not in clocks:
                    reasons.append("Time differs from original-text evidence")
                if local.replace(fold=0).utcoffset() != local.replace(fold=1).utcoffset():
                    reasons.append("Repeated local time needs explicit confirmation")
            expected = resolve_day(evidence, reference)
            hint_days = {h["date"] for h in approved if h.get("date")}
            if len(hint_days) == 1: expected = date.fromisoformat(next(iter(hint_days)))
            actual = value if isinstance(value, date) else datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(ZoneInfo(timezone)).date()
            if expected is None or actual != expected:
                reasons.append("Date resolution needs review")
        if proposal.startAt and proposal.endAt and proposal.endAt <= proposal.startAt:
            raise ValueError("Invalid duration")
        if proposal.dueAt and proposal.dueDate:
            raise ValueError("Conflicting deadline precision")
    return list(dict.fromkeys(reasons))
