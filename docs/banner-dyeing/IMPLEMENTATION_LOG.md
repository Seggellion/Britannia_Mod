# Banner and Dyeing Implementation Log
## 2026-07-30 - Parallel Medium approval and runtime integration

- Seggellion approved all six prepared Parallel Medium packages on 2026-07-30, confirmed creator/original-art
  provenance, confirmed the artwork was not copied from reference art, and granted distribution permission.
- The six intake manifests now bind that approval to the prepared hashes and all validate
  `READY_FOR_INTEGRATION`. Post-integration live Gate E remains explicitly unperformed.
- Promoted twelve exact 128 × 128 RGBA textures, five deduplicated fabric geometry models, and the
  `britannia_mod:medium_parallel` placement profile into runtime resources.
- All six catalogue entries use approved 1 × 2 dimensions, `wall_parallel` only, brass and iron mounts with brass
  by default, definition-specific base/mask resources, and `content_status: in_progress`.
- The shared wall-parallel physical mount remains untinted. Perpendicular Medium resources and Gate E evidence are
  unchanged. Persistence, networking, crafting policy, the Large family, and Milestone 17 remain unchanged.
- Production is 35 active definitions: 6 placeholder, 6 in progress, 23 complete, and 0 disabled.
- Scaffold generation is idempotent across 139 declared files and `--check` passes. The focused integration suite
  passes 82 tests; the complete banner/dyeing suite passes 646; after standalone clean, the unrestricted suite
  passes 652 tests across 60 suites with zero failures, errors, or skips; build passes.
- The normal JAR contains 5,098 entries and the all-JAR contains 5,102. Both contain 35 definitions with status
  totals 6 placeholder, 6 in progress, and 23 complete; all 12 Parallel Medium textures, five geometry models, the
  parallel profile, and every completed-family texture are present. Duplicate, intake/review, removed-overlay, and
  banner-recipe entries are absent.

## 2026-07-30 - Parallel Medium authoritative extraction and draft intake preparation

- The mandated baseline passed on `banners-dyetub` at parent
  `1ec430e84e6cb5d0091bde94c95c9f8037aa792a`, merge base
  `62df1dc97c5113a86f9c0f258cb90538f31efe89`, 34 commits ahead of `patch-18`, with
  `79474963299603ae73b2efcaf58a9a8614dc881b` in the current ancestry. Only the expected untracked `.claude/` and
  `logs/` paths existed; neither was inspected or modified.
- Read-only/no-save Illustrator automation used only
  `C:/projects/britannia/raw fiels/tabbard/banner_medium_wall.ai`, size 29,780,859 bytes and SHA-256
  `a6a75becd1793dac7a5b36361c0a33d615846ac4e97502deff794ef5ac337fac`. The hash remained identical after
  inspection and all exports. The PDF-compatible CMYK document has one 128 × 128 artboard at
  `[-32, 32, 96, -96]`, 72 ppi raster effects, six embedded raster bases, zero linked/placed items, zero text
  frames, and exactly six relevant top-level layers. No perpendicular, ambiguous, or unrelated layer exists.
- The authoritative layer membership is `verdant_grape_pennon`, `silver_rosette_pennon`,
  `four_seals_pennon`, `twin_spades_pennon`, `ankh_pennon`, and `joined_wards`. The first five replace clearly
  provisional `medium_wall_01` through `medium_wall_05` at unchanged catalogue indices 7 through 11.
  `joined_wards` exactly matches the existing identity at index 12. Saved-item and placed-state decoding aliases
  map the five superseded provisional IDs to the canonical Illustrator names; no duplicate active identity remains.
- Each authored base/mask union extended beyond the artboard. One proportional downscale (never upscale) and one
  shared translation centred each complete pair inside a two-pixel transparent margin before artboard-clipped
  export. Mask normalization only sets active RGB to white and applies
  `output alpha = min(authored mask alpha, base alpha)`; it does not expand or reinterpret the authored selection.
- All six pairs are 128 × 128 8-bit RGBA, with active and transparent mask pixels, protected fixed base pixels,
  white active mask RGB, and zero mask-alpha-over-base violations.

| Definition | Base SHA-256 | Dye-mask SHA-256 |
|---|---|---|
| `verdant_grape_pennon` | `5faf14b7a221752158bbf59393e6b096f4df109db3716437614898794cf0af3b` | `c1aa265275d1a61e2221bc547b3665bf2288f62ae062bbee95a6dd85ef2dfddb` |
| `silver_rosette_pennon` | `e8eb84f1e2fee6cff3e6210cce20846e4dff8c675b6d9fbf8d762a3209c42633` | `e938710b77cd045981a077b4ea80c6d0a5af713e2b3a6ea068e954ec95bb2be8` |
| `four_seals_pennon` | `0b7db42e5ef3969a347df79ee19cd1546f924a423227b5dd251ccc5a1f145a8f` | `81128cba4d6e1a49c60870eddf53ddd8c7bc7b1fcc04462b653a883a15cb9e14` |
| `twin_spades_pennon` | `f6e5a834a57b940ade9b84873b78ad71f0c91efa09eeb8d93ef0a4820ed3fc0b` | `6908bb015759849df80c3c2b4aae7a746d5982fa311a1ae6bb1513ad35d515db` |
| `ankh_pennon` | `65163afac35ae78167436a08be48f4029beb8df45b24fc0f544a6938191bfb80` | `7077db933ffbeb8383616f8bf34ba42f6c75472b6a0463599b23f36f2e393eab` |
| `joined_wards` | `8551830308310eb073cc06bcf4760d69db4ebe897c2aed88bfe49239a84c5a70` | `ec964aa2ae06ab42ca323c232c4a2e18e952d5cd3aecdb9dcd1b161a6d9dba82` |

- Proposed logical dimensions are 1 × 2: every base-alpha silhouette is 68–84 pixels wide and 115–121 pixels
  high, matching the approved Medium footprint, while the live `medium-wall` 2 × 2 default is explicitly
  provisional. The proposal supports only `wall_parallel`, reuses the existing untinted
  `britannia_mod:banner/mount/wall_parallel` physical mount, retains brass/iron materials with brass by default,
  and would use `britannia_mod:medium_parallel` after approval.
- Five proposed fabric geometry groups were prepared: one shared grape/rosette pointed group and distinct Four
  Seals, Twin Spades, Ankh, and Joined Wards groups. No completed perpendicular Medium model has identical
  silhouette, proportions, attachment edge, and UV bounds, so no perpendicular geometry was duplicated or changed.
- Every intake is accurately `NOT_APPROVED`; the real validator returns `NOT_READY`, never `INVALID`, for all six
  packages. The request supplies authoritative artwork, family membership, and final names, but no product-owner
  identity/date, creator/original-art attestation, distribution permission, logical-dimension approval, geometry
  approval, or manual review. Consequently, no draft texture, geometry, placement profile, or `in_progress`
  definition was integrated.
- Runtime remains 35 definitions: 12 placeholder, 0 in progress, 23 complete, and 0 disabled. Extra-small, Small,
  and perpendicular Medium runtime assets, geometry, placement, status, and persistence schemas remain unchanged.
  Gate E is unperformed; its reusable parallel-only runbook is
  `docs/banner-dyeing/PARALLEL_MEDIUM_LIVE_REVIEW.md`.
- Scaffold generation was run twice; SHA-256 comparison passed for all 38 selected generated outputs, and
  `--check` passed. The focused identity/asset/validator suite passed 83 tests. The complete banner/dyeing suite
  passed 646 tests; standalone `clean` passed; the unrestricted suite passed 652 tests across 60 suites with zero
  failures, errors, or skips; and `build` passed. The first banner-suite attempt exposed only two stale assertions
  expecting 11 provisional names; both were updated to the authoritative data-derived count of six.
- Both production JARs contain 35 canonical definitions with 12 placeholder, 0 in progress, 23 complete, and
  0 disabled. They contain the canonical parallel identities but no superseded provisional definitions, draft
  textures, draft geometry, `medium_parallel` profile, intake YAML, or review artifacts. All completed-family
  textures and shared mount/profile/index/atlas resources are present. The normal JAR has 5,068 entries and the
  all-JAR has 5,072; neither has duplicate ZIP entries.

## 2026-07-29 - Perpendicular Medium Gate E approval and closeout

- Seggellion, acting as product owner, reported every check in the eight-banner perpendicular Medium live-review
  runbook passed on 2026-07-29 against commit `79474963299603ae73b2efcaf58a9a8614dc881b`.
- Batch evidence is recorded in `content/banner-final-intake/MEDIUM_FAMILY_GATE_E_REVIEW.md`; each approved
  submission directory has hash-bound `GATE_E_REVIEW.md` evidence. Every result cell is PASS, Gate E is PASS, and
  no correction was requested.
- Exactly the eight Medium definitions transitioned from `in_progress` to `complete`. Production is now 35 active
  definitions: 12 placeholder, 0 in progress, 23 complete, and 0 disabled.
- No artwork bytes, geometry, stable IDs, catalogue indices, dimensions, orientations, mounts, placement profiles,
  item or placed persistence, networking, crafting policy, `medium-wall` content, or Milestone 17 work changed.
- Scaffold generation and `--check` passed; all eight intake files remain `READY_FOR_INTEGRATION`. The focused
  closeout selection passed 103 tests, the complete banner/dyeing suite passed 638 tests across 58 suites, standalone
  `clean` passed, the unrestricted suite passed 644 tests across 59 suites, and `build` passed with no failures,
  errors, or skips.
- The sole initial test failure was a fixture expecting unformatted `/reload` while the evidence correctly records
  the Markdown command as `` `/reload` ``; the assertion was aligned with the evidence and the same selection passed.
- Both production JARs contain 35 definitions (12 placeholder, 0 in progress, 23 complete), every exact Medium
  definition/base/mask resource, and zero duplicate entries. The normal JAR has 5,068 entries and the all-JAR has
  5,072. The existing two-warning compile baseline remains unchanged.
## 2026-07-29 - Perpendicular Medium approval and integration

- Seggellion approved all eight definitions on 2026-07-29, including authorship/originality, non-reference-copy provenance, distribution permission, 1 x 2 dimensions, perpendicular-only orientation, the five proposed geometry groups, and manual intake review. Visible crossbar/attachment pixels are approved fixed authored details.
- All eight intake records now identify Seggellion as creator, approver, and manual intake reviewer; every real validator result is `READY_FOR_INTEGRATION`. Requested content status remains `in_progress` because Gate E live runtime review has not yet been performed.
- Added runtime copies of all 16 exact approved PNGs and five de-duplicated geometry models. Generated definitions use definition-specific base/mask resources, approved geometry, and `britannia_mod:medium_perpendicular`.
- Added the perpendicular-only placement profile with only `wall_perpendicular` mount geometry. `PlacementProfile` accepts a map for any supported subset; registry cross-validation requires a non-empty map's keys to equal the consuming banner's supported-orientation set, preventing accidental unsupported orientation data.
- Production is 35 active definitions: 12 placeholder, 8 in progress, 15 complete, and 0 disabled. The `medium-wall` group, stable IDs, catalogue indices, persistence, block structures, network schema, crafting absence, and Milestone 17 remain unchanged.
- All eight intake validations and scaffold `--check` passed. The focused Medium/catalogue/scaffold suite passed 59 tests; the complete banner/dyeing suite passed 636 tests; after standalone `clean`, the unrestricted suite passed 642 tests across 58 suites with zero failures, errors, or skips; `build` passed.
- Initial corrections were limited to superseded test assumptions and build orchestration: scaffold fixtures needed the newly authoritative asset files; placement/render tests now iterate definition-supported orientations; the Medium render fixture now resolves an approved geometry; the client-index test accepts explicit null mount maps; and stale Gradle test bytecode required one forced no-cache compile. A combined `clean test` invocation reproduced NeoForm's known transient `Patch directory not found`; the required standalone `clean`, `test`, and `build` sequence passed.
- Both production JARs contain all 16 Medium PNGs, five approved geometry models, eight in-progress definitions, and the perpendicular-only profile, with no missing or duplicate entries. The normal JAR has 5,068 entries and the all-JAR has 5,072. Existing compile warnings remain the two-warning baseline (`PlayerSleepMixin` Javadoc and deprecated `initializeClient`). Live Gate E checks remain explicitly unperformed in the Medium runbook.

## Historical 2026-07-29 - Perpendicular medium source audit and draft intake preparation

- Confirmed the clean tracked baseline at `d55d8093a2b9558063489130bc473e9aeace7008` on `banners-dyetub`.
  The closeout is the current HEAD, is an ancestor of itself, and the branch is 31 commits ahead of `patch-18`
  with merge base `62df1dc97c5113a86f9c0f258cb90538f31efe89`. Only the expected untracked `.claude/` and `logs/`
  existed; neither path was inspected or modified.
- Audited `medium` and `medium-wall` separately. `medium` remains exactly the eight source-named definitions at
  indices 13 through 20 and `medium-wall` remains the distinct six-definition parallel-family placeholder group
  at indices 7 through 12.
- Read-only/no-save Adobe Illustrator automation used only
  `C:/projects/britannia/raw fiels/tabbard/banner_medium.ai`, size 47,417,154 bytes and SHA-256
  `5a219e6e276884e6b7173ec5c6608c8a85b53dcbdeb768b0f2c19ead0423d34d`. The hash was identical before
  and after export. The PDF-compatible CMYK document has one 128 x 128 artboard at `[-32, 32, 96, -96]`,
  72 ppi raster effects, eight embedded raster bases, no linked items, and eight exact-name top-level layers.
  `ward_of_serpents` contains its mask in a named child layer; every other layer has named `base_texture` and
  `dye_mask` page items.
- All eight Illustrator names map exactly to their existing stable IDs: `tournament_medium`,
  `ceremonial_tournament`, `iron_quarter`, `outer_ward`, `ward_of_serpents`, `serpent_guard`,
  `crossroad_guard`, and `argent_shield`. No new definition, provisional-ID migration, parallel layer, unrelated
  layer, or ambiguous name was found.
- Each complete authored base/mask union extended beyond the artboard. One proportional downscale (never upscale)
  and one shared translation centered the pair in a two-pixel transparent margin before artboard-clipped export.
  Mask preparation only changed active RGB to white and applied
  `output alpha = min(authored mask alpha, base alpha)`.

| Definition | Base SHA-256 | Dye-mask SHA-256 |
|---|---|---|
| `tournament_medium` | `0fd6d82d3e674f964387be404739bd17a298af04fad6d36e1a7867e86549d503` | `739d65774502e03213da5fc56a8b21222c1febbda1e5163ca05a0cd674d521bb` |
| `ceremonial_tournament` | `b8b623c0b03c8ee2dffb9183610d4dbc05703cc10876b6a783041fd7ab689804` | `8153536154f5163ccbca26cacec58c4c5cd60a1adbdd7e5c7ddc60954b8464ab` |
| `iron_quarter` | `a0a7e52030d084b734737ed6dce970a402e38bc02cdd684b6196eb4271a337ca` | `1959fca2ee4cfe059ffd629cb2130d5835488ebad78280381df5cc14d0c750a8` |
| `outer_ward` | `24269b8f0e07ff6f5050490217457f0fdb7931d57ebee4c51e046143af85a016` | `ff2c4c2231b6aa0090c0c1fb0650bcf79f5c1da868cea1375e68a77a1f7daae0` |
| `ward_of_serpents` | `73332ee0c37c5c72fae721de76c9ea85141845bc59252d348d84b433104ff6ff` | `badcb1bcea7a0fbc37ef0bc0d4257a9c60378244e0a1b4eb756a47deb8a01d8d` |
| `serpent_guard` | `6b157d409cb268bf03f305f0eda85450bb8dac3eaeaf227303ace82baba72ce8` | `b3cd16f6c62431d44b76df6eef9a6554e4b03b668deb46a0d9daaf791202eaee` |
| `crossroad_guard` | `78f8580a8a308e0b3895fd7992a14c37074d8e03115565cdce18c0b76bcfdd59` | `f7cea5c270a963ed2782f47c73eabf8401d94915d516da7b3c499ae98fcc5f3a` |
| `argent_shield` | `3f0f1829dd09920f15f4fe984608dd85088769a40cac7a3c7787f32100017556` | `0852a65d273ff9220cd56db45142db0b048050887f15925a22f479fddbdc67de` |

- Every pair is 128 x 128, 8-bit RGBA with active and transparent mask pixels, fixed base pixels, white active mask
  RGB, and zero mask-alpha-over-base violations. Five proposed geometry groups are recorded as intake evidence:
  shared tournament pair, distinct Iron Quarter, shared pointed wards, shared rounded guards, and distinct Argent
  Shield. Proposed logical dimensions remain 1 x 2 pending placed-aspect review.
- The real intake validator returned `NOT_READY` and no `INVALID` findings for all eight drafts. Owner approval,
  approval identity/date, original-art creator/copying provenance, and distribution permission are absent. Four
  bases (`tournament_medium`, `ceremonial_tournament`, `iron_quarter`, and `argent_shield`) also contain apparent
  authored crossbar/attachment pixels that require owner classification against the hardware-free contract.
- Runtime integration is additionally blocked by a verified data-contract conflict: `PlacementProfile` accepts an
  orientation-mount map only when both `wall_parallel` and `wall_perpendicular` are present, while this batch is
  expressly perpendicular-only. An empty map would omit the required perpendicular-specific mount selection.
  No schema, renderer, profile, catalogue entry, definition, localization, runtime texture, or runtime model was
  changed to work around this conflict.
- Review artifacts include natural, mask, blue recolour, alignment, combined sheet, and perpendicular silhouette
  previews. The reusable unperformed matrix is
  `docs/banner-dyeing/PERPENDICULAR_MEDIUM_LIVE_REVIEW.md`.
- Scaffold generation produced the same aggregate digest
  `c6ebe3fd01fe2e8c2f5a1947bdbb018420d2dcfe3d1ede39130ce6195db44663` on two consecutive runs,
  and `--check` passed. The focused medium-intake/scaffold selection passed; the banner/dyeing selection passed
  636 tests across 57 suites. After `clean`, the unrestricted suite passed 642 tests across 58 suites with zero
  failures, errors, or skips, and `build` passed.
- Both production JARs retain 35 definitions with 20 placeholder, 0 in progress, 15 complete, and 0 disabled. The
  normal JAR has 5,032 entries and the all-JAR has 5,036; both have zero duplicates, all completed extra-small and
  small definitions, required mounts/index/atlas/placeholders, and no draft medium assets, intake/review files,
  removed three-file fields, or banner recipes.
- Production remains data-derived at 35 definitions: 20 placeholder, 0 in progress, 15 complete, and 0 disabled.
  Extra-small and small remain complete and unchanged; `medium-wall` remains unchanged. No persistence, crafting,
  larger-family, Milestone 17, push, merge, or parallel-medium work was performed.

## 2026-07-29 - Small-family Gate E approval and closeout

- Seggellion, acting as product owner, reported that every check in the six-banner Small-family live-review runbook
  passed against commit `e4f457b132667efc0c9789ee6044c47bedf68bdc` on 2026-07-29.
- Batch evidence is recorded in `content/banner-final-intake/SMALL_FAMILY_GATE_E_REVIEW.md`; each approved intake
  directory has hash-bound `GATE_E_REVIEW.md` evidence. All result cells are PASS, Gate E is PASS, and no correction
  was requested.
- Exactly the six Small definitions transitioned from `in_progress` to `complete`. Production is now 35 active
  definitions: 20 placeholder, 0 in progress, 15 complete, and 0 disabled.
- No artwork bytes, geometry, stable IDs, catalogue indices, dimensions, orientations, mounts, placement profiles,
  persistence schemas, networking, crafting policy, or Extra-small content changed.
- Medium-family work begins with a readiness audit only; no Medium artwork or approval is inferred by this closeout.
- `scaffold_banners.bat` generation and `--check` passed; all six intake validations remain
  `READY_FOR_INTEGRATION`. The focused closeout selection passed 89 tests. After a separate clean, the full suite
  passed 638 tests across 57 suites with zero failures, errors, or skips, and `build` passed.
- Initial validation corrections were limited to expectations: one focused test listed completed families in the
  reverse catalogue order, and one full-suite Extra-small test compared its nine IDs against all completed families.
  The first now follows indices 21 through 35; the second scopes its assertion to the `x-small` group. A combined
  `clean test build` invocation also exposed NeoForm's transient `Patch directory not found`; the established
  separate clean, test, and build sequence passed without source or configuration changes.
- Both production JARs contain 35 definitions, all six completed Small definitions, no `end_01`/`end_02` definition,
  and no duplicate ZIP entries. The normal JAR has 5,032 entries; the all-JAR has 5,036.


## 2026-07-29 - Small-family intake approval and integration

- Product-owner approval was supplied for all six canonical small-family definitions by Seggellion on 2026-07-29.
  Seggellion is the artwork creator; the artwork is original, distribution permission is confirmed, and manual asset
  verification is recorded as performed by Seggellion on the same date.
- The provisional stable IDs `end_01` and `end_02` were replaced at their existing catalogue indices 22 and 23 by
  `star_standard` (Star Standard) and `ship_standard` (Ship Standard). Illustrator provenance continues to identify
  the unchanged historical source-layer names without exposing them as active runtime IDs.
- All six intake manifests return `READY_FOR_INTEGRATION`. The exact approved 128 x 128 RGBA PNG bytes were copied
  to definition-specific runtime paths; source, runtime, catalogue, and intake SHA-256 values agree.
- Five approved geometry models are integrated: one shared paired-pennon model plus distinct Star Standard, Ship
  Standard, Iron Ward, and Iron Ward Auxiliary models. The approved `britannia_mod:small` 1 x 1 placement profile
  supports both wall orientations and reuses the untinted brass/iron orientation mount geometry.
- Scaffold generation is idempotent and `--check` passes. Production remains 35 active definitions: 20
  `placeholder`, 6 `in_progress`, 9 `complete`, and 0 disabled. No small definition is marked complete.
