# Patch 18 Fertile Dirt Recovery — Milestone 0 Report

Date: 2026-08-24
Scope: Milestone 0 only — repository archaeology and design lock
Repository: `Britannia_Mod`
Baseline commit: `e57f3e3e2530fbbe5f90c14774d2f0f9fb19e595`

This report synthesizes six read-only investigation streams and a lead-agent integration review. No Milestone 1 production behavior was implemented.

## 1. Executive summary

The repository already has a canonical fertile-soil item, but its actual identity is `britannia_mod:fertilized_dirt`, not the proposed name `britannia_mod:fertile_dirt`. It is registered as `ItemRegistry.FERTILIZED_DIRT`, drops from Earth Elementals at a 35% chance for 1–2 items, and currently converts exact `minecraft:dirt` or a prepared community plot into a permanently reusable `britannia_mod:farming_block`. The new acquisition route must output this existing item; registering or renaming a second fertile-dirt item would violate the single-identity requirement and require an unnecessary migration.

The repository also already contains the systems needed for the feature:

- a generic, persisted, renewable `WildResource` scheduler and ledger;
- the exact custom shovel `britannia_mod:britannia_shovel`;
- a server-authoritative farming block entity with NBT persistence and client update packets;
- a fixed-main-hand/offhand interaction precedent in the banner dye-tub code;
- a reusable unlimited-water helper and the existing `britannia_mod:water_well`;
- explicit house build-right decisions and Adventure-mode resource handlers;
- JUnit and NeoForge GameTest coverage for the adjacent systems.

What does not exist is equally clear: there is no custom `britannia_mod:dirt`, dung block/item, requested bowl-chain item, finite fertility counter, dirt-gather cooldown, or bowl preparation service. There is a legacy `britannia_mod:empty_pewter_bowl`, but it has no container-return contract and is semantically distinct from the explicitly requested generic `empty_bowl`; it should remain untouched.

The recommended architecture is an extension of existing seams, not a parallel framework. Dung joins `WildResource`; dirt gathering is a high-priority server right-click transaction for the custom shovel and exact vanilla dirt; bowl preparation follows the dye-tub plan/revalidate/apply pattern; water-well filling extends the existing unlimited source helper; and five-use fertility lives on `FarmingBlockEntity` and decrements only inside authoritative harvest commits.

Most work is straightforward and localized. Milestone 7 is the only structurally broad change because annual, perennial, community, tall/trellis, fruit-tree, and flower-conversion paths must preserve one substrate-owned counter. Major risks are Adventure-mode routing, both-hand double execution, Creative/container duplication, the separation between ordinary crop and fruit-tree harvests, legacy infinite farm plots, and the current lack of a survival acquisition route for the Britannia shovel.

## 2. Existing fertile-dirt architecture

### Canonical identity

| Concern | Existing identity/evidence |
| --- | --- |
| Item holder | `ItemRegistry.FERTILIZED_DIRT` |
| Registry ID | `britannia_mod:fertilized_dirt` |
| Item class | `com.seggellion.britannia_mod.item.FertilizedDirtItem` |
| Registration | `src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java:972-973` |
| Display name | `item.britannia_mod.fertilized_dirt` → “Fertilized Dirt”, `assets/britannia_mod/lang/en_us.json:989` |
| Model | `assets/britannia_mod/models/item/fertilized_dirt.json`; currently uses `minecraft:block/dirt` |
| Creative exposure | `CreativeTabRegistry.java:125` |

There is no `britannia_mod:fertile_dirt`. All later milestones must use `ItemRegistry.FERTILIZED_DIRT` and retain `britannia_mod:fertilized_dirt` unless a separately approved migration project changes the public ID.

### Earth Elemental acquisition

`src/main/resources/data/britannia_mod/loot_table/entities/earth_elemental.json` has a fertile-dirt pool whose entry is `britannia_mod:fertilized_dirt`, uniform count 1–2, guarded by a `random_chance` of `0.35`. The entity ID is `britannia_mod:earth_elemental` at `EntityRegistry.java:659-663`. `ShameDungeonSpawner` spawns Earth Elementals on Shame floor 1 under its existing server-side tick/cap rules. The loot route must remain unchanged.

Repository-wide searches found no other survival acquisition route or recipe. Creative-tab access and the Earth Elemental spawn egg are development/Creative access, not additional survival production.

### Application routes

`FertilizedDirtItem.useOn` (`src/main/java/com/seggellion/britannia_mod/item/FertilizedDirtItem.java:29-69`) owns the ordinary route:

1. Exact `minecraft:dirt` is required (`block == Blocks.DIRT`).
2. On the logical server, the dirt is replaced by `britannia_mod:farming_block` with blockstate hydration 1.
3. One item is consumed unless the player has Creative `instabuild`.
4. The gravel-place sound plays and a Farming `TOOL` action is awarded.

It also delegates a prepared `britannia_mod:community_hoed_farm_block` to `CommunityHoedFarmBlock.fertilizeCommunityPlot`. A plain `community_farm_block` only displays the “hoe first” instruction.

Relevant substrate identities are:

- `britannia_mod:farming_block` — `BlockRegistry.java:261`, block item `ItemRegistry.java:929`, BE `britannia_mod:farming_block_be` at `BlockEntityRegistry.java:160`;
- `britannia_mod:community_farm_block` — `BlockRegistry.java:241`;
- `britannia_mod:community_hoed_farm_block` — `BlockRegistry.java:251`;
- `britannia_mod:community_farm_block_be` — `BlockEntityRegistry.java:188-194`;
- `britannia_mod:house_farm_plot` — `BlockRegistry.java:2451`;
- `britannia_mod:house_farm_plot_be` — `BlockEntityRegistry.java:174`.

Current application defects relevant to later milestones:

- both application routes set blockstate hydration but do not initialize the new BE hydration field, which starts at 0 and later wins reconciliation;
- ordinary conversion uses `level.setBlock`, so `FarmingBlock.setPlacedBy` does not assign an owner;
- ordinary application has no explicit `level.mayInteract` or `HouseBuildRights` validation;
- directly placed Creative/debug `farming_block` items bypass any fertile-dirt provenance;
- house farm plots do not accept or consume the item.

There is no finite-use state and no current exhaustion transition. Ordinary farming blocks are indefinitely reusable; community annuals return to their base after one harvest, while community perennials can regrow indefinitely.

## 3. Farming and harvest lifecycle

### Planting

The main interaction is `FarmingBlock.useItemOn` (`FarmingBlock.java:109`). Grapes have a special branch at lines 162–196. Other registered seeds converge in `tryPlantSeed` (`FarmingBlock.java:445-538`). Server validation checks plot ownership, Farming skill, existing crop state, support/trellis requirements, and tree space. A successful plant calls `FarmingBlockEntity.plant`, sets `HAS_SEEDS`, consumes the seed, and awards Farming skill.

