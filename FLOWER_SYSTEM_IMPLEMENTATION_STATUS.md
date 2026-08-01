# Persistent Flower System Implementation Status

Repository: `C:/projects/britannia/mod/Britannia_Mod`  
Branch: `Farming`  
Current HEAD: `f3589b2526ecd51ab5286f6e8735a1527ea74df7`
Working state: Milestone 6 is owner-approved and ready for its isolated commit; Milestone 7 is authorized and in progress; unrelated `.claude/` is preserved.

| Milestone | Status | HEAD/commit | Tests | Owner approval | Notes |
|---|---|---|---|---|---|
| 0. Preflight | Approved | `41fb51a` | Baseline `compileJava processResources test` passed during discovery | Approved | Root, exact branch, HEAD, guidance, and baseline recorded. |
| 1. Discovery | Approved | `41fb51a` | Read-only discovery and baseline validation complete | Approved | Discovery report is present and owner questions were answered. |
| Owner decision gate | Approved | `41fb51a` | N/A | Approved | B1, B2, A1-A3, G1-G4, AS1, N1, and color-capacity direction recorded. |
| 2. Data contracts | Approved | `0a600a9d2b86ac839e4ff03b1dabf445eccec7bf` | Final `compileJava processResources test` passed; 13 tests, 0 failures | Approved | Committed as `feat(flowers): add species and colour data contracts`. |
| 3. Placeholders | Approved | `ebba19b92269e5649eb254302e6311fa57f5743b` | Full build/test passed; 18 tests, 0 failures; focused domain and asset tests passed; generator check passed; bounded client resource-load smoke passed for flower assets | Approved | Committed as `content(flowers): add placeholder items seeds models and masks`. |
| 4. Lifecycle | Approved | `f5ed405484d99695862fe788dd01b8449cef594f` | Full automated suite plus isolated server/client smoke passed | Approved | Committed as `feat(flowers): add atomic planting and persistent flower state`. |
| 5. Growth | Approved | `f3589b2526ecd51ab5286f6e8735a1527ea74df7` | Full and focused flower suites passed: 38 tests, 0 failures; placeholder audit and bounded isolated server/client smokes passed | Approved | Committed as `feat(flowers): integrate perennial growth with farming simulation`. |
| 6. Interactions | Approved | Working tree on `f3589b2` | Full 48-test suite, focused flower suites, 231-file generator audit, diff check, and isolated server/client smokes passed | Approved | Centralized authorization, interactions, protection, uprooting, and Poppy stage 7 only; ready for its isolated commit. |
| 7. Rendering | In progress | Working tree after the Milestone 6 commit | Validation pending | Authorized | Two cached baked-model passes using shared geometry and separate base/mask artwork; implementation remains outside the Milestone 6 commit. |
| 8. Species content | Not started |  |  | Not authorized | Initial definitions exist only as Milestone 2 domain data. |
| 9. QA | Not started |  |  | Not authorized | Milestone 2 unit coverage is not full feature QA. |
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

Milestone 3 is validated, owner-approved, and committed as `ebba19b92269e5649eb254302e6311fa57f5743b`. Milestone 4 is validated, owner-approved, and committed as `f5ed405484d99695862fe788dd01b8449cef594f`. Milestone 5 is validated, owner-approved, and committed as `f3589b2526ecd51ab5286f6e8735a1527ea74df7`. Milestone 6 is validated, owner-approved, and ready for its isolated commit. Milestone 7 is authorized and in progress.

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
