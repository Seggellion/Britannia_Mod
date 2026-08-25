# Patch 18 Notes Research & Evidence Report

## Purpose and conclusion

This report supports `PATCH_18_NOTES.md`. It records the Git boundary, final-state behavior, content counts, evidence inspected and claims intentionally omitted from the public release notes.

The final branch state, rather than commit titles or earlier design documents, was treated as authoritative. This matters in Patch 18 because at least three substantial implementations changed direction before release:

1. banner crafting was added and then deliberately removed;
2. locally generated managed deposits were implemented and then deleted in favor of Rails-only deposit placement;
3. the standalone grape-vine system was retired in favor of the Farming plot's perennial arbor.

## Comparison baseline

### Selected baseline

The pre-Patch-18 baseline is:

```text
62df1dc97c5113a86f9c0f258cb90538f31efe89
Add refill fishing rod barrel at 5213 66 8912 (#400)
2026-06-08 21:42:24 -0700
```

This is not an arbitrary comparison to a recently moved `main` branch:

- `origin/main`, `origin/0.1.8`, `origin/seggellion` and `origin/HEAD -> origin/main` all point directly at this commit.
- The commit is the exact merge base between the selected baseline and Patch 18 HEAD.
- It is an ancestor of Patch 18 HEAD, so the comparison is a clean forward range.
- The first unique Patch 18 commit is `65039307f5f4215338350908247af5a91d04b0fa`, `Implement farming for patch 18 (Refs #420)`, dated 2026-07-13.

### Patch 18 HEAD used

```text
1c1d5c17eaf2e428b21704ce466d47ca9b26529d
chore(structure): ship the re-exported large_patio floor
2026-08-23 09:47:55 -0700
```

### Comparison range

```text
62df1dc97c5113a86f9c0f258cb90538f31efe89..1c1d5c17eaf2e428b21704ce466d47ca9b26529d
```

### Concurrent HEAD/worktree transition

At the beginning of this audit, local `patch-18` was at `546a8b30973dabf7d77c7ecfae6900e76bd69207` and the following two files had uncommitted modifications:

```text
src/main/resources/assets/britannia_mod/structures/large_patio.nbt
src/main/resources/data/britannia_mod/structures/large_patio.nbt
```

Those changes were not initially counted in the committed release range. While the audit was in progress, the repository advanced independently to `1c1d5c17`, which committed those two re-exported patio files. The final report was retargeted to that newer HEAD. At the documentation stage, no source or asset changes remained uncommitted; only the two requested Markdown files were newly created.

## Patch scale and history shape

The selected range contains:

- **445 commits**
- **23 merge commits**
- **6,229 changed paths**
- **610,867 text insertions and 11,123 text deletions** reported by `git diff --numstat`
- **5,502 added, 655 modified, 66 deleted and 6 renamed paths**
- **1,193 changed production Java files**, of which 1,013 are added
- **3,981 changed runtime asset paths** under `src/main/resources/assets`
- **187 changed runtime data paths** under `src/main/resources/data`
- **526 changed unit-test or GameTest paths** when production GameTest classes and `src/test` are combined

The raw scale needs context. It includes 352 banner source/review paths under `content/`, 23 documentation paths, 285 `.old` files across the range, generated models, test suites and large authored model JSON. It should not be presented publicly as 6,229 independently playable additions.

The branch preserves substantial merge history rather than presenting one release squash. Major integration merges include banking/farming/blacksmithing/banners/shrines, villa work, Guildmasters, quests, house farm plots, wild resources, Grabby Hands, vendor economy and new assets. Later mining, housing, vegetation and release corrections were committed directly along the first-parent line. There are also “rescued work” and consolidation-fix commits after the broad merges, which is why final-tree inspection was necessary.

## Research method

The audit used the following evidence classes:

- `git status`, `git branch`, `git merge-base`, `git rev-list`, `git log`, `git show` and range diffs;
- final Java registrations and runtime services;
- final JSON catalogues, tags, recipes, loot tables and worldgen policy;
- final blockstates, models, textures, animations, geometry and sounds;
- localization strings where they expose a player-visible screen or action;
- unit tests and GameTests where they state intended behavior and exercise the final implementation;
- earlier Markdown only as supporting context, never as authority over final code or assets.

## Major commit groups investigated

