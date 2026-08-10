# UltimaCraft New Assets — Asset Import Manifest

## Milestone 1 inventory baseline

- Inventory date: 2026-08-09 (America/Vancouver).
- Raw root: `C:\projects\britannia\raw fiels\models to import`.
- Scan mode: recursive, read-only. No source file or archive was modified.
- Files directly present under the raw root: 74 files, 4,705,838 bytes.
- Direct extension counts: 33 `.png`, 24 `.json`, 10 `.zip`, 3 `.mcmeta`, 2 `.yml`, 2 `.bbmodel`.
- All ten outer ZIPs were enumerated without extraction into the source tree. Seven nested ZIP entries were also enumerated in memory.
- No `.obj`, `.mtl`, `.gltf`, or `.glb` files were found.
- Source packs include Minecraft JSON/PNG resources, Blockbench `.bbmodel` projects, Nexo/ItemsAdder/Oraxen/CraftEngine configuration, and a ModelEngine training-dummy blueprint.
- Milestone 1 did not copy/import assets, create placeholders, register content, or change gameplay.

### Status meanings

- `FOUND`: complete usable source model/texture pair exists, or the exact requested asset already exists in the tracked repository.
- `PARTIAL`: related source exists, but art, variants, dimensions, animation, or identity must be completed or approved.
- `MISSING`: no credible source match was found after recursive directory/archive inspection.

`PLACEHOLDER`, `IMPORTED`, and `VALIDATED` are later-milestone states and are not assigned in this inventory milestone.

## Owner decisions recorded after Milestone 0

- Ibis cap: working requirement is 15 ibis total across all three Jhelom areas, counted independently of other city animals.
- Moongate: update existing `MoongateBlock`; collapse the current two-block teleporter implementation to one logical block with a 32-voxel-high custom model while preserving teleport behavior.
- Water well: supports watering cans, vanilla buckets, and pitchers. Correct the existing empty-pitcher registration for water behavior only.
- Training dummy: axes are supported. Milestone 7 isolates the working slugs `swordsmanship`, `mace_fighting`, `fencing`, and `tactics`; the external skill service must confirm them before release.
- Silk: a future textile material/item distinct from `britannia_mod:spiders_silk`.
- Loom: later processing rule is 5 `ball_of_yarn` or 5 `spool_of_thread` → 1 `folded_cloth`; no broader tailoring UI/crafting system is authorized.
- Display cases: decorative and neighbor-connected only; no storage or displayed-item inventory.
- Sandstone: reuse/reconcile the existing sandstone family described below rather than create duplicate IDs.
- Purchased asset packs: all art originating from purchased packs is approved only as temporary placeholder art and is expected to be replaced later. When copied into the mod in later milestones, classify it as `PLACEHOLDER`, not final imported art.
- Merchant-cart palette: approved IDs/colors are red, purple, blue, green, yellow, and white.
- Axe mapping: every axe maps to Swordsmanship for training-dummy purposes.

## Source-container checksums

| Source container | Files | SHA-256 |
|---|---:|---|
| `blood.zip` | 40 | `163669328cb2eb8f90610012f19372e119e26c603897351121e10cd1e839da60` |
| `elitecreatures-medieval_market_decoration_v2.zip` | 50 | `1bd4c8d8aa2555e34b26d64a7d84b50c6e79b72e69694a3d3b05d4d3c1931c5a` |
| `Garden Essentials Vol 4 - Bushes.zip` | 29 | `577485157ae9361c6b06454ed9d7feaf3c8767d11b45d347f0bb8699a617efa0` |
| `globe.zip` | 27 | `7934ba451b3d1392b368ca17d3edb15a8565b6e92ed1c02fcd80f36333947e21` |
| `Medieval Market Furniture Set.zip` | 231 | `a94e1bb79123a277d819df3af70039a1109c9d4ae44be17ad3d64fea4d7b8a73` |
| `Nexo Assets - Crates & Barrels.zip` | 27 | `31ad339bebc4c28e49878e54b0131a47ff426d9ba8070b14feec2c954cfbe3e8` |
| `Nexo Assets - Tailoring Station.zip` | 15 | `e7b5cb579d06b738dfb61ec2023132c78b4a3e86aa2b54a6697e0708ebdfa8ca` |
| `shizuart_farmer_props.zip` | 66 | `ba58633db19701e46837b46be0b98fd4ab57f038e647d0b55013f7ed838771b6` |
| `ShizuArt_Plants_Bundle.zip` | 183 | `17b6de23501fdde2fe0619a1d45064d2ef1c71bc64c45ccda974b8e1cf421a05` |
| `training_dummy.zip` | 54 | `4997030831eda233a91b4aa8fa1609842d015015e6cacc579f2b9ca9e7b7b085` |

Purchased-pack readmes/install instructions were present, but no explicit redistribution/license grant was found inside the archives. The owner authorized their use only as temporary placeholder art that will be replaced later.

## Requested asset summary

