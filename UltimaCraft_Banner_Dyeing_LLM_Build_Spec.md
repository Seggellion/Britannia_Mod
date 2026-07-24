# UltimaCraft Banner & Material-Aware Dyeing System
## LLM Implementation Playbook and Build Specification

**Document type:** Executable implementation playbook for a coding LLM  
**Companion document:** `UltimaCraft_Banner_and_Dyeing_System_Design.md`  
**Status:** Draft ready for repository-specific adaptation  
**Version:** 0.1  
**Target feature:** 33 configurable banners, material-aware dyeing, dye tubs, layered rendering, multi-block placement, mounts, crafting integration, and persistence

---

# 1. Purpose

This document tells a coding LLM how to build the UltimaCraft banner and dyeing feature **milestone by milestone**.

It is not merely an architectural overview. It defines:

- The order of implementation.
- The responsibilities of each subsystem.
- The files and registries that should exist.
- A repository-discovery phase so the LLM does not guess the mod loader or APIs.
- A placeholder catalogue for all 33 banners.
- Test and acceptance criteria for every milestone.
- Required checkpoints, commits, and handoff reports.
- Rules for stopping when repository facts are missing.
- A content workflow that allows final banner art and dimensions to be filled in later.

The coding LLM should treat this document as the implementation authority unless it conflicts with the existing UltimaCraft codebase. When a conflict is found, the LLM must document it and adapt to existing project conventions instead of forcing a new architecture.

---

# 2. Non-Negotiable Product Requirements

The finished feature must support all of the following:

1. There are 33 banner definitions.
2. Banner definitions are data-driven.
3. Banners can be made from cotton, wool, linen, or silk.
4. Admin-generated banners default to cotton.
5. Crafted banners derive their material from the crafting process.
6. A dye item can be loaded into a dye tub.
7. A loaded dye tub stores a pigment identity.
8. A banner can be dyed using the loaded tub.
9. The material determines the final displayed colour.
10. The same pigment can produce different output colours on different materials.
11. Explicit pigment-to-material mappings take priority.
12. When no explicit mapping exists, the system finds the closest compatible material colour.
13. Closest-colour matching uses a perceptual colour space, preferably OKLab.
14. A dyeing preview or confirmation view shows the resolved result.
15. Banners can be re-dyed indefinitely.
16. A banner's material, colour, design, and mount persist between item and placed-block forms.
17. Banner artwork supports a tintable fabric layer and a static non-tintable layer.
18. Banner widths include one, two, and three blocks.
19. Banners can support wall-parallel placement, wall-perpendicular placement, or both.
20. Mount hardware supports brass and iron/metal variants.
21. Multi-block banners behave as one object.
22. The server is authoritative for dyeing, placement, state mutation, and drops.
23. New banners, pigments, colours, and materials can be added without editing a large central switch statement.
24. Missing content fails visibly and safely rather than crashing worlds or silently losing state.

---

# 3. LLM Operating Contract

The implementing LLM must follow these rules during every milestone.

## 3.1 Inspect before editing

Before creating code, inspect:

- The build system.
- Minecraft version.
- Mod loader or server framework.
- Mapping set.
- Java or Kotlin version.
- Existing registration patterns.
- Existing item data or component system.
- Existing networking layer.
- Existing screen/menu conventions.
- Existing block entity conventions.
- Existing data-generation tools.
- Existing test framework.
- Existing UltimaCraft material and blacksmithing systems.
- Existing naming, package, logging, and error-handling conventions.

Do not invent APIs from memory when the repository can answer the question.

## 3.2 Respect existing architecture

Prefer extending existing UltimaCraft services and abstractions.

Examples:

- Reuse the existing material identity type if blacksmithing already has one.
- Reuse existing data component or NBT helpers.
- Reuse the established packet registration system.
- Reuse existing admin command helpers.
- Reuse existing recipe and crafting-material infrastructure.
- Reuse existing test utilities and game-test patterns.

Create a new abstraction only when the existing code cannot reasonably support the feature.

## 3.3 Keep milestones atomic

Each milestone must:

- Have one clear purpose.
- Keep the project compiling.
- Include tests or a documented manual verification procedure.
- Avoid unrelated refactors.
- End with a short implementation report.
- End with one clean Git commit when Git access is available.

Do not begin the next milestone while the current milestone has failing tests, compilation errors, unresolved data validation errors, or known state-loss bugs.

## 3.4 Do not hide uncertainty

When required information is unavailable:

1. Search the repository.
2. Search the companion design document.
3. Record the missing fact in `OPEN_QUESTIONS.md`.
4. Use a reversible placeholder only when it cannot corrupt saved data.
5. Stop and ask for clarification if continuing would create incompatible public APIs, saved-data formats, or asset conventions.

## 3.5 No fake completion

The LLM must not mark a milestone complete when:

- Tests were not run.
- The build did not succeed.
- Placeholder code throws `UnsupportedOperationException` on the core path.
- A client-only implementation has no server validation.
- State is not preserved through item-to-block and block-to-item conversion.
- The 33-entry catalogue contains fewer or more than 33 active entries.
- Generated placeholder assets are missing from packaged resources.
- A manual test was required but not performed or clearly reported as unperformed.

## 3.6 Keep a build log

Create or update:

```text
docs/banner-dyeing/IMPLEMENTATION_LOG.md
```

For each milestone, record:

- Date.
- Commit hash, if available.
- Files added or changed.
- Tests run.
- Results.
- Decisions.
- Known limitations.
- Next milestone.
- Any deviation from this playbook.

---

# 4. Repository Tokens

This document uses tokens until Milestone 0 resolves the actual repository layout.

```text
<MOD_ID>              Example: ultimacraft
<NAMESPACE>           Example: com.example.ultimacraft
<SOURCE_ROOT>         Main Java/Kotlin source root
<RESOURCE_ROOT>       Main resource root
<TEST_ROOT>           Unit-test source root
<GAMETEST_ROOT>       Game/integration test root, if present
<GENERATED_ROOT>      Data-generation output root, if present
<DOCS_ROOT>           Project documentation root
```

After repository discovery, create:

```text
docs/banner-dyeing/PROJECT_FACTS.md
```

It must map every token to an actual path and record the framework APIs selected for:

- Registration.
- Item instance data.
- Block entity data.
- Networking.
- Screens and menus.
- Rendering.
- Recipe integration.
- Commands.
- Data generation.
- Tests.

All later milestones must use the resolved paths.

---

# 5. Recommended Module Boundaries

Adapt names to the repository's conventions.

```text
banner/
  api/
  block/
  blockentity/
  client/
    model/
    render/
    screen/
  command/
  data/
  item/
  material/
  mount/
  network/
  placement/
  registry/
  state/
  validation/

dye/
  api/
  client/
  colour/
  item/
  network/
  palette/
  registry/
  service/
  state/
```

The conceptual ownership is:

| Area | Owns |
|---|---|
| Banner definition | Shape, dimensions, orientations, model and texture references |
| Banner instance state | Definition, material, colour, source pigment, mount |
| Fabric material | Natural colour and palette |
| Pigment | The colour intention stored by a dye item or tub |
| Palette | Allowed output colours for one material |
| Dye resolver | Explicit mapping and nearest-colour fallback |
| Dye tub | Stored pigment and optional uses |
| Placement | Anchor, occupied cells, support validation, orientation |
| Renderer | Layered fabric tint, static overlay, mount |
| Crafting | Material and mount assignment |
| Networking | Server-authoritative preview and application |
| Content tools | Placeholder generation and registry validation |

