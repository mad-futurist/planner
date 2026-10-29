"""Persist/import synthetic examples and export explicitly contributed observations.

No training or provider requests are made by this module.
"""
import argparse
from collections import Counter
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
from zoneinfo import ZoneInfo
from sqlalchemy import func, select
from app.learning import fingerprint, put, snapshot, split_for, wire
from app.persistence.database import SessionFactory
from app.persistence.models import Correction, History, LearningSample, Note, User, now, uid

ASSETS = Path(__file__).parent / "domain/assets"


def save_evaluation(db, case, result, *, run_key, engine, captured, zone, attempts=None):
    return put(db, key="evaluation:" + fingerprint([run_key, case["id"]]), group="regression:" + case["id"],
               origin="synthetic", kind="evaluation", label="synthetic_expected",
               data={"input": {"content": case["text"], "locale": case["locale"], "timezone": zone, "capturedAt": captured, "interpretationHints": case.get("hints", {})},
                     "caseId": case["id"], "model": engine, "runKey": run_key, "expected": {key: value for key, value in case.items() if key not in {"text", "locale", "hints", "id"}},
                     "observation": result, "providerAttempts": attempts or [], "exposedEvaluation": True})


def import_report(db, path):
    report = json.loads(path.read_text(encoding="utf-8"))
    corpus = json.loads((ASSETS / "interpretation_benchmark_v1.json").read_text(encoding="utf-8"))
    cases = {c["id"]: c for c in corpus["cases"]}
    run = report.get("datasetRunId") or "report:" + fingerprint(report)
    imported = 0
    for result in report.get("cases", []):
        case = cases.get(result.get("id"))
        if case is None and isinstance(result.get("expected"), dict) and result.get("text"):
            case = {**result["expected"], "id": result["id"], "text": result["text"], "locale": result.get("locale", "unknown")}
        if case and result.get("text") == case["text"]:
            save_evaluation(db, case, result, run_key=run, engine=report["engine"], captured=report.get("capturedAt", corpus["createdAt"]), zone=report.get("timezone", corpus["timezone"]))
            imported += 1
    # Smoke reports contain some complete input cases and some outcome-only checks.
    for index, result in enumerate(report.get("checks", [])):
        case = cases.get(result["name"])
        put(db, key="smoke:" + fingerprint([run, index]), group="regression:" + case["id"] if case else "smoke:" + result["name"],
            origin="synthetic", kind="smoke_observation", label="synthetic_expected" if case else "unlabeled",
            data={"input": {"content": case["text"], "locale": case["locale"], "timezone": corpus["timezone"], "capturedAt": corpus["createdAt"], "interpretationHints": case.get("hints", {})} if case else None,
                  "inputAvailable": bool(case), "runKey": run, "observation": result, "exposedEvaluation": True})
        imported += 1
    return imported


