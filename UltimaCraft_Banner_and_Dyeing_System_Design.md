# UltimaCraft Banner and Material-Aware Dyeing System

**Document type:** Technical design document  
**Status:** Draft for implementation planning  
**Version:** 0.1  
**Scope:** Banner content framework, dye tubs, material-aware colour resolution, rendering, persistence, placement, and future expansion

---

## 1. Executive Summary

UltimaCraft will add approximately 33 decorative banner designs and a reusable dyeing system.

Each banner will be:

- Craftable from a fabric material such as cotton, wool, linen, or silk.
- Dyeable and re-dyeable without a permanent lock.
- Available in data-defined sizes, including one-, two-, and three-block-wide forms.
- Placeable either parallel to a wall or perpendicular to it, depending on the design.
- Rendered with either a brass or iron/metal mounting variant.
- Composed of a dyeable fabric layer and one or more non-dyeable detail layers.

The dyeing system will use a **material-aware palette**. A dye represents a pigment or intended colour, while each fabric material decides how that pigment appears. The same red dye may therefore produce a muted red on linen, a saturated red on silk, and a warmer red on wool.

If a dye does not have an exact colour entry for a material, the system will choose the nearest compatible colour in that material's palette.

The recommended architecture is data-driven. Banner shape, size, orientation, mount support, texture references, material, and dye state should be stored as independent properties instead of being implemented as separate hard-coded item types for every combination.

---

## 2. Design Goals

### 2.1 Primary goals

1. Add the full banner catalogue without creating an unmanageable number of item and block definitions.
2. Allow players to apply colour through an intuitive dye-tub interaction.
3. Make colour results depend on fabric material.
4. Preserve colour when a banner moves between item and placed-block form.
5. Allow unlimited re-dyeing.
6. Support future dye rarity, special dyes, dye crafting, and additional dyeable item categories.
7. Keep the system server-authoritative and safe for multiplayer.
8. Make new banners, materials, and colours addable through data files wherever practical.

### 2.2 Non-goals for the first release

The first release does not need to include:

- Player-crafted dyes.
- Dye depletion, durability, or limited uses unless later requested.
- Multi-region banner painting.
- Player-created heraldry.
- Pattern editing.
- Animated cloth simulation.
- Historical accuracy rules that restrict which dyes can be applied to which banners.
- A complete rarity economy for special dyes.

Hooks should exist for these features, but they should not block the initial implementation.

---

## 3. Source Catalogue and Content Framing

The provided banner sheet is the visual and naming reference for the initial banner set. It contains silhouettes and labels across four pages, ranging from large and medium wall banners to small pennons and extra-small guard or court banners.

Named examples in the reference include:

- Joined Wards
- Tournament
- Ceremonial Tournament
- Iron Quarter
- Outer Ward
- Ward of Serpents
- Serpent Guard
- Crossroad Guard
- Argent Shield
- Silver and Gold Pennon
- Silver Pennon
- Iron Ward
- Iron Ward Auxiliary
- Road Guard
- Pale Road Guard
- Red Crosslets
- Captain's Red Crosslets
- Scarlet Court
- Verdant Court

The sheet also contains repeated generic size labels such as `large`, `medium-wall`, `small`, and `x-small`. These should be treated as catalogue groupings rather than implementation classes.

### 3.1 Recommended content rule

Every visible banner design receives one stable `banner_definition_id`.

Combinations such as material, colour, and mount are runtime state and should not require separate definitions unless the art or geometry truly changes.

For example:

```text
ultima:ward_of_serpents
```

can represent all of the following without separate banner IDs:

```text
cotton + green + brass mount
wool + crimson + iron mount
silk + ice blue + brass mount
linen + undyed + iron mount
```

---

## 4. Player Experience

The feature has two closely related interactions.

## 4.1 Loading a dye tub

**Held items**

- Left hand: dye item
- Right hand: dye tub

**Action**

The player uses the dye tub.

**Result**

The tub stores the dye's pigment state. Depending on the final gameplay decision, the dye item may be consumed, partially consumed, or retained.

**Feedback**

- A sound and particle effect confirm success.
- The tub's tooltip and appearance update.
- The tub displays the stored colour name.
- Special dyes may display a rarity or effect label later.

## 4.2 Dyeing a banner item

**Held items**

- Left hand: dyeable banner item
- Right hand: dye tub containing a colour

**Action**

The player uses the dye tub.

**Result**

A dyeing screen opens. The system reads the banner's fabric material and displays the colour that this tub will produce on that material.

The player confirms the operation, and the resolved colour is stored on the banner item.

## 4.3 Dyeing a placed banner

The first release may support either of these approaches:

### Recommended first-release approach

Require the player to remove the banner, dye the item, and place it again.

This is simpler, avoids awkward ownership questions, and keeps the hand interaction consistent.

### Optional direct-world approach

Allow the player to use a loaded dye tub directly on a placed banner. The same preview and confirmation rules apply, and the placed block entity is updated.

This can be added after item dyeing is stable.

## 4.4 Re-dyeing

A banner can be re-dyed indefinitely.

Applying a new colour replaces the previous resolved colour state. The banner's design, material, size, orientation support, and mount selection remain unchanged.

## 4.5 Failure feedback

The interaction must explain why it failed.

Examples:

- “This tub contains no dye.”
- “This item cannot be dyed.”
- “This material has no compatible colour palette.”
- “The banner must be in your left hand.”
- “The dye tub must be in your right hand.”
- “This item is missing material data.”
- “The selected mount is not supported by this banner.”

---

## 5. Core Architecture

The system should separate five concepts:

1. **Banner definition** — shape, artwork, placement, and geometry.
2. **Fabric material** — cotton, wool, linen, silk, and their dye behaviour.
3. **Dye pigment** — the colour carried by a dye item or dye tub.
4. **Resolved material colour** — the actual display colour after matching pigment to material.
5. **Banner instance state** — the specific material, colour, and mount on one item or placed banner.

This separation prevents a combinatorial explosion.

Without separation, 33 banners × 4 materials × dozens of colours × 2 mounts could create thousands of individual item variants. With instance data, the project needs roughly 33 definitions plus registries for materials, dyes, colours, and mounts.

---

## 6. Domain Model

## 6.1 Banner Definition

A banner definition describes immutable design properties.

Suggested fields:

```json
{
  "id": "ultima:ward_of_serpents",
  "display_name": "Ward of Serpents",
  "catalogue_group": "medium",
  "dimensions": {
    "width_blocks": 2,
    "height_blocks": 3
  },
  "supported_orientations": [
    "wall_parallel",
    "wall_perpendicular"
  ],
  "supported_mounts": [
    "brass",
    "iron"
  ],
  "default_mount": "brass",
  "textures": {
    "fabric_base": "ultima:textures/banner/ward_of_serpents_fabric.png",
    "dye_mask": "ultima:textures/banner/ward_of_serpents_mask.png",
    "static_overlay": "ultima:textures/banner/ward_of_serpents_overlay.png"
  },
  "model": "ultima:models/banner/ward_of_serpents.json",
  "collision_profile": "medium_wall",
  "placement_profile": "two_block_wall_banner"
}
```

### Definition rules

- `id` must remain stable after release.
- `width_blocks` must be one, two, or three for the current scope.
- Height should remain data-driven because the source catalogue includes varied proportions.
- Supported orientations should be explicit per design.
- Mount support should be explicit per design.
- Texture and model paths should not be inferred from display names at runtime.
- Generic catalogue labels such as `large` or `x-small` are useful for organization, but placement should use exact dimensions.

## 6.2 Fabric Material

A material defines crafting identity and colour behaviour.

Initial materials:

- Cotton
- Wool
- Linen
- Silk

Suggested fields:

```json
{
  "id": "ultima:silk",
  "display_name": "Silk",
  "default_undyed_colour": "ultima:silk_natural",
  "palette": "ultima:palettes/silk.json",
  "render_properties": {
    "saturation_multiplier": 1.12,
    "lightness_multiplier": 0.98,
    "roughness": 0.28,
    "sheen": 0.35
  },
  "tags": [
    "fabric",
    "fine"
  ]
}
```

The render properties are optional and should only be used if the renderer supports them. The authoritative colour result should come from the material palette rather than from runtime shader multipliers alone.

## 6.3 Dye Pigment

A pigment represents the colour intention carried by a dye item.

Suggested fields:

```json
{
  "id": "ultima:crimson_dye",
  "display_name": "Crimson Dye",
  "reference_colour": "#A51C30",
  "colour_space": {
    "oklab": [0.48, 0.17, 0.07]
  },
  "tags": [
    "red",
    "common"
  ],
  "rarity": "common",
  "special_effect": null
}
```

The exact numeric values above are illustrative only.

A pigment does not need to know every material result. Material palettes are responsible for resolving the pigment.

## 6.4 Material Palette Entry

Each fabric material owns a set of allowed output colours.

Example silk palette entry:

```json
{
  "id": "ultima:silk_ruby",
  "display_name": "Ruby Silk",
  "display_colour": "#A81742",
  "match_colour_oklab": [0.50, 0.18, 0.05],
  "tags": [
    "red",
    "rich"
  ]
}
```

Example linen palette entry for the same general colour family:

```json
{
  "id": "ultima:linen_madder",
  "display_name": "Madder Linen",
  "display_colour": "#8D4C45",
  "match_colour_oklab": [0.48, 0.09, 0.04],
  "tags": [
    "red",
    "muted"
  ]
}
```

## 6.5 Dye Tub State

The dye tub is a stateful item.

Suggested stored data:

```json
{
  "schema_version": 1,
  "pigment_id": "ultima:crimson_dye",
  "remaining_uses": null,
  "custom_name": null
}
```

For unlimited-use tubs, `remaining_uses` can be absent or `null`.

If limited-use tubs are introduced later, this field can become an integer without changing the colour model.

## 6.6 Banner Item State

Suggested stored data:

```json
{
  "schema_version": 1,
  "banner_definition_id": "ultima:ward_of_serpents",
  "material_id": "ultima:silk",
  "resolved_colour_id": "ultima:silk_ruby",
  "source_pigment_id": "ultima:crimson_dye",
  "mount_id": "ultima:brass",
  "custom_data": {}
}
```

### Why store both source pigment and resolved colour?

`source_pigment_id` records what the player used.

`resolved_colour_id` records the exact visual result.

This makes rendering deterministic and protects old items if palette matching rules change later. It also allows migrations or tooltip text such as:

```text
Dyed with Crimson Dye
Result: Ruby Silk
```

## 6.7 Placed Banner State

A placed banner must preserve the item state plus placement information.

```json
{
  "schema_version": 1,
  "banner_definition_id": "ultima:ward_of_serpents",
  "material_id": "ultima:silk",
  "resolved_colour_id": "ultima:silk_ruby",
  "source_pigment_id": "ultima:crimson_dye",
  "mount_id": "ultima:brass",
  "orientation": "wall_perpendicular",
  "facing": "north",
  "anchor_position": [120, 65, -34],
  "owner_id": null
}
```

When broken, the block must create an item containing the same material, colour, design, and mount state.

---

## 7. Material-Aware Colour Resolution

## 7.1 Required behaviour

When a player dyes an item:

1. Read the pigment from the dye tub.
2. Read the target item's material.
3. Load the palette assigned to that material.
4. Check for an explicit pigment-to-colour mapping.
5. If no explicit mapping exists, calculate the nearest palette colour.
6. Store the resolved colour ID on the item.
7. Show the resolved name and preview before confirmation.

## 7.2 Explicit mapping first

Designers may want a specific canonical result.

Example:

```json
{
  "pigment_overrides": {
    "ultima:crimson_dye": "ultima:silk_ruby",
    "ultima:ice_dye": "ultima:silk_glacial"
  }
}
```

Explicit mappings should always win over nearest-colour matching.

This is especially important for signature colours such as ice dye, event dyes, faction dyes, or other culturally important Ultima-style colours.

## 7.3 Nearest-colour matching

RGB distance is not recommended because equal numeric distances in RGB do not consistently look equally different to people.

Use a perceptual colour space such as **OKLab**.

For each palette entry, calculate the distance from the dye's reference colour:

```text
distance =
sqrt(
  (L1 - L2)^2 +
  (a1 - a2)^2 +
  (b1 - b2)^2
)
```

Choose the compatible palette entry with the smallest distance.

### Deterministic tie-breaking

If two colours have the same or nearly the same distance:

1. Prefer an entry sharing the pigment's colour-family tag.
2. Prefer an explicit material priority value.
3. Prefer the lexicographically smaller stable ID.

The result must not depend on registry iteration order.

## 7.4 Compatibility filters

A palette entry may optionally restrict which pigments can select it.

```json
{
  "id": "ultima:silk_glacial",
  "allowed_pigment_tags": [
    "ice",
    "magical"
  ],
  "excluded_pigment_tags": [
    "mundane"
  ]
}
```

For the initial release, normal palette entries should remain unrestricted. Restriction support is mainly for future rare and special colours.

## 7.5 Suggested visual identity by material

These are art-direction guidelines, not hard-coded mathematical rules.

### Cotton

- Balanced saturation
- Broad, practical colour range
- Good baseline material
- Default material for admin-generated banners

### Wool

