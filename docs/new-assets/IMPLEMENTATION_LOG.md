# UltimaCraft New Assets — Implementation Log

## Milestone 0 — Workspace Bootstrap and Repository Reconnaissance

### Workspace baseline

- Project start date: 2026-08-09 (America/Vancouver).
- Requested primary path `C:\projects\britannia\britannia_mod` was absent. The actual repository is `C:\projects\britannia\mod\Britannia_Mod`.
- Base branch: `patch-18`; feature branch: `new-assets`.
- Worktree: `C:\projects\britannia\new-assets`.
- Starting commit: `50061f07231271e67591b2720ddf21cf4fa54fcc`.
- Raw source: `C:\projects\britannia\raw fiels\models to import` (spelling matches the filesystem).
- Initial feature-worktree state: clean at `patch-18` (`0 0` divergence). The primary `patch-18` worktree was ahead of `origin/patch-18` by 181 commits and had pre-existing untracked documentation/`.claude` content; none of it was modified.
- Scope: reconnaissance and this log only. No gameplay, source, resource, or asset implementation was performed.

### Exact precedent path index

All repository paths below are relative to `C:\projects\britannia\new-assets`.

- Registration/client: `src/main/java/com/seggellion/britannia_mod/BritanniaMod.java`; `src/main/java/com/seggellion/britannia_mod/registry/{BlockRegistry,ItemRegistry,EntityRegistry,BlockEntityRegistry,MenuRegistry,CreativeTabRegistry,LargeStructureRegistry}.java`.
- Entity variants/rendering: `src/main/java/com/seggellion/britannia_mod/entity/CitizenEntity.java`; `entity/BaseBritanniaAnimal.java`; `entity/BaseBritanniaMonster.java`; `client/renderer/entity/BaseBritanniaRenderer.java`.
- Animated block entities: `src/main/java/com/seggellion/britannia_mod/block/SmallForgeBlockEntity.java`; `block/entity/LargeForgeBlockEntity.java`; `structure/multiblock/LargeStructureAnchorBlockEntity.java`.
- Cities/spawning: `src/main/java/com/seggellion/britannia_mod/registry/CityRegistry.java`; `spawner/CitySpawnRules.java`; `spawner/CitySpawner.java`; `client/RegionCache.java`; `util/RegionData.java`; `CityCoordinates.java`.
- Bootstrap: `src/main/java/com/seggellion/britannia_mod/event/WorldBootstrapHandler.java`; `network/WorldBootstrapAPI.java` (declared `...sync` package); bootstrap city definition/cache classes in the same bootstrap/sync subsystem.
- Multiblocks: `src/main/java/com/seggellion/britannia_mod/structure/multiblock/{LargeStructureAnchorBlock,LargeStructurePartBlock,LargeStructureAnchorBlockEntity}.java`; `structure/placement/{ShrinePlacementPlanner,ShrinePlacementExecutor}.java`.
- Containers: `src/main/java/com/seggellion/britannia_mod/block/ArmoireBlock.java`; `block/entity/ArmoireBlockEntity.java`; the Britannia chest and trash-barrel block/entity classes in those same packages.
- Adventure/tools: `src/main/java/com/seggellion/britannia_mod/event/GlobalEventHandler.java`; `block/FarmingBlock.java`; `util/AxeHarvestRules.java`; `event/PlayerEventHandler.java`; `event/WoodChopEventHandler.java`; `util/ModTags.java`.
- Water: `src/main/java/com/seggellion/britannia_mod/block/WaterBarrelBlock.java`; water-trough block class; `item/PitcherItem.java`; `item/WateringCanItem.java`; `registry/ItemRegistry.java`.
- Skills/weapons/hits: `src/main/java/com/seggellion/britannia_mod/skill/SkillManager.java`; `skill/BlacksmithCrafting.java`; `registry/WeaponRegistry.java`; `registry/WeaponProfile.java`; `skill/crafting/CraftableDef.java`; `event/BardEvents.java`; `src/main/resources/data/britannia_mod/blacksmithing/craftables.json`.
- Processing/textiles: juice-press and wine-barrel block/block-entity classes under `src/main/java/com/seggellion/britannia_mod/block`; `CraftableRegistry`/`BlacksmithCrafting`; item registrations in `registry/ItemRegistry.java`; banner fabrics under `src/main/resources/data/britannia_mod/fabric_materials`.
- Connected blocks: `src/main/java/com/seggellion/britannia_mod/block/HouseFarmPlotBlock.java`; `BannisterBlock.java`; `DoubleWallBlock.java`; `WallConnection.java`; `WallShape.java`; `IronFenceBlock.java`.
- Resources: `src/main/resources/assets/britannia_mod/lang/en_us.json`; blockstates/models under `assets/britannia_mod`; loot under `data/britannia_mod/loot_table`; tags under `data/*/tags/block` and `data/*/tags/item`.
- Moongates: `src/main/java/com/seggellion/britannia_mod/block/MoongateBlock.java`; `MoongateTeleportationHandler.java`; `MoongateTickHandler.java`; dungeon moongate block/entity and `MoongateLinkingWand.java`; paired teleport behavior in `BritanniaTeleportService.java`; corresponding `moongate_block`, `moongate_top`, `dungeon_moongate_block`, and `dungeon_moongate_top` resources/registrations.

### Registration and client architecture

- Stack: Minecraft 1.21.1, NeoForge 21.1.72, GeckoLib 4.6.6.
- `BritanniaMod.java` is the main entry point and event/client setup hub.
- Deferred registers are centralized in `BlockRegistry`, `ItemRegistry`, `EntityRegistry`, `BlockEntityRegistry`, `MenuRegistry`, `CreativeTabRegistry`, and `LargeStructureRegistry`.
- Block items are separate from blocks. Block-entity registration is split between `BlockRegistry` and `BlockEntityRegistry`, so additions must follow the relevant subsystem precedent.
- Entity renderers, block-entity renderers, and cutout layers are wired in `BritanniaMod.ClientModSetup`.

### Entity variants and GeckoLib

- `CitizenEntity` is the best persisted/synchronized variant precedent: `SynchedEntityData`, `finalizeSpawn`, and NBT save/load.
- `BaseBritanniaAnimal` and `BaseBritanniaRenderer` are the closest shared animal/render bases.
- White-ibis variants must be selected server-side, synchronized, and persisted; renderer-only randomness would be unstable.
- Animated entity precedents include `BaseBritanniaAnimal`, `BaseBritanniaMonster`, and `CitizenEntity`.
- Animated block entities include the small/large forges, blue/purple tents, and the large-structure anchor.
- No server-authoritative one-shot block animation precedent was found. The training dummy needs an explicit server-to-client trigger and deterministic replay/reset per valid hit.

### Jhelom and regional spawning

- `CityRegistry` defines exact key `Jhelom` as three AABBs (main city plus two islands).
- `CitySpawnRules` uses a managed cap of 10 per area, a 200-tick interval, and five spawn attempts; `CitySpawner` enforces join filtering and caps.
- Dynamic `RegionCache`/`RegionData` provides region name, climate, and bounds, but no durable static ID. Bootstrap city records have runtime `public_id`, but no repository-defined Jhelom UUID exists.
- A requested cap of 15 ibis across all Jhelom areas conflicts with the existing shared cap of 10 per AABB. Adding ibis to the generic allow-list would make them compete with other city entities and would not enforce a Jhelom-wide total. A dedicated policy is needed.

### Dedicated-server bootstrap

- `BritanniaMod` owns start, recurring tick, and stop lifecycle wiring.
- `WorldBootstrapHandler` fetches asynchronously and applies regions, cities, service NPCs, fish, grapes, players, and quests on the server thread.
- `network/WorldBootstrapAPI.java` declares package `...sync` despite its filesystem path; future work should respect the declared package until intentionally corrected.
- Runtime city bootstrap/cache is the natural integration point if spawn bounds must come from authoritative service data.

### Multiblocks and block entities

- The robust large-structure precedent is `LargeStructureAnchorBlock`, `LargeStructurePartBlock`, `LargeStructureAnchorBlockEntity`, `ShrinePlacementPlanner`, and `ShrinePlacementExecutor`.
- It supplies root/part offsets, bounds/chunk/replaceability/authorization preflight, transactional placement and rollback, verification/sync, central teardown, and one-drop behavior.
- Simpler vertical precedents are `DoubleWallBlock`, `TripleMetalDoorBlock`, and `TallThinBlock`.
- Legacy armoires, troughs, and three-height lights lack equivalent atomic guarantees. Large props and multiblock containers should use the shrine lifecycle pattern.
- `BlockEntityRegistry` is the general registry; the large-structure anchor is the strongest persistence/sync precedent and `FlowerBlockEntity` is a simpler one.

### Containers

- `ArmoireBlock`/`ArmoireBlockEntity` provide `MenuProvider` + `Container`, `NonNullList`, `ContainerHelper` persistence, vanilla chest menus, opener tracking/sounds, and server-side drop-on-remove.
- Vanilla row menus cover the requested 9–54 slots.
- A multiblock crate must keep one authoritative inventory on its root and resolve all parts to it, preventing duplicate contents or drops when any part breaks.