- The first full banner selection ran 630 tests and exposed 31 stale test/fixture expectations: catalogue and client
  index counts, placeholder-only small-geometry selectors, scaffold seed assets, and pre-integration status totals.
  Corrections kept the production behavior intact while updating those fixtures for 20 `placeholder`, 6
  `in_progress`, 9 `complete`, 12 unique client geometry IDs, 32 unique texture IDs, and the approved small geometry.
  A focused 113-test rerun then exposed two remaining semantic counts: the admin placeholder queue intentionally
  includes all 26 non-complete entries, while scaffold support JSON increased from eight to nine. Both were corrected.
- The final banner selection passed 630 tests across 55 suites. After `clean`, the unrestricted suite passed 636
  tests across 56 suites with zero failures, errors, or skips; `build` passed. Both production JARs contain all 35
  definitions, the 12 approved small textures, five approved small geometry models, and the small placement profile;
  neither contains `end_01`/`end_02` definitions or duplicate ZIP entries. The normal JAR has 5,032 entries and the
  all-JAR has 5,036.
- Existing compiler warnings remain unchanged: missing Javadoc on a Mixin `@Overwrite`, deprecated
  `initializeClient`, and standard deprecation/unchecked notices. No new warning class was introduced.
- Product-owner asset testing is recorded in each intake. The reusable live-runtime matrix remains available for a
  distinct post-integration evidence pass; no granular runtime result was inferred from the owner statement.


## 2026-07-28 - Small-family final-asset preparation

- Audited exactly the six canonical small definitions at indices 21 through 26:
  `silver_and_gold_pennon`, `star_standard`, `ship_standard`, `pennon_of_silver`, `iron_ward`, and
  `iron_ward_auxiliary`. All six remain runtime `placeholder` definitions because no approved intake was available.
- Read-only/no-save Adobe Illustrator automation used only
  `C:/projects/britannia/raw fiels/tabbard/banner_small.ai`, size 31,508,332 bytes and SHA-256
  `fc2ec331b6569387e2dc7ceb3902305a8b9f79adae7087ee10379baa308c53fb`. The hash was identical before export,
  after export, and after all automation. The PDF-compatible Illustrator document contains one 128 x 128 artboard,
  CMYK document colour space (COM value 1), 72 ppi raster effects, six matching top-level layers, six embedded
  raster bases, no linked items, and explicit dye-mask artwork. `star_standard` expresses its mask as a named child layer;
  the other five use named page items.
- Every authored base/mask union extended beyond the artboard. One deterministic proportional downscale (never an
  upscale) and one shared translation centered each complete union inside a two-pixel transparent margin. Base and
  mask were then exported separately with artboard clipping from that shared coordinate system. No artwork was
  trimmed independently, stretched, redesigned, or saved back to Illustrator.
- All six packages now contain 128 x 128, 8-bit RGBA `base_texture.png` and `dye_mask.png`. Preparation changed only
  active mask RGB to white and applied `output mask alpha = min(authored mask alpha, base alpha)`. Every mask has
  active pixels, transparent pixels, protected fixed base pixels, zero non-white active pixels, and zero pixels
  whose alpha exceeds the base.

| Definition | Base SHA-256 | Dye-mask SHA-256 |
|---|---|---|
| `silver_and_gold_pennon` | `81ea9cdaa1fe5a52f75ae04beb821f80151634d570006976f2405edd750fec4a` | `f7c1b8a5ca8d64c71b93bf66bf82c2d42c8a6cead623d936136ce9617726f819` |
| `star_standard` | `3f6fa75c4f30de2d8db72a32f27e68fae44d9cd05ac7bcd7d9168d9e4e9fcc3b` | `0e192ef62e902345edd7b8b2f78794efbcce66597b4583e87b97c1d9ba349fad` |
| `ship_standard` | `47aff2d8480318c4001eb8e410b95a01fc51fcb25618209c6a16a7f95c80be2e` | `fd6c3f5637b5d4b888753aeaa9b1121770c5a8873f88fc2558cbf43f7860e91d` |
| `pennon_of_silver` | `ca4326f83307df453862a3617d72252189e495915a5a7ccbc4e18d78e40aca9b` | `51a077bfe3d7c3c13cedc8bd96b3913e6c26b2b80802c49be18685024ac97b06` |
| `iron_ward` | `61dc3a84e5bf5b2bee06df71d3fc1ccd1d638e522056e8d4bb48f25cf1b12cbe` | `16bcb9fb950b47723c363b27f0562e0b95ae023fa9362effcdb47ad695dc0c9f` |
| `iron_ward_auxiliary` | `a38f29c4325250dcb50d3b1af1ba8ce20772d9ecfb4eb17d3cc0247231da380a` | `856ced21d8cdf2baa9f2662b7d9028facb9dbbfd715dfe3ad2f10e52370838d4` |

- Proposed source geometry preserves the established 10-unit small-family height, two-pass thickness, and
  128-pixel UV basis. Silver and Gold Pennon and Pennon of Silver share the one paired-pennon geometry proposal;
  `star_standard`, `ship_standard`, `iron_ward`, and `iron_ward_auxiliary` each have distinct proportions and source models.
  Proposed placement profile `britannia_mod:small` reuses the established parallel/perpendicular mount geometry;
  brass and iron remain independent untinted materials. These proposals are intake evidence only and were not
  registered as runtime resources.
- The actual intake validator classified all six drafts `NOT_READY` with no `INVALID` findings. Common blockers are
  product-owner identity/date and approval, original-art/creator/copying provenance, and distribution permission.
  `star_standard` and `ship_standard` additionally require final display names. Manual visual/live review is not performed.
- Five diagnostic views per definition and a combined family sheet are tracked under
  `content/banner-final-intake/submissions/<stable_path>/review/` and
  `content/banner-final-intake/review/small_family_review.png`. The reusable live procedure and unperformed
  definition matrix are in `docs/banner-dyeing/SMALL_FAMILY_LIVE_REVIEW.md`.
- Focused preparation validation passed 6 tests. The full banner/dyeing selection passed 630 tests across 55 suites;
  after `clean`, the unrestricted suite passed 636 tests across 56 suites. Both had zero failures, errors, or skips.
  The production build passed. Scaffold generation was idempotent with digest
  `0596cee06b0de4e99664b08d868d0f0433418fec0bab747e9208b6413ae7c8fc`, and `--check` passed.
- Both production JARs contain 35 definitions (26 placeholder, 0 in progress, 9 complete), all nine completed
  extra-small definitions and their 18 textures, the client index, directory atlas, distinct orientation mounts,
  brass/iron resources, and still-required placeholders. Neither JAR contains draft small runtime assets, intake or
  review content, three-file/static-overlay fields, banner recipes, or duplicate ZIP entries. The normal JAR has
  5,002 entries and the all-JAR has 5,006.
- No small definition was marked `complete` or `in_progress`; no runtime small asset was adopted. The completed
  extra-small family, `small_curtain`, state schema, crafting-disabled policy, later size families, and Milestone 17
  remain unchanged.

## 2026-07-28 - Nine-banner extra-small Gate E closeout

- Direct product-owner evidence records the complete live review against commit
  `bf68e4b0025f1aed9a905904a669a09f39e06d31` on Minecraft 1.21.1 with NeoForge 21.1.72. The exact execution date
  was not separately supplied, so 2026-07-28 is the closeout-record date. Resource packs, shaders, and screenshots
  were not supplied.
- All required shared checks and all per-banner checks passed for Road Guard, Pale Road Guard, Red Crosslets,
  Captain's Red Crosslets, Scarlet Court, Verdant Court, Small Curtain, Prosperity Standard, and Guardian Standard.
  This includes 128 x 128 fidelity, two-file selective recolouring, natural/fixed/dyeable regions, preview,
  brass/iron, both orientations, all facings, geometry groups, lifecycle, tracking, and reload behavior. Gate E is
  `PASS`, every definition is `APPROVED`, and no correction was requested.
- Exactly those nine catalogue entries transitioned from `in_progress` to `complete`. Production remains 35 active
  definitions: 26 `placeholder`, 0 `in_progress`, 9 `complete`, and 0 disabled. `small_curtain` remains the canonical
  ninth ID; `x_small_unnamed_01` remains inactive.
- The authoritative batch evidence is `content/banner-final-intake/EXTRA_SMALL_GATE_E_REVIEW.md`; each intake
  directory has a definition-specific `GATE_E_REVIEW.md`. Road Guard's superseded 64 x 64 history is preserved and
  followed by the new 128 x 128 approval with the reviewed hashes.
- No artwork bytes, geometry, placement profile, renderer, atlas, client index architecture, command, state schema,
  placement, persistence, dyeing, crafting, or non-extra-small definition was changed. Another size family and
  Milestone 17 were not started.
- Automated closeout validation passed: 88 focused tests across 7 suites; 623 banner/dyeing tests across 54 suites;
  `clean`; 629 unrestricted tests across 55 suites; and the production build, all with zero failures, errors, or
  skips. Scaffold generation completed, all 37 generated-output hashes were unchanged on the idempotency run, and
  `--check` passed. The normal JAR has 5,002 entries and the all-JAR 5,006, both with zero duplicates, 35 definitions
  (26 placeholder, 0 in progress, 9 complete), both geometry groups, all 18 source/runtime/intake/package-identical
  PNGs, required client index/atlas/profile resources, and no active legacy ID, old asset fields, recipes, or pattern
  content. Review Markdown is intentionally excluded from runtime packaging.
- The first focused run completed 88 tests with four failed stale expectations: raw inherited placeholder statuses
  were counted as explicit strings in two tests, one manifest-ID comparison retained the `britannia_mod:` namespace,
  and one admin validation count still treated complete definitions as placeholders. Those four assertions were
  corrected to the actual data contracts; the identical 88-test selection then passed. No runtime or asset fix was
  required.
- Closeout commit: this section is part of the containing
  `content(banners): complete extra-small banner family` commit; its full hash is reported after creation because a
  commit cannot embed its own hash.

## 2026-07-28 - Authoritative-baseline intake audit correction

- Audited `29109dff0ec7bddf45221b535aeee27c110add23` in place as the product-owner-authorized baseline. Commits
  `8f166187ad362718cf294edfe5680ec8086aab46` and `29109dff0ec7bddf45221b535aeee27c110add23`
  remain intact; the correction is additive.
- Closed the only substantive contract gap: an `APPROVED` final-content intake now requires both
  `base_texture.png` and `dye_mask.png` to be exactly 128 x 128, while non-final 16 x 16 diagnostic placeholders
  remain unchanged and runtime rendering stays resolution-independent.
- Corrected the intake guide, checklist, README, generic template, and extra-small batch template. The batch template
  now covers all nine authoritative IDs, uses `small_curtain`, includes Prosperity Standard and Guardian Standard,
  and labels current `in_progress` catalogue data as informational rather than intake approval. Mask guidance now
  matches validation: active alpha requires visible base alpha and may not exceed it; deliberate partial-alpha
  recolouring remains valid.
- Revalidated all nine approved intake packages as `READY_FOR_INTEGRATION`; source, runtime, and packaged hashes
  match for all 18 PNGs. Scaffold generation was idempotent and `--check` passed with 35 definitions.
- The focused audit passed 56 tests. The full banner/dyeing selection passed 619 tests across 53 suites, and the
  post-clean unrestricted suite passed 625 tests across 54 suites, all with zero failures, errors, or skips. The
  production build passed. Both JARs contain 35 definitions (26 placeholder, 9 in progress), all 18 exact 128 x 128
  family assets, required geometry/index/atlas/profile resources, no duplicate entries, no active legacy ID or old
  asset fields, and no banner recipe/pattern content.
- No runtime, persistence, catalogue identity, dimensions, orientations, mounts, final approval status, or artwork
  bytes required correction. The live nine-banner review remains unperformed.

## 2026-07-28 - Complete extra-small banner family integration

### Scope and authoring evidence

- Integrated exactly nine x-small definitions: Road Guard (27), Pale Road Guard (28), Red Crosslets (29), Captain's
  Red Crosslets (30), Scarlet Court (31), Verdant Court (32), Small Curtain (33), Prosperity Standard (34), and
  Guardian Standard (35). The first 33 indices were preserved. The last two were genuinely absent and were appended;
  no predecessor or duplicate was found. `x_small_unnamed_01` remains inactive after its earlier migration to
  `small_curtain`.
- Automated Adobe Illustrator export used only
  `C:/projects/britannia/raw fiels/tabbard/banner.ai`. Its pre/post SHA-256 was
  `e0f63c8a2e6a39621f2adea296541d62cf0a5933ed22e2e4c68cbfbe8513879e`; automation closed without saving.
  All nine named top-level layers supplied identifiable `base_texture` and `dye_mask` artwork.
- One proportional placement per pair produced aligned 128 x 128 RGBA canvases with transparent margins. The
  deterministic preparation step makes active mask RGB white and applies
  `output alpha = min(authored mask alpha, base alpha)` without expanding or redesigning authored selections.
  Small Curtain's real clipped mask group was isolated instead of exporting its coloured base.
- Review artifacts and machine reports are under `content/banner-final-intake/review/`,
  `content/banner-final-intake/extra_small_illustrator_report.json`, and
  `content/banner-final-intake/extra_small_asset_report.json`.

### Runtime hashes

| Definition | Base SHA-256 | Dye-mask SHA-256 |
|---|---|---|
| `road_guard` | `46ce83a31b9954cea1b3934249eab919ca9408658772b96b0e2752c6aaa48b2d` | `8efeff71ca5c8689fef725c7fc3172b783687ffcb3c9dd0bf978874cad048f77` |
| `pale_road_guard` | `d3a95ac9ea835943ee57f12fc8ff68048bcba65fbaa4a90c8761b6a0e22fb9bb` | `2f4c56225adfea15a0bf9f07a58f228555774c189a464af6de7722fe5a9c2da0` |
| `red_crosslets` | `222f27705b9543662db1788bde63fb81ba5efb510a738c811115fbcfa4fe1950` | `31162c610a4b49a049e4589e13d68e54e025771fa86ad13454eac58efdcf4b51` |
| `captains_red_crosslets` | `2eb1cf8b6aa5b1f863dc673b010281875723ab3c0c315328e92a7e56d9e2da68` | `48431385c961644d65cf2bf60f322efb132070a30636da3f5acc94747c463b89` |
| `scarlet_court` | `1d243ac0cff95dd4b3bc5c6e8c3c5117c31c0b747b1b8c7be1f66122ab7e2d2e` | `3e63c73a6abf07eabdbd71d2fb1311a2f7b4b2f3de39c3544e36310d6b1f1814` |
| `verdant_court` | `d7b79fc3d313a2082b16193fabc9a47ffa226a7956cfe0b1ed00355d585c65dd` | `44aa39f534353d60027fbf4ca780a92700ad23dfd57b86ba3cd2d541bab0adb2` |
| `small_curtain` | `e5ead84ea05f73ad61281a0160e2c2875955a195af917494c1bc83849e96b355` | `39d7f7fdc5efc3e11139acbb69ed88ad30abc685d402ab6765a38dc3e8ba4651` |
| `prosperity_standard` | `c40be2bbc878fb3102fd1fada88f150e507612635abe9b81132daaba9f5e4acf` | `187fa08bdd798125328e04b850dc761f4131c0caf8cfc7b0aacf252fd457a043` |
| `guardian_standard` | `d4a4245760608e8ec6048292827672e8d61836497431c173459218b29f9c6e2a` | `5b74d9f79a5f5130e9790aa23a30fe9b543a202dccae686205c84451d22d0b1e` |

Runtime files use
`assets/britannia_mod/textures/banner/<stable_path>/{base_texture,dye_mask}.png`, and every runtime hash matches its
approved intake file.

### Geometry, placement, and rendering

- Road Guard is the physical fabric authority for eight definitions, all of which reference the single
  `britannia_mod:banner/road_guard/geometry` model. Small Curtain references the distinct
  `britannia_mod:banner/small_curtain/geometry` model and its flat-bottomed silhouette. Both models map the authored
  128-pixel canvas without stretching.
- The new `britannia_mod:extra_small` placement profile data-selects distinct
  `britannia_mod:banner/mount/wall_parallel` and
  `britannia_mod:banner/mount/wall_perpendicular` models. The placed extractor selects the model from actual
  orientation, while existing facing transforms handle north, south, east, and west. Brass and iron remain
  independent material IDs/textures and the mount render pass remains untinted.
- Shared baked geometry now remaps its UVs to each definition's registered base/mask sprites. This keeps registration
  catalogue-derived and removes any need for a per-banner Java switch.
- Natural item/preview/placed rendering uses base plus mount; dyed rendering adds only the colour-tinted mask pass.
  Existing state schema, stable IDs, material, resolved colour, pigment provenance, mount, orientation, save/load,
  item/block transfer, break/drop, pick block, re-placement, reload, and client tracking contracts remain intact.

### Catalogue and approval status

- The target is derived from the 35 canonical IDs. Generated status is 26 `placeholder`, 9 `in_progress`, 0
  `complete`, and 0 disabled. The client index contains the shared artwork geometry once, Small Curtain geometry
  once, both orientation mount models once, and all 18 definition-specific textures once. Directory atlas stitching
  covers the entire banner texture tree.
- Road Guard's former base/mask hashes
  `a75880a969570e47fdff0b15eb812eb9e94564f345c424c00af92fe3d4cb1240` and
  `89495bc3e8b9b8a4e98424d539536b57853f08bf20771cf3456151014120b669` belonged to 64 x 64 assets.
  `GATE_E_REVIEW.md` is retained but explicitly historical for those superseded bytes. Road Guard and all eight
  peers remain `in_progress`.
- Final verification passed scaffold idempotency, 616 banner/dyeing tests across 53 suites, the clean boundary, 622
  unrestricted tests across 54 suites, and the production build, with zero failures, errors, or skips. The generated
  all-JAR contains 5,006 unique entries and zero duplicates: 35 definition JSON files; all nine exact family IDs;
  all 18 current 128 x 128 PNGs with intake-matching hashes; both artwork models; both orientation mount models;
  client index; block atlas; brass/iron and still-required placeholder resources. It has no active legacy Small
  Curtain ID, `fabric_base`, `static_overlay`, banner recipe, or banner pattern content.
- No crafting, recipes, patterns, fabric-base/static-overlay architecture, unrelated-family art change, or
  Milestone 17 work was introduced. The live matrix in `EXTRA_SMALL_LIVE_REVIEW.md` is intentionally unperformed.

## 2026-07-27 - Road Guard Gate E product-owner approval and closeout

### Manual review evidence

- The self-identified Product Owner completed the manual review on 2026-07-27 using Minecraft 1.21.1 and NeoForge
  21.1.72 after commit `d03fe2985baf6886cbb109765e68aac9f0a36ac7`. Resource-pack and shader details were not
  specified, no screenshots were supplied, and no general observation was supplied.
- Appearance passed: natural base; fixed charcoal/non-dyeable regions; intended yellow dye regions; preserved
  highlights, shadows, texture, and local contrast; and absence of purple, black, missing-texture, or diagnostic
  fallback.
- Materials passed: natural cotton, wool, linen, and silk, including their distinct metadata and palettes while
  sharing the approved native artwork. Brass and iron mounts both rendered correctly and remained untinted.
- Item and preview passed: inventory; first- and third-person hand; dropped item; item frame; current and proposed dye
  previews; cancel; apply; re-dye replacement; and unlimited dye-tub behaviour.
- All seven pigments passed: Madder Red, Woad Blue, Verdigris, Weld Gold, Soot Black, Chalk White, and Ice Blue.
- Placement passed: parallel, perpendicular, north, south, east, and west; correct artwork rotation; geometry/mount
  alignment; and item/preview/placed consistency.
- Persistence and lifecycle passed: save/reload; relog/tracking; F3+T; `/reload`; break/drop; support loss; pick block
  with complete configured state; and re-placement.
- The Product Owner explicitly marked the overall Road Guard visual result `APPROVED`. The complete evidence is
  recorded in `content/banner-final-intake/submissions/road_guard/GATE_E_REVIEW.md`; it contains no failed or
  unresolved required review item.

### Completion decision

- Gate E passed. Road Guard transitioned from `in_progress` to `complete`; it is the sole completed definition. The
  other 32 definitions remain `placeholder`, no definition remains `in_progress`, and none is disabled.
- Stable ID/index, display name, dimensions, orientations, mounts, default mount, placement profile, geometry,
  two-file asset identities, provenance, state schemas, placement, persistence, dyeing, and command behaviour are
  unchanged. Crafting remains product-disabled, no other banner was integrated, and Milestone 17 has not started.
- Scaffold generation is deterministic and owns the derived Road Guard definition, catalogue status, and metadata.
  The client asset index, block-atlas definition, other 32 definitions, Road Guard geometry, and approved PNG bytes
  remained unchanged across repeated generation and `--check`.
- Five focused completion/content suites passed 63 tests with zero failures, errors, or skips. The first banner-wide
  run correctly exposed one stale administrator projection assertion that still expected 33 non-complete entries.
  The assertion was updated to the approved 32 placeholders, four pages, and explicit exclusion of completed Road
  Guard; its 31-test suite then passed. The final banner/dye selection passed 604 tests across 52 suites with zero
  skips. A clean passed, the unrestricted suite passed 609 tests across 53 suites with zero skips, and the production
  build passed. No new compiler warning was introduced.
- The rebuilt `build/libs/Britannia_Mod-0.1.7k-all.jar` contains 4,975 entries and zero duplicates. It contains 33
  definitions with 32 placeholder, zero in-progress, and one complete (`britannia_mod:road_guard`), plus Road Guard
  geometry/base/mask, the client asset index, and the block-atlas definition. It contains no `fabric_base`,
  `static_overlay`, banner recipe, or pattern content. Embedded Road Guard hashes remain
  `a75880a969570e47fdff0b15eb812eb9e94564f345c424c00af92fe3d4cb1240` and
  `89495bc3e8b9b8a4e98424d539536b57853f08bf20771cf3456151014120b669`.