This is a feature-oriented index, not a 445-commit changelog.

| Area | Representative history | Final-state result |
|---|---|---|
| Farming | `65039307`, farming integration in `632570fe`, later skill, identification and grape corrections | 67 crop definitions, skill gates, environmental growth, specialized harvests and one grape-arbor system |
| Blacksmithing | `9acfb7e2`, blacksmithing integration in `632570fe` | 202-entry final catalogue, crafting/repair/smelt workflows and registered outputs |
| Banking and service NPCs | banking branch history integrated through `632570fe` | Complete Bank hub/box/balance/cheque flows plus durable transfers and persistent service NPC posts |
| Banners and dyeing | banners branch in `632570fe`, content intake through final catalogue | 35 complete banners, dye preview/application and multiblock placement; crafting removed by `59c96ef8` |
| Shrines and monoliths | shrines branch in `632570fe`, later `666c8706` asset replacement | Nine approved shrine variants; three provisional monolith variants |
| Villa and houses | `084af544`, `009498ea` through `c2bc44f3`, final structure commits through `1c1d5c17` | 10 deed styles, new large homes, owner build rights, restart persistence and rebuilt structures |
| Guildmasters | `2accdbd1` | Registry-driven guild training with live quotes and a 40.0 ceiling |
| Client branding | `314ec504`, later `8bf7a9de` | Branded Version 18 title experience and menu music |
| Quest remediation | `e94e2b1e` plus `17cda9e2` through `02dcc148` | Persistent escorts, refusal reasons, retry, server objectives and stable quest-giver identity |
| House farm plots | `8fc89ad2`, rescued follow-up `e33488f0` | Persistent assigned crops/flowers integrated with house ownership |
| Wild resources | `6c2295a8` | Persistent Sulphurous Ash and black-lipped oyster nodes |
| Grabby Hands | `d31d71ef`, `40fa27d2` crate enrollment | Movable furniture/containers and placeable loose items with protection and state preservation |
| Vendor/trader economy | `7a63ca79`, later buyback and policy corrections | Live purchase quotes, accepted-commodity buyback, provenance and durable sale handling |
| New assets | `fe023a7c`, containing `3075a105` through `db204622` | Decorative props, multiblocks, crates, well, ladder, birds, dummy, textiles, cases, moongate, fence and stalls |
| Mining/resources | `e463657a` through `72dce0d6` | Skill progression, managed deposits, restoration, Rails authority, vanilla mineral suppression and resource loops |
| Vegetation and terrain visuals | `90555b4a`, `104ae602`, `a5f20e1e`, `99485fa7` | Managed regrowth, 32 stone variants, sand variants and cycling flagstone |
| Reliability corrections | `4ae2b925`, `9baa6e42`, `7ce2de4e`, `e4418d6f` and later fixes | External waits removed from tick paths, bounded population work and contained log floods |

## Headline feature evidence

### 1. Farming and floriculture

Final evidence:

- `farming/CropRegistry.java` registers exactly **67** crop definitions; `FarmingSkillRequirementTest` and `FarmingSkillProgressionCloseoutTest` assert the final count.
- `CropDefinition`, `CropGrowthContext`, `CropEnvironmentRules`, `CropQualityCalculator` and `FarmingBlockEntity` carry hydration, nutrient, climate, altitude, growth and quality behavior.
- `FarmingSkillRequirementValidator` and `FarmingCultivationGate` apply server-side skill checks.
- `FarmingPlantingItemPresentation` is viewer-specific and obscures an unfamiliar seed until the viewer meets its requirement.
- `CropHarvestTool`, `GrainHarvestTools` and `RootCropShovelTools` support crop-specific tools.
- `TallCropSupport` handles corn, bananas, hops and the grape arbor.
- `CropRegistry` describes grapes as a perennial tall crop with an 8–12 yield; `GrapeVarietyAgronomy` applies variety-specific nutrient, climate, altitude and hydration data.
- `LegacyGrapeVineMigration` converts old vines into the new plot system or safely refunds a vine that has no plot.
- `FlowerRegistry` defines exactly **7 species** and **23 color definitions**, with weighted per-species palettes and persistent server-selected color.
- `HouseFarmPlotBlockEntity`/house plot handlers preserve assignment after harvest and clear it with a hoe.

Boundaries:

- The repository guarantees two built-in Concord varieties, but the live grape roster is loaded from the shard bootstrap. No fixed public variety count is defensible from this repository alone.
- Several vanilla compatibility entries are included in the 67 crop definitions. The public notes call these “crop definitions,” not 67 wholly original item species.

### 2. Mining and managed resources

Final evidence:

- `data/britannia_mod/mining/mineables.json` contains **27 active Mining definitions**.
- `data/britannia_mod/resources/resources.json` contains **29 managed resources**: the 27 Mining resources plus clay and silica sediment beds.
- Metal/mineral definitions are iron, coal, silver, tin, shadow iron, copper, gold, agapite, verite and valorite.
- Stone definitions are stone, cobblestone, calcite/limestone, sandstone, diorite, andesite, granite, tuff, deepslate, cobbled deepslate, basalt, blackstone, igneous rock, metamorphic rock, volcanic rock, glacial rock and dripstone.
- `PlacementPlanner` and the shape planners support vertical, layered, vertical-layered, cluster, snake, geode and sedimentary-lens shapes.
- `MiningBreakGate`, `PickaxeMiningRules` and the mining catalog enforce tool and skill requirements and award Mining once per accepted break.
- `DepositLedger`, restoration debt/scheduler code and materialization services persist deposit identity and restoration.
- Most resource definitions restore on a six-hour schedule; silica uses 24 hours. The public notes omit exact timers because deployment configuration and the player-facing promise are more important than the implementation default.
- `PurityOreItem` and `GradedStoneItem` carry economic material quality.
- provenance handling allows player-placed blocks to be removed without treating construction as a fresh deposit.
- managed mutation policies refuse fake-player automation and ignore Fortune/Silk Touch for managed extraction.
- `SmallForgeBlockEntity`/forge smelting code alloys copper and tin into bronze.
- Three new furnace recipes produce raw glass from silica, plaster from limestone and common stone from graded cobblestone.

Final authority correction:

- `263f9b3f` briefly implemented local natural iron/gold/copper generation.
- `62ad3753` subsequently deleted the entire `resource/natural` package and chunk-generation listener, removed natural distribution data, and made Rails the only source of managed deposit instances.
- Final `ResourceCatalog` rejects a stale `natural` data block by name.
- The server still owns deposit geometry/materialization and restoration once Rails supplies a row.
- `vanilla_feature_policy.json`, NeoForge feature removal and `NoiseVeinOreAuthority` suppress Overworld vanilla economic minerals and remove ore results from Minecraft's noise veins.

This is why the public notes describe **server-curated** managed deposits and explicitly reject a hidden second natural distribution.

### 3. Blacksmithing

Final evidence:

- `data/britannia_mod/blacksmithing/craftables.json` contains **202 recipes with 202 unique outputs**.
- Final category counts are:

| Category | Count |
|---|---:|
| Armor | 34 |
| Axes | 15 |
| Bashing | 17 |
| Bladed | 69 |
| Cannons | 4 |
| Helmets | 17 |
| Miscellaneous | 12 |
| Polearms | 16 |
| Shields | 15 |
| Throwing | 3 |

- Axes, Bashing, Bladed, Polearms and Throwing total **120 weapon recipes**; tests assert this exact total.
- `BlacksmithItemRegistry` dynamically registers final output items and model paths.
- `BlacksmithMenu`, its screen and crafting services expose Repair, Smelt, Shields, Armor and Weapons workflows.
- Final services require a hammer, nearby anvil, skill and recipe ingredients; resolve success, failure and exceptional quality server-side; and carry material/quality/origin metadata.
- Repair consumes matching material; smelting recovers a bounded portion of eligible work.
- `CraftableDef.catalogue` marks the final JSON catalogue entries non-provisional. The “provisional data” tooltip remains only for compatibility definitions constructed through the legacy fallback constructor.

### 4. Banking and service NPCs

Final evidence:

- `BankMainScreen`, `BankBoxScreen`, `BankBalanceScreen` and `BankChequeIssuanceScreen` implement the visible hub, vault, balance and cheque workflows.
- Localization and screen code expose Open Bank Box, Balance, Create Cheque, Deposit All Coins, denomination withdrawal, item drag-to-vault, drag-to-pack and cheque cashing.
- `BankItemEligibility` rejects currency through the item-vault path, quest-bound content, unsupported origins and invalid nested content.
- `BankItemWeight` includes nested contents and enforces a shared nesting-depth guard.
- Deposit, withdrawal, currency and cheque proxy services use prepare/confirm/cancel boundaries and durable receipts, with startup reconciliation for interrupted work.
- `ServiceNpcEntity`, service registries, assignment caches, spawn operations and reconcilers preserve service-NPC identity, post and assignment across restarts.
- Rails/client calls use dedicated server credentials and HMAC request signing; banking and NPC I/O are dispatched off the server tick.

Boundary:

- Banking requires a bank-capable service NPC and live authenticated shard services. The repository proves the client/server capability, not the deployment's current NPC roster or account data.

### 5. Guildmasters and training dummies

Final evidence:

- `GuildmasterCapability` reads active `guild.train` capability and taught skills from the live service-NPC registry; it does not hard-code the guild roster.
- `GuildTrainingQuote` caps training at the lower of 40.0 and the skill's own cap; one gold buys one tenth of skill under the implemented rule.
- Purchase services revalidate NPC range, identity, capability, taught skill, current skill and payment before committing.
- `GuildmasterTrainingScreen` displays current value, cap, available lesson and price.
- `TrainingDummyService` accepts fists or a supported weapon discipline, applies a 60-tick cooldown, caps the matching combat skill at 25.0 and has a 10% Tactics-attempt chance.
- `TrainingWeaponClassifier` maps empty hand to Wrestling, fencing weapons/polearms to Fencing, axes/swords to Swordsmanship and maces/bashing weapons to Mace Fighting.
- The animated multiblock dummy consumes valid strikes without damaging the training weapon.

Boundary:

- The code comments reference twelve seeded guild types, but registry rows are external and can change without a mod build. The public notes intentionally make no fixed guild-count claim.

### 6. Housing

Final evidence:

- `HouseStyle` has exactly **10** values:
  - Wooden House
  - Field Stone House
  - Wood and Plaster House
  - Thatched Roof Cottage
  - Small Brick House
  - Stone and Plaster House
  - Two-Story Villa
  - Large Patio
  - Stone Keep
  - Castle
- `0007e400` records these ten deed rows; Architect GameTests verify that all ten resolve to registered deed items.
- Added structure NBT exists for the villa, patio and keep. Six small structure exports were modified/rebuilt. Castle existed at the baseline and is not described as a new Patch 18 building.
- `EconomicVendorPurchaseService`, Architect menu/catalog code and tests route deeds through normal city pricing, availability and exact-currency purchase.
- `HouseBuildRights` grants an owner placement/break rights inside their own region while preserving ordinary survival mechanics and denying another owner.
- House infrastructure and perimeter foundations remain protected; interior floor foundations can be removed.
- `StructureRegionCodec` and `StructureRegionRehydrator` restore persisted house regions from Rails after restart.
- `HousePrivacyHandler` performs atomic public/private changes, door locking, key issuance/removal and sign reset.
- lockable-door logic makes a lock outrank redstone.

Boundary:

- A house row created before region persistence and lacking a `structure` payload cannot be reconstructed. The public notes therefore say **newly recorded** house regions survive restart rather than claiming retroactive restoration for every legacy house.
- Region restore requires Rails. If the shard cannot reach Rails after its bounded retries, regions remain unresolved and the failure is logged.

### 7. Grabby Hands and interiors

Final evidence:

- `tags/block/grabby_movable.json` contains exactly **35** movable types.
- `tags/item/grabby_placeable_items.json` contains exactly **30** loose items that can be placed in a generic world host.
- `GrabbyGesture` defines pickup as sneak-right-click with both hands empty; normal right-click still uses the object.
- `GrabbyPickupTransaction` and `GrabbyPlacementTransaction` preserve enrolled state and use one atomic mutation boundary.
- Containers preserve contents and lock state; open, nested, protected or unsafe cases are refused.
- Crates were temporarily excluded, then deliberately enrolled by `40fa27d2`; all three crate sizes are in the final tag.
- `GrabbyDestructionService` requests confirmation for an enrolled object hit with a recognized axe and respects locks/protection before prompting.
- House and city authorization are checked; Adventure mode is not weakened globally.

### 8. Vendors, traders, city population and quests

Final evidence:

- `EconomicVendorPurchaseService` obtains live catalog rows/quotes, revalidates price and stock, settles exact coin value and applies product metadata.
- `EconomicBuybackCatalogService`, `TraderCommodityFilter` and accepted-commodity policy use Rails buyback authority rather than retail price or local category guessing.
- `EconomicTraderSaleReceiptStore` and reservation/reconciliation services close the crash window around trader sales.
- `TownPersonPopulationManager` reconciles townspeople toward server-provided regional targets with bounded spawn/despawn work.
- economic NPC posts and legacy spawn blocks migrate toward the same authoritative post lifecycle.
- `QuestProxyService`, `QuestObjectiveWatcher`, `QuestJournalRefresh` and quest GameTests prove persistent escort assignment, refusal reasons, bounded turn-in retry, server-authoritative objectives and UUID-based quest-giver identity.

Boundary:

- Repository rollout documents contain several counts for seeded, material-backed and excluded RunUO products, and those counts changed as later housing/resource work landed. Live Rails stock is external. The public notes describe the behavior and expanded catalogue integration without publishing a fragile product-count claim.

### 9. Banners, dyeing and shrines

Final banner inventory:

- **35** banner definitions, all with `content_status: complete`
- sizes: **15** at 1×1, **14** at 1×2, **6** at 2×2
- **4** fabric materials: cotton, linen, silk and wool
- **7** pigment definitions: six common pigments plus `ice_blue` marked `development_special`
- **2** mounts: brass and iron
- wall-parallel and wall-perpendicular placement according to each authored definition/profile

Final behavior evidence:

- `BannerItem`, item state and layered render code persist definition, material, color, pigment and mount.
- banner placement planners validate orientation and the complete footprint before atomically placing up to a 2×2 structure.
- preview/render payloads provide a client ghost and placed render data.
- dye-tub item/loading state, preview validation, screen/session and application services provide a server-confirmed dye transaction.
- all active definitions are exposed as configured creative stacks when the server-side datapack registry is available.

Crafting correction:

- `6803f5ee` added banner crafting.
- `59c96ef8` is explicitly titled `revert(banners): remove unintended banner crafting system` and deletes the recipe serializer, pattern item, fabric/mount crafting tags and banner recipes.
- No survival banner recipes exist in the final tree.

Shrines/monoliths:

- `ShrineMonolithDefinitions` has **nine approved shrine variants**: Honesty, Compassion, Valor, Justice, Sacrifice, Honor, Spirituality, Humility and Chaos.
- Shrines are a protected 2×1×2 structure with one shared geometry and variant textures; the interior decorator cycles enabled variants.
- The three monolith variants are named diagnostic/provisional and have `ContentStatus.PROVISIONAL`. They were excluded from the public content list.
- Shrine and monolith items are creative entries; no survival acquisition path was found.

### 10. New assets, creatures and world atmosphere

Registered Patch 18 world-building content includes:

- low-risk props: cloth bolt, folded cloth, globe, kettle, pewter mug, plates and silverware, fern and moonglow bush;
- atomic multiblocks: dress form, fountain, loom, scarecrow and six merchant-cart colors;
- small, medium and large crate containers;
- water well and climbable adventure ladder;
- animated training dummy;
- spinning wheel, yarn/thread and textile processing;
- connected display case;
- refreshed one-block moongate presentation;
- fully connecting wooden fence;
- blue, green, purple and red multiblock market stalls.

The final runtime asset delta under `src/main/resources/assets` contains:

| Asset type | Changed paths |
|---|---:|
| JSON | 2,516 |
| PNG | 1,114 |
| `.mcmeta` | 38 |
| OGG | 7 |
| OBJ | 5 |
| NBT | 9 |
| `.old` and other legacy copies | 290+ |

The seven added sounds are `flamingo_idle`, four hit effects used by new content, `leather1`, and `stones2`; `stones2` is registered as the streaming menu theme. The asset count is evidence of scale, not a public feature count.

Creature evidence:

- Added entity registrations are `IbisEntity` and `FlamingoEntity`.
- Ibis has white/scarlet persistent variants and flock behavior; `JhelomIbisPopulation` restricts it to Jhelom and caps the city-wide population at 15.
- Flamingo has pink/rose/white persistent variants and is included in the ambient city spawn pool.
- `ParrotProtectionHandler` cancels every damage source and makes parrots unkillable.

