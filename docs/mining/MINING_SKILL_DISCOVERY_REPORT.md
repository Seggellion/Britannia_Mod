# Mining Skill Progression — Discovery Report (Milestone 1)

Date: 2026-08-14 · Branch `patch-18` · HEAD `40fa27d2`
All statements below were verified by reading the referenced source, not inferred from filenames.
`UNKNOWN` marks anything not proven.

---

## 1. Existing Mining break flow

### 1.1 The actual path

The custom Mining flow is a **cancel-and-replace** interception of the NeoForge break event.
There is no mixin and no custom block `onRemove` logic in the path.

```text
player left-click (survival dig)
└─ PlayerEvent.BreakSpeed  (server; ToolInteractionHandler.onBreakSpeed)
   · Britannia pickaxe + non-mineable block → event canceled (cannot dig)
   · Britannia pickaxe + allowed block     → speed 2.0 (or 2.0 + quality*0.5)
└─ ServerPlayerGameMode.destroyBlock → NeoForge BlockEvent.BreakEvent
   └─ CustomBlockBreakHandler.onBlockBreak   (event/CustomBlockBreakHandler.java:26)
      1. requires ServerLevel + non-null player
      2. isBritanniaPickaxe(mainhand)?  (ToolRegistry.PICKAXE | QualityToolItem | BritanniaPickaxeItem)
         · not holding one → handler does nothing (vanilla break proceeds)
      3. PickaxeMiningRules.isAllowedStoneBlock / isAllowedOreBlock (util/PickaxeMiningRules.java)
         · neither → event NOT canceled... (see 1.4 quirk)
      4. event.setCanceled(true)  ← vanilla break suppressed
      5. level.setBlock(pos, fluidState.createLegacyBlock(), 2)   ← block removed server-side
      6. spawn ItemEntity drop: GradeStoneItem (stone) or PurityOreItem (ore), random grade/purity 1–5
      7. displayClientMessage("You mined ...")   (hard-coded English, actionbar)
      8. BrokenBlockTracker.recordBrokenBlock(level, pos, state, playerUUID)  ← restoration record
```

Registration: `BritanniaMod.java:208` (`ToolInteractionHandler`), `:210` (`CustomBlockBreakHandler`),
`:192` (`BlockRestoreHandler`). `BreakSpeedHandler` (event/BreakSpeedHandler.java) duplicates
`ToolInteractionHandler.onBreakSpeed` but is **imported and never registered — dead code**.
`StoneOreBreakEventHandler.old` is a retired predecessor (never compiled; `.old` extension).

### 1.2 What the flow does NOT do today

| Concern | Current state |
|---|---|
| Skill gate | **None.** No skill of any kind is consulted. |
| Skill award | **None.** No `SkillManager` call anywhere in the mining path. |
| Tool durability | **Not consumed.** No `hurtAndBreak` in the path; cancelling `BreakEvent` also suppresses vanilla durability damage. |
| Tool tier check | None beyond "is a Britannia pickaxe". `BaseOreBlock` does not set `requiresCorrectToolForDrops()`. |
| Protection checks | None in this handler. (StructureProtectionHandler/SurvivalZoneHandler operate on their own events; interaction order with BreakEvent is UNKNOWN — verify in Milestone 3.) |
| Creative policy | None. A creative player holding a Britannia pickaxe goes through the same custom drop + restoration path. |
| Fake players | Not distinguished (`event.getPlayer()` accepted as-is). |
| Localization | Break messages are hard-coded English `Component.literal`. |
| XP orbs | Vanilla XP never drops (event canceled); no custom XP. |

### 1.3 Non-Britannia-tool routes (all bypass the managed flow)

- **Vanilla tool / bare hand on custom ore blocks:** `BreakSpeed` is only restricted for
  Britannia tools, so digging proceeds; the block breaks via vanilla logic; **no loot table
  exists for any custom ore/rock block** (`data/britannia_mod/loot_table/blocks/` has none),
  so the block is destroyed with **no drops, no restoration record** — permanent node loss.
- **Vanilla tool on vanilla stone/ore (e.g. plain stone, iron ore):** fully vanilla break,
  vanilla drops, no restoration record.