---

# 6. Stable Data Contracts

Use the project's established serialization technology. The examples are JSON-shaped contracts, not a demand to use a specific codec library.

## 6.1 Banner definition

```json
{
  "schema_version": 1,
  "id": "<MOD_ID>:ward_of_serpents",
  "display_name_key": "banner.<MOD_ID>.ward_of_serpents",
  "content_status": "placeholder",
  "source_reference": {
    "page": 2,
    "row": 9,
    "source_label": "Ward of serpents"
  },
  "catalogue_group": "medium",
  "dimensions": {
    "width_blocks": 1,
    "height_blocks": 2,
    "provisional": true
  },
  "supported_orientations": [
    "wall_parallel",
    "wall_perpendicular"
  ],
  "supported_mounts": [
    "<MOD_ID>:brass",
    "<MOD_ID>:iron"
  ],
  "default_mount": "<MOD_ID>:brass",
  "default_material": "<MOD_ID>:cotton",
  "assets": {
    "geometry": "<MOD_ID>:banner/placeholder/medium",
    "fabric_base": "<MOD_ID>:banner/placeholder/fabric_base",
    "dye_mask": "<MOD_ID>:banner/placeholder/dye_mask",
    "static_overlay": "<MOD_ID>:banner/placeholder/static_overlay"
  },
  "placement_profile": "<MOD_ID>:placeholder_medium"
}
```

## 6.2 Banner instance state

```json
{
  "schema_version": 1,
  "banner_definition_id": "<MOD_ID>:ward_of_serpents",
  "material_id": "<MOD_ID>:silk",
  "resolved_colour_id": "<MOD_ID>:silk_ruby",
  "source_pigment_id": "<MOD_ID>:crimson_dye",
  "mount_id": "<MOD_ID>:brass"
}
```

A placed instance additionally stores placement-facing data or derives it from block state:

```json
{
  "orientation": "wall_perpendicular",
  "facing": "north",
  "anchor_position": [0, 0, 0]
}
```

## 6.3 Fabric material

```json
{
  "schema_version": 1,
  "id": "<MOD_ID>:silk",
  "display_name_key": "material.<MOD_ID>.silk",
  "natural_colour_id": "<MOD_ID>:silk_natural",
  "palette_id": "<MOD_ID>:silk",
  "tags": [
    "fabric",
    "fine"
  ]
}
```

## 6.4 Pigment

```json
{
  "schema_version": 1,
  "id": "<MOD_ID>:crimson_dye",
  "display_name_key": "pigment.<MOD_ID>.crimson_dye",
  "reference_srgb": "#A51C30",
  "reference_oklab": [0.48, 0.17, 0.07],
  "tags": [
    "red",
    "common"
  ],
  "rarity": "common"
}
```

## 6.5 Palette entry

```json
{
  "id": "<MOD_ID>:silk_ruby",
  "display_name_key": "colour.<MOD_ID>.silk_ruby",
  "display_srgb": "#A81742",
  "match_oklab": [0.50, 0.18, 0.05],
  "priority": 0,
  "tags": [
    "red",
    "rich"
  ]
}
```

## 6.6 Dye tub state

```json
{
  "schema_version": 1,
  "pigment_id": "<MOD_ID>:crimson_dye",
  "remaining_uses": null
}
```

Store both the source pigment and resolved colour on a dyed banner. This preserves historical appearance if palette matching changes later.

---

# 7. Hand Interaction Contract

The product language describes the dye tub in the right hand and the dye or banner in the left hand.

The recommended implementation uses:

```text
MAIN_HAND = dye tub
OFF_HAND  = dye item or dyeable banner
```

This usually matches right-hand and left-hand play while respecting the player's Minecraft handedness setting.

Do not hard-code physical left/right unless the project explicitly requires physical hand sides.

## 7.1 Load tub

```text
Main hand: dye tub
Off hand: dye item
Use action: load or replace tub pigment
```

## 7.2 Dye item

```text
Main hand: loaded dye tub
Off hand: dyeable banner
Use action: request server preview, open confirmation view, apply after confirmation
```

All mutations are validated again by the server at confirmation time.

---

# 8. Placeholder Asset Strategy

The first systems milestones must not wait for final banner art.

Use shared placeholder assets:

```text
assets/<MOD_ID>/textures/banner/placeholder/fabric_base.png
assets/<MOD_ID>/textures/banner/placeholder/dye_mask.png
assets/<MOD_ID>/textures/banner/placeholder/static_overlay.png
assets/<MOD_ID>/textures/banner/placeholder/missing.png

assets/<MOD_ID>/models/banner/placeholder/large.*
assets/<MOD_ID>/models/banner/placeholder/medium_wall.*
assets/<MOD_ID>/models/banner/placeholder/medium.*
assets/<MOD_ID>/models/banner/placeholder/small.*
assets/<MOD_ID>/models/banner/placeholder/x_small.*
```

Every one of the 33 definitions initially points to the appropriate shared placeholder geometry and textures.

Also create a content manifest:

```text
content/banner_catalogue.yml
```

and a generated implementation report:

```text
content/banner_catalogue_status.md
```

The manifest is the editable source of truth for content production. Runtime definitions may be generated from it or kept synchronized through validation.

## 8.1 Placeholder visual rules

Placeholder art should be unmistakable:

- Neutral grayscale fabric.
- A visible border.
- A small diagnostic mark that remains static.
- No final heraldic artwork.
- A visible size-family marker, where practical.
- No copyrighted art copied from the reference sheet.

The reference sheet is for names, silhouettes, grouping, and production framing. Final in-game assets must be original project assets.

---

# 9. Canonical 33-Banner Placeholder Catalogue

The catalogue below has exactly 33 entries.

Names marked `provisional` may be renamed later, but stable IDs should not be casually changed after players can obtain the items.

The default dimensions are safe implementation placeholders inferred from the catalogue group:

```text
large       -> 3 × 2 blocks
medium-wall -> 2 × 2 blocks
medium      -> 1 × 2 blocks
small       -> 1 × 1 block
x-small     -> 1 × 1 block
```

Every dimension remains provisional until the owner fills in the final values.