- Slightly warm, dense, and earthy
- Rich dark colours
- Softer highlights
- Some bright pigments may resolve to deeper shades

### Linen

- Muted and slightly desaturated
- Natural, dusty, and historical appearance
- Light colours may appear warm or creamy
- Dark colours should retain visible fibre texture

### Silk

- High chroma where appropriate
- Deep jewel tones
- Stronger highlights or sheen
- Rare dyes may appear especially vivid

These rules should guide palette authors. The game should resolve to authored palette entries, not generate every final colour by applying multipliers at runtime.

---

## 8. Dyeing Screen

## 8.1 Screen purpose

The screen exists to:

- Confirm the target item.
- Show its fabric material.
- Preview the resolved colour.
- Explain colour approximation when needed.
- Prevent accidental recolouring.

## 8.2 Suggested layout

```text
+--------------------------------------------------+
| Dye Banner                                      X |
|--------------------------------------------------|
| [Banner preview]   Ward of Serpents              |
|                    Material: Silk                 |
|                    Mount: Brass                   |
|                                                  |
| Tub Colour: Crimson Dye                          |
| Result: Ruby Silk                                |
| Match: Exact / Closest available                 |
|                                                  |
| [Current colour] -> [New colour preview]         |
|                                                  |
|              [Cancel]   [Apply Dye]              |
+--------------------------------------------------+
```

## 8.3 UI rules

- The preview must use the same rendering path as the final item whenever possible.
- The apply button is disabled if the tub is empty or the item is invalid.
- The screen displays `Exact match` when an explicit mapping exists.
- The screen displays `Closest available colour for Silk` when fallback matching is used.
- A re-dye operation should show both current and new colours.
- Special dyes may add a small rarity frame later, but rarity must not obscure the actual colour.
- The server re-validates all data when the player confirms.

## 8.4 No-choice versus choice mode

The user's current design implies that the tub contains one pigment and the material determines one result. In that model, the UI is a confirmation and preview screen, not a free colour picker.

A future multi-colour tub could allow the player to choose among stored pigments, but that should be a separate feature.

---

## 9. Texture and Rendering Pipeline

## 9.1 Recommended layer structure

The original two-texture concept is correct. A three-part asset structure is even safer:

1. **Fabric base** — grayscale fabric shading and weave.
2. **Dye mask** — defines which pixels receive tint.
3. **Static overlay** — heraldry, trim, stitching, symbols, damage, or colours that must not change.

Mount geometry and mount textures should be separate from the fabric layers.

### Rendering formula

Conceptually:

```text
tinted_fabric = fabric_base × resolved_colour
final_banner = composite(tinted_fabric through dye_mask, static_overlay)
```

If the static overlay has transparency, it is placed above the tinted fabric.

## 9.2 Why use a dye mask?

A dye mask allows a texture to contain:

- Dyeable fabric.
- Non-dyeable embroidery.
- Neutral shadows.
- Metallic thread.
- Painted emblems.
- Weathering or edge details.

A binary mask is sufficient for the first release. A grayscale mask can support partial tint strength later.

## 9.3 Item and block consistency

The item renderer and placed-banner renderer must read the same:

- Banner definition ID
- Material ID
- Resolved colour ID
- Mount ID

Do not maintain a separate item-only colour implementation. That creates visual mismatches and duplication.

## 9.4 Material rendering

Material identity may affect more than colour in future:

- Fabric normal map
- Roughness
- Sheen
- Weave scale
- Edge treatment
- Animation stiffness

For the first release, material may be represented only by palette differences and optional base texture variants.

A safe content structure is:

```text
banner design
  + material fabric texture
  + resolved colour
  + static design overlay
  + mount model/texture
```

## 9.5 Mount rendering

Mounts should be independent style variants:

```text
ultima:brass
ultima:iron
```

A mount definition may include:

```json
{
  "id": "ultima:brass",
  "display_name": "Brass Mount",
  "model_suffix": "_brass",
  "texture": "ultima:textures/banner_mount/brass.png",
  "tags": ["metal", "decorative"]
}
```

The mount changes the support hardware, not the fabric colour.

---

## 10. Banner Sizes, Placement, and Facing

## 10.1 Size model

The user-facing requirement includes banners that are:

- One block wide
- Two blocks wide
- Three blocks wide

Height should be defined per banner rather than inferred from width.

Suggested placement dimensions:

```json
{
  "width_blocks": 3,
  "height_blocks": 2,
  "depth_blocks": 1
}
```

The visible mesh may be thinner than a full block even though placement occupancy uses block cells.

