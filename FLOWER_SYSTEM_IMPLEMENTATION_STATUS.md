# Persistent Flower System Implementation Status

Repository: `C:/projects/britannia/mod/Britannia_Mod`  
Branch: `Farming`  
Current HEAD: `66b36a926bac376188fbe79bd2ba73d2d4fb0190`
Working state: Milestone 7 is committed; Milestone 8 is owner-approved, revalidated, and ready for its isolated commit; Milestone 9 is authorized and in progress after that commit; unrelated `.claude/` is preserved.

| Milestone | Status | HEAD/commit | Tests | Owner approval | Notes |
|---|---|---|---|---|---|
| 0. Preflight | Approved | `41fb51a` | Baseline `compileJava processResources test` passed during discovery | Approved | Root, exact branch, HEAD, guidance, and baseline recorded. |
| 1. Discovery | Approved | `41fb51a` | Read-only discovery and baseline validation complete | Approved | Discovery report is present and owner questions were answered. |
| Owner decision gate | Approved | `41fb51a` | N/A | Approved | B1, B2, A1-A3, G1-G4, AS1, N1, and color-capacity direction recorded. |
| 2. Data contracts | Approved | `0a600a9d2b86ac839e4ff03b1dabf445eccec7bf` | Final `compileJava processResources test` passed; 13 tests, 0 failures | Approved | Committed as `feat(flowers): add species and colour data contracts`. |
| 3. Placeholders | Approved | `ebba19b92269e5649eb254302e6311fa57f5743b` | Full build/test passed; 18 tests, 0 failures; focused domain and asset tests passed; generator check passed; bounded client resource-load smoke passed for flower assets | Approved | Committed as `content(flowers): add placeholder items seeds models and masks`. |
| 4. Lifecycle | Approved | `f5ed405484d99695862fe788dd01b8449cef594f` | Full automated suite plus isolated server/client smoke passed | Approved | Committed as `feat(flowers): add atomic planting and persistent flower state`. |
| 5. Growth | Approved | `f3589b2526ecd51ab5286f6e8735a1527ea74df7` | Full and focused flower suites passed: 38 tests, 0 failures; placeholder audit and bounded isolated server/client smokes passed | Approved | Committed as `feat(flowers): integrate perennial growth with farming simulation`. |
| 6. Interactions | Approved | `bc94e4d10bff03a86f5cf519b4a03a488f3e6e9d` | Full 48-test suite, focused flower suites, 231-file generator audit, diff check, and isolated server/client smokes passed | Approved | Committed as `feat(flowers): add protected interactions and poppy mastery stage`. |
| 7. Rendering | Approved | `66b36a926bac376188fbe79bd2ba73d2d4fb0190` | 57-test suite, focused rendering suite, 231-file generator audit, isolated dedicated-server restarts, live client fixture, resource reload, chunk cycle, and screenshot review passed | Approved | Committed as `feat(flowers): render multi-plane flowers with dye masks`. |
| 8. Species content | Approved | Unstaged working tree on `66b36a9` | Full/focused 64-test suite, 231-file generator audit, diff check, and isolated server/client smokes passed | Approved | Owner-approved and revalidated; isolated commit pending. Approved definitions were already complete; localized two interaction messages and added comprehensive content/viability/acquisition coverage. |
| 9. QA | In progress | Begins after the isolated Milestone 8 commit | Test-gap analysis pending | Authorized | Regression testing, save safety, and multiplayer QA only. |
| 10. Closeout | Not started |  |  | Not authorized |  |

## Current Milestone 2 validation state

- Final combined command: `.\gradlew.bat compileJava processResources test --console=plain --no-configuration-cache` — passed.
- `compileJava`: passed with two pre-existing warnings (`PlayerSleepMixin` missing `@Overwrite` Javadoc and deprecated `Item.initializeClient`).
- `processResources`: passed/up to date.
- `test`: passed, 13 tests, 0 failures, 0 errors, 0 skipped.
- Dye Tub source on `Farming`: unavailable. The compatibility-ready finding is documented in the decision and discovery reports.
- DOCX content review: complete structural/text extraction; visual rendering unavailable because LibreOffice/`soffice` is not installed.

