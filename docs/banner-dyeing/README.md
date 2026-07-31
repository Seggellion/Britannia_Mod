# Banner Dyeing

Banner dyeing is a data-driven, server-authoritative system for 35 released banner definitions. Every released
definition is `complete`; crafting remains product-disabled. Operators obtain banners and dye tubs through the
`/britannia` commands documented below.

## Release contract

- Minecraft 1.21.1, NeoForge 21.1.72.
- 35 active definitions, 0 disabled, 0 placeholder or in-progress definitions.
- Four materials and palettes: cotton, linen, silk, and wool.
- Seven pigments and two physical mounts: brass and iron.
- 70 banner PNGs, each 128 x 128 RGBA: one complete `base_texture` and one `dye_mask` per definition.
- One shared banner item, one anchor block entity per placed banner, and non-entity part blocks for the remaining
  footprint.
- Persisted colour authority is `resolved_colour_id`; `source_pigment_id` is optional provenance. RGB values are
  display data and are never persisted in banner state.

The machine-readable release lock is `content/banner_release_contract.json`; the complete definition inventory is
`content/banner_catalogue.yml`. Release readiness is recorded in `GATE_F_RELEASE_REVIEW.md`.

## Operator commands

Run `/britannia banner validate` after startup and after `/reload`. It must report 35 active, 0 disabled, and no
errors.

Create a natural banner:

```text
/britannia banner give @s britannia_mod:road_guard britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass
```

Create a banner with an explicit released colour:

```text
/britannia banner give @s britannia_mod:road_guard britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:iron
```

Create a loaded dye tub:

```text
/britannia dye tub give @s britannia_mod:woad_blue
```

Hold the tub in the main hand and the banner in the off hand. The server creates an opaque, one-use preview
session. Cancel never mutates either stack; confirm revalidates player, session, both hands, registry generation,
resolved result, and remaining uses before applying.

## Documentation map

- `ADDING_A_BANNER.md`: catalogue, resource, intake, scaffold, and Gate E workflow.
- `ADDING_A_MATERIAL.md`, `ADDING_A_PIGMENT.md`, `ADDING_A_PALETTE_COLOUR.md`, `ADDING_A_MOUNT.md`: supported data
  extension workflows.
- `SAVED_DATA_AND_MIGRATIONS.md`: schemas, compatibility rules, recovery, and approved legacy aliases.
- `TEST_PLAN.md`: automated, local, multiplayer, reload, lifecycle, and Gate F checks.
- `COMPATIBILITY.md`: server/client authority, resource packs, data packs, and unsupported combinations.
- `RELEASE_NOTES_DRAFT.md`: product-owner draft; no version change or release is implied.

## Failure model

Missing definitions, materials, palettes, pigments, mounts, geometry, base textures, masks, or placement profiles
produce typed diagnostics and deterministic fallback rendering where applicable. Stored stable IDs are retained.
No repair guesses a colour: a missing colour can be re-resolved only when a still-active source pigment provides
authoritative provenance. Unknown schema versions and structurally corrupt data are rejected.
