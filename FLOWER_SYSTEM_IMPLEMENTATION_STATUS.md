# Persistent Flower System Implementation Status

Repository: `C:/projects/britannia/mod/Britannia_Mod`  
Branch: `Farming`  
Current validation base HEAD: `68ae7754543cd733cf225f92d3c428398814b0a3`
Working state: **Corrective Milestone 11 — Approved** and ready for its one owner-authorized isolated commit; unrelated `.claude/`, supplied milestone briefs, and unrelated archives are preserved.

Corrective Milestone 11 supersedes historical pass-specific model-resource wording while preserving the approved two-texture/two-pass visual result.

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
| 8. Species content | Approved | `9e596ac2ece519754f3828745e3e1e921120a935` | Full/focused 64-test suite, 231-file generator audit, diff check, and isolated server/client smokes passed | Approved | Committed as `content(flowers): wire initial species palettes and growth profiles`. |
| 9. QA | Approved | `7b7a4384686e52db4cafeb26e38ece3f683ac316` | 82 focused tests pass; strict two-client raw-input accounting, clean disk-world restart, live reload, protected bypass checks, and dense-garden review pass | Approved | Committed as `test(flowers): complete regression and multiplayer coverage`; final pre-commit review removed the inert plural skinning-knife tag and revalidated all 82 tests plus the generator audit. |
| 10. Closeout | Approved | See final closeout commit in branch history | Full/focused 82-test gates, 231-file placeholder audit, isolated server/client smoke, and repository audit passed | Approved | Owner approved the documentation and implementation handoff. This does not approve placeholder art or authorize merge, push, release, or deployment. |
| 11. Canonical model correction | Approved | See final flower rendering correction commit in branch history | Full 108-test gate, focused 9-test renderer gate, 182-file generator audit, isolated dedicated-server/client restart, reload/chunk-cycle capture, and nine-view visual review passed | Owner-authorized validation and isolated commit | Replaced 98 pass-specific stage JSONs with 49 canonical stage models; retained the same 98 PNGs and corrected the two-pass renderer to bind the mask directly after completing the base pass. |

## Corrective Milestone 11 implementation state

- The duplicate-model defect was confirmed in `FlowerVisualModels`, `FlowerBlockEntityRenderer`, `ClientModSetup`, `tools/generate_flower_placeholders.py`, all 98 stage JSONs, three focused test suites, the placeholder manifest, and the hash ledger.
- Every species/stage now has one canonical model path: `assets/britannia_mod/models/block/flowers/<species>/stage_<n>.json`.
- Each canonical JSON inherits `shared/multi_plane` and resolves exactly two unique image resources through `flower` (base), `dye_mask`, and `particle` (base alias).
- `FlowerVisualModels` exposes 49 canonical `StageModel` records and registers only their canonical `ModelResourceLocation` values.
- `FlowerBlockEntityRenderer` resolves one canonical baked model. The base pass renders it in white from the block atlas; the dye-mask pass sends the same baked geometry through normalized UV remapping to the directly bound grayscale mask with the saved tint. Direct binding ensures the otherwise-unused mask material is available without a second baked model.
- All 98 former `stage_<n>_base.json` and `stage_<n>_dye_mask.json` files were retired through the generator only after their bytes matched the prior hash ledger. The 98 PNG textures are unchanged.
- Gameplay, saved data, registry IDs, networking, blockstates, soil rendering, item models, palettes, and interaction code are unchanged.

Final validation is complete. `cleanTest compileJava processResources test` passed 15 suites / 108 tests with no failure, error, or skip; the focused renderer suite passed all 9 tests; the non-writing generator audit verified 182 managed files plus its ledger. A fresh isolated client run correctly exposed that atlas-remapping an unstitched mask produced corrupt planes; direct mask binding then exposed premature buffer switching, which was corrected by completing the base submission before acquiring the mask render type. The corrected isolated dedicated server loaded the persisted fixture and reached `Done`; the corrected client connected, captured all nine required views, completed an in-session resource reload and chunk unload/reload, and exited cleanly. Visual review confirmed aligned shared geometry, tint isolation, empty-mask behavior, deterministic invalid-value fallbacks, dense-garden rendering, distinct Poppy stages 6/7, and persistence across server restart, reload, and chunk cycle. The server then completed clean player/world save and reported all dimensions saved. Existing unrelated repository resource warnings remain outside this correction. **Corrective Milestone 11 — Approved.**

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

Milestone 3 is validated, owner-approved, and committed as `ebba19b92269e5649eb254302e6311fa57f5743b`. Milestone 4 is validated, owner-approved, and committed as `f5ed405484d99695862fe788dd01b8449cef594f`. Milestone 5 is validated, owner-approved, and committed as `f3589b2526ecd51ab5286f6e8735a1527ea74df7`. Milestone 6 is validated, owner-approved, and committed as `bc94e4d10bff03a86f5cf519b4a03a488f3e6e9d`. Milestone 7 is validated, owner-approved, and committed as `66b36a926bac376188fbe79bd2ba73d2d4fb0190`. Milestone 8 is owner-approved, revalidated, and committed as `9e596ac2ece519754f3828745e3e1e921120a935`. Milestone 9 is **Approved**, revalidated, and committed as `7b7a4384686e52db4cafeb26e38ece3f683ac316`; Milestone 10 is **Approved** for the documentation and implementation handoff recorded by the final closeout commit in branch history.

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

## Historical Milestone 7 implementation state (model-resource shape superseded by Corrective Milestone 11)

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

## Milestone 9 gap analysis and test additions

- The seven existing flower suites and the ordinary farming implementation were audited against the required regression, persistence, authorization, multiplayer, and runtime rows before tests were added.
- `FlowerRegressionTest` locks representative annual, perennial, tall, trellis, berry, fruit-tree, root, grain, and grape definitions; quality, hydration, seed-return, soil, weather, care, and community constants; and source isolation between flower and ordinary crop paths.
- `FlowerSaveCompatibilityTest` exercises every registered species, every stage, and every palette colour through save/load and client update tags, including private/community data, administrator/player provenance, UUID presence/absence, blocked growth, Poppy stages 6/7, legacy defaults, unknown species/colour, raw invalid tint, invalid stages, missing fields, corrupt community data, and unsupported versions.
- `FlowerMultiplayerTransactionTest` proves deterministic server-order first-wins planting on one shared plot without duplicate selection, seed consumption, or feedback; equal observer tags; both arrival orders and the exact boundary of the transient ten-tick interaction gate; and position-scoped suppression of a losing post-uproot click.
- `FlowerProtectionBypassTest` exhaustively checks direct mutation reasons for ordinary and protected flowers, Creative/operator bypass, explosion/fluid/piston/system policy, and source routing through the centralized protection service.
- Strict raw-input validation required bounded production corrections. `FlowerInteractionTransactionGate` now grants one state-changing flower transaction per ten-tick contention window without persistence or natural-growth effects; a position/dimension replacement guard covers the same window after uproot replaces the block entity. Raw skinning-knife right-clicks and true server-side Adventure blade clicks route through the existing centralized interaction policy. The singular NeoForge 1.21 `tags/item/skinning_knives.json` resource makes the already-approved tag available at runtime; the inert plural legacy copy was removed from resources, generator ownership, the hash ledger, and tests. No balance value, save schema, recipe, acquisition source, renderer, or Milestone 10 behavior changed.