### Adventure placement and axe handling

- `GlobalEventHandler` builds `AdventureModePredicate` components, assigning `CAN_PLACE_ON` to saplings and `CAN_BREAK` to the two-handed axe.
- `FarmingBlock` is the precedent for narrowly permitting a server-side Adventure placement and consuming only after success.
- Ladder placement/breaking must use equally narrow predicates and must not relax Adventure globally.
- `AxeHarvestRules`, `PlayerEventHandler`, and `WoodChopEventHandler` implement special two-handed-axe behavior for logs/leaves/fruit trees.
- `ModTags` and data tags are the right normal tool-classification layer. No canonical `minecraft:mineable/axe` entry was found; ladders should get ordinary axe mineability plus exact Adventure permission without being treated as trees.

### Water interactions

- `WaterBarrelBlock` and the trough check for `PitcherItem`; `PitcherItem` persists `FilledWithWater`.
- `ItemRegistry.PITCHER_EMPTY` is registered as a plain `BlockItem`, so the existing pitcher fill path appears disconnected from the registered empty pitcher.
- `WateringCanItem` is server-authoritative, has 12 charges, and refills from water sources. Farming/flower/orange-root systems understand watering cans and/or buckets.
- The well specification must choose supported containers (watering can, bucket, pitcher, or a stated combination). Reusing pitchers also requires fixing the registration mismatch.

### Skills, weapons, and dummy hits

- `SkillManager` stores skills by player UUID, uses lowercase string slugs, syncs/persists them, and offers chance-based `trySkillGain` (+0.1) and deterministic `awardSkillGain`, both bounded only by the configured global maximum.
- No caller-specific cap of 25 exists. Dummy gains require an explicit capped wrapper/check.
- `BlacksmithCrafting` has a per-player `LAST_ATTEMPT_TICK` guard useful as an anti-duplicate precedent.
- No authoritative combat-skill identifiers were found in tracked code/data. Likely slugs include `swordsmanship`, `mace_fighting`, `fencing`, `tactics`, `wrestling`, `anatomy`, and `lumberjacking`, but runtime/bootstrap confirmation is required, especially for mace.
- `WeaponRegistry`, `WeaponProfile`, `CraftableDef`, and `blacksmithing/craftables.json` provide structured metadata. Current families/categories include axes, bashing, bladed, polearms, and throwing.
- `BlacksmithEquipmentItem.definition()` exposes stable category metadata; some legacy weapons do not. No authoritative family/item-to-combat-skill map exists, and `bladed`/`axes` are too broad to infer safely. Display-name matching is not acceptable.
- `PlayerEventHandler` and `BardEvents` show server-side `AttackEntityEvent` interception, cancellation, checks, and cooldowns.
- No dummy durability-suppression precedent exists. Canceling the attack event is the likely mechanism but NeoForge event order must be verified in live testing.

### Processing and textiles

- Juice press code provides server insert/extract plus persistence; wine barrel code provides timed processing.
- `CraftableRegistry`/`BlacksmithCrafting` demonstrate data-driven, server-authoritative, anti-duplication transactions.
- Existing inventory IDs include `cotton`, `flax`, and `spiders_silk`; cotton/flax also have crop/seed systems.
- No inventory IDs were found for `silk`, `thread`, `spool_of_thread`, `yarn`, `ball_of_yarn`, `cloth`, `bolt`, `loom`, or `spinning_wheel`.
- Banner data uses conceptual cotton/wool/silk/linen fabric IDs, but these are not inventory items. No project textile tag defines vanilla wool as input.
- Recipes must clarify whether silk means `spiders_silk`, plus exact intermediate/output IDs and quantities.

### Neighbor-aware models

- `HouseFarmPlotBlock` is the strongest four-way precedent, maintaining N/E/S/W properties across placement, neighbor updates, rotation, and mirroring.
- `BannisterBlock`, `DoubleWallBlock`, and `WallConnection`/`WallShape` cover straight/corner/T visuals; `IronFenceBlock` covers vertical single/bottom/middle/top connections.
- New connected assets should follow these update conventions and provide complete blockstate rotation/mirror coverage.

### Creative tabs, localization, data, and rendering

- `CreativeTabRegistry` explicitly populates tabs through safe lookup.
- English localization, blockstates, and models are primarily hand-authored.
- Loot uses canonical singular `data/.../loot_table`; tags mainly use singular `tags/block` and `tags/item`, though legacy plural directories remain.
- No `GatherDataEvent`/provider framework was found; follow the hand-authored resource convention unless datagen is separately approved.
- Alpha-tested assets must be included in the existing client cutout/render-layer setup.

### Existing moongates

- Random-city gates already use `moongate_block`/`moongate_top`, `MoongateBlock`, `MoongateTeleportationHandler`, and `MoongateTickHandler`. They include Jhelom and handle mounts/escorts plus a 100-tick cooldown.
- Paired dungeon gates already use `dungeon_moongate_block`/`dungeon_moongate_top`, a block entity, `DungeonMoongateBlock`, `MoongateLinkingWand`, and `BritanniaTeleportService` cooldown handling.
- No Shame-specific gate ID/code was found. Jhelom ↔ Shame likely means world configuration of the generic paired system, not a third gate implementation; confirm with the owner.
- Existing risks outside Milestone 0: the linking wand keeps first selection in singleton item state rather than per stack/player, and dungeon gate top placement lacks full replaceability preflight.

### Naming and semantic collisions

- **Moongate:** overlaps two existing systems; confirm random-city versus paired behavior before adding code.
- **Sandstone walls:** existing `ornate_sandstone_wall`, `regular_sandstone_wall`, and `sandstone_block_wall` plus sandstone brick/road assets. These walls use a custom two-high pattern, not vanilla `WallBlock`.
- **Textiles:** cotton/flax/spiders' silk inventory IDs coexist with cotton/wool/silk/linen banner material definitions.
- **Water:** water barrel, trough, watering can, empty pitcher, and buckets already overlap the concept; pitcher registration is inconsistent.
- **Containers:** armoires, chests, trash barrels, and wine barrels overlap behavior, but no exact crate IDs were found.
- No exact requested IDs were found for the other new props in tracked source/resources.
- `assets/britannia_mod/models/block/containers/barrel.json` appears potentially orphaned and should be checked before choosing a generic barrel model name.

### Top-level raw asset inventory

Only the top level was inspected; archives were not extracted and folders were not recursively inventoried.

Directories: `blood`, `fountain`, `moongate`, `training_dummy`, `white ibis`.

Archives: `blood.zip`, `elitecreatures-medieval_market_decoration_v2.zip`, `Garden Essentials Vol 4 - Bushes.zip`, `globe.zip`, `Medieval Market Furniture Set.zip`, `Nexo Assets - Crates & Barrels.zip`, `Nexo Assets - Tailoring Station.zip`, `shizuart_farmer_props.zip`, `ShizuArt_Plants_Bundle.zip`, `training_dummy.zip`.

### Risks and blockers for later milestones

1. No checked-in manifest yet defines exact batch scope and canonical IDs.
2. Ibis counting must reconcile a Jhelom-wide cap of 15 with the existing per-area shared cap of 10.
3. Combat-skill slugs and weapon-to-skill mapping are absent from tracked data.
4. The dummy needs a cap-25 skill path, anti-farming cadence, server-triggered one-shot animation, and verified durability suppression.
5. Well-compatible containers are ambiguous and empty-pitcher registration is inconsistent.
6. Textile recipes lack canonical intermediate/output IDs and exact input semantics.
7. Jhelom ↔ Shame likely overlaps the paired-gate system and needs configuration ownership clarified.
8. Large props/containers require atomic placement and teardown to prevent orphans/duplication.
9. Raw archives may include licensing/readme constraints; inspect them before any import or conversion.

### Milestone validation

- Worktree/branch bootstrap completed from `patch-18` at the recorded commit.
- No raw archive was extracted or modified.
- No source, resource, gameplay data, asset, or existing repository file changed.
- This log is the only intended worktree change.
- No commit was created.

## Milestone 1 — Raw Asset Inventory and Import Manifest

### Scope and method

- Approved by the owner after Milestone 0; changes remain uncommitted.
- Recursively enumerated the read-only raw root on 2026-08-09.
- Direct tree result: 74 files totaling 4,705,838 bytes: 33 PNG, 24 JSON, 10 ZIP, 3 MCMETA, 2 YML, and 2 BBMODEL files.
- Enumerated all ten outer ZIPs and seven nested ZIP entries in memory. No archive was extracted into or modified under the raw source tree.
- Inspected model element bounds, Blockbench animation metadata, texture references/dimensions/alpha formats, filename relationships, archive configuration, and SHA-256 checksums for assigned candidates.
- Created `docs/new-assets/ASSET_IMPORT_MANIFEST.md`; no asset was imported and no placeholder was created.

### Owner decisions incorporated

