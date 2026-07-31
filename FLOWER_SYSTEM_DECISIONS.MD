# Persistent Flower System Owner Decisions

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
