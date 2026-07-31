# UltimaCraft Persistent Flower System — Milestones 0 and 1 Discovery Report

This report is discovery-only. It records the repository state, confirmed implementation behavior, recommended integration boundaries, and the owner decisions required before Milestone 2. No flower implementation, registration, test, model, texture, recipe, loot-table, or placeholder asset was created.

## 1. Repository and branch baseline

### Confirmed facts

- Repository root: `C:/projects/britannia/mod/Britannia_Mod`
- Owner-authorized branch: `Farming` (capital `F`). The attachment originally required lowercase `farming`; the owner's later direct instruction explicitly overrides that requirement.
- HEAD before discovery: `41fb51a568fb393e27a276d12cbcbfd26c8730d0` (`41fb51a added documentation`)
- Upstream state: `Farming...origin/Farming [ahead 1]`
- Working tree before discovery: untracked `.claude/`; no tracked changes.
- `.claude/settings.local.json` was preserved. It is a local command-permission history file, not project architecture guidance.
- Git emitted `unable to access 'C:\Users\dusti/.config/git/ignore': Permission denied`. This warning did not prevent repository inspection or validation.
- Required opening commands were run:

```text
git rev-parse --show-toplevel
git branch --show-current
git status --short --branch
git log -5 --oneline
```

- No branch switch, creation, reset, merge, rebase, cleanup, commit, or push was performed.

## 2. Guidance documents reviewed

### Confirmed facts

The following files were read completely before architecture recommendations were finalized:

- `UltimaCraft_Persistent_Flower_System_Design.docx`
- `UltimaCraft_Flower_System_Codex_Milestone_Playbook.md`
- `README.md`
- The attached owner assignment

Repository-wide searches found no `AGENTS.md`, `CLAUDE.md`, or `CONTRIBUTING.md`. `.agents/` is empty.

The DOCX was extracted structurally without modifying it: all 224 paragraphs and 43 tables were read. A visual render was attempted outside tracked project files with the bundled document renderer, but LibreOffice/`soffice` is not installed (`FileNotFoundError`, Windows error 2). The document's text and tables were therefore fully reviewed, but its original page layout could not be visually verified. That limitation does not affect the code-integration requirements extracted from it.

`README.md` supplies one developer command:

```text
./gradlew runClient -Pdev --no-configuration-cache
```

### Authority applied

1. Direct owner instructions, including the `Farming` capitalization override
2. `UltimaCraft_Persistent_Flower_System_Design.docx`
3. `UltimaCraft_Flower_System_Codex_Milestone_Playbook.md`
4. Confirmed repository behavior
5. Recommendations in this report

## 3. Dependency and build environment

### Confirmed facts

| Item | Confirmed value and source |
|---|---|
| Minecraft | `1.21.1`, `gradle.properties` |
| NeoForge | `21.1.72`, `gradle.properties` |
| Java | Toolchain 21, `build.gradle`; validation JVM was Microsoft Java `21.0.8` |
| Gradle | Wrapper `8.9`, `gradle/wrapper/gradle-wrapper.properties` |
| NeoGradle | Userdev plugin `7.0.165`, `build.gradle` |
| GeckoLib | `4.6.6`, common and NeoForge artifacts in `build.gradle`/`gradle.properties` |
| Mixin | Processor `0.8.5`, `build.gradle` |
| MixinExtras | `0.3.5`, `build.gradle` |
| NanoHTTPD | `2.2.0`, runtime/JarJar dependency in `build.gradle` |
| Mod ID | `britannia_mod`, `gradle.properties` and `BritanniaMod.MODID` |
| Project version | `0.1.8`, `gradle.properties` |

`build.gradle` applies `java-library`, `maven-publish`, and NeoGradle userdev. Main Java is under `src/main/java`. Main resources combine:

- `src/main/resources`
- `src/generated/resources` when present

There is currently no `src/generated/resources` directory and no `src/test` tree. Repository inventory found 747 main Java files and 5,428 main resource files.

No data-generation provider (`GatherDataEvent` or provider subclasses) was found. The project's farming resources are handwritten JSON and PNG files. NeoGradle exposes `runData`, but that task is not backed by a repository farming datagen pipeline.

Derived Gradle tasks include:

- Compile/build: `compileJava`, `processResources`, `test`, `check`, `build`, `jar`, `jarJar`
- Runtime: `runClient`, `runServer`
- Validation/runtime support: `runGameTestServer`, `testJunit`
- Data: `runData`

The client and server run configurations use the existing run directories; the server adds `--nogui`. `build.gradle` enables game-test namespaces for configured development runs, but the repository contains no GameTest classes.

There is no custom JSON/model/resource-validation task. `processResources` is the least-invasive resource pipeline check; an actual client resource load remains necessary in a later asset milestone.

## 4. Baseline validation and pre-existing failures

### Commands and results

1. `.\gradlew.bat --version` — passed after dependency/network access was allowed.
2. `.\gradlew.bat tasks --all --no-configuration-cache` — passed and supplied the task inventory above.
3. `.\gradlew.bat compileJava processResources test --no-configuration-cache` — passed with exit code 0.

The first sandboxed wrapper discovery attempt could not download the wrapper because socket access was denied (`java.net.SocketException: Permission denied: getsockopt`). That was environmental, not a repository failure; the approved retry succeeded.

Two deliberately short controller invocations of the baseline command ended at the command timeout boundary. The same command was then allowed to finish and passed in 240.9 seconds. Compiled classes and processed resources were present under ignored `build/` outputs. There are no test sources, so `test` passing does not represent flower or farming behavioral coverage.

### Remaining unverified

- Client-side model and texture reload
- Dedicated-server startup and classloading safety
- GameTest behavior
- Multiplayer synchronization
- Upgrade of a saved world containing future flower data

Those are later-milestone validations, not baseline discovery checks.

## 5. Farming-system component map

### Confirmed facts

| Concern | Current authoritative component |
|---|---|
| Soil block and interactions | `src/main/java/com/seggellion/britannia_mod/block/FarmingBlock.java`, `FarmingBlock.useItemOn`, `useWithoutItem`, `randomTick` |
| Soil/crop persistence | `src/main/java/com/seggellion/britannia_mod/block/entity/FarmingBlockEntity.java` |
| Natural dirt conversion | `src/main/java/com/seggellion/britannia_mod/item/FertilizedDirtItem.java`, `FertilizedDirtItem.useOn` |
| Community preparation | `src/main/java/com/seggellion/britannia_mod/item/FarmingHoeItem.java`; `CommunityFarmBlock` and `CommunityFarmBlockEntity` |
| Crop definitions and seed lookup | `src/main/java/com/seggellion/britannia_mod/farming/CropDefinition.java`; `CropRegistry.java` |
| Climate/region | `FarmingClimate.java`; `FarmingClimateResolver.java`; Rails `RegionCache`/`RegionData` |
| Environment eligibility | `FarmingBlockEntity.createGrowthContext`; `CropEnvironmentRules.java`; `TallCropSupport.java` |
| Growth and quality math | `CropQualityCalculator.java`; `CropGrowthContext.java` |
| Harvesting | `FarmingBlock.tryHarvestCrop`, `calculateHarvestYield`, `regrowAfterHarvest` |
| Farming skill | `src/main/java/com/seggellion/britannia_mod/skills/FarmingSkill.java`; `SkillManager.java` |
| Tool policies | `CropHarvestTool`; `CropRegistry` helpers; `data/britannia_mod/tags/items/grain_harvest_blades.json`; `root_crop_shovels.json` |
| Crop visuals | `FarmingBlockEntityRenderer.java`; `CropVisualModels.java`; `ClientModSetup.registerAdditionalModels` and `registerRenderers` |
| Registration | `BritanniaMod.java`; `BlockRegistry.java`; `BlockEntityRegistry.java`; `ItemRegistry.java`; `CreativeTabRegistry.java` |
| Debugging | `src/main/java/com/seggellion/britannia_mod/commands/FarmingDebugCommand.java` |

