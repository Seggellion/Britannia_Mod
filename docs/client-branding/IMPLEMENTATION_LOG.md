# UltimaCraft Client Branding Implementation Log

## Milestone 0 - Reconnaissance, baseline, and worktree establishment

Date: 2026-08-11

Status: Gate 0 passed with pre-existing baseline failures recorded

Production behavior changed: No

### Worktree establishment

- Confirmed the original repository was on local `patch-18` at `5c9f4f2f688d169997cb4e45757fcbe1e7079373`.
- Local `patch-18` was 202 commits ahead of `origin/patch-18`; the task explicitly required the local branch, so no fetch/pull/rebase was performed.
- Original worktree dirt was limited to four unrelated untracked local files and was preserved.
- Created branch `client-branding` directly from local `patch-18`.
- Created sibling worktree `C:\projects\britannia\mod\Britannia_Mod_client_branding`.
- Initial `patch-18...client-branding` divergence was `0 0`; both pointed at the same base commit.
- New worktree was clean before investigation and validation.
- No repository `AGENTS.md` was present; no additional commit restrictions were discovered.

### Reconnaissance completed

- Read the supplied design and milestone playbook in full.
- Searched Java, mixins, resources, `assets/minecraft`, build configuration, workflow configuration, and custom screens for all required branding/background terms.
- Inspected exact mapped Minecraft 1.21.1 and patched NeoForge 21.1.72 sources from the local NeoGradle cache.
- Inspected the actual client resource JAR and runtime asset index.
- Recorded title render order, logo resource IDs and sampling dimensions, splash loading behavior, panorama resources, menu/list/separator resources, state selection, resource-pack precedence, and relevant NeoForge screen events.
- Enumerated pre-world and exclusion screens in `SCREEN_MATRIX.md`.
- Hashed and visually inspected the protected title background implementation.
- Checked the current upstream status of NeoForge panorama-overlay issue #3258 on 2026-08-11.

### Architecture decision for later milestones

The baseline supports a resource-first implementation:

1. keep the existing `TitleScreenBackgroundMixin` and chest animation unchanged;
2. override normal and rare vanilla title logo PNGs with the same transparent UltimaCraft wordmark;
3. suppress only the vanilla edition layer with a transparent PNG;
4. add `Version 18` through a title-only client `ScreenEvent.Render.Post` hook;
5. replace the normal splash resource for the ordinary rotation;
6. use opaque black pre-world menu/list/separator/Create World header resources while leaving every `inworld_*` resource absent;
7. avoid panorama-overlay alpha as a dependency;
8. add a new mixin only if a named screen fails the live matrix and no supported resource/event solution exists.

Known caveats are the hardcoded splash exceptions, direct `Screen.MENU_BACKGROUND` consumers, and `WinScreen` end credits. They are documented rather than hidden behind a broad renderer.

### Baseline validation

#### Canonical workflow discovery

- CI compile/test command: Gradle `build` from `.github/workflows/build.yml`.
- CI game-test command: `./gradlew runGameTestServer --no-configuration-cache --console=plain` (not required as the normal Milestone 0 compile/test baseline).
- Documented local client command: `./gradlew runClient -Pdev --no-configuration-cache` from `README.md`.
- Wrapper properties pin Gradle 8.9.

#### Fresh-worktree wrapper defect

Command attempted:

```text
.\gradlew.bat build --no-configuration-cache --console=plain
```

Result: immediate failure before Gradle loaded:

```text
Could not find or load main class org.gradle.wrapper.GradleWrapperMain
```

Cause: `gradle/wrapper/gradle-wrapper.jar` checks out as a 133-byte Git LFS pointer to a 43,504-byte object, but the current `.gitattributes` no longer declares that path as LFS. `git lfs checkout` therefore does not materialize it in a fresh worktree. The original worktree happens to retain the real historical payload. This repository bootstrap defect predates client branding and was not repaired in this milestone.

#### Equivalent pinned Gradle 8.9 build

To test the project without modifying the tracked wrapper path, the already-installed distribution named by `gradle-wrapper.properties` was invoked directly:

```text
C:\Users\dusti\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat build --no-configuration-cache --console=plain
```

Result:

- Minecraft/NeoForge transformation pipeline completed;
- `compileJava`, `classes`, JAR assembly, scaffold compile, and `compileTestJava` completed;
- 1,788 tests executed;
- 1,750 passed, 21 failed, 17 skipped;
- Gradle build result: failed at `:test` after 4m 15s.

All 21 failures are pre-existing banner/scaffold asset-integrity failures. They reproduce against bytes in the untouched original worktree. Representative evidence:

- `tournament_curtain` expected SHA-256 `352c75ef...` but both worktrees contain `9f0429b6...`;
- `road_guard` expected SHA-256 `9cbe8311...` but both worktrees contain `bd20b8fb...`.

Failing test classes:

- `BannerScaffoldToolTest` (12 failures);
- `ExtraSmallBannerFamilyIntegrationTest`;
- `ParallelLargeGateECloseoutTest`;
- `ParallelLargeIntegrationTest` (2 failures);
- `ParallelMediumIntegrationTest` (2 failures);
- `PerpendicularMediumIntegrationTest`;
- `SmallBannerFamilyIntegrationTest` (2 failures).

The HTML report was generated at `build/reports/tests/test/index.html` in the feature worktree. Build outputs are ignored and are not part of the milestone commit.

#### Client smoke

The documented client flow was run with the same already-installed pinned Gradle 8.9 distribution:

```text
...\gradle-8.9\bin\gradle.bat runClient -Pdev --no-configuration-cache --console=plain
```

Result:

- Minecraft 1.21.1, NeoForge 21.1.72, Britannia 0.1.8, and GeckoLib 4.6.6 loaded;
- resource manager loaded `vanilla`, `mod_resources`, `mod/britannia_mod`, `mod/geckolib`, and `mod/neoforge`;
- audio and all GUI texture atlases initialized;
- client remained responsive at the title-menu stage;
- no world was opened or modified;
- process was stopped intentionally with Ctrl+C after the smoke condition, producing the expected non-zero interrupted task exit.

The log contains many pre-existing missing sound/model/texture and GeckoLib parsing warnings. These were not introduced by client branding and are outside this milestone.

### Gate 0 checklist

- [x] Separate `client-branding` branch and sibling worktree established.
- [x] Base commit, divergence, and starting clean/dirty states recorded.
- [x] Exact title, logo, splash, panorama, generic menu, list, and separator paths documented from local source.
- [x] Relevant NeoForge screen events and their limitations documented.
- [x] Reliable pre-world discriminator (`minecraft.level == null`) confirmed in vanilla source.
- [x] Existing main-menu background implementation classified and protected hashes recorded.
- [x] Screen coverage matrix created.
- [x] Baseline compile/test state known, including pre-existing failures.
- [x] Client startup supported and smoke-tested without entering a world.
- [x] No protected asset or production code/resource changed.
- [x] No unrelated file changed.

Gate 0 passes because every gate criterion is satisfied; the gate requires a known baseline build state, not a falsely green baseline. The two repository defects above are carried forward as baseline conditions and must not be attributed to later branding work.

### Milestone 0 deliverables

- `docs/client-branding/INVENTORY.md`
- `docs/client-branding/SCREEN_MATRIX.md`
- `docs/client-branding/ASSET_MANIFEST.md`
- `docs/client-branding/IMPLEMENTATION_LOG.md`

Planned local commit message:

```text
docs(client-branding): establish client branding baseline
```

No push, merge, rebase, force operation, or remote mutation is authorized or performed.

## Milestone 1 - Non-game black background policy

Date: 2026-08-11

Status: Gate 1 passed

Production behavior changed: Yes - pre-world menu backgrounds are now opaque black

Starting commit: `6f180621ed31704155b3024794ddbd6b10bd1a08`

### Implementation