No client rendering, dedicated-server loading, multiplayer synchronization, world planting, or save compatibility is claimed by this milestone.

## Current Milestone 3 validation state

- Final combined command: `.\gradlew.bat compileJava processResources test --console=plain --no-configuration-cache` - passed.
- `compileJava`: passed with the same two pre-existing warnings recorded for Milestone 2.
- `processResources`: passed.
- `test`: passed, 18 tests total (13 domain and 5 asset-contract tests), 0 failures.
- Placeholder generator verification: `python tools/generate_flower_placeholders.py --check` - passed for 228 generated files plus the hash ledger.
- Bounded client smoke: `.\gradlew.bat runClient -Pdev --no-configuration-cache --console=plain` reached resource reload, sound initialization, and atlas creation. The intentionally persistent client was then stopped after the bounded validation window.
- Targeted log searches found no missing-model, missing-texture, file-not-found, or resource-load failure messages for the 14 flower/seed item IDs or the `britannia_mod:item/flowers` and `britannia_mod:block/flowers` asset paths.
- The client log contains pre-existing resource errors for unrelated content. They were left untouched.
- Standalone per-stage pass models are not registered or selected at runtime in Milestone 3; their structure, texture references, dimensions, transparency, grayscale masks, and alpha separation are enforced by `FlowerAssetContractTest` and the generator hash ledger pending Milestone 7 rendering work.
- All generated art is placeholder art and is not approved final artwork.

## Current Milestone 4 validation state

- Final combined command: `.\gradlew.bat compileJava processResources test --console=plain --no-configuration-cache` - passed; 27 tests total (13 domain, 5 asset-contract, and 9 lifecycle tests), 0 failures.
- Focused `FlowerDomainTest`, `FlowerAssetContractTest`, and `FlowerLifecycleTest` runs passed independently.
- Placeholder generator verification: `python tools/generate_flower_placeholders.py --check` - passed for 228 generated files plus the hash ledger.
- Dedicated-server smoke used an isolated working directory under `build/`; the server created a new disposable world and reached `Done (8.954s)`. No flower registration, classloading, or resource failure was logged. The smoke processes were stopped after startup.
- The dedicated-server log retained two unrelated pre-existing findings: the client-only `TitleScreenBackgroundMixin` is referenced on the dedicated-server distribution, and the absent optional `config/britannia_mod.properties` file logs a warning/stack trace before defaults are used. Neither prevented startup and neither is in Milestone 4 scope.
- Client smoke used an isolated working directory under `build/`; resource reload, OpenAL initialization, and block-atlas creation completed. Targeted searches found no missing model/blockstate/resource error for `flower_block` or the flower block entity. Existing unrelated asset warnings remain.
- Lifecycle tests cover every registered flower seed, stage-1 initialization, exactly one colour selection after validation, origin/protection/UUID/provenance capture, complete soil/community snapshots, rollback at every post-replacement failure point, full save/load, client UUID redaction, deterministic legacy/unknown-data behavior, and rejection of corrupt community metadata.
- This milestone intentionally does not claim a manually played world planting session, two live multiplayer clients, growth, harvesting, cutback, uprooting, protection enforcement, flower rendering, Poppy stage-7 gameplay, or a skinning knife.
- No Farming skill award is made at planting because the approved flower definitions expose no crop tier or skill modifier; inventing an award amount would exceed the data contract. Planting reuses the existing crop-plant sound.

## Current Milestone 5 validation state

- Final combined command: `.\gradlew.bat cleanTest compileJava processResources test --console=plain --no-configuration-cache` - passed.
- Final focused `FlowerDomainTest`, `FlowerAssetContractTest`, `FlowerLifecycleTest`, and `FlowerGrowthTest` run passed: 38 tests total, 0 failures, 0 errors, 0 skipped.
- `FlowerGrowthTest` contributes 11 tests covering all seven species, timing profiles, natural maxima, deterministic stage boundaries, blocked/resumed growth, shared multiplier/weather semantics, reset identity preservation, persistence, client observer agreement, and architectural reuse.
- Placeholder generator verification: `python tools/generate_flower_placeholders.py --check` - passed for 228 generated files plus the hash ledger.
- A bounded dedicated-server smoke in a disposable ignored `build/` runtime reached `Done (9.810s)` and logged no flower-related error. The known dedicated-server `TitleScreen` distribution error remains; the disposable directory initially lacked `server.properties`, which Minecraft then generated. Neither prevented startup.
- A bounded client smoke in a disposable ignored `build/` runtime completed resource reload, OpenAL/sound initialization, and the 8192x4096 block atlas. Targeted searches found no flower-related error. Existing unrelated invalid-path and GeckoLib animation errors remain untouched.
- No manual planted-world save/restart, prolonged random-tick observation, or two-live-client multiplayer session is claimed. Persistence, resumed growth, natural maturity, reset invariants, and identical observer update tags are covered by automated tests; live interaction/rendering awaits later authorized milestones.
- An initial runtime launch did not honor the intended working-directory override and reached stable startup in the existing `run/server` directory before being stopped. No tracked files changed, but its log/world runtime may have been touched; the final server validation was rerun successfully in the isolated disposable directory above.

Milestone 3 is validated, owner-approved, and committed as `ebba19b92269e5649eb254302e6311fa57f5743b`. Milestone 4 is validated, owner-approved, and committed as `f5ed405484d99695862fe788dd01b8449cef594f`. Milestone 5 is validated, owner-approved, and committed as `f3589b2526ecd51ab5286f6e8735a1527ea74df7`. Milestone 6 is validated, owner-approved, and committed as `bc94e4d10bff03a86f5cf519b4a03a488f3e6e9d`. Milestone 7 is validated, owner-approved, and committed as `66b36a926bac376188fbe79bd2ba73d2d4fb0190`. Milestone 8 is owner-approved, revalidated, and ready for its isolated commit. Milestone 9 is authorized and in progress after that commit.

## Current Milestone 6 implementation state

- `FlowerProtectionService` is the sole Creative/operator-level-2 authorization policy. `FlowerMutationReason` represents care, harvest, cutback, uproot, normal break, replacement, Poppy stage 7, explosion, fluid, piston, admin, command, world-generation, and system paths.
- `FlowerInteractionService` owns deterministic right-click handling, successful-only tool damage, mature shearing, crop-quality/provenance metadata, care, private/community restoration, and Poppy stage 7. Adventure cutback and indirect event paths delegate to the same policy through `FlowerInteractionHandler`.
- Right-click ordering is care, scissors harvest, skinning-knife Poppy stage 7, uprooting tool, protected replacement denial, then superclass behavior. Adventure cutback remains a canceled left-click path.
- Mature shearing produces exactly one harvested flower, recalculates/persists quality, preserves identity, and resets to stage 1. `HarvestedFlowerItem` delegates off-hand/shift-use to `CropSeedExtractor`, producing one mapped seed and consuming one flower outside Creative.
- Permanent uprooting accepts `farming_hoe` and `root_crop_shovels`, restores exact private soil or the unprepared community block, returns no produce/seed, and damages the tool only after restoration succeeds. Normal player breaking uses the same restoration transaction and produces no drop.
- All flowers return `PushReaction.BLOCK`. Protected flowers are removed from explosion targets and reject fluid placement. Ordinary flowers retain normal explosion/fluid behavior; system/command mutation remains authorized.
- The dedicated `skinning_knife` is a non-combat utility `Item` with 128 durability, implicit stack size 1, no repair ingredient, no recipe/economy source, farming-tool creative placement, and membership in `skinning_knives`. Its generated icon is explicitly placeholder art.
- Poppy stage 7 requires exact Poppy stage 6, Farming skill at least 100.0, a tagged skinning knife, server authority, and mutation authorization. It changes only stage/growth completion and has no permanent unlock.
- Existing `FarmingEventHandler` nutrient interception remains a pre-existing crop-only duplicate. Flower care does not broaden or rewrite that event path; both crop block care and flower care reuse `FarmingSoilCare` nutrient identity and normalized arithmetic.

## Current Milestone 6 validation state

- Final combined command: `.\gradlew.bat cleanTest compileJava processResources test --console=plain --no-configuration-cache` - passed; 48 tests total, 0 failures.
- Focused `FlowerDomainTest`, `FlowerAssetContractTest`, `FlowerLifecycleTest`, `FlowerGrowthTest`, and `FlowerInteractionTest` run passed independently. `FlowerInteractionTest` contributes 10 interaction/protection tests.
- Placeholder generator verification: `python tools/generate_flower_placeholders.py --check` - passed for 231 generated files plus the hash ledger. `git diff --check` passed with line-ending warnings only.
- A bounded dedicated-server smoke used a disposable ignored `build/` runtime and an ephemeral Minecraft port. Britannia initialized and the server reached `Done (12.586s)`; the harness then terminated the isolated process tree. No tag, skinning-knife registration, flower interaction-handler, block-entity, flower protection, or flower resource failure was logged.
- The server retained the pre-existing dedicated-server `TitleScreenBackgroundMixin` invalid-distribution error and absent optional `config/britannia_mod.properties` warning before successfully reaching readiness. These are unrelated to Milestone 6 and remain untouched.
- A bounded client smoke used the same disposable ignored runtime. Britannia initialized, OpenAL and the sound engine started, and the 8192x4096 block atlas was created. Targeted log review found no flower or `skinning_knife` resource failure; unrelated legacy missing-model warnings remain.
- No live two-client session or manually played interaction fixture is claimed. Watering/fertilizer, shearing, Adventure cutback, private/community uprooting, protected denial, Poppy stage 7, and restart-after-interaction are covered by automated/source-contract tests but remain candidates for owner playtesting.
- No Milestone 7 renderer, block-entity-renderer registration, model selection, or tint-mask behavior was added. The temporary soil-only presentation remains in effect.

## Current Milestone 7 implementation state

- `FlowerVisualModels` owns immutable identifiers for all 98 render models: seven species, seven stages, and the base/mask passes. Its pure render-plan resolver preserves exact valid stages and saved 24-bit tints, clamps invalid stages only for presentation, and supplies deterministic Poppy-stage-1/white fallbacks without mutating persisted flower state.
- `FlowerBlockEntityRenderer` reacquires baked models from `ModelManager` on every render, submits exactly two ordered `RenderType.cutout()` passes (white base, then saved-tint mask), and shares one pose, deterministic yaw, packed light, and overlay across both passes. Missing requested models fall back per pass; missing fallback models skip only that pass with bounded diagnostics.
- `ClientModSetup` registers all 98 additional models and binds `FLOWER_BLOCK_BE` to the flower renderer exactly once under the existing client-only subscriber. Reload handling clears only bounded diagnostics; no baked-model reference survives a reload.
- The renderer draws flower planes above the existing soil surface. Hydration soil blockstates, the existing crop renderer/model selector, persisted flower data, growth, interactions, recipes, world generation, balance, and species content are unchanged.
- Placeholder geometry and artwork remain the Milestone 3 shared four-plane assets. Every shared-parent face now declares tint index 0 so Minecraft applies the renderer multiplier to both white-base and coloured-mask submissions. Milestone 7 adds no third pass and no new PNG artwork.
- Corrupt out-of-range saved tint integers are preserved through load/save in a persistence-only `FlowerColor` construction path. Normal callers still enforce the 24-bit RGB boundary; the renderer receives corrupt raw values and uses its deterministic species fallback without rewriting saved data.

## Current Milestone 7 validation state

- Owner visual approval closes the Milestone 7 visual gate. The owner personally reviewed and approved placement above farming soil, visible soil, multi-plane viewing angles, deterministic rotation, base/mask alignment, fixed stem and leaf colours, bloom-only tint isolation, light and dark tint readability, stage selection, Poppy stages 6 and 7, empty early-stage masks, same-species colour variation, absence of visible Z-fighting and coloured halos, and the overall renderer result.
- Final combined command: `.\gradlew.bat cleanTest compileJava processResources test --console=plain --no-configuration-cache` - passed; 57 tests total, 0 failures, 0 errors, 0 skipped.
- Focused `FlowerDomainTest`, `FlowerAssetContractTest`, `FlowerLifecycleTest`, `FlowerGrowthTest`, `FlowerInteractionTest`, and `FlowerRenderingTest` run passed independently. `FlowerRenderingTest` contributes nine rendering/model/reload/boundary tests.
- Placeholder generator verification: `python tools/generate_flower_placeholders.py --check` - passed for 231 generated files plus the hash ledger. A generator overwrite/check cycle also passed after the manifest contract was updated.
- An ignored full project copy under `build/flower-validation-project/` hosted an isolated flat disposable world on `127.0.0.1:25576`. The dedicated server reached readiness on three clean starts, including two save/stop/restart cycles after corrections; the final start reached `Done (0.507s)`. No flower client-render classloading failure occurred. Known unrelated `TitleScreenBackgroundMixin`, optional-config, and legacy startup diagnostics remain untouched.
- A live client joined and rejoined that server as `Dev`. A temporary client-only capture harness existed only in the ignored project copy; it used Minecraft's real framebuffer screenshot and `reloadResourcePacks()` APIs and never entered tracked source.
- The world contained a seven-species by seven-stage matrix, targeted Poppy stage-5 light/dark, Poppy stage-6/7, empty-mask, community/protected, unknown-species, low/high-stage, corrupt-tint fixtures, and a 10x10 dense garden (160 flowers total including the targeted row and invalid cases). All valid species/stages, multiple colours, hydration 0-5, deterministic rotations, and soil visibility were inspected.
- The first close-up exposed untinted white masks because the shared model faces lacked `tintindex`; the final corrected captures show the saved light and burgundy Poppy stage-5 colours distinctly while the green base remains unchanged. A corrupt `ColorTint:-1` initially cleared block-entity state; the persistence-only raw-tint path now preserves `-1`, logs once per diagnostic scope, and renders the configured fallback without changing saved NBT.
- Static front, side, elevated, low, normal-eye, matrix, and dense-garden views showed correct soil-top placement, multi-plane visibility, aligned alpha-disjoint base/mask pixels, and no visible checkerboarding, halos, seam separation, or static Z-fighting. Continuous-motion flicker and formal frame-time profiling were not measured by the automated framebuffer harness.
- A live in-session resource reload completed twice after the fixture was visible; the final corrected run then captured the unchanged fixture. Teleporting to `(1000,1000)` and back forced a chunk unload/reload and preserved species, stage, tint, rotation, and rendering. Server restart queries also confirmed the unknown species, clamped stages, and exact corrupt tint persisted as expected.
- Evidence is retained in ignored development output at `build/flower-render-review/`; `defect-before-tintindex.png` records the found tint failure and screenshots `25` through `33` record the corrected restart, close-up, matrix, dense, live-reload, and chunk-cycle results.
- No two-live-client session or formal profiler capture was performed. Deterministic observer equality remains covered by automated synchronization/render-plan tests; owner multiplayer and subjective motion/performance playtesting remain recommended rather than claimed.

## Milestone 8 gap analysis and resolution

- The design document, `FLOWER_SYSTEM_DECISIONS.MD`, `FlowerRegistry`, item registration, localization, model resources, creative groupings, and the existing tests were compared species by species before editing.
- All seven definitions already matched the approved design palettes, weights, stage limits, initial timings, item/seed mappings, altitude ranges, and the recorded canonical-climate conversion. No approved palette or balance value was overwritten.
- The design's conceptual 0-100 hydration values remain normalized into the shared profile and evaluated against the existing 0-5 FarmingBlock hydration scale. The composite nutrient range remains the recorded repository adaptation: the same midpoint and conservative symmetric tolerance are applied to nitrogen, phosphorus, potassium, and organic matter through `CropQualityCalculator`. No second growth formula or climate enum was introduced.
- The only player-facing data gap was two hard-coded interaction messages. Protected-action denial and Poppy stage-7 requirements now use English localization keys.
- The only validation gap was explicit per-species practical viability and lower/unsuitable-condition coverage. `FlowerSpeciesContentTest` now audits all seven species, all palettes and mappings, practical maturity, differentiated climate response, player-facing resources, acquisition boundaries, and the 98-model/98-PNG contract.
- Debug output remains literal by repository convention and is not a missing localization-dependent registration. No broad lore, tooltip, manual, or guidebook content was added.

## Initial species content table

Legend: climate columns are preferred / tolerated / unsuitable; hydration and nutrient ranges are design-normalized ideal / tolerated values; `N/P/K/OM` means the same initial composite midpoint/tolerance is applied to all four shared farming nutrients. Every row has stage models 1-7, English localization, one-item mature harvest with stage-1 identity-preserving reset, species-mapped one-seed extraction through `CropSeedExtractor`, Creative/admin access, no recipe/worldgen/economy source, and **Initial tuning** balance status.

| Species registry ID | Flower item / seed item | Natural / absolute max | Base ticks | Climate: preferred / tolerated / unsuitable | Altitude ideal / tolerated Y | Hydration ideal / tolerated | N/P/K/OM ideal / tolerated | Palette count / fallback | Models / localization | Harvest / seed recovery | Acquisition / balance |
|---|---|---:|---:|---|---|---|---|---|---|---|---|
| `britannia_mod:poppy` | `poppy` / `poppy_seeds` | 6 / 7 | 5 | `TEMPERATE` / `ARID` / `ICE,FIRE,WETLAND,TROPICAL,MAGICAL,UNDERGROUND` | 45-125 / 20-170 | .30-.55 / .15-.70 | .35-.65 / .15-.80 | 8 / `scarlet` | 1-7 / yes | 1 flower, reset 1 / 1 seed | Creative/admin + renewal / Initial tuning |
| `britannia_mod:snowdrop` | `snowdrop` / `snowdrop_seeds` | 7 / 7 | 7 | `ICE` / `TEMPERATE,WETLAND` / `FIRE,TROPICAL,ARID,MAGICAL,UNDERGROUND` | 55-155 / 30-220 | .50-.75 / .35-.90 | .50-.80 / .30-.95 | 4 / `snow_white` | 1-7 / yes | 1 flower, reset 1 / 1 seed | Creative/admin + renewal / Initial tuning |
| `britannia_mod:lily` | `lily` / `lily_seeds` | 7 / 7 | 8 | `TEMPERATE` / `ICE` / `FIRE,WETLAND,TROPICAL,ARID,MAGICAL,UNDERGROUND` | 45-150 / 20-200 | .45-.70 / .30-.85 | .60-.85 / .40-1.00 | 10 / `snow_white` | 1-7 / yes | 1 flower, reset 1 / 1 seed | Creative/admin + renewal / Initial tuning |
| `britannia_mod:foxglove` | `foxglove` / `foxglove_seeds` | 7 / 7 | 9 | `TEMPERATE` / `WETLAND` / `ICE,FIRE,TROPICAL,ARID,MAGICAL,UNDERGROUND` | 70-180 / 40-240 | .50-.75 / .35-.90 | .50-.80 / .25-.95 | 9 / `violet` | 1-7 / yes | 1 flower, reset 1 / 1 seed | Creative/admin + renewal / Initial tuning |
| `britannia_mod:campion` | `campion` / `campion_seeds` | 7 / 7 | 6 | `TEMPERATE` / `WETLAND` / `ICE,FIRE,TROPICAL,ARID,MAGICAL,UNDERGROUND` | 45-150 / 20-210 | .45-.70 / .25-.85 | .40-.70 / .20-.90 | 6 / `rose_pink` | 1-7 / yes | 1 flower, reset 1 / 1 seed | Creative/admin + renewal / Initial tuning |
| `britannia_mod:hyacinth` | `hyacinth` / `hyacinth_seeds` | 7 / 7 | 7 | `TEMPERATE` / `ICE` / `FIRE,WETLAND,TROPICAL,ARID,MAGICAL,UNDERGROUND` | 40-120 / 20-170 | .35-.60 / .20-.75 | .50-.75 / .30-.90 | 9 / `hyacinth_blue` | 1-7 / yes | 1 flower, reset 1 / 1 seed | Creative/admin + renewal / Initial tuning |
| `britannia_mod:orfluer` | `orfluer` / `orfluer_seeds` | 7 / 7 | 9 | `MAGICAL,ICE` / `TEMPERATE` / `FIRE,WETLAND,TROPICAL,ARID,UNDERGROUND` | 110-220 / 70-280 | .40-.65 / .25-.80 | .55-.85 / .35-1.00 | 9 / `lavender` | 1-7 / yes | 1 flower, reset 1 / 1 seed | Creative/admin + renewal / Initial tuning |

