# Milestone 0 - Audit Re-verification and Architecture Decision Record

**Project:** UltimaCraft OreVein Remediation
**Date:** 2026-08-20
**Source audit:** `docs/OREVEIN_ENGINEERING_AUDIT.md` (2026-08-20)
**Playbook:** `docs/ULTIMACRAFT_OREVEIN_REMEDIATION_PLAYBOOK.md`
**Status:** investigation complete, no implementation started

---

## 1. Repository / Git State

| Item | Value |
|---|---|
| Worktree path | `C:\projects\britannia\mod\Britannia_Mod\.claude\worktrees\flagstone-foundation-kickoff-8fb72e` |
| Branch | `claude/ultimacraft-orevein-remediation-70bfeb` |
| HEAD | `c2bc44f3 chore(housing): sanitize the housing programme for the production branch` |
| `git status --short` | empty (clean) |
| Divergence from `main` | 418 ahead, 0 behind |
| Pre-existing unrelated changes | none in this worktree |

The playbook, audit and kickoff prompt are **untracked** files in the main checkout
(`C:\projects\britannia\mod\Britannia_Mod`, branch `patch-18`). They are not present in this
worktree and were read from that location. This record is written beside them, also untracked,
following the established "internal docs stay off the branch" convention.

Nothing was modified. The only command executed against the build was `./gradlew test`.

### Baseline test result (unmodified tree)

```
2759 tests completed, 5 failed, 17 skipped      (4m 5s)
```

The five failures are pre-existing and in unrelated subsystems:

| Failing test | Subsystem |
|---|---|
| `RoutineSkillGainPresentationTest.gainsStillMutatePersistAndSync` | skill presentation |
| `MilestoneEightContentReportTest.committedReportExactlyMatchesDeterministicCatalogueAndResourceReconstruction` | shrines/monoliths content report |
| `CorrectiveMilestoneNineARenderAlignmentTest.ownerSuppliedAssetsAndUnchangedProtectedAssetsHaveExpectedHashes` | asset hash pins |
| `MonolithMilestoneSevenRenderingTest.alternateTextureIsOpaqueDistinctTwoMaterialAtlasWithPinnedHash` | asset hash pins |
| `MonolithMilestoneSevenRenderingTest.crystallineTextureIsOpaqueDistinctTwoMaterialAtlasWithPinnedHash` | asset hash pins |

All 82 Mining/deposit unit tests pass:

| Class | tests / failures |
|---|---|
| `MineableCatalogContractTest` | 13 / 0 |
| `MineableCatalogValidationTest` | 12 / 0 |
| `MiningBreakGateTest` | 9 / 0 |
| `MiningCalibrationTest` | 5 / 0 |
| `MiningEconomyIdentityTest` | 9 / 0 |
| `MiningFeedbackPolicyTest` | 8 / 0 |
| `MiningRestorationPolicyTest` | 7 / 0 |
| `MiningSkillTest` | 3 / 0 |
| `SilverVerticalSliceTest` | 10 / 0 |
| `ManagedClayDepositTest` | 6 / 0 |

GameTests were **not** run in M0 (617 `@GameTest` methods exist; a headless `gameTestServer` run is
available and should establish the second half of the baseline before M1 lands code).

---

## 2. Verified Runtime Architecture

The audit described three adjacent systems. There are in fact **four**. The audit never mentions
`ManagedDeposit` — the string does not appear anywhere in `docs/OREVEIN_ENGINEERING_AUDIT.md` — and
the entire `com.seggellion.britannia_mod.deposit` package is absent from its analysis. That package
is the newest and healthiest seam in the area and already implements much of what the playbook
schedules for M6.

### 2.1 Placement (Rails-curated, operator-triggered)

```
/populateores <ore>                       PopulateOresCommand.executePopulateOres
    -> OreVeinFetcher.fetchOreVeins(server, shardName)     synchronous HTTPS, server thread
    -> for each row where row.oreType == ore
        -> generateOreVein(level, pos, radius, oreType, block, rotation)
            -> switch (oreType) { Cluster | Vertical | Snake | Geode | VerticalLayered | Layered }
                -> level.setBlock(...) directly, RNG = level.getRandom()
                -> originalBlocks.put(pos, oldState)   static Map<BlockPos,BlockState>
```

- `PopulateOresCommand.java:32-343`, registered in `CommandRegistry.java:40`.
- `level` is `source.getLevel()` — whatever dimension the operator stands in.
- `OreVeinFetcher.java:42-82`. Bounded by `BoundedHttp` (5 s connect / 10 s read / 1 MiB cap) but
  executed inline on the server thread, so a slow Rails can stall the tick loop up to ~15 s.
- `OreVeinFetcher.fetchOreVeins`'s `shardName` parameter is **dead** — line 48 uses
  `credentials.shardName()` instead.

### 2.2 `OreVeinLoader` — loaded, never consumed

`BritanniaMod.java:130` calls `OreVeinLoader.loadOreVeins()`, which parses
`src/main/resources/data/britannia_mod/ore_veins.json` (10 rows) and logs each one.
`OreVeinLoader.getOreVeins()` has **zero callers** anywhere in `src/`. The data is dead weight.

### 2.3 Extraction — two independent front halves over one shared back half

```
BlockEvent.BreakEvent
  HIGH    MiningGateHandler.onBlockBreak            skill gate; cancels on denial
  HIGH    ManagedDepositInteractionHandler.onBreak  managed-deposit takeover; cancels always
  NORMAL  StructureProtectionHandler.onBlockBreak   house/zone rules; cancels on refusal
  NORMAL  CustomBlockBreakHandler.onBlockBreak      managed yield; cancels only for a Britannia pickaxe
  LOWEST  MiningGateHandler.onBlockBroken           clears provenance

PlayerInteractEvent.LeftClickBlock
  HIGH    ManagedDepositInteractionHandler.onLeftClick   adventure-mode deposit working
```

