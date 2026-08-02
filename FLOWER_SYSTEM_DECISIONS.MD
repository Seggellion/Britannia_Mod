# Persistent Flower System Owner Decisions

> **Current rendering authority:** Corrective Milestone 11 supersedes A1/M3/M7 wording that described separate base-pass and dye-mask species/stage model resources. Historical milestone notes below remain as an implementation record, not the current asset contract.

Owner decision gate: **Approved** for Milestone 2 on branch `Farming` at HEAD `41fb51a568fb393e27a276d12cbcbfd26c8730d0`.

## B1 - Poppy skinning knife

- **Selected option:** Add `britannia_mod:skinning_knife` and use `britannia_mod:skinning_knives`.
- **Repository findings:** No registered skinning knife, source class, or item tag exists on `Farming`. `ModTags.Items` is the authoritative item-tag constant location.
- **Implementation consequence:** Milestone 2 adds only `ModTags.Items.SKINNING_KNIVES`. Item registration, tag JSON membership, recipe, assets, durability, and the Poppy interaction remain prohibited.
- **Implementation milestone:** Semantic tag contract in Milestone 2; tool content and interaction in the later item/interaction milestones.
- **Compatibility/testing:** Data packs may extend the tag later. Milestone 6 must verify Farming skill `>= 100`, exact stage 6, authorization, and no durability on denial.

## B2 - Flower administrator authorization

- **Selected option:** A flower administrator is a Creative-mode player or a server player with operator permission level 2 or greater.
- **Repository findings:** Creative checks use `Player.isCreative()`/`abilities.instabuild`; administrative commands use `ServerPlayer.hasPermissions(2)`. No external permission service exists.
- **Implementation consequence:** `FlowerProtectionService` centralizes this rule and recognizes explicitly authorized command/world-generation mutation reasons. No external permission dependency is introduced.
- **Implementation milestone:** Foundation in Milestone 2; all actual mutation handlers in Milestone 6 or their assigned later milestone.
- **Compatibility/testing:** Pure policy tests cover Creative, permission levels 1/2, protected denial, and system mutation. Later event-path tests must prove every handler delegates to the service.

## A1 - Rendering architecture

- **Selected option:** Two cached baked-model passes in a flower block-entity renderer: untinted base, then stored-colour-tinted grayscale dye mask, using shared geometry and alpha-disjoint artwork.
- **Repository findings:** `FarmingBlockEntityRenderer`, `CropVisualModels`, and `ClientModSetup.registerAdditionalModels` already use cached standalone baked crop models.
- **Implementation consequence:** No renderer, model JSON, model registration, or textures are created in Milestone 2. The stored colour contract exposes an opaque ARGB adapter for the later tint call.
- **Implementation milestone:** Renderer in Milestone 7; asset scaffolding only in its approved content milestone.
- **Compatibility/testing:** Later QA must verify pass order, alpha disjointness, reload stability, multiplayer agreement, Z-fighting, mip bleed, and dense-garden performance.

## A2 - Existing climate mapping

- **Selected option:** Map all species to the canonical existing farming climates only. Do not extend `FarmingClimate` or add a second region engine.
- **Repository findings:** `FarmingClimateResolver` produces `TEMPERATE`, `ICE`, `FIRE`, `WETLAND`, `TROPICAL`, `ARID`, `MAGICAL`, or `UNDERGROUND`. `CropDefinition.climateFit` gives preferred climates full fit, allowed climates 0.65 fit, and forbidden climates zero.
- **Implementation consequence:** `FlowerGrowthProfile` implements the shared `FarmingGrowthProfile` contract and delegates formula work to `CropQualityCalculator`. Every canonical climate is explicitly allowed or unsuitable for every initial definition.
- **Implementation milestone:** Definitions and validation in Milestone 2; live world resolution/growth in Milestone 5.
- **Compatibility/testing:** Tests reject alias/new/parallel climate values and unresolved mappings. The mappings are initial tuning, not final balance.

### Final climate mapping