| # | Stable placeholder ID | Initial display name | Group | Source | Default dimensions | Name status |
|---:|---|---|---|---|---|---|
| 01 | `large_01` | Large Banner 01 | large | Page 1, row 1 | 3 × 2 | provisional |
| 02 | `large_02` | Large Banner 02 | large | Page 1, row 2 | 3 × 2 | provisional |
| 03 | `large_03` | Large Banner 03 | large | Page 1, row 3 | 3 × 2 | provisional |
| 04 | `large_04` | Large Banner 04 | large | Page 1, row 4 | 3 × 2 | provisional |
| 05 | `large_05` | Large Banner 05 | large | Page 1, row 5 | 3 × 2 | provisional |
| 06 | `large_06` | Large Banner 06 | large | Page 1, row 6 | 3 × 2 | provisional |
| 07 | `medium_wall_01` | Medium Wall Banner 01 | medium-wall | Page 1, row 7 | 2 × 2 | provisional |
| 08 | `medium_wall_02` | Medium Wall Banner 02 | medium-wall | Page 1, row 8 | 2 × 2 | provisional |
| 09 | `medium_wall_03` | Medium Wall Banner 03 | medium-wall | Page 2, row 1 | 2 × 2 | provisional |
| 10 | `medium_wall_04` | Medium Wall Banner 04 | medium-wall | Page 2, row 2 | 2 × 2 | provisional |
| 11 | `medium_wall_05` | Medium Wall Banner 05 | medium-wall | Page 2, row 3 | 2 × 2 | provisional |
| 12 | `joined_wards` | Joined Wards | medium-wall | Page 2, row 4 | 2 × 2 | source-named |
| 13 | `tournament_medium` | Tournament Medium | medium | Page 2, row 5 | 1 × 2 | source-named |
| 14 | `ceremonial_tournament` | Ceremonial Tournament | medium | Page 2, row 6 | 1 × 2 | source-named |
| 15 | `iron_quarter` | Iron Quarter | medium | Page 2, row 7 | 1 × 2 | source-named |
| 16 | `outer_ward` | Outer Ward | medium | Page 2, row 8 | 1 × 2 | source-named |
| 17 | `ward_of_serpents` | Ward of Serpents | medium | Page 2, row 9 | 1 × 2 | source-named |
| 18 | `serpent_guard` | Serpent Guard | medium | Page 2, row 10 | 1 × 2 | source-named |
| 19 | `crossroad_guard` | Crossroad Guard | medium | Page 2, row 11 | 1 × 2 | source-named |
| 20 | `argent_shield` | Argent Shield | medium | Page 3, row 1 | 1 × 2 | source-named |
| 21 | `silver_and_gold_pennon` | Silver and Gold Pennon | small | Page 3, row 2 | 1 × 1 | source-named |
| 22 | `end_01` | End Banner 01 | small | Page 3, row 3 | 1 × 1 | provisional |
| 23 | `end_02` | End Banner 02 | small | Page 3, row 4 | 1 × 1 | provisional |
| 24 | `pennon_of_silver` | Pennon of Silver | small | Page 3, row 5 | 1 × 1 | source-named |
| 25 | `iron_ward` | Iron Ward | small | Page 3, row 6 | 1 × 1 | source-named |
| 26 | `iron_ward_auxiliary` | Iron Ward Auxiliary | small | Page 3, row 7 | 1 × 1 | source-named |
| 27 | `road_guard` | Road Guard | x-small | Page 3, row 8 | 1 × 1 | source-named |
| 28 | `pale_road_guard` | Pale Road Guard | x-small | Page 3, row 9 | 1 × 1 | source-named |
| 29 | `red_crosslets` | Red Crosslets | x-small | Page 3, row 10 | 1 × 1 | source-named |
| 30 | `captains_red_crosslets` | Captain's Red Crosslets | x-small | Page 3, row 11 | 1 × 1 | source-named |
| 31 | `scarlet_court` | Scarlet Court | x-small | Page 3, row 12 | 1 × 1 | source-named |
| 32 | `verdant_court` | Verdant Court | x-small | Page 4, row 1 | 1 × 1 | source-named |
| 33 | `x_small_unnamed_01` | Extra-Small Banner 01 | x-small | Page 4, row 2 | 1 × 1 | provisional |

## 9.1 Canonical scaffold manifest

The LLM should create the following file and use it as the scaffold generator input.

```yaml
schema_version: 1
defaults:
  default_material: "<MOD_ID>:cotton"
  supported_mounts:
    - "<MOD_ID>:brass"
    - "<MOD_ID>:iron"
  default_mount: "<MOD_ID>:brass"
  supported_orientations:
    - wall_parallel
    - wall_perpendicular
  content_status: placeholder

groups:
  large:
    width_blocks: 3
    height_blocks: 2
    placement_profile: "<MOD_ID>:placeholder_large"
    geometry: "<MOD_ID>:banner/placeholder/large"
  medium-wall:
    width_blocks: 2
    height_blocks: 2
    placement_profile: "<MOD_ID>:placeholder_medium_wall"
    geometry: "<MOD_ID>:banner/placeholder/medium_wall"
  medium:
    width_blocks: 1
    height_blocks: 2
    placement_profile: "<MOD_ID>:placeholder_medium"
    geometry: "<MOD_ID>:banner/placeholder/medium"
  small:
    width_blocks: 1
    height_blocks: 1
    placement_profile: "<MOD_ID>:placeholder_small"
    geometry: "<MOD_ID>:banner/placeholder/small"
  x-small:
    width_blocks: 1
    height_blocks: 1
    placement_profile: "<MOD_ID>:placeholder_x_small"
    geometry: "<MOD_ID>:banner/placeholder/x_small"

shared_placeholder_assets:
  fabric_base: "<MOD_ID>:banner/placeholder/fabric_base"
  dye_mask: "<MOD_ID>:banner/placeholder/dye_mask"
  static_overlay: "<MOD_ID>:banner/placeholder/static_overlay"

banners:
  - { index: 1,  id: large_01, group: large, page: 1, row: 1, display_name: "Large Banner 01", name_status: provisional }
  - { index: 2,  id: large_02, group: large, page: 1, row: 2, display_name: "Large Banner 02", name_status: provisional }
  - { index: 3,  id: large_03, group: large, page: 1, row: 3, display_name: "Large Banner 03", name_status: provisional }
  - { index: 4,  id: large_04, group: large, page: 1, row: 4, display_name: "Large Banner 04", name_status: provisional }
  - { index: 5,  id: large_05, group: large, page: 1, row: 5, display_name: "Large Banner 05", name_status: provisional }
  - { index: 6,  id: large_06, group: large, page: 1, row: 6, display_name: "Large Banner 06", name_status: provisional }
  - { index: 7,  id: medium_wall_01, group: medium-wall, page: 1, row: 7, display_name: "Medium Wall Banner 01", name_status: provisional }
  - { index: 8,  id: medium_wall_02, group: medium-wall, page: 1, row: 8, display_name: "Medium Wall Banner 02", name_status: provisional }

  - { index: 9,  id: medium_wall_03, group: medium-wall, page: 2, row: 1, display_name: "Medium Wall Banner 03", name_status: provisional }
  - { index: 10, id: medium_wall_04, group: medium-wall, page: 2, row: 2, display_name: "Medium Wall Banner 04", name_status: provisional }
  - { index: 11, id: medium_wall_05, group: medium-wall, page: 2, row: 3, display_name: "Medium Wall Banner 05", name_status: provisional }
  - { index: 12, id: joined_wards, group: medium-wall, page: 2, row: 4, display_name: "Joined Wards", name_status: source-named }
  - { index: 13, id: tournament_medium, group: medium, page: 2, row: 5, display_name: "Tournament Medium", name_status: source-named }
  - { index: 14, id: ceremonial_tournament, group: medium, page: 2, row: 6, display_name: "Ceremonial Tournament", name_status: source-named }
  - { index: 15, id: iron_quarter, group: medium, page: 2, row: 7, display_name: "Iron Quarter", name_status: source-named }
  - { index: 16, id: outer_ward, group: medium, page: 2, row: 8, display_name: "Outer Ward", name_status: source-named }
  - { index: 17, id: ward_of_serpents, group: medium, page: 2, row: 9, display_name: "Ward of Serpents", name_status: source-named }
  - { index: 18, id: serpent_guard, group: medium, page: 2, row: 10, display_name: "Serpent Guard", name_status: source-named }
  - { index: 19, id: crossroad_guard, group: medium, page: 2, row: 11, display_name: "Crossroad Guard", name_status: source-named }

  - { index: 20, id: argent_shield, group: medium, page: 3, row: 1, display_name: "Argent Shield", name_status: source-named }
  - { index: 21, id: silver_and_gold_pennon, group: small, page: 3, row: 2, display_name: "Silver and Gold Pennon", name_status: source-named }
  - { index: 22, id: end_01, group: small, page: 3, row: 3, display_name: "End Banner 01", name_status: provisional }
  - { index: 23, id: end_02, group: small, page: 3, row: 4, display_name: "End Banner 02", name_status: provisional }
  - { index: 24, id: pennon_of_silver, group: small, page: 3, row: 5, display_name: "Pennon of Silver", name_status: source-named }
  - { index: 25, id: iron_ward, group: small, page: 3, row: 6, display_name: "Iron Ward", name_status: source-named }
  - { index: 26, id: iron_ward_auxiliary, group: small, page: 3, row: 7, display_name: "Iron Ward Auxiliary", name_status: source-named }
  - { index: 27, id: road_guard, group: x-small, page: 3, row: 8, display_name: "Road Guard", name_status: source-named }
  - { index: 28, id: pale_road_guard, group: x-small, page: 3, row: 9, display_name: "Pale Road Guard", name_status: source-named }
  - { index: 29, id: red_crosslets, group: x-small, page: 3, row: 10, display_name: "Red Crosslets", name_status: source-named }
  - { index: 30, id: captains_red_crosslets, group: x-small, page: 3, row: 11, display_name: "Captain's Red Crosslets", name_status: source-named }
  - { index: 31, id: scarlet_court, group: x-small, page: 3, row: 12, display_name: "Scarlet Court", name_status: source-named }

  - { index: 32, id: verdant_court, group: x-small, page: 4, row: 1, display_name: "Verdant Court", name_status: source-named }
  - { index: 33, id: x_small_unnamed_01, group: x-small, page: 4, row: 2, display_name: "Extra-Small Banner 01", name_status: provisional }
```

