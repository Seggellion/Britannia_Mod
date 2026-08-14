# Mining Mineable Master Catalog (Milestones 1–2)

Date: 2026-08-14 · Branch `patch-18` · HEAD `40fa27d2` (M1 discovery; M2 encoding added same day)

> **Milestone 2 status:** the progression below is now **encoded** in
> `src/main/resources/data/britannia_mod/mining/mineables.json` (schema 1; 27 definitions —
> 25 ACTIVE covering exactly the 29 managed blocks, plus deferred Dripstone 35.0 / Obsidian 60.0),
> loaded and validated fail-fast by `mining/MineableCatalog` + `Mineables.init()`
> (`BritanniaMod` constructor) and registry-checked at server start
> (`Mineables.validateBlockIdsResolve()`). **No break behavior is wired yet.** The JSON file is
> the machine-reviewable source of truth from here on; the "Proposed/Approved Mining req"
> columns below became the encoded `required_mining` values (custom rocks got provisional
> owner-review tiers: igneous 40.0, metamorphic 30.0, volcanic 45.0, glacial 35.0;
> high-purity silver held at Silver's 55.0 pending owner decision; per-definition `challenge`
> is seeded equal to the requirement until Milestone 4 calibration). Contract coverage:
> `src/test/java/com/seggellion/britannia_mod/mining/MineableCatalogContractTest.java` pins the
> ladder, the exact 29-block ACTIVE coverage, exactly-once resolution, NOT_APPLICABLE for
> unmanaged blocks, and the localization keys;
> `MineableCatalogValidationTest.java` pins duplicate/range/identity/malformed-reference
> rejection and data-only extensibility.

Scope: every natural rock / geological resource that is, or is proposed to be, governed by Mining.
"Managed" = recognized by `PickaxeMiningRules` and therefore in the Britannia break/drop/restore flow.
`NONE` = proven absent. `UNKNOWN` = not proven. Proposed requirements come from
`MINING_SKILL_DESIGN.md` §7/§8 and are **not implemented anywhere yet**.

Shared facts (hold for every managed row; not repeated per row):

- **Existing Mining requirement: NONE** — no skill gate exists in the repository.
- **Skill-gain difficulty input: NONE** — no Mining award exists.
- **Managed path tool:** Britannia pickaxe family (`ToolRegistry.PICKAXE`, `QualityToolItem`,
  `BritanniaPickaxeItem`); no vanilla tool-tier gate (`needs_*_tool` tags absent; custom blocks
  not in `minecraft:mineable/pickaxe`).
- **Restoration:** managed breaks are recorded in `BrokenBlockDataStorage` and restored by
  `BlockRestoreHandler` after 6 real-time hours, Overworld only, no provenance.
- **Dimensions:** Overworld in practice (single-map shard; restoration handler is Overworld-only).
  Depth/biome rules: none — veins are placed by `/populateores` wherever Rails vein rows say.
- **Tests: NONE** for every row (no mining/restoration/ore-economy test exists).

---

## A. Managed stone family (break → `GradeStoneItem` (grade 1–5, stacksTo 1) → stone trader)

