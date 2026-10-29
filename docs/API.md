# API and synchronization contracts

Last reviewed: **2026-10-28**. Authoritative implementation: [routes](../backend/app/api/routes.py), [accounts](../backend/app/api/accounts.py), [Pydantic schemas](../backend/app/schemas.py), [services](../backend/app/services.py). FastAPI generates /openapi.json and /docs. [Testing](TESTING.md) separates real HTTP compatibility from provider fixtures and live AI.

## Implemented backend contract

Base path /api/v1, JSON with camelCase fields and client-generated UUIDs. Protected routes derive owner from an enrolled bearer token. Mutation routes use a UUID Idempotency-Key, except enrollment/revocation. The server canonicalizes UUID spelling and stores payload digest + response atomically; reuse with changed input is 409.

Operators can still issue a 24-hour single-use invitation. POST /auth/enroll receives {code, deviceId} and returns {token, userId, expiresInDays:90}. Public email/password and Google login issue the same device session. Tokens are random and only hashes are stored. /auth/revoke invalidates the current credential. Signing in again renews access to the same owner; switching an existing local dataset to another owner requires explicit local export/clear.

## Endpoint inventory

Paths below omit /api/v1.

| Method/path | Actual behavior |
| --- | --- |
| POST /auth/enroll; POST /auth/revoke; GET /config | Enrollment/revocation; config reports configured key/model, not provider health |
| POST /notes | Atomic authorized raw ingestion, explicit manual records or durable extraction job; stable note identity |
| GET /notes | Keyset pagination, limit 1–100/default 50; notes + nextCursor |
| GET /notes/{id} | Note metadata plus stored records, authorized or 404 |
| PATCH /notes/{id} | {expectedVersion, content}; retains originalContent, increments revision and queues extraction |
| POST /notes/{id}/reprocess | Requeue failed/unconfigured/finished interpretation through a durable job |
| GET /notes/{id}/interpretation | Note processingStatus, latest history and active records |
| PATCH /records/{id} | Complete RecordMutation including expectedVersion; validates type/version/provenance |
| POST /records/{id}/complete; POST /records/{id}/undo | Expected-version task action; completion timestamp is independent |
| POST /notes/{id}/corrections | {noteId, expectedNoteVersion, records}; supports notes with no completed AI history |
| POST /interpretations/{id}/corrections | Same correction DTO associated with a history; preserves history |
| GET /types; POST /types; PATCH /types/{id} | Versioned registry; PATCH appends next version and retains older definitions |
| POST /rules | Persist explicitly approved bounded contains → baseKind rule; complex rules rejected |
| POST /ideas/{id}/develop | Durable development job for current idea/input revision; response is a job, not fabricated suggestions |
| GET /jobs/{id} | Durable processing state and optional development result |
| GET /changes | Identity-scoped ordered incremental feed |
| GET /health (outside prefix) | Database health and aiConfigured; no authentication |

Android task actions use record PATCH through the outbox. Dedicated complete/undo endpoints support other clients. Archive, pin, and idea deferral are PATCH fields. Account deletion is described below; per-record cloud deletion and external-calendar endpoints are not delivered.

## DTOs and bounds

Minimal capture:
```json
{
  "id": "61a68d9e-708a-4a01-84e6-774de311d2e1",
  "content": "Call the doctor tomorrow",
  "createdAt": "2026-10-15T08:00:00.000Z",
  "source": "APP",
  "privacyMode": "CLOUD_ALLOWED",
  "locale": "en",
  "timezone": "Europe/Paris"
}
```

NoteDTO additionally accepts originalContent, updatedAt, version, processingStatus, syncStatus, manual and records; server controls processing/ownership. Maximum text 20,000 characters, up to 12 initial records. Initial records require explicit manual=true and matching sourceNoteId. LOCAL_ONLY is not a valid server privacy mode: private notes never enter upload.