- Treat the ibis limit as one Jhelom-wide cap of 15, independent from other city animals.
- Replace the existing `MoongateBlock` visual/structure with one logical block and a 32-voxel-high custom model while preserving its teleport behavior.
- Water wells will fill watering cans, vanilla buckets, and pitchers; the pitcher registration may be corrected for water behavior only.
- Axes are valid training-dummy weapons and map to Swordsmanship. Canonical skill slugs and non-axe weapon mapping remain unresolved.
- Textile silk is a future inventory material distinct from `spiders_silk`.
- Loom processing is narrowly authorized as 5 balls of yarn or 5 spools of thread to 1 folded cloth.
- Display cases remain decorative/connected only, with no storage or displayed-item inventory.
- Existing sandstone IDs/resources are reuse targets rather than duplicated registrations.

### Inventory conclusions

- `FOUND`: fountain, globe, small crate, large crate, water well, scarecrow, dress form, folded cloth, and the existing tracked sandstone family.
- `PARTIAL`: white/scarlet ibis pair, moongate, six-cart family, training dummy, Moonglow bush, ladder, fern, loom, and spool-of-thread representation.
- `MISSING`: medium crate, bolt of cloth, spinning wheel, ball of yarn, silk inventory item, all display-case state art, pewter mug, kettle, and plates/silverware.
- White ibis source includes 512×512 white texture and `walk`, `idle`, and `eating` animations; no scarlet texture exists.
- Merchant source provides only red, purple, and a structurally different base wagon, not six color variants.
- The training source has a `hit` animation but is a roughly 20×44×14-voxel punching bag, not the requested 32×48 structure.
- Fountain geometry fits a 32×32×approximately-46-voxel envelope, suitable for the requested 2×2×3 structure after origin normalization.
- Crate source provides distinct small and large single-crate geometries; the other models are stacks, not a medium crate.
- Tailoring source provides loom, standing loom, mannequin, fabric stack, fabric spools, and tailoring station. The loom is only 2 blocks high; no spinning wheel or bolt exists.
- No display-case, sandstone raw-source, pewter-mug, kettle, plate, or silverware files were found.
- Existing tracked sandstone registrations already include `custom_sandstone_brick` with four variants, `regular_sandstone_wall`, both sandstone windows/posts, `sandstone_battlement`, and `sandstone_column`.

### Provenance and licensing note

- Multiple archives identify themselves as purchased MCModels/Nexo/ShizuArt resources and include setup/readme material.
- No explicit redistribution/license grant was found inside the inspected archives. The owner authorized the purchased art only as temporary placeholder material that will be replaced later.

### Milestone 1 validation

- Manifest contains every requested asset or requested variant family with a proposed/existing ID, source path, source format/checksum where available, `FOUND`/`PARTIAL`/`MISSING` status, target paths, behavior, dimensions, animation, collision, and blocker notes.
- Special checks for ibis variants, six cart colors, display-case states, crate sizes, loom/spinning wheel, and large requested dimensions are explicitly recorded.
- Raw source remained read-only.
- No Java/game resource changes, imports, placeholders, commits, merges, or pushes were made.

### Post-review owner resolutions

- Purchased-pack art is temporary placeholder material and will be replaced later. Any later copied pack asset must be marked `PLACEHOLDER` in the manifest rather than treated as final art.
- Merchant-cart colors are approved as red, purple, blue, green, yellow, and white.
- All axes map to Swordsmanship for training-dummy classification.
- The manifest now includes a consolidated dimensional re-authoring matrix for the moongate, training dummy, ladder, and loom. It distinguishes those visual changes from origin normalization (fountain) and multiblock placement/collision work (large crate).

### Milestone 1 commit

- Owner approval was received after the post-review resolutions were incorporated.
- Committed exactly the Milestone 1 documentation as `221f69da397a61bb2a4db1131b8128be9cadd377` (`Document new assets inventory`).
- No implementation files or raw assets were included in that commit.

## Milestone 2 — Asset Import Infrastructure and Low-Risk Decorative Assets

### Scope implemented

- Starting HEAD: `221f69da397a61bb2a4db1131b8128be9cadd377`.
- Added final block/item IDs for `globe`, `fern`, `moonglow_bush`, `folded_cloth`, `bolt_of_cloth`, `pewter_mug`, `kettle`, and `plates_and_silverware`.
- Added reusable horizontally oriented `DecorativePropBlock` geometry/collision handling and a substrate-aware, replaceable, no-collision `DecorativePlantBlock`.
- Registered all eight block items in the existing decor creative tab and added English localization, blockstates, item models, one-drop loot tables, and deliberate voxel shapes.
- Registered fern and Moonglow bush on the client cutout render layer. No world generation was added.
- Reused the existing sandstone family rather than creating duplicate registry IDs. Added one-drop loot tables for its ten extant blocks and explicit `minecraft:mineable/pickaxe` membership.

### Art and provenance

- Normalized temporary purchased-pack art for globe, fern, Moonglow bush, and folded cloth into the Britannia namespace. Source namespaces were removed from all model texture references.
- Added unmistakable code-authored temporary models for bolt of cloth, pewter mug, kettle, and plates/silverware using conspicuous placeholder materials/colors.
- All eight new assets use final registry IDs but are classified `PLACEHOLDER`; purchased artwork is not represented as final imported art.
- Raw source files and archives remained read-only.

### Validation performed

- Parsed 64 relevant blockstate/model/item/loot/tag JSON resources successfully.
- Verified all referenced local models/textures resolve, with zero resource-reference errors and no retained `lanshan`, `workshop_six`, or `shizuart_furnitures` namespaces.
- Verified imported PNG dimensions/color mode: globe 128x128, fern 256x256, Moonglow bush 256x256, and folded cloth 64x64, all ARGB.
- `gradlew.bat compileJava`: PASS (36 existing compiler warnings).
- `gradlew.bat build`: PASS after updating the exact repository-item preservation count from 735 to 743 for the eight intentional new block items; 1,679 tests completed, 17 skipped, zero failed.
- `gradlew.bat runGameTestServer --no-configuration-cache`: PASS; all 337 required game tests passed.
- The first game-test pass exposed unqualified sandstone tag values resolving as `minecraft:*`; corrected them to `britannia_mod:*`. The second pass had no missing-tag/model/texture errors and showed the intended Britannia tag entries. Final audit assigns the ten sandstone blocks and three metal props to pickaxe mineability and the wooden globe to axe mineability.
- The game-test environment continued to log expected missing local configuration/authentication warnings while using its test defaults; these did not fail the suite.

### Validation still requiring an interactive client

- Visual scale, UVs, cutout edges, inventory transforms, collision feel, and all four horizontal orientations require live placement review.
- Existing sandstone four-variant appearance and neighbor transitions require live visual/collision review.
- These owner/client checks are recorded in `docs/new-assets/LIVE_TEST_CHECKLIST.md`; JSON and dedicated-server validation do not substitute for them.

### Tooling note

- The feature worktree held a Git LFS pointer for `gradle/wrapper/gradle-wrapper.jar`. A local materialized copy from the primary worktree was used only to run Gradle and is restored to the exact tracked pointer before milestone handoff.

### Commit status

- Owner approval and explicit commit authorization were received.
- Committed Milestone 2 as `3075a1052a27c0225e8f640e1906deaa660e2548` (`Add low-risk decorative assets`).

## Milestone 3 — Large Decorative Multiblocks

### Scope implemented

- Starting HEAD: `3075a1052a27c0225e8f640e1906deaa660e2548`.
- Added final block/item IDs for six merchant-cart colors plus `fountain`, `scarecrow`, `dress_form`, and `loom`.
- Added reusable `DecorativeMultiblockBlock` and `DecorativeMultiblockItem` infrastructure with horizontal orientation, authoritative part/root addressing, deliberate per-cell collision, root-only rendering, atomic placement preflight, transactional rollback, integrity checks, whole-structure teardown, and exactly one manual item drop.
- Placement rejects unloaded chunks, world-border/world-height violations, protected cells, block entities, non-replaceable cells, missing base support, and any partial obstruction before mutating the world.
- Structures use the requested occupancy: carts 3x3x3, fountain 2x2x3, scarecrow 2x1x2, dress form 1x1x2, and loom 2x1x3.
- The loom is structural/decorative only in this milestone. The approved 5-yarn or 5-thread to 1-folded-cloth conversion remains deferred until the corresponding textile inputs and processing contract are ready.

### Art and provenance

- Added `tools/new-assets/import_milestone3.ps1` as a deterministic, read-only-source import/normalization script.
- Merchant cart red/purple, fountain, scarecrow, dress form, and loom use temporary source/purchased-pack art under final Britannia IDs. Blue/green/yellow/white carts reuse the temporary cart geometry with obvious vanilla wool color placeholders.
- Re-authored the loom from its approximately 32x32x36 source bounds to an exact 32x48x16-voxel model envelope, satisfying its 2-wide by 3-high contract.
- Normalized the fountain to x/z `0..32` while retaining its 46-voxel vertical range; normalized scarecrow to x `0..25`, y `0..31.71682`, z `0..16`; retained dress-form bounds x `1..15`, y `0..32`, z `4..12`.
- Raw source files and archives remained read-only. All copied purchased art remains classified `PLACEHOLDER` and must be replaced later.

### Validation performed