| # | Display name | Registry ID | Origin | Block class | `deduceStoneType` string | Stone commodity (`stoneCommodityKey`) | Sellable today | Worldgen source | Assets (bs/model/tex) | Lang | Proposed Mining req |
|---|---|---|---|---|---|---|---|---|---|---|---|
| A1 | Stone | `minecraft:stone` | vanilla | vanilla | `Cobblestone` ⚠ | `cobblestone` (not `stone`!) | yes (as cobblestone) | vanilla worldgen | vanilla | vanilla | 0.0 |
| A2 | Cobblestone | `minecraft:cobblestone` | vanilla | vanilla | `Cobblestone` | `cobblestone` | yes | vanilla (player-made mostly) | vanilla | vanilla | 0.0 |
| A3 | Diorite | `minecraft:diorite` | vanilla | vanilla | `Diorite` | `diorite` | yes | vanilla worldgen | vanilla | vanilla | 10.0 |
| A4 | Andesite | `minecraft:andesite` | vanilla | vanilla | `Andesite` | `andesite` | yes | vanilla worldgen | vanilla | vanilla | 15.0 |
| A5 | Granite | `minecraft:granite` | vanilla | vanilla | `Granite` | `granite` | yes | vanilla worldgen | vanilla | vanilla | 20.0 |
| A6 | Tuff | `minecraft:tuff` | vanilla | vanilla | `Tuff` | `tuff` | yes | vanilla worldgen | vanilla | vanilla | 25.0 |
| A7 | Calcite | `minecraft:calcite` | vanilla | vanilla | `Limestone` ⚠ | `limestone` | yes (as limestone) | vanilla worldgen | vanilla | vanilla | 5.0 |
| A8 | Deepslate | `minecraft:deepslate` | vanilla | vanilla | `Deepslate` | **no mapping** | **NO** | vanilla worldgen | vanilla | vanilla | 30.0 |
| A9 | Cobbled Deepslate | `minecraft:cobbled_deepslate` | vanilla | vanilla | `Cobbled Deepslate` | **no mapping** | **NO** | player-made | vanilla | vanilla | 30.0 (UNKNOWN if intended) |
| A10 | Basalt | `minecraft:basalt` | vanilla | vanilla | `Basalt` | `basalt` | yes | vanilla worldgen | vanilla | vanilla | 40.0 |
| A11 | Smooth Basalt | `minecraft:smooth_basalt` | vanilla | vanilla | `Basalt` | `basalt` | yes | vanilla worldgen | vanilla | vanilla | 40.0 |
| A12 | Blackstone | `minecraft:blackstone` | vanilla | vanilla | `Blackrock` ⚠ | **no mapping** (`blackrock`) | **NO** | vanilla (Nether-native) | vanilla | vanilla | 45.0 |
| A13 | Igneous Rock | `britannia_mod:igneous_rock` | custom | `BaseOreBlock` | `Igneous Rock` | **no mapping** | **NO** | UNKNOWN (no placement path found) | ✓/✓/✓ | ✓ | UNKNOWN (data-driven later) |
| A14 | Metamorphic Rock | `britannia_mod:metamorphic_rock` | custom | `BaseOreBlock` | `Metamorphic Rock` | **no mapping** | **NO** | UNKNOWN | ✓/✓/✓ | ✓ | UNKNOWN |
| A15 | Volcanic Rock | `britannia_mod:volcanic_rock` | custom | `BaseOreBlock` | `Volcanic Rock` | **no mapping** | **NO** | UNKNOWN | ✓/✓/✓ | ✓ | UNKNOWN |
| A16 | Glacial Rock | `britannia_mod:glacial_rock` | custom | `BaseOreBlock` | `Glacial Rock` | **no mapping** | **NO** | UNKNOWN | ✓/✓/✓ | ✓ | UNKNOWN |

Stone family shared: refined item n/a; material identity = stone commodity family; smelting n/a;
crafting consumers: stone traders only (no recipe consumes `GradeStoneItem`); loot table n/a for
vanilla (managed path replaces loot; vanilla loot still applies to vanilla-tool breaks).

## B. Managed ore family (break → `PurityOreItem` (purity 1–5, stacksTo 1) → forge (6 purity = 2 ingots) or ore trader)