**Front half A — Mining catalogue (ores + stone).**
`Mineables.resolve(BlockState)` -> `MineableCatalog` (`/data/britannia_mod/mining/mineables.json`,
schema 1, 26 definitions, all ACTIVE). Gate is `MiningBreakGate.evaluateResolved`; yield is
`CustomBlockBreakHandler.handleOreBreaking` / `handleStoneBreaking`; award is
`MiningSkill.awardForBreak`. **The tool is not a field anywhere in this half** — it is
`CustomBlockBreakHandler.isBritanniaPickaxe(ItemStack)` at `CustomBlockBreakHandler.java:70-74`.

**Front half B — managed deposits (clay, silica).**
`ManagedDeposits.resolve(BlockState)` -> `ManagedDeposit` record (`deposit/ManagedDeposit.java`),
which *does* carry `TagKey<Item> extractionTool`, `Supplier<Item> extractedItem`, `int extractedCount`.
Extraction is the single transaction `ManagedDepositExtraction.extract` — house rights, then tool,
then record, then mutate, then yield. Wrong tool returns `WRONG_TOOL` **before any world mutation**.

**Shared back half — restoration.**
`BrokenBlockTracker.recordBrokenBlock` -> `BrokenBlockDataStorage` (per-`ServerLevel` `SavedData`,
key `broken_blocks`) -> `BlockRestoreHandler.onServerTick(ServerTickEvent.Pre)`.

### 2.4 End-to-end trace

```
definition/source          placement            extraction           yield            depletion         persistence            regeneration
-------------------------  -------------------  -------------------  ---------------  ----------------  ---------------------  -------------------
Rails ore_veins table      /populateores        MiningBreakGate +    PurityOreItem /  setBlock(fluid)   broken_blocks          BlockRestoreHandler
  (ORE_TYPES + shape       (6 imperative        CustomBlockBreak     GradeStoneItem                     SavedData per level    +6h wall clock
   switch)                  shapes, level RNG)   Handler (pickaxe)                                      (BlockPos -> record)

mineables.json             none (natural        same                 same             same              same                   same
  (stone family)            vanilla terrain)

ManagedDeposits (Java)     /manageddeposit      ManagedDepositExtraction              setBlock(fluid)   same                   same
  CLAY, SILICA_SAND         place / placehere    (tag-driven tool)   configured item
```

### 2.5 Worldgen

There is none. `src/main/resources/data/britannia_mod/neoforge/biome_modifier/` contains exactly two
files, `mongbat_spawn.json` and `wisp_spawn.json`, both `neoforge:add_spawns`. There are no
`worldgen/` data directories, no `ConfiguredFeature`/`PlacedFeature` registrations, and no
`neoforge:remove_features` modifier. `features/AltitudeScaledFeature.java` extends
`Feature<OreConfiguration>` but is **never registered** — dead code.

---

## 3. Audit Findings: Confirmed / Changed / Disproved

