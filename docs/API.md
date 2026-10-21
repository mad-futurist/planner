# API and synchronization contracts

Last reviewed: **2026-10-17**. Authoritative implementation: [routes](../backend/app/api/routes.py), [Pydantic schemas](../backend/app/schemas.py), [services](../backend/app/services.py). FastAPI generates /openapi.json and /docs. [Testing](TESTING.md) separates real HTTP compatibility from provider fixtures and live AI.

## Implemented backend contract

Base path /api/v1, JSON with camelCase fields and client-generated UUIDs. Protected routes derive owner from an enrolled bearer token. Mutation routes use a UUID Idempotency-Key, except enrollment/revocation. The server canonicalizes UUID spelling and stores payload digest + response atomically; reuse with changed input is 409.

Operators issue a 24-hour single-use invitation. POST /auth/enroll receives {code, deviceId} and returns {token, userId, expiresInDays:90}. Tokens are random and only hashes are stored. /auth/revoke invalidates the current credential. Re-enrollment requires another operator invitation for the **same owner**; consumer SSO/recovery/automatic renewal are not delivered.

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

Android task actions use record PATCH through the outbox. Dedicated complete/undo endpoints support other clients. Archive, pin, and idea deferral are PATCH fields; no cloud-delete or external-calendar endpoint is delivered.

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

ASGI bodies are capped at 131,072 bytes. Provider requests cap at 128 KiB; oversized context fails durably. Context contains up to 50 latest enabled types, 20 approved rules (contains strings up to 200 characters), and five correction examples. Provider responses cap at 128,000 bytes and generated output at 4,096 tokens; configured timeout defaults to 30 seconds. Ingestion allows 60 new notes per owner per rolling minute based on server received_at; retry of an existing operation/note is deduplicated before that limit.

## Error and job handling

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
7. Pending local operations suppress remote note/record replacement. Confirmed user records reject late unconfirmed records. Backend raw/input/user-correction guards protect the same boundary.
8. Keep this device's version explicitly fetches current server revision, replaces blocked record PATCH operations with one rebased payload at the original queue position, preserving different commands, and resends under a new operation ID. Rejected record edits can use the same explicit latest-saved-version recovery after correcting local values. It does not silently pick a winner.

PostgreSQL writers take per-owner advisory transaction locks so feed sequence allocation follows commit order. Android polls while STARTED; WorkManager provides deferred connectivity-aware and periodic sync without an exact latency promise. Owner/cursor/version meanings: [Data model](DATA_MODEL.md).

## Intended boundaries

Production communication requires HTTPS; Android's cleartext exception exists only in debug for 10.0.2.2/127.0.0.1/localhost. Provider credentials stay in backend environment variables. Cloud permission is per capture and chosen before saving; changing the current capture mode does not recall already transmitted notes. Clear local data removes device data/credentials, not server records. Export is local JSON notes/records, without credentials; it is not a cloud backup or import format.

Related: [Running](RUNNING.md), [Screens](SCREENS.md), [Decisions](DECISIONS.md).