- The containing closeout commit cannot record its own hash; the full hash is recorded in the final closeout handoff.

## 2026-07-27 - Road Guard placed-render resource-resolution correction

### Symptom and evidence

- A natural Road Guard with valid persisted state (`road_guard`, cotton, `cotton_natural`, no source pigment, brass,
  wall-parallel, 1 x 1) rendered as a purple/black block-like rectangle. The state, synchronized definition,
  definition resource IDs, custom geometry file, and placed footprint were all correct and were not changed.
- The live client log proved that `britannia_mod:banner/road_guard/geometry#standalone` was registered and reached
  model bake, disproving the suspected unregistered-geometry cause. It then reported both Road Guard textures missing
  from `minecraft:textures/atlas/blocks.png`, followed by Britannia item and placed fallback diagnostics with
  `MISSING_BASE_TEXTURE`.
- This was a missing-texture-atlas failure that deliberately selected Britannia's fallback plan. Because
  `banner/placeholder/missing` was absent from the same atlas, that fallback sprite itself resolved to Minecraft's
  purple/black missing sprite. It was not the vanilla missing baked geometry model and was not an unintended full
  block: both banner blocks still return `RenderShape.INVISIBLE`, and only the anchor block-entity renderer emits
  placed banner quads.

### Generic correction and lifecycle

- Added the banner texture directory to the Minecraft block atlas through
  `assets/minecraft/atlases/blocks.json`. Its `banner` directory source emits `banner/` sprite IDs across resource
  namespaces, covering base, mask, mount, diagnostic, and future final banner textures without per-banner entries.
- Replaced Java's per-content geometry/texture allowlist with scaffold-owned
  `assets/britannia_mod/banner_client_assets.json`. It deterministically de-duplicates every geometry, base, and mask
  ID declared by the authoritative catalogue. `ModelEvent.RegisterAdditional` consumes the indexed geometry IDs
  before bake; bake completion validates those models and indexed textures and atomically publishes a new resource
  generation, clearing item, appearance, and placed caches.
- The scaffold generates and owns both client registration resources, and `--check` rejects either missing or changed
  output. Infrastructure-only missing and mount assets remain explicit because they are not definition fields.
  No Road Guard-specific renderer branch or gameplay authority was introduced.

### Validation status

- Focused index/scaffold/Road Guard/placed tests initially passed 45 tests with zero failures, errors, or skips.
  Tests cover all declared geometry and texture IDs, shared-ID de-duplication, standalone
  `ModelResourceLocation` conversion, Road Guard and placeholder registration, missing-index detection, natural and
  recoloured item/preview/placed plans, typed missing resources, both mounts/orientations, all facings, and cache
  recovery.
- The first live correction encoded the atlas directory source as `britannia_mod:banner`. Minecraft 1.21.1's
  `DirectoryLister` accepts a path string rather than a resource ID, and the live log rejected the colon as an
  invalid path segment. The source was corrected to `banner`, which scans that texture subdirectory across resource
  namespaces and preserves the namespace in emitted sprite IDs. A stale processed-resource copy was then identified
  before the next verification and explicitly refreshed; no stale build output is accepted as proof.
- A quick-play launch attempt replaced NeoForge's required generated client arguments and failed before resource
  loading. Normal `runClient` was used for the actual live model-bake verification; the launch-argument failure was
  unrelated to banner code or content.
- The corrected client then completed real model bake with no missing banner model/texture or failed-atlas lookup
  diagnostics. A direct sandbox-world run published all 33 definitions with zero content errors, joined the existing
  world, and produced no item or placed banner fallback diagnostic. This proves resource availability and cache
  recovery at runtime, including the already persisted Road Guard state.
- Product-owner live verification confirms that the placed banner renders correctly, the purple missing-texture
  fallback is absent, the approved Road Guard model/art is visible, and dye application visibly affects the banner.
  This is valid partial Gate E evidence, but it does not approve the remaining mount, material, item-context,
  orientation/facing, persistence, reload, lifecycle, fixed-pixel, or full-pigment matrix.
- Scaffold generation completed twice with 33 definitions, 33 active, zero disabled, 32 placeholder, and one
  `in_progress`. The generated index, atlas, metadata, catalogue status, and Road Guard PNG SHA-256 values were
  identical before, between, and after both runs. `--check` passed, and no banner/dye recipe was generated.
- The generated client index contains six de-duplicated geometry IDs:
  `banner/placeholder/large`, `banner/placeholder/medium_wall`, `banner/placeholder/medium`,
  `banner/placeholder/small`, `banner/road_guard/geometry`, and `banner/placeholder/x_small`. It contains four
  de-duplicated definition textures: placeholder and Road Guard `base_texture`/`dye_mask` pairs. The generated
  `assets/minecraft/atlases/blocks.json` adds the `banner` directory with prefix `banner/`; this also stitches
  placeholder `missing` and brass/iron mount textures without excluding or removing unrelated atlas sources.
- The final focused selection passed 89 tests across ten suites with zero failures, errors, or skips. The full
  `com.seggellion.britannia_mod.bannerdyeing.*` selection passed 603 tests across 52 suites with zero skips. A clean
  passed, the unrestricted suite passed 608 tests across 53 suites with zero skips, and the production build passed.
  The existing compiler-warning baseline remains the missing `@Overwrite` Javadoc on `PlayerSleepMixin` and the
  deprecated-for-removal `OrderShieldItem.initializeClient` override; no new warning was introduced.
- The newly built `build/libs/Britannia_Mod-0.1.7k-all.jar` contains 4,975 entries and zero duplicates. It contains
  all 33 definitions, all required Road Guard and placeholder geometry/textures, both mount textures,
  `assets/britannia_mod/banner_client_assets.json`, `assets/minecraft/atlases/blocks.json`,
  `BannerClientAssetIndex.class`, and `BannerAssetAvailability.class`. It contains no `fabric_base`,
  `static_overlay`, banner recipe, or pattern content. Its embedded Road Guard PNG hashes remain
  `a75880a969570e47fdff0b15eb812eb9e94564f345c424c00af92fe3d4cb1240` and
  `89495bc3e8b9b8a4e98424d539536b57853f08bf20771cf3456151014120b669`.
- The approved Road Guard PNG bytes and hashes remain unchanged. Definition, item state, block-entity state,
  placement structure, dimensions, orientations, mounts, content status, dye rules, and crafting absence are
  unchanged.
- The containing corrective commit cannot record its own hash; the full hash is recorded in the final corrective
  handoff.
- Road Guard remains `in_progress`, and Gate E remains `NOT READY` until the full manual visual matrix is completed.

## 2026-07-27 - Milestone 16 Batch 1A: Road Guard Recolour Proof of Concept

### Approved intake and content decision

- Integrated only `britannia_mod:road_guard` from
  `content/banner-final-intake/submissions/road_guard/road_guard.yml`. The read-only intake validator returned
  `READY_FOR_INTEGRATION`; approval is by `Product Owner`, dated `2026-07-27`.
- Applied only the approved name `Road Guard`, 1 x 1 dimensions, parallel/perpendicular orientations, brass/iron
  mounts with brass default, custom geometry ID, approved reuse of `britannia_mod:placeholder_x_small`, and the two
  Road Guard resource IDs. Stable ID and catalogue index `27` are unchanged.
- Road Guard is the sole `in_progress` definition. The other 32 remain `placeholder`; none is `complete` or disabled.
  Crafting remains product-disabled and Milestone 17 has not started.

### Runtime assets, geometry, and scaffold ownership

- The product owner manually pre-positioned
  `src/main/resources/assets/britannia_mod/textures/banner/road_guard/base_texture.png` and `dye_mask.png`.
  Their bytes were not copied, re-encoded, optimized, or otherwise modified during integration.
- Both files are 64 x 64, 8-bit true-colour RGBA. Base SHA-256 is
  `a75880a969570e47fdff0b15eb812eb9e94564f345c424c00af92fe3d4cb1240`; mask SHA-256 is
  `89495bc3e8b9b8a4e98424d539536b57853f08bf20771cf3456151014120b669`. The mask contains 354
  active and 3,742 transparent pixels, no partial-alpha pixels, and no active pixel over transparent base.
- Added `models/banner/road_guard/geometry.json` as a two-pass planar model adapted from the approved Blockbench
  source. It maps the authored 64-pixel-tall, 22-pixel-wide atlas region onto a centered 1 x 1 presentation plane,
  duplicates the same UV/shape for the mask pass, and omits all legacy mount geometry. Runtime mounts remain
  independent and untinted.
- Integrated asset hashes are declared with the Road Guard catalogue entry. The scaffold validates external model
  and texture existence/hash before generation or `--check`, records the approved hashes in ownership metadata, and
  never generates or overwrites those files, including under `--force`.
- Normal scaffold generation changed the Road Guard definition, shared status report, and ownership metadata;
  localization remained `Road Guard`. The generated definition has exactly `geometry`, `base_texture`, and
  `dye_mask`. No static overlay, rendering strategy, recipe, pattern, or material-specific texture was introduced.

### Rendering, placement, and state

- Existing shared appearance resolution selects the Road Guard base and mask. Natural cotton, wool, linen, and silk
  use the unchanged full-colour base plus the selected mount. Pigment presence or an administratively selected
  non-natural resolved colour inserts the resolved-colour-tinted mask; pigment identity itself is not a render key.
- Custom item geometry is registered through the existing resource/model reload sets. Placed extraction now
  generically derives a custom geometry's existing footprint family from synchronized approved dimensions when its
  resource ID is not one of the placeholder-family IDs. This fixes custom-content fallback without a Road
  Guard-specific Java branch.
- Automated coverage verifies both mounts, both approved orientations, all four horizontal facings, natural and dyed
  item/preview/placed pass ordering, untinted base and mount, typed missing-model/base/mask fallbacks, data-unavailable
  fallback, non-mutation, and later recovery.
- `BannerInstanceState`, its codecs, item-to-block state, block-entity persistence, placed structure, lifecycle, and
  saved-data schema remain unchanged.

### Pixel and automated evidence

- Deterministic semantic coordinates are highlight `(16,18)`, shadow `(10,24)`, fixed charcoal `(6,20)`, and
  transparent background `(0,0)`. Tests cover red, blue, green, light, and dark resolved colours. Highlight remains
  brighter than shadow after each tint; charcoal and transparent pixels remain byte-equivalent to the base output.
- The approved mask contains no partial-alpha pixel, so no such content assertion was invented; the architecture's
  synthetic partial-alpha formula remains covered by Milestone 16A tests.
- The first focused run passed compilation but reported three assertion/fixture issues: zero-valued `complete` status
  was omitted from the report, a model assertion matched the explanatory credit word `mount`, and the re-dye test
  accidentally compared blue with blue. The status generator and two focused assertions were corrected. The repeated
  focused selection passed 54 tests with zero failures, errors, or skips.
- The first restricted intake-validator attempt could not access the Gradle distribution; the approved retry used the
  existing build cache and returned `READY_FOR_INTEGRATION`. This was an environment permission failure, not an intake
  or code failure.
- Normal scaffold generation and final `--check` passed with manifest/definitions/active `33/33/33`, zero disabled,
  33 localizations, 14 provisional names, 32 provisional dimensions, and five placeholder families.
- The banner/dye regression passed 598 tests across 51 suites with zero failures, errors, or skips. The required
  `clean`, from-clean full 598-test run, and `build` all passed. Compilation retained only the existing missing
  `PlayerSleepMixin` `@Overwrite` Javadoc and deprecated-for-removal `OrderShieldItem.initializeClient` warnings.
- The first JAR-inspection script used unavailable PowerShell/.NET `Convert.ToHexString`; replacing only that display
  conversion with `BitConverter` completed the same read-only inspection. The production all-JAR contains 4,968
  unique entries and no duplicates, 33 definitions with status totals 32/1/0, all Road Guard runtime resources, and
  the exact approved PNG hashes. It contains no intake package, Road Guard third texture, removed schema term, or
  missing required resource.
- Final commit evidence is recorded in the milestone handoff because a commit cannot contain its own hash.

### Manual status and next action

- No live client was used. Every natural/recoloured material, mount, item context, preview, placed orientation/facing,
  persistence, relog, resource reload, data reload, break/recovery, pick-block, and re-placement visual check remains
  unperformed.
- Road Guard remains `in_progress`; Gate E is `NOT READY`. Stop after this integration for Road Guard visual review.
  Do not integrate another banner or begin Milestone 17.

## 2026-07-27 - Milestone 16A: Two-File Selective-Recolour Assets

### Corrective architecture decision

- The product owner rejected the former split of a neutral cloth image, dye mask, and fixed-art image. The runtime,
  synchronized display projection, tooling, generated content, intake kit, and tests now have one architecture only:
  `base_texture` plus `dye_mask`. There is no optional fixed-art layer, rendering strategy selector, or legacy
  production schema.
- `base_texture` is the complete authored, full-colour default banner, including silhouette, native colour
  relationships, cloth texture, heraldry, borders, fixed details, highlights, shadows, and transparency.
- `dye_mask` is aligned grayscale RGBA. Its RGB retains brightness, shading, texture, and local contrast; its alpha
  controls replacement strength. Fixed pixels stay in the base and use a transparent corresponding mask pixel.
- With base pixel `B`, mask pixel `M`, resolved dye colour `D`, and normalized mask alpha `a`, the rendered mask colour
  is `T.rgb = M.rgb * D.rgb`; normal source-over composition produces
  `output.rgb = B.rgb * (1 - a) + T.rgb * a` and preserves `output.a = B.a`.
- Natural/default state renders the base unchanged plus the mount. Recolouring activates when a source pigment is
  present or the resolved colour differs from the selected material's natural colour, adding one tinted mask pass
  between base and mount. A pigment resolving to the natural colour still activates the mask.
- Cotton, wool, linen, and silk remain unchanged persisted data identities controlling natural colour, palette,
  resolution, validation, tooltips, and metadata. They never select different banner images and do not tint the
  complete default base merely because the selected material differs.

### Runtime, data, and placeholder migration

- Replaced the `BannerAssets` fields and definition/network codecs with `base_texture` and `dye_mask`. All 33
  controlled definitions were regenerated with the new pair and remain `placeholder`; stable IDs, catalogue
  indexes, dimensions, orientations, supported/default mounts, placement profiles, and schema version `1` are
  unchanged. The display payload retains its existing packet type and has no separate numeric schema field.
- Item and placed appearance state, availability, keys, plans, diagnostics, and caches no longer carry a fixed-art
  identity. Natural item/placed plans contain base plus mount; recoloured plans contain base, tinted mask, plus
  untinted mount. The preview continues to build real current/proposed stacks and now preserves pigment presence so
  it applies the same derived rule.
- The shared baked-model path remains generic. It filters the two texture groups from each shared footprint model,
  delegates the model's translucent render type for partial mask alpha, and creates no banner/material/colour/mount
  combination models. The anchor remains the only placed renderer; parts, bounds, lighting, orientations, facings,
  footprint transforms, placement, and persistence are unchanged.
- Deterministic scaffold generation created `base_texture.png` by drawing the former diagnostic fixed pixels
  (opaque charcoal border and bright ring) over the former grayscale cloth placeholder while preserving the old
  silhouette and alpha. It regenerated the mask so those fixed diagnostic pixels are transparent and retained
  grayscale active cloth pixels. The former two source textures were removed from runtime resources.
- The scaffold owns the new base and mask, generates five two-group translucent footprint models and all 33
  definitions, and reports two banner image assets in the catalogue status. No recipe, status transition, or final
  content was generated.

### Intake and validation

- The guide, review checklist, README, example, and seven-record extra-small template now require exactly one
  full-colour base and one grayscale-alpha mask. They explain transparent, active, and partial mask pixels, fixed
  artwork, material identity, default/recoloured appearance, and the two-file overlapping-detail limitation.
- The read-only validator requires the two files, distinct resource IDs, source files and hashes, matching dimensions,
  8-bit RGBA PNGs, transparent and active mask pixels, and grayscale mask RGB with maximum channel difference `1`.
  The base is permitted to be full-colour. Removed intake keys are explicitly invalid and explain the current
  `base_texture + dye_mask` contract.

### Corrections and verification

- The first migrated test compile reported 29 stale three-field constructors/accessors in test fixtures. Updating
  those fixtures to the single two-file contract corrected compilation. The first synthetic pixel expectations then
  exposed the deliberately rounded integer reference values; correcting those expected values aligned the fixture
  with the implemented multiply/source-over formula.
- Focused architecture/validator/render/preview/pixel selections passed. Normal scaffold generation and `--check`
  both passed with manifest/definitions/active `33/33/33`, zero disabled entries, 33 localization entries, 14
  provisional names, 33 provisional dimensions, and five geometry families.
- The required banner/dye regression passed 591 tests across 50 suites with zero failures, errors, or skips.
  `gradlew.bat clean --no-daemon`, the from-clean full 591-test/50-suite run, and
  `gradlew.bat build --no-daemon` all passed. The unchanged compile warning baseline remains the missing
  `PlayerSleepMixin` `@Overwrite` Javadoc and deprecated-for-removal `OrderShieldItem.initializeClient`.
- All 42 changed JSON/JSON-compatible YAML files parsed. Both new 16 x 16 images are 8-bit true-colour RGBA PNGs;
  the mask has 72 transparent and 184 active grayscale pixels, all aligned over opaque base pixels.
- The production all-JAR has 4,963 unique entries and zero duplicate names. It contains 33 definitions using base
  plus mask, the two required placeholder images, no removed placeholder image, no removed runtime class term, and
  no banner recipe.
- No live client was launched. Every requested visual/manual check remains explicitly unperformed; no visual
  equivalence or final-art claim is made.
- Commit subject: `refactor(banners): adopt two-file selective recolour assets`. Its full hash is recorded in the
  final handoff because a commit cannot contain its own hash.

### Next milestone

Return to Milestone 16 Batch 1 only after approved two-file final assets and decisions exist. Milestone 17 has not
started.

## 2026-07-26 - Milestone 16 Preparation: Final Banner Content Intake Kit

### Reason and scope

- Milestone 16 Batch 1 correctly stopped without content edits or a commit: no extra-small definition had approved
  final owner decisions, original final artwork, complete provenance, or manual verification evidence.
- Added a documentation and validation-tooling intake kit only. No final art was generated, copied, inferred, or
  integrated. Milestone 16 remains pending actual approved content, and Milestone 17 has not started.
- The live catalogue remains exactly 33 stable definitions with all 33 at `placeholder`. No name, dimension,
  orientation, mount, default mount, placement profile, asset resource, localization, definition JSON, registry
  content, runtime behavior, or command changed.

### Owner-facing intake files

- Added `docs/banner-dyeing/FINAL_CONTENT_INTAKE.md`, defining stable identity, required decisions, image
  responsibilities, independent mounts, file and alpha rules, provenance, approval/content states, existing-world
  consequences, the integration sequence, and the product-disabled crafting boundary. Milestone 16A subsequently
  replaced its original three-image intake contract with the authoritative two-image contract.
- Added `docs/banner-dyeing/FINAL_CONTENT_REVIEW_CHECKLIST.md`, covering identity, assets, automated checks, item
  contexts, four materials and three representative colours, brass/iron, both orientations and all facings,
  persistence/lifecycle, and final owner review.
- Added `content/banner-final-intake/README.md`, `intake.example.yml`, and
  `extra_small_batch.example.yml`. The templates use JSON-compatible YAML 1.2, are visibly `NOT_APPROVED`, live
  outside runtime resources, and contain no recipe, pattern, crafting, price, NPC/shop, arbitrary-NBT, component,
  source-pigment, or item-orientation fields.
- The batch template names each of the seven stable extra-small IDs exactly once. All decision fields remain blank or
  null; existing values appear only inside a clearly labelled `current_provisional_state`.

### Read-only validator

- Added `FinalContentIntakeValidator` to the existing non-runtime scaffold source set and a narrow
  `--check-final-intake <path>` dispatch in `BannerScaffoldTool`. Normal generation, `--check`, and `--force`
  execution paths are unchanged.
- The validator reads one intake plus the live manifest identity set, validates schema and supported values, confirms
  default-mount membership, resolves repository-relative source paths safely, checks file existence and SHA-256,
  decodes the required PNG images, requires matching dimensions and 8-bit true-colour RGBA, rejects ambiguous asset
  resource IDs and approved placeholder IDs, and validates approval/provenance/permission fields. Milestone 16A
  subsequently changed the required image set to exactly base plus mask.
- Results are deterministic and typed as `NOT_READY`, `READY_FOR_INTEGRATION`, or `INVALID`. Manual verification is
  checked for truthful attribution when claimed but is not required for integration readiness; it remains mandatory
  before `content_status: complete`.
- The validator performs no writes, asset copies, definition generation, catalogue mutation, status transition,
  staging, or registry publication. Intake files are not a runtime registry folder and are not packaged as live data.

### Tests and validation

- Added focused documentation/template and validator tests for required files and clauses, forbidden fields, all
  seven unapproved records, runtime isolation, catalogue preservation, valid readiness, `NOT_APPROVED`, missing and
  unknown IDs, dimensions, orientations, mounts, default mount membership, asset existence, PNG validity and alpha,
  provenance, distribution permission, copied-reference rejection, ambiguous mappings, manual-claim attribution,
  deterministic output, and non-mutation.
- The first focused compile found one local report-list variable that was captured by lambdas and then reassigned.
  Separating the ordered immutable result fixed compilation without changing validation policy. The final focused
  selection passed 17 tests across two suites with zero failures, errors, or skips.
