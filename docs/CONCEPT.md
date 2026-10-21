# Product concept

Last reviewed: **2026-10-21**. This document owns **intended behavior**. [Features](FEATURES.md) distinguishes implementation from executed verification.

## Vision

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

Write in the main composer or floating panel; keep the private default or explicitly consent to Cloud AI for future captures. Save raw text and eligible outbox work in one Room transaction. Confirm after commit and allow the next thought immediately. Cloud-eligible notes synchronize and undergo background interpretation. Pending, failed, ambiguous, and unconfigured processing remain separate from successful local saving. Manual organization remains usable offline.

Examples: “Call the doctor tomorrow” is an untimed task; “Finish the report by Friday” has a deadline; “Meet Nicolas Thursday at 14:00 and prepare questions by Wednesday” may produce multiple records. English, French, and Russian are supported extraction inputs; live quality still needs [provider acceptance](TESTING.md#live-provider-smoke-test).

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

MVP: Android Today, day Calendar, Tasks, Ideas, Profile, Journal, Review, Settings, editable records, persistent capture, floating capture, real FastAPI/provider integration, durable jobs/sync, versioned types, corrections, and approved simple rules.

Outside MVP: external calendar writes, fine-tuning, embeddings infrastructure, automatic type discovery, media/voice capture, global Search, project/backlog management, cloud backup/import, and analytics. Timed reminders and recurrence expansion are **not delivered**: timestamps/rules can be stored without claiming notification delivery or generated occurrences.

## Current discrepancies

The 2026-10-21 brief supersedes the simplified visual treatment in ADR-011. Supplied screenshots now guide typography, mint/sand/apricot/lavender colors, card composition, original vector accents and navigation ([ADR-016](DECISIONS.md#adr-016-reference-led-ui-and-complete-task-discovery)). Intended parity does not authorize fake identities, inactive controls, invented AI activity or screen-content blur. Actual captures/comparisons are in [Visual audit](VISUAL_AUDIT.md).

The real provider adapter supports strict schema requests with a validated compatibility fallback. No key/model is configured: live extraction/development remains unverified. Physical-device, store-policy, accessibility and representative performance gates remain in [Roadmap](ROADMAP.md).

Related: [Screens](SCREENS.md), [Architecture](ARCHITECTURE.md), [Features](FEATURES.md).