| Audit finding | Current verdict | Evidence |
|---|---|---|
| 1. High-skill player + wrong tool destroys a managed resource | **CONFIRMED, and a concrete vector identified** | `MiningBreakGate` never reads the held item (no `ItemStack` in `MiningBreakGate.java`); on `ELIGIBLE` it returns without cancelling. `CustomBlockBreakHandler.java:56` acts only `if (isBritanniaPickaxe(heldItem))` and otherwise falls off the method without cancelling, so vanilla breaking proceeds. See §3.1 for the reachable path. |
| 2. Vanilla ore features still active | **CONFIRMED** | Only two biome modifiers exist, both `add_spawns`. No `remove_features`, no worldgen data, no registered features. |
| 2b. Vanilla iron/gold are catalogued mineables | **CONFIRMED** | `mineables.json`: `iron -> minecraft:iron_ore, minecraft:deepslate_iron_ore`; `gold -> minecraft:gold_ore, minecraft:deepslate_gold_ore, britannia_mod:gold_ore`. |
| 2c. Coal is placed but not managed | **CONFIRMED** | `PopulateOresCommand.java:41` `ORE_TYPES.put("coal", Blocks.COAL_ORE)` and the `case "coal", "tin"` branch place it; `minecraft:coal_ore` appears nowhere in `mineables.json`. It therefore breaks with vanilla drops, no gate, no restoration. |
| 3. Unsafe shape writes | **CONFIRMED, worse than described** | `ClusterVein` and `GeodeVein` have **no host check at all** — not even the bedrock guard the audit implies; they overwrite air, water, lava, block entities and bedrock. `SnakeVein` likewise. `VerticalVein` guards bedrock only. `LayeredVein` places into `isAir()` in two phases and into `!isAir()` (anything solid, incl. bedrock/block entities) in the third. `VerticalLayeredVein` is `isAir()`-only in all three phases. |
| 4. Shapes consume `ServerLevel#getRandom()` | **CONFIRMED** | `RandomSource random = level.getRandom();` is line 2-3 of `generate` in all six shape classes. |
| 4b. Repeat `/populateores` rerolls and reapplies | **CONFIRMED** | No instance ledger, no dedupe; the command refetches from Rails and regenerates with fresh RNG each invocation. |
| 5. Restoration is O(all pending) every server pre-tick | **CONFIRMED** | `BlockRestoreHandler.onServerTick(ServerTickEvent.Pre)` iterates `storage.getBrokenBlocks().entrySet()` for **every** `ServerLevel` on **every** tick (20 Hz), including unloaded and not-yet-due records. |
| 6. `/populateores clear` is dangerous | **CONFIRMED, quantified** | `CHUNK_RADIUS = 20` -> 41x41 = 1681 chunks; `level.getChunk(x,z)` resolves to `getChunk(x,z,ChunkStatus.FULL,true)` which **generates** missing chunks (`Level.java:195-208`). `clearChunkOres` scans `y = minBuildHeight..maxBuildHeight` **inclusive** = 385 layers x 256 columns = 98,560 blocks/chunk, so ~165.7 M synchronous block reads. The success message says "Globally cleared", but the scan is bounded to ~±320 blocks. |
| 7. Explosion/piston bypass managed extraction | **CONFIRMED** | `BaseOreBlock` and `ManagedDepositBlock` are plain `Block` subclasses with no `getPistonPushReaction` override and no `pushReaction(...)` property, so both default to `PushReaction.NORMAL`. No `ExplosionEvent` listener covers ores or deposits (the mod has such listeners only in `FlowerInteractionHandler:85` and `ManagedVegetationInteractionHandler:57`). Explosions do not fire `BreakEvent`, so no restoration debt is created. |
| 8. Undo state is static, dimension-unsafe, nonpersistent | **CONFIRMED** | `PopulateOresCommand.java:35` `private static final Map<BlockPos, BlockState> originalBlocks` — no dimension key, never serialised, shared across all six shapes and every invocation. |
| 9a. `SnakeVein` fails on small radii | **CONFIRMED** | `SnakeVein.java:29` `random.nextInt(radius - 9) + 10` throws `IllegalArgumentException` for `radius <= 9`, and it is evaluated *before* the full-height override on line 32, so the throw is unconditional at `i = 0`. |
| 9b. `GeodeVein` is not a geode | **CONFIRMED** | `GeodeVein.generate` is `radius * 2` uniformly random scatter points in a box. No shell, no interior, no crust. Also throws for `radius < 2` (`random.nextInt(radius / 2)`). |
| 9c. `LayeredVein` mixes air placement and solid replacement | **CONFIRMED** | Core and peripheral phases require `currentState.isAir()`; the scatter phase requires `!currentState.isAir()`. |
| 9d. `VerticalLayeredVein` is air-only | **CONFIRMED** | All three phases are guarded by `level.getBlockState(target).isAir()`. Silver therefore only materialises in caves and air pockets. |
| 9e. Placement counts include duplicates | **CONFIRMED** | `placed++` follows every `setBlock` unconditionally in all six shapes; positions are never deduplicated, and out-of-build-height writes silently fail while still incrementing. |
| 10. Rails fetch and placement are synchronous on the server thread | **CONFIRMED** | `OreVeinFetcher.fetchOreVeins` performs a blocking `HttpURLConnection` inside the Brigadier executor. |
| Restoration delay is 6 real-world hours, not 24 | **CONFIRMED** | `BlockRestoreHandler.java:47` `private static final int RESTORE_HOURS = 6`. |
| Restoration uses wall-clock epoch | **CONFIRMED** | `BrokenBlockTracker.recordBrokenBlock` stores `System.currentTimeMillis()`; `BlockRestoreHandler` compares against `System.currentTimeMillis()`. Offline time counts. |
| Restoration persists across restart | **CONFIRMED** | `BrokenBlockDataStorage extends SavedData`, name `broken_blocks`, per `ServerLevel`, `setDirty()` on add/remove. |
| Unloaded chunks are not force-loaded | **CONFIRMED** | `if (!level.isLoaded(data.pos)) continue;` — correct, but the record is still visited every tick. |
| Restoration can overwrite water/lava | **CONFIRMED** | `BlockRestoreHandler.canRestoreInto` returns true when `current.isAir()` or `current.canBeReplaced()`. In 1.21.1 both `Blocks.WATER` and `Blocks.LAVA` are declared with `.replaceable()` (`Blocks.java:339-369`), and `canBeReplaced()` returns `this.replaceable` (`BlockBehaviour.java:788`). |
| The Mining catalogue is a strong seam worth preserving | **CONFIRMED** | `MineableCatalog` is classpath JSON, fail-fast, codec-shaped, duplicate-block-guarded, range-validated, and covered by 25 executable unit tests. |
| **Audit is silent on the `deposit` package** | **AUDIT INCOMPLETE** | `grep -c ManagedDeposit docs/OREVEIN_ENGINEERING_AUDIT.md` = 0. See §3.2. |
| **Silica must be added from scratch (M6)** | **DISPROVED** | Silica already exists end-to-end as a hand-placed managed deposit. See §3.2. |

### 3.1 The wrong-tool destruction path, concretely

The bypass is real but narrower than "any vanilla tool", because two other handlers incidentally
cover most of the space. The reachable vector is a **Britannia two-handed axe**:

1. `CityGameModeHandler.onPlayerTick` (`:45`) treats `QualityToolItem` **and `TwoHandedAxeItem`** as
   a "special tool" and switches the player to `SURVIVAL` outside cities.
2. The player breaks `britannia_mod:verite_ore` (`required_mining` 95) with Mining >= 95.
3. `MiningGateHandler` (HIGH): `evaluateResolved` -> `ELIGIBLE` -> `permitsBreak()` -> returns, does
   not cancel. The gate never looked at the axe.
4. `ManagedDepositInteractionHandler` (HIGH): `ManagedDeposits.resolve` is empty for an ore -> returns.
5. `StructureProtectionHandler` (NORMAL, `:57`): `isAllowedTool` is **true** for `TwoHandedAxeItem` ->
   early return, no refusal.
6. `CustomBlockBreakHandler` (NORMAL): `isBritanniaPickaxe(axe)` is false (`QualityShovelItem` and
   `TwoHandedAxeItem` both extend vanilla item classes, not `QualityToolItem`) -> the method returns
   **without cancelling**.
7. Vanilla breaking completes. `britannia_mod:verite_ore` has **no loot table** (there is no
   `data/britannia_mod/loot_table/blocks/verite_ore.json`; only 52 unrelated block tables exist), so
   the block is destroyed with **no drop, no managed yield, no restoration record, no skill award**.

