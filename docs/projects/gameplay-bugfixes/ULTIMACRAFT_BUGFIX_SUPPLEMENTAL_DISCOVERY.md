# UltimaCraft supplemental discovery

Completed 2026-09-06 (America/Vancouver; diagnostic timestamps cross into September 7 UTC). Discovery only: no production fix implemented. The user explicitly authorized discovery with the supplemental document's six decisions and exclusions. Original findings remain historical evidence at their original revision.

## Verified state and scope

| Repository | Branch / full HEAD | Preserved worktree state |
|---|---|---|
| `C:/projects/britannia/mod/Britannia_Mod` | `patch-18`; `421e27853dde4099d1d794568e33e6709507a53b` | No commits since original discovery; no tracked modifications. Two existing untracked farming documents and discovery directory preserved. |
| Ubuntu WSL `/home/dusti/ultimacraft-website` | `release/public`; `a9425ca41afa9b4ba7539426044e7b50e70a3966` | Existing modified `app/themes/Avatar/views/pages/article.html.erb`; three untracked playbooks and Zone.Identifier companions preserved. No edits/database operations here. |

No applicable AGENTS.md found on either ancestor chain or in inspected repository paths. Existing other mod worktrees were untouched. Minecraft1.21.1, NeoForge21.1.72, GeckoLib4.6.6, Gradle8.9; tests used Eclipse Adoptium JDK21.0.9.10 via explicit JAVA_HOME because PATH Java is older.

**Nineteen tracked reports; sixteen functional issues in scope.** BUG-04, BUG-06 and BUG-07 are excluded artwork reports: no new investigations, tasks, milestones or aesthetic gates. Historical evidence remains in the original document. User supplies full watering-can artwork; actual state selection/synchronization remains code work. Functional moongate shader compatibility and fence model selection remain in scope. No Fabric/Atrevion expansion, branch switch, release label, commit, push, deployment, production trade or production item manipulation occurred.

**Source-confirmed** means inspected code/data at these revisions; **runtime-confirmed** means the stated isolated server probe; **unverified** means no actual reported client/world/vendor evidence. Server tests do not prove physical client input, visuals, natural chunk population or paid vendor acceptance. Source seed rows are not loaded city records. Existing local JAR metadata identifies older dirty revision `a2f6391cdcc61fe8563545169af69145fd7d94f3`; the reported deployed JAR remains unidentified. Generated outputs may contain diagnostics and are not release artifacts.

## New issue status

| Issue | Finding | Confidence / limit |
|---|---|---|
| BUG-15 | Native pumpkin/melon landscape features remain loaded; stems still fruit. Structures contain fruit and stems. No corresponding custom spontaneous-spawn entry found. | Runtime registry/controlled stem evidence; actual reported provenance/world datapacks unknown. |
| BUG-16 | Three bowl recipes and seven dye-tub pigment variants require their driver in MAIN_HAND. All swapped arrangements fail. | Registered server item-use handlers reproduced; actual client gestures remain future testing. |
| BUG-17 | Exhausted-input branches put the last compatible output in MAIN_HAND instead of merging with inventory. | Counts1/2/3/64, both arrangements, full components and server merge checked. |
| BUG-18 | Canonical broccoli/orange lack commodity mappings, excluding them before economic produce quote requests. Rails seed source omits both too. | Runtime serialization/source filtering confirmed; actual NPC/shard/city, loaded catalog and same-vendor controls unverified. |
| BUG-19 | Empty MAIN_HAND takes merchandise into inventory. Reached decorator rotation dismantles the case. No content-only offhand ejection exists. | Server dispatch and16-gesture conservation matrix confirmed; client sync unverified. |

## BUG-15 — Spontaneous pumpkin and melon routes

**R15.1–R15.4:** stop unwanted spontaneous sources; preserve intentional cultivation/items/recipes; distinguish new-chunk placement from existing growth; no existing-plant deletion or global random-tick disablement.

Native blocks: `minecraft:pumpkin`, `minecraft:melon`, `minecraft:pumpkin_stem`, `minecraft:melon_stem`, `minecraft:attached_pumpkin_stem`, `minecraft:attached_melon_stem`; seeds: `minecraft:pumpkin_seeds`, `minecraft:melon_seeds`; native melon yield: `minecraft:melon_slice`. Custom definitions `pumpkin`/`watermelon` use `britannia_mod:pumpkin_seeds_custom`/`britannia_mod:watermelon_seeds` and yield `britannia_mod:pumpkin`/`britannia_mod:watermelon`. They grow as state on `britannia_mod:farming_block`, not spontaneous native fruit blocks. Custom-system compatibility definitions `vanilla_pumpkin`/`vanilla_melon` also exist. [farming/CropRegistry.java:105](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/farming/CropRegistry.java:105), [farming/CropRegistry.java:123](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/farming/CropRegistry.java:123), [registry/ItemRegistry.java:1031](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java:1031).

