"""Owner-scoped, append-only observations. Model predictions are never gold labels."""
import copy
import hashlib
import json
from sqlalchemy import select
from app.persistence.models import History, LearningSample, User
from app.services import lock_user

POLICY = "learning-v1"
PROMPT_VERSION = "extraction-2026-10-28-grounded-v1"


def snapshot(note):
    return {"noteId": getattr(note, "id", None), "inputVersion": getattr(note, "version", None),
            "content": note.content, "originalContent": getattr(note, "original_content", note.content),
            "capturedAt": note.created_at, "timezone": note.timezone, "locale": note.locale,
            "interpretationHints": note.interpretation_hints or {}}


def changes(before, after, path=""):
    """JSON-pointer diff; snapshots are retained as the authority for list reordering."""
    if isinstance(before, dict) and isinstance(after, dict):
        result = []
        for key in sorted(before.keys() | after.keys()):
            pointer = path + "/" + str(key).replace("~", "~0").replace("/", "~1")
            if key not in before: result.append({"path": pointer, "operation": "add", "after": after[key]})
            elif key not in after: result.append({"path": pointer, "operation": "remove", "before": before[key]})
            else: result.extend(changes(before[key], after[key], pointer))
        return result
    if isinstance(before, list) and isinstance(after, list):
        result = []
        for index in range(max(len(before), len(after))):
            pointer = path + "/" + str(index)
            if index >= len(before): result.append({"path": pointer, "operation": "add", "after": after[index]})
            elif index >= len(after): result.append({"path": pointer, "operation": "remove", "before": before[index]})
            else: result.extend(changes(before[index], after[index], pointer))
        return result
    return [] if before == after else [{"path": path or "/", "operation": "replace", "before": before, "after": after}]


def put(db, *, key, group, origin, kind, label, data, owner=None):
    if owner:
        lock_user(db, owner)
        user = db.scalar(select(User).where(User.id == owner).execution_options(populate_existing=True))
        if user is None: return None  # Deletion during provider I/O must not resurrect data.
        origin = "synthetic" if user.dataset_synthetic else origin
    existing = db.scalar(select(LearningSample).where(LearningSample.event_key == key))
    if existing: return existing
    row = LearningSample(user_id=owner, event_key=key, group_key=group, origin=origin,
                         event_kind=kind, label_source=label, data=copy.deepcopy(data))
    db.add(row)
    return row


def captured(db, note, records):
    return put(db, key=f"capture:{note.id}:{note.version}", group="note:" + note.id, owner=note.user_id,
               origin="cloud_note", kind="capture", label="user_manual" if note.manual and records else "unlabeled",
               data={"input": snapshot(note), "records": records, "rawOnly": note.manual and not records})


def corrected(db, note, correction, kind):
    history = db.scalar(select(History).where(History.note_id == note.id).order_by(History.created_at.desc(), History.id.desc()).limit(1))
    return put(db, key="correction:" + correction.id, group="note:" + note.id, owner=note.user_id,
               origin="cloud_note", kind=kind, label="user_action" if kind in {"task_state", "record_action"} else "user_edit",
               data={"input": snapshot(note), "correctionId": correction.id, "interpretationId": history.id if history else None,
                     "before": correction.previous_interpretation, "after": correction.corrected_interpretation,
                     "changes": changes(correction.previous_interpretation, correction.corrected_interpretation),
                     "labelCaveat": "Observed user edit; not independently adjudicated ground truth"})


def predicted(db, note_input, owner, *, key, kind, provider, result=None, status, reasons=None, context=None, error=None, history_id=None, outcome=None):
    note_id = note_input.get("noteId")
    return put(db, key=key, group="note:" + note_id if note_id else key, owner=owner,
               origin="cloud_note" if note_id else "ai_preview", kind=kind, label="model_prediction",
               data={"input": note_input, "model": provider.model, "promptVersion": PROMPT_VERSION,
                     "context": context or {}, "providerAttempts": getattr(provider, "attempts", []),
                     "prediction": result.model_dump(mode="json") if result is not None else None,
                     "status": status, "reviewReasons": reasons or [], "errorClass": type(error).__name__ if error else None,
                     "interpretationId": history_id, "trainingTarget": False, "outcome": outcome or {}})


def wire(row):
    return {"id": row.id, "schemaVersion": row.schema_version, "groupKey": row.group_key,
            "origin": row.origin, "eventKind": row.event_kind, "labelSource": row.label_source,
            "createdAt": row.created_at, "data": row.data}


def split_for(group):
    bucket = int(hashlib.sha256(group.encode()).hexdigest()[:8], 16) % 100
    return "test" if bucket < 10 else "validation" if bucket < 20 else "train"


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
