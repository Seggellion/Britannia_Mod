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

## Milestone 2 - UltimaCraft title graphic

Date: 2026-08-11

Status: Gate 2 passed with final-quality generated art

Production behavior changed: Yes - vanilla title and edition artwork is replaced by UltimaCraft branding

Starting commit: `c918f9629452c0aa378f8a859dcea8f7b766160c`

### Source measurement and integration decision

Pinned Minecraft 1.21.1 source reconfirmed that `LogoRenderer` draws a 256x44 logo at Y 30 from the top 44 rows of a 256x64 logical texture, then draws a 128x14 edition layer at Y 67. The bundled high-resolution resources are 1024x256 for normal/rare title art and 512x64 for the edition layer. Normal and rare paths must match because the rare path is selected with probability `1.0E-4`.

The resource-first Milestone 0 architecture remained valid: no Java renderer, new mixin, title-screen replacement, or coordinate patch was needed.

### Image generation and selection

Built-in image generation produced three exact-spelling candidates on flat `#00FF00` chroma-key backgrounds:

1. charcoal stone faces with aged-gold edging;
2. bright aged-gold faces with dark-bronze outlines;
3. ivory stone faces with antique-brass edging.

All three were converted with the installed chroma-key helper, normalized to the exact 1024x256 canvas, and compared at the actual 256x64 logical texture size. Candidate 2 was selected because it remained widest, brightest, and most immediately readable after the 4:1 downscale. Candidate 1 became too dark against black areas of the protected background, while candidate 3 used less of the available horizontal title box.

Selected prompt:

```text
Use case: logo-brand
Asset type: production candidate for a Minecraft 1.21.1 title-screen wordmark texture
Primary request: one original wordmark containing the exact text "UltimaCraft", spelled U-l-t-i-m-a-C-r-a-f-t, with no other text
Backdrop: perfectly flat uniform #00FF00 chroma key with no shadow, gradient, texture, reflection, or floor plane
Style: original medieval high-fantasy display lettering; broad classical serif forms; warm aged-gold faces; restrained dark-bronze outlines; shallow engraved wear
Composition: compact 4:1 single-line silhouette, centered with generous padding, all artwork inside the upper 69% of the target texture
Constraints: exact capitalization, distinct letters, strong open counters, crisp opaque subject, readable at 256 pixels wide
Avoid: Minecraft or official Ultima logo imitation, voxel/block forms, runes, extreme swashes, scenery, shields, swords, banners, frames, extra symbols, watermark, mockup, perspective tilt
```

The initial soft matte left 940 green-dominant low-alpha pixels after normalization. The image-generation workflow's prescribed one-pixel edge contraction reduced that to 41 resampling remnants; 40 have alpha 11 or lower and the last has alpha 23. No green-dominant pixel reaches the materially visible alpha threshold of 32, and visual inspection on black shows no fringe.

### Runtime assets

- `minecraft.png`: 1024x256 RGBA, SHA-256 `865EFD5DC70AEA7F1839919BC306B28C690F71DF33E50E5682C2BAB237655A3E`.
- `minceraft.png`: byte-identical to `minecraft.png`, covering the vanilla rare-logo path.
- `edition.png`: 512x64 RGBA, fully transparent, SHA-256 `CA5DE485B94EC28D85E83A70DF6704E4D08BF9340C5C7FF726242BB295B70235`.

The wordmark has visible alpha bounds `(43,8)` through `(979,167)`. Rows 176-255 are fully transparent and therefore cannot be clipped by vanilla's top-44-of-64 sampling. Candidate files and previews remain ignored under `build/client-branding-logo-candidates`; only the three selected runtime PNGs are packaged.

### Automated and packaged-resource validation

Added `ClientBrandingTitleAssetTest` with four checks:

1. the title runtime directory contains exactly the three selected assets;
2. normal and rare title files are dimension-matched, byte-identical, and hash-locked;
3. the wordmark has real transparency, substantial coverage, antialiased edges, safe bounds, no materially visible chroma key, and no content below row 175;
4. the edition layer matches vanilla dimensions and is fully transparent.

Focused result: all seven cumulative client-branding tests passed.

The regular assembled JAR contains exactly `edition.png`, `minceraft.png`, and `minecraft.png` under `assets/minecraft/textures/gui/title`. Their JAR-entry hashes match the working-tree hashes. No keyed source, discarded candidate, normalized preview, contact sheet, or generation output is packaged.

### Live title matrix

Minecraft framebuffer captures verified exact dimensions rather than relying on desktop-window crops:

| Resolution | GUI scale | Result |
|---:|---:|---|
| 1280x720 | Auto | Pass |
| 1920x1080 | 2 | Pass |
| 2560x1440 | 3 | Pass |
| 3440x1440 | 4 | Pass |

Every case showed the complete centered `UltimaCraft` wordmark, no vanilla Minecraft title/edition artwork, no clipping or stretching, readable letter identity, normal splash rendering, functional controls, preserved runtime/legal attribution, and the unchanged protected chest-to-medallion background. `Version 18` remains intentionally deferred to Milestone 3.

### Full build comparison

The installed pinned Gradle 8.9 distribution was used because the tracked wrapper remains the Milestone 0 Git LFS pointer.