| Route / registration | New/existing chunks; cultivation impact | Evidence / bounded treatment |
|---|---|---|
| Configured `minecraft:patch_pumpkin` → placed same ID → biome vegetation | Initial decoration; fruit does not regrow itself | RANDOM_PATCH/SIMPLE_BLOCK places pumpkins on grass, rarity1/300. Loaded biome settings contain46 references. Remove exact landscape feature. [net/minecraft/data/worldgen/features/VegetationFeatures.java:148](C:/projects/britannia/mod/Britannia_Mod/build/neoform/neoFormJoined1.21.1-20240808.144430/steps/unzipSources/unpacked/net/minecraft/data/worldgen/features/VegetationFeatures.java:148), [net/minecraft/data/worldgen/placement/VegetationPlacements.java:227](C:/projects/britannia/mod/Britannia_Mod/build/neoform/neoFormJoined1.21.1-20240808.144430/steps/unzipSources/unpacked/net/minecraft/data/worldgen/placement/VegetationPlacements.java:227) |
| Configured `minecraft:patch_melon` → placed normal and `minecraft:patch_melon_sparse` | Initial decoration | Loaded settings contain2 normal +1 sparse references: jungle/bamboo jungle and sparse jungle. Remove exact placed IDs. [net/minecraft/data/worldgen/features/VegetationFeatures.java:210](C:/projects/britannia/mod/Britannia_Mod/build/neoform/neoFormJoined1.21.1-20240808.144430/steps/unzipSources/unpacked/net/minecraft/data/worldgen/features/VegetationFeatures.java:210), [net/minecraft/data/worldgen/placement/VegetationPlacements.java:254](C:/projects/britannia/mod/Britannia_Mod/build/neoform/neoFormJoined1.21.1-20240808.144430/steps/unzipSources/unpacked/net/minecraft/data/worldgen/placement/VegetationPlacements.java:254) |
| Native stems: random tick; bonemeal can invoke same path | Existing loaded chunks, including intentional crops | Both mature stems produced fruit after3 explicit tick calls, seed23. AGE/FACING lacks planted/wild provenance. Preserve intentional growth. [net/minecraft/world/level/block/StemBlock.java:81](C:/projects/britannia/mod/Britannia_Mod/build/neoform/neoFormJoined1.21.1-20240808.144430/steps/unzipSources/unpacked/net/minecraft/world/level/block/StemBlock.java:81), [net/minecraft/world/level/block/StemBlock.java:124](C:/projects/britannia/mod/Britannia_Mod/build/neoform/neoFormJoined1.21.1-20240808.144430/steps/unzipSources/unpacked/net/minecraft/world/level/block/StemBlock.java:124), [net/minecraft/world/level/block/AttachedStemBlock.java:45](C:/projects/britannia/mod/Britannia_Mod/build/neoform/neoFormJoined1.21.1-20240808.144430/steps/unzipSources/unpacked/net/minecraft/world/level/block/AttachedStemBlock.java:45) |
| Village `minecraft:pile_pumpkin` / `minecraft:pile_melon` configured/placed features and jigsaw pools | New villages; separate from landscape patches | Piles place fruit; pumpkin pile also selects jack-o-lantern. Source-registered, not naturally generated in probe. Preserve pending Q2. [net/minecraft/data/worldgen/features/PileFeatures.java:24](C:/projects/britannia/mod/Britannia_Mod/build/neoform/neoFormJoined1.21.1-20240808.144430/steps/unzipSources/unpacked/net/minecraft/data/worldgen/features/PileFeatures.java:24), [net/minecraft/data/worldgen/placement/VillagePlacements.java:17](C:/projects/britannia/mod/Britannia_Mod/build/neoform/neoFormJoined1.21.1-20240808.144430/steps/unzipSources/unpacked/net/minecraft/data/worldgen/placement/VillagePlacements.java:17) |
| Structure NBT: village farms/streets, mansion, outpost | New structures may place stems that later fruit in existing chunks | Runtime scanned1180 templates;13 matched uncarved pumpkin/melon prefixes. Follow-up resolved used palette entries below. Placed blocks retain no template provenance. |
| Custom farm random ticks/lifecycle | Existing deliberately planted FarmingBlock plots | Crop registration is not spontaneous spawning. Preserve custom and native-seed compatibility planting/yield/recipes/skill rules. [block/FarmingBlock.java:350](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/block/FarmingBlock.java:350), [block/entity/FarmingBlockEntity.java:548](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/block/entity/FarmingBlockEntity.java:548) |
| WildResourceManager and managed vegetation | Can populate loaded chunks | Wild entries: ash/oysters/dung; profiles: grass/fern/flowers/blood moss. No pumpkin/melon registration found. Tuning controls weights/rates, not arbitrary crop identity. [wildresource/WildResourceEntries.java:55](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/wildresource/WildResourceEntries.java:55), [wildresource/WildResourceManager.java:47](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/wildresource/WildResourceManager.java:47), [vegetation/ManagedVegetationProfile.java:15](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/vegetation/ManagedVegetationProfile.java:15) |
| Villagers, external datapacks/overrides | Potential existing-chunk automation | Runtime villager-plantable tag excludes both native seeds. NeoForge SpecialPlantable extension exists, no implicated registration found. Reported-world external packs unverified. [net/minecraft/world/entity/ai/behavior/HarvestFarmland.java:112](C:/projects/britannia/mod/Britannia_Mod/build/neoform/neoFormJoined1.21.1-20240808.144430/steps/unzipSources/unpacked/net/minecraft/world/entity/ai/behavior/HarvestFarmland.java:112) |

Used native template entries (`blocks[].state` resolved into palette, not unused strings), under `minecraft:structure/`:

| Template | Used crop blocks |
|---|---|
| `pillager_outpost/feature_tent2.nbt` | pumpkin4 |
| `village/savanna/houses/savanna_small_farm.nbt` | melon1 |
| `village/savanna/streets/crossroad_07.nbt`, `straight_06.nbt`, `straight_11.nbt` | melon_stem5/12/14 |
| Matching three `village/savanna/zombie/streets/` templates | melon_stem5/12/14 |
| `village/taiga/houses/taiga_large_farm_1.nbt`, `taiga_large_farm_2.nbt` | pumpkin7/5 |
| `village/taiga/houses/taiga_small_farm_1.nbt` | pumpkin4 + pumpkin_stem17 |
| `village/taiga/zombie/houses/taiga_large_farm_2.nbt` | pumpkin5 |
| `woodland_mansion/1x2_a8.nbt` | melon16, pumpkin16, attached_melon_stem16, attached_pumpkin_stem16 |
| Separate carved decorations: `pillager_outpost/feature_targets.nbt`, `woodland_mansion/1x1_a2.nbt` | carved_pumpkin2/1; distinguish from growing fruit |

The checked-in `src/main/resources/data/britannia_mod/neoforge/biome_modifier/suppress_vanilla_overworld_ores.json` removes ores only; `worldgen/vanilla_feature_policy.json` is an ore-policy contract. Native BiomeDefaultFeatures/OverworldBiomes wire crop patches. Runtime settings prove they survive this mod's loaded modifiers, not that a flat GameTest world naturally generated them.

**Proposal:** narrow NeoForge remove-features modifier for the three landscape IDs using existing policy-validation style. No crop/item removal, broad crop event cancellation, existing-block cleanup or random-tick disablement. If structures are included, target identified processors/pools/templates only for new placement. Native-stem suppression cannot distinguish wild from intentional plants. Q2 covers structure scope; Q4 covers separate native-crop skill policy.

**Verification/risks:** new noise-generated chunk fixture in affected biomes and controls; exact feature absence after reload; existing fruit/stems remain; native planting fruits and custom/compatible seeds cultivate. Inspect actual report IDs/coordinates and packs. Feature-only changes leave structure stems; overrides may reintroduce features. Source/registry evidence is not completed removal or proof of reported-world cause.

## BUG-16 — Role-aware two-hand recipes

**R16.1–R16.4:** either arrangement selects the same recipe; charge actual role slots; commit once per activation; nonmatches preserve unrelated interactions.

Short IDs below use `britannia_mod:`. Live immediate inventory is three bowl recipes plus seven pigment variants, using normal right-click/use. No custom packet/keybinding invokes them. Crafting tables and blacksmith GUI recipes are separate systems.

| Current MAIN driver + OFF input | Context | Roles/cost/output/remainders | Current policy |
|---|---|---|---|
| `empty_bowl` + `dirt` | Air-use or after target declines; source-water ray has precedence | Consume1 each → new `bowl_of_dirt`; no remainder | No skill/tool/durability/RNG; consumes even Creative; swapped fails |
| `bowl_of_dirt` + `dung` | Same handler | Consume1 each → new `bowl_of_fertile_dirt` | Same policy; swapped fails |
| `bowl_of_fertile_dirt` + `bowl_of_water` | Normal use | Consume1 each → new `fertilized_dirt` +2 custom `empty_bowl` | No skill/durability/RNG; consumes even Creative; swapped fails |
| `dye_tub` + `madder_red`, `woad_blue`, `verdigris`, `weld_gold`, `soot_black`, `chalk_white`, or `ice_blue` | Loaded/enabled pigment required | Retain tub maxstack1; replace only dye_tub_state with unlimited pigment; consume1 pigment outside Creative; no refund of previous pigment | No skill/durability/RNG; same pigment no-op; tub MAIN only; swapped fails |

