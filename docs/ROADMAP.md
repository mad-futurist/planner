# Roadmap

Last reviewed: **2026-10-17**. Implementation is delivered in the current checkout; acceptance is scoped, with live-provider/device gates still open. [Features](FEATURES.md) owns statuses and evidence.

## Completed work and current boundary

The 2026-10-14 baseline had README/LICENSE/.gitignore and project instructions, with no app/backend. Documentation and reference pictures were established first. The later full MVP brief authorized development. Android, backend, migrations, real provider adapter, sync, all six primary routes and opt-in floating capture now exist and run. Original screen concepts remain unchanged and separately labeled.

## Milestones

| Phase | Status | Delivered / remaining gate |
| --- | --- | --- |
| A — Foundation | Verified | One Android app module, compatible pinned build, design tokens, Room/schema, Hilt, navigation; debug/release/lint pass |
| B — Offline capture | Verified | Local note/outbox transaction, original text, Journal/manual organization, persisted drafts; offline force-stop/reopen and recreation tests pass |
| C — Backend | Verified | FastAPI/auth, SQLAlchemy/Alembic/PostgreSQL, durable jobs, replaceable real HTTP provider; 31 tests and Docker/fresh migration pass; live model quality remains an M2 gate |
| D — End-to-end sync | Verified | Ordered idempotent outbox, WorkManager/foreground refresh, transactional cursor, conflicts/corrections; real Android/backend compatibility passes with AI unconfigured |
| E — Today and organizer | Implemented | Actual Room sections and interactions, Calendar/Ideas/Review/Settings/editors; key UI/repository and fixture checks pass; full primitive/picker/export matrix remains |
| F — Floating capture | Implemented | Opt-in permission, specialUse lifecycle, shared capture, gestures/draft/IME/Back/rotation/revocation; API 36 emulator checks; physical/OEM/store gate open |
| G — Reliability/polish | In Progress | Migrations, version guards, durable failure states, bounded work, TLS/privacy boundaries, tests and handoff docs; representative performance/accessibility/live-provider gates open |

Acceptance groups:

- **M1 offline persistence: Verified**, including actual offline capture and process reopen.
- **M2 live editable AI interpretation: In Progress**. Adapter and fixture/real-HTTP-unconfigured flows pass; no provider key/model was available. Run the documented live smoke before declaring this milestone Verified.
- **M3 functional Room Today: Implemented**, with scoped automated/visual verification. Larger device/UX acceptance remains explicit.
- Floating capture is delivered on the tested API 36 configuration; no universal Android/OEM availability or Play approval is claimed.

## Pending acceptance tasks

1. Configure a compatible real provider and run [live smoke](TESTING.md#live-provider-smoke-test): EN/FR/RU multi-intent/time semantics, editing/restart, delayed correction precedence, idea development/approval.
2. Extend notification/export/clear checks beyond API 36; test export failures/large datasets, all generic field primitives, native DST picker and archive/restore. Notification capture, parsed export and clear cancel/confirmation already passed on the emulator.
3. Run physical-device/API 26–37/OEM overlay checks, secure-window suppression, lock/unlock, process/service stops, large fonts, small/landscape displays and TalkBack. Complete distribution declarations before Play release.
4. Measure local save latency on representative midrange hardware, scroll/frame behavior and prolonged-use memory; validate UI during 10–30-second provider delays. The 150-ms target remains a target.
5. Production delivery: choose HTTPS hosting, signed Android release, managed PostgreSQL backups, credential recovery/rotation and operational monitoring without note contents. Local smoke is not deployment approval.
6. Add paging/selection policy for datasets exceeding 200 daily/idea records or 50 provider-context types; keep existing pinned type versions and incremental sync compatibility.

## Documentation Definition of Done

Each implementation change updates its owning doc and relevant Features/Roadmap status; significant choices append a historical ADR. Code remains authoritative. Preserve conceptual pictures and label runtime fixtures. Check links/fragments/pictures and whitespace during handoff. [README](../README.md) remains the navigation index; [Running](RUNNING.md) and [Testing](TESTING.md) hold executed commands.

## Beyond MVP

Timed reminder scheduling/exact-alarm policy, recurrence occurrence generation, external calendars, Search/full backlog, voice/images, advanced appearance, cloud backup/import, fine-tuning, vector retrieval and automatic type discovery are not delivered. Avoid adding inactive controls for these.
