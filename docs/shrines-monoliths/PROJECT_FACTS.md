# Shrine and Monolith Project Facts

This file separates facts found in the repository from product requirements approved by the two root specifications. Recommendations and unresolved questions are explicitly labelled and are not repository facts.

## Status and scope

| Classification | Finding | Evidence |
| --- | --- | --- |
| Verified repository fact | The feature branch is `shrines-monoliths`; the integration branch is `patch-18`. | `git branch --show-current`; `git branch --list patch-18 shrines-monoliths` |
| Verified repository fact | Both branches began Milestone 0 at `62df1dc97c5113a86f9c0f258cb90538f31efe89`; that commit is also their merge base. | `git rev-parse HEAD`; `git rev-parse patch-18`; `git merge-base shrines-monoliths patch-18` |
| Verified repository fact | At discovery time the branches were identical: zero commits unique to `patch-18` and zero unique to `shrines-monoliths`. | `git rev-list --left-right --count patch-18...shrines-monoliths` returned `0 0` |
| Verified repository fact | This repository is a Minecraft mod project. No website or rail project is part of the requested scope. | `build.gradle`, `gradle.properties`, `src/main/java`, `src/main/resources` |
| Approved product requirement | Milestone 0 is documentation and discovery only. It must not introduce shrine or monolith gameplay. | `UltimaCraft_Shrine_and_Monolith_Codex_Playbook.md` |

## Build and runtime

| Classification | Finding | Evidence |
| --- | --- | --- |
| Verified repository fact | Minecraft version: `1.21.1`. | `gradle.properties` (`minecraft_version`) |
| Verified repository fact | NeoForge version: `21.1.72`. | `gradle.properties` (`neo_version`) |
| Verified repository fact | Java toolchain and Gradle launcher: Java 21. The wrapper ran with Microsoft OpenJDK `21.0.8`. The ambient `java` on `PATH` is Oracle Java 8 (`1.8.0_491`) and is not the build launcher. | `build.gradle`; `gradle/gradle-daemon-jvm.properties`; `java -version`; `.\gradlew.bat --version --no-daemon` |
| Verified repository fact | Gradle wrapper version: `8.9`. | `gradle/wrapper/gradle-wrapper.properties`; wrapper `--version` output |
| Verified repository fact | NeoGradle UserDev plugin version: `7.0.165`. | `build.gradle` |
| Verified repository fact | GeckoLib version: `4.6.6`. | `gradle.properties`; `build.gradle` |
| Verified repository fact | Mod ID and asset/data namespace: `britannia_mod`; Java group/root package: `com.seggellion.britannia_mod`. | `gradle.properties`; `src/main/java/com/seggellion/britannia_mod/BritanniaMod.java` |
| Verified repository fact | Main Java root: `src/main/java`. Main resource roots: `src/main/resources` and the declared `src/generated/resources`. Mod metadata templates: `src/main/templates`; generated metadata is written below `build/generated/sources/modMetadata`. | `build.gradle` |
| Verified repository fact | No `src/generated/resources` directory exists on this branch, and no generated/build/run path is tracked. | filesystem inventory; `git ls-files -- build src/generated/resources run runs` |
| Verified repository fact | The Java plugin supplies the conventional `src/test/java` test root, but that directory and repository-authored Java tests are absent on this branch. | `build.gradle`; `rg --files src/test`; Java filename inventory |
| Verified repository fact | Documented client command: `.\gradlew.bat runClient -Pdev --no-configuration-cache` on Windows (the README shows the shell form `./gradlew runClient -Pdev --no-configuration-cache`). | `README.md`; `build.gradle` |
| Verified repository fact | The configured dedicated-server run is `runServer`; the native Windows invocation is `.\gradlew.bat runServer -Pdev --no-configuration-cache`. It supplies `--nogui`. | `build.gradle` |
| Verified repository fact | Native baseline commands are `.\gradlew.bat test --no-daemon --stacktrace` and `.\gradlew.bat clean build --no-daemon --stacktrace`. A production package is created by the `build` lifecycle; `-Pdev` disables embedded GeckoLib packaging for development. | `build.gradle`; Gradle wrapper tasks |
| Verified repository fact | The Milestone 0 NeoForm failure was an incomplete generated NeoForge compile artifact: it contained exactly 4,096 class entries and omitted common/client Minecraft classes. Rerunning NeoGradle's own supply pipeline with a 4 GB recompiler heap rebuilt 9,729 class entries; the normal full test task and normal clean production build then passed without a tracked Gradle-memory override. | generated JAR inventory; `supplyRawJarForneoFormJoined1.21.1-20240808.144430`; post-recovery `test` and `clean build` output |