`FarmingBlockEntity.plant` (`FarmingBlockEntity.java:146-166`) stores crop/variant identity, resets growth progress/stage/ticks/maturity, closes any community seed window, updates tall-crop presentation, and calls the normal changed-and-sync path. Planting is not a successful harvest and must not consume finite fertility.

### Growth and maturity

`FarmingBlock.randomTick` invokes `FarmingBlockEntity.tickGrowth` only while hydrated. `tickGrowth` (`FarmingBlockEntity.java:477-526`) is server-side and evaluates climate, altitude, nutrients, hydration, and support before advancing progress and visual age. It sets `mature=true` only when progress is complete and the crop is at a mature age. Growth does not consume fertility.

Fruit-tree morphology is separately owned by `OrangeTreeRootBlockEntity`; the stable farming substrate remains below it. Fruit becomes ripe at root growth step 10.

### Successful ordinary/tall/trellis harvest

All non-tree crop endpoints converge in:

`FarmingBlock.tryHarvestCrop(FarmingBlockEntity, BlockState, Level, BlockPos, Player, ItemStack, InteractionHand, String)` at `FarmingBlock.java:540`.

Before committing, it rejects no crop, immature crops, tree crops, missing support, wrong tools, and incomplete tall structures. Its server-side success transaction then computes yield/quality, creates the harvest, produces optional straw/seed, damages the required tool, selects regrowth/reset behavior, plays feedback, and awards Farming `HARVEST`.

Callers include direct farming-block use, empty-hand harvest, `TrellisBlock`, and `harvestTallCropFromSegment`, which is used by grape arbors and corn/tall segments. This convergence point is the authoritative successful-harvest event for non-tree crops. It is a method-level transaction, not a posted NeoForge event.

The future decrement belongs after output/tool success and immediately before the existing post-harvest lifecycle branch at `FarmingBlock.java:616`. At that point every refusal has returned, the fifth harvest can still grant its output, and there is only one path to decrement.

### Current post-harvest behavior

- Perennial/trellis/banana/grape crops call `regrowAfterHarvest`, retain seeds, and regress below maturity.
- Assigned house crops also regrow, including normally annual crops.
- Ordinary annual crops clear crop/seed state and retain the same reusable farming block.
- Community annual crops immediately return to `community_farm_block` through `resetAnnualCropState` (`FarmingBlock.java:735-745`).
- Community perennials currently stay fertile forever.

For finite tracked community soil, harvests 1–4 must instead clear an annual crop, retain `farming_block`, and reopen the existing 1,200-tick seed window. Harvest 5 returns it to `community_farm_block`.

### Fruit-tree harvest

Fruit trees bypass `tryHarvestCrop`. `OrangeFruitBlock.useItemOn`/`dropFruitFromTree` (`OrangeFruitBlock.java:50-91`) validates ripe fruit, scissors, and house ownership; grants fruit; changes the fruit block to leaves; records the root harvest; damages scissors; and awards Farming `HARVEST`. `OrangeTreeRootBlockEntity.onFruitHarvested` later regresses the canopy after 80% of planned fruit is taken.

The repository’s semantic harvest unit is therefore one accepted ripe-fruit-block click, not a complete canopy cycle. Milestone 7 should decrement once after that per-fruit transaction succeeds. The fifth click grants fruit and then performs exhaustion cleanup. This is deliberately locked from repository behavior; if product intent was five whole fruiting cycles, that is a different rule.

### Flowers

Flowers use `FlowerInteractionService.harvest` and `FlowerBlockEntity.harvestAndReset`, not the crop path. The requirement says crop harvests, so flower harvests do not decrement fertility. If finite soil converts to a flower BE, however, the counter must travel through `FlowerSoilSnapshot`/`FlowerPersistentState` and be restored when soil is recreated; otherwise conversion would silently erase it.

### State, persistence, sync, and exhaustion

`FarmingBlock.FERTILIZER` is an agronomy blockstate value (none/manure/chemical), not fertile-dirt provenance or remaining use count. The correct owner is `FarmingBlockEntity`, which already owns hydration, nutrients, planted crop, growth, community provenance/window, ownership, and rendering state.

Its `saveAdditional`/`loadAdditional` methods (`FarmingBlockEntity.java:581-636`) persist state. `setChangedAndSync`, `getUpdateTag`, and `getUpdatePacket` (`FarmingBlockEntity.java:723-745`) provide authoritative save and client synchronization. `HouseFarmPlotBlockEntity extends FlowerBlockEntity extends FarmingBlockEntity`, so this state owner already spans the farming hierarchy.

Milestone 7 should add a tracked/untracked marker plus `remainingFertileHarvests` clamped to 0–5. A fresh application sets tracked/5. Missing legacy NBT loads as untracked/unlimited to preserve existing worlds, Creative/debug block placement, and house plots that never consumed fertilized dirt.

Existing exhausted states should be reused:

- ordinary tracked farming soil → clean crop/tree/tall presentation, then `minecraft:dirt`;
- tracked community soil → clean presentation, then existing `britannia_mod:community_farm_block`;
- house plot → remain structurally intact and untracked/unlimited because the item cannot currently initialize it.

## 4. WildResource architecture

### Registration and policy model

`WildResourceEntry` is the generic policy record: ID, weight, per-chunk cap, tuning, candidate generator, ordered placement predicates, existing-node validator, placement, harvest, and loot. `WildResourceRegistry` provides deterministic registration/selection; `WildResources` owns the singleton registry. Entries are hard-coded in `WildResourceEntries.java`, bootstrapped by `BritanniaMod.java:155`; this is not datapack worldgen.

Current IDs are:

- `britannia_mod:sulphurous_ash_patch`;
- `britannia_mod:black_lipped_oyster`.

Sulphurous Ash is the closest renewable surface-resource precedent: low non-colliding block, any-tool Adventure harvest, exact-one plain item drop, scheduler/ledger/cooldown. Black-lipped Oyster supplies the precedent for a strict direct-substrate predicate.

### Scheduler, caps, and persistence

`WildResourceManager` handles loaded server chunks only. It listens to chunk load/unload, level unload, and server post-ticks; never force-loads chunks; round-robins dimensions; and processes bounded work. A due chunk performs at most one weighted entry attempt.

`WildResourceSpawnScheduler.attempt` enforces per-resource per-chunk cap, probe budget, same-type spacing, and the entry predicates before placement and node recording. Timers use server game time.

`WildResourceSavedData` persists per dimension under `britannia_wild_resources`, schema 1. It stores schedules by chunk/resource/absolute next tick and nodes by resource ID/position/placed tick. The data is generic, so dung requires no schema bump. Chunk unload does not erase schedules or nodes.