The same step 6-7 tail is reached for **`minecraft:iron_ore` / `minecraft:gold_ore` with any vanilla
pickaxe inside a house the player owns** (`HouseBuildRights` -> `ALLOWED` -> no refusal at step 5),
and there the vanilla loot table *does* fire, leaking raw iron/gold outside the economy.

The existing GameTest named `vanillaToolCanNoLongerDestroyGatedOreDroplessly`
(`MiningGateGameTests.java:236`) does **not** cover this. It sets Mining to `10.0f` against
`VERITE_ORE` (required 95) — an *under-skilled* player. It proves the skill gate, and its name
overstates what it pins. `MiningBreakGateTest` contains zero occurrences of "tool", "pickaxe" or
"item".

### 3.2 What the audit missed: the `deposit` package and silica

`com.seggellion.britannia_mod.deposit` (landed in `be7787b3`, hardened in `daca8d70`) already
provides, for `ManagedDepositBlock` resources:

- a per-resource record with a **tag-keyed extraction tool** (`ManagedDeposit.extractionTool`);
- **one authoritative extraction transaction** with validate-before-mutate ordering
  (`ManagedDepositExtraction.extract`);
- **wrong-tool denial that cancels before any world mutation** — `onBreak` cancels unconditionally
  for any non-creative player once the block resolves as a deposit;
- adventure-mode left-click working, house-authority checks, and operator commands
  (`/manageddeposit place|placehere|remove|inspect|types`).

**Silica already exists**:

| Piece | Location |
|---|---|
| Deposit block | `BlockRegistry.SILICA_SAND_DEPOSIT` -> `britannia_mod:silica_sand_deposit`, `ManagedDepositBlock`, `MapColor.TERRACOTTA_WHITE`, strength 0.5, `SoundType.SAND` |
| Resource entry | `ManagedDeposits.SILICA_SAND` |
| Extraction tag | `ModTags.Items.SILICA_SHOVELS` -> `data/britannia_mod/tags/item/silica_shovels.json` |
| Yield item | `ItemRegistry.SILICA_SAND` |
| Economy identity | `CommodityMappings:176` `map("silica_sand", "glass", "raw", "sand", ...)` |
| Downstream recipe | `data/britannia_mod/recipe/raw_glass_from_silica_sand.json` |
| Assets | blockstate, item model, `en_us.json` entries |
| GameTests | `ManagedDepositHouseProtectionGameTests:126`, `HousingMaterialSupplyGameTests:169-194` |
| Gravity | plain `Block`, not `FallingBlock` — already satisfies "not gravity-movable" |

Playbook M6 is therefore ~80% already delivered. The **real** silica gaps are:

1. `silica_shovels.json` contains only `britannia_mod:britannia_shovel` — the same item as
   `clay_shovels.json`. There is no dedicated silica shovel; the tag seam exists but the item does not.
2. No generation at all — silica is hand-placed via `/manageddeposit` only (playbook M7).
3. No per-resource regeneration timing — silica inherits the global 6 h.
4. `ManagedDeposit` is a Java `List.of`, not data-driven.
5. Fortune/Silk-Touch and fake-player policy for deposits are implied by the code path but not pinned
   by tests.

---

## 4. Current Resource Matrix

### 4.1 `/populateores` placement capability

`ORE_TYPES` has 19 entries, but `generateOreVein`'s switch has cases for only 10. The other 9 return 0
and can never be placed — they exist only to widen `clear`.

| `/populateores` type | Block placed | Shape | In Mining catalogue? | Extraction tool | Regeneration |
|---|---|---|---|---|---|
| `copper` | `britannia_mod:copper_ore` | Cluster | yes (75) | Britannia pickaxe (hardcoded) | 6 h |
| `verite` | `britannia_mod:verite_ore` | Cluster | yes (95) | Britannia pickaxe | 6 h |
| `iron` | `minecraft:iron_ore` | Vertical | yes (0) | Britannia pickaxe | 6 h |
| `valorite` | `britannia_mod:valorite_ore` | Vertical | yes (99) | Britannia pickaxe | 6 h |
| `shadow_iron` | `britannia_mod:shadow_iron_ore` | Vertical | yes (70) | Britannia pickaxe | 6 h |
| `gold` | `minecraft:gold_ore` | Snake | yes (85) | Britannia pickaxe | 6 h |
| `agapite` | `britannia_mod:agapite_ore` | Geode | yes (90) | Britannia pickaxe | 6 h |
| `silver` | `britannia_mod:silver_ore` | VerticalLayered (air-only) | yes (55) | Britannia pickaxe | 6 h |
| `tin` | `britannia_mod:tin_ore` | Layered | yes (65) | Britannia pickaxe | 6 h |
| **`coal`** | **`minecraft:coal_ore`** | Layered | **NO** | **any vanilla pickaxe** | **none** |
| `diamond`, `deepslate_diamond`, `redstone`, `deepslate_redstone`, `vanilla_copper`, `emerald`, `deepslate_emerald`, `lapis`, `deepslate_lapis` | — | **none (returns 0)** | no | n/a | n/a |

### 4.2 Mining catalogue (26 ACTIVE definitions)

- **ORE (9):** `iron` 0, `silver` 55, `tin` 65, `shadow_iron` 70, `copper` 75, `gold` 85, `agapite` 90,
  `verite` 95, `valorite` 99.
- **STONE (17):** `stone` 0, `cobblestone` 0, `calcite` 5, `sandstone` 5, `diorite` 10, `andesite` 15,
  `granite` 20, `tuff` 25, `deepslate` 30, `cobbled_deepslate` 30, `metamorphic_rock` 30,
  `glacial_rock` 35, `dripstone` 35, `basalt` 40, `igneous_rock` 40, `blackstone` 45,
  `volcanic_rock` 45.