| Species | Preferred | Tolerated | Unsuitable | Altitude (ideal / tolerated Y) | Rationale and unavoidable coarse-enum loss |
|---|---|---|---|---|---|
| Poppy | `TEMPERATE` | `ARID` | `ICE`, `FIRE`, `WETLAND`, `TROPICAL`, `MAGICAL`, `UNDERGROUND` | 45-125 / 20-170 | Sunny, well-drained meadow/farmland intent maps to temperate; dry foothill support maps conservatively to arid. The enum cannot distinguish meadow/farmland from forest or mild arid foothills from desert. |
| Snowdrop | `ICE` | `TEMPERATE`, `WETLAND` | `FIRE`, `TROPICAL`, `ARID`, `MAGICAL`, `UNDERGROUND` | 55-155 / 30-220 | Cool woodland/cold foothill intent maps to ice, with temperate forest and moist conditions tolerated. `WETLAND` also includes waterlogged ground, so this tolerance loses the design's “moist but well-drained” distinction. |
| Lily | `TEMPERATE` | `ICE` | `FIRE`, `WETLAND`, `TROPICAL`, `ARID`, `MAGICAL`, `UNDERGROUND` | 45-150 / 20-200 | Fertile temperate meadow/woodland-edge habitat maps directly; cool plains map to ice. The enum cannot express fertile soil habitat, and wetland is excluded because it over-represents waterlogging. |
| Foxglove | `TEMPERATE` | `WETLAND` | `ICE`, `FIRE`, `TROPICAL`, `ARID`, `MAGICAL`, `UNDERGROUND` | 70-180 / 40-240 | Woodland edge and moist hills map to temperate with wetland tolerated. The evaluator cannot distinguish moist slopes/partial shade from swamp or standing water. |
| Campion | `TEMPERATE` | `WETLAND` | `ICE`, `FIRE`, `TROPICAL`, `ARID`, `MAGICAL`, `UNDERGROUND` | 45-150 / 20-210 | Meadow, hedgerow, woodland-edge, coast, and damp grassland map primarily to temperate, with wetland tolerated for dampness. Coastal and hedgerow distinctions are lost. |
| Hyacinth | `TEMPERATE` | `ICE` | `FIRE`, `WETLAND`, `TROPICAL`, `ARID`, `MAGICAL`, `UNDERGROUND` | 40-120 / 20-170 | Temperate garden/meadow and well-drained plains map directly; cool woodland edge maps to ice. Garden drainage and shade are not expressible in the climate enum. |
| Orfluer | `MAGICAL`, `ICE` | `TEMPERATE` | `FIRE`, `WETLAND`, `TROPICAL`, `ARID`, `UNDERGROUND` | 110-220 / 70-280 | The fictional highland/magic-touched intent maps to magical and cool/ice climates; altitude supplies the highland distinction. Ancient ruins and mountain woodland cannot be represented directly. |

Hydration uses the existing discrete `FarmingBlockEntity` scale 0-5. Nutrients use its four normalized 0.0-1.0 values. The design's composite nutrient band is conservatively adapted to the same midpoint/tolerance for nitrogen, phosphorus, potassium, and organic matter with equal weights because the design does not provide nutrient-specific targets.

## A3 - Community farming plots

- **Selected option:** Flowers may be planted on community farming plots and must restore the proper community state after approved permanent uprooting.
- **Repository findings:** `FarmingBlockEntity` stores only `CommunityPlot` plus a seed deadline. Mature crop removal restores `britannia_mod:community_farm_block` in its default unprepared state; there is no unique community plot identifier.
- **Implementation consequence:** `FlowerSoilSnapshot` always distinguishes `PRIVATE_FARMING_BLOCK` from `COMMUNITY_PLOT`. Community snapshots require `FlowerCommunityRestoration`, currently the exact target block ID plus `prepared=false`. The state is NBT-ready and survives growth/reset because it belongs to persistent identity.
- **Implementation milestone:** Data contract in Milestone 2; conversion and persistence in Milestone 4; actual restoration on permanent uproot in Milestone 6.
- **Compatibility/testing:** Validation rejects missing community metadata and community data on private soil. Round-trip tests cover the restoration target. If community plots later gain unique IDs, the data version must be migrated explicitly.

## G1 - Flower-cutting blades

- **Selected option:** Reuse `britannia_mod:grain_harvest_blades`, including `britannia_mod:dagger`; charge one durability only after a successful authorized reset to stage 1.
- **Repository findings:** `ModTags.Items.GRAIN_HARVEST_BLADES` and its JSON are already authoritative and include all vanilla swords, `viking_sword`, and `dagger`. Existing crop harvest damages tools only after success.
- **Implementation consequence:** Do not duplicate members into a flower-only list. `FlowerResetReason.SWORD_CUTBACK` is distinct from permanent removal.
- **Implementation milestone:** Contract recorded in Milestone 2; interaction and durability in Milestone 6.
- **Compatibility/testing:** Denied, invalid, protected, client-only, or no-op attempts must cost no durability and produce no drops.

## G2 - Permanent uprooting tools

- **Selected option:** `britannia_mod:farming_hoe` plus items in `britannia_mod:root_crop_shovels`; charge one durability after successful permanent uprooting.
- **Repository findings:** `ItemRegistry.FARMING_HOE` is the canonical hoe and `ModTags.Items.ROOT_CROP_SHOVELS` is the existing shovel policy.
- **Implementation consequence:** `FlowerRemovalReason.PERMANENT_UPROOT` ends the flower instance and restores the preserved private FarmingBlock soil or community plot. It is intentionally not a stage-1 reset.
- **Implementation milestone:** Reason/restoration contracts in Milestone 2; behavior in Milestone 6.
- **Compatibility/testing:** Tests later must prove restoration happens before durability is charged and that denied actions preserve state and durability.

## G3 - Harvest and seed recovery

