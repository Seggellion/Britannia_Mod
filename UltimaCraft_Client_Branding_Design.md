# UltimaCraft Client Branding and Non-Game UI Design

**Project:** UltimaCraft  
**Feature:** Client Branding / Main Menu / Splash System / Non-Game Background Removal  
**Target runtime:** Minecraft 1.21.1, NeoForge 21.1.72, Java 21  
**Target release label:** Version 18  
**Status:** Design baseline for Codex implementation  
**Primary implementation principle:** resource-first, client-only, minimally invasive

---

## 1. Executive Summary

UltimaCraft should present itself as UltimaCraft from the moment the normal Minecraft title screen appears.

The project will:

1. Replace the visible Minecraft title treatment on the main menu with an original **UltimaCraft** wordmark.
2. Render **Version 18** as a stable subtitle directly beneath the UltimaCraft title.
3. Preserve the already-approved/customized UltimaCraft main-menu background.
4. Remove moving panoramas, decorative menu backgrounds, and other non-game client background art wherever practical.
5. Use solid black as the universal fallback background for pre-world/non-game screens when complete removal is not practical.
6. Replace vanilla Minecraft splash text with an Ultima/Ultima Online/UltimaCraft themed corpus of researched facts, jokes, references, and short original lines.
7. Make the LLM/agent responsible for generating the graphics required by this feature rather than silently delegating visual work to the project owner.
8. Preserve all gameplay screens, HUDs, inventory/container interfaces, custom UltimaCraft service NPC interfaces, and world rendering unless a later requirement explicitly expands scope.

The project should avoid replacing Minecraft's complete title-screen implementation if resource overrides plus small client render hooks can accomplish the result. Retaining vanilla screen behavior reduces maintenance risk and preserves button behavior, accessibility, narration, mod compatibility, and future patchability.

---

## 2. Product Intent

The client should feel like a purpose-built UltimaCraft game client rather than Minecraft with a mod installed.

The title screen is the strongest branding surface. The player should see:

- the existing UltimaCraft menu background,
- a custom UltimaCraft title graphic,
- `Version 18`,
- an Ultima-themed splash line,
- the normal functional menu controls.

Once the player leaves the title screen for another pre-world interface, the visual language should become deliberately restrained: **black background, normal controls, no rotating scenery, no unrelated Minecraft panorama artwork**.

This is intended to make the client feel cohesive and to keep attention on UltimaCraft rather than on vanilla Minecraft presentation.

---

## 3. Terminology

### 3.1 Main Menu

The primary title screen shown after client startup and before entering a world/server.

### 3.2 Pre-World / Non-Game Screen

A GUI screen displayed while no world is loaded, conceptually equivalent to a screen rendered while the client has no active level/world.

Examples include:

- Options
- Language
- Accessibility
- Resource Pack
- World Selection
- Create World
- Multiplayer
- Add Server
- Direct Connection
- Realms screens, when present
- Credits/acknowledgements where technically applicable
- Other nested menus opened from the title screen

### 3.3 In-World Screen

A screen opened while the player is in a loaded world.

Examples include:

- pause menu,
- inventory,
- containers,
- custom UltimaCraft banking/service screens,
- books,
- chat,
- game HUD,
- settings opened while a world remains loaded.

These are **outside this project's background-removal scope by default**.

### 3.4 Protected Main-Menu Background

The currently implemented UltimaCraft title-screen background. It already exists and is approved enough that this project must not replace, recolor, crop, blacken, or otherwise redesign it unless a defect makes that unavoidable.

---

## 4. Goals

### G1. UltimaCraft Title

Replace the visible vanilla Minecraft title artwork with a high-quality original UltimaCraft wordmark.

The result must:

- read clearly as `UltimaCraft`,
- be legible at common GUI scales,
- retain transparent edges,
- look intentional at 720p through ultrawide resolutions,
- avoid looking like stretched text,
- avoid directly tracing or copying the Minecraft wordmark,
- avoid directly tracing or copying an official Ultima logo,
- visually fit the medieval/fantasy UltimaCraft identity.

### G2. Version Subtitle

Render the exact visible text:

`Version 18`

The subtitle must:

- appear directly beneath the main title,
- remain centered with the title,
- not collide with the splash text,
- not collide with menu buttons,
- scale correctly with GUI scale and window resizing,
- remain readable against the approved title background.

### G3. Preserve Current Title Background

The existing main-menu background is protected.

No milestone may overwrite or blacken it without evidence that the current implementation fundamentally prevents the other requirements.

If a technical conflict exists, the agent must first refactor how that approved background is displayed and then reproduce its current appearance.

### G4. Remove Non-Game Background Art

All pre-world/non-game screens should use black rather than vanilla decorative background artwork.

The system should suppress or replace, as applicable:

- rotating panorama/cubemap animation,
- panorama overlays,
- menu background textures,
- menu list backgrounds,
- header/footer background art,
- dirt/menu-style tiling,
- title-screen-only artwork leaking into nested screens,
- other decorative pre-world background textures discovered during reconnaissance.

Functional UI textures such as buttons, sliders, icons, list entries, focus states, accessibility indicators, text fields, and selection highlights must remain intact unless separately branded.

### G5. Replace Vanilla Splashes

No normal vanilla Minecraft splash lines should remain in the title-screen splash rotation.

The replacement corpus will contain:

- accurate Ultima/Ultima Online facts,
- short UltimaCraft project facts,
- original Britannian humor,
- city references,
- Virtue references,
- moongate references,
- skill and economy jokes,
- occasional self-aware Version 18 jokes,
- short homages written in original wording.

### G6. Agent-Generated Graphics

The implementation agent owns the visual asset workflow.

It must not simply add `TODO: owner creates logo`.

The expected process is:

1. inspect current title dimensions and rendering,
2. define the required logo canvas and safe area,
3. generate multiple visual candidates if image-generation capability is available,
4. evaluate them against the acceptance rubric,
5. retain the best final transparent asset,
6. validate it in-client at multiple scales.

If the Codex runtime does not expose image generation, it must create a precise asset-generation specification and prompt and clearly mark the graphic gate as blocked. It must not quietly ship a low-quality placeholder as final.

Deterministic black textures may be generated programmatically.

---

## 5. Non-Goals

This project does not, unless later expanded:

- redesign vanilla menu buttons,
- redesign fonts globally,
- redesign the in-game HUD,
- change inventory/container backgrounds,
- change UltimaCraft custom service interfaces,
- change server gameplay logic,
- change networking,
- alter Rails services,
- alter world rendering,
- remove legally required notices or licensing/attribution,
- remove accessibility or narration functionality,
- change the already-approved title background merely for visual consistency.

---

## 6. Research Findings and Architectural Implications

### 6.1 NeoForge Resources Can Override Minecraft Client Resources

NeoForge treats mod assets as resource packs and permits a mod to provide resources in namespaces other than its own, including `minecraft`. This makes targeted replacement of vanilla title and menu textures a first-class option.

**Implication:** prefer exact resource overrides where the desired change is purely visual.

### 6.2 Vanilla Resources and Source Are Available in the Development Environment

NeoForge documentation explicitly recommends inspecting vanilla resources from the development environment when determining exact resource formats and paths.

**Implication:** Codex must not guess title/logo/panorama/menu-background resource paths from memory. It must inspect the actual Minecraft 1.21.1 client resources and decompiled/mapped source used by this project.

### 6.3 Screens Render Through `GuiGraphics` and Background Rendering Is Screen-Owned

NeoForge's screen documentation describes screen rendering around `GuiGraphics`, `Screen#renderBackground`, and screen-specific rendering.

**Implication:** when resource replacement cannot distinguish the title screen from other pre-world screens, a small client-only render/background hook may be justified.

### 6.4 Avoid an Unnecessary Full `TitleScreen` Replacement

Replacing the entire title screen would duplicate or shadow vanilla behavior such as:

- button initialization,
- screen navigation,
- narration,
- accessibility,
- demo state,
- Realms/mod-added integrations,
- future NeoForge patches.

**Implication:** the default architecture is:

1. resource override for title graphic where possible,
2. resource override/custom splash source,
3. small render hook for `Version 18`,
4. targeted pre-world background suppression only where required.

A custom `TitleScreen` implementation is a fallback, not the starting point.