RecordDTO requires id/sourceNoteId/title. It carries baseKind, customTypeId/typeSchemaVersion, fields (up to 32), distinct startAt/endAt/dueAt/dueDate/plannedFor/remindAt/timezone/recurrenceRule, state/completedAt, pinned/deferredUntil, creation/update, interpretationVersion/isUserConfirmed/version. PATCH is a full validated record plus expectedVersion, not an arbitrary partial dictionary. Title is 1–1,000 characters. Offset instants normalize to millisecond UTC; day-only dates stay distinct. See [Data model](DATA_MODEL.md#temporal-semantics).

TypeDTO includes UUID/schemaVersion/name/baseKind, up to 32 primitive field definitions, displayConfiguration, allowedCapabilities and enabled. FieldSpec declares type, label, required, enum values and optional unit. Existing records retain a pinned schema version.

Extraction is schemaVersion=1 with up to 12 proposals and ambiguity reasons. Each proposal includes kind/title/fields, optional custom type/temporal values, and original-text evidence. Temporal validation checks both dates and actual clock times; unsupported expressions or repeated local times require review. History records riskLevel validated_low/review_required rather than a confidence probability. Development returns 3–5 nonblank steps, each at most 1,000 characters.

Bare-hour evidence such as “Thursday at 14”, “jeudi à 14” or “четверг в 14” can validate 14:00. Quantities, partial year matches, malformed minutes and unsupported dot-separated clocks cannot authorize an invented hour/minute; they remain review cases.

Provider-only refinement (2026-10-28; public DTOs remain unchanged): strict evidence entry keys enumerate startAt/endAt/dueAt/dueDate/plannedFor/remindAt. Extraction context adds bounded temporalGrounding with capture-local day, up to 12 deterministic candidates and 24 doubts. Explicit resolved instants without doubts restrict nullable startAt values in the provider output schema; semantic evidence/clock/date/DST checks still run, including for legacy JSON mode. A wrong model value is reviewed, never silently replaced. Real-provider results and one residual unnecessary quantity review are in Testing.

ASGI bodies are capped at 128,000 bytes. Provider requests cap at 128 KiB; oversized context fails durably. Context contains up to 50 latest enabled types, 20 approved rules (contains strings up to 200 characters), and five correction examples. Provider responses cap at 128,000 bytes and generated output at 4,096 tokens; configured timeout defaults to 30 seconds. Ingestion allows 60 new notes per owner per rolling minute based on server received_at; retry of an existing operation/note is deduplicated before that limit.

## Error and job handling

2026-10-21 provider increment (**In Progress; live inference blocked without credentials**): `LLM_RESPONSE_FORMAT=auto` attempts strict closed JSON Schema, then retries once in JSON-object mode only for an explicit unsupported `response_format` HTTP 400/422. `json_schema` disables fallback; `json_object` explicitly selects legacy mode. Provider-only fields/evidence use bounded key/value lists, decoded and validated into unchanged extraction v1 DTOs. Truncation/refusal, malformed/duplicate fields, authorization failures and ordinary invalid output never trigger fallback. Both paths retain Pydantic, evidence/date/clock/DST and registry validation. Strict format requirements: [official OpenAI documentation](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=chat).

HTTP error bodies use FastAPI detail; validation errors contain field-specific entries. UI displays safe messages and retains local work.

| Response/state | Meaning / client action |
| --- | --- |
| 400 | Invalid idempotency UUID; terminal operation failure |
| 401 | Missing/expired/revoked enrollment; reconnect with a new same-owner invitation |
| 403/404 | Rejected or absent resource; never expose another owner's record |
| 409 | Revision mismatch or conflicting ID/key; persist conflict and require explicit resolution |
| 413/422 | Size/schema/semantic rejection; retain saved local note and mark failed operation |
| 429 / 5xx / timeout | Backoff/jitter with bounded retry; local data remains readable |
| queued/running | Durable processing pending/in flight; no capture lock |
| unconfigured | Provider key/model absent; manual organization works, reprocess after configuration |
| complete / stale / failed | Persisted result, rejected obsolete result, or terminal safe error |

Transport has up to eight attempts and visible failed/conflict state. AI jobs have up to five attempts by default; timeout/network/429/5xx are retryable, invalid schema/output is terminal. A two-minute lease and token recover interrupted workers and reject obsolete writes. Changing configuration does not invent a completed result; explicitly reprocess terminal unconfigured jobs.

## Synchronization protocol

1. Room commits note + eligible outbox with stable IDs and immutable payload. Private notes have no cloud outbox.
2. WorkManager or foreground refresh serializes sync. A note creation must precede its dependent edits; failed/conflicting earlier work blocks later operations for that entity.
3. Server authentication, idempotency, ingestion/job/changes and response storage commit atomically.
4. Client removes acknowledged operation and applies eligible response inside a Room transaction.
5. GET /changes?cursor=0&limit=100 returns {changes:[{sequence,kind,entityId,payload}],nextCursor,hasMore}. Cursor is a nonnegative sequence string; up to 100 rows and roughly 1 MiB per page. Sequence gaps from other identities are normal.
6. Client applies note/record/history/type/rule/job changes and cursor together. Replayed UUIDs upsert. Archives remain changes. Initial manual capture publishes the parent note before its records.
7. Pending local operations defer remote note/record replacement into durable Meta snapshots, committed with the cursor. A rotating bounded reconciliation scan applies them after pending work clears. Only reconciliation consumes these snapshots; older acknowledgments cannot erase newer same-version processing statuses. Confirmed user records reject late unconfirmed records. Backend raw/input/user-correction guards protect the same boundary.
8. Keep this device's version explicitly fetches current server revision, replaces blocked record PATCH operations with one rebased payload at the original queue position, preserving different commands, and resends under a new operation ID. Rejected record edits can use the same explicit latest-saved-version recovery after correcting local values. It does not silently pick a winner.

PostgreSQL writers take per-owner advisory transaction locks so feed sequence allocation follows commit order. Android polls while STARTED; WorkManager provides deferred connectivity-aware and periodic sync without an exact latency promise. Owner/cursor/version meanings: [Data model](DATA_MODEL.md).

## Intended boundaries

Production communication requires HTTPS; Android's cleartext exception exists only in debug for 10.0.2.2/127.0.0.1/localhost. Provider credentials stay in backend environment variables. Cloud permission is per capture and chosen before saving; changing the current capture mode does not recall already transmitted notes. Clear local data removes device data/credentials, not server records. Export is local JSON notes/records, without credentials; it is not a cloud backup or import format.

Related: [Running](RUNNING.md), [Screens](SCREENS.md), [Decisions](DECISIONS.md).

## Public authentication and recovery

Auth routes below do not use business-mutation Idempotency-Key. All paths use /api/v1. Passwords are 12–128 characters; credentials never enter validation error bodies. Register/forgot responses are generic to avoid directly revealing registered addresses. SMTP is required; unconfigured mail/Google capabilities return 503 and are reported by GET /auth/options.

| Route | Contract |
| --- | --- |
| GET /auth/options | Available email/Google capabilities |
| POST /auth/register | {email,password}; 202, sends single-use 24-hour verification link; existing account is never overwritten |
| POST /auth/login | {email,password,deviceId}; verified email required; returns token/userId/expiresInDays |
| POST /auth/forgot-password | {email}; 202 generic response, single-use one-hour reset email if account exists |
| POST /auth/verify-email | JSON {token}; consumes verification token |
| GET /auth/verify-email | Browser confirmation form; GET does not consume token |
| POST /auth/verify-email/confirm | URL-encoded {token}; explicit browser confirmation |
| GET /auth/password-reset | Browser password form; GET does not consume token |
| POST /auth/password-reset | URL-encoded {token,password}; completes recovery |
| POST /auth/reset-password | JSON {token,password}; completes recovery, revokes all old sessions and pending Google links |
| POST /auth/google/start | {challenge,password?}; SHA-256 PKCE challenge; optional bearer plus current password explicitly links Google |
| GET /auth/google/callback | Server OAuth redirect; verifies code/state, Google signature/issuer/audience/nonce/expiry/verified email; redirects one-use handoff to app.planner://auth |
| POST /auth/google/exchange | {state,code,verifier,deviceId}; code is the one-use app handoff; 10-minute flow and PKCE binding; returns device session |
| DELETE /auth/account | Bearer plus {ownerId,confirmation:"DELETE",password}; deletes this owner's active server data, returns safe confirmation |

Email/password hashes use Argon2id. Google email matching alone never links owners; existing accounts must sign in and explicitly link. A Google-only account can set a password through verified-email recovery before password-confirmed deletion. Google secrets remain server-side. Login permits 10 attempts/IP/action per fixed UTC minute; registration/recovery permit 5. Counters use keyed hashes and survive restarts. Recipient mail has a two-minute cooldown. 429 includes Retry-After. These bounds do not establish resistance to distributed abuse; provider/edge controls and live delivery acceptance remain launch gates.

## Preview, explicit choices and operational endpoints

Local POST /preview records add optional kindChoices (default empty for older responses). Values task_deadline, task_unscheduled, idea mark an undated/untimed tentative action needing a user organization choice; baseKind is provisional in that case. This is additive preview metadata, not a new business kind, temporal grant, extraction-v1 field or automatic commitment. Android private auto-apply rejects unresolved choices. Explicit choices use existing manual records/dueDate and date/time hints; no new server endpoint or hint-kind value is introduced. POST /preview/ai remains the separate model contract.

POST /preview and POST /preview/ai are authenticated previews that create no business note/event. Input includes content, createdAt, timezone, locale and optional interpretationHints. /preview is deterministic local-rules parity with no stored sample. /preview/ai performs an explicit potentially billable provider call, retains an owner-scoped learning observation (including safe error outcomes), uses the same bounded registry/rules/corrections and semantic validation as worker extraction, and returns provider/engine, records, needsReview/reviewReasons and previewOnly. Its confirmation discloses retained interpretation history. No automatic network preview occurs on typing. Cloud consent is enforced in the Android caller; contribution consent, authentication and budgets are separate server controls. Dataset endpoints and export boundaries: [Dataset](DATASET.md).

NoteDTO adds interpretationHints={schemaVersion:1,clarifications:[...]}, defaulting to {} for older notes. Clarifications identify exact UTF-16 start/end offsets, matching text, kind=date/time/datetime/literal and corresponding date/time values. At most 24 choices; surrogate splits, overlapping spans, stale text and invalid field combinations are rejected. Choice hints authorize only the selected start/deadline/planned date/time, never an invented end or reminder. Raw-note PATCH invalidates hints. Unresolved event ambiguities require review and cannot silently become precise events.

Explicit Save original without event sends manual=true and records=[] through the existing ingestion contract. It synchronizes the raw note without scheduling automatic interpretation. This differs from ordinary Note for AI capture, which can schedule a worker. The private variant produces no upload.

GET /diagnostics returns owner-scoped processing counts, oldest queued work and actual managed provider calls used/cap. Daily budget defaults to 100 actual requests per owner/UTC day and covers worker plus explicit AI preview; absent provider configuration consumes none. Exhaustion defers worker work with rate_limited status until the next UTC day without consuming an attempt; preview returns 429. GET /ready (outside prefix) requires database and a fresh worker heartbeat (45 seconds), returning 503 otherwise; /health remains database-only. Neither endpoint certifies provider response quality.


## Architecture migration compatibility

The 2026-10-28 structural refactor retains `/api/v1`, endpoint paths, DTO validation, UUID/idempotency keys, version conflicts, owner scoping, cursor/response limits and wire names. Resource routers map validated DTOs to plain application commands; application services own transaction decisions through UnitOfWork. ORM and provider/security clients remain adapters. Worker and explicit AI preview release database transactions before external provider I/O. No API or PostgreSQL migration is introduced by this refactor. Existing public-account and clarification/dataset migrations remain part of the checkout.


Boundary mapping retains evaluated wire defaults and recursively tracks supplied nested fields. Both ordinary serialization and exclude_unset serialization match the prior DTO payload, preserving idempotency fingerprints for records, temporal hints and field definitions. Backend compatibility regressions and the API 37 real-HTTP manual sync test verify this behavior.
