# Testing and verification

Last reviewed: **2026-10-29 (Europe/Paris)**. The latest increment is first. Earlier results remain historical. Scope and feature statuses: [Features](FEATURES.md). Run Android instrumentation on a disposable emulator; it adds QA notes, and the explicit preview fixture replaces local app data.

## 2026-10-28 architecture migration and compact capture

This section supersedes earlier counts and unconfigured-provider statements for the migration. Architecture, interfaces, component hierarchy, dependency rules and compatibility exceptions are in [Architecture](ARCHITECTURE.md), [Dependencies](DEPENDENCIES.md) and [migration record](ARCHITECTURE_MIGRATION.md). Historical feature scores below remain scoped to their original run.

Final Android main-source verification: assembleDebug, assembleDebugAndroidTest, testDebugUnitTest, assembleRelease and lintDebug **passed in 17m03s**. JVM results: **43 passed**, 0 failures/errors/skips. Lint: **0 errors / 9 warnings**. APK release signing is still an operator gate. Only UI-test fixture/selector updates and whitespace cleanup follow this full build; production behavior remains fixed.

Final backend: **179 passed, 0 skipped**, 61.78 seconds, with PLANNER_TEST_POSTGRES_URL set to the existing local PostgreSQL database. The one existing Starlette/httpx deprecation warning remains. Tests cover accounts, learning/consent, ownership, idempotency including nested default/presence compatibility, feed-generated keys, rollback-isolated JSON, worker lease fencing and framework-free application boundaries.

Docker API/worker images were rebuilt against the final backend, retaining the database volume. Both /health and /ready pass with aiConfigured=true; runtime UID is 10001 and Alembic is f26a81d705bc (head). No SMTP/Google/public deployment was activated. A fresh synthetic-owner Android HTTP test passed **1/1** (18.236 seconds): raw-only cloud upload, manual organization, complete/undo, rejected-payload correction/retry and linked idea/task provenance, with an empty final outbox. It deliberately uses manual records, so a configured model is not invoked. The test signs out and clears its QA endpoint afterward.

Main Medium_Phone_API_37.0 update: adb install -r succeeded without clear/uninstall or fixture seeding. The first cold am start -W reported timeout; the process remained alive with an empty current-PID crash buffer. A follow-up returned Status ok, and Android activity metadata confirmed resumed, visible, reportedDrawn=true. This is successful update/display evidence, not a cold-start performance pass; personal note content was not read.

Final device total: **26 distinct passing API 37 scenarios** (13 capture + 11 organizer/editor + one routes/overlay/worker fixture + one real-HTTP case). The final organizer and test-APK/lint run spanned an overnight host pause; the reported 33,486-second/9-hour wall durations are not performance results. Final lint remains **0 errors / 9 warnings**. [Machine-readable verification](assets/benchmarks/2026-10-28-architecture-verification.json).

The final local-rule corpus rerun passes **43/43**, with 12 calendar-ready events, 14 clarification cases and no unsafe-calendar cases; [full report](assets/benchmarks/2026-10-28-architecture-local.json). All 43 observations/expectations were persisted as a synthetic benchmark run in PostgreSQL. This is exposed curated acceptance, not held-out quality or latency evidence. No new paid AI inference or training ran for the structural refactor.

The explicit API 37 fixture passed all eight routes, light/dark captures, actual private native-overlay saving and an unconstrained WorkManager factory probe. A constrained capture-sync job remained enqueued awaiting Android's validated network; the probe tests constructor configuration without changing production constraints. The preceding capture suite had 12 passes and one test timeout waiting for an offscreen Idea before scrolling. Room assertions had already verified the correct private idea and original text. The corrected test scrolls to the exact saved UUID first; that rerun passed all **13 capture cases**. Four of nine organizer cases also passed, while five legacy organizer checks required explicit raw mode, UI-driven snackbar dismissal, modal-close synchronization and exact-card selectors; the final organizer/editor rerun passed **11/11**, including both new editor regressions. Two new editor regressions passed **2/2**, 74.231 seconds: reopen after Save displays the current title, and Activity recreation retains the draft's old expected version and rejects overwriting a parallel correction. The final test-APK/lint build passed in 3m55s; production sources were unchanged.

Android uses the existing pinned toolchain. Sources stay unchanged while Gradle/lint run; previous internal lint PSI failures from overlapping edits are retained in the migration record. API 37 emulator checks do not establish physical/OEM, Play, performance, TalkBack or full live-AI acceptance. Live SMTP/Google, release signing, public infrastructure and deployment remain open.

## 2026-10-28 contextual intent and event-date choices

Directly reproduced the reported maybe find ai course classification as information before the change. The user clarified that this should offer Task + deadline / Someday / Idea, not force a kind; known dentist at 16 should offer Today or a selected date. Implemented ADR-024. Shared EN/FR/RU rules now recognize broader actions and emit optional kindChoices for tentative untimed/undated actions. Android private automatic application refuses unresolved choices. Date/type buttons before Send edit only the draft; after Send they resume that pending submission. An explicitly selected deadline stores dueDate without startAt/plannedFor; Someday stays undated; Idea resets stale search/page state. Known event clocks receive Today/Choose date shortcuts without a Someday shortcut. Unknown information has a preview Change type action and explicit Journal receipt. Mixed tentative drafts remain edit/split/original-only scope; complete mixed/temporal-only capture retains its earlier behavior.

Executed Python corpus tests: 75 passed. Full backend suite: **172 passed, 0 skipped**, including three real PostgreSQL concurrency cases, 146.40 seconds; only the existing Starlette/httpx deprecation warning remains. Added alternate-corpus report import coverage for exact input, expected kind choices, capture context and repeatability. Stable Android full build/unit/androidTest/lint passed: **36 unit tests** (34 repository + two corpus tests), **0 lint errors / 8 warnings**, 3m37s. A preceding 5m32s build also passed; the stable rerun incorporates the final context-menu filtering edit. No release or physical-device acceptance was run.

The [original 43-case local regression](assets/benchmarks/2026-10-28-local-intent-regression.json) passes **43/43**, preserving its entity/date outcomes. The [new 30-case choices corpus](assets/benchmarks/2026-10-28-noncalendar-choices.json) passes **30/30** in Python and its shared Kotlin corpus test. New corpus locales: EN 17 / FR 6 / RU 7. Its task base kinds are provisional where kindChoices is nonempty; these are not labels that maybe means a commitment. The two CLI runs saved all 73 results to PostgreSQL by default; the initial partial failing probe was also retained with an explicit missing-full-snapshot limitation. Current metadata snapshot: **4,631 synthetic observations / 423 distinct input texts**. No live model calls or training ran in this increment.

First expanded API 37 device suite: **OK (13 tests), 130.275 seconds**. Five new scenarios cover the exact phrase choosing Someday, Idea before Send, a native-picked task deadline, dentist Today with no Someday shortcut, and preview override of unknown information. Tests check raw retention, one final record, exact temporal fields and private no-outbox behavior. Choice-dialog image was visually correct, but two list screenshots caught an early native display frame despite semantic assertions. The final test revision adds record-ID test tags, waits for the newly created ID in the screen's ViewModel query, scrolls that exact card into view and waits for a committed render plus two display frames before screenshots. Final rerun and visual evidence are recorded after execution. These nonvisual component tags do not change application behavior.

Docker API/worker rebuilt without replacing the database volume; /ready reports ready with aiConfigured=true. Running-container hashes of benchmark, dataset tooling, preview code and shared rules match the checkout. Public preview metadata is additive; Room stays v4. Main-device update and final screenshots follow successful strengthened device verification. Existing private information notes are not bulk-reclassified automatically.

Final strengthened run: **OK (13 tests), 320.957 seconds**, on Planner_Clarification_QA API 37. The final debug/androidTest/lint build with nonvisual component tags and native-frame screenshot synchronization passed in 6m30s. Domain/repository logic was unchanged from the earlier passing 36-unit-test build. Tests identify the exact newly saved UUID in the screen query and card, scrolling as needed rather than accepting an older identical title. Final captures were pulled and visually inspected: [choice dialog](assets/screens/2026-10-28/maybe-course-choice.png), [new Someday task](assets/screens/2026-10-28/maybe-course-someday.png), [deadline task in Upcoming](assets/screens/2026-10-28/maybe-course-deadline.png). The earlier loading-frame captures were replaced. Repeated identical titles are distinct QA notes from separate runs, with exactly one record per tested capture. Wall-clock times on this loaded development host are not performance acceptance. Main AVD identity was verified and the final debug APK installed with -r and launched; no uninstall, clear or user-data instrumentation occurred. Source/doc/whitespace and exact-key checks are recorded at handoff; no further model calls were made.

## 2026-10-28 private capture application and CPU investigation

The user reported that ordinary notes still appeared only in Journal after ADR-022. Inspected the connected Medium_Phone_API_37.0, emulator-5554: its visible capture mode was Private. The earlier fix covered submitted ambiguous events, but default private capture with a complete preview still invoked raw-only repository capture. An undated manual task also did not select its Someday bucket. Implemented ADR-023: default private main-composer capture applies complete local proposals with original note and local history atomically; clarification resumes the same single/multi-intent submission; original-only remains explicit; task/event destination selection follows persisted semantics. Cloud/overlay behavior and Room v4 are retained. Bottom navigation was already a composable and was moved into its own file without changing its interface.

Stable main-source build: **33 unit tests passed** (32 repository cases plus the shared interpreter corpus), debug/androidTest assemble passed, lint **0 errors / 8 warnings**, 5m09s. Five new unit regressions cover multilingual local task/event persistence, multi-entity transactions/history/no-outbox, ambiguity/incomplete-proposal rejection without partial saves, deadline instants remaining dueAt, and correction/original preservation. Backend source did not change, so backend tests were not rerun in this increment.

Initial expanded disposable API 37 acceptance: **8/8 passed, 111.014 seconds**, including the previous four scenarios plus default private task visible in Tasks, default complete event, explicit original-only capture and mixed event/task clarification. Visual review caught an early Calendar loading frame: the title assertion could also match the composer/outgoing content. The final tests restrict title matching to descendants of calendar-list/tasks-list before asserting display and taking screenshots; final rerun evidence is recorded after execution. Synthetic QA only; no tests were run against user data. The initial passing debug APK was installed with -r on the verified Medium_Phone AVD and MainActivity launched; no data clear/uninstall or bulk reinterpretation occurred.

Final strengthened acceptance: **OK (8 tests), 85.853 seconds**. The rebuilt test APK requires matching the actual list ancestry, displayed card and focused Activity before capture. Both final images were pulled and visually inspected: [default private task in Someday](assets/screens/2026-10-28/private-default-task-in-tasks.png), [default private event in Calendar](assets/screens/2026-10-28/private-default-event-in-calendar.png). The loading-frame image was replaced by this verified Calendar capture. Identical cards represent separate synthetic notes from repeated runs and the mixed-input scenario; every scenario checks its newly created note and exact record count. The strengthened test-only rebuild passed in 4m38s; its only main-source difference from the earlier tested/installed APK was trimming an extra trailing blank line in Components.kt. No behavior changed between the two runs. Final documentation/image references and whitespace checks pass. No new claim is made for release/physical-device/performance acceptance.