- Added exact-dimension opaque-black overrides for `menu_background.png`, `menu_list_background.png`, `header_separator.png`, `footer_separator.png`, and `tab_header_background.png` under `assets/minecraft/textures/gui`.
- Every pixel in every override is exactly ARGB `0xFF000000`; there is no gradient, noise, transparency, embedded decoration, or panorama dependency.
- Added no production Java, event hook, broad screen replacement, or new mixin.
- Left all four `inworld_*` resources and every panorama face/overlay absent so Minecraft's existing level-aware resource selection remains authoritative.
- Left the protected title animation asset, renderer mixin, and mixin registration byte-identical.

### Deterministic policy test

Added `ClientBrandingBackgroundPolicyTest` with three checks:

1. all five runtime PNGs exist, decode, match the vanilla physical dimensions, and contain only opaque black pixels;
2. the project contains no override for any protected `inworld_*` or panorama resource;
3. the three approved title-background files retain their Milestone 0 SHA-256 values.

Focused command:

```text
...\gradle-8.9\bin\gradle.bat test --tests com.seggellion.britannia_mod.client.branding.ClientBrandingBackgroundPolicyTest --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL`; all three policy tests passed.

### Live client validation

The client was launched from isolated fresh title sessions at GUI scale 2 and captured at 1938x1038. The following routes passed visual inspection:

- protected title screen;
- Options;
- Language;
- Accessibility;
- Video Settings;
- Controls;
- Resource Packs;
- Select World with a populated world list;
- Create World, including its tab header/body/footer;
- Join Multiplayer;
- NeoForge Mods list;
- a loaded disposable single-player validation world.

Every tested pre-world screen was uniformly black outside functional UI. Buttons, disabled states, sliders, scrollbars, text fields, focus outlines, list content, pack icons, world thumbnails, and footer controls remained legible. The loaded world retained normal world/HUD rendering with no black overlay. The title retained the protected chest-to-medallion presentation and existing controls.

Screenshots and the validation save are ignored runtime evidence under `build/client-branding-validation` and `run`; neither is included in the milestone commit.

### Full build and packaged-resource verification

The repository's tracked wrapper remains the Milestone 0 Git LFS pointer, so the installed pinned Gradle 8.9 distribution was used directly. The client-branding worktree is outside the app sandbox's declared writable root; permissioned validation was therefore required for Gradle's project cache and outputs.

Full build command:

```text
...\gradle-8.9\bin\gradle.bat build --no-configuration-cache --console=plain
```

Result:

- transformation, compilation, resource processing, JAR assembly, scaffold compilation, and test compilation completed;
- 1,791 tests executed: 1,753 passed, 21 failed, 17 skipped;
- the same 21 pre-existing banner/scaffold asset-integrity tests failed as in Milestone 0;
- the increase from 1,788 to 1,791 tests is exactly the three passing Milestone 1 policy tests;
- no new failing test class or failure count was introduced.

The assembled regular JAR contains all five exact client-branding GUI paths. It contains no client-branding `inworld_*` or panorama asset.

### Gate 1 checklist

- [x] Standard pre-world menu, list, separator, and Create World header resources are opaque black.
- [x] Minimum live matrix passed: title, Options, Language, Accessibility, Video, Controls, Resource Packs, Select World, and Multiplayer.
- [x] Create World and NeoForge Mods list received additional live coverage.
- [x] Loaded-world rendering remained unchanged with no black overlay.
- [x] Functional controls, list content, focus, disabled states, scrollbars, icons, text, and thumbnails remained visible.
- [x] No `inworld_*` or panorama override was added.
- [x] Protected title asset, mixin, and registration hashes still match Milestone 0.
- [x] Focused policy tests pass.
- [x] Full build failure count and classes match the recorded baseline exactly.
- [x] JAR assembly includes the five intended runtime assets.
- [x] No unrelated tracked file changed.

Gate 1 passes because the resource-first policy produces opaque-black pre-world screens without regressing the title or active-world rendering.

Planned local commit message:

```text
feat(client): establish non-game black background policy
```

No push, merge, rebase, force operation, or remote mutation is authorized or performed.