## 9.2 Fields the owner can fill later

Each manifest entry should eventually support:

```yaml
final_display_name:
width_blocks:
height_blocks:
supported_orientations:
supported_mounts:
default_mount:
geometry:
fabric_base:
dye_mask:
static_overlay:
collision_profile:
placement_profile:
recipe_id:
notes:
content_status:
```

The scaffold generator should preserve owner-edited values and refuse to overwrite files unless explicitly passed a force flag.

---

# 10. Standard Milestone Output

At the end of every milestone, the coding LLM must provide:

```markdown
## Milestone N Report

### Summary
What was implemented.

### Files
Files added, changed, deleted, or generated.

### Tests run
Exact commands and results.

### Manual checks
Checks performed, or clearly marked not performed.

### Decisions
Repository-specific choices and why.

### Known limitations
Anything intentionally deferred.

### Acceptance criteria
A checklist with pass/fail status.

### Commit
Commit hash and message, if available.

### Next action
The next milestone only.
```

---

# 11. Milestone 0 — Repository Discovery and Implementation Facts

## Goal

Understand the actual UltimaCraft codebase before changing it.

## Tasks

1. Inspect build files and determine:
   - Minecraft version.
   - Mod loader/framework.
   - Language and version.
   - Mappings.
   - Dependency versions.
2. Identify the existing systems for:
   - Item and block registration.
   - Block entities.
   - Item instance data.
   - Network packets.
   - Menus/screens.
   - Client rendering.
   - Recipes.
   - Commands.
   - Data generation.
   - Tests.
3. Locate the blacksmithing or material system.
4. Determine whether fabric materials should reuse an existing material ID abstraction.
5. Determine how the project handles client-only classes.
6. Run the unmodified build and tests.
7. Create:
   - `docs/banner-dyeing/PROJECT_FACTS.md`
   - `docs/banner-dyeing/OPEN_QUESTIONS.md`
   - `docs/banner-dyeing/IMPLEMENTATION_LOG.md`
8. Copy this playbook and the companion design document into the repository's documentation area if they are not already tracked.

## Do not

- Register items.
- Add gameplay code.
- Select APIs based only on general Minecraft knowledge.
- Refactor existing material systems.

## Required tests

Run the project's normal clean build and existing test suite.

## Acceptance criteria

- The unmodified project builds, or pre-existing failures are documented.
- Every repository token in Section 4 is resolved.
- The selected item-data, block-entity, networking, and rendering APIs are recorded.
- Existing material integration points are identified.
- No gameplay behaviour changed.

## Suggested commit

```text
docs(banners): record repository integration facts
```

---

# 12. Milestone 1 — Feature Skeleton, IDs, and Test Harness

## Goal

Create empty feature modules and stable identifier types without implementing gameplay.

## Tasks

1. Create package/module skeletons for banner and dye systems.
2. Add stable value types or wrappers for:
   - Banner definition ID.
   - Material ID, reusing the project type if possible.
   - Pigment ID.
   - Resolved colour ID.
   - Mount ID.
   - Placement profile ID.
3. Create shared constants for:
   - Schema version.
   - Default cotton material.
   - Brass and iron mounts.
   - Orientation values.
4. Add test fixtures and helper builders.
5. Add one smoke test that loads the feature module without registrations.
6. Add structured log categories for content validation.

## Design rule

IDs are serialized as namespaced resource identifiers using the project's standard type.

## Required tests

- ID validation.
- Round-trip serialization.
- Invalid namespace/path rejection.
- Feature bootstrap smoke test.

## Acceptance criteria

- Project compiles.
- Stable IDs have equality and serialization tests.
- No banner content is registered yet.
- Client-only code is isolated correctly.

## Suggested commit

```text
feat(banners): add feature skeleton and stable identifiers
```

---

# 13. Milestone 2 — Core Data Records and Codecs

## Goal

Represent banner definitions, materials, pigments, palettes, mounts, and instance state.

## Tasks

Implement immutable data structures and codecs for:

1. `BannerDefinition`
2. `BannerDimensions`
3. `BannerAssets`
4. `BannerSourceReference`
5. `FabricMaterialDefinition`
6. `PigmentDefinition`
7. `MaterialPalette`
8. `MaterialPaletteEntry`
9. `MountDefinition`
10. `PlacementProfile`
11. `BannerInstanceState`
12. `DyeTubState`
13. `DyeResult`
14. `MatchType`

## Validation rules

- Width is between 1 and 3 for this feature.
- Height is positive and within a documented safe maximum.
- Orientation list is non-empty.
- Supported mount list is non-empty.
- Default mount is contained in supported mounts.
- Natural colour exists in the material palette.
- Pigment and palette colour formats are valid.
- OKLab components are finite.
- IDs are namespaced and stable.
- Schema versions are recognized.
- `BannerInstanceState` cannot exist without a definition, material, resolved colour, and mount.
- `source_pigment_id` may be absent only for natural/undyed state.

## Required tests

- Serialization round trips for every record.
- Missing required fields.
- Invalid dimensions.
- Invalid default mount.
- Unknown schema version.
- Optional source pigment.
- Migration fixture for a future-version rejection path.

## Acceptance criteria

- Data structures do not depend on client classes.
- Codecs produce helpful errors.
- Records are immutable or treated as immutable.
- No registry-loading code is required yet.