- **Explosions, pistons, fluids, command `/setblock`, worldgen:** do not fire
  `BlockEvent.BreakEvent`; completely outside the system.

### 1.4 Quirk found (documented, not fixed)

In `CustomBlockBreakHandler`, when the held item is a Britannia pickaxe but the block is neither
allowed stone nor allowed ore, the code returns **before** `setCanceled(true)` — but
`ToolInteractionHandler.onBreakSpeed` has already canceled the dig for that combination, so the
state is unreachable in normal play. Ordering is load-bearing; a future gate must not rely on it.

### 1.5 Earliest safe server-authoritative denial point

**Top of `CustomBlockBreakHandler.onBlockBreak`, before step 4 (cancel) / 5 (setBlock) / 6 (drop)
/ 8 (restoration record)** — i.e. a `BlockEvent.BreakEvent` listener decision. `BreakEvent`:

- fires server-side only for player harvest in Survival/Adventure/Creative;
- is cancelable (`ICancellableEvent`), and cancellation restores/preserves the block client-side;
- precedes every mutation (block removal, loot, restoration registration).

A denial at this point leaves block, inventory, durability, restoration state, and skill state
untouched, satisfying design §10.3. A complementary `PlayerEvent.BreakSpeed` cancellation (the
pattern `ToolInteractionHandler` already uses) can suppress the dig animation for under-skilled
players as UX, but the authoritative gate is the `BreakEvent` decision. To also cover
vanilla-tool breaks of managed blocks (1.3), the gate must live in its own `BreakEvent` listener
that runs for **all** players, not only Britannia-pickaxe holders.

---

## 2. Restoration system

Package `com.seggellion.britannia_mod.blockrestore` (source dir `block/blockrestore/`).

| Aspect | Finding |
|---|---|
| Classes | `BrokenBlockTracker` (static facade), `BrokenBlockData` (pos, original `BlockState`, `brokenTime` ms epoch, breaker UUID), `BrokenBlockDataStorage` (`SavedData`, name `broken_blocks`), `BlockRestoreHandler` (tick consumer, in `event/`) |
| Persistence | Per-`ServerLevel` `SavedData` → NBT list under `blocks`; survives restart. `BlockState` serialized via `NbtUtils.writeBlockState`. |
| Key | `Map<BlockPos, BrokenBlockData>` (ConcurrentHashMap). One record per position; `add()` **overwrites** any pending record at the same position. |
| Timer | `BlockRestoreHandler.onServerTick` (`ServerTickEvent.Pre`), restores when `now - brokenTime >= 6h` **real time** (`System.currentTimeMillis`), checked every tick. |
| Scope | **Overworld only** — handler hardcodes `Level.OVERWORLD`. Records created in other dimensions would persist forever, unrestored. (Mining in other dimensions: no evidence found either way → practical impact UNKNOWN.) |
| Chunk behavior | `level.isLoaded(pos)` guard; unloaded chunks are skipped and retried each tick until loaded. **No force-loading.** |
| Occupancy / conflict policy | **None.** `setBlockAndUpdate(pos, originalState)` unconditionally — overwrites any player-placed replacement block; does not check for entities/players standing in the cell. |
| Duplicate prevention | By map key only. No idempotency beyond position uniqueness. |
| Provenance | **None.** Any allowed block broken with a Britannia pickaxe is recorded — including player-placed cobblestone/stone. There is no worldgen/natural-vs-player-placed distinction anywhere in the mod for these blocks. |
| Restart behavior | Records persist via SavedData; timer is wall-clock so downtime counts toward the 6h. |
| Eligible blocks | Exactly what `CustomBlockBreakHandler` records: allowed stones + allowed ores broken with a Britannia pickaxe. |
| Player feedback | Breaker (if online) receives "You hear a cave collapse, N blocks fallen". |
| Admin tooling | `/brokenblocks list|destroy <id>|destroy_all` (`commands/BlockCommands.java`, permission 2). |
| Tests | **None found** (no unit or GameTest references `blockrestore`). |

