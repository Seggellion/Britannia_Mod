# Shrine placeholder assets

These files are temporary development assets authorized by the project owner on 2026-08-03.
They are not final approved art and are intended to be replaced in place without changing stable variant IDs.

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
