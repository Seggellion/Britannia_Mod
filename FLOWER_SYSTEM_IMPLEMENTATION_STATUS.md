# Persistent Flower System Implementation Status

Repository: `C:/projects/britannia/mod/Britannia_Mod`  
Branch: `Farming`  
Current HEAD: `0a600a9d2b86ac839e4ff03b1dabf445eccec7bf`
Working state: Milestone 2 is committed; Milestone 3 is owner-approved and pending its isolated commit; Milestone 4 is authorized and in progress; unrelated `.claude/` is preserved.

| Milestone | Status | HEAD/commit | Tests | Owner approval | Notes |
|---|---|---|---|---|---|
| 0. Preflight | Approved | `41fb51a` | Baseline `compileJava processResources test` passed during discovery | Approved | Root, exact branch, HEAD, guidance, and baseline recorded. |
| 1. Discovery | Approved | `41fb51a` | Read-only discovery and baseline validation complete | Approved | Discovery report is present and owner questions were answered. |
| Owner decision gate | Approved | `41fb51a` | N/A | Approved | B1, B2, A1-A3, G1-G4, AS1, N1, and color-capacity direction recorded. |
| 2. Data contracts | Approved | `0a600a9d2b86ac839e4ff03b1dabf445eccec7bf` | Final `compileJava processResources test` passed; 13 tests, 0 failures | Approved | Committed as `feat(flowers): add species and colour data contracts`. |
| 3. Placeholders | Approved | Pending isolated commit on `0a600a9` | Full build/test passed; 18 tests, 0 failures; focused domain and asset tests passed; generator check passed; bounded client resource-load smoke passed for flower assets | Approved | Item, seed, model, texture, manifest, and resource-contract work only. |
| 4. Lifecycle | In progress | Working tree after Milestone 3 commit | Validation pending | Authorized | FlowerBlock lifecycle, planting, persistence, and synchronization only. |
| 5. Growth | Not started |  |  | Not authorized | No random tick or live growth behavior added. |
| 6. Interactions | Not started |  |  | Not authorized | No shearing, cutback, uprooting, item-to-seed, or Poppy stage-7 behavior added. |
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

Milestone 3 is validated and owner-approved. Its isolated commit is pending; Milestone 4 is in progress and remains uncommitted for owner review.