- transformation, compilation, resource processing, JAR assembly, scaffold compilation, and test compilation completed;
- 1,795 tests executed: 1,757 passed, 21 failed, 17 skipped;
- the same 21 pre-existing banner/scaffold asset-integrity tests failed as in Milestones 0 and 1;
- the four-test increase from 1,791 is exactly `ClientBrandingTitleAssetTest`;
- no new failing test class or failure count was introduced.

### Gate 2 checklist

- [x] Final art reads exactly `UltimaCraft` with no extra text.
- [x] Wordmark is original and does not imitate the Minecraft or official Ultima logos.
- [x] Three candidates were generated and evaluated at the real logical display size.
- [x] Selected asset is final quality rather than a placeholder.
- [x] Normal and rare logo paths are byte-identical.
- [x] Vanilla edition art is suppressed by a dimension-matched transparent resource.
- [x] Exact resolution/GUI-scale matrix passed, including ultrawide.
- [x] No vanilla Minecraft title artwork remains visible.
- [x] Protected title background files remain byte-identical to Milestone 0.
- [x] Focused branding tests pass.
- [x] Full build failure count and classes match the recorded baseline exactly.
- [x] JAR contains only the selected title runtime assets, not candidates or generation artifacts.
- [x] No unrelated tracked file changed.

Gate 2 passes because the shipped generated wordmark is final quality, technically clean, readable across the required matrix, and integrated without changing the protected background or title-screen implementation.

Planned local commit message:

```text
feat(client): replace title branding with UltimaCraft
```

No push, merge, rebase, force operation, or remote mutation is authorized or performed.

## Milestone 3 - Version 18 title subtitle

Date: 2026-08-11

Status: Gate 3 passed

Production behavior changed: Yes - the main title now displays `Version 18` beneath the UltimaCraft wordmark

Starting commit: `fe481f059691093a9beced355e24effce7f329f0`

### Implementation

- Added a client-only game-bus subscriber for `ScreenEvent.Render.Post`.
- Restricted rendering to `TitleScreen`; every other screen returns without drawing.
- Rendered the exact literal `Version 18` with the vanilla font and shadow in opaque warm parchment `#F0E2B6`.
- Centered from the current GUI width and placed the baseline at GUI Y 76: title top 30 + title height 44 + a 2-pixel gap.
- Kept the subtitle code-rendered; no title PNG, protected background file, mixin, or mixin registration changed.

`TitleBrandingLayoutTest` adds four deterministic checks for exact copy, odd/even centering, title-relative Y placement, and button clearance at the logical GUI heights represented by the live validation matrix.

### Live title matrix

Minecraft framebuffer captures verified exact dimensions rather than desktop-window dimensions. The validation harness accounted for Windows' 125% display scaling and waited for the final client resource-initialization signal before capture.

| Resolution | GUI scale | Result |
|---:|---:|---|
| 1280x720 | Auto | Pass |
| 1920x1080 | 2 | Pass |
| 2560x1440 | 3 | Pass |
| 3440x1440 | 4 | Pass |

Every case showed exact `Version 18` copy centered directly beneath the complete UltimaCraft wordmark. The line remained readable, clear of the rotating splash and first button, and stable through GUI-scale and aspect-ratio changes. Title controls, runtime/legal attribution, and the protected chest-to-medallion background remained present.

Screenshots are ignored runtime evidence under `build/client-branding-validation/milestone-3`; they are not packaged or committed. The temporary capture harness was removed, and the local development GUI scale was restored to 2.

### Automated, build, and package validation

Focused result: all four `TitleBrandingLayoutTest` checks passed.

The installed pinned Gradle 8.9 distribution was used because the tracked wrapper remains the Milestone 0 Git LFS pointer.

- transformation, compilation, resource processing, regular and all-in-one JAR assembly, scaffold compilation, and test compilation completed;
- 1,799 tests executed: 1,761 passed, 21 failed, 17 skipped;
- the same 21 pre-existing banner/scaffold asset-integrity tests failed as in Milestones 0-2;
- the four-test increase from 1,795 is exactly `TitleBrandingLayoutTest`;
- no new failing test class or failure count was introduced;
- the assembled JAR contains `TitleBrandingLayout.class`, `TitleBrandingRenderer.class`, and the three unchanged title PNGs.

Protected/background hashes remain the Milestone 0 values, and all title-asset hashes remain the Milestone 2 values.

### Gate 3 checklist

- [x] Exact visible subtitle text is `Version 18`.
- [x] Subtitle is code-rendered and not baked into title art.
- [x] Rendering is client-only and restricted to `TitleScreen`.
- [x] Subtitle is centered relative to the vanilla title box and responsive to GUI width.
- [x] Subtitle clears the title art, rotating splash, and menu controls across the exact matrix.
- [x] Wordmark and edition assets remain byte-identical to Milestone 2.
- [x] Protected title background files remain byte-identical to Milestone 0.
- [x] Focused subtitle tests pass.
- [x] Full build failure count and classes match the recorded baseline exactly.
- [x] JAR assembly contains the intended renderer/layout classes and unchanged title assets.
- [x] No unrelated tracked file changed.

Gate 3 passes because the exact responsive subtitle is integrated at the title-only render boundary without changing the approved wordmark, protected background, or any non-title screen.

Planned local commit message:

```text
feat(client): add Version 18 title subtitle
```

No push, merge, rebase, force operation, or remote mutation is authorized or performed.

## Milestone 4 - UltimaCraft splash corpus

Date: 2026-08-11

Status: Gate 4 passed

Production behavior changed: Yes - normal title rotation now draws only from the researched UltimaCraft corpus

