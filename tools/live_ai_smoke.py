"""Explicitly billable local Docker/API smoke using only a disposable synthetic owner.

Run with backend's uv environment. Never runs in pytest/CI. Does not print keys,
invitations, session tokens or passwords. Removes its own account in finally.
"""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess
import time
from urllib.parse import urlparse
from uuid import uuid4
import httpx

ROOT = Path(__file__).resolve().parents[1]
CAPTURED = "2026-10-27T09:00:00.000Z"
ZONE = "Europe/Paris"


def bootstrap():
    source = """
import json, secrets
from app.persistence.database import SessionFactory
from app.persistence.models import User
from app.security.identity import issue_invite, enroll
from app.security.accounts import PASSWORDS
from uuid import uuid4
with SessionFactory() as db:
    code = issue_invite(db)
    session = enroll(db, code, str(uuid4()))
    password = secrets.token_urlsafe(32)
    db.get(User, session['userId']).password_hash = PASSWORDS.hash(password)
    db.get(User, session['userId']).dataset_synthetic = True
    db.commit()
    print(json.dumps(session | {'password': password}))
"""
    result = subprocess.run(["docker", "compose", "exec", "-T", "api", "uv", "run", "--no-sync", "python", "-c", source], cwd=ROOT, capture_output=True, timeout=30)
    if result.returncode:
        raise RuntimeError("Disposable owner bootstrap failed; no credentials were printed")
    return json.loads(result.stdout)