- **Selected option:** Shearing a mature flower yields one harvested flower item, keeps the planted flower, and resets it to stage 1 without changing persistent identity. Sword cutback yields no item or seed. The harvested item uses the existing item-to-seed activation mechanism.
- **Repository findings:** `CropSeedExtractor.shouldExtract` accepts off-hand use or shift-use. `CropSeedExtractor.tryExtractSeed` creates one seed on the logical server, adds it to inventory or drops it, plays `SoundEvents.PUMPKIN_CARVE`, and consumes one source item unless `abilities.instabuild`. It applies no cooldown, animation, skill award, or extra game-mode rule. `SeedExtractableHealingCropItem.use` invokes extraction before ordinary eating; `GrapesItem.use` does the same and preserves grape variety. Mapping is a per-item seed supplier (or the grape-specific registry reference), not a universal `CropRegistry` reverse lookup. Only produce registered with those item classes currently supports the mechanism; it is not literally enabled on every crop item.
- **Implementation consequence:** Each `FlowerDefinition` contains stable harvested-item and seed-item IDs so the later harvested flower item can delegate to `CropSeedExtractor`; no parallel interaction is designed.
- **Implementation milestone:** Mapping contract in Milestone 2; item registration later; shearing/reset and item-to-seed behavior in Milestone 6 or its assigned interaction milestone.
- **Compatibility/testing:** Later tests must verify off-hand and shift-use ordering, one seed, one-item consumption outside Creative, server authority, overflow drop, sound, and preserved flower identity.

## G4 - Indirect destruction

- **Selected option:** Protected flowers are explosion/fluid immune, reject unauthorized replacement, block pistons, and produce no denied drops. Ordinary flowers block pistons but otherwise receive normal environmental destruction. Authorized commands and world generation are administrative/system mutation.
- **Repository findings:** FarmingBlock has no comprehensive indirect-protection precedent; command authorization consistently uses operator level 2.
- **Implementation consequence:** `FlowerMutationReason` distinguishes player/environment paths from `ADMIN_COMMAND` and `WORLD_GENERATION`; `FlowerProtectionService` owns authorization. No block/event behavior is implemented in Milestone 2.
- **Implementation milestone:** Contracts in Milestone 2; block and event enforcement in Milestone 6/later lifecycle work.
- **Compatibility/testing:** Later bypass tests must cover explosions, fluids, replacement, pistons, drops, commands, and world generation.

## AS1 - Texture resolution

- **Selected option:** Every aligned in-world base/dye-mask pair is 128x128 pixels.
- **Repository findings:** Existing crop assets vary from 16 to 256 pixels; 128x128 is the most common inspected square size but is not enforced by code.
- **Implementation consequence:** Both files must share dimensions, UV layout, transparent padding, and pixel alignment. No assets are created in Milestone 2.
- **Implementation milestone:** Binding contract now; placeholder/final assets in Milestones 3 and 7.
- **Compatibility/testing:** Asset validation must reject mismatched dimensions/UV alignment and any third in-world texture.

## N1 - Persisted identity and provenance

- **Selected option:** Persist planter UUID when player-planted, stable planting-region name and planting climate, current authoritative quality, compact colour tint, species, stage, origin, protection, soil/community restoration state, data version, and farming growth state. Do not persist a display-name snapshot.
- **Repository findings:** Current crop harvest calculates quality from current conditions and writes a 1-100 score; it does not persist planting quality. `FruitProvenance` writes the current Rails region name or `Unknown` onto harvested items. Farming growth recalculates current climate from the world.
- **Implementation consequence:** `FlowerRegionProvenance` is captured once at planting. Current resolved climate remains a live growth-evaluator input and is not stored under a second ambiguous field. `FlowerQuality` is one persisted 1-100 authoritative last-calculated value. It starts at the existing crop default of 50, is recalculated from current conditions by the future harvest/quality path, and replaces the prior value rather than creating separate planting/current fields. `FlowerPersistentState` stores these values and has an NBT round trip with no selector dependency.
- **Implementation milestone:** Contracts in Milestone 2; block-entity persistence/synchronization in Milestone 4; live quality calculation in Milestone 5/harvest work.
- **Compatibility/testing:** Round-trip tests cover UUID, provenance, quality, raw tint, soil, community restoration, stage, origin, and growth state. Later migrations must preserve unknown saved tint values rather than rerolling.

## C1 - Flower colour capacity and Dye Tub compatibility

- **Selected option:** Store one compact 24-bit RGB tint integer, selected from an explicit per-species weighted allowlist. Do not use a flower-colour enum or per-colour registration/model/texture.
- **Repository findings:** The active `Farming` branch contains no Dye Tub source classes, resources, serialization, synchronization, validation, or tint-application symbols. `git ls-tree` finds only unrelated `LabelColor` and `GrapeColor`; stale log references to `bannerdyeing` tests are not active-branch implementation evidence.
- **Implementation consequence:** `FlowerColor` is a compatibility-ready raw RGB tint value (`0x000000..0xFFFFFF`), `FlowerColorDefinition` supplies configured named values, and NBT stores `ColorTint` as an int. `opaqueArgb()` is the future renderer boundary. Direct Dye Tub reuse is impossible on this branch and is not claimed.
- **Implementation milestone:** Value/palette/serialization contracts in Milestone 2; reassess direct adapter reuse if a Dye Tub implementation later appears on `Farming` before renderer work.
- **Compatibility/testing:** Capacity is 16,777,216 values, comfortably above the requested approximately two million, without allocating a table. Tests verify arbitrary values, palette allowlists, defaults, validation, deterministic selection, and round-trip persistence.

## Remaining owner decisions

None block Milestone 2. Later balancing, final art, and live interaction feedback remain milestone-scoped implementation work rather than unresolved owner-gate decisions.

## Milestone 3 implementation consequences

### M3.1 - Ordinary flower and seed item registrations

