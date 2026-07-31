# Saved Data and Migrations

## Current schemas

All current persisted banner-domain schemas are version 1:

- `BannerInstanceState`: definition ID, material ID, resolved colour ID, optional source pigment ID, and mount ID.
- `DyeTubState`: optional pigment ID and optional remaining uses; absent uses is the only unlimited form.
- `BannerPlacedStructure`: orientation, width, height, and a complete deterministic row-major rectangle.
- Banner definition and client asset index data are also schema 1.

Unknown schema versions, invalid resource locations, impossible tub states, and corrupt/incomplete placement
rectangles are rejected. Unknown extra JSON fields are tolerated by current codecs. A Milestone 10 block entity
with no placement tag is migrated to the documented 1 x 1 wall-parallel structure and written back at schema 1.

## Stable-ID migration

The following operator-obtainable provisional IDs existed from administrative commit
`502c79bd5aa4dd8fffec481cc07baa2ebb67c518` and are decoded through one centralized, idempotent alias map:

| Historical ID | Canonical released ID |
|---|---|
| `x_small_unnamed_01` | `small_curtain` |
| `end_01` | `star_standard` |
| `end_02` | `ship_standard` |
| `medium_wall_01` ... `medium_wall_05` | `verdant_grape_pennon`, `silver_rosette_pennon`, `four_seals_pennon`, `twin_spades_pennon`, `ankh_pennon` |
| `large_01` ... `large_06` | `tournament_curtain`, `threefold_chain_standard`, `iron_serpent_standard`, `silver_fleur_curtain`, `gilded_trellis_curtain`, `gilded_chevron_curtain` |

The migration changes only `banner_definition_id`. Material, resolved colour, source pigment, and mount are
preserved exactly. New writes always use canonical IDs. Unknown IDs are retained unchanged so a restored data pack
can recover them.

`prosperity_standard` and `guardian_standard` were appended as new definitions and have no predecessor aliases.

## Colour recovery

A present, active source pigment can safely re-resolve a missing colour under current registry data. With no source
pigment, or an unavailable source pigment, there is no authoritative reconstruction: validation remains invalid,
raw IDs stay intact, and repair fails without guessing a natural colour.

Migration, missing-content, and malformed-load diagnostics are deduplicated and bounded.