Starting commit: `0b3e22e4a1cfb2ca5b6fabb102ca36a88b4c0ed7`

### Research and editorial review

Research preceded drafting. `SPLASH_RESEARCH.md` records official Ultima Online and Shroud of the Avatar sources plus repository source for project-specific mechanics. Coverage includes the Principles and eight Virtues, required cities and landmarks, Lord British, Avatar, classic companions and antagonists, Sosaria/Britannia, Codex and Gem vocabulary, moongates and facets, town criers, banks/currency, reagents, shrines, travel, skills, and training.

`SPLASH_EDITORIAL.md` records the six-dimension 0-2 rubric for accuracy, relevance, humor/charm, clarity, brevity/fit, and originality. Every accepted line scored at least 9/12 with a non-zero accuracy score. The final 160-line composition is:

- 42 Virtue/lore/people/world lines;
- 50 city, landmark, and local-travel lines;
- 40 classic play, NPC, magic, banking, and skill lines;
- 18 repository-grounded UltimaCraft mechanic lines;
- 10 branding/display/build-meta lines.

All copy is newly written microcopy. No official prose, dialogue, lyric, or long quotation is reproduced.

### Resource implementation and validator

Added `assets/minecraft/texts/splashes.txt` at the exact vanilla namespace path selected in Milestone 0. The implementation is resource-only: Minecraft's existing `SplashManager` loads and randomly chooses the rows, and the existing `SplashRenderer` retains the yellow angled pulse. No Java production class, title coordinates, wordmark, subtitle, mixin, background, button, or legal/runtime attribution changed.

Final corpus measurements:

- 160 UTF-8 rows with no byte-order mark;
- longest row: 45 code points, below the 55-point editorial limit and 70-point hard ceiling;
- zero empty, untrimmed, control-character, exact, case-insensitive, or punctuation-insensitive duplicate rows;
- zero canonical matches to the normal Minecraft 1.21.1 corpus;
- SHA-256 `02A45F05AA83B51792C8D5D093FC7619E64B572274730881CB4383A6D32A7E17`.

Added `ClientBrandingSplashCorpusTest` with three deterministic checks for encoding/count, line quality/uniqueness, and vanilla leakage. The leakage check uses a test-only set of 441 unique canonical hash prefixes derived from all 446 vanilla rows. The fixture contains no vanilla text and is not a runtime resource.

Hardcoded December 24, January 1, October 31, and rare username greetings remain the accepted renderer exceptions recorded in Milestone 0; this milestone owns normal corpus rotation only.

### Live title and rotation validation

All captures were made after final resource initialization and verified against exact Minecraft framebuffer dimensions. Each row used an independent client start.

| Resolution | GUI scale | Observed splash | Result |
|---:|---:|---|---|
| 1280x720 | Auto | `Valor trains offshore.` | Pass |
| 1920x1080 | 2 | `Yew keeps the prison offshore.` | Pass |
| 2560x1440 | 3 | `Skara Brae ferry departs eventually.` | Pass |
| 3440x1440 | 4 | `Seekers read the fine print.` | Pass |

Two further independent 1280x720/Auto restarts selected `Truth brought receipts.` and `Cove is not on the moongate menu.` The six distinct results establish normal random rotation in combination with the deterministic resource/vanilla validator. Every line was complete, correctly encoded, readable at the retained vanilla angle, and clear of `Version 18`.

The exact UltimaCraft wordmark, subtitle, controls, runtime/legal attribution, and protected chest-to-medallion background remained unchanged. Screenshots are ignored evidence under `build/client-branding-validation/milestone-4`; the temporary harness was removed, and the local GUI scale was restored to 2.

### Automated, build, and package validation

Focused result: all three `ClientBrandingSplashCorpusTest` checks passed.

The installed pinned Gradle 8.9 distribution was used because the tracked wrapper remains the Milestone 0 Git LFS pointer.

- transformation, compilation, resource processing, regular/all-in-one JAR assembly, scaffold compilation, and test compilation completed;
- 1,802 tests executed: 1,764 passed, 21 failed, 17 skipped;
- the same 21 pre-existing banner/scaffold asset-integrity tests failed as in Milestones 0-3;
- the three-test increase from 1,799 is exactly `ClientBrandingSplashCorpusTest`;
- no new failing test class or failure count was introduced;
- the regular JAR contains one `assets/minecraft/texts/splashes.txt` entry with 160 rows and byte-identical hash;
- research, editorial, and test-hash files are absent from the runtime JAR.

All three protected-background hashes remain the Milestone 0 values. All three title-asset hashes remain the Milestone 2 values.

### Gate 4 checklist

- [x] Research artifact covers the required Ultima/Ultima Online/Shroud/project vocabulary through authoritative and repository sources.
- [x] Editorial artifact records composition, rubric, representative decisions, and rejections.
- [x] Corpus contains 160 original, reviewed lines with a maximum length of 45.
- [x] Validator rejects encoding, whitespace, empty, control, duplicate, over-limit, and vanilla-leak defects.
- [x] Normal vanilla 1.21.1 splash corpus is fully replaced at the exact resource path.
- [x] Vanilla random selection and yellow angled pulse remain intact.
- [x] Six independent starts selected six distinct corpus rows.
- [x] Exact resolution/GUI-scale matrix passed, including ultrawide.
- [x] No observed line overlapped `Version 18` or changed title controls/attribution.
- [x] Wordmark, subtitle code, title assets, and protected background remain unchanged.
- [x] Focused splash tests pass.
- [x] Full build failure count and classes match the recorded baseline exactly.
- [x] JAR contains only the intended runtime corpus, not research/test artifacts.
- [x] No unrelated tracked file changed.