Active paths: [registry/ItemRegistry.java:339](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java:339); [bowlpreparation/BowlPreparationItem.java:26](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/bowlpreparation/BowlPreparationItem.java:26) → [bowlpreparation/BowlPreparationService.java:25](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/bowlpreparation/BowlPreparationService.java:25)/[bowlpreparation/BowlPreparationService.java:75](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/bowlpreparation/BowlPreparationService.java:75); [bowlpreparation/FertileDirtMixingItem.java:22](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/bowlpreparation/FertileDirtMixingItem.java:22) → [bowlpreparation/FertileDirtMixingService.java:28](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/bowlpreparation/FertileDirtMixingService.java:28)/[bowlpreparation/FertileDirtMixingService.java:70](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/bowlpreparation/FertileDirtMixingService.java:70); [registry/DyeItemRegistry.java:25](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/registry/DyeItemRegistry.java:25) → [dye/item/DyeTubItem.java:29](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/dye/item/DyeTubItem.java:29) → [dye/item/DyeTubLoadingService.java:18](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/dye/item/DyeTubLoadingService.java:18). Bowl plans snapshot both full stacks and revalidate ItemStack.matches before consumption; getters/output copy. Dye revalidates original tub state and pigment identity. Preserve these commit boundaries.

**Runtime:** recipes ×counts1/2/3/64 ×both arrangements:210 successful normal crafts +12 swapped nonmatches;222 activations logged before/after with slots/components and per-hand results. Seven pigments ×both arrangements:7 tub changes +7 swapped no-ops. Registered ItemStack.use/Item.use server handlers were called with modeled main-then-off dispatch, not physical client input. Current tests intentionally assert restricted arrangements/hand replacement; revise consciously with fixes.

**Dispatch:** Minecraft.startUseItem loops MAIN then OFF, target first then that hand's air-use; consumed result or block-use FAIL stops client dispatch. ServerPlayerGameMode fires RightClickBlock, honors cancellation, calls onItemUseFirst, block use unless secondary-use bypass, then item useOn. A block's ItemInteractionResult.FAIL does not immediately return from server game mode: item useOn may still run. [net/minecraft/client/Minecraft.java:1700](C:/projects/britannia/mod/Britannia_Mod/build/neoform/neoFormJoined1.21.1-20240808.144430/steps/unzipSources/unpacked/net/minecraft/client/Minecraft.java:1700), [net/minecraft/server/level/ServerPlayerGameMode.java:337](C:/projects/britannia/mod/Britannia_Mod/build/neoform/neoFormJoined1.21.1-20240808.144430/steps/unzipSources/unpacked/net/minecraft/server/level/ServerPlayerGameMode.java:337), [net/minecraft/client/multiplayer/MultiPlayerGameMode.java:305](C:/projects/britannia/mod/Britannia_Mod/build/neoform/neoFormJoined1.21.1-20240808.144430/steps/unzipSources/unpacked/net/minecraft/client/multiplayer/MultiPlayerGameMode.java:305).

| Nearby handler | Actual relationship / scope |
|---|---|
| Empty bowl + source water | One-input filling already supports actual hand; source ray precedes dry-recipe guard. Preserve precedence. [bowlpreparation/BowlWaterFillingService.java:21](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/bowlpreparation/BowlWaterFillingService.java:21) |
| Tub + custom banner | Opens preview/session, not immediate conversion. MAIN-only session checks need conflict regression, not editor rewrite. [dye/item/DyeTubItem.java:38](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/dye/item/DyeTubItem.java:38) |
| Hammer/equipment + ingot on anvil | NORMAL RightClickBlock opens gated GUI; later selected-recipe packet handles crafting/repair. Keep material/tool/skill/session checks. [event/BlacksmithInteractionEvent.java:27](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/event/BlacksmithInteractionEvent.java:27) |
| Crop extraction/food/planting | CropSeedExtractor acts on one crop stack OFF or sneaking; no second ingredient. Mainhand food/planting can own use before OFF. [item/CropSeedExtractor.java:17](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/item/CropSeedExtractor.java:17) |
| Farms/flowers/house plots | Block use and HIGHEST flower/house handlers can own watering/planting/protection first. Resolver must not bypass rights. [event/FlowerInteractionHandler.java:25](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/event/FlowerInteractionHandler.java:25), [event/HouseFarmPlotInteractionHandler.java:21](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/event/HouseFarmPlotInteractionHandler.java:21) |
| Cases/decorator/Grabby | Empty case can store ingredient; occupied case takes into empty MAIN. Grabby HIGHEST defers to either-hand decorator. BUG-19 must precede case defaults. [grabbyhands/GrabbyInteractionHandler.java:54](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/grabbyhands/GrabbyInteractionHandler.java:54), [grabbyhands/GrabbyGesture.java:78](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/grabbyhands/GrabbyGesture.java:78) |
| Lockpicking/chairs/horse merchant | Contextual hand interactions, not immediate combinations of these ingredients; no broad refactor justified |

**Proposal:** small role resolver returns recipe identity, expected snapshots and actual slots; retain existing bowl/dye commit adapters. A narrow main-phase RightClickItem recognition hook on both logical sides can find an OFF-held driver before ordinary use; old item callbacks delegate rather than also commit. Preserve block-target precedence except explicit case override. Accepted main callback owns gesture and prevents second OFF commit; revalidate live stacks server-side. Nonmatches PASS. No current same-item pair exists; deterministic role orientation, reject truly ambiguous multiple recipe IDs without mutation. Avoid blanket per-tick cooldown conflating separate clicks. Add packet sequencing only if actual client evidence requires it.

**Risks/verification:** every recipe/pigment both ways, real activation, dual callbacks, stale inputs,1/2/64 counts, same-pigment/unavailable registry no-ops, output/remainder capacity, Creative parity, water-source precedence, protected farms, case/anvil/preview fallback. Tool-break cases apply only to actual durable recipe roles, not these bowls/tubs. Preserve components/costs/skills; no balancing/global interaction rewrite. BUG-17 remains a distinct insertion contract.

## BUG-17 — Compatible final output retained in hand

**R17.1–R17.4:** reproduce final output with headroom; compare full components/destinations; conserve inputs/outputs exactly once; preserve legitimately incompatible stacks.

**Cause, high confidence/runtime-confirmed:** BowlPreparationService.apply tests the already-shrunk main input: empty → set MAIN_HAND to output; otherwise → BowlPreparationOutput.giveOrDrop. FertileDirtMixingService does the same for fertilizer, and puts returned bowls in emptied OFF_HAND. These use the same output factories, not different final-item metadata. [bowlpreparation/BowlPreparationService.java:80](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/bowlpreparation/BowlPreparationService.java:80), [bowlpreparation/FertileDirtMixingService.java:75](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/bowlpreparation/FertileDirtMixingService.java:75), [bowlpreparation/BowlPreparationOutput.java:12](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/bowlpreparation/BowlPreparationOutput.java:12).

