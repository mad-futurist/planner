# Product concept

Last reviewed: **2026-10-28**. This document owns **intended behavior**. [Features](FEATURES.md) distinguishes implementation from executed verification.

## Vision

Capture remains immediate for an original raw note. Creating a precise calendar event requires resolving meaningful date/time ambiguity first: “dentist 16” must not silently choose today at 16:00 or the 16th day. A calm live local preview underlines uncertain spans, offers explicit choices on tap and shows their interpretation before saving the event. Saving the original without an event remains available. A selected date without a time is still incomplete for a timed event; no invented duration or timezone transition is accepted. Editing invalidates old choices. This is now implemented in the main composer; the floating input retains raw capture.

Public service access should support email/password with confirmation/recovery and optional Google sign-in, while offline private use remains available without an account. Identity linking must be explicit and must preserve the local dataset's owner. Authentication code exists; live mail/Google/deployment acceptance remains open in Features. The synthetic EN/FR/RU corpus evaluates local rules and, in an explicitly authorized live run, the combined provider/deterministic interpreter. Curated acceptance does not establish general model quality or generalization.

Planner is a calm personal capture-and-return system. One persistent input receives thoughts, obligations, ideas, links, and plans. It saves the original immediately; background interpretation returns useful structure through a lightweight Today surface. Capture does not require a conversation, category, project, or date.

The interface answers “What matters now?” and “Where do I put the next thought?” It prioritizes cognitive relief and trust over a comprehensive productivity dashboard.

## Principles

- Capture first, structure afterward; local saving never waits for AI.
- Room is Android's observable business-data source of truth.
- Preserve original input separately from interpretations; every interpretation is correctable.
- AI proposes data. Deterministic, version-aware code validates and applies it.
- Never invent important missing dates, hours, durations, or successful AI results.
- Protect user corrections from delayed responses and stale editor snapshots.
- Use four stable kinds and a versioned Type Registry for specialization.
- Keep actions predictable and reversible; avoid guilt states, streaks, rewards, and dense controls.
- Choose privacy before transmission. Private captures stay on the device.
- Floating capture and its notification fallback are opt-in; platform limitations are explicit.

## Primary user flows

### Capture and organize

Write in the main composer or floating panel; keep the private default or explicitly consent to Cloud AI for future captures. In the main composer, default Private capture applies complete, visibly labeled local-rule proposals into tasks/events/ideas/information alongside the original note and local history in one Room transaction. Resolve uncertainty before creating structured items, or choose Original note only. Undated tasks remain undated and become discoverable in Someday; saved events select their Calendar day. Local rules have bounded vocabulary and are not model inference. The floating panel remains raw capture. Cloud-eligible automatic notes synchronize and undergo background interpretation; explicit original-only capture does not schedule interpretation. Confirm after commit and allow the next thought immediately. Pending, failed, ambiguous, and unconfigured processing remain separate from successful local saving. Manual organization remains usable offline. This replaces the earlier default private raw-only behavior under ADR-023; older Journal notes are not reinterpreted automatically.

Examples: “Call the doctor tomorrow” is an untimed task; “Finish the report by Friday” has a deadline; “Meet Nicolas Thursday at 14:00 and prepare questions by Wednesday” may produce multiple records. English, French, and Russian are supported extraction inputs; live quality still needs [provider acceptance](TESTING.md#live-provider-smoke-test).

Tentative actions such as maybe find ai course should offer Task + deadline, Someday or Idea rather than being silently treated as information or a commitment. The selection preserves original text and previews its effect; a private automatic submit waits for the choice. A known event clock, such as dentist at 16, offers Today at 16:00 or a chosen date, without a Someday shortcut. Other unrecognized notes stay recoverable as information, with an explicit Journal destination and editable kind. Current contextual intent selection applies to one-item drafts; unresolved mixed intent is kept for editing/splitting or explicit original-only saving (ADR-024).

### Return and correct

Journal exposes original text, timestamp, processing status, and interpretations. Record details support kind, type, fields, and separate dates. Save corrections locally with history. Review shows at most five ambiguous notes with daily deferral; it is never a prerequisite for another capture.

### Plan a day

Today contains scheduled commitments, a two-item initial Anytime focus pool, a quiet tomorrow preview, one stable saved idea, and an actual completed-task recap. It does not surface a red overdue backlog. Date navigation in Calendar exposes scheduling, planning, and deadline meanings. See [temporal semantics](DATA_MODEL.md#temporal-semantics) for the implemented inclusion policy.

### Find an unfinished task

Tasks keeps every unfinished task discoverable through paginated Today & earlier, Upcoming, Someday and All views. Undated tasks never acquire an artificial deadline. Archived and completed tasks have separate recovery views. Today remains a small focus surface. Up to three older unresolved tasks can return in a stable daily Review set; Today offers one quiet link. Today, Later, Smaller action and Let go are explicit choices.

### Develop an idea

Explicitly request 3–5 proposed next steps; select and approve tasks before creation. Later defers the saved idea until tomorrow and leaves today's slot empty. Daily selection prefers the least recently shown eligible idea and stays stable across reopening. Make a task creates a linked action and preserves the original idea; approved AI development remains a separate flow. Missing configuration, offline service, invalid output, and stale results must offer recovery without fabricated suggestions.

### Capture over another app

Enable Floating quick capture in Settings, grant Android's overlay permission, and verify the real permission on return. Tap the draggable bubble, write multiline text, save locally, and collapse. Close/Back retains unsent text where storage permits. Windows hide on lock and inside Planner. Force-stop, process termination, OEM restrictions, and store eligibility constrain availability; a user-controlled capture notification provides a fallback.

## Intended scope

Cloud interpretation examples retain original input, proposed extraction and user changes separately so future improvement can be reviewed. Model output is not automatically ground truth. Contribution to service-wide datasets is an explicit, revocable choice, off by default; private local notes stay local. Collection does not start training. Implementation boundaries and provenance labels are in [Dataset](DATASET.md).

MVP: Android Today, day Calendar, Tasks, Ideas, Profile, Journal, Review, Settings, editable records, persistent capture, floating capture, real FastAPI/provider integration, durable jobs/sync, versioned types, corrections, and approved simple rules.

Outside MVP: external calendar writes, fine-tuning, embeddings infrastructure, automatic type discovery, media/voice capture, global Search, project/backlog management, cloud backup/import, and analytics. Timed reminders and recurrence expansion are **not delivered**: timestamps/rules can be stored without claiming notification delivery or generated occurrences.

## Current discrepancies

The 2026-10-21 brief supersedes the simplified visual treatment in ADR-011. Supplied screenshots now guide typography, mint/sand/apricot/lavender colors, card composition, original vector accents and navigation ([ADR-016](DECISIONS.md#adr-016-reference-led-ui-and-complete-task-discovery)). Intended parity does not authorize fake identities, inactive controls, invented AI activity or screen-content blur. Actual captures/comparisons are in [Visual audit](VISUAL_AUDIT.md).

The real provider adapter supports strict schema requests with a validated compatibility fallback. Live extraction/development now passes scoped API/worker checks; the combined interpreter passes 42/43 curated EN/FR/RU cases. Undated Russian quantity text can trigger unnecessary review. Full live Android correction/approval, held-out quality, physical-device, store-policy, accessibility and representative performance gates remain in [Roadmap](ROADMAP.md).

Related: [Screens](SCREENS.md), [Architecture](ARCHITECTURE.md), [Features](FEATURES.md).