## Suggested commit

```text
feat(banners): define banner and dye data contracts
```

---

# 14. Milestone 3 — Data Registries and Validation Pipeline

## Goal

Load definitions from data and validate cross-references.

## Tasks

1. Implement or integrate registries for:
   - Banner definitions.
   - Fabric materials.
   - Pigments.
   - Material palettes.
   - Mounts.
   - Placement profiles.
2. Implement a two-stage validation process:
   - Structural validation while decoding.
   - Cross-reference validation after all registries load.
3. Produce a validation report grouped by severity:
   - Error.
   - Warning.
   - Information.
4. In development, fail fast on invalid core data.
5. In production, disable invalid entries where safe and log exact resource paths.
6. Expose read-only lookup services.
7. Add reload support if the project supports data reloads.

## Required tests

- Valid registry set.
- Missing palette.
- Missing default mount.
- Duplicate stable ID.
- Invalid referenced geometry.
- Cross-reference cycle, if possible.
- Reload replaces old definitions safely.
- Disabled invalid definition cannot be spawned.

## Acceptance criteria

- Registries can load a minimal test dataset.
- Errors identify the source resource.
- Consumers cannot mutate registry contents.
- Reload behaviour is documented.

## Suggested commit

```text
feat(banners): add data registries and cross-reference validation
```

---

# 15. Milestone 4 — Scaffold All 33 Banner Placeholders

## Goal

Create a complete, valid 33-banner catalogue before final art exists.

## Tasks

1. Add `content/banner_catalogue.yml` from Section 9.
2. Create `tools/scaffold_banners.*` using a language already supported by the project.
3. The scaffold tool must:
   - Read the manifest.
   - Assert exactly 33 unique indexes.
   - Assert exactly 33 unique IDs.
   - Generate or verify one definition per banner.
   - Add localization placeholders.
   - Point definitions to shared placeholder assets.
   - Produce `content/banner_catalogue_status.md`.
   - Avoid overwriting customized files by default.
   - Support `--check`.
   - Support `--force` only with an explicit warning.
4. Add shared placeholder textures and geometry for:
   - Large.
   - Medium wall.
   - Medium.
   - Small.
   - Extra-small.
5. Generate all 33 definitions.
6. Add a registry count assertion in development.
7. Add a content-status field so unfinished entries remain visible to developers.
8. Add a developer command or log report that lists placeholder entries.

## Required generated paths

One definition per ID:

```text
data/<MOD_ID>/banner_definitions/<banner_id>.json
```

or the repository-equivalent path.

Localization must include every banner.

## Required tests

- Manifest count is exactly 33.
- IDs are unique.
- Indexes 1 through 33 are continuous.
- All definitions load.
- All assets resolve to either final or shared placeholder assets.
- No unlocalized banner entry.
- `--check` is clean after generation.
- Re-running without `--force` does not destroy edits.

## Acceptance criteria

- The in-game registry exposes exactly 33 active banner definitions.
- All 33 can be enumerated by a developer command or test.
- Placeholder definitions package correctly.
- No final dimensions or names are falsely presented as confirmed.
- The user can edit the manifest later without changing system code.

## Suggested commit

```text
feat(banners): scaffold 33 placeholder banner definitions
```

---

# 16. Milestone 5 — Materials, Palettes, and Colour Mathematics

## Goal

Implement material-aware colour resolution independently of items and rendering.

## Tasks

1. Register initial fabric materials:
   - Cotton.
   - Wool.
   - Linen.
   - Silk.
2. Add one natural/undyed palette colour for each.
3. Add a small development palette with enough colours to test matching.
4. Implement sRGB-to-linear conversion.
5. Implement linear RGB-to-OKLab conversion or use a verified existing library.
6. Implement perceptual distance.
7. Implement `DyeResolver`:
   - Explicit mapping first.
   - Compatibility filtering.
   - Nearest-colour fallback.
   - Deterministic tie-breaking.
8. Cache immutable conversion results where appropriate.
9. Keep the resolver server-safe and client-safe.
10. Add an optional debug explanation object:
    - Selected colour.
    - Match type.
    - Distance.
    - Candidates considered.
    - Rejection reasons.

## Tie-breaking order

1. Lowest perceptual distance.
2. Shared pigment colour-family tag.
3. Higher palette priority.
4. Stable ID lexical order.

## Required tests

- Known sRGB-to-OKLab reference values within tolerance.
- Explicit mapping beats closer fallback.
- Each material can resolve the same pigment differently.
- Deterministic ties.
- Compatibility restrictions.
- Empty palette error.
- Natural colour resolution.
- No NaN or infinite values.
- Cache does not change results.

## Acceptance criteria

- One red pigment produces four material-specific results.
- Resolver output is deterministic.
- No item or screen code is needed to test the resolver.
- Colour math has reference-based tests, not visual guesses only.

## Suggested commit

```text
feat(dyes): add material palettes and perceptual colour resolution
```

---

# 17. Milestone 6 — Dye Items and Stateful Dye Tub

## Goal

Allow a dye item to load a pigment into a dye tub.

## Tasks

1. Register or integrate:
   - Base dye item type.
   - Dye tub item.
2. Add data storage for `DyeTubState`.
3. Implement main-hand tub plus off-hand dye interaction.
4. Validate interaction on the server.
5. Decide through configuration or existing design:
   - Whether the dye item is consumed.
   - Whether loading replaces an existing pigment.
   - Whether the tub has unlimited uses.
6. For unresolved product decisions, use these reversible defaults:
   - Loading replaces the previous pigment.
   - Dye item is consumed once.
   - Tub uses are unlimited.
7. Add tooltip:
   - Empty state.
   - Stored pigment.
   - Uses, only if finite.
8. Add server-confirmed sound and particles.
9. Add creative/admin access to development pigments.

## Required tests

- Empty tub serialization.
- Loaded tub serialization.
- Loading valid dye.
- Replacing pigment.
- Invalid off-hand item.
- Client spoof attempt.
- Stack count handling.
- Dye consumption rollback if mutation fails.
- Tooltip data source does not mutate state.

## Manual test

1. Obtain empty tub and pigment.
2. Hold tub in main hand and pigment in off hand.
3. Use.
4. Confirm tub tooltip changes.
5. Relog and verify pigment persists.

## Acceptance criteria

- Tub state persists through save/reload.
- The server controls consumption and state.
- Invalid interactions do not consume items.
- The interaction respects the configured hand contract.

## Suggested commit

```text
feat(dyes): add loadable dye tub item
```

---

# 18. Milestone 7 — Generic Dyeable Item API and Banner Item State

## Goal

Make banner items hold design, material, colour, and mount state.

## Tasks

1. Define a generic dyeable-item contract.
2. Implement banner item state encoding using the project's established item data system.
3. Add safe constructors/factories:
   - Natural cotton admin banner.
   - Crafted material banner.
   - Fully specified development banner.
4. Add tooltip content:
   - Banner display name.
   - Material.
   - Resolved colour.
   - Source pigment, when present.
   - Mount.
   - Dimensions.
   - Placement orientations.
   - Placeholder warning in development builds.
5. Add item-state validation and repair rules.
6. Add migration path for missing resolved colours:
   - Re-resolve source pigment if available.
   - Otherwise use material natural colour.
7. Ensure stackability follows project expectations:
   - Items with different state must not merge incorrectly.

## Required tests