## Practical growth scenarios

All scenarios use valid private FarmingBlock support, all four nutrients at the species profile midpoint, fertilizer level 0, and the shared evaluator. The tolerated climate is a positive but slower 0.65 climate-fit tier; the listed unsuitable climate blocks growth.

| Species | Positive practical condition | Lower-performing condition | Unsuitable condition |
|---|---|---|---|
| Poppy | Hydration 2, `TEMPERATE`, Y85 | `ARID`, same soil/Y | `FIRE` |
| Snowdrop | Hydration 3, `ICE`, Y105 | `TEMPERATE`, same soil/Y | `FIRE` |
| Lily | Hydration 3, `TEMPERATE`, Y97 | `ICE`, same soil/Y | `FIRE` |
| Foxglove | Hydration 3, `TEMPERATE`, Y125 | `WETLAND`, same soil/Y | `FIRE` |
| Campion | Hydration 3, `TEMPERATE`, Y97 | `WETLAND`, same soil/Y | `FIRE` |
| Hyacinth | Hydration 2, `TEMPERATE`, Y80 | `ICE`, same soil/Y | `FIRE` |
| Orfluer | Hydration 3, `MAGICAL`, Y165 | `TEMPERATE`, same soil/Y | `FIRE` |

## Milestone 8 acquisition and content boundaries

- All fourteen flower/seed items are registered, localized, modeled, textured with non-final placeholders, and appended to the existing Farming Produce/Farming Seeds creative groupings without reordering unrelated entries.
- `skinning_knife` remains registered, localized, modeled, placeholder-textured, present in the farming tools grouping, and the sole built-in member of `britannia_mod:skinning_knives`.
- Approved renewable path: shear a mature flower for exactly one species item while preserving the planted colour/identity and resetting to stage 1; off-hand or shift-use converts one harvested item into exactly one mapped seed with existing Creative/server-authoritative behavior.
- Deferred: flower/seed/skinning-knife recipes, natural generation, grass/mob/chest/merchant/quest sources, economy pricing, Rails integration, and final art. Skinning-knife acquisition is Creative/admin only until a later owner-approved source is implemented.

## Current Milestone 8 validation state

- Final combined command: `.\gradlew.bat cleanTest compileJava processResources test --console=plain --no-configuration-cache` - passed; 64 tests total, 0 failures, 0 errors, 0 skipped.
- Focused `FlowerDomainTest`, `FlowerAssetContractTest`, `FlowerLifecycleTest`, `FlowerGrowthTest`, `FlowerInteractionTest`, `FlowerRenderingTest`, and `FlowerSpeciesContentTest` runs passed independently. `FlowerSpeciesContentTest` contributes seven comprehensive species-content tests.
- Placeholder generator verification: `python tools/generate_flower_placeholders.py --check` - passed for 231 generated files plus the hash ledger. `git diff --check` passed with line-ending warnings only.
- A dedicated-server smoke ran from an ignored isolated project copy under `build/m8-runtime-smoke-66b36a9/`, generated a disposable world, and reached `Done (8.178s)`. Targeted review found no flower-related registration, classloading, or resource failure. The first bounded launch expired while Gradle prepared the isolated copy before Minecraft started; the warm retry passed.
- A client smoke from the same isolated copy reached sound-engine initialization and block-atlas creation. Targeted review found no Poppy, Snowdrop, Lily, Foxglove, Campion, Hyacinth, Orfluer, flower renderer, model, or texture failure.
- The client retained unrelated pre-existing invalid-path and GeckoLib animation diagnostics plus the optional-configuration warning. These did not prevent readiness and were left untouched.
- Milestone 8 changed no visual assets or model references, so the owner-approved Milestone 7 rendering result remains applicable and no new subjective visual review is claimed.
- No manually played multi-condition growth session, live two-client multiplayer session, recipe/economy/world-generation integration, or Milestone 9 regression pass is claimed. The approved practical-growth cases and acquisition boundaries are covered deterministically by the new suite.