### 2.1 Exploit interactions relevant to Mining (documented for M3/M7)

1. **Place-break loop is currently live:** place cobblestone → break with Britannia pickaxe →
   receive a `GradeStoneItem` → the placed block restores after 6h. Repeatable item generation
   today; would become a skill-gain exploit the moment Mining awards attach to this path.
2. **Pending-restore clobber:** if a player places an allowed block at a position with a pending
   restore and breaks it, `recordBrokenBlock` overwrites the original record — the original ore's
   state is lost and the *replacement* block is what restores.
3. **Restoration overwrite:** a player structure built into a pending-restore cell is silently
   overwritten at restore time.

---

## 3. Skill engine

`com.seggellion.britannia_mod.skill.SkillManager` — Rails-authoritative, server-side.

| Aspect | Finding |
|---|---|
| Storage | In-memory `PLAYER_SKILLS: Map<UUID, PlayerSkills>` (`Map<String, Float>`), seeded at login, cleared at logout. **Rails is the persistence layer** (no NBT/attachment/scoreboard). |
| Skill defs | `SKILL_DEFS` fetched once from Rails `SKILL_CONFIG` endpoint: `slug, name, max_value, skill_difficulty_modifier, gain_on_success, gain_on_failure`. Fallback def until loaded: max 100, modifier 1.0, gainOnSuccess=true, gainOnFailure=false (deliberately matches seeded Rails rows). |
| Load states | `SkillDataState { NOT_LOADED, LOADING, AVAILABLE, UNAVAILABLE }` per player; login seeds LOADING then async-fetches; failure → UNAVAILABLE. `getSkillSnapshot(uuid, skill)` exposes state+value. |
| Authoritative read | `SkillManager.getSkill(player, "mining")` / `getSkillSnapshot` — server memory only; client never supplies values. |
| Gain API (roll) | `trySkillGain(player, slug, success)` → `trySkillGainCapped(..., cap)`: gain chance = `((100 − current)/100) × difficultyModifier`; on success value += **0.1** (float), capped by `min(def.max, activityCap)`; chat message in UO font/teal; `sendSkillSync`; async `postGain` → Rails `SKILL_GAIN`. |
| Gain API (direct) | `awardSkillGain(player, slug, amount)` — deterministic award (Farming uses this with its own modifier stack); same sync/persist path. |
| Confirmed-value API | `applyConfirmedValue` — records a value Rails already committed (Guildmaster training purchase), no POST back. |
| Admin | `setSkillAdmin` → Rails `SKILL_SET`. |
| Precision / cap | 0.1 gain unit, float storage; cap = Rails `max_value` (Mining row: 100.0; schema default 120 is the powerscroll cap, applied separately server-side in Rails). |
| Cooldowns | None in `SkillManager`. Anti-spam is per-caller (e.g. `BlacksmithCrafting.acceptAttempt` per-tick dedup; `TrainingDummyService` uses `trySkillGainCapped` with an activity cap of 30). |
| Client sync | `SkillSyncPayload` (state, revision counter, admin bypass flag, values map) on login/respawn/dimension change/gamemode change/every gain; skipped for connections without the channel (GameTest mock players). Client cache: `ClientSkillTable`. |
| Difficulty inputs | **Per-skill only** (Rails `skill_difficulty_modifier`). There is no per-action/material difficulty input in `trySkillGain`; Farming implements per-material difficulty in mod code (`FarmingSkill.award`: base gain × current-skill band × crop tier × diversity × repetition) feeding `awardSkillGain`. |
| Mining registration | **Rails-side: EXISTS.** `UoSkillRoster` (Rails `app/services/uo_skill_roster.rb`) seeds "Mining" (slug `mining`, max 100.0, defaults: modifier 1.0, gain_on_success true, gain_on_failure false). Mod-side: no code references `"mining"` as a skill today. |
| Trainer | Rails `db/seeds/guildmaster_definitions.rb`: guild `miner` ("Miner") teaches **Item Identification, Mining** — the existing Guildmaster purchase flow (Rails-first commit + `applyConfirmedValue`) covers Mining with no new work. |
| Unavailable policy (precedent) | Farming (`FarmingCultivationGate`): state ≠ AVAILABLE → deny with dedicated message; FakePlayer → `NON_PLAYER_POLICY` deny; creative/permission-level admin → `APPROVED_BYPASS` (no gain). This is the repository-standard skill-gate pattern to mirror. |
| Tests | `src/test/.../skill/SkillManagerCappedGainTest.java` (capped-gain math), `GuildTrainingPurchaseGameTests`. No Mining tests. |