Reconciliation distinguishes `VALID`, `DEFER`, `OWNED_INVALID`, and `MISSING_OR_REPLACED`. Invalid owned nodes are removed without loot, removed from the ledger, and placed on cooldown. Reconciliation is queued by chunk-load activity; it is not a continuous neighbor-survival loop.

### Placement and support

`WildResourcePlacementRules.surfaceCandidate` uses `MOTION_BLOCKING_NO_LEAVES` and targets the space above the surface. `isSafeTarget` requires a replaceable dry target with no block entity.

Dung should add one closed predicate beside `isAshSupport`/`isOysterSubstrate`:

```java
support.is(Blocks.DIRT) || support.is(Blocks.COARSE_DIRT)
```

Do not reuse `isNaturalAshSupport`; it also accepts grass, stone, sand, gravel, Nether blocks, and more. Do not use an extensible tag for this product rule. The entry validator should treat wrong block identity as `MISSING_OR_REPLACED`, invalid exact support as `OWNED_INVALID`, otherwise `VALID`.

The heightmap/direct-support combination means natural dung appears only above exposed dirt/coarse dirt. Grass-covered terrain is intentionally ineligible. Live density QA is required because eligible surface area may be sparse.

### Dung lifecycle and drops

Recommended identities:

- block registry: `britannia_mod:dung`;
- item registry: `britannia_mod:dung` in the separate item registry;
- `DungBlock`: low, non-colliding, implements `AdventureHarvestableBlock` for any tool;
- `ItemRegistry.DUNG`: plain `Item`, not a `BlockItem`.

No player placement path should exist. A BlockItem would create untracked nodes that caps/spacing ignore, Adventure harvesting refuses, and invalid-support reconciliation never adopts. Administrative `/setblock` placement remains outside the gameplay lifecycle.

For natural balance, start with the closest ash precedent: weight 1, cap 2/chunk, attempt 3–6 minutes, post-removal cooldown 20–40 minutes, 4 probes, spacing 8. Adding a third weight-1 entry slightly competes with ash/oyster when multiple entries are due, so cadence regression tests are required.

Adventure harvesting uses `WildResourceHarvestService.harvestOne`: revalidate node/entry/expected block, set air, remove the node, schedule cooldown, pop exactly one item, and post one event. Ordinary Survival breaking uses the one-roll block loot table; the LOWEST break listener only removes the ledger/schedules cooldown and does not create a second drop. Creative ordinary removal yields no item. Unsupported tracked dung removed by reconciliation yields no item/event.

The current system only revalidates support on load-triggered reconciliation. That is the locked M2 precedent. Do not add a bare `canSurvive/updateShape` removal that can leave a stale persisted node. If immediate support removal is later required, it must call one atomic dung invalidation operation that removes world block and ledger together and schedules cooldown.

## 5. Custom shovel architecture

The exact tool is `ToolRegistry.SHOVEL`, registry ID `britannia_mod:britannia_shovel`, registered at `ToolRegistry.java:27-30` as `QualityShovelItem` with stack size 1. `ToolRegistry.createShovel` attaches material/quality data. Assets are `models/item/britannia_shovel.json` and `item.britannia_mod.britannia_shovel` in `en_us.json`.

`QualityShovelItem` extends vanilla `ShovelItem`. It currently has no `useOn` override. It stores `Quality` and `Material` in `DataComponents.CUSTOM_DATA`, reports correct drops for `BlockTags.MINEABLE_WITH_SHOVEL`, and changes destroy speed. The registered tier remains iron regardless of attached material metadata.

The shovel is already the exact allowed item in:

- `britannia_mod:root_crop_shovels`;
- `britannia_mod:clay_shovels`;
- `britannia_mod:silica_shovels`.

Successful root-crop, clay, and silica work charges one durability. Dirt gathering should likewise charge exactly one durability only after a successful output.

Repository search found no survival recipe, blacksmith output, vendor/trade, or other production route for this tool. The creative tab and test factory create it, while the vendor rollout maps the UO shovel to `minecraft:iron_shovel`. This is not an M1 blocker, but it is a full-loop acceptance blocker that the milestone plan currently does not assign.

## 6. Mining boundary

Dirt is absent from `data/britannia_mod/mining/mineables.json`, `ManagedDeposits`, and `data/britannia_mod/resources/resources.json`. Existing tests assert ordinary dirt is not a managed deposit. Gathering must not add it to those catalogues.

M3 must not call or enter:

- `MineableCatalog` / `Mineables.resolve`;
- `MiningBreakGate.evaluate`;
- `MiningGateHandler`;
- `CustomBlockBreakHandler`;
- `MiningSkill.awardForBreak`;
- `MiningExtractionTool`;
- `MiningProvenance` or `BrokenBlockTracker`;
- `ManagedDeposits.resolve`;
- `ManagedDepositExtraction.extract`;
- `ManagedExtractionPolicy` as an extraction engine (its actor classifier may be reused);
- `Resources.resolve`;
- `BlockEvent.BreakEvent`, vanilla destruction, loot tables, or deposit regeneration.

Clay/silica are left-click/break managed deposits with hours-long regeneration. Dirt gathering is a right-click interaction on an unchanged block. Keeping those event families separate is the strongest proof that no Mining skill, XP, provenance, deposit debt, or resource node is touched.

## 7. Cooldown architecture

The strongest player-scoped precedent is `TrainingDummyService`: a namespaced next-tick value in `ServerPlayer.getPersistentData()` keyed against `player.server.overworld().getGameTime()`. `MiningBreakGate` and `CarpetTeleporterBlockEntity` provide persistent feedback-throttle precedents.

Locked dirt-gather cooldown:

- duration: **1,200 server ticks / 60 seconds**;
- key: `britannia_mod:dirt_gather_next_tick`;
- owner: `ServerPlayer` persistent data, not an item stack;
- clock: persisted overworld game time;
- commit order: validate first, write next-allowed tick before output, then grant/drop item and damage tool;
- feedback: yellow action-bar remaining seconds, rounded up, throttled to once per 40 ticks; green action-bar success;
- rewind guard: reset/fail open if stored-next minus current exceeds the configured cooldown, protecting clock resets/corrupt values.

This is meaningful without dominating farming: one dirt ingredient produces one fertilized dirt that supports five successful harvests, amortizing the gather delay to 12 seconds per harvest. Crop growth is already measured in minutes/random ticks, and WildResource dung has a much longer ambient respawn cadence.

Player persistence defeats hotbar changes, hand swaps, a second shovel, reconnects on the same server state, and repeated packets. An item cooldown would be item-type scoped, could suppress unrelated shovel use, and has no demonstrated relog persistence; item custom data and static UUID maps have direct bypass/lifecycle weaknesses. Death/clone persistence must be tested, and copied explicitly if vanilla/NeoForge clone behavior does not retain the tag.