Fixture: Survival mock player; ingredient in selected slot0, other input slot40; compatible output stacks of one in slots9 and10, all other inventory free. Two initial output stacks guarantee a partial compatible destination even at craft64. Use registered ItemStack.use and retain the same hands/inventory for the whole series. Reverse hands separately as negative control. No metadata stripping or capacity override.

| Starting input count | Immediately before last normal craft | Final result for each of3 recipes |
|---|---|---|
| 1 | Inputs1/1; slot9=1, slot10=1 | MAIN output1; both compatible stored outputs untouched |
| 2 | Penultimate output went to slot9=2; slot10=1 | MAIN output1; combined output total4 |
| 3 | Penultimate output went to slot9=3; slot10=1 | MAIN output1; combined total5 |
| 64 | After craft63: slot9=64, slot10=1; inputs1/1 | MAIN output1 despite slot10 headroom63; combined total66 |

Swapped cases preserve inputs/control outputs because BUG-16 prevents crafting; they are not claimed as successful reversed recipes. Final-mix remainders total exactly2×craft count, including128 empty bowls at count64. Logs capture every before/after stack, slot and callback/result. Full component maps and patches agree for final/earlier outputs: default lore/enchantments/repair cost; no custom_data, quality, damage, name, ownership or origin introduced; patches empty. Sound/particle packet counts were not instrumented.

All12 successful recipe/count series merged when the final output was removed from MAIN_HAND and passed to ordinary Inventory.add; MAIN remained empty. This proves server compatibility/insertion behavior, not a physical client drag/reconnect. Those remain future acceptance but are unnecessary to distinguish this cause. Plan snapshots and copies rule out observed mutable aliasing; output factories do not read metadata from an already exhausted input.

**Proposal:** shared compatible-stack-first output delivery across both bowl services, then deterministic empty slot/hand fallback and one drop for remainder. Construct outputs once from canonical recipe or immutable intended metadata. Define equivalent remainder placement without consuming newly returned bowls in a second callback. Preserve capacity protections including Creative inventory-add behavior. Full inventory cannot reroll, refund a completed craft, lose or duplicate outputs. Do not clear meaningful components to force stacking.

**Files/risks/verification:** two bowl services, output helper, BUG-16 role adapters and existing tests. Validate equipped-hand consequences, output/remainder capacity, stale input, incompatible quality/name/owner/custom components, full/near-full storage, both hands and actual server/client reconciliation. Vendor rejection is separate: BUG-18 classification ignores ordinary crop quality. Display-case preservation already copies full stacks; no common component-construction defect is established.

## BUG-18 — Produce buyback and Rails boundary

**R18.1–R18.4:** establish canonical IDs and trader context; trace quote/sale authority; compare harvest components and controls; enable requested purchases without invented prices or indiscriminate merchant expansion.

Canonical IDs: `britannia_mod:broccoli` (not brocoli), `britannia_mod:orange` (singular, not oranges). Orange is SeedExtractableHealingCropItem; broccoli WeightedCookedFoodItem. Both edible and registered crops. [registry/ItemRegistry.java:846](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java:846), [registry/ItemRegistry.java:858](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java:858). Ordinary farm harvest builds from CropDefinition and applies CropQualityCalculator; fruit routes additionally set provenance. Probe used that exact definition/quality decorator at75 and compared default/admin-style stacks; it did not harvest a mature world plant or trade. [block/FarmingBlock.java:590](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/block/FarmingBlock.java:590), [farming/CropQualityCalculator.java:1](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/farming/CropQualityCalculator.java:1).

| Item/control | Components / commodity identity | Category and pricing source | First observed failure / confidence |
|---|---|---|---|
| `britannia_mod:broccoli` | Default and quality75 serialization equal; no commodity_key | Mod category absent; inspected CommoditySeeder has no broccoli row | describeSaleItem emits only item_id/item_name; produce filter rejects absent category before Rails. Runtime serializer + source filter; live row/price unknown. |
| `britannia_mod:orange` | Same comparison; item_name orange, no oranges alias | Mod category absent; inspected seed source has no orange row | Same pre-Rails exclusion; crop quality not the cause. Live row/price unknown. |
| `britannia_mod:carrots` | Crop carrot; commodity key carrots; same classification at quality75 | produce/vegetable; mod mapping and Rails seed agree | Positive mapping control, not proven accepted at reported vendor. |
| `britannia_mod:apple` | Key apple; default/quality75 classification agree | produce/fruit; mod mapping and Rails seed agree | Positive mapping control; loaded flags/stock/treasury and actual sale unverified. |

**Active path:**

1. ServerCatalogService validates interaction and routes registry kind=trader to buyback, vendor to retail. EconomicBuybackCatalogService.prepare uses server main inventory slots, cached authoritative accepted_commodities, describeSaleItem and TraderCommodityFilter. Missing/absent/empty policy fails closed; missing category fails produce policy. CommodityMappings looks up exact item ID then path fallback, not crop quality. [economy/ServerCatalogService.java:44](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/economy/ServerCatalogService.java:44), [economy/EconomicBuybackCatalogService.java:87](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/economy/EconomicBuybackCatalogService.java:87), [economy/TraderCommodityFilter.java:35](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/economy/TraderCommodityFilter.java:35), [economy/CommodityMappings.java:189](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/economy/CommodityMappings.java:189), [economy/ServerEconomyService.java:537](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/economy/ServerEconomyService.java:537).
2. Remaining rows POST `/api/economic_buyback_catalog`, authenticated shard, world_npc_public_id/player/items. Rails resolves active WorldNpc in that shard, active assignment, registered spawn point, economic trader type; city comes from spawn point, not arbitrary UI city. Shard accepted-commodity override wins over type policy. [config/routes.rb:496](/home/dusti/ultimacraft-website/config/routes.rb:496), [app/controllers/api/economic_buyback_catalogs_controller.rb:35](/home/dusti/ultimacraft-website/app/controllers/api/economic_buyback_catalogs_controller.rb:35), [app/services/economy/trader_post_resolver.rb:26](/home/dusti/ultimacraft-website/app/services/economy/trader_post_resolver.rb:26), [app/services/economic_npcs/effective_policy.rb:16](/home/dusti/ultimacraft-website/app/services/economic_npcs/effective_policy.rb:16).
3. BuybackValuation resolves exact case-insensitive category/subcategory/name or composite key; no singular/plural aliases. It enforces resolved commodity policy, npc_buy_enabled, stock cap, current_price/form multiplier, denomination/minimum payout and treasury sufficiency. npc_sell_enabled is the retail direction. Zero/too-small basket totals produce value_below_denomination_minimum; do not infer an explicit per-row positive-price guard. Dynamic current_price carries supply/price calculation; no extra crop-specific demand check found. [app/services/economy/buyback_valuation.rb:87](/home/dusti/ultimacraft-website/app/services/economy/buyback_valuation.rb:87), [app/services/economy/buyback_valuation.rb:119](/home/dusti/ultimacraft-website/app/services/economy/buyback_valuation.rb:119), [app/services/economy/buyback_valuation.rb:210](/home/dusti/ultimacraft-website/app/services/economy/buyback_valuation.rb:210).
4. Server sends resolved products/notices to NpcCatalogScreen. AbstractSellTraderRoleHandler sends cart ID/name/quantity/saved matchNbt in SellItemsC2SPayload; NetworkHandler enqueues server handling. ServerEconomyService revalidates entity/distance/city/actual inventory and reserves live stack copies durably before HTTP. Full matchNbt component comparison applies to wine; ordinary produce is selected by item identity. UI onSuccess callback alone is not settlement proof. [npc/AbstractSellTraderRoleHandler.java:31](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/npc/AbstractSellTraderRoleHandler.java:31), [network/NetworkHandler.java:171](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/network/NetworkHandler.java:171), [economy/ServerEconomyService.java:87](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/economy/ServerEconomyService.java:87), [economy/ServerEconomyService.java:325](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/economy/ServerEconomyService.java:325), [economy/ServerEconomyService.java:397](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/economy/ServerEconomyService.java:397).
5. POST `/api/trader_transactions` (legacy404/405 fallback `/api/transactions`) routes world NPC requests to Economy::EconomicTraderSale. Under transaction/locks, resolve post again, revalidate shared valuation, debit treasury/change commodity inventory once, with shard idempotency replay. Mod success grants returned currency and resolves reservation; failures/replays use existing refund/reconciliation branches. Preserve receipt/accounting framework and test local rejection/retry/crash behavior. [economy/ServerEconomyService.java:148](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/economy/ServerEconomyService.java:148), [economy/ServerEconomyService.java:630](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/economy/ServerEconomyService.java:630), [config/routes.rb:490](/home/dusti/ultimacraft-website/config/routes.rb:490), [app/controllers/api/trader_transactions_controller.rb:96](/home/dusti/ultimacraft-website/app/controllers/api/trader_transactions_controller.rb:96), [app/services/economy/economic_trader_sale.rb:75](/home/dusti/ultimacraft-website/app/services/economy/economic_trader_sale.rb:75).

