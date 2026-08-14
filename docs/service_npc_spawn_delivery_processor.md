# Service NPC spawn delivery processor — Milestone 5, Slice 3B

Slice 2 supplies the bounded, authenticated protocol client. Slice 3A owns the
schema-2 durable outbox, receipts, exact tokens, and pure delivery policy. Slice
3B connects those pieces to the server lifecycle and applies acknowledged state
to exact loaded blocks. Future Slice 4 owns UUID replacement and claim repair.
NPC assignment and entity spawning remain later-milestone work.

## Lifecycle and cadence

One processor is registered per `MinecraftServer` after `ServerStartedEvent`,
after authentication initialization and overworld storage availability. It is
stopped first during `ServerStoppingEvent`, before the shared HTTP executor and
credentials are cleared. Stop prevents selection, requests cancellation on
every active handle, clears runtime tracking, and ignores late completions.

The processor evaluates work every 20 server ticks. Each cycle considers at
most four eligible records and maintains at most two requests in flight.
Ordering is deterministic:

1. earliest `NextAttemptAtEpochMillis`
2. earliest `RecordedAtEpochMillis`
3. spawn UUID
4. operation ID

Only `READY` and due `RETRY_WAIT` records are eligible. Permanent failures and
collision-repair records are never automatically submitted. Runtime indexes
prevent duplicate operation IDs and concurrent requests for one spawn UUID.

## Attempt and request handling

Before submission, the processor captures the full operation token, calculates
the next bounded backoff, and conditionally persists attempt count, last-attempt
time, and a provisional next-attempt deadline. The record stays `RETRY_WAIT`
while transport is active; there is no persisted `IN_FLIGHT` state. A crash
after attempt persistence therefore cannot cause an immediate retry storm.

The adapter copies only protocol fields: stable operation ID, spawn UUID,
operation, revision, location, and the supported UPSERT/optional REMOVE
snapshot. Shard name is a local exact-match guard and is never serialized.
Credentials, retry state, failure codes, dispositions, collision evidence, and
block registration state are excluded from JSON.

HTTP work uses `ServiceNpcSpawnRegistrationClient` and the existing bounded
server HTTP executor. Completion always schedules `server.execute(...)` before
touching SavedData, runtime tracking, chunks, blocks, or block entities. Server
ticks never wait on a future.

## Decisions and retry state

Typed results use the closed Slice 3A decision mapper:

- success conditionally acknowledges the exact token
- transient transport, saturation, 429, and retryable 5xx use deterministic
  exponential backoff, combined with bounded Retry-After
- endpoint/protocol/malformed/oversized responses retry in exactly five minutes
- authentication/configuration failures retry in five minutes and open the
  memory-only authentication circuit
- known domain failures become durable `PERMANENT_FAILURE`
- validated replacement-required collisions become `COLLISION_REPAIR`
- cancellation leaves the crash-safe provisional attempt state unchanged

Persisted identifiers come from a closed safe-code mapping. Raw response text,
JSON, URLs, exception messages, headers, Retry-After source text, and secrets
are never persisted or logged.

Missing credentials or server key, a malformed server key, local shard
mismatch, `UNAUTHORIZED`, and `SERVER_NOT_AUTHORIZED` open a five-minute
per-server circuit. While open, selection is skipped without rewriting every
record or repeating the diagnostic. Expiry resumes selection. Any valid typed
protocol response closes the circuit; transport and domain failures do not
open it.

## Exact completion and loaded state

Runtime entries are keyed by operation ID and contain the complete immutable
token, submission timestamp, operation, location, and cancellable handle.
Completion removes the exact active entry once and verifies that the current
SavedData record still has the same full token. Reconfiguration or destruction
therefore makes the old completion obsolete without changing newer work.

UPSERT success atomically replaces pending work with a durable receipt. REMOVE
success removes only the matching pending REMOVE and creates no receipt.
Permanent and collision states are reflected as `ERROR` on an exact loaded
block, using a safe code. Ordinary retry remains `PENDING_REGISTRATION` or
`PENDING_UPDATE`, never `ERROR`.

## Replay-safe receipt application

Receipt checks are location-directed and inspect at most four receipts per
processor cycle. They require an already-loaded chunk, the spawn block and
block-entity types, matching UUID/location/revision, UPSERT, `LIVE`, and
`APPLIED` or `ALREADY_APPLIED`. No callback force-loads a chunk or searches for
the UUID elsewhere.

First pass sets `REGISTERED`, clears the transient error, stores Rails'
acknowledgement time, and persists server-only
`LastAcknowledgedOperationId` plus
`LastAcknowledgedRecordedAtEpochMillis`. The receipt remains durable. A later
matching pass sees the exact marker and conditionally consumes the receipt.
This replay-safe two-step design works across later ticks, block-entity
unavailability, chunk reload, and server restart without claiming atomicity
between chunk and SavedData files.

Reconciliation runs immediately after UPSERT success, from the block entity's
first server tick after load, and from the bounded periodic receipt pass.
Authoritative changes mark the block entity dirty, notify block tracking, and
refresh any player currently viewing its menu.

## Deliberate exclusions

Slice 3B does not generate or replace UUIDs, change claims, use
`SupersedesSpawnPointId`, increment collision repair count, scan stale claims,
reconcile WorldEdit bypasses, spawn entities, assign NPCs, mutate banking or
inventory, change Rails, or introduce schema 3. GameTests use a controlled
transport seam and must run with `runGameTestServer --no-configuration-cache`
because of the known Gradle configuration-cache serialization issue.