- All seven harvested flower items and seven seed items are registered as ordinary `net.minecraft.world.item.Item` instances.
- `CropSeedItem` was not reused because it represents ordinary crop planting, and `SeedExtractableHealingCropItem` was not reused because it would add unrelated food/healing behavior.
- `FlowerRegistry` remains the only authoritative seed-to-species mapping. Milestone 3 does not implement `CropSeedExtractor` activation; the future interaction can delegate to that existing service without changing registry IDs.

### M3.2 - Placeholder model and texture structure (historical; superseded by Corrective Milestone 11)

- Rendering Option A is represented by 49 base pass models and 49 dye-mask pass models inheriting one shared four-plane cutout geometry parent.
- Every logical stage uses exactly `stage_<n>_base_texture.png` and `stage_<n>_dye_mask.png`; the particle reference aliases the paired base texture.
- Every in-world PNG is 128x128. Base and mask share the same full-canvas UV and pixel grid, and their visible alpha is disjoint.
- Stages 1 and 2 intentionally have transparent dye masks because the placeholder progression has no visible bloom at those stages. Stages 3-7 contain neutral grayscale tint regions.
- Poppy stage 7 has a distinct reserved placeholder model and texture pair, but no advancement or other gameplay behavior is implemented.

### M3.3 - Deterministic placeholder safety

- `tools/generate_flower_placeholders.py` uses only the Python standard library and creates deterministic PNG/JSON/Markdown output.
- Its default mode is create-only. `--overwrite-placeholders` verifies current files against `tools/flower_placeholder_hashes.json` before writing, and `--check` performs a non-writing byte-for-byte audit.
- The generator therefore refuses to overwrite owner replacement artwork that no longer matches the recorded placeholder hash.

### M3.4 - Content grouping and deferred skinning knife

- Flower produce and seeds are appended to the existing Britannia World farming produce/seed groupings without reordering existing entries.
- `britannia_mod:flowers` and `britannia_mod:flower_seeds` provide grouping tags; neither tag duplicates the authoritative one-seed-to-one-species mapping.
- The dedicated skinning knife remains deferred. Milestone 3 adds no item, durability, recipe, balance, or Poppy interaction for it.

## Milestone 4 implementation consequences

### M4.1 - One generic persistent flower block

- `britannia_mod:flower_block` and `britannia_mod:flower_block_be` are the sole block and block-entity registrations for all seven species. Species, stage, colour, identity, provenance, quality, growth state, and the soil restoration snapshot live in the block entity rather than multiplying registry entries.
- The generic block has no block item, random tick, growth handler, harvest interaction, cutback, uproot, protection enforcement, renderer, or client-only class reference in this milestone.
- Six temporary hydration variants render only the existing farming-soil models. They are an explicit Milestone 4 bridge and do not select flower species/stage models or compete with the Milestone 7 renderer.

### M4.2 - Atomic FarmingBlock conversion

- Flower planting is integrated in `FarmingBlock.useItemOn` after existing care-item handling and before grape/ordinary-crop planting.
- The held item's registry ID is resolved through `FlowerRegistry`; a tagged but unmapped flower seed is rejected rather than falling through to ordinary crop behavior.
- All non-random validation completes before the colour selector is invoked exactly once. The service then captures the complete farming soil/block-entity snapshot, builds the complete persistent state, replaces the block, initializes and verifies the flower block entity, synchronizes it, consumes one seed outside Creative, and emits the existing crop-plant sound.
- Any exception after replacement restores the exact original block state, every farming block-entity field, community restoration metadata/deadline, and held-stack count. Expected invalid inputs return an explicit non-mutating result instead of relying on partial mutation or exception control flow.

### M4.3 - Planting identity, authority, and skill behavior

- Player planting records the player's UUID. Creative mode or server operator permission level 2 records administrative origin and protected state; otherwise origin is player and protection is false. Pre-existing soil preparation ownership does not change flower origin.
- Planting region name and climate are immutable provenance captured once; future current-climate evaluation remains live world state. Initial authoritative quality is 50, matching the existing crop default.
- No Farming skill award is granted at flower planting because `FlowerDefinition` has no crop tier or skill modifier from which to derive the existing award formula. Adding a guessed amount would be an unauthorized balance decision. Existing planting sound feedback is reused.

### M4.4 - Persistence, synchronization, and compatibility

- Full server persistence includes data version, species, stage, exact RGB tint, quality, planting origin, protection, optional planter UUID, planting-region provenance, complete growth state, hydration, fertilizer level, N/P/K/organic matter, soil origin, optional community restoration target/prepared state, and the community seedable deadline.
- Client update tags and packets contain the shared visual/gameplay identity but deliberately omit planter UUID. Multiple observers deserialize the same species, stage, colour, quality, provenance, and soil state without rerunning colour selection.
- Unknown saved species IDs and RGB values are preserved. Unknown colours receive a deterministic visual fallback without rewriting saved identity; invalid stages clamp deterministically with bounded warnings. Impossible community metadata is rejected rather than silently normalized.

## Milestone 5 implementation consequences

### M5.1 - Shared evaluator and live growth entry point