## 8. Bowl and inventory interaction architecture

### Existing overlap

The repository has `britannia_mod:empty_pewter_bowl` (`ItemRegistry.java:327-328`) and ten filled food bowls (`wooden_bowl_of_*`, `pewter_bowl_of_*`, lines 291–319). The empty pewter bowl is a plain default-stack item. Filled bowls are `WeightedCookedFoodItem`; their `finishUsingItem` delegates to `super` and adds nutrition, with no `usingConvertsTo`, crafting remainder, or return of `empty_pewter_bowl`.

Do not silently alias or repurpose that legacy material-specific item. The product and milestone plan explicitly request a separate generic custom `britannia_mod:empty_bowl`. Register exactly:

- `britannia_mod:empty_bowl`;
- `britannia_mod:bowl_of_dirt`;
- `britannia_mod:bowl_of_fertile_dirt`;
- `britannia_mod:bowl_of_water`.

`minecraft:bowl` and `britannia_mod:empty_pewter_bowl` remain invalid inputs.

### Fixed-hand precedent and transaction shape

`DyeTubItem.use` plus `DyeTubLoadingService.plan/apply` is the closest precedent:

- offhand callback returns PASS;
- client main-hand callback returns sided success without mutation;
- server reads the offhand and creates a validation plan;
- apply revalidates live item identity/state immediately before mutation;
- exactly one ingredient is consumed on success;
- a recognized main-hand interaction consumes the action, preventing the other hand from independently completing it.

Create a small bowl preparation service with the same pure-plan/server-apply split. The item `use` path should be location-free generic item use after higher-priority block/entity interactions pass, not a block-break or global recipe event. Require the exact main/offhand order; reversed hands pass/do nothing. An invalid tuple returns PASS, preserving normal offhand behavior. A client-accepted valid tuple returns sided success, and a stale server plan consumes that already accepted action without mutation, so the other hand cannot reinterpret it.

On the client, never mutate inventory. On the server main thread:

1. validate hand, actor, exact two live stacks, and output identities;
2. revalidate immediately before commit;
3. consume exact inputs;
4. place outputs in freed hands/slots where practical;
5. call `Inventory.add` for remaining outputs and drop only the mutated remainder if insertion returns false;
6. play feedback once and return sided success (`SUCCESS` client / `CONSUME` server).

No custom network payload is necessary. Vanilla use packets already identify the held hand, and server-thread serialization plus live-stack revalidation makes repeated packets single-winner.

### Creative and container rules

Creative must not be a special duplication branch for these value/container transformations. Consume the named bowl-chain inputs and grant the exact outputs in Creative as in Survival. Creative users can obtain inputs/outputs directly, while a non-consuming final interaction would preserve two filled bowls and also return two empty bowls on every click. Avoid the three-argument `ItemUtils.createFilledResult` default Creative behavior for this chain.

All new ingredient/bowl variants should use normal stack size 64. Full inventory is not a refusal: consumed inputs usually create space, and any exact remainder is safely dropped rather than deleted. Tests must use stacked inputs, near/full inventories, both hands, repeated calls, and two players.

Final conservation is locked to:

`bowl_of_fertile_dirt + bowl_of_water -> fertilized_dirt + 2 x empty_bowl`

Two reusable custom bowls enter; neither disappears. Existing pitcher/container transformations conserve the entered container, and there is no stronger repository precedent for destroying one.

## 9. Water and well architecture

`britannia_mod:water_well` is `WaterWellBlock` (`BlockRegistry.java:2659`) with item `ItemRegistry.WATER_WELL_ITEM` (`ItemRegistry.java:2430`). It is a decorative multiblock with no water block entity, charge, refill, or finite state.

`WaterWellBlock.useItemOn` delegates valid parts to `WaterSourceInteraction.fillFromSource`. That helper is server-authoritative, supports the watering can, pitcher, and vanilla bucket, treats the source as unlimited, uses `ItemUtils.createFilledResult` for buckets, and plays `SoundEvents.BUCKET_FILL`. JUnit `MilestoneFiveUtilityTest` and `NewAssetsUtilityGameTests` cover these existing containers.

M5 should extend/delegate this helper for the new empty bowl but use the bowl-chain exact-consumption policy rather than the helper’s default Creative bucket behavior. The well remains unchanged and unlimited. Resolve a clicked multiblock part to its anchor for permission/reach checks and feedback position.

For world water, follow the existing `WateringCanItem.use` raycast precedent:

```java
getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY)
```

Then defensively require `fluidState.is(FluidTags.WATER)` and `fluidState.isSource()`. Do not remove or alter the source. A flowing-water hit is invalid. Use server-only inventory mutation and play the existing fill feedback once; `BUCKET_FILL` is the well helper’s established sound and no additional animation is required.

Priority for `empty_bowl.use`: first perform the source-only raycast. If an actual water source is explicitly targeted, water filling wins even when custom dirt is in the offhand. Otherwise evaluate the requested dry preparation tuple. Clicking a well is owned earlier by `WaterWellBlock` and delegates to the same bowl fill service.

## 10. Protection, Adventure mode, and multiplayer authority

### Exact APIs

`HouseBuildRights.evaluateBreak(Level, BlockPos, Player)` (`HouseBuildRights.java:100-112`) returns outside-house, owner-allowed, foreign-owner denied, foundation denied, or house-infrastructure denied. `evaluatePlace` is ownership-only. `StructureProtectionHandler` applies these to ordinary break/place events, but it has Creative and custom-tool early exits. Any economy interaction that mutates/grants before those later events must call authority itself.

`level.mayInteract(player, pos)` remains the vanilla/world-border/spawn-protection check. `ManagedDepositExtraction` is the ordering precedent: actor → world/house permission → tool → record/world transaction → output → durability.

### Dirt gathering in Adventure: resolved event boundary

Lead review of the decompiled Minecraft 1.21.1 sources resolved a conflict between two possible designs. `ItemStack.onItemUse` (`build/neoform/.../net/minecraft/world/item/ItemStack.java:370-383`) returns PASS without calling the item callback when `player.getAbilities().mayBuild` is false and the stack lacks an Adventure placement predicate. Therefore `QualityShovelItem.useOn` alone is not a reliable Adventure endpoint.

M3 must use a server-side `PlayerInteractEvent.RightClickBlock` handler at HIGH/HIGHEST priority, MAIN_HAND only. For exact `ToolRegistry.SHOVEL` + exact `Blocks.DIRT`, it must validate real actor, non-Creative/non-Spectator policy, reach/`mayInteract`, `HouseBuildRights.evaluateBreak`, cooldown, and live inventory before output. Cancel with a successful result whenever the feature owns the exact dirt gesture, including cooldown/permission denial, so inherited shovel flattening cannot replace dirt with a path.

