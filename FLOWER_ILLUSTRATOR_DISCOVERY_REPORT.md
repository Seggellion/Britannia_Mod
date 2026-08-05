# Flower Illustrator Discovery Report

## Milestone status

Milestone 18 discovery and Milestone 19 integration are **complete**. Milestone 19 was committed locally as `2e5ff48eba79c8f547ef1fe2379b1d89ec9edcc3`. After Milestone 20, the owner requested that dye tint preserve the base artwork's detail. The source-authoritative correction now restores the detailed originals as each authored-mask stage's `base_texture` and retains the former alpha-disjoint rasters as hidden reversible objects. The correction and its validation remain uncommitted for owner review.

The refreshed source matches the owner's approvals:

- `lilly -> lily` is approved.
- The source now uses the exact container name `orfluer`; its repository mapping is approved.
- All 33 authored masks are now flat white `RGB(255,255,255)`.
- Hyacinth stages 6 and 7 are visible and resized to approximately 131.872 x 131.872 points.
- The 128 x 128 Artboard 1 crop is approved.
- Sixteen base-only stages will retain transparent runtime mask placeholders.
- Snowdrop `back` and `front` are approved as `NON_EXPORT`.
- Native Illustrator export settings and external source retention are approved.

No texture was exported during this discovery refresh.

## Repository baseline

- Workspace: `C:\projects\britannia\mod\Britannia_Mod_farming_codex`
- Exact owner-approved branch: `Farming`
- HEAD: `142712bbe9acb2d29b65e4e85ab56ce5a49e0ed3`
- Tracked working-tree changes: none
- Existing untracked project documents were preserved.

The milestone document's lowercase `farming` gate is superseded by the owner's established case-sensitive `Farming` branch instruction.

## Authoritative source revision

| Property | Initial discovery source | Current authoritative source |
|---|---|---|
| Path | `C:\projects\britannia\raw fiels\flowers.ai` | same |
| Size | 78,138,903 bytes | 80,453,426 bytes |
| Last modified (UTC) | `2026-08-04T21:47:34.8107050Z` | `2026-08-05T03:53:13Z` |
| SHA-256 | `340D14CFB6DC103ED90395BFFA3200B0F2AD84D88C34A4FBAD941AEA626BC62C` | `1B5407855D448B8385CC6D067858A347DF3C0B0993B390382A36EE1D42C0A9EE` |
| Status | superseded by owner corrections | authoritative detail-preserving tint source |

The prior alpha-disjoint source is retained byte-for-byte at `C:\projects\britannia\raw fiels\codex_backups\flowers_pre_detail_preserving_tint_20260804_205234.ai` with SHA-256 `00E0C2F5CE56422361FC88C7F8F0BAD20A337B1FD85F33ED4252B6680F22A223`. The earlier owner-resaved source also remains at `C:\projects\britannia\raw fiels\codex_backups\flowers_pre_base_transparency_20260804_181043.ai` with SHA-256 `6E3DD39B95DA983B317D5F379F33F4BBD08A2E7FF72B7B2FD874DF4D3CFD4048`.

## Refreshed Illustrator inventory

| Property | Finding |
|---|---|
| Illustrator | Adobe Illustrator 2026, version 30.7 |
| Document | PDF-compatible AI; one artboard |
| Color mode/profile | RGB; current AI is untagged after scripted save; PNG numeric RGB is treated as sRGB |
| Artboard | `[0,0,128,-128]`, 128 x 128 points |
| Top-level layers | 7 |
| Stage sublayers | 49 |
| Named export base RasterItems | 49 |
| Hidden embedded original base RasterItems | 33 |
| Authored mask GroupItems | 33 |
| Approved base-only stages | 16 |
| Hidden stage layers | 0 |
| Locked stage layers/items | 0 |
| Placed/linked assets | 0 |
| Text frames | 0 |
| Extra stage layers | 0 |
| Duplicate base/mask candidates | 0 |

Top-level order:

1. `hyacinth`
2. `lilly` (owner-approved repository alias `lily`)
3. `campion`
4. `poppy`
5. `orfluer`
6. `foxglove`
7. `snowdrop`

Poppy retains source names `stage1` through `stage6` and `stage_7`; the numeric stage mapping is unambiguous.

## Approved pairing coverage

| Source container | Repository species | Base stages | White mask stages | Base-only stages |
|---|---|---|---|---|
| `hyacinth` | `hyacinth` | 1-7 | 3-7 | 1-2 |
| `lilly` | `lily` | 1-7 | 3-7 | 1-2 |
| `campion` | `campion` | 1-7 | 3-6 | 1-2, 7 |
| `poppy` | `poppy` | 1-7 | 3-6 | 1-2, 7 |
| `orfluer` | `orfluer` | 1-7 | 3-7 | 1-2 |
| `foxglove` | `foxglove` | 1-7 | 2-6 | 1, 7 |
| `snowdrop` | `snowdrop` | 1-7 | 3-7 | 1-2 |
| **Total** | **7 species** | **49** | **33** | **16** |