Specialized farming abstractions include `TrellisBlock`, `TallCropSupport`, `FruitTreeRegistry`, `FruitTreeDefinition`, and fruit-tree root block entities such as `OrangeTreeRootBlockEntity`. They demonstrate specialized support and multi-block growth but are not suitable base classes for a persistent, single-block flower.

## 6. Existing soil-preparation and planting sequence

### Confirmed facts

There are two preparation paths:

1. Natural soil:
   - `FertilizedDirtItem.useOn` converts vanilla `Blocks.DIRT` directly to `BlockRegistry.FARMING_BLOCK`.
   - The resulting farming soil starts at hydration 1.
   - The item is consumed and Farming skill is awarded on the server.
   - A vanilla hoe is not the conversion mechanism.

2. Community soil:
   - `FarmingHoeItem.useOn` only accepts `CommunityFarmBlock`.
   - It creates `CommunityHoedFarmBlock`, damages the hoe by 1, and starts a prepared-soil expiry.
   - `CommunityFarmBlockEntity.PREPARED_EXPIRY_TICKS` is 3,600 ticks (three minutes).
   - Fertilized dirt subsequently creates a `FarmingBlock` and starts a community seed window.
   - `FarmingBlockEntity` uses a 1,200-tick community seed window. If no crop is planted, `FarmingBlock.randomTick` reclaims the plot.

The design's name `britannia_hoe` does not exist as a registry ID. The repository item is `britannia_mod:farming_hoe`, registered as `ItemRegistry.FARMING_HOE` and implemented by `FarmingHoeItem`.

`FarmingBlock` blockstate properties are:

- `HYDRATION`: integer 0–5
- `FERTILIZER`: integer 0–2
- `HAS_SEEDS`: boolean

`FarmingBlockEntity` stores:

- Hydration
- Four normalized soil values: nitrogen, phosphorus, potassium, and organic matter
- Planted crop ID and stored seed variety
- Growth progress, 0-based growth stage, tick progress, mature/blocked flags
- Perennial root-established time
- Community-plot state and seed deadline
- Growth-data version

`FarmingBlock.useItemOn` currently resolves interactions in this order:

1. Trellis placement/delegation
2. Care items (`applyCareItem`)
3. Grape seeds
4. Ordinary crop seeds via `CropRegistry.bySeed`
5. Mature crop harvesting
6. Superclass behavior

Ordinary `tryPlantSeed` validates the current block/entity, resolves the seed to a `CropDefinition`, checks support and occupancy, mutates only on the server, calls `FarmingBlockEntity.plant`, sets `HAS_SEEDS`, consumes one seed, plays a sound, and awards Farming skill.

`FarmingEventHandler` separately intercepts nutrient right-clicks. It calls `FarmingBlockEntity.addNutrients`, consumes server-side, cancels the event, and can bypass the richer `FarmingBlock.applyCareItem` fertilizer path. That is a pre-existing ownership/duplication risk; the flower system should not reproduce it.

### Recommendation

Flower planting should be recognized in `FarmingBlock.useItemOn` after care items and before grape/ordinary crop lookup. A repository-consistent `FlowerPlantingService.tryPlant` should own validation and the atomic conversion.

The service must:

1. Recognize a registered flower seed without consuming it.
2. Validate that the FarmingBlock has no planted crop and that all placement/community/protection rules pass.
3. Snapshot hydration, all four nutrients, community/origin data, and any other farming state **before** block replacement.
4. On the server only, select one color from the species palette.
5. Compute planting origin and protection from the planting player.
6. Replace the FarmingBlock with the generic FlowerBlock.
7. Initialize its FlowerBlockEntity at stage 1 with the snapshot, species, selected color, origin, and protection.
8. Consume the seed only after initialization succeeds.
9. Return a handled interaction result to both sides and synchronize from the server.

Snapshotting before `Level.setBlock` is essential: `FarmingBlock.onRemove` clears planted/stored crop data when the block type changes and the old block entity is removed.

Any invalid flower-seed use must return without block mutation, color selection, item consumption, sound, durability cost, or skill award.

## 7. Existing growth-evaluation sequence

### Confirmed facts

`FarmingBlock.randomTick` is the entry point:

1. Reclaim an expired unplanted community plot.
2. Synchronize blockstate hydration from the block entity.
3. On a 10% random-tick chance, decrease hydration by one.
4. While exposed to rain, raise hydration to at least two.
5. If hydration is positive, perform legacy grape migration and call `FarmingBlockEntity.tickGrowth`.

`FarmingBlockEntity.tickGrowth` is server-only. It:

1. Resolves `plantedCropId` through `CropRegistry`.
2. Repairs mature state when appropriate.
3. Builds a `CropGrowthContext` using nutrients, hydration, climate, altitude, special environment, support, and skill.
4. Calls `CropQualityCalculator.calculateGrowthMultiplier`.
5. Pauses and sets `growthBlocked` when the multiplier is not positive.
6. Adds `multiplier / baseGrowthTicks` to progress.
7. Maps progress to a 0-based stage and mature flag.
8. Synchronizes the block entity after changes.

`FarmingClimateResolver.resolve` first uses Rails region data, then biome temperature/downfall/tags, proximity to water, darkness, and depth. Its available enum is:

`TEMPERATE`, `ICE`, `FIRE`, `WETLAND`, `TROPICAL`, `ARID`, `MAGICAL`, `UNDERGROUND`

The design's meadow, foothill, woodland, and similar flower profiles are conceptual and do not currently exist as repository region tags.

`CropQualityCalculator` is authoritative for:

- Nutrient fit across the four soil values
- Hydration fit
- Climate and altitude eligibility
- Support/environment gates
- Growth multiplier
- Harvest quality

Growth ticks currently pass no player, so the growth context's Farming level is zero during passive growth. Player Farming skill participates when harvest quality is calculated.

### Reuse classification

**Directly reusable**

- `FarmingClimate` and `FarmingClimateResolver`
- `CropEnvironmentRules` where a flower profile explicitly adopts an existing rule
- `FarmingSkill.SKILL_ID` and `SkillManager.getSkill`
- Existing hydration scale (0–5), soil normalization (0.0–1.0), and server random-tick entry pattern
- Existing farming sound, synchronization, logging, and item-quality conventions where applicable

