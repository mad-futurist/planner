# Running Planner

Historical verification environment (2026-10-21): Windows 11, Android Studio 2026.2.2 with bundled JBR 25.0.4 (previous checks used Java 21), Android SDK/platform 37, API 36 x86_64 emulator, Python 3.12, uv 0.12.2, Docker Desktop. Run PowerShell commands from the repository root unless stated otherwise.

Host recheck (2026-10-27): Docker Desktop is available. Java was initially absent; the follow-up installed Eclipse Temurin JDK 21.0.12.1+1 under `%USERPROFILE%\.jdks\jdk-21.0.12.1+1`, verified the official archive's SHA-256, and set user JAVA_HOME and PATH. Open a new terminal (restart the parent app if it retains its old environment) to pick up those settings. A subsequent check found Android Studio 2026.2.2.3 installed at `C:\Program Files\Android\Android Studio` with working JBR 25.0.4. SDK at `%LOCALAPPDATA%\Android\Sdk` was initially partial; the Gradle retry installed platform 37.0 revision 2, Build Tools 36.0.0 and platform-tools 37.0.1 using the already accepted licenses. User ANDROID_HOME now points to that SDK and user PATH includes platform-tools. android/local.properties remains absent; ANDROID_HOME supplies the SDK location. The debug APK build and signature verification passed with the Windows socket workaround below; unit/lint/release/device checks were not rerun during this setup. Backend dependencies can be installed with `uv sync --frozen --python 3.12` to match the container runtime; without an explicit version, this host's uv initially selected Python 3.14. See [current checks](TESTING.md#2026-10-27-host-recheck).

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

A configured flag is not a successful live call. Previously unconfigured notes require Journal → Retry AI. Live gpt-4.1-mini extraction/development was exercised on 2026-10-28, with 42/43 curated combined-interpreter cases and 16/16 API/worker checks; wider Android acceptance remains. See [live evidence](TESTING.md#2026-10-28-live-openai-extraction-and-worker-verification) and the [full UI procedure](TESTING.md#live-provider-smoke-test).

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

Open the android folder in Android Studio Quail 4 (2026.1.4) or newer that supports AGP 9.4. Keep the pinned AGP 9.4.1 / Gradle 9.6.0; Studio 2025.2.1 only supports AGP 8.13. Historical checks used Studio 2026.2.2's bundled JBR 25.0.4 and Java 21. Current Studio 2026.2.2.3 includes working JBR 25.0.4; the CLI JDK is Temurin 21 at the user JAVA_HOME above. Select a compatible JDK in Settings → Build Tools → Gradle. Install SDK platform 37/build tools and an API 36 emulator. Minimum runtime is API 26; the executed device tests cover API 36.

Android Studio creates ignored android/local.properties. On Windows, a manual SDK path needs an escaped drive colon, e.g. sdk.dir=C\:/Users/NAME/AppData/Local/Android/Sdk. Set ANDROID_HOME or JAVA_HOME as appropriate for CLI use.

```powershell
$env:JAVA_HOME=[Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
$env:ANDROID_HOME=[Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User')
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

- Windows Gradle `Unable to establish loopback connection` with `UnixDomainSockets.connect` / `Invalid argument`: this host reproduced it with both Temurin 21 and Studio JBR 25. A process-local `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Temp\planner-java` after creating that directory allowed Gradle's daemon connection to proceed. Keep this workaround local to the affected machine/session, not a repository-wide absolute path. Java documents this [socket temporary-directory setting and path-length limit](https://docs.oracle.com/en/java/javase/16/core/networking-properties.html); the underlying Windows cause was not independently established.
- API health fails: inspect docker compose ps and logs; migration must finish successfully, ports must be free and database credentials must agree. POSTGRES_PASSWORD is a Compose/root-environment value; changing it does not update an existing PostgreSQL volume password.
- Missing AI: set both key and model, recreate API/worker, then Retry AI. A provider timeout/rate limit retries durably; invalid output is a visible failed job.
- Reconnect required: issue a fresh same-owner invitation; credentials expire after 90 days. Keystore invalidation requires re-enrollment.
- Blocked local changes: Settings shows Retry or an explicit Keep this device's version for revision conflicts. Original captures remain accessible.
- No bubble: verify actual system permission, enable preference, unlock and reopen Planner. Use notification capture if the platform refuses overlay execution.

On this affected Windows host, apply the socket workaround in the same PowerShell session as the build:

```powershell
New-Item -ItemType Directory -Path C:\Temp\planner-java -Force | Out-Null
$env:JAVA_TOOL_OPTIONS=($env:JAVA_TOOL_OPTIONS+' -Djdk.net.unixdomain.tmpdir=C:\Temp\planner-java').Trim()
$env:JAVA_HOME=[Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
$env:ANDROID_HOME=[Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User')
.\android\gradlew.bat -p android :app:assembleDebug --console=plain
```

Related: [Testing](TESTING.md), [API](API.md), [Architecture](ARCHITECTURE.md).

## Public email/password and Google

Offline/private use does not require registration. For public accounts configure PUBLIC_BASE_URL, SMTP_HOST/PORT/FROM, SMTP_STARTTLS=true and SMTP_USERNAME/PASSWORD as required by the mail service. Registration confirms email before password sign-in. Recovery sends a single-use link and revokes all previous device sessions after reset. Email links use the public backend origin; validate actual delivery, expiry and browser confirmation before launch. Local development without SMTP still supports operator invitations; the UI reports unavailable mail capability.

Optional Google sign-in requires a server-side Web OAuth client, GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET. Register the exact redirect URI https://YOUR_HOST/api/v1/auth/google/callback. Google returns to the backend; the backend validates the identity and hands off to app.planner://auth using installation-generated PKCE. The client secret never goes into Android. Existing email accounts first sign in, then Link Google with current password; email equality alone does not merge accounts. Validate cancellation, app return, expired flow and recovery on actual supported devices before release. No real SMTP/Google project was configured during this increment.

For a distributable APK, set PLANNER_BACKEND_URL to the public HTTPS origin before Gradle builds. Optional release signing uses PLANNER_KEYSTORE_PATH (absolute path), PLANNER_KEYSTORE_PASSWORD, PLANNER_KEY_ALIAS and PLANNER_KEY_PASSWORD. Missing signing configuration deliberately produces an unsigned release APK. Keep secrets in operator/CI secret storage; do not commit them. Application identity and signing key must remain stable for upgrades.

## Production deployment profile

[compose.production.yml](../compose.production.yml) is an optional overlay, not a running deployment. Copy [ops/.env.production.example](../ops/.env.production.example) to ignored ops/.env.production and replace every placeholder: public DNS, independent random owner/runtime database passwords, URL-encoded database URLs, a random rate-limit secret of at least 32 characters, and SMTP configuration. Do not reuse development database credentials. Production startup rejects placeholder/default secrets, missing HTTPS/mail and disabled STARTTLS. Google and LLM remain optional and explicitly unavailable until configured.

Configuration can be validated without creating or deploying a production environment:

```powershell
docker compose --env-file ops/.env.production.example -f docker-compose.yml -f compose.production.yml config --no-env-resolution --quiet
```

An operator with configured DNS/secrets and a tested backup can start a separate deployment:

```powershell
docker compose --project-name planner-prod --env-file ops/.env.production -f docker-compose.yml -f compose.production.yml up -d --build
```

This is an operator command, not an action executed during development. It uses a separate Compose volume/project; migrating an existing deployment requires its own tested backup/upgrade procedure. Bootstrap creates the restricted runtime role; migrations use the separate owner URL. API/database have no published ports; Caddy exposes 80/443 and manages TLS. Proxy trust is limited to the configured static proxy address. Check subnet availability before deployment. App containers run as UID 10001, with bounded memory/CPU and API concurrency. Access logs stay disabled, and Caddy default structured logs redact callback/recovery token/code/state query values and Referer.

GET /health checks database connectivity only. GET /ready additionally requires a worker heartbeat within 45 seconds; neither proves live inference. Configure external alerts for readiness failure, old queues, exhausted daily budget, auth failures and backup failure. Authenticated /api/v1/diagnostics reports owner-scoped counts; content-free operator status and cleanup commands are:

```powershell
docker compose exec -T api uv run --no-sync python -m app.operations status
docker compose exec -T api uv run --no-sync python -m app.operations prune-expired-auth
```

Use the corresponding production Compose project/options for an actual deployment. Schedule cleanup externally; no scheduler or alert destination is silently installed. Cleanup removes expired auth/flow/rate metadata, old heartbeats and request accounting older than 90 days. Business changes/idempotency/history are retained; a future feed-bootstrap/retention design is needed before pruning them. AI_DAILY_REQUEST_LIMIT defaults to 100 actual managed calls per owner/UTC day. Quota delays jobs until the next UTC day without losing notes or spending a retry; it is not a monetary spending cap or distributed-abuse solution.

## Backup and isolated restore

[Database backup tool](../tools/database_backup.py) uses pg_dump custom format and Docker binary copy, avoiding corrupt PowerShell binary pipelines. It refuses existing output files and restores only into a new database named planner_restore_* or planner_smoke_*; it never overwrites a current database.

```powershell
uv run --project backend --python 3.12 python tools/database_backup.py smoke
uv run --project backend --python 3.12 python tools/database_backup.py backup --output backups/planner-20261027.dump
uv run --project backend --python 3.12 python tools/database_backup.py restore --input backups/planner-20261027.dump --database planner_restore_20261027
```

For the separate production project add --project planner-prod before the subcommand. The smoke uses synthetic temporary source/restore databases and removes both. Archives contain private data and credentials hashes: store encrypted off-host, restrict access, define retention/deletion and routinely verify restore plus application-level reads. These policies and scheduling remain operator work; a successful synthetic restore does not prove disaster recovery for a deployed service.

## Reproduce the local interpreter benchmark

### If a saved capture appears only in Journal

Tentative single actions such as maybe find ai course now offer Task + deadline…, Someday or Idea. Choose inline before Send, or choose in the prompt after Send to finish that save. A deadline is explicit dueDate, not a scheduled appointment. Known dentist at 16 offers Today at 16:00 / Choose date rather than Someday. An unknown information preview has Change type…; saving it as information explicitly reports Journal. Contextual choices currently resolve one-item drafts; split mixed tentative items or save the original only.

New default Private captures in the main composer now apply complete local interpretations without a server: buy milk appears in Tasks → Someday (no invented date), and dentist tomorrow at 16 appears in Calendar on the selected day. Uncertain time/day input asks for clarification first. Original note only deliberately saves no structured records. Cloud automatic capture instead requires an enrolled account/provider and successful sync; floating capture remains raw. The October 28 update preserves older Journal-only notes and does not bulk-reinterpret them. Test a new capture to verify the new default path; older notes remain available for explicit organization/editing.

PlannerBottomNavigation is a stateless component in its own source file; file boundaries do not diagnose CPU usage. The October 28 reported spike coincided with system-component ANRs; the short later idle sample does not establish a root cause or performance fix. Evidence and limits are in [Testing](TESTING.md#2026-10-28-private-capture-application-and-cpu-investigation).

Benchmark CLI runs now persist cases in the configured database by default, including errors. Use --no-persist explicitly for isolated/CI measurements. Operator commands for importing historical reports, generating diverse EN/FR/RU examples, backfilling existing server history and exporting synthetic or consented data are in [Dataset](DATASET.md#operator-commands). Run them from backend after migration f26a81d705bc. JSONL exports are ignored by Git and refuse overwrites. They may contain personal text when using contributed scope; restrict storage and define retention. Collection/export does not train a model. The local synthetic snapshot contains 4,557 observations; the live smoke keeps synthetic learning examples while deleting its disposable business account.

As of 2026-10-28 the local backend is configured for real gpt-4.1-mini inference using ignored backend/.env. No key is checked in or bundled in Android. To reproduce live checks deliberately, see [executed live evidence and commands](TESTING.md#2026-10-28-live-openai-extraction-and-worker-verification). [Live API smoke tool](../tools/live_ai_smoke.py) performs billable requests through normal jobs using a fresh synthetic account, then deletes only that account and verifies session revocation. It is restricted to a loopback API origin; do not run it in CI. Direct --engine provider benchmarks use operator credentials outside per-owner managed-call quotas. The 43-case combined-interpreter score is 42/43; an undated Russian quantity task can trigger unnecessary review. Wider Android live acceptance is still pending.

```powershell
Push-Location backend
uv run --frozen python -m app.benchmark --engine local --output ../docs/assets/benchmarks/2026-10-27-local.json
Pop-Location
```

The shared corpus contains 43 synthetic EN/FR/RU acceptance cases, including dentist 16, partial clarifications, quantities, multiple entities, Unicode spans and DST. Both Kotlin and Python check it. The JSON reports base-kind distribution, temporal/clarification checks, unsafe calendar cases and host timings. It does not measure custom registry types, held-out generalization or raw-model quality. --engine provider makes actual potentially billable calls and requires LLM_API_KEY/LLM_MODEL; local-only measurement on October 27 was followed by explicitly authorized live measurement on October 28.

The separate 30-case local choice corpus uses the same runner and persists observations by default:

```powershell
Push-Location backend
uv run --frozen python -m app.benchmark --engine local --corpus app/domain/assets/noncalendar_choices_v1.json --output ../tmp/noncalendar-choices.json
Pop-Location
```

Its provisional kinds plus kindChoices test local UI intent policy, not model accuracy or automatic commitments. --no-persist remains the explicit isolated/CI opt-out. Reports preserve their own expected fields and capture context for repeatable dataset import.