Legacy TraderRoleHandler also uses CommodityMappings for roles containing produce/costermonger, excluding unmapped items in collectSimpleCommodities. Thus omission affects both catalog families although identity/settlement branches differ. [npc/TraderRoleHandler.java:27](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/npc/TraderRoleHandler.java:27).

**Source versus loaded backend:** `db/seeds/economic_trader_types.rb:96` declares produce_trader, profession farmer, entity britannia_mod:produce_trader, accepted category produce. `db/seeds/farmer_vendor.rb:22` makes farmer a seed-selling vendor that buys nothing. They are different types. `app/services/commodity_seeder.rb:394` lists apple/banana/concord_grapes/peaches/pears/berries and squash/carrots/corn/cabbage/lettuce/onion/pumpkin/potato/tomato, but neither orange nor broccoli. No reported-item matches in the inspected local development logs for brocoli/broccoli/canonical orange IDs. No database connection, Rails runner, seed run or authenticated catalog request was made. Actual reported NPC, shard/city, loaded revision/prices/flags/stock/treasury remain unknown; Q1 requests evidence instead of inventing context.

**Proposal:** exact intended mappings broccoli→produce/vegetable and orange→produce/fruit, with commodity names aligned to authoritative catalog. Inspect/repair missing city rows through existing economy rollout only after identifying legitimate price source; seed absence alone does not prove deployed omission. Keep category/shard/type policies, flags/caps/funds, exact reserved stacks and authoritative payout. Validate effective policy/catalog revision and fresh screen retrieval so stale cache cannot mask the repair. No prices proposed; audit gaps below do not authorize buying all crops/flowers/seeds/reagents/food.

**Risks/verification:** mapping-only repair can expose missing backend rows; wrong farmer vendor legitimately buys nothing; aliases/path fallback can hide wrong IDs; normal/admin harvest components need comparable same-vendor fixtures. Current ordinary crop matching can select another quality of the same item; record actual reserved slots/components and do not solve this by stripping data. Test acceptance and correct rejection for wrong type/shard/city, missing row/policy, buy flag false, stock cap, funds, below-minimum valuation, unavailable/stale catalog; quote→sale parity and inventory/currency/receipt accounting once. No Rails tests or database-backed integration executed here.

### Related crop audit

Runtime examined all67 CropRegistry harvest identities;49 have FOOD components. Eighteen definitions have mappings,13 of them edible; **36 edible definitions lack mappings**. Absence is not automatically a policy bug for each crop. Table includes all definitions to preserve denominators and distinguish nonfood; no seeds/flowers/reagents/processed-item acceptance expansion is implied. Full default/quality stack components are in diagnostic logs, extracted mapping records in ignored crop-commodity-audit.json.

