# Flower Illustrator Discovery Report

## Milestone status

Milestone 18 discovery is **complete and owner-approved for Milestone 19**. The owner intentionally corrected and resaved the Illustrator source after the first inventory. During Milestone 19, the owner then explicitly authorized a source-authoritative correction making every mask-selected base area transparent. Each source change was followed by a full native re-inventory before export.

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
| Size | 78,138,903 bytes | 50,391,256 bytes |
| Last modified (UTC) | `2026-08-04T21:47:34.8107050Z` | `2026-08-05T01:11:11Z` |
| SHA-256 | `340D14CFB6DC103ED90395BFFA3200B0F2AD84D88C34A4FBAD941AEA626BC62C` | `00E0C2F5CE56422361FC88C7F8F0BAD20A337B1FD85F33ED4252B6680F22A223` |
| Status | superseded by owner corrections | authoritative Milestone 19 export baseline |

The owner explicitly authorized the base-transparency correction and resave. A byte-identical backup of the prior owner-resaved source is retained at `C:\projects\britannia\raw fiels\codex_backups\flowers_pre_base_transparency_20260804_181043.ai` with SHA-256 `6E3DD39B95DA983B317D5F379F33F4BBD08A2E7FF72B7B2FD874DF4D3CFD4048`.

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

Every stage has exactly one named embedded `base_texture` RasterItem. On each of the 33 authored-mask stages, the export base is a corrected 128 x 128 embedded raster at exact artboard bounds `[0,0,128,-128]`; the previous high-resolution embedded base remains hidden as `base_texture_original_nonexport`. Authored masks are named `dye_mask` vector GroupItems. Every mask path is filled `RGB(255,255,255)`, normally blended at 100% opacity, with no strokes or clipping paths.

The corrected source was reopened and re-inventoried after saving: 49 stage layers, 49 export bases, 33 white masks, 33 hidden original bases, zero linked assets, and zero hidden or locked stage layers. A PDF-compatible rendering was also inspected as a cross-check. Pixel validation of the embedded corrections found zero base/mask alpha overlap across all 33 authored-mask stages and zero pixel changes outside the selected mask areas.

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
| ILLUSTRATOR-001 | authoritative source is external `flowers.ai` at hash `00E0C2F5...2A223` | APPROVED |
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
| ILLUSTRATOR-014 | source remains external and authoritative; owner authorized alpha-disjoint base correction | APPROVED AND COMPLETED |
| ILLUSTRATOR-015 | mask-selected base pixels are transparent; hidden embedded originals are retained for reversibility | APPROVED AND VERIFIED |

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

Milestone 18's hard stop is cleared for Milestone 19 only. Milestone 20 remains unauthorized. The later source edit was explicitly owner-authorized for Milestone 19 and is recorded above; no stage, commit, push, fetch, or GitHub access occurred.