Atmosphere/content evidence:

- `ManagedVegetationConfig` defaults to 75% grass, 20% fern and 5% flowers when selecting managed vegetation; swamps substitute Blood Moss for the flower share.
- managed vegetation grows from grass-block random ticks, matures and schedules regrowth after a valid blade cut; cuts deliberately produce no drop.
- `WildResourceEntries` defines Sulphurous Ash near lava and black-lipped oysters on calcite near water, with persistent scheduling, bounded chunk populations and respawn windows.
- oyster harvesting uses the registered dagger path and yields one black pearl.
- Blood Moss is visual managed swamp vegetation in the final implementation and has no harvest drop. The public notes do not call it a collectible reagent.
- `TitleBranding*`, title mixins, splashes and `MenuMusicBranding` provide the Version 18 client presentation.
- terrain visuals include 32 deterministic stone variants, vanilla sand variants, cycling flagstone, variable foundation sides, sandstone/villa families and ceiling stalactites.

## Major directories and files inspected

Representative high-value evidence paths:

```text
src/main/java/com/seggellion/britannia_mod/bank/
src/main/java/com/seggellion/britannia_mod/service/banking/
src/main/java/com/seggellion/britannia_mod/client/screen/Bank*.java
src/main/java/com/seggellion/britannia_mod/service/guild/
src/main/java/com/seggellion/britannia_mod/farming/
src/main/java/com/seggellion/britannia_mod/block/FarmingBlock.java
src/main/java/com/seggellion/britannia_mod/block/entity/FarmingBlockEntity.java
src/main/java/com/seggellion/britannia_mod/mining/
src/main/java/com/seggellion/britannia_mod/resource/
src/main/java/com/seggellion/britannia_mod/worldgen/
src/main/java/com/seggellion/britannia_mod/skill/crafting/
src/main/java/com/seggellion/britannia_mod/grabbyhands/
src/main/java/com/seggellion/britannia_mod/banner/
src/main/java/com/seggellion/britannia_mod/bannerdyeing/
src/main/java/com/seggellion/britannia_mod/dye/
src/main/java/com/seggellion/britannia_mod/structure/
src/main/java/com/seggellion/britannia_mod/economy/
src/main/java/com/seggellion/britannia_mod/population/
src/main/java/com/seggellion/britannia_mod/quest/
src/main/java/com/seggellion/britannia_mod/vegetation/
src/main/java/com/seggellion/britannia_mod/wildresource/
src/main/java/com/seggellion/britannia_mod/entity/
src/main/java/com/seggellion/britannia_mod/registry/{BlockRegistry,ItemRegistry,EntityRegistry,CreativeTabRegistry}.java
src/main/resources/data/britannia_mod/{blacksmithing,mining,resources,banner_definitions,pigments,fabric_materials,banner_mounts}/
src/main/resources/data/britannia_mod/neoforge/biome_modifier/
src/main/resources/data/britannia_mod/worldgen/vanilla_feature_policy.json
src/main/resources/data/britannia_mod/tags/block/grabby_*.json
src/main/resources/data/britannia_mod/tags/item/grabby_placeable_items.json
src/main/resources/data/britannia_mod/recipe/
src/main/resources/assets/britannia_mod/{blockstates,models,textures,geo,animations,sounds}/
src/main/resources/assets/britannia_mod/lang/en_us.json
src/test/java/com/seggellion/britannia_mod/
src/main/java/com/seggellion/britannia_mod/gametest/
```

## Features deliberately excluded or narrowed

### Excluded as superseded

- **Banner crafting:** explicitly reverted; no public claim of craftable banners.
- **Natural managed deposit distribution:** deleted; public claims server-curated deposits only.
- **Standalone grape vines:** retired; public describes the Farming arbor only.
- **Intermediate placeholder banner families/names:** only the 35 final complete definitions are counted.

### Excluded as internal or administrative

- admin resource previews/removal operations and diagnostic commands;
- banner catalogue/debug commands;
- test scaffolds, fixture resources and GameTest templates;
- network protocol versioning, codec internals and internal milestone terminology;
- docs, source art/review intake and `.old` legacy copies;
- build, worktree and branch-administration changes.

### Excluded or narrowed because acquisition is not established

