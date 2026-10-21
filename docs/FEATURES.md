# Features and implementation status

Last reviewed: **2026-10-17**. Code owns implemented behavior; [Concept](CONCEPT.md) owns intent. Status describes the evidence scope below, never universal device/provider coverage.

## Status contract

- **Planned** — intended, no delivered implementation.
- **In Progress** — implementation or required acceptance remains incomplete; identify the gap.
- **Implemented** — code exists and relevant build/source or focused behavioral checks establish it; full acceptance still has named gaps.
- **Verified** — the listed behavior passed executed checks in the stated environment. This does not imply untested devices/providers or exhaustive coverage.

Documentation is part of Done. Do not advance status from a picture, specification, or unexecuted command.

## Inventory

| ID | Feature | Status | Evidence / remaining scope |
| --- | --- | --- | --- |
| F01 | Android foundation, layers, tokens and navigation | Verified | Debug/release builds, lint and API 36 launch/routes; release signing not configured |
| F02 | Local-first app capture and persistence | Verified | Repository atomic/private/disk tests, offline force-stop/reopen and instrumented UI capture/recreation |
| F03 | Paginated Journal and original text | Verified | 50-note queries; original text observed after offline reopen and Activity recreation; huge-dataset scroll gate pending |
| F04 | Manual interpretation/capture | Verified | Repository/instrumentation persistence; real-backend manual correction and complete/undo sync |
| F05 | Room-driven Today, Schedule/Anytime/tomorrow | Verified | DST/date predicate tests, local completion UI, persisted fixture rendering; only API 36 visual acceptance |
| F06 | Task editing/completion/undo/pin/reschedule/archive | Implemented | Completion/undo UI and repository tests; optimistic edit and duplicate-tap regression checks; all editor actions compile, broader picker/archive UX acceptance pending |
| F07 | Internal day Calendar | Implemented | Navigation and temporal tests; local day/picker UI; manual DST picker interaction/other-device acceptance pending |
| F08 | Ideas, stable daily selection and Later | Verified | Persistent selection/deferral repository tests and actual saved-idea rendering; archive/restore supported in Details |
| F09 | Develop idea and approved task conversion | In Progress | Real provider adapter/jobs and atomic once-only/stale approval unit test; live provider/suggestions UI acceptance unexecuted |
| F10 | Bounded ambiguous-note Review | Implemented | Five-note SQL bound, persisted daily deferral and editable proposal title flow; automated backend ambiguity checks; full manual triage session pending |
| F11 | Settings, export/clear/diagnostics | Verified | API 36 connection/route, parsed 14-note/13-record export, clear cancellation and confirmed empty-Journal outcome; export failure/large datasets remain pending |
| F12 | Overlay opt-in, permission and lifecycle | Verified | API 36 real permission flow, foreground service, launcher/other-app availability and revocation cleanup; physical/OEM/Play gates pending |
| F13 | Floating shared-Room capture, gestures/draft/Back | Verified | Offline Clock-app capture appeared in Journal; keyboard, Back/draft, drag/snap and rotation checked on emulator; last insets change rechecked before handoff |
| F14 | Notification capture fallback | Verified | API 36 permission grant, notification tap focused the composer/IME, and disable checked; other-device coverage pending |
| F15 | Durable outbox and incremental sync | Verified | Stable IDs/order/retry/merge unit tests, real HTTP enrollment/correction/completion/undo instrumentation; prolonged multi-device outage gate pending |
| F16 | Authorized backend ingestion/storage/jobs/migrations | Verified | Backend tests, actual PostgreSQL concurrent leases, Docker startup and fresh-database migration smoke |
| F17 | Real extraction, multi-intent, EN/FR/RU, validation | In Progress | Real HTTP adapter, schema/evidence/clock/DST/risk checks and deterministic fixtures pass; no live model configured |
| F18 | AI failures, bounds/retries/reprocess | Verified | Unconfigured, timeout/429, invalid response, retry/lease and request-budget tests; actual unconfigured sync; live-provider rate-limit acceptance pending |
| F19 | Corrections/history and stale-result protection | Verified | Backend correction-during-extraction test; Android pending/confirmed merge and stale-editor tests; live delayed inference gate pending |
| F20 | Versioned Type Registry and generic fields | Implemented | Backend adding/versioning type regression and retained records; generic Android editor compiles; all primitive controls device matrix pending |
| F21 | Relevant correction examples and approved simple rules | Verified | Bounded retrieval, approved-owner rule/isolation tests; event rules still require temporal validation; personalization quality is unmeasured |
| F22 | Privacy, real identity and protected credentials | Verified | Private no-outbox tests; authorization/cross-owner/revocation checks; real Keystore enrollment; debug/release network config and backup exclusion checked; production hardening remains |
| F23 | Accessibility, responsiveness and performance | In Progress | Lifecycle queries, insets, keys, bounds, labels, dark tokens and timing diagnostic implemented; representative latency/memory/font/TalkBack/delayed-AI matrix unexecuted |

