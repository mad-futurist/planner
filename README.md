# Planner

A runnable offline-first Android AI organizer with a FastAPI/PostgreSQL backend. Capture first; keep original notes; return structured tasks, commitments and ideas through a calm Today screen. Room drives Android business data, and AI proposes information that deterministic, version-aware code applies.

The app includes Today, day Calendar, Tasks, Ideas, Profile, Journal, Review, Settings, editable records, task completion/undo, durable sync and opt-in floating capture. It launches and supports private/manual capture without an AI key. The real provider adapter is implemented; **live AI remains unverified because no key/model was configured**.

## Run locally

Requires Android Studio supporting AGP 9.4 (Quail 4 / 2026.1.4 or newer), a compatible Gradle JDK, SDK 37 and Docker Desktop:
```powershell
if (!(Test-Path backend/.env)) { Copy-Item backend/.env.example backend/.env }
docker compose up -d --build
docker compose exec api uv run --no-sync python -m app.security.identity
.\android\gradlew.bat -p android :app:assembleDebug
```

Open android in Android Studio or install android/app/build/outputs/apk/debug/app-debug.apk with adb. The app works immediately offline. To connect, enter **http://10.0.2.2:8000** and the single-use invitation in Settings. Provider key/model stay in backend/.env; configure them, recreate API/worker and follow the live smoke procedure.

Complete SDK/environment, host/emulator networking, same-owner enrollment/renewal, provider, overlay, release and troubleshooting instructions: **[Running](docs/RUNNING.md)**. Enable Floating quick capture in Settings, grant Android's overlay permission, then switch to Home/another app; it hides inside Planner. Notification capture is a separate opt-in fallback.

## Verification

Debug and unsigned release builds pass; lint has no errors. The correction increment has **24 Android unit tests, 10 device acceptance tests, 2 component screenshot comparisons and 48 backend tests passing**; PostgreSQL/Docker checks and detailed results are in [Testing](docs/TESTING.md). Offline save/reopen, real HTTP sync without AI configuration and API 36 overlay behavior were exercised.

Physical devices, live-provider inference, Play review and representative performance/accessibility remain open gates. Timed reminder delivery, recurrence expansion, external calendars, global Search/media, cloud backup/import and fine-tuning are not delivered.

## Documentation index

| Document | Owns |
| --- | --- |
| [Concept](docs/CONCEPT.md) | Product vision, principles, intended flows and scope |
| [Architecture](docs/ARCHITECTURE.md) | Actual packages, dependencies, layers and data flow |
| [Features](docs/FEATURES.md) | Planned / In Progress / Implemented / Verified status and evidence |
| [Data model](docs/DATA_MODEL.md) | Entities, relationships, temporal semantics and migrations |
| [Screens](docs/SCREENS.md) | Navigation, interactions, states, concept pictures and runtime captures |
| [API](docs/API.md) | Endpoints, DTOs, errors, identity and synchronization |
| [Decisions](docs/DECISIONS.md) | Historical choices, rationale, trade-offs and rejected alternatives |
| [Roadmap](docs/ROADMAP.md) | Milestones, completed work and remaining acceptance |
| [Running](docs/RUNNING.md) | Reproducible startup/configuration and platform limits |
| [Testing](docs/TESTING.md) | Commands, executed outcomes and live-provider smoke procedure |
| [Design system](docs/DESIGN_SYSTEM.md) | Fonts/licenses, tokens, components and responsive rules |
| [Visual audit](docs/VISUAL_AUDIT.md) | Actual emulator captures and reference comparisons |
| [UX behavior](docs/UX_BEHAVIOR.md) | Links to the owning behavior specifications |

Project instructions: [AGENTS.md](AGENTS.md). Supplied pictures are unchanged **design references**; runtime captures are separately captioned and use explicit QA fixtures, not production sample data.

## Living documentation workflow

Documentation is part of the Definition of Done.

1. Read this index and relevant docs before editing; inspect actual models/interfaces/tests.
2. Record scope/status when work starts. Keep intended behavior separate from delivered behavior.
3. Update owning docs **with each** code, UX, architecture, schema or API change.
4. Append significant decisions with rationale and rejected alternatives; preserve superseded history.
5. Run relevant checks, record outcomes/limits and update Features/Roadmap. Do not claim execution from a plan, picture or mock.
6. Check links/pictures with tools/check_docs.py and whitespace before handoff.

Code is authoritative for implemented behavior; Concept owns intent. Screens owns interactions/loading/empty/error/offline behavior; Data model and API own contracts. Link these instead of duplicating competing specifications. Future agents must be able to identify what exists, why, source locations and remaining gates.