| Definition | Harvest ID | Edible | Category/subcategory/key |
|---|---|---|---|
| squash | `britannia_mod:squash` | yes | produce/vegetable/squash |
| carrot | `britannia_mod:carrots` | yes | produce/vegetable/carrots |
| corn | `britannia_mod:corn` | yes | produce/vegetable/corn |
| cabbage | `britannia_mod:cabbage` | yes | produce/vegetable/cabbage |
| lettuce | `britannia_mod:lettuce` | yes | produce/vegetable/lettuce |
| yellow_onion | `britannia_mod:yellow_onion` | yes | **absent** |
| green_onion | `britannia_mod:green_onion` | yes | **absent** |
| pumpkin | `britannia_mod:pumpkin` | yes | produce/vegetable/pumpkin |
| potato | `britannia_mod:potato` | yes | produce/vegetable/potato |
| watermelon | `britannia_mod:watermelon` | yes | **absent** |
| vanilla_potato | `minecraft:potato` | yes | produce/vegetable/potato |
| wheat | `minecraft:wheat` | no | grain/whole/wheat |
| rye | `britannia_mod:rye` | no | grain/whole/rye |
| barley | `britannia_mod:barley` | no | grain/whole/barley |
| oats | `britannia_mod:oats` | no | grain/whole/oats |
| mustard | `britannia_mod:mustard_seeds` | no | **absent** |
| beans | `britannia_mod:beans` | yes | **absent** |
| rice | `britannia_mod:rice` | no | **absent** |
| tomato | `britannia_mod:tomato` | yes | produce/vegetable/tomato |
| garlic | `britannia_mod:garlic` | no | **absent** |
| ginseng | `britannia_mod:ginseng` | no | **absent** |
| mandrake | `britannia_mod:mandrake` | no | **absent** |
| nightshade | `britannia_mod:nightshade` | no | **absent** |
| brown_mushroom | `minecraft:brown_mushroom` | no | **absent** |
| red_mushroom | `minecraft:red_mushroom` | no | **absent** |
| vanilla_pumpkin | `minecraft:pumpkin` | no | produce/vegetable/pumpkin |
| vanilla_melon | `minecraft:melon_slice` | yes | **absent** |
| pineapple | `britannia_mod:pineapple` | yes | **absent** |
| strawberry | `britannia_mod:strawberry` | yes | **absent** |
| blueberry | `britannia_mod:blueberry` | yes | **absent** |
| raspberry | `britannia_mod:raspberry` | yes | **absent** |
| cranberry | `britannia_mod:cranberry` | yes | **absent** |
| blackberry | `britannia_mod:blackberry` | yes | **absent** |
| huckleberry | `britannia_mod:huckleberry` | yes | **absent** |
| mulberry | `britannia_mod:mulberry` | yes | **absent** |
| elderberry | `britannia_mod:elderberry` | yes | **absent** |
| cherries | `britannia_mod:cherries` | yes | **absent** |
| cotton | `britannia_mod:cotton` | no | **absent** |
| flax | `britannia_mod:flax` | no | **absent** |
| hemp | `britannia_mod:hemp` | no | **absent** |
| hops | `britannia_mod:hops` | no | **absent** |
| snow_peas | `britannia_mod:snow_peas` | yes | **absent** |
| peas | `britannia_mod:peas` | yes | **absent** |
| turnips | `britannia_mod:turnips` | yes | **absent** |
| apple | `britannia_mod:apple` | yes | produce/fruit/apple |
| pear | `britannia_mod:pears` | yes | produce/fruit/pears |
| peach | `britannia_mod:peaches` | yes | produce/fruit/peaches |
| lemon | `britannia_mod:lemon` | yes | **absent** |
| lime | `britannia_mod:lime` | yes | **absent** |
| orange | `britannia_mod:orange` | yes | **absent** |
| olive | `britannia_mod:olive` | yes | **absent** |
| plum | `britannia_mod:plum` | yes | **absent** |
| bell_peppers | `britannia_mod:bell_peppers` | yes | **absent** |
| cucumbers | `britannia_mod:cucumbers` | yes | **absent** |
| honeydew | `britannia_mod:honeydew` | yes | **absent** |
| cantaloupe | `britannia_mod:cantaloupe` | yes | **absent** |
| banana | `britannia_mod:banana` | yes | produce/fruit/banana |
| broccoli | `britannia_mod:broccoli` | yes | **absent** |
| cauliflower | `britannia_mod:cauliflower` | yes | **absent** |
| rhubarb | `britannia_mod:rhubarb` | yes | **absent** |
| celery | `britannia_mod:celery` | yes | **absent** |
| tobacco | `britannia_mod:tobacco` | no | **absent** |
| radish | `britannia_mod:radish` | yes | **absent** |
| parsnip | `britannia_mod:parsnip` | yes | **absent** |
| yam | `britannia_mod:yam` | yes | **absent** |
| rutabaga | `britannia_mod:rutabaga` | yes | **absent** |
| grapes | `britannia_mod:grapes` | yes | **absent** |

## BUG-19 — Offhand decorator ejection and case survival

**R19.1–R19.5:** offhand use ejects real stored contents into the world; leaves case intact; preserves full count/components; checks live root/rights and commits once; failed ejection preserves storage.

Tool `britannia_mod:interior_decorator_tool`, maxstack1/durability1; case `britannia_mod:display_case`. Independent/end/straight/corner/T/cross are states of one block with two cells and one root BE, not distinct item IDs. [registry/ItemRegistry.java:1219](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java:1219), [block/DisplayCaseBlock.java:81](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/block/DisplayCaseBlock.java:81), [registry/BlockEntityRegistry.java:50](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/registry/BlockEntityRegistry.java:50).

BE owns a **real ItemStack**, not an inventory reference/display token. storeOne copies one input including all components; full-stack save/load can preserve legacy count>1. takeDisplayedItem transfers the reference, clears/synchronizes immediately. displayedItem returns a live reference; preview must not mutate it. [block/entity/DisplayCaseBlockEntity.java:32](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/block/entity/DisplayCaseBlockEntity.java:32).

**Source path:** case useItemOn resolves upper→root and checks level.mayInteract/player.mayUseItemAt; occupied returns ItemInteractionResult.FAIL. Empty MAIN passes to useWithoutItem, which clears then adds to inventory, overflow popResource. With an inert nonempty main item, useOn can pass and OFF decorator is reached. Generic decorator FACING rotation changes only the selected cell; neighbor validation sees inconsistent facing and dismantles the structure with dropItem=false, so no case item is returned. [block/DisplayCaseBlock.java:185](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/block/DisplayCaseBlock.java:185), [item/InteriorDecoratorToolItem.java:126](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/item/InteriorDecoratorToolItem.java:126), [block/DecorativeMultiblockBlock.java:275](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/block/DecorativeMultiblockBlock.java:275), [block/DecorativeMultiblockBlock.java:326](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/block/DecorativeMultiblockBlock.java:326).

Grabby HIGHEST defers to decorator in either hand; it is not the reproduced blocker. Earlier mainhand block/use behavior is. Adding only an offhand branch to InteriorDecoratorToolItem.useOn cannot handle empty-main or placeable-main cases reliably. Sneaking bypasses ordinary block use and exposes unsafe rotation. A mainhand decorator also reaches that rotation; preserve meaningful behavior on other objects, not accidental case dismantling.

**Runtime:** named diamond sword, damage17, custom Owner/Origin/Quality; BE save/recreate equality passed. Empty MAIN/offhand decorator on upper returned CONSUME, transferred sword into inventory, world entities0, case intact. Refill +stick MAIN: MAIN PASS, OFF CONSUME, both cells air. Initial diagnostic failed its assumption that contents would remain stored, exposing the stronger defect.

Follow-up loaded legacy count3 named/custom diamonds and tested16 combinations (2 targets ×2 sneak states ×4 main items), always offhand decorator. Corrected active-area fixture conserved count/components in all16; results:

| MAIN item / posture | Root target | Upper target |
|---|---|---|
| Empty, normal | MAIN takes all3 into inventory; case remains | Same |
| Empty, sneaking | OFF rotation:3 world drops, case destroyed, case-item drop0 | Same |
| Stick, normal or sneaking | MAIN passes; OFF rotation:3 world drops, case destroyed, case-item0 | Same |
| Stone block, normal or sneaking | MAIN FAIL; contents3/case unchanged | MAIN CONSUME placement; contents3/case unchanged |
| Decorator, normal or sneaking | MAIN rotation:3 world drops; case destroyed, case-item0 | Same |

The first follow-up spaced fixtures beyond the active entity-query area and failed a drop-total assertion on the tenth case. Reusing the guaranteed active test area resolved it; **do not report merchandise deletion from that failed fixture**. Generic rotation/dismantling and lack of case item remain confirmed. No successful content-only ejection exists or is implemented.

**Rights:** current case content checks world/item-use permission, not a stored owner (none exists). StructureProtectionHandler protects break/place, not all content right-clicks. HouseBuildRights supplies authoritative target ownership where housing policy applies. Do not treat permissive outside-house/admin results as proof of ownership. New case operation should honor root/world/item-use and applicable housing protection, deny without falling into rotation/storage, and resolve malformed/missing roots safely. No new global container access policy inferred.

