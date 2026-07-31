# Gate F Release Review

Prepared: 2026-07-30

Branch: `banners-dyetub`

Candidate commit: `4cd7aec6babc2fb856244238d74d5fcf393d3c4d`

Status: **PASS**

Gate F technical validation: **PASS**

Product-owner Gate F approval: **APPROVED**

Release performed: **NO**

The product owner completed the required dedicated-server, two-client concurrency, synchronization, lifecycle,
reload/restart, representative visual, diagnostics, and qualitative performance review on 2026-07-30 against the
exact candidate commit and JAR hashes recorded below. The complete approval matrix is preserved in
`GATE_F_LIVE_EVIDENCE.md`.

## Locked release inventory

The authoritative catalogue and `content/banner_release_contract.json` lock 35 unique, ordered, complete
definitions; four materials/palettes; seven pigments; two mounts; and schema version 1. Every row below has
supported mounts `britannia_mod:brass` and `britannia_mod:iron`, default mount `britannia_mod:brass`, default
material `britannia_mod:cotton`, and status `complete`. The exact source references, SHA-256 values, intake paths,
and approval notes remain in `content/banner_catalogue.yml`.

| # | Stable ID / display / group | Size / orientations / placement | Geometry | Base and mask |
|---:|---|---|---|---|
| 1 | `tournament_curtain` — Tournament Curtain — large | 2x2; parallel; `large_parallel` | `banner/large/tournament_curtain/geometry` | `banner/tournament_curtain/{base_texture,dye_mask}` |
| 2 | `threefold_chain_standard` — Threefold Chain Standard — large | 2x2; parallel; `large_parallel` | `banner/large/threefold_chain_standard/geometry` | `banner/threefold_chain_standard/{base_texture,dye_mask}` |
| 3 | `iron_serpent_standard` — Iron Serpent Standard — large | 2x2; parallel; `large_parallel` | `banner/large/iron_serpent_standard/geometry` | `banner/iron_serpent_standard/{base_texture,dye_mask}` |
| 4 | `silver_fleur_curtain` — Silver Fleur Curtain — large | 2x2; parallel; `large_parallel` | `banner/large/silver_fleur_curtain/geometry` | `banner/silver_fleur_curtain/{base_texture,dye_mask}` |
| 5 | `gilded_trellis_curtain` — Gilded Trellis Curtain — large | 2x2; parallel; `large_parallel` | `banner/large/gilded_trellis_curtain/geometry` | `banner/gilded_trellis_curtain/{base_texture,dye_mask}` |
| 6 | `gilded_chevron_curtain` — Gilded Chevron Curtain — large | 2x2; parallel; `large_parallel` | `banner/large/gilded_chevron_curtain/geometry` | `banner/gilded_chevron_curtain/{base_texture,dye_mask}` |
| 7 | `verdant_grape_pennon` — Verdant Grape Pennon — medium-wall | 1x2; parallel; `medium_parallel` | `banner/medium_wall/grape_rosette_pair/geometry` | `banner/verdant_grape_pennon/{base_texture,dye_mask}` |
| 8 | `silver_rosette_pennon` — Silver Rosette Pennon — medium-wall | 1x2; parallel; `medium_parallel` | `banner/medium_wall/grape_rosette_pair/geometry` | `banner/silver_rosette_pennon/{base_texture,dye_mask}` |
| 9 | `four_seals_pennon` — Four Seals Pennon — medium-wall | 1x2; parallel; `medium_parallel` | `banner/medium_wall/four_seals_pennon/geometry` | `banner/four_seals_pennon/{base_texture,dye_mask}` |
| 10 | `twin_spades_pennon` — Twin Spades Pennon — medium-wall | 1x2; parallel; `medium_parallel` | `banner/medium_wall/twin_spades_pennon/geometry` | `banner/twin_spades_pennon/{base_texture,dye_mask}` |
| 11 | `ankh_pennon` — Ankh Pennon — medium-wall | 1x2; parallel; `medium_parallel` | `banner/medium_wall/ankh_pennon/geometry` | `banner/ankh_pennon/{base_texture,dye_mask}` |
| 12 | `joined_wards` — Joined Wards — medium-wall | 1x2; parallel; `medium_parallel` | `banner/medium_wall/joined_wards/geometry` | `banner/joined_wards/{base_texture,dye_mask}` |
| 13 | `tournament_medium` — Tournament Medium — medium | 1x2; perpendicular; `medium_perpendicular` | `banner/medium/tournament_pair/geometry` | `banner/tournament_medium/{base_texture,dye_mask}` |
| 14 | `ceremonial_tournament` — Ceremonial Tournament — medium | 1x2; perpendicular; `medium_perpendicular` | `banner/medium/tournament_pair/geometry` | `banner/ceremonial_tournament/{base_texture,dye_mask}` |
| 15 | `iron_quarter` — Iron Quarter — medium | 1x2; perpendicular; `medium_perpendicular` | `banner/medium/iron_quarter/geometry` | `banner/iron_quarter/{base_texture,dye_mask}` |
| 16 | `outer_ward` — Outer Ward — medium | 1x2; perpendicular; `medium_perpendicular` | `banner/medium/pointed_ward/geometry` | `banner/outer_ward/{base_texture,dye_mask}` |
| 17 | `ward_of_serpents` — Ward of Serpents — medium | 1x2; perpendicular; `medium_perpendicular` | `banner/medium/pointed_ward/geometry` | `banner/ward_of_serpents/{base_texture,dye_mask}` |
| 18 | `serpent_guard` — Serpent Guard — medium | 1x2; perpendicular; `medium_perpendicular` | `banner/medium/rounded_guard/geometry` | `banner/serpent_guard/{base_texture,dye_mask}` |
| 19 | `crossroad_guard` — Crossroad Guard — medium | 1x2; perpendicular; `medium_perpendicular` | `banner/medium/rounded_guard/geometry` | `banner/crossroad_guard/{base_texture,dye_mask}` |
| 20 | `argent_shield` — Argent Shield — medium | 1x2; perpendicular; `medium_perpendicular` | `banner/medium/argent_shield/geometry` | `banner/argent_shield/{base_texture,dye_mask}` |
| 21 | `silver_and_gold_pennon` — Silver and Gold Pennon — small | 1x1; both; `small` | `banner/small/pennon_pair/geometry` | `banner/silver_and_gold_pennon/{base_texture,dye_mask}` |
| 22 | `star_standard` — Star Standard — small | 1x1; both; `small` | `banner/small/star_standard/geometry` | `banner/star_standard/{base_texture,dye_mask}` |
| 23 | `ship_standard` — Ship Standard — small | 1x1; both; `small` | `banner/small/ship_standard/geometry` | `banner/ship_standard/{base_texture,dye_mask}` |
| 24 | `pennon_of_silver` — Pennon of Silver — small | 1x1; both; `small` | `banner/small/pennon_pair/geometry` | `banner/pennon_of_silver/{base_texture,dye_mask}` |
| 25 | `iron_ward` — Iron Ward — small | 1x1; both; `small` | `banner/small/iron_ward/geometry` | `banner/iron_ward/{base_texture,dye_mask}` |
| 26 | `iron_ward_auxiliary` — Iron Ward Auxiliary — small | 1x1; both; `small` | `banner/small/iron_ward_auxiliary/geometry` | `banner/iron_ward_auxiliary/{base_texture,dye_mask}` |
| 27 | `road_guard` — Road Guard — x-small | 1x1; both; `extra_small` | `banner/road_guard/geometry` | `banner/road_guard/{base_texture,dye_mask}` |
| 28 | `pale_road_guard` — Pale Road Guard — x-small | 1x1; both; `extra_small` | `banner/road_guard/geometry` | `banner/pale_road_guard/{base_texture,dye_mask}` |
| 29 | `red_crosslets` — Red Crosslets — x-small | 1x1; both; `extra_small` | `banner/road_guard/geometry` | `banner/red_crosslets/{base_texture,dye_mask}` |
| 30 | `captains_red_crosslets` — Captain's Red Crosslets — x-small | 1x1; both; `extra_small` | `banner/road_guard/geometry` | `banner/captains_red_crosslets/{base_texture,dye_mask}` |
| 31 | `scarlet_court` — Scarlet Court — x-small | 1x1; both; `extra_small` | `banner/road_guard/geometry` | `banner/scarlet_court/{base_texture,dye_mask}` |
| 32 | `verdant_court` — Verdant Court — x-small | 1x1; both; `extra_small` | `banner/road_guard/geometry` | `banner/verdant_court/{base_texture,dye_mask}` |
| 33 | `small_curtain` — Small Curtain — x-small | 1x1; both; `extra_small` | `banner/small_curtain/geometry` | `banner/small_curtain/{base_texture,dye_mask}` |
| 34 | `prosperity_standard` — Prosperity Standard — x-small | 1x1; both; `extra_small` | `banner/road_guard/geometry` | `banner/prosperity_standard/{base_texture,dye_mask}` |
| 35 | `guardian_standard` — Guardian Standard — x-small | 1x1; both; `extra_small` | `banner/road_guard/geometry` | `banner/guardian_standard/{base_texture,dye_mask}` |