| Requested asset | Proposed/existing registry ID | Status | Placeholder proposal |
|---|---|---|---|
| Ibis, white/scarlet | `ibis` with persistent variant | `PLACEHOLDER` | White source imported unchanged; UV-safe scarlet recolor and all runtime behavior implemented in Milestone 6 |
| Moongate visual replacement | existing `moongate_block` | `PLACEHOLDER` | Existing ID now uses one logical block and a re-authored 32-voxel model; purchased art remains temporary |
| Merchant carts, six colors | `merchant_cart_<color>` | `PLACEHOLDER` | Six final IDs implemented; red/purple use temporary purchased art and four variants use vanilla-color placeholders |
| Training dummy | `training_dummy` | `PLACEHOLDER` | Implemented in Milestone 7; purchased punching-bag art re-authored to exact 32×48 visible bounds and must be replaced later |
| Fountain | `fountain` | `PLACEHOLDER` | Temporary source art normalized to the requested 2x2x3 structure |
| Moonglow bush | `moonglow_bush` | `PLACEHOLDER` | Temporary purchased purple-flower bush imported in Milestone 2 |
| Sandstone family | existing IDs | `VALIDATED` | Existing connected family reused; no duplicate IDs |
| Globe | `globe` | `PLACEHOLDER` | Temporary purchased large-globe art imported in Milestone 2 |
| Small crate | `small_crate` | `PLACEHOLDER` | Temporary purchased art; 9-slot container implemented in Milestone 4 |
| Medium crate | `medium_crate` | `PLACEHOLDER` | Unmistakable code-authored placeholder; 27-slot container implemented in Milestone 4 |
| Large crate | `large_crate` | `PLACEHOLDER` | Temporary purchased art normalized to an authoritative 2x2x2 structure; 54-slot container implemented in Milestone 4 |
| Water well | `water_well` | `PLACEHOLDER` | Temporary purchased art normalized to a functional 1x2x2 well in Milestone 5 |
| Ladder | `ladder` | `PLACEHOLDER` | Temporary purchased art re-authored to a 3-block/48-voxel structure in Milestone 5 |
| Scarecrow | `scarecrow` | `PLACEHOLDER` | Temporary purchased art implemented as a two-block-high structure |
| Fern | `fern` | `PLACEHOLDER` | Temporary purchased small-flora art imported in Milestone 2 |
| Dress form | `dress_form` | `PLACEHOLDER` | Temporary purchased mannequin art implemented under the final ID |
| Folded cloth | `folded_cloth` | `PLACEHOLDER` | Temporary purchased fabric-stack art imported in Milestone 2 |
| Loom | `loom` | `PLACEHOLDER` | Temporary purchased model re-authored to the requested 32x48x16-voxel envelope |
| Bolt of cloth | `bolt_of_cloth` | `PLACEHOLDER` | Unmistakable code-authored temporary model added in Milestone 2 |
| Spinning wheel | `spinning_wheel` | `PLACEHOLDER` | Functional one-cell magenta/black replacement-art block implemented in Milestone 8 |
| Ball of yarn | `ball_of_yarn` | `PLACEHOLDER` | Final processing ID with unmistakable vanilla-texture placeholder item model |
| Spool of thread | `spool_of_thread` | `PLACEHOLDER` | Final processing ID with unmistakable vanilla-texture placeholder item model |
| Silk textile input | `silk` | `MISSING` | Deferred textile item/art; must not alias spiders' silk |
| Display case family | `display_case` | `PLACEHOLDER` | One functional two-block ID with compositional independent/end/middle/corner placeholder visuals implemented in Milestone 9 |
| Pewter mug | `pewter_mug` | `PLACEHOLDER` | Unmistakable code-authored temporary model added in Milestone 2 |
| Kettle | `kettle` | `PLACEHOLDER` | Unmistakable code-authored temporary model added in Milestone 2 |
| Plates and silverware | `plates_and_silverware` | `PLACEHOLDER` | Unmistakable code-authored temporary model added in Milestone 2 |

## Detailed entries

### 1. Ibis bird — white and scarlet variants

- Requested asset: Ibis bird, white and scarlet.
- Proposed registry ID/category: `britannia_mod:ibis`; entity with synchronized/persisted variant.
- Source model: `white ibis\white ibis.bbmodel`.
- Source textures: `white ibis\texture.png`; no scarlet texture found anywhere in the raw tree or archives.
- Format/checksum: Blockbench `.bbmodel` `da2731a389fb01d103b5cab181561c55389ab13c91e35daf16b80a0dca8a7a2c`; 512×512 ARGB PNG `5127c6846e013cc7021bc63c906693526685d1787906fc24ca4a43c232fa2507`.
- Import status: `PLACEHOLDER` — implemented in Milestone 6. The purchased white model/texture remain temporary art, and the derived scarlet texture is also temporary pending live acceptance and eventual replacement.
- Final targets: `EntityRegistry`; `entity/IbisEntity.java`; `entity/IbisVariant.java`; ibis model/renderer classes; `JhelomIbisPopulation`; `assets/britannia_mod/geo/ibis.geo.json`; `animations/ibis.animation.json`; `textures/entity/ibis_white.png`; `textures/entity/ibis_scarlet.png`; spawn egg, localization, and loot resources.
- Required behavior: implemented as one entity type with a synchronized integer variant selected server-side and persisted as `IbisVariant`; Jhelom-only regional spawning uses one independent 15-ibis total across the three Jhelom AABBs and does not register global biome spawning.
- Dimensions/animation: source bounds x `-3..3`, y `-5.07779..13.15677`, z `-22.37905..8.3`; animations `walk` 2s, `idle` 6s, `eating` 6s.
- Collision: animal-sized hitbox authored independently from visual bounds.
- Notes/blockers: the white PNG is copied byte-for-byte. The image-generation pass supplied the approved scarlet palette only because its raster output changed atlas dimensions and alpha; `import_milestone6.ps1` applies that palette deterministically only to connected neutral plumage, preserving the 512×512 atlas, every alpha value, UV layout, eyes, beak, and legs. The generated placeholder requires live visual acceptance.

### 2. Moongate visual replacement

- Requested asset: replacement art for existing `MoongateBlock`.
- Existing registry ID/category: `britannia_mod:moongate_block`; animated teleport block. Do not add a parallel moongate ID/system.
- Source model: `moongate\portal.bbmodel`.
- Source textures: `moongate\portal_texture.png` through `portal_texture6.png`.
- Format/checksum: Blockbench `.bbmodel` `a22ccc954d3f7b8a36670c017da4f506d0bb18b326700f416b1e8931d0dd48a7`; texture checksums are recorded in the inventory evidence. Textures are ARGB and range from 32×32 to animated strips of 128×1536.
- Import status: `PLACEHOLDER` — implemented in Milestone 10 under the existing final ID with temporary purchased art.
- Final targets: existing `MoongateBlock` and `moongate_block` registration; re-authored `models/block/moongate_block.json`; six temporary source textures under `textures/block/new_assets/moongate`; existing teleport handler/tick handler unchanged. The legacy top block/item IDs remain registered only for old-save compatibility.
- Required behavior: preserve existing destination/configuration, mount/escort, and cooldown behavior while moving to one logical block.
- Dimensions/animation: raw bounds x `-16..16`, y `-1.5..41.5`, z `-16..16`; `idle` 3s and `spawn` 1.5s. Owner target is 32 voxels high.
- Collision: portal interaction volume must be deliberate and must not use the raw 32×32×43 bounds unchanged.
- Notes/blockers: Milestone 10 scaled x/z by one half and translated/scaled y from `-1.5..41.5` to `0..32`, preserving the layered planes and animated atlases. The one-cell block is pass-through; its 32-voxel model intentionally renders above that cell. `moongate_top` is invisible and self-removing but remains registered for migration safety. The paired-dungeon system was deliberately left unchanged.