### 3.1 Calibration pre-analysis (for Milestone 4; no changes made)

With the stock formula (gain-on-success only, chance `= (100−c)/100 × m`), expected activations
for 0→100 at modifier `m = 1.0`:

```text
E = Σ_{v=0.1..100 step 0.1} 100/(m·v) = (1000 · H_1000)/m ≈ 7,485/m  activations
```

- `m = 1.0` (current Rails default for Mining) → ≈ 7,485 activations — **too fast** for the
  18,000 target.
- `m ≈ 0.416` → ≈ 18,000 activations, inside the 17,100–18,900 band.

Because `skill_difficulty_modifier` is **per-skill data in Rails**, Mining can be calibrated
without touching any other skill or any global formula. Caveats for M4: the harmonic-sum model
assumes every qualifying activation rolls (true if Mining uses `trySkillGain` directly), and the
tail (99→100) alone costs ≈ 2,400/m expected activations — per-material difficulty shaping (the
Farming pattern) would change this arithmetic and must be re-derived against whichever award path
M4 selects. This estimate is a design aid, not a decision.

---

## 4. Worldgen

- **Mechanism:** admin command `/populateores [ore|clear|region ...]`
  (`commands/PopulateOresCommand.java`, permission 2). Vein definitions are fetched from Rails
  (`OreVeinFetcher` → `ORE_VEINS` endpoint; per-shard; fields `ore_type,x,y,z,radius,rotation,region`).
  There is **no chunk-generation feature**; ore exists where an operator has run the command.
- **Generators** (`features/`): `ClusterVein` (copper, verite), `VerticalVein` (iron, valorite,
  shadow_iron), `SnakeVein` (gold), `GeodeVein` (agapite), `VerticalLayeredVein` (**silver**),
  `LayeredVein` (coal, tin). Silver already has the design-preferred vertical-layered shape wired.
- **`/undoores`** restores from an in-memory (non-persistent) original-blocks map.
- **Dead code:** `util/OreVeinLoader` + `data/britannia_mod/ore_veins.json` are loaded at startup
  (`BritanniaMod.java:129`) but `getOreVeins()` has **zero consumers** — the JSON file is vestigial;
  Rails is the live vein source.
- **Rails seed data** (`db/seeds/ore_veins.rb`, region-tagged, Britain region): tin ×47, iron ×133,
  copper ×1. **No silver/gold/coal/shadow_iron/agapite/verite/valorite veins seeded.** The
  `ore_veins` table supports any `ore_type` string; live-DB contents beyond seeds: UNKNOWN.
- `/populateores` also supports placing vanilla `coal`, `diamond`, `redstone`, `emerald`, `lapis`,
  `vanilla_copper` — none of which are Britannia-mineable (see catalog).

---

## 5. Refining, blacksmithing, Bronze

- **Ore → ingot:** right-click a `SmallForgeBlock`/`LargeForgeBlock` with a `PurityOreItem`.
  The forge accumulates purity per ore type; every 6 purity → **2 ingots** dropped
  (`SmallForgeBlockEntity.addPurity`). Purity is 1–5 random at mining. **No skill is consulted or
  gained during refining** (no Mining, no Blacksmithy). Design §17: document as future parity
  hook; do not expand scope.
- **Material metadata:** `UOMetalToolMaterial` enum — iron (vanilla ingot), gold (vanilla ingot),
  shadow_iron, copper, tin, silver, agapite, verite, valorite (custom ingots), each with a
  `SimpleTier`. This is the material identity used by blacksmith crafting, repair, smelt-recovery,
  and `WeaponRegistry.createWeapon`.