**Requires a small adapter or extension**

- `CropQualityCalculator`: its formulas should stay authoritative, but signatures are coupled to `CropDefinition`/`CropGrowthContext`. Introduce a shared growth-profile interface or an adapter implemented by `FlowerDefinition`; do not copy the formulas.
- `FarmingBlockEntity`: its soil fields are private and its block-entity type is tied to FarmingBlock. Add an explicit immutable soil snapshot/copy API or extract a shared soil value object.
- Care interactions: `FarmingBlock.applyCareItem` is block-specific. Extract/delegate watering and nutrient operations to a small shared soil-care service.
- Visual model selection: reuse the renderer registration/cached standalone-model pattern through a flower-specific resolver.

**Incompatible and requires an owner decision**

- Conceptual flower region profiles versus the current coarse `FarmingClimate` enum
- The design's named administrator permission path versus the repository's operator-level convention
- The required skinning knife, which is not registered or tagged

**Must not be duplicated**

- Nutrient/hydration fit formulas
- Climate and altitude resolution
- Farming-skill storage and lookup
- Server-authoritative growth timing
- Quality calculation
- Permission conventions once the owner chooses the source

## 8. Existing harvest and reset sequence

### Confirmed facts

`FarmingBlock.tryHarvestCrop` validates maturity, support, required tool, and tall-crop state. On the server it:

- Calculates yield and `CropGrowthContext`
- Calculates quality with `CropQualityCalculator`
- Writes quality via item `CustomData`
- Adds region provenance
- Drops produce directly with `Block.popResource`
- Applies seed-return chance
- Damages the required tool by 1 for scissors, grain blade, or root shovel
- Awards Farming skill
- Plays a sound and logs the result

Annual crops clear the crop and either leave an empty FarmingBlock or return a community plot. Perennials call `FarmingBlockEntity.regrowAfterHarvest` and remain planted, but reset to each crop's configured post-harvest age—commonly 4 and banana 5—not to stage 1.

`useWithoutItem` delegates to harvest. `playerWillDestroy` clears crop state; `playerDestroy` restores community plots. `onRemove` clears crop/stored-seed state when the block type changes. FarmingBlock itself has no special explosion, piston, or fluid protection.

### Recommendation

The flower system should reuse the harvest-quality/yield service only after the owner defines flower drops. It must not reuse `regrowAfterHarvest` directly because the flower contract always resets the same planting to **stage 1** while retaining species, color, protection, origin, soil, and timing identity.

A single `FlowerBlockEntity.resetToStageOne(ResetReason)` should be the authoritative mutation for approved harvest, sword cutback, and other persistent regrowth. It should never call color selection.

## 9. Existing persistence and synchronization sequence

### Confirmed facts

`FarmingBlockEntity` uses standard block-entity NBT:

- `saveAdditional`
- `loadAdditional`
- `getUpdateTag`
- `getUpdatePacket`
- `setChangedAndSync`, which sends a block update with flag 3

There is no farming codec, attachment, or data-component persistence for in-world crop identity. Item-specific values such as grape variety and harvest quality use `DataComponents.CUSTOM_DATA`, but that is not the in-world farming save mechanism.

The farming block entity stores a growth data version (`GROWTH_AGE_DATA_VERSION = 3`) and migrates older stage layouts. It also remaps known crop IDs:

- `sweet_potato` → `yam`
- `mandrake_root` → `mandrake`
- `strawberries` → `strawberry`
- `blueberries` → `blueberry`

An unknown crop ID remains stored and blocks growth rather than being randomly replaced.

### Recommendation

`FlowerBlockEntity` should use the same NBT/update-tag/update-packet convention. Suggested saved fields:

- `DataVersion` (start at 1)
- Namespaced `SpeciesId`
- Compact 24-bit `ColorTint` RGB integer selected from a registered palette definition; this remains the saved authority
- `GrowthStage` (1–7), `GrowthProgress`, growth timer/blocked state
- Hydration and four soil values, using the existing scales
- `PlantingOrigin` (`PLAYER`, `ADMIN`, and any explicitly approved system/world origin)
- `Protected`
- Planter UUID when planted by a player, plus stable planting-region provenance and one authoritative persisted quality score

The block entity should be the single authority for growth stage, matching existing crop rendering and avoiding blockstate/BE divergence. FlowerBlock may retain soil-facing `HYDRATION`/`FERTILIZER` blockstate properties for visual/interaction compatibility.

Load rules from the design should be explicit:

- Missing origin/protection from legacy flower data → `PLAYER` and unprotected
- Unknown species → safe missing-flower behavior and a bounded warning; do not substitute a random species
- Unknown color → deterministic default visual fallback only; do not rewrite saved color identity
- Out-of-range stage → clamp to the absolute species/system maximum and log
- Never call palette selection from a constructor, `loadAdditional`, update-tag handling, chunk load, client connection, or resource reload

Color selection occurs only in the successful server planting transaction. Clients receive the stored identity through the normal update tag/packet.

## 10. Crop, seed, item, block, and block-entity registration conventions

### Confirmed facts

- `BritanniaMod` attaches central deferred registers to the mod event bus.
- `BlockRegistry` uses `DeferredRegister.Blocks`/`DeferredHolder`; FarmingBlock is a custom registered block.
- `BlockEntityRegistry` registers explicit block-entity types and their valid blocks; `FARMING_BLOCK_BE` is the farming precedent.
- `ItemRegistry` is a large central item registry. Its crop-seed helper creates `CropSeedItem` instances tied to crop IDs.
- `CropRegistry` is a hard-coded Java bootstrap with a `BY_ID` map and linear seed lookup. Crop species are not data-driven JSON.
- `CropDefinition` is an immutable Java record containing growth, soil, climate, altitude, yield, lifecycle, support, and tool data.
- Crop stage counts are definition-driven and currently represented internally as 0-based ages.
- Annual/perennial behavior is expressed in crop definitions; trellis, tall, grape, and fruit-tree paths add specialized code.
- Item/tool tags include both `data/britannia_mod/tags/items/...` and legacy/parallel `tags/item/...` copies. Relevant files are:
  - `data/britannia_mod/tags/items/grain_harvest_blades.json`
  - `data/britannia_mod/tags/items/root_crop_shovels.json`
- Language is handwritten in `assets/britannia_mod/lang/en_us.json`.
- Creative-tab content is explicitly added in `CreativeTabRegistry`, including farming seed/produce collections.
- Crop harvest loot is generated directly in Java rather than crop loot tables.
- Recipes, models, blockstates, tags, and language are handwritten. No farming datagen provider exists.

Repository-wide exact-ID searches found no existing registrations for:

`poppy`, `snowdrop`, `lily`, `foxglove`, `campion`, `hyacinth`, `orfluer`, or any corresponding `_seeds` ID in the `britannia_mod` namespace.

`grave_flowers` and related decorative blocks exist, but they do not collide with the proposed IDs.

### Recommendation

Use the design IDs unchanged:

- `britannia_mod:poppy`
- `britannia_mod:snowdrop`
- `britannia_mod:lily`
- `britannia_mod:foxglove`
- `britannia_mod:campion`
- `britannia_mod:hyacinth`
- `britannia_mod:orfluer`

Use matching `<species>_seeds` IDs. Keep one generic in-world `flower_block` plus a flower block entity; species/color/stage combinations must not create separate registered blocks.

Use American `Color` in Java symbol names (`FlowerColor`, `colorId`) because the repository already uses that convention (`GrapeColor`), while retaining player-facing “colour” wording where the design requires it.

## 11. Tool, skill, game-mode, and permission integration points

### Confirmed facts

- Farming skill ID: `FarmingSkill.SKILL_ID = "farming"`.
- Server lookup: `SkillManager.getSkill(player, "farming")`, represented as a float. The Poppy gate can safely use `>= 100.0F`.
- Skill persistence is mediated by `SkillManager` and the Rails HTTP integration; flower code must not add a separate player capability.
- No registered skinning-knife item or skinning-knife tag exists.
- A `rune_carving_knife` string appears in crafting data, but no suitable registered item was found for the flower requirement.
- Vanilla swords derive from `SwordItem`; custom swords use `QualitySwordItem`. `WeaponRegistry` includes `viking_sword` and `dagger`.
- `grain_harvest_blades` currently includes vanilla swords plus `viking_sword` and `dagger`.
- The actual hoe is `britannia_mod:farming_hoe` / `ItemRegistry.FARMING_HOE`.
- The Britannia shovel is `britannia_mod:britannia_shovel` / `ToolRegistry.SHOVEL`, implemented by `QualityShovelItem`. `root_crop_shovels` contains that tool.
- Tool durability uses `hurtAndBreak(1, player, Player.getSlotForHand(hand))` on successful harvest. Denied interactions do not damage tools.
- `PlayerEventHandler.onLeftClickBlock` is the repository precedent for an Adventure-only attack override. It checks `GameType.ADVENTURE`, recognizes the held tool, performs a server mutation, and cancels the event.
- Creative checks use `player.isCreative()` or `abilities.instabuild`.
- Administrative commands and protected spawn/config interactions consistently use `ServerPlayer.hasPermissions(2)`.
- No general named-permission service matching the design pseudocode was found.

### Recommendation

- Reuse the existing authoritative `britannia_mod:grain_harvest_blades` tag, including the dagger, for later flower cutback.
- Add a dedicated `britannia_mod:skinning_knives` semantic tag and later register `britannia_mod:skinning_knife`.
- Handle Adventure sword cutback in a server-side left-click block event patterned after `PlayerEventHandler.onLeftClickBlock`. Recognized flower attacks should be canceled on both logical sides to prevent normal destruction/client prediction; only the server calls `resetToStageOne`.
- Charge durability only after a successful authorized mutation.
- Centralize Creative/admin decisions in `FlowerProtectionService`; do not scatter `isCreative`/permission checks through block, item, and event code.

## 12. Existing model and rendering pipeline

### Confirmed facts

Farming crop visuals do **not** use a blockstate stage-property model. The soil blockstate model represents the farming soil, while crop visuals are rendered above it by:

- `src/main/java/com/seggellion/britannia_mod/client/renderer/FarmingBlockEntityRenderer.java`
- `src/main/java/com/seggellion/britannia_mod/farming/CropVisualModels.java`
- `ClientModSetup.registerAdditionalModels`
- `ClientModSetup.registerRenderers`

The block-entity renderer:

1. Reads crop ID, growth stage, and variant from `FarmingBlockEntity`.
2. Resolves a standalone baked model through `CropVisualModels.modelLocation`.
3. Uses a deterministic per-position rotation.
4. Renders with `RenderType.cutout`.

Crop model resources are under `assets/britannia_mod/models/block/crops/<crop>/...`. Naming is historically mixed (`<crop>_age_<n>`, `om_<crop>_<n>`, and crop-specific aliases). Models include crossed planes, multiple explicit planes, and some fuller geometry. They are not uniformly vanilla crossed-plant parents.

There is no block `BlockColor` registration. Item color handlers exist in `ClientModSetup`. Some JSON contains `tintindex`, but current FarmingBlockEntityRenderer renders baked crop models with a global white color and does not supply per-plant block tint.

GeckoLib is used elsewhere, but not in the farming crop pipeline. Custom baked-model/geometry infrastructure exists elsewhere in the mod, but the current crop pipeline does not require it.

Texture resolution is inconsistent rather than a stable convention. Under `textures/block/crops`, inspected dimensions include 16×16, 32×32, 64×64, 128×128, 256×256, and tall sprite sheets. The most common observed square size is 128×128, but many crop assets use smaller pixel-art resolutions.

Model `particle` textures are inconsistently declared. Flower models should explicitly use the untinted base texture as `particle` so break particles are stable and do not require per-particle block-entity color.

Item models are handwritten JSON. Resource reload rebuilds baked models; stored species/color must stay in the server-synchronized block entity and must not be recomputed during reload.

## 13. Two viable two-texture, multi-plane rendering approaches

Both options preserve exactly two PNG files for every unique in-world stage model:

```text
<model_name>_base_texture.png
<model_name>_dye_mask.png
```

The mask is grayscale with alpha only where tint is applied. Both files have identical dimensions, UV positions, transparent padding, and pixel alignment.

### Option A — two cached baked-model passes in a flower block-entity renderer (recommended)

Create two standalone JSON model resources per species/stage: one referencing the base texture and one referencing the dye-mask texture. Register both through `ClientModSetup.registerAdditionalModels`. `FlowerBlockEntityRenderer` renders the base model in white, then renders the mask model with the stored RGB color, both with the same transform and `RenderType.cutout`.

| Assessment | Result |
|---|---|
| Current architecture | Strong match to `FarmingBlockEntityRenderer` and standalone baked-model registration |
| Z-fighting | Low if base and mask alpha are disjoint: every mask-visible pixel must be transparent in base. Any deliberately overlapping opaque pixels would be unsafe |
| Depth/render order | Deterministic base-then-mask pass; same cutout layer and transform |
| Alpha | Straightforward cutout alpha; mask alpha selects tint regions and grayscale preserves shading |
| Mip bleeding | Controlled by identical padding, transparent gutters, UV inset, and avoiding stray RGB in transparent pixels |
| Performance | Two cached model submissions per flower; acceptable for the required two-layer visual, but must be profiled in dense fields |
| Resource packs | Standard JSON/PNG overrides; easiest option for pack authors |
| Stage complexity | 49 logical species/stage entries and 98 pass JSONs unless shared parents reduce JSON duplication; exactly 98 in-world PNGs |
| Client/server separation | Renderer/model resolution stays client-only; server syncs IDs/stage/RGB only |
| Maintainability | Repository-consistent and debuggable with normal model tooling |
| Placeholders | Two aligned images and matching multi-plane pass JSON per stage |

The two pass models may inherit a shared geometry parent so plane coordinates/UVs cannot drift. That does not add a third texture and does not violate the two-texture contract.