Add a narrow `QualityShovelItem.useOn` interception for exact dirt on the client only (or an equivalent client event result) to suppress Survival client prediction of vanilla path creation. Server authority remains the event/service; the item override must not become a second grant path.

### Dung breaking

Adventure left-click delivery is enabled client-side by `ClientAdventureBreakGateMixin` for `AdventureHarvestableBlock`. The server authority is `WildResourceInteractionHandler.onLeftClick`, which resolves the saved node/entry and calls the harvest strategy. The existing service lacks house, `mayInteract`, and fake-player checks.

Before a dung/WildResource strategy removes the block, validate actor, reach, `level.mayInteract`, and `HouseBuildRights.evaluateBreak`. The authorization boundary should return a result that distinguishes “not a resource” from recognized-but-denied outcomes. The Adventure handler must cancel every recognized dung outcome, including a denial; allowing a protected node to fall through is unsafe for a player who happens to have `mayBuild`. Add a HIGH/HIGHEST tracked-WildResource break guard for ordinary non-Adventure breaks so a later LOWEST bookkeeping listener and vanilla loot cannot become a foreign-house or fake-player bypass. Creative administrative removal remains no-drop; Survival/Adventure successful player harvesting gives exactly one. The saved-node + expected-block recheck and server-thread removal make simultaneous clicks single-winner.

### Well and inventory-only preparation

Current wells are unlimited and allow existing supported containers without a house ownership check. To preserve the required existing well behavior, new bowl filling should at minimum require `level.mayInteract` at the resolved anchor but should not reinterpret non-mutating water access as block breaking. If private/foreign-house well use is intended, add a named ownership-only `HouseBuildRights.evaluateUse` policy and apply it consistently to all well containers in a separately approved compatibility change; do not misuse foundation-aware `evaluateBreak` only for bowls.

Dry/final bowl preparation is inventory-only generic item use after higher-priority block/entity interactions pass and has no location permission. It works in air and while targeting an inert/PASS-through block; a handled block interaction wins first. It still requires a real server player, fixed main hand, live-stack revalidation, and exact atomic consumption/output. Repeated packets fail after the first stack mutation. Different players act on independent inventories; two players gathering from the same inexhaustible dirt may each succeed if their player cooldowns permit, which is the intended player-scoped rule.

## 11. Registry, data, and assets audit

### Identity audit

Exact searches found no registrations, generated data, tags, recipes, models, textures, loot, fixtures, or documentation for:

- `britannia_mod:dirt`;
- `britannia_mod:dung`;
- `britannia_mod:empty_bowl`;
- `britannia_mod:bowl_of_dirt`;
- `britannia_mod:bowl_of_fertile_dirt`;
- `britannia_mod:bowl_of_water`;
- `britannia_mod:fertile_dirt`.

The clean new commodity ID is `britannia_mod:dirt`. Vanilla `minecraft:dirt` is in another namespace and is not a registry collision. The nearby `quarter_dirt_block`, `half_dirt_block`, and `three_quarter_dirt_block` are placeable fractional construction blocks, not loose commodities.

The resource pack overrides vanilla dirt/coarse-dirt blockstates with eight randomized Britannia world models. This is only rendering and does not reserve the item ID. It also means screenshots cannot reliably distinguish Dirt and Coarse Dirt; tests must assert registry/blockstate identity.

The only “manure” occurrence is a comment describing `FarmingBlock.FERTILIZER` value 1. There is no manure item. Dung must not be added as a direct `FarmingSoilCare` fertilizer in these milestones; its approved role is the bowl ingredient.

### Stacking and placement locks

- custom `dirt`: plain item, stack 64, not placeable;
- `empty_bowl` and three filled variants: custom/plain items as needed, stack 64, not placeable;
- dropped `dung`: plain item, stack 64, not placeable;
- world `dung`: separate block with no BlockItem;
- existing `fertilized_dirt`: current default stack behavior, application item only.

Block and item registries can legally share `britannia_mod:dung`; tests must not assume `BlockRegistry.DUNG.get().asItem()` is the commodity.

### Existing relevant assets

- `fertilized_dirt` lang/model; model uses the vanilla dirt texture;
- `empty_pewter_bowl` lang/model/16×16 texture;
- wooden/pewter filled food-bowl lang/models/textures;
- water-well blockstate, block model, item model, texture, and lang;
- Britannia shovel model/lang/material tint paths;
- complete Sulphurous Ash and Black-lipped Oyster WildResource block/item/loot assets.

### Missing M1 resource surface

```text
assets/britannia_mod/lang/en_us.json
assets/britannia_mod/models/item/dirt.json
assets/britannia_mod/models/item/dung.json
assets/britannia_mod/models/item/empty_bowl.json
assets/britannia_mod/models/item/bowl_of_dirt.json
assets/britannia_mod/models/item/bowl_of_fertile_dirt.json
assets/britannia_mod/models/item/bowl_of_water.json

assets/britannia_mod/blockstates/dung.json
assets/britannia_mod/models/block/dung.json
assets/britannia_mod/textures/block/wild_resource/dung.png
data/britannia_mod/loot_table/blocks/dung.json
```

Distinct item textures are strongly preferred for custom dirt, dung, and all bowl states. A separate dung item texture is optional if its generated item model deliberately reuses the block pile texture. No recipe JSON is appropriate.

The project has no active data-generator provider despite including `src/generated/resources`; these assets are currently hand-authored. Use the repository’s singular 1.21 data directories (`loot_table`, `recipe`, `tags/block`, `tags/item`) and do not copy legacy plural-directory mistakes.

Final art is not an architecture blocker. Functional models can follow the existing generated-item and ash-pile conventions, but M1 cannot ship missing model JSON and M8 should not call temporary indistinguishable artwork final.

## 12. Test architecture and baseline

### Best existing patterns to extend