### 6.5 Current NeoForge 1.21.1 Panorama Overlay Risk

As of the research date, NeoForge has an open 1.21.1 rendering issue involving incorrect `panorama_overlay.png` alpha during the opening transition when `earlyWindowControl` is involved.

**Implication:** do not make an alpha-sensitive panorama overlay the core method for implementing the approved title background or black-screen policy. Prefer deterministic static rendering or opaque black where possible.

---

## 7. Proposed Technical Architecture

## 7.1 Client Branding Package

Create or extend a client-only package following existing project conventions, for example:

```text
...client.branding
```

Potential responsibilities:

```text
ClientBrandingHooks
TitleBrandingRenderer
NonGameBackgroundPolicy
```

Names are illustrative. Existing project naming conventions take precedence.

No common/server class may hard-reference client-only Minecraft classes.

## 7.2 Title Wordmark

### Preferred implementation

Replace the exact vanilla title logo texture/sprite resource consumed by Minecraft 1.21.1.

Codex must discover and record the exact resource path from the local 1.21.1 client resources before changing it.

Advantages:

- vanilla title layout remains intact,
- no duplicate title rendering,
- vanilla transition behavior remains intact,
- minimal code,
- lower compatibility risk.

### Fallback implementation

If the current client version renders the title in a way that cannot be safely overridden with one resource, suppress only the title artwork and draw `UltimaCraft` in a post/background-safe title-screen render hook.

Do not replace the complete title screen merely to draw the logo.

## 7.3 Version 18 Subtitle

Preferred approach: render `Version 18` through a client-side title-screen render hook.

The subtitle should be positioned relative to the detected title/logo bounding box rather than through brittle absolute screen coordinates.

Recommended behavior:

- centered horizontally,
- title-relative Y offset,
- configurable constant for small visual tuning,
- GUI-scale aware,
- screen resize safe.

Rendering the subtitle as text is preferred to baking it into the title PNG because:

- it can be independently positioned,
- it remains crisp,
- it can later be changed without regenerating the logo,
- it is easier to validate and localize if necessary.

## 7.4 Existing Main Background

Milestone 0 must identify exactly how the current custom title background is implemented.

Possible implementations include:

- overridden panorama faces,
- overridden panorama overlay,
- a static texture drawn by custom code,
- a resource-pack override,
- a custom title renderer.

The selected background-removal strategy depends on this result.

### Critical rule

If the current title background depends on vanilla panorama resources, those resources cannot simply be replaced globally with black until the title background has been decoupled from them.

## 7.5 Black Pre-World Background Policy

Use this priority order:

### Strategy A: Resource Overrides

Use black replacement assets for exact vanilla decorative resources that are used only where desired.

Examples to investigate, not assumed paths:

- panorama cube faces,
- menu background,
- menu list background,
- header/footer backgrounds,
- panorama overlay.

### Strategy B: Screen-Aware Background Rendering

If the same resource is required by the approved title background and by nested menu screens, implement a client-only policy:

```text
if current screen is the title screen:
    preserve/render approved UltimaCraft title background
else if no world is loaded:
    render opaque black background
else:
    preserve vanilla/in-game behavior
```

Exact hooks must be selected from the actual 1.21.1 NeoForge/Mojang source available locally.

### Strategy C: Narrow Mixin/Patch Only If Necessary

A mixin or invasive patch is allowed only if:

- no supported NeoForge event/resource path can achieve the result,
- the exact target method is documented,
- the patch is narrowly scoped,
- automated/live regression evidence is added.

## 7.6 Splash Text

Two supported architectures should be evaluated during reconnaissance.

### Option A: Override Vanilla Splash Resource

Replace the exact Minecraft splash text resource with the UltimaCraft corpus.

Advantages:

- minimal code,
- keeps vanilla random selection and animation.

### Option B: Mod-Owned Splash Resource and Renderer

Store the corpus under the UltimaCraft namespace and render it explicitly on the title screen.

Advantages:

- full ownership of content,
- avoids depending on a vanilla text resource,
- easier to test and evolve independently.

### Preferred decision rule

Use Option A if it cleanly replaces every normal vanilla splash in the actual 1.21.1 runtime and does not conflict with existing title customization.

Use Option B if resource precedence, formatting, or title customization makes Option A unreliable.