| # | Display name | Registry ID | Origin | Block class | `deduceOreType` string | Ingot (refined) | Material identity (`UOMetalToolMaterial`) | Vein generator (`/populateores`) | Rails seeded veins | Rails `ore/raw` row | Assets (bs/model/tex) | Lang | Approved Mining req |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| B1 | Iron Ore | `minecraft:iron_ore` | vanilla | vanilla | `Iron ore` | `minecraft:iron_ingot` | `IRON` | `VerticalVein` (`iron`) | 133 | `iron` | vanilla | vanilla | 0.0 |
| B2 | Deepslate Iron Ore | `minecraft:deepslate_iron_ore` | vanilla | vanilla | `Iron ore` | `minecraft:iron_ingot` | `IRON` | (not placed by command) | — | `iron` | vanilla | vanilla | 0.0 |
| B3 | Silver Ore | `britannia_mod:silver_ore` | custom | `BaseOreBlock` | `Silver ore` | `britannia_mod:silver_ingot` | `SILVER` | `VerticalLayeredVein` (`silver`) | **0** | `silver` | ✓/✓/✓ | ✓ | 55.0 |
| B4 | High-Purity Silver Ore | `britannia_mod:high_purity_silver_ore` | custom | `BaseOreBlock` | `High-Purity Silver ore` | **unrefinable** — forge strips `" ore"` → `"high-purity silver"`, which matches no `UOMetalToolMaterial`, so the forge rejects the item ⚠ | none (no material enum) | `/populateores high_purity_silver` accepted but **no case in the generator switch → places 0 blocks** ⚠ | 0 | none (`high_purity_silver` absent) | **✗/✗/✗** | ✓ | UNKNOWN (owner: silver 55.0 variant?) |
| B5 | Tin Ore | `britannia_mod:tin_ore` | custom | `BaseOreBlock` | `Tin ore` | `britannia_mod:tin_ingot` | `TIN` | `LayeredVein` (`tin`) | 47 | `tin` | ✓/✓/✓ | ✓ | 65.0 |
| B6 | Shadow Iron Ore | `britannia_mod:shadow_iron_ore` | custom | `BaseOreBlock` | `Shadow Iron ore` | `britannia_mod:shadow_iron_ingot` | `SHADOW_IRON` | `VerticalVein` (`shadow_iron`) | 0 | `shadow_iron` | ✓/✓/✓ | ✓ | 70.0 |
| B7 | Copper Ore | `britannia_mod:copper_ore` | custom | `BaseOreBlock` | `Copper ore` | `britannia_mod:copper_ingot` | `COPPER` | `ClusterVein` (`copper`) | 1 | `copper` | ✓/✓/✓ | ✓ | 75.0 |
| B8 | Gold Ore (vanilla) | `minecraft:gold_ore` | vanilla | vanilla | `Gold ore` | `minecraft:gold_ingot` | `GOLD` | `SnakeVein` (`gold`) | 0 | `gold` | vanilla | vanilla | 85.0 |
| B9 | Deepslate Gold Ore | `minecraft:deepslate_gold_ore` | vanilla | vanilla | `Gold ore` | `minecraft:gold_ingot` | `GOLD` | (not placed by command) | — | `gold` | vanilla | vanilla | 85.0 |
| B10 | Gold Ore (custom) | `britannia_mod:gold_ore` | custom | `BaseOreBlock` | `Gold ore` | `minecraft:gold_ingot` | `GOLD` | not in `/populateores` map (command places vanilla gold_ore) ⚠ | 0 | `gold` | **✗/✗/✗** | ✓ | 85.0 |
| B11 | Agapite Ore | `britannia_mod:agapite_ore` | custom | `BaseOreBlock` | `Agapite ore` | `britannia_mod:agapite_ingot` | `AGAPITE` | `GeodeVein` (`agapite`) | 0 | `agapite` | ✓/✓/✓ | ✓ | 90.0 |
| B12 | Verite Ore | `britannia_mod:verite_ore` | custom | `BaseOreBlock` | `Verite ore` | `britannia_mod:verite_ingot` | `VERITE` | `ClusterVein` (`verite`) | 0 | `verite` | ✓/✓/✓ | ✓ | 95.0 |
| B13 | Valorite Ore | `britannia_mod:valorite_ore` | custom | `BaseOreBlock` | `Valorite ore` | `britannia_mod:valorite_ingot` | `VALORITE` | `VerticalVein` (`valorite`) | 0 | `valorite` | ✓/✓/✓ | ✓ | 99.0 |

Ore family shared: loot tables **NONE** (custom ore blocks have no loot table — vanilla-tool break
destroys with zero drops and zero restoration); economy sale posts `category=ore, subcategory=raw,
item_name/commodity_key = display string` (identity-mismatch risk E1, discovery report §6.3);
crafting consumers of ingots: `BlacksmithCrafting` (all craftables), `WeaponRegistry`, repair (½
original ingots), smelt-recovery (½ back); ingot item models all placeholder
(`minecraft:item/iron_ingot` texture) and no ingot lang entries.

