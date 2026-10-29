# Features and implementation status

Last reviewed: **2026-10-28**. Code owns implemented behavior; [Concept](CONCEPT.md) owns intent. Status describes the evidence scope below, never universal device/provider coverage.

## Status contract

- **Planned** — intended, no delivered implementation.
- **In Progress** — implementation or required acceptance remains incomplete; identify the gap.
- **Implemented** — code exists and relevant build/source or focused behavioral checks establish it; full acceptance still has named gaps.
- **Verified** — the listed behavior passed executed checks in the stated environment. This does not imply untested devices/providers or exhaustive coverage.

Documentation is part of Done. Do not advance status from a picture, specification, or unexecuted command.

## Inventory

2026-10-21 correction/redesign increment is delivered with scoped verification; scoped screenshot/device evidence is recorded below. Baseline failures/skips and superseded results remain historical. [Visual audit](VISUAL_AUDIT.md) owns visual comparisons.

| ID | Feature | Status | Evidence / remaining scope |
| --- | --- | --- | --- |
| F41 | Canonical Android packages and feature ownership | Verified | Canonical layers, feature Routes/UiState/components and port/usecase/model ownership enforced; 187 backend / 43 JVM / 25 API 37 scenarios, builds/lint pass. SQL histories unchanged; actual legacy worker spec executes. [Package tree](ANDROID_PACKAGES.md) |
| F40 | Four-layer architecture and compact capture UI | Verified | Four logical layers, feature MVVM/immutable state, stateless navigation/content, constructor-injected workers and shared capture/approval; 179 backend / 43 JVM / 26 API 37 scenarios; debug/release/lint and inward import checks pass. Compatibility and remaining public/device gates: [migration record](ARCHITECTURE_MIGRATION.md) |
| F01 | Android foundation, layers, tokens and navigation | Verified | Debug/release builds, lint and API 36 launch/routes; the historical source review identified MVVM gaps; their migration and current acceptance are tracked by F40; release signing not configured |
| F02 | Local-first app capture and persistence | Verified | Repository atomic/private/disk tests, offline force-stop/reopen and instrumented UI capture/recreation |
| F03 | Paginated Journal and original text | Verified | 50-note queries; original text observed after offline reopen and Activity recreation; huge-dataset scroll gate pending |
| F04 | Manual interpretation/capture | Verified | Repository/instrumentation persistence; real-backend manual correction and complete/undo sync |
| F05 | Room-driven Today, Schedule/Anytime/tomorrow | Verified | DST/date predicate tests, local completion UI, persisted fixture rendering; only API 36 visual acceptance |
| F06 | Task editing/completion/undo/pin/reschedule/archive | Implemented | Completion/undo UI and repository tests; optimistic edit and duplicate-tap regression checks; all editor actions compile, broader picker/archive UX acceptance pending |
| F07 | Internal day Calendar | Implemented | Navigation and temporal tests; local day/picker UI; manual DST picker interaction/other-device acceptance pending |
| F08 | Ideas, stable daily selection and Later | Verified | Persistent selection/deferral repository tests and actual saved-idea rendering; archive/restore supported in Details |
| F09 | Develop idea and approved task conversion | In Progress | Real provider development/job passed live API smoke; atomic once-only/stale approval fixtures pass; live Android suggestions/approval UX acceptance remains |
| F10 | Bounded ambiguous-note Review | Implemented | Five-note SQL bound, persisted daily deferral and editable proposal title flow; automated backend ambiguity checks; full manual triage session pending |
| F11 | Settings, export/clear/diagnostics | Verified | API 36 connection/route, parsed 14-note/13-record export, clear cancellation and confirmed empty-Journal outcome; export failure/large datasets remain pending |
| F12 | Overlay opt-in, permission and lifecycle | Verified | API 36 real permission flow, foreground service, launcher/other-app availability and revocation cleanup; physical/OEM/Play gates pending |
| F13 | Floating shared-Room capture, gestures/draft/Back | Verified | Offline Clock-app capture appeared in Journal; keyboard, Back/draft, drag/snap and rotation checked on emulator; last insets change rechecked before handoff |
| F14 | Notification capture fallback | Verified | API 36 permission grant, notification tap focused the composer/IME, and disable checked; other-device coverage pending |
| F15 | Durable outbox and incremental sync | Verified | Durable incoming snapshots and rotating bounded reconciliation fix skipped siblings; Room regressions also protect newer same-version processing status against old acknowledgments; v4 migration replays historical feed |
| F16 | Authorized backend ingestion/storage/jobs/migrations | Verified | PostgreSQL claim and expired-lease success/failure fencing regressions pass; migration head e04c9f512a83 and local Docker readiness verified; deployment acceptance remains separate |
| F17 | Real extraction, multi-intent, EN/FR/RU, validation | Implemented | Live gpt-4.1-mini plus deterministic grounding/validation: 42/43 curated cases and 16/16 API/worker checks; one unnecessary Russian quantity review, held-out quality and broader Android acceptance remain |
| F18 | AI failures, bounds/retries/reprocess | Implemented | Lease race fixed; fixtures cover failure/retry/quota and worker readiness; persistent daily managed-call cap implemented; live-provider rate-limit acceptance remains open |
| F19 | Corrections/history and stale-result protection | Verified | Backend correction-during-extraction test; Android pending/confirmed merge and stale-editor tests; live delayed inference gate pending |
| F20 | Versioned Type Registry and generic fields | Implemented | Live registered Reading-task extraction and version retention pass; generic Android editor compiles; all primitive controls device matrix pending |
| F21 | Relevant correction examples and approved simple rules | Verified | Bounded retrieval, approved-owner rule/isolation tests; event rules still require temporal validation; personalization quality is unmeasured |
| F22 | Privacy, real identity and protected credentials | Verified | Private no-outbox tests; authorization/cross-owner/revocation checks; real Keystore enrollment; debug/release network config and backup exclusion checked; production hardening remains |
| F23 | Accessibility, responsiveness and performance | In Progress | Lifecycle queries, insets, keys, bounds, labels, dark tokens and timing diagnostic implemented; 140% font/360 dp rendering and dark goldens pass; representative latency/memory/TalkBack/delayed-AI matrix remains open |
| F24 | Complete task discovery, date-free views and recovery | Verified | Repository paging/earlier/undated tests; API 36 Someday → completion/Undo → Archived → Restore and recreation |
| F25 | Local/connected Profile and actual aggregates | Verified | Empty/real count repository test; actual API 36 Profile render and primary/secondary navigation; type-collection aggregation compiles |
| F26 | Persistent least-recently-shown idea rotation | Verified | Multiday deterministic repository regression, Later/Archive and kind-correction eligibility guards; Today render |
| F27 | Non-destructive idea conversion/provenance | Verified | Atomic repository preservation/search regression and actual HTTP linked-task sync; source-link/editing and preserved idea search passed on API 36 |
| F28 | Explicit informed cloud consent/private default | Verified | Fail-closed policy test and API 36 cancellation/private-no-outbox/explicit cloud capture acceptance; overlay consent guard implemented |
| F29 | Reference-led reusable UI and navigation | Verified | All required actual emulator captures reviewed against references, corrected fonts/navigation/shadows/week dots; light/dark component goldens pass; small/font-scale scope is in Visual audit |
| F30 | Bounded old-task return and triage | Verified | Stable three-slot review/snooze repository regression and API 36 Smaller action → linked task, original retained |
| F31 | Searchable paginated saved ideas | Verified | Bound title/details query, 50-row paging, repository search and actual API 36 search → convert → source link → edit |
| F32 | Live local interpretation and explicit temporal clarification | Verified | Shared EN/FR/RU corpus, 3 focused API 37 acceptance scenarios including actual underline tap, exact event, partial-date/raw fallback and edit invalidation; main composer only, wider accessibility/device coverage pending |
| F33 | Reproducible interpretation benchmark | Verified | Local 43/43 in Python/Kotlin; live combined interpreter 42/43 with initial/final reports retained; 16/16 real API/worker checks. Curated acceptance, not held-out/raw-model accuracy |
| F34 | Public email/password, recovery and Google sign-in | In Progress | Verified-email/Argon2id, reset/revocation, nonce/PKCE Google handoff and explicit password linking implemented; test transports pass, live SMTP/Google configuration and acceptance pending |
| F35 | Production configuration and operational checks | In Progress | CI/signing hooks, TLS/runtime-role profile, request budgets, worker readiness, diagnostics and backup-to-new-database tooling implemented; isolated backup smoke and config validation pass, deployment/live drills pending |
| F36 | Passive interpretation dataset and diverse synthetic examples | Implemented | Migration/import/generation/export executed: 4,557 observations, 394 distinct texts; live journal smoke 19/19, full backend 140 plus one added bulk regression pass; Android build/unit/lint pass; contribution Settings interaction acceptance remains open |
| F37 | Save submitted event after final date/time clarification | Verified | Stable debug build/unit/lint pass; four focused API 37 device scenarios pass, including immediate dentist at 16 submission, native date selection, exact Room event, selected day and visible Calendar entry; scoped emulator evidence in Testing |
| F38 | Apply private automatic capture and reveal saved records | Verified | 33 unit tests, debug/androidTest build and lint pass; eight strengthened API 37 scenarios pass, including ordinary private Send into actual Tasks/Calendar lists, original-only and mixed clarification; screenshots inspected and main AVD updated with -r; historical raw notes and broader device coverage remain separate |
| F39 | Contextual Task/deadline/Someday/Idea and event-date choices | Verified | 172 backend and 36 Android unit tests, build/lint and 13 strengthened API 37 scenarios pass; all three choices, known-clock Today, exact new card IDs and rendered screenshots verified; 43+30 local cases persisted; mixed tentative/per-device/process-restoration gates remain explicit |