**Proposal:** case-specific offhand-tool recognition at the block interaction boundary early enough to own the first/main callback, with matching client recognition and respect for existing protection cancellation. Resolve both cells to one root service; matched occupied case takes precedence over mainhand default store/take/place/use. Empty case must remain safe without accidentally storing/spinning offhand tool. Preserve meaningful mainhand-only interactions separately.

Snapshot full stored count/components, choose reachable collision-safe position outside case toward interaction face, construct ItemEntity from copy, and confirm server insertion before clearing the exact still-current stack. Guard reentry on this root because spawn callbacks can run synchronously. Failed/refused insertion leaves content intact; on exception discard provisional entity and preserve/restore original content. Sync committed state once. Current takeDisplayedItem/popResource clears first; vanilla popResource neither exposes insertion success nor guarantees drops when block drops disabled or snapshots restoring. Do not reuse it as atomic ejection, reconstruct item type alone, silently delete or substitute inventory insertion. A small root-local operation suffices.

**Verification/risks:** both cells/connected variants, malformed roots,2hands/duplicate callbacks/rapid clicks/2players; named/damaged/quality/custom/absent-empty components, count1/legacy multi-count, full inventories; case+player+world count/component conservation and reachable drops. Rights denial and rejected ItemEntity insertion preserve exact storage. Success reload stays empty; refusal reload retains content. Case structure/facing/connection data unchanged. Real clients must show one removal and no ghost display after reconnect. No headless test certifies that visual outcome.

## Focused corrections to original decisions

| Decision / affected issues | Evidence and revised planning |
|---|---|
| D1; BUG-03 | Empty-only60seconds, previous hoed soil, downtime excluded are decided. Current reclaim is random-tick-only and returns unprepared community base; private conversion lacks deadline. Proposal: persist dimension gameTime deadline now+1200, deterministic loaded server tick, evaluate overdue empty plots on load; no wall-clock/offline subtraction. Chunk absence counts online simulation time; low TPS stretches wall time. Planting cancels expiry before committed crop/snapshot state. |
| D1 preparation timer | Community BE has3600-tick PreparedExpiresAt; fertilizer replaces BE and loses it. Restoring old absolute deadline can instantly unprepare soil. Capture prior hoed BlockState and remaining preparation budget; propose pause budget while fertilized and restore relative to current gameTime. Avoid fresh3600 reward on every retry; define safe old-data default. [block/entity/CommunityFarmBlockEntity.java:22](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/block/entity/CommunityFarmBlockEntity.java:22), [block/CommunityHoedFarmBlock.java:46](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/block/CommunityHoedFarmBlock.java:46). |
| D1 private/use accounting | Restore exact minecraft:farmland including MOISTURE, not dirt; independent later vanilla drying/trampling still applies. Keep source/house identity and uses distinct from seedability. Never reinitialize partially spent uses to5 or erase planted ownership/root age via timeout. Exact vanilla farmland has no BE; retained entitlement needs bounded persistence design. Q5 identifies remaining economic ambiguity, not reopening hoed/empty/downtime decisions. |
| D1 flowers | FlowerSoilSnapshot captures seed deadline/remaining uses, restores old deadline on uproot. Cancel/suspend empty timer in persisted flower snapshot too; never reclaim occupied flower. After uproot, use lifecycle accounting instead of resurrecting overdue planting deadline. [block/entity/FarmingBlockEntity.java:409](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/block/entity/FarmingBlockEntity.java:409), [block/entity/FarmingBlockEntity.java:482](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/block/entity/FarmingBlockEntity.java:482), [farming/FlowerSoilSnapshot.java:22](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/farming/FlowerSoilSnapshot.java:22). |
| D2; BUG-09 | Current fertilizer accepts exact ordinary dirt and rejects vanilla farmland. Add only empty farmland inside acting player's owned house plus existing prepared community access. HouseBuildRights.ownsHouseAt(dimension,target,UUID) requires matching StructureRecord owner and exact fullBox containment. No town membership/nearest-lot/client claim. [item/FertilizedDirtItem.java:35](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/item/FertilizedDirtItem.java:35), [structure/HouseBuildRights.java:155](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/structure/HouseBuildRights.java:155), [util/HouseUtil.java:87](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/util/HouseUtil.java:87). |
| D2 boundaries/reload | FullBox uses rotated bounds plus10-block basement. Manager indexes dimension/chunk then block-center containment. Codec persists owner/boxes; rehydrator restores from durable backend records at server start. Missing/not-yet-loaded record must fail owner eligibility. HouseFarmPlotBlock.mayManagePlot is different/permissive (Creative/op and missing-record fallback true), not a delegated-manager authority and unsuitable here. No delegated-manager inclusion proposed. [util/StructureUtils.java:121](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/util/StructureUtils.java:121), [structure/StructureRegionRehydrator.java:69](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/structure/StructureRegionRehydrator.java:69), [block/HouseFarmPlotBlock.java:240](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/block/HouseFarmPlotBlock.java:240). |
| D3; BUG-05/14 | Bucket/can/rain stay valid; fertilizer's initial moisture1 retained; rain qualifies without manual watering. bowl_of_water is plain Item with no soil/flower hydration reference; both-hand ServerPlayerGameMode probes returned PASS at moisture0 with unchanged bowl. Add bounded actual-hand bowl-care/remainder route; no dry-gate/initial-moisture rebalance. Document bowl increment through existing care abstraction, not silently equating it to bucket volume. [registry/ItemRegistry.java:345](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java:345), [farming/FlowerInteractionService.java:148](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/farming/FlowerInteractionService.java:148). |
| D4; BUG-08 | Eligible failure destroys produce; minimum refusal is separate and should not roll/consume/reward. Carrot and green onion annual plants consumed at harvest; perennial/tree failure follows lifecycle regrowth, not universal uproot. No usable produce refund. Odds, costs, seed/straw byproducts, gains/Creative remain choices; crop tier/threshold is not failure formula. |
| D5; BUG-08 | Grapes definition80 but gate explicitly NOT_APPLICABLE; GrapeSeedsItem direct path never calls gate. Remove both bypasses. Cultivated flower planting already species-gated; harvest lacks equivalent. Reuse poppy20/snowdrop40/lily50/foxglove65/campion10/hyacinth30/orfluer95; keep separate poppy stage7 knife/high-skill rule. [farming/FarmingCultivationGate.java:142](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/farming/FarmingCultivationGate.java:142), [item/GrapeSeedsItem.java:69](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/item/GrapeSeedsItem.java:69), [farming/FlowerPlantingService.java:73](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/farming/FlowerPlantingService.java:73), [farming/FlowerRegistry.java:97](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/farming/FlowerRegistry.java:97), [farming/FlowerInteractionService.java:185](C:/projects/britannia/mod/Britannia_Mod/src/main/java/com/seggellion/britannia_mod/farming/FlowerInteractionService.java:185). |
| D6; BUG-11/12/13 | Preserve existing dirt_path under placed custom fence; vanilla shovel provenance decided. Historical oak/custom reversion remains baseline, not desired outcome. Retain L→T→L adjacent-arm defect and collision1.0 versus vanilla1.5. Correct fence state/collision with narrow path-survival exception; collision height alone does not settle path solidity. Creating path under existing fence is outside requirement. |
| Artwork / BUG-01/14 | User supplies full-can art; code selects actual charge state/sync. Functional shader compatibility and fence model selection remain. BUG-04/06/07 excluded. |

