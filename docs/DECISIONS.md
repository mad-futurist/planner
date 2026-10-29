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

Source: [Android foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types), [Android 15 start restrictions](https://developer.android.com/about/versions/15/behavior-changes-15). Implementation: [overlay package](../android/app/src/main/java/app/planner/infrastructure/overlay/OverlayService.kt).

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

Rejected: endlessly retrying a known-invalid frozen payload; silently replacing server values; deleting unrelated commands while coalescing edits; publishing records before their original note. Source: [SyncEngine](../android/app/src/main/java/app/planner/infrastructure/sync/SyncEngine.kt), [Settings](../android/app/src/main/java/app/planner/presentation/settings/SettingsScreen.kt), [backend routes](../backend/app/api/routes.py), [worker](../backend/app/jobs/worker.py).

## ADR-016: Reference-led UI and complete task discovery

Date: 2026-10-21. Status: **Accepted and implemented; verification scoped in Testing**. Supersedes ADR-011's simplified visual treatment; its history remains above.

The new user brief makes the five original screenshots the visual target. Adopt their serif/sans typography, mint/sand/apricot/lavender palette, rounded geometry, original vector landscape accents and consistent Today/Calendar/Tasks/Ideas/Profile navigation. Do not fabricate identities, counts, provider activity or inactive media/settings controls. Preserve Room, data and synchronization infrastructure.

Add paginated task discovery and bounded old-task return instead of filling Today with a backlog. Persist daily idea selection with rotation across eligible alternatives. Idea-to-task conversion creates a linked task, preserving the original. Private capture remains the default; cloud upload needs an informed explicit choice. Query/Meta changes should reuse schema v3 unless a persisted entity actually changes.

Rejected: screenshot image backgrounds, a new database, random idea selection on every opening, artificial deadlines, silent cloud opt-in, arbitrary toolchain downgrades and a large UI framework. The installed Studio is now 2026.2.2; the existing AGP 9.4.1/Gradle 9.6.0 debug/unit baseline passes. Live-provider acceptance remains blocked without credentials; deterministic fixtures do not substitute for it.

Component research: [Design system](DESIGN_SYSTEM.md). Verification: [Testing](TESTING.md), [Visual audit](VISUAL_AUDIT.md).

Implementation choices: native Compose/Material controls and original Canvas paths, bundled OFL Playfair Display/DM Sans, and native instrumentation goldens avoid a new runtime framework or an unvalidated screenshot Gradle plugin. See the candidate/license assessment in Design system. Cloud consent and capture mode commit together; legacy cloud preference alone no longer authorizes new captures. Profile tab returns to its root rather than restoring a secondary Journal/Settings destination. The expanded Tasks/Ideas lists page at 50 rows; the original Today focus cap stays intact.

Linked manual actions use the active new-capture privacy choice and fail closed to private by default; private sources remain private even if cloud mode is selected. This prevents newly written smaller-action text from inheriting a formerly shared source's upload permission. Existing cloud records retain their original synchronization contract.

## ADR-017: Durable incoming snapshots and transactional worker fencing

Date: 2026-10-27. Status: **Implemented; regression evidence in Testing**. Supersedes ADR-012's assumption that an atomic merge/cursor alone guarantees convergence.

When pending local operations block a remote note/record merge, persist the latest snapshot under a typed incoming key in Room Meta in the same transaction as the cursor. A rotating, bounded 100-entry reconciliation pass applies snapshots after acknowledgment; newer deferred versions survive older responses. This reuses the existing durable table rather than introducing an in-memory queue or discarding the feed range. Private and confirmed-correction guards remain. Clear local data removes the inbox with Meta.

Worker completion/failure takes the owner lock first, then re-reads and locks the job row and checks status/token in that transaction. Claiming still uses SKIP LOCKED. This preserves the API owner→job lock order and fences superseded workers on success and error. PostgreSQL regressions reproduce the previously failing schedule on both branches. No lease timeout increase is a substitute for fencing.

## ADR-018: Explicit clarification, versioned hints and an honest local preview

Date: 2026-10-27. Status: **Implemented; device acceptance tracked in Testing**. Source: the requested `dentist 16` ambiguity and clarification before saving a calendar event.

Save original without event uses existing manual=true with an empty record list, including for cloud sync, so delayed worker interpretation cannot create an event contrary to that explicit choice. This requires no additional wire flag; normal Note for AI capture retains its existing background interpretation path.

The composer runs a debounced, bounded, deterministic preview offline. Android and backend share one versioned JSON rule set and synthetic EN/FR/RU corpus. Four existing base kinds remain task/event/idea/information; no new entity kind is introduced. Amber underline/background and accessible question controls identify doubts; a tap opens explicit date/time/literal choices and a candidate preview. Confirmed choices are underlined in the primary color. A date without a time does not authorize a timed event. Raw note saving remains available; explicit event creation requires a resolved day/time and rejects nonexistent/repeated DST clock times rather than guessing.

Clarifications are independent `interpretationHints` v1 metadata anchored to exact original **UTF-16** start/end offsets and text, including explicit date/time/literal choice. UTF-16 matches Android editable offsets; backend checks surrogate boundaries, text equality and overlap. Editing invalidates hints and any remote preview; originalContent is never rewritten. Room v4 adds a JSON column with a 3→4 migration; Alembic adds a JSON field. Older uploads default to no hints. Exports include the metadata. Hints inform the provider and deterministic temporal validation; they cannot authorize unrelated invented ends/reminders.

The local preview is labeled `local-rules-v1`, never presented as a model response. A separate, explicit Cloud-AI preview asks before sending the draft, stores no note/record, uses the same bounded provider context/rules as jobs and is invalidated on edit/privacy changes. A persistent per-owner UTC daily request budget covers actual managed worker attempts and AI previews; exhausted jobs wait without consuming retry attempts or losing notes. The benchmark is a curated acceptance corpus, not a held-out quality estimate. The user selected local-only measurement; no live-provider score is claimed.

## ADR-019: Public email/password accounts, explicit Google linking and deployment boundaries

Date: 2026-10-27. Status: **Implemented with test transports; live integrations require operator configuration**. Source: the user chose a public service with email/password plus Google.

Use Argon2id through argon2-cffi for password hashing, confirmed email before password sign-in, hashed expiring single-use verification/recovery tokens, generic registration/recovery responses, per-IP/action attempt limits and a per-recipient email cooldown. Password reset revokes device credentials, other email tokens and pending Google links. Owner-first locking serializes sensitive operations. Existing invitation/device-token contracts remain compatible; local owner guards still prohibit silently mixing datasets.

Google authentication uses the system browser and a backend confidential OAuth client. Validate the RS256 signature with Google's JWKS, issuer, audience, expiry, verified email and nonce. The app generates PKCE and receives a short-lived one-use handoff bound to verifier/state; Google client secrets stay on the backend. Google identities use issuer/sub, and email equality never silently links accounts. Existing password accounts link Google only after authenticating the current password. Google-only users may set a password through their verified email recovery flow. Account deletion requires owner/password/typed confirmation, removes active server rows and revokes sessions; local notes remain available for export and new captures return to private. External provider retention and encrypted backup expiry are separate operator responsibilities.

The three new security dependencies (argon2-cffi, PyJWT/cryptography, email-validator) replace custom cryptography and parsing. No password or OAuth token is logged or included in validation responses. Browser confirmation forms do not consume tokens on GET. Access logs are disabled to avoid callback/recovery URL leakage.

Production configuration is explicit: HTTPS Caddy, a restricted database runtime role separate from migrations, proxy trust limited to the configured proxy address, bounded API concurrency/resources, non-root app containers, worker heartbeat readiness, content-free diagnostics, CI and a restore-to-new-database tool. Deployment, DNS, SMTP, Google OAuth registration, release keys and external monitoring/backup storage are not fabricated or activated by code changes. Public rollout still requires those live gates and device/load/security acceptance.

Primary protocol/library references: [Google OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect), [argon2-cffi](https://argon2-cffi.readthedocs.io/en/stable/api.html). Source locations and test outcomes are owned by Architecture, API, Data model and Testing.

## ADR-020: Ground live extraction in deterministic temporal candidates

Date: 2026-10-28. Status: **Implemented; live evidence and residual errors in Testing**.

The first real gpt-4.1-mini benchmark passed 21/43 cases: generic evidence keys (note/title/text) did not support the validator's field-specific contract, and the model repeatedly used the summer offset for Paris in late October. Previous transport fixtures established protocol behavior but did not establish live interpretation quality.

Constrain provider-only evidence keys to the six temporal field names. Explain exact-quote evidence, execution-day versus deadline semantics, incomplete event retention and strict JSON-encoded field values; legacy JSON mode continues using ordinary objects. Enrich extraction requests with the existing deterministic parser's bounded temporal candidates, capture-local day and doubts, including validated UTF-16 choices. When explicit parsing has resolved instants and no doubts, constrain the private output schema's nullable startAt to those instants. The model still assigns intents, fields and supporting spans. Deterministic semantic validation still rejects incorrect evidence, unsupported dates, wrong clock times and DST ambiguities. Do not rewrite a wrong provider answer silently; the regression explicitly retains an injected wrong instant and requires review.

This changes the provider request contract only: public extraction v1, Room, original text and migrations remain unchanged. The benchmark now evaluates the combined model-plus-deterministic interpreter, not raw model accuracy. Retain initial and final results, and report residual conservative review rather than tuning away failed acceptance. Tests use synthetic data; live API/worker checks use a fresh disposable owner and delete that owner afterward. Direct operator benchmarks are explicitly billable and outside per-owner managed-call quotas. Keys remain in ignored backend/.env, never in reports or APKs.

Protocol reference: [official OpenAI Structured Outputs documentation](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=chat). Model availability was checked through the configured project's API; exact outcomes are in Testing.

## ADR-021: Append-only examples with provenance and explicit contribution

Date: 2026-10-28. Status: **Implemented; executed evidence and remaining UI acceptance in Testing**. Source: the user requested permanent examples, extraction outputs, user changes and greater diversity for passive dataset improvement.

Keep an owner-scoped PostgreSQL learning journal alongside existing history/corrections. Original text, prediction, deterministic application and observed user edits are distinct immutable events grouped by note. Include model/context/prompt/schema provenance, invalid/stale/review outcomes and before/after diffs. Model answers do not become gold labels; completion is an action, not a semantic correction. Existing inaccessible historical input revisions are marked unavailable. Private local input and unsent drafts are not transmitted.

Import previous synthetic reports and persist benchmark runs by default. A separate operator-only synthetic-owner flag lets QA business accounts be deleted while retaining synthetic examples; real account deletion erases its journal. Service-wide real-data export requires separate revocable account consent, off by default, with a Settings disclosure. Consent withdrawal affects future exports; already exported files/backups remain operator responsibility. No model training or global personalization starts automatically.

Generate labeled template cases independently of the local classifier and keep ambiguous/negated/mixed-language challenge cases unlabeled. Group translations, variants and note revisions to aid split hygiene; known regression cases remain exposed, not held-out claims. No new dependencies or Android business schema are required. Contract, commands and boundaries: [Dataset](DATASET.md).


## ADR-022: Resume a submitted event after final clarification

Date: 2026-10-28. Status: **Implemented; four scoped API 37 device regressions pass**. Source: the user reported dentist at 16 followed by choosing a date, with no visible Calendar event.

The previous Apply clarification action only changed the draft and required another explicit save. Preserve the pending single-event submit intent and resume it on final confirmation, labeled Save event. Partial choices lead to the next question; dismiss/Keep editing cancels the submit intent. Opening an underline for draft editing alone must not save. Seed the dialog from the fresh analysis used by submit, rather than the debounced preview, to retain an already parsed time even on an immediate Send. Save remains a Room transaction with duplicate guards and original-text preservation. After commit select the saved event's local Calendar date and show an explicit date/time receipt. No auto-creation of incomplete events, extra Task copy or external-calendar write is introduced.

The supplied ANR names com.google.android.gms.persistent, not Planner. It indicates emulator load but does not establish a Planner persistence failure. Reproduce the actual save flow on a disposable AVD and verify the Room event and selected Calendar day. See Screens and Testing.

## ADR-023: Apply complete local interpretations for private automatic capture

Date: 2026-10-28. Status: **Implemented; 33 unit tests and eight strengthened API 37 device scenarios pass**. Source: the user still sees every capture only in Journal; inspection of the connected Medium_Phone_API_37.0 UI confirmed Private mode.

The earlier default private path intentionally stored only raw text, even when its displayed local preview had a complete task/event. ADR-022 covered the submitted ambiguous single-event path, so its passing tests did not cover this ordinary Send path. Change the main composer's default Private capture to apply complete local-rule proposals in the same Room transaction as original text and local interpretation history. Uncertainty still requires clarification before structured saving. Multi-intent private capture resumes after successive choices; one private note may have several independent records. Model-derived local records remain unconfirmed until edited; explicit manual choices retain their existing confirmation semantics. No new network request, consent or Android schema is introduced. Cloud automatic capture continues through the provider; overlay remains raw capture.

Trade-off: local rules cover a bounded vocabulary rather than full model semantics. Label the preview and private mode honestly and retain an explicit Original note only choice. Never invent dates or put an undated task into Today. After save, select the matching Tasks bucket and event Calendar day so persisted records are discoverable. Deadline instants are dueAt, not scheduled appointments. Keep raw text independent and all resulting records correctable. Historical Journal notes are not silently reinterpreted in bulk; a separate explicit recovery path is needed if requested.

Implementation uses pure domain planning and repository persistence, with submission intent/state in the ViewModel; composables render controls and submit choices. PlannerBottomNavigation was already a separate stateless composable and is moved to its own file for source organization. That file split is not claimed to fix CPU usage. The supplied CPU window is investigated independently through content-free thread/ANR diagnostics; no permanent performance fix is inferred from a short idle sample.

## ADR-024: Contextual choices for tentative actions and known event times

Date: 2026-10-28. Status: **Implemented; 172 backend / 36 Android unit tests and 13 strengthened API 37 device scenarios pass**. The user reported maybe find ai course remaining outside Tasks and requested choices rather than forcing it into Task or Idea.

Expand shared local action vocabulary for EN/FR/RU and distinguish a provisional action kind from user intent. An undated/untimed tentative action exposes optional preview kindChoices: task_deadline, task_unscheduled, idea. Private automatic capture cannot apply that proposal until the user chooses or explicitly saves the original only. Inline choices update the draft; after Send, the choice resumes the pending submission. Task + deadline opens a native date picker and writes dueDate without invented plannedFor/startAt; Someday has no date; Idea has no task deadline. Original input remains independent. Unknown information remains information with an explicit Journal receipt and Change type action instead of silently coercing every sentence into a task.

A known clock with no day offers Today at that clock and Choose date, not a Someday shortcut. Bare ambiguous numbers retain time-versus-day clarification. Draft-only date buttons do not save before Send. These choices use existing manual record fields and versioned temporal hints; Room stays v4. Private capture sends no provider request. Cloud automatic classification remains a separate provider flow; its inline manual overrides remain explicit.

Scope: contextual intent choices resolve one-item drafts. Mixed drafts with unresolved kind choices ask the user to edit/split or save the original only; they never silently flatten multiple proposals into one selected kind or commit provisional tasks. Complete mixed interpretations and temporal-only clarification retain their existing behavior. Kind/deadline selections live in the Activity ViewModel; process restoration of small selection state remains the existing MVVM-04 follow-up. Benchmark expectations explicitly retain provisional kindChoices, not an assumption that maybe is a commitment or a gold task label.


## ADR-025 — Four logical layers, feature state and explicit adapter boundaries

**Date:** 2026-10-28. **Status:** implemented and locally verified; acceptance scope in [Architecture migration](ARCHITECTURE_MIGRATION.md).

The shared ViewModel exposed mutable flows, concrete services and DAO access; Compose owned some business commands. Backend routers/services mixed HTTP schemas, SQL and policies. Adopt Domain → Application as inward dependencies, with infrastructure implementations and presentation/interface callers. Keep the existing Android/Python monoliths and native overlay instead of a rewrite or new deployment/build modules.

Separate Android domain values from Room objects through explicit mappings, retaining Room v4/table contracts. Introduce shared CaptureUseCase, transactional development approval and capability ports. Feature ViewModels publish immutable UiState; content takes typed callbacks. The activity shell persists coordinated selection state and publishes atomic snackbar/undo payloads. Hilt's Compose ViewModel factory is required for navigation destinations; the new AndroidX helper is a justified DI integration dependency.

Backend application services use plain values/snapshots, UoW/query ports and external-provider/security ports. HTTP/provider adapters retain strict validation; application-assembled records also enforce domain invariants. SQLAlchemy snapshots track dirty fields and database-generated feed keys. Worker runtime retains leases/heartbeat while ProcessJob owns interpretation application, with provider calls outside transactions and original lease fencing unchanged.

Keep narrow source-compatible aliases and historical CLI/provider/security paths where renaming would add migration risk. Native overlay remains a platform adapter at its manifest-compatible path. Enforce imports with tools/check_architecture.py and behavioral regressions; no file move is evidence of CPU/ANR improvement or public-production readiness.

UX: remove explanatory/debug composer labels and duplicate type controls. Keep short contextual Deadline/Someday/Idea and Today/Date actions, underlines, the capture mode, and explicit cloud transmission consent. Original text and structured-save clarification rules are preserved.


ADR-025 implementation details: route constants and callback navigation are centralized; SyncWorker uses a constructor-injected factory configured by PlannerApp. Modal forms have opening-scoped generations and independently saved expected versions. The SQL adapter retains generated feed keys across autoflush, while plain command mapping preserves nested supplied-field metadata for existing idempotency digests. These preserve lifecycle/concurrency/transport behavior without database resets or schema downgrades.


## ADR-026: Canonical packages with feature-owned presentation

Date: 2026-10-29. Status: implemented and locally verified; 187 backend / 43 JVM / 25 API 37 scenarios; debug/release/lint pass. Source: the user requested a clear physical hierarchy after the earlier logical-layer refactor left a flat features package.

Use domain/{model,policy,interpretation,time}, application/{port,model,usecase}, infrastructure adapters and presentation organized by feature. Keep one existing Gradle module. Each screen owns its Route/Screen/ViewModel/UiState; navigation registers Routes. Extract genuine visual sections and shared components, without adding pass-through use cases or four duplicate repositories/entities for registry base kinds.

Remove old source aliases instead of preserving parallel package trees. Preserve Android entry-point/application IDs, SQL contract, DataStore/channel/work names and original notes. Move Room schema histories intact with the new class package and verify identical identity hashes. Map the old persisted worker name in the new factory and exercise it through an actual work spec. OverlayService's nonexported manifest component moves with its platform package. Settings' native permission/service binding is a narrow route exception, not screen infrastructure access. Enforce package/path/owner rules in CI, including negative tests.