- **Blacksmithing:** `BlacksmithCrafting` — craftables from `data/britannia_mod/blacksmithing/craftables.json`,
  generic `"ingot"` ingredient satisfied by whichever supported metal is in the offhand; gates on
  `blacksmithy` skill; gains via `trySkillGain` exactly once per accepted attempt.
- **Bronze evidence (design §6.3 audit):**
  - Mod: **no** bronze ore block, **no** bronze ingot item, **no** Copper+Tin alloy recipe, **no**
    `UOMetalToolMaterial.BRONZE`. Only cosmetic uses: `bronze_shield` craftable (consumes generic
    ingots; a UO item name, not a material) and decorative `chest_metal_bronze`.
  - Rails: `CommoditySeeder` seeds `metal/ingots/bronze` (base 6.5, priced between copper 6.0 and
    iron 8.0) but **no `ore/raw/bronze`**.
  - `BlockBreakUtils.ORE_TYPES` contains a vestigial `"dull_copper"` entry in an **unused** map
    (no call site reads it) — not a live Dull Copper implementation.
  - **Verdict: Bronze is economy-recognized as a refined metal only; it is not mineable and has
    no production path. Per design §6.3, do not create a Bronze ore. Whether to add a Copper+Tin
    alloy production path is out of Mining scope** (recorded in gap analysis as
    OWNER/ARCHITECTURE DECISION REQUIRED only if bronze ingots must become obtainable).

---

## 6. Economy / material flow

### 6.1 Mod → Rails sale payloads (`economy/ServerEconomyService.java`)

- `PurityOreItem` sale: `item_name` = `commodity_key` = `getOreType(stack)` — a **display string**
  such as `"Silver ore"`, `"High-Purity Silver ore"`; `category="ore"`, `subcategory="raw"`,
  plus `purity`.
- `GradeStoneItem` sale: stone type normalized via `CommodityMappings.stoneCommodityKey`
  (supported set: cobblestone, stone, andesite, diorite, granite, tuff, basalt, blackstone,
  limestone, quartz); unmappable types are **rejected client-side of the sale** (`return false`);
  `category="stone"`, `subcategory="blocks"`, `commodity_key="stone|blocks|<name>"`.

### 6.2 Rails commodity identities (`app/services/commodity_seeder.rb`, current `banking` tip)

- `ore/raw`: tin 2.0, copper 3.0, iron 4.0, **silver 6.0**, gold 8.0, shadow_iron 6.0,
  agapite 10.0, verite 16.0, valorite 24.0 (all nine approved metals present).
- `metal/ingots`: tin, copper, **bronze**, iron, silver, gold, shadow_iron, agapite, verite,
  valorite.
- `stone/*`: cobblestone (rubble), stone (common), andesite/diorite/granite (igneous),
  tuff/basalt/blackstone (volcanic), limestone (sedimentary), quartz (mineral).

### 6.3 Identity mismatches detected (flagged; no fix in discovery)

| # | Mismatch | Evidence | Consequence |
|---|---|---|---|
| E1 | Ore sale `item_name` `"Silver ore"`/`"silver_ore"` vs Rails `ore/raw` `item_name` `silver` | `SaleTransactionProcessor.normalized_commodity_keys` strips `_log/_wood/_planks` but **not** `_ore`; category+subcategory present → exact lookup required → `MissingCommodity` on miss | Ore sales against seed-shaped rows would fail. Live-DB rows may differ (legacy identities): UNKNOWN. Verify before M8. |
| E2 | Stone sale subcategory `blocks` (and identity key `stone|blocks|x`) vs Rails stone subcategories `rubble/common/igneous/volcanic/sedimentary/mineral` | `ServerEconomyService:498` vs `CommoditySeeder` | Same exact-lookup failure risk. UNKNOWN live-DB shape. Verify before M8. |
| E3 | Mining vanilla **Stone** yields stone type `"Cobblestone"` (`BlockBreakUtils.deduceStoneType`) | `util/BlockBreakUtils.java:52` | The `stone` commodity can never receive mined supply; everything funnels into `cobblestone`. |
| E4 | Blackstone yields `"Blackrock"`; Deepslate/Cobbled Deepslate/4 custom rocks yield names with **no stone commodity mapping** | `BlockBreakUtils` vs `CommodityMappings.SUPPORTED_STONE_COMMODITIES` | These mined stones are **unsellable** today. |
| E5 | Calcite yields `"Limestone"`; no limestone block exists | `deduceStoneType` | Calcite **is** the limestone supply source — a naming aliasing to preserve, not duplicate. |
| E6 | "Silver" is also a currency denomination (copper/silver/gold coins) | `docs/vendor-trader-economy/ECONOMY_RULES.md` | Coin-silver ≠ commodity-silver. Mining must reuse the *commodity* identity (`ore/raw/silver`, `metal/ingots/silver`) and never touch the currency concept. |

