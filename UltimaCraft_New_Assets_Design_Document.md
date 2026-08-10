# UltimaCraft New Assets Project — Design Document

## 1. Purpose

This project adds a large batch of imported and newly completed assets to UltimaCraft while preserving existing mod architecture, gameplay conventions, and multiplayer/server compatibility.

The source asset intake directory is:

`C:\projects\britannia\raw fiels\models to import`

The spelling of `raw fiels` is intentional in this specification because that is the path supplied by the project owner. The implementation agent must verify the path on disk rather than silently correcting it.

The implementation must be performed in an isolated Git worktree and branch named:

- Branch: `new-assets`
- Worktree directory: `C:\projects\britannia\new-assets`
- Base branch: `patch-18`
- Main working copy to leave untouched: `C:\projects\britannia\britannia_mod`

The project is intentionally milestone-driven. Asset intake, data normalization, gameplay logic, rendering, collision, networking, and live-game validation must not be collapsed into one large change.

---

## 2. Primary Goals

1. Inventory the raw model/texture source directory and determine which requested assets already exist.
2. Import usable source assets without destroying or modifying the raw source files.
3. Create safe placeholders for missing models or textures so every requested registry entry can be implemented and tested.
4. Add the requested gameplay behavior for assets that are more than decoration.
5. Reuse existing UltimaCraft systems wherever possible instead of building parallel infrastructure.
6. Keep every milestone independently reviewable and reversible.
7. Produce a documented mapping from every source asset to its final in-mod resource.
8. Ensure the final feature works in single-player and on the UltimaCraft dedicated server.

---

## 3. Architectural Principles

### 3.1 Reconnaissance before implementation

Before adding any registry object, the agent must inspect the existing codebase for precedents involving:

- entity registration and regional spawn caps;
- Service NPC or city/region population rules;
- GeckoLib or other animated entity/block systems;
- multi-block placement and teardown;
- containers and menus;
- water acquisition;
- Adventure-mode custom placement/destruction rules;
- tool-based block breaking;
- plant/decorative block registration;
- crafting/processing machines;
- item conversion recipes;
- creative tabs;
- localization;
- loot tables;
- datagen;
- networking;
- neighboring-block state recomputation;
- existing moongate behavior.

Existing conventions are authoritative unless they conflict with an explicit requirement in this document.

### 3.2 Raw assets are read-only inputs

The folder `C:\projects\britannia\raw fiels\models to import` is an intake source, not a working directory.

The agent must:

- never move or delete source files;
- never rename source files in place;
- copy approved files into the mod resource tree;
- record original filename/path and final target;
- record SHA-256 where practical;
- preserve alpha channels and texture dimensions unless there is a documented technical reason not to;
- avoid overwriting existing approved UltimaCraft assets without owner approval.

### 3.3 Placeholder-first when source art is missing

If a requested model or texture does not exist, implementation should not stall.

Create a clearly recognizable temporary placeholder that:

- uses the correct final registry ID and resource path;
- respects the intended block/entity dimensions;
- has obvious placeholder visuals;
- is listed in the asset manifest as `PLACEHOLDER`;
- can later be replaced without changing gameplay code or registry IDs.

Placeholder creation must not be presented as final art.

### 3.4 Functional assets must not be implemented as decoration

The following require behavior in addition to models:

- Ibis bird
- Training dummy
- Large crate
- Medium crate
- Small crate
- Water well
- Ladder
- Spinning wheel
- Display cases
- Moongate, if existing UltimaCraft moongate infrastructure provides behavior that should be reused

### 3.5 Multi-block objects need one authoritative root

For 2-block, 3-block, or larger structures, prefer the existing UltimaCraft multi-block pattern if one exists.

Where no precedent exists, use an authoritative root block/block entity plus subordinate parts so that:

- placement is atomic;
- partial placement cannot occur;
- breaking any valid part removes the structure correctly;
- container or machine state is stored only once;
- block drops occur once;
- server/client state remains synchronized;
- collision/selection shapes are predictable.

---

## 4. Asset Intake Manifest