- Re-ran the deterministic importer successfully and parsed all 43 generated Milestone 3 JSON resources.
- Verified zero legacy source-namespace texture references and zero unresolved local model/texture references.
- Verified generated model bounds for every asset/color and PNG dimensions/color modes, including animated fountain water metadata.
- Added unit coverage for cart part count/root selection, root-only rendering, all-direction anchor round trips, minimum-cell mapping, and the 27-part state-space limit.
- Added a dedicated-server GameTest proving that removal of a fountain child tears down its whole structure.
- The first server launch exposed Minecraft's cache bake visiting unused values in the shared 0..26 `PART` property for smaller structures. `getShape` now returns an empty shape for unused state values.
- `gradlew.bat runGameTestServer --no-daemon --no-configuration-cache`: PASS; all 338 required GameTests passed after the guard fix.
- Focused unit/preservation tests: PASS, including the intentional repository-item count update from 743 to 753.
- `gradlew.bat build --no-daemon --no-configuration-cache`: PASS; 1,683 tests completed, 17 skipped, zero failed or errored.

### Validation still requiring an interactive client

- Place every structure in all four orientations and verify scale, UVs, cutout/translucent rendering, collision, obstruction rejection, whole teardown, one drop, and save/reload persistence.
- Fountain water animation/translucency and the six merchant-cart color treatments require visual acceptance.
- These checks are recorded in `docs/new-assets/LIVE_TEST_CHECKLIST.md`.

### Commit status

- Owner approval and explicit commit authorization were received.
- Committed Milestone 3 as `1abae182` (`Add decorative multiblock structures`).

## Milestone 4 — Crate Container Family

### Scope implemented

- Starting HEAD: `1abae182`.
- Added final `small_crate`, `medium_crate`, and `large_crate` block/item IDs with 9-, 27-, and 54-slot inventories respectively, using vanilla one-, three-, and six-row chest menus.
- Added `CrateBlock` and `CrateBlockEntity`: only the authoritative root creates/persists an inventory, all valid child interactions resolve to that root, all inventory changes are server-authoritative, and `ContainerHelper` provides disk persistence.
- Small and medium crates occupy one cell. The large crate uses atomic 2x2x2 placement, one root inventory, per-cell fitted collision, whole-structure teardown, one crate-item drop, and one contents drop regardless of the broken part.
- Reused the Milestone 3 transactional placement infrastructure and added a pre-teardown root hook so container contents are emitted exactly once while the block entity still exists.
- Corrected the shared asymmetric-footprint local z-axis transform discovered while integrating the corner-anchored large crate. Existing symmetric/one-cell Milestone 3 structures retain their occupied volumes, and the all-direction anchor regression tests pass.
- Added comparator output on each crate's root and assigned all three wooden crates to axe mineability.

### Art and provenance

- Added `tools/new-assets/import_milestone4.ps1` as a deterministic, read-only-source crate importer.
- Normalized temporary purchased small-crate art to its final namespace. It remains 12x11x12 voxels with a 64x64 ARGB texture.
- Added an unmistakable code-authored magenta/black medium-crate placeholder with bounds x/z `0.5..15.5`, y `0..14`; no source stack was relabeled as a medium crate.
- Translated the temporary purchased large-crate model, including rotation origins, to x `0.25..27.75`, y `0.5..19`, z `0.46815..21.53185` inside its authoritative 2x2x2 structure. Its source texture remains 128x128 ARGB.
- All purchased crate art is classified `PLACEHOLDER` and remains replacement work. The raw archive was never modified.

### Validation performed

- Deterministic importer: PASS after correcting PowerShell tuple grouping; all 12 generated crate JSON resources parse successfully.
- Resource audit: PASS; no retained `Crates & Barrels`, `workshop_six`, or `Raw Files` model references.
- Focused unit/regression tests: PASS. Coverage verifies 9/27/54 capacities, large-inventory NBT round trip through first/last slots, one root across eight large-crate cells, supported row-count enforcement, multiblock transform regression, and exact repository-item count 756.
- Dedicated-server GameTests: PASS; all 339 required tests passed. The new test opens a server chest menu, verifies one large-crate block entity, breaks a child in Survival, checks whole teardown, and proves exactly seven stored diamonds plus one crate item drop.
- The first GameTest assertion used the framework's default Creative player and therefore correctly received no block item; the test now explicitly selects Survival before validating the drop contract.
- `gradlew.bat build --no-daemon --no-configuration-cache`: PASS; 1,687 tests completed, 17 skipped, zero failures or errors.
- `gradlew.bat runClient --no-daemon --no-configuration-cache`: PASS for Milestone 4 resource startup. The client completed resource reload, initialized the sound engine and texture atlases, and logged no `small_crate`, `medium_crate`, or `large_crate` model/texture failures. Numerous unrelated pre-existing missing-resource warnings remain elsewhere in the mod.

### Validation still requiring an interactive client/multiplayer session

- In-world visual alignment, UVs, inventory icons, collision feel, opening sounds, and all four orientations require live review.
- Save/reload persistence, shift-click behavior, root/child breaking while populated, simultaneous two-client access, and breaking while another client has the menu open remain on the live checklist.

### Tooling note

- The feature worktree's Git LFS wrapper pointer was temporarily materialized for Gradle validation and is restored before handoff.

### Commit status

- Owner approval and explicit commit authorization were received.
- Committed Milestone 4 as `6c9761dd` (`Add crate container family`).

## Milestone 5 — Water Well and Adventure Ladder

### Scope implemented

- Starting HEAD: `6c9761dd`.
- Added final `water_well` and `ladder` block/item IDs using the existing atomic multiblock placement and whole-structure teardown infrastructure.
- The well occupies an authoritative 1x2x2 volume and handles all interactions server-side. It fills watering cans to 12 charges, converts vanilla buckets to water buckets, and marks registered pitchers as filled with water without accepting unrelated containers.
- Corrected `pitcher_empty` to register the existing water-only `PitcherItem` while preserving its `BlockItem` placement behavior.
- The ladder occupies an authoritative 1x1x3 volume, is tagged climbable across every part, has deliberate two-sided rails/rungs collision, places from the ground, tears down as one structure, and drops once.
- Adventure placement permission is confined to the ladder item. Adventure break permission is added to axes for only the custom ladder; the existing two-handed axe handler preserves its log/fruit-tree behavior and now recognizes this ladder as well.

### Art, provenance, and dimensional re-authoring

- Added `tools/new-assets/import_milestone5.ps1` as a deterministic importer that reads `shizuart_farmer_props.zip` without modifying the archive.
- Normalized the temporary purchased farmer-well model to x `0..16`, y `0..32`, z `0..32` inside the well's 1x2x2 structure.
- Re-authored the temporary purchased stepladder into a 48-voxel-high model with x `0..16`, y `-16..32`, z `0..16`. The model renders from the middle of the three structure cells so all elements remain inside Minecraft's permitted extended-model range.
- Both assets use the purchased 256x256 ARGB farmer-props atlas under final resource IDs. They are classified `PLACEHOLDER` and require later replacement.

### Validation performed

- Focused unit/regression tests: PASS. Coverage verifies both authoritative footprints, pitcher placement plus water state, all three accepted well container families, rejection of unrelated containers, and exact repository-item count 758.
- Dedicated-server GameTests: PASS; all 341 required tests passed. Milestone 5 tests exercise all three server-side well conversions and Adventure placement, climbability of all three ladder cells, child-part teardown, and exactly one ladder-item drop.
- `gradlew.bat build --no-daemon --no-configuration-cache`: PASS after the client-model correction; 1,690 tests completed, 17 skipped, zero failures or errors.
- Initial client validation correctly rejected a ladder root model extending above y=32. The resource was translated to y `-16..32` and moved to the middle structure cell; the subsequent client resource reload logged no Milestone 5 model or texture failures. Unrelated pre-existing missing-resource warnings remain elsewhere in the mod.

### Validation still requiring an interactive client/multiplayer session

- In-world well/ladder alignment, UVs, all four orientations, fitted collision, double-sided climb feel, obstruction rollback, and save/reload require live review.
- Creative/Survival/Adventure item behavior and a two-client permission/synchronization pass remain on the live checklist.

### Tooling note

- The feature worktree's Git LFS wrapper pointer was temporarily materialized for Gradle validation and restored exactly before handoff.

### Commit status

- Owner approval and explicit commit authorization were received.
- Committed Milestone 5 as `e602f341` (`Add water well and adventure ladder`).

## Milestone 6 — Ibis Entity, Variants, and Jhelom Population

### Scope implemented

- Starting HEAD: `e602f341`.
- Added one `britannia_mod:ibis` creature entity and spawn egg. White and scarlet are synchronized integer variants on that entity type, selected server-side and persisted in NBT.
- Added the GeckoLib model/renderer and converted the source `walk`, `idle`, and `eating` animations.
- Added a dedicated `JhelomIbisPopulation` policy over the exact three `CityRegistry` Jhelom AABBs. It counts and caps ibis at 15 across the combined areas, independently of the generic 10-per-area city-animal cap.
- Ibis joins outside Jhelom or above the cap are rejected server-side. Population replenishment selects only Jhelom positions and only loaded chunks. No biome modifier or global natural-spawn registration was added.