| Area | Existing tests/patterns |
| --- | --- |
| WildResource policy/assets | `SulphurousAshPolicyTest`, `BlackLippedOysterPlacementTest`, `WildResourceAssetContractTest` |
| WildResource scheduling/persistence | `WildResourceSpawnSchedulerTest`, `WildResourceSavedDataTest`, `WildResourceReconciliationTest`, `WildResourceRuntimeSafetyTest` |
| WildResource event/drop | `WildResourceHarvestEventContractTest`, managed-vegetation GameTests |
| Fixed main/offhand atomic plan | `DyeTubLoadingServiceTest`, `DyeTubItem` |
| Full-inventory exchange/two players | `NewAssetsTextileGameTests`, `GrabbyMultiplayerGameTests`, `GrabbyPickupTransaction` |
| Real right-click routing | `GrabbyInteractionPathGameTests`, `HouseOwnerBuildRightsGameTests` |
| Cooldown/player isolation | `NewAssetsTrainingDummyGameTests` |
| Mining/non-regression | `ManagedClayDepositTest`, `ManagedExtractionPolicyTest`, `MiningBreakGateTest`, `MiningSkillTest`, `MineableCatalog*Test`, mining/deposit GameTests |
| House protection | `ManagedDepositHouseProtectionGameTests`, `HouseOwnerBuildRightsGameTests` |
| Water/well helper | `MilestoneFiveUtilityTest`, `NewAssetsUtilityGameTests`, `NewAssetsCrossSystemAuditTest` |
| Farming lifecycle | `GrapeArborGameTests`, `HouseFarmPlotIntegrationTest`, `FlowerRegressionTest`, crop registry tests |
| Farming save compatibility | `FlowerSaveCompatibilityTest`, existing `FarmingBlockEntity` NBT tests/patterns |
| Registry/serialization | `CreativeTabRegistryIntegrityGameTests`, `CityProvenanceGameTests`, `DataPackDirectoryConventionTest` |
| Loot directories | `LootTableDirectoryTest` |

Coverage gaps are material: there is no direct test of `FertilizedDirtItem`, Earth Elemental fertile loot identity, ordinary/community application, fruit-tree fertility, a real clicked water well, or a persisted player cooldown across reconnect/death.

Recommended later test classes:

- `src/test/java/com/seggellion/britannia_mod/farming/FertilizedDirtLifecycleTest.java`;
- `.../farming/FertilizedDirtSaveCompatibilityTest.java`;
- `.../farming/FertilizedDirtRegistryLootContractTest.java`;
- `.../wildresource/DungWildResourcePolicyTest.java`;
- `.../patch18/FertileDirtRegistryAssetContractTest.java`;
- `src/main/java/com/seggellion/britannia_mod/gametest/FertileDirtRecoveryGameTests.java`;
- `.../gametest/FertilizedDirtLifecycleGameTests.java`.

No new GameTest structure is required; the existing empty template is sufficient.

### Commands and exact M0 results

JUnit baseline:

```powershell
.\gradlew.bat test --rerun-tasks --console=plain
```

Result: BUILD SUCCESSFUL in 5m26s. XML totals: 389 suites, 3,097 tests run, 3,080 passed, 0 failures, 0 errors, 17 skipped.

Focused WildResource baseline:

```powershell
.\gradlew.bat test --tests "com.seggellion.britannia_mod.wildresource.*"
```

Result: BUILD SUCCESSFUL; 38 run, 38 passed, 0 failed, 0 skipped.

Focused bowl/well architecture baseline:

```powershell
.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.DyeTubLoadingServiceTest" --tests "com.seggellion.britannia_mod.structure.multiblock.MilestoneFiveUtilityTest" --tests "com.seggellion.britannia_mod.NewAssetsCrossSystemAuditTest"
```

Result: BUILD SUCCESSFUL; 19 run, 19 passed, 0 failed.

The first GameTest launch with configuration cache enabled failed before running tests with Gradle’s cached `ProviderBackedFileCollectionSpec ... null array` error. Rerunning with the repository-safe cache bypass executed the suite:

```powershell
.\gradlew.bat --no-configuration-cache runGameTestServer --console=plain
```

GameTests: 784 run, 783 passed, 1 failed, 0 reported skipped. Failure:

`authoredgeometrysurvivesreconstructionandmaterializes`

The failure is **pre-existing/environmental**, not new: the 2026-08-23 prior `run/gametest/logs/latest.log` already reports the same required failure, and the fresh run fails because persisted GameTest-world data maps the same deterministic managed-deposit ID to a different coordinate (`deposit_identity_conflict`). No fertile-dirt production code existed or changed between those observations. The unauthenticated Rails/config warnings during GameTests are expected environmental noise and did not prevent 783 tests from passing.

Required totals format:

```text
JUnit:
run: 3097
passed: 3080
failed: 0
skipped: 17

GameTests:
run: 784
passed: 783
failed: 1 (pre-existing/environmental)
```

## 13. Locked M0 decisions

### A. Final bowl conservation

**Verdict: return two custom empty bowls.**

Survival and Creative both perform the exact conversion:

`bowl_of_fertile_dirt + bowl_of_water -> ItemRegistry.FERTILIZED_DIRT + 2 x empty_bowl`

Two reusable containers enter, and no repository precedent justifies deleting one. The canonical returned container is the new `britannia_mod:empty_bowl`, not vanilla bowl or legacy empty pewter bowl.

### B. Dirt-gather cooldown

**Verdict: 1,200 ticks / 60 seconds per player.**

Store `britannia_mod:dirt_gather_next_tick` in `ServerPlayer.getPersistentData()` against overworld server game time. Enforce and claim it server-side before output. Main-hand/tool changes and reconnects do not bypass it. Feedback throttle: 40 ticks.

### C. Canonical dirt ingredient

**Verdict: register a new plain item `britannia_mod:dirt`.**

No custom loose dirt commodity exists. Fractional dirt construction blocks and `minecraft:dirt` are not substitutes. Use default stack size 64 and no placement behavior.

### D. Gatherable terrain

**Verdict: exact `minecraft:dirt` only.**

Do not accept coarse dirt, grass, farmland, podzol, mud, paths, tags, or custom fertile soil. Dung support remains independently locked to dirt + coarse dirt. The inherited shovel may first flatten coarse dirt into normal dirt; a later click on the now-exact dirt is a distinct vanilla-then-gather sequence, not direct coarse-dirt acceptance.

### E. Five-harvest state ownership

**Verdict: durable state on `FarmingBlockEntity`.**

Add tracked/untracked provenance plus `remainingFertileHarvests` 0–5, persisted/synced through existing BE methods. Initialize 5 in both actual item application routes. Missing legacy state is untracked/unlimited.

Successful decrement points:

- non-tree crops: the server success transaction in `FarmingBlock.tryHarvestCrop`, after output/tool success and before regrowth/reset;
- fruit trees: one accepted ripe `OrangeFruitBlock` harvest after output success;
- flowers: no decrement.

Exhaustion after the fifth granted harvest: ordinary soil → `minecraft:dirt`; community soil → `community_farm_block`; house plots remain untracked because no existing item application initializes them.

### Additional locked behavior