The project should create and maintain:

`docs/new-assets/ASSET_IMPORT_MANIFEST.md`

Each requested asset should have an entry containing:

- Requested asset name
- Proposed registry ID
- Category
- Source model path
- Source texture path(s)
- Source file format
- Source SHA-256 if available
- Import status: `FOUND`, `PARTIAL`, `MISSING`, `PLACEHOLDER`, `IMPORTED`, `VALIDATED`
- Final Java/resource target paths
- Required behavior
- Dimensions
- Animation requirement
- Collision requirement
- Notes/blockers

The manifest is the source of truth for asset completion.

---

## 5. Requested Asset Specifications

## 5.1 Ibis Bird

Proposed registry concept: `ibis`

Variants:

- White
- Scarlet

Requirements:

- Both variants are the same species/entity unless repository architecture strongly favors separate entity types.
- The ibis must spawn naturally only in the Jhelom region.
- Maximum regional population: 15 ibis total across both color variants unless the existing regional population system requires a different interpretation.
- The agent must inspect the raw intake folder for an ibis model and white texture.
- If the scarlet texture is absent, derive it from the white texture while preserving:
  - UV layout;
  - alpha/transparency;
  - shading;
  - eye/beak/leg detail where possible.
- The derived scarlet texture must be documented as generated, not imported.
- Variant choice should be synchronized and persistent.
- Do not create global biome spawning if the existing server uses region-based spawn control.

Preferred implementation:

- one entity type;
- a persistent variant enum/data value;
- variant-specific texture selection.

Validation:

- cannot naturally spawn outside Jhelom;
- Jhelom cap is enforced;
- both variants render correctly;
- reconnect/reload preserves variant.

---

## 5.2 Moongate

The agent must first search for existing UltimaCraft moongate code and any current Dungeon Shame moongate implementation.

Do not invent a new teleportation system before reconciling with existing behavior.

If an existing system is present:

- import the new model/texture into that system where appropriate;
- preserve established destination/configuration behavior;
- do not break existing gates.

If the requested asset is purely visual and no reusable moongate implementation exists, import/register the asset as a nonfunctional placeholder and document the missing gameplay contract for owner review.

---

## 5.3 Merchant Carts

Six color variants.

Requirements:

- inspect the raw directory for cart models and all six texture variants;
- prefer one shared geometry with texture/model variants;
- avoid six duplicated Java block classes unless existing architecture requires it;
- colors must have stable registry/resource names;
- carts should use appropriate collision rather than a full cube if geometry is open;
- functionality beyond decoration is not implied unless an existing merchant-cart system is discovered.

If some colors are missing, create placeholders and mark them clearly in the manifest.

---

## 5.4 Training Dummy

Physical dimensions:

- 3 blocks high
- 2 blocks wide

Gameplay requirements:

Using the dummy can raise:

- Swordsmanship
- Mace Fighting
- Fencing

Skill gain limit:

- training from the dummy may raise the relevant weapon skill only up to 25.0;
- it must never train that weapon skill above 25.0.

Timing:

- after a valid strike, that player must wait 3 seconds before another training attempt on the dummy;
- cooldown should be server-authoritative.

Tactics:

- each valid training strike has a small chance to train Tactics;
- the exact probability should reuse any existing UltimaCraft/UO skill-gain conventions if present;
- if no suitable precedent exists, isolate the chance in a named constant/config value and document the chosen default.

Must NOT train:

- Wrestling
- Anatomy
- Lumberjacking

Weapon durability:

- striking a training dummy must not reduce weapon durability.

Weapon classification:

- detect the equipped weapon and map it to Swordsmanship, Mace Fighting, or Fencing using existing UltimaCraft weapon/skill classification if available;
- do not rely on fragile display-name matching.

Animation:

- the dummy must visibly animate when struck;
- the animation should be server-triggered and synchronized to nearby clients;
- animation must not create repeated skill attempts.

Interaction:

- only legitimate player strikes should trigger training;
- prevent rapid-click, packet, off-hand, or multi-hit duplication from bypassing cooldown.

