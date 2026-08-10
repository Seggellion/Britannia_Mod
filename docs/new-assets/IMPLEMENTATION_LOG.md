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