Standalone Tasks, Search, media/voice, external calendars, timed reminder delivery, recurrence expansion, cloud backup/import, fine-tuning and embeddings remain **Planned/outside delivered MVP**. Stored reminder/recurrence fields do not establish delivery.

## Verification ledger

Historical documentation baseline (2026-10-14): eight owning docs plus README; 98 local references/26 heading fragments, five picture embeds, unchanged-image hashes and whitespace audit passed. This established documentation only; initial application statuses were Planned.

Current executed evidence (2026-10-15, Europe/Paris):

- Android debug and unsigned release assemble successfully. Full debug lint passed with **0 errors, 10 warnings** (tooling/target updates, English literals and style suggestions); the overlay compatibility resize hint also produces a compiler deprecation warning.
- **15 Android repository/domain tests**, zero failures/skips: atomic/private capture, disk recreation, DST/time separation, completion/undo/dedup, stable idea, jitter, outbox ordering, stale AI/editor guards, scheduler failure, Room 1/2→3 migration preservation, and once-only/stale development approval.
- **5 Android instrumentation acceptance tests**, zero failures/skips on API 36 Medium_Phone: local capture/recreation, Room-driven completion/restore, six routes, manual interpretation/recreation, and actual PostgreSQL-backed HTTP enrollment/Keystore/manual correction/complete/undo and rejected-edit recovery without a configured provider.
- **31 backend pytest cases**, including real PostgreSQL concurrent claims; schema/multi-intent/multilingual/evidence, private input/body limit, HTTP adapter transport fixtures, auth/idempotency/rate limit, corrections, registry compatibility, job retries/leases/development revision, migration repeatability, approved-rule isolation and request budget.
- Docker API/worker images built and services started with migration ordering; actual /health reported database ok and aiConfigured=false. An independent fresh PostgreSQL container initialized and migrated to head; temporary smoke container was stopped afterward.
- API 36 manual overlay checks and actual offline app force-stop/reopen, export and clear checks are detailed in [Testing](TESTING.md#manual-device-evidence). One explicit QA fixture rendering test passed; its sample data is test-only and does not enter production startup.

Resolved failures are part of development history: unused tooling pulled an unavailable Material 3 version; native emulator rendering initially produced black images; a migration fixture assumed every exported entity had indices; lint caught local-properties escaping, explicit domain attributes and observable locale; an indefinite completion snackbar obstructed restoration. Relevant checks passed after correction. No unresolved failure is disguised as a pass.

Latest reproducible commands/results and remaining manual gates: [Testing](TESTING.md). Real-provider integration, physical devices, Play eligibility and measured midrange performance have **not** been verified. Documentation/link audits establish no additional runtime guarantees.

Final handoff audit (2026-10-17): 168 local references, 31 heading fragments, eight PNGs and five original-image hashes passed; source whitespace passed across 73 files. Final Android acceptance XML reports five tests, zero failures/errors/skips. An expired invitation during the session gap was replaced for the successful run.