Notable: `britannia_mod:gold_ore` is registered and catalogued but never placed by anything.
`britannia_mod:high_purity_silver_ore` is registered, uncatalogued and unplaceable — dead.
`britannia_mod:sandstone_deposit` is a plain `Block` on the *Mining* path, not a `ManagedDepositBlock`.

### 4.3 Managed deposits (2)

| Deposit | Block | Tool tag | Tag contents | Yield | Regeneration |
|---|---|---|---|---|---|
| `clay_deposit` | `britannia_mod:clay_deposit` | `britannia_mod:clay_shovels` | `britannia_mod:britannia_shovel` | 1x `minecraft:clay_ball` | 6 h |
| `silica_sand_deposit` | `britannia_mod:silica_sand_deposit` | `britannia_mod:silica_shovels` | `britannia_mod:britannia_shovel` | 1x `britannia_mod:silica_sand` | 6 h |

---

## 5. Current Worldgen and Vanilla Ore Status

Today, **every vanilla ore feature generates normally, everywhere**. UltimaCraft contributes no
worldgen: no `ConfiguredFeature`, no `PlacedFeature`, no `remove_features` modifier, no registered
`Feature`. `/populateores` writes into already-generated terrain after the fact, and
`/populateores clear` is the only "suppression" mechanism — a bounded, chunk-generating,
provenance-blind block scan that turns 19 named block types into `minecraft:stone`.

**A trap that M5 must not walk into.** Vanilla's `underground_ores` generation step contains both the
metals we intend to control *and* stone-family features the Mining ladder depends on. From
`net/minecraft/data/worldgen/placement/OrePlacements.java` (1.21.1):

- **Candidates for suppression:** `ore_coal_upper`, `ore_coal_lower`, `ore_iron_upper`,
  `ore_iron_middle`, `ore_iron_small`, `ore_gold`, `ore_gold_lower`, `ore_gold_extra`, `ore_redstone`,
  `ore_redstone_lower`, `ore_diamond`, `ore_diamond_large`, `ore_diamond_medium`,
  `ore_diamond_buried`, `ore_lapis`, `ore_lapis_buried`, `ore_emerald`, `ore_copper`,
  `ore_copper_large`.
- **Must stay allowed:** `ore_andesite_upper`, `ore_andesite_lower`, `ore_diorite_upper`,
  `ore_diorite_lower`, `ore_granite_upper`, `ore_granite_lower`, `ore_tuff`, `ore_blackstone`,
  `ore_gravel`, `ore_dirt`, `ore_clay`. Andesite (15), diorite (10), granite (20), tuff (25) and
  blackstone (45) are ACTIVE catalogued mineables; removing them would delete the mid-tier Mining
  training material.
- **Nether, evaluate separately:** `ore_gold_nether`, `ore_gold_deltas`, `ore_quartz_nether`,
  `ore_quartz_deltas`, `ore_ancient_debris_large`, `ore_debris_small`, `ore_magma`, `ore_soul_sand`,
  `ore_gravel_nether`. Basalt and blackstone are Nether-native and catalogued, and
  `BlockRestoreHandler` was explicitly fixed to cover all dimensions for exactly that reason.

Removing the whole `underground_ores` step, or sweeping by family name, would break the existing
Mining ladder. The classification must be explicit and per-key.

---

## 6. Current Restoration Lifecycle

| Property | Current behavior | Location |
|---|---|---|
| Delay | **6 real-world hours**, single global constant | `BlockRestoreHandler.RESTORE_HOURS = 6` |
| Clock | wall-clock epoch, offline time counts | `System.currentTimeMillis()` in tracker and handler |
| Storage | per-`ServerLevel` `SavedData`, key `broken_blocks`, `Map<BlockPos, BrokenBlockData>` | `BrokenBlockDataStorage` |
| Record | `pos`, `originalState`, `brokenTime`, `playerUUID` | `BrokenBlockData` |
| Persistence | survives save/restart, `setDirty()` on mutation | `BrokenBlockDataStorage.add/remove` |
| Scheduler | `ServerTickEvent.Pre`, **all levels, all records, every tick (20 Hz)** | `BlockRestoreHandler.onServerTick` |
| Chunk policy | never force-loads; `if (!level.isLoaded(pos)) continue;` | correct, but the record is still visited |
| Occupancy | refuses when the cell is non-air and non-replaceable, or any entity overlaps | `canRestoreInto` |
| **Water/lava** | **overwritten** — both are `.replaceable()` in 1.21.1 | see §3 table |
| Blocked retry | full-rate retry every tick, no backoff | `continue` in the loop |
| Feedback | "You hear a cave collapse, N blocks fallen" to the breaking player | `sendRestorationMessages` |
| Diagnostics | `/brokenblocks list\|destroy\|destroy_all`, `/mining restorations`, `/manageddeposit inspect` | `BlockCommands`, `MiningDebugCommand`, `ManagedDepositCommands` |

Complexity is `O(levels x pending_records)` per tick. With 10,000 debts that is 200,000 map-entry
visits per second, almost all of them for records that are neither due nor loaded.

`MiningRestorationPolicyTest` is a **source-text** test: it asserts
`handler.contains("canBeReplaced")`. It pins the presence of the string that produces the water/lava
defect, so it will need updating rather than merely re-running when M1 fixes the behavior.

---

## 7. Architecture Decisions

### A. Canonical resource definition

**Decision: evolve `ManagedDeposit` into the canonical `ResourceDefinition`, and have it *reference*
`MineableCatalog` by id. Do not create a third catalogue, and do not extend `MineableDefinition`.**

There are already two front-half catalogues. `MineableDefinition` carries progression identity
(`required_mining`, `challenge`, `category`, `drop`, `economy_commodity`, `restorable`) and cannot
express a tool. `ManagedDeposit` carries exactly the fields `MineableDefinition` lacks — block, tool
tag, yield item, yield count — and is already the newer, better-shaped model.