- `FlowerGrowthProfile` continues to implement the Milestone 2 `FarmingGrowthProfile` contract. The only shared interface addition is `baseGrowthTicks()`, which existing `CropDefinition` already supplies through its record field; crop values and formulas are unchanged.
- `FlowerGrowthEvaluator` is an adapter, not a second farming engine. It builds the existing `CropGrowthContext` and calls `CropQualityCalculator.weightedNutrientFit`, `hydrationFit`, and `growthMultiplier`. `FarmingClimateResolver` dynamically resolves the current world climate on every flower growth evaluation; saved planting climate remains historical provenance only.
- `FlowerBlock.randomTick` follows the existing FarmingBlock cadence: synchronize BE hydration to blockstate, apply the shared 10% hydration-decay roll, apply the shared rain-at-`pos.above()` minimum hydration of 2, then ask `FlowerBlockEntity` to evaluate growth. The block is randomly ticking only on the logical server through Minecraft's `ServerLevel` entry point.
- `FarmingBlock.HYDRATION_DECAY_CHANCE`, `shouldDecayHydration`, and `hydrationAfterRain` are now shared helpers used by both crops and flowers. Existing FarmingBlock sequencing and results are unchanged.

### M5.2 - Initial timing conversion and 1-based stages

- The design supplies normalized growth intent but no exact flower tick counts. Initial flower timings therefore use the existing crop `baseGrowthTicks` unit and its observed 4-10 range: Poppy 5, Snowdrop 7, Lily 8, Foxglove 9, Campion 6, Hyacinth 7, and Orfluer 9. These are initial tuning values, not final balance.
- One authoritative mapping converts normalized progress to a 1-based stage: `1 + floor(progress * naturalMaximumStage)`, clamped to `1..naturalMaximumStage`. Progress 0 is stage 1; progress 1 is deterministic natural maturity.
- Automatic Poppy progression uses natural maximum 6 and can never select stage 7. A Poppy already raised manually to reserved stage 7 is treated as terminal and cannot be demoted by natural ticking. The other six initial species use natural maximum 7. Mature flowers remain planted at progress 1 and receive no further automatic advancement or reset.

### M5.3 - Blocking, quality, identity, and perennial reset

- Zero hydration preserves FarmingBlock's outer growth precondition and produces a blocked flower state. Unsuitable climate or altitude produces the existing zero multiplier. Poor nutrient/hydration fit otherwise changes speed through the unchanged crop multiplier formula. Restored conditions clear the blocked flag and resume from saved progress.
- Persisted flower quality remains stable at its current authoritative value during natural growth. `CropQualityCalculator` can estimate current fit for diagnostics, but quality mutation and harvested-item quality remain deferred to the Milestone 6 harvest path.
- `FlowerBlockEntity.resetToStageOne(FlowerResetReason)` is the central logical-server reset. `HARVEST`, `SWORD_CUTBACK`, `PRUNING`, and `ADMIN_RESET` are represented, but no player interaction calls the method in this milestone. Reset changes only stage/progress/timing/blocked state and synchronizes; species, exact tint, origin, protection, planter UUID, provenance, quality, soil/fertilizer values, and community restoration state remain unchanged.
- Colour selection is absent from random tick, evaluation, advancement, blocking, reset, persistence, and synchronization paths.

### M5.4 - Care interaction deferral and diagnostics

- Player watering and fertilizer clicks on `FlowerBlock` remain deferred. The current care handlers are coupled to `FarmingBlockEntity`; enabling a partial flower variant before Milestone 6's central mutation authorization would let ordinary players change protected flowers. Rain, hydration decay, blockstate synchronization, saved soil nutrients, and condition-sensitive growth are active now.
- `/farming debug` recognizes the generic FlowerBlockEntity and reports species, exact tint, stage/maxima, progress, timing, mature/blocked state, hydration/nutrients, current and planting climates, altitude, multiplier, blocking reason, persisted-quality authority, and community origin. It does not expose the planter UUID.

## Milestone 6 implementation consequences

### M6.1 - Central mutation policy and event boundary

- `FlowerProtectionService` remains the only administrator/protection decision point. All direct interactions and the left-click, normal-break, explosion, and fluid handlers pass an explicit `FlowerMutationReason`; every flower returns piston reaction `BLOCK`.
- Protected environmental mutation is denied even without an actor. Ordinary environmental mutation follows the block/event default and produces no configured flower drop. Explicit command, world-generation, admin-removal, and system reasons remain authorized.

### M6.2 - Interaction order, care, harvesting, and seed extraction

- `FlowerBlock` right-click order is care, mature scissors harvest, tagged skinning-knife Poppy advancement, farming-hoe/root-shovel uproot, protected replacement denial, then superclass behavior. Adventure blade cutback is separate and canceled on both sides.
- `FarmingSoilCare` centralizes nutrient item identity and normalized addition/clamping for crops and flowers. Flower watering uses the existing 0-5 scale, watering-can charges, bucket replacement, sounds, consumption, and Farming tending precedent. The older crop-only nutrient event duplication remains documented rather than expanded.
- Shearing creates exactly one mapped harvested item, applies the shared quality and region-provenance component convention, resets and persists the same flower, then damages scissors once. No seed is returned directly.
- `HarvestedFlowerItem` uses `CropSeedExtractor.shouldExtract` and `tryExtractSeed`: off-hand or shift-use produces exactly one `FlowerRegistry` seed, plays the existing pumpkin-carve sound, consumes one produce item outside Creative, has no cooldown/animation/skill award, and cannot duplicate client-side.

