"""Bounded, deterministic offline preview. This is not a simulated model response."""
import json
import re
from datetime import date, datetime, time, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

RULES = json.loads((Path(__file__).parent / "assets/capture_rules_v1.json").read_text(encoding="utf-8"))


def utf16_length(text):
    return len(text.encode("utf-16-le")) // 2


def utf16_slice(text, start, end):
    return text.encode("utf-16-le")[start * 2:end * 2].decode("utf-16-le")


def valid_hints(content, hints):
    if hints and hints.get("schemaVersion", 1) != 1: raise ValueError("Unsupported clarification version")
    values = hints.get("clarifications", []) if hints else []
    accepted = []
    for hint in values:
        try:
            if hint["start"] < 0 or hint["end"] > utf16_length(content) or hint["start"] >= hint["end"] or utf16_slice(content, hint["start"], hint["end"]) != hint["text"]:
                raise ValueError("Clarification no longer matches original text")
        except (UnicodeDecodeError, KeyError) as error:
            raise ValueError("Invalid clarification span") from error
        if any(hint["start"] < other["end"] and hint["end"] > other["start"] for other in accepted):
            raise ValueError("Clarifications overlap")
        accepted.append(hint)
    return accepted


def preview(content, created_at, zone, hints=None):
    reference = datetime.fromisoformat(created_at.replace("Z", "+00:00")).astimezone(ZoneInfo(zone))
    hints = valid_hints(content, hints)
    records, doubts = [], []
    def search(name, text): return re.search(RULES[name], text, re.I)
    pieces = []
    last = 0
    for match in re.finditer(RULES["split"], content, re.I):
        pieces.append((last, match.start())); last = match.end()
    pieces.append((last, len(content)))
    for begin, end in pieces[:12]:
        raw = content[begin:end]
        left = len(raw) - len(raw.lstrip()); begin += left
        text = raw.strip()
        if not text: continue
        end = begin + len(text)
        start16, end16 = utf16_length(content[:begin]), utf16_length(content[:end])
        selected = [h for h in hints if h["start"] >= start16 and h["end"] <= end16]
        kind = next((rule["kind"] for rule in RULES["kinds"] if re.search(rule["pattern"], text, re.I)), "information")
        day, clock, used, segment_doubts = None, None, [], []
        def doubt(a, b, reason):
            segment_doubts.append({"start": utf16_length(content[:begin+a]), "end": utf16_length(content[:begin+b]), "text": text[a:b], "reason": reason})
        def covered(a, b):
            return any(h["start"] <= utf16_length(content[:begin+a]) and h["end"] >= utf16_length(content[:begin+b]) for h in selected)
        iso = search("isoDate", text)
        if iso:
            try: day = date.fromisoformat(iso.group())
            except ValueError:
                if not covered(*iso.span()): doubt(iso.start(), iso.end(), "invalid_date")
            used.append(iso.span())
        else:
            for relative in RULES["relativeDays"]:
                match = re.search(relative["pattern"], text, re.I)
                if match: day = reference.date() + timedelta(days=relative["offset"]); break
            if day is None:
                for index, pattern in enumerate(RULES["weekdays"]):
                    match = re.search(pattern, text, re.I)
                    if match:
                        if search("uncertainDate", text):
                            if not covered(0, len(text)): doubt(0, len(text), "ambiguous_weekday")
                        else: day = reference.date() + timedelta(days=(index-reference.weekday()) % 7)
                        break
        clock_match = search("ampm", text) or search("clock", text)
        if clock_match:
            try:
                hour, minute = int(clock_match[1]), int(clock_match[2] or 0)
                if len(clock_match.groups()) == 3: hour = hour % 12 + (12 if clock_match[3].lower() == "pm" else 0)
                clock = time(hour, minute)
            except ValueError:
                if not covered(*clock_match.span()): doubt(clock_match.start(), clock_match.end(), "invalid_time")
            used.append(clock_match.span())
        elif not search("address", text):
            match = search("hour", text)
            if match:
                try: clock = time(int(match[1]), 0)
                except ValueError:
                    if not covered(*match.span()): doubt(match.start(), match.end(), "invalid_time")
                used.append(match.span())
        short = search("shortDate", text) if iso is None else None
        if short:
            used.append(short.span())
            if not covered(*short.span()): doubt(*short.span(), "ambiguous_date")
        if kind == "event":
            for match in re.finditer(RULES["bareNumber"], text, re.I):
                if not any(match.start() < b and match.end() > a for a, b in used) and not covered(*match.span()):
                    doubt(*match.span(), "time_or_day")
        for hint in selected:
            if hint.get("date"): day = date.fromisoformat(hint["date"])
            if hint.get("time"): clock = time.fromisoformat(hint["time"])
        start_at = None
        if day and clock:
            local = datetime.combine(day, clock)
            aware = local.replace(tzinfo=ZoneInfo(zone))
            roundtrip = aware.astimezone(timezone.utc).astimezone(ZoneInfo(zone)).replace(tzinfo=None)
            repeated = aware.utcoffset() != local.replace(tzinfo=ZoneInfo(zone), fold=1).utcoffset()
            if roundtrip != local or repeated:
                doubt(0, len(text), "clock_change")
            else: start_at = aware.astimezone(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")
        if kind == "event" and (not day or not clock) and not segment_doubts:
            doubt(0, len(text), "missing_date_time" if not day and not clock else "missing_date" if not day else "missing_time")
        if clock and not day and kind == "task" and not segment_doubts: doubt(0, len(text), "missing_date")
        records.append({"baseKind": kind, "title": text, "start": start16, "end": end16, "date": day.isoformat() if day else None, "time": clock.strftime("%H:%M") if clock else None, "startAt": start_at if not segment_doubts else None,
                        "dueDate": day.isoformat() if day and kind == "task" and search("deadline", text) and not segment_doubts else None,
                        "plannedFor": day.isoformat() if day and kind == "task" and not search("deadline", text) and clock is None and not segment_doubts else None,
                        "calendarEligible": kind == "event" and start_at is not None and not segment_doubts})
        doubts.extend(segment_doubts)
        records[-1]["kindChoices"] = ["task_deadline", "task_unscheduled", "idea"] if kind == "task" and day is None and clock is None and re.search(RULES.get("tentativeAction", r"(?!)"), text, re.I) else []
    return {"schemaVersion": 1, "engine": "local-rules-v1", "records": records, "doubts": doubts[:24]}