Every stage has exactly one named embedded `base_texture` RasterItem. On each of the 33 authored-mask stages, the export base is the detailed high-resolution original and the former alpha-disjoint 128 x 128 raster remains hidden as `base_texture_alpha_disjoint_nonexport`. Authored masks are named `dye_mask` vector GroupItems. Every mask path is filled `RGB(255,255,255)`, normally blended at 100% opacity in Illustrator, with no strokes or clipping paths. Runtime opacity is controlled separately by the renderer.

The detail-restored source was saved and exported after a complete preflight: 49 stage layers, 49 export bases, 33 white masks, and 33 hidden alpha-disjoint archive rasters. Pixel validation found 58,516 visible mask pixels with detailed base beneath them, with varied underlying base colours on every authored-mask stage.

Under the owner's override, a base-only stage is complete. Its base may be exported while its existing transparent mask placeholder remains unchanged.

## Geometry and visibility resolution

- Hyacinth 6/7 are now visible and unlocked.
- Their previous 1,024 x 1,024 point bases are now approximately 131.872 x 131.872 points with bounds near `[-3.404,0.191,128.468,-131.681]`.
- Campion 5 and Orfluer 4/7 remain non-square source objects.
- Many source objects extend beyond the artboard.
- The owner explicitly approved Artboard 1 as the canonical 128 x 128 crop, so these bounds no longer block export.
- Snowdrop's hidden embedded `back` and `front` rasters remain outside stage layers and are approved `NON_EXPORT`.

## Existing repository asset audit

The repository still satisfies the Corrective Milestone 11 contract:

- 49 canonical stage models
- 49 existing base PNG placeholders
- 49 existing mask PNG placeholders
- one canonical model per species/stage
- no base-pass or mask-pass duplicate models
- model parent `britannia_mod:block/flowers/shared/multi_plane`
- model keys `flower`, `dye_mask`, and base-aliased `particle`
- renderer performs base and mask passes on identical canonical geometry
- blockstates reference only canonical stage models
- all current PNGs are 128 x 128

Model and texture paths already match the approved destination convention. Milestone 19 should replace PNGs only and avoid model JSON churn.

## Approved export policy

| Decision | Approved value |
|---|---|
| Tool | Native Illustrator 30.7 scripting |
| Isolation | Exact named object within its approved stage layer |
| Canvas | Artboard 1 |
| Dimensions | 128 x 128 pixels |
| Format | PNG24 with transparency |
| Color | preserve sRGB numeric RGB values; current AI is untagged RGB after scripted save |
| Alpha | Straight alpha |
| Antialiasing | Art Optimized |
| Crop/rescale | Artboard crop only; no post-export crop or rescale |
| Authored masks | Preserve white pixels, alpha, silhouette, and padding |
| Base-only masks | Retain existing transparent placeholder |
| Snowdrop extra items | NON_EXPORT |
| Source retention | External AI remains authoritative and outside repository; prior revision retained in `codex_backups` |

## Owner decision register

| ID | Decision | Status |
|---|---|---|
| ILLUSTRATOR-001 | authoritative source is external `flowers.ai`; current hash `1B540785...A9EE` | APPROVED |
| ILLUSTRATOR-002 | `lilly -> lily`; exact `orfluer -> orfluer` mapping | APPROVED |
| ILLUSTRATOR-003 | 49 bases, 33 masks, 16 base-only placeholder-retention mappings | APPROVED |
| ILLUSTRATOR-004 | native Illustrator scripting | APPROVED |
| ILLUSTRATOR-005 | Artboard 1 crop | APPROVED |
| ILLUSTRATOR-006 | 128 x 128 PNG | APPROVED |
| ILLUSTRATOR-007 | sRGB | APPROVED |
| ILLUSTRATOR-008 | straight alpha | APPROVED |
| ILLUSTRATOR-009 | Art Optimized; no independent rescale | APPROVED |
| ILLUSTRATOR-010 | all stages exportable; Snowdrop extras NON_EXPORT | APPROVED |
| ILLUSTRATOR-011 | flat-white authored masks | APPROVED AND VERIFIED |
| ILLUSTRATOR-012 | transparent placeholders retained for 16 base-only stages | APPROVED |
| ILLUSTRATOR-013 | no extra Illustrator stages | APPROVED |
| ILLUSTRATOR-014 | source remains external and authoritative; alpha-disjoint correction completed | HISTORICAL |
| ILLUSTRATOR-015 | mask-selected base pixels transparent; hidden detailed originals retained | SUPERSEDED |
| ILLUSTRATOR-016 | detailed originals restored as export bases; former alpha-disjoint rasters retained hidden; runtime masks use 50% opacity | APPROVED BY OWNER REQUEST AND VERIFIED |

## Milestone 18 closeout