---

## 8. Visual Specification

## 8.1 UltimaCraft Wordmark

The title should be original fantasy branding with Minecraft readability.

Recommended creative direction:

- medieval/high-fantasy carved lettering,
- strong silhouette,
- slightly weathered stone, parchment, aged metal, or warm gold accents,
- subtle dimensionality,
- high contrast,
- transparent background,
- no scenery in the logo asset,
- no Minecraft dirt/grass/block logo imitation,
- no direct recreation of an official Ultima logo.

### Production canvas

Start with a power-of-two transparent canvas such as:

```text
1024 x 256 PNG
```

The final selected art should remain legible when displayed around one quarter of that width.

The exact final resource dimensions should be decided after inspecting how Minecraft 1.21.1 samples the title texture.

## 8.2 Logo Generation Prompt

The implementation agent may refine this after measuring the live title area:

> Create an original transparent wordmark reading “UltimaCraft” for a medieval fantasy Minecraft total-conversion style project. Use highly legible handcrafted fantasy lettering with restrained carved-stone and aged-gold detail, a strong horizontal silhouette, subtle depth, and clean transparent edges. The word must remain readable when reduced to roughly 250–350 pixels wide. Do not imitate the Minecraft logo, do not copy an official Ultima logo, do not add a background, landscape, shield, banner, character, or extra words. Center the complete word on a 4:1 transparent canvas with comfortable edge padding.

Generate multiple candidates where possible and select based on in-game readability, not on full-resolution beauty.

## 8.3 `Version 18`

Recommended appearance:

- centered below title,
- smaller than splash,
- neutral light gray, parchment, or restrained warm gold depending on contrast,
- no heavy shadow that makes it look like part of the title image,
- no animation required.

## 8.4 Black Background

Use true opaque black:

```text
#000000
```

Do not use near-black gradients or decorative noise unless later requested.

The purpose is visual silence.

---

## 9. Splash Research and Editorial System

The splash corpus is a content feature, not filler.

## 9.1 Source Hierarchy

Research in this order:

1. **Official Ultima Online documentation (`uo.com`)**
2. **Project canon and implemented UltimaCraft mechanics**
3. **RunUO source when confirming classic-system behavior**
4. **Reliable secondary Ultima references for discovery/cross-checking**
5. Community recollection only when independently verified

Facts that cannot be verified should not be presented as facts.

## 9.2 Recommended Research Buckets

Build source notes for:

- the eight Virtues,
- the three Principles,
- Britain,
- Moonglow,
- Yew,
- Minoc,
- Trinsic,
- Skara Brae,
- Jhelom,
- Magincia,
- moongates,
- town criers,
- banks and currency,
- training and skill gain,
- merchants,
- shrines,
- dungeons,
- classic travel conventions,
- UltimaCraft-specific systems already implemented.

## 9.3 Corpus Composition

Target initial release:

```text
120–180 splash lines
```

Suggested composition:

- 25% researched lore/fact splashes
- 20% city/location references
- 20% gameplay/mechanics humor
- 15% Virtue/moongate humor
- 10% UltimaCraft-specific references
- 10% Version/build/meta jokes

Avoid overloading one joke or one city.

## 9.4 Editorial Rules

Each splash should:

- ideally fit on one line at normal title-screen scale,
- target <= 55 characters,
- use 70 characters as a hard editorial ceiling unless testing proves otherwise,
- be understandable without a paragraph of context,
- be accurate if phrased as a fact,
- be original wording unless a very short quotation is intentionally used,
- avoid copying long copyrighted dialogue or manual text,
- avoid duplicate punchlines,
- avoid topical jokes that will age immediately,
- avoid jokes that attack players or communities,
- avoid excessive exclamation marks,
- avoid generic AI-style fantasy phrasing.

## 9.5 Humor Rubric

Score each candidate 0–2 on:

1. **Ultima relevance**
2. **Accuracy**
3. **Clarity**
4. **Brevity**
5. **Actual humor/charm**
6. **In-game readability**

Minimum recommended acceptance:

```text
9 / 12
```

Any factual accuracy score of `0` is an automatic rejection.

## 9.6 Splash Creation Workflow

For each researched fact/reference:

1. Record the source.
2. Write a one-sentence factual note in original wording.
3. Generate up to three splash candidates from that note.
4. Remove candidates that require explanation.
5. Check character length.
6. Check duplicates and near-duplicates.
7. Check factual wording against source.
8. Check for copied language.
9. Score with the humor rubric.
10. Add only passing candidates to the shipped corpus.

## 9.7 Example Tone

These are tone examples, not the final researched corpus:

```text
Britannia: now with more blocks.
Mind the moongate.
Eight Virtues. Infinite side quests.
You have gained in patience.
Bank first. Dungeon second. Probably.
Moonglow takes moonlight seriously.
Jhelom has opinions about your training plan.
The town crier knows. The town crier always knows.
Version 18: Britannia persists.
Training dummies remain undefeated on paperwork.
```

The implementation milestone must generate a much larger researched set.

---

## 10. Screen Coverage Matrix

Milestone 0 must produce a concrete matrix for the actual 1.21.1 client.

At minimum test:

| Screen / State | Desired Background |
|---|---|
| Main Title | Existing UltimaCraft background |
| Options from Title | Black |
| Language | Black |
| Accessibility | Black |
| Video Settings | Black |
| Controls | Black |
| Resource Packs | Black |
| World Selection | Black |
| Create World | Black |
| Multiplayer | Black |
| Add Server | Black |
| Direct Connection | Black |
| Realms entry/screens when available | Black unless technically unsafe |
| Credits/Acknowledgements | Black while preserving required content |
| Connecting / pre-world status screens | Black where safe |
| In-world Pause | Unchanged |
| In-world Options | Unchanged by default |
| Inventory / Containers | Unchanged |
| UltimaCraft custom UI | Unchanged |

Any new pre-world screen discovered during reconnaissance should be added.

---

## 11. Startup and Legal/Attribution Boundary

The project may remove decorative backgrounds but must not casually remove:

- legal notices,
- copyright notices,
- third-party acknowledgements,
- mandatory runtime attribution,
- accessibility text.

If a Mojang/NeoForge startup screen is legally or technically required, preserve required marks/text and blacken only replaceable decorative surroundings.

The objective is UltimaCraft visual branding, not concealment of platform/runtime attribution.

---

## 12. Automated Validation

Create a small validation layer where practical.

### 12.1 Splash Validator

The build/test suite should detect:

- empty lines,
- duplicate lines,
- case-insensitive duplicates,
- trailing whitespace,
- lines above the accepted hard limit,
- accidental inclusion of known vanilla splash lines if a baseline can be extracted,
- malformed encoding.

Optional: near-duplicate detection for normalized punctuation.

### 12.2 Asset Validator

Validate:

- PNG files load successfully,
- title PNG has alpha,
- required black assets are opaque,
- dimensions match expected production values,
- no accidentally huge source image is shipped,
- no untracked temporary candidate assets are bundled into the production JAR.

### 12.3 Client-Only Safety

A dedicated-server run/build must not load client-only branding classes.

---

## 13. Live Validation Matrix

Validate at minimum:

### Resolutions

- 1280x720
- 1920x1080
- 2560x1440
- an ultrawide resolution when practical

### GUI Scale

- Auto
- 2
- 3
- 4 where supported

### Main Title Checks

- no visible Minecraft title text/art,
- UltimaCraft wordmark fully visible,
- `Version 18` visible,
- splash visible and non-overlapping,
- existing background visually unchanged,
- buttons remain clickable,
- no clipped title at small window sizes,
- no unexpected panorama motion.

### Nested Screen Checks

- black background,
- controls readable,
- lists readable,
- focus highlights visible,
- no missing functional icons,
- no black text rendered invisibly on black due to removed backing art,
- screen transitions do not flash vanilla panoramas/backgrounds.

### In-World Regression Checks

- pause screen remains expected,
- inventory remains expected,
- custom UltimaCraft UIs remain expected,
- HUD remains expected,
- no black background injected behind gameplay screens.

---

## 14. Performance Requirements

This feature should be effectively free at runtime.

Avoid:

- per-frame file I/O,
- per-frame image generation,
- per-frame string file parsing,
- repeated resource lookup allocations when avoidable,
- custom framebuffer effects.

