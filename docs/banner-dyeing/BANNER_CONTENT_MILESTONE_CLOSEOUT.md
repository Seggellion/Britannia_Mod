# Banner Content Milestone Closeout

Date: 2026-07-30

Status: PASS — all banner families are integrated, live-reviewed, complete, and validated.

## Evidence boundary

The family Gate E records preserve product-owner visual evidence separately from automated repository evidence. Codex did not directly observe the Minecraft client. Seggellion supplied the Parallel Large PASS results against commit `598dde33b4f2f322f1ed32c4773b8ab69080eb22`; this closeout independently verified the tested bytes, catalogue, generated data, tests, and production packages.

## Final catalogue

- Active definitions: 35
- Complete: 35
- Placeholder: 0
- In progress: 0
- Disabled: 0
- Provisional names: 0
- Approved display names, dimensions, orientations, mounts, artwork, and intake packages: 35 of 35
- Active definitions using placeholder artwork: 0

The typed diagnostic placeholder resources remain packaged only for missing-content fallback. They are not active banner artwork.

## Family completion

| Family | Count | Canonical members | Orientation coverage | Gate E evidence | Result |
|---|---:|---|---|---|---|
| Extra-small | 9 | `road_guard`, `pale_road_guard`, `red_crosslets`, `captains_red_crosslets`, `scarlet_court`, `verdant_court`, `small_curtain`, `prosperity_standard`, `guardian_standard` | Parallel and perpendicular | `content/banner-final-intake/EXTRA_SMALL_GATE_E_REVIEW.md`; tested commit `bf68e4b0025f1aed9a905904a669a09f39e06d31` | PASS; complete |
| Small | 6 | `silver_and_gold_pennon`, `star_standard`, `ship_standard`, `pennon_of_silver`, `iron_ward`, `iron_ward_auxiliary` | Parallel and perpendicular | `content/banner-final-intake/SMALL_FAMILY_GATE_E_REVIEW.md`; tested commit `e4f457b132667efc0c9789ee6044c47bedf68bdc` | PASS; complete |
| Medium, perpendicular | 8 | `tournament_medium`, `ceremonial_tournament`, `iron_quarter`, `outer_ward`, `ward_of_serpents`, `serpent_guard`, `crossroad_guard`, `argent_shield` | Perpendicular only | `content/banner-final-intake/MEDIUM_FAMILY_GATE_E_REVIEW.md`; tested commit `79474963299603ae73b2efcaf58a9a8614dc881b` | PASS; complete |
| Medium, parallel | 6 | `verdant_grape_pennon`, `silver_rosette_pennon`, `four_seals_pennon`, `twin_spades_pennon`, `ankh_pennon`, `joined_wards` | Parallel only | `content/banner-final-intake/PARALLEL_MEDIUM_GATE_E_REVIEW.md`; tested commit `c61d8121d6d1224ea5647bedee8f3d13dd7af933` | PASS; complete |
| Large, parallel | 6 | `tournament_curtain`, `threefold_chain_standard`, `iron_serpent_standard`, `silver_fleur_curtain`, `gilded_trellis_curtain`, `gilded_chevron_curtain` | Parallel only | `content/banner-final-intake/PARALLEL_LARGE_GATE_E_CLOSEOUT.md`; tested commit `598dde33b4f2f322f1ed32c4773b8ab69080eb22` | PASS; complete |

No unsupported Large perpendicular definition or resource was introduced.

## Canonical stable-ID migrations

Compatibility aliases decode historical IDs, but only canonical IDs are active and generated:

- `x_small_unnamed_01` → `small_curtain`
- `end_01` → `star_standard`
- `end_02` → `ship_standard`
- `medium_wall_01` → `verdant_grape_pennon`
- `medium_wall_02` → `silver_rosette_pennon`
- `medium_wall_03` → `four_seals_pennon`
- `medium_wall_04` → `twin_spades_pennon`
- `medium_wall_05` → `ankh_pennon`
- `large_01` → `tournament_curtain`
- `large_02` → `threefold_chain_standard`
- `large_03` → `iron_serpent_standard`
- `large_04` → `silver_fleur_curtain`
- `large_05` → `gilded_trellis_curtain`
- `large_06` → `gilded_chevron_curtain`

`joined_wards` was already canonical at parallel Medium index 12.

## Final architecture

Every definition has exactly one complete full-colour `base_texture`, one grayscale-alpha `dye_mask`, one geometry identity, and one placement-profile identity. All final banner textures are 128 × 128, 8-bit RGBA.