## Current Milestone 9 validation state

- Baseline before the strict corrections passed the exact combined build and all eleven focused suites: 80 tests, 0 failures/errors/skips. After the corrections, the exact final combined command and all eleven focused suites pass with 82 tests, 0 failures/errors/skips. `python tools/generate_flower_placeholders.py --check` verifies 231 files plus the hash ledger, and `git diff --check` passes with line-ending warnings only.
- Strict runtime work used the ignored project `build/m9-runtime-9e596ac/`, isolated world `run/m9-server/m9-flower-world`, and two real loopback clients: `M9Admin` and `M9Player`. The accounting harness sent ordinary vanilla interaction packets, captured authoritative inventory/durability/Farming-skill/block-entity/loose-item snapshots, and never used an owner world. Temporary harness source and compiled instrumentation are removed after evidence capture; retained ignored logs, text reports, worlds, and screenshots are evidence only.
- The strict harvest race had one winner: one Lily entered the winner's inventory, scissors took one durability, Farming changed `0.0 -> 0.0143`, the loser was unchanged, loose flower count stayed zero, and the flower changed stage 7/quality 60 to stage 1/quality 5. Two independent seed-extraction clients each converted one Poppy into one seed, and a repeated-packet extraction with one source still produced exactly one seed. Extraction never awarded skill.
- Poppy mastery-versus-harvest was proved in both orders inside the contention window. Mastery-first changed stage 6 to 7 and charged only the knife once; harvest was a no-op with no output or skill. Harvest-first produced exactly one Poppy, charged only scissors once, awarded exactly `0.0143`, and reset stage 6 to stage 1; the knife was unchanged. No execution produced both transitions.
- Harvest-versus-Adventure-cutback was proved in both orders. Harvest-first produced exactly one Foxglove, charged scissors once, awarded exactly `0.0143`, and made cutback a no-op. Cutback-first reset stage 7 to stage 1 with no output or skill and made harvest a no-op. The production service charges the winning blade once; the delayed client report could not retain the Adventure sword because the disposable fixture reconciled that slot, so that one delayed inventory field is not treated as independent durability evidence.
- Private uproot produced exactly one restored private FarmingBlock with hydration 4/fertilizer 2, one total hoe durability charge, no output, no loose entity, and no skill. The corrected shared-window community race produced exactly one unprepared `community_farm_block`, one total hoe durability charge, no output, no loose entity, and no skill. An earlier strict attempt exposed a second click against the newly restored community plot; the new position/dimension replacement guard prevents that mixed transaction.
- Care contention consumed one of eight total bone-meal items, changed only nitrogen `0.8 -> 1.0`, and awarded only the winner `0.003575`. The loser and every other state field were unchanged. Planting, cutback, uproot, Poppy mastery, and seed extraction award no Farming skill; care and harvest retain their existing awards.
- Raw protected normal break by `M9Player` left exact flower NBT, inventories, skills, and loose-item count unchanged. Raw protected `BlockItem` use left the flower exact, kept stone at 64, and created no loose item. Survival operator-level-2 and Creative normal breaks each restored the exact underlying private FarmingBlock and created no drop.
- Removing support below a flower leaves the flower floating with exact state; the block has no support-survival callback. The adjacent falling-sand fixture produced one ordinary sand entity and did not mutate the flower. This is the documented applicable neighbor/support result.
- Ordinary and protected flowers both resisted the attempted water replacement because the occupied flower block is inherently non-replaceable, so the intended destructive ordinary-versus-protected fluid distinction cannot be reached by normal placement. Automated event-policy coverage remains valid; the destructive distinction is not falsely marked as live-passed. Powered piston, explosion, and command/system checks retain their prior passing evidence; command replacement changed the selected protected flower to stone and removed its block entity.
- Clients converged on final flower/block state after every strict race; the final resource reload and dense-garden captures passed. A clean `save-all flush`, stop, and same-world restart preserved the stage-1 Foxglove fixture and the protected stage-7 dense-garden Poppy. No flower registration, render, model, texture, or persistence error was found.
- Three strict-live defects were reproduced and corrected: mixed mastery/harvest transitions, post-uproot follow-up interaction on the replacement block, and raw packet routing/tag recognition for skinning-knife mastery plus server-authoritative Adventure detection. Regression tests cover both gate arrival orders/boundary and position-scoped post-uproot suppression. No approved gameplay, protection strength, renderer, persistence schema, species balance, recipe, acquisition source, or Milestone 10 behavior changed.
- Formal profiler benchmarking, packet-level counters, arbitrary other-mod mutation, and world-generation mutation remain unexecuted, as permitted when accurately recorded. There is no unresolved critical production defect. Milestone 9 is **Approved** and committed as `7b7a4384686e52db4cafeb26e38ece3f683ac316`.

## Milestone 10 final closeout

### Final implementation summary

The implemented feature uses one generic `FlowerBlock` and one generic `FlowerBlockEntity` for Poppy, Snowdrop, Lily, Foxglove, Campion, Hyacinth, and Orfluer. A successful server-side seed transaction converts an existing `FarmingBlock`, chooses one value from the species allowlist, and persists the exact 24-bit tint with species, stage, origin, protection, planter UUID, planting provenance, quality, farming growth state, and private/community restoration data. The representation has 16,777,216 possible RGB values and is the Dye-Tub-compatible boundary available on this branch; no Dye Tub implementation exists here to reuse directly.

Flowers reuse the farming hydration/nutrient scales, `FarmingClimateResolver`, altitude evaluation, `CropQualityCalculator`, random-tick cadence, weather hydration, Farming skill source, soil-care arithmetic, quality/provenance conventions, and `CropSeedExtractor`. Mature scissors harvesting yields one mapped flower item and resets the same planting to stage 1. Off-hand or shift-use converts one harvested flower into its mapped seed. Adventure grain-blade cutback resets without output. Approved hoe/shovel uprooting ends the instance and restores private farming soil or the community block. Poppy stops naturally at stage 6 and requires Farming 100 plus the tagged skinning knife for each stage-7 cycle.

Creative/operator-level-2 planting creates protected flowers. Central policy covers care, harvest, cutback, uproot, normal break, replacement, Poppy mastery, explosion, fluid, piston, admin, command, world-generation, and system reasons. Rendering is client-only and performs exactly two ordered cutout submissions: untinted base then exact saved-tint grayscale mask. Save/load, update-tag redaction, corrupt visual fallbacks, live resource reload, restart, and strict two-client contention have been validated. All current art remains generated technical placeholder art under the replacement contract in `FLOWER_ASSET_PLACEHOLDER_MANIFEST.md`.

### Milestone commit history audit

Every hash below was verified with Git on `Farming`. Milestones 0, 1, and the owner decision gate share the documentation baseline commit; history was not rewritten to manufacture separate boundaries.

| Milestone | Commit | Message | Files / scope | Validation state | Owner approval |
|---|---|---|---|---|---|
| 0. Preflight | `41fb51a568fb393e27a276d12cbcbfd26c8730d0` | `added documentation` | Design/playbook intake and repository baseline; same 85-file documentation commit as Milestone 1/gate | Baseline compile/resources/tests passed | Approved |
| 1. Discovery | `41fb51a568fb393e27a276d12cbcbfd26c8730d0` | `added documentation` | Full farming architecture, renderer, persistence, tooling, risk, and test discovery | Read-only discovery completed | Approved |
| Owner decision gate | `41fb51a568fb393e27a276d12cbcbfd26c8730d0` | `added documentation` | B1-B2, A1-A3, G1-G4, AS1, N1, and colour-capacity decisions | No blockers remained | Approved |
| 2. Data contracts | `0a600a9d2b86ac839e4ff03b1dabf445eccec7bf` | `feat(flowers): add species and colour data contracts` | 33 files; domain values, registries, shared growth profile, policy, persistence contracts, tests | 13 tests and build passed | Approved |
| 3. Placeholders | `ebba19b92269e5649eb254302e6311fa57f5743b` | `content(flowers): add placeholder items seeds models and masks` | 238 files; 14 items, 49 stages, 98 PNGs, generator, ledger, manifest | 18 tests, generator, client resource smoke passed | Approved |
| 4. Lifecycle | `f5ed405484d99695862fe788dd01b8449cef594f` | `feat(flowers): add atomic planting and persistent flower state` | 24 files; generic block/entity, atomic conversion, NBT and sync | 27 tests plus server/client smoke passed | Approved |
| 5. Growth | `f3589b2526ecd51ab5286f6e8735a1527ea74df7` | `feat(flowers): integrate perennial growth with farming simulation` | 16 files; shared evaluator adapter, timing, random tick, reset, diagnostics | 38 tests plus generator/server/client checks passed | Approved |
| 6. Interactions | `bc94e4d10bff03a86f5cf519b4a03a488f3e6e9d` | `feat(flowers): add protected interactions and poppy mastery stage` | 30 files; protection, care, harvest, extraction, cutback, uproot, Poppy, knife | 48 tests plus server/client checks passed | Approved |
| 7. Rendering | `66b36a926bac376188fbe79bd2ba73d2d4fb0190` | `feat(flowers): render multi-plane flowers with dye masks` | 15 files; two-pass BER, resolver, 98 registrations, tint and fallback corrections | 57 tests, reload/restart/chunk/dense visual review passed | Approved |
| 8. Species content | `9e596ac2ece519754f3828745e3e1e921120a935` | `content(flowers): wire initial species palettes and growth profiles` | 6 files; localized feedback and comprehensive species/content coverage | 64 tests plus server/client/generator checks passed | Approved |
| 9. QA | `7b7a4384686e52db4cafeb26e38ece3f683ac316` | `test(flowers): complete regression and multiplayer coverage` | 18 files; four QA suites, ten-tick transaction gate, post-uproot guard, authoritative raw routing, singular tag path, QA docs | 11 suites, 82 tests, strict two-client/restart/reload/dense evidence passed | Approved |
| 10. Closeout | See final closeout commit in branch history | `docs(flowers): close implementation and asset handoff` | Documentation, invariant/species/test/repository audits, player/admin and asset handoff | Final validation recorded below | Approved |

### Final species-content table

All values remain **Initial tuning**, not final production balance. `N/P/K/OM` uses the same approved midpoint/tolerance for the four shared farming nutrients. All harvested mappings produce one item on mature scissors harvest and all seed mappings use one harvested item to produce one seed through `CropSeedExtractor`.

| Species ID | Flower item | Seed item | Natural / absolute max | Base ticks | Preferred / tolerated / unsuitable climates | Ideal / tolerated altitude | Hydration ideal / tolerated | N/P/K/OM ideal / tolerated | Palette / fallback | Harvested-item mapping | Seed-extraction mapping | Stage-model coverage | Art status | Balance |
|---|---|---|---:|---:|---|---|---|---|---|---|---|---|---|---|
| `britannia_mod:poppy` | `britannia_mod:poppy` | `britannia_mod:poppy_seeds` | 6 / 7 | 5 | `TEMPERATE` / `ARID` / `ICE,FIRE,WETLAND,TROPICAL,MAGICAL,UNDERGROUND` | 45-125 / 20-170 | .30-.55 / .15-.70 | .35-.65 / .15-.80 | 8 / `scarlet` | Poppy | Poppy -> Poppy Seeds | Stages 1-7; stage 7 manual | Placeholder; not approved | Initial tuning |
| `britannia_mod:snowdrop` | `britannia_mod:snowdrop` | `britannia_mod:snowdrop_seeds` | 7 / 7 | 7 | `ICE` / `TEMPERATE,WETLAND` / `FIRE,TROPICAL,ARID,MAGICAL,UNDERGROUND` | 55-155 / 30-220 | .50-.75 / .35-.90 | .50-.80 / .30-.95 | 4 / `snow_white` | Snowdrop | Snowdrop -> Snowdrop Seeds | Stages 1-7 | Placeholder; not approved | Initial tuning |
| `britannia_mod:lily` | `britannia_mod:lily` | `britannia_mod:lily_seeds` | 7 / 7 | 8 | `TEMPERATE` / `ICE` / `FIRE,WETLAND,TROPICAL,ARID,MAGICAL,UNDERGROUND` | 45-150 / 20-200 | .45-.70 / .30-.85 | .60-.85 / .40-1.00 | 10 / `snow_white` | Lily | Lily -> Lily Seeds | Stages 1-7 | Placeholder; not approved | Initial tuning |
| `britannia_mod:foxglove` | `britannia_mod:foxglove` | `britannia_mod:foxglove_seeds` | 7 / 7 | 9 | `TEMPERATE` / `WETLAND` / `ICE,FIRE,TROPICAL,ARID,MAGICAL,UNDERGROUND` | 70-180 / 40-240 | .50-.75 / .35-.90 | .50-.80 / .25-.95 | 9 / `violet` | Foxglove | Foxglove -> Foxglove Seeds | Stages 1-7 | Placeholder; not approved | Initial tuning |
| `britannia_mod:campion` | `britannia_mod:campion` | `britannia_mod:campion_seeds` | 7 / 7 | 6 | `TEMPERATE` / `WETLAND` / `ICE,FIRE,TROPICAL,ARID,MAGICAL,UNDERGROUND` | 45-150 / 20-210 | .45-.70 / .25-.85 | .40-.70 / .20-.90 | 6 / `rose_pink` | Campion | Campion -> Campion Seeds | Stages 1-7 | Placeholder; not approved | Initial tuning |
| `britannia_mod:hyacinth` | `britannia_mod:hyacinth` | `britannia_mod:hyacinth_seeds` | 7 / 7 | 7 | `TEMPERATE` / `ICE` / `FIRE,WETLAND,TROPICAL,ARID,MAGICAL,UNDERGROUND` | 40-120 / 20-170 | .35-.60 / .20-.75 | .50-.75 / .30-.90 | 9 / `hyacinth_blue` | Hyacinth | Hyacinth -> Hyacinth Seeds | Stages 1-7 | Placeholder; not approved | Initial tuning |
| `britannia_mod:orfluer` | `britannia_mod:orfluer` | `britannia_mod:orfluer_seeds` | 7 / 7 | 9 | `MAGICAL,ICE` / `TEMPERATE` / `FIRE,WETLAND,TROPICAL,ARID,UNDERGROUND` | 110-220 / 70-280 | .40-.65 / .25-.80 | .55-.85 / .35-1.00 | 9 / `lavender` | Orfluer | Orfluer -> Orfluer Seeds | Stages 1-7 | Placeholder; not approved | Initial tuning |

### Player and administrator guide

#### Planting and growth

- Prepare valid private or community farming soil, then use a registered flower seed. Tilling, watering, or fertilizing alone never creates a flower.
- Successful planting stores species and one server-selected colour. Creative or operator-level-2 planting creates a protected flower; soil prepared earlier by an administrator does not transfer protection to an ordinary planter.
- Flowers reuse farming hydration, nutrients, climate, altitude, weather, quality, and growth cadence. Poppy naturally stops at stage 6; the other initial species naturally reach stage 7.
- A planted flower is persistent. Growth, care, harvest reset, cutback, save/restart, client reconnect, and resource reload do not change its saved colour.

#### Harvest, seed recovery, cutback, and uprooting

- Use the registered scissors on a mature flower to receive exactly one mapped flower item. The same planted flower resets to stage 1 with unchanged species and colour.
- Use the existing off-hand or shift-use harvested-crop interaction on one harvested flower to obtain its one mapped seed. Flower colour does not select a different seed.
- In Adventure mode, attack an eligible unprotected flower with an item in `britannia_mod:grain_harvest_blades`. Successful cutback resets to stage 1, costs one durability, and produces no flower or seed.
- Use `britannia_mod:farming_hoe` or an item in `britannia_mod:root_crop_shovels` to uproot an authorized flower. Private soil restores to `FarmingBlock`; community soil restores to the unprepared community block. Uprooting ends the instance, and replanting performs a new colour roll.
- For Poppy mastery, use an item in `britannia_mod:skinning_knives` on an authorized stage-6 Poppy with Farming skill 100 or greater. Success advances to stage 7 and costs one durability. After any reset, stage 7 must be earned again.

#### Protection and administration

- Creative- or operator-level-2-planted flowers are protected. Ordinary actors cannot care for, harvest, cut back, uproot, normally break, replace, or master them, and denied actions produce no consumption, durability, drop, skill, or success feedback.
- Protected explosions and exposed fluid mutation hooks are denied; all flowers block pistons. Authorized Creative/operator removal and explicit command/system mutation remain possible.
- The bounded `/farming debug` flower diagnostics and bounded fallback warnings are approved operational aids. They do not expose planter UUID to clients and are not temporary test instrumentation.

#### Current acquisition limitation

Flower items and seeds are currently available through Creative/admin access and flower renewal paths. The skinning knife is currently Creative/admin only. No survival recipe, natural generation, merchant, economy, or Rails source has been added.

### Final design-invariant audit

The tables below use the required columns. “Automated evidence” identifies the focused suite(s); runtime evidence is not inflated beyond the recorded Milestone 7/9 work.

#### FarmingBlock conversion and identity

| Invariant | Implementation path | Automated evidence | Runtime/manual evidence | Final result | Known limitation |
|---|---|---|---|---|---|
| Tilling never creates `FlowerBlock`; fertilized dirt creates only `FarmingBlock`; watering/fertilizer never converts it | `FertilizedDirtItem`, `FarmingHoeItem`, `FarmingBlock.useItemOn` | `FlowerRegressionTest`, `FlowerLifecycleTest` | Existing farming server/client smoke | Pass | Contract/live flower planting split is intentional |
| Only a valid mapped flower seed converts `FarmingBlock`; invalid planting is atomic | `FarmingBlock.useItemOn` -> `FlowerPlantingService` | Lifecycle rollback injection and multiplayer first-wins tests | Strict shared-state evidence; ordinary planting contention remains deterministic harness coverage | Pass | No human click fixture retained |
| Community plots are supported with restoration metadata | `FlowerSoilSnapshot`, `FlowerCommunityRestoration`, planting/restoration services | Lifecycle/save/interaction/multiplayer tests | Strict community uproot restored exactly once | Pass | No unique community plot ID exists in the current farming system |
| One species ID, exact tint, origin, protection, server planter UUID, provenance, quality, farming state, and community restoration state persist | `FlowerPersistentState`, `FlowerBlockEntity`, soil/provenance/quality value objects | Domain/lifecycle/save compatibility tests | Clean disk-world restart preserved representative private/protected states | Pass | Unknown/corrupt values use bounded fallback policy |
| Clients receive approved synchronized fields only; planter UUID stays server-side | `FlowerBlockEntity.clientStateTag`, update tag/packet | Lifecycle/save/multiplayer UUID-redaction tests | Two clients converged after every strict action | Pass | No packet-byte capture/profile |

#### Colour selection and persistence

| Invariant | Implementation path | Automated evidence | Runtime/manual evidence | Final result | Known limitation |
|---|---|---|---|---|---|
| Colour is selected exactly once after successful server validation | `FlowerPlantingService`, `WeightedFlowerColorSelector`, `FlowerColorLifecycle` | Domain/lifecycle/multiplayer planting tests | Disk fixture retained saved values across restart | Pass | Planting race uses deterministic server-order harness |
| Species palettes are explicit allowlists with at least two entries and no small enum ceiling | `FlowerRegistry`, `FlowerDefinitionValidator`, 24-bit `FlowerColor` | Domain/species tests cover 8/4/10/9/6/9/9 palettes and 16,777,216-value capacity | Multiple same-species colours visually reviewed | Pass | Current palettes are initial tuning |
| Load, growth, reset, rendering, and resource reload never select colour | Persistent state, growth evaluator, reset methods, renderer plan/reload handler | Save/growth/rendering source and behavior tests | Restart, reconnect, chunk cycle, and live reload preserved colour | Pass | None |
| Existing flowers are never recoloured | All lifecycle transitions preserve `FlowerColor` | Growth/interaction/save/multiplayer suites | Two-client convergence and restart evidence | Pass | Future nutrient bias remains planting-only |
| Unknown palette tint and invalid raw tint are preserved; corrupt presentation fallback never rewrites NBT | Persistence-only raw tint path and `visualColor`/renderer fallback | Save compatibility and rendering tests | Corrupt `ColorTint:-1` survived live restart and rendered fallback | Pass | Bounded warning, not automatic migration |

#### Persistent regrowth and removal

| Invariant | Implementation path | Automated evidence | Runtime/manual evidence | Final result | Known limitation |
|---|---|---|---|---|---|
| Mature shearing yields one flower and resets the same planting to stage 1 | `FlowerInteractionService.harvest`, `FlowerBlockEntity.harvestAndReset` | Interaction/multiplayer tests | Strict Lily/Poppy/Foxglove races produced one winning output | Pass | None |
| Sword cutback resets to stage 1 without item or seed | `FlowerInteractionHandler.onLeftClickBlock`, `cutBack`, `resetToStageOne` | Interaction/protection/multiplayer tests | Strict cutback-first/harvest-first races | Pass | Delayed fixture could not independently retain one sword-slot durability reading; production path is test-covered |
| Harvest/cutback preserve identity and do not restore community soil | Central reset mutates stage/progress/timing only | Growth/interaction/save tests | Clients converged on reset state | Pass | None |
| Reset never produces a seed; seed recovery consumes a harvested item through existing extraction | `HarvestedFlowerItem`, `CropSeedExtractor` | Interaction/species/regression tests | Strict repeated-packet extraction produced one seed from one source | Pass | No survival acquisition source |
| True uprooting ends the flower and restores private/community substrate once | `restoreUnderlyingSoil`, replacement guard | Interaction/multiplayer/save tests | Strict private/community races: one restoration, one durability, no output/skill | Pass | Position guard intentionally suppresses same-position use only for ten ticks |

#### Protection

| Invariant | Implementation path | Automated evidence | Runtime/manual evidence | Final result | Known limitation |
|---|---|---|---|---|---|
| Creative and operator-level-2 planting is protected; ordinary planting is unprotected; protection derives from planter, not prepared soil | `FlowerProtectionService`, planting access/origin capture | Domain/lifecycle/protection tests | Operator/Creative authorization exercised in strict runtime | Pass | No external permissions-plugin integration |
| Unauthorized care, harvest, cutback, uproot, Poppy mastery, normal break, and BlockItem replacement are denied | Central mutation reasons in service/handler | Interaction and exhaustive bypass tests | Raw protected break and stone placement preserved exact state | Pass | None for covered paths |
| Protected explosion removal and exposed fluid mutation are denied; all flowers block pistons | Explosion/fluid event hooks; `FlowerBlock.getPistonPushReaction` | Bypass/interaction source-policy tests | Explosion/piston passed; normal water placement could not replace either nonreplaceable flower | Pass for exposed engine paths | Ordinary/protected destructive fluid distinction is unreachable through normal placement |
| Command/system mutation remains possible | `FlowerMutationReason.ADMIN_COMMAND`, `WORLD_GENERATION`, `SYSTEM_MUTATION` | Protection policy suite | `/setblock` replaced a protected flower | Pass | Arbitrary other-mod mutation cannot be universally intercepted |
| Denied actions cause no consumption, durability, drop, skill, or success feedback | Service validates authorization/state before all effects | Interaction/protection/multiplayer accounting | Strict inventories, loose items, durability, and skills stayed exact | Pass | No packet counter instrumentation |

#### Poppy stage 7

| Invariant | Implementation path | Automated evidence | Runtime/manual evidence | Final result | Known limitation |
|---|---|---|---|---|---|
| Natural Poppy growth stops at 6; stage 7 requires exact stage 6, Farming >=100, `britannia_mod:skinning_knives`, and authorization | Registry profile, growth evaluator, interaction service, singular tag | Domain/growth/interaction/species/protection tests | Strict mastery/harvest both orders | Pass | Knife acquisition is Creative/admin only |
| One durability is charged only after successful mastery | `advancePoppy`, `damageAfterSuccess` | Interaction/multiplayer tests | Mastery-first knife damage 0->1; loser unchanged | Pass | None |
| Natural growth never creates 7; saved 7 persists; harvest/cutback reset to 1 and natural regrowth stops at 6 again | Growth evaluator, persistent state, reset path | Growth/save/interaction/rendering tests | Stage-7 protected Poppy survived restart; reset races converged | Pass | No permanent mastery flag by design |

#### Shared farming simulation

| Invariant | Implementation path | Automated evidence | Runtime/manual evidence | Final result | Known limitation |
|---|---|---|---|---|---|
| Flowers reuse hydration/nutrient scales, climate resolver, altitude evaluator, `CropQualityCalculator`, and growth-multiplier semantics | `FarmingGrowthProfile`, `FlowerGrowthEvaluator`, `FarmingClimateResolver` | Domain/growth/species/regression tests | Practical mixed-condition/dense fixtures | Pass | Coarse eight-value climate enum loses fine habitat distinctions |
| Flowers reuse random-tick cadence, hydration decay, and weather hydration | `FlowerBlock.randomTick`, shared `FarmingBlock` helpers | Growth/regression tests | Dedicated worlds remained stable | Pass | No prolonged weather benchmark |
| Flowers reuse Farming skill source, soil care, quality/provenance, and crop item-to-seed extraction | `SkillManager`/`FarmingSkill`, `FarmingSoilCare`, `CropQualityCalculator`, `FruitProvenance`, `CropSeedExtractor` | Interaction/species/regression/accounting tests | Exact harvest/care skill totals and seed extraction passed | Pass | No separate flower economy/value layer |
| No duplicate flower climate, nutrient, hydration, quality, or skill engine exists | Adapter/profile architecture and source isolation | Growth/regression source-contract tests | Repository audit found no parallel engine | Pass | Existing crop-only nutrient event duplication predates flowers |

#### Rendering and exact texture contract

| Invariant | Implementation path | Automated evidence | Runtime/manual evidence | Final result | Known limitation |
|---|---|---|---|---|---|
| One generic renderer/block/entity; model chosen by synchronized species/stage | `FlowerBlockEntityRenderer`, `FlowerVisualModels`, `FlowerBlock`, `FlowerBlockEntity` | Rendering/lifecycle tests | Seven-by-seven matrix and two clients | Pass | None |
| One canonical baked model renders first with its untinted base texture; the same geometry renders second with the dye mask and exact saved tint; every shared face has `tintindex: 0` | Renderer, canonical stage models, and generated shared parent | Asset/rendering/save tests | Corrective light/dark, late-stage, early-stage/empty-mask, matrix, and dense-garden captures passed | Pass | Continuous-motion flicker was not formally recorded |
| Invalid tint/species/stage use deterministic visual fallbacks without persistent mutation | Render-plan resolver and `visualColor` | Rendering/save compatibility tests | Corrupt/unknown fixtures survived reload/restart | Pass | Bounded warnings are retained intentionally |
| Rendering never mutates persistent state; dedicated server never loads client renderer classes; reload is safe | Client-only subscriber, reload-managed model lookups | Rendering/protection source checks | Dedicated starts and two live reloads passed | Pass | Known unrelated client-only mixin warning remains |
| Exact content is 7 species, 49 canonical stage models, 49 base textures, 49 dye masks, and 98 in-world PNGs at 128x128 | Generator, ledger, manifest, resolver | Asset/rendering/species tests and generator check | Corrective client resource load and full-matrix visual review passed | Pass | Artwork is placeholder, not final |
| Each pair has identical dimensions/UV/padding/pixel alignment; visible alpha is disjoint; masks are grayscale/alpha-selected | Generator image/model contract | `FlowerAssetContractTest` | No halos, seams, checkerboarding, or static Z-fighting observed | Pass | Artist must preserve contract |
| No third in-world texture/pass, per-colour texture/block/model, baked final mask colour, or invalid mask channel exists | Two-pass resolver/generator contract | Asset/rendering/species repository tests | Runtime logs/captures showed two aligned passes | Pass | None |

#### Multiplayer and transaction safety

| Invariant | Implementation path | Automated evidence | Runtime/manual evidence | Final result | Known limitation |
|---|---|---|---|---|---|
| Two clients receive the same species, colour, and stage and converge after tested actions | Update tags/packets and server-authoritative transitions | Lifecycle/save/multiplayer tests | Two distinct loopback clients converged after every strict fixture | Pass | No packet-byte capture |
| Planting contention has one winner | Atomic `FlowerPlantingService` | Deterministic first-wins transaction test | Not repeated as a raw two-client fixture | Pass | Deterministic harness, not network scheduling |
| Harvest, cutback/harvest, mastery/harvest, care, and uprooting contention each commit one result | Per-entity ten-tick gate plus post-uproot position/dimension guard | Interaction and multiplayer tests, including both orders and exact tick-10 boundary | Strict two-client raw packets covered all listed non-plant races | Pass | No formal packet counter profile |
| No duplicate item, seed, durability, or skill occurs | Effects occur only after committed server mutation | Regression/interaction/multiplayer accounting | Exact counts: one output/seed/durability/award where applicable, losers zero | Pass | One delayed cutback client slot limitation is documented above |
| Transaction gates expire exactly and do not persist, block natural growth, alter saved identity, or lock later interactions | Transient BE field and static replacement map; no NBT/update-tag fields | Exact 10-tick tests, save/source isolation | Later interactions and restart remained available | Pass | Replacement guard blocks all same-position clicks inside its deliberately short window because stale packets are not identifiable |

### Deferred work register

The following items are wholly deferred and are not partially implemented: final owner-approved species artwork; survival flower-seed acquisition; survival skinning-knife acquisition; recipes; natural flower generation; merchant/economy integration; Rails integration; flower pricing; formal packet-level profiling; formal performance benchmarking; arbitrary other-mod mutation interoperability; a world-generation mutation fixture; flower breeding/genetics; cross-pollination; quality-based yield/value expansion; biome or seasonal influence; admin colour-selection tools; rare-colour achievements; flower-arranging/decorative crafting; additional species; and nutrient-biased colour weighting at planting.

**Future nutrient logic may bias colour weights only when a new seed is successfully planted.**

**It must never recolour an existing flower.**

### Known limitations

- All 98 in-world flower textures and all 15 item textures are technical placeholders; owner-approved artwork count is 0 and 113 texture replacements remain.
- Survival flower/seed acquisition is incomplete. Renewal works only after Creative/admin access supplies initial content.
- The skinning knife has no survival recipe, merchant, economy, or Rails source.
- Formal packet-level profiling and formal profiler/frame-time benchmarking were not performed.
- Arbitrary other-mod block mutation cannot be universally intercepted; only exposed/known engine paths are governed.
- Normal water placement cannot demonstrate an ordinary/protected destructive distinction because occupied `FlowerBlock` instances are inherently nonreplaceable.
- Flowers remain floating if support below them is removed because no support-survival callback is implemented.
- DOCX visual rendering was unavailable because LibreOffice/`soffice` is not installed; all 224 paragraphs and 43 tables were structurally reviewed.
- Existing unrelated optional configuration, legacy asset, GeckoLib, and client-only mixin warnings remain outside flower scope.

Placeholder artwork is a limitation of presentation only; it does not invalidate the validated gameplay, persistence, protection, rendering contract, or transaction behavior.

### Final validation and repository audit

- Full gate: `.\gradlew.bat cleanTest compileJava processResources test --console=plain --no-configuration-cache` passed on 2026-08-01.
- Focused gate: all eleven flower suites passed with 82 tests, 0 failures, 0 errors, and 0 skipped.
- Placeholder audit: `python tools/generate_flower_placeholders.py --check` verified 231 generated files plus the hash ledger. The test suite also enforces the artist warnings and the singular NeoForge item-tag path.
- Dedicated server: an ignored run under `build/m10-runtime/server` loaded Britannia 0.1.8, created/reopened only `server-world`, reached `Done (3.799s)`, accepted a loopback RCON stop, and logged `Stopping server`, `Saving players`, and `Saving worlds`. No flower-specific registration, classloading, persistence, model, texture, or skinning-knife failure appeared.
- Client: an ignored run under `build/m10-runtime/client` reloaded `mod/britannia_mod`, initialized OpenAL and the sound engine, and created the `8192x4096x4` block atlas. Targeted review found no flower, Orfluer, or skinning-knife warning/error. Pre-existing non-flower missing-model/texture warnings remain outside scope.
- Representative planted state was not recreated in Milestone 10 because no production behavior or visual asset changed after the owner-approved Milestone 9 clean save/restart and two-client fixture. That evidence remains authoritative and is cross-referenced in the matrix.
- Repository scope: Milestone 10 changes only four flower Markdown closeout records. The generator-owned manifest, generator, hash ledger, production Java, tests, models, PNGs, localization, tags, recipes, world generation, economy, Gradle configuration, and runtime files are unchanged.
- History boundary: Milestone 10 began from `7b7a4384686e52db4cafeb26e38ece3f683ac316`, nine commits ahead of `origin/Farming`. Its commit is intentionally referenced from branch history rather than self-referenced or amended. Unrelated `.claude/` remains untouched.
- Runtime artifacts: the Milestone 10 server/client working directories and temporary init script were ignored disposable evidence and are removed after validation. Existing tracked `logs/` files predate this closeout and are not modified by the Milestone 10 diff.
- Source audit: all intended flower paths are referenced or intentionally policy/data-facing. `FlowerRemovalReason` remains a reserved semantic enum from Milestone 2 rather than temporary instrumentation; no test harness, debug branch, fake player, temporary command, or compiled instrumentation is retained in source.
- `git diff --check` reports only the repository's existing CRLF conversion warnings and no whitespace errors. Duplicate-case documentation and plural `tags/items/skinning_knives.json` audits are clean.

### Final owner approval and feature disposition

- **Milestone 10 - Approved.** The owner approved the Milestone 10 documentation and implementation handoff.
- The Persistent Flower System implementation milestones are complete on the `Farming` branch.
- Placeholder artwork remains non-final. Zero placeholder textures are approved as final artwork; final-art replacement is a separate owner-approval process.
- Survival flower/seed acquisition and survival skinning-knife acquisition remain deferred.
- Recipes, natural generation, merchants, economy, and Rails integration remain deferred.
- This approval includes no merge, push, pull request, release tag, deployment, or production-readiness claim.

Persistent Flower System implementation: **Approved on the Farming branch.**

Final artwork: **Not approved and still pending replacement.**

Deferred acquisition, economy, generation, and expansion features: **Not implemented.**

Milestone 10 status: **Approved.** Commit message: `docs(flowers): close implementation and asset handoff`.

Corrective Milestone 11 status: **Corrective Milestone 11 — Approved.** Commit message: `fix(flowers): use canonical models with two texture passes`. This approval authorizes only the isolated local commit; it does not authorize merge, push, tag, release, or deployment.

## Illustrator Asset Pipeline - Milestone 19 technical integration

Milestone 19 technical integration is **complete and committed locally** on the case-sensitive `Farming` branch as `2e5ff48eba79c8f547ef1fe2379b1d89ec9edcc3`. Milestone 20 validation was subsequently authorized and completed; its documentation-only closeout remains uncommitted for owner review.

### Authoritative Illustrator source

- Source: `C:\projects\britannia\raw fiels\flowers.ai`
- Current SHA-256: `00E0C2F5CE56422361FC88C7F8F0BAD20A337B1FD85F33ED4252B6680F22A223`
- Current size and timestamp: 50,391,256 bytes; `2026-08-05T01:11:11Z`
- Pre-correction backup: `C:\projects\britannia\raw fiels\codex_backups\flowers_pre_base_transparency_20260804_181043.ai`
- Backup SHA-256: `6E3DD39B95DA983B317D5F379F33F4BBD08A2E7FF72B7B2FD874DF4D3CFD4048`

The owner authorized source-authoritative base correction. On all 33 authored-mask stages, the prior embedded base is retained hidden as `base_texture_original_nonexport`; a corrected embedded 128 x 128 `base_texture` occupies exact Artboard 1 bounds and is transparent wherever `dye_mask` selects. The 16 base-only stages remain unchanged. Post-save native inventory found 49 export bases, 33 white masks, 33 hidden originals, zero linked assets, zero hidden/locked stage layers, and the approved 7-by-7 species/stage structure.

Illustrator's scripted save left the AI as untagged RGB. The corrected pixels preserve the previously exported sRGB numeric values, and the runtime PNGs contain no ICC profile, matching the existing resource convention. This color-profile limitation is recorded rather than silently claiming that the current AI carries an embedded sRGB tag.

### Runtime asset result

- 49 base PNGs exported from named Illustrator objects.
- 33 authored white masks exported from named Illustrator groups.
- 16 fully transparent mask placeholders retained where no source `dye_mask` exists.
- Exact authored-mask matrix: Hyacinth 3-7; Lily 3-7; Campion 3-6; Poppy 3-6; Orfluer 3-7; Foxglove 2-6; Snowdrop 3-7.
- All 98 production PNGs are 128 x 128, hash-verified after copy, and referenced by the existing canonical model paths.
- Base/mask alpha overlap is zero across all 49 pairs.
- All visible authored-mask RGB is exactly `255,255,255`.
- The fresh Illustrator export matches all 33 pre-embed corrected candidates in alpha and visible RGB.
- 85 tracked PNGs differ from the generated-placeholder baseline: 49 bases, 33 authored masks, and the Campion/Poppy/Foxglove stage-7 masks corrected to approved transparent placeholders.
- 49 canonical model JSONs remain unchanged; no pass-specific model JSONs were added.

`FlowerAssetContractTest` now encodes the approved per-species mask matrix instead of assuming every stage after 2 has a mask. It also requires exact white visible-mask pixels, preserves the zero-overlap rule, and treats the 98 integrated textures as Illustrator-owned outputs outside the historical placeholder hash ledger. Unchanged generated JSON ledger checks normalize Windows line endings.

### Validation and scope boundary

- Native Illustrator re-export: 49 bases plus 33 masks; source hash unchanged before/after.
- Pixel audit: 49 stages, 98 PNGs, 33 authored masks, 16 transparent placeholders, 0 errors, 0 overlap pixels.
- Visual QA: base, mask, and combined 7-by-7 contact sheets inspected; growth sequences, padding, and mask alignment are coherent.
- Focused automated gate: `.\gradlew.bat test --tests "com.seggellion.britannia_mod.farming.FlowerAssetContractTest" --console=plain --no-configuration-cache --no-daemon --max-workers=1` passed in 52 seconds with 5 tests and 0 failures.
- No full build, clean, gameplay regression suite, client launch, dedicated server, or Milestone 20 visual-review fixture was run during Milestone 19.
- Milestone 19 was later staged and committed locally as `2e5ff48`; no fetch, pull, merge, rebase, reset, push, GitHub action, release, or deployment occurred.

Milestone 19 status: **Technical integration complete and committed locally.**

## Illustrator Asset Pipeline - Milestone 20 validation closeout

Milestone 20 validated the exact committed M19 asset set without changing implementation code, models, textures, or the authoritative Illustrator source.

### Structural, source, and pixel validation

- Exact graph: 49 canonical flower models, 49 base textures, 49 dye masks, no pass-specific models, no stale resources, and no orphans.
- Fresh native source export: 49 bases and 33 masks, with 0 production byte mismatches and unchanged source SHA-256 `00E0C2F5CE56422361FC88C7F8F0BAD20A337B1FD85F33ED4252B6680F22A223`.
- Source correction proof: 58,516 selected pixels transparent, 0 changed alpha or visible RGB outside mask regions, 0 base/mask overlap.
- Runtime pixels: 98/98 at 128 x 128, 33 white authored masks, 16 transparent placeholders, coherent padding and growth sequences.

### Live client/server acceptance

- An ignored, independent `--no-hardlinks` local clone of `2e5ff48` hosted a disposable dedicated-server fixture with all seven species and stages 1-7.
- The fixture validated 49/49 server identities and saved colours before shutdown and 49/49 after clean save/restart without reconstruction.
- Client A captured front, side, elevated, Poppy stage-7, and post-resource-reload views. Client B overlapped Client A on the same server fixture and captured the same front state; the overlap ran from 20:34:38 to 20:34:48 local time.
- Client A/B fixture crops had luminance/edge structure correlations of `0.9994` / `0.9997`. Before/after resource reload correlations were `0.9790` / `0.9887`; exact bytes varied with lighting/cloud rendering while geometry, species/stages, and tint placement remained stable.
- A separate restart client rendered the persisted fixture and saved colours. No missing flower model, cross-species mapping, mirrored art, clipping defect, halo, seam, tint leakage, UV shift, or static Z-fighting was observed.

### Regression and limitations

- Full automated gate: `15` suites / `108` tests, `0` failures, `0` errors, `0` skipped; `BUILD SUCCESSFUL in 27s`.
- Dedicated server started twice and shut down twice with all dimensions saved.
- Static screenshots do not measure continuous-motion flicker or GPU performance. Pre-existing unrelated model/texture warnings, a dedicated-server client-only mixin warning, and unavailable localhost service responses were observed and left out of scope.
- No clean, production implementation, asset edit, source edit, stage, commit, fetch, push, GitHub action, release, or deployment occurred in Milestone 20.

Milestone 20 status: **Validation pass; awaiting owner approval to commit these documentation-only closeout records.**

## Post-Milestone 20 detail-preserving dye tint correction

The fully opaque/alpha-disjoint result made selected flower regions read as flat colour. The correction restores the source base artwork beneath each mask and blends the saved dye colour over it at 50% opacity. A highlight-only adjustment layer was evaluated but not selected: the current white masks encode coverage only, so highlight-sensitive recolouring would require newly authored grayscale intensity masks or a custom shader and a broader asset contract.

### Implementation and source result

- `FlowerVisualModels` defines `DYE_MASK_OPACITY = 0.5F` and carries that alpha in each resolved mask tint.
- `FlowerBlockEntityRenderer` keeps the base on `RenderType.cutout()`, binds the mask with `RenderType.entityTranslucent(...)`, and scales submitted vertex alpha by the resolved mask opacity.
- The renderer still uses one canonical baked model, two textures, two ordered passes, one transform, and the same saved RGB tint.
- The authoritative Illustrator source now uses the detailed originals as the 33 mask-bearing `base_texture` objects. The prior alpha-disjoint rasters are hidden as `base_texture_alpha_disjoint_nonexport` for reversibility.
- Source SHA-256 is `1B5407855D448B8385CC6D067858A347DF3C0B0993B390382A36EE1D42C0A9EE`; the immediately prior source is backed up at `flowers_pre_detail_preserving_tint_20260804_205234.ai` with SHA-256 `00E0C2F5CE56422361FC88C7F8F0BAD20A337B1FD85F33ED4252B6680F22A223`.
- Exactly 33 production base PNGs changed. Sixteen base-only PNGs and all 33 authored mask PNGs remained byte-identical.

### Validation

- Pixel audit: 49 bases and 49 masks at 128 x 128; 33 exact-white authored masks; 16 transparent placeholders; 58,516 mask pixels with visible detailed base beneath them; every authored stage has non-flat underlying colour variation.
- Focused automated gate: `FlowerRenderingTest` and `FlowerAssetContractTest`, 14 tests, 0 failures/errors.
- Full automated gate: 15 suites / 108 tests, 0 failures/errors/skips; `BUILD SUCCESSFUL in 44s`.
- Isolated live fixture: all seven species and stages rendered after the change; front, side, elevated, Poppy, and post-reload captures completed. Elevated/side inspection showed preserved petal and leaf shading with no mask shift, seam, halo, clipping defect, static Z-fighting, or flower renderer exception.
- The resource reload completed and the isolated server saved all dimensions on shutdown. Historical unrelated resource warnings and unavailable local-service responses remained out of scope.

Correction status: **Implemented and validated locally; no commit, fetch, push, GitHub action, release, or deployment performed.**