- Natural banner creation.
- Admin default is cotton.
- State round trip.
- Different materials do not merge.
- Different colours do not merge.
- Missing colour fallback.
- Unknown definition produces safe error item or disabled stack.
- Tooltip uses registry display values.
- Re-dye operation changes only colour fields.

## Acceptance criteria

- Any of the 33 definitions can be represented by one shared banner item architecture.
- Material and mount are instance data.
- No separate item class is created for every material-colour combination.
- State survives inventory movement and serialization.

## Suggested commit

```text
feat(banners): add stateful dyeable banner items
```

---

# 19. Milestone 8 — Dye Preview and Confirmed Item Dyeing

## Goal

Open a preview when a loaded tub is used with a banner and apply the resolved colour after confirmation.

## Tasks

1. Implement server request validation:
   - Main hand contains dye tub.
   - Tub is loaded.
   - Off hand contains dyeable banner.
   - Banner material exists.
   - Pigment exists.
   - Palette exists.
2. Resolve the result on the server.
3. Open the project's standard menu/screen flow.
4. Display:
   - Banner.
   - Material.
   - Current colour.
   - Tub pigment.
   - Resolved result.
   - Exact or closest-match label.
   - New-colour preview.
5. On confirmation:
   - Re-read both held stacks.
   - Re-resolve or verify server token.
   - Apply state atomically.
   - Decrement use count only after success, if finite.
6. On cancel:
   - Make no changes.
7. Protect against stale screens:
   - Hand swap.
   - Item moved.
   - Tub pigment changed.
   - Target stack changed.
8. Return a clear result packet.

## Security rule

The client never supplies the authoritative resolved colour ID.

## Required tests

- Valid preview.
- Valid confirmation.
- Cancel.
- Stale hand state.
- Changed target.
- Changed tub.
- Invalid material.
- Missing palette.
- Re-dye.
- Unlimited uses.
- Finite uses, if supported.
- Server rejects arbitrary colour ID.

## Manual test

Dye cotton and silk versions of the same banner using the same tub. Confirm different result names and colours.

## Acceptance criteria

- The interaction is complete from held items to updated banner.
- Re-dyeing works indefinitely.
- Failed confirmation does not mutate either item.
- Approximation is disclosed to the player.

## Suggested commit

```text
feat(dyes): add banner dye preview and server-confirmed application
```

---

# 20. Milestone 9 — Layered Item Rendering

## Goal

Render banner items using fabric tint, dye mask, static overlay, material appearance, and mount.

## Tasks

1. Implement the project's appropriate dynamic item-rendering path.
2. Read:
   - Banner definition.
   - Material.
   - Resolved colour.
   - Mount.
3. Compose:
   - Fabric base.
   - Dye tint through mask.
   - Static overlay.
   - Mount model/texture.
4. Use shared placeholder assets for all 33 entries.
5. Provide a missing-content fallback.
6. Add a client asset cache keyed by immutable render state.
7. Clear caches on resource reload.
8. Ensure no server class loads client rendering classes.
9. Add an inventory preview scene or developer screen if useful.

## Rendering rules

- Static overlays are never tinted.
- Mount colour is independent from fabric colour.
- Natural colour uses the material's natural palette entry.
- Missing resolved colour falls back safely and logs once.
- Placeholder entries remain visually identifiable.

## Required tests

Where renderer unit tests are impractical, add:

- State-to-render-key tests.
- Asset resolution tests.
- Reload cache tests.
- Screenshot/manual test checklist.

## Manual matrix

Render at least:

- One large.
- One medium wall.
- One medium.
- One small.
- One extra-small.
- Cotton natural.
- Wool dark.
- Linen muted.
- Silk saturated.
- Brass mount.
- Iron mount.

## Acceptance criteria

- Item appearance matches state.
- Static details do not tint.
- Both mounts render.
- Resource reload does not retain stale assets.
- Missing assets show the diagnostic fallback.

## Suggested commit

```text
feat(banners): add layered banner item rendering
```

---

# 21. Milestone 10 — Single-Block Placement Foundation

## Goal

Place one-block banners with persistent state before multi-block complexity.

## Tasks

1. Implement the anchor banner block and block entity.
2. Store or derive:
   - Banner instance state.
   - Facing.
   - Orientation.
3. Implement item-to-block transfer.
4. Implement block-to-item drop transfer.
5. Validate wall support.
6. Add wall-parallel placement first.
7. Sync state to tracking clients.
8. Handle chunk save/reload.
9. Add pick-block behaviour if appropriate.
10. Add safe missing-definition fallback.

## Required tests

- Place a one-block banner.
- Save/reload.
- Break and recover identical state.
- Client receives state.
- Invalid wall support.
- Definition removed after save.
- Mount and colour preserved.
- Creative and survival break behaviour.

## Acceptance criteria

- A dyed one-block banner places and returns unchanged.
- Block entity data is versioned.
- State does not live only on the client.
- No multi-block code is required yet.

## Suggested commit

```text
feat(banners): add persistent single-block banner placement
```

---

# 22. Milestone 11 — Multi-Block Anchor and Occupied Parts

## Goal

Support two- and three-block-wide banners as one logical object.

## Tasks

1. Implement an anchor-and-parts model.
2. The anchor owns:
   - Full banner instance state.
   - Definition ID.
   - Orientation/facing.
   - Occupied offsets.
3. Child parts store only enough information to find the anchor.
4. Placement is transactional:
   - Validate all cells.
   - Place anchor and parts.
   - Roll back all cells on failure.
5. Breaking any part:
   - Resolves the anchor.
   - Removes the whole banner.
   - Produces at most one drop.
6. Handle:
   - Explosions.
   - Piston rules.
   - World-edit-like removal hooks if applicable.
   - Chunk boundaries.
   - Orphan repair.
7. Add development visualization of occupied cells.

## Required tests

- Two-block width.
- Three-block width.
- Blocked middle cell.
- Blocked final cell.
- Placement rollback.
- Break anchor.
- Break child.
- Explosion.
- Chunk unload/reload.
- Anchor across chunk boundary.
- Orphan child cleanup.
- Duplicate-drop prevention.

## Acceptance criteria

- Multi-block banners behave as one object.
- Only the anchor stores full instance state.
- No partial placement remains after failure.
- Breaking any part produces one correctly stateful item.

## Suggested commit

```text
feat(banners): support anchor-based multi-block banners
```

---

# 23. Milestone 12 — Wall-Parallel, Wall-Perpendicular, and Mount Variants

## Goal

Complete placement orientations and mount handling.

## Tasks

1. Add orientation enum/state:
   - `wall_parallel`
   - `wall_perpendicular`
2. Make support validation orientation-aware.
3. Make occupied-cell transforms facing-aware.
4. Add placement selection:
   - Infer from clicked face and player intent.
   - Cycle orientation when both are available, using project conventions.
5. Render brass and iron mounts.
6. Enforce each definition's supported orientations and mounts.
7. Add placement ghost or outline if the framework supports it.
8. Add clear invalid-placement feedback.

## Required tests

- Four horizontal facings.
- Parallel placement.
- Perpendicular placement.
- Definition that supports only one orientation.
- Brass mount.
- Iron mount.
- Unsupported mount rejection.
- Occupied offset rotation.
- Collision/selection shapes.
- Neighbor support removal.

## Acceptance criteria

- Orientation changes geometry and occupancy correctly.
- Mount style persists in item and block form.
- Definitions can restrict orientation and mount support.
- Invalid combinations are rejected before state mutation.

## Suggested commit

```text
feat(banners): add orientation-aware placement and mount variants
```

---

# 24. Milestone 13 — Placed Banner Rendering and Live State Sync

## Goal

Render placed banners with the same layered logic as items.

## Tasks

1. Share render-state extraction between item and block renderers.
2. Render:
   - Correct geometry by definition and orientation.
   - Material-specific fabric.
   - Resolved colour.
   - Static overlay.
   - Mount.
3. Synchronize block entity state:
   - Initial chunk data.
   - Tracking updates.
   - Re-dye update if direct-world dyeing is later enabled.
4. Cache immutable model/material combinations.
5. Invalidate on resource or data reload.
6. Add distance and visibility checks appropriate to the framework.
7. Prevent child parts from rendering duplicate cloth.

## Required tests

- Item and placed render keys match.
- Only anchor renders full banner.
- State sync to late-joining client.
- Resource reload.
- Data reload.
- Missing definition fallback.
- Orientation model selection.
- Mount selection.

## Manual test

Place the same banner in all supported orientations with both mounts and at least four material-colour combinations.

## Acceptance criteria

- Item and block appearances agree.
- Child parts do not duplicate the model.
- Late joiners see the correct state.
- Reloads do not require replacing the block.

## Suggested commit

```text
feat(banners): add layered placed-banner rendering and sync
```

---

# 25. Milestone 14 — Crafting and Existing Material-System Integration

## Goal

Make crafted banners inherit fabric material and mount from crafting inputs.

## Tasks

1. Study the existing blacksmithing/material crafting implementation again.
2. Reuse its material identity where compatible.
3. Add banner crafting inputs:
   - Banner design/pattern.
   - Fabric material input.
   - Mount material or mount component.
4. Produce an undyed banner:
   - Selected definition.
   - Crafted material.
   - Material natural colour.
   - Crafted/default mount.
5. Add recipe serializers or custom crafting hooks only if required.
6. Validate unsupported material or mount combinations.
7. Add recipe data generation.
8. Keep colour outside the crafting recipe.

## Required tests

- Cotton craft.
- Wool craft.
- Linen craft.
- Silk craft.
- Brass mount craft.
- Iron mount craft.
- Natural colour assigned correctly.
- Invalid material.
- Invalid mount.
- Recipe remainder handling.
- Craft output state survives shift-click or bulk crafting.

## Acceptance criteria

- Material is determined at crafting time.
- Colour is determined after crafting by dyeing.
- Existing material infrastructure is reused where practical.
- No recipe explosion is required for every material-colour combination.

## Suggested commit

```text
feat(banners): integrate banner crafting with fabric materials
```

---

# 26. Milestone 15 — Admin Tools, NPC-Ready Dye Sources, and Debugging

## Goal

Make the feature usable by developers and ready for initial dye distribution.

## Tasks

1. Add admin command(s) using existing command conventions:
   - Give banner by definition.
   - Optional material.
   - Optional colour.
   - Optional mount.
   - Default material is cotton.
2. Add command suggestions from registries.
3. Add dye tub creation/loading command for testing.
4. Add catalogue validation command.
5. Add palette-resolution debug command.
6. Add a simple registry or shop integration hook for NPC-purchased dyes.
7. Do not build dye crafting yet.
8. Ensure production permissions are correct.

## Conceptual commands

Adapt syntax to the project:

```text
/ultimacraft banner give <player> <definition> [material] [colour] [mount]
/ultimacraft dye tub <player> <pigment>
/ultimacraft banner validate
/ultimacraft dye resolve <pigment> <material>
/ultimacraft banner placeholders
```

## Required tests

- Defaults to cotton.
- Invalid IDs produce suggestions.
- Permission checks.
- Fully specified banner.
- Natural-colour banner.
- Placeholder listing count is 33.
- Dye resolution debug result matches service result.

## Acceptance criteria

- Developers can obtain any placeholder banner.
- Admin default material is cotton.
- Initial pigments can be supplied through NPC/shop integration later without changing the dye model.
- Commands do not bypass state validation.

## Suggested commit

```text
feat(banners): add admin and dye debugging tools
```

---

# 27. Milestone 16 — Replace Placeholder Content Incrementally

## Goal

Allow final art, names, dimensions, orientations, and recipes to be filled in without changing system code.

## Workflow per banner

1. Update `content/banner_catalogue.yml`.
2. Change `content_status` from `placeholder` to `in_progress`.
3. Fill:
   - Final display name.
   - Width and height.
   - Supported orientations.
   - Supported mounts.
   - Geometry path.
   - Fabric base.
   - Dye mask.
   - Static overlay.
   - Placement profile.
   - Recipe.
4. Add original assets.
5. Run scaffold `--check`.
6. Run registry validation.
7. Run content tests.
8. Perform the placement/render test matrix.
9. Change `content_status` to `complete`.
10. Commit one logical batch.

## Batch recommendation

Process content in size-family batches:

1. Extra-small.
2. Small.
3. Medium.
4. Medium wall.
5. Large.

This allows shared geometry and placement profiles to stabilize before the largest assets.

## Content acceptance checklist per banner

```markdown
- [ ] Final name approved
- [ ] Stable ID approved
- [ ] Dimensions verified
- [ ] Parallel placement verified
- [ ] Perpendicular placement verified, if supported
- [ ] Brass mount verified
- [ ] Iron mount verified
- [ ] Fabric base authored
- [ ] Dye mask authored
- [ ] Static overlay authored
- [ ] Cotton palette tested
- [ ] Wool palette tested
- [ ] Linen palette tested
- [ ] Silk palette tested
- [ ] Natural colour tested
- [ ] Light colour tested
- [ ] Dark colour tested
- [ ] Item render tested
- [ ] Placed render tested
- [ ] Break/drop persistence tested
- [ ] Recipe tested
- [ ] Localization complete
- [ ] `content_status: complete`
```

## Acceptance criteria

- Content can be replaced one banner at a time.
- Shared placeholder assets remain available until the final entry is complete.
- No system code changes are needed merely to add final art.
- The status report clearly lists unfinished entries.

## Suggested commit pattern

```text
content(banners): complete <size-family> banner assets
```

---

# 28. Milestone 17 — Migration, Reliability, Performance, and Release QA

## Goal

Prepare the system for long-lived multiplayer worlds.

## Tasks

1. Add schema migration tests.
2. Add fallbacks:
   - Missing definition.
   - Missing material.
   - Missing colour.
   - Missing pigment.
   - Missing mount.
3. Confirm resolved colour IDs preserve old appearances after palette changes.
4. Add registry reload and client sync tests.
5. Profile:
   - Chunk rendering.
   - Model cache size.
   - Block entity tick usage.
   - Network packet size.
   - Large banner walls.
6. Ensure banner block entities do not tick unless required.
7. Rate-limit or de-duplicate repeated missing-asset logs.
8. Add compatibility notes.
9. Complete user and developer documentation.
10. Run the complete QA matrix.
11. Create release notes.

## Required stress tests

- A wall with many banners.
- All 33 definitions loaded.
- Mixed sizes and orientations.
- Repeated chunk unload/reload.
- Server restart.
- Resource reload.
- Data reload.
- Player joins near a dense banner area.
- Simultaneous dyeing by multiple players.
- Broken or intentionally removed data resource.

