# Service NPC Spawn Persistence and Lifecycle (Milestone 4)

Milestone 4 adds a server-authoritative Service NPC spawn post to the NeoForge mod. It records durable intent for a later synchronization milestone, but it does not call Rails, create NPC assignments, retry work, or perform HTTP delivery.

## Authoritative configuration

The block opens `ServiceNpcSpawnMenu`, which binds the configuration session to the player UUID, container ID, dimension, block position, spawn-point UUID, and current configuration revision. Configuration and refresh packets are accepted only while that exact menu remains open and the player has permission level 2, is in the bound dimension, is within eight blocks, and is still addressing the same block entity.

City choices come from the immutable bootstrap city cache keyed by public UUID. Service NPC type choices come from the immutable bootstrap registry and include only active, spawnable types. The client never authorizes a selection. Registry overflow is reported as unavailable; a partial registry is not sent.

All packet strings and list sizes are explicitly bounded. A rejected request returns an authoritative state snapshot and a typed error without changing the block entity or pending-work store.

## Persistent block-entity state

The block entity stores:

- `SpawnPointId`
- `CityPublicId`
- `ServiceNpcTypeKey`
- `Enabled`
- `ConfigurationRevision`
- `RegistrationState`
- `LastErrorCode`
- `AssignedNpcPublicId`
- `AssignedNpcDisplayName`
- `AssignmentRevision`
- `LastSuccessfulSyncEpochMillis`
- identity-origin world name, dimension, and coordinates

Registration states are `UNCONFIGURED`, `PENDING_REGISTRATION`, `PENDING_UPDATE`, `REGISTERED`, and `ERROR`. `REGISTERED` is decode/display compatibility for future synchronization; Milestone 4 never transitions a post into it. Enabled remains a separate boolean.

New posts start enabled and unconfigured at revision zero. The first accepted complete configuration advances to revision one and creates `PENDING_REGISTRATION`; later accepted changes create `PENDING_UPDATE`. Revisions use checked `long` increments. No-op, invalid, stale, or unauthorized requests do not increment the revision or create pending work.

Assignment and last-synchronization fields are display-only compatibility fields in this milestone. Milestone 4 does not populate an assignment.

## UUID lifecycle and copy repair

The block-entity constructor does not create a UUID. Ordinary placement creates it only on the logical server, stamps the actual location, and adds a durable claim. Legacy NBT with no UUID is initialized on its first server tick.

Claims are stored server-save-wide in overworld `SavedData`. A claim contains the spawn-point UUID plus save name, dimension, and coordinates. A matching claim makes the current post canonical. A claim elsewhere, or an identity-origin stamp elsewhere, makes a copied post rekey itself. Legacy data with neither a claim nor an origin uses the first server-side claimant.

Repaired `/clone` and structure-template copies preserve the original post and claim, receive a new random UUID, and reset to new-post defaults. City, type, revisions, registration state, assignment cache, synchronization cache, and errors are cleared; enabled returns to true. Repair does not enqueue an UPSERT.

Tools that bypass normal block callbacks remain an administrative limitation. A stale claim intentionally causes conservative rekeying instead of allowing a later copy to steal an uncertain canonical identity.

## Durable pending work

Pending work is stored in overworld `SavedData` under `britannia_service_npc_spawn_pending`, schema version 1. Each UUID has at most one current operation:

- A newer UPSERT supersedes an older UPSERT.
- An identical UPSERT at the same revision is idempotent.
- A conflicting UPSERT at the same revision, or a lower-revision UPSERT, is rejected.
- REMOVE supersedes UPSERT and is terminal for that UUID.
- REMOVE replaces REMOVE only by the revision/timestamp ordering rules.

The store never automatically prunes unacknowledged work and warns at 16,384 records. Its future-facing API is limited to an immutable `snapshot()` and conditional `acknowledgeIfMatches(...)`.

Records are decoded independently. Malformed records are logged, quarantined outside normal snapshots, and retained when the data is saved. An unsupported root schema is preserved read-only and rejects mutation rather than overwriting future-format data.

Milestone 4 does not implement delivery, retries, backoff, credentials, or Rails authentication.

## Removal and item behavior

True block destruction persists REMOVE before releasing the matching UUID claim. The helper is idempotent and is invoked by the authoritative block replacement/removal path, including normal break, replacement, and explosion. Block-entity unload, save/reload, `setRemoved`, and client callbacks do not create REMOVE work.

The block uses `PushReaction.BLOCK` and cannot be moved by normal or sticky pistons.

Drops and block-entity item serialization are deliberately clean. Block-item data does not retain identity, configuration, enabled state, revisions, registration/assignment/synchronization state, origin metadata, or pending-operation data. A clean loot table supplies the ordinary drop.

## Verification

Focused JUnit coverage exercises immutable caches, state transitions, revisions, queue supersession and corruption handling, claim resolution, validation, item-tag sanitization, and bounded codecs.

The dedicated headless GameTest server covers new and distinct identities, save/reload reconstruction, survival and creative player breaks, block replacement, explosion, unload semantics, normal/sticky pistons, `/clone`, structure-template copy repair, clean drops, and block-entity item serialization. The generated GameTest holder and template are excluded from distributable JARs while remaining available to the development run through the main classes/resources directories.

Actual Ctrl+middle-click and third-party WorldEdit behavior are manual compatibility checks; GameTests do not claim coverage for those external interaction paths.