### 3. Merchant carts — six colors

- Requested asset: six decorative merchant-cart colors.
- Proposed registry IDs/category: approved `merchant_cart_red`, `merchant_cart_purple`, `merchant_cart_blue`, `merchant_cart_green`, `merchant_cart_yellow`, and `merchant_cart_white`; decorative multiblock blocks.
- Source models/textures: `Medieval Market Furniture Set.zip` raw entries `medieval_market_wagon_red`, `medieval_market_wagon_purple`, and uncolored/base `medieval_market_wagon2`, each with `.json`, `.bbmodel`, and `.png` art.
- Source format/checksums: Minecraft JSON/PNG and Blockbench. Model hashes: base `418e47bfae3acf3e24f20d3853e0c3fa90a1375f5e538c4b27e104170794703b`, purple `2f94428ac2534f0abdf2bd15ae89a72f169e5219d255d397013ea8d6bd564c01`, red `2212cc3bf20136d25e7a4619d3ad9b0c9f94dc6060188a14f393b162c3ad941e`.
- Import status: `PLACEHOLDER` — all six final IDs are implemented in Milestone 3. Red/purple use temporary purchased wagon art; blue/green/yellow/white reuse that temporary geometry with conspicuous vanilla wool color placeholders.
- Final targets: `DecorativeMultiblockBlock`, `DecorativeMultiblockItem`, per-color blockstate/model/item resources, registries, localization, empty block loot tables, creative tab, and axe-mineability tags.
- Required behavior: decoration only; share placement/teardown code and geometry where art allows.
- Dimensions/animation: base bounds about 25×24×35 voxels; red/purple about 36×38×47 voxels. No animation metadata.
- Collision: authored cart body/wheel shapes, not full cubes.
- Notes/blockers: each cart occupies an authoritative centered 3x3x3 structure, places atomically, tears down as a whole, and drops once. All six models remain replacement-art candidates; interactive visual/collision review is still required.

### 4. Training dummy

- Requested asset: animated 2-block-wide × 3-block-high training dummy.
- Proposed registry ID/category: `britannia_mod:training_dummy`; functional animated multiblock trainer.
- Source model: direct gray JSON under `training_dummy\resourcepack\...\training_dummy_gray.json`; animated ModelEngine blueprint `training_dummy.zip::fv_punching_bags/plugins/ModelEngine/blueprints/fv_punching_bag_gray.bbmodel`.
- Source texture: direct 128×128 ARGB `...\fv_punching_bag\gray.png`; eight other color textures are unrelated optional variants.
- Format/checksum: animated `.bbmodel` hash `9d63b0d4e9ffec0a33bc02071f3f97e5facfc79dd00a4b682815f8c459eaac7a`; direct JSON `cc7291f83211b86882bcfb3f6241acdfea721e24454a3e571bdd2bbb8346c17f`; gray PNG `c33911195d7c4f83c8cb7bfff6309a6c5f9c1dc9885d4b1b1f777f1a61c44b88`.
- Import status: `PLACEHOLDER` — implemented in Milestone 7 with temporary purchased punching-bag art.
- Final targets: authoritative multiblock root/parts, animated root block entity/renderer, training event service, model/animation/texture resources, registries/localization/loot.
- Required behavior: Swordsmanship/Mace Fighting/Fencing training to 25.0; axes supported; small Tactics chance; no Wrestling/Anatomy/Lumberjacking; 3-second per-player server cooldown; no weapon durability loss.
- Dimensions/animation: source blueprint bounds x `-10..10`, y `4..48`, z `-7..7` were deterministically transformed to exact 32×48×14 visible bounds. The five-second looping source `hit` animation was retimed to a one-second non-looping server trigger.
- Collision: 2×3 multiblock occupancy with narrower deliberate strike/collision shapes.
- Notes/blockers: final art and interactive collision/animation review remain open. The working skill slugs require external-service confirmation.
- Owner resolution: all axes map to Swordsmanship. Milestone 7 maps ordinary Bladed to Swordsmanship, Bashing to Mace Fighting, Polearms/explicit thrusting blades to Fencing, and leaves Throwing unsupported.

### 5. Fountain

- Requested asset: 2×2×3 decorative fountain.
- Proposed registry ID/category: `britannia_mod:fountain`; decorative multiblock.
- Source model: `fountain\models\item\tiered_fountain_angel.json` with related tiered/water models.
- Source textures: `fountain\textures\tiered_fountain.png`, `fountain_water.png`, and animation `.mcmeta` for water.
- Format/checksum: JSON `81e97f1937863dc06b0d047c78af8d47e9817b4256783683dbe4925d563d6a5f`; texture `7d8800aac2053571a703968ecf23b632cf3f35a7d81e05a1267938bafbb31ffb`; water texture `c5c6a8c797899105dd0cd42b38f896ca2022922269576d8746d43064db624637`.
- Import status: `PLACEHOLDER` — normalized and implemented as temporary art in Milestone 3.
- Final targets: `DecorativeMultiblockBlock`, `DecorativeMultiblockItem`, and `assets/britannia_mod/{blockstates,models/block,models/item,textures/block}` fountain resources; registries/localization/empty loot/client translucent layer.
- Required behavior: decorative, atomic place/teardown, one drop.
- Dimensions/animation: angel model bounds x/z `-8..24` (exact 32×32 footprint) and y `-16..30` (46 voxels, compatible with a 3-block envelope); animated water texture.
- Collision: basin/pillar shapes matching visible footprint as closely as practical.
- Notes/blockers: the model was translated to x/z `0..32` and retains y `-16..30`, rendered from the middle-layer root across an authoritative 2x2x3 structure. Interactive water-animation, translucency, and collision review remains open.