City precious-metal supply columns (`CityCommodity.recalculate_precious_supply`) track item_names
`copper|silver|gold` — consistent with E1's expectation that the canonical ore identity is
`silver`, not `silver ore`.

---

## 7. Silver inventory (gap summary — full classification in gap analysis)

Present today: `silver_ore` block+item+assets+lang; `high_purity_silver_ore` block+item+lang
(**no assets**); `silver_ingot` item + model (**placeholder iron texture, no lang**);
`UOMetalToolMaterial.SILVER` tier; forge refining (generic purity path); `VerticalLayeredVein`
generator wired for `silver` in `/populateores`; Rails `ore/raw/silver` + `metal/ingots/silver`
commodities; Rails `ore_veins` accepts silver veins.

Missing: Mining requirement (no gate exists for anything), silver vein rows in Rails seeds,
in-world silver (until `/populateores silver` runs against real vein data), dedicated silver
ingot texture + lang, high-purity ore assets, loot tables/tags (absent for all custom ores),
any tests. No duplicate silver identity found anywhere — extend, don't recreate.

---

## 8. Assets

- **Complete block asset sets** (blockstate+model+item model+texture) for: copper, tin, silver,
  shadow_iron, agapite, verite, valorite ores; igneous/metamorphic/volcanic/glacial rocks.
- **Missing entirely:** `gold_ore` (custom) and `high_purity_silver_ore` — registered blocks with
  lang entries but no blockstate/model/texture → would render as missing-texture blocks if placed.
- **Ingots:** all 7 custom ingot item models exist but every one references
  `minecraft:item/iron_ingot` as its texture (placeholders); no ingot lang entries.
- **Mined items:** `purity_ore_item` model + `purity_ore_1..5` textures (custom_model_data
  2001–2005; one texture set shared by all metals — purity-differentiated, not metal-differentiated);
  `grade_stone_item` + `stone_grade_1..5` equivalents. No lang entries for either.
- **Sounds:** `britannia_mod:mining1`, `mining2` sound events registered (used by the retired
  handler; **not** played by the current break flow).
- **Raw asset area `C:\projects\britannia\raw fiels`:** no silver/ore/ingot source art. Possibly
  reusable for future rocks: `FischVogel's Stone Set.zip`, `Stone Textures (1).zip`,
  `cave_floor_models*.zip`, `cave_wall/`. Third-party `supplementaries` silver door/goblet assets
  exist but are another mod's IP — do not reuse without license verification. Nothing was modified.

---

## 9. Tests & commands relevant to Mining

- Unit: `./gradlew.bat test` (JUnit 5; `src/test`); precedent `SkillManagerCappedGainTest`.
- GameTests: `./gradlew.bat runGameTestServer --no-configuration-cache`
  (`src/main/.../gametest/*GameTests.java`, namespace `britannia_mod`; jar excludes gametest classes).
- **No existing test covers mining, block restoration, or ore economy.**
- Admin commands: `/populateores`, `/undoores`, `/brokenblocks` (all permission 2).

## 10. Commands run during discovery

```text
git status --short / branch --show-current / rev-parse HEAD / worktree list --porcelain / remote -v
(read-only file inspection throughout; WSL-side read-only greps of the Rails repo)
```

No production code, assets, or data files were modified. No builds or tests were executed
(discovery required none; nothing to verify by execution yet).