All asset identifiers above are in namespace `britannia_mod`. Asset audit requires exactly 70 unique released
128 x 128 RGBA PNGs and the declared block-atlas source.

## Historical audit and migration

History was audited from administrative commit `502c79bd5aa4dd8fffec481cc07baa2ebb67c518`. Fourteen provisional IDs
were operator-obtainable and are migrated through `BannerDefinitionMigrations`; `end_01` and `end_02` were missing
from the earlier scattered alias list and are now included. Codec tests prove migration changes only definition ID,
is idempotent, writes canonical IDs, and preserves unknown raw IDs. Full mapping is in
`SAVED_DATA_AND_MIGRATIONS.md`.

No mapping exists for `prosperity_standard` or `guardian_standard`: history shows both were appended as new
definitions rather than replacements.

## Hardening review

- Persistence: schema-1 banner, tub, and placement data round-trip; unknown schemas/corrupt IDs/impossible tub
  states/incomplete rectangles reject. Optional pigment and future unknown fields behave as documented.
- Colour evolution: saved resolved colour IDs retain identity; mapping edits affect only fresh dye operations.
- Missing content: definitions, material, palette, pigment, mount, placement profile, geometry, base, mask, mount
  assets, index, and atlas paths have typed validation/fallback coverage. No unsafe colour guessing remains.