Recommended state:

- multi-block root;
- transient animation state;
- per-player cooldown stored in an appropriate server-side capability/component/map, not as a single global dummy cooldown if that would incorrectly block other players.

---

## 5.5 Fountain

Dimensions:

- 2 blocks long
- 2 blocks wide
- 3 blocks high

Requirements:

- imported or placeholder multi-block geometry;
- collision must follow the visible structure as closely as practical;
- placement must fail if any occupied position is blocked;
- breaking should remove all parts and drop once;
- decorative unless the existing model/system explicitly implies water interaction.

---

## 5.6 Moonglow Bushes

Requirements:

- inspect source assets;
- register using existing plant/decorative foliage patterns;
- use proper transparency/render layer;
- avoid full-cube collision;
- if the codebase has a Moonglow regional-decoration system, integrate with it only after recon.

No world-generation requirement is implied by this project unless discovered as an existing convention or later authorized.

---

## 5.7 Sandstone Brick Walls

Requirements:

- use the established Minecraft/UltimaCraft wall-block behavior if possible;
- connect correctly to neighboring compatible blocks;
- import texture/model resources from source when present;
- add appropriate block/item tags and loot.

Do not implement these as non-connecting decorative full cubes if a wall-block implementation is suitable.

---

## 5.8 Globe

Single decorative asset unless source geometry proves larger than one block.

Requirements:

- model import or placeholder;
- suitable non-full-cube collision;
- creative tab and localization.

---

## 5.9 Crates

Assets:

- Small crate
- Medium crate
- Large crate

All are containers.

Requirements:

- inspect dimensions/models before deciding whether each is one block or multi-block;
- use existing container/menu infrastructure;
- server-authoritative inventory;
- persistent contents;
- suitable container slot counts based on existing UltimaCraft crate/chest conventions where available;
- opening/closing must synchronize correctly in multiplayer;
- breaking behavior must follow existing container-drop conventions;
- prevent duplication when a multi-block crate is broken.

Do not invent decorative-only crates.

---

## 5.10 Water Well

Dimensions:

- 2 blocks high

Behavior:

- player can obtain water from the well.

The exact water container/item interaction must be discovered from existing UltimaCraft systems before implementation. Examples to inspect include buckets, pitchers, bottles, custom water containers, or farming hydration tools.

Requirements:

- use existing water-bearing items if available;
- do not create a parallel water economy without owner authorization;
- interaction must be server-authoritative;
- do not consume or duplicate containers incorrectly;
- place/break as a coherent two-block object.

---

## 5.11 Ladder

Dimensions:

- 3 blocks high

Geometry/behavior:

- double-sided;
- visually/stucturally more like narrow stairs than a flat vanilla ladder;
- must actually allow climbing;
- Adventure-mode players are allowed to place it on the ground;
- can be destroyed with an axe.

Requirements:

- multi-block placement;
- ground-supported placement;
- both sides climbable;
- correct collision and traversal;
- custom Adventure-mode placement allowance using the safest existing project pattern;
- axe is the intended harvesting/destruction tool;
- no accidental unrestricted Adventure-mode block placement system.

Breaking any segment should resolve the whole ladder according to the selected multi-block pattern.

---

## 5.12 Scarecrow

Dimensions:

- 2 blocks high

Requirements:

- decorative multi-block object unless existing farming behavior is discovered;
- imported model or placeholder;
- appropriate collision;
- creative tab/localization/loot.

No crop-protection mechanic is implied by this project.

---

## 5.13 Fern

Requirements:

- small foliage/plant block;
- transparency;
- replaceable/non-full collision behavior based on existing plant conventions;
- suitable placement substrate/tags based on repository precedent;
- no unrequested world generation.

---

## 5.14 Dress Form

Dimensions:

- 2 blocks high

Requirements:

- decorative multi-block object unless existing equipment-display behavior is discovered and clearly reusable;
- do not silently turn it into an armor stand;
- use imported model or placeholder.

---

## 5.15 Folded Cloth