### 6. Moonglow bush

- Requested asset: Moonglow bush decoration.
- Proposed registry ID/category: `britannia_mod:moonglow_bush`; transparent decorative plant.
- Candidate model: `ShizuArt_Plants_Bundle.zip::ItemsAdder/contents/shizuart_furnitures/models/bush_props/bush_purple_flowers.json`.
- Candidate texture: shared `.../textures/bush_props/bush.png`.
- Format/checksum: JSON `4fc256e399e835be0e378b71fe8a6d415a49f777b2b0187fef10e69ee82a77c6`; PNG `69635ac6e3d341a5093b2a0efd6acbbb55c68d4dfe33868a212a00ebd7bfd6af`.
- Import status: `PLACEHOLDER` — the generic purchased purple-flower bush was normalized as temporary art in Milestone 2; it is not final Moonglow art.
- Final targets: `BlockRegistry.MOONGLOW_BUSH`, `ItemRegistry.MOONGLOW_BUSH`, `DecorativePlantBlock`, and `assets/britannia_mod/{blockstates/moonglow_bush.json,models/block/new_assets/moonglow_bush.json,models/item/moonglow_bush.json,textures/block/new_assets/moonglow_bush.png}` plus localization, loot, creative-tab, and cutout registration.
- Required behavior: decoration only; no world generation.
- Dimensions/animation: normalized candidate is non-animated; replacement art and scale remain an art-review item.
- Collision: low/non-full foliage collision.
- Notes/blockers: registered under the final ID with player-facing temporary-art labeling. Replace the model/texture without changing the ID.

### 7. Sandstone family — existing tracked assets

- Requested assets/existing registry IDs:
  - 16×16 full block with four variants → `custom_sandstone_brick`.
  - 16×5 wall family → `regular_sandstone_wall` (existing visual geometry is 16×5×32 and neighbor-aware).
  - `ornate_sandstone_window`.
  - `sandstone_window`.
  - `sandstone_post`.
  - `ornate_sandstone_post`.
  - `sandstone_battlement`.
  - `sandstone_column`.
- Category: existing blocks/items; no raw import required.
- Source model/texture paths: tracked `src/main/resources/assets/britannia_mod/models/block/structure/sandstone/**` and `textures/block/structure/sandstone/**`; exact blockstates and item models already exist.
- Source format/provenance: Git-tracked JSON/PNG at starting HEAD `50061f07231271e67591b2720ddf21cf4fa54fcc`.
- Import status: `VALIDATED` — existing registrations and connected models were reused; Milestone 2 added missing block loot tables and explicit pickaxe mineability without duplicating the family.
- Final Java/resource targets: existing `BlockRegistry`/`ItemRegistry` holders and same tracked resource paths; reuse rather than duplicate.
- Required behavior: preserve four texture variants on `custom_sandstone_brick`; preserve neighbor-aware `DoubleWallBlock` states for the wall/window/post/battlement/column family.
- Dimensions/animation: `custom_sandstone_brick` is a full block; existing straight sandstone family models are 16×5×32. No animation.
- Collision: existing custom block/wall collision remains on the live-client checklist; dedicated-server resource/tag loading passed.
- Notes/blockers: the owner listed “sandstone battlement” twice; both references map to the single existing `sandstone_battlement` unless a distinct ornate battlement is later requested. Existing `ornate_sandstone_wall` and `sandstone_block_wall` also remain available but were not newly requested.

### 8. Globe

- Requested asset: globe decoration.
- Proposed registry ID/category: `britannia_mod:globe`; decorative block/item.
- Source model/texture: `globe.zip::Nexo/pack/assets/minecraft/models/lanshan/essentials/globe/large_globe.json` and matching texture; mini/retro alternatives also exist.
- Format/checksum: JSON `5b52c322a76177b9fde48934c43fb4eb2e5cc6ed204adbd271cd115460d2dff2`; PNG `a8e9affe05c5eb7131cb5cd6a3b39c04a2f1f754b86d3aaad9c044dcde0e2d6e`.
- Import status: `PLACEHOLDER` — the purchased large-globe model/texture was normalized as temporary art in Milestone 2.
- Final targets: `BlockRegistry.GLOBE`, `ItemRegistry.GLOBE`, `DecorativePropBlock`, and `assets/britannia_mod/{blockstates/globe.json,models/block/new_assets/globe.json,models/item/globe.json,textures/block/new_assets/globe.png}` plus localization, loot, creative-tab, and axe-mineability entries.
- Required behavior: decoration only.
- Dimensions/animation: large model about 15×24×12 voxels, no animation.
- Collision: narrow pedestal/globe approximation, not full cube.
- Notes/blockers: large geometry is the temporary selection and carries a temporary-art label. Final replacement can retain the registry ID.

### 9. Crate container family

#### Small crate

- Proposed registry ID/category: `britannia_mod:small_crate`; one-block container.
- Source: `Nexo Assets - Crates & Barrels.zip::Raw Files/models/crate.json` and `Raw Files/textures/crate.png`.
- Format/checksum: JSON `3de490b572b01174427e0a6233977d2861f3433c0668cbcd6716f3c74146608b`; PNG `d98c6fbcb91a272b81d2968d0c2b1c3c187023aec254431cab0c7a68f7c29f10`.
- Import status: `PLACEHOLDER` — temporary purchased art and the final container ID were implemented in Milestone 4.
- Final targets: `CrateBlock`, `CrateBlockEntity`, shared multiblock placement item, registries, vanilla one-row chest menu, blockstate/model/item/texture, localization, empty block loot, creative tab, cutout layer, and axe tag.
- Required behavior: persistent server-authoritative 9-slot inventory.
- Dimensions/animation/collision: source bounds 12×11×12 voxels; no animation; fitted crate collision.

#### Medium crate