- Reload: immutable registry/client snapshots publish atomically; 25 repeated publication cycles expose no partial
  state; generation changes clear bounded presentation caches.
- Multiplayer: sessions are player-bound, opaque, one-use, replay-protected and hand/registry/result revalidated.
  Cross-player token attempts cannot consume either session; terminal records are capped at 4,096.
- Lifecycle: exactly one anchor block entity, no part entity, no banner ticker, chunk-load-triggered integrity work,
  exact pick/drop state, and deterministic 140/280-banner structural fixtures including cross-chunk footprint.
- Diagnostics: missing-asset 256, malformed block-entity load 1,024, migration 64, and replay 4,096 ceilings.

## Network and performance budget

| Surface | Measured release value | Regression ceiling |
|---|---:|---:|
| Full 35-definition render snapshot | 11,619 bytes | 65,536 bytes |
| Dye preview | 297 bytes | 4,096 bytes |
| Confirm / cancel | 16 bytes | exact UUID-only shape |
| Application result | at most 18 bytes | fixed UUID + enum + boolean |
| Largest tested 3x2 block-entity update tag | 578 bytes | 4,096 bytes |
| Dense optional fixture | 280 banners / 536 cells / 38 ms observed | informational, no wall-clock CI gate |

Snapshot element counts and preview strings reject values above their protocol bounds before large allocation.
Client caches are lazy 256-entry LRU caches; theoretical release-state cardinality is larger, so eviction is
expected and tested rather than preallocation.

## Technical gate results

| Check | Result |
|---|---|
| Baseline branch / divergence | PASS — `banners-dyetub`, merge base `62df1dc...`, `0 41` before this candidate |
| Focused hardening selection | PASS — 40 tests |
| Complete banner/dye suite | PASS — 417 tests / 55 suites, 0 failures/errors/skips |
| Scaffold generation | PASS — 35 active, 0 disabled/provisional |
| Scaffold `--check` | PASS — byte-stable |
| `git diff --check` | PASS |
| Clean full tests | PASS — 695 tests / 71 suites, 0 failures/errors/skips; configuration cache disabled to avoid a NeoForm stale-path defect |
| Production build | PASS — `./gradlew build --no-configuration-cache` |
| Release JAR audit | PASS — 5,134 entries, 35 definitions, 70/70 released textures, required metadata/index/atlas/classes, 0 banner recipe/pattern entries |
| Dedicated-server closest validation | PASS for construction/data reload — `runGameTestServer` loaded 35/0/0 with no validation messages and no client-class probe; no GameTest functions exist, so the runner then reported `No test functions were given` |

Candidate artifacts (not published):

- `Britannia_Mod-0.1.7k.jar`, 23,024,329 bytes,
  SHA-256 `7CD283BCC929A645D2E5A08B3B9DF520E23FF7873BE5650000BFAA1F15F66539`
- `Britannia_Mod-0.1.7k-all.jar`, 23,595,967 bytes,
  SHA-256 `AA4DB58AAB9C59CE97BF3ABD395C03A1BF7379A549EDA1A55AE574572113F5D8`

The product version remains `0.1.7k`; this milestone did not change or publish it.

## Product-owner Gate F result

- Reviewer: Product Owner
- Review date: 2026-07-30
- Exact candidate commit tested: PASS
- Exact normal and all-JAR hashes verified: PASS
- Dedicated-server ready state and restart: PASS
- Two-client connection, complete catalogue, late tracking, and reconnect: PASS
- Concurrent dyeing, stale-preview rejection, and cross-player token isolation: PASS
- Cross-client rendering and multi-block lifecycle: PASS
- Reload, representative visual sampling, diagnostics, and qualitative performance smoke: PASS
- Known limitations reviewed and accepted: PASS
- Distribution rights for included banner assets confirmed by the product owner
- Release blockers: NONE
- Overall product-owner approval: APPROVED

Gate F decision: **PASS**.

This approval does not merge the branch, change the version, publish either artifact, create a tag, or perform a
release.