def diverse_cases(variants=3):
    dates = ["2026-10-27T09:00:00Z", "2026-10-24T09:00:00Z", "2027-03-27T09:00:00Z", "2026-12-31T23:30:00Z", "2028-02-28T09:00:00Z", "2026-01-01T00:30:00Z"]
    zones = ["Europe/Paris", "UTC", "America/New_York", "Asia/Tokyo", "Asia/Kolkata", "Australia/Sydney"]
    phrases = {
        "en": ["Buy {n} apples", "Call the doctor tomorrow", "Finish the report by Friday", "Dentist tomorrow at {clock}", "dentist {n}", "Dentist tomorrow", "Idea: build a reading garden {name}", "The door code is {n}37", "Meeting next Friday at {clock}", "Dentist 16/11"],
        "fr": ["Acheter {n} pommes", "Appeler le dentiste demain", "Envoyer le rapport avant vendredi", "Dentiste demain à {clock}", "dentiste {n}", "Dentiste demain", "Idée: créer un jardin de lecture {name}", "Le code de la porte est {n}37", "Rendez-vous vendredi prochain à {clock}", "Dentiste 16/11"],
        "ru": ["Купить {n} яблок", "Позвонить врачу завтра", "Закончить отчет до пятницы", "Стоматолог завтра в {clock}", "стоматолог {n}", "Стоматолог завтра", "Идея: сделать сад для чтения {name}", "Код двери: {n}37", "Встреча в следующую пятницу в {clock}", "Стоматолог 16/11"],
    }
    families = ["quantity", "execution_day", "deadline", "exact_event", "bare_number", "missing_time", "idea", "information", "next_weekday", "short_date"]
    alternate = {
        "en": [
            ["Order {n} notebooks", "Get {n} bottles of water"], ["Collect the parcel tomorrow", "Water the plants tomorrow"],
            ["Send the invoice by Friday", "Renew the insurance before Friday"], ["Meeting with Nicolas tomorrow at {clock}", "Haircut tomorrow at {clock}"],
            ["meeting {n}", "haircut {n}"], ["Lunch with Alice tomorrow", "Vet appointment tomorrow"],
            ["Idea: neighborhood tool library", "Idea: organize a book exchange"], ["WiFi password: synthetic-{n}", "Membership number: {n}37"],
            ["Doctor appointment next Friday at {clock}", "Lunch next Friday at {clock}"], ["Meeting 16/11", "Haircut 16/11"]],
        "fr": [
            ["Commander {n} cahiers", "Prendre {n} bouteilles d'eau"], ["Récupérer le colis demain", "Arroser les plantes demain"],
            ["Envoyer la facture avant vendredi", "Renouveler l'assurance avant vendredi"], ["Réunion avec Nicolas demain à {clock}", "Coiffeur demain à {clock}"],
            ["rendez-vous {n}", "coiffeur {n}"], ["Déjeuner avec Alice demain", "Rendez-vous vétérinaire demain"],
            ["Idée: bibliothèque d'outils du quartier", "Idée: organiser un échange de livres"], ["Mot de passe WiFi: fictif-{n}", "Numéro d'adhérent: {n}37"],
            ["Médecin vendredi prochain à {clock}", "Déjeuner vendredi prochain à {clock}"], ["Rendez-vous 16/11", "Coiffeur 16/11"]],
        "ru": [
            ["Заказать {n} тетрадей", "Взять {n} бутылок воды"], ["Забрать посылку завтра", "Полить растения завтра"],
            ["Отправить счет до пятницы", "Продлить страховку до пятницы"], ["Встреча с Николя завтра в {clock}", "Стрижка завтра в {clock}"],
            ["встреча {n}", "стрижка {n}"], ["Обед с Алисой завтра", "Прием у ветеринара завтра"],
            ["Идея: библиотека инструментов для соседей", "Идея: организовать обмен книгами"], ["Пароль WiFi: выдуманный-{n}", "Номер абонемента: {n}37"],
            ["Прием у врача в следующую пятницу в {clock}", "Обед в следующую пятницу в {clock}"], ["Встреча 16/11", "Стрижка 16/11"]],
    }
    for captured in dates:
        for zone in zones:
            local = datetime.fromisoformat(captured.replace("Z", "+00:00")).astimezone(ZoneInfo(zone))
            tomorrow = local.date() + timedelta(days=1)
            for variant in range(variants):
                number = ((2, 16, 30, 1, 23, 31, 7, 12, 18, 28)[variant] + local.day - 1) % 31 + 1
                clock = ("09:15", "16:00", "02:30", "23:45", "12:00", "08:05", "14:30", "19:00", "00:15", "11:59")[variant]
                for locale, templates in phrases.items():
                    for index, template in enumerate(templates):
                        if variant % 3: template = alternate[locale][index][variant % 3 - 1]
                        weekday = (local.day + variant) % 7
                        weekdays = {"en": ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], "fr": ["lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"], "ru": ["понедельника", "вторника", "среды", "четверга", "пятницы", "субботы", "воскресенья"]}
                        if index == 2: template = template.replace({"en": "Friday", "fr": "vendredi", "ru": "пятницы"}[locale], weekdays[locale][weekday])
                        family = families[index]
                        text = template.format(n=number, clock=clock, name=("A", "B", "C", "D", "E", "F", "G", "H", "I", "J")[variant])
                        names = {"en": ["Nicolas", "Alice", "Sarah", "Oleg", "Maria"], "fr": ["Nicolas", "Alice", "Sarah", "Oleg", "Marie"], "ru": ["Николя", "Алисой", "Сарой", "Олегом", "Марией"]}
                        text = text.replace("Николя" if locale == "ru" else "Nicolas", names[locale][(local.day + variant) % 5])
                        if variant % 3 == 1: text += "!"
                        if variant % 3 == 2: text = "📝 " + text
                        expected = {"kinds": [{0: "task", 1: "task", 2: "task", 3: "event", 4: "event", 5: "event", 6: "idea", 7: "information", 8: "event", 9: "event"}[index]], "requiresClarification": index in {4, 5, 8, 9}}
                        if index == 1: expected["plannedFor"] = tomorrow.isoformat()
                        if index == 2: expected["dueDate"] = (local.date() + timedelta(days=(weekday-local.weekday()) % 7)).isoformat()
                        if index == 3:
                            wall = datetime.fromisoformat(str(tomorrow) + "T" + clock)
                            aware = wall.replace(tzinfo=ZoneInfo(zone))
                            unsafe = aware.utcoffset() != wall.replace(tzinfo=ZoneInfo(zone), fold=1).utcoffset() or aware.astimezone(timezone.utc).astimezone(ZoneInfo(zone)).replace(tzinfo=None) != wall
                            expected["requiresClarification"] = unsafe
                            if not unsafe: expected["startAt"] = aware.astimezone(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")
                        yield {"id": fingerprint([captured, zone, variant, locale, family]), "family": family, "input": {"content": text, "locale": locale, "timezone": zone, "capturedAt": captured}, "expected": expected, "label": "synthetic_template"}
                        if index == 3:
                            absolute = text.replace({"en": "tomorrow", "fr": "demain", "ru": "завтра"}[locale], str(tomorrow))
                            yield {"id": fingerprint([captured, zone, variant, locale, family, "iso"]), "family": family, "input": {"content": absolute, "locale": locale, "timezone": zone, "capturedAt": captured}, "expected": expected, "label": "synthetic_template"}
                            missing_day = text.replace({"en": "tomorrow ", "fr": "demain ", "ru": "завтра "}[locale], "")
                            if clock.endswith(":00"): missing_day = missing_day.replace(clock, clock[:-3])
                            yield {"id": fingerprint([captured, zone, variant, locale, "missing_day"]), "family": "missing_day", "input": {"content": missing_day, "locale": locale, "timezone": zone, "capturedAt": captured}, "expected": {"kinds": ["event"], "requiresClarification": True}, "label": "synthetic_template"}
                        if index == 4:
                            # Paired ambiguous and explicit-choice samples retain the same group.
                            start = len(text[:text.rfind(str(number))].encode("utf-16-le")) // 2
                            hints = {"schemaVersion": 1, "clarifications": [{"start": start, "end": start+len(str(number)), "text": str(number), "kind": "datetime", "date": tomorrow.isoformat(), "time": "09:15"}]}
                            instant = datetime.fromisoformat(str(tomorrow)+"T09:15").replace(tzinfo=ZoneInfo(zone)).astimezone(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")
                            yield {"id": fingerprint([captured, zone, variant, locale, family, "clarified"]), "family": family, "input": {"content": text, "locale": locale, "timezone": zone, "capturedAt": captured, "interpretationHints": hints}, "expected": {"kinds": ["event"], "requiresClarification": False, "startAt": instant}, "label": "synthetic_template"}
                    multi = {"en": "Meet Nicolas tomorrow at 14:00 and prepare questions by today", "fr": "Rendez-vous demain à 14h00 et préparer les questions pour aujourd'hui", "ru": "Встреча завтра в 14:00 и подготовить вопросы до сегодня"}[locale]
                    yield {"id": fingerprint([captured, zone, locale, "multi"]), "family": "multi_intent", "input": {"content": multi, "locale": locale, "timezone": zone, "capturedAt": captured}, "expected": {"kinds": ["event", "task"], "requiresClarification": False, "records": [{"startAt": datetime.fromisoformat(str(tomorrow)+"T14:00").replace(tzinfo=ZoneInfo(zone)).astimezone(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")}, {"dueDate": str(local.date())}]}, "label": "synthetic_template"}
    challenges = {
        "en": ["Don't book the dentist on Friday", "dentst 16", "dentist 16, not 17", "Call maman demain at 4", "🦷 dentist 16", "Move the meeting from Friday to Monday", "Cancel tomorrow's meeting", "Pay 16.50 euros, receipt #20261028", "Meeting at 14 in London, I'm in Paris", "Finish report then send it; no deadline"],
        "fr": ["Ne pas réserver le dentiste vendredi", "dentiste seize", "dentiste 16, pas 17", "Appeler maman завтра à 4", "🦷 dentiste 16", "Déplacer le rendez-vous de vendredi à lundi", "Annuler la réunion de demain", "Payer 16,50 euros, reçu 20261028", "Réunion à 14h à Londres, je suis à Paris", "Finir puis envoyer le rapport, sans échéance"],
        "ru": ["Не записывать к стоматологу на пятницу", "стоматолг 16", "стоматолог 16, не 17", "Позвонить maman demain в 4", "🦷 стоматолог 16", "Перенести встречу с пятницы на понедельник", "Отменить завтрашнюю встречу", "Заплатить 16,50 евро, чек 20261028", "Встреча в 14 по Лондону, я в Париже", "Закончить отчет и отправить, срока нет"],
    }
    for locale, texts in challenges.items():
        for index, text in enumerate(texts):
            yield {"id": fingerprint([locale, text]), "family": "challenge-"+str(index), "input": {"content": text, "locale": locale, "timezone": "Europe/Paris", "capturedAt": dates[0]}, "expected": None, "label": "unlabeled"}
    for locale, text in {"en": "dentist at 16", "fr": "dentiste à 16", "ru": "стоматолог в 16"}.items():
        basic = {"content": text, "locale": locale, "timezone": "Europe/Paris", "capturedAt": dates[0]}
        yield {"id": fingerprint(["reported-missing-day", locale]), "family": "reported_missing_day", "input": basic, "expected": {"kinds": ["event"], "requiresClarification": True}, "label": "synthetic_template"}
        hint = {"schemaVersion": 1, "clarifications": [{"start": 0, "end": len(text.encode('utf-16-le'))//2, "text": text, "kind": "datetime", "date": "2026-11-16", "time": "16:00"}]}
        yield {"id": fingerprint(["reported-missing-day-clarified", locale]), "family": "reported_missing_day", "input": basic | {"interpretationHints": hint}, "expected": {"kinds": ["event"], "requiresClarification": False, "startAt": "2026-11-16T15:00:00.000Z"}, "label": "synthetic_template"}


def generate(db, variants):
    from sqlalchemy.dialects.postgresql import insert as pg_insert
    from sqlalchemy.dialects.sqlite import insert as sqlite_insert
    insert = pg_insert if db.bind.dialect.name == "postgresql" else sqlite_insert
    count, batch = 0, {}
    def flush():
        if batch:
            db.execute(insert(LearningSample).on_conflict_do_nothing(index_elements=["event_key"]), list(batch.values()))
            db.commit(); batch.clear()
    for case in diverse_cases(variants):
        key = "generated:v1:"+case["id"]
        batch[key] = {"id": uid(), "event_key": key, "group_key": "template:v1:"+case["family"], "origin": "synthetic", "event_kind": "template_case", "label_source": case["label"], "schema_version": 1, "created_at": now(),
                      "data": {"input": case["input"], "expected": case["expected"], "family": case["family"], "templateVersion": 1, "requiresHumanReview": case["expected"] is None}}
        count += 1
        if len(batch) >= 250: flush()
    flush()
    return count


def backfill(db):
    count = 0
    for note in db.scalars(select(Note).order_by(Note.id).execution_options(yield_per=200)):
        put(db, key=f"capture:{note.id}:{note.version}", group="note:"+note.id, owner=note.user_id, origin="cloud_note", kind="historical_capture", label="unlabeled",
            data={"input": snapshot(note), "snapshotType": "backfilled_current_state", "initialCaptureSnapshotAvailable": False})
        count += 1
    for history in db.scalars(select(History).order_by(History.id).execution_options(yield_per=200)):
        note = db.get(Note, history.note_id)
        if db.scalar(select(LearningSample.id).where(LearningSample.group_key == "note:"+note.id, LearningSample.event_kind.in_(["interpretation", "historical_interpretation"]), LearningSample.data["interpretationId"].as_string() == history.id).limit(1)):
            continue
        exact = note.version == history.input_version
        put(db, key="history:"+history.id, group="note:"+note.id, owner=note.user_id, origin="cloud_note", kind="historical_interpretation", label="model_prediction",
            data={"input": snapshot(note) if exact else None, "inputAvailable": exact, "historicalInputVersion": history.input_version, "prediction": history.proposed_data, "model": history.model_version, "status": history.validation_result, "interpretationId": history.id, "trainingTarget": False})
        count += 1
    for correction in db.scalars(select(Correction).order_by(Correction.id).execution_options(yield_per=200)):
        note = db.get(Note, correction.source_note_id)
        put(db, key="correction:"+correction.id, group="note:"+note.id, owner=note.user_id, origin="cloud_note", kind="historical_correction", label="user_edit",
            data={"input": None, "inputAvailable": False, "before": correction.previous_interpretation, "after": correction.corrected_interpretation, "correctionId": correction.id, "labelCaveat": "Historical edit; original revision snapshot and action category unavailable"})
        count += 1
    return count


def export_rows(db, output, scope):
    if output.exists(): raise ValueError("Refusing to overwrite an existing dataset export")
    query = select(LearningSample)
    if scope == "synthetic": query = query.where(LearningSample.origin == "synthetic")
    else: query = query.join(User, LearningSample.user_id == User.id).where(User.dataset_consent.is_(True), User.dataset_synthetic.is_(False))
    counts = Counter()
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("x", encoding="utf-8") as target:
        for row in db.scalars(query.order_by(LearningSample.id).execution_options(yield_per=200)):
            value = wire(row)
            value["split"] = "regression" if row.data.get("exposedEvaluation") else split_for(row.group_key)
            target.write(json.dumps(value, ensure_ascii=False)+"\n")
            counts[row.label_source] += 1
    return dict(counts)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    a = commands.add_parser("import-reports"); a.add_argument("paths", type=Path, nargs="+")
    b = commands.add_parser("generate"); b.add_argument("--variants", type=int, choices=range(1, 11), default=3)
    commands.add_parser("backfill")
    commands.add_parser("stats")
    c = commands.add_parser("export"); c.add_argument("--scope", choices=["synthetic", "contributed"], default="synthetic"); c.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    with SessionFactory() as db:
        if args.command == "import-reports": result = {"observationsProcessed": sum(import_report(db, path) for path in args.paths)}
        elif args.command == "generate": result = {"casesProcessed": generate(db, args.variants)}
        elif args.command == "backfill": result = {"historicalRowsProcessed": backfill(db)}
        elif args.command == "export": result = {"exportedLabels": export_rows(db, args.output, args.scope)}
        else:
            rows = db.execute(select(LearningSample.origin, LearningSample.event_kind, LearningSample.label_source, func.count()).group_by(LearningSample.origin, LearningSample.event_kind, LearningSample.label_source)).all()
            locale = LearningSample.data["input"]["locale"].as_string()
            locales = db.execute(select(locale, func.count()).group_by(locale)).all()
            result = {"total": sum(row[3] for row in rows), "locales": {a or "unknown": n for a,n in locales},
                      "uniqueTexts": db.scalar(select(func.count(func.distinct(LearningSample.data["input"]["content"].as_string())))),
                      "groups": [{"origin": a, "kind": b, "label": c, "count": n} for a,b,c,n in rows]}
        db.commit()
        print(json.dumps(result, ensure_ascii=True))


if __name__ == "__main__": main()