### Option B — custom composite baked model/geometry loader

Create one stage JSON that names both textures and invokes a custom geometry loader. At bake time, the loader creates/caches base quads and tintable mask quads. A flower renderer or model-data path supplies the per-plant color.

| Assessment | Result |
|---|---|
| Current architecture | Technically supported by custom model infrastructure elsewhere, but not the crop precedent |
| Z-fighting | Can deliberately offset mask geometry by a tiny, deterministic normal/scale amount; more control, more responsibility |
| Depth/render order | Custom quad ordering or explicit two-pass rendering can guarantee order |
| Alpha | Fully controllable, but loader/render code must correctly preserve cutout semantics |
| Mip bleeding | Same asset constraints as Option A; custom code cannot repair bad padding |
| Performance | Can cache a composite and reduce lookups, but the tinted layer still requires a second material/pass or equivalent quad work |
| Resource packs | Requires pack authors to understand the custom loader schema |
| Stage complexity | One stage JSON per logical model, but more Java bake/reload complexity |
| Client/server separation | Safe only if all loader/model classes remain client-only |
| Maintainability | Higher reload, version-upgrade, and model-debugging cost |
| Placeholders | Two aligned images plus one custom-loader JSON per stage |

### Recommendation

Approve Option A. It follows the current farming renderer, minimizes new rendering infrastructure, and gives resource packs standard JSON/PNG override points. Use shared geometry parents, alpha-disjoint layers, `RenderType.cutout`, the base as particle texture, and an explicit dense-field performance test.

Pure blockstate variant expansion is not recommended: species, stage, and persistent color are block-entity data, and enumerating all combinations would be unmanageable. A plain `BlockColor` alone cannot select a per-block-entity species/stage model in the existing pipeline.

## 14. Recommended flower architecture using repository-consistent names

### Recommended symbols

- `farming/FlowerDefinition.java` — immutable species profile, analogous to `CropDefinition`
- `farming/FlowerRegistry.java` — hard-coded initial definitions and seed lookup, analogous to `CropRegistry`
- `farming/FlowerColor.java`, color definitions, and palette entries — compact 24-bit RGB value plus stable configured IDs/weights
- `farming/FlowerPlantingOrigin.java` — persisted origin enum
- `farming/FlowerSoilSnapshot.java` — immutable transfer object using existing farming scales
- `farming/FlowerGrowthAdapter.java` — adapts `FlowerDefinition` to the shared farming evaluator without copying formulas
- `farming/FlowerPlantingService.java` — validated, server-authoritative, atomic FarmingBlock conversion
- `farming/FlowerProtectionService.java` — one authorization policy used by all direct and indirect mutations
- `block/FlowerBlock.java` — one generic block, random ticks/care/delegated interactions
- `block/entity/FlowerBlockEntity.java` — species, stable color, stage/progress, origin/protection, soil, version, packets
- `event/FlowerInteractionHandler.java` — left-click cutback, break-event enforcement, and indirect event hooks
- `client/renderer/FlowerBlockEntityRenderer.java`
- `client/renderer/FlowerVisualModels.java`

### Recommended boundaries

- Do not subclass `FarmingBlockEntity`: its constructor/type and private storage are coupled to FarmingBlock. Use a snapshot/shared value type and shared evaluator.
- Do not create one block per species, stage, or color.
- Do not add a flower-only climate, nutrient, hydration, quality, or skill engine.
- Keep growth stage 1-based in the flower domain because the design is explicitly stages 1–7. Convert only at the adapter boundary where an existing 0-based API requires it.
- Natural maxima belong in `FlowerDefinition`; Poppy's natural maximum is 6 and absolute maximum is 7.
- All reset paths call one block-entity method and retain identity.
- All permanent/temporary mutations call `FlowerProtectionService` before touching state or durability.

## 15. Exact proposed FarmingBlock-to-flower seed integration point

### Recommendation

Add flower-seed recognition inside `FarmingBlock.useItemOn`, immediately after `applyCareItem` and before the grape/ordinary `CropRegistry.bySeed` branches.

The call should be conceptually:

```text
FlowerPlantingService.tryPlant(
    level,
    pos,
    player,
    hand,
    heldStack,
    currentFarmingBlockState,
    currentFarmingBlockEntity
)
```

The service returns one of three explicit outcomes:

- `NOT_A_FLOWER_SEED`: continue existing grape/crop/harvest logic
- `REJECTED`: leave block/entity/item unchanged and return the appropriate non-consuming interaction result
- `PLANTED`: stop existing logic; server has replaced and initialized the block, consumed one seed, synchronized, played feedback, and awarded any approved skill

The old FarmingBlockEntity must be snapshotted before replacement because `FarmingBlock.onRemove` clears it. Server color selection must occur after all validation but before new block-entity initialization. If replacement or initialization cannot complete, the original farming state must be restored and the seed must not be consumed.

Hoe/preparation logic is deliberately untouched: using `britannia_mod:farming_hoe` alone still produces only the existing community-preparation state, and fertilized dirt still produces only FarmingBlock.

## 16. Existing farming components that should remain authoritative

### Confirmed/recommended authority

| Component | Flower use |
|---|---|
| `FarmingClimateResolver` | Sole region/climate resolver |
| `FarmingClimate` | Initial climate vocabulary unless owner approves extension |
| `CropQualityCalculator` formulas | Sole fit/growth/quality math after interface extraction or adapter |
| FarmingBlockEntity soil scale | Sole hydration/nutrient value scale |
| FarmingBlock random-tick cadence | Pattern for passive growth and weather hydration |
| `SkillManager` / `FarmingSkill` | Sole Farming level source |
| Existing tool tags | Reuse root-shovel semantics where approved; create narrowly scoped flower tags rather than hard-coded item lists |
| Block-entity NBT/update packets | Persistence and network convention |
| `ClientModSetup` model/renderer events | Client registration entry points |
| Existing DeferredRegister classes | All blocks, items, block entities, and creative-tab entries |
| Existing logging/debug command style | Later diagnostics |

The implementation may extract small shared interfaces/services, but it should leave these behaviors authoritative rather than copying them into flower packages.

## 17. Likely files to add or modify, grouped by later milestone

This is a forecast, not authorization to create these files.

### Milestone 2 — Architecture Foundation and Data Contracts

Likely additions:

- `src/main/java/com/seggellion/britannia_mod/farming/FlowerDefinition.java`
- `FlowerRegistry.java`
- `FlowerColor.java`
- `FlowerPlantingOrigin.java`
- `FlowerSoilSnapshot.java`
- `FlowerGrowthAdapter.java`
- `FlowerProtectionService.java`

Likely small modifications:

- `CropQualityCalculator.java` and/or `CropGrowthContext.java` to expose a shared profile contract
- `FarmingBlockEntity.java` for an explicit soil snapshot/export API

### Milestone 3 — Placeholder Items, Seeds, Models, and Textures

Likely modifications:

- `ItemRegistry.java`
- `CreativeTabRegistry.java`
- `assets/britannia_mod/lang/en_us.json`

Likely additions:

- Flower and seed item classes only if generic existing item classes are insufficient
- Handwritten item models/textures
- The stage model/texture tree in section 18

### Milestone 4 — FlowerBlock Lifecycle, Planting, Persistence, and Synchronization

Likely additions:

- `block/FlowerBlock.java`
- `block/entity/FlowerBlockEntity.java`
- `farming/FlowerPlantingService.java`

Likely modifications:

- `FarmingBlock.java`
- `BlockRegistry.java`
- `BlockEntityRegistry.java`
- `BritanniaMod.java` only if a new register/event hook is required

### Milestone 5 — Shared Growth Evaluation and Perennial Regrowth

Likely modifications:

- `FlowerBlock.java`
- `FlowerBlockEntity.java`
- `FlowerGrowthAdapter.java`
- Shared evaluator/care services introduced in Milestone 2

### Milestone 6 — Interactions, Protection, Uprooting, and Poppy Stage 7

Likely additions:

- `event/FlowerInteractionHandler.java`
- `data/britannia_mod/tags/items/flower_cutting_swords.json`
- `data/britannia_mod/tags/items/skinning_knives.json`

Likely modifications:

- Tool/item registration if a skinning knife must be added
- `FlowerProtectionService`, `FlowerBlock`, and flower block entity

### Milestone 7 — Multi-Plane Rendering and Dye-Mask Tinting

Likely additions:

- `client/renderer/FlowerBlockEntityRenderer.java`
- `client/renderer/FlowerVisualModels.java`
- Recommended base/mask JSON models

Likely modification:

- `ClientModSetup.java`

### Milestone 8 — Species Content, Balance, and Player-Facing Data

Likely modifications/additions:

- `FlowerRegistry.java` definitions for all seven species
- `en_us.json`
- Approved recipes, tags, and loot/seed sources

### Milestone 9 — Regression, Save Safety, and Multiplayer QA

Likely additions:

- Pure Java tests under `src/test/java` if the build's JUnit configuration is confirmed/added
- NeoForge GameTests if the owner approves establishing the repository's first fixtures
- Debug command extensions and documented manual test matrix

### Milestone 10 — Handoff and closeout

Likely documentation/status files required by the playbook, only when that gate is reached.

## 18. Placeholder asset structure proposed for the seven species

### Recommendation for rendering Option A

For every species and stage 1–7:

```text
assets/britannia_mod/models/block/flowers/<species>/stage_<n>_base.json
assets/britannia_mod/models/block/flowers/<species>/stage_<n>_dye_mask.json
assets/britannia_mod/textures/block/flowers/<species>/stage_<n>_base_texture.png
assets/britannia_mod/textures/block/flowers/<species>/stage_<n>_dye_mask.png
```

Species:

```text
poppy
snowdrop
lily
foxglove
campion
hyacinth
orfluer
```

This is 49 logical in-world stage models, 98 render-pass JSONs, and exactly 98 in-world PNGs. Shared JSON geometry parents may reduce duplicated coordinates but do not add textures.

Each base/mask pair must have:

- Identical canvas dimensions
- Identical plane geometry and UV rectangles
- Identical transparent gutters
- Base alpha cleared anywhere the mask is visible
- Mask alpha cleared everywhere that should remain fixed-color
- Grayscale mask values preserving highlight/midtone/shadow
- Base texture as the model particle texture

Item placeholders should follow existing handwritten conventions:

```text
assets/britannia_mod/models/item/<species>.json
assets/britannia_mod/models/item/<species>_seeds.json
assets/britannia_mod/textures/item/flowers/<species>.png
assets/britannia_mod/textures/item/flowers/<species>_seeds.png
```

The two-texture invariant applies to each unique in-world flower stage model; ordinary inventory icons remain standard item textures.

No placeholder assets were created during discovery.

## 19. Save-compatibility and migration assessment

### Confirmed facts

There is no pre-existing flower save data and no proposed species/seed ID collides with a current Britannia registration. Initial registration is therefore a new-data migration, not a remap of existing blocks.

The current farming block entity demonstrates versioned NBT and ID aliases. Generic FlowerBlock plus a stable namespaced species ID avoids a block-registry migration for every species.

### Recommendation

- Start flower `DataVersion` at 1.
- Treat saved species and color IDs as immutable identity.
- Add aliases only for deliberate future renames; do not fuzzy-match.
- Missing legacy origin/protection defaults to player/unprotected, as required by the design.
- Unknown species remains visibly recoverable through a safe missing model/state and warning.
- Unknown color uses a deterministic visual fallback without overwriting NBT.
- Clamp corrupt stages, but never use corruption recovery as a reason to reroll color.
- Test NBT round trips, chunk unload/reload, server restart, reconnect, and resource reload.
- Do not serialize client-only model locations.

Save compatibility does not currently require an owner decision because the design already supplies the fallback policy and no ID conflict was found.

## 20. Test strategy

### Confirmed facts

- No unit tests, GameTests, or farming integration fixtures exist.
- NeoGradle exposes `test`, `testJunit`, and `runGameTestServer`.
- `run/saves/sandbox` exists as a manual development world and was not opened or modified.
- `/farming debug` already reports crop, stage, hydration, nutrients, climate, fit, and model details.
- Farming planting/harvest code uses structured log prefixes such as `[farming planting]` and `[farming harvest]`.

### Recommended later-milestone strategy

1. **Pure deterministic tests**
   - Weighted color selection with an injected seeded RNG
   - Natural/absolute stage bounds for all species
   - Poppy stage-6/7 gate
   - Reset preserving identity
   - Permission matrix
   - Unknown/missing ID and corrupt-stage fallbacks

2. **NBT round-trip tests**
   - Save/load all flower fields
   - Legacy missing origin/protection
   - Unknown species/color behavior
   - No color-selector invocation during load/update-tag handling

3. **GameTests if approved**
   - FarmingBlock + valid seed → initialized FlowerBlock
   - Invalid seed leaves block and stack unchanged
   - Soil snapshot survives conversion
   - Growth pauses/resumes through hydration/climate
   - Harvest and Adventure sword cutback reset to stage 1
   - Poppy never naturally reaches 7
   - Protected mutation paths are denied
   - Explosion/piston/fluid policies

4. **Dedicated-server validation**
   - `compileJava processResources test`
   - `runGameTestServer` when fixtures exist
   - `runServer --nogui` smoke test to catch client-only classloading

5. **Client/multiplayer matrix**
   - Resource load/reload for all 49 model pairs
   - Dense-field render/performance check
   - Two players observing planting, watering, growth, cutback, and protection denial
   - Disconnect/reconnect, chunk reload, and server restart

6. **Diagnostics**
   - Extend `/farming debug` or add a sibling `/flower debug` using the same permission/log style
   - Log planting with species/color/origin/protection and reset reason, but rate-limit unknown-ID warnings

## 21. Risks, conflicts, and unresolved issues

### Confirmed risks/conflicts