- Proposed registry ID/category: `britannia_mod:medium_crate`; container.
- Source: no specifically medium geometry/texture. `crate_stack.json` is a two-crate stack (about 27×22×13 voxels) and is not a credible single medium crate without owner approval.
- Format/checksum: none assigned.
- Import status: `PLACEHOLDER` — an unmistakable magenta/black code-authored placeholder and the final container ID were implemented in Milestone 4.
- Final targets: same crate family implementation/resources using the `medium_crate` final ID and vanilla three-row chest menu.
- Required behavior: persistent server-authoritative 27-slot inventory.
- Dimensions/animation/collision: one-block placeholder bounds x/z `0.5..15.5`, y `0..14`; fitted collision; no animation.
- Notes/blockers: replacement art remains required. No stack model was silently relabeled.

#### Large crate

- Proposed registry ID/category: `britannia_mod:large_crate`; likely multiblock container.
- Source: `Nexo Assets - Crates & Barrels.zip::Raw Files/models/large_crate.json` and `Raw Files/textures/large_crate.png`.
- Format/checksum: JSON `a0e821d18adf7637844d29226eaee5bbd4963f3852feade1854570ca036deb5b`; PNG `095664941d9bdbf7d3dfb734f58a174d3f931f4a3b3a932bf53997314beea65d`.
- Import status: `PLACEHOLDER` — temporary purchased art and the final multiblock container ID were implemented in Milestone 4.
- Final targets: `CrateBlock`, one root `CrateBlockEntity`, shared transactional multiblock item, registries, vanilla six-row chest menu, resources/localization/empty loot/client cutout/axe tag.
- Required behavior: persistent server-authoritative 54-slot inventory; all child interactions resolve to the one root inventory; breaking any part tears down the structure and drops contents and block item once.
- Dimensions/animation/collision: normalized bounds x `0.25..27.75`, y `0.5..19`, z `0.46815..21.53185`; authoritative 2x2x2 occupancy; no animation; deliberate per-cell collision.
- Notes/blockers: `large_crate_stack` and mixed-stack models remain unassigned alternate decorations. Temporary art requires later replacement and interactive footprint review.

### 10. Water well

- Requested asset: 2-block-high water well.
- Proposed registry ID/category: `britannia_mod:water_well`; functional two-block structure.
- Source model: preferred candidate `shizuart_farmer_props.zip::ItemsAdder/contents/shizuart_furnitures/models/farmer_props/farmer_well.json`; a larger `medieval_market_well` alternative also exists.
- Source texture: shared `.../textures/farmer_props/farmer_props.png`.
- Format/checksum: JSON `34f6ffd7a127c526ed0bece211dd88883633aabf5d6912b70aa716ceaa083ded`; PNG `e382264c5753533a713aef1f852a0522c602435e8bfb7839d1d940fb2eb265f6`.
- Import status: `PLACEHOLDER` — temporary purchased art and the final functional ID were implemented in Milestone 5.
- Final targets: `WaterWellBlock`, shared transactional multiblock placement, `WaterSourceInteraction`, `BlockRegistry`/`ItemRegistry`, blockstate/model/item/texture resources, localization, empty loot, creative tab, and cutout registration.
- Required behavior: server-authoritatively fill watering cans, vanilla buckets, and pitchers without duplication; correct empty-pitcher registration for water behavior only.
- Dimensions/animation: normalized bounds x `0..16`, y `0..32`, z `0..32`; authoritative 1x2x2 occupancy (four cells). No animation.
- Collision: deliberate per-cell stone/masonry collision with interaction available from every valid part.
- Notes/blockers: `pitcher_empty` now uses the existing water-only `PitcherItem` while remaining a placeable pitcher block item. Interactive footprint, UV, and reach review remains open; the art remains temporary.

### 11. Double-sided ladder

- Requested asset: ground-placeable, double-sided, climbable 3-block-high ladder, axe-destructible in Adventure mode.
- Proposed registry ID/category: `britannia_mod:ladder`; functional multiblock utility structure.
- Candidate source model: `shizuart_farmer_props.zip::ItemsAdder/contents/shizuart_furnitures/models/farmer_props/farmer_stepladder.json`.
- Source texture: shared farmer-props atlas.
- Format/checksum: JSON `cc4f5a7439f0f50e67e0fb3f831415c99517e85072bb4ecb81169c66f667c595`; PNG `e382264c5753533a713aef1f852a0522c602435e8bfb7839d1d940fb2eb265f6`.
- Import status: `PLACEHOLDER` — temporary purchased art was re-authored and the final functional ID was implemented in Milestone 5.
- Final targets: `LadderMultiblockBlock`, `AdventureLadderItem`, shared transactional multiblock placement, narrowly scoped Adventure place/break predicates, climbable/axe tags, blockstate/model/item/texture resources, localization, empty loot, creative tab, and cutout registration.
- Required behavior: atomic ground placement, climbable from both faces, whole-structure teardown, narrow Adventure placement, axe destruction.
- Dimensions/animation: re-authored to a full 48-voxel visual with model bounds x `0..16`, y `-16..32`, z `0..16`, rendered from the middle cell of an authoritative 1x1x3 structure. No animation.
- Collision: narrow stair/rung traversal shapes, not a flat vanilla ladder or full cubes.
- Notes/blockers: the first 0..48 root model exceeded Minecraft's extended-element parser limit, so the unchanged 48-voxel visual was translated to -16..32 and rendered from the middle cell. Interactive double-sided climbing, traversal feel, and all-or-nothing placement still require live review; the art remains temporary.

### 12. Scarecrow

- Requested asset: decorative 2-block-high scarecrow.
- Proposed registry ID/category: `britannia_mod:scarecrow`; decorative multiblock.
- Preferred source: `Medieval Market Furniture Set.zip` raw `medieval_market_scarecrow.json`/`.bbmodel` and matching PNG. A ShizuArt farmer scarecrow is a viable alternate.
- Format/checksum: preferred JSON `7983b8f2e8aac3d47b60b26fb4616b2bd77699d4f424da0915733c285dabd5ec`; PNG `256f992e8daeb3240d58d4dc9e184f26d631334824d8066a1bcd148688068992`.
- Import status: `PLACEHOLDER` — preferred purchased art was normalized and implemented in Milestone 3.
- Final targets: shared multiblock block/item implementation, block/item registry, blockstate/model/item/texture, localization, empty loot, creative tab, cutout layer, and axe-mineability tag.
- Required behavior: decoration only; no crop-protection mechanic.
- Dimensions/animation: preferred bounds about 25×31.7×16 voxels; Shizu alternate about 30×31.5×25; no animation.
- Collision: narrow post/body shape and coherent two-block placement.
- Notes/blockers: the Medieval Market candidate was selected and translated to x `0..25`, y `0..31.71682`, z `0..16`; it occupies a 2x1x2 authoritative structure. Art is temporary and interactive review remains open.