- `.\tools\scaffold_banners.bat --check` passed with the unchanged 33 definitions, 33 active, zero disabled,
  14 provisional names, 33 provisional dimensions, and five placeholder families. The banner/dye regression passed,
  then `.\gradlew.bat clean --no-daemon`, the from-clean full 584-test/49-suite run, and
  `.\gradlew.bat build --no-daemon` all passed.
- The production all-JAR contains 33 placeholder definitions, the unchanged six placeholder models and four
  placeholder textures, zero intake templates/classes, zero banner recipes, zero pattern entries, and no duplicate
  entry names. `git diff --check` and JSON-compatible YAML parsing passed.
- Banner crafting remains absent and product-disabled.

## 2026-07-26 - Milestone 15: Admin Tools, NPC-Ready Dye Sources, and Debugging

### Command architecture and authority

- Added one common/server command tree under provisional root `/britannia`, registered once through the existing
  `CommandRegistry` `RegisterCommandsEvent` listener. Every branch inherits the repository-standard
  `hasPermission(2)` predicate before registry or inventory work. No client command, persistent command state, or
  custom command packet was added.
- Branches are `banner give <targets> <definition> [material] [colour] [mount]`, `banner validate`,
  `banner placeholders [page]`, `dye tub give <targets> [pigment]`, and
  `dye resolve <pigment> <material>`.
- Suggestions read the latest immutable registry publication on every request: 33 active definition IDs, four
  material IDs, only the selected material's active palette colours, only the definition's active supported mounts,
  and seven active pigments that have an authoritative registered pigment item. Missing arguments or unavailable
  registry data yield no unsafe contextual suggestions.

### Banner and dye-tub administration

- `BannerAdminService` validates active definition/material/palette/colour/mount references and delegates complete
  state construction to `BannerItemFactory`. Definition-only acquisition uses `britannia_mod:cotton`, cotton's
  authored natural colour, the definition default mount, current schema, and absent source pigment. A material-only
  request uses that material's natural colour. Direct explicit colours must belong to the selected palette and still
  record no source pigment; explicit mounts must be active and supported.
- Multi-target behavior is partial success: each target receives a separately created stack, and one failed target
  does not corrupt or roll back successful targets. Delivery follows existing normal insertion then safe
  `drop(stack, false)` remainder behavior. A remainder is dropped at most once and is never silently deleted.
- `DyeTubAdminService` creates only the registered stack-size-one dye tub. Empty output stores canonical
  `DyeTubState.empty()`; loaded output stores the server-validated stable pigment ID with absent
  `remaining_uses`, the sole unlimited representation. Administrative creation consumes no pigment, mutates no held
  stack, and emits no loading effects.

### Validation, catalogue, and dye debugging

- `BannerCatalogueAdminService` is read-only. It reuses the existing `ProductionBannerCatalogue` and
  `ProductionDyeContent` boundaries plus diagnostics retained from registry publication. Output reports 33 active/0
  disabled banners, four materials, four palettes, seven pigments, two mounts, 33 incomplete placeholders, 14
  provisional names, 33 provisional dimensions, zero errors, and zero warnings. Chat issue detail is capped at five
  entries.
- Placeholder output is stable-ID lexical order, eight entries per page, default page one, with out-of-range pages
  clamped safely. It reports localized name, dimensions, content status, provisional-name marker, provisional-
  dimension marker, total count, and page count. The count is derived from active registry data, not hard-coded.
- `DyeResolutionDebugService` invokes the same `DyeResolver.explain` path as gameplay. The production explicit sample
  `madder_red + cotton` resolves to `cotton_red` as `EXPLICIT_MAPPING` with distance approximately
  `0.060252859817`. The nearest sample `woad_blue + cotton` resolves to `cotton_blue` as `NEAREST_COLOUR` with
  distance approximately `0.018741671496`. Output also includes compatible/rejected counts and the resolver's final
  tie-break classification; no registry, item, tub, or inventory state is mutated.

### Pigment-source boundary and scope

- Added `PigmentSourceService` and `RegistryPigmentSourceService`. A stable pigment ID is checked against snapshot
  availability, active/disabled registry state, the immutable registered `PigmentItem` identity, positive count, and
  stack maximum before a fresh stack is returned. Available entries are immutable, deterministic, and expose only
  stable pigment ID, display translation key, registered item ID, existing rarity, and existing tags.
- No NPC adapter was registered because the repository has no safe dependency-free NPC/shop service extension point.
  No NPC entity, merchant inventory, price, currency, stock persistence, dialogue, economy hook, shop UI, loot,
  crafting, or survival acquisition behavior was added.
- Corrective Milestone 14R remains intact: no pattern item/component, banner recipe serializer, banner recipe JSON,
  banner crafting tag, cloth/mount crafting-input item, configured creative pattern variant, or crafting test was
  restored. No placement, rendering, direct placed-banner dyeing, final art, catalogue ID/dimension, or Milestone 16
  content change was made.

### Automated and manual verification

- Initial restricted Gradle compilation could not access the wrapper distribution. The approved retry reached Java
  compilation and found two `CommandSyntaxException` message-type errors; converting Brigadier `Message` values to
  literal components corrected both. Production compile then passed with only the two existing warnings.
- The first focused Milestone 15 run executed 31 tests with one scope-fixture failure because a Javadoc sentence
  contained the literal word `price`; the sentence was made acquisition-neutral without changing behavior or
  weakening the scope assertion. The identical focused command then passed 31/31.
- `tools\scaffold_banners.bat --check` passed with manifest=33, definitions=33, active=33, disabled=0,
  localization=33, provisional names=14, provisional dimensions=33, and five asset families.
- The banner/dye regression command passed 567 tests across 47 suites with zero failures, zero errors, and zero
  skips. `gradlew.bat clean --no-daemon` passed in 14 seconds; the from-clean full test passed the same 567 tests,
  and final full test/build reruns passed. `gradlew.bat build --no-daemon` completed `check`, `jar`, `jarJar`,
  `assemble`, and `build`.
- The production all-JAR contains the command tree, 22 administrative projection classes, eight pigment-source
  classes, 33 banner definitions, and the existing banner/dye runtime. It contains zero banner recipe resources,
  banner crafting/pattern classes, or Milestone 15 NPC/shop classes. Both changed JSON files parse successfully, and
  `git diff --check` passed.
- No live Minecraft client/server was launched. All 32 manual command/end-to-end checks are explicitly unperformed;
  no recipes or configured creative variants were added merely to make manual testing easier.
- Commit subject: `feat(banners): add admin and dye debugging tools`. The full hash is recorded in the final handoff
  because a commit cannot contain its own hash.

### Next milestone

Milestone 16 only. It has not started.

## 2026-07-26 - Corrective Milestone 14R: Remove the Unintended Banner Crafting System

### Reason and history

- Milestone 14 implemented the draft playbook's banner-pattern and crafting architecture in commit
  `6803f5ee232800f780712dd2c6cdc8b34cd84988`.
- The product owner rejected that crafting system before release. Corrective Milestone 14R removes it through a new
  forward corrective commit; Milestone 14 remains in history and history was not rewritten.
- The corrective commit uses message `revert(banners): remove unintended banner crafting system`. Its full hash is
  recorded in the final handoff because a commit cannot contain its own hash.
- No replacement recipe, acquisition mechanism, command, creative configured-banner variant, NPC integration, loot
  table, pattern alternative, or Milestone 15 feature is introduced.

### Removal and preservation boundary

- Started with `git revert --no-commit 6803f5ee232800f780712dd2c6cdc8b34cd84988` and audited the complete 68-file
  inverse diff against Milestone 13 commit `b5f2ec8fa06e981125857a7e247b3a667df0c8b3`.
- Removed the four crafting-only items, pattern component, recipe serializer, ten production Java classes, 33
  recipe JSON files, six crafting tags, four item models, crafting localization, 33 creative pattern variants,
  crafting tests, and recipe/tag/status behavior from the scaffold.
- Restored the Milestone 13 item, component, creative-tab, scaffold, and regression-test boundaries. Added only a
  generated status note, corrective documentation, and narrow absence/preservation tests beyond the Milestone 13
  tree.
- Preserved all 33 definitions and their stable IDs/dimensions, four fabrics, four palettes, seven pigments, brass
  and iron mount definitions, shared banner state/factories, dye tub, server-authoritative preview/application,
  placement, multi-block lifecycle, both orientations, placement ghost, item/placed rendering and synchronization,
  persistence, break/drop, pick-block, explosion/piston behavior, and cross-chunk integrity.

### Validation

- `.\tools\scaffold_banners.bat` initially preserved the status file because the established CRLF/LF metadata
  mismatch classified it as customized. `.\tools\scaffold_banners.bat --force` refreshed only declared scaffold
  outputs. Final `.\tools\scaffold_banners.bat --check` passed with 33 manifest entries, 33 definitions, 33 active,
  zero disabled, 33 localization entries, 14 provisional names, 33 provisional dimensions, and five asset families;
  the scaffold reports no recipe count.
- The first 37-test focused run had one test-fixture initialization failure because the new scope test loaded a
  deferred registry before the established Minecraft bootstrap. Adding the existing
  `Milestone7RegisteredTestContent.ensureRegistered()` bootstrap fixed it; the identical focused command then passed
  all 37 tests.
- A later four-test count check initially expected 474 repository items from an incomplete source-line estimate; the
  runtime-derived total was 550 because multiline registrations were omitted by that estimate. The assertion was
  corrected to the verified runtime total and passed 4/4. The required banner/dye feature subset remains nine items.
- `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` passed all
  536 tests across 46 suites. `.\gradlew.bat clean --no-daemon` passed, followed by
  `.\gradlew.bat test --no-daemon --stacktrace` with 536 tests, zero failures, zero errors, and zero skips.
  After the final count correction the full test command passed again.
- `.\gradlew.bat build --no-daemon` passed. The packaged all-JAR contains 33 definitions, four materials, four
  palettes, seven pigments, two mounts, `BannerItemFactory`, the shared banner item, dye runtime, and the placed
  renderer. It contains zero banner recipes, crafting tags, removed crafting/pattern classes, or removed item models.
- An optional `compileJava --rerun-tasks` warning audit exceeded the two-minute command window; its two spawned Java
  processes were stopped after the required build had already passed. A final normal build then passed in 10 seconds
  with every task up to date. The unchanged source warning baseline remains the missing mixin `@Overwrite` Javadoc
  and deprecated-for-removal `OrderShieldItem.initializeClient` warning observed by the clean Milestone 14 compile.
- The two surviving changed JSON files (`content/.banner_scaffold_metadata.json` and `en_us.json`) parse successfully;
  scaffold/Gson tests validate the generated data. Final diff, staged-diff, scope, and Git-history checks are recorded
  in the handoff report.

## 2026-07-26 - Milestone 13: Placed Banner Rendering and Live State Sync

### Preflight baseline

- Verified branch `banners-dyetub`, HEAD `2ee72ccdf036a6e63a9d6fa55cabd1aa000fd29b`, merge base with `patch-18`
  `62df1dc97c5113a86f9c0f258cb90538f31efe89`, divergence `0 13`, and the sole preserved untracked path
  `.claude/`.
- Previously preserved `ModConfig.java`, root specification, `logs/`, and `tmp/` paths were intentionally absent
  before Milestone 13. They were not restored or recreated. `.claude/` remained untouched, uninspected, uncommitted,
  and excluded from staging.

### Shared appearance and placed rendering

- Refactored item appearance extraction around immutable `BannerAppearanceState`/`BannerAppearanceKey` values shared
  with placed rendering. The shared record contains only stable display inputs and resource/data generations; it
  contains no world, player, position, block entity, item-stack, mutable registry, render-buffer, or time reference.
- Registered exactly one client-only `BannerBlockEntityRenderer` for the authoritative anchor and zero child
  renderers. Anchor and part block models are explicitly invisible while their established selection/collision
  behavior is preserved.
- Added generated, two-sided cutout meshes for large 3x2, medium-wall 2x2, medium 1x2, small 1x1, and x-small 1x1
  families in both wall-parallel and wall-perpendicular orientations across all four facings. At Milestone 13 the
  renderer used the then-current three-image contract; Milestone 16A superseded its image fields and pass list while
  retaining the geometry, facings, and untinted mounts. No final heraldic art was added.
- Geometry keys include appearance, persisted orientation/facing/footprint, family, mount, reload generations, and
  fallback state, but not anchor position or world identity. Persisted occupancy remains authoritative when current
  definition dimensions disagree, and the renderer emits one safe missing-content diagnostic without mutating the
  block entity or its children.

### Bounds, lighting, cache lifetime, and live state

- Dynamic render bounds are the persisted occupied-cell union plus a finite 0.125-block cloth/mount margin. The
  renderer uses a finite 64-block distance from the full bounds and relies on those bounds for ordinary frustum
  culling; malformed state falls back to a finite one-cell box.
- Lighting takes the maximum block/sky values from the anchor and at most six occupied cells whose chunks are already
  loaded. It never forces a chunk load and does not use full-bright lighting.
- Shared appearance and placed-plan caches are each access-bounded to 256 entries; placed diagnostic de-duplication
  is separately capped at 256. Display-data replacement and model/resource reloads invalidate item, appearance, and
  placed caches. Diagnostics are generation-aware and de-duplicated, so missing content can recover after a later
  data or resource reload without retaining stale plans.
- Added a server-only banner-state replacement boundary that preserves the placed structure, marks the anchor dirty,
  and calls `sendBlockUpdated`. Existing update-tag and update-packet paths synchronize initial tracking, late join,
  and live refresh; rendering re-extracts current anchor state and naturally selects a new bounded key.

### Tests, corrections, and verification boundary

- Added deterministic placed-render tests for shared appearance/tint separation, five families, two orientations,
  four facings, two mounts, anchor convention, bounds, fallback, cache bounds/invalidation, state changes, update
  tags/packets, and dedicated-server client isolation.
- The first focused run exposed a test-fixture error: it paired the production block-entity type with a custom test
  block. A narrow test `BlockEntityType` was introduced after Minecraft test bootstrap; one missing import and one
  premature static initializer were corrected. A production compile then exposed one reassigned facing captured by a
  lambda; extraction now captures the normalized final facing. No production behavior was weakened to satisfy tests.
- Focused `BannerPlaced*` validation passed 34 tests. The combined item/placed render selection passed, and the
  banner-dyeing package passed 532 tests across 45 suites with zero failures, errors, or skips before the clean full
  gate.
- No GameTest/client integration source was added: the repository has no established GameTest source/bootstrap or
  live client-render harness, and introducing one would be unrelated framework construction. The automated boundary
  verifies serialized update tags/packets and renderer state changes; it does not claim a manually observed live
  client session.
- Manual in-game matrix testing was not performed because there is still no safe configured-banner acquisition path.
  No recipe, creative entry, or command was added solely for QA.
- `tools\scaffold_banners.bat --check` initially reported all 49 generated text artifacts changed because the
  machine-wide Git checkout converted their tracked LF bytes to CRLF while the checker compares generated LF bytes
  literally. Normalizing only those files to their existing tracked bytes produced no content diff; the exact rerun
  passed with 33 manifest entries, 33 definitions, 33 active/0 disabled, 33 localization entries, 14 provisional
  names, 33 provisional dimensions, and five asset families. Scaffold generation was not run.
- The required banner package test passed 532 tests across 45 suites. From-clean full `gradlew.bat test --no-daemon
  --stacktrace` also passed 532 tests across 45 suites with zero failures, errors, or skips. `gradlew.bat clean
  --no-daemon`, `gradlew.bat build --no-daemon`, and `git diff --check` passed; the production build completed
  `jar`, `jarJar`, `assemble`, `check`, and `build`.
- `Britannia_Mod-0.1.7k-all.jar` contains one anchor renderer, zero part renderers, the shared appearance classes,
  placed render state/key/cache/geometry classes, five family models, three fabric layers, both mount models and
  textures, the missing-content resources, and the existing parallel/perpendicular block resources. Banner crafting,
  recipe, command, and direct-world dye handler class checks each found zero entries.

### Scope boundary and next milestone

- No crafting, recipe, admin command, NPC integration, direct placed-banner dyeing, mount swapping, final artwork,
  extra block entity, child renderer, or Milestone 14 work was added.
- Stop after the Milestone 13 validation and commit. Do not begin Milestone 14.

## 2026-07-21 - Milestone 12: Orientation-Aware Placement and Mount Variants

### Orientation selection and authority

- Added stable wall-parallel and wall-perpendicular placement modes without adding orientation to
  `BannerInstanceState`. Sneak-use cycles a server-owned, per-player ephemeral preference in stable order, normalizes
  it against the held definition, and synchronizes only the selected display value to the client. Logout and server
  stop clear preference state; no client-to-server orientation payload or item-stack mutation was introduced.
- Definitions that expose one orientation remain fixed to that mode. Definitions that expose both default
  deterministically to wall-parallel. Unsupported orientation and mount choices fail before world reads or mutation.

### Geometry, support, persistence, and lifecycle

- The shared local/world transform now drives placement, preview, part-to-anchor resolution, persistence, repair,
  removal, pick block, and diagnostic shapes for both orientations and every horizontal facing. Parallel width grows
  viewer-right and requires wall support behind every top-row cell. Perpendicular width grows outward from the wall
  and requires only the anchor's wall support.
- Anchor and generic part blockstates persist orientation. The anchor's versioned placed-structure record remains
  authoritative across reload and definition changes; legacy records still migrate to one-cell wall-parallel.
  Integrity repairs a stale anchor orientation property from the persisted record, rejects mismatched parts, and
  teardown remains duplicate-proof and rollback-safe.
- Brass and iron remain instance mount IDs flowing through planning, persistence, preview, item return, and render
  descriptors. They do not change occupied cells or support requirements in this milestone.

### Client ghost and presentation boundary

- Added a client-only world-space wireframe ghost after particles. It uses synchronized display-only registry data,
  the held configured shared banner, the selected orientation, already-loaded chunks, and the same pure transforms as
  server planning. It renders planned cells, required supports, dimensions, orientation, mount, and typed local
  failures without changing blocks or sending per-frame packets.
- Locally clear plans remain advisory because server protection is not fully knowable on the client. The preview says
  so explicitly and server placement always revalidates. Brass uses gold, iron gray, missing mount data magenta,
  blocked cells red, and invalid supports orange. Placed models remain neutral diagnostic assets; final cloth and
  mount artwork are intentionally deferred.

### Gate D status and validation evidence

- The generated catalogue status now contains one row for every stable ID with dimensions, supported orientations,
  supported/default mounts, automated parallel/perpendicular/brass/iron results, manual result, and content status.
  All 33 rows pass the automated matrix; manual in-game validation was not performed. Final names, dimensions,
  per-definition orientations, per-definition mounts, and placed artwork remain unapproved.
- Normal scaffold generation and `tools\\scaffold_banners.bat --check` passed with 33 manifest entries,
  33 definitions, 33 active/0 disabled entries, 33 localization entries, 14 provisional names, 33 provisional
  dimensions, and five placeholder asset families.
- Focused M12 validation passed with 88 tests across 13 suites, 0 failures, 0 errors, and 0 skipped. Its planner
  matrix executes 1,056 successful plans: 33 definitions x two mounts x two orientations x four facings.
- `gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` passed with
  496 tests across 41 suites before the final planner read-order hardening. Final `gradlew.bat clean --no-daemon
  --stacktrace` passed in 25 seconds; the clean full `gradlew.bat test --no-daemon --stacktrace` passed in 2 minutes
  57 seconds with 498 tests across 41 suites, 0 failures, 0 errors, and 0 skipped.
- Final `gradlew.bat build --no-daemon --stacktrace` passed in 51 seconds and completed `jar`, `jarJar`, `assemble`,
  `check`, and `build`. `Britannia_Mod-0.1.7k-all.jar` contains the M12 preference, preview, ghost, payload, and
  perpendicular-model resources; it contains zero part block entities, placed block-entity renderers, or part item
  models. Existing compiler warnings are unchanged and no M12 warning was introduced.
- Milestone commit subject: `feat(banners): add orientation-aware placement and mount variants`; the resulting hash
  is reported in the handoff because a commit cannot contain its own hash.

### Scope boundary and next milestone

- No recipe, creative-tab entry, admin/debug command, NPC integration, direct placed-banner dyeing, new banner item,
  per-definition block, part block entity, placed block-entity renderer, or final heraldic asset was added.
- Stop at Gate D after the Milestone 12 commit. Do not begin Milestone 13.

## 2026-07-21 - Milestone 11: Multi-Block Anchor and Occupied Parts

### Structure content, footprint, and transform

- Kept `britannia_mod:banner` and its existing `britannia_mod:banner` block entity as the single authoritative anchor.
  Added exactly one generic `britannia_mod:banner_part` occupied-part block with no block entity and no `BlockItem`.
  Parts store only bounded local offsets plus facing; the anchor alone owns the complete `BannerInstanceState` and
  authoritative placed footprint.
- All 33 active wall-parallel definitions are eligible from catalogue dimensions: 13 use 1x1, 8 use 1x2, 6 use
  2x2, and 6 use 3x2. Maximum occupancy is six cells. Placeholder/provisional status does not affect eligibility,
  and there is no stable-ID or group-name footprint switch.
- The clicked wall-adjacent target is the top-left anchor when viewed from the front. `FACING` points outward;
  viewer-right is `FACING.getCounterClockWise()`. Local horizontal offsets increase viewer-right and vertical offsets
  increase downward. Cells and stored offsets are deterministically ordered by vertical offset and then horizontal
  offset, with the anchor first.

### Saved placement state and Milestone 10 migration

- Added versioned placed-structure schema 1 under anchor NBT key `placed_structure`. It stores stable orientation,
  width, height, and the complete ordered occupied offsets. The stored footprint remains authoritative if a later data
  pack changes a definition's dimensions, and it can be decoded without the live banner registry.
- A Milestone 10 anchor missing `placed_structure` migrates to a valid one-cell wall-parallel placement and is marked
  changed for the next save. Unknown/future or malformed placement data is contained as structurally invalid without
  erasing otherwise decodable banner state.