def run(base_url):
    parsed = urlparse(base_url)
    if parsed.scheme != "http" or parsed.hostname not in {"127.0.0.1", "localhost"} or parsed.path not in {"", "/"} or parsed.username or parsed.query or parsed.fragment:
        raise ValueError("This smoke is restricted to the local Docker API origin")
    report = {"schemaVersion": 1, "generatedAt": datetime.now(timezone.utc).isoformat(), "scope": "Synthetic server/API/real-worker smoke; Android UI and external account providers are not exercised", "checks": [], "cleanup": False}
    session = bootstrap()
    owner = session["userId"]
    sample_ids = []
    with httpx.Client(base_url=base_url.rstrip("/") + "/api/v1/", headers={"Authorization": "Bearer " + session["token"]}, timeout=45, follow_redirects=False) as client:
        def request(method, path, body=None):
            response = client.request(method, path, json=body, headers={"Idempotency-Key": str(uuid4())})
            if not response.is_success:
                raise RuntimeError(f"Synthetic API {method} {path.split('/')[0]} returned {response.status_code}")
            return response.json()

        def check(name, passed, **detail):
            report["checks"].append({"name": name, "passed": bool(passed), **detail})
            print(json.dumps({"check": name, "passed": bool(passed)}), flush=True)

        def wait_note(note_id):
            end = time.monotonic() + 100
            while time.monotonic() < end:
                note = request("GET", "notes/" + note_id)
                if note["processingStatus"] in {"organized", "review", "failed", "unconfigured"}:
                    return note
                time.sleep(1)
            raise TimeoutError("Synthetic interpretation did not finish within 100 seconds")

        def capture(text, locale="en", **values):
            note_id = str(uuid4())
            request("POST", "notes", {"id": note_id, "content": text, "createdAt": CAPTURED, "timezone": ZONE, "locale": locale, "privacyMode": "CLOUD_ALLOWED", **values})
            return note_id

        try:
            corpus = json.loads((ROOT / "backend/app/domain/assets/interpretation_benchmark_v1.json").read_text(encoding="utf-8"))
            selected = {"en-event-explicit", "fr-event-explicit", "ru-event-explicit", "en-call-not-appointment", "en-dentist-16", "en-confirm-today-16", "en-multi", "en-idea"}
            pending = [(case, capture(case["text"], case["locale"], interpretationHints=case.get("hints", {}))) for case in corpus["cases"] if case["id"] in selected]
            notes = {}
            for case, note_id in pending:
                note = wait_note(note_id)
                notes[case["id"]] = note
                records = [r for r in note["records"] if r["state"] != "archived"]
                history = request("GET", "notes/" + note_id + "/interpretation")["interpretation"]
                if case["doubts"]:
                    ok = note["processingStatus"] == "review" and not records
                else:
                    ok = note["processingStatus"] == "organized" and [r["baseKind"] for r in records] == case["kinds"]
                    for field in ("startAt", "plannedFor", "dueDate"):
                        if field in case:
                            ok = ok and len(records) > 0 and records[0].get(field) == case[field]
                ok = ok and note["originalContent"] == case["text"] and history is not None and history["modelVersion"] != "unconfigured"
                check(case["id"], ok, processingStatus=note["processingStatus"], records=records, interpretation=history)

            raw_id = capture("dentist 16", manual=True, records=[])
            raw = request("GET", "notes/" + raw_id)
            check("raw-only-does-not-create-event", raw["manual"] and not raw["records"] and request("GET", "notes/" + raw_id + "/interpretation")["interpretation"] is None)

            task = next((r for r in notes["en-call-not-appointment"]["records"] if r["baseKind"] == "task"), None)
            if task:
                done = request("POST", "records/" + task["id"] + "/complete", {"expectedVersion": task["version"]})
                undone = request("POST", "records/" + task["id"] + "/undo", {"expectedVersion": done["version"]})
                check("complete-and-undo", done["state"] == "completed" and undone["state"] == "pending")
                corrected = request("PATCH", "records/" + task["id"], undone | {"title": "Call my dentist instead", "expectedVersion": undone["version"]})
                retained_note = request("GET", "notes/" + task["sourceNoteId"])
                check("semantic-user-edit-retains-original", corrected["title"] == "Call my dentist instead" and retained_note["originalContent"] == "Call the doctor tomorrow")
            else:
                check("complete-and-undo", False, reason="No valid extracted task")

            idea = next((r for r in notes["en-idea"]["records"] if r["baseKind"] == "idea"), None)
            if idea:
                job = request("POST", "ideas/" + idea["id"] + "/develop", {})
                end = time.monotonic() + 100
                while job["status"] in {"queued", "running"} and time.monotonic() < end:
                    time.sleep(1)
                    job = request("GET", "jobs/" + job["id"])
                steps = (job.get("result") or {}).get("steps", [])
                check("real-idea-development", job["status"] == "complete" and 3 <= len(steps) <= 5, job=job)
            else:
                check("real-idea-development", False, reason="No valid extracted idea")

            type_id = str(uuid4())
            request("POST", "types", {"id": type_id, "schemaVersion": 1, "name": "Reading task", "baseKind": "task", "fieldDefinitions": {"book": {"type": "text", "label": "Book", "required": True}}})
            typed = wait_note(capture("Read chapter 2 of the book Dune"))
            check("registry-type-extraction", typed["processingStatus"] == "organized" and any(r["customTypeId"] == type_id and r["fields"].get("book", "").casefold() == "dune" for r in typed["records"]), records=typed["records"], processingStatus=typed["processingStatus"])
            request("PATCH", "types/" + type_id, {"id": type_id, "schemaVersion": 2, "name": "Reading task", "baseKind": "task", "fieldDefinitions": {"book": {"type": "text", "label": "Book", "required": True}, "chapter": {"type": "number", "label": "Chapter"}}})
            retained = request("GET", "notes/" + typed["id"])["records"]
            versions = request("GET", "types")["types"]
            check("registry-version-retention", len([t for t in versions if t["id"] == type_id]) == 2 and all(r["typeSchemaVersion"] == 1 for r in retained if r["customTypeId"] == type_id))

            rule_id = str(uuid4())
            request("POST", "rules", {"id": rule_id, "description": "Synthetic bookmark rule", "conditions": {"contains": "Bookmark:"}, "action": {"baseKind": "information"}, "explicitlyApproved": True})
            ruled = wait_note(capture("Bookmark: useful article about reading gardens"))
            check("approved-rule-extraction", ruled["processingStatus"] == "organized" and bool(ruled["records"]) and all(r["baseKind"] == "information" for r in ruled["records"]), records=ruled["records"])

            preview = request("POST", "preview/ai", {"content": "dentist 16", "createdAt": CAPTURED, "timezone": ZONE, "locale": "en"})
            check("explicit-ai-preview-requires-review", preview["previewOnly"] and preview["needsReview"], preview=preview)
            feed = request("GET", "changes?cursor=0&limit=100")
            check("incremental-feed", bool(feed["changes"]) and all("payload" in c for c in feed["changes"]))
            report["diagnostics"] = request("GET", "diagnostics")
            summary = request("GET", "dataset/summary")
            examples = request("GET", "dataset/examples?limit=100")["examples"]
            sample_ids = [sample["id"] for sample in examples]
            trace_present = any(attempt.get("rawResponse") for sample in examples for attempt in sample["data"].get("providerAttempts", []))
            edit_present = any(sample["eventKind"] == "record_edit" and any(change["path"] == "/title" for change in sample["data"].get("changes", [])) for sample in examples)
            check("passive-dataset-journal", summary["total"] >= 20 and trace_present and edit_present and summary["contributionEnabled"] is False, summary=summary)
        except Exception as error:
            report["failureClass"] = type(error).__name__
            # Exceptions above contain only safe fixed context, never credentials/provider bodies.
            report["failure"] = str(error) if isinstance(error, (RuntimeError, TimeoutError)) else "Synthetic smoke aborted"
        finally:
            try:
                deleted = request("DELETE", "auth/account", {"ownerId": owner, "confirmation": "DELETE", "password": session["password"]})
                report["cleanup"] = deleted.get("deleted") is True
                report["sessionRevoked"] = client.get("config").status_code == 401
                if sample_ids:
                    source = "from sqlalchemy import select,func; from app.persistence.database import SessionFactory; from app.persistence.models import LearningSample; " + "db=SessionFactory(); print(db.scalar(select(func.count()).select_from(LearningSample).where(LearningSample.id.in_(" + repr(sample_ids) + "), LearningSample.user_id.is_(None), LearningSample.origin=='synthetic'))); db.close()"
                    archived = subprocess.run(["docker", "compose", "exec", "-T", "api", "uv", "run", "--no-sync", "python", "-c", source], cwd=ROOT, capture_output=True, timeout=30)
                    count = int(archived.stdout) if archived.returncode == 0 else 0
                    check("synthetic-dataset-survives-account-cleanup", count == len(sample_ids), examplesRetained=count)
            except Exception as error:
                report["cleanupErrorClass"] = type(error).__name__
    report["passed"] = sum(c["passed"] for c in report["checks"])
    report["executed"] = len(report["checks"])
    report["success"] = bool(report["checks"]) and report["passed"] == report["executed"] and "failureClass" not in report and "cleanupErrorClass" not in report and report["cleanup"] and report.get("sessionRevoked", False)
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = run(args.base_url)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items() if k not in {"checks", "diagnostics"}}, ensure_ascii=False))
    raise SystemExit(0 if result["success"] else 1)
