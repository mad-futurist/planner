# Testing and verification

Last reviewed: **2026-10-17 (Europe/Paris)**. Backend/build/unit/manual evidence dates from October 15; the final Android acceptance run dates from October 17. Scope and feature statuses: [Features](FEATURES.md). Run on a disposable emulator; instrumentation adds QA notes, and the explicit preview fixture replaces local app data.

## Executed results

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

1. Capture “Meet Nicolas tomorrow at 14:00 and prepare questions by today”; verify local saving first, then a real stored event and separate task, dates resolved from capture timezone, original text preserved.
2. Repeat French “Rendez-vous avec Nicolas demain à 14h00” and Russian “Встреча с Николя завтра в 14:00”; inspect correct local clock/day and Unicode.
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