## Approved structure contract

| Classification | Finding |
| --- | --- |
| Approved product requirement | A shrine is `2 × 1 × 2` and occupies four cells. |
| Approved product requirement | A monolith is `3 × 3 × 2` and occupies eighteen cells. |
| Approved product requirement | Every monolith render applies a positive sixteen-voxel Y correction (`+16` voxels, one block) without moving collision. |
| Approved product requirement | Shrine variants share geometry and change texture only. |
| Approved product requirement | Monolith variants may change both geometry and texture. |
| Approved product requirement | The Interior Decorator cycles variants only within the current family. Shrine-to-monolith and monolith-to-shrine conversion are forbidden. |
| Approved product requirement | Model geometry must not determine collision or selection. Every future anchor/part shape must remain inside that cell's local `0..16` bounds so adjacent stairs and ordinary blocks remain placeable. |
| Approved product requirement | Approved shrine identities are Honesty, Compassion, Valor, Justice, Sacrifice, Honor, Spirituality, Humility, and Chaos. |

The approved requirements above come from `UltimaCraft_Shrine_and_Monolith_System_Design.md` and `UltimaCraft_Shrine_and_Monolith_Codex_Playbook.md`.

## Registration, state, persistence, and networking

| Classification | Finding | Evidence |
| --- | --- | --- |
| Verified repository fact | `BritanniaMod` wires deferred registers from `BlockRegistry`, `BlockEntityRegistry`, `ItemRegistry`, and `DataComponentRegistry` to the mod event bus. | `src/main/java/com/seggellion/britannia_mod/BritanniaMod.java` |
| Verified repository fact | Milestone 2 adds a separate `LargeStructureRegistry` with exactly one anchor block (`large_structure_anchor`), one part block (`large_structure_part`), one anchor-only block entity (`large_structure`), and one family placement item (`shrine`). Neither structure block has a `BlockItem`. | `registry/LargeStructureRegistry.java`; registration/scope tests |
| Verified repository fact | A raw shrine item stack defaults to the approved Honesty diagnostic shrine. Milestone 3 adds persistent/network-synchronized component `britannia_mod:shrine_instance_state` (schema, family ID, variant ID); configured stacks retain their exact IDs and placement validates them against the catalogue. The immutable plan still captures exactly four original and expected block states before mutation. | `structure/item/ShrineItem*.java`; `registry/DataComponentRegistry.java`; `structure/placement/ShrinePlacementPlanner.java` |
| Verified repository fact | The anchor block entity persists schema version, family ID, variant ID, facing, and the exact ordered placed footprint beneath NBT key `shrine_state`; block-state facing is authoritative and persisted facing is validation-only. Unknown IDs remain represented as typed missing-definition state, while malformed and future-schema state fail closed. | `LargeStructureAnchorBlockEntity.java`; `PlacedStructureState.java`; `PlacedStructureStatus.java`; persistence tests |
| Verified repository fact | The placement executor consumes one item only after anchor placement, block-entity initialization, three ordered part placements, complete verification, and synchronization all succeed. Every injected mutation failure restores the plan in reverse order with drops suppressed. | `ShrinePlacementExecutor.java`; `ShrinePlacementService.java`; executor tests |
| Verified repository fact | Blocks use a `DeferredRegister<Block>` for namespace `britannia_mod`; items use a `DeferredRegister<Item>`; block entities primarily use `DeferredRegister<BlockEntityType<?>>`. | `src/main/java/com/seggellion/britannia_mod/registry/BlockRegistry.java`; `ItemRegistry.java`; `BlockEntityRegistry.java` |
| Verified repository fact | The modern data-component example is `britannia_mod:wine_data`, a persistent and network-synchronized `DataComponentType<WineData>` backed by both `Codec` and `StreamCodec`. | `src/main/java/com/seggellion/britannia_mod/registry/DataComponentRegistry.java`; `src/main/java/com/seggellion/britannia_mod/components/WineData.java` |
| Verified repository fact | Older item-instance state also uses vanilla `DataComponents.CUSTOM_DATA`, `CustomData`, and `CompoundTag`. | `src/main/java/com/seggellion/britannia_mod/item/WeightedFishItem.java` and other `CUSTOM_DATA` search results |
| Verified repository fact | Block entities conventionally override `saveAdditional`/`loadAdditional` with `HolderLookup.Provider`, call `setChanged`, return `saveWithoutMetadata` from `getUpdateTag`, and use `ClientboundBlockEntityDataPacket.create(this)`. Server changes call `sendBlockUpdated`. | `src/main/java/com/seggellion/britannia_mod/block/entity/WineBottleBlockEntity.java`; `src/main/java/com/seggellion/britannia_mod/block/nudgeable/NudgeableBlockEntity.java`; `src/main/java/com/seggellion/britannia_mod/block/structure/AdaptiveRoofBlockEntity.java`; `src/main/java/com/seggellion/britannia_mod/block/entity/DoubleBedBlockEntity.java` |
| Verified repository fact | Custom payloads are registered from `RegisterPayloadHandlersEvent` with a versioned registrar (`"1"`), `CustomPacketPayload.Type`, and `StreamCodec`. Server handlers use `ctx.enqueueWork`, resolve server-owned objects, and validate before mutation. | `src/main/java/com/seggellion/britannia_mod/network/NetworkHandler.java`; payload classes in `network/` |

## Rendering

| Classification | Finding | Evidence |
| --- | --- | --- |
| Verified repository fact | Client-only registration is isolated with `Dist.CLIENT` mod-bus subscribers. Block-entity renderers are registered during `EntityRenderersEvent.RegisterRenderers`. | `src/main/java/com/seggellion/britannia_mod/ClientModSetup.java`; `src/main/java/com/seggellion/britannia_mod/block/nudgeable/ClientEventHandler.java` |
| Verified repository fact | Custom baked-model loaders use `ModelEvent.RegisterGeometryLoaders`; other model hooks use `RegisterAdditional` and `ModifyBakingResult`. | `src/main/java/com/seggellion/britannia_mod/ClientModSetup.java`; `src/main/java/com/seggellion/britannia_mod/client/ClientModelHandler.java`; `src/main/java/com/seggellion/britannia_mod/block/structure/StoneFloorGeometryLoader.java` |
| Verified repository fact | GeckoLib block models extend `GeoModel` and render through `GeoBlockRenderer`; entity and item renderers follow the corresponding GeckoLib classes. | `block/model/BlueTentModel.java`; `block/renderer/BlueTentRenderer.java`; `block/model/LargeForgeModel.java`; `client/renderer/CitizenRenderer.java` |
| Verified repository fact | Fixed GeckoLib resources live under `assets/britannia_mod/geo`, `animations`, and `textures`. Dynamic selection exists: `CitizenGeoModel` chooses geometry/texture from synchronized entity state. | `src/main/resources/assets/britannia_mod`; `client/model/CitizenGeoModel.java` |
| Verified repository fact | Render-bound accommodations are renderer-specific, for example an inflated AABB for wine bottles and off-screen rendering overrides for armoire and three-height light. | `src/main/java/com/seggellion/britannia_mod/client/renderer/WineBottleBlockEntityRenderer.java`; `src/main/java/com/seggellion/britannia_mod/block/renderer/ArmoireRenderer.java`; `src/main/java/com/seggellion/britannia_mod/block/renderer/ThreeHeightLightRenderer.java` |
| Verified repository fact | No common safe missing-model/missing-texture fallback or bounded diagnostic mechanism was found for GeckoLib block renderers on this branch. Models return direct resource locations. | inspected `*Model.java`/`*Renderer.java` files and missing-resource searches |
| Implementation recommendation | A later milestone should introduce a bounded, deterministic missing-resource fallback/diagnostic at the shrine/monolith renderer boundary rather than assume existing GeckoLib behavior is safe. | Recommendation based on the verified absence above; not an existing convention |