Gate 4 passes because a researched, independently reviewed, deterministic 160-line UltimaCraft corpus wholly owns normal splash rotation and passes automated, packaged-resource, and six-start live validation without changing the approved title composition.

Planned local commit message:

```text
feat(client): replace vanilla splashes with UltimaCraft corpus
```

No push, merge, rebase, force operation, or remote mutation is authorized or performed.

## Milestone 5 - Complete pre-world background cleanup

Date: 2026-08-11

Status: Gate 5 passed

Production behavior changed: No - the Milestone 1 five-resource implementation already covered the complete discovered pre-world matrix

Starting commit: `b85d50f1a80a059488b97d070d78cf75175aac77`

### Renderer audit and implementation decision

Re-audited Minecraft 1.21.1, NeoForge 21.1.72, Realms, and project-owned screen sources against every row in `SCREEN_MATRIX.md`.

- Standard pre-world screens inherit `Screen#renderBackground`, which selects the normal `menu_background` only while no level is loaded.
- Lists and framed screens consume the normal list/separator assets; `CreateWorldScreen` also consumes `tab_header_background` directly.
- `GenericMessageScreen` and `ReceivingLevelScreen.Reason.OTHER` explicitly draw panorama/blur and then `MENU_BACKGROUND` in the same render method, so the opaque resource still closes their final surface.
- The non-poem `WinScreen` credits path renders `Screen.MENU_BACKGROUND` directly and therefore receives the accepted black credits presentation. The end-poem/portal path remains separate.
- Realms screens either inherit the standard path, deliberately remain transparent over the title, or render an underlying screen before a functional popup.
- NeoForge's Mods list consumes the same standard/direct menu assets.
- Active-world `Screen` rendering selects `inworld_menu_background`; containers and project-owned gameplay screens use their own guarded render paths.

No uncovered production renderer was found. Adding another asset, event handler, post-render fill, or mixin would have widened scope without changing a failing matrix row, so Milestone 5 keeps the accepted runtime implementation unchanged and records the completed evidence instead.

### Complete live and source-verified matrix

The client ran at an exact 1920x1080 framebuffer and GUI scale 2. Live black-background passes covered:

- protected title and pre-world Options;
- Skin Customization, Music & Sounds, Credits & Attribution, the rolling credits presentation, and Online Options;
- Select World; Create World Game/World/More tabs; Game Rules; Experiments; and Data Packs;
- multiplayer safety onboarding, server list, Direct Connection, Add/Edit Server, and a deterministic disconnected screen produced through closed loopback port `127.0.0.1:1`;
- the reachable Realms invalid-session state;
- NeoForge Mods.

Cumulative Milestone 1 evidence covers Language, Accessibility, Video, Controls, Resource Packs, the initial world-selection/creation/multiplayer routes, and loaded-world rendering. Source inspection closed the authentication-only Realms variants, launch-only Quick Play/error variants, Telemetry when disabled, confirmation/alert variants, generic waiting/progress, pre-world receiving-level, and direct generic-message routes. Each such variant resolves through a live-proven resource consumer; required labels, fields, lists, focus, disabled states, warnings, icons, narration, and action controls remain outside the overridden asset set.

Ignored framebuffer evidence is stored under `build/client-branding-validation/milestone-5`. Temporary UI/capture helpers were deleted after the sweep; screenshots are not packaged or committed.

### Flash and transition validation

A title-to-Options transition was captured as 40 consecutive desktop frames at 25 ms intervals. Five exposed-background points were sampled in every frame: `(100,100)`, `(200,500)`, `(1700,500)`, `(100,900)`, and `(1800,900)`. All 200 samples were exactly RGB `(0,0,0)`, with no exposed panorama frame.

The direct-render source audit additionally confirms that `GenericMessageScreen` and pre-world `ReceivingLevelScreen` draw the opaque menu resource after panorama/blur in the same call, preventing a resource-order flash on those paths.

### In-world regression guards

Live captures verified:

- normal world and HUD rendering;
- player inventory over the normal world-backed treatment;
- pause menu over the normal blurred world;
- Options opened from pause over the same in-world background.

Project `MenuScreen` explicitly overrides `renderBackground` with an empty implementation, and project gameplay screens/containers remain on their own render paths. All four `inworld_*` resources and every panorama resource remain absent. Portal/end transitions and the end-poem portal presentation remain unchanged.

### Automated, build, hash, and package validation

Focused `ClientBrandingBackgroundPolicyTest` result: `BUILD SUCCESSFUL`; all three policy checks passed.

The installed pinned Gradle 8.9 distribution was used because the tracked wrapper remains the Milestone 0 Git LFS pointer.

- transformation, compilation, resource processing, regular/all-in-one JAR assembly, scaffold compilation, and test compilation completed;
- 1,802 tests executed: 1,764 passed, 21 failed, 17 skipped;
- the same 21 pre-existing banner/scaffold asset-integrity tests failed as in Milestones 0-4;
- no new test, failing class, or failure count was introduced;
- the regular JAR contains exactly the five accepted black GUI paths and no `inworld_*` or panorama override;
- all five runtime hashes remain the Milestone 1 values;
- all three protected title-background hashes remain the Milestone 0 values.