### M6.3 - Removal, indirect policy, and Poppy mastery

- Farming hoe and `root_crop_shovels` permanently restore the preserved private FarmingBlock soil or unprepared community block atomically. No uproot drop is created. Normal break uses the same restoration and no-drop policy so community restoration cannot be bypassed.
- Protected flowers are removed from explosion target lists and reject fluid placement; ordinary flowers keep normal environmental behavior. Every flower blocks piston movement.
- `skinning_knife` is a recipe-less non-combat utility item with 128 durability, default non-stackable durability behavior, no declared repair ingredient, farming-tool creative placement, and a generated placeholder icon. Recipe/economy placement remains deferred.
- Exact Poppy stage 6 plus Farming 100.0 or greater, `skinning_knives`, server authority, and mutation authorization advances to stage 7. Successful advancement costs one durability and preserves identity/soil/quality. Any reset returns to stage 1, and natural growth remains capped at 6 without a saved unlock flag.

## Milestone 7 implementation consequences

### M7.1 - Client renderer, registration, and soil boundary

- `client/renderer/FlowerBlockEntityRenderer` is registered exactly once for `BlockEntityRegistry.FLOWER_BLOCK_BE` through the existing client-only `EntityRenderersEvent.RegisterRenderers` subscriber. No common registry, block, block entity, domain, or persistent-state class references it.
- The normal FlowerBlock blockstate continues to render the six hydration-driven farming-soil wrapper models. The block-entity renderer renders only the species/stage flower planes one block above that soil and never duplicates the soil model.
- The renderer follows the farming crop transform: translate to block center and soil top, apply the same deterministic position/species yaw function, translate back, acquire one `RenderType.cutout` buffer, then render base first and dye mask second with the same pose, block state, packed light, and packed overlay.

### M7.2 - Authoritative model resolver and reload behavior (historical; superseded by Corrective Milestone 11)

- `client/renderer/FlowerVisualModels` precomputes 49 base and 49 dye-mask `ModelResourceLocation` identifiers for all seven species and stages 1-7. `ClientModSetup.registerAdditionalModels` registers all 98 standalone pass models explicitly; blockstate discovery is not relied upon.
- Resource identifiers remain cached because they are immutable. `BakedModel` instances are never stored in static or renderer fields: every render reacquires the model from Minecraft's reload-managed `ModelManager`, which owns the baked-model cache. Reload clears only bounded diagnostic sets and never reads or writes flower state.
- Valid stages map exactly. Invalid known-species stages clamp visually to 1-7 without persistence mutation. Unknown species use the deterministic Poppy stage-1 model pair and neutral white tint without replacing the saved species. A missing baked model attempts the same safe pair and skips the pass if even that fallback is unavailable. All unexpected-state and missing-model diagnostics are bounded.

### M7.3 - Tint, pass order, and geometry discipline

- The renderer reads the synchronized `FlowerPersistentState.color().tintValue()` directly. Every valid 24-bit value, including values not present in the original planting palette, is converted exactly as `R/255.0F`, `G/255.0F`, `B/255.0F`, alpha 1.0. No selector, palette weight, planter UUID, nutrients, or growth progress participates in rendering.
- An impossible numeric tint outside `0x000000..0xFFFFFF` uses the known species' configured fallback colour; an unknown species uses neutral white. These are visual-only fallbacks and do not rewrite state.
- The base baked model is rendered in white before the paired grayscale mask is rendered with the saved tint. Both passes inherit the same four-plane geometry and use identical transforms. No depth offset, translucent layer, emissive layer, third pass, or third in-world texture was introduced; alpha-disjoint placeholder artwork remains the Z-fighting control.
- Expected visible-flower cost is two cached baked-model lookups/submissions and one cutout buffer/pose transform. Model JSON, textures, registries, palette weights, and large collections are not parsed or rebuilt per frame.

### M7.4 - Validation corrections and corrupt-tint preservation

- Live same-species colour comparison showed that `ModelBlockRenderer` applies the supplied RGB multiplier only to tint-indexed baked quads. The shared four-plane parent therefore declares `tintindex: 0` on every face. Both passes inherit that contract: white leaves the base artwork unchanged, while the mask receives the exact resolved tint. The generator, hash ledger, manifest, and rendering contract test enforce this requirement.
- The ordinary public `FlowerColor(int)` constructor still rejects values outside the 24-bit RGB range. Deserialization alone may construct a raw saved tint so corrupt NBT survives load/save unchanged and reaches the existing renderer fallback. This avoids clearing the entire block-entity state or silently rewriting the corrupt value; bounded persistence and rendering warnings identify the condition.

## Milestone 8 implementation consequences

### M8.1 - Approved species values retained

- The species-by-species comparison found `FlowerRegistry` already matches the design palettes, weights, fallbacks, stage limits, altitude bands, and initial base-growth timings. The canonical climate conversion and shared midpoint/tolerance nutrient adaptation remain governed by A2; legacy `FarmingClimate` aliases outside the approved canonical eight are not accepted by flower profiles.
- No palette, profile, timing, crop definition, renderer, saved tint, harvest quantity, or seed mapping was changed merely to create Milestone 8 activity. All seven definitions remain initial tuning rather than final production balance.

### M8.2 - Player-facing messages and acquisition boundary

