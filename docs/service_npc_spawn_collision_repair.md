# Service NPC spawn UUID collision repair — Milestone 5, Slice 4

Milestone 4 handles duplicate identities that are visible locally during normal
placement, clone, structure, and claim reconciliation. Slice 3B persists a
typed Rails collision without changing identity. Slice 4 performs the bounded
local replacement authorized by that durable typed result. General stale-claim
or WorldEdit-bypass reconciliation remains future work, as do NPC assignment
and entity spawning.

## Authority and evidence

Automatic replacement is permitted only for a decoded Rails
`UUID_COLLISION` with `replacement_uuid_required=true`. Generic HTTP 409,
timeouts, authentication errors, revision conflicts, location conflicts,
unknown outcomes, and malformed responses never authorize replacement.

The supported collision kinds are:

- `LIVE`: canonical server, dimension, and coordinates must be available. A
  canonical location matching the authenticated local server key, dimension,
  and coordinates is rejected as `uuid_collision_same_location`. World/save
  name is diagnostic and is not part of this equality check.
- `TOMBSTONED`: the prior UUID remains owned by a Rails tombstone and may be
  replaced locally without canonical-location details.
- `REDACTED`: foreign ownership stays undisclosed; the local UUID may be
  replaced without inferring a canonical location.

No repair sends a REMOVE for the colliding UUID or changes a Rails row.

## Durable phases and bounds

Phase A is the Slice 3B state: the colliding UUID is the record key,
disposition is `COLLISION_REPAIR`, typed evidence is present, and
`SupersedesSpawnPointId` is absent.

Phase B is staged atomically in schema 2: a generated UUIDv4 becomes the
record key, a fresh operation UUID is assigned, revision resets to one,
`SupersedesSpawnPointId` records the immediate prior UUID, retry metadata is
cleared, and disposition remains `COLLISION_REPAIR`. The configuration and
physical location are preserved. Phase B is not network eligible.

A maximum of two repair records is inspected per normal 20-tick processor
cycle, ordered by recorded timestamp, spawn UUID, and operation ID. UUID
generation tries at most 32 random UUIDv4 candidates and rejects the old UUID,
claims, pending records, receipts, and local operation-ID collisions.

At most three consecutive replacement UUIDs are generated. A fourth valid
collision becomes `PERMANENT_FAILURE` with
`uuid_collision_repair_exhausted`.

## Replay-safe ordering

Chunk data, claim SavedData, and pending SavedData are separate files. Repair
therefore uses replay-safe ordering rather than claiming filesystem
transactionality:

1. Validate the exact Phase-A token, evidence, loaded block, revision, location,
   and configuration.
2. Atomically replace pending A with staged pending B.
3. Claim B at the exact location if unclaimed.
4. Release A only if A still claims that exact location.
5. Update the exact block entity from A to B.
6. Reset revision, registration cache, acknowledgement markers, and assignment
   cache; preserve city, Service NPC type, enabled state, location, and ordinary
   block state.
7. Verify the block and B claim.
8. Conditionally change the exact Phase-B record to `READY`.

A crash before staging leaves Phase A. A crash after staging reuses the same B
and operation ID. A block already containing B repairs a missing B claim; a B
claim with a block still containing A repairs the block. A block and claim
already matching B only need the exact READY transition. Reconciliation is
idempotent on block load and during later bounded cycles.

The processor skips every `COLLISION_REPAIR` record. Once READY is durably
confirmed, ordinary Slice 3B delivery may proceed even if the chunk later
unloads.

## Claim and block safety

The coordinator checks only the collision record's recorded location and its
old/new claims. It never scans all claims or block entities and never
force-loads a chunk.

A claim for A at another location is left untouched. An A claim at the repaired
location is released only after B is claimed. An existing B claim at the same
location is idempotent; a B claim elsewhere is never stolen and permanently
blocks delivery with `collision_repair_claim_conflict`.

The replacement block becomes `PENDING_REGISTRATION`, revision one, with a
cleared error, synchronization timestamp, acknowledgement marker, and
assignment display cache. City, type, enabled state, orientation, and ordinary
block state are preserved. The staged outbox record is the only replacement
UPSERT.

Configuration requests are rejected while Phase B remains unconfirmed. After
B becomes READY, a normal revision-two configuration may compact the operation
while retaining immediate lineage and repair count. A newer ordinary Phase-A
configuration safely supersedes the old collision token.

Destruction before staging replaces A's collision with the normal authoritative
REMOVE and no B is generated. Destruction after staging prevents B delivery;
destruction after the block reports B creates REMOVE for B. Repair itself never
creates REMOVE for A.

## Safe diagnostics

Permanent repair failures use closed codes:

- `uuid_collision_same_location`
- `uuid_collision_repair_exhausted`
- `uuid_generation_exhausted`
- `collision_repair_block_missing`
- `collision_repair_block_mismatch`
- `collision_repair_configuration_mismatch`
- `collision_repair_claim_conflict`
- `collision_repair_invalid_evidence`
- `collision_repair_staged_state_invalid`
- `collision_repair_failed`

Logs contain only UUIDs, operation IDs, repair count, collision kind,
same-server location, and safe codes. They exclude credentials, headers, raw
Rails bodies, exception messages, NBT, and REDACTED canonical information.

GameTests use durable typed collision fixtures and run with
`runGameTestServer --no-configuration-cache` because of the existing Gradle
configuration-cache serialization limitation.
