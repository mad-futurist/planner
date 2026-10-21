# Screens and UX

Last reviewed: **2026-10-21**. The supplied images are visual targets under [ADR-016](DECISIONS.md#adr-016-reference-led-ui-and-complete-task-discovery). Current emulator comparisons: [Visual audit](VISUAL_AUDIT.md). Historical captures remain separately labeled.

## Current navigation and interactions

[PlannerUI](../android/app/src/main/java/app/planner/features/PlannerUI.kt) owns the persistent composer and consistent Today / Calendar / Tasks / Ideas / Profile navigation. Saved primary navigation entries restore useful route/list state. Profile links Journal, Review and Settings. Secondary headers have Back. Keyboard visibility hides primary navigation while keeping capture above IME/system insets. No chat, month grid or analytics view exists.

The plus menu chooses Note for AI, Task for today, Task without a date, Idea, Event or Information. Classification is optional. Text saves after one Room transaction; raw input is independent of interpretation. Main/overlay inputs cap at 20,000 characters; in-flight saves prevent duplicate submission and retain newer typing. Drafts persist through DataStore. Local save confirmation never implies successful AI.

The composer states Private / on this device by default. An empty Today includes a nonblocking first-use explanation of private Journal storage, manual capture and optional cloud interpretation. Choosing Cloud AI first explains server/provider transmission, relevant context, queued offline uploads, provider retention responsibility, and the ability to switch back. Consent does not upload existing private notes. Cancel keeps captures private. Missing/legacy preferences fail closed. The overlay follows the same stored policy and directs first-time consent to Planner.

## Shared design and UI states

[Design system](DESIGN_SYSTEM.md) owns bundled fonts/licenses, semantic color tokens, cards, icons and vector illustrations. Large serif headings, contemporary body text, ivory/mint/sand/apricot/lavender surfaces and soft elevation now follow the supplied references. Actual values/counts/identity/status come from local state. No fake user initials, provider activity, streaks or rewards.

| State | Actual behavior |
| --- | --- |
| Loading | Local read hint; capture remains available |
| Empty | Section-specific explanation and relevant add/navigation action |
| Query error | Distinct safe error and Try again; no fabricated empty-data claim |
| Saving / saved | Duplicate send guard; confirmation after Room commit; continued typing retained |
| Offline / pending | Local reading/editing works; cloud-eligible outbox persists |
| AI unavailable / failed | Honest header/Journal status and Retry AI; private mode is explicit |
| Stale edit / invalid form | Retain form and explain correction/reopen requirement |
| Permission denied / revoked | Actual toggle state; in-app capture and notification alternative |

## Today

[Organizer screens](../android/app/src/main/java/app/planner/features/OrganizerScreens.kt) uses the real device date/locale. Apricot sunrise preview shows the earliest tomorrow commitment and real remaining count. Schedule uses chronological timeline cards; Anytime initially shows two selected untimed tasks, then an explicit +N expansion. Deadlines never become invented meetings. Star/checkbox controls complete with Undo; card/details opens editing.

Idea of the day uses a persisted daily slot with least-recently-shown eligible rotation. The lavender card supports Details, Develop and Later. Later leaves today’s slot empty, with alternatives considered on subsequent days. Done today uses actual completion instants and expands into restorable tasks. One quiet Worth revisiting link can lead to at most three older unresolved tasks in Review; it does not add them to the focus pool. Temporal predicates: [Data model](DATA_MODEL.md#temporal-semantics).

## Calendar

The month label opens a native date picker; previous/next week controls and seven date cells change the selected day. Actual Room counts drive week dots. Week start is configurable in Settings. Today returns to the device day. Timed commitments and planned/due tasks are separate groups with editable cards. Local empty/loading/error states are explicit. No external calendar writes or invented appointment duration.

## Tasks

Today & earlier, Upcoming and Someday filters have Room-derived counts. More views exposes All unfinished, Completed and Archived. Lists page 50 rows, pinned first; pinned/ordinary sections collapse independently. Someday includes every task with no execution date, deadline or start. Earlier tasks remain reachable without red warning states. All unfinished resolves cross-date cases without silently changing dates.

Completion/Undo, Details, planned date/deadline editing, reschedule, pin/unpin and archive/restore all use the existing repository/records. Add task selects manual undated capture. Pagination and empty states preserve discoverability beyond the Today day-query bound.

## Profile

Local device identity or the connected workspace UUID prefix is shown without a fabricated name/photo. SQL aggregates show actual completed tasks, non-archived saved ideas, original notes and unfinished tasks. Registered type collections appear only when records exist. Journal, Review and Settings remain accessible; a new profile explains that capture starts the collection. No inferred personality/focus-time statistics.

## Journal and review

Journal pages 50 chronological original notes with timestamps, privacy/processing status, derived records, edit/manual interpretation and cloud-only Retry AI. Newer/Older uses the local database. Original text remains visible after corrections.

Review combines at most three persistent old-task slots and at most five ambiguous-note slots. Old tasks offer Today, Later (seven-day review snooze without a deadline), Smaller action (new linked action, original retained), Details and Let go (archive). Edited/rescheduled/completed/archived items leave the daily slot; slots do not refill immediately. Note review offers editable proposal titles and explicit interpretation dates, plus daily Later. Review never blocks capture.

## Ideas and suggestions

Ideas has a bound title/details search, 50-row paging and editable saved cards. Search follows SQLite LIKE behavior (ASCII case-insensitive, non-ASCII case-sensitive). Make a task creates a new raw note/task with provenance and the current capture privacy choice. A private source always produces a private task, and the original idea is preserved. Task Details links back to the source idea/action where available.

Develop keeps the existing durable AI job flow. Private ideas remain private and offer manual conversion; queued/running, unavailable, unconfigured, failed and stale states are distinct. Completed results expose 3–5 selectable steps; approval creates tasks atomically once after input/idea version checks. Live-provider behavior remains unverified.

## Record details

[RecordEditor](../android/app/src/main/java/app/planner/features/RecordEditor.kt) exposes original note, title, kind, versioned type/generic fields, separate planned day/deadline/start/end and pin/archive/restore/complete. Provenance IDs stay out of generic user fields and appear as a source link. Reminder editing is explicitly stored-only. Native time editing rejects DST gaps and asks which repeated occurrence to use. Save retains validation errors; Cancel discards unsaved values. Immediate pin/archive/complete acts on saved values; save form edits first.

## Settings

Grouped real controls cover floating capture, notification capture, AI endpoint/enrollment/sync, privacy, system/light/dark appearance, Sunday/Monday week start, export/clear and diagnostics. Rows expand into working controls. Timed reminders, notification digests, external calendars, cloud backups and other unimplemented options are omitted.

Export streams keyset pages into local schemaVersion=1 JSON without credentials. It is a best-effort export during concurrent edits; failure can leave a partial destination, and retry opens the picker again. Export disables simultaneous clear/export. Cancel changes nothing.

Clear requires confirmation, stops capture surfaces/work, serializes against sync, and clears Room/drafts/endpoint/credential/consent. New capture returns to private. Server copies remain. Terminal sync failures retain Retry / Send latest saved version / explicit Keep local conflict recovery.

## Quick-capture overlay

The existing native WindowManager service remains. A draggable 56 dp forest circle opens an ivory rounded panel with handle, shared sparkle icon, multiline mint field, privacy choice, close/send controls and local-save feedback. It draws original vector icons and bundled body font, and does not blur/read other apps.

| Interaction | Actual behavior |
| --- | --- |
| Enable | Explain permission; verify Android result; start from visible Activity |
| Tap | Restore draft/privacy, open field and request IME |
| Save | Shared Room capture with OVERLAY source; confirm locally before any network |
| Close / Back | Persist draft and collapse; predictive Back on API 33+ |
| Drag / rotate | Snap/remember normalized position; preserve draft and reposition |
| App foreground / lock | Hide windows; ordinary app/unlock restores when permitted |
| Disable / revoke | Stop service and remove windows/listeners |
| Notification fallback | Separate opt-in capture action opens the main composer |

Draft writes are debounced; explicit close flushes. Force-stop/process/storage failure can lose recent unsent keystrokes, while committed notes persist. The service uses specialUse, START_NOT_STICKY and a persistent notification. No boot resurrection promise. Physical/OEM/store limits remain acceptance gates.

## Reference gallery

These original pictures are copied unchanged into the repository. Values and system chrome are illustrative, not runtime evidence.

### Today concept

<img src="assets/screens/today.png" alt="Supplied Today concept with schedule, Anytime, idea and capture field" width="360" />

### Calendar concept

<img src="assets/screens/calendar.png" alt="Supplied Calendar concept" width="360" />

### Tasks

<img src="assets/screens/tasks.png" alt="Supplied Tasks concept with pinned and day groups" width="360" />

Tasks now has its own functional route; current captures and intentional differences are in [Visual audit](VISUAL_AUDIT.md).

### Settings concept

<img src="assets/screens/settings.png" alt="Supplied Settings concept" width="360" />

### Floating capture concept

<img src="assets/screens/quick-capture.png" alt="Supplied floating capture concept" width="360" />

## Reference differences and open UX decisions

Five primary navigation destinations and an always-available composer consume more height than the reference Calendar/Settings screens; these screens scroll. Real dates, counts, private mode, local identity and implemented setting descriptions replace fictional reference values. The Today card omits unearned Curated by AI attribution. The overlay uses the actual other-app background and visible Android keyboard, without blur. Original vector drawings approximate the reference motifs; they are not extracted commercial illustrations.

Current captures and screen-specific differences: [Visual audit](VISUAL_AUDIT.md). Full font/TalkBack/OEM coverage and live inference remain open. The old `assets/screens/*runtime*.png` files are October 15 historical evidence and are not the redesigned UI.