1. The design names `britannia_hoe`; the repository's canonical ID is `britannia_mod:farming_hoe`.
2. No skinning knife item/tag exists, so the Poppy stage-7 rule cannot be implemented as written without an owner choice.
3. No named permission service exists; the repository uses operator permission level 2.
4. Design region profiles do not map one-to-one to the current `FarmingClimate` enum.
5. Community farming plots reclaim only while empty; a persistent flower could occupy public soil indefinitely unless a policy is chosen.
6. `FarmingEventHandler` and `FarmingBlock.applyCareItem` duplicate fertilizer ownership.
7. FarmingBlockEntity cannot be safely subclassed for flowers without coupling block-entity registration and private storage.
8. Existing perennial reset ages violate the flower stage-1 reset contract if reused directly.
9. FarmingBlock has no comprehensive explosion/piston/fluid protection precedent.
10. Existing structure/spawn protections are partial and inconsistent; they are not a reusable per-block authorization system.
11. Crop texture resolutions and tag directory forms are inconsistent.
12. A two-pass renderer can Z-fight if base/mask opaque pixels overlap.
13. There is no automated resource-model validation or existing behavioral test suite.
14. The old block entity is destroyed during FarmingBlock replacement, so conversion must snapshot first and consume last.
15. Generic command/world mutation has no player actor at the block callback level; policy must distinguish authorized commands/system changes from gameplay bypasses.

### Risk controls recommended

- Centralize mutation authorization.
- Centralize stage-1 reset.
- Keep server-only color selection.
- Use explicit snapshot/transaction ordering.
- Share farming evaluators rather than copying.
- Use alpha-disjoint two-pass assets and shared geometry parents.
- Establish automated persistence and interaction tests before content balancing.

## 22. Consolidated owner questions — resolved

The owner resolved every question below and explicitly authorized Milestone 2. The discovery evidence and historical option framing are retained; the selected recommendations below and `FLOWER_SYSTEM_DECISIONS.MD` are now authoritative.

### BLOCKING

#### B1. Which registered item satisfies the Poppy skinning-knife gate?

- **Discovered:** No skinning knife item or item tag exists. `rune_carving_knife` appears only as crafting data and is not a confirmed registered tool.
- **Why needed:** Stage-6 Poppy → stage 7 cannot be wired to a nonexistent registry entry.
- **Options:** (A) register a dedicated `britannia_mod:skinning_knife` plus `skinning_knives` tag; (B) designate an existing registered tool such as the dagger; (C) tag several existing knives/tools.
- **Recommendation:** A dedicated registered skinning knife and tag. This gives the requirement a stable semantic ID and lets data packs extend the tag.
- **Consequences:** A adds an item, assets, recipe/balance work; B is quickest but changes an existing tool's meaning; C is flexible but makes the special gate less clear.

#### B2. What is the approved administrator permission source?

- **Discovered:** The repository has no named permission service. Commands and admin content consistently use `ServerPlayer.hasPermissions(2)`; Creative is checked separately.
- **Why needed:** Protection is assigned at planting and every later mutation must use the same authorization rule.
- **Options:** (A) Creative or operator permission level 2; (B) integrate a named external permission node/service; (C) Creative only.
- **Recommendation:** A, wrapped in `FlowerProtectionService`, unless a permission plugin is already planned outside this repository.
- **Consequences:** A is immediately repository-consistent; B adds a dependency/integration and fallback policy; C is simple but does not provide the design's authorized-admin path.

### ARCHITECTURE

#### A1. Approve the rendering implementation.

- **Discovered:** The current farming pipeline uses a block-entity renderer and cached standalone baked JSON models. Both report options meet the exact two-PNG contract.
- **Why needed:** Model/resource layout and client code differ substantially between the options.
- **Options:** (A) two cached baked-model passes in `FlowerBlockEntityRenderer`; (B) a custom composite geometry/baked-model loader.
- **Recommendation:** A, with shared geometry parents and alpha-disjoint base/mask pixels.
- **Consequences:** A creates more small JSON pass files but is standard and pack-friendly; B reduces JSON count but adds custom bake/reload code and raises maintenance/resource-pack complexity.

#### A2. How should conceptual flower regions map to the farming climate engine?

- **Discovered:** Existing authoritative values are `TEMPERATE`, `ICE`, `FIRE`, `WETLAND`, `TROPICAL`, `ARID`, `MAGICAL`, and `UNDERGROUND`; meadow/foothill/woodland profiles do not exist.
- **Why needed:** Species definitions must supply real evaluator inputs without adding a duplicate simulation.
- **Options:** (A) map initial flower profiles onto the existing enum; (B) extend `FarmingClimate`/resolver with new categories; (C) add a second fine-grained region-tag dimension shared by crops and flowers.
- **Recommendation:** A for the initial seven species; record the normalized mapping in `FlowerDefinition`.
- **Consequences:** A is low risk but less expressive; B affects all climate switches and save/config behavior; C is the most expressive and largest farming refactor.

#### A3. May flowers be planted on community farming plots?

- **Discovered:** Empty community plots reclaim after 1,200 ticks, but planted persistent crops do not. A perennial flower could reserve public soil indefinitely.
- **Why needed:** Conversion destroys the current FarmingBlock identity and must decide whether/how the community flag survives.
- **Options:** (A) reject flower seeds on community plots; (B) allow them but require approved uprooting to restore the community block; (C) allow unrestricted persistent occupation.
- **Selected decision:** B. Allow community flowers and persist restoration data for the default unprepared `britannia_mod:community_farm_block` state.
- **Consequences:** A avoids public-plot griefing and simplifies migration; B supports community flowers but adds restoration/abandonment rules; C is simplest technically but creates an ownership/griefing risk.

### GAMEPLAY

#### G1. What is the flower-cutting sword tag scope and durability cost?

- **Discovered:** `grain_harvest_blades` includes all vanilla swords, `viking_sword`, and `dagger`; there is no flower-specific tag. Existing successful tool harvests cost 1 durability.
- **Why needed:** Adventure attack recognition must be explicit and data-driven.
- **Options:** (A) new `flower_cutting_swords` tag with vanilla swords + `viking_sword`; (B) copy all grain blades including dagger; (C) accept every `SwordItem`; durability can be 1 or 0 on successful cutback.
- **Selected decision:** Reuse the authoritative `grain_harvest_blades` policy including the dagger, and charge 1 durability only after a successful authorized reset; no sword-cutback drop.
- **Consequences:** A gives tight pack-configurable scope; B makes dagger valid too; C automatically includes modded swords but is harder to balance. Zero durability removes the existing farming-tool cost precedent.

#### G2. Which tools permanently uproot an unprotected player flower?

- **Discovered:** The design names a hoe and allows configurable shovel behavior. The actual hoe ID is `britannia_mod:farming_hoe`; `root_crop_shovels` currently contains `britannia_mod:britannia_shovel`.
- **Why needed:** Permanent removal must be distinct from persistent harvesting/cutback and must restore an appropriate soil/community state.
- **Options:** (A) farming hoe only; (B) farming hoe plus `root_crop_shovels`; (C) all hoes/shovels through broad vanilla tags; (D) no tool uprooting, normal authorized break only.
- **Recommendation:** B, cost 1 durability on success, using the canonical `farming_hoe` ID; community restoration remains subject to A3.
- **Consequences:** A is narrow; B matches existing root-tool semantics; C is accessible but broad and harder to protect; D simplifies tools but weakens the requested uproot behavior.

