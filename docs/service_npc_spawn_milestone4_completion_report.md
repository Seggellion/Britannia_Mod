# Service NPC Spawn Block Milestone 4 Completion Report

**Status: Automated implementation complete; manual verification pending.**

This recovery report was reconstructed on 2026-07-15 from the committed implementation, the seven-file repair diff, current source inspection, current automated verification, and the supplied recovery-audit record. Milestone 4 is not fully complete and must not be merged until the required manual checks are completed or a human explicitly accepts a documented exception.

## 1. Branch and base commit

- Branch: `feature/service-npc-m4-local-spawn-block`
- Upstream: none configured
- `banking` merge base: `e5ae1a1e6961d5ba785993f1d360f9cd2958bba5`
- Base subject: `Merge branch 'feature/service-npc-m3-dialogue-registry' into banking`

## 2. Milestone 4 implementation commit

- Commit: `f8be72b7b0a82ec59e44bde23452b0a8fce471d6`
- Subject: `WIP: recover interrupted Service NPC Milestone 4 implementation`
- The branch contains that one implementation commit after the `banking` merge base. The recovery work did not amend or rewrite it.

## 3. Recovery-repair status

The recovery audit left seven source/test files modified. Revalidation confirmed that the changes belong to the stated audit scope. Source review found one remaining in-scope edge case: a Service NPC registry containing only inactive or non-spawnable types had to be treated as unavailable, consistently with the authoritative option payload. The validator and its existing focused test file were tightened accordingly. No repair change exceeds Milestone 4 scope.

The repaired behavior is:

1. Normal placement discards preloaded UUID and configuration data and creates server-owned identity.
2. An empty city registry is unavailable.
3. A Service NPC registry with no active, spawnable configuration option is unavailable.
4. Authoritative S2C state preserves enabled state, pending state, revision, validation status, assignment display fields, and registry-availability behavior.
5. Pending UPSERT and REMOVE records survive store serialization and reload.
6. GameTests cover hostile placement data and valid cached configuration producing pending state.
7. Focused tests cover empty/non-configurable registries and authoritative payload round trips.

## 4. Files created by the recovered Milestone 4 implementation

- `docs/service_npc_spawn_persistence_lifecycle.md`
- `src/gametest/service_npc_spawn_test_empty.nbt.b64`
- `src/main/java/com/seggellion/britannia_mod/block/ServiceNpcSpawnBlock.java`
- `src/main/java/com/seggellion/britannia_mod/block/entity/ServiceNpcSpawnBlockEntity.java`
- `src/main/java/com/seggellion/britannia_mod/city/BootstrapCityDefinition.java`
- `src/main/java/com/seggellion/britannia_mod/city/BootstrapCityRegistryCache.java`
- `src/main/java/com/seggellion/britannia_mod/city/BootstrapCityRegistrySnapshot.java`
- `src/main/java/com/seggellion/britannia_mod/client/screen/ServiceNpcSpawnScreen.java`
- `src/main/java/com/seggellion/britannia_mod/gametest/ServiceNpcSpawnGameTests.java`
- `src/main/java/com/seggellion/britannia_mod/menu/ServiceNpcSpawnMenu.java`
- `src/main/java/com/seggellion/britannia_mod/network/payload/ServiceNpcSpawnConfigureC2SPayload.java`
- `src/main/java/com/seggellion/britannia_mod/network/payload/ServiceNpcSpawnPayloadCodec.java`
- `src/main/java/com/seggellion/britannia_mod/network/payload/ServiceNpcSpawnPayloadHandler.java`
- `src/main/java/com/seggellion/britannia_mod/network/payload/ServiceNpcSpawnResyncC2SPayload.java`
- `src/main/java/com/seggellion/britannia_mod/network/payload/ServiceNpcSpawnStateS2CPayload.java`
- `src/main/java/com/seggellion/britannia_mod/registry/MenuRegistry.java`
- `src/main/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnClaim.java`
- `src/main/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnClaimData.java`
- `src/main/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnConfigurationValidator.java`
- `src/main/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnIdentityResolver.java`
- `src/main/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnItemDataSanitizer.java`
- `src/main/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnLocation.java`
- `src/main/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnMenuRequestValidator.java`
- `src/main/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnPendingData.java`
- `src/main/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnPendingOperation.java`
- `src/main/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnPendingRecord.java`
- `src/main/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnPendingWorkSource.java`
- `src/main/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnRegistrationState.java`
- `src/main/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnStateMachine.java`
- `src/main/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnValidationError.java`
- `src/main/resources/assets/britannia_mod/blockstates/service_npc_spawn_block.json`
- `src/main/resources/assets/britannia_mod/models/block/service_npc_spawn_block.json`
- `src/main/resources/assets/britannia_mod/models/item/service_npc_spawn_block.json`
- `src/main/resources/data/britannia_mod/loot_table/blocks/service_npc_spawn_block.json`
- `src/test/java/com/seggellion/britannia_mod/city/BootstrapCityRegistryCacheTest.java`
- `src/test/java/com/seggellion/britannia_mod/network/payload/ServiceNpcSpawnPayloadCodecTest.java`
- `src/test/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnClaimResolverTest.java`
- `src/test/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnConfigurationValidatorTest.java`
- `src/test/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnItemDataTest.java`
- `src/test/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnMenuValidationTest.java`
- `src/test/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnPendingDataTest.java`
- `src/test/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnStateTest.java`