- Milestone: 18 - refreshed discovery and approval
- Branch and HEAD before/after: `Farming` at `142712bbe9acb2d29b65e4e85ab56ce5a49e0ed3`
- Source Illustrator path: `C:\projects\britannia\raw fiels\flowers.ai`
- Approved source hash: `00E0C2F5CE56422361FC88C7F8F0BAD20A337B1FD85F33ED4252B6680F22A223`
- Layers discovered: 56 relevant layers
- Complete conventional base/mask pairs: 33
- Approved base-only mappings: 16
- Ambiguous mappings: 0
- Missing bases: 0
- Missing masks under owner override: 0
- Extra stage layers: 0
- Canonical models mapped: 49
- Textures exported in Milestone 18: 0
- Owner decisions required: none

Milestone 18's hard stop was cleared for Milestone 19. The later source edit was explicitly owner-authorized and is recorded above. Milestone 19 was committed locally; no push, fetch, or GitHub access occurred.

## Milestone 20 validation closeout

- Validation baseline: local `Farming` commit `2e5ff48eba79c8f547ef1fe2379b1d89ec9edcc3`.
- Authoritative source rechecked at `C:\projects\britannia\raw fiels\flowers.ai`; SHA-256 remains `00E0C2F5CE56422361FC88C7F8F0BAD20A337B1FD85F33ED4252B6680F22A223`.
- Fresh native Illustrator re-export: 49 bases and 33 authored masks; production comparison found 0 byte mismatches.
- Hidden-original comparison: 58,516 mask-selected source pixels are transparent in corrected bases; 0 alpha changes and 0 visible-RGB changes outside selected regions.
- Repository graph: exactly 49 canonical models, 49 bases, and 49 masks; no pass-specific models, stale flower resources, or orphan paths.
- Pixel contract: 98/98 PNGs at 128 x 128; 33 white authored masks; 16 transparent placeholders; 0 base/mask overlap pixels.
- Live dedicated-server fixture: all seven species and stages 1-7, including Poppy stage 7, validated at 49/49 before and after a clean save/restart.
- Live clients: front, side, elevated, Poppy, post-resource-reload, overlapping two-client, and post-restart views were captured and inspected. No missing model, cross-species mapping, mirroring, halo, seam, static Z-fighting, clipping defect, or tint leakage was observed.
- Full regression: 15 suites / 108 tests, 0 failures, 0 errors, 0 skipped.

Runtime limitations are static visual inspection rather than a continuous-motion flicker or GPU-performance measurement. Moving clouds and lighting changed exact screenshot bytes; fixture luminance/edge structure remained strongly correlated across clients (`0.9994` / `0.9997`) and before/after reload (`0.9790` / `0.9887`). Pre-existing unrelated resource warnings and unavailable localhost service responses were observed and left out of scope.

### Authoritative re-export instructions

1. Open the external `flowers.ai` in Illustrator and verify the 49 named detailed `base_texture` objects, 33 flat-white `dye_mask` groups, 33 hidden `base_texture_alpha_disjoint_nonexport` objects, and 16 approved base-only stages.
2. Export each visible object in isolation against Artboard 1 as transparent PNG24, 128 x 128, straight alpha, Art Optimized antialiasing, with no post-export crop or rescale. Treat numeric RGB as sRGB; retain the existing transparent mask file for the 16 base-only stages.
3. Map `lilly` to repository `lily` and source `orfluer` to repository `orfluer`; exclude Snowdrop non-stage extras.
4. Re-run dimension, white-mask, base-detail overlap, source/destination hash, canonical-graph, focused `FlowerAssetContractTest`, full test, dedicated-server, and resource-reload checks before accepting another replacement.

Milestone 20 validation result: **Pass; awaiting owner approval for these uncommitted closeout records.**

## Post-Milestone 20 source-authoritative detail restoration

The authoritative AI was backed up, preflighted in full, updated, saved, and exported through Illustrator 30.7. All 33 mask-bearing stages promoted the retained detailed raster back to `base_texture`; the prior corrected raster was renamed `base_texture_alpha_disjoint_nonexport` and hidden. All 16 base-only stages were untouched.

- Current source SHA-256: `1B5407855D448B8385CC6D067858A347DF3C0B0993B390382A36EE1D42C0A9EE`.
- Backup immediately before restoration: `flowers_pre_detail_preserving_tint_20260804_205234.ai`, SHA-256 `00E0C2F5CE56422361FC88C7F8F0BAD20A337B1FD85F33ED4252B6680F22A223`.
- Native export: 49 bases and 33 masks; 33 production bases changed, 16 bases remained byte-identical, and all 33 masks remained byte-identical.
- Detailed overlap: 58,516 mask-selected pixels now expose underlying base artwork; all 33 authored-mask stages contain varied base colour/detail beneath their masks.
- Runtime blend: the white coverage masks remain unchanged and are applied as a 50%-opacity translucent tint overlay.

Detail-preserving correction: **Implemented and validated locally; uncommitted.**