- The same banner and placement records are used by disk NBT, update tags, and update packets. Placement transfers both
  records before child placement and synchronizes only after the complete structure verifies.

### Placement transaction and support

- Planning remains read-only and server-authoritative. It validates item state, registry references, profile,
  rectangular dimensions, every world/border/build-height position, every already-loaded chunk, replaceability,
  protection, all top-row wall supports, anchor block-entity compatibility, part offset encoding, and final blockstate
  acceptance before mutation. It never requests or force-loads a chunk.
- The support policy is one sturdy wall face behind every top-row cell; lower rows do not require their own wall face.
  Placement order is anchor block, anchor banner/placement data, row-major parts, exact cell and anchor-state
  verification, client synchronization, neighbor/effect notification, and finally item consumption. Survival consumes
  exactly one shared banner after success; creative consumes none.
- Every failure restores exact pre-placement blockstates in reverse mutation order with drops suppressed. Rollback and
  structure mutation run under the shared lifecycle guard, and consumption/effects never occur for a failed attempt.

### Removal, drops, pistons, and pick block

- One central lifecycle service owns anchor break, child break, support loss, explosions, external replacement, orphan
  cleanup, recovery failure, and rollback cleanup. A per-level/anchor reentrancy guard prevents callbacks from removing
  twice or duplicating drops.
- Survival anchor break, child break, and support loss remove every occupied banner cell and emit exactly one item at
  the anchor, reconstructed from the anchor's exact state. Creative removal emits none. Explosion policy is complete
  removal with no banner drop. Vanilla block loot/player-destroy callbacks emit no competing item.
- External replacement preserves the replacement cell and cleans the remaining known banner cells without a drop.
  Both anchor and part use `PushReaction.BLOCK`, preventing piston push and sticky pull.
- Pick block on the anchor reconstructs the exact shared banner item. Pick block on a child resolves its encoded anchor
  without loading chunks, validates membership against synchronized anchor placement data, and returns the same item.

### Chunk integrity and recovery

- Chunk-load inspection is deferred until `ServerTickEvent.Post`, because NeoForge warns against level interaction in
  the load callback. Placement, part resolution, inspection, and repair use only already-loaded chunks through
  `hasChunk`, `hasChunkAt`, and `getChunkNow`.
- A child whose anchor chunk is unloaded is left untouched. A definitively absent/invalid/mismatched anchor makes the
  child an orphan and removes it without a drop. Missing expected parts are repaired only when their chunks are loaded
  and cells are replaceable. Any obstruction is preserved and the remaining known structure is removed without a
  drop; repair never overwrites unrelated content.
- Persisted offsets drive reload, integrity, repair, removal, and definition-change behavior. Cross-chunk placement is
  accepted only when every involved chunk is already loaded; subsequent independently ordered chunk loads do not cause
  false orphan deletion.

### Assets, scope, and validation evidence

- Packaged diagnostic resources contain the existing anchor blockstate/model and the new part blockstate/model. The
  part is a thin full-cell occupancy marker with outline/collision geometry supplied by its block. The production JAR
  contains `BannerPartBlock`, the anchor block entity, and all structure services; it contains no part item model,
  part block entity, banner block-entity renderer, or placed layered renderer.
- No GameTest was added because the repository has no GameTest source root, bootstrap, templates, or registration.
  Unit tests exercise actual blocks and block entities plus the footprint, planner, transaction, persistence, lifecycle
  policy, resources, and source-level platform boundaries. Live in-game interaction/visual checks were not performed
  because no safe configured-banner acquisition path exists; no recipe, creative entry, or admin command was added for
  QA.
- Narrow Milestone 11 tests passed: 38 tests, 0 failures, 0 errors, 0 skipped. The first run had one test-fixture
  failure because a multi-block support position was hard-coded at y=70 while the planned click was at y=0; the test
  now derives that support position from the plan, and production code was unchanged.
- `tools\\scaffold_banners.bat --check` passed: manifest=33, definitions=33, active=33, disabled=0,
  localization=33, provisional names=14, provisional dimensions=33, asset families=5.
- `gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` passed with
  480 tests, 0 failures, 0 errors, 0 skipped across 38 suites. `gradlew.bat clean --no-daemon` passed in 13 seconds.
  The clean `gradlew.bat test --no-daemon --stacktrace` passed in 1 minute 53 seconds with the same 480/0/0/0 result.
  `gradlew.bat build --no-daemon` passed in 22 seconds and completed `jar`, `jarJar`, `assemble`, `check`, and `build`.
- One earlier `compileTestJava` launcher timed out while Gradle daemons remained active; `gradlew.bat --stop` stopped two
  daemons and the immediate rerun passed without a source change. Existing compiler warnings remain the missing
  `@Overwrite` Javadoc on `PlayerSleepMixin` and deprecated-for-removal `OrderShieldItem.initializeClient`; no new
  compiler warning was introduced.

### Counts, limitations, and next milestone

- Production content remains 33 active/0 disabled banners, 4 materials, 4 palettes, 7 pigments, and 2 mounts.
  Registration remains one shared banner item, one dye tub, seven pigment items, and three total data components
  including wine data. This milestone adds one block but zero items and zero block-entity types.
- Development presentation remains static diagnostic geometry. Wall-perpendicular placement, orientation selection,
  cycling, placement ghosts, mount-dependent occupancy, placed layered rendering, direct placed-banner dyeing,
  recipes, commands, NPC integration, and final heraldic artwork remain out of scope.
- Ordinary external `setBlock` replacement is handled through block callbacks. A world-edit tool that bypasses normal
  callbacks can temporarily leave parts until deferred chunk integrity processing; no universal hook exists for tools
  that bypass both callbacks and chunk lifecycle.
- No approved catalogue identity or dimension changed. Next milestone: Milestone 12 only. It has not started.

## 2026-07-21 - Milestone 10: Single-Block Placement Foundation

### Scope and registration

- Added one focused `BannerBlockRegistry` with exactly one `britannia_mod:banner` block and one matching block entity
  type. No `BlockItem` or per-definition block registration was added; the existing shared `BannerItem` remains the
  only inventory form and now routes `useOn` through the placement service.
- Eligibility is data-driven from the immutable registry snapshot. A banner is placeable only when its active
  definition is exactly 1x1 with a supported wall orientation and its stored material, palette colour, optional source
  pigment, and mount all remain resolvable and active. The 13 currently eligible definitions are selected by these
  traits rather than an ID allowlist; the other 20 definitions fail with a typed deferred/unsupported result.
- Added a four-way horizontal blockstate and one thin diagnostic block model that reuses the existing missing texture.
  This milestone intentionally adds no block-entity renderer, layered placed-banner rendering, child/part blocks, or
  final heraldic art.

### Planning, authority, and transaction boundary

- Placement is server-authoritative. The planner is read-only and validates the shared item, complete stored state,
  current registry publication, single-block eligibility, horizontal clicked face, replaceable target, world and
  border bounds, sturdy wall support, protection checks, block-entity compatibility, and state acceptance before any
  mutation. `FACING` points outward and the support is the block opposite that direction.
- The executor mutates exactly one target cell, locates the new banner block entity, transfers the complete
  `BannerInstanceState`, verifies exact equality, and only then consumes one item in survival. Creative placement does
  not consume. Sounds and the block-place game event occur only after a committed success.
- A placement, block-entity, state-transfer, or verification failure restores the exact original target block state
  with drops suppressed. Typed failures are localized to server action-bar feedback. The live adapter respects
  `ServerLevel.mayInteract`, `Player.mayUseItemAt`, the world border, build height, replaceability, and sturdy-face
  support checks.

### Persistence, synchronization, support, and item return

- `BannerBlockEntity` persists the complete versioned state through `BannerInstanceState.CODEC`, including banner
  definition, material, resolved colour, optional source pigment, and mount. Load failures are contained and logged as
  structurally invalid state rather than crashing or silently substituting another catalogue entry.
- Runtime status distinguishes configured-valid, configured-with-missing-references, unconfigured, and structurally
  invalid. Missing data-pack references never rewrite the stored stable IDs. State changes mark the entity changed and
  send the normal block-entity update; update tags and packets use the same persisted representation.
- Normal break, support loss, explosion/default block drops, and pick-block all use the block entity as the sole source
  for reconstructing one shared banner item. Valid and reference-missing states preserve all six state fields exactly.
  Unconfigured or structurally invalid entities safely return one raw, unconfigured shared banner rather than a guessed
  configuration. There is no duplicate loot-table path.
- The wall-parallel shape rotates with `FACING`. Removing the supporting face transitions the block to air through
  normal neighbor-update behavior, allowing the same one-item drop path to run.

### Automated coverage and corrections

- Added 33 focused tests for data-driven 13/20 eligibility, all typed planning failures, one-cell targeting and outward
  facing, planner non-mutation, exact state transfer, survival/creative consumption, every rollback branch, natural
  and dyed persistence, optional pigment, update tag/packet application, invalid NBT containment, missing-reference ID
  preservation, break/pick transfer, registrations, absence of a `BlockItem`, shape/support/drop ownership, assets,
  localization, and common/client/Milestone-11 scope isolation.
- No GameTest was added. The repository has no GameTest source root, bootstrap, templates, or registration. The tests
  instead exercise the actual block, block entity, shared item/component codecs, planner, transaction executor, and
  packaged resources. No claim of live-world visual or interaction testing is made.
- The first focused run exposed a test-fixture issue: constructing a vanilla block-entity update packet calls through
  the attached level for registry access. The packet-application test was corrected to construct the actual packet
  from the persisted update tag and apply it through `onDataPacket`; production packet creation remains unchanged.
- During the clean full-suite run, a short-lived earlier Gradle launcher retained
  `build/test-results/test/binary/output.bin`. `gradlew --stop` released the two stale daemons; the rerun passed without
  changing code or tests.

### Final commands and results

1. Normal scaffold generation passed: manifest=33, definitions=33, active=33, disabled=0, localization=33,
   provisional names=14, provisional dimensions=33, asset families=5.
2. Focused Milestone 10 tests passed: 33 tests, 0 failures, 0 errors, 0 skipped.
3. `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace`
   passed with 460 tests, 0 failures, 0 errors, and 0 skipped across 35 suites.
4. Final `.\gradlew.bat clean --no-daemon` passed in 11 seconds.
5. Final `.\gradlew.bat test --no-daemon --stacktrace` passed from the clean output: 460 tests, 0 failures,
   0 errors, 0 skipped across 35 suites.
6. Final `.\gradlew.bat build --no-daemon --stacktrace` passed in 16 seconds; `jar`, `jarJar`, `assemble`, `check`,
   and `build` completed. `Britannia_Mod-0.1.7k-all.jar` contains the new block/BE/placement classes, one banner
   blockstate, one banner block model, and localization resource.
7. `git diff --check` passed; only repository line-ending conversion notices were emitted.

The unchanged compiler warnings are missing `@Overwrite` Javadoc on `PlayerSleepMixin` and the deprecated-for-removal
`Item.initializeClient` override in `OrderShieldItem`. No new warning was introduced.

### Counts, manual boundary, limitations, and next milestone

- Production content remains 33 active/0 disabled banners, 4 materials, 4 palettes, 7 pigments, and 2 mounts. Exactly
  13 current definitions are 1x1 wall-placeable and 20 remain deferred. New registrations are one block, one block
  entity type, and zero separate block items; existing registrations remain one shared banner, one dye tub, seven
  pigment items, and three total data components including wine data.
- The live survival/creative, support-loss, protected-area, explosion, pick-block, save/reload, relog, and multiplayer
  matrix was not performed because the repository still has no safe configured-banner acquisition path. No recipe,
  creative entry, debug command, or Milestone 15 command was introduced solely for QA. Automated state, transaction,
  codec, resource, and ownership tests are not represented as in-game screenshots.
- Placed banners use diagnostic static geometry only. Custom item names are not part of `BannerInstanceState` and are
  therefore not transferred through placement. Multi-block layouts, placed layered rendering, direct-world dyeing,
  crafting, commands, and NPC integration remain outside this milestone.
- Next milestone: Milestone 11 only. It has not started.

## 2026-07-21 - Milestone 9: Layered Item Rendering

### Rendering API and side boundary

- Verified the exact NeoForge 21.1.72 sources before selecting an API. `Item.initializeClient` is deprecated for
  removal and directs extension users to `RegisterClientExtensionsEvent`; Milestone 9 adds neither that deprecated
  override nor a BEWLR. The banner instead uses current `ModelEvent.RegisterAdditional`,
  `ModelEvent.ModifyBakingResult`, stack-aware vanilla `ItemOverrides`, standard `applyTransform`, and NeoForge baked
  render passes.
- One `BannerItemBakedModel` wraps the one shared `britannia_mod:banner` inventory model. Its override resolves a
  stack-specific immutable appearance to the five family geometry models plus the selected mount pass. It delegates
  GUI, first/third-person, ground, and fixed transforms to the repository-standard item model.
- Renderer, model repository, cache, state extractor, tint registration, preview stack helper, and connection cleanup
  are isolated under `client/banner`. Common state projections and payloads contain no client or Blaze3D imports.
  `BritanniaMod` references only the common render-data sync listener.

### Client render data and authority

- Added an immutable display-only projection of active banner assets/status, material natural-colour and palette
  display sRGB entries, and mount assets. `S2CBannerRenderDataPayload` synchronizes this projection on login and
  `OnDatapackSyncEvent`; the client publishes it atomically with a monotonically increasing generation and clears it
  on disconnect.
- Server gameplay continues to use `RegistrySnapshot`; the client projection is never used for dye resolution,
  confirmation, validation, or mutation. Stable resolved-colour IDs remain authoritative. Missing/stale data, unknown
  server IDs, and absent local assets select the diagnostic fallback rather than another lexical entry or colour.
- Server data-pack overrides to display metadata are synchronized. Models/textures remain client resource-pack
  content, so a server override can select only an asset available in the client's packs; new server-only assets fall
  back diagnostically. This exact multiplayer boundary is recorded in `OPEN_QUESTIONS.md`.

### Render state, layers, cache, and fallback

- The Milestone 9 typed immutable state contained definition/material/colour/mount IDs, canonical display sRGB,
  geometry, the then-current three-image asset identities, mount geometry/texture, placeholder status, natural flag,
  typed fallback reason/stable diagnostic ID, and client-data generation. Milestone 16A later replaced those image
  identities and the natural flag with base, mask, and derived recolour-active state. Extraction reads but never
  validates, repairs, substitutes, or mutates `ItemStack` state.
- The render key contains every appearance field plus client-data and baked-resource generations. It intentionally
  excludes `ItemStack`, source pigment, custom name, player, level, screen, registry maps, and timestamps.
- Milestone 9 used the then-current three-image layer order and tint-index-1 mask. Milestone 16A later reduced it to
  untinted complete base, an optional active tint-index-1 mask, and untinted brass/iron mount while preserving the
  shared-model design.
- A synchronized access-ordered client cache stores at most 256 immutable-key-to-baked-model entries. Model bake or
  resource reload and client render-data replacement clear entries and missing-log identities. Diagnostics are
  de-duplicated by reason, stable ID, data generation, and resource generation, preventing per-frame log spam while
  allowing a later successful reload.
- At Milestone 9, any missing component, registry entry, asset, or mount resource returned the one missing-item model.
  Milestone 16A narrowed banner image failures to the complete base and selective mask. Original components and
  stable IDs remain untouched.

### Assets and preview integration

- Updated the scaffold templates before regenerating scaffold-owned files. Milestone 9 packaged five visibly
  distinct family JSON models and its then-current three-image diagnostic placeholder. Milestone 16A later migrated
  that package to one complete base, one selective mask, one missing texture/model, and the unchanged visibly
  different brass and iron mount models/textures. This is diagnostic presentation, not final heraldic art.
- Mount definitions now reference `banner/mount/brass` and `banner/mount/iron`. The scaffold metadata owns the new
  outputs and `--check` remains deterministic. Catalogue identities, names, dimensions, and all 33 definition IDs are
  unchanged.
- Extended only the S2C preview payload with current/proposed display-only stable-ID descriptors. The screen creates
  detached client stacks and calls the normal item renderer twice, so inventory and preview share the same layered
  architecture. Existing current/new swatches and localized details remain. The proposed stack never mutates the held
  item, and `C2SConfirmDyeApplicationPayload` remains exactly one session UUID.

### Automated coverage, initial failures, and corrections

- Added 25 focused rendering tests covering all 33 definitions, all five families, four materials, natural/dyed
  cotton, brass/iron, canonical colour projection, render-key equality/invalidation/exclusions, exact layer order and
  tint indices, alpha/cutout pixels, every typed fallback, non-mutation, asset packaging/ownership, bounded cache,
  reload invalidation, log de-duplication, packet round-trip, atomic publication, detached previews, shared screen
  renderer, confirmation authority, current model events/transforms, and client/common isolation.
- The first restricted scaffold and Gradle attempts could not download/use the wrapper distribution because sandbox
  socket access was denied. Approved retries used the configured Gradle 8.9/Java 21 toolchain.
- The first `compileTestJava` failed because the Milestone 8 payload test still called the former three-argument open
  payload constructor. It was corrected to provide current/proposed render descriptors; compilation then passed.
- The first 426-test banner/dye regression run had one failure: `BannerScaffoldToolTest` expected five placeholder
  models but the new missing diagnostic model makes six. The assertion was corrected and expanded to require the two
  mount models and two mount textures. The rerun and clean full suite passed without weakening tint, fallback, cache,
  authority, or isolation coverage.

### Final commands and results

1. Normal scaffold generation passed: manifest=33, definitions=33, active=33, disabled=0, localization=33,
   provisional names=14, provisional dimensions=33, asset families=5.
2. `.\tools\scaffold_banners.bat --check` passed in 25 seconds with the same counts.
3. Focused `BannerRender*` tests passed after final disabled-content coverage in 1 minute 38 seconds: 25 tests,
   0 failures, 0 errors, 0 skipped.
4. `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace`
   passed after the documented scaffold count correction: 426 tests, 0 failures, 0 errors, 0 skipped.
5. Final `.\gradlew.bat clean --no-daemon` passed in 19 seconds.
6. Final `.\gradlew.bat test --no-daemon --stacktrace` passed immediately after clean in 56 seconds: 427 tests,
   0 failures, 0 errors, 0 skipped across 30 suites.
7. Final `.\gradlew.bat build --no-daemon` passed in 36 seconds; `jar`, `jarJar`, `assemble`, `check`, and
   `build` completed. The production JAR contains 24 banner-client class entries, 8 banner models, 6 banner textures,
   and the common render-data payload.
8. `git diff --check` passed; only repository line-ending conversion notices were emitted.

The unchanged compiler warnings are missing `@Overwrite` Javadoc on `PlayerSleepMixin` and the deprecated-for-removal
`Item.initializeClient` override in `OrderShieldItem`. No new warning was introduced.

### Counts, manual boundary, limitations, and next milestone

- Production content remains 33 active/0 disabled banners, 4 materials, 4 palettes, 7 pigments, and 2 mounts.
  Registrations remain one shared banner, one dye tub, seven pigment items, and two banner/dye components (three total
  component registrations including pre-existing wine data). Cache capacity is 256 entries and the tested resource
  and client-data generation replacements retain zero obsolete entries after clear.
- The manual size/material/mount/context rendering matrix was not performed. There is still no safe configured-banner
  acquisition path, and no recipes, creative catalogue, or Milestone 15 command was added solely for QA. Automated
  model/pixel/JAR tests are not represented as live inventory, hand, dropped-item, frame, or preview screenshots.
- Final heraldic art, placed-banner blocks/entities/rendering, placement, collision, drops, crafting, recipes,
  commands, NPC integration, and direct-world dyeing remain absent.
- Next milestone: Milestone 10 only. It has not started.

## 2026-07-20 - Milestone 8: Dye Preview and Confirmed Item Dyeing

### Player flow and interaction routing

- Completed the first player-facing item dyeing path: a loaded registered dye tub in `MAIN_HAND` plus a configured
  shared banner in `OFF_HAND` is validated and resolved on the server, opens a client-only preview, and mutates only
  after a server-confirmed apply. Physical left/right hands are not hard-coded.
- `DyeTubItem` routes a recognized off-hand `PigmentItem` first through the unchanged Milestone 6 loading service;
  otherwise the shared banner item requests preview; empty or unknown items retain the existing typed invalid-off-hand
  loading result. Off-hand tub use returns pass before client or server mutation.
- Normal server-side item use is already the preview intent, so no redundant `C2S_RequestDyePreview` was added.
  Client prediction performs no component write, consumption, success sound, or particle emission.

### Network architecture and authority

Four typed play payloads were added under `network/payload/dye` and registered through the existing protocol-`1`
`PayloadRegistrar` path in `NetworkHandler`:

| ID | Direction | Fields |
|---|---|---|
| `britannia_mod:open_dye_preview` | S2C | session UUID, display-only preview projection, lifetime milliseconds |
| `britannia_mod:confirm_dye_application` | C2S | session UUID only |
| `britannia_mod:cancel_dye_preview` | C2S | session UUID only |
| `britannia_mod:dye_application_result` | S2C | session UUID, typed result enum, close-screen flag |

- The display projection contains localization keys, explicit/nearest match type, perceptual distance, current/new
  sRGB swatches, and placeholder/provisional flags. It is never returned to or trusted by the server.
- Confirm and cancel contain no pigment, material, banner definition, mount, resolved colour, match type, distance,
  banner state, or tub state. C2S handlers enqueue authoritative work and resolve the `ServerPlayer`; S2C handlers are
  selected only on the client distribution and open/update `DyePreviewScreen` from `ClientNetworkHandler`.
- Common packet records and all common preview/session/application classes contain no Minecraft client or Blaze3D
  imports. The client-only screen is annotated and isolated under `client/screen`.

### Preview session design

