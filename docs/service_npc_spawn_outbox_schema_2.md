# Service NPC durable outbox — Milestone 5, Slice 3A

Milestone 4 records local physical-post intent. Slice 2 provides a manually invoked, bounded Rails protocol client. Slice 3A adds durable delivery state and pure policy only. It does not scan the outbox, submit HTTP, schedule retries, mutate blocks, consume receipts from block entities, or repair UUIDs. Automatic delivery belongs to Slice 3B; collision repair belongs to Slice 4.

## SavedData root

The existing overworld-owned save-wide file remains `britannia_service_npc_spawn_pending`. Schema 2 is:

```text
SchemaVersion: 2
Records: [pending operation compounds]
Acknowledgements: [UPSERT receipt compounds]
```

Corrupt pending and acknowledgement compounds retain the existing quarantine convention: they are excluded from active maps but preserved in their respective serialized collections. Pending and acknowledgement collections are independently bounded at 16,384 entries. An over-limit root is preserved read-only rather than partially loaded or pruned.

Missing, malformed, negative, or unsupported schema versions fail closed. The untouched root is preserved and re-saved without downgrade or partial decoding.

## Schema-1 migration

A schema-1 root is decoded through the original intent fields, marked dirty, and next saved as schema 2. Operation, spawn UUID, shard name, location, optional city/type snapshot, enabled state, configuration revision, and original timestamp are retained. Existing corrupt/duplicate compounds remain quarantined.

Each migrated operation receives a deterministic RFC UUIDv3 derived with UTF-8 from:

```text
britannia:service_npc_spawn_pending:v1-to-v2:
spawn UUID | operation | revision | recorded timestamp | world | dimension | x | y | z
```

Repeated unsaved loads therefore produce the same Rails-valid operation ID. Newly enqueued schema-2 operations use random UUIDv4 IDs.

## Pending record

The original intent fields remain, with:

- `OperationId`
- `Disposition`
- `AttemptCount`
- optional `LastAttemptAtEpochMillis`
- `NextAttemptAtEpochMillis`
- optional `LastFailureCode`
- optional `SupersedesSpawnPointId`
- `CollisionRepairCount`
- optional `CollisionEvidence`

Dispositions are closed: `READY`, `RETRY_WAIT`, `PERMANENT_FAILURE`, and `COLLISION_REPAIR`. In-flight is deliberately memory-only for Slice 3B. Attempt/collision counters and timestamps are nonnegative; failure codes are limited to `[a-z0-9_]{1,64}` and never contain bodies, URLs, exception messages, headers, or credentials.

A full operation token contains operation ID, spawn UUID, operation, configuration revision, and recorded timestamp. Every attempt, retry, permanent-failure, collision, and acknowledgement mutation compares the complete token so an obsolete completion cannot mutate newer compacted work.

Milestone 4 compaction remains:

- equal identical UPSERT/REMOVE retains the record, ID, timestamp, and delivery state
- a higher UPSERT or accepted replacement REMOVE carries a newly generated ID and fresh delivery metadata
- equal conflicting or stale UPSERT is rejected
- REMOVE replaces UPSERT and remains terminal against later UPSERT
- a new accepted local operation removes any stale receipt for that spawn UUID

`markAttemptStarted` increments safely and records a provisional retry-wait timestamp so a crash cannot cause an immediate retry storm. The remaining exact-token APIs store retry wait, permanent failure, or validated collision-repair evidence. They calculate or schedule nothing themselves.

## Collision evidence

Evidence stores only the typed `LIVE`, `TOMBSTONED`, or `REDACTED` kind, the required replacement flag, optional safe LIVE canonical location, and receipt time. Canonical location contains only Minecraft server UUID, world, dimension, and bounded coordinates. REDACTED/TOMBSTONED evidence cannot contain canonical details. Raw JSON, shard credentials, owner data, city/type data, and database IDs are never stored. Slice 3A does not change the spawn UUID, claim, block, or repair count.

## Acknowledgement receipts

A validated `APPLIED` or `ALREADY_APPLIED` UPSERT success atomically replaces the exact pending record with one receipt. The receipt contains the operation token, submitted location, acknowledged revision, `LIVE`, acknowledgement time, and outcome. It contains no credential, server key, city, or Service NPC type.

A successful REMOVE removes only the exact pending operation and creates no receipt because its block is already absent. Obsolete or inconsistent success cannot remove newer work.

One UPSERT receipt is retained per spawn UUID. Higher revisions may replace lower receipts; lower receipts cannot replace higher. Equal-revision replacement requires the same acknowledged block snapshot. Receipt lookup and exact conditional consumption are data-only APIs for future Slice 3B/block application; no production caller consumes them in this slice.

## Pure retry policy

Backoff uses:

```text
min(5 minutes, 5 seconds × 2^(attempt-1) × deterministic jitter)
```

Jitter is deterministically derived from operation UUID and attempt count in the inclusive ±20% band. The final result never exceeds five minutes. Retry-After combines as `max(backoff, bounded hint)`, still capped at five minutes. Epoch addition saturates instead of wrapping. Slow compatibility, authentication/configuration, malformed/oversized response, and endpoint-unavailable policy is exactly five minutes.

## Pure delivery decisions

The closed decisions are:

- `ACKNOWLEDGE_SUCCESS`: valid `APPLIED` / `ALREADY_APPLIED`
- `RETRY_WITH_BACKOFF`: timeouts, executor saturation, generic transport, 429, and retryable 5xx
- `RETRY_AT_SLOW_INTERVAL`: endpoint unavailable, response protocol incompatibility, malformed response, and oversized response
- `AUTHENTICATION_BLOCKED`: missing/malformed server configuration or Rails authentication/authorization rejection
- `PERMANENT_FAILURE`: known nonretryable request/domain outcomes, including stale/revision conflict and location occupied
- `COLLISION_REPAIR`: only a validated typed replacement-required UUID collision
- `NO_CHANGE`: explicit cancellation

This mapper performs no I/O and does not mutate the outbox.

## Security and slice boundary

Shard name remains part of Milestone 4 local intent. Shard secrets, authorization headers, server configuration keys, raw responses, exception messages, runtime URLs, and retry implementation state are excluded. A same-shard LIVE collision may explicitly retain the Rails-approved non-secret canonical Minecraft server UUID inside collision evidence.

No production code added here calls `ServiceNpcSpawnRegistrationClient.submit`. No executor, background thread, tick hook, scanner, lifecycle registration, in-flight map, HTTP callback, block lookup, claim update, client packet, block-state acknowledgement application, or UUID replacement exists in Slice 3A.