### 13. Fern

- Requested asset: small transparent fern.
- Proposed registry ID/category: `britannia_mod:fern`; decorative plant.
- Candidate model/texture: `ShizuArt_Plants_Bundle.zip` `flora_small_plant.json` plus shared `flora.png` atlas.
- Format/checksum: JSON `411a870c47d8c9694c3445b5eed99f96bff4122d3fb1a687f69fc424c5bba7c3`; PNG `d67371f36eecc14f672edce002346f88b822cee8540560b56cf5a9fd0c769dfe`.
- Import status: `PLACEHOLDER` — the generic purchased small-flora model was normalized as temporary fern art in Milestone 2.
- Final targets: `BlockRegistry.FERN`, `ItemRegistry.FERN`, `DecorativePlantBlock`, and `assets/britannia_mod/{blockstates/fern.json,models/block/new_assets/fern.json,models/item/fern.json,textures/block/new_assets/fern.png}` plus localization, loot, creative-tab, and cutout registration.
- Required behavior: no world generation; use existing substrate/replaceability conventions.
- Dimensions/animation: candidate about 28.6×14.1×28.6 voxels; no animation.
- Collision: low/non-full or empty plant collision.
- Notes/blockers: player-facing name marks the art temporary; replace model/texture later without changing the final ID.

### 14. Dress form

- Requested asset: decorative 2-block-high dress form.
- Proposed registry ID/category: `britannia_mod:dress_form`; decorative multiblock.
- Source model/texture: `Nexo Assets - Tailoring Station.zip` `mannequin.json` and `mannequin.png`.
- Format/checksum: JSON `88a5952ff5dd73cd698de3ddf56fbc4c91c28aafaf4794e3063db81da6caa7d3`; PNG `ae9dcbecaebe225d1f6b567548d0e6f387bf8582325e47e0c6e8b3f318eb9d54`.
- Import status: `PLACEHOLDER` — purchased mannequin art was implemented under the final Dress Form ID in Milestone 3.
- Final targets: shared multiblock block/item implementation and standard resources/registry/localization/empty loot/client cutout/axe tag.
- Required behavior: decoration only; must not become an armor stand.
- Dimensions/animation: exact 14×32×8-voxel envelope; no animation.
- Collision: narrow base/post/torso approximation.
- Notes/blockers: source vendor calls it mannequin; final player-facing name remains Dress Form (Temporary Art). It occupies a 1x1x2 authoritative structure and must not behave as an armor stand.

### 15. Folded cloth

- Requested asset: placeable folded-cloth decoration and textile output.
- Proposed registry ID/category: `britannia_mod:folded_cloth`; placeable block item that is also a processing output.
- Source model/texture: tailoring pack `fabric_stack.json` and `fabric_stack.png`.
- Format/checksum: JSON `462b4e66336f694fd1e7d43a905bdbba3bb3b3b31252e2533c53a61fb00d5c5a`; PNG `e3684fe6d38beaa0b207e8c5a684e287ee98ce70405353355d689c170ecd4f5e`.
- Import status: `PLACEHOLDER` — the purchased fabric-stack model/texture was normalized as temporary folded-cloth art in Milestone 2.
- Final targets: `BlockRegistry.FOLDED_CLOTH`, `ItemRegistry.FOLDED_CLOTH`, `DecorativePropBlock`, and `assets/britannia_mod/{blockstates/folded_cloth.json,models/block/new_assets/folded_cloth.json,models/item/folded_cloth.json,textures/block/new_assets/folded_cloth.png}` plus localization, loot, and creative-tab registration; later loom recipe data.
- Required behavior: later accept 5 `ball_of_yarn` or 5 `spool_of_thread` for one folded cloth at the loom.
- Dimensions/animation: model bounds 14×12×13 voxels; no animation.
- Collision: shallow fitted stack collision.
- Notes/blockers: no separate inventory-only item is needed unless later UI constraints require one.

### 16. Loom

- Requested asset: 2-block-wide × 3-block-high loom.
- Proposed registry ID/category: `britannia_mod:loom`; structural multiblock with a narrowly authorized conversion interaction.
- Candidate source model/texture: tailoring pack `loom.json` and `loom.png`; `standing_loom` is a smaller alternate, while `tailoring_station` is three blocks wide.
- Format/checksum: loom JSON `c26734be38e00153790ff3eac416501e6ed3c9ef72ff9f9c708da9b245328966`; PNG `b8b30210c695878d61732fbb7eec613ad11af4f3e1244a77064973a3c6b5f5fa`.
- Import status: `PLACEHOLDER` — temporary purchased art was re-authored and implemented in Milestone 3.
- Final targets: shared authoritative multiblock block/item implementation, models/textures, registries/localization, empty loot, creative tab, client cutout, and axe-mineability tag. Processing definitions remain deferred.
- Required behavior: structural/decorative in Milestone 3. Later convert 5 balls of yarn to 1 folded cloth or 5 spools of thread to 1 folded cloth; no broader tailoring system.
- Dimensions/animation: re-authored model bounds x `0..32`, y `-16..32`, z `0..16`, exactly 32x48x16 voxels across a 2x1x3 authoritative structure; no animation.
- Collision: loom frame/working area approximation across the declared footprint.
- Notes/blockers: source scaling/re-authoring is complete. Yarn/thread item availability and the duplication-safe conversion interaction remain deferred to the textile-processing milestone.

### 17. Bolt of cloth

