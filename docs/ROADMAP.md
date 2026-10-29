# Roadmap

Last reviewed: **2026-10-28**. Implementation is delivered in the current checkout; acceptance is scoped, with live-provider/device/public-deployment gates still open. The two reproduced consistency defects are fixed with permanent regression coverage. [Features](FEATURES.md) owns statuses and evidence.

## Completed work and current boundary

The 2026-10-14 baseline had README/LICENSE/.gitignore and project instructions, with no app/backend. Documentation and reference pictures were established first. The later full MVP brief authorized development. Android, backend, migrations, real provider adapter, sync, all eight primary/secondary routes and opt-in floating capture now exist and run. Original screen concepts remain unchanged and separately labeled.

## Milestones

| Phase | Status | Delivered / remaining gate |
| --- | --- | --- |
| A — Foundation | Verified | One Android app module, compatible pinned build, design tokens, Room/schema, Hilt, navigation; debug/release/lint pass |
| B — Offline capture | Verified | Local note/outbox transaction, original text, Journal/manual organization, persisted drafts; offline force-stop/reopen and recreation tests pass |
| C — Backend | Implemented | Transactional success/failure lease fencing passes PostgreSQL regressions; public auth and operational profile implemented with test transports; live integrations/deployment and M2 remain open |
| D — End-to-end sync | Verified | Durable deferred snapshots, bounded rotating reconciliation and historical cursor replay now cover skipped siblings and older same-version acknowledgments in Room regressions; broader multi-device/load acceptance remains |
| E — Today and organizer | Implemented | Actual Room sections and interactions, Calendar/Tasks/Ideas/Profile/Review/Settings/editors; key UI/repository and fixture checks pass; full primitive/picker/export matrix remains |
| F — Floating capture | Implemented | Opt-in permission, specialUse lifecycle, shared capture, gestures/draft/IME/Back/rotation/revocation; API 36 emulator checks; physical/OEM/store gate open |
| I — Android physical package organization | Verified | Canonical physical layers, feature-owned Routes/UiState/components, unchanged SQL histories and persisted-worker-name compatibility; 187 backend / 43 JVM / 25 API 37 scenarios; builds/lint pass. [Package map](ANDROID_PACKAGES.md) |
| H — Architecture boundaries and compact capture | Verified | Four logical layers, feature MVVM/UDF, application ports/UoW and compact contextual actions; 179 backend / 43 JVM / 26 API 37 scenarios; builds/lint/import checks pass. [Migration record](ARCHITECTURE_MIGRATION.md) retains compromises and broader gates |
| G — Reliability/polish | In Progress | Migrations, version guards, durable failure states, bounded work, TLS/privacy boundaries, tests and handoff docs; representative performance/accessibility/live-provider gates open |

Acceptance groups:

- **M1 offline persistence: Verified**, including actual offline capture and process reopen.
- **M2 live editable AI interpretation: In Progress**. Live extraction/development and API/worker smoke pass; curated combined-interpreter score is 42/43. Android live editing/restart/delayed correction and suggestions approval remain to be exercised, along with the residual unnecessary quantity review.
- **M3 functional Room Today: Implemented**, with scoped automated/visual verification. Larger device/UX acceptance remains explicit.
- Floating capture is delivered on the tested API 36 configuration; no universal Android/OEM availability or Play approval is claimed.

## 2026-10-21 correction milestone

Delivered: paginated Tasks including undated/earlier/archive recovery; real Profile summaries; bounded persistent old-task review; multiday idea rotation; preserved idea-to-task provenance/search; informed cloud consent; strict provider schema negotiation and bare-hour validation; reusable reference-led design, all routes and refined native overlay. Bundled fonts/licenses, runtime comparisons and component goldens are included. No runtime UI framework or toolchain churn was introduced.

## Pending acceptance tasks

The 2026-10-27 audit found two release blockers. Both are now fixed: permanent PostgreSQL tests exercise expired-lease success/failure completion, and Room tests exercise deferred sibling merging plus older same-version acknowledgments. Migration replays historical feed gaps. Public rollout still requires the acceptance below. Historical evidence: [readiness audit](TESTING.md#2026-10-27-architecture-and-production-readiness-audit).

2026-10-21 functional corrections and reference-led UI are implemented; scoped visual/device verification is recorded in [Features](FEATURES.md) and [Visual audit](VISUAL_AUDIT.md). That increment preserved Room/DTOs; the subsequent clarification increment adds Room v4 and additive versioned hint DTOs while preserving the outbox contract. Live inference was configured and exercised on October 28; remaining acceptance is listed below.

1. Extend the [executed live API/worker checks](TESTING.md#2026-10-28-live-openai-extraction-and-worker-verification) to Android editing/restart/delayed correction and development approval; address conservative false review on undated quantity tasks and measure held-out quality.
2. Extend notification/export/clear checks beyond API 36; test export failures/large datasets, all generic field primitives, native DST picker and archive/restore. Notification capture, parsed export and clear cancel/confirmation already passed on the emulator.
3. Run physical-device/API 26–37/OEM overlay checks, secure-window suppression, lock/unlock, process/service stops, large fonts, small/landscape displays and TalkBack. Complete distribution declarations before Play release.
4. Measure local save latency on representative midrange hardware, scroll/frame behavior and prolonged-use memory; validate UI during 10–30-second provider delays. The 150-ms target remains a target.
5. Production delivery: choose HTTPS hosting, signed Android release, managed PostgreSQL backups, credential recovery/rotation and operational monitoring without note contents. Local smoke is not deployment approval.
6. Add paging/selection policy for datasets exceeding 200 daily/idea records or 50 provider-context types; keep existing pinned type versions and incremental sync compatibility.
7. Close the [MVVM source-review findings](ARCHITECTURE.md#2026-10-28-mvvm-source-review): guard manual-organization submissions, bind feedback to its Undo target, unify Calendar date/zone queries, restore small selection state after process death and move semantic commands/progress behind ViewModel/repository APIs. Add focused state-transition tests. This review implements no runtime refactor and does not change prior persistence acceptance.

October 28 private-capture correction (ADR-023): the user reported Journal-only results after the narrower clarification fix. Default private capture now uses pure local proposal planning and an atomic Room record/history transaction, with successive clarification, original-only capture and visible destination selection. F38 is verified in scope: 33 unit tests and eight strengthened API 37 scenarios pass, including actual Calendar/Tasks card visibility; screenshots were inspected and the main AVD updated without clearing data. Existing Journal-only notes are not bulk-reinterpreted. Stateless bottom navigation has its own file; this is not a performance fix. System ANRs and the reported CPU spike remain a separate diagnostic scope rather than an inferred Compose recomposition defect.

ADR-024 contextual intent choices for tentative single actions and Today/date shortcuts for known event clocks are verified in scope: 172 backend tests, 36 Android unit tests and 13 strengthened API 37 scenarios pass; native choices, exact new card IDs and rendered captures were inspected. The original 43-case and new 30-case local corpora pass and all observations are persisted. Per-item kind choices in mixed tentative drafts, process-restored selection state and universal natural-language classification remain open; local rules are bounded and private mode does not invoke a model.

## 2026-10-27 clarification and public-service increment

Implemented: deterministic live EN/FR/RU preview, highlighted temporal doubts, date/time/literal choices before event creation, immutable original text and versioned hint persistence; a shared 43-case Python/Kotlin acceptance corpus; durable incoming reconciliation and transactional worker fencing. The public account implementation supports confirmed email/password, recovery/revocation and Google with explicit linking. Operational additions include managed-call quotas, readiness/diagnostics, non-root containers, CI, optional release signing/TLS/runtime roles and isolated backup/restore tooling.

These additions do not close M2 or establish a public production deployment. Configure and exercise real SMTP/Google, HTTPS/DNS, release signing, encrypted off-host retention/restore, external monitoring, distributed abuse/load controls, privacy/retention policy and supported-device acceptance before public rollout. The user selected local-only interpreter measurement; no real-provider benchmark was run. Four-base-kind synthetic acceptance does not establish custom-type assignment quality.

## 2026-10-28 live verification

October 28 update: real gpt-4.1-mini was configured locally and actual inference was tested. The first 21/43 result exposed provider-contract defects; after the documented grounding/schema fixes, the combined interpreter passed 42/43 and real server smoke passed 16/16. The extra quantity-task review remains open. M2 stays In Progress for live Android editing/restart/delayed-correction and development approval, broader devices and held-out quality. No public rollout or live SMTP/Google acceptance occurred.

October 28 dataset increment: passive server input/prediction/edit collection, consent boundaries, synthetic QA retention and operator tooling are implemented. PostgreSQL contains 4,557 synthetic observations (394 distinct texts); extended live journal smoke passes 19/19. Training, held-out quality, real-world label adjudication, export retention operations and separate device acceptance of the contribution Settings control remain open. Event clarification save-resumption is verified by four focused API 37 device scenarios, including exact dentist at 16 native date selection and visible Calendar membership; physical-device/live-Android acceptance remains broader work.

## Documentation Definition of Done

Each implementation change updates its owning doc and relevant Features/Roadmap status; significant choices append a historical ADR. Code remains authoritative. Preserve conceptual pictures and label runtime fixtures. Check links/fragments/pictures and whitespace during handoff. [README](../README.md) remains the navigation index; [Running](RUNNING.md) and [Testing](TESTING.md) hold executed commands.

## Beyond MVP

Timed reminder scheduling/exact-alarm policy, recurrence occurrence generation, external calendars, global Search/project management, voice/images, advanced appearance, cloud backup/import, fine-tuning, vector retrieval and automatic type discovery are not delivered. Avoid adding inactive controls for these.
