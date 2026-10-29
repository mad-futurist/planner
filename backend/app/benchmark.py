"""Synthetic acceptance benchmark; --engine provider performs real, potentially billable calls."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import json
from pathlib import Path
import time
from uuid import uuid4
from app.domain.capture_preview import preview


def run(engine="local", limit=None, persist=False, corpus_path=None):
    run_key = "benchmark:" + str(uuid4())
    corpus = json.loads((Path(corpus_path) if corpus_path else Path(__file__).parent / "domain/assets/interpretation_benchmark_v1.json").read_text(encoding="utf-8"))
    provider = None
    if engine == "provider":
        from app.config import settings
        from app.ai.provider import OpenAICompatibleProvider
        if not settings.ai_available:
            return {"schemaVersion": 1, "engine": engine, "status": "unconfigured", "executed": 0, "message": "Set LLM_API_KEY and LLM_MODEL in backend/.env; no provider calls were made."}
        provider = OpenAICompatibleProvider(settings)
    results, distribution, confusion = [], Counter(), Counter()
    for case in corpus["cases"][:limit]:
        started = time.perf_counter()
        error = None
        try:
            if provider is None:
                actual = preview(case["text"], corpus["createdAt"], corpus["timezone"], case.get("hints"))
            else:
                from app.domain.validation import semantic_review
                context = {"capturedAt": corpus["createdAt"], "timezone": corpus["timezone"], "locale": case["locale"], "types": [], "rules": [], "corrections": [], "interpretationHints": case.get("hints", {})}
                extraction = provider.extract(case["text"], context)
                reasons = semantic_review(extraction, case["text"], corpus["createdAt"], corpus["timezone"], case.get("hints"))
                actual = {"records": [r.model_dump(mode="json") | {"calendarEligible": r.baseKind == "event" and r.startAt is not None and not reasons} for r in extraction.records], "doubts": [{"reason": reason} for reason in reasons]}
        except Exception as exc:
            error = type(exc).__name__
            actual = {"records": [], "doubts": []}
        kinds = [r["baseKind"] for r in actual["records"]]
        distribution.update(kinds)
        for index, expected in enumerate(case["kinds"]): confusion[(expected, kinds[index] if index < len(kinds) else "missing")] += 1
        checks = {"entities": kinds == case["kinds"], "calendar": [r["calendarEligible"] for r in actual["records"]] == case["calendar"], "clarification": bool(actual["doubts"]) == bool(case["doubts"])}
        if "kindChoices" in case and provider is None: checks["kindChoices"] = [r.get("kindChoices", []) for r in actual["records"]] == case["kindChoices"]
        for field in ("startAt", "dueDate", "plannedFor"):
            if field in case: checks[field] = bool(actual["records"]) and actual["records"][0].get(field) == case[field]
        results.append({"id": case["id"], "locale": case["locale"], "text": case["text"], "expectedKinds": case["kinds"], "expected": {k:v for k,v in case.items() if k not in {"id","locale","text"}}, "actual": actual, "checks": checks, "passed": all(checks.values()) and error is None, "error": error, "milliseconds": round((time.perf_counter()-started)*1000, 3)})
        if persist:
            from app.dataset import save_evaluation
            from app.persistence.database import SessionFactory
            with SessionFactory() as db:
                save_evaluation(db, case, results[-1], run_key=run_key, engine=provider.model if provider else "local-rules-v1", captured=corpus["createdAt"], zone=corpus["timezone"], attempts=getattr(provider, "attempts", []))
                db.commit()
    timings = sorted(r["milliseconds"] for r in results)
    return {"schemaVersion": 1, "datasetRunId": run_key if persist else None, "generatedAt": datetime.now(timezone.utc).isoformat(), "capturedAt": corpus["createdAt"], "timezone": corpus["timezone"], "corpusId": Path(corpus_path).name if corpus_path else "interpretation_benchmark_v1.json", "engine": "local-rules-v1" if provider is None else provider.model,
            "status": "complete", "scope": f"{len(corpus['cases'])} curated synthetic EN/FR/RU acceptance cases; not a held-out generalization study. Custom type assignment is not measured. " + ("Local rules are not AI inference. kindChoices marks unresolved user intent, not a commitment." if provider is None else "Actual configured-provider inference followed by deterministic semantic validation; this evaluates the combined interpreter, not raw model accuracy."),
            "executed": len(results), "passed": sum(r["passed"] for r in results), "entityDistribution": dict(distribution), "confusion": [{"expected": a, "actual": b, "count": n} for (a,b),n in sorted(confusion.items())],
            "calendarReadyEvents": sum(r["calendarEligible"] for result in results for r in result["actual"]["records"]), "clarificationCases": sum(bool(result["actual"]["doubts"]) for result in results),
            "perLocale": {locale: {"cases": sum(r["locale"] == locale for r in results), "passed": sum(r["locale"] == locale and r["passed"] for r in results)} for locale in ("en", "fr", "ru")},
            "unsafeCalendarCases": sum(bool(c["doubts"]) and any(r["calendarEligible"] for r in result["actual"]["records"]) for c,result in zip(corpus["cases"], results)),
            "p50Milliseconds": timings[len(timings)//2] if timings else None, "p95Milliseconds": timings[min(len(timings)-1, int(len(timings)*.95))] if timings else None, "cases": results}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--engine", choices=["local", "provider"], default="local")
    parser.add_argument("--limit", type=int, choices=range(1, 44), metavar="1..43")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--corpus", type=Path, help="Alternate curated corpus using the same versioned case contract")
    parser.add_argument("--no-persist", action="store_true", help="Explicit isolated/CI opt-out from database collection")
    args = parser.parse_args()
    report = run(args.engine, args.limit, persist=not args.no_persist, corpus_path=args.corpus)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    print(json.dumps({k: v for k,v in report.items() if k not in {"cases", "confusion"}}, ensure_ascii=False))
    raise SystemExit(2 if report["status"] == "unconfigured" else 0 if report["passed"] == report["executed"] else 1)