- Requested bowl item IDs are exact and separate from `empty_pewter_bowl`.
- New commodities and bowls stack to 64.
- Dung is not player-placeable and has no BlockItem.
- Dirt gathering charges one shovel durability only on successful grant.
- Well water is unlimited and no well state is consumed.
- Source water is not removed; flowing water is rejected.
- Source/well targeting has priority over offhand dirt when an empty bowl explicitly targets water.
- Bowl preparation uses generic `Item.use` after higher-priority block/entity use passes; it is not artificially restricted to raycast MISS.
- Use results follow `sidedSuccess`: client SUCCESS, server CONSUME. Invalid tuples PASS; a stale accepted server plan consumes without mutation.
- Dirt gather uses a server RightClickBlock handler because Adventure can skip `Item.useOn`.
- Bowl-chain inputs are consumed exactly even in Creative; dirt gathering and WildResource harvesting do not create Creative economic drops.
- Cooldown and invalid-action feedback are action-bar messages; fill/mix sounds play once server-side.

## 14. Proposed implementation architecture

### M1 foundation

Likely production files:

- `ItemRegistry.java`: `DIRT`, `DUNG`, `EMPTY_BOWL`, `BOWL_OF_DIRT`, `BOWL_OF_FERTILE_DIRT`, `BOWL_OF_WATER`;
- `BlockRegistry.java`: `DUNG`;
- `CreativeTabRegistry.java`: intentional item exposure;
- new `block/DungBlock.java`;
- bowl item classes sufficient for registry identity, without enabling the chain;
- hand-authored lang/models/blockstate/texture/loot listed in section 11.

Do not register `fertile_dirt`, a Dung BlockItem, recipes, cooldown behavior, or five-harvest state in M1.

### M2 dung WildResource

Extend `WildResourcePlacementRules` with the closed support predicate and `WildResourceEntries` with dung ID/tuning/placement/inspection/harvest/loot. Reuse `WildResourceSavedData`, manager, scheduler, reconciliation, and `WildResourceHarvestService`. Add result-bearing protection/actor validation before any manual Adventure removal, cancel recognized denials, and guard ordinary tracked-node breaks before vanilla loot proceeds.

### M3 dirt gathering

Add a small `DirtGatheringService` and registered `DirtGatheringInteractionHandler`:

- MAIN-hand `RightClickBlock` on exact dirt;
- exact Britannia shovel;
- real player, Adventure/Survival allowed; Creative/Spectator/fake denied economic output;
- `level.mayInteract` and `HouseBuildRights.evaluateBreak` before mutation;
- player NBT cooldown claimed before output;
- custom dirt inserted or dropped safely;
- one durability on success;
- dirt block never changes;
- event canceled on every feature-owned exact-dirt attempt.

Add a narrow client-prediction interception in `QualityShovelItem` for exact dirt so inherited vanilla flattening does not make a ghost dirt path. Do not call Mining or managed-resource code.

### M4 dry bowl preparation

Add a neutral `BowlPreparationService` shaped like `DyeTubLoadingService` with plan/revalidate/apply. Wire `empty_bowl.use` and `bowl_of_dirt.use`, MAIN only. Consume exact main/off inputs in every mode, produce one paid output, use a neutral give-or-drop helper, and return sided success once.

### M5 water acquisition

Add source-only raycast behavior to `EmptyBowlItem`. Extend `WaterSourceInteraction` with an exact `ItemRegistry.EMPTY_BOWL` branch and delegate `WaterWellBlock` through it. Keep the well stateless and water unchanged. Exercise real root/child well parts, source water, flowing water, full inventory, Adventure, and public-well compatibility.

### M6 final mixing

Add the final tuple to the same preparation service. Output `ItemRegistry.FERTILIZED_DIRT` and a count-2 `EMPTY_BOWL` stack. Prefer placing primary output in an emptied main hand and returned bowls in an emptied offhand, then safely insert/drop any remainder. Revalidate both stacks immediately before shrink.

### M7 five-harvest lifecycle

Add state/methods/NBT/sync to `FarmingBlockEntity`; extend flower conversion snapshots; initialize state and BE hydration atomically in `FertilizedDirtItem` and `CommunityHoedFarmBlock`; decrement at the two authoritative crop/tree success points; modify community annual behavior for remaining uses; and add exact exhaustion cleanup for ordinary/community/tree/tall presentation. Preserve house plot structure and legacy untracked plots.

### M8 integration

Run the full loop in real server GameTests, exercise exploit matrices, run focused/full JUnit, run full GameTests with `--no-configuration-cache`, distinguish the known managed-deposit world failure, and perform live-client visual/Adventure checks. No merge is part of this work.

## 15. Risks and mitigations

| Risk | Evidence/impact | Required mitigation |
| --- | --- | --- |
| Duplicate fertile identity | Spec says `fertile_dirt`; repo has `fertilized_dirt` | Always output existing holder; registry contract forbids new ID |
| Client/server double execution | Item use runs on both logical sides | Client predicts result only; all inventory/world mutation server-side |
| Both hands firing | Minecraft continues to offhand after PASS | Fixed MAIN contract, OFF guard, valid main returns consuming result |
| Stale/repeated packet | Stacked items and repeated use | Server plan revalidates exact live stacks immediately before shrink |
| Creative container duplication | Non-consuming inputs plus two returned bowls | Exact-consume bowl inputs in Creative; no Creative gather/drop economy |
| Partial/full inventory | Outputs can be lost or partially inserted | Freed-hand placement plus add/drop mutated remainder; capacity-aware Creative helper |
| Mining crossover | Custom shovel is also a sediment/root tool | Right-click event only; forbid all Mining/deposit entry points and assert no progression |
| Adventure bypass/failure | `Item.useOn` skipped without mayBuild | Server RightClickBlock authority; client prediction suppression |
| House protection bypass | New grant paths may precede BreakEvent protection | Explicit `mayInteract` + HBR before dirt/dung output |
| Public/private well ambiguity | Existing well has no use protection | Preserve public behavior; any private-well policy must apply consistently to every container |
| WildResource support validity | Existing reconciliation is load-triggered, not immediate | Exact spawn/validator predicate; no independent survival removal that leaves stale ledger |
| WildResource double drop | Manual Adventure and vanilla loot are separate | Manual pop only in canceled Adventure; loot table only in ordinary break |
| Untracked player-placed dung | BlockItem would bypass ledger/caps | Plain item only; no BlockItem |
| Sparse dung sites | Exact support excludes normal grass surface | Start ash cadence; live density/cap QA before balance lock |
| Chunk persistence | Node/schedules and fertility must survive reload | Reuse generic SavedData and BE NBT/update paths; round-trip tests |
| Double harvest decrement | Many visible crop interaction endpoints | Decrement only inside converged server success methods |
| Fruit-tree semantic mismatch | Per-fruit clicks are separate existing HARVEST actions | Locked per fruit; explicit tests and product conflict documented |
| Community annual reset | Current first harvest destroys fertile substrate | Remaining 4–1 retains block/reopens seed window; fifth returns base |
| Flower conversion loss | Replacing BE can drop the counter | Carry state in flower soil snapshot; flowers do not decrement |
| Legacy farm provenance | Old BEs cannot prove item application | Missing NBT remains untracked/unlimited; document compatibility |
| Hydration/owner defects | Current application initializes blockstate only/unowned | Centralize BE initialization and validate protection before conversion |
| Asset gaps | All new models/textures absent | M1 asset contracts; final visual QA in M8 |
| Shovel availability | No survival production route found | Assign/approve an acquisition route before full-loop acceptance |