CPU evidence: [content-free diagnostic snapshot](assets/benchmarks/2026-10-28-cpu-diagnostic.json). The user's 13:08:19–13:08:28 device-log window reports Planner 72% (15 user / 57 kernel), with total 99%. Reading the same emulator's am_anr event log found System UI service/input timeouts, keyboard startup timeout and com.google.android.as startup timeout at 13:08:00–13:08:51. dumpsys activity lastanr identified System UI StatusBar input timeout. A later three-frame top thread sample of the same Planner PID showed main-thread CPU 0/1/1%, all other Planner threads 0%, ~239 MiB resident memory. This does not reproduce or explain the earlier spike. Concurrent system failures support system contention as a hypothesis, not proof that Planner is innocent or the cause. No sustained high-CPU loop or CPU improvement is established by this short sample. The zero-duration 1970/0%-awake row cannot establish real load or note timestamps.

No root cause was assigned to navigation file organization or MVVM layering. A trace captured during recurrence is needed to distinguish running app work, runnable-but-unscheduled threads, binder/lock contention and system load; see [Android ANR diagnosis](https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs). This increment claims a functional capture fix, not a performance fix or representative-device acceptance.

## 2026-10-28 MVVM source review

Inspected Android entry/DI, Compose shell/screens/editors/account/dataset controls, the single PlannerViewModel, domain policy, repository, Room and sync APIs, plus unit/instrumentation test sources. Findings and source links: [Architecture](ARCHITECTURE.md#2026-10-28-mvvm-source-review), IDs MVVM-01–05. Counted 24 public MutableStateFlow declarations and found direct UI → DAO/service calls. No focused ViewModel state-transition unit tests are present; repository/domain unit tests and selected UI instrumentation exist. Manual-organization repeat submission, mismatched snackbar Undo, stale Calendar count bounds after timezone changes and process-restored selection resets are code-level findings; no new device reproduction or behavioral test was executed for them in this review. Runtime source/schema was unchanged. Existing build/test results below remain historical, not evidence that these newly identified schedules pass. Documentation checks are the only verification required for this documentation-only audit increment.

## 2026-10-28 passive dataset and clarification-save regression

Applied Alembic **f26a81d705bc (head)** to the existing local PostgreSQL volume and rebuilt API/worker. /ready returns ready with aiConfigured=true. Imported 173 historical synthetic observations; generated 4,356 unique template/challenge cases with three variants, six timezones and date/clock-boundary contexts; retained 26 events from a disposable live synthetic account; persisted a two-case local benchmark by its new default behavior. Current database/export: **4,557 observations, 394 distinct input texts**, EN 1,551 / FR 1,494 / RU 1,496 / unknown 16. Template expectations account for 4,326 rows; 30 generated challenge cases intentionally have no gold label. These counts measure stored examples/context variants, not model accuracy or unique natural utterances. Repeating generation preserves existing rows. Export to ignored tmp/learning-20261028-synthetic.jsonl succeeded without overwriting another file.

[Extended live journal smoke](assets/benchmarks/2026-10-28-learning-smoke.json): **19/19 checks passed** against the real configured model/API/worker/PostgreSQL. It verifies exact retained input, provider trace, extraction outcome, semantic user-edit diff, completion/undo action labels and all 26 synthetic samples surviving QA account cleanup; the deleted session returns 401. Only synthetic data was used. Real account samples instead follow account deletion; service-wide contributed export requires current explicit consent. Local-only notes/drafts are not uploaded. No training ran.

Full backend suite: **140 passed**, including three real PostgreSQL concurrency tests. An additional bulk generation repeatability regression was added afterward and passed separately: **141 distinct current tests covered across the two runs**. Tests cover ownership, consent default/revocation/idempotency, real/synthetic deletion, immutable before/after diffs, stale and invalid responses, safe provider traces, label separation, export grouping and bulk repeatability. Remaining warning: upstream Starlette/httpx deprecation. Stable Android debug + androidTest assemble, **28 unit tests** and debug lint passed. Separate device interaction acceptance of the new contribution Settings control remains open.

Reported dentist at 16 issue: final clarification previously changed only the draft. The fix resumes a pending single-event submission on Save event, retains parsed clock values from fresh analysis even on immediate Send and selects the saved event's Calendar day after the Room commit. Draft-only clarification still does not save; cancellation preserves the draft. The attached ANR belongs to com.google.android.gms.persistent and does not establish a Planner database failure.

Final focused instrumentation on disposable Planner_Clarification_QA, API 37 Google Play x86_64: **OK (4 tests), 94.768 seconds**. Scenarios cover bare-number confirmation with automatic save, date-only raw-note fallback, Russian underline/edit invalidation/private-AI guard, and immediate dentist at 16 submission with native date selection three days ahead. The last scenario verifies retained 16:00, exactly one event for the new note, unchanged original text, Room day-query membership, selected Calendar date and the visible event title. [Actual Calendar capture](assets/screens/2026-10-28/dentist-at-16-saved-calendar.png). The initial device run passed three cases and all fourth-case persistence/day assertions but failed at Espresso.closeSoftKeyboard because its root had not regained focus after dialog dismissal. The test now waits for dialog closure, hides IME through window insets and waits for navigation; the app source was unchanged between those two device runs. Stable main-source unit/debug/androidTest/lint build passed before them; the final test-only APK rebuild also passed. This establishes the reported offline main-composer flow on this AVD, not external-calendar integration or physical-device acceptance.

Visual review of that passing run caught a System UI ANR overlay during another emulator's boot. The final test additionally checks displayed text and Activity window focus before taking the Calendar screenshot. Repeated runs retain separate synthetic notes on the disposable AVD, so the visible-title assertion handles multiple identical QA titles; each run independently verifies exactly one event for its newly created note. One intermediate rerun failed only because the single-title selector matched three prior QA instances; this was corrected in the test. **Final strengthened suite: OK (4 tests), 101.129 seconds**; its clean screenshot was pulled and visually inspected, superseding the obstructed capture. Multiple identical cards in this QA image are separate notes from repeated runs, not duplicate records within one saved note. No further app-source change was required.

Verified the existing Medium_Phone_API_37.0 AVD identity on emulator-5554, installed the debug APK with adb install -r (Success) and launched MainActivity. No uninstall, app-data clear or user-note inspection was performed. Tests and synthetic QA notes were confined to the separate Planner_Clarification_QA AVD. The backend remains ready with AI configured. Documentation references/image checks, git diff --check and an exact-key scan of reviewable files pass; the configured key remains only in ignored local configuration.

## 2026-10-28 live OpenAI extraction and worker verification

The user authorized real AI requests. Saved the supplied key only in ignored backend/.env, selected project-accessible gpt-4.1-mini with the existing Chat Completions/strict-schema adapter, and recreated local API/worker without replacing the database volume. Model-list access and a synthetic strict-output preflight returned HTTP 200. The key is absent from source, reports and APKs. /ready now reports ready with aiConfigured=true; this is supplemented by actual inference evidence below, not treated as proof by itself.

The [initial live benchmark](assets/benchmarks/2026-10-28-gpt-4.1-mini-initial.json) passed **21/43**; [initial API smoke](assets/benchmarks/2026-10-28-live-smoke-initial.json) passed **8/16**. Real answers exposed generic evidence keys, incorrect Paris summer offsets in winter, and ambiguous strict field serialization. Fixed the provider-only schema/prompt and supplied bounded deterministic temporal grounding; see ADR-020. No postprocessing silently rewrites wrong model instants and the semantic validator remains mandatory. The initial report's static scope string incorrectly mentions local rules; its engine and per-case outputs are real model calls. The runner's wording is corrected for current reports.

The [final combined-interpreter benchmark](assets/benchmarks/2026-10-28-gpt-4.1-mini.json) passed **42/43 (97.7%)**, EN 20/20, FR 11/11, RU 11/12. All entity sequences match: **14 tasks, 26 events, 3 ideas, 3 information records**. Twelve events are calendar-ready; no expected-ambiguous input is calendar-ready after validation. Fifteen cases requested review versus fourteen expected. The remaining failure is **«Купить 16 яблок»**: it correctly remains a task without a date, but the model unnecessarily flags the absence of a date as ambiguous. This conservative false review remains an open quality issue; it was not removed from scoring. Median request time **4.485 s**, p95 **5.734 s**, on this host/run. This curated, previously used corpus is acceptance evidence, not held-out generalization or raw model accuracy. It measures model extraction plus deterministic schema/semantic checks and temporal constraints.

The [final live server smoke](assets/benchmarks/2026-10-28-live-smoke.json) passed **16/16** against actual local Docker API/PostgreSQL/worker and OpenAI: EN/FR/RU winter-clock events; untimed call task; dentist-16 review with no created record; explicit hint creating an exact event; multi-intent extraction; original text retention; raw-only capture; completion/undo; actual 3–5-step idea development; real registered Reading-task field extraction and retained schema version; approved rule; explicitly requested AI preview; incremental feed. Synthetic account cleanup succeeded and the deleted session returned 401. No personal notes were used. This does not establish Android live approval/restart/delayed-correction UX, physical devices, external calendar writes, SMTP or Google acceptance.

Added nine offline regressions for multilingual winter grounding/evidence keys, rejection of an injected wrong clock without rewriting it, ambiguous weekdays/bare numbers/DST gaps, exact UTF-16 choices and legacy JSON object compatibility. Final full suite: **125 passed, 0 skipped**, including three real PostgreSQL concurrency cases, in 116.72 seconds. The remaining warning is Starlette's upstream httpx test-client deprecation. An earlier full run had 124 passes and an intermittent Windows OSError (bad file descriptor reading pyproject.toml) in the Alembic subprocess; a focused rerun and then the final complete suite passed without an application/test source change. The Windows error's root cause was not established. Android source/schema did not change in this increment; earlier Android build/device evidence remains scoped to its date.

Explicitly billable reproduction (requires a configured local provider; never part of CI):

```powershell
Push-Location backend
uv run --frozen --python 3.12 python -m app.benchmark --engine provider --output ../tmp/live-provider-benchmark.json
Pop-Location
uv run --project backend --frozen --python 3.12 python tools/live_ai_smoke.py --output tmp/live-api-smoke.json
```

The first command directly uses the operator's configured provider and is outside per-owner managed-call quotas. The second is limited to a loopback API origin, bootstraps its own disposable Docker owner, uses normal API/jobs and managed-call accounting, and deletes only that account in finally. Do not enable live tests in unattended CI. Neither tool prints credentials; the report contains synthetic inputs/results. Backend remains running at http://127.0.0.1:8000 with its existing volume.

## 2026-10-27 implementation verification

This increment fixes the audit's reproduced defects. No live LLM, SMTP delivery or Google project was used; email/provider/Google tests use explicit synthetic transports, with cryptographic JWT verification also tested locally.

- Backend: full suite **115 passed**, including real PostgreSQL concurrent claims and expired-lease success/failure fencing. A subsequent focused raw-only ingestion regression also passed, for **116 distinct passing tests** across these runs. Auth coverage includes confirmation GET safety, password hash/recovery/session revocation, generic responses, limits, Google signature/audience/issuer/expiry/nonce/PKCE/replay and explicit linking, owner-isolated account deletion and production configuration. The fixed-minute limit test initially crossed a wall-clock minute; its clock is now fixed. The remaining pytest warning is Starlette's upstream httpx test-client deprecation.
- Local synthetic interpreter benchmark: **43/43 passed** in Python and the shared Kotlin corpus test; EN 20, FR 11, RU 12. Across 43 inputs there are 46 proposed records: tasks 14, events 26, ideas 3, information 3. Twelve events have exact calendar times; fourteen cases require clarification. Unsafe calendar cases: zero. [Full result](assets/benchmarks/2026-10-27-local.json) includes per-case fields/checks/confusion and host timings; timings are host measurements under development load, not Android performance acceptance. This curated corpus does not measure held-out generalization, custom registry-type assignment or AI quality.
- Android: debug and unsigned release assemble, focused main-composer acceptance on a fresh disposable API 37 Google Play x86_64 AVD, and debug lint were executed. Three acceptance scenarios verify pre-save dentist-16 clarification and exact confirmed event, date-only clarification with raw-note fallback, and tapping an actual Russian underline followed by editing invalidation/private AI guard. Old transitive Espresso 3.6 failed before interactions because API 37 removed reflective InputManager.getInstance; selecting stable Espresso 3.7 fixes it. Earlier concurrent source edits also caused a lint FIR analysis crash; stable-source lint passes. Unit regressions cover durable sibling replay, older same-version acknowledgment protection, Room 1/2/3→4 preservation/default hints, raw-only sync and the shared corpus. The final stable main-source build passed **28 unit tests** (27 repository cases plus the shared 43-case interpreter test), debug/release assemble and lint with **0 errors, 8 warnings**. The warnings are dependency/target/style suggestions; native overlay resize deprecation remains. Screenshot evidence is recorded below.
- Docker: rebuilt API/worker/migration images and started local Compose, database healthy, Alembic **e04c9f512a83 (head)**, runtime UID **10001**, /ready **ready** with aiConfigured=false. Production overlay config validates without a real production env; Caddy validates with callback/recovery query redaction. No production project was started. Synthetic backup/restore smoke passed using isolated temporary databases which were removed; no existing database was overwritten. Operator status reports heartbeat/queue metadata without notes.
- CI workflow and release-signing hooks are implemented, but no remote Actions run or operator-signed artifact is claimed. Historical API 36 acceptance/goldens below were not rerun as part of this focused API 37 increment. Physical devices, TalkBack, prolonged load, live SMTP/Google, actual encrypted off-host restore, monitoring and live-model inference remain open gates.

Reproduce backend checks from the repository root, setting a dedicated PostgreSQL URL to enable the three isolated-schema concurrency cases:

```powershell
$env:PLANNER_TEST_POSTGRES_URL='postgresql+psycopg://planner:planner@127.0.0.1:5432/planner'
uv run --project backend --python 3.12 pytest backend/tests -q
uv run --project backend --python 3.12 python tools/database_backup.py smoke
uv run --project backend --python 3.12 python tools/check_docs.py
```

Use the environment workaround in Running if needed, then run :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug. Install both debug APKs on a disposable test AVD and run:

```text
adb -s emulator-5560 shell am instrument -w -e class app.planner.CaptureClarificationAcceptanceTest app.planner.test/androidx.test.runner.AndroidJUnitRunner
```

The acceptance test writes synthetic QA notes to its target installation; do not run it on a personal-data device. ADB process exit alone is not proof of passing tests: inspect the runner's OK (3 tests). No personal notes were read for the benchmark. The dedicated AVD has independent data from the existing Medium_Phone installation.

Final focused device rerun on the raw-only fix: **OK (3 tests)**, 46.88 seconds. Actual [composer preview](assets/screens/2026-10-27/capture-preview.png) and [clarification dialog](assets/screens/2026-10-27/capture-clarification.png) were captured and visually inspected: highlighted 16, visible interpretation, explicit date/time/literal options and raw-save fallback, no clipped action labels. UUID suffixes and background records are synthetic repeat-run QA fixtures. This is not a full accessibility/screen-size matrix. Automatic approval review rejected the adb command to stop the disposable emulator with the reason blocked by policy; it was left running. Local Compose also remains running. Verification commands were executed separately afterward.

Library/config references: [AndroidX Test stable 3.7 release notes](https://developer.android.com/jetpack/androidx/releases/test), [Caddy log filtering](https://caddyserver.com/docs/caddyfile/directives/log). Validation of the proxy configuration does not replace a deployed log/privacy test.

## 2026-10-27 host recheck

Docker Desktop 4.95.0 / Engine 29.8.2 was already running, with no Planner containers/images/volumes. Created ignored backend/.env from its example without adding provider credentials. `docker compose config --quiet` and `docker compose up -d --build` passed; API/worker/migrate images built using Python 3.12.16. PostgreSQL and API became healthy, worker remained running, and migrate exited with code 0. `alembic current` reports `c81fa728ac09 (head)`. Actual HTTP `/health` returns `status=ok, aiConfigured=false`; `/openapi.json` exposes Planner API 1.0.0 with 19 paths. Services were left running with their persistent volume.

Host dependencies were installed with `uv sync --frozen --python 3.12` using Python 3.12.10 / uv 0.13.5. `uv run --frozen --python 3.12 pytest -q` finished with **47 passed / 1 skipped / 0 failures**; the PostgreSQL URL was not set for that invocation. After Compose startup, setting PLANNER_TEST_POSTGRES_URL and running `uv run --frozen --python 3.12 pytest -q tests/test_postgres.py` produced **1 passed / 0 skipped / 0 failures** against the running database using its unique temporary schema. Together these runs exercised all 48 distinct backend tests. The full suite emitted the existing Starlette/httpx deprecation warning; the PostgreSQL run reported a Windows permission warning while attempting to create its optional pytest cache. Neither warning failed a test.

Android `:app:assembleDebug` initially failed before compilation because JAVA_HOME was unset and Java was absent from PATH. The authorized JDK follow-up installed Eclipse Temurin **21.0.12.1+1** from the official Adoptium API's Windows x64 ZIP, checked SHA-256 against its published metadata, and set user JAVA_HOME/PATH to `%USERPROFILE%\.jdks\jdk-21.0.12.1+1` and its bin directory. `java -version` and `javac -version` passed; `gradlew.bat -p android --version` downloaded the pinned distribution and successfully reported **Gradle 9.6.0 with launcher/daemon JVM 21.0.12.1**. Existing terminal/app processes may require an environment refresh or restart.

The subsequent Studio check found a registered **Android Studio 2026.2.2.3** at `C:\Program Files\Android\Android Studio`; its bundled JBR **25.0.4** runs successfully, and Studio was running. SDK at `%LOCALAPPDATA%\Android\Sdk` initially contained emulator/system-image components and accepted license files. The Gradle retry installed **platform 37.0 revision 2**, **Build Tools 36.0.0** and **platform-tools 37.0.1** using those licenses. User ANDROID_HOME now points to the SDK and user PATH includes platform-tools; android/local.properties remains absent. Two initial shell `adb version` probes stalled and were stopped; a separate hidden-process invocation with explicit arguments succeeded with exit 0, reporting **adb 1.0.41 / 37.0.1-15733141**. No device acceptance run is implied by that version check.

Actual `:app:assembleDebug` retries on both Temurin 21 and JBR 25 initially failed while connecting to the Gradle daemon: `Unable to establish loopback connection`, caused by `UnixDomainSockets.connect` / `Invalid argument`. Creating `C:\Temp\planner-java` and setting process-local `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Temp\planner-java` allowed the next retry to connect. With Temurin 21 and the SDK location set, `gradlew.bat -p android :app:assembleDebug --no-daemon --console=plain --stacktrace` then **passed in 11m 31s**, with all **42 tasks executed**. This establishes a working local workaround, not the underlying Windows cause.

The generated `android/app/build/outputs/apk/debug/app-debug.apk` is **14.45 MiB**, application ID `app.planner`, version 0.1.0 (code 1). Build Tools `apksigner verify --verbose` passed with APK Signature Scheme v2. The build emitted the existing SOFT_INPUT_ADJUST_RESIZE deprecation and packaged two native libraries without stripping their symbols; neither stopped the debug build. Android release build, unit tests, lint and device tests were **not rerun** during this environment setup. Earlier results below describe the earlier environment. The missing-Java branch in gradlew.bat also falls through and emits extra invalid-JAVA_HOME/command errors after its first diagnostic; no wrapper change was made in this startup audit.

Live AI remains unexecuted: both LLM_API_KEY and LLM_MODEL are empty in the example configuration. Configure them and run the existing [live smoke procedure](#live-provider-smoke-test). No application/API/schema/architecture change was made during environment setup. The later readiness audit below records newly discovered defects and revises the affected acceptance statuses.

## 2026-10-27 architecture and production-readiness audit

The current checkout passed `:app:testDebugUnitTest :app:lintDebug`: **24 tests, 0 failures/errors/skips; lint 0 errors and 7 warnings**. Gradle reported success in 4m 55s. The checkout now also has a locally generated, untracked `android/gradle/gradle-daemon-jvm.properties` requesting Java 25; it was present before this audit and was not edited. These results supplement the debug APK and backend evidence above. Device acceptance, physical devices, release signing, live inference, load/performance and recovery/restore drills were not executed in this audit.

A controlled PostgreSQL probe reproduced a worker fencing defect in [worker completion](../backend/app/jobs/worker.py): an old worker pauses after reading/checking its lease token but before taking the owner lock; its lease expires, another worker claims and commits the current result, then the old worker resumes and overwrites that result. The observed final record contained the old result at version 2 with two job attempts. The probe used mock providers and a unique temporary PostgreSQL schema, which was removed afterward; no real provider requests or user rows were involved. The existing concurrent-claim and lease-reclaim tests do not exercise this completion window. **Unresolved production blocker:** revalidate ownership of the lease under appropriate transactional locking/fencing at completion and on failure updates.

A separate **Room/Robolectric diagnostic passed by reproducing an Android feed-loss defect**: [mergeRecord](../android/app/src/main/java/app/planner/data/PlannerRepository.kt) discards updates whenever any operation is pending for the source note, while [the changes loop](../android/app/src/main/java/app/planner/sync/SyncEngine.kt) commits the advanced cursor. The probe queued a local edit to task A, applied a server update to confirmed sibling task B with the production merge/cursor transaction sequence, then acknowledged A. Cursor 42 was retained while B remained at version 1 instead of the remote version 2. No durable deferred-incoming queue or forced re-fetch of those skipped entities exists. This was an isolated repository/Room reproduction, not a full two-device HTTP acceptance run. **Unresolved production blocker:** retain/reconcile skipped incoming updates before treating their cursor range as consumed.

The diagnostic source and init script live under ignored `tmp/codex-production-audit-20261027`; they were injected through Android's Kotlin test source set without changing production/test sources or checked-in Gradle configuration. The targeted run reported one diagnostic test, zero failures/errors/skips, and Gradle success in 7m 41s. Its assertions describe the defect, so a green diagnostic is **not** a fixed-product regression pass. The PostgreSQL probe is in the same ignored directory. Convert both schedules to permanent regression tests when implementing fixes. F15/F16/F18 and the corresponding roadmap milestones are reopened below; prior passing test evidence remains historical and scoped.

Operational readiness is still incomplete: the repository has a local Compose setup, but no checked-in CI pipeline, production TLS/deployment configuration, tested backup/restore automation, worker-lag/provider-cost monitoring or server-side deletion/retention workflow. Operator-issued 90-day device enrollment is the implemented access model; automatic renewal/recovery is not delivered. Health checks confirm database access and configured AI flags, not worker progress or successful provider inference. These are named release gates, not claims that an external production environment was inspected.

## Executed results

2026-10-21 baseline: Android debug and original **15 unit tests passed**; backend **30 passed/1 PostgreSQL skip** while the local database service was stopped. Restoring that service enabled the PostgreSQL fixture.

Current correction evidence: **24 Android repository/domain tests passed**, zero failures/skips, including original 1/2→3 migration preservation; **48 backend tests passed**, zero skips, including actual PostgreSQL claims. Debug/unsigned release build and lint pass: **0 errors, 7 warnings**. The current host is Studio 2026.2.2/JBR 25.0.4; bytecode targets Java 17.

**10 device acceptance tests passed** on the separate API 36 Planner_Visual_QA emulator, including private save/recreation, all eight routes, completion/Undo, manual interpretation, actual enrolled HTTP sync/rejected-edit recovery/provenance, consent cancellation/allow, undated task/archive recovery, grouped settings/cancel, smaller-action review and preserved idea search/source editing. The initial navigation run caught Profile incorrectly restoring a secondary page; it passed after correction. No existing user emulator data was cleared.

The explicit full runtime fixture/audit passed at both 1080×2400 / font 1.0 and 945×2100 / font 1.4 (all required screen captures plus native overlay save to Room). The empty/private-first-use fixture also passed. **Two component golden comparisons passed** after visual review, separately from their recording run. Small-screen/font-scale results are recorded in [Visual audit](VISUAL_AUDIT.md). Live provider remains blocked: current health/config reports `aiConfigured=false`; transport fixtures and schema requests are not live inference.

Historical October 15/17 checks (not a replacement for the current results above):

| Check | Actual result / environment |
| --- | --- |
| Android assembleDebug + assembleRelease | Passed; Java 21, AGP 9.4.1 / Gradle 9.6.0, compile 37 / target 36 / min 26; release APK unsigned |
| Android testDebugUnitTest | **15 passed**, no skips/failures; Robolectric SDK 35 with real Room SQLite |
| Android lintDebug / release vital lint | Passed; debug **0 errors, 10 warnings**; tooling/target updates, English UI literals, KTX/accessibility suggestions; native resize compatibility hint has a compiler deprecation warning |
| AppAcceptanceTest on Medium_Phone API 36 x86_64 | **5 passed**, no skips/failures, including actual Docker/PostgreSQL HTTP enrollment, Keystore, manual correction, completion/undo and recovery after rejected edits with AI unconfigured |
| PreviewFixtureTest, explicit preview=true | **1 passed** separately; QA-only persisted Today dataset, never production startup |
| Backend pytest | **31 passed**, no skips/failures, including final parent-feed/relevance regressions |
| PostgreSQL concurrency | Eight distinct claims with leases; dedicated temporary schema removed |
| Alembic / Docker | API/worker images build; DB/API healthy, worker running; fresh independent PostgreSQL initialization + migration to head passed |
| Live provider | **Not executed**; no key/model configured; fixture transport tests are not live integration |
| Physical device / Play review | **Not executed** |

Backend tests use Python 3.12.13 on the host; container uses Python 3.12.15. pytest emits one Starlette deprecation warning recommending httpx2 for its TestClient; the actual adapter uses httpx and its fixture tests pass.

## Reproduce automated checks

From repository root, with SDK/JDK configured:
```powershell
.\android\gradlew.bat -p android :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
```

Backend:
```powershell
Push-Location backend
uv sync --frozen
$env:PLANNER_TEST_POSTGRES_URL='postgresql+psycopg://planner:planner@127.0.0.1:5432/planner'
uv run --frozen pytest -q
Pop-Location
```

The PostgreSQL test creates/removes a unique schema, without deleting business rows. Without that variable it is skipped: report that skip, not a 31-case PostgreSQL pass. SQLite fixtures use temporary files.

Android acceptance, with a running API/worker and a fresh same-owner invitation (keep it out of logs):
```powershell
$taskInvite=(Get-Content path/to/local-invitation.txt -Raw).Trim()
.\android\gradlew.bat -p android :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.planner.AppAcceptanceTest' "-Pandroid.testInstrumentationRunnerArguments.invite=$taskInvite" '-Pandroid.testInstrumentationRunnerArguments.serverUrl=http://10.0.2.2:8000'
```

Issue invitations through [Running](RUNNING.md). The real-backend case is skipped without an invitation and assumes AI is **unconfigured**; do not run it against a configured provider expecting that assertion to pass. Gradle's device runner may uninstall test/target packages during cleanup. Reports are generated under android/app/build/test-results, reports and outputs/androidTest-results.

To generate the separate preview on a **disposable emulator only**:
```powershell
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class app.planner.PreviewFixtureTest -e preview true app.planner.test/androidx.test.runner.AndroidJUnitRunner
adb shell am start -n app.planner/.MainActivity
```

The preview clears local notes/credentials/connection and seeds manual private records solely in androidTest. Capture screenshots after the actual Today UI appears; caption them as fixtures. No test provider or sample seeding is included in the production app.

### Current visual and component checks

Use a **disposable** API 36 emulator. The preview fixture clears its local Room data, endpoint and credentials; it must not run on a personal device/dataset. This session used `Planner_Visual_QA` on `emulator-5556`; check the AVD identity before selecting that serial.

```powershell
$taskAdb="$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
$taskQa='emulator-5556'
& $taskAdb -s $taskQa emu avd name
& $taskAdb -s $taskQa install -r android/app/build/outputs/apk/debug/app-debug.apk
& $taskAdb -s $taskQa install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $taskAdb -s $taskQa shell appops set app.planner SYSTEM_ALERT_WINDOW allow
& $taskAdb -s $taskQa shell am start -W -n app.planner/.MainActivity
& $taskAdb -s $taskQa shell am instrument -w -e class app.planner.PreviewFixtureTest -e preview true -e audit true app.planner.test/androidx.test.runner.AndroidJUnitRunner
& $taskAdb -s $taskQa pull /sdcard/Android/data/app.planner/files/visual-audit docs/assets/runtime/2026-10-21/
& $taskAdb -s $taskQa shell am instrument -w -e class app.planner.DesignSystemScreenshotTest app.planner.test/androidx.test.runner.AndroidJUnitRunner
```

The first command must identify the disposable QA AVD. The audit expects `OK (1 test)`; goldens expect `OK (2 tests)`. The adb process exit code alone is insufficient: inspect runner output for failures/crashes. The fixture records private manual data only, verifies an actual overlay save and disables its service afterward. Use `-e empty true` with `preview=true` for the private first-use state.

Component goldens are in `android/app/src/androidTest/assets/goldens`. They pin the API 36 / density 420 / font scale 1.0 / GMT environment. For an intentional design change, record with `-e recordGoldens true`, pull `.../files/goldens`, visually inspect both themes, then rebuild the test APK and run normal comparison. A failed comparison must not update its own baseline. Current tolerance: at most 0.5% pixels with any channel differing by more than 16. The goldens are cropped fixed components, not reference-image equality tests.

For responsive capture, set `wm size 945x2100` and `settings put system font_scale 1.4` on the disposable device, run the audit, pull into a separate directory, then **always restore** `font_scale 1.0` and `wm size reset`. These overrides never touched the user's existing emulator.

Documentation:
```powershell
uv run --project backend python tools/check_docs.py
git diff --check
```

[check_docs.py](../tools/check_docs.py) verifies local references/fragments, PNG headers/dimensions, trailing doc whitespace and hashes of the five original concepts. It does not establish runtime correctness.

## Manual device evidence

Executed on API 36 emulator:

- Disabled Wi-Fi/data; captured an app note, force-stopped/reopened Planner, and read the exact original in Journal.
- Real Display over other apps permission flow; opted into foreground overlay; bubble appeared on launcher and above Clock.
- Offline overlay captured multiline text into shared Room and Journal showed the exact text; send confirmed locally.
- IME opened; Back collapsed; draft survived reopening. Drag snapped to an edge and position survived display changes.
- Forced landscape/portrait change preserved draft and service without a crash. Permission revocation removed the service; re-grant/reopen restored supported availability.
- Reopening Planner hid the bubble without duplicate windows; Settings disable stopped its service.
- Granted notification permission; opted into fallback; tapping Planner quick capture opened a focused composer with IME; disabling removed it.
- Exported JSON with schemaVersion=1, **14 notes and 13 records**, including the exact private overlay capture; pulled and parsed the completed file. Clear-data cancellation preserved that note.
- On October 17, confirmed Clear local data through the actual dialog; Journal showed its empty state, the overlay service was absent, and the app remained usable.

Tests do not establish secure-window suppression on every OEM, exact process-survival behavior, foreground-service store approval or physical-device compatibility.

## Live-provider smoke test

**Unexecuted procedure**; use a disposable enrolled account and non-sensitive examples. Configure compatible key/model through [Running](RUNNING.md), recreate API/worker and verify aiConfigured=true. That flag alone is insufficient.

1. In Profile → Settings → AI organization & connection, enroll using the local server URL/invitation. Tap the composer privacy row, read the explanation, and explicitly allow Cloud AI for new notes. Capture “Meet Nicolas tomorrow at 14:00 and prepare questions by today”; verify local saving first, then a real stored event and separate task, dates resolved from capture timezone, original text preserved.
2. Also capture “Meet Thursday at 14”, “Rendez-vous jeudi à 14” and “Встреча в четверг в 14”; require local 14:00 with original evidence, no invented duration. Repeat French “Rendez-vous avec Nicolas demain à 14h00” and Russian “Встреча с Николя завтра в 14:00”; inspect correct local clock/day and Unicode.
3. Capture “Call the doctor tomorrow”; expect an untimed task, no invented appointment hour/duration. Capture an ambiguous “next Friday” expression; verify review instead of guessing.
4. Edit kind/title/date while extraction is in flight; verify delayed output cannot overwrite correction. Restart app/API/worker, then confirm records/corrections and ordered outbox remain.
5. Verify Today/Calendar inclusion, complete/undo and daily recap while online/offline.
6. Save an eligible idea, Develop, verify 3–5 **real** proposals, select/approve some and verify once-only task creation. Edit idea during development; stale approval must fail. Missing key, timeout and provider error must preserve the idea.
7. Add/version a type through authenticated API; verify new extraction, generic editing and existing pinned-version records. Approve a simple rule; verify owner isolation and bounded correction context.
8. Record provider/model identifier, environment, inputs, observed outcomes and limitations without secrets or private data. Advance F09/F17/M2 only after actual success.

## Remaining acceptance

- Export cancellation/failure/large datasets, all generic primitives, native DST picker and archive/restore.
- API 26–37, physical/OEM devices, small/large fonts, dark mode, TalkBack/focus, lock/unlock/secure windows, service/task-manager stops.
- Simulated 10–30-second provider delays with Android UI interaction; representative under-150-ms local confirmation, scrolling/frame and prolonged-use memory measurements.
- Extended multi-device conflicts/network outages and production HTTPS/backups/signing/credential recovery.

Related: [Roadmap](ROADMAP.md), [API](API.md), [Screens](SCREENS.md).

## Verification incidents and scope

The new device tests initially exposed Profile restoring a secondary destination, and a test attempted navigation before an asynchronously saved dialog had closed. Profile navigation was corrected; the test now waits for visible navigation after save/IME dismissal. Visual comparison caught and corrected variable-font weight, heavy shadows and a clipped filter count at 140% type.

One immediate post-install fixture launch reported `Process crashed` without a Java app crash trace. A normal cold Activity launch succeeded, and subsequent complete fixture, acceptance and golden runs passed. The fixture now stops leftover QA overlay state before reseeding. This observation is retained rather than counted as a pass; no physical-device or prolonged stability guarantee is inferred.

Current build/lint checks ran on the final Android sources. Backend/API/worker images were rebuilt with the provider/date corrections; local health remains database ok and `aiConfigured=false`. Live-provider smoke is still unexecuted, with its exact procedure above. Original reference hashes, runtime/golden PNG validity and documentation links are checked by `tools/check_docs.py`.