## 10.2 Orientation values

Use stable enum-like values:

```text
wall_parallel
wall_perpendicular
```

### Wall parallel

The banner lies in a plane parallel to the supporting wall.

### Wall perpendicular

The banner projects outward from the wall, similar to a hanging sign or projecting standard.

A design may support one or both.

## 10.3 Placement validation

Before placement, the server checks:

1. The clicked surface is valid.
2. The banner supports the requested orientation.
3. Every occupied block cell is replaceable.
4. The supporting wall or anchor cells are valid.
5. No protected-region rule prevents placement.
6. The chosen mount is supported.
7. The banner does not exceed world bounds.

## 10.4 Anchor block approach

For multi-block banners, use one anchor block or block entity as the authoritative owner.

All other occupied cells are lightweight parts that reference the anchor.

Benefits:

- One colour state.
- One inventory drop.
- Easier updates.
- Fewer duplicated block entities.
- Clear breaking behaviour.

If any part is broken, the system should resolve the anchor and remove the entire banner.

## 10.5 Placement preview

A ghost preview is strongly recommended for two- and three-block banners.

The preview should indicate:

- Valid placement
- Blocked placement
- Orientation
- Width and height
- Mount style

---

## 11. Crafting and Material Assignment

## 11.1 Crafted banners

The banner's material is determined by the crafting recipe or crafting input, similar to UltimaCraft's blacksmithing material system.

Example outcome:

```text
Ward of Serpents pattern
+ silk fabric input
+ brass mounting components
= undyed silk Ward of Serpents banner with brass mount
```

The dye is applied after crafting.

## 11.2 Admin-generated banners

Admin-generated banners default to:

```text
material: cotton
colour: cotton natural / undyed
mount: banner definition default
```

Admin commands should allow explicit overrides.

Example conceptual command:

```text
/givebanner <player> <definition> [material] [colour] [mount]
```

Exact command syntax depends on the mod framework.

## 11.3 Recipe validation

A recipe must only create a banner when:

- The material is tagged as a valid fabric.
- The banner definition accepts that material, or has no restriction.
- The mount input resolves to a supported mount.
- Required design components are present.

For the first release, all four fabric materials can be allowed for all banners unless art limitations require exceptions.

---

## 12. Item Tooltips

A banner tooltip should expose its meaningful state.

Example:

```text
Ward of Serpents
Silk Banner
Colour: Ruby Silk
Dyed with: Crimson Dye
Mount: Brass
Size: 2 × 3 blocks
Placement: Parallel or Perpendicular
```

An undyed example:

```text
Iron Ward Auxiliary
Linen Banner
Colour: Natural Linen
Mount: Iron
Size: 1 × 2 blocks
Placement: Wall Parallel
```

A dye tub tooltip:

```text
Dye Tub
Contains: Crimson Dye
Uses: Unlimited
```

If fallback matching occurred, that detail belongs in the dyeing screen rather than permanently cluttering the tooltip.

---

## 13. Interaction State Machine

## 13.1 Dye tub loading

```text
EMPTY_TUB
  -> player uses with valid dye
LOADED_TUB

LOADED_TUB
  -> player uses with different valid dye
LOADED_TUB with replaced pigment

LOADED_TUB
  -> optional empty action
EMPTY_TUB
```

## 13.2 Banner dyeing

```text
IDLE
  -> valid banner in left hand
  -> loaded tub in right hand
PREVIEW_REQUESTED

PREVIEW_REQUESTED
  -> server validates
PREVIEW_OPEN

PREVIEW_OPEN
  -> player cancels
IDLE

PREVIEW_OPEN
  -> player confirms
SERVER_APPLY_REQUEST

SERVER_APPLY_REQUEST
  -> valid
BANNER_UPDATED

SERVER_APPLY_REQUEST
  -> invalid
ERROR_FEEDBACK
```

The server must not trust colour IDs sent by the client. The client sends intent; the server calculates or confirms the resolved result.

---

## 14. Networking and Multiplayer Authority

## 14.1 Server responsibilities

The server is authoritative for:

- Held-item validation
- Dye tub contents
- Material identity
- Palette lookup
- Colour resolution
- Consumption or use count
- Item mutation
- Block entity mutation
- Placement validation
- Drops
- Permissions

## 14.2 Client responsibilities

The client handles:

- Preview rendering
- UI
- Input
- Non-authoritative colour display
- Placement ghost
- Sounds and particles after server confirmation

## 14.3 Packet outline

Conceptual packets:

```text
C2S_RequestDyePreview
C2S_ConfirmDyeApplication
S2C_OpenDyePreview
S2C_DyeApplicationResult
S2C_SyncBannerState
```

Preview data from the server should include:

```json
{
  "target_slot": "off_hand",
  "material_id": "ultima:silk",
  "source_pigment_id": "ultima:crimson_dye",
  "resolved_colour_id": "ultima:silk_ruby",
  "match_type": "explicit"
}
```

---

## 15. Data Registries

Recommended registries:

```text
BannerDefinitionRegistry
FabricMaterialRegistry
PigmentRegistry
MaterialPaletteRegistry
MountRegistry
PlacementProfileRegistry
```

## 15.1 Suggested data folders

```text
data/ultima/banner_definitions/
data/ultima/fabric_materials/
data/ultima/pigments/
data/ultima/material_palettes/
data/ultima/banner_mounts/
data/ultima/placement_profiles/
```

Assets:

```text
assets/ultima/textures/banner/
assets/ultima/textures/banner_material/
assets/ultima/textures/banner_mount/
assets/ultima/models/banner/
assets/ultima/lang/
```

## 15.2 Validation at load time

On registry load, report:

- Duplicate IDs
- Missing textures
- Missing models
- Unknown material palette
- Invalid colour format
- Invalid OKLab values
- Unsupported width
- Empty orientation list
- Unknown mount
- Missing natural colour
- Palette entry without stable ID
- Explicit pigment mapping to a missing colour
- Placement profile too small for declared dimensions

Fail fast in development. In production, disable invalid entries and log a clear error rather than crashing the entire server where possible.

---

## 16. Suggested API Boundaries

The names below are conceptual.

```java
interface DyeableItem {
    MaterialId getMaterial(ItemStack stack);
    ResolvedColourId getResolvedColour(ItemStack stack);
    void setResolvedColour(ItemStack stack, ResolvedColourId colour);
    boolean canApplyPigment(ItemStack stack, PigmentId pigment);
}
```

```java
interface DyeResolver {
    DyeResult resolve(
        PigmentId pigment,
        MaterialId material
    );
}
```

```java
record DyeResult(
    ResolvedColourId colour,
    MatchType matchType,
    double distance
) {}
```

```java
interface BannerStateCodec {
    BannerInstanceState read(ItemStack stack);
    void write(ItemStack stack, BannerInstanceState state);
    BannerInstanceState read(BlockEntity blockEntity);
    void write(BlockEntity blockEntity, BannerInstanceState state);
}
```

```java
interface BannerPlacementService {
    PlacementResult validate(
        BannerDefinition definition,
        Position anchor,
        Direction facing,
        BannerOrientation orientation,
        MountId mount
    );
}
```

The exact language and APIs depend on the UltimaCraft codebase and mod loader.

---

## 17. Persistence and Versioning

Every stored state object should contain a schema version.

```json
{
  "schema_version": 1
}
```

This allows future migration when:

- IDs are renamed.
- Colour palettes are revised.
- Mount data changes.
- New material properties are added.
- A banner definition changes dimensions.
- Dye use counts are introduced.

### Persistence rule

Never store only a raw RGB value.

Store stable IDs for the material and resolved colour. A cached RGB value may be stored for convenience, but the ID must remain authoritative.

### Missing data fallback

If a stored colour ID is missing after an update:

1. Try resolving the stored source pigment against the stored material.
2. If that fails, use the material's natural colour.
3. Log a migration warning.
4. Do not delete the banner or crash the world.

---

## 18. Special and Rare Dyes

Rare-dye metadata is not required for the first release, but the model should support it.

Potential fields:

```json
{
  "rarity": "rare",
  "trade_category": "event",
  "historical_tags": [
    "legacy",
    "ice"
  ],
  "display_effect": "subtle_shimmer",
  "npc_price_class": "luxury",
  "restricted_palette_entries": [
    "ultima:silk_glacial",
    "ultima:cotton_frost"
  ]
}
```

Possible future categories:

- Common NPC dyes
- Crafted plant dyes
- Mineral dyes
- Faction dyes
- Event dyes
- Legacy Ultima-inspired dyes
- Magical dyes
- Ice dyes
- Metallic or pearlescent dyes

Rare dyes should still pass through the material system. An ice dye can have distinct cotton, wool, linen, and silk results rather than forcing every material to the same RGB value.

---

## 19. Art Production Workflow

## 19.1 Per banner design

Create:

1. Banner definition data.
2. Geometry/model.
3. Grayscale fabric base.
4. Dye mask.
5. Static overlay.
6. Brass mount presentation.
7. Iron mount presentation.
8. Inventory icon or dynamic item render.
9. Placement profile.
10. Localization entry.

## 19.2 Per material

Create:

1. Material definition.
2. Natural/undyed colour.
3. Palette.
4. Optional fabric texture or normal map.
5. Optional rendering properties.
6. Crafting tags and recipe integration.

## 19.3 Per pigment

Create:

1. Pigment definition.
2. Item registration.
3. Reference colour.
4. Tags.
5. Optional explicit material mappings.
6. NPC shop entry for the first release.
7. Localization entry.

## 19.4 Texture authoring rules

- Keep dyeable fabric near neutral grayscale.
- Avoid baking strong hue into the tintable base.
- Put non-dyeable colour into the static overlay.
- Use consistent value ranges across banners to avoid the same colour appearing radically brighter on one design.
- Test every palette on at least one large, one medium, one small, and one extra-small banner.
- Preserve readable heraldry at the smallest scale.
- Check brass and iron mounts against light and dark fabric colours.

---

## 20. Implementation Plan

## Phase 1 — Data model and proof of concept

Build:

- Fabric material registry
- Pigment registry
- Palette registry
- Dye resolver
- Dye tub state
- One test banner
- Item dyeing interaction
- Simple preview screen
- Item tooltip

Exit criteria:

- One banner can be cotton, wool, linen, or silk.
- One pigment produces different results by material.
- Unsupported exact colours resolve to the nearest palette entry.
- Colour persists across save and reload.

## Phase 2 — Block placement and rendering

Build:

- Banner definition registry
- Layered item renderer
- Layered block renderer
- Anchor-based multi-block placement
- Wall-parallel placement
- Wall-perpendicular placement
- Brass and iron mounts
- Break/drop state preservation

Exit criteria:

- A dyed item places with the correct appearance.
- Breaking the banner returns the same state.
- Multi-block banners place and break as one unit.
- Multiplayer clients see matching state.

## Phase 3 — Full banner catalogue

Build:

- All approximately 33 banner definitions
- One-, two-, and three-block widths
- Design-specific heights
- Placement profiles
- All static overlays and masks
- Catalogue names and localization
- Validation report

Exit criteria:

- Every reference-sheet banner is registered.
- Every banner passes placement tests.
- Every banner renders with both supported mounts.
- Each design has an explicit orientation list.

## Phase 4 — Crafting integration

Build:

- Material-aware crafting
- Default cotton admin generation
- Recipe validation
- Mount selection through crafting
- Admin give command options

Exit criteria:

- Crafted input material becomes banner material.
- Admin banners default to cotton.
- Colour is independent from crafting material.
- Crafted and admin-generated banners share the same data model.

## Phase 5 — Polish and future hooks

Build:

- NPC dye sales
- Special dye metadata
- Improved particles and sounds
- Placement preview
- Data migrations
- Developer documentation
- Automated registry validation

Optional:

- Direct dyeing of placed banners
- Dye use counts
- Emptying or washing a tub
- Dye crafting
- Rare visual effects

---

## 21. Testing Strategy

## 21.1 Colour resolution tests

Test:

- Explicit mapping wins.
- Nearest OKLab colour is selected.
- Tie-breaking is deterministic.
- Empty palette fails safely.
- Missing pigment fails safely.
- Missing material falls back or reports an error.
- Rare-colour restrictions work.
- Results remain identical on client and server.

## 21.2 Persistence tests

Test:

- Dyed banner survives relog.
- Dyed banner survives server restart.
- Item-to-block state is preserved.
- Block-to-item state is preserved.
- Re-dye replaces colour but not material.
- Mount remains unchanged after dyeing.
- Old schema migrates.

## 21.3 Placement tests

For each banner:

- All supported facings
- All supported orientations
- Brass mount
- Iron mount
- Valid wall
- Invalid wall
- Blocked occupied cells
- World border
- Protected area
- Breaking anchor
- Breaking child part
- Explosion or bulk removal
- Chunk unload and reload

## 21.4 Rendering tests

For each material:

- Natural colour
- Light dye
- Dark dye
- Highly saturated dye
- Near-neutral dye
- Rare dye
- Brass mount
- Iron mount

For each size group:

- Large
- Medium wall
- Medium
- Small
- Extra-small

## 21.5 Multiplayer tests

Test:

- Two players viewing one dyed banner.
- Simultaneous dye attempts.
- Item moved during confirmation.
- Tub colour changed during confirmation.
- Permission changes during placement.
- Client sends an invalid colour ID.
- Late-joining client receives correct block state.

---

## 22. Acceptance Criteria

The feature is ready for its first release when:

1. The banner catalogue is represented by stable data definitions.
2. Banners support one-, two-, and three-block widths as required.
3. Each banner explicitly declares wall-parallel and/or wall-perpendicular support.
4. Each banner supports its intended brass and iron/metal mount variants.
5. Crafted banners store their fabric material.
6. Admin-generated banners default to cotton.
7. A dye can be loaded into a dye tub.
8. A loaded tub can dye a banner held in the left hand.
9. The dyeing screen previews the material-specific result.
10. Cotton, wool, linen, and silk have distinct palettes.
11. Missing exact matches resolve to the nearest valid colour.
12. Re-dyeing is unlimited.
13. Colour persists on both item and placed block.
14. Breaking a banner returns an item with identical design, material, colour, and mount.
15. Static design details are not unintentionally tinted.
16. Multiplayer clients see the same result.
17. Invalid or missing data fails safely with clear logs.
18. New banners and colours can be added without modifying central switch statements.

---

## 23. Risks and Mitigations

## 23.1 Combinatorial asset workload

**Risk:** Thirty-three designs multiplied by materials, mounts, and placements could create excessive art work.

**Mitigation:** Reuse geometry where silhouettes match, separate mount assets, tint fabric at runtime, and keep state data-driven.

## 23.2 Tint quality varies by texture

**Risk:** The same palette colour looks inconsistent across banners.

**Mitigation:** Establish grayscale authoring standards and a shared preview scene for artists.

## 23.3 Multi-block desynchronization

**Risk:** Child blocks become orphaned or duplicate drops.

**Mitigation:** Use a single anchor block entity, lightweight child parts, and centralized break handling.

## 23.4 Palette changes alter existing items

**Risk:** Rebalancing a palette changes the appearance of previously dyed banners.

**Mitigation:** Store the resolved colour ID, not only the source pigment.

## 23.5 Client exploits

**Risk:** A modified client requests impossible rare colours.

**Mitigation:** The server resolves colours and validates the held items at confirmation time.

## 23.6 Too many special cases

**Risk:** Every banner becomes custom-coded.

**Mitigation:** Use definition data, placement profiles, and shared render/placement services. Add custom code only when a design cannot fit the general system.

---

## 24. Open Decisions

These decisions can be finalized during implementation:

1. Does loading a dye tub consume the dye item?
2. Does a tub have unlimited uses or a use count?
3. Can a loaded tub be emptied or washed?
4. Can players dye placed banners directly in the first release?
5. Does mount style come only from crafting, or can players swap it later?
6. Do all banner designs support both brass and iron mounts, or only a defined subset?
7. Will cotton, wool, linen, and silk use different fabric textures as well as different palettes?
8. Does the dyeing screen require a confirmation click for every use?
9. How are special dyes such as ice dye obtained and priced?
10. Should an approximation threshold reject a dye when no material colour is reasonably close?
11. Should undyed banners be dyeable with a “natural” or “bleach” item to restore their base colour?
12. Which Minecraft version, mod loader, networking API, and rendering system does UltimaCraft currently use?

None of these decisions prevents implementation of the core data model.

---

## 25. Recommended First Vertical Slice

Implement one complete path before producing all 33 designs.

Use:

- One medium wall banner
- Cotton and silk
- One ordinary red pigment
- One ice pigment
- Brass and iron mounts
- One wall-parallel placement
- One wall-perpendicular placement

The vertical slice should demonstrate:

```text
craft banner
-> material stored
-> load dye tub
-> open preview
-> resolve material colour
-> dye item
-> place multi-block banner
-> render layered texture
-> break banner
-> recover identical item
-> re-dye with another pigment
```

Once this path is stable, the remaining banner catalogue becomes mostly data and art production rather than new systems programming.

---

## 26. Final Architectural Recommendation

Build one reusable dyeing framework and one reusable banner framework.

The dyeing framework should know about:

```text
pigment
material
palette
resolved colour
dyeable item state
dye tub state
```

The banner framework should know about:

```text
design
dimensions
orientation
mount
texture layers
placement
item/block state conversion
```

They meet only through the banner's implementation of a generic dyeable-item interface.

That separation lets UltimaCraft later reuse the same dye system for clothing, carpets, curtains, tents, furniture fabric, ship sails, or other crafted textiles without rewriting banner-specific logic.
