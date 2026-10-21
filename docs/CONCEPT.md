# Product concept

Last reviewed: **2026-10-17**. This document owns **intended behavior**. [Features](FEATURES.md) distinguishes implementation from executed verification.

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

Write in the main composer or floating panel; choose Private when desired. Save raw text and eligible outbox work in one Room transaction. Confirm after commit and allow the next thought immediately. Cloud-eligible notes synchronize and undergo background interpretation. Pending, failed, ambiguous, and unconfigured processing remain separate from successful local saving. Manual organization remains usable offline.

Examples: “Call the doctor tomorrow” is an untimed task; “Finish the report by Friday” has a deadline; “Meet Nicolas Thursday at 14:00 and prepare questions by Wednesday” may produce multiple records. English, French, and Russian are supported extraction inputs; live quality still needs [provider acceptance](TESTING.md#live-provider-smoke-test).

### Return and correct

Journal exposes original text, timestamp, processing status, and interpretations. Record details support kind, type, fields, and separate dates. Save corrections locally with history. Review shows at most five ambiguous notes with daily deferral; it is never a prerequisite for another capture.

### Plan a day

Today contains scheduled commitments, a two-item initial Anytime focus pool, a quiet tomorrow preview, one stable saved idea, and an actual completed-task recap. It does not surface a red overdue backlog. Date navigation in Calendar exposes scheduling, planning, and deadline meanings. See [temporal semantics](DATA_MODEL.md#temporal-semantics) for the implemented inclusion policy.

### Develop an idea

Explicitly request 3–5 proposed next steps; select and approve tasks before creation. Later defers the saved idea until tomorrow and leaves today's slot empty. Selection stays stable across reopening. Missing configuration, offline service, invalid output, and stale results must offer recovery without fabricated suggestions.

### Capture over another app

Enable Floating quick capture in Settings, grant Android's overlay permission, and verify the real permission on return. Tap the draggable bubble, write multiline text, save locally, and collapse. Close/Back retains unsent text where storage permits. Windows hide on lock and inside Planner. Force-stop, process termination, OEM restrictions, and store eligibility constrain availability; a user-controlled capture notification provides a fallback.

## Intended scope

MVP: Android Today, Journal, day Calendar, Ideas, Review, Settings, editable records, persistent capture, floating capture, real FastAPI/provider integration, durable jobs/sync, versioned types, corrections, and approved simple rules.

Outside MVP: external calendar writes, fine-tuning, embeddings infrastructure, automatic type discovery, media/voice capture, Search, a full backlog, advanced appearance settings, cloud backup/import, and analytics. Timed reminders and recurrence expansion are **not delivered**: timestamps/rules can be stored without claiming notification delivery or generated occurrences.

## Current discrepancies

The full MVP request superseded the earlier documentation-only scope and main-screen-only design iteration; the calm color direction remains adopted ([ADR-011](DECISIONS.md#adr-011-current-implementation-and-calmer-today-direction)). Real app/backend code now exists and builds. The provider adapter is implemented, but no API key/model was supplied, so live extraction/development acceptance is unexecuted. Physical-device, store-policy, accessibility, and representative performance gates remain in [Roadmap](ROADMAP.md).

Related: [Screens](SCREENS.md), [Architecture](ARCHITECTURE.md), [Features](FEATURES.md).