## 5. Files modified by the recovered Milestone 4 implementation

- `.github/workflows/build.yml`
- `build.gradle`
- `src/main/java/com/seggellion/britannia_mod/BritanniaMod.java`
- `src/main/java/com/seggellion/britannia_mod/ClientModSetup.java`
- `src/main/java/com/seggellion/britannia_mod/event/WorldBootstrapHandler.java`
- `src/main/java/com/seggellion/britannia_mod/network/ClientNetworkHandler.java`
- `src/main/java/com/seggellion/britannia_mod/network/NetworkHandler.java`
- `src/main/java/com/seggellion/britannia_mod/registry/BlockEntityRegistry.java`
- `src/main/java/com/seggellion/britannia_mod/registry/BlockRegistry.java`
- `src/main/java/com/seggellion/britannia_mod/registry/CreativeTabRegistry.java`
- `src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java`
- `src/main/resources/assets/britannia_mod/lang/en_us.json`

## 6. Files modified by the repair audit

- `src/main/java/com/seggellion/britannia_mod/block/entity/ServiceNpcSpawnBlockEntity.java`
- `src/main/java/com/seggellion/britannia_mod/gametest/ServiceNpcSpawnGameTests.java`
- `src/main/java/com/seggellion/britannia_mod/network/payload/ServiceNpcSpawnStateS2CPayload.java`
- `src/main/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnConfigurationValidator.java`
- `src/test/java/com/seggellion/britannia_mod/network/payload/ServiceNpcSpawnPayloadCodecTest.java`
- `src/test/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnConfigurationValidatorTest.java`
- `src/test/java/com/seggellion/britannia_mod/service/spawn/ServiceNpcSpawnPendingDataTest.java`

## 7. Block, block-entity, menu, and screen classes

- `ServiceNpcSpawnBlock` is an operator-placeable, creative-visible, non-colliding block with a server ticker, operator-only menu opening, true-removal capture, and `PushReaction.BLOCK`.
- `ServiceNpcSpawnBlockEntity` owns identity, configuration, registration/display state, claims, pending work, persistence, duplicate repair, destruction capture, and item-data stripping.
- `ServiceNpcSpawnMenu` binds a session to owner UUID, container, dimension, position, and spawn-point UUID.
- `ServiceNpcSpawnScreen` renders authoritative state, city/type selections, enabled state, revision, registration/assignment status, validation errors, save, refresh, and close actions.

## 8. Registry and resource additions

The implementation registers `service_npc_spawn_block`, its block item, block-entity type, `service_npc_spawn_menu`, client menu screen, three payloads, and creative-tab exposure. It adds blockstate, block model, item model, clean block loot table, English names, the GameTest holder, and its generated empty structure. Bootstrap login processing replaces the immutable city and Service NPC registry caches.

## 9. Exact NBT keys and defaults

Block-entity keys are:

- `SpawnPointId`: absent/null until server identity generation.
- `CityPublicId`: absent/null by default.
- `ServiceNpcTypeKey`: absent/null by default; blank values are not saved.
- `Enabled`: `true` by default and when the key is absent.
- `ConfigurationRevision`: `0`; negative stored values clamp to `0`.
- `RegistrationState`: `UNCONFIGURED` when absent/blank; unsupported values decode as `ERROR` and set `LastErrorCode` to `unsupported_registration_state`.
- `LastErrorCode`: absent/null by default.
- `AssignedNpcPublicId`: absent/null by default.
- `AssignedNpcDisplayName`: absent/null by default.
- `AssignmentRevision`: `0`; negative stored values clamp to `0`.
- `LastSuccessfulSyncEpochMillis`: absent/null by default.
- `IdentityWorldName`, `IdentityDimension`, `IdentityX`, `IdentityY`, `IdentityZ`: absent/null until an origin is stamped. Invalid origin data clears the origin and sets `ERROR`/`invalid_identity_origin`.