## Existing multiblock and lifecycle systems

These are inspections only; none was changed or selected for reuse in Milestone 0.

| System and paths | Ownership and representation | Placement/lifecycle findings | Reuse finding |
| --- | --- | --- | --- |
| Carpet teleporter: `item/CarpetTeleporterItem.java`, `block/CarpetTeleporterBlock.java`, `block/CarpetDummyBlock.java`, `block/CarpetPart.java` | Center anchor block entity plus eight dummy blocks; parts encode a grid offset and derive the center. | Preflights replaceability but mutates non-transactionally; no rollback. Anchor removal clears all expected cells without verifying every occupant. Parts can self-remove if the center is absent. No centralized drop, explosion, piston, chunk, reentrancy, or repair contract. | The deterministic local-offset calculation is conceptually relevant, but the system is not a safe generic primitive. |
| Double bed: `block/DoubleBedBlock.java`, `block/entity/DoubleBedBlockEntity.java` | Four same-block parts in a `2 × 1 × 2` layout; every cell creates a block entity; parts resolve `HEAD_LEFT`. | Replaceability is preflighted; placement is non-transactional and has no rollback. Removal attempts whole-structure teardown, with one dropping cell, but has no explicit reentrancy, chunk-integrity, explosion, or piston policy. | Layout/rotation examples are relevant; duplicated ownership and incomplete lifecycle are unsuitable as a generic base. |
| Triple metal door: `block/TripleMetalDoorBlock.java` | Three vertically stacked same-block parts. | Preflight plus non-transactional placement; neighbor shape updates remove invalid parts. No anchor state, rollback, centralized drops, chunk repair, or explicit piston contract. | Not a safe generic primitive. |
| Tall thin block: `block/TallThinBlock.java` | Two same-block halves using `DoubleBlockHalf`. | Preflight and linked survival/removal; no anchor persistence, transaction, or rollback. Shapes are cell-local. | Useful vanilla-style two-cell reference only. |
| Dungeon moongate: `block/DungeonMoongateBlock.java`, `DungeonMoongateTopBlock.java`, related block entity | Base anchor/block entity plus separate top. | Top is placed and removed directly; no complete preflight, rollback, part back-reference, or repair. Base is piston-blocked. | Not a safe generic primitive. |
| Window `2x3`: `block/Window2x3Block.java`, `WindowCollisionBlock.java` | One visible origin plus invisible collision cells with facing only. | Blind part placement, no rollback or encoded origin; origin cleanup removes matching collision cells. No orphan/pick/drop lifecycle. | Demonstrates rendering/collision separation, but not safe ownership. |
| Decorative oversized shapes: `block/DecorativeItems3x3Block.java`, `TallDecorativeBlock.java`, `TallDecorative3Block.java` | Single blocks with shapes extending beyond one cell. | Not logical multiblocks and violate the future cell-bounded collision contract. | Explicitly must not be copied for shrine/monolith collision. |
| Extended chandelier: `block/ExtendedLightChandelierBlock.java` | Source block creates nearby replaceable light blocks. | Cleanup is limited to its own ghost-light type; not a placed logical structure contract. | Not a generic multiblock base. |

Verified repository fact: Milestone 3 now provides that complete contract for the four-cell shrine family: anchor ownership, persisted footprint, preflight, atomic placement, rollback, centralized teardown/drop/pick/explosion/piston/fluid policy, unloaded-chunk deferral, orphan handling, loaded-cell repair, bounded scheduling/diagnostics, and reentrancy protection. Monolith placement remains absent.

### Completed banner feature on another local branch

