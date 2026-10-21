# Testing and verification

Last reviewed: **2026-10-21 (Europe/Paris)**. The correction increment is reported first; October 15/17 results remain historical below. Scope and feature statuses: [Features](FEATURES.md). Run on a disposable emulator; instrumentation adds QA notes, and the explicit preview fixture replaces local app data.

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