Target shape:

```
ResourceDefinition
  id                    britannia_mod:silver           canonical, one per resource
  block                 britannia_mod:silver_ore
  depleted_state        fluid-aware empty (current behavior)
  mining                optional -> MineableCatalog id  (skill requirement + challenge, unchanged)
  extraction_tool       item tag
  yield                 item + count (+ purity/grade policy)
  shape                 shape id + config              (M3)
  placement             dimensions, biome tag, altitude, spacing, salt, host tag   (M3/M7)
  regeneration          delay + mode                   (M4)
  revision              int
```

Storage: **classpath JSON + fail-fast parse**, the same mechanism `MineableCatalog` and
`CraftableRegistry` already use — not a datapack registry. The repository's established convention is
"authoritative gameplay catalogue = classpath JSON validated at mod construction, tested by plain
JUnit without booting Minecraft", and `MineableCatalogValidationTest` (12 tests) proves it works.
A datapack registry would move validation to world load, lose the pure-JUnit test seam, and let a
server-side pack silently redefine the economy.

`ManagedDeposits.resolve` and `Mineables.resolve` become views over the one registry.

### B. Managed vanilla-looking resources

**Decision: yes — introduce `britannia_mod:iron_deposit` and migrate gold to the existing
`britannia_mod:gold_ore`. Retire `minecraft:iron_ore`, `minecraft:deepslate_iron_ore`,
`minecraft:gold_ore`, `minecraft:deepslate_gold_ore` from the ACTIVE catalogue. Schedule this as its
own milestone after M5, not in M1.**

The repository has already made this exact call twice and documented the reasoning in
`BlockRegistry`: `clay_deposit` exists because "registering `minecraft:clay` would make every river
bed an economic deposit", and `sandstone_deposit` exists because "`minecraft:sandstone` stays out of
it and stays worthless". Vanilla iron and gold are the last resources violating that principle, and
they are the reason UltimaCraft currently cannot distinguish a controlled deposit from ordinary
terrain, a village blacksmith's decoration, or a player's own wall.

Gold is already half-migrated: `britannia_mod:gold_ore` is registered and catalogued but nothing
places it. Finishing it is small.

Legacy vanilla ore blocks in existing chunks become **decorative and non-economic** after cutover.
They stop resolving through the catalogue, so they break with vanilla rules and vanilla drops.
That is deliberate and must be announced, because it is a visible change for players.

Coal needs a decision in M1 regardless: today `/populateores coal` places unmanaged
`minecraft:coal_ore` with full vanilla drops and no restoration. **Recommendation: remove `coal` from
`ORE_TYPES`/`generateOreVein` until it has a real definition**, rather than leaving a placement path
that manufactures free economy input.

### C. Rails coexistence

**Decision: retain Rails-curated coordinates, but only as an *importer* that produces
`DepositInstance` records. Never as a direct writer.**

Requirements:

1. The fetch moves off the server thread; the apply happens on it, in bounded slices.
2. A row's identity must come from **immutable row fields**, never from execution time. Preferred is
   a Rails-supplied stable `id` on the `ore_veins` payload. **The payload does not carry one today**
   (`OreVeinFetcher` reads `ore_type, x, y, z, radius, rotation, region` only). The smallest API
   contract improvement is: add an immutable `id` to each `ore_veins` row. Until Rails ships it,
   derive `instanceId = hash(shard, dimension, ore_type, x, y, z, radius, rotation)` — stable across
   re-imports, and it changes only when the curated row genuinely changes.
3. Import is idempotent: an instance already in the ledger is skipped, not rerolled. The deterministic
   seed is stored at creation, so re-materialisation reproduces the identical shape.
4. Dimension is recorded explicitly on the instance rather than inherited from wherever the operator
   was standing.
5. Validation (unknown resource, radius bounds, build height, malformed coordinates) rejects the row
   with a per-row report instead of throwing mid-loop.

### D. Restoration timing

**Decision: keep the 6-hour global default for every existing resource. Add an optional
per-definition `regeneration.delay`. Give silica 24 hours. Change nothing in M0-M3.**

Six hours is live production behavior for every ore and both deposits, and is pinned by
`ManagedClayDepositGameTests`, `MiningRestorationGameTests` and `MiningDebugCommand`'s reporting.
Silently retiming it would alter the shard economy without a decision. Silica is the safe place to
introduce per-resource timing because it is not yet generated anywhere — there is no live silica
state to migrate, so a 24-hour value costs nothing.

The global default remains, so a definition that omits `regeneration` keeps today's behavior.

### E. Deposit identity

**Decision: a compact per-dimension `DepositInstance` ledger. Standing block positions are *not*
persisted — they are recomputed from the shape plan.**

Smallest useful record:

| Field | Purpose |
|---|---|
| `instanceId` (long or UUID) | stable identity, duplicate rejection |
| `resourceId` | which definition |
| `revision` | definition fingerprint at materialisation |
| `origin` (packed long) | deposit anchor |
| `seed` (long) | deterministic replan input |
| `bounds` (2 packed longs) | locate/inspect without replanning |
| `source` enum | `NATURAL` / `RAILS` / `ADMIN` / `RETROFIT` |
| `materialized` / `depleted` counts | diagnostics, exhaustion signals |

Dimension is implied by the per-dimension store, matching how `broken_blocks` and `MiningProvenance`
already work.

Stable-id derivation:

- **Natural:** `hash(worldSeed, dimensionId, resourceId, ownerCellX, ownerCellZ, definitionSalt)` —
  reproducible without persistence, so a lost ledger is rebuildable.
- **Rails:** see §C.
- **Admin:** random id generated and persisted at `/manageddeposit place` time; there is no
  deterministic input to derive from and none should be invented.
