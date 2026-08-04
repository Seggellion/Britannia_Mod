# Provisional shrine and monolith assets

Following the corrective Milestone 4 review, the project owner explicitly approved these exact files for repository and Milestone 4 use on 2026-08-03: "I like the placeholder assets, use them."
They remain intentionally replaceable development art rather than a permanent final-art commitment. Replacement must occur in place without changing stable variant IDs unless a later approved migration says otherwise.

## Shared geometry

- `assets/britannia_mod/geo/shrine.geo.json` is a static GeckoLib placeholder altar.
- It is authored facing North, with Y up and the anchor at the lower front-left occupied cell.
- Model extents are X `[-24, 8]`, Y `[0, 16]`, Z `[-8, 24]` model units.
- GeckoLib translates to the anchor cell center before rotating: North `0`, East `-90`, South `180`, West `90` degrees around +Y.
- `assets/britannia_mod/geo/shrine_missing.geo.json` is a bounded diagnostic cube, not shrine art.

## Variant textures

The nine 128 by 128 PNG files under `assets/britannia_mod/textures/block/shrine/` were generated with OpenAI ImageGen for temporary development use:

- `honesty.png`
- `compassion.png`
- `valor.png`
- `justice.png`
- `sacrifice.png`
- `honor.png`
- `spirituality.png`
- `humility.png`
- `chaos.png`

Each texture is mapped directly from its stable lowercase variant ID. Replacements must keep the same file names and dimensions unless the geometry manifest is intentionally updated at the same time.

## Item presentation

There remains exactly one registered configured shrine item. Its generated item model uses the Honesty texture as a temporary family-level icon; this does not change or default the configured item component to Honesty.

## Milestone 6 diagnostic monolith

On 2026-08-03 the project owner accepted the original missing-asset blocker and explicitly authorized creation of exactly one provisional diagnostic package for family `monolith`, variant `diagnostic_missing_content`. These files are implementation diagnostics, not final artwork:

- Geometry: `assets/britannia_mod/geo/monolith_diagnostic.geo.json` (1,007 bytes; SHA-256 `0D58B1ED73A8811E10B276DB7E55B29F7248DD047074C0FE786882DF0757E53C`).
- Texture: `assets/britannia_mod/textures/block/monolith/diagnostic_stone.png` (2,034 bytes; SHA-256 `FE07CE0672EE51D76F2833D1044264C7B65B2ADEB076873D1B07C953509944F4`).
- Required static GeckoLib resource: `assets/britannia_mod/animations/monolith.animation.json` (84 bytes; SHA-256 `D63D4CBCEF6A3E410EE94F38F5684F7B3E9F94BCC69B4D80C925FBBB61FC1530`).
- Vanilla-backed item presentation: `assets/britannia_mod/models/item/monolith.json` (40 bytes; SHA-256 `E48339859F7AA66BBC08246B8BC65AC1827E28241509518F9884A854739A2BED`). It inherits `minecraft:block/stone`; no separate item icon was created.

The static model validates the logical `3 x 3 x 2` footprint without defining collision. Its exact authored cube bounds are X `[-8, 40]`, Y `[-16, 32]`, and Z `[-8, 24]` model voxels. The model origin is the lower front-left anchor cell; local X is viewer-right, local Y is up, and local Z is away from the viewer. Its single bone pivot is `[0, -16, 0]`. Default forward is North, with the front rib toward negative Z. GeckoLib rotates the anchor render around positive Y as North `0`, East `-90`, South `180`, and West `90` degrees.

The lowest authored Y is deliberately `-16`. The renderer translates exactly `[0, +1, 0]` Minecraft blocks (`[0, +16, 0]` model voxels) once, placing the visual base at Y `0` without moving the anchor, footprint, persisted offsets, collision, or selection. The measured post-translation visual extent is Y `[0, 48]` voxels. Finite all-facing render bounds are X/Z `[-2, 3]` and Y `[0, 3]` blocks, expanded only by the existing `1/128` block tolerance.

The texture is exactly 32 by 32 pixels, neutral stone, and fully opaque; all four cubes use the texture's origin UV `[0, 0]` with GeckoLib's box-UV expansion. It contains no text, shrine texture reuse, virtue symbol, ankh, human figure, or alternate palette. The model has no animation; the empty animation file exists only because the current `GeoModel` contract requires an animation resource.

Replacement of any file in this package requires future owner approval. This authorization creates no second monolith variant and grants Milestone 7 no additional model, texture, cycling, or final-art scope. Actual controlled in-world visual-horizon alignment remains `UNVERIFIED`.