- `DyePreviewSessionService` is runtime-only, synchronized, and keyed by player UUID. Sessions use random UUIDs,
  expire after 30,000 milliseconds, and allow exactly one active session per player. Creating a new preview replaces
  the previous session.
- A session stores creation/expiry time, expected main/off item identities, defensive exact copies of both stacks,
  authoritative pigment, complete current banner/tub states, resolved colour/match/distance, display projection, and
  the immutable registry snapshot reference used for resolution.
- Exact stack matching includes item, count, and every component, so custom names, custom data, unrelated components,
  banner state, tub state, and hand swaps all stale the relevant confirmation.
- The registry system has no numeric generation counter. Snapshot publication replaces the immutable aggregate
  object, so reference identity is the exact runtime publication identity; any reload publication requires a fresh
  preview.
- A matching confirmation removes/marks the session consumed before validation or mutation. Short-lived terminal
  tombstones distinguish expired, cancelled, replaced, and replayed IDs without persisting data. Cleanup is lazy on
  create/claim/cancel/count; no tick handler or saved player data exists.
- Logout, dimension change, death, and server stop remove sessions. Successful and failed matched confirmations are
  one-use; cancellation removes the matching session; duplicate confirmation returns `SESSION_REPLAYED`.

### Preview validation and display

Validation is non-mutating and ordered across: exact main-hand tub; exact off-hand shared banner; configured banner
component; loaded/non-depleted tub; published registry availability; active/disabled pigment; complete banner
validation (definition, material, palette, stored colour, mount, supported mount); resolver success; and the generic
`DyeableItem` colour-update plan. Missing stored colour is not auto-repaired to open a preview.

Typed failures distinguish invalid hands, empty/depleted tub, unconfigured/invalid banner, unavailable registry,
missing/disabled pigment/material/definition/mount, missing palette, resolver failure, no compatible colour, generic
dyeable rejection, and session creation failure. Preview creation changes neither stack and never decrements uses.

`DyePreviewScreen` displays banner/material/mount, current colour and optional source pigment, tub pigment, resolved
new colour, explicit or closest-available label, static registered banner icon, current/new authoritative swatches,
placeholder warning, restrained provisional-dimension warning, Cancel, and Apply Dye. Apply disables immediately,
remains disabled in flight, and disables on the advisory local timeout. Escape, inventory key, and Cancel send only
the opaque cancellation intent. The screen has no menu, container, layered renderer, dye mask, overlay, geometry, or
final heraldry dependency; layered item rendering remains deferred to Milestone 9.

### Confirmation, atomicity, finite use, and feedback

Confirmation claims the player-bound session, checks expiry, re-reads exact hands, compares exact stack copies,
verifies registered item identities, checks the same registry publication, revalidates tub and generic banner state,
re-runs `DyeResolver`, and requires an exact match with the previewed authoritative `DyeResult`.

The service then detects exact no-op, plans the banner colour/source update and finite tub decrement completely, and
only then applies the banner component followed by the tub component. Expected failures change neither stack. An
unexpected runtime failure logs player/session/pigment/material context and restores the prior approved components on
a best-effort narrow rollback boundary.

- Unlimited tub: component remains exactly unchanged.
- Finite tub: one successful real application decrements exactly once.
- Finite one: becomes zero while retaining its pigment; later preview/apply is typed `TUB_DEPLETED`.
- Exact same colour and pigment: `ALREADY_DYED`, no mutation, use, sound, or particles; screen closes.
- Same colour with a different pigment: source provenance updates and one finite use is consumed.
- Banner schema, definition, material, mount, custom name, and unrelated components remain unchanged.
- Success emits one vanilla `DYE_USE` sound, six restrained `HAPPY_VILLAGER` particles, localized action-bar feedback,
  and a typed close result. Failures and cancellation emit no success effects.

### Automated coverage and corrections

- Added registered-`ItemStack` JUnit coverage for preview validation, missing/disabled registry content, no compatible
  colour, resolver failure, non-mutation, finite preview, exact stack staleness, session uniqueness/replacement,
  expiry/cancel/disconnect/replay/cross-player rejection, explicit and nearest application, finite/unlimited use,
  exact no-op, same-colour provenance, sequential re-dyes, resolver/registry changes, component preservation,
  rollback, all four packet codecs/IDs, C2S authority fields, view-model state, localization, routing, and client-class
  isolation.
- No GameTest was added. The repository still has no GameTest source root, annotated bootstrap, templates, or test
  registration. Registered production-shaped items/components plus isolated packet/client-server boundaries exercise
  this item-only milestone without introducing unrelated framework infrastructure. No live in-game claim is made.
- The first restricted `compileJava` attempt could not access the Gradle distribution because sandbox networking was
  denied. The approved retry passed with only the two existing compiler warnings.
- The first focused test command completed after the tool timeout; its XML proved 23 tests, 0 failures/errors/skips.
  The expanded focused command then passed normally: 27 tests, 0 failures/errors/skips.
- The first complete 402-test banner/dye run found two obsolete Milestone 6 scope-only guards: one included the now-
  required `DyeTubItem` banner routing, and one prohibited every future dye-named payload/screen. Both guards were
  narrowed to the exact unchanged Milestone 6 loading-core files; their no-client/no-banner/no-payload guarantees
  remain intact, and no stale-state, replay, authority, or atomicity assertion was weakened.
- Corrected required banner/dye command: 402 tests, 0 failures, 0 errors, 0 skipped.

### Final commands and results

1. `.\tools\scaffold_banners.bat --check` passed in 35 seconds: manifest=33, definitions=33, active=33,
   disabled=0, localization=33, provisional names=14, provisional dimensions=33, asset families=5.
2. `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` passed
   after the documented scope-guard correction: 402 tests, 0 failures, 0 errors, 0 skipped.
3. `.\gradlew.bat clean --no-daemon` passed in 39 seconds.
4. `.\gradlew.bat test --no-daemon --stacktrace` passed from clean state in 3 minutes 20 seconds: 402 tests,
   0 failures, 0 errors, 0 skipped.
5. `.\gradlew.bat build --no-daemon` passed in 53 seconds; check, jar, jarJar, assemble, and build completed.
6. `git diff --check` passed during source review; final staged diff checks are recorded in the handoff report.

The unchanged compiler warnings are missing `@Overwrite` Javadoc on `PlayerSleepMixin` and the deprecated-for-removal
`Item.initializeClient` override in `OrderShieldItem`.

### Counts, manual boundary, limitations, and next milestone

- Production content remains 33 active/0 disabled banners, 4 materials, 4 palettes, 7 pigments, and 2 mounts.
  Gameplay registrations remain one shared banner, one dye tub, seven pigment items, and two banner/dye typed data
  components (three total registrations in `DataComponentRegistry`, including pre-existing wine data).
- The full manual obtain/open/cancel/apply/stale/double-click/expiry/re-dye/relog checklist was not performed because
  there is still no safe banner acquisition path before Milestone 15 and no live client/server session was launched.
  Automated persistence and handler tests are not presented as in-game, multiplayer, relog, or world-save evidence.
- Preview is text, static icon, and swatches only. There is no layered item rendering, placed banner, block, block
  entity, placement, crafting, recipe, admin command, NPC integration, or direct-world dyeing.
- Session lifetime, finite-use zero policy, and exact no-op close UX remain provisional as recorded in
  `OPEN_QUESTIONS.md`.
- Next milestone: Milestone 9 - Layered Item Rendering - only. It has not started.

## 2026-07-20 - Milestone 7: Generic Dyeable Item API and Banner Item State

### Files and architecture

- Added exactly one shared `britannia_mod:banner` registration through `BannerItemRegistry`. It has provisional
  maximum stack size 1 and no prototype banner component, so a raw stack is explicitly unconfigured rather than an
  arbitrary `large_01`/cotton/brass banner. No per-definition, material, colour, or mount items were registered.
- Added exactly one `britannia_mod:banner_instance_state` component to `DataComponentRegistry`, attaching the existing
  immutable `BannerInstanceState.CODEC` for persistent `ItemStack` storage and
  `BannerInstanceState.STREAM_CODEC` for component network synchronization. No raw custom NBT or custom packet is an
  authoritative banner-state path.
- Added the common-side `DyeableItem` contract plus banner-neutral read, update-plan, and typed-failure values. It
  exposes material, resolved colour, optional source pigment, pigment applicability, pure colour-update planning, and
  explicit stale-safe application without assuming `BannerInstanceState` for future textiles.
- Added `BannerItemStateAccess`, typed validation status/issues, immutable colour-update and repair plans, and explicit
  apply methods. Reads, validation, tooltips, planning, and reload inspection never mutate stacks. Apply changes only
  the typed banner component, preserving custom names and every unrelated component.
- Validation distinguishes valid, valid-with-diagnostics, unconfigured, registry-unavailable, repairable, and invalid
  state. Issues retain stable IDs for missing/disabled definitions, materials, pigments and mounts; missing palettes,
  colours, unsupported mounts, component/decode failures where observable, invalid items, and stale plans.
- A missing source pigment is diagnostic while an existing resolved colour remains usable. Missing definition,
  material, palette, or mount never triggers substitution or erasure.
- Added `BannerItemFactory` typed results for natural cotton admin banners, crafted-material natural banners, and
  fully specified development banners. Cotton/material natural colours are read from the active material/palette;
  mounts and every supplied reference are validated without silent fallback.
- Added missing-colour repair planning. An active stored pigment is re-resolved with `DyeResolver`; otherwise the
  material natural colour is proposed. Definition, material, mount, schema version, and historical source-pigment ID
  are retained. Natural fallback deliberately retains unavailable pigment provenance and reports it diagnostically.
- Added safe localized tooltip projection for natural, dyed, placeholder, provisional-dimension, both-orientation,
  one-orientation, unconfigured, repairable, and missing-reference states. Configured names use the active banner
  definition; player custom names still win through normal `ItemStack` behavior.
- Added one static item model using the existing original high-contrast banner placeholder texture. No renderer or
  final heraldic artwork was added.
- Creative/development access uses the smallest safe boundary: the shared item is registered but no raw or generated
  banner stack is added to the creative tab because its callback cannot safely depend on the reload-published server
  data snapshot. Factory integration tests cover acquisition until Milestone 15.

### Factories, persistence, merge, and repair evidence

- Natural cotton admin factory: success; cotton natural colour, definition default mount, no source pigment.
- Crafted factories: cotton, wool, linen, and silk all succeed with their authored natural colours. Explicit brass
  and iron mounts succeed; unsupported/missing references return typed failures.
- Fully specified dyed silk/ruby/madder/brass factory: success and full validation success.
- All 33 active definitions create valid natural cotton stacks using the same registered item and each definition ID,
  material, natural colour, default mount, and absent source pigment are asserted.
- Actual registered `ItemStack` persistence covers raw, natural, all four crafted materials, fully specified dyed,
  custom name, unrelated custom data, and every one of the 33 catalogue definitions. The registered component stream
  codec round trip preserves all five identities and schema version.
- Merge compatibility independently distinguishes definition, material, resolved colour, present/different/absent
  source pigment, mount, and configured/unconfigured state after serialization. Identical states are component-
  compatible, but the selected maximum stack size 1 prevents inventory stacking.
- Missing colour with an active source pigment produces a re-resolved repair; missing/no source uses natural colour;
  unavailable historical source uses natural colour while retaining provenance. Plans do not mutate before apply,
  stale plans fail, and missing definition/material/mount are not repaired.

### Registry, catalogue, manual, and integration boundary

- Production counts remain 33 active/0 disabled banners, 4 active materials, 4 active palettes, 7 active pigments,
  2 active mounts, one dye tub, and seven pigment items. The Milestone 6 loading/resolver/component regressions pass.
- Registry-removal tests cover definition, material, palette, resolved colour, source pigment, and mount; disabled
  tests cover definition, material, source pigment, and mount; registry-unavailable state retains the component.
- No GameTest was added. The repository still has no GameTest source root, annotated bootstrap, or templates, and the
  existing registered-`ItemStack` JUnit integration boundary directly exercises the component/persistence/merge work
  in scope without introducing unrelated framework infrastructure.
- No in-game acquisition path exists before Milestone 15, so the manual obtain/tooltip/relog/merge/missing-content
  checklist was not performed. Automated persistence is not claimed as a live world-save or relog test.

### Commands and exact results

1. Git preflight matched exactly: branch `banners-dyetub`; merge base
   `62df1dc97c5113a86f9c0f258cb90538f31efe89`; divergence `0 7`; starting HEAD
   `56b67c02cbb668544300df3f07a4e9dd966c6be4`; and only preserved modified `ModConfig.java` plus the preserved
   untracked root specifications, `.claude/`, `logs/`, and `tmp/` were present.
2. The first restricted `compileJava` could not access the Gradle 8.9 distribution because sandbox networking was
   denied. The approved retry passed in 28 seconds with only the two existing compiler warnings.
3. The first focused `Banner*` run executed 113 tests and found two fixture-boundary failures: the missing-colour
   parameter used a natural stack whose colour existed, and the configured-name assertion supplied a fixture snapshot
   while `getName` reads the global runtime snapshot. The fixture now uses the dyed stack and `configuredName` exposes
   the same snapshot-backed projection used by `getName`; the corrected 113-test run passed.
4. The first complete banner/dye run executed 375 tests and found one obsolete Milestone 6 scope assertion that
   scanned the entire `dye` package and prohibited the now-required generic `DyeableItem` API. It now scans the exact
   Milestone 6 dye-tub implementation files, preserving the original no-banner/client/UI coupling guarantee. The
   corrected required narrow command passed in 32 seconds: 375 tests, 0 failures, 0 errors, 0 skipped.
5. `.\\tools\\scaffold_banners.bat --check` passed in
   11 seconds: manifest=33, definitions=33, active=33, disabled=0, localization=33, provisional names=14,
   provisional dimensions=33, asset families=5.
6. `.\\gradlew.bat clean --no-daemon` passed in 11 seconds.
7. `.\\gradlew.bat test --no-daemon --stacktrace` passed from clean state in 63 seconds: 375 tests, 0 failures,
   0 errors, 0 skipped.
8. `.\\gradlew.bat build --no-daemon` passed in 21 seconds; test/check, jar, jarJar, assemble, and build completed.
9. `git diff --check` passed during implementation review. Final staged checks and commit evidence are recorded in the
   Milestone 7 handoff report.

The unchanged compiler warnings are missing `@Overwrite` Javadoc on `PlayerSleepMixin` and the deprecated-for-removal
`Item.initializeClient` override in `OrderShieldItem`.

### Known limitations and next milestone

- No dye-preview/confirmation screen, menu, packet, held-item banner dyeing, tub use, block, block entity, placement,
  renderer, recipe, crafting integration, command, NPC shop, or direct-world dyeing was added.
- Item art is diagnostic placeholder art; no final heraldry exists. Stack size 1 remains provisional.
- No manual in-game, save/reload, relog, multiplayer, or GameTest verification was performed for the documented
  infrastructure/acquisition reasons.
- Next milestone: Milestone 8 - Dye Preview and Confirmed Item Dyeing - only. It has not started.

## 2026-07-20 - Milestone 6: Dye Items and Stateful Dye Tub

### Files and architecture

- Added one focused `DyeItemRegistry` with `britannia_mod:dye_tub` and seven pigment items whose registry paths match
  the seven existing pigment-definition IDs exactly: `madder_red`, `woad_blue`, `verdigris`, `weld_gold`,
  `soot_black`, `chalk_white`, and `ice_blue`.
- Added one reusable immutable-identity `PigmentItem` and one reusable `DyeTubItem`. The tub has a default maximum
  stack size of one; pigment items retain the normal maximum of 64.
- Added `britannia_mod:dye_tub_state` to the existing `DataComponentRegistry`. Its type attaches the established
  persistent `DyeTubState.CODEC` and network `DyeTubState.STREAM_CODEC`. Every newly registered tub has an explicit
  `DyeTubState.empty()` prototype component.
- Added `DyeTubStateAccess`. Reads normalize a missing legacy component to `DyeTubState.empty()` without mutating the
  stack; writes always install one explicit typed component. Empty, finite, and unlimited states retain their existing
  versioned contract, and unlimited remains represented only by absent `remaining_uses`.
- Added typed loading results and a plan/apply service. Planning validates the exact tub, exact off-hand stack,
  server-owned item mapping, snapshot availability, active/disabled pigment membership, current tub state, same-
  pigment no-op, and complete replacement state before mutation. Apply rechecks the planned stack identities/state,
  writes only the tub component, then shrinks the off-hand stack by one only in survival.
- The authoritative mapping is a fixed item-registry-ID to `PigmentId` map plus an immutable `PigmentItem` identity
  check. Client-editable components and custom data never supply the pigment ID.
- Extended snapshot publication with an explicit first-publication flag so an unloaded empty snapshot is distinct
  from a published snapshot whose requested definition is missing.
- `DyeTubItem.use` accepts only `InteractionHand.MAIN_HAND`, reads `player.getOffhandItem()`, returns pass from the off
  hand, performs no client-side mutation, and uses the normal item-use round trip without a custom payload.
- Successful server mutation plays vanilla `SoundEvents.BOTTLE_FILL` once and sends eight restrained vanilla
  `ParticleTypes.SPLASH` particles. No-op and failure results emit no success effects. Action-bar feedback is sent
  once from the server after planning/application.
- Tooltips use translations for empty/hint, contains, uses, and unlimited text. Loaded pigment names come from the
  current active snapshot. Missing or removed definitions preserve the stored stable ID and display an unavailable-
  pigment diagnostic rather than mutating or erasing state.
- Added the tub and seven pigments to the existing Britannia items creative tab without reordering unrelated items.
- Added eight minimal generated item models and two original shared 32 x 32 placeholder textures. The final project
  assets were generated with the built-in image tool as crisp diagnostic pixel-art sprites, keyed to transparency,
  and downscaled with nearest-neighbour sampling. They are placeholders, not final item art.
- Added focused component, mapping, loading, atomicity, tooltip, feedback, scope, resource, registry-availability,
  real `ItemStack` persistence, and registered network-component tests.

### State, loading, and provisional gameplay decisions

1. Canonical new-tub state is the explicit typed `DyeTubState.empty()` component. An absent legacy component reads as
   the same value without a read-side write.
2. A successful load stores schema version 1, the server-derived stable pigment ID, and absent `remaining_uses`.
3. Survival consumes exactly one off-hand pigment after the component write; creative inventory permissions consume
   zero. Loading a different pigment replaces the old value.
4. Loading the same pigment returns `ALREADY_CONTAINS`, changes neither stack, consumes nothing, and emits no success
   effects.
5. Empty/unsupported off hands, unavailable registry data, missing or disabled definitions, an invalid tub, or stale
   plan/state modify neither stack. The result model distinguishes every case without using exceptions for expected
   interactions.
6. These consumption, replacement, capacity, and no-op rules are reversible provisional defaults and are not final
   product approval.

### Persistence and integration boundary

- Plain JUnit registers a narrow test component and the eight production-shaped items into the real Minecraft built-
  in registries after the required version/bootstrap initialization. Tests then use the actual `ItemStack` persistent
  codec boundary for explicit empty, loaded unlimited, stable pigment ID, and finite fixture round trips.
- The registered component type's attached network stream codec is exercised from a component read on a registered
  tub and written back to another registered tub. The plain-JUnit registry view intentionally does not claim full
  connection-level item-registry ID synchronization, which NeoForge only configures during a real modded connection.
- Different loaded components remain unequal under `ItemStack.isSameItemSameComponents`; the tub maximum stack size
  is one, so one state cannot represent or load multiple tubs. Pigment items remain stackable to 64.
- No GameTest was added. `runGameTestServer` exists, but the repository still has no GameTest source root, annotated
  tests, templates, or test registration bootstrap. Building that unrelated framework would exceed this narrow item
  milestone. The registered `ItemStack` codec and loading-service boundary is the closest practical integration test.
- No in-game client, relog, or save/reload check was performed. Automated `ItemStack` serialization is evidence for
  component persistence, not a claim that a world relog was manually verified.

### Commands and exact results

1. Git preflight matched exactly: branch `banners-dyetub`; merge base
   `62df1dc97c5113a86f9c0f258cb90538f31efe89`; divergence `0 6`; starting HEAD
   `c897bf440aeefdc8f17c21621a38146987e5cc9f`; and only the preserved modified `ModConfig.java` plus the preserved
   untracked root specifications, `.claude/`, `logs/`, and `tmp/` were present.
2. The first restricted `compileJava` could not access the Gradle 8.9 distribution because sandbox networking was
   denied. The approved retry reached compilation and found one new compile error: `Component.withStyle` is not on
   the immutable interface. The tooltip now calls `copy().withStyle(...)`; the corrected compile passed in 26 seconds.
3. Focused-test harness corrections, with no production assertion weakened:
   - the first 35-test attempt had 22 initialization failures because real built-in registry access requires Minecraft
     bootstrap;
   - the first bootstrap patch placed two `@BeforeAll` methods after their class braces and caused four test-source
     compile errors; the methods were moved inside their test classes;
   - the next 39-test run had 24 initialization failures because Minecraft version detection must precede bootstrap;
     `SharedConstants.tryDetectVersion()` was added before `Bootstrap.bootStrap()` and NeoForge registry unfreezing;
   - the next 43-test run had one network assertion failure because plain JUnit does not mark the built-in item
     registry as connection-synchronized. The test was corrected to the requirement's registered component stream-
     codec boundary; persistent tests continue to use the actual `ItemStack` codec.
4. Final focused command selecting the Milestone 6 component, mapping, loading, tooltip, resource/scope, persistence,
   and availability tests passed in 24 seconds: 43 tests, 0 failures, 0 errors, 0 skipped.
5. `.\tools\scaffold_banners.bat --check` passed in 53 seconds: 33 manifest entries, 33 definitions, 33 active,
   0 disabled, 33 generated banner localization entries, 14 provisional names, 33 provisional dimensions, and 5
   placeholder asset families.