Splash data should load once per resource reload/title initialization, following whichever architecture is selected.

---

## 15. Compatibility Requirements

The implementation must:

- remain client-only,
- not require server protocol changes,
- not alter server compatibility,
- tolerate window resizing,
- tolerate resource reload,
- tolerate GUI-scale changes,
- preserve accessibility/narration,
- preserve mod-added title buttons where possible,
- preserve NeoForge's normal screen lifecycle.

External user-selected resource packs may override some resource-first branding assets because of normal resource-pack precedence. If authoritative branding against external packs becomes a requirement, that should be treated as a separate policy decision rather than hidden inside this milestone.

---

## 16. Failure Modes to Avoid

Do not:

- overwrite the approved main-menu background while trying to blacken the panorama,
- replace all `Screen` rendering and accidentally black out in-game GUIs,
- cancel a screen render event and thereby remove widgets,
- hard-code title coordinates for only 1920x1080,
- bake `Version 18` into the title graphic without a reason,
- ship placeholder art as the final logo,
- leave vanilla splashes mixed into the corpus,
- generate 150 random jokes with no research record,
- depend on alpha-sensitive panorama behavior when an opaque/static solution is available,
- add a broad mixin before proving a resource/event solution cannot work.

---

## 17. Deliverables

Production implementation should ultimately include:

1. final UltimaCraft title asset,
2. `Version 18` title-screen rendering,
3. preserved approved main background,
4. black pre-world background behavior,
5. curated splash corpus,
6. splash source/research notes,
7. asset manifest,
8. screen matrix,
9. automated validation,
10. live validation evidence,
11. implementation log.

Recommended project documentation folder:

```text
docs/client-branding/
```

Recommended files:

```text
INVENTORY.md
ASSET_MANIFEST.md
SCREEN_MATRIX.md
SPLASH_RESEARCH.md
SPLASH_EDITORIAL.md
IMPLEMENTATION_LOG.md
VALIDATION.md
```

---

## 18. Definition of Done

The feature is complete when:

- the title says UltimaCraft graphically,
- `Version 18` appears beneath it,
- the approved main-menu background is preserved,
- vanilla Minecraft splash content is absent from normal rotation,
- the shipped splash corpus meets the editorial/research standard,
- pre-world nested screens use black instead of unrelated decorative backgrounds,
- no title panorama/video-like motion remains unless it is intentionally part of the approved background,
- in-world UI is unaffected,
- the client builds cleanly,
- dedicated-server validation remains clean,
- automated tests pass,
- live screen-matrix validation passes,
- the final asset is not a placeholder,
- evidence is recorded in project documentation.

---

## 19. Research Baseline

Primary technical references:

- NeoForge 1.21.1 Screens documentation:  
  https://docs.neoforged.net/docs/1.21.1/gui/screens/
- NeoForge client textures documentation:  
  https://docs.neoforged.net/docs/1.21.1/resources/client/textures/
- NeoForge resources documentation explaining that mod resources may override Minecraft resources:  
  https://docs.neoforged.net/docs/1.21.4/resources/
- NeoForge sides documentation:  
  https://docs.neoforged.net/docs/1.21.1/concepts/sides/
- NeoForge issue #3258 concerning 1.21.1 panorama-overlay alpha behavior:  
  https://github.com/neoforged/NeoForge/issues/3258

Primary Ultima research references:

- Official Ultima Online Virtues documentation:  
  https://uo.com/wiki/ultima-online-wiki/gameplay/the-virtues/
- Official Britain page:  
  https://uo.com/wiki/ultima-online-wiki/world/cities-and-towns/cities-and-towns-britain/
- Official Moonglow page:  
  https://uo.com/wiki/ultima-online-wiki/world/cities-and-towns/cities-and-towns-moonglow/
- Official Movement and Travel page, including public moongates:  
  https://uo.com/wiki/ultima-online-wiki/beginning-the-adventure/movement-and-travel/
- Official Town Crier page:  
  https://uo.com/wiki/ultima-online-wiki/gameplay/the-town-crier/

These references are a starting point. Milestone 0/4 must inspect the exact local 1.21.1 implementation and expand Ultima source coverage before shipping the final corpus.