### Gate 5 checklist

- [x] Every row in the pre-world screen matrix is live-passed, cumulatively live-passed, or source-verified where authentication/launch/error setup is impractical.
- [x] Options, accessibility/language/video/controls families, packs, world selection/creation, multiplayer/add/direct, Realms, credits, status/error, and NeoForge/project boundaries are covered.
- [x] All practical pre-world screens are opaque black outside functional UI.
- [x] Functional controls, content, focus, disabled states, lists, icons, warnings, tooltips, and narration remain visible/operable.
- [x] Forty transition frames show no one-frame panorama flash.
- [x] World/HUD, inventory, pause, in-world Options, containers, project gameplay screens, and portal/end paths remain guarded.
- [x] No additional production code or resource was necessary.
- [x] Focused background-policy tests pass.
- [x] Full build failure count and classes match the recorded baseline exactly.
- [x] JAR contents and protected/runtime hashes match the accepted manifests.
- [x] Temporary validation helpers are removed and local options are restored.
- [x] No unrelated tracked file changed.

Gate 5 passes because the resource-first implementation now has complete matrix, transition, package, and gameplay-regression evidence, with no uncovered screen requiring a broader renderer intervention.

Planned local commit message:

```text
feat(client): complete pre-world background cleanup
```

No push, merge, rebase, force operation, or remote mutation is authorized or performed.

## Milestone 5 correction - loading backgrounds and website entry

Date: 2026-08-11

Status: Correction passed; Milestone 6 not started

Production behavior changed: Yes - loading/status panoramas are now black, and the title Realms button is now the UltimaCraft website entry

Starting commit: `c6bef97d61d7a4135cdc2c1d06b62ff8ec459ae4`

### Loading-screen correction

The user-reported live behavior superseded the initial Gate 5 source-only conclusion for transient loading frames. Minecraft's loading/status classes call `Screen#renderPanorama` before drawing their status content, and that call can become visible before the ordinary opaque menu resource covers the frame.

Added `LoadingScreenPanoramaMixin`, a client-only injection at the head of that one decorative call. `LoadingScreenBackgroundPolicy` limits replacement to:

- `GenericMessageScreen`;
- `ReceivingLevelScreen` when its renderer actually calls the panorama path;
- `ConnectScreen`;
- `LevelLoadingScreen`;
- `GenericWaitingScreen`;
- `ProgressScreen`.

For those classes the mixin fills the current GUI bounds with opaque `#000000` and cancels only the panorama draw. The screen then continues normally, retaining progress percentages, loading/status text, narration, and cancel/action controls. The title, accessibility onboarding, ordinary options/menu screens, and all other panorama callers fail the policy predicate and remain untouched. Nether portal and end portal receiving paths never call `renderPanorama`, so their special rendering is preserved.

No panorama face, panorama overlay, or `inworld_*` resource was added.

### Title website entry

Added `TitleWebsiteButtonBranding`, a client game-bus subscriber for `ScreenEvent.Init.Post`.

- It runs only for `TitleScreen`.
- It identifies the exact vanilla Realms button by its `menu.online` component.
- It removes that widget and inserts a replacement at the same X/Y/width/height.
- The exact visible label is `Ultimacraft website`.
- The click action uses Minecraft's supported `Util.getPlatform().openUri` path with the exact URI `https://www.ultimacraft.com`.
- Multiplayer, Mods, Options, language, accessibility, Quit, and copyright controls are unchanged.

The Realms notification overlay remains a transparent title-layer concern; only the main-menu entry button/action is replaced.

### Live validation

The client launched successfully with the new mixin active. Exact 1920x1080 framebuffer evidence confirmed:

- `Ultimacraft website` occupies the former Minecraft Realms slot on the protected UltimaCraft title screen;
- the wordmark, `Version 18`, splash, chest/medallion background, remaining buttons, and attribution are unchanged;
- opening the existing local validation world displays a solid-black loading surface with its `0%` progress indicator and no panorama.

Ignored evidence is stored under `build/client-branding-validation/milestone-5-correction`. The temporary capture helper was deleted, the client was closed cleanly, and local GUI/multiplayer-warning options remain restored.

### Automated, build, hash, and package validation

Added five tests:

- two `LoadingScreenBackgroundPolicyTest` cases lock all six loading/status inclusions and the title/onboarding/ordinary-screen exclusions;
- three `TitleWebsiteButtonBrandingTest` cases lock the exact label, HTTPS host, Realms-only recognition, and in-place geometry.

The focused eight-test set, including the three existing background-policy checks, passed.

Full build comparison:

- transformation, compilation, resource processing, regular/all-in-one JAR assembly, scaffold compilation, and test compilation completed;
- 1,807 tests executed: 1,769 passed, 21 failed, 17 skipped;
- the five-test increase from 1,802 is exactly the new correction coverage;
- the same 21 pre-existing banner/scaffold asset-integrity tests failed;
- no new failing class or failure count was introduced.

The regular JAR contains `LoadingScreenBackgroundPolicy.class`, `LoadingScreenPanoramaMixin.class`, `TitleWebsiteButtonBranding.class`, and the updated client mixin registration. The protected chest sequence and `TitleScreenBackgroundMixin` hashes remain unchanged. `britannia_mod.mixins.json` changed only to register the loading guard and now has SHA-256 `585E7F65AD1543F8B81F83FFD15702D54BB2042E25C5F994A2FA593E1B3098A6`.