## 16. Contradictions and open product conflicts

1. **`fertile_dirt` terminology vs registry reality.** The repository’s public saved/loot ID is `britannia_mod:fertilized_dirt`. M0 resolves this by reusing it; no rename/duplicate.
2. **Requested `empty_bowl` vs legacy `empty_pewter_bowl`.** The legacy item is economy/material-specific and has no container contract. M0 resolves this by creating exact `empty_bowl`; the legacy item remains invalid. This leaves an acquisition gap because current economy documentation sells the pewter bowl, not the new generic bowl.
3. **Current farming is infinite.** There is no exhausted state/counter. M7 is a real lifecycle change, not a one-line decrement.
4. **Community annuals currently consume a preparation after one harvest.** Five uses require retaining/reopening the plot through uses 1–4.
5. **Fruit trees have per-fruit harvest transactions.** M0 locks each ripe-fruit click as one successful harvest. Five complete canopy cycles would require a product override.
6. **House plots do not accept fertilized dirt.** They remain permanent/untracked; adding finite house-plot application is not authorized.
7. **Legacy already-applied farming blocks have no provenance.** M0 preserves them as unlimited rather than retroactively assigning five uses.
8. **Existing wells are public, unlimited uses.** Applying a new foreign-house restriction only to bowls would be inconsistent; applying it to all containers would alter compatibility. M0 preserves public access and requires only vanilla `mayInteract` for the new fill.
9. **Creative convention conflict.** Many project actions preserve Creative inputs, but this chain returns reusable containers. M0 deliberately consumes bowl inputs in Creative to meet the no-duplication/conservation contract.
10. **Custom shovel progression gap.** The required `britannia_shovel` has no repository-visible survival acquisition route, and Milestones 1–8 do not assign one. This must be resolved before end-to-end release.
11. **Bowl progression gap.** The new generic empty bowl likewise has no acquisition route in the current milestone plan. M1 can register it, but M8 cannot claim a survival-complete loop without an approved source.

Items 10 and 11 do not block registry foundation work, but they block the final real-player loop if left unresolved.

## 17. Milestone map

| Milestone | Intended scope | Likely files | Primary tests | Dependency | Principal risk |
| --- | --- | --- | --- | --- | --- |
| M1 | Registry/item/block/data foundation only | `ItemRegistry`, `BlockRegistry`, `CreativeTabRegistry`, `DungBlock`, lang/models/textures/loot | registry/asset/serialization contracts | M0 IDs locked | duplicate IDs, missing models, accidental BlockItem |
| M2 | Dung as WildResource | `WildResourceEntries`, `WildResourcePlacementRules`, harvest/protection handler | support matrix, scheduler, SavedData, reconciliation, Adventure/Survival drops | M1 dung identities | double drops, stale ledger, sparse eligible surface |
| M3 | Custom shovel dirt gathering | new service/handler, narrow `QualityShovelItem` prediction override | Adventure/Survival, cooldown, house, full inventory, no Mining progression | M1 dirt, M0 cooldown | Adventure event routing, inherited path flattening |
| M4 | Dry bowl steps | bowl items + preparation plan/apply service | hand order, stacks, stale plans, Creative exact conversion, two players | M1 bowl/dirt/dung IDs, M2 supply, M3 supply | both-hand/double packet duplication |
| M5 | Bowl water from source/well | `EmptyBowlItem`, `WaterSourceInteraction`, `WaterWellBlock` delegation | source vs flowing, real multiblock part, unchanged source/well, Adventure | M1 bowl IDs | Creative add semantics, source priority, public-well compatibility |
| M6 | Final fertile mixing | preparation service final tuple | exact inventory deltas, 2-bowl return, existing fertile ID, overflow | M4 + M5 | bowl loss/duplication, accidental new fertile ID |
| M7 | Five-harvest farming lifecycle | `FarmingBlockEntity`, `FertilizedDirtItem`, `CommunityHoedFarmBlock`, `FarmingBlock`, `OrangeFruitBlock`, flower snapshots | 5→0 sequence, annual/perennial/community/tree, failed attempts, save/sync, two players | canonical output from M6 | double decrement, tree cleanup, legacy compatibility |
| M8 | Full integration/exploit/regression | Patch-specific GameTests/report only except fixes | full scenario, packet/hand/Creative/relog/chunk, full JUnit/GameTests | M1–M7 and acquisition decisions | hidden exploit, known dirty GameTest world, unfinished art |

MILESTONE 0: PASS

SAFE TO START MILESTONE 1: YES

Primary architecture:
Extend the existing WildResource ledger for dung, handle custom-shovel dirt gathering as a protected server right-click transaction entirely outside Mining, use a fixed-main/offhand plan-and-apply service for custom bowls and existing well/source-water seams, output the canonical `britannia_mod:fertilized_dirt`, and persist five successful uses on the authoritative `FarmingBlockEntity` substrate.

Locked decisions:
- Bowl conservation: return 2 × `britannia_mod:empty_bowl`; consume exact inputs in all modes.
- Dirt-gather cooldown: 1,200 ticks / 60 seconds, persisted per player and claimed server-side.
- Dirt ingredient: new plain stackable `britannia_mod:dirt` item.
- Gatherable terrain: exact `minecraft:dirt` only.
- Five-harvest state owner: `FarmingBlockEntity`; decrement only in authoritative crop/tree success commits.

Outstanding blockers:
None for M1. Full-loop blockers: no survival acquisition route is present for `britannia_mod:britannia_shovel` or the new generic `britannia_mod:empty_bowl`; those routes require product assignment before M8 can pass.

Baseline validation:
JUnit 3,097 run / 3,080 passed / 0 failed / 17 skipped. GameTests 784 run / 783 passed / 1 pre-existing environmental failure (`authoredgeometrysurvivesreconstructionandmaterializes`). Focused WildResource 38/38 passed; focused bowl/well architecture 19/19 passed.

Repository state:
Branch `patch-18`, HEAD `e57f3e3e2530fbbe5f90c14774d2f0f9fb19e595`, 0 ahead / 0 behind `origin/patch-18` at kickoff. Worktree was already dirty with staged Patch 18 notes/image and unrelated modified asset files; both authoritative project docs and this report are untracked. No unexpected branch movement, merge, rebase, reset, cleanup, commit, or production edit occurred during M0.