Decorative item/block.

The intake milestone must determine whether the source asset is intended as:

- inventory item;
- placeable decoration;
- block model;
- or both.

Prefer existing UltimaCraft decorative-item conventions.

---

## 5.16 Loom

Dimensions:

- 2 blocks wide
- 3 blocks high

This project specifies the physical asset but does not explicitly define a loom recipe/process.

Requirements:

- implement the structure and registry;
- inspect existing tailoring/textile systems;
- if a loom mechanic already exists, integrate only if behavior is clearly established;
- otherwise keep this milestone visual/structural and document gameplay as deferred rather than inventing recipes.

---

## 5.17 Bolt of Cloth

The intake milestone must determine whether this is:

- an item;
- a placeable decorative object;
- a tailoring resource;
- or multiple representations.

Reuse existing textile item conventions and do not create duplicate cloth concepts if equivalent items already exist.

---

## 5.18 Spinning Wheel

Required conversion behavior:

- wool -> `ball of yarn`
- cotton -> `spool of thread`
- flax -> `spool of thread`
- silk -> `spool of thread`

Requirements:

- inspect existing item IDs for wool, cotton, flax, silk, yarn, thread;
- reuse existing IDs where available;
- only add missing output items if necessary;
- server-authoritative conversion;
- no duplication;
- conversion quantities/timing should follow an existing crafting/processing precedent where possible;
- if no precedent exists, isolate values in recipe/data definitions rather than hard-coding them deep in UI logic.

The physical dimensions must be determined from source geometry during intake unless the repository already has a standard.

---

## 5.19 Display Cases

Dimensions:

- each logical display-case segment occupies 1 block horizontally
- 2 blocks high

Requirements:

- can stand independently;
- neighboring cases combine visually;
- model selection changes based on neighboring display-case blocks;
- supports straight runs and turns/corners;
- the result should behave conceptually like stairs/fences where neighbor placement changes the rendered state.

Recommended implementation approach:

- one display-case block family;
- persistent facing/orientation if required by art;
- horizontal connection state derived from north/east/south/west neighbors;
- model selection via blockstate/multipart or a bounded connection enum;
- lower/upper half for the two-block height;
- recompute neighbors on placement/removal;
- avoid a separate Java class per visual combination.

Required visual states should be determined from available source models. At minimum support:

- independent/single;
- end/edge;
- middle/straight;
- corner/turn.

If the art contains left/right or inner/outer variants, map those explicitly.

Functional display behavior is not specified. Do not add storage or item-display inventory unless an existing system clearly provides it or the owner later requests it.

---

## 5.20 Pewter Mug

Decorative item/placeable asset as supported by source and existing conventions.

Use a small collision shape if placeable.

---

## 5.21 Kettle

Decorative item/placeable asset unless an existing cooking system provides an obvious integration.

No cooking behavior is implied.

---

## 5.22 Plates and Silverware

Decorative asset set.

The intake manifest must document whether source art represents:

- one combined place setting;
- separate plate and silverware models;
- or multiple variants.

Do not invent additional variants merely to fill the category.

---

## 6. Naming and Registry Strategy

Final names should follow existing UltimaCraft naming conventions discovered during Milestone 0.

When no precedent requires otherwise, prefer snake_case IDs such as:

- `ibis`
- `merchant_cart_<color>`
- `training_dummy`
- `fountain`
- `moonglow_bush`
- `sandstone_brick_wall`
- `globe`
- `small_crate`
- `medium_crate`
- `large_crate`
- `water_well`
- `ladder`
- `scarecrow`
- `fern`
- `dress_form`
- `folded_cloth`
- `loom`
- `bolt_of_cloth`
- `spinning_wheel`
- `display_case`
- `pewter_mug`
- `kettle`
- `plates_and_silverware`

Do not rename existing equivalent registry IDs merely to match this suggestion.

---

## 7. Resource/Model Import Rules

The agent must inspect supported source formats and convert only when necessary.

For each imported model:

1. verify coordinate scale;
2. verify Minecraft block origin/pivot;
3. verify texture paths;
4. verify UVs;
5. verify transparency;
6. verify face culling;
7. verify render type;
8. verify block/item transform;
9. verify collision separately from visual geometry;
10. verify multi-block dimensions in actual game coordinates.

Do not infer collision directly from complicated decorative geometry if it creates bad gameplay. Collision should be deliberately authored.

---

## 8. Scarlet Ibis Texture Generation

If only the white ibis texture exists, derive a scarlet variant non-destructively.

The generation process should:

- copy the white texture;
- preserve transparent pixels;
- preserve dark outlines/shadows;
- recolor appropriate white plumage regions toward scarlet/red;
- avoid recoloring eyes, beak, legs, or other non-plumage details unless source art clearly requires it;
- preserve image dimensions exactly;
- save to the final scarlet texture resource path;
- record the generated asset in the manifest.

If automated region selection cannot reliably distinguish body detail, generate a conservative placeholder scarlet pass and mark it for owner art review rather than damaging the base texture.

---

## 9. Gameplay and Networking Requirements

All state-changing behavior must be server-authoritative.

This includes:

- skill gain;
- dummy cooldowns;
- crate inventories;
- water acquisition;
- spinning-wheel conversion;
- ibis variant/spawn rules;
- multi-block placement/breaking;
- display-case neighbor state if synchronized state is used.

Clients may render animations and UI but must not be trusted to grant items, train skills, or bypass cooldowns.

---

## 10. Creative Tab, Localization, Loot, and Tags

Every player-obtainable asset must be reviewed for:

- Creative Tab placement;
- English localization;
- block item registration where applicable;
- loot table;
- correct harvest/tool tags;
- block tags;
- item tags;
- render layer;
- recipe/data registration where applicable.

Special notes:

- Ladder must identify axe behavior.
- Containers must not duplicate contents on break.
- Plant assets need appropriate transparency and replaceability.
- Multi-block structures must drop once.

---

## 11. Validation Strategy

Every milestone should use the strongest available validation appropriate to its scope.

Expected layers:

- compile;
- focused unit/game tests where infrastructure exists;
- datagen/resource validation;
- clean client launch;
- clean dedicated-server launch;
- live placement/render/collision test;
- multiplayer interaction test for functional assets;
- save/reload test for persistent state.

Visual assets must be tested in-game. JSON validation alone is insufficient.

---

## 12. Project Documentation

Maintain:

- `docs/new-assets/ASSET_IMPORT_MANIFEST.md`
- `docs/new-assets/IMPLEMENTATION_LOG.md`
- `docs/new-assets/LIVE_TEST_CHECKLIST.md`

Each milestone report should include:

- branch;
- HEAD;
- files changed;
- requested assets affected;
- tests run;
- test result;
- screenshots/live checks still required;
- placeholders introduced;
- blockers;
- whether commit authorization is being requested.

---

## 13. Explicit Non-Goals

Unless later authorized, this project must not:

- redesign the global skill system;
- redesign regional spawning;
- add world generation for all decorative assets;
- invent a new moongate destination/network system;
- invent tailoring recipes for the loom;
- turn the dress form into an armor stand;
- add scarecrow crop protection;
- add kettle cooking mechanics;
- add display-case inventory/storage;
- overwrite raw source files;
- merge to `patch-18`;
- push to remote;
- modify other agents' worktrees.

---

## 14. Completion Definition

The project is complete only when:

- every requested asset appears in the manifest;
- every asset is either imported or explicitly marked as a placeholder;
- functional assets satisfy the required gameplay behavior;
- all relevant assets are registered and obtainable as intended;
- Jhelom ibis spawning/cap is validated;
- training dummy skill rules and 3-second cooldown are validated;
- crate persistence is validated;
- water well interaction is validated;
- ladder Adventure-mode placement/climbing/axe destruction is validated;
- spinning-wheel conversions are validated;
- display-case combining logic is validated;
- clean client and dedicated server builds pass;
- live-game visual/collision checks pass;
- owner has approved final milestone closure.