## Remaining questions and technical choices

These do not reconfirm six decisions or five reports, and do not prevent completing discovery. Q1–Q3 were presented during investigation; no answer received by document completion.

| ID | Missing evidence / choice | Recommendation / affected proposed milestone |
|---|---|---|
| Q1 | Reported NPC/type, shard/city, missing sell entry versus post-click refusal; live revision/price/flags/stock/treasury and same-vendor controls | Obtain report identity/read-only catalog or logs. Mod omission clear, but M5 live acceptance/catalog repair cannot be certified without context. |
| Q2 | Include newly generated village/mansion/outpost crops/decorations, or landscape plants only? | Remove3 landscape features, preserve structure content pending answer. Blocks M6 structure expansion only. Existing blocks untouched either way. |
| Q3 | Eligible harvest probability formula; tool/fertility cost, seed/straw byproducts, failure skill gain, Creative/admin outcomes | One injected server roll after refusals; current-harvest loss with lifecycle regrowth, no success progress/usable produce on failure. No numeric odds invented. Blocks M3 outcome/economic contract, not gate routing. |
| Q4 | Native crops outside custom cultivation receive gates/failure rules too? | Keep separate native policy unchanged unless explicitly included. Blocks only M3 extension, not compatible native seeds inside FarmingBlock or BUG-15 landscape removal. |
| Q5 | Empty timeout forfeits unused fertilizer application, or retains remaining entitlement for later use, especially after prior harvests? Hoed BlockState alone cannot encode it. | Do not erase/replenish uses implicitly. Prefer preserving recorded remaining entitlement with explicit resume behavior; exact farmland needs small side-data persistence if retained. Blocks M1 final accounting/migration only, not decided restoration/timing/eligibility. |

Nonblocking engineering proposals:1200 online simulation ticks/load reconciliation; pause/restore hoe budget; role resolver with existing adapters; compatible-stack-first delivery; early case-specific dispatch and confirmed entity insertion; exact-ID feature suppression. These are implementation recommendations, not extra approved mechanics.

## Evidence ledger and reproducible commands

Historical evidence at unchanged mod HEAD reused: focused166 JUnit passes/6skips; broad **3462 executed JUnit passes/17skips**; existing **1102 GameTest passes**; original **5 temporary passes**. No broad rerun.

New JUnit: **83 tests passed,0 failed,0 skipped,10 suites**, BUILD SUCCESSFUL. Set JAVA_HOME and run from mod root:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot'
.\gradlew.bat test --tests 'com.seggellion.britannia_mod.bowlpreparation.*' --tests 'com.seggellion.britannia_mod.structure.DisplayCase*' --tests 'com.seggellion.britannia_mod.economy.TraderCommodityFilterTest' --tests 'com.seggellion.britannia_mod.economy.EconomicBuyback*' --tests 'com.seggellion.britannia_mod.bannerdyeing.DyeTubLoadingServiceTest' --tests 'com.seggellion.britannia_mod.worldgen.VanillaFeaturePolicyTest' --console=plain
.\gradlew.bat --no-configuration-cache -I tmp/gameplay-bugfixes/supplemental/diagnostic.gradle runGameTestServer --console=plain
.\gradlew.bat --no-configuration-cache -I tmp/gameplay-bugfixes/supplemental/followup.gradle runGameTestServer --console=plain
```

| Diagnostic run | Result / interpretation |
|---|---|
| Initial Gradle attempt without --no-configuration-cache | Task serialization null-array failure before tests; retry disabled configuration cache |
| Supplemental namespace,6 tests | **5 passed,1 failed,0 skipped**. Bowl matrix, dye matrix, hydration, crop audit and worldgen resources passed. Case test assumption that decorator leaves contents stored failed because case dismantled; retained as discovery finding, not a passing fix test. |
| First follow-up namespace,2 tests | **1 passed,1 failed,0 skipped**. Stems/template audit passed. Spaced case fixtures failed item-entity query conservation outside active area after10 gestures. |
| Corrected follow-up, same command, guaranteed active fixture area | **2 passed,0 failed,0 skipped**.16 case gestures conserve merchandise components/count; case destruction confirmed. Native stems/tag/template check also passed again. Do not add repeated runs into a misleading unique-pass total. |

Logs: [targeted-junit.log:1](C:/projects/britannia/mod/Britannia_Mod/tmp/gameplay-bugfixes/supplemental/targeted-junit.log:1), [diagnostic-build.log:1](C:/projects/britannia/mod/Britannia_Mod/tmp/gameplay-bugfixes/supplemental/diagnostic-build.log:1), [diagnostic-run.log:1](C:/projects/britannia/mod/Britannia_Mod/tmp/gameplay-bugfixes/supplemental/diagnostic-run.log:1), [followup-run.log:1](C:/projects/britannia/mod/Britannia_Mod/tmp/gameplay-bugfixes/supplemental/followup-run.log:1), [followup-active-area.log:1](C:/projects/britannia/mod/Britannia_Mod/tmp/gameplay-bugfixes/supplemental/followup-active-area.log:1). Sources: [java/com/seggellion/britannia_mod/gametest/SupplementalDiscoveryDiagnostics.java:1](C:/projects/britannia/mod/Britannia_Mod/tmp/gameplay-bugfixes/supplemental/java/com/seggellion/britannia_mod/gametest/SupplementalDiscoveryDiagnostics.java:1), [java/com/seggellion/britannia_mod/gametest/SupplementalFollowupDiagnostics.java:1](C:/projects/britannia/mod/Britannia_Mod/tmp/gameplay-bugfixes/supplemental/java/com/seggellion/britannia_mod/gametest/SupplementalFollowupDiagnostics.java:1). Temp init scripts register only designated namespaces and disposable working directories. Mock-player login hooks logged missing-auth/bootstrap errors; no credentialed economy fixture or sale call existed. Existing deprecation warnings remain. No Rails suite/database-backed integration, actual client input/visual test or naturally generated biome/structure test was run.

## Resume scratchpad and stop point

Discovery complete. Next separately authorized work is a multi-milestone implementation playbook, using this document plus revised planning/acceptance. No implementation branch or clean release artifact was created.

Final mod status: existing untracked FARMING_EMPTY_INVENTORY_TEST_CHECKLIST.md and FARMING_GAMELOOP_DISCOVERY.md; untracked docs/projects/gameplay-bugfixes/ contains3 updated documents plus this new one. No tracked source/asset/config/recipe modification. Temp sources/resources/logs/worlds, JSON audit and pre-edit copies of original3 docs live under ignored tmp/gameplay-bugfixes/supplemental/. Generated build outputs can contain diagnostics; do not distribute. Rails retains only pre-existing article modification and3 untracked playbooks/Zone.Identifier files. Both branches/HEADs unchanged.