- Requested asset: bolt of cloth resource/decoration.
- Proposed registry ID/category: `britannia_mod:bolt_of_cloth`; placeable block item unless later textile design chooses item-only.
- Source model/texture: none. `fabric_stack` is assigned to folded cloth and `fabric_spools` depicts spools, not a cloth bolt.
- Source format/checksum: none.
- Import status: `PLACEHOLDER` — an unmistakable magenta/black code-authored cloth-roll model was added in Milestone 2.
- Final targets: `BlockRegistry.BOLT_OF_CLOTH`, `ItemRegistry.BOLT_OF_CLOTH`, `DecorativePropBlock`, `assets/britannia_mod/{blockstates/bolt_of_cloth.json,models/block/new_assets/bolt_of_cloth.json,models/item/bolt_of_cloth.json}`, localization, loot, and creative-tab registration.
- Required behavior: no processing contract yet.
- Dimensions/animation/collision: small placeable prop; no animation; fitted placeholder collision.
- Notes/blockers: replacement art is still required; preserve the final registry ID when replacing the placeholder.

### 18. Spinning wheel and textile outputs

#### Spinning wheel

- Proposed registry ID/category: `britannia_mod:spinning_wheel`; functional processing block/structure.
- Source model/texture: none. `standing_loom` and `tailoring_station` are not spinning wheels and will not be silently relabeled.
- Source format/checksum: none.
- Import status: `PLACEHOLDER` — functional one-cell block implemented in Milestone 8 with unmistakable magenta/black code-authored replacement art.
- Final targets: immediate processing block, standard resources/registries/localization/loot. No hidden inventory or block entity is required by the approved interaction contract.
- Required behavior: wool → `ball_of_yarn`; cotton/flax/future silk → `spool_of_thread`; server-authoritative and duplication-safe.
- Dimensions/animation/collision: deferred to placeholder/final art; animation not required by current contract.
- Notes/blockers: the one-cell footprint is explicitly temporary because no source art exists; final art may justify a later footprint migration.

#### Ball of yarn

- Proposed registry ID/category: `britannia_mod:ball_of_yarn`; processing item.
- Source model/texture: none.
- Import status: `PLACEHOLDER` — final ID and processing behavior implemented in Milestone 8 using a conspicuous vanilla-texture placeholder model.
- Final targets: `ItemRegistry`, final item model/texture, localization/creative tab.
- Required behavior: output from wool and valid 5-item loom input.
- Dimensions/animation/collision: inventory item, no block collision/animation.
- Notes/blockers: final dedicated icon is still required.

#### Spool of thread

- Proposed registry ID/category: `britannia_mod:spool_of_thread`; processing item.
- Candidate source: tailoring pack `fabric_spools.json`/`.png`.
- Format/checksum: JSON `d1024c6b217116bce9069a2b46c0a014635131fab56809b5cd9652f65aed1b78`; PNG `d94ac30cf6e7c7b2e0e3f70e6bfd9f72251500e628570bf193e4fda6fe6fbfcc`.
- Import status: `PLACEHOLDER` — final item ID and processing behavior implemented in Milestone 8 using a conspicuous vanilla-texture placeholder model. The oversized purchased prop was deliberately not used as the handheld model.
- Final targets: `ItemRegistry`, final item model/texture/localization/creative tab; the optional placeable decoration remains unauthorized.
- Required behavior: output from cotton/flax/future silk and valid 5-item loom input.
- Dimensions/animation/collision: source prop is about 17×32×15 voxels; no animation. Item-only form has no collision.
- Notes/blockers: avoid making a two-block-tall prop the default handheld item model without transform review.

#### Silk textile input

- Proposed registry ID/category: `britannia_mod:silk`; future textile material item.
- Source model/texture: none; tracked fabric material/palette definitions named `silk` are data definitions, not inventory items.
- Import status: `MISSING`.
- Proposed targets: future `ItemRegistry` entry and item resources only when silk acquisition is defined.
- Required behavior: spinning-wheel input distinct from `britannia_mod:spiders_silk`.
- Dimensions/animation/collision: inventory item.
- Notes/blockers: Silk is a distinct future textile material whose creation/acquisition is explicitly deferred; it must not alias `britannia_mod:spiders_silk`.

### 19. Display cases

- Requested asset: decorative 1×2 case segments with single/end/middle/corner neighbor visuals.
- Proposed registry ID/category: `britannia_mod:display_case`; neighbor-aware two-block decorative family.
- Source model/texture: none. No `display`, `case`, or `showcase` candidate appears in the raw tree, outer archives, or nested archives.
- Source format/checksum: none.
- Import status: `PLACEHOLDER` — final ID, atomic two-cell structure, and neighbor-aware placeholder visuals implemented in Milestone 9.
- Final targets: `DisplayCaseBlock`, shared transactional multiblock placement, four root connection flags, multipart side models, standard registries/resources/localization/empty loot, axe mineability, and client cutout rendering.
- Required behavior: visual connection only; no storage and no displayed-item inventory.
- Dimensions/animation: one block per segment, two blocks high; no animation.
- Collision: fitted glass/frame case shape, recomputed with neighbors where needed.
- Notes/blockers: code-authored magenta/black/glass art is deliberately conspicuous replacement art. Each absent neighbor supplies a side pane, so independent/end/middle/corner—and deterministic T/cross layouts—need no duplicate registry IDs. Live visual review remains required.

### 20. Pewter mug

- Proposed registry ID/category: `britannia_mod:pewter_mug`; small placeable decorative block item.
- Source model/texture/format/checksum: none.
- Import status: `PLACEHOLDER` — an unmistakable code-authored tabletop model was added in Milestone 2.
- Final targets: `BlockRegistry.PEWTER_MUG`, `ItemRegistry.PEWTER_MUG`, `DecorativePropBlock`, `assets/britannia_mod/{blockstates/pewter_mug.json,models/block/new_assets/pewter_mug.json,models/item/pewter_mug.json}`, localization, loot, creative-tab, and pickaxe-tag entries.
- Required behavior: decoration only.
- Dimensions/animation/collision: small tabletop prop, no animation, small fitted collision.
- Notes/blockers: replacement art is still required; preserve the final registry ID when replacing the placeholder.