- **Retrofit:** `hash(migrationBatchId, dimensionId, resourceId, discoveredOrigin)`, so a re-run of
  the same batch is idempotent.

### F. Restoration storage

**Decision: option 1 — keep per-dimension `SavedData`, re-index it by `ChunkPos`. Do not move to
chunk attachments now.**

| | per-dimension `SavedData` by `ChunkPos` | chunk attachments |
|---|---|---|
| Migration from `broken_blocks` | in-place: read the legacy flat `blocks` list, group by `ChunkPos`, write `schema: 2` | must touch every region file; records for never-loaded chunks have nowhere to go |
| `/brokenblocks list` and `/mining restorations` | still enumerable without loading chunks | impossible without loading |
| Removes the O(all-debts) tick scan | yes | yes |
| Blast radius | one class plus a versioned reader | serialization, chunk lifecycle, and every diagnostic |

The playbook already lists chunk attachments under deferred work; the evidence supports keeping them
there.

Scheduling design:

```
per-dimension SavedData, schema 2
    Map<ChunkPos, List<Debt>>            Debt = { packedPos, originalState, dueAtEpochMillis, playerUUID }
ChunkEvent.Load    -> push that chunk's debts onto an in-memory due-time priority queue
ChunkEvent.Unload  -> drop them from the queue
ServerTickEvent    -> every N ticks, drain queue head while dueAt <= now, bounded per pass
blocked cell       -> exponential backoff, re-queued, surfaced by /mining restorations
```

Offline time still counts (`dueAt` is absolute epoch). Restart still works (`SavedData`). Unloaded
chunks stay unloaded and contribute **zero** per-tick cost. Restoration proceeds only when the
expected depleted state is present, and water/lava/occupied cells stay blocked.

### G. Vanilla ore suppression

**Decision: `neoforge:remove_features` biome modifiers in
`data/britannia_mod/neoforge/biome_modifier/`, listing explicitly enumerated placed-feature keys.
Separate Overworld and Nether files. No block scanning.**

That directory already exists and demonstrably works for this mod (two `add_spawns` modifiers ship
there today), so the mechanism needs no new plumbing.

Rules:

- Remove named `PlacedFeature` keys only; never the whole `underground_ores` step.
- Maintain an explicit UltimaCraft-owned `suppressed` / `allowed` classification covering **every**
  ore-producing vanilla feature, with the §5 stone-family list pinned as `allowed`.
- Verify the exact 1.21.1 keys against the live registry at startup rather than trusting a
  hand-written list; log any ore-producing feature that is in neither bucket.
- **Nether: suppress nothing in the first pass.** Nether gold, quartz and ancient debris are outside
  the UO metal ladder, and blackstone/basalt are catalogued mineables that must keep generating.
- Deepslate variants are covered because they come from the same configured feature via
  `targetStates`, not from separate placed features — that must be asserted by test, not assumed.

### H. Existing-world policy

**Decision: hybrid, as the playbook proposes.**

1. New chunks: vanilla suppression active, managed deposits generated deterministically.
2. Existing chunks: **unchanged by default**. No global scan, ever.
3. Curated Rails deposits: importable deliberately into the same idempotent ledger.
4. Retrofit: optional, bounded region, dry-run preview with candidate/impact/conflict counts,
   explicit apply, durable migration ledger, idempotent re-run, documented rollback.

Additional decision: **`/populateores clear` should be disabled outright in M1, not repaired.** It
cannot distinguish a managed deposit from natural terrain, a village blacksmith's decoration, or a
player's wall, because provenance for those blocks does not exist. Its legitimate successor is the
M8 retrofit framework. Disabling it with a clear message is honest; making the scan "safer" would
preserve a tool whose core premise is unsound. `/undoores` should go with it — its ledger is a
static, non-persistent, dimension-blind map that is wrong more often than right.

---

## 8. M1-M4 Impact Map

Nothing below has been implemented.

### M1 - Correctness containment

| Area | Files |
|---|---|
| Wrong-tool gate | `mining/MiningBreakGate.java` (add tool to `Subject`/`evaluateResolved`), `mining/MiningGateHandler.java`, `event/CustomBlockBreakHandler.java` |
| Tool identity | `util/ModTags.java`, new `data/britannia_mod/tags/item/*.json` |
| Piston | `block/BaseOreBlock.java`, `block/ManagedDepositBlock.java` (`PushReaction.BLOCK`, following `FlowerBlock:112`) |
| Explosion | new listener or block override, modelled on `event/ManagedVegetationInteractionHandler.java:57` |
| Water/lava restoration | `event/BlockRestoreHandler.java` (`canRestoreInto`) |
| Placement input validation | `commands/PopulateOresCommand.java`, all six `features/*Vein.java` |
| Dangerous commands | `commands/PopulateOresCommand.java` (`clear`, `undoores`) |
| Coal | `commands/PopulateOresCommand.java` `ORE_TYPES` |
| Tests | `MiningGateGameTests`, `MiningRestorationGameTests`, `MiningBreakGateTest`, **`MiningRestorationPolicyTest` (source-text assertions will need rewriting)**, new piston/explosion GameTests |

### M2 - Canonical definitions

`deposit/ManagedDeposit.java`, `deposit/ManagedDeposits.java`, `mining/MineableCatalog.java`,
`mining/MineableDefinition.java`, `mining/Mineables.java`, `util/PickaxeMiningRules.java`,
new `data/britannia_mod/resources/*.json`, `util/ModTags.java`,
tests `MineableCatalogContractTest`, `MineableCatalogValidationTest`, new `ResourceDefinition` tests.

### M3 - Pure planners and guarded placement

All six `features/*Vein.java` (rewrite as pure planners), new `PlacementPlanner` and
`MaterializationService`, `commands/PopulateOresCommand.java` (delegate),
delete/retire `features/AltitudeScaledFeature.java`, new deterministic geometry unit tests.

