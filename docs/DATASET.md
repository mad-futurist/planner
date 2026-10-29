# Passive interpretation dataset

Status: **Implemented, 2026-10-28**. Migration, PostgreSQL imports/generation, real-provider journal smoke, backend tests and Android build/lint passed; the new contribution Settings control has not had separate device interaction acceptance. This feature collects examples; it does not start training, publish data or automatically change the model.

The first local PostgreSQL snapshot contained **4,557 observations and 394 distinct input texts**: 4,356 generated template/challenge cases, 173 imported historical observations, 26 retained live synthetic-account events and two default-persisted benchmark cases. The later intent-choice increment brings the snapshot to **4,631 observations / 423 distinct texts**. Context variants reuse text across dates/timezones. Of the generated cases, 4,326 have template expectations and 30 deliberately remain unlabeled. These are not independently verified model answers. [Initial counts](assets/benchmarks/2026-10-28-dataset-summary.json), [intent-increment counts](assets/benchmarks/2026-10-28-intent-dataset-summary.json) and executed checks in [Testing](TESTING.md) record the scope.

## What is retained

PostgreSQL learning_samples is an append-only journal independent of current records. It stores input snapshots (original/current text, capture instant, locale/timezone, versioned clarification choices), model/context/prompt version, actual provider request format and prompt/schema hashes, bounded raw successful responses, validation/review/stale/error outcomes, and the record state after deterministic application. Provider errors retain safe status/error classes, not authorization headers or raw upstream authentication error messages. Repeated requests do not rewrite earlier observations.

Every server capture, manual record, extraction/development attempt, explicit AI preview, raw edit, interpretation correction, record edit and completion/undo is recorded. Predictions and edits share a note group. Corrections contain full before/after snapshots, JSON-pointer field diffs and the preceding interpretation ID when available. Later AI does not replace an observed correction; obsolete leased results are marked obsolete_lease and cannot apply records. Deletion during provider I/O cannot recreate an owner's examples.

Private LOCAL_ONLY notes and unsent drafts are never uploaded for this feature. Default private composer capture now retains its local-rules proposal in Room InterpretationHistory alongside original text and applied records; corrections remain local UserCorrection entries. It does not enter the PostgreSQL learning journal or service-wide export. The server journal records only information already explicitly sent to the backend. AI previews create no business note/event, but now retain an owner-scoped interpretation example; the confirmation explains this.

## Labels and scope

| Label source | Meaning |
| --- | --- |
| model_prediction | Observed AI proposal, including failed/review/stale outcomes; never automatically a training target |
| user_edit | User changed semantics/fields; useful supervision candidate, not independently adjudicated truth |
| user_manual | Explicit manual capture with records |
| user_action | Completion/undo; not a semantic interpretation label |
| synthetic_expected | Curated regression expectations plus separately retained actual output/checks |
| synthetic_template | Declared expectations constructed by a deterministic scenario template, independent of classifier output |
| unlabeled | Raw input or challenging synthetic text needing human review |

Generated examples cover EN/FR/RU, four base kinds, quantities, execution days/deadlines, explicit and incomplete events, short dates, ambiguous weekdays/numbers, multi-intent notes and paired explicit clarification choices. Context varies across six timezones, winter/summer clock transitions, year boundaries and leap years. Separate unlabeled challenges include negation, cancellation/rescheduling, spelling errors, emoji/UTF-16, mixed languages, decimal amounts and explicit foreign timezone references. Model correctness is not assumed for generated examples; expectations are retained separately from observations.

## Privacy, consent and deletion

Ordinary examples belong to one owner and follow account deletion. They are not included in service-wide contributed exports until that account explicitly enables learning-v1 consent. Consent defaults off, can be withdrawn, and is checked at export time rather than copied into old examples. Settings → AI organization → Contribute cloud examples shows the disclosure and current server choice; offline/unknown state is explicit. Revocation excludes future exports. Already exported files and backup retention require operator handling; no automatic anonymization or retrospective erasure of external copies is claimed.