### 21. Kettle

- Proposed registry ID/category: `britannia_mod:kettle`; placeable decorative block item.
- Source model/texture/format/checksum: none. Market `pot`/`hangingpot` assets are not identified as kettles.
- Import status: `PLACEHOLDER` — an unmistakable code-authored tabletop model was added in Milestone 2.
- Final targets: `BlockRegistry.KETTLE`, `ItemRegistry.KETTLE`, `DecorativePropBlock`, `assets/britannia_mod/{blockstates/kettle.json,models/block/new_assets/kettle.json,models/item/kettle.json}`, localization, loot, creative-tab, and pickaxe-tag entries.
- Required behavior: decoration only; no cooking.
- Dimensions/animation/collision: tabletop prop, no animation, fitted collision.
- Notes/blockers: replacement art is still required; preserve the final registry ID when replacing the placeholder.

### 22. Plates and silverware

- Proposed registry ID/category: `britannia_mod:plates_and_silverware`; combined place-setting decorative block item unless later art proves separate pieces.
- Source model/texture/format/checksum: none. The farmer `fork` is an agricultural pitchfork and is not silverware.
- Import status: `PLACEHOLDER` — one unmistakable code-authored combined place-setting model was added in Milestone 2.
- Final targets: `BlockRegistry.PLATES_AND_SILVERWARE`, `ItemRegistry.PLATES_AND_SILVERWARE`, `DecorativePropBlock`, `assets/britannia_mod/{blockstates/plates_and_silverware.json,models/block/new_assets/plates_and_silverware.json,models/item/plates_and_silverware.json}`, localization, loot, creative-tab, and pickaxe-tag entries.
- Required behavior: decoration only.
- Dimensions/animation/collision: shallow tabletop prop, no animation, very low fitted collision.
- Notes/blockers: replacement art is still required; keep one combined final ID unless the owner later authorizes separate pieces.

## Special-check conclusions

- White ibis model/texture: found; animated Blockbench source is usable.
- Scarlet ibis texture: missing; must be derived or placeholder-generated in Milestone 6.
- Six merchant-cart colors: not found; only red, purple, and a different base wagon exist.
- Display-case states: no source models found for single/end/middle/corner.
- Crate sizes: only `crate` and `large_crate` are distinct single-crate geometries; medium is missing. Stack models are not size substitutes.
- Loom: model/texture found, but geometry is 2 blocks high rather than the requested 3.
- Spinning wheel: missing; `standing_loom` is not a spinning wheel.
- Fountain: source matches the requested 2×2×3 envelope after origin normalization.
- Training dummy: animated source exists, but its 20×44×14-voxel punching-bag geometry does not match the requested 32×48 multiblock silhouette.
- Moongate: completed in Milestone 10; raw 32×43×32 bounds were re-authored to the owner-requested 16×32×16 visual on one logical block.
- Water well/scarecrow/dress form: complete source candidates match their approximate two-block-height requirements; the well was normalized to its final 1x2x2 Milestone 5 footprint.
- Ladder: the approximately 2.25-block source was re-authored to a full 3-block/48-voxel visual in Milestone 5.

## Models requiring dimensional re-authoring

| Requested asset | Source model | Measured source bounds | Required envelope | Re-authoring required |
|---|---|---|---|---|
| Moongate | `moongate\portal.bbmodel` | 32×43×32 voxels (x/y/z) | One logical block with a 32-voxel-high visual | Completed in Milestone 10: scaled/re-originated to x/z `0..16` and y `0..32`; retained the legacy top registry IDs as invisible self-cleaning compatibility aliases without changing teleport behavior |
| Training dummy | `training_dummy.zip::.../fv_punching_bag_gray.bbmodel` | 20×44×14 voxels | 32×48-voxel visible structure occupying 2×3 blocks | Completed in Milestone 7: transformed to exact 32×48×14 bounds, retimed to a one-second one-shot hit, and paired with separate atomic 2×3 occupancy/collision |
| Ladder | `shizuart_farmer_props.zip::.../farmer_stepladder.json` | about 18×36×32 voxels | 3 blocks/48 voxels high, double-sided and climbable | Completed in Milestone 5: extended/rebuilt to x `0..16`, y `-16..32`, z `0..16`, rendered from the middle structure cell, with independent double-sided traversal collision |
| Loom | `Nexo Assets - Tailoring Station.zip::.../loom.json` | about 32×32×36 voxels | 2 blocks wide × 3 blocks/48 voxels high | Extend/rebuild the frame to 48-voxel height and normalize it to the authoritative 2×3 multiblock root |

The fountain needs origin normalization but already fits its requested 2×2×3 envelope. The water well, scarecrow, and dress form fit their approximate two-block-height contracts. Large-crate art crosses cell bounds and therefore needs multiblock placement/collision authoring, but no owner-specified voxel envelope requires visual re-scaling.

## Placeholder proposal for later milestones

No placeholders were created in Milestone 1. Proposed placeholder work:

1. Medium crate.
2. Blue, green, yellow, and white merchant-cart art using the approved palette.
3. Scarlet ibis only if safe recoloring cannot be produced from white art.
4. Moonglow bush and fern unless generic candidates receive art approval.
5. Bolt of cloth.
6. Spinning wheel.
7. Ball of yarn and a dedicated spool-of-thread item representation.
8. Future silk inventory item when acquisition is designed.
9. Display-case single/end/middle/corner set.
10. Pewter mug.
11. Kettle.
12. Combined plates-and-silverware place setting.
13. Reworked/placeholder geometry for moongate, training dummy, ladder, and loom where the found source does not meet required bounds.

## Unassigned source packs

- `blood` folder/ZIP: no requested Milestone 1 asset match.
- `elitecreatures-medieval_market_decoration_v2.zip`: contains alternate crates/market props, but no clean small/medium/large size family and no other requested exact match.
- Garden Essentials and ShizuArt plant packs contain many generic plants/bushes; only candidate mappings above are assigned.
- Extra barrels, mixed crate stacks, market furniture, farmer props, plants, and alternate training-dummy colors remain unassigned and must not be imported opportunistically.