## Final release acceptance criteria

- Exactly 33 active catalogue entries.
- All required entries marked complete or explicitly approved as placeholders.
- Clean build.
- Unit tests pass.
- Integration/game tests pass.
- No state-loss defect.
- No duplicate drops.
- No client-only class load on dedicated server.
- No unresolved critical validation errors.
- Documentation includes adding a banner, material, palette colour, pigment, and mount.
- Performance is acceptable under the project's normal server target.

## Suggested commit

```text
release(banners): complete banner and material-aware dyeing system
```

---

# 29. Optional Milestones After First Release

These are explicitly outside the required first-release path.

## 29.1 Direct dyeing of placed banners

Use a loaded tub on the placed anchor. Apply the same server preview and validation rules.

## 29.2 Dye crafting

Add plants, minerals, alchemy, or profession recipes that produce pigment items.

## 29.3 Finite tub uses

Add capacity and remaining-use rules with atomic decrements.

## 29.4 Washing and natural restoration

Add a washing tub, bleach, or natural-colour operation.

## 29.5 Rare dyes

Add ice, event, faction, legacy, magical, metallic, or pearlescent pigments.

## 29.6 Multi-region tinting

Allow multiple dye masks or individually dyeable regions. This requires a new instance-state schema and must be versioned.

## 29.7 Other dyeable textiles

Reuse the generic dyeable-item framework for:

- Clothing.
- Curtains.
- Carpets.
- Tents.
- Furniture.
- Ship sails.
- Upholstery.
- Decorative cloth.

---

# 30. Global Test Matrix

## 30.1 Materials

```text
cotton
wool
linen
silk
```

## 30.2 Colour cases

```text
natural
exact pigment mapping
nearest-colour fallback
light
dark
saturated
muted
rare/special placeholder
missing colour fallback
```

## 30.3 Sizes

```text
1 block wide
2 blocks wide
3 blocks wide
```

## 30.4 Orientations

```text
wall parallel
wall perpendicular
unsupported orientation
```

## 30.5 Mounts

```text
brass
iron
unsupported mount
```

## 30.6 Lifecycle

```text
create item
craft item
admin-generate item
load tub
preview dye
cancel dye
apply dye
re-dye
place
save
reload
client join
break
recover item
place again
```

## 30.7 Failure cases

```text
empty tub
invalid off-hand item
missing material
missing pigment
missing palette
missing definition
missing model
missing texture
blocked placement
invalid support
stale UI confirmation
client spoofed colour
orphan child block
removed registry entry
unknown schema version
```

---

# 31. Required Developer Documentation

Before release, the LLM must create:

```text
docs/banner-dyeing/README.md
docs/banner-dyeing/PROJECT_FACTS.md
docs/banner-dyeing/IMPLEMENTATION_LOG.md
docs/banner-dyeing/OPEN_QUESTIONS.md
docs/banner-dyeing/ADDING_A_BANNER.md
docs/banner-dyeing/ADDING_A_MATERIAL.md
docs/banner-dyeing/ADDING_A_PIGMENT.md
docs/banner-dyeing/ADDING_A_PALETTE_COLOUR.md
docs/banner-dyeing/ADDING_A_MOUNT.md
docs/banner-dyeing/SAVED_DATA_AND_MIGRATIONS.md
docs/banner-dyeing/TEST_PLAN.md
```

`ADDING_A_BANNER.md` must explain:

1. Add or edit the manifest entry.
2. Choose dimensions.
3. Choose orientations.
4. Choose mounts.
5. Add geometry.
6. Add fabric base.
7. Add dye mask.
8. Add static overlay.
9. Add localization.
10. Add recipe.
11. Run scaffold check.
12. Run validation.
13. Run the content checklist.
14. Mark complete.

---

# 32. Review Gates

The LLM must stop for review at these points.

## Gate A — After Milestone 0

Review repository facts and selected integration APIs.

## Gate B — After Milestone 4

Review:

- Exactly 33 placeholders.
- Stable IDs.
- Provisional names.
- Provisional dimensions.
- Manifest editing workflow.

Stable IDs should preferably be approved before players obtain items.

## Gate C — After Milestone 8

Review the complete item dyeing interaction and preview.

## Gate D — After Milestone 12

Review placement, dimensions, orientations, and mount behaviour.

## Gate E — After the first completed content batch

Review final asset conventions before producing the remaining banners.

## Gate F — Before release

Review migrations, performance, content status, and the complete test report.

---

# 33. Definition of Done

The feature is done when an administrator or player can:

1. Obtain or craft any one of the 33 banner designs.
2. Have its material determined as cotton, wool, linen, or silk.
3. Load a pigment into a dye tub.
4. Preview the material-specific colour result.
5. Apply the colour.
6. Re-dye the same banner.
7. See static heraldic details remain unchanged.
8. Choose or craft a supported brass or iron mount.
9. Place the banner in a supported orientation.
10. Place one-, two-, and three-block-wide banners.
11. Save and reload the world.
12. Break the banner.
13. Recover an item with identical design, material, colour, and mount.
14. Place it again without state loss.

The development team must also be able to:

1. Add a 34th banner through data and assets without central code changes.
2. Add a new pigment without editing banner classes.
3. Add a new palette colour without editing the dye resolver.
4. Add a new material through the documented registry path.
5. Identify all incomplete content from one generated report.
6. Run automated validation that proves the catalogue contains exactly 33 entries for this release.

---

# 34. First Prompt to Give the Coding LLM

Use this as the first implementation prompt:

```text
Implement Milestone 0 from the UltimaCraft Banner & Material-Aware Dyeing
System LLM Implementation Playbook.

Do not add gameplay code yet.

Inspect the repository and create:
- docs/banner-dyeing/PROJECT_FACTS.md
- docs/banner-dyeing/OPEN_QUESTIONS.md
- docs/banner-dyeing/IMPLEMENTATION_LOG.md

Resolve the repository tokens, identify the existing material/blacksmithing
integration points, run the unmodified build and tests, and provide the
standard milestone report.

Do not guess the Minecraft version, mod loader, registration APIs, item-data
system, networking layer, rendering path, or test framework. Ground every
decision in repository files.
```

For every later prompt, use:

```text
Implement only Milestone <N> from the playbook.

Read:
- the playbook,
- the companion design document,
- PROJECT_FACTS.md,
- OPEN_QUESTIONS.md,
- IMPLEMENTATION_LOG.md,
- and the current repository state.

Do not begin Milestone <N+1>.
Run the required tests and produce the standard milestone report.
Stop if a missing repository fact would force an incompatible public API or
saved-data decision.
```

---

# 35. Final Guidance to the Implementing LLM

The most important architectural constraint is separation.

The dyeing system owns:

```text
pigment
material
palette
resolved colour
dye tub
generic dyeable-item interaction
```

The banner system owns:

```text
banner definition
dimensions
geometry
texture layers
mount
placement
item/block state conversion
```

A banner participates in dyeing through a generic dyeable-item contract.

Do not create thousands of item IDs for combinations of:

```text
33 designs × 4 materials × colours × 2 mounts
```

Create 33 stable banner definitions and store material, colour, and mount as instance state.

Build one vertical slice completely, prove persistence and multiplayer authority, and only then scale the content catalogue. The 33 placeholders exist precisely so the system can be completed before final art and measurements are known.