Only operator-created synthetic QA owners can carry dataset_synthetic=true; public registration/ingestion cannot set it. Their samples are archived as ownerless synthetic data before the disposable account is deleted. Real account deletion erases its samples instead. Synthetic generation/import uses ownerless data directly. Existing reports may lack raw transport responses/input snapshots; import preserves that gap instead of inventing history. Historical backfill marks unavailable old input revisions.

## API

All routes require the existing bearer credential and derive ownership from it:

- GET /api/v1/dataset/summary: own counts, policy version and current contribution choice.
- GET /api/v1/dataset/examples?limit=50&cursor=...: own immutable examples, keyset pagination, maximum 100 rows and roughly 1 MiB per page (at least one row).
- POST /api/v1/dataset/consent: {enabled, policyVersion:"learning-v1"}, UUID Idempotency-Key. Existing and future account examples become eligible/ineligible for future contributed exports. There is no public cross-owner dataset endpoint.

## Operator commands

Run from backend with the database configured; no provider calls are made by these commands:

```powershell
$reportPaths = (Get-ChildItem ../docs/assets/benchmarks/*.json).FullName
uv run --frozen python -m app.dataset import-reports $reportPaths
uv run --frozen python -m app.dataset generate --variants 3
uv run --frozen python -m app.dataset backfill
uv run --frozen python -m app.dataset stats
uv run --frozen python -m app.dataset export --scope synthetic --output ../tmp/learning/synthetic.jsonl
```

Import and generation are repeatable with stable event keys. Stats contain counts, not note text. Export refuses to overwrite an existing file; JSONL is ignored by Git. --scope contributed exports only currently opted-in real owners. Such exports may contain personal text and require restricted storage and a retention policy. The dataset is not an anonymized public artifact.

Benchmark CLI now persists every case by default, including errors and raw provider traces when available. --no-persist is an explicit isolated/CI opt-out; CI uses it to avoid writing observations into its test database. Live API smoke marks its newly created QA owner synthetic, so business cleanup no longer destroys learning examples.

--corpus app/domain/assets/noncalendar_choices_v1.json selects the new 30-case intent-choice corpus. Reports include expected case fields, corpus ID, capture time and timezone so importing non-default case IDs retains their own context rather than inheriting the original corpus's date. kindChoices retains required user alternatives; a provisional task proposal is not a confirmed user commitment. The October 28 intent increment retained 43 regression results, 30 choice results and one partial pre-change reproduction: current PostgreSQL metadata is 4,631 synthetic observations / 423 distinct input texts. Earlier 4,557-row snapshots/exports remain historical and are not silently overwritten. Private UI choices remain local original/manual record data and are not uploaded by these synthetic operator commands.

## Grouping and future evaluation

All revisions/predictions/edits of a real note share note:ID. Generated translations and variants share a template-family group. Known benchmark/smoke cases are marked exposedEvaluation and exported as regression, not as unseen test data. Other groups receive deterministic train/validation/test suggestions so one group cannot cross splits. This is a grouping aid, not proof of held-out independence: cross-note correction context, near duplicates and template overlap still require dataset review. No training/evaluation score is claimed from generated rows alone.

Source: [models](../backend/app/persistence/models.py), [journal](../backend/app/learning.py), [operator tooling](../backend/app/dataset.py), [API](../backend/app/api/dataset.py), [migration](../backend/alembic/versions/f26a81d705bc_learning_dataset.py), [Settings control](../android/app/src/main/java/app/planner/features/DatasetControls.kt). Decision: ADR-021 in Decisions.


## Architecture migration verification

The final structural-refactor benchmark ran inside the rebuilt API container and persisted another complete 43-case synthetic local-rule run, including inputs, expectations and outcomes. [Migration benchmark report](assets/benchmarks/2026-10-28-architecture-local.json) records the run identifier and all results. The Android real-HTTP compatibility test used a separate operator-marked synthetic owner and manual captures/corrections; those observations follow the same append-only journal. Earlier counts above are dated snapshots, not a current database total. No private device notes, new paid model responses or training were used for this migration check.