6. `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` passed in
   37 seconds. XML results: 297 tests, 0 failures, 0 errors, 0 skipped.
7. `.\gradlew.bat clean --no-daemon` passed in 23 seconds.
8. `.\gradlew.bat test --no-daemon --stacktrace` passed in 72 seconds. XML results: 297 tests, 0 failures, 0 errors,
   0 skipped.
9. `.\gradlew.bat build --no-daemon` passed in 22 seconds; `test`, `check`, `jar`, `jarJar`, `assemble`, and `build`
   completed.
10. Final review tightened registry publication so the availability flag and immutable snapshot remain one atomic
    publication value. The focused 43-test selection then passed again in 37 seconds, the full 297-test suite passed
    again in 31 seconds, and `build` passed again in 16 seconds.
11. Final diff, staging, and commit checks are recorded in the Milestone 6 handoff report.

The existing compiler warnings remain unchanged: missing `@Overwrite` Javadoc on `PlayerSleepMixin` and the
deprecated-for-removal `Item.initializeClient` override in `OrderShieldItem`.

### Catalogue, registry, manual, and scope results

- Registered content: seven pigment items, one dye tub, one new typed component, and seven deterministic mappings.
- Production data remains 33 active/0 disabled banners, 4 active materials, 4 active palettes, and 7 active pigments.
- Survival service evidence consumes one item; creative evidence consumes zero; replacement changes the pigment;
  same-pigment evidence changes nothing and consumes zero.
- The complete manual checklist remains unperformed: obtain items; verify empty tooltip; load main-hand tub from off-
  hand pigment; verify survival consumption and loaded tooltip; relog/save-reload; replace pigment; repeat same
  pigment; verify creative no-consumption; and try a non-pigment. No in-game claim is made.
- No banner item, banner component, dyeable-item API, banner state adapter, preview screen, menu, payload, colour
  application, block, block entity, placement, renderer, crafting, recipe, command, NPC shop, or direct-world dyeing
  was added. Milestone 7 has not started.

### Known limitations and deviations

- Consumption rules remain provisional; tub capacity is unlimited for now; washing/emptying and finite-use gameplay
  are absent.
- Final item art and final pigment availability/acquisition are unresolved; current access is development creative-tab
  access only.
- There is no banner item, banner dyeing path, or preview screen.
- Live GameTest, in-game multiplayer, relog, and world-save checks were not performed for the documented repository-
  infrastructure reason above.

### Next milestone

Milestone 7 - Generic Dyeable Item API and Banner Item State - only. It has not started.

## 2026-07-20 - Milestone 5: Materials, Palettes, and Colour Mathematics

### Files and architecture

- Added an isolated common-side colour layer under `dye/colour`: strict canonical sRGB parsing, eight-bit channel
  normalization, the standard inverse sRGB transfer function, linear-sRGB-to-OKLab conversion, Euclidean OKLab
  distance, authored-reference comparison, and explicit comparison/tie tolerances.
- Added `DyeResolver` and immutable resolution outcome/explanation records under `dye/service`. Expected content
  failures are returned as typed values rather than generic unchecked exceptions. Lightweight and explanatory entry
  points share one selection algorithm and return the same `DyeResult`.
- Extended the existing Milestone 3 cross-reference pipeline to reject authored pigment or palette OKLab values that
  disagree excessively with their canonical sRGB. Added `ProductionDyeContent` as a release/development-content
  boundary for the four required materials without hard-coding that requirement into the generic loader.
- Authored four material definitions, four compact material palettes, and seven pigment definitions under the existing
  data-resource folders. No parallel colour model, item registration, gameplay object, component, packet, screen,
  renderer, recipe, command, block, or block entity was introduced.
- Transferred the Milestone 4 cotton placeholder material/palette out of scaffold ownership. The scaffold now uses a
  private in-memory cotton fixture only to cross-validate its banner outputs; it emits no colour resources. Normal
  regeneration refreshed the sidecar metadata, and `--check` remains clean.
- Added material, resolved-colour, and pigment localization while retaining all 33 banner localization keys exactly.
- Added independent reference, resolver, compatibility, ordering, immutability, registry-integration, production-data,
  and common-side safety tests.

### Colour mathematics and numeric policy

- Canonical input remains uppercase six-digit `#RRGGBB`. Parsing produces three integer channels in `[0, 255]`, then
  normalizes each channel to `[0, 1]`; malformed, lowercase, short, prefixless, non-finite, and out-of-range values are
  rejected rather than clamped.
- The inverse sRGB transfer function is `c / 12.92` at `c <= 0.04045`, otherwise
  `((c + 0.055) / 1.055)^2.4`. Source: W3C CSS Color 4's sRGB conversion algorithm, which reproduces IEC
  61966-2-1: https://www.w3.org/TR/css-color-4/#color-conversion-code .
- Linear sRGB is converted directly to OKLab with Bjorn Ottosson's updated 2021-01-25 matrices and signed cube-root
  stage: https://bottosson.github.io/posts/oklab/ . The implementation uses the published ten-decimal constants.
- All calculations use Java `double`. `COMPARISON_EPSILON` is `1e-12`; values at or below it are treated as numerical
  zero. `TIE_EPSILON` is `1e-9`. Independent reference-vector assertions use a `5e-9` tolerance.
- Authored OKLab triples may contain any finite doubles because the established codec permits them; they are not
  clamped. Registry validation compares them with their computed canonical-sRGB values and rejects a definition when
  Euclidean disagreement exceeds `5e-7`. NaN and both infinities remain structural errors.
- The computed OKLab value from canonical sRGB is authoritative for matching. Authored `reference_oklab` and
  `match_oklab` remain persisted audit values and must agree within the validation tolerance; disagreement cannot be
  silent. No conversion cache was added because the current immutable dataset is small and keeping the utility pure
  avoids shared mutable state; a future immutable snapshot-local cache may be added without changing results.

### Resolver semantics

1. Look up the pigment, material, and material palette in one immutable `RegistrySnapshot`.
2. Verify palette ownership and non-empty content defensively.
3. If a pigment override exists, verify its target and return it immediately as `EXPLICIT_MAPPING`; compatibility and
   mathematical proximity cannot displace it.
4. Otherwise filter entries. Any shared excluded pigment tag rejects the entry first. Empty allowed tags impose no
   positive restriction; non-empty allowed tags require at least one shared pigment tag.
5. Compute Euclidean distance between pigment and entry OKLab values derived from canonical sRGB.
6. Select lowest distance; values within `1e-9` enter tie-breaking. Then prefer a shared colour-family tag, higher
   priority, and finally the lexicographically smaller resolved-colour ID.
7. Only tags with the `colour_family_` prefix count as colour families. Generic tags such as `common`, `development`,
   `fabric`, and rarity tags never affect that tie stage.
8. Candidate input is normalized to stable-ID order, and explanation candidates/rejections are emitted in a
   deterministic order. Registry, JSON, map, set, and resource load order cannot affect the result.
9. No compatible entry returns `NO_COMPATIBLE_COLOUR` with ordered rejection reasons. Missing IDs, ownership errors,
   missing natural colours, and malformed explicit mappings use other typed failures. The resolver never substitutes
   an incompatible or natural colour silently.
10. Dedicated natural lookup returns the material's authored natural colour with `MatchType.NATURAL`; it does not
    represent natural state as a pigment.

### Development content counts

- Materials: 4 - `cotton`, `wool`, `linen`, and `silk`.
- Palettes: 4. Cotton, wool, and linen each contain 8 entries; silk contains 9; total entries: 33.
- Pigments: 7 - `madder_red`, `woad_blue`, `verdigris`, `weld_gold`, `soot_black`, `chalk_white`, and `ice_blue`.
- Explicit overrides: 4, one `madder_red` mapping in each material palette.
- Compatibility-restricted entries: 1, `silk_glacial`, allowed for `ice` and excluded for `mundane` pigments.
- `madder_red` representative results:
  - cotton -> `cotton_red`, explicit mapping, distance `0.060252859817`;
  - wool -> `wool_oxblood`, explicit mapping, distance `0.049274171079`;
  - linen -> `linen_madder`, explicit mapping, distance `0.113283634696`;
  - silk -> `silk_ruby`, explicit mapping, distance `0.074164136656`.
- All values above are development data for architecture proof and are not final art-direction-approved colours.

### Commands and exact results

1. Required Git preflight:
   - `git branch --show-current` - `banners-dyetub`.
   - `git status --short --branch` - only the preserved modified `ModConfig.java` and preserved untracked root
     specifications, `.claude/`, `logs/`, and `tmp/` were present.
   - `git merge-base banners-dyetub patch-18` - `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
   - `git rev-list --left-right --count patch-18...banners-dyetub` - `0 5`.
   - starting `HEAD` - `f75635f03af636c0699c3458c26538fc415f4dbe`.
2. The first restricted `compileJava compileScaffoldJava` attempt could not access the Gradle 8.9 distribution because
   sandbox network access was denied. The approved retry passed in 56 seconds. It confirmed the two existing compiler
   warnings: missing `@Overwrite` Javadoc on `PlayerSleepMixin`, and the deprecated-for-removal
   `Item.initializeClient` override in `OrderShieldItem`.
3. The first complete narrow Milestone 5 run passed in 31 seconds. After final defensive/order tests and scaffold
   wording regeneration, the final required narrow command
   `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` passed in
   23 seconds. XML results: 254 tests, 0 failures, 0 errors, 0 skipped.
4. `.\tools\scaffold_banners.bat` regenerated only scaffold-owned status/metadata successfully: 33 definitions,
   33 active, 0 disabled, 33 localization entries, 14 provisional names, and 33 provisional dimensions.
5. Final `.\tools\scaffold_banners.bat --check` passed in 16 seconds with the same counts.
6. `.\gradlew.bat clean --no-daemon` passed in 11 seconds.
7. `.\gradlew.bat test --no-daemon --stacktrace` passed in 56 seconds. XML results: 254 tests, 0 failures,
   0 errors, 0 skipped.
8. `.\gradlew.bat build --no-daemon` passed in 21 seconds; `test`, `check`, `jar`, `jarJar`, `assemble`, and `build`
   completed.
9. `git diff --check` passed before documentation/staging review. Final staged checks are recorded in the handoff.

No JUnit test initially failed and no production validation or test assertion was weakened. The only initial failure
was the expected restricted-sandbox Gradle distribution access error; the approved retry used the configured
toolchain successfully.

### Catalogue, validation, and scope results

- Catalogue entries: 33 active, 0 disabled. Stable IDs, source references, display labels, provisional dimensions,
  definitions, and the manifest count are unchanged.
- Gate B decisions are reflected in the generated status report and `OPEN_QUESTIONS.md`: all 33 stable IDs remain,
  all 14 unnamed entries remain visibly provisional, and `Tournament Medium` / `Pennon of Silver` remain canonical
  scaffold labels.
- Registry validation reports 4 active materials, 4 active palettes, 7 active pigments, 33 active banners, and no
  validation errors or warnings for the authored development dataset.
- Generic intentionally small registry fixtures still publish successfully; the four-material rule exists only in
  `ProductionDyeContent` and is invoked at the production-catalogue boundary.
- Common-side class scans found no Minecraft client, Blaze3D, gameplay registration, component, payload, screen,
  block-entity, recipe, or command references in the colour/resolver implementation.
- No manual visual or in-game check was performed because this milestone contains no item, tub, UI, rendering, or
  gameplay path. Correctness is established by reference vectors and deterministic automated tests, not screenshots.

### Known limitations and deviations

- Development palette colours are not final art-approved palettes, and the pigment catalogue is not final.
- Final special-dye restrictions and rare-pigment semantics remain open.
- The approximation-rejection threshold remains deferred; nearest matching currently selects the closest compatible
  entry regardless of absolute distance.
- Whether authored OKLab remains persisted long term is open. Milestone 5 retains it with strict consistency
  validation for backward compatibility.
- There are no dye items, dye tubs, held-item interactions, components, item state adapters, consumption rules,
  tooltips, particles, sounds, UI, networking, rendering, blocks, block entities, placement, crafting, recipes,
  commands, NPC shops, or direct-world dyeing.

### Next milestone

Milestone 6 - Dye Items and Stateful Dye Tub - only. It has not started.

## 2026-07-20 - Milestone 4: Scaffold All 33 Banner Placeholders

### Files and generated content

- Added the canonical editable manifest at `content/banner_catalogue.yml`. It is YAML 1.2 expressed in its
  JSON-compatible syntax so the existing Gson/Java/Gradle dependency set can validate it without a new runtime or
  parser dependency.
- Added the Java scaffold implementation under `tools/scaffold`, the Gradle `scaffoldBanners` task, and the Windows
  entry point `tools/scaffold_banners.bat`.
- Generated exactly 33 definitions under
  `src/main/resources/data/britannia_mod/banner_definitions`, in canonical manifest order.
- Generated minimal supporting definitions: one placeholder cotton material, one single-natural-colour cotton
  palette, brass and iron mounts, and five size-family placement profiles. No pigment is required for the natural
  scaffold state.
- Milestone 3 generated four 16 x 16 diagnostic PNGs and five shared vanilla-model JSON placeholders under
  `assets/britannia_mod/.../banner/placeholder`. Milestone 16A later replaced the former three banner images with a
  complete diagnostic base and selective mask while retaining the conventional high-contrast missing texture; none
  contains final heraldry.
- Structurally merged exactly 33 banner translation keys into the existing `en_us.json` while retaining unrelated
  keys and the file's existing layout.
- Generated `content/banner_catalogue_status.md` and the sidecar
  `content/.banner_scaffold_metadata.json` used for non-overwrite detection.
- Added catalogue-specific runtime validation in `ProductionBannerCatalogue` and the real reload listener without
  changing the generic Milestone 3 loader or its small-dataset behavior.
- Added 48 Milestone 4 tests across manifest, generator, production content, catalogue-boundary, asset, localization,
  scope, and safety cases.
- Updated `OPEN_QUESTIONS.md` with Gate B label discrepancies and the remaining universal runtime asset-mapping
  decision.

### Scaffold architecture and invocation

- Generation command: `.\tools\scaffold_banners.bat`.
- Non-mutating verification command: `.\tools\scaffold_banners.bat --check`.
- Destructive opt-in command: `.\tools\scaffold_banners.bat --force`.
- The tool validates the complete manifest before calculating or writing any output. It requires exactly 33 entries,
  unique stable IDs, unique continuous indices 1 through 33, the canonical ID/order/table, canonical group counts,
  positive source references, visible provisional status, placeholder content status, provisional dimensions, safe
  output IDs, supported groups, and the scaffold defaults.
- Normal generation compares each declared output with the last generated SHA-256 recorded in the sidecar. An
  unmodified generated output can be refreshed; a customized output is reported and preserved. Unknown and unrelated
  files are never deleted or rewritten.
- Localization is handled per generated key. Existing unrelated keys remain untouched, customized generated values
  are preserved normally, and `--force` warns before replacing them.
- `--check` writes nothing and returns non-zero for missing, changed, duplicate, unsafe, undecodable, inactive, or
  otherwise invalid output.
- `--force` prints an explicit list of customized declared files and localization keys before overwriting them. It
  still does not touch unrelated files.
- File replacement uses a same-directory temporary file followed by `ATOMIC_MOVE` where the filesystem supports it,
  with a same-filesystem replace fallback.

### Catalogue and registry results

- Manifest entries: 33.
- Unique IDs: 33.
- Unique indices: 33, continuous from 1 through 33.
- Group counts: large 6, medium-wall 6, medium 8, small 6, x-small 7.
- Name status counts: provisional 14, source-named 19.
- Content status counts: placeholder 33, in-progress 0, complete 0.
- Provisional dimensions: 33.
- Generated banner definitions: 33.
- Generated localization entries: 33.
- Active banner definitions after Milestone 3 development validation: 33.
- Disabled banner definitions: 0.
- Supporting active definitions: one material, one palette, two mounts, five profiles, zero pigments.
- Placeholder asset families: 5; common diagnostic textures: 4.
- All emitted logical placeholder geometry and texture IDs map deterministically to a file in the scaffold's declared
  output set.

### Commands and exact results

1. Required Git preflight:
   - `git branch --show-current` - `banners-dyetub`.
   - `git status --short --branch` - only the preserved modified `ModConfig.java` and preserved untracked root
     specifications, `.claude/`, `logs/`, and `tmp/` were present.
   - `git merge-base banners-dyetub patch-18` - `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
   - `git rev-list --left-right --count patch-18...banners-dyetub` - `0 4`.
   - `git rev-parse HEAD` - `343e65f436c9cb07ce9c96aec39c752dc318c1d0`.
2. Initial restricted `compileScaffoldJava` and generation attempts could not access the Gradle 8.9 distribution
   because sandbox network access was denied. Approved retries used the existing configured Gradle toolchain.
3. `.\gradlew.bat compileScaffoldJava --no-daemon --stacktrace` - passed in 33 seconds. It confirmed the two existing
   compiler warnings: missing `@Overwrite` Javadoc on `PlayerSleepMixin`, and the deprecated-for-removal
   `Item.initializeClient` override in `OrderShieldItem`.
4. `.\tools\scaffold_banners.bat` - passed in 22 seconds and reported 33 manifest entries, 33 generated definitions,
   33 active, 0 disabled, 33 localization entries, 14 provisional names, 33 provisional dimensions, and 5 asset
   families.
5. `.\tools\scaffold_banners.bat --check` - passed after generation; the final post-fix check passed in 11 seconds
   with the same counts.
6. The first narrow 48-test Milestone 4 run compiled successfully and found one failure in the localization force-path
   test. The replacement helper called `Matcher.start()` after a second `find()` invalidated the prior match. The
   offsets are now captured before the duplicate-match check; no validation or safety assertion was weakened.
7. The corrected narrow command selecting `BannerCatalogueManifestTest`, `BannerScaffoldToolTest`,
   `GeneratedBannerCatalogueTest`, and `ProductionBannerCatalogueTest` passed: 48 tests, 0 failures, 0 errors,
   0 skipped.
8. `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` - passed in
   1 minute 6 seconds. XML results: 207 tests, 0 failures, 0 errors, 0 skipped.
9. `.\gradlew.bat clean --no-daemon` - passed in 21 seconds.
10. `.\gradlew.bat test --no-daemon --stacktrace` - passed in 53 seconds. XML results: 207 tests, 0 failures,
    0 errors, 0 skipped.
11. `.\gradlew.bat build --no-daemon` - passed in 29 seconds; `jar`, `jarJar`, `assemble`, and `build` completed.
12. Packaged-JAR entry inspection found exactly 33 banner-definition JSON files, 5 placeholder model JSON files,
    4 placeholder PNG files, and the merged `en_us.json` in `Britannia_Mod-0.1.7k-all.jar`.
13. `git diff --check` passed during implementation review; staged checks are required immediately before commit.

### Validation and scope decisions

- The tool feeds all generated JSON through the Milestone 2 codecs and the complete generated data set through the
  Milestone 3 development policy. It does not introduce a second runtime format or bypass the loader.
- The release assertion is held in `ProductionBannerCatalogue`, not `DefinitionRegistry` or `RegistryDataLoader`.
  It activates when canonical production IDs are present and requires the exact ID set, 33 active entries, and zero
  disabled entries. Empty and intentionally small generic fixtures remain outside the boundary.
- The reload listener validates a candidate snapshot before atomically publishing it globally, so an incomplete real
  catalogue does not replace the last valid snapshot.
- The placeholder palette's single natural colour and illustrative structural OKLab triple exist only because the
  Milestone 2 codec requires them. No conversion, distance calculation, dye resolution, authored production palette,
  or Milestone 5 behavior was added.
- Placeholder model JSON and PNG existence is proven by the scaffold's deterministic mapping. No claim is made that
  the later renderer uses these files, because rendering is outside this milestone.

### Known limitations and Gate B

- Final names are not approved. Fourteen entries remain visibly `Name Required`; source-named labels are preserved
  but are not represented as final owner approval.
- `Tournament Medium` versus `Tournament`, and `Pennon of Silver` versus `Silver Pennon`, require Gate B review.
- All 33 dimensions are provisional. Final per-banner orientation and mount support are also unapproved scaffold
  defaults.
- Final original heraldic art, renderer integration, gameplay registrations, placement mechanics, items, blocks,
  components, packets, screens, recipes, crafting, and commands are not implemented or manually verified.
- The universal future logical-ID-to-vanilla/GeckoLib/custom-loader mapping remains open; only the Milestone 4
  placeholder output set has an authoritative mapper.
- No in-game or runtime rendering test was performed because there is no banner item, block, or renderer in scope.

### Next milestone

Stop for Gate B review of the complete 33-entry catalogue, stable IDs, provisional names/dimensions, and manifest
workflow. Milestone 5 has not started and must not begin before Gate B approval.

## 2026-07-20 - Milestone 3: Data Registries and Validation Pipeline

### Files changed

- Added immutable registry primitives and the aggregate `RegistrySnapshot` under `bannerdyeing/registry`.
- Added resource discovery, structural decoding, cross-reference validation, fixed-point production disabling,
  atomic publication, read-only global access, and the common/server reload listener.
- Added structured validation severity, stage, issue, summary, report, and policy types under
  `bannerdyeing/validation`.
- Registered the reload listener explicitly from `BritanniaMod` on the NeoForge game event bus.
- Added `RegistryDatasetFixtures` and `RegistryDataLoaderTest` under test sources. The fixtures are generated in
  test code from the Milestone 2 codecs and never enter packaged resources.
- Updated `OPEN_QUESTIONS.md` with the unresolved physical-asset mapping boundary.

### Commands and results

