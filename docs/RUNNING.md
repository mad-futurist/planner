# Running Planner

Current verification environment (2026-10-21): Windows 11, Android Studio 2026.2.2 with bundled JBR 25.0.4 (previous checks used Java 21), Android SDK/platform 37, API 36 x86_64 emulator, Python 3.12, uv 0.12.2, Docker Desktop. Run PowerShell commands from the repository root unless stated otherwise.

## Backend with Docker Compose

Install/start Docker Desktop. Copy configuration **only if it does not already exist**:

```powershell
if (!(Test-Path backend/.env)) { Copy-Item backend/.env.example backend/.env }
docker compose up -d --build
docker compose ps
curl.exe http://127.0.0.1:8000/health
```

PostgreSQL persists in planner_db; migrate runs Alembic before API/worker. Loopback ports are 5432 and 8000. Health returns status=ok and aiConfigured=false until provider configuration exists. Open http://127.0.0.1:8000/docs for generated contracts. Restart with docker compose up -d; stop services with docker compose down. Avoid deleting the database volume when preserving notes/history.

Issue a single-use invitation locally:
```powershell
docker compose exec api uv run --no-sync python -m app.security.identity
```

Enter the displayed code in Android Settings with the backend URL. Invitations expire after 24 hours. Renew/issue another device for the same owner with --user-id EXISTING_OWNER_UUID; owner is returned by enrollment and stored in the database. Do not issue a new identity to replace an existing app dataset unless deliberately exporting/clearing it.

Only generic service/job errors belong in logs. Never paste invitations, bearer credentials, API keys or private notes into reports.

## Provider configuration

Edit ignored backend/.env:
```dotenv
LLM_BASE_URL=https://YOUR_COMPATIBLE_PROVIDER/v1
LLM_API_KEY=YOUR_BACKEND_ONLY_KEY
LLM_MODEL=YOUR_PROVIDER_MODEL_ID
LLM_TIMEOUT_SECONDS=30
LLM_RESPONSE_FORMAT=auto
WORKER_CONCURRENCY=2
MAX_JOB_ATTEMPTS=5
```

The adapter calls /chat/completions with strict JSON Schema by default. Auto mode falls back once only on an explicit unsupported-format error; json_schema disallows fallback and json_object selects validated legacy JSON mode. Both paths decode/validate before durable application. [API negotiation](API.md#error-and-job-handling) describes the unchanged extraction DTO and provider-only wire lists. Select a model/provider supporting that contract and max_tokens. This is replaceable backend integration; no provider key belongs in Android. Requests may incur provider charges.

After configuration:
```powershell
docker compose up -d --force-recreate api worker
```

A configured flag is not a successful live call. Previously unconfigured notes require Journal → Retry AI. Execute [the live smoke test](TESTING.md#live-provider-smoke-test) before marking live extraction/development Verified. No API key/model was available during development verification.

## Backend without containers for API/worker

Use uv and Python 3.12; Docker can still run PostgreSQL:
```powershell
docker compose up -d db
Push-Location backend
uv sync --frozen
uv run --frozen alembic upgrade head
uv run --frozen uvicorn app.main:app --host 0.0.0.0 --port 8000 --no-access-log
```

In another terminal, from backend: uv run --frozen python -m app.jobs.worker. Issue an invitation with uv run --frozen python -m app.security.identity. DATABASE_URL in .env defaults to loopback PostgreSQL; Compose overrides it to db. Do not run a host API and container API on the same port. Stop the host commands with Ctrl+C and return with Pop-Location.

## Android

Open the android folder in Android Studio Quail 4 (2026.1.4) or newer that supports AGP 9.4. Keep the pinned AGP 9.4.1 / Gradle 9.6.0; Studio 2025.2.1 only supports AGP 8.13. The current installed Studio 2026.2.2 bundled JBR 25.0.4 runs the build; Java 21 also passed earlier checks. Select the compatible bundled JBR in Settings → Build Tools → Gradle. Install SDK platform 37/build tools and an API 36 emulator. Minimum runtime is API 26; the executed device tests cover API 36.

Android Studio creates ignored android/local.properties. On Windows, a manual SDK path needs an escaped drive colon, e.g. sdk.dir=C\:/Users/NAME/AppData/Local/Android/Sdk. Set ANDROID_HOME or JAVA_HOME as appropriate for CLI use.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\android\gradlew.bat -p android :app:assembleDebug
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.planner/.MainActivity
```

If adb is not on PATH, use SDK/platform-tools/adb.exe. Linux/macOS use ./android/gradlew -p android :app:assembleDebug.

The application works without a backend: write a private note, open Profile → Journal, use the plus menu for a manual task/idea, and edit stored records. For cloud processing, explicitly allow Cloud AI from the composer privacy control (or the first-use explanation); existing private notes stay private. Then Profile → Settings → AI organization & connection → backend URL **http://10.0.2.2:8000** on the standard emulator and the invitation above. 10.0.2.2 routes to the host; localhost inside the emulator refers to the emulator itself. Physical devices need a reachable HTTPS endpoint; Android's local HTTP exception is restricted to debug emulator/loopback hosts. adb reverse tcp:8000 tcp:8000 plus http://127.0.0.1:8000 is another debug-device option.

Release build:
```powershell
.\android\gradlew.bat -p android :app:assembleRelease
```

Output is app-release-unsigned.apk; configure your own release signing for distribution. Release has no cleartext exception. TLS termination, server secrets/backups and Play declarations are operator responsibilities.

## Floating capture

In Planner Settings, enable Floating quick capture, read the explanation, grant Display over other apps in Android settings and return to Planner. Switch to Home/another ordinary app to see the bubble; it hides inside Planner. Drag to reposition, tap to compose, Save to persist locally, Back/close to retain draft. Disable in Planner Settings to remove it immediately.

The service needs a persistent foreground notification and uses specialUse. Android 13+ notification permission controls drawer visibility; the foreground service still follows platform notification requirements. Notification capture is a separate opt-in fallback that opens the main composer. Lock/secure windows, force-stop, task-manager stops, process termination and OEM restrictions can remove/suppress the bubble; reopen Planner to restore when allowed. No AccessibilityService, screen reading or clipboard monitoring.

## Troubleshooting

- API health fails: inspect docker compose ps and logs; migration must finish successfully, ports must be free and database credentials must agree. POSTGRES_PASSWORD is a Compose/root-environment value; changing it does not update an existing PostgreSQL volume password.
- Missing AI: set both key and model, recreate API/worker, then Retry AI. A provider timeout/rate limit retries durably; invalid output is a visible failed job.
- Reconnect required: issue a fresh same-owner invitation; credentials expire after 90 days. Keystore invalidation requires re-enrollment.
- Blocked local changes: Settings shows Retry or an explicit Keep this device's version for revision conflicts. Original captures remain accessible.
- No bubble: verify actual system permission, enable preference, unlock and reopen Planner. Use notification capture if the platform refuses overlay execution.

Related: [Testing](TESTING.md), [API](API.md), [Architecture](ARCHITECTURE.md).