### M4 - Identity and scalable regeneration

`block/blockrestore/BrokenBlockDataStorage.java` (schema 2, `ChunkPos` index),
`block/blockrestore/BrokenBlockData.java`, `event/BlockRestoreHandler.java` (queue + cadence),
new `DepositInstance` + per-dimension ledger, `commands/BlockCommands.java`,
`commands/MiningDebugCommand.java`, `commands/ManagedDepositCommands.java`,
legacy-fixture migration tests, 10k-debt scale tests.

---

## 9. Risks / Unknowns

1. **Rails `ore_veins` has no stable row id.** Confirmed absent from the payload
   `OreVeinFetcher` parses. Requires either a Rails change or the derived-hash fallback in §C.
   The Rails repository is WSL-only from this environment and was not inspected.
2. **GameTest baseline not established.** 617 `@GameTest` methods exist; only the 2759 JUnit tests
   were run. A headless `gameTestServer` run should be taken before M1 changes behavior, so new
   failures are attributable.
3. **Five pre-existing JUnit failures** are unexplained here (asset-hash pins and a skill-presentation
   assertion). They are outside this project's systems, but they must stay quarantined as a known
   baseline so M1+ reports do not absorb them.
4. **Source-text tests will resist correct fixes.** `MiningRestorationPolicyTest` asserts on the
   literal presence of `canBeReplaced` — the very call that causes the water/lava defect.
   `MiningFeedbackPolicyTest` and `SilverVerticalSliceTest` are similar. These must be converted to
   behavioral tests as their subjects change, not merely edited to keep passing.
5. **Retiring vanilla iron/gold from the catalogue is player-visible.** Existing chunks keep their
   vanilla ore, which will stop yielding purity ore and stop awarding Mining. Needs an owner decision
   and an announcement, not just an engineering change.
6. **Live deposit state is unknown.** Whether any production world already holds
   `/populateores`-placed veins or pending `broken_blocks` debts was not verified from here; project
   memory indicates nothing has been deployed since `e4418d6f`. A retrofit plan must confirm this
   against the live shard before assuming a clean slate.
7. **Exact 1.21.1 placed-feature keys** were read from the decompiled `OrePlacements`; the
   biome-to-feature wiring (`BiomeDefaultFeatures.addDefaultOres`) was only spot-checked. M5 must
   enumerate against the live registry.
8. **The audit is incomplete, not merely stale.** It missed an entire package. Other conclusions in
   it should be treated as hypotheses until individually re-checked, which is what this record did
   for the ones that drive M1-M5.

---

## 10. Milestone 0 Exit Criteria

| Playbook exit criterion | Verdict | Note |
|---|---|---|
| Every critical audit claim has been re-verified or corrected | **PASS** | §3; all 10 critical/high findings confirmed, two corrections recorded (audit missed `deposit/`; silica already exists) |
| No implementation has started beyond harmless test scaffolding | **PASS** | `git status --short` empty; no files created in the worktree; only `./gradlew test` was run |
| The canonical resource-definition direction is selected | **PASS** | §7A — evolve `ManagedDeposit` into `ResourceDefinition`, reference `MineableCatalog`, classpath JSON |
| The managed block strategy is selected | **PASS** | §7B — custom managed blocks for iron/gold, scheduled post-M5; legacy vanilla becomes decorative |
| Restoration timing policy is explicitly documented | **PASS** | §7D — 6 h global default retained, per-definition override added, silica 24 h |
| Rails coexistence strategy is explicitly documented | **PASS** | §7C — retained as an idempotent importer; minimal Rails API contract addition documented |

Additional decisions the playbook asked M0 to resolve:

| Question | Answer |
|---|---|
| Legacy vanilla ores decorative after cutover? | Yes (§7B) |
| Deposit id derivation for the four sources? | §7E |
| `ResourceDefinition` extends or composes the Mining catalogue? | Composes/references (§7A) |
| `SavedData` or chunk attachments? | `SavedData` re-indexed by `ChunkPos` (§7F) |

---

## 11. Recommendation

**The codebase is ready to proceed to M1**, with three amendments to the plan.

The audit's engineering conclusions hold: every critical and high-priority finding it raised is
confirmed in the current tree, several of them worse than described. The Mining catalogue is as sound
as claimed and should be preserved.

Amendments the owner should approve before M1 begins:

1. **M6 is largely already delivered.** Silica exists as a managed deposit with a tag-driven tool, a
   validate-before-mutate extraction transaction, economy identity, a downstream recipe, assets and
   GameTests. M6 should be rescoped to the four real gaps: a dedicated silica shovel item, moving
   `ManagedDeposits` to data, per-resource regeneration timing, and pinning the
   Fortune/Silk-Touch/fake-player policies. The wrong-tool defect does **not** apply to silica or
   clay — `ManagedDepositInteractionHandler.onBreak` already cancels before mutation. It applies to
   the Mining-catalogue resources.

2. **M1's wrong-tool fix should be scoped to the Mining path**, and its regression test should use the
   Britannia two-handed axe (§3.1), which is the vector that actually reaches vanilla breaking.
   A test using a bare hand or vanilla pickaxe would pass for the wrong reason, because
   `StructureProtectionHandler` and `CityGameModeHandler` incidentally block those in the common case.

3. **Coal and `/populateores clear` are M1 decisions, not M8 ones.** Coal currently manufactures free
   economy input; `clear` can synchronously generate ~1681 chunks and rewrite ~165 M blocks with no
   provenance. Both should be closed in M1 — coal removed from placement until it has a definition,
   `clear` and `undoores` disabled rather than repaired.

Recommended M1 ordering: wrong-tool gate first (it is the only finding that permanently destroys
player-facing value), then piston/explosion policy, then water/lava restoration, then shape input
validation, then the dangerous commands.

**Stopping here as instructed. No M1 work has begun.**
