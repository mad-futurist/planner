# Decision log

Last reviewed: **2026-10-17**. No previous decision log existed at the baseline inspection. Preserve entries after creation; future changes append a replacement and mark the old entry **Superseded by ADR-...**.

**Accepted** below means an agreed product/architecture requirement from the user's instructions or reference brief, not implemented code. **Proposed** means a choice still needs validation or resolution. **Superseded** preserves historical context. Feature delivery uses the separate [status contract](FEATURES.md#status-contract).

## ADR-001: Documentation and evidence advance together

Date: 2026-10-14. Status: **Accepted** (current user request and AGENTS instructions).

Decision: Keep the eight owning Markdown documents and README index current in the same change as implementation. Code owns implemented behavior; Concept owns intended behavior; Features owns status/evidence; Roadmap owns milestone progress.

Rationale/trade-off: Persistent knowledge improves handoffs but requires ongoing updates. Link owning contracts to avoid competing duplicate specifications. The initial audit establishes no runtime code; all application features begin Planned. Treat the attached brief as intended scope and pictures as concepts.

Rejected: documenting only at the end; marking a feature implemented from a specification or image; claiming tests from static descriptions. Documentation is part of the Definition of Done, not an optional follow-up.

Links: [README workflow](../README.md#living-documentation-workflow), [Features](FEATURES.md), [Roadmap](ROADMAP.md).

## ADR-002: Local persistence is the UI source of truth

Date: 2026-10-14. Status: **Accepted** (product requirement; implementation pending).

Decision: Android capture writes Room first, with eligible outbox work in the same transaction. UI observes local queries; remote changes are merged into Room before rendering. Deferrable sync and foreground refresh complement each other.

Rationale/trade-off: Local capture remains available during network/provider failure; this requires transactional outbox, retry, and conflict handling. Work scheduling is not a guarantee of immediate or exact-time execution.

Rejected: network-first saving; rendering competing remote state directly; an in-memory-only outbox.

Links: [Architecture](ARCHITECTURE.md), [API synchronization](API.md#synchronization-protocol).

## ADR-003: Preserve raw input and protect corrections

Date: 2026-10-14. Status: **Accepted** (product requirement; implementation pending).

Decision: Separate raw notes, structured records, versioned interpretation history, and user corrections. User-confirmed edits take precedence over stale AI responses.

Rationale/trade-off: Users can recover original meaning and correct AI, at the cost of revision/history storage and explicit merge rules. Raw-text edit retention and record identity mapping must still be designed.

Rejected: one serialized provider response as the entire database; destructive replacement of original input; unconditional application of late extraction.

Links: [Data integrity/versioning](DATA_MODEL.md#integrity-and-versioning-requirements), [Concept](CONCEPT.md).

## ADR-004: Replaceable AI proposes; deterministic code applies

Date: 2026-10-14. Status: **Accepted** (architecture requirement; implementation pending).

Decision: Run real provider interpretation behind a backend interface. Validate structured and semantic output before application. Queue durable jobs in PostgreSQL, bound retries/concurrency, and show actual failures. Idea suggestions become tasks only after approval.

Rationale/trade-off: Provider replacement and reproducible failure handling improve reliability; a worker and persistent retry state add operational work. No provider is selected/configured yet.

Rejected: provider keys in the APK; fabricated fallback classifications; LLM database/external side effects; in-memory FastAPI tasks as the sole durable queue.

Links: [Architecture](ARCHITECTURE.md), [API](API.md), [Features F09/F17/F18](FEATURES.md#inventory).

## ADR-005: Temporal meanings remain separate

Date: 2026-10-14. Status: **Accepted** for semantic separation; storage encoding **Proposed**.

Decision: Scheduled start/end, deadline, planned day, and reminder are distinct. Do not invent appointment times or durations. Use explicit timezone context; review ambiguity.

Rationale/trade-off: Separate fields prevent deadlines becoming meetings. Date-only deadlines, daylight-saving boundaries, overdue inclusion, and traveling-user day semantics require additional modeling and tests. Proposed encoding uses instants plus distinct day-only dates; no schema has adopted it.

Rejected: one interchangeable date field; midnight or arbitrary duration as a substitute for missing information; automatic external calendar writes from deadlines.

Links: [Temporal semantics](DATA_MODEL.md#temporal-semantics), [Calendar concept](SCREENS.md#calendar).

## ADR-006: Data-driven Type Registry

Date: 2026-10-14. Status: **Accepted** (architecture requirement; implementation pending).

Decision: Stable base kinds are task, event, idea, and information. Versioned type definitions specialize them through supported field primitives and display/capability configuration.

Rationale/trade-off: New types should not require UI subclasses or retraining an ever-growing classifier. Generic rendering/schema validation need compatibility rules and old-version retention.

Rejected: executable code from type descriptions; a subclass/UI rewrite per custom type; removing old definitions still referenced by records. Complex emergent type discovery is outside the MVP.

Links: [Data model](DATA_MODEL.md), [Features F20](FEATURES.md#inventory).

## ADR-007: Cohesive packages and modular backend

Date: 2026-10-14. Status: **Proposed** (stack direction from brief; build validation pending).

Decision: Start with cohesive Android feature/core packages without unnecessary Gradle modules. Use a FastAPI modular monolith and PostgreSQL-backed worker; select compatible stable versions during Foundation.

Rationale/trade-off: Clear layer boundaries are useful before module/build complexity. Separate modules may later be justified by ownership or build performance. No package tree, library version, or SDK baseline exists yet.

Rejected for MVP: microservices, Kafka, Kubernetes, and speculative abstractions for trivial functions. These add complexity without satisfying capture and reliability requirements.

Links: [Architecture](ARCHITECTURE.md), [Roadmap Foundation](ROADMAP.md#milestones).

## ADR-008: Overlay lifecycle must be validated

Date: 2026-10-14. Status: **Accepted** for opt-in/privacy requirements; lifecycle mechanism **Proposed**.

Decision: Share local capture with an opt-in WindowManager overlay; verify real permission, remove promptly on disable/revocation, preserve drafts where practical, and provide a user-controlled notification fallback. Validate service eligibility and restrictions against the selected SDK and supported Android versions before choosing the lifecycle implementation.

Rationale/trade-off: Cross-app capture reduces friction but requires permission, focus/gesture handling, cleanup, and platform-dependent lifecycle limits. Draw-over permission alone does not establish persistent execution. Device testing is necessary.

Rejected: AccessibilityService shortcuts, other-app content/clipboard collection, unrelated foreground-service types used solely for survival, or a force-stop persistence promise. A supported lifecycle approach has not been selected or tested.

Links: [Overlay concept](SCREENS.md#quick-capture-overlay), [Architecture overlay boundary](ARCHITECTURE.md#overlay-boundary).

## ADR-009: Cloud privacy precedes synchronization

Date: 2026-10-14. Status: **Accepted** for privacy boundaries; identity mechanism **Proposed**.

Decision: Let users choose local-only capture before upload; private notes do not enter the outbox upload path. Keep provider secrets on the backend, private text out of default logs, and access scoped to authorized identities.

Rationale/trade-off: Sensitive personal notes need an explicit cloud boundary. A real minimal device/user identity, protected token storage, enrollment/renewal, and local-data clear/export semantics still need a concrete security design and tests.

Rejected: trusting arbitrary client user IDs; a hardcoded shared secret presented as production authentication; invisible cloud transmission; provider secrets in the APK.

Links: [API boundaries](API.md#intended-boundaries), [Settings](SCREENS.md#settings).

## ADR-010: Concept pictures do not freeze every control

Date: 2026-10-14. Status: **Accepted** for reference labeling; final navigation/control policy **Proposed**.

Decision: Preserve the five supplied images unchanged and embed them as concept references. Derive runtime data from persisted state and use Android-owned system chrome. Document scope differences before adopting extra controls.

Rationale/trade-off: The pictures establish visual hierarchy but contain fictional values, inconsistent platform chrome, and controls beyond basic MVP scope. Media capture, advanced settings, a Search destination, and a 500-character note cap need independent decisions.

Rejected: using the concept images as static app backgrounds or runtime evidence; nonfunctional decorative actions; external calendar integration inferred solely from the Settings picture.

Links: [Reference differences](SCREENS.md#reference-differences-and-open-ux-decisions), [Concept scope](CONCEPT.md#intended-scope).

## ADR-011: Current implementation and calmer Today direction

Date: 2026-10-14. Status: **Accepted** for implementation choices; runtime verification recorded separately.

The pasted MVP now authorizes full development. Keep the user's intervening calm design direction: charcoal text, muted teal, mint, sand, and pale blue-green; no decorative AI illustration, neon/purple treatment, or gamified recap. The original supplied pictures remain historical concepts. Use one Android app module, current stable artifact versions verified through Maven metadata, AGP 9.4.1/Gradle 9.6.0, Kotlin 2.4.21, compile SDK 37, target 36, and minimum 26. Unused preview tooling was removed after its transitive dependency requested an unavailable Material 3 release; the runnable app uses published Material 3 1.4.0.

Room v1 stores original text separately from interpretations and separates date-only `dueDate` from instant `dueAt`. Today includes due-today/planned-today tasks, timed records, and continuing events; it does not turn overdue tasks into a red warning pool. Persist the first eligible idea each day; Later hides that slot until tomorrow without selecting a replacement. These deterministic choices replace the previously unresolved policies.

Links: [Room implementation](DATA_MODEL.md#current-room-implementation), [Features](FEATURES.md).

## ADR-012: Identity, sync ordering, and normalized instants

Date: 2026-10-14. Status: **Accepted** for implemented contracts; verification remains scoped in Features.

Use operator-issued 24-hour single-use invitations and random 90-day device bearer credentials, storing hashes on the backend and an AES-GCM Keystore-protected token on Android. No shared APK secret or arbitrary user-ID authorization. Consumer SSO, recovery, and refresh-token rotation remain outside this minimal identity. Refuse switching accounts over an existing local dataset without explicit export/clear.

Persist request IDs and immutable payloads in an ordered outbox. Creation precedes dependent edits; failed/conflicting operations block later changes to the same entity. Eight transport retries become a visible terminal failure. Foreground refresh runs only while STARTED; WorkManager provides connectivity-aware and periodic deferrable sync. Merge and cursor checkpoint commit together. Pending/confirmed local edits reject stale AI records; conflict replacement requires an explicit Keep local action. PostgreSQL feed writers hold per-identity advisory locks to prevent sequence/commit-order gaps; durable workers claim with leased SKIP LOCKED rows.

Store queryable instants as fixed millisecond UTC on both sides; Room 1→2 normalizes prior strings without reset. Day-only deadlines use `dueDate`, execution days use `plannedFor`. Scheduled membership is checked independently of deadline membership. Empty daily idea slots are not persisted until an eligible idea exists; an already selected/deferred slot remains stable.

Links: [API](API.md#implemented-backend-contract), [Room migration](DATA_MODEL.md#current-room-implementation).

## ADR-013: Opt-in overlay and notification lifecycle

Date: 2026-10-15. Status: **Accepted** for implementation; device/Play verification remains separate.

Use a user-started specialUse foreground service with the explicit cross-app capture purpose in the manifest and a low-importance persistent notification. Start/restore only from a visible Activity; use START_NOT_STICKY, not a boot/background resurrection promise. Hide windows while locked and while Planner is foreground to avoid covering navigation and duplicating its persistent composer. Show them again when an ordinary app becomes foreground. Observe permission changes and release windows/listeners/scope on shutdown. The platform controls suppression over other secure/system windows; the app does not inspect other applications.

Use native WindowManager views for overlay focus/keyboard handling while sharing Room capture and design colors. A scrollable panel supports small/rotated displays. Conflate draft writes in application scope; flush explicit close; permission/process termination does not guarantee the last unsent keystrokes. Notification capture is opt-in and opens the app's real composer. Background specialUse eligibility and Google Play declaration review require distribution/device validation; no store approval is claimed.

Source: [Android foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types), [Android 15 start restrictions](https://developer.android.com/about/versions/15/behavior-changes-15). Implementation: [overlay package](../android/app/src/main/java/app/planner/features/overlay/OverlayService.kt).

## Future entry format

Append a new stable ADR number with date, status, context/source of the requirement, concrete decision, rationale/trade-offs, rejected alternatives, affected documents and real source paths, and verification implications. Record supersession links in both entries; do not rewrite history to conceal changed behavior.


## ADR-014: Reliability bounds and explicit stale-edit handling

Date: 2026-10-15. Status: **Accepted** for implemented behavior; scoped checks in Features.

Resolve ADR-007's module proposal through the built single-module Android/package structure; ADR-008's lifecycle proposal through ADR-013; ADR-009's identity proposal through ADR-012; ADR-010's navigation proposal through the six actual routes in Screens. Earlier “pending” statements describe the original 2026-10-14 baseline and are preserved as history.

Move shared immutable Room-annotated values into core/model and pure capture/completion/time policy into domain. Keep repository transactions and network mapping in data/sync. Accept annotations in shared models to avoid duplicate field-for-field models and mappers in this MVP; reconsider separate persistence/domain models if responsibilities actually diverge.

Reject stale full-form writes with an inline recoverable error. Completion is a narrower user intent: re-read the current record inside a transaction, preserve newer fields and make duplicate completion/restore taps idempotent. Completion feedback is short and dismissible so it does not cover restoration indefinitely. Archive remains reversible through Details → Restore item. Post-commit scheduling/diagnostic failure must never report a committed note as unsaved.

Room 2→3 adds outbox ordering/dependency indices. Backend development jobs include the exact idea revision; old results remain durable. Server ingestion time, independent of offline capture time, controls rate limiting. Canonical UUIDs identify idempotent operations. Incorrect existing-owner invitation IDs fail rather than silently creating another identity.

Validate actual clock time against original-text evidence and review repeated local times. Apply approved kind rules to semantic checks before derived records. Use explicit validated_low/review_required risk labels rather than model confidence. Provider context uses at most 50 latest enabled types, 20 approved rules, and five lexical correction examples from 50 candidates; request budget is 128 KiB. Oversized context fails durably. This bounds work but means very large registries need a future context-selection policy; registry storage and old-version lookup remain intact.

Export uses streaming keyset pages to avoid offset duplicates under new captures. It remains a best-effort local export during concurrent edits, not a transactional backup. Android excludes cloud backup/device transfer and encrypts the credential, while SQLite data itself remains unencrypted.

Rejected: stale snapshot overwrites, indefinite blocking feedback, destructive migration resets, request-rate decisions from backdatable timestamps, unbounded prompts or fabricated provider success. Tests, source locations and remaining acceptance are in [Features](FEATURES.md), [Data model](DATA_MODEL.md), [Screens](SCREENS.md) and [Testing](TESTING.md).


## ADR-015: Recover rejected edits without losing queued intent

Date: 2026-10-15. Status: **Accepted** for implementation; real-HTTP regression is recorded in Testing.

A failed record PATCH can block a newer corrected edit behind its immutable rejected payload. Settings therefore offers Send the latest saved version as well as explicit conflict resolution. Fetch the current authorized server revision, coalesce only record PATCH operations into the current local snapshot under a new operation UUID, and retain the original blocked position in the queue. Preserve distinct development requests, so a corrected edit is sent ahead of those requests rather than deleting or reordering user intent.

Initial manual capture emits the note change before its records; paginated replica reads can observe a parent before derived data even when the page ends mid-capture. Correction-context retrieval excludes zero-overlap examples; one unrelated correction is not a universal preference.

Rejected: endlessly retrying a known-invalid frozen payload; silently replacing server values; deleting unrelated commands while coalescing edits; publishing records before their original note. Source: [SyncEngine](../android/app/src/main/java/app/planner/sync/SyncEngine.kt), [Settings](../android/app/src/main/java/app/planner/features/SettingsScreen.kt), [backend routes](../backend/app/api/routes.py), [worker](../backend/app/jobs/worker.py).