## C. Explicitly searched — NOT managed by Mining today

| # | Resource | Registry ID | Status in repo | Notes |
|---|---|---|---|---|
| C1 | Coal Ore | `minecraft:coal_ore` | exists; **not Britannia-mineable** | `/populateores coal` can place it (`LayeredVein`); a Britannia pickaxe **cannot dig it** (BreakSpeed canceled — not in `PickaxeMiningRules`); vanilla tools mine it with vanilla loot, no restoration. Rails: no coal commodity found. Design §7.3: classify, don't ungate — currently effectively outside the mining economy. |
| C2 | Vanilla Copper Ore | `minecraft:copper_ore` | exists; not Britannia-mineable | `/populateores vanilla_copper` can place it. Same posture as C1. |
| C3 | Limestone | — | **NONE** (no block) | The limestone *commodity* exists and is supplied by mined Calcite (`deduceStoneType`→"Limestone"). Preserve the alias; do not create a duplicate limestone identity. |
| C4 | Dripstone Block | `minecraft:dripstone_block` | exists in game; unmanaged | No references in mining rules/economy. Proposed 35.0 tier needs it added to the managed set (M2/M6 decision). |
| C5 | Obsidian | `minecraft:obsidian` | exists; unmanaged | Vanilla diamond-pickaxe rules apply, untouched. Proposed 60.0 tier would require managed-set membership without overriding tool rules (design §8). |
| C6 | Quartz-bearing rock | `minecraft:nether_quartz_ore` | exists (Nether); unmanaged | `quartz` stone commodity exists in Rails. No Overworld quartz block. NOT APPLICABLE unless owner adds a source. |
| C7 | Bronze | — | **NONE as material** | Rails `metal/ingots/bronze` commodity only; no ore block, no ingot item, no alloy recipe; `bronze_shield`/`chest_metal_bronze` are cosmetic names. See gap analysis. |
| C8 | Dull Copper | — | **NONE** (by design) | Vestigial unused entry in `BlockBreakUtils.ORE_TYPES` only. Tin occupies the 65.0 tier. Do not add. |
| C9 | Deepslate variants of custom ores | — | NONE | No deepslate silver/tin/etc. Project uses single ore blocks; "variants if project uses variants" (design §14) → not applicable. |
| C10 | Diamond/Redstone/Emerald/Lapis ores | `minecraft:*` | exist; placeable via `/populateores`; unmanaged | Vanilla behavior; outside UO ladder; no economy identity found. |
| C11 | Coal (deepslate) & other deepslate vanilla ores | `minecraft:deepslate_*_ore` | exist; unmanaged except deepslate iron/gold (B2/B9) | Only iron/gold deepslate forms are in `PickaxeMiningRules`. |

## D. Cross-cutting registries (evidence pointers)

- Managed-set definition: `util/PickaxeMiningRules.java` (hard-coded `state.is(...)` lists —
  **not data-driven**; M2 must replace with the definition/tag pattern per design §19).
- Type deduction / drop naming: `util/BlockBreakUtils.java` (hard-coded chains + vestigial
  `ORE_TYPES` weight map with unused `dull_copper` entry).
- Block registrations: `registry/BlockRegistry.java:2152–2189`; items `registry/ItemRegistry.java`
  (`*_ORE_ITEM` BlockItems ~2261–2283, ingots 1846–1868, `PURITY_ORE_ITEM` 1993,
  `GRADE_STONE_ITEM` 2001); creative tab 5 "Ores" `registry/CreativeTabRegistry.java:480–498`.
- Vein generators: `features/{Cluster,Layered,Vertical,VerticalLayered,Geode,Snake}Vein.java`;
  placement command `commands/PopulateOresCommand.java`; Rails fetch `util/OreVeinFetcher.java`;
  dead local loader `util/OreVeinLoader.java` + `data/britannia_mod/ore_veins.json`.