`identityReconciled` and `destructionHandled` are transient flags and are reset after load.

## 10. Physical-post UUID lifecycle

The constructor does not allocate identity. Normal placement resets all state and generates a random UUID on the logical server, stamps the actual save/dimension/position, and claims it. Legacy posts without a UUID generate one on first server tick. A loaded canonical post retains its UUID; a detected copy receives a new UUID and new-post defaults. True destruction records REMOVE before releasing the matching claim.

## 11. Server-only identity generation

UUID generation occurs in server placement/reconciliation paths using `ServerLevel`. The client does not authorize or create identity, and authoritative UUID is not synchronized through block-entity NBT.

## 12. Normal-placement hostile-data handling

`initializeNewPlacement` now gates only on completed reconciliation. It always resets an unreconciled placed entity, so hostile or preloaded UUID, city, type, enabled, revision, registration, error, assignment, sync, and origin values cannot survive ordinary placement. The post then claims a newly generated server UUID. A dedicated GameTest verifies this path.

## 13. UUID claim SavedData

Claims are server-save-wide overworld `SavedData` named `britannia_service_npc_spawn_claims` (stored as `world/data/britannia_service_npc_spawn_claims.dat`). Root keys are `SchemaVersion` (`1`) and `Claims`. Each claim uses `SpawnPointId`, `WorldName`, `Dimension`, `X`, `Y`, and `Z`. Corrupt records are quarantined and retained on save. Unsupported root schemas are preserved read-only.

## 14. Canonical-location rules

A matching saved claim makes that UUID canonical at its current location. A claim at another location, or a saved identity origin different from the current location, means the loaded block is a copy and must rekey. Legacy data with neither claim nor origin is canonical for the first server-side claimant. Location identity includes save name, dimension resource location, and block coordinates.

## 15. `/clone` and structure duplicate repair

Both `/clone` and structure-template copies are exercised. After at least one server tick, the source retains its UUID and claim; the copy gets a new UUID, is enabled, unconfigured, revision `0`, and has no city, type, registration error, assignment cache, or sync cache. Rekey repair does not create UPSERT work.

## 16. Item and pick-block identity stripping

`removeComponentsFromTag` removes every block-entity field, `saveToItem` deliberately attaches no block-entity or cached components, and the loot table emits a clean ordinary block item. GameTests verify clean drops and block-entity item serialization. Real ordinary and Ctrl+middle-click behavior remains a required manual check.

## 17. Registration-state enum and transitions

States are `UNCONFIGURED`, `PENDING_REGISTRATION`, `PENDING_UPDATE`, `REGISTERED`, and `ERROR`. A first accepted change from any state other than `REGISTERED`/`PENDING_UPDATE` becomes `PENDING_REGISTRATION`; accepted changes from `REGISTERED` or `PENDING_UPDATE` become `PENDING_UPDATE`. Milestone 4 never promotes a post to `REGISTERED`; that value is forward-facing decode/display compatibility.

## 18. Enabled-state behavior

Enabled is independent from registration state. New/rekeyed posts default to `true`. An accepted configuration can set it false, persists it in block NBT and pending UPSERT/REMOVE snapshots, and returns it in authoritative S2C state. Rejected requests do not change it.

## 19. Configuration revision rules

New/rekeyed posts start at `0`. A request must match the current revision. A true accepted change uses checked `long` increment and queues the new snapshot before mutating block state. No-change, invalid, stale, unauthorized, overflow, or store-rejected requests do not advance it. Stored negative revisions clamp to zero.

## 20. Menu authorization and packet validation

Configure/resync validation checks authenticated server player, permission, correct menu type and owner, container ID, dimension, distance, submitted position, block presence/type, block-entity type, menu/submitted/block UUID agreement, `stillValid`, and expected revision. Registry selection is validated server-side after session validation. Strings, payload sizes, option counts, and enum ordinals are bounded. Every handled rejection with a valid bound menu sends authoritative S2C state; the screen replaces local selections and enabled state from that response.

## 21. Permission and distance rules

Placement and opening require permission level 2; the client placement preview requires creative mode. Save/resync also recheck permission level 2. The maximum squared menu distance is `64.0`, i.e. eight blocks from the post center, and the player must remain in the bound dimension.

## 22. Immutable city and Service NPC type cache behavior

