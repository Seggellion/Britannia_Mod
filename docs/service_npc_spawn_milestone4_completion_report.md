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

The current forced run executed 42 tests across 13 suites: 1 city-cache test, 3 payload-codec tests, 4 claim/resolver tests, 5 configuration-validator tests, 1 item-data test, 2 menu-validation tests, 6 pending-data tests, 2 state-machine tests, and 18 existing compatibility/dialogue/service tests. Result: 42 passed, 0 failed, 0 errors, 0 skipped. The supplied recovery audit reported 41 passing tests before the additional no-configurable-type regression case was added.

## 30. Commands run

- `.\gradlew.bat compileJava --console=plain`
- `.\gradlew.bat test --console=plain --rerun-tasks`
- `.\gradlew.bat build --console=plain`
- `.\gradlew.bat runGameTestServer --no-configuration-cache --console=plain`
- `git diff --check`
- `jar tf` against every `build/libs/*.jar`, followed by exact marker scans

The first sandboxed compile and test invocations could not download `gradle-8.9-bin.zip` (`java.net.SocketException: Permission denied`). They were rerun without source changes in an approved environment with dependency access and passed. This was an environment restriction, not a source failure.

## 31. Current automated results

- Compile: PASS (`BUILD SUCCESSFUL`); two existing warnings (`PlayerSleepMixin` missing `@Overwrite` Javadoc and deprecated `OrderShieldItem.initializeClient`).
- JUnit: PASS; 42/42, no failures/errors/skips.
- Full build: PASS (`BUILD SUCCESSFUL`).
- GameTests: PASS; all 12 required tests passed in 2.247 seconds and Gradle exited successfully.
- `git diff --check`: PASS; no whitespace errors. Git emitted only LF-to-CRLF working-copy warnings.

The GameTest development server also logged unrelated existing environment warnings (missing local config with defaults used, client-only mixin target on dedicated server, and existing event log noise); none failed a required test.

## 32. JAR inspection

- `build/libs/Britannia_Mod-0.1.7k.jar`: 4,641 entries; 0 `ServiceNpcSpawnGameTests` entries; 0 `service_npc_spawn_test_empty` entries; 0 other case-insensitive `gametest` entries.
- `build/libs/Britannia_Mod-0.1.7k-all.jar`: 4,645 entries; 0 `ServiceNpcSpawnGameTests` entries; 0 `service_npc_spawn_test_empty` entries; 0 other case-insensitive `gametest` entries.

Both distributable JARs are clean of the Milestone 4 holder, structure template, and other development-only GameTest artifacts.

## 33. Known limitations

- Milestone 4 records local durable intent only. It does not deliver to Rails, retry, back off, acknowledge remote work, authenticate HTTP, or assign/spawn NPCs.
- `REGISTERED` and assignment/sync fields are compatibility/display state only in this milestone.
- Stale claims conservatively cause rekeying; they are not automatically stolen.
- Third-party tools that bypass block loading, ticking, or normal callbacks may bypass or delay duplicate repair/removal capture.
- Actual client UI behavior, Ctrl+middle-click, the production WorldEdit version, and real pending-store restart persistence require manual evidence.

## 34. Manual verification still required

All four sections in `docs/service_npc_spawn_milestone4_manual_verification.md` remain `NOT RUN`: UI/authorization/restart; ordinary and Ctrl+middle-click identity stripping; production WorldEdit copy/paste; and real pending-store restart with UPSERT plus REMOVE. Automated tests do not replace them.

## 35. Rails scope confirmation

No Rails application/repository file was accessed or modified during recovery closeout. No Rails change is part of the implementation or repair diff. Milestone 4 adds no new Rails call, credential, token, endpoint, or delivery behavior.

## 36. Milestone boundary confirmation

Milestone 5 was not started. The branch was not merged, rebased, amended, squashed, or pushed during this closeout. It is ready for manual verification, not for merge into `banking`.