- **Provisional monoliths:** registered for creative use, but all three variants are diagnostic/provisional.
- **Shrines:** included only as world-builder content; no survival recipe or vendor acquisition was found.
- **Banners:** the final system and content are public, but the notes explicitly state that Patch 18 adds no survival crafting recipes.
- **Exact live grape varieties:** external bootstrap data; only two built-ins are guaranteed locally.
- **Exact live Guildmaster roster:** external service registry; capability is verified, count is not stable in the mod.
- **Exact live deposit supply/locations:** Rails rows are external; the mod's planning, materialization, extraction and restoration are verified.
- **Exact vendor product count:** repository rollout reports reflect different rollout stages and live stock is external.

### Other notable omissions

- Nine disabled vanilla diamond-equipment recipes were added, but restricted vanilla iron/diamond/netherite equipment was already removed by baseline runtime policy. This is a policy closure, not a strong new Patch 18 player feature.
- Several special rock definitions note that no old local worldgen path was found. They remain valid Rails-curated resources, but the public notes do not promise that every catalogue entry is currently deployed in the world.
- Managed Blood Moss is deliberately no-drop; it is presented as swamp vegetation, not a harvestable reagent.
- Exact restoration and wild-resource timers are implementation defaults and may be server-tuned; the public notes communicate restoration/respawn without marketing a timer guarantee.

## Quality and reliability evidence

Player-facing reliability changes supported by final code/history include:

- all major banking movements use durable receipts and reconciliation;
- trader sales reserve/reconcile across failure windows;
- Rails authentication moved to dedicated-server credentials and requests are signed;
- world-state, NPC, population and legacy spawn network waits were removed from tick paths;
- town population maintenance is bounded;
- log-flood fixes prevent repeated service timeouts from cascading into kicks;
- quest completion moved to server authority;
- house privacy validates the region before mutation;
- resource restoration retains debt when a write fails;
- multi-block banners, shrines, houses and decorative structures plan/validate before mutation;
- full-inventory textile and Grabby paths preserve or safely drop output rather than deleting it.

## Public-note claim audit

Every exact public count has a final-tree source:

| Public count | Evidence |
|---|---|
| 67 crop definitions | 67 final `crop(...)` registrations; asserted by farming tests |
| 7 flower species | `FlowerRegistry.INITIAL_SPECIES_IDS` and seven registered definitions |
| 23 flower colors | 23 `registerColor` entries |
| 29 managed resources | 29 entries in `resources.json` |
| 27 Mining definitions | 27 active entries in `mineables.json` |
| 202 blacksmith entries | final `craftables.json`; asserted by catalogue tests |
| 120 weapons | sum of five weapon categories; asserted by tests |
| 10 house styles | `HouseStyle.values()` and Architect deed tests |
| 35 movable blocks | final `grabby_movable` tag |
| 30 placeable loose goods | final `grabby_placeable_items` tag |
| 35 complete banners | final banner-definition directory and content status |
| 4 fabrics / 6 common pigments / 2 mounts | final datapack definitions |
| 32 stone variants | final deterministic stone-variant implementation/assets |

Claims involving live Rails state are phrased as capabilities, authority or conditional availability rather than as guaranteed current stock/population.

## Validation performed

- `gradlew.bat test` completed successfully against the documented HEAD. The generated reports contain **3,024 tests**, with **0 failures**, **0 errors** and **17 skipped**.
- Final catalogue/tag data was parsed independently to confirm the crop, flower-color, resource, Mining, blacksmithing, Grabby Hands, banner, fabric, pigment and mount counts used above.
- Banner dimensions and blacksmithing categories were regrouped from their final JSON definitions rather than copied from planning documents.
- Direct whitespace scans found no trailing whitespace or tab-indented lines in either generated Markdown file.

## Remaining ambiguity

The repository does not contain enough authoritative data for confident public claims about:

1. the exact grape-variety catalogue deployed on the live shard;
2. which managed deposit rows and locations are currently active in the live world;
3. the current stock, price and availability of every economic vendor product or Architect deed in each city;
4. the current live Guildmaster type roster and staffing locations;
5. a normal survival acquisition route for banners or shrines;
6. retroactive region persistence for houses whose Rails rows predate the stored structure payload.

The public notes either omit these points, state the server-controlled context or identify the limitation directly.