### Art, provenance, and deterministic import

- Added `tools/new-assets/import_milestone6.ps1`; it reads the raw `.bbmodel` and texture without modifying either source.
- Converted 60 exported bones, 89 cubes, and all three source animations into final GeckoLib resources.
- Copied the purchased 512×512 ARGB white texture byte-for-byte (SHA-256 `5127c6846e013cc7021bc63c906693526685d1787906fc24ca4a43c232fa2507`).
- The image-generation edit established the approved natural scarlet palette, but its direct output was rejected because it changed the atlas to 1254×1254 RGB and removed transparency. The importer instead performs a deterministic connected neutral-plumage recolor: 14,355 pixels change while all 262,144 alpha values and the exact UV atlas dimensions remain unchanged.
- Both the purchased white art and generated scarlet recolor are `PLACEHOLDER` assets to be replaced later.

### Validation performed

- Java and test sources compile successfully.
- Focused unit tests pass for the three-area/15-total policy, exact cap boundaries, safe variant fallback, source checksum, texture dimensions/alpha invariants, three animation names, and absence of global biome-spawn registration.
- `gradlew.bat build --no-daemon --no-configuration-cache`: PASS; 1,695 tests completed, 17 skipped, zero failures or errors.
- Dedicated-server GameTests: PASS; all 343 required tests passed. The two Milestone 6 tests cover both variants on one entity type and the scarlet NBT save/reload round trip.
- Development-client startup reached completed resource reload and renderer registration with no ibis-specific model, animation, texture, or renderer errors. Unrelated pre-existing resource warnings remain elsewhere in the mod.

### Validation still requiring an interactive client/multiplayer session

- In-world model scale, pivot alignment, UVs, animation transitions, scarlet appearance, hitbox, sounds, and creative spawn-egg presentation require live review.
- Natural replenishment in each Jhelom area, the combined 15-bird cap under simultaneous chunk loading, outside-region rejection, restart persistence, and two-client variant synchronization remain on the live checklist.

### Commit status

- Owner approval and explicit commit authorization were received.
- Committed Milestone 6 as `8b8feaf4` (`Add Jhelom ibis variants`).

## Milestone 7 — Training Dummy Skill Trainer

### Scope implemented

- Starting HEAD: `8b8feaf4`.
- Added `britannia_mod:training_dummy` as one atomic, directional 2-wide × 3-high multiblock with one root block entity and a GeckoLib renderer. Breaking any cell removes the whole structure; sneaking intentionally bypasses training so an owner can dismantle it normally.
- Added a server-authoritative left-click interceptor. Supported main-hand strikes cancel block/item use before mining durability can be consumed; only the server-side `START` action is accepted, so client repeats, hold packets, offhand activity, and duplicate start/stop sequences cannot grant additional attempts.
- Added a persistent per-player 60-tick cooldown. An accepted strike plays the synchronized one-shot animation even when its random skill-gain roll does not succeed; cooldown rejections do not replay it.
- Added an activity-capped `SkillManager.trySkillGainCapped` path so Swordsmanship, Mace Fighting, and Fencing cannot cross 25.0. A small 10% accepted-hit roll attempts ordinary Tactics gain. Wrestling, Anatomy, and Lumberjacking are never referenced by the trainer.
- Weapon classification uses registered item types and authoritative Blacksmith catalogue categories, never display names. All Axes and ordinary Bladed weapons map to Swordsmanship; Bashing maps to Mace Fighting; Polearms and explicit thrusting blade IDs map to Fencing; Throwing, bows, unsupported items, and empty hands are rejected.
- The repository does not contain an authoritative combat-skill bootstrap. The isolated working slugs are `swordsmanship`, `mace_fighting`, `fencing`, and `tactics`; these require integration confirmation against the external skill service before release.

### Art, dimensions, and deterministic import

- Added `tools/new-assets/import_milestone7.ps1`; it reads the purchased archive/direct item model without modifying source files.
- Re-authored the 20×44-voxel punching-bag blueprint to exact visible x/y bounds of 32×48 voxels, retained its 14-voxel depth, and gave it deliberate per-cell collision across the authoritative 2×3 structure.
- Retimed the source five-second looping `hit` animation to a one-second non-looping `animation.training_dummy.hit` trigger.
- The gray 128×128 source art remains a `PLACEHOLDER`: it is a punching bag rather than final Ultima-style training-dummy art and must be replaced later.

### Validation performed

- Focused policy/classification/asset tests: PASS.
- `gradlew.bat build --no-daemon --no-configuration-cache`: PASS; 1,700 tests completed, 17 skipped, zero failures or errors.
- Dedicated-server GameTests: PASS; all 344 required tests passed. The Milestone 7 test covers one authoritative animation owner, two-player cooldown isolation, repeat rejection, accepted animation count, supported axes/swords, unsupported/unarmed rejection, and unchanged durability.
- Development-client startup reached completed resource reload with no training-dummy model, blockstate, texture, animation, or renderer warnings/errors. Unrelated pre-existing resource warnings remain elsewhere in the mod.

### Validation still requiring an interactive client/multiplayer session

- Final world scale, pivots, UVs, facing, collision feel, hit animation replay, sound/feedback, item presentation, and sneak-to-dismantle behavior require live review.
- The exact external skill slugs, real skill persistence/sync, 25.0 boundary, 10% Tactics behavior, dual-wield/offhand behavior, and two-client cooldown/animation synchronization remain on the live checklist.

### Commit status

- Owner approval and explicit commit authorization were received.
- Committed Milestone 7 as `63a3492f` (`Add animated training dummy`).

## Milestone 8 — Textile Processing

### Scope implemented

- Starting HEAD: `63a3492f`.
- Added final inventory IDs `britannia_mod:ball_of_yarn` and `britannia_mod:spool_of_thread` with temporary unmistakable placeholder models.
- Added `britannia_mod:spinning_wheel` as a functional one-cell replacement-art block. One wool item produces one ball of yarn; one existing `britannia_mod:cotton` or `britannia_mod:flax` produces one spool of thread.
- Upgraded the existing 2×3 `britannia_mod:loom` in place to accept five balls of yarn or five spools of thread and produce one existing placeable `britannia_mod:folded_cloth`.
- Exchanges are immediate and server-authoritative. Input is consumed and output is inserted exactly once; a full inventory drops the output at the player instead of losing or duplicating it. Insufficient loom stacks fail without mutation, and unsupported inputs pass through without mutation.
- No silk inventory item was registered. `britannia_mod:spiders_silk` is explicitly unsupported, preserving the owner's boundary that future textile silk is a distinct material.
- No processing GUI, persistent machine inventory, timer, energy system, broader tailoring recipes, bolt-of-cloth conversion, or placeable spool block was added.

### Art and registration

- The spinning wheel uses code-authored magenta/black one-block placeholder geometry because the source inventory contains no spinning-wheel art; its footprint must be reviewed when replacement art arrives.
- Yarn and thread use conspicuous vanilla-texture placeholder item models. The purchased two-block-tall fabric-spool prop was not repurposed as the handheld thread item.
- Added block/item registrations, creative-tab entries, localization, spinning-wheel blockstate/models/loot, axe mineability, and updated the repository item-count preservation guard from 760 to 763.

### Validation performed

- Focused conversion-policy and resource-contract tests: PASS.
- `gradlew.bat build --no-daemon --no-configuration-cache`: PASS; 1,702 tests completed, 17 skipped, zero failures or errors.
- Dedicated-server GameTests: PASS; all 345 required tests passed. The Milestone 8 test covers wool/cotton/flax one-to-one exchanges, five-yarn and five-thread loom exchanges through a child cell, insufficient-input rollback, spiders'-silk rejection, exact full-inventory output dropping at the player, and isolated two-player loom exchanges.
- Development-client startup reached completed resource reload with no `spinning_wheel`, `ball_of_yarn`, or `spool_of_thread` model/blockstate/texture warnings or errors. Unrelated pre-existing resource warnings remain elsewhere in the mod.

### Validation still requiring an interactive client/multiplayer session

- Final placeholder presentation, facing, collision, sounds, creative entries, full-inventory drops, rapid interaction, and two-player exchanges require live review.
- Future `britannia_mod:silk` acquisition and spinning-wheel support remain deliberately deferred; spiders' silk must continue to be rejected.

### Commit status

- Owner approval and explicit commit authorization were received.
- Committed Milestone 8 as `65ea30aa` (`Add textile processing`).

## Milestone 9 — Connected Decorative Display Cases

### Scope implemented

- Starting HEAD: `65ea30aa`.
- Added one final `britannia_mod:display_case` block/item ID as an atomic 1-wide × 2-high structure using the shared transactional multiblock placement and teardown contract.
- Added server-derived north/east/south/west root connection flags. A lone case is independent, one neighbor produces an end, two opposite neighbors produce a middle, and two adjacent neighbors produce a corner. Three- and four-way layouts remain deterministic through the same compositional side-removal model rather than requiring extra IDs.
- Neighbor state is recalculated after placement and teardown, including when a connected case's upper cell is broken. Each structure drops exactly one item and leaves no orphan cells.
- The display case is strictly decorative: it implements no `EntityBlock`, block entity, container, storage menu, displayed-item inventory, or item-rendering mechanic.

### Art and registration

- No source art exists. Added a conspicuous code-authored magenta/black/glass 16×16×32-voxel placeholder assembled from a common frame plus four conditional glass sides.
- Added multipart blockstate models, an independent inventory model, empty multiblock loot, localization, creative-tab registration, axe mineability, and cutout rendering.
- Updated the exact repository-item preservation count from 763 to 764 for the one intentional new block item.

### Validation performed

- Java compilation and focused resource/contract tests: PASS.
- Gradle 8.9 `build --no-configuration-cache`: PASS; 1,704 tests completed, 17 skipped, zero failures or errors.
- Dedicated-server GameTests: PASS; all 346 required tests passed. The Milestone 9 test covers live independent/end/middle/corner transitions, exact shared-face flags, absence of block entities on both cells, upper-cell teardown, neighbor disconnection, and exactly one item drop.
- Development-client startup reached completed resource reload and atlas creation with no `display_case` blockstate, multipart-model, item-model, or texture warnings/errors. Unrelated pre-existing resource warnings remain elsewhere in the mod.

### Validation still requiring an interactive client

- Placeholder appearance, glass transparency, inventory presentation, collision/reach, atomic placement in all facings, creative entry, axe behavior, and live independent/end/middle/corner transitions remain on the checklist.
- No storage or displayed-item mechanism is authorized; future art should preserve the single final ID and connection contract.

### Commit status

- Owner approval and explicit commit authorization were received.
- Committed Milestone 9 as `b852d912` (`Add connected display cases`).

## Milestone 10 — One-Block City Moongate

### Scope implemented

- Starting HEAD: `b852d912`.
- Updated the existing `britannia_mod:moongate_block` in place; no parallel block ID, teleport network, block entity, or packet was added.
- Kept the random-city teleport path intact, including Jhelom, mount and active-escort transport, momentum reset, destination chunk loading, and the 100-tick cooldown.
- Left the paired dungeon moongate block, top, block entity, linking wand, and paired-destination behavior separate and unchanged.

### Art and migration

- Imported all six purchased portal textures as explicitly temporary placeholder art through deterministic `import_milestone10.ps1` checksum validation.
- Re-authored the raw 32×43×32-voxel layered portal into a 16×32×16 envelope. The registered block still occupies one pass-through interaction cell while its model renders 32 voxels high.
- Removed the legacy `moongate_top` item from the creative tab, but retained its block and item registry IDs so existing chunks and inventories remain loadable. The old top now renders invisibly and removes itself on placement, neighbor update, or a random server tick.
- Registered the final city moongate on the translucent render layer and added scaled inventory transforms for its tall model.

### Validation status

- Focused resource, registry, teleport-preservation, legacy migration, and model/atlas contract tests: PASS.
- Gradle 8.9 `build --no-configuration-cache`: PASS; 1,708 tests completed, 17 skipped, zero failures or errors.
- Dedicated-server GameTests: PASS; all 347 required tests passed. At Milestone 10 the test proved the final city gate occupied one pass-through cell without a block entity and removed a placed legacy top without disturbing the final gate. The post-closure camera-facing visual subsequently added a render-only block entity while preserving the one-cell teleport contract; the test now covers that superseding contract.
- Development-client startup completed resource reload and block-atlas creation with no `moongate_block`, Milestone 10 model, or imported portal-texture warnings/errors. The existing 44×44 `dungeon_moongate_block` mip warning and other unrelated pre-existing resource warnings remain outside this milestone.

### Commit status

- Owner approval and explicit commit authorization were received.
- Committed Milestone 10 as `f5fc7084` (`Integrate one-block city moongate`).

## Milestone 11 — Cross-System Polish and Creative/Data Audit

### Scope completed

- Starting HEAD: `f5fc7084`.
- Audited 37 requested block IDs plus `ball_of_yarn`, `spool_of_thread`, and the ibis entity/spawn egg across registration, item exposure, creative placement, localization, blockstates/models, loot, tool tags, render layers, placeholder disclosure, manifest status, and principal functional integration points.
- Added `NewAssetsCrossSystemAuditTest` as a permanent regression guard for that complete surface.
- Corrected missing cutout rendering for the globe and folded cloth, pickaxe mineability for the water well, axe mineability for the training dummy, and localization for the custom sandstone brick.
- Added explicit temporary-art wording to the ibis spawn egg and city moongate player-facing names.
- Confirmed that Jhelom ibis population bootstrap, training-dummy service, crate storage, all three authorized water containers, Adventure ladder placement, textile ratios, decorative-only display cases, and existing city-moongate teleportation remain wired.
- Confirmed silk is the only intentionally missing manifest asset. It remains a future textile material distinct from `britannia_mod:spiders_silk`.
- Recorded the family-level result and closure boundary in `MILESTONE_11_AUDIT.md`.

### Validation status

- Focused `NewAssetsCrossSystemAuditTest`: PASS; four tests completed with zero failures.
- Gradle 8.9 `build --no-configuration-cache`: PASS; 1,712 tests completed, 17 skipped, zero failures or errors.
- Dedicated-server GameTests: PASS; all 347 required tests passed.
- Development-client startup reached completed resource reload and block-atlas creation. No audited new-assets model, texture, localization, or render warnings were logged; the existing 44x44 dungeon-moongate mip warning and unrelated pre-existing resource warnings remain outside this milestone.

### Validation still requiring owner live review

- Creative-tab presentation, in-world visual quality, collision/interaction feel, representative multiplayer behavior, and placeholder acceptance remain on the Milestone 11/12 live checklist.

### Commit status

- Owner approval and explicit commit authorization were received.
- Committed Milestone 11 as `775806db` (`Audit new asset integrations`).

## Milestone 12 — Owner Live Validation and Project Closure

### Closure preparation

- Starting HEAD: `775806db`.
- Consolidated the automated evidence, required owner-observed checks, known exceptions, replacement-art backlog, and final closure gates in `MILESTONE_12_CLOSURE_REPORT.md`.
- Added a single ordered Milestone 12 live-validation pass to `LIVE_TEST_CHECKLIST.md`; the earlier milestone sections remain the detailed procedures for any failed spot check.
- Corrected the dimensional re-authoring matrix to record the loom work completed in Milestone 3.
- Preserved the project boundary: no merge or push is authorized, silk remains deferred, display cases remain decorative-only, and all temporary/placeholder art remains replaceable under its final IDs.

### Current status

- Automated readiness is green based on the approved Milestone 11 evidence: 1,712 tests with zero failures or errors, all 347 required GameTests passed, and client resource reload completed without audited new-assets warnings.
- The owner explicitly approved Milestone 12 and authorized its isolated commit on 2026-08-10. No separate live-session evidence or canonical combat-skill slug confirmation was supplied; those evidence gaps remain recorded in the closure report rather than being represented as tests that were run.
- The approval accepts the current temporary/placeholder presentation for this closure, consistent with the prior direction that purchased packs are temporary and will be replaced later.
- The project is owner-approved for closure with the documented evidence exceptions. No merge or push was performed, and future replacement art or textile-silk work is outside this milestone sequence.

### Commit status

- Owner approval and explicit final commit authorization were received.
- This closure record is included in the final isolated Milestone 12 commit.

## Post-Closure Asset Defect Pass

### Scope implemented

- Starting HEAD: `ec82f6b2` (`Close new assets project`).
- Addressed all ten owner-reported defects: shared 0.8 ibis scale (subsequently reduced to 0.64); exact 8:5 eating/idle weighting; faster wandering and local flock following; 1.2 baked-model scaling for six carts and the water well; camera-facing city-moongate portal layers; Hedge Bush bottom/middle/top stacking; active three-cell ladder climbing and landing; narrowly scoped Adventure scarecrow placement on community farm blocks; dress-form top UV/shading repair; and a new eight-variant `pool_of_blood` block from the formerly unassigned pack.
- Added deterministic `import_defect_assets.ps1` and updated the Milestone 3/5 importers so regeneration preserves every defect correction.
- Kept purchased art temporary, retained `moongate_block` registry compatibility, and left city-moongate teleport behavior intact. The later owner-authorized breaking rename deliberately replaced `moonglow_bush` with `hedge_bush`.
- Recorded root-cause analysis and the dimensional/re-authoring implications in `POST_CLOSURE_DEFECT_REPORT.md`.

### Validation status

- Focused defect, cross-system, and moongate tests: PASS.
- Dedicated-server GameTests: PASS; all 348 required tests passed.
- Development-client resource reload: PASS for the affected assets after correcting direct oversized JSON coordinates and remapping two inherited blood texture slots. The existing 44×44 moongate mip-level warning remains non-fatal and contains no missing texture.
- Final Gradle build: PASS; 1,718 tests completed with zero failures or errors and 17 skipped.

### Commit status

- Changes remain uncommitted for owner review.

## Follow-up Ibis, Hedge, and Flamingo Adjustments

### Scope implemented

- Confirmed and regression-guarded automatic Jhelom Ibis replenishment: Overworld only, three-area shared cap of 15, loaded candidate chunks only, bounded attempts, independent from the generic city-animal cap.
- Reduced both Ibis variants by another multiplicative 20%, from render scale `0.8` to `0.64`, and changed natural variant selection to 65% White / 35% Scarlet.
- Per explicit data-loss authorization, removed the `moonglow_bush` block/item/resource ID and registered only `hedge_bush`; no missing-mapping alias or migration shim was added.
- Added a mobile `flamingo` creature, spawn egg, creative exposure, empty loot table, and Britannia-spawner participation. After the missing entity assets were supplied, replaced the initial plushie fallback with the animated GeckoLib rig, synchronized/persistent Pink/Rose/White variants, uniform spawn-time color selection, and the supplied ambient audio converted from MP3 to OGG.
- Added `import_flamingo.ps1` with checksum coverage for all supplied geometry, animation, texture, and audio files; the importer removes the superseded baked plushie resources. Updated `import_defect_assets.ps1` so the breaking hedge resource paths regenerate deterministically.
- Corrected two owner-reported replacement defects. The supplied zero-height feet had coincident top/bottom faces; the importer now removes only the hidden undersides to eliminate z-fighting without changing any color texture. Britannia spawn-block saves now reset the prior selection's cooldown, passive `CREATURE` selections remain eligible in Peaceful, and spawned UUIDs are recorded only after `addFreshEntity` succeeds.

### Validation status

- Deterministic Flamingo importer: PASS against all ten supplied source checksums; generated geometry/animation JSON parses, all three imported textures are 64x64, the converted OGG is accepted by the client sound engine, and the superseded static block model/texture are absent.
- Focused replacement-Flamingo, post-closure defect, and cross-system contracts: PASS.
- Gradle 8.9 clean build: PASS; 1,723 tests completed with zero failures or errors and 17 skipped.
- Dedicated-server GameTests: PASS on the confirmation run; all 351 required tests passed. Coverage now includes a real configured Britannia spawn block adding and tracking exactly one selected Flamingo, in addition to allowlist, color synchronization, and persistence checks. The first run exposed the known intermittent, unrelated textile dropped-item proximity assertion; an unchanged immediate rerun passed all 351.
- Development-client resource reload: PASS for Flamingo through sound-engine startup and complete atlas creation. No Flamingo model, animation, texture, or sound warning/error was logged; unrelated pre-existing project resource warnings remain outside this follow-up.

### Commit status

- Changes remain uncommitted; no commit was requested for this follow-up pass.

## Targeted Corrective Asset Milestone

### Scope implemented

- Starting HEAD: `ffd2660e4cbb474717678cb84f18f2b6b18f879f` on branch `new-assets`.
- Spinning wheel: added a synchronized `active` blockstate that is set only after a successful server-side textile conversion and resets after 20 ticks. Active variants select the existing active model, including its named bobbin geometry, and a new `.mcmeta` plays all four frames of the 128x512 wheel strip. Rejected inputs remain idle.
- Scarecrow: removed four inverted arm/leg cubes that exactly overlapped ordinary cubes. An owner screenshot then exposed a separate remaining defect in the source export: the head, hat, body, and one side used ordinary baked shading while the opposite limbs explicitly disabled shading, producing near-black asymmetric faces. The final model disables ambient occlusion and per-element shading consistently, and the deterministic Milestone 3 importer preserves both corrections. The source texture atlas was not recolored or replaced.
- Merchant carts: retained the approved 1.2 baked-quad scale and added a `-0.4`-block Y correction. This moves the scaled source minimum from `-9.6` voxels to the authoritative structure's `-16`-voxel ground plane.
- Moongate: restored the intended `moongate_base` blockstate plus camera-facing `moongate_billboard` renderer split, then applied a uniform 1.2 scale to both layers. Teleport behavior and the existing registry ID are unchanged.
- Fountain: applied a centered 1.3 baked-quad scale, expanded placement to a centered 3x3x3 authoritative structure with root part 13, and authored matching nine-cell basin / three-layer central pillar collision. Existing placed fountains should be replaced because their saved part layout uses the former footprint.
- Added focused contracts and GameTests for active/idle wheel transitions, active texture metadata/model routing, scarecrow duplicate removal, cart/moongate/fountain transforms, and the fountain's 27-cell placement and collision.
- Updated `ASSET_IMPORT_MANIFEST.md` with the corrected dimensions, grounding, geometry diagnosis, animation behavior, and fountain footprint migration note.

### Files changed

- Runtime/client: `ClientModSetup.java`, `SpinningWheelBlock.java`, `ClientModelHandler.java`, `DecorativeScaledModel.java`, `MoongateBlockEntityRenderer.java`, and `BlockRegistry.java`.
- Assets: `blockstates/fountain.json`, `blockstates/moongate_block.json`, `blockstates/spinning_wheel.json`, `models/block/new_assets/scarecrow.json`, and `textures/block/new_assets/spinning_wheel_animated.png.mcmeta`.
- Deterministic import: `tools/new-assets/import_milestone3.ps1`.
- Tests: `NewAssetsTextileGameTests.java`, `NewAssetsUtilityGameTests.java`, `PostClosureAssetDefectContractTest.java`, `MoongateMilestoneTenContractTest.java`, and `TextileProcessingContractTest.java`.
- Documentation: `ASSET_IMPORT_MANIFEST.md` and this implementation log.

### Validation status

- Java compilation and final Gradle `assemble`: PASS; the corrected mod JAR packages successfully.
- Focused spinning-wheel and corrective-asset contract tests: PASS. The final milestone run covered 15 tests across `PostClosureAssetDefectContractTest`, `TextileProcessingContractTest`, and `MoongateMilestoneTenContractTest`, with zero failures or errors. After the owner screenshot follow-up, all nine `PostClosureAssetDefectContractTest` cases passed again, including explicit assertions that every retained scarecrow element is unshaded and model ambient occlusion is disabled.
- Full Gradle build diagnostic: reached 1,729 tests. It initially reported 17 failures: one stale moongate assertion was corrected in this milestone, while the other 16 are unrelated pre-existing banner texture/hash mismatches present at the starting HEAD. The broad build therefore remains non-green for reasons outside this corrective scope.
- Dedicated-server GameTests: PARTIAL on two unchanged runs; 351 of 352 required tests passed each time. The repeated failure was the unrelated existing training-dummy test `validhitsareperplayerratelimitedanddonotconsumedurability`. The new spinning-wheel and fountain GameTests passed.
- Development client: PASS through resource reload, model bake, atlas creation, and existing-world load. A second launch after the owner screenshot follow-up accepted the consistently unshaded scarecrow model without any scarecrow model/texture warning or error. Logs also confirm the cart, fountain, and moongate baked transforms were applied. The existing non-fatal 44x44 dungeon-moongate mip warning remains.
- Static checks: changed JSON and animation metadata were accepted by the contract suite and development-client resource loader; `git diff --check` passes apart from line-ending notices.

### Remaining owner review

- In-world visual acceptance is still required for the active spinning-wheel motion, scarecrow shading from representative angles, cart ground contact in all facings, moongate apparent size, and fountain scale/collision feel.
- Existing placed fountains should be broken and replaced to migrate from the former footprint to the new centered 3x3x3 part layout.
- The unrelated banner hash failures and intermittent training-dummy GameTest remain outside this asset-correction milestone.

### Commit status

- Changes remain uncommitted; awaiting explicit owner authorization.

## Corrective Feature — Display Case Rebuild

### Source and architecture analysis

- Starting HEAD: `ffd2660e4cbb474717678cb84f18f2b6b18f879f` on branch `new-assets` in `C:\projects\britannia\new-assets`; the pre-existing dirty worktree was preserved.
- Authoritative inputs: `display_case_redo.json` (connected source, 14 source elements, `0..16 × 0..28 × 0..16`), `display_case_independant_redo.json` (13 elements, inset `1..15 × 0..28 × 1..15`), and `display_case_corner_redo.bbmodel` (nine source elements, genuine west/south-open L frame, no groups/pivots/non-zero rotations).
- The existing `display_case` registry, decorative-only contract, transactional two-cell root/upper placement, single-drop teardown, four persisted root connection flags, server neighbor updates, item/creative registration, empty loot, axe tag, and cutout render registration remain sound and were preserved.
- The superseded visual layer was the Milestone 9 magenta/black base plus four conditional vanilla-glass side planes. Those assets could not express owner proportions or a true corner and were removed.

### Authoritative runtime assets

- `display_case_independent.json` is regenerated from the exact owner independent JSON.
- The connected source is deterministically decomposed into `display_case_base_connected.json` plus exact end, straight, and tee frames. A four-way interior renders the connected base without any upper frame, allowing six- and nine-case furniture grids to remain open inside.
- `display_case_frame_corner.json` is exported from the Blockbench element/outliner data as the canonical south/west-open corner frame; blockstate rotations `90`, `180`, and `270` cover the other corner directions without duplicated files.
- The owner independent source remains the complete item model and is also split into independent base/frame layers for block rendering. Base rotation is selected separately from topology by persisted player facing, preventing a corner rotation from turning its plank UVs against adjacent cases.
- The two connected owner sources each contain one exact-coincident opaque post. Runtime generation retains the first owner's geometry/UV entry and removes only its coplanar duplicate to prevent z-fighting.
- All three sources use the same 128×128 fully opaque `display_case.png`; the runtime texture is extracted from the corner `.bbmodel`. The owner files contain no glass cuboids or transparent texture pixels, so no non-authoritative panes were invented. Runtime models disable ambient occlusion and per-element directional shading so enclosed faces retain the authored texture instead of blackening.
- The inventory model now uses the owner independent case with explicit GUI/ground/fixed/hand transforms and the same requested 22-voxel final height.

