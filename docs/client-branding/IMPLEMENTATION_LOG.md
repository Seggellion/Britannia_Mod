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
