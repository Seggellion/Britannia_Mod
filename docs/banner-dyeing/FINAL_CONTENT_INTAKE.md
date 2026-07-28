# Final Banner Content Intake

## 1. Purpose

Final banner content requires explicit product-owner approval and original project artwork. Codex may validate and
integrate approved inputs, but it may not invent artwork, names, dimensions, geometry, placement, orientations,
mounts, localization, provenance, or approval.

Every banner uses one architecture: a complete full-colour base texture plus a selective-recolour dye mask.

## 2. Stable identity

- Stable IDs do not normally change when artwork or display names change.
- Display names and localization may change after approval.
- Saved items and placed structures identify banners by stable ID.
- Milestone 16 Batch 1B is the explicit exception: `britannia_mod:x_small_unnamed_01` migrated to the canonical
  `britannia_mod:small_curtain`, and the legacy ID is decoded as the new ID for saved-item and placed-state
  compatibility.

## 3. Required owner decisions

Approve each value explicitly:

- Final display name and localization
- Width in blocks (`1`–`3`)
- Height in blocks (`1`–`2`)
- Supported orientations (`wall_parallel`, `wall_perpendicular`)
- Supported mounts (`britannia_mod:brass`, `britannia_mod:iron`)
- Default mount
- Placement profile
- Geometry convention and geometry resource
- Target content status
- Pixel dimensions for the two aligned images

These ranges and identifiers are supported runtime capabilities, not automatic approval of any banner decision.

## 4. Required visual assets

Supply exactly two aligned images:

- `base_texture.png`: complete authored full-colour default banner artwork
- `dye_mask.png`: grayscale RGBA selective-recolour layer

Supply geometry/model artwork when shared geometry is not explicitly approved. An editable authoring file and preview
image are optional references, not runtime image layers.

There is no optional overlay, legacy rendering strategy, per-banner strategy, or third runtime image.

## 5. Base-texture rules

The base texture must:

- Be the complete original default appearance
- Contain native colours, cloth texture, highlights, shadows, heraldry, borders, and fixed-colour details
- Contain the complete silhouette and required transparency
- Contain every pixel that must remain unchanged during dyeing
- Exclude brass and iron mount hardware
- Align exactly with the dye mask

It may be full-colour and must not be forced to grayscale. Cotton, wool, linen, and silk do not select different
banner textures; all materials use this same authored default artwork.

## 6. Dye-mask rules

The dye mask must:

- Be an 8-bit true-colour RGBA PNG whose active RGB is grayscale
- Use transparent pixels where the base must remain unchanged
- Use grayscale brightness to preserve highlights, midtones, shadows, texture, and local contrast
- Use alpha to control replacement or blend strength
- Contain at least one transparent pixel and at least one active pixel
- Use active alpha only where the aligned base pixel is fully opaque
- Align exactly with the base texture
- Exclude mount hardware

For validation, active RGB channels may differ by at most 1. A bright mask pixel produces a brighter dyed result; a
dark mask pixel produces a darker result. A flat white mask is valid only when deliberately approved as flat output.

## 7. Fixed artwork and the two-file limitation

Fixed artwork remains in the base texture. To prevent a pixel from changing, make the corresponding dye-mask pixel
transparent.

> A fixed-colour foreground detail cannot independently sit over a recoloured underlayer at the exact same pixel
> using the two-file architecture.

For current pixel-art banners, each pixel must be classified as fixed or recolourable. A future overlapping-detail
exception requires product-owner approval and a new architecture milestone.

## 8. Mount and material rules

Brass and iron mounts remain separate shared renderer layers. Banner image files must not include hardware, and
mounts are never tinted by banner dye.

Fabric material remains persisted data identity:

- `britannia_mod:cotton`
- `britannia_mod:wool`
- `britannia_mod:linen`
- `britannia_mod:silk`

Material controls natural colour, palette membership, dye resolution, tooltip state, administrative validation, and
future gameplay rules. It does not select a material-specific image or model.

## 9. File and composition requirements

- Use lower-case `.png` names and case-sensitive namespaced resource IDs.
- Use 8-bit true-colour RGBA PNGs with preserved alpha.
- Both images must have exactly equal pixel dimensions and pixel-for-pixel alignment.
- Keep the mask transparent wherever the aligned base alpha is below `255`, so two-pass blending preserves base alpha.
- Use nearest-neighbour/pixel filtering appropriate to authored pixel art.
- Avoid indexed-colour conversion when it damages alpha.
- Include no environmental background, external scene lighting, mockup, or source-sheet crop.
- Record repository-relative source paths and SHA-256 hashes after final edits.

Current diagnostic placeholders are 16 × 16. That is not final-art policy; final pixel dimensions require explicit
approval or a verified renderer convention.

Composition uses base pixel `B`, mask pixel `M`, resolved dye colour `D`, and normalized mask alpha `a`:

```text
T.rgb      = M.rgb × D.rgb
output.rgb = B.rgb × (1 - a) + T.rgb × a
output.a   = B.a
```

The default state renders the base unchanged. A recoloured state renders the tinted mask over the base.

## 10. Original-art provenance

Record:

- Artist or owner
- Creation method
- Editable source-project file location
- Confirmation that the artwork is original to the project
- Confirmation that the project may distribute it
- Confirmation that it was not copied from reference artwork

A concise, truthful provenance statement is sufficient.

## 11. Approval states

- `MISSING`: required input is absent.
- `AMBIGUOUS`: input exists but mapping or meaning is unclear.
- `PROVISIONAL`: useful current information that is not approved.
- `APPROVED`: the product owner has approved the exact value or asset.

Only `APPROVED` values may replace live catalogue values.

## 12. Content statuses and render state

- `placeholder`: shared diagnostic content
- `in_progress`: approved inputs are partly integrated; verification remains incomplete
- `complete`: automated and manual requirements passed and the owner approved the result
- `disabled`: excluded by production validation

Manual verification is mandatory before `complete`.

Recolouring is derived from existing state:

```text
apply_recolour =
    source_pigment_id is present
    OR resolved_colour_id differs from the selected material's natural_colour_id
```

A natural banner renders base plus mount. A pigment-dyed banner renders base, tinted mask, and mount—even when that
pigment resolves to the material’s natural colour. An administrative non-natural colour also renders the mask without
inventing pigment history.

## 13. Existing-world consequences

- Stable IDs preserve saved identity.
- Existing placed structures retain their persisted footprint and orientation.
- Changed definition dimensions affect new placement only.
- Existing structures are not automatically resized.
- Removed orientation or mount support affects new placement and validation only.
- Existing items and structures retain stored material, colour, pigment, mount, and structure state.
- Breaking and replacing may be necessary to adopt changed dimensions.

This two-file migration changes display metadata and rendering resources only. `BannerInstanceState` and placed
structure schemas remain unchanged.

## 14. Integration workflow

1. Owner fills one intake YAML.
2. Owner supplies `base_texture.png` and `dye_mask.png`.
3. Intake validation runs.
4. Codex audits provenance, mappings, mask semantics, and hashes.
5. Manifest is updated.
6. Definition becomes `in_progress`.
7. Generated outputs are refreshed.
8. Automated tests run.
9. Natural, recoloured, item, preview, placed, mount, reload, and persistence behaviour is reviewed.
10. Definition becomes `complete` only after product-owner approval.

`READY_FOR_INTEGRATION` authorizes integration work; it does not authorize `content_status: complete`.

## 15. Crafting boundary

```text
Banner crafting: not approved
Crafting requirements: not applicable
Admin acquisition: implemented
Survival acquisition: unresolved
```

Recipe completion is not a final-content criterion.