#### G3. Define mature harvest drops and seed recovery.

- **Discovered:** Existing crops define tool, produce yield, seed-return chance, quality, and reset behavior. The design fixes persistent stage-1 regrowth and says sword cutback has no default drop, but deliberately leaves final economy/yields unresolved.
- **Why needed:** Registrations, loot/interaction logic, tool policy, and progression tests depend on these rules.
- **Options:** (A) mature right-click/shears yields one flower item and resets; successful permanent uproot returns one seed; sword gives nothing; (B) bare-hand mature harvest with probabilistic seed return; (C) sword/shears also produce drops; (D) no seed recovery from planted flowers.
- **Selected decision:** Shearing yields one harvested flower item and resets the same plant to stage 1. The harvested item later delegates to the existing `CropSeedExtractor` off-hand/shift-use mechanism; sword cutback yields nothing.
- **Consequences:** A clearly separates harvest/cutback/uproot; B is more accessible but makes seeds renewable through routine harvest; C encourages attack harvesting and complicates Adventure behavior; D makes seed supply depend entirely on recipes/world sources.

#### G4. Confirm indirect-destruction behavior for protected and ordinary flowers.

- **Discovered:** No existing component comprehensively handles explosions, pistons, fluids, replacement, and command mutation. Commands are already permission-level-2 operations, while environmental mutations have no player actor.
- **Why needed:** The design forbids indirect bypasses but does not completely define ordinary-flower environmental behavior or drops.
- **Options:** (A) protected flowers are explosion/fluid immune, nonreplaceable, and piston-blocking; ordinary flowers are piston-blocking but otherwise follow normal environmental destruction; authorized `/setblock`/worldgen counts as admin/system mutation; (B) make all flowers environmentally immune; (C) protect only player-driven paths.
- **Recommendation:** A, with no protected-flower drops from denied indirect events and normal configured drops for ordinary environmental destruction.
- **Consequences:** A fulfills protection while retaining ordinary world interaction; B is safest but makes all flowers unusually indestructible; C is easiest but violates the indirect-bypass requirement.

### ASSET

#### AS1. What placeholder texture resolution should be authoritative?

- **Discovered:** Existing crop textures have no consistent resolution; common sizes include 16, 32, 64, and 128 pixels square, with 128×128 the most frequent inspected square size.
- **Why needed:** Every base/mask pair must have identical resolution/padding, and changing resolution later creates avoidable rework across 98 in-world textures.
- **Options:** (A) 32×32 pixel-art placeholders; (B) 64×64; (C) 128×128 to match the largest common crop convention.
- **Selected decision:** C, 128×128 for every aligned in-world base/mask pair.
- **Consequences:** A is fastest and crisp but limits fine mask shading; B supports highlights/shadows without excessive placeholder cost; C offers detail but increases asset size and labor with no current renderer requirement.

### NON-BLOCKING

#### N1. Store the planter UUID for audit/support?

- **Discovered:** The required protection decision needs origin/protected state but not ownership. The design makes planter UUID optional.
- **Why needed:** It affects NBT schema and future diagnostics, but not initial authorization if protected flowers are admin/Creative-only mutable.
- **Options:** (A) store UUID when a player plants; (B) omit it; (C) store UUID plus display-name snapshot.
- **Selected decision:** Store planter UUID without a display-name snapshot, plus stable planting-region provenance, current authoritative quality, color, and the other persistent identity fields recorded in the decision log.
- **Consequences:** A modestly improves audit/debugging and future migration options; B is minimal; C stores stale/redundant identity data and is not recommended.

No owner-gate blocker remains for Milestone 2. Milestone 3 and later work remain unauthorized by the current assignment.

## 23. Milestone 2 foundation update

### Dye Tub inspection result

The active `Farming` branch does **not** contain a Dye Tub implementation. Repository-tree and content searches found no Dye Tub source class, block, block entity, item, resource, value object, serialization key, synchronization payload, validator, or tint application. The only tracked color-named Java files are unrelated `util/LabelColor.java` and `winery/GrapeColor.java`. Stale `logs/` references to `com.seggellion.britannia_mod.bannerdyeing` tests are not source or active-branch implementation evidence and were not reused.

Consequently, no Dye Tub class or symbol could be reused or adapted directly. Milestone 2 establishes a compatibility-ready boundary:

- `FlowerColor` stores one validated 24-bit RGB tint integer and exposes the later opaque-ARGB renderer adapter.
- `FlowerColorDefinition` provides configured stable IDs without making the saved schema depend on a small enum.
- `FlowerPaletteEntry` keeps species-specific allowlists, positive weights, and optional rarity labels.
- `FlowerPersistentState` serializes the selected value as NBT integer `ColorTint` and never depends on a selector while loading.

The value space is 16,777,216 colors, exceeding the requested approximate two-million capacity without preallocating or registering one object per possible value. If Dye Tub source later appears on `Farming`, its exact integer packing and validation can be adapted at the `FlowerColor` boundary without changing saved flower identity.

### Existing crop-item-to-seed behavior confirmed

- `item/CropSeedExtractor.shouldExtract(Player, InteractionHand)` recognizes off-hand use **or** shift-use.
- `item/CropSeedExtractor.tryExtractSeed(...)` creates exactly one seed on the logical server, adds it to inventory or drops it, plays `SoundEvents.PUMPKIN_CARVE`, and consumes one source item unless the player has `abilities.instabuild`.
- The method returns sided success; the client predicts success but creates/consumes nothing.
- No animation, cooldown, skill award, or additional game-mode behavior is applied.
- `SeedExtractableHealingCropItem.use` performs this check before normal item use/eating and receives its crop-to-seed mapping through a per-item seed supplier.
- `GrapesItem.use` performs the same check and constructs `ItemRegistry.GRAPE_SEEDS` while copying grape variety.
- This mechanism is not active on every harvested crop class today. The flower item must reuse/extend `CropSeedExtractor`, not assume a universal reverse mapping in `CropRegistry`.

`FlowerDefinition.seedItemId` and `harvestedItemId` are the Milestone 2 mapping contract. They do not register items or enable the interaction.

### Shared evaluator and persistence boundary

`FarmingGrowthProfile` is the narrow shared data interface now implemented by both `CropDefinition` and `FlowerGrowthProfile`. `CropQualityCalculator` continues to own the unchanged hydration, nutrient, growth-multiplier, and quality formulas. Flower definitions adapt design ranges to existing scales rather than copying formulas.

`FlowerPersistentState`, `FlowerSoilSnapshot`, `FlowerCommunityRestoration`, `FlowerRegionProvenance`, `FlowerQuality`, and `FlowerGrowthState` form the NBT-ready Milestone 2 contract. They are not a block entity and do not perform world mutation. The complete climate mapping and rationale are recorded in `FLOWER_SYSTEM_DECISIONS.MD`.