| Classification | Finding | Evidence |
| --- | --- | --- |
| Verified repository fact | A completed banner feature exists on local branch `banners-dyetub`, including commit `4cd7aec`, but it is not contained in `shrines-monoliths` or `patch-18`. | `git branch --all --contains 4cd7aec`; `git ls-tree banners-dyetub` |
| Verified repository fact | It separates mutation-free planning (`BannerPlacementPlanner`) from execution (`BannerPlacementExecutor`), preflights bounds/chunks/replaceability/support/protection/state encoding, consumes the item only after success, and rolls back failed mutations. | `banners-dyetub:src/main/java/com/seggellion/britannia_mod/banner/placement/` |
| Verified repository fact | It persists the placed footprint in the anchor block entity; lightweight parts encode orientation and local offsets and deterministically resolve the anchor. | `banners-dyetub:.../BannerBlockEntity.java`; `BannerPartBlock.java`; `BannerPlacedStructure.java`; `BannerStructureTransform.java` |
| Verified repository fact | Its lifecycle service centralizes whole-structure removal/drop behavior and guards placement/removal reentrancy. Integrity handling defers unloaded anchor chunks, repairs valid missing parts, removes orphans, preserves unrelated obstructions, and bounds diagnostics. | `banners-dyetub:.../BannerStructureLifecycle.java`; `BannerStructureIntegrity.java`; `BannerStructureIntegrityHandler.java` |
| Verified repository fact | That branch contains focused unit/contract tests for footprints, planning, rollback/execution, lifecycle, dense placement, render bounds, persistence, synchronization, dedicated-server safety, and release artifacts. | `banners-dyetub:src/test/java/com/seggellion/britannia_mod/bannerdyeing/` |
| Implementation recommendation | If the banner branch is integrated before shrine/monolith implementation, later milestones should evaluate its small planning/transform/lifecycle concepts for extraction or adaptation instead of copying current-branch legacy systems. | Recommendation only; no banner code was merged or reused |

## Interior Decorator

| Classification | Finding | Evidence |
| --- | --- | --- |
| Verified repository fact | Item ID `britannia_mod:interior_decorator_tool` is registered by `ItemRegistry`; implementation is `InteriorDecoratorToolItem`. It is a single-stack, one-durability tool. | `registry/ItemRegistry.java`; `item/InteriorDecoratorToolItem.java` |
| Verified repository fact | The tool's `useOn` performs mutations only on the logical server side and returns sided interaction results. It supports offhand-assisted nudge for `INudgeable`, horizontal rotation, and style cycling for carpet teleporters, thin walls, and blank sign holders. | `item/InteriorDecoratorToolItem.java` |
| Verified repository fact | The tool class contains no administrator permission check. Elsewhere this repository commonly treats creative players or server players with permission level 2 as administrators, but that predicate is not centralized for this tool. | `InteriorDecoratorToolItem.java`; permission checks in spawn blocks and command classes |
| Verified repository fact | Nudge feedback is a literal action-bar message (`Nudged <face>`), not a localization key. The registered item name is localized as `item.britannia_mod.interior_decorator_tool`. | `InteriorDecoratorToolItem.java`; `assets/britannia_mod/lang/en_us.json` |
| Verified repository fact | Existing decorator-related interactions that must not regress include custom walls, chairs, blue/purple tents, double beds, floors, rotatable furniture, hanging items, store signs, top-only slabs, carpet teleporters, thin walls, blank sign holders, and generic nudgeable furniture. Some are dispatched by the item and others by the target block. | `rg` call-site inventory for `INTERIOR_DECORATOR_TOOL` and `InteriorDecoratorToolItem` |

## Protection and placement

| Classification | Finding | Evidence |
| --- | --- | --- |
| Verified repository fact | `StructureProtectionHandler` cancels survival break events in protected recorded structures unless the player owns the structure or uses an allowed tool; creative players bypass it. No matching general placement event/service was found. | `src/main/java/com/seggellion/britannia_mod/structure/StructureProtectionHandler.java`; `src/main/java/com/seggellion/britannia_mod/structure/StructureRegionManager.java`; event searches |
| Verified repository fact | `SurvivalZoneHandler` changes player game mode in/out of owned houses, indirectly causing vanilla Adventure-mode placement restrictions. | `src/main/java/com/seggellion/britannia_mod/structure/SurvivalZoneHandler.java` |
| Verified repository fact | Existing replaceability checks use `state.canBeReplaced(context)` or `state.isAir() || state.canBeReplaced()`. | carpet, bed, door, tall-block, and structure placement classes |
| Verified repository fact | Build-height checks are implemented ad hoc by tall structures against `getMaxBuildHeight`. No repository-wide world-border placement convention was found. | `TripleMetalDoorBlock.java`; `TallThinBlock.java`; world-border search |
| Verified repository fact | Chunk safety checks elsewhere use `hasChunk`, `hasChunkAt`, or `isLoaded`; no generic placement permission service and no deliberate chunk force-loading convention was found. | repository searches; multiblock classes |
| Verified repository fact | `StructurePlacer` preflights terrain/replaceability and calls structure-template placement but has no transaction rollback or explicit chunk/world-border preflight. | `structure/StructurePlacer.java` |

