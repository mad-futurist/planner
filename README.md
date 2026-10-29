# Planner

A runnable offline-first Android AI organizer with a FastAPI/PostgreSQL backend. Capture first; keep original notes; return structured tasks, commitments and ideas through a calm Today screen. Room drives Android business data, and AI proposes information that deterministic, version-aware code applies.

The app includes Today, Calendar, Tasks, Ideas, Profile, Journal, Review, Settings, editable records, durable sync and opt-in floating capture. Android feature ViewModels publish immutable state through application ports; backend HTTP and worker workflows use application services and explicit units of work. See [architecture migration](docs/ARCHITECTURE_MIGRATION.md) for current verification and compatibility boundaries. The main composer previews EN/FR/RU interpretation offline, highlights doubts and asks for explicit date/time choices before creating ambiguous events. Default Private Send applies complete local proposals to records alongside original text/history; Original note only is explicit. Cloud automatic capture uses the provider, while floating capture stays raw. Public account code supports confirmed email/password, recovery and optional Google sign-in; live mail/Google acceptance requires configuration. It launches and supports private/manual capture without an AI key. Real OpenAI extraction and development were exercised on 2026-10-28: **42/43 curated interpreter cases and 16/16 live API/worker checks pass**. Wider live Android/device acceptance remains open.

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

The canonical Android package organization is verified: **187 backend tests, 43 Android JVM tests and 25 distinct API 37 scenarios passed**. Debug/test/release APK builds pass; lint has **0 errors / 9 warnings**. See the [physical package map](docs/ANDROID_PACKAGES.md) and [Testing](docs/TESTING.md) for scope, corrected attempts, Room/worker compatibility and remaining gates. Earlier real-HTTP/model checks retain their own dated evidence; this package-only follow-up did not request new paid inference.

The [passive dataset journal](docs/DATASET.md) preserves server inputs, model proposals, outcomes and user changes with separate provenance. The pre-refactor PostgreSQL metadata snapshot recorded **4,631 synthetic observations and 423 distinct input texts**; the earlier real-provider journal smoke passed **19/19**. Generated variants are not model-quality evidence. Local-only notes remain local; service-wide contribution requires separate consent, off by default. No training starts automatically.

For tentative actions such as maybe find ai course, the main composer offers Deadline / Someday / Idea. A known event clock offers Today / Choose date. These explicit local choices preserve original text; [30 curated choice cases](docs/assets/benchmarks/2026-10-28-noncalendar-choices.json) pass alongside the original 43-case regression. The new corpus tests local user-choice behavior, not live-model accuracy.

The [43-case synthetic local benchmark](docs/assets/benchmarks/2026-10-27-local.json) passes in Python and Kotlin: 14 tasks, 26 events, 3 ideas and 3 information records; 12 exact calendar events and 14 clarification cases. This is curated acceptance, not a general model-quality score.

The [real-provider benchmark](docs/assets/benchmarks/2026-10-28-gpt-4.1-mini.json) measures gpt-4.1-mini plus deterministic temporal/schema validation. One Russian quantity note triggers unnecessary review; this remains a quality issue.

Public deployment, live SMTP/Google, physical devices, broader live-provider acceptance, Play review and representative performance/accessibility remain open gates. Timed reminder delivery, recurrence expansion, external calendars, global Search/media, cloud backup/import and fine-tuning are not delivered.

## Documentation index

| Document | Owns |
| --- | --- |
| [Concept](docs/CONCEPT.md) | Product vision, principles, intended flows and scope |
| [Architecture](docs/ARCHITECTURE.md) | Actual packages, dependencies, layers and data flow |
| [Android packages](docs/ANDROID_PACKAGES.md) | Physical layers, feature ownership, complete source tree and extension rules |
| [Dependencies](docs/DEPENDENCIES.md) | Enforced inward imports, composition roots and compatibility exceptions |
| [Architecture migration](docs/ARCHITECTURE_MIGRATION.md) | Before/after structure, checks and remaining acceptance |
| [Features](docs/FEATURES.md) | Planned / In Progress / Implemented / Verified status and evidence |
| [Data model](docs/DATA_MODEL.md) | Entities, relationships, temporal semantics and migrations |
| [Screens](docs/SCREENS.md) | Navigation, interactions, states, concept pictures and runtime captures |
| [API](docs/API.md) | Endpoints, DTOs, errors, identity and synchronization |
| [Decisions](docs/DECISIONS.md) | Historical choices, rationale, trade-offs and rejected alternatives |
| [Roadmap](docs/ROADMAP.md) | Milestones, completed work and remaining acceptance |
| [Running](docs/RUNNING.md) | Reproducible startup/configuration and platform limits |
| [Testing](docs/TESTING.md) | Commands, executed outcomes and live-provider smoke procedure |
| [Dataset](docs/DATASET.md) | Passive input/prediction/edit collection, labels, contribution consent and synthetic examples |
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