- Protected-action denial and the Poppy stage-7 requirement now use `en_us.json` localization keys. Existing debug-command output remains literal under the repository's established diagnostic convention.
- The only approved content sources remain Creative/admin access, mature shearing, and harvested-item seed extraction. Recipes, natural generation, loot, merchants, economy/Rails placement, and a survival acquisition source for the skinning knife remain deferred.

### M8.3 - Deterministic completeness and viability evidence

- `FlowerSpeciesContentTest` is the Milestone 8 audit suite. It locks all seven IDs and item mappings, the 8/4/10/9/6/9/9 palette counts, weights/fallbacks, exact stage/timing/profile mappings, practical positive maturity, slower tolerated climates, blocked unsuitable climates, localization/creative/tag/model/texture coverage, harvest/seed wiring, and absence of unapproved acquisition data.

## Milestone 9 implementation consequences

### M9.1 - Strict contention is a transient single-winner transaction boundary

- Strict two-client raw-input validation established that valid flower mutations which arrive close together must not chain through intermediate states. Each flower block entity therefore accepts one player-driven state mutation in a ten-game-tick contention window. The gate is transient, server-authoritative, absent from NBT/update tags, and excluded from natural growth; later intentional input remains available at the exact window boundary.
- Harvest, care, cutback, uproot initiation, and Poppy mastery continue to use the existing centralized services and successful-only item/skill effects. The gate changes only which near-simultaneous transaction commits; it does not change any approved output, durability, skill amount, stage rule, protection decision, or save contract.
- Uproot replaces the flower block entity with the saved soil block, so its losing follow-up packet cannot use the per-entity gate. A separate transient position/dimension guard covers the same ten-tick window after successful restoration and suppresses only interaction at that replaced position.

### M9.2 - Raw event routing follows authoritative runtime identity

- NeoForge 1.21 resolves the built-in skinning-knife membership from the singular `data/britannia_mod/tags/item/skinning_knives.json` path. The inert plural legacy copy was removed from generator ownership, its hash ledger, tests, and resources so there is one authoritative path and no duplicate membership contract. A highest-priority right-click boundary routes tagged knives on flowers into `FlowerInteractionService`, preserving the central policy when the ordinary block-use path is not reached by the raw packet.
- Adventure cutback uses the server player's actual `GameType.ADVENTURE`, not `abilities.mayBuild`; operator permission can otherwise leave that ability true even while the player is in Adventure mode. Non-server fallback keeps the existing ability-based predicate for client cancellation.

### M9.3 - Support and fluid runtime boundaries

- Flower blocks have no support-survival callback. Removing the block below leaves the flower floating and preserves its exact persistent state; this is the documented neighbor/support behavior rather than an implicit uproot or drop path.
- Ordinary and protected flowers are both inherently non-replaceable to normal water placement while occupied. The approved protection policy still denies destructive fluid mutation of protected flowers and permits ordinary environmental mutation, but normal placement cannot exercise that distinction. Validation records the unreachable destructive fixture accurately instead of weakening block replacement or inventing a bypass.

## Milestone 10 closeout decisions

- Milestone 9 owner approval is recorded by the isolated commit `7b7a4384686e52db4cafeb26e38ece3f683ac316` (`test(flowers): complete regression and multiplayer coverage`). Milestone 10 does not reopen approved gameplay, balance, persistence, protection, or rendering decisions.
- The existing implementation-status document is the sole final closeout record. No overlapping `FLOWER_SYSTEM_CLOSEOUT.md` is required.
- Current generated flower art remains technical placeholder art. Technical asset-contract validation and the earlier owner-approved renderer result do not constitute approval of any final species artwork.
- Normal final-art replacement preserves the existing paths, 128x128 paired canvas, UV layout, transparent padding, alpha-disjoint base/mask ownership, grayscale visible mask RGB, shared `tintindex: 0`, and exact two-pass renderer. It requires no Java change.
- The generator remains a placeholder scaffold with create-only default, non-writing `--check`, and hash-guarded overwrite. It is not a final-art conversion or approval pipeline.
- No additional owner decision blocks technical closeout. Future artwork approval and every item in the deferred-work register require separate owner authorization.

### M10.1 - Final owner approval

- The owner approved the Milestone 10 documentation and implementation handoff. Milestone 10 is **Approved**, and the Persistent Flower System implementation milestones are complete on `Farming`.
- This approval covers the implemented technical feature and closeout records only. It approves zero placeholder textures as final artwork; final artwork remains pending a separate owner review and replacement process.
- Survival flower/seed acquisition, survival skinning-knife acquisition, recipes, natural generation, merchants, economy, Rails integration, and all other deferred expansion remain unimplemented.
- No merge, push, pull request, release tag, deployment, or production-readiness claim is included in this approval.

## Corrective Milestone 11 - One canonical model, two textures