1. Required Git preflight:
   - `git branch --show-current` - `banners-dyetub`.
   - `git status --short --branch` - only the preserved modified `ModConfig.java` and preserved untracked root
     specifications, `.claude/`, `logs/`, and `tmp/` were present.
   - `git merge-base banners-dyetub patch-18` - `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
   - `git rev-list --left-right --count patch-18...banners-dyetub` - `0 3`.
   - Milestone 2 full hash - `31e46ddba426136e904a7d59cfddb32322ca3a8b`.
2. Initial restricted `.\gradlew.bat compileJava --no-daemon --stacktrace` - Gradle distribution access was denied
   by the sandbox. The approved retry passed in 1 minute 2 seconds and confirmed the two existing warnings: missing
   `@Overwrite` Javadoc on `PlayerSleepMixin`, and the deprecated-for-removal `Item.initializeClient` override in
   `OrderShieldItem`.
3. First `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.RegistryDataLoaderTest"
   --no-daemon --stacktrace` - 25 tests ran; one report-order test assertion failed because it compared the report's
   explicit enum/domain ordering to unrelated ordinary string ordering. The redundant string-sort assertion was
   removed; validation ordering and production code were not weakened.
4. Corrected narrow registry test command - 25 tests passed, 0 failures, 0 errors, 0 skipped.
5. Completed narrow registry test command after the remaining structural-attribution cases were added - 29 tests
   passed, 0 failures, 0 errors, 0 skipped.
6. Final `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` -
   passed in 32 seconds. XML results: 159 tests, 0 failures, 0 errors, 0 skipped.
7. Final `.\gradlew.bat clean --no-daemon` - passed in 12 seconds.
8. Final `.\gradlew.bat test --no-daemon --stacktrace` - passed in 41 seconds. XML results: 159 tests,
   0 failures, 0 errors, 0 skipped.
9. Final `.\gradlew.bat build --no-daemon` - passed in 22 seconds; `jar`, `jarJar`, `assemble`, and `build`
   completed.
10. Common-source scan - no `net.minecraft.client`, Blaze3D, gameplay objects, item stacks, block entities, screens,
    packets, or `DeferredRegister` usage in the registry/validation implementation.
11. `git diff --check`, staged-diff checks, and final scope checks are recorded in the milestone handoff report.

### Registry and resource-loading decisions

- The six registries are typed views inside one immutable aggregate snapshot. Active lookup maps, deterministic
  definition lists, source-bearing entries, and disabled diagnostic lists are all defensively copied and read-only.
- `AtomicReference<RegistrySnapshot>` is the sole publication mechanism. Candidate work occurs off to the side and
  one reference write exposes the complete new snapshot to concurrent readers.
- `AddReloadListenerEvent` is the repository-compatible NeoForge 21.1 server-data hook. Effective resources are read
  and structurally decoded during `SimplePreparableReloadListener.prepare`; policy validation and publication occur
  during `apply`.
- Folders are exactly `banner_definitions`, `fabric_materials`, `pigments`, `material_palettes`, `banner_mounts`, and
  `placement_profiles` below each data namespace.
- `ResourceManager.listResources` supplies only the effective resource at a path, so ordinary higher-priority pack
  replacement is not a duplicate. Duplicate checks operate on embedded IDs across distinct effective resources.
- Embedded stable IDs are authoritative. Filenames and namespaces are retained as exact diagnostics but are not
  required to match an embedded ID. This permits pack organization without inventing a filename identity contract.

### Validation and policy decisions

- Stage 1 parses every effective JSON resource and reuses the Milestone 2 codecs for required fields, schema,
  dimensions, colours, OKLab values, identifiers, and local collection constraints. It never publishes partial data.
- Stage 2 validates palette owners, material/palette and natural-colour agreement, override pigments, default
  materials, default/supported mounts, placement-profile existence, and dimension containment. Profile width and
  height must be at least the banner's declared width and height; rotations and occupied cells remain deferred.
- Development/fail-fast rejects any candidate with an error, retains the prior snapshot, exposes/logs the complete
  report, and lets the reload framework surface failure without terminating the JVM from low-level code.
- Production/disable-invalid removes the owner of each invalid decoded definition, validates again, and repeats to a
  stable fixed point. A bad palette can therefore disable its material and then banners using that material. Authored
  immutable definitions are never mutated and unrelated substitutes are never selected.
- Every issue carries stage, severity, domain, optional definition ID, exact source resource, stable issue code,
  message, and optional related ID. Reports are deduplicated and deterministically ordered before logging once.

### Test coverage and integration boundary

- Test-only generated datasets cover valid data, every applicable missing/mismatch case, malformed and unknown-schema
  resources, duplicate IDs, simultaneous errors, dependency cascades, replacement and preservation, reference-safe
  production subsets, deterministic ordering, immutable exposure, empty datasets, and authoritative embedded IDs.
- Atomic publication has a concurrent-reader test, and common registry classes are scanned for client references.
- The listener is compiled and wired to `AddReloadListenerEvent`, but no automated Minecraft bootstrap/GameTest was
  added. Runtime resource-manager override behavior and listener invocation therefore have isolated core coverage plus
  API compilation, not a live-server integration test. `ResourceManager.listResources` itself owns pack priority.

### Known limitations and deviations

- Logical asset IDs are syntax-validated. Physical geometry/model/texture existence is not checked until the project
  chooses a reliable mapping across vanilla, GeckoLib, texture, and custom-loader resource types; this is recorded in
  `OPEN_QUESTIONS.md`.
- Structurally undecodable resources remain visible in the report but cannot appear in a disabled typed-definition
  list because no valid immutable definition exists to retain.
- No resource-pack-stack integration test was added because constructing the actual Minecraft pack/bootstrap layer is
  unsuitable for the ordinary unit harness. Same-path replacement uses the platform's effective-resource map.
- No production definitions, catalogue manifest, assets, gameplay registrations, components, items, blocks, block
  entities, screens, packets, rendering, recipes, commands, placement mechanics, or Milestone 4 work were added.

### Next milestone

Stop after the Milestone 3 commit and owner review. The next permitted work is Milestone 4 - Scaffold All 33 Banner
Placeholders - only; do not begin it as part of this milestone.

## 2026-07-20 - Milestone 2: Core Data Records and Codecs

### Files changed

- Added banner data contracts under `banner/data`: `BannerDefinition`, `BannerDimensions`, `BannerAssets`,
  `BannerSourceReference`, `BannerContentStatus`, `MountDefinition`, and `PlacementProfile`.
- Added common item-compatible banner state under `banner/state`: `BannerInstanceState`.
- Added dye definitions under `dye/data`: `FabricMaterialDefinition` and `PigmentDefinition`.
- Added palette contracts under `dye/palette`: `MaterialPalette` and `MaterialPaletteEntry`.
- Added dye state and result contracts: `DyeTubState`, `DyeResult`, and `MatchType`.
- Added `DataCodecs` for the shared schema, canonical sRGB, finite-number, OKLab, tag, and non-blank-string
  structural codecs.
- Added `CoreDataFixtures`, `CoreDataCodecTest`, and `CoreDataValidationTest`.
- Updated `OPEN_QUESTIONS.md` with the deliberately deferred occupied-cell/anchor semantics for placement profiles.

### Commands and results

1. Required Git preflight:
   - `git branch --show-current` - `banners-dyetub`.
   - `git status --short --branch` - only the preserved modified `ModConfig.java` and preserved untracked root
     specifications, `.claude/`, `logs/`, and `tmp/` were present.
   - `git merge-base banners-dyetub patch-18` - `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
   - `git rev-list --left-right --count patch-18...banners-dyetub` - `0 2`.
   - `git log --oneline --decorate -5` - expected Milestone 1 `129ed2d` and Milestone 0 `e43bbc5` were the first
     two feature commits.
2. Initial restricted `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon
   --stacktrace` - could not access/download the Gradle 8.9 distribution because sandbox network access was denied.
3. The first approved retry was given an accidentally short command timeout. Its orphaned worker temporarily held
   `build/test-results/test/binary/output.bin`; the next retry reported that output-lock error. `.\gradlew.bat --stop`
   stopped the one orphaned daemon. No source or test assertion failed in either attempt.
4. `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` after the
   complete fixture coverage was added - passed in 11 seconds. XML results: 130 tests, 0 failures, 0 errors,
   0 skipped.
5. `.\gradlew.bat clean --no-daemon` - passed in 9 seconds.
6. `.\gradlew.bat test --no-daemon --stacktrace` - passed in 39 seconds. XML results: 130 tests, 0 failures,
   0 errors, 0 skipped.
7. `.\gradlew.bat build --no-daemon` - passed in 14 seconds; `jar`, `jarJar`, `assemble`, and `build` completed.
8. `git diff --check` - passed before documentation/staging review; repeated during final review.
9. Common-source client/API scan - no `net.minecraft.client`, Blaze3D, gameplay objects, registration APIs,
   reload listeners, payloads, screens, renderers, items, blocks, or block entities in the Milestone 2 data packages.

No initially failing JUnit test required correction. The only initial failures were Gradle environment access and an
orphaned-worker file lock, as described above.

### Structural validation decisions

- Reused all six Milestone 1 stable ID wrappers. Asset, model, texture, and palette identities use namespaced
  `ResourceLocation` values rather than a parallel identifier system.
- All stored top-level contracts use `BannerDyeingConstants.CURRENT_SCHEMA_VERSION`; unknown versions fail with an
  explicit codec error. Nested value records and the transient `DyeResult` do not repeat a schema field.
- Persistent codecs require every non-optional field and do not provide invalid-value defaults. Network codecs exist
  only for `BannerInstanceState` and `DyeTubState`, the two contracts selected for future typed synchronized item
  components by `PROJECT_FACTS.md`.
- Banner width is limited to one through three blocks. Height is limited to one through 16 blocks; 16 is a documented
  conservative occupancy bound that exceeds the current catalogue while preventing unbounded structural input.
- Banner orientations and mounts must be non-empty, and the default mount must be in the supported mount collection.
- Canonical sRGB is uppercase six-digit `#RRGGBB`. OKLab components and dye-result distances must be finite; distances
  must also be non-negative. Numeric fixture values remain illustrative authored data, not scientifically verified
  production colours.
- Palettes reject duplicate entry IDs, require their natural colour to be one of their own entries, and require local
  pigment overrides to target one of those entries. Overrides are copied into lexical pigment-ID order for stable
  output.
- Unlimited dye-tub uses have one representation: an absent `remaining_uses`. Finite counts may be zero or positive;
  a use count without a loaded pigment is structurally invalid. No consumption or replacement policy is encoded.
- Lists and maps are defensively copied and exposed as immutable collections. Source-sheet labels are preserved only
  as provenance and are never promoted to display names.

### Validation deferred to Milestone 3

- Registry membership and existence of referenced definitions, materials, pigments, colours, mounts, palettes,
  placement profiles, models, and textures.
- Agreement between a banner definition and its referenced placement profile or material palette.
- Cross-resource uniqueness, missing resources, disabled-entry policy, and complete registry-set validation.
- Whether an absent source pigment corresponds to the material's natural colour; that requires loaded material and
  palette data. Decoding intentionally remains valid without live registries.

### Known limitations and deviations

- `PlacementProfile` deliberately contains only schema version, stable ID, declared dimensions, and a wall-support
  flag. Occupied offsets, rotations, anchor choice, support-cell rules, and placed-state persistence remain deferred
  to the placement milestones and are recorded in `OPEN_QUESTIONS.md`.
- No data loading, registries, catalogue data, palette resolution, colour conversion, gameplay objects, components,
  packets, rendering, placement behavior, commands, recipes, or assets were added.
- `MaterialPaletteEntry` is nested in a versioned `MaterialPalette`; it does not repeat `schema_version`.
- `DyeResult` is a small immutable resolver result and is not a stored top-level state object, so it does not include a
  schema field or a network codec.

### Next milestone

Stop after the Milestone 2 commit and owner review. The next permitted work is Milestone 3 - Data Registries and
Validation Pipeline - only; do not begin it as part of this milestone.

## 2026-07-20 — Milestone 1: Feature Skeleton, IDs, and Test Harness

### Files changed

- Added common feature entry points: `BannerFeature`, `DyeFeature`, and `BannerDyeingBootstrap`.
- Added the shared `StableResourceId` contract and immutable `ResourceLocation` wrappers for banner definitions, fabric materials, pigments, resolved colours, mounts, and placement profiles.
- Added the stable `BannerOrientation` enum for wall-parallel and wall-perpendicular orientations.
- Added `BannerDyeingConstants` with schema version 1 and the initial cotton, brass, and iron IDs.
- Added JUnit Jupiter test dependencies and enabled the JUnit Platform in `build.gradle`.
- Added reusable ID fixtures and tests covering construction, parsing, equality, string form, persistent codecs, stream codecs, invalid identifiers, null rejection, orientation serialization, bootstrap constants, logger categories, and common-code client-reference safety.

### Commands and results

1. `git branch --show-current` — `banners-dyetub`.
2. `git merge-base patch-18 HEAD` — `62df1dc97c5113a86f9c0f258cb90538f31efe89`; `git rev-list --left-right --count patch-18...banners-dyetub` returned `0 1` before this milestone commit.
3. Initial restricted-sandbox narrow Gradle invocation — could not download/access the Gradle distribution because network access was denied; rerun with approved dependency access.
4. `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` — first approved run reached project compilation and found one test-only `JsonOps` input type error. The test was corrected to use `JsonPrimitive`; production code compiled.
5. The same narrow test command after correction — passed in 13 seconds. XML results: 53 tests, 0 failures, 0 errors, 0 skipped.
6. `.\gradlew.bat clean --no-daemon` — passed in 17 seconds.
7. `.\gradlew.bat test --no-daemon --stacktrace` — passed in 59 seconds. XML results: 53 tests, 0 failures, 0 errors, 0 skipped.
8. `.\gradlew.bat build --no-daemon` — passed in 22 seconds; `jar`, `jarJar`, `assemble`, and `build` completed.
9. Common-source client-reference scan — passed with no `net.minecraft.client` or `com.mojang.blaze3d` references.
10. Out-of-scope API scan — passed with no registrations, block entities, menus, screens, renderers, recipes, or custom payload APIs in the Milestone 1 packages.

### Decisions

- Model stable domain identifiers as small immutable record wrappers around `ResourceLocation`, with a shared read-only contract while retaining distinct compile-time types.
- Put both persistent `Codec` and network `StreamCodec` definitions on each identifier type so later milestones share one canonical serialization boundary.
- Reject malformed identifiers through the repository's Minecraft 1.21.1 `ResourceLocation` validation rather than adding a second validation grammar.
- Keep the feature bootstraps side-effect-free in this milestone. Registration and gameplay wiring belong to later milestones.
- Use structured logger categories dedicated to banner and dye content validation.
- Add the smallest conventional JUnit 5 harness because the repository had no test framework or test sources.

### Known limitations and deviations

- No registries, content catalogue, JSON loading, blocks, items, block entities, screens, renderers, packets, recipes, or gameplay behavior are implemented; these are intentionally outside Milestone 1.
- The bootstrap classes establish common boundaries but are not invoked by the main mod initializer until a later registration milestone has real work to wire.
- This milestone has unit-level common-code safety checks only. No manual in-game check is meaningful for a registration-free skeleton.
- The first approved narrow run exposed and led to correction of a test-only compile error before the successful validation runs; it was not a production-code failure.

### Next milestone

Stop after the Milestone 1 commit and owner review. The next permitted work is Milestone 2 only; do not begin it as part of this milestone.

## 2026-07-20 — Milestone 0: Repository Discovery and Implementation Facts

### Branch-creation evidence

- Remote used: `origin` (`https://github.com/Seggellion/Britannia_Mod.git`).
- Remote refresh: `git fetch --prune origin` succeeded.
- Starting branch: `patch-18`, tracking `origin/patch-18`.
- `patch-18` tip after fetch: `62df1dc97c5113a86f9c0f258cb90538f31efe89` (`Add refill fishing rod barrel at 5213 66 8912 (#400)`).
- Local/remote divergence: `git rev-list --left-right --count patch-18...origin/patch-18` returned `0 0`.
- Existing feature branch check: `banners-dyetub` did not exist locally.
- Creation command: `git switch -c banners-dyetub patch-18`.
- Creation point / merge base: `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
- Initial feature/base divergence: `git rev-list --left-right --count patch-18...banners-dyetub` returned `0 0`.
- Current branch: `banners-dyetub`.
- No reset, deletion, merge, rebase, stash, or checkout-discard operation was used.

### Pre-existing working tree

The branch was intentionally created with the user's existing work present and preserved:

- Modified: `src/main/java/com/seggellion/britannia_mod/config/ModConfig.java` (local API base URL selection).
- Untracked: `.claude/`, `UltimaCraft_Banner_Dyeing_LLM_Build_Spec.md`, `UltimaCraft_Banner_and_Dyeing_System_Design.md`, `logs/`, and `tmp/`.

The two root specification files were read in full before branch creation. None of the pre-existing paths is part of the Milestone 0 commit.

### Files added

- `docs/banner-dyeing/PROJECT_FACTS.md`
- `docs/banner-dyeing/OPEN_QUESTIONS.md`
- `docs/banner-dyeing/IMPLEMENTATION_LOG.md`

No gameplay code, registrations, renderers, packets, screens, recipes, block entities, catalogue entries, or assets were added.

### Repository facts recorded

- Gradle 8.9 / NeoGradle UserDev 7.0.165.
- Minecraft 1.21.1 / NeoForge 21.1.72 / Java 21 / active official mappings.
- `britannia_mod` and `com.seggellion.britannia_mod` token mappings.
- NeoForge deferred registration conventions.
- Typed persistent/networked data components as the selected item-state architecture.
- NBT plus update-tag/update-packet block-entity conventions.
- Custom payload networking and direct client `Screen` flow.
- Client-only event gating and rendering registration.
- Static/manual recipe state and absent data generator.
- Empty unit/GameTest source state.
- Metal-specific blacksmithing material flow and the absence of a reusable general material ID.
- Branch, CI, and release integration conventions.
- The exact 33-banner catalogue constraint, including unnamed placeholder handling.

### Baseline commands and results

1. `git fetch --prune origin` — passed; added remote refs and confirmed `patch-18` remained current.
2. `.\gradlew.bat clean build --no-daemon` in the restricted sandbox — could not start because Gradle 8.9 download network access was denied (`java.net.SocketException: Permission denied: getsockopt`). This was an environment restriction, not a repository failure.
3. `.\gradlew.bat clean build --no-daemon` with approved dependency/network access — failed in `:neoFormPatch` before project compilation. A second identical clean-build invocation reproduced the failure.
4. `.\gradlew.bat test --no-daemon --stacktrace` with approved access — passed in 1m 10s. `compileTestJava` and `test` were `NO-SOURCE`. Project compilation emitted two existing warnings: missing Javadoc on `PlayerSleepMixin`'s `@Overwrite`, and the deprecated-for-removal `Item.initializeClient(IClientItemExtensions)` override in `OrderShieldItem`.
5. `.\gradlew.bat neoFormPatch --rerun-tasks --no-daemon --stacktrace` — passed/up-to-date in 20s, showing the NeoForm patch stage works outside the combined clean-build invocation.
6. `.\gradlew.bat clean --no-daemon` — passed in 12s.
7. `.\gradlew.bat build --no-daemon` immediately after the separate clean — passed in 42s; compiled, packaged `jar`/`jarJar`, and reported `test` / `testJunit` as `NO-SOURCE`.
8. `.\gradlew.bat tasks --all --no-daemon` — passed and confirmed `build`, `check`, `test`, `runData`, and `runGameTestServer` tasks.

Assessment: the combined `clean build` failure is pre-existing build/toolchain behavior, likely an ordering/cache interaction between parallel Gradle clean and NeoForm output. The inference is supported by the repeatable combined failure and successful isolated patch task plus separate clean/build. It does not currently block later work, provided verification uses separate `clean` and `build` invocations. No unrelated build fix was attempted.

### Documentation and post-change checks

1. Required-file/content validation — passed. All three documents exist; repository tokens, open-question categories, blacksmithing facts, and the exact-33/`Name Required` rule are present.
2. Trailing-whitespace scan across `docs/banner-dyeing/*.md` — passed with no matches.
3. `.\gradlew.bat build --no-daemon` — passed in 19s after the documentation changes; `test` and `testJunit` remained `NO-SOURCE`, and compilation/package tasks were up-to-date.
4. Final staged `git diff --check` and scope inspection are required immediately before commit.

### Decisions

- Use typed custom data components for banner and dye-tub item state.
- Use namespaced `ResourceLocation` IDs under `britannia_mod`.
- Do not reuse metal-only `UOMetalToolMaterial` or jewelry's separate metal enum for fabrics.
- Reuse blacksmithing's server-authoritative material-from-input interaction principles.
- Use the existing payload-opened client `Screen` convention for a slotless dye preview and a server-revalidated C2S confirmation.
- Keep renderers/screens/models in client packages and common state/codecs free of client imports.
- Register placed-banner rendering through the client render event; verify the non-deprecated item-render API in the rendering milestone.
- Preserve exactly 33 banner entries in release. Unnamed entries remain included with stable provisional IDs and `Name Required` status; placeholder names/dimensions are not final content.

### Known limitations and deviations

- The root specification files remain untracked and were not duplicated into `docs/`. The initiating prompt identifies the root copies as authoritative and limits the requested Milestone 0 commit to the three integration documents. This is a documented deviation from the playbook's general instruction to copy untracked specs into the documentation area.
- The repository has no implemented data-generation provider, reloadable JSON registry, unit tests, or GameTests. Later milestones must establish these incrementally.
- The repository's existing custom item renderer hook is deprecated for removal; its supported replacement must be verified before banner rendering work.
- No manual in-game check was required or performed for this documentation-only milestone.

### Commit

- Message: `docs(banners): record repository integration facts`
- Hash: this log is part of that commit; record the resulting hash in the Milestone 0 report.

### Next milestone

Stop at Gate A for owner review. Recommend Milestone 1 — Feature Skeleton, IDs, and Test Harness — only after the repository facts and integration choices are approved.