### Correction checklist

- [x] Loading/status panorama calls are replaced by opaque black.
- [x] Loading text, progress, narration, and controls remain rendered.
- [x] Title, onboarding, ordinary menus, portal/end transitions, and in-world paths are excluded.
- [x] No panorama or `inworld_*` resource override was added.
- [x] Minecraft Realms main-menu button is absent.
- [x] Exact `Ultimacraft website` replacement occupies the same slot.
- [x] Website action targets exactly `https://www.ultimacraft.com` through Minecraft's platform browser API.
- [x] Protected title presentation remains unchanged.
- [x] Focused correction/protection tests pass.
- [x] Full build failure count and classes match baseline.
- [x] JAR registration/classes and hashes are verified.
- [x] Temporary validation helper is removed and local options are restored.
- [x] Milestone 6 was not started.

Planned local commit message:

```text
fix(client): black out loading screens and add website link
```

No push, merge, rebase, force operation, or remote mutation is authorized or performed.

## Milestone 6 - Automated regression coverage

Date: 2026-08-11

Status: Gate 6 passed with the inherited full-suite baseline exceptions recorded

Production behavior changed: No

Starting commit: `208aa5b4556b762e9cff40dd84b558d5d11bbbb4`

### Coverage audit and additions

The existing branding tests already covered the content-heavy contracts:

- strict splash encoding, size, trimming, readability, exact/canonical uniqueness, and vanilla-line exclusion;
- title PNG loading, exact dimensions, hashes, useful alpha, clean transparency, safe-area placement, and normal/rare parity;
- transparent edition-layer dimensions and opacity;
- exact dimensions and per-pixel opaque black for all five pre-world background assets;
- absence of panorama and `inworld_*` overrides;
- protected title-background hashes;
- responsive `Version 18` layout, loading-screen policy, and website-button identity/geometry.

Added `ClientBrandingRuntimeContractTest` with five cross-cutting tests that close the remaining Gate 6 gaps:

1. all eleven branding runtime resources must exist at their exact production paths;
2. the mod's `assets/minecraft/textures/gui` override tree may contain only the eight approved title/background PNGs, and its text override tree may contain only `splashes.txt`;
3. both branding event subscribers must carry an explicit `Dist.CLIENT` restriction;
4. all non-mixin branding types must remain under the client package;
5. `LoadingScreenPanoramaMixin` and `TitleScreenBackgroundMixin` must be registered in the mixin configuration's `client` section and never in its common `mixins` section.

No production Java, resource, build configuration, gameplay system, or live presentation changed.

### Focused validation

Command:

```text
C:\Users\dusti\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat test --tests "com.seggellion.britannia_mod.client.branding.*" --tests "com.seggellion.britannia_mod.client.TitleBrandingLayoutTest" --tests "com.seggellion.britannia_mod.client.LoadingScreenBackgroundPolicyTest" --tests "com.seggellion.britannia_mod.client.TitleWebsiteButtonBrandingTest" --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL`; all 24 focused branding tests passed, including all five new runtime-contract tests.

### Clean full build

Command:

```text
C:\Users\dusti\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat clean build --no-configuration-cache --console=plain
```

Result:

- a real `clean` removed prior project outputs;
- Minecraft/NeoForge transformation, `compileJava`, resource processing, regular/all-in-one JAR assembly, scaffold compilation, and `compileTestJava` completed from the clean state;
- 1,812 tests executed: 1,774 passed, 21 failed, 17 skipped;
- the five-test increase from 1,807 is exactly `ClientBrandingRuntimeContractTest`, whose five cases all passed;
- the same 21 pre-existing banner/scaffold asset-integrity cases failed in the same seven classes recorded at Milestone 0;
- there is no new failure, error, skipped test, or failing class attributable to client branding.

The Gradle `build` result remains failed only because the repository's inherited banner/scaffold baseline is not green. Repairing those unrelated runtime assets or expectations would violate this milestone's scope; all applicable branding automation is green.

### Dedicated-server safety

The repository's CI-defined command was run exactly through the installed pinned Gradle distribution:

```text
C:\Users\dusti\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat runGameTestServer --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 34 seconds. ModLauncher reported `Env=SERVER`; Britannia 0.1.8 and its dependencies loaded; all 349 required GameTests passed; and the dedicated server saved and shut down cleanly. Neither client branding mixin nor either client event subscriber loaded on the server.

Pre-existing development warnings about the absent optional local configuration, initial generated server properties, and development refmaps did not prevent startup or tests and are unrelated to branding.

### Package and protection audit

The clean regular JAR contains:

- the three selected title PNGs, five black background PNGs, splash corpus, protected chest sequence, and mixin configuration at their exact runtime paths;
- `LoadingScreenBackgroundPolicy`, `TitleBrandingLayout`, `TitleBrandingRenderer`, `TitleWebsiteButtonBranding`, `LoadingScreenPanoramaMixin`, and `TitleScreenBackgroundMixin` classes.

It contains no branding test class, vanilla-splash hash fixture, client-branding research document, candidate, prompt, contact sheet, or source-format image. The protected hashes remain:

- `chest_sequence.png`: `7187FB8CA89FF959CB57022BEC15C113EE44FC4F15B2839B02D0D6E7217B0B30`;
- `TitleScreenBackgroundMixin.java`: `70C9D1EAE4C95F42DC08E8029002FB890B782452E2517E263A3E2378C517AD62`;
- `britannia_mod.mixins.json`: `585E7F65AD1543F8B81F83FFD15702D54BB2042E25C5F994A2FA593E1B3098A6`.

### Gate 6 checklist

- [x] Splash corpus validation is green.
- [x] Production asset presence and exact resource paths are locked.
- [x] Production PNG dimensions and title alpha are locked.
- [x] Every black replacement pixel is opaque black.
- [x] Runtime override directories reject accidental candidate/source files.
- [x] Client-only packages, subscribers, and mixin registration are locked.
- [x] Focused 24-test branding suite is green.
- [x] Clean build completes assembly and reproduces only the known 21-test repository baseline.
- [x] Dedicated server loads safely and all 349 required GameTests pass.
- [x] Production JAR contents and protected hashes are verified.
- [x] No production behavior or unrelated file changed.
- [x] Milestone 7 was not started.

Gate 6 passes because every applicable client-branding regression check and the dedicated-server safety run are green. The only non-green full-suite results are the exact inherited, out-of-scope banner/scaffold baseline documented before branding work began.

Planned local commit message:

```text
test(client): cover branding resources and splash corpus
```

No push, merge, rebase, force operation, or remote mutation is authorized or performed.

## Milestone 7 - Live client acceptance

Date: 2026-08-11

Status: Gate 7 passed — owner visually approved

Production behavior changed: No

Starting commit: `9b1530769597bdb8b9b312315fbd3b485520420a`

### Live validation performed

Ran the Minecraft 1.21.1 / NeoForge 21.1.72 development client through the complete required title-screen matrix:

- 1280×720 at GUI scale Auto;
- 1920×1080 at GUI scale 2;
- 2560×1440 at GUI scale 3;
- 3440×1440 at GUI scale 4.

Exact framebuffer captures confirm that the protected chest/medallion background, full UltimaCraft wordmark, `Version 18`, rotating splash, `Ultimacraft website` replacement, remaining buttons, and legal/version text are visible and collision-free at every matrix point. No vanilla panorama, stretching, seam, stale frame, or clipped control was observed. Singleplayer, Options, Accessibility, Resource Packs, Select World, and return/escape navigation paths were exercised successfully; the website label, exact HTTPS target, platform-browser action, Realms-only identity, and geometry remain locked by the green Milestone 5 tests.

`F3+T` completed and returned to an unchanged branded title screen. Representative Options, Accessibility, Resource Packs, and Select World screens rendered on opaque black while retaining readable content and controls. These final spot checks supplement the full Milestone 5 pre-world matrix.

A 50-frame burst captured the transition into the existing disposable `New World`: Preparing Resources, the intermediate blank frame, `0%`, and progress through `100%` all rendered on black. Four safe corner locations were sampled in every frame (200 samples total) with zero non-black samples and no panorama or stale-frame flash.

The world then rendered normally. Exact 1280×720 HUD, inventory, and pause captures show normal world-backed behavior with no pre-world black-background leakage. The world saved and each client process stopped cleanly; the resolution-matrix runs ended `BUILD SUCCESSFUL`.

### Evidence and cleanup

The full evidence record and owner checklist are in `docs/client-branding/VALIDATION.md`. Ignored captures remain under `build/client-branding-validation/milestone-7` for review only and will not enter the production JAR. The temporary window-control helper and noncanonical captures were removed.

The pre-validation `run/options.txt` was restored byte-for-byte. Its before/after SHA-256 is `DCB76C9729A6C4F275D656F0BCF42706DECB7B4EC89E15A90159980D13790AC7`.

No production Java, resource, build configuration, gameplay system, remote branch, or external service changed. Gate 6's focused tests, clean-build baseline, dedicated-server pass, package audit, and protected hashes carry forward because Milestone 7 changes documentation only.

### Gate 7 status

- [x] Required resolution/GUI-scale matrix captured and reviewed.
- [x] Title composition and resize behavior pass.
- [x] Pre-world menus remain black and readable.
- [x] Loading transition remains black without a panorama or sampled flash.
- [x] Resource reload preserves branding.
- [x] In-world HUD, inventory, and pause rendering show no regression.
- [x] Runtime options restored exactly and temporary tooling removed.
- [x] Explicit owner visual approval received on 2026-08-11.
- [x] Milestone 8 was not started.

Gate 7 passed after the owner explicitly approved the visual evidence on 2026-08-11.

Planned local commit message:

```text
docs(client-branding): record live acceptance validation
```

No push, merge, rebase, force operation, or remote mutation is authorized or performed.

## Milestone 8 - Final audit and handoff

Date: 2026-08-11

Status: Gate 8 passed; branch is integration-ready

Production behavior changed: No

Starting commit: `ac4872573d120e1bf270c7fc71019a06302c5ef7`

Merge base with `patch-18`: `5c9f4f2f688d169997cb4e45757fcbe1e7079373`

### Final branch audit

The final feature range contains nine approved milestone commits before this handoff correction. Relative to the merge base, `client-branding` is nine commits ahead and zero commits behind. The cumulative diff contains 31 paths and 3,006 inserted lines before this final documentation update.

Every changed path belongs to the authorized client-branding scope:

- one line-ending policy update for the two hash-validated corpus files;
- seven client-branding documents;
- four client-only branding helpers/subscribers and one client-only loading mixin;
- one client mixin registration;
- nine exact Minecraft-namespace runtime resources;
- seven branding test classes and one test-only vanilla splash hash fixture.

There is no gameplay, block, entity, farming, economy, Rails, network-protocol, server-logic, custom service-UI, in-world HUD/inventory, or unrelated branch change. `git diff --check` passes for the complete merge-base range.

All milestone commits are present in chronological order:

1. `6f180621` — baseline reconnaissance;
2. `c918f962` — non-game black background policy;
3. `fe481f05` — UltimaCraft title graphic;
4. `0b3e22e4` — `Version 18` subtitle;
5. `b85d50f` — researched splash corpus;
6. `c6bef97d` — complete pre-world sweep;
7. `208aa5b4` — loading-screen correction and website button;
8. `9b153076` — automated regression coverage;
9. `ac487257` — owner-approved live acceptance evidence.

The final documentation was reconciled with the implementation. One historical typo in the Milestone 6 entry was corrected from eight inherited failing classes to seven, matching both the Milestone 0 list and the fresh test XML.

### Final validation

Focused command:

```text
C:\Users\dusti\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat test --tests "com.seggellion.britannia_mod.client.branding.*" --tests "com.seggellion.britannia_mod.client.TitleBrandingLayoutTest" --tests "com.seggellion.britannia_mod.client.LoadingScreenBackgroundPolicyTest" --tests "com.seggellion.britannia_mod.client.TitleWebsiteButtonBrandingTest" --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL`; all 24 focused branding tests passed.

Clean build command:

```text
C:\Users\dusti\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat clean build --no-configuration-cache --console=plain
```

Result: production/scaffold compilation, resource processing, regular/all-in-one JAR assembly, and test compilation completed. The suite executed 1,812 tests: 1,774 passed, 21 failed, 17 skipped, and zero errored. The failures are the exact inherited banner/scaffold baseline across the same seven classes listed at Milestone 0; no branding test failed and no new failure appeared.

Dedicated-server command:

```text
C:\Users\dusti\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat runGameTestServer --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 30 seconds. ModLauncher reported `Env=SERVER`; all 349 required GameTests passed; the server saved and shut down cleanly; no client-branding runtime class loaded server-side.

### Package, asset, and hygiene audit

The freshly assembled regular JAR contains all nine selected Minecraft-namespace branding resources, all six required branding/title classes, and the mixin registration. Every packaged branding resource is byte-identical to its source file. The JAR contains no branding test class, vanilla splash hash fixture, client-branding research/editorial document, candidate, prompt, contact sheet, or source-format artwork.

No branding production file contains a placeholder, temporary marker, draft, TODO, or FIXME. Repository-wide legacy placeholders and `tmp/manual-verification-server-stdin.gradle` predate this branch and are absent from the merge-base diff.

Protected title presentation remains intact:

- `chest_sequence.png`: `7187FB8CA89FF959CB57022BEC15C113EE44FC4F15B2839B02D0D6E7217B0B30`;
- `TitleScreenBackgroundMixin.java`: `70C9D1EAE4C95F42DC08E8029002FB890B782452E2517E263A3E2378C517AD62`;
- `britannia_mod.mixins.json`: `585E7F65AD1543F8B81F83FFD15702D54BB2042E25C5F994A2FA593E1B3098A6`.

Milestone 7's owner-approved live matrix remains the final visual authority. No production file changed after that approval.

### Integration notes

- Integration source: `client-branding`.
- Integration target: `patch-18`.
- Expected merge base: `5c9f4f2f688d169997cb4e45757fcbe1e7079373`.
- Preserve all milestone commits or merge the final branch tip according to the owner's integration policy.
- Expect overlap only if the target independently changed `.gitattributes`, `britannia_mod.mixins.json`, the four new client branding class names, the loading mixin path, the nine Minecraft-namespace resource paths, or `docs/client-branding`.
- Preserve `TitleScreenBackgroundMixin` and `chest_sequence.png` byte-for-byte during conflict resolution.
- Preserve both `client` mixin registrations in `britannia_mod.mixins.json`: `LoadingScreenPanoramaMixin` and the pre-existing `TitleScreenBackgroundMixin`.
- After integration, rerun the 24 focused branding tests, the clean build with its documented inherited baseline, and `runGameTestServer`; then perform a short title/loading smoke if target-side menu code changed.
- Do not interpret the known 21 banner/scaffold failures as a branding regression unless their count or class set changes.

### Gate 8 checklist

- [x] Working tree started clean.
- [x] Complete diff from merge base inspected.
- [x] All changed paths are within client-branding scope.
- [x] All approved milestone commits exist.
- [x] Documentation matches implementation and validation output.
- [x] No placeholder branding asset remains.
- [x] No temporary generation/test evidence is shipped.
- [x] Focused branding tests pass.
- [x] Clean build reproduces only the inherited baseline after assembling artifacts.
- [x] Dedicated server and all required GameTests pass.
- [x] Production JAR contents and hashes are verified.
- [x] Branch divergence and merge notes are recorded.
- [x] No merge, push, rebase, tag, release, or deployment was performed.

Gate 8 passes. The branch is ready for later integration, subject to the known repository-wide banner/scaffold baseline.

Planned local commit message:

```text
docs(client-branding): finalize implementation handoff
```

No merge, push, rebase, tag, release, deployment, or remote mutation is authorized or performed.