- Each flower species/growth-stage lookup resolves one canonical multi-plane model at `assets/britannia_mod/models/block/flowers/<species>/stage_<n>.json`.
- The canonical model exposes the fixed base texture, grayscale dye-mask texture, and a particle alias to the base texture. The two unique image resources remain `stage_<n>_base_texture.png` and `stage_<n>_dye_mask.png`.
- `FlowerVisualModels` registers and resolves 49 canonical stage models. Separate base-pass and dye-mask stage-model resources are prohibited.
- `FlowerBlockEntityRenderer` requests one canonical baked model. It renders those baked quads once with the block-atlas base texture and white tint, then renders the same geometry again through normalized UV remapping to the directly bound dye-mask texture with the saved flower tint. Direct binding avoids relying on an unused model texture key being stitched into the block atlas.
- The shared transform, cutout rendering, packed light/overlay, `tintindex: 0`, and alpha-disjoint artwork remain the Z-fighting and tint-isolation strategy. No gameplay, registry, persistence, networking, palette, growth, interaction, or item contract changes.
- The placeholder generator may retire only prior hash-verified pass-specific stage JSONs during this migration. Owner-replaced artwork remains protected by the existing ledger checks.
- Runtime validation established that the two render types must be acquired sequentially: the base pass is fully submitted before the directly bound mask buffer is requested. This preserves the passed `MultiBufferSource` lifecycle and prevents emission into a buffer that render-type switching has already ended.
- Final validation passed 15 suites / 108 tests, the focused 9-test renderer suite, generator and repository audits, persisted-world dedicated-server restart/save, corrected client reconnect, live resource reload, chunk unload/reload, and original-resolution review of nine fresh captures. **Corrective Milestone 11 — Approved.**

## Farming Skill Progression Milestone 14 - Definition metadata

- The authoritative schema is `FarmingSkillRequirement.minimumFarmingSkill()`, implemented as an explicit finite `float` in inclusive range `0..100`. This matches the existing Farming skill representation and preserves the approved inclusive future comparison boundary.
- `CropDefinition` owns requirements for all 67 crop-catalog species, including berries, trellis/lifecycle-special crops, orchard species, compatibility definitions, and Grapes. `FlowerDefinition` owns requirements for the seven persistent flowers. `FruitTreeDefinition` remains a non-owning structure overlay and must resolve its crop-owned value; aliases never own values.
- Missing requirements never default to zero. Construction and centralized bootstrap validation fail with the species, planting item where relevant, invalid value, and approved proposal source. Duplicate IDs, conflicting values, ambiguous items, incomplete counts, unresolved mappings, and fruit-tree overlay conflicts also fail closed.
- Common setup runs the definition/mapping validator once after deferred registrations are available. Proposal/catalog reconciliation remains test-only so the Markdown authority is not duplicated into a production value table.
- Requirements are definition metadata only. They are not persisted to crop, flower, tree, or item state and are not networked. Existing saved species IDs resolve current metadata automatically; no migration or compatibility fallback is required.
- Native vanilla planting routes remain excluded and untouched. Catalog compatibility definitions may carry approved metadata but authorize no native interception.
- Grapes carries the approved Farming 80 metadata only through its existing `CropDefinition`. The established grape planting, variety, component/NBT, name, rendering, and harvest systems are unchanged; future `a brown seed` presentation is deferred.
- Ordinary Poppy carries Farming 20. The independent stage-7 mastery contract remains Farming 100, stage 6, an approved skinning knife, and authorized mutation. Neither threshold derives from the other.
- These decisions do not reopen DECISION-001 through DECISION-016 and do not authorize Milestone 15 planting enforcement, Milestone 16 presentation, acquisition/economy work, or any recipe behavior.

## Farming Skill Progression Milestone 15 - Server-authoritative cultivation gate

- New planting uses one central inclusive policy: `current Farming >= minimumFarmingSkill`. The result vocabulary is `ELIGIBLE`, `INSUFFICIENT_SKILL`, `NOT_APPLICABLE`, `UNRESOLVED_SPECIES`, `APPROVED_BYPASS`, `NON_PLAYER_POLICY`, and `SKILL_DATA_UNAVAILABLE`.
- The server observes explicit skill-data load state. Missing, loading, and failed Rails data blocks planting; it is never treated as an authoritative zero and never fails open. Creative and real operator-level-2-or-higher players retain the approved bypass.
- Fake and non-player automation is denied because no trustworthy owner-UUID skill attribution contract exists. World generation, commands, growth, care, harvest, perennial reset, and protection decisions remain outside the new-planting gate.
- Ordinary crop eligibility is checked immediately after authoritative non-grape definition resolution and before species-specific support/clearance feedback, crop state, blocks, structures, consumption, sounds, or Farming awards. Eligible and bypassed players still pass through every existing occupancy, support, soil, and structure rule. Flowers are checked after registered definition/target validation and before soil snapshot, colour selection, conversion, persistence, consumption, or success feedback.
- Direct custom seed, farming-block, trellis, tall-crop, berry, orchard, root/tuber/bulb, and lifecycle-special paths converge on the ordinary transaction. All seven flowers converge on the flower transaction. Native vanilla planting is not intercepted.
- Grapes retains Farming 80 metadata but remains explicitly exempt from active Milestone 15 enforcement. Its planting, varieties, component/NBT, naming, rendering, and harvest code is untouched; `a brown seed` remains deferred to Milestone 16.
- Denials consume the interaction, do not consume the item or mutate the target, and trigger a server correction broadcast. Localized action-bar feedback uses only a generic unidentified material category and current/required Farming values, never the resolved species identity.
- Milestone 15 adds no viewer-specific item names, tooltip, narration, hover text, recipes, acquisition/economy behavior, requirement persistence, or new network payload.