### Logic, collision, and tests

- The root still recomputes connections server-side on neighbor changes. Independent, end, opposite-straight, true corner, three-neighbour tee, and four-neighbour interior states now each select the appropriate owner-derived frame. Player-selected `facing` remains persisted and unchanged by connection updates. Root connection flags are mirrored to the upper cell so that cell can render the cage with the correct topology and local light sample.
- The upper cage is scaled exactly 50% around global `y=16`: original bands `16..19`, `19..27`, and `27..28` become `16..17.5`, `17.5..21.5`, and `21.5..22`. Its generated block models use upper-local `y=0..6`, while the full inventory model remains global `y=0..22`.
- Collision now follows the same topology and half-height geometry dynamically: inset/full lower bases, perimeter-only upper rails/posts ending at global `y=22`, one exterior wall for a tee, and no upper collision for a four-way interior. `dynamicShape()` prevents upper cells from caching the independent shape instead of querying their live root.
- `DisplayCaseContractTest` validates topology routing, all four corner rotations, owner-derived component counts/texture, facing-driven bases, disabled shading, two-block limits, absence of coplanar duplicate bounds, and the scaled inventory transform.
- `NewAssetsDisplayCaseGameTests` additionally covers six-case tee openings, a nine-case partition-free center, topology-matched collision, straight removal/reconnection, 90-degree extension, all four corner directions, and facing preservation.
- `tools/new-assets/import_display_case_redo.ps1` records the exact misspelled owner filename and reproducibly generates the layered base/frame models plus embedded owner texture without changing raw inputs.

### Validation status

- Final Gradle `assemble`, Java compilation, and the focused `DisplayCaseContractTest`: PASS. The contract verifies the imported element counts and extents, exact texture contract, topology selectors, all four corner rotations, collision limits, duplicate removal, and inventory transform.
- Full Gradle unit-suite diagnostic: PARTIAL; 1,735 tests completed with 17 failures and 17 skipped. All display-case tests passed. The 17 failures are the same unrelated pre-existing banner hash/scaffold-preservation failures already present in this worktree.
- Dedicated-server GameTests: PARTIAL overall; 355 of 356 required tests passed. All three display-case GameTests passed, including independent/pair/run/corner transitions, a partition-free six-case tee and nine-case center, live topology collision, middle removal and restoration, 90-degree extension, all four corner directions, teardown, one-drop behavior, and facing preservation. The sole failure was the unrelated existing training-dummy test `validhitsareperplayerratelimitedanddonotconsumedurability` (`Adventure-mode supported strike was rejected`).
- Development client: PASS. The final resource reload logged zero `display_case` blockstate/model/texture errors or warnings. Nine cases placed through the ordinary creative item workflow with one shared facing rendered as a continuous 3×3 cabinet: perimeter-only half-height posts/rails, completely open internal cells, aligned floor planks, and no blackened interior or outer-edge frame faces. Earlier independent, pair, straight-run, and genuine L-corner reviews remain valid.
- Save/reload: PASS. The independently placed case, pair, straight run, and genuine L corner all remained correctly connected after the client and saved world were reopened; the corner itself was confirmed again in a third clean client session.
- Static resource checks: PASS; all display-case resource JSON parses, full models stay within global `y=0..22`, topology frames stay within upper-local `y=0..6`, owner-derived counts are base `1`, end `10`, straight `8`, corner `7`, and tee `4`, every rendered element has ambient/directional shading disabled, the owner texture remains 128×128 and fully opaque, and no superseded full connected/corner runtime model is referenced.
- Multiplayer: NOT RUN with two clients. Connection changes are still computed on the logical server and sent with ordinary block updates; the dedicated-server tests cover the same authoritative state transitions.
- Owner-source caveat: none of the three redo sources contains a glass cuboid or a transparent texture pixel. The runtime therefore faithfully presents the owner's open framed bays and does not reintroduce the obsolete vanilla-glass planes.

### Commit status

- Not committed; awaiting owner authorization.

## Market Stall Block Family

### Source analysis and import

- Starting HEAD: `317a9bd76f32a47b4176fdcc7a30af09025a3f36` on branch `new-assets` in `C:\projects\britannia\new-assets`; unrelated owner changes to the kettle, pewter mug, and medieval-market pot texture were preserved.
- Authoritative source: `C:\projects\britannia\raw fiels\models to import\medieval market\medieval_market_marketstall_red.bbmodel`, SHA-256 `F0CD41734254E5DE24FF5BB753F8FE1273D7B8D87DE11E60A9C0DC94FAF3B82E`. The read-only source was not changed.
- The Blockbench `java_block` project contains 12 ungrouped root cubes, one embedded 256x256 PNG, box UVs, and source X rotations of `22.5` and `-45` degrees with their pivots preserved. Its raw element bounds are `x=-16..32`, `y=-12..32`, `z=-11.5..16`; rotation-aware visible bounds are `48 x 44.815764 x 27.531494` voxels.
- The open/front side is local negative Z (north). The source's positive-Z cloth is the rear curtain. Runtime normalization keeps the exact width and height, compresses depth to the requested 16 voxels, and shifts the rotation-aware minimum Y to ground level. Final bounds are `x=-16..32`, `y=0..44.815764`, `z=0..16`, within the requested 3x1x3 occupied envelope.
- `tools/new-assets/import_market_stall.py` validates the source checksum and structure, exports one canonical geometry model, extracts the red PNG byte-for-byte, and creates mask-driven blue, green, and purple cloth variants. Only red-dominant pixels inside the three canopy/curtain UV regions change; wood, rope/support details, alpha, highlights, folds, and all non-fabric RGBA values remain exact.
- The three zero-thickness source cloth planes receive only a 0.01-voxel runtime thickness, with degenerate and hidden contact faces omitted to prevent z-fighting. Ambient occlusion is disabled for this imported model to avoid black coplanar/inside faces.

### Runtime architecture

- Four registry blocks/items (`market_stall_red`, `market_stall_blue`, `market_stall_green`, and `market_stall_purple`) share the existing `DecorativeMultiblockBlock` and `DecorativeMultiblockItem` implementation; no second multiblock framework or block entity was introduced.
- The authoritative lower-center root is part 1. Nine ordinary block states occupy local cells `x=-1..1`, `y=0..2`, `z=0`, rotating together for north/east/south/west. Placement preflights every cell and every lower support, then commits transactionally with rollback on failure.
- Breaking any root or child resolves the root, removes all nine cells, and drops one matching color item. Empty loot tables prevent vanilla duplicate drops.
- One canonical geometry model is inherited by four texture-substitution child models. Multipart blockstates render only part 1, rotated by facing. `MarketStallNormalizedModel` applies the measured ground/depth correction after model baking.
- Per-cell directional collision follows the rear curtain, side posts, counter rails, upper valance, and canopy. The lower-center customer area stays open, and overhead fabric does not fill the walkable volume. Occupancy, selection, collision, and teardown use the same facing transform.
- All four items are exposed together in the creative tab in Red, Blue, Green, Purple order, use wood properties/sound, cutout rendering, axe mineability, and established color-prefix localization.

### Validation status

- Deterministic importer and scoped resource validator: PASS; zero errors and zero warnings for `market_stall`.
- Java compilation and focused `MarketStallContractTest`: PASS. Contracts cover registry/resources, shared geometry, exact red extraction, selective recoloring, alpha/non-fabric preservation, footprint, directional routing, normalization, collision source, creative order, loot, and localization.
- Dedicated-server GameTests: PASS for both market-stall tests. All four colors placed through the ordinary item path in all four facings, occupied and resolved all nine cells, and tore down from root, left/right side, middle, and top children with exactly one matching item and no orphans. Blocked lower side, center/root, upper space, and uneven support rejected cleanly without item consumption or partial cells.
- Full dedicated-server suite: PARTIAL overall; 357 of 358 required tests passed on the final rerun. The sole failure was the unrelated existing training-dummy test `validhitsareperplayerratelimitedanddonotconsumedurability` (`Adventure-mode supported strike was rejected`).
- Development-client startup/resource bake: PASS through sound-engine startup and full block-atlas creation. All market-stall block/item variants were baked through `MarketStallNormalizedModel`, with no market-stall model, blockstate, texture, or missing-resource warning/error. Save/reload, two-client multiplayer, in-world visual inspection, and hands-on walking collision checks were not run. State uses ordinary synchronized/persisted block properties with no block entity, but those manual scenarios remain owner acceptance items.

### Commit status

- Not committed; awaiting owner authorization.