`BootstrapCityRegistryCache` and `ServiceNpcRegistryCache` use atomic whole-snapshot replacement. Their snapshots defensively copy into unmodifiable ordered maps. City options are sorted by display name and public UUID. Service NPC options include only active, spawnable types and are sorted by display name/key. Empty city snapshots and registries with no active, spawnable type are unavailable; oversized or invalid registries fail closed rather than sending partial options.

## 23. Pending UPSERT and REMOVE schema

Pending work is overworld `SavedData` named `britannia_service_npc_spawn_pending` (stored as `world/data/britannia_service_npc_spawn_pending.dat`), schema version `1`. Root keys are `SchemaVersion` and `Records`. Each record uses `Operation`, `SpawnPointId`, `ShardName`, `WorldName`, `Dimension`, `X`, `Y`, `Z`, optional `CityPublicId`, optional `ServiceNpcTypeKey`, `Enabled`, `ConfigurationRevision`, and `RecordedAtEpochMillis`. UPSERT requires city and nonblank type; REMOVE may retain the last known city/type or omit them. Revisions and timestamps must be nonnegative. Shard, world, type, and label inputs are bounded.

## 24. Queue supersession

There is at most one current record per UUID. Newer-revision UPSERT replaces older UPSERT; an equal identical snapshot is idempotent; stale or equal conflicting UPSERT is rejected. REMOVE supersedes UPSERT and is terminal against later UPSERT. REMOVE-versus-REMOVE orders by revision, then timestamp, with identical snapshots idempotent and conflicts rejected. Acknowledgement removes work only when UUID, operation, revision, and recorded timestamp all match.

## 25. Restart behavior

Block state, identity origin, claims, and pending work are durable NBT/SavedData. Automated codec and store reload tests cover block/claim/pending persistence, including simultaneous unacknowledged UPSERT and REMOVE records. The supplied recovery audit also reported restart-oriented coverage. A real graceful server stop/restart with inspection of the world data file remains mandatory manual verification.

## 26. Removal and tombstone capture

Changing the block to a different block invokes true-destruction capture. The block entity persists a REMOVE snapshot before releasing its matching claim, guards duplicate handling, and records an error if the store rejects the tombstone. Normal/survival/creative breaks, replacement, and explosion are covered. Chunk unload and save/reload do not create false REMOVE work.

## 27. Piston behavior

The block returns `PushReaction.BLOCK`. GameTests verify that normal and sticky pistons cannot move it.

## 28. GameTest infrastructure

`build.gradle` defines the dedicated `gameTestServer`, generates `data/britannia_mod/structure/service_npc_spawn_test_empty.nbt` from the tracked Base64 source, adds generated resources to the development source set, and excludes the holder package and template from the distributable JAR. CI has a dedicated GameTest job.

The 12 GameTests are:

- `newPlacementGetsIdentityWithoutPendingWork`
- `separatePlacementsGetDistinctIdentities`
- `normalPlacementRejectsPreloadedIdentityAndConfiguration`
- `cachedConfigurationCreatesReloadablePendingUpsertAndSyncedState`
- `saveAndReloadPreservesCanonicalIdentity`
- `survivalCreativeBreakAndReplacementCreateTerminalRemoveWork`
- `explosionCreatesTerminalRemoveWork`
- `chunkUnloadRemovalDoesNotCreateRemoveWork`
- `normalAndStickyPistonsCannotMovePosts`
- `cloneCommandRekeysAndResetsCopiedState`
- `structureTemplateCopyRekeysAndResetsCopiedState`
- `dropsAndBlockEntityItemSerializationContainNoIdentity`

## 29. JUnit tests

The final forced run executed 107 tests across 23 suites with 0 failures, 0 errors, and 0 skipped. This includes the original 42-test Milestone 4 core set plus authentication, URL-resolution, bounded/cancellable HTTP, null-bootstrap, quest/economy proxy, registry, codec, and generation-tracker coverage.

## 30. Commands run

- Rails focused, seed, authentication, compact-bootstrap, registry, verification, transaction/economy, linker, autoload, migration, syntax, and whitespace gates.
- `.\gradlew.bat test --console=plain --rerun-tasks`
- `.\gradlew.bat runGameTestServer --no-configuration-cache --console=plain`
- `.\gradlew.bat compileJava --console=plain --rerun-tasks`
- `.\gradlew.bat build --console=plain --rerun-tasks`
- `git diff --check` in both worktrees.
- Entry-name and content scans against every `build/libs/*.jar`, followed by tracked-source/staging exclusion scans.

## 31. Final automated results