Natural rendering uses the authored base unchanged plus an untinted mount. Recoloured rendering uses the same untinted base, a resolved-colour-tinted mask, and the untinted mount. Mask-transparent pixels preserve the base; mask grayscale preserves local brightness; mask alpha controls selective replacement/blending. There is no fabric-base field, static overlay, optional overlay, render strategy, material-specific texture, or banner-specific Java rendering switch.

Extra-small and Small placement profiles contain distinct parallel and perpendicular mount arrangements. Perpendicular Medium uses only its perpendicular mount arrangement. Parallel Medium and Large use only their parallel mount arrangement. Brass and iron remain separate untinted mount materials. The client asset index contains 70 unique final textures and 25 unique geometry models; the block atlas stitches the complete `banner/` texture directory. Geometry is indexed before model bake, typed missing-resource fallbacks remain available, and the banner instance and placed-structure persistence schemas are unchanged.

## Asset and catalogue audit

The closeout audit checked all 35 authoritative catalogue entries:

- 35 unique stable IDs and 35 unique catalogue indices
- 35 localization keys
- 35 existing placement profiles
- 105 matching catalogue/intake/runtime SHA-256 values: base, mask, and geometry for every definition
- 70 PNGs with 128 × 128 dimensions, 8-bit depth, and RGBA colour type
- 35 masks with transparent and active pixels, strictly grayscale RGB, and no mask alpha outside base alpha
- zero active provisional IDs and zero duplicate runtime definitions/resources

The six reviewed Large base, mask, and geometry hashes are unchanged from the tested integration commit. No artwork, geometry, placement, dimension, orientation, mount, catalogue index, display name, or stable ID changed during closeout.

## Validation

- Focused closeout regression: 57 tests across 6 suites; 0 failures, 0 errors, 0 skipped.
- Banner/dyeing suite: 661 tests across 62 suites; 0 failures, 0 errors, 0 skipped.
- Clean unrestricted suite: 667 tests across 63 suites; 0 failures, 0 errors, 0 skipped.
- `scaffold_banners.bat --check`: PASS for 35 active definitions.
- Consecutive normal scaffold generations produced identical generated state; idempotency PASS.
- `gradlew clean`: PASS.
- `gradlew build`: PASS.
- `git diff --check`: PASS at pre-commit validation.

The only source corrections during validation were a mechanically omitted Java quote in a newly expanded aggregate assertion and a stale 29-ID exact expected set; both were corrected before the clean suite. A short-timeout Gradle invocation briefly retained its own `output.bin`; after that process exited, validation reran successfully. The first ad hoc PowerShell audit assumed a newer JSON option and preferred a source geometry hash where Small Curtain explicitly records a distinct runtime hash; the read-only audit was corrected and then passed without repository data changes.

## Production JAR audit

| JAR | Size | SHA-256 | Entries | Duplicate names | Definition/status audit | Forbidden-content audit |
|---|---:|---|---:|---:|---|---|
| `build/libs/Britannia_Mod-0.1.7k.jar` | 23,020,048 bytes | `a0175d60fe82b6bbef1ba1c9b3e146ce08f6da86031e05445088d56c4b7576c3` | 5,130 | 0 | 35 definitions; 35 complete; 0 placeholder/in-progress/disabled | PASS |
| `build/libs/Britannia_Mod-0.1.7k-all.jar` | 23,591,686 bytes | `c5ab628c232c7e7655b6953f9af53342fb62522eac56ac1efab51cd1c5552a75` | 5,134 | 0 | 35 definitions; 35 complete; 0 placeholder/in-progress/disabled | PASS |

Both JARs contain all 70 final base/mask textures, all referenced geometry and placement profiles, parallel and perpendicular mount models, brass and iron models/textures, the deterministic client asset index, and the block-atlas definition. Neither contains static-overlay or fabric-base resources, banner recipes/pattern content, intake YAML, review artifacts, or provisional active definitions.

## Preservation and scope

- Approved artwork bytes: unchanged
- Geometry and placement resources: unchanged
- Stable IDs, indices, display names, dimensions, orientations, and mounts: unchanged
- Banner instance state, block-entity state, and placed-structure schemas: unchanged
- Crafting and pattern content: absent
- `.claude/` and `logs/`: untouched and unstaged
- Push, merge, release preparation, and Milestone 17: not performed

## Deferred work

Artist guidance for deliberate partial-alpha recolouring, any separately approved future need for overlapping fixed detail, survival acquisition, release policy, and other unrelated gameplay or administration decisions remain outside this content closeout. They do not reopen any banner family or final-content intake.

## Result

PASS — all banner families are integrated, live-reviewed, complete, and validated.

The Banner Content milestone is closed. Any merge, push, release preparation, or subsequent milestone requires separate explicit authorization.