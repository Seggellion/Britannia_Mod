# Owner-approved shrine and provisional monolith assets

Following the corrective Milestone 4 review, the project owner approved the original provisional shrine
files for repository use. Corrective Milestone 9A later replaced their visible geometry and nine virtue
textures with owner-supplied files derived from `Shrine_old.json`; the owner separately approved the
resulting flat model, granite exterior, corrected UV rows, and 14/15-voxel profile. The monolith files
remain explicitly provisional development art. Future replacement must preserve stable IDs unless a
separately approved migration says otherwise.

## Shared geometry

- `assets/britannia_mod/geo/shrine.geo.json` is the current static owner-supplied GeckoLib shrine.
- It is authored facing North, with Y up and the anchor at the lower front-left occupied cell.
- Corrective Milestone 9A aligns the geometry with the lower-front-left anchor after GeckoLib's
  Bedrock-X mirroring. Model extents are X `[-7, 23]`, Y `[0, 15]`, Z `[-7, 23]` model units.
- The 30 by 30 model-voxel base leaves a symmetric one-voxel inset from the four horizontal
  footprint edges. Its effective presentation is 1.875 by 1.875 blocks, centered in the logical
  2 by 2 footprint for all four facings.
- Four equal `13 x 14 x 13` surface cubes sit one model voxel below four 15-voxel-high granite rim
  pieces. Each 128 by 128 virtue top is divided into four exact 64 by 64 quadrants; the raw-Z UV rows
  are intentionally swapped so the complete symbol is oriented correctly in world space.
- Current SHA-256 is `374E8455075B8EFFCDFE4E36432FB9254A112F777141D319F90B5545EA92AB51`.
- GeckoLib translates to the anchor cell center before rotating: North `0`, East `-90`, South `180`, West `90` degrees around +Y.
- `assets/britannia_mod/textures/block/shrine/granite.png` is the separate owner-supplied granite
  material rendered only on the `granite_rim` bone; SHA-256 is
  `A1D3C1A881B6DC6990EB56932B702CDA78AE0BBF10355FDA90B8A3133B4CCA77`.
- `assets/britannia_mod/geo/shrine_missing.geo.json` is a bounded diagnostic cube, not shrine art.

## Variant textures

The nine 128 by 128 PNG files under `assets/britannia_mod/textures/block/shrine/` are the exact
owner-supplied virtue textures installed during corrective Milestone 9A:

- `honesty.png`
- `compassion.png`
- `valor.png`
- `justice.png`
- `sacrifice.png`
- `honor.png`
- `spirituality.png`
- `humility.png`
- `chaos.png`

Each texture is mapped directly from its stable lowercase variant ID. Their exact hashes are recorded
in `CONTENT_REPORT.json`. The later UV/height correction changed only `shrine.geo.json`; all ten
owner-supplied shrine PNGs, including granite, remained byte-identical. Replacements must keep the same
file names and dimensions unless geometry and content evidence are intentionally updated together.

## Item presentation

There remains exactly one registered configured shrine item. Its generated item model uses the current
owner-supplied Honesty texture as the family-level icon; this does not change or default an arbitrary
configured component because the Creative-tab stack is explicitly configured as `shrine/honesty`.

## Milestone 6 diagnostic monolith

On 2026-08-03 the project owner accepted the original missing-asset blocker and explicitly authorized creation of exactly one provisional diagnostic package for family `monolith`, variant `diagnostic_missing_content`. These files are implementation diagnostics, not final artwork:

- Geometry: `assets/britannia_mod/geo/monolith_diagnostic.geo.json` (1,007 bytes; SHA-256 `0D58B1ED73A8811E10B276DB7E55B29F7248DD047074C0FE786882DF0757E53C`).
- Texture: `assets/britannia_mod/textures/block/monolith/diagnostic_stone.png` (2,034 bytes; SHA-256 `FE07CE0672EE51D76F2833D1044264C7B65B2ADEB076873D1B07C953509944F4`).
- Required static GeckoLib resource: `assets/britannia_mod/animations/monolith.animation.json` (84 bytes; SHA-256 `D63D4CBCEF6A3E410EE94F38F5684F7B3E9F94BCC69B4D80C925FBBB61FC1530`).
- Vanilla-backed item presentation: `assets/britannia_mod/models/item/monolith.json` (40 bytes; SHA-256 `E48339859F7AA66BBC08246B8BC65AC1827E28241509518F9884A854739A2BED`). It inherits `minecraft:block/stone`; no separate item icon was created.

The static model validates the logical `3 x 3 x 2` footprint without defining collision. Its exact authored cube bounds are X `[-8, 40]`, Y `[-16, 32]`, and Z `[-8, 24]` model voxels. The model origin is the lower front-left anchor cell; local X is viewer-right, local Y is up, and local Z is away from the viewer. Its single bone pivot is `[0, -16, 0]`. Default forward is North, with the front rib toward negative Z. GeckoLib rotates the anchor render around positive Y as North `0`, East `-90`, South `180`, and West `90` degrees.

The lowest authored Y is deliberately `-16`. The renderer translates exactly `[0, +1, 0]` Minecraft blocks (`[0, +16, 0]` model voxels) once, placing the visual base at Y `0` without moving the anchor, footprint, persisted offsets, collision, or selection. The measured post-translation visual extent is Y `[0, 48]` voxels. Finite all-facing render bounds are X/Z `[-2, 3]` and Y `[0, 3]` blocks, expanded only by the existing `1/128` block tolerance.

The texture is exactly 32 by 32 pixels, neutral stone, and fully opaque; all four cubes use the texture's origin UV `[0, 0]` with GeckoLib's box-UV expansion. It contains no text, shrine texture reuse, virtue symbol, ankh, human figure, or alternate palette. The model has no animation; the empty animation file exists only because the current `GeoModel` contract requires an animation resource.

Replacement of any existing file in this package requires future owner approval. Actual controlled in-world visual-horizon alignment remains `UNVERIFIED`.

## Milestone 7 diagnostic alternate monolith

On 2026-08-03 the project owner approved exactly one additional provisional diagnostic package for family `monolith`, stable technical ID `diagnostic_alternate`. This is model-cycling implementation content, not final artwork or lore, and it does not authorize a third variant.

- Geometry: `assets/britannia_mod/geo/monolith_diagnostic_alternate.geo.json` (1,145 bytes; SHA-256 `B2FCBE6841C53313A754A9722E5633957A9AB0334FB96FF05FBCFC42BA7512DC`).
- Texture: `assets/britannia_mod/textures/block/monolith/diagnostic_alternate_stone.png` (1,900 bytes; SHA-256 `E320B72F2D0B9429D07DD4E62D264535760797AE6D1F9DD047C082B6736A2C4A`).
- Localization identity: `structure.britannia_mod.monolith.diagnostic_alternate` with provisional label `Diagnostic Monolith Alternate`.
- No second animation or item presentation was created. The alternate reuses the technically required static `assets/britannia_mod/animations/monolith.animation.json` and existing vanilla-backed monolith item model.

The alternate is a five-cube stepped and tapered upright monument, visibly different in silhouette from the four-cube existing diagnostic. Its exact authored union remains X `[-8, 40]`, Y `[-16, 32]`, and Z `[-8, 24]` model voxels. Its single bone pivot is `[0, -16, 0]`; model origin and axes retain the lower-front-left contract. Default forward is North, made readable by the asymmetric front ridge at negative Z with origin `[10, -6, -6]` and size `[12, 28, 4]`. It contains no shrine, virtue, ankh, human, text, or copied decoration.

The texture is exactly 32 by 32 pixels and fully opaque. It uses dark cool layered stone with restrained mineral variation and no embedded text, symbol, icon, figure, lighting, or perspective. OpenAI ImageGen created the source raster at `C:\Users\dusti\.codex\generated_images\019fc9b3-b045-75a0-8d82-8e25a9fe1763\exec-f07925ea-55f7-4db6-93c2-5fb2bbf516ef.png`; the repository asset was deterministically reduced to the final 32 by 32 opaque PNG path above. The final generation prompt requested one square diffuse game texture of restrained dark cool layered stone, flat/even lighting, broad readable variation, and explicitly prohibited text, runes, icons, symbols, shrine or virtue imagery, ankh, human figures, faces, ornament, carved decoration, lore motifs, borders, perspective, and transparency.

Both enabled provisional variants use the same ordered eighteen-cell `3 x 3 x 2` footprint, floor placement, cell-bounded collision, lower-front-left render origin, and exact `[0, +16, 0]` voxel display correction. Their measured extents are equal, so the existing finite all-facing union bounds remain X/Z `[-2, 3]` and Y `[0, 3]` blocks plus `1/128` tolerance. The renderer applies the correction once at the shared anchor; collision and occupied cells remain unchanged.

The explicit catalogue cycle is `diagnostic_missing_content -> diagnostic_alternate -> diagnostic_missing_content`. Replacement of either provisional package or addition of a third variant requires later owner approval. Live visual distinction, horizon alignment, culling, and interaction remain `UNVERIFIED` despite resource parsing and transform tests.