Global Search, media/voice, external calendars, timed reminder delivery, recurrence expansion, cloud backup/import, fine-tuning and embeddings remain **Planned/outside delivered MVP**. Stored reminder/recurrence fields do not establish delivery.

## Verification ledger

Readiness audit (2026-10-27): current Android repository tests passed **24/24**, lint **0 errors/7 warnings**. A separate PostgreSQL probe demonstrated that a worker whose lease has expired can overwrite a newer worker's completed result; its isolated test schema was removed. A separate Room diagnostic reproduced a skipped incoming sibling update whose cursor was already consumed. At audit time F15/F16/F18 were therefore **In Progress**, superseding earlier labels; the subsequent fixes and permanent regressions supersede that defect state, as recorded in the current inventory. These diagnostic passes establish defects, not successful fixes; see [audit evidence](TESTING.md#2026-10-27-architecture-and-production-readiness-audit). Live AI remains unconfigured, and production operations/recovery/device gates are open. No application code was fixed in that review itself; the later implementation increment is recorded below.

Host recheck (2026-10-27): Docker images built, fresh-volume migrations reached `c81fa728ac09`, API/database became healthy and worker started. Backend suite passed 47 tests with its PostgreSQL case skipped; that case then passed separately against Compose, covering all 48 distinct backend tests across both runs. Android debug build initially stopped before compilation due to missing Java; the follow-up installed Temurin JDK 21, configured user JAVA_HOME/PATH and verified Gradle 9.6.0 starts on JVM 21.0.12.1. The subsequent check found Studio 2026.2.2.3/JBR 25.0.4. With the local socket-temp-directory workaround, Gradle installed platform 37.0 revision 2, Build Tools 36.0.0 and platform-tools 37.0.1, then **assembled the debug APK successfully** (42 tasks, 11m 31s); APK signature verification passed. User ANDROID_HOME/PATH are configured; an explicit-process adb version probe passed. Release/unit/lint/device checks were not rerun during this setup. Live AI remains unconfigured. This startup audit changes no feature statuses or milestones and does not replace historical Android/device evidence. Commands, warnings and environment gaps: [host recheck](TESTING.md#2026-10-27-host-recheck).

Historical documentation baseline (2026-10-14): eight owning docs plus README; 98 local references/26 heading fragments, five picture embeds, unchanged-image hashes and whitespace audit passed. This established documentation only; initial application statuses were Planned.

Historical executed evidence (2026-10-15, Europe/Paris):

- Android debug and unsigned release assemble successfully. Full debug lint passed with **0 errors, 10 warnings** (tooling/target updates, English literals and style suggestions); the overlay compatibility resize hint also produces a compiler deprecation warning.
- **15 Android repository/domain tests**, zero failures/skips: atomic/private capture, disk recreation, DST/time separation, completion/undo/dedup, stable idea, jitter, outbox ordering, stale AI/editor guards, scheduler failure, Room 1/2→3 migration preservation, and once-only/stale development approval.
- **5 Android instrumentation acceptance tests**, zero failures/skips on API 36 Medium_Phone: local capture/recreation, Room-driven completion/restore, six routes, manual interpretation/recreation, and actual PostgreSQL-backed HTTP enrollment/Keystore/manual correction/complete/undo and rejected-edit recovery without a configured provider.
- **31 backend pytest cases**, including real PostgreSQL concurrent claims; schema/multi-intent/multilingual/evidence, private input/body limit, HTTP adapter transport fixtures, auth/idempotency/rate limit, corrections, registry compatibility, job retries/leases/development revision, migration repeatability, approved-rule isolation and request budget.
- Docker API/worker images built and services started with migration ordering; actual /health reported database ok and aiConfigured=false. An independent fresh PostgreSQL container initialized and migrated to head; temporary smoke container was stopped afterward.
- API 36 manual overlay checks and actual offline app force-stop/reopen, export and clear checks are detailed in [Testing](TESTING.md#manual-device-evidence). One explicit QA fixture rendering test passed; its sample data is test-only and does not enter production startup.

Resolved failures are part of development history: unused tooling pulled an unavailable Material 3 version; native emulator rendering initially produced black images; a migration fixture assumed every exported entity had indices; lint caught local-properties escaping, explicit domain attributes and observable locale; an indefinite completion snackbar obstructed restoration. Relevant checks passed after correction. No unresolved failure is disguised as a pass.

Latest reproducible commands/results and remaining manual gates: [Testing](TESTING.md). Real-provider integration, physical devices, Play eligibility and measured midrange performance have **not** been verified. Documentation/link audits establish no additional runtime guarantees.

Final handoff audit (2026-10-17): 168 local references, 31 heading fragments, eight PNGs and five original-image hashes passed; source whitespace passed across 73 files. Final Android acceptance XML reports five tests, zero failures/errors/skips. An expired invitation during the session gap was replaced for the successful run.

## 2026-10-21 correction evidence

Android repository/domain suite: **24 passed**, zero failures/skips, including prior 1/2→3 migration preservation and all new query/rotation/provenance/review/privacy/count regressions. Device acceptance initially caught Profile tab restoring a secondary destination; after correction **10 passed** on the separate API 36 Planner_Visual_QA emulator, including real current Docker/PostgreSQL sync with AI unconfigured. Smaller-action provenance and preserved idea search/source-link editing also passed. Strict/fallback/provider failure and multilingual bare-hour fixtures bring backend coverage to **48 passed/0 skipped**. Both fixed light/dark component screenshot comparisons passed. Debug/release build and lint pass; latest lint has **0 errors, 7 warnings**. Live key/model flags are false; no live inference success is claimed.

## 2026-10-27 implementation increment

The current inventory supersedes the audit-only defect state above. Both reproduced consistency bugs now have permanent regressions. Stable Android debug/release builds, 28 unit tests and lint with 0 errors/8 warnings pass; focused API 37 clarification acceptance and final screenshot evidence are in Testing. Backend has 116 distinct passing tests across a full 115-test run plus the new raw-only regression. The 43-case local benchmark passes in Python and Kotlin. Public authentication and operations remain In Progress for live SMTP/Google, deployment, signing, monitoring/retention and device/load/security acceptance. No real-provider requests or public rollout occurred.

## 2026-10-28 live provider increment

The user authorized actual inference and configured a local backend key. Initial live results (21/43 benchmark, 8/16 API smoke) exposed evidence/clock/provider-wire defects. After ADR-020's provider-only fixes the combined interpreter passed **42/43**, all four entity sequences match, twelve events are exact/calendar-ready, and no expected-ambiguous input becomes calendar-ready. One Russian undated quantity task triggers unnecessary review and remains an open quality defect. Actual API/PostgreSQL/worker/OpenAI smoke passed **16/16**, including development, registered fields/versioning, rules, original preservation and review boundaries. Disposable test accounts were deleted and their sessions revoked. Keys never enter reports or APKs. Wider Android live acceptance and held-out quality remain open; M2 is not marked complete. See [live evidence](TESTING.md#2026-10-28-live-openai-extraction-and-worker-verification).