## Existing shrine and monolith content inventory

| Classification | Finding | Evidence |
| --- | --- | --- |
| Verified repository fact | Milestone 3 retains one invisible anchor, three invisible parts, one anchor-only block entity, and one shared family item. It adds versioned persistence, exact configured-item recovery, centralized whole-structure lifecycle, and bounded integrity repair, but no final shrine renderer/texture, monolith placement, or decorator integration. | `src/main/java/com/seggellion/britannia_mod/structure`; registration/resource inventories; scope tests |
| Verified repository fact | The nine approved shrine identities now have stable lower-case logical variant IDs, but still have no claimed localization, model, or texture path. Their client resources are explicitly unavailable while identity remains stable. | `ShrineMonolithDefinitions.java`; catalogue tests |
| Verified repository fact | No supplied monolith models or textures are present, so there are no repository-backed authored coordinates, lowest-Y measurements, stable names, or provisional asset names to record. | asset/file inventory and model-content searches |
| Approved product requirement | The positive sixteen-voxel monolith Y render correction is authoritative even though no supplied model is currently available to inspect. | root design/playbook |
| Unresolved question | Asset delivery, exact monolith names, and authored-coordinate evidence remain unresolved; see `OPEN_QUESTIONS.md`. | `docs/shrines-monoliths/OPEN_QUESTIONS.md` |

## Testing facts

| Classification | Finding | Evidence |
| --- | --- | --- |
| Verified repository fact | Milestone 3 retains JUnit Jupiter 5.10.2 and expands the shrine/monolith suite to sixteen test classes and 121 test methods. The six Milestone 3-focused classes contain 47 persistence, configured-item, lifecycle, integrity, and scope tests; all earlier definition/placement/rollback/shape tests remain green. | `build.gradle`; `src/test/java/com/seggellion/britannia_mod/structure`; JUnit XML results |
| Verified repository fact | Client/server runs set `neoforge.enabledGameTestNamespaces=britannia_mod`, but no `@GameTest` implementation exists on this branch. | `build.gradle`; GameTest symbol search |
| Verified repository fact | The current branch has plain-JUnit coverage through narrow world adapters plus actual registered blocks/items/components and actual NBT/update-tag/packet-application APIs. It still has no `@GameTest`, client test suite, dedicated-server startup test, Interior Decorator test, or CI workflow; live gameplay remains unverified. | source/docs/`.github` searches; Milestone 3 results |
| Verified repository fact | The unintegrated `banners-dyetub` branch has JUnit/contract coverage, including dedicated-server-safety and production/release contract tests; those tests are not available to the active branch. | `git ls-tree banners-dyetub -- src/test`; banner branch build files |

## Implementation recommendations

These are not repository facts or Milestone 0 implementation commitments.

1. Define a single anchor-owned, persisted footprint contract with deterministic local-to-world transforms and lightweight parts.
2. Separate a mutation-free preflight plan from an all-or-rollback execution step; consume the held item only after verification and synchronization succeed.
3. Centralize teardown, drops, pick-block, explosion, piston, chunk deferral, orphan handling, repair, and reentrancy policy.
4. Add an explicit placement-permission abstraction because current structure protection covers breaking, not a reusable per-cell placement check.
5. Keep render transforms and per-cell collision/selection definitions independent; never derive shapes from model bounds.

## Unresolved questions

Only evidence-backed unresolved matters are tracked in `docs/shrines-monoliths/OPEN_QUESTIONS.md`.
