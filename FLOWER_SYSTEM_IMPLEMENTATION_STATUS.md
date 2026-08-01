# Persistent Flower System Implementation Status

Repository: `C:/projects/britannia/mod/Britannia_Mod`  
Branch: `Farming`  
Current HEAD: `f5ed405484d99695862fe788dd01b8449cef594f`
Working state: Milestone 5 is owner-approved and ready for its isolated commit; Milestone 6 is authorized and in progress; unrelated `.claude/` is preserved.

| Milestone | Status | HEAD/commit | Tests | Owner approval | Notes |
|---|---|---|---|---|---|
| 0. Preflight | Approved | `41fb51a` | Baseline `compileJava processResources test` passed during discovery | Approved | Root, exact branch, HEAD, guidance, and baseline recorded. |
| 1. Discovery | Approved | `41fb51a` | Read-only discovery and baseline validation complete | Approved | Discovery report is present and owner questions were answered. |
| Owner decision gate | Approved | `41fb51a` | N/A | Approved | B1, B2, A1-A3, G1-G4, AS1, N1, and color-capacity direction recorded. |
| 2. Data contracts | Approved | `0a600a9d2b86ac839e4ff03b1dabf445eccec7bf` | Final `compileJava processResources test` passed; 13 tests, 0 failures | Approved | Committed as `feat(flowers): add species and colour data contracts`. |
| 3. Placeholders | Approved | `ebba19b92269e5649eb254302e6311fa57f5743b` | Full build/test passed; 18 tests, 0 failures; focused domain and asset tests passed; generator check passed; bounded client resource-load smoke passed for flower assets | Approved | Committed as `content(flowers): add placeholder items seeds models and masks`. |
| 4. Lifecycle | Approved | `f5ed405484d99695862fe788dd01b8449cef594f` | Full automated suite plus isolated server/client smoke passed | Approved | Committed as `feat(flowers): add atomic planting and persistent flower state`. |
| 5. Growth | Approved | Working tree on `f5ed405` | Full and focused flower suites passed: 38 tests, 0 failures; placeholder audit and bounded isolated server/client smokes passed | Approved | Shared growth evaluation and perennial reset only; ready for its isolated commit. |
| 6. Interactions | In progress | Working tree after the Milestone 5 commit | Validation pending | Authorized | Centralized authorization, interactions, protection, uprooting, and Poppy stage 7 only. |
| 7. Rendering | Not started |  |  | Not authorized | No runtime renderer, client registration, tinting, or model-selection behavior added; Milestone 3 contains standalone placeholder assets only. |
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

Milestone 3 is validated, owner-approved, and committed as `ebba19b92269e5649eb254302e6311fa57f5743b`. Milestone 4 is validated, owner-approved, and committed as `f5ed405484d99695862fe788dd01b8449cef594f`. Milestone 5 is validated and owner-approved for its isolated commit. Milestone 6 is authorized and in progress.