- Rails exact gate at seed `20972`: PASS; 66 runs, 426 assertions, 0 failures, 0 errors.
- Quest harness: PASS independently and at seeds `20972`, `40740`, `1`, `12345`, `42424`, and `65535`; each complete-file run was 7 runs, 56 assertions.
- Provisional-user repair tests: PASS; 6 runs, 31 assertions.
- Existing Minitest stub users: PASS; 17 runs, 115 assertions.
- Rails authentication, compact bootstrap/registry, verification/economy, Zeitwerk, migration, Ruby syntax, and whitespace checks: PASS.
- NeoForge JUnit: PASS; 23 suites, 107 tests, 0 failures, 0 errors, 0 skipped.
- Compile: PASS (`BUILD SUCCESSFUL`); two existing warnings (`PlayerSleepMixin` missing `@Overwrite` Javadoc and deprecated `OrderShieldItem.initializeClient`).
- Full forced build: PASS (`BUILD SUCCESSFUL`).
- GameTests: PASS; all 12 required tests passed and Gradle exited successfully.
- `git diff --check`: PASS; no whitespace errors. Git emitted only LF-to-CRLF working-copy warnings.

The GameTest development server logged unrelated existing environment warnings (missing local config with defaults used, client-only mixin target on dedicated server, and existing event log noise); none failed a required test.

## 32. JAR inspection

- `build/libs/Britannia_Mod-auth-hardening-0.1.7k.jar`: 4,665 entries.
- `build/libs/Britannia_Mod-auth-hardening-0.1.7k-all.jar`: 4,669 entries.

Each fresh artifact contained zero test classes, GameTest classes, GameTest fixtures, deleted credential classes, synthetic credential files, runtime files, world data, or logs. Content scans found zero legacy bearer/Authorization strings, verification-world markers, diagnostic fingerprint, or WorldEdit references. Required server-auth implementation classes are code only; no credential value or credential configuration file is packaged.

## 33. Known limitations

- The spawn-point pending store records local durable intent only. Milestone 4 does not deliver, retry, back off, acknowledge remote work, or assign/spawn NPCs.
- `REGISTERED` and assignment/sync fields are compatibility/display state only in this milestone.
- Stale claims conservatively cause rekeying; they are not automatically stolen.
- Third-party tools that bypass block loading, ticking, or normal callbacks may bypass or delay duplicate repair/removal capture.
- The Service NPC Spawn Block is invisible and untargetable in Adventure mode, but visible and targetable in Creative mode. Server-side configuration authorization remains permission-based, so operator verification was completed safely in Creative mode.
- De-op closes an already-open configuration menu. This prevents a stale normal-client Save; direct unauthorized handler rejection is covered automatically.

## 34. Manual verification

Authentication/bootstrap: PASS. Section A: `PASS — ACCEPTED DESIGN VARIANCE`. Sections B, C, and D: PASS.

The live stale-menu `UNAUTHORIZED` response was not reachable because permission loss closed the menu before Save. No mutation, revision increment, UUID change, or additional pending work occurred. Ambiguous-target and accidental operator-input attempts were recorded as invalid instructions/input, not product failures.

Section B passed ordinary and Ctrl+middle-click identity stripping. Section C passed with WorldEdit 7.3.8+6939-7d32b45. Section D used `m4_auth_hardened_pending_verification` and produced exactly one UPSERT plus one REMOVE; the store survived restart byte-for-byte with SHA-256 `99314F4A2FD05C213B53F0EA83C3571605EF61D45456637624A0DFBE8A63AA17` and zero credential markers.

## 35. Rails closeout scope

Rails now enforces shard-bound server authentication for the active NeoForge endpoints and provides the `minecraft_server` compact bootstrap profile with bounded query growth and profile-isolated ETags. NeoForge treats `api_base_url` as the service origin, centralizes `/api` construction, uses bounded/cancellable HTTP, and accepts null bootstrap inventory/stats as empty objects.

Closeout also loaded official Minitest mock support, repaired provisional-user purchased-item/badge/shard preservation atomically, and replaced brittle Quest-versus-PlayerQuestState numeric-ID inequalities with domain relationship and named-field assertions. An explicit equal-ID regression proves equal primary-key numbers across the two tables are harmless.

## 36. Milestone boundary confirmation

**Completion status: Implementation and manual verification complete.**

Milestone 5 was not started. Nothing was merged, rebased, amended, squashed, or pushed. Runtime files, credentials, worlds, logs, screenshots, NBT evidence, WorldEdit, EULA, server properties, operator data, and build outputs remain outside the selective commits.
