# UltimaCraft Client Branding Inventory

## Scope and baseline

This inventory is the Milestone 0 source-of-truth for client branding work. It was prepared on 2026-08-11 from the exact local Minecraft 1.21.1 and NeoForge 21.1.72 development artifacts used by this project. No branding feature or production resource was changed during this milestone.

| Item | Baseline |
|---|---|
| Repository | `C:\projects\britannia\mod\Britannia_Mod` |
| Feature worktree | `C:\projects\britannia\mod\Britannia_Mod_client_branding` |
| Base branch | local `patch-18` |
| Feature branch | `client-branding` |
| Base commit | `5c9f4f2f688d169997cb4e45757fcbe1e7079373` (`Clean placeholder model metadata`) |
| Initial divergence | `patch-18...client-branding` = `0 0` |
| Minecraft | 1.21.1, data/world version 3955, protocol 767 |
| NeoForge | 21.1.72 |
| NeoGradle | UserDev 7.0.165 |
| Java | Toolchain 21 |
| Mod id/version | `britannia_mod` / 0.1.8 |

The original `patch-18` worktree was already dirty only through four unrelated untracked local files: `.claude/settings.local.json` and the three supplied UltimaCraft branding prompt/design/playbook Markdown files. Those files were left untouched. The new feature worktree began clean.

## Authoritative inputs and local source

Product behavior is defined by the supplied `UltimaCraft_Client_Branding_Design.md` and `UltimaCraft_Client_Branding_Codex_Playbook.md`. Those files are owner-supplied local inputs in the original worktree, not files introduced to the feature branch.

Implementation facts below come from the project's mapped and patched local dependencies rather than remembered resource names:

- mapped Minecraft/NeoForge source: `C:\Users\dusti\.gradle\caches\ng_execute\72eae032be7ff018a2217709209f9db43bf2eafb3788e655beb9ee335b6e57d3\output`;
- Minecraft client resource JAR: `C:\Users\dusti\.gradle\caches\ng_execute\8e174e702c7f291ba66853f922adcbb391521e92b0bfe790dec479ca456ad773\output`;
- runtime asset index: `C:\Users\dusti\.gradle\caches\minecraft\assets\indexes\asset-index.json`.

The client JAR contains placeholder one-pixel panorama faces. The runtime asset index supplies the real 1024x1024 panorama images. This split is important when inspecting resources; the JAR alone is not the complete runtime panorama.

## Existing project branding and menu work

Repository searches covered `TitleScreen`, `ScreenEvent`, panorama resources, menu/list backgrounds, title/logo resources, splashes, `GuiGraphics`, `renderBackground`, mixins, `assets/minecraft`, custom screens, resource-pack overrides, and client configuration.

Findings:

- The approved main-menu background is implemented by `TitleScreenBackgroundMixin`; it is not a panorama-face replacement or panorama-overlay replacement.
- The project already packages resources under `assets/minecraft` for block atlas/model overrides, establishing that cross-namespace resources are an existing project convention.
- There are no existing title-logo, edition-logo, splash-corpus, menu-background, list-background, or panorama overrides.
- No project-owned custom screen is reachable as a pre-world navigation screen. Project screens found through `Minecraft#setScreen` are gameplay/network/keybind flows and remain out of this branding scope.
- `britannia_mod.mixins.json` registers the protected title mixin only in its client mixin list.

## Exact vanilla title implementation

### Classes and render order

The main title class is `net.minecraft.client.gui.screens.TitleScreen`.

Its relevant `render` order is:

1. panorama/fade calculations;
2. `renderPanorama`;
3. `super.render`, which renders the screen's widgets;
4. `LogoRenderer#renderLogo`;
5. `ClientHooks#renderMainMenu`;
6. the splash text;
7. NeoForge/Minecraft branding lines and Realms content.

`TitleScreen#renderBackground` is intentionally empty. This makes normal `Screen` menu-background resources irrelevant to the title screen itself. The current mixin draws the approved background inside `TitleScreen#render`, before `Screen#render`/widgets, with a tail fallback.

### Logo resources and dimensions

`net.minecraft.client.gui.components.LogoRenderer` consumes:

| Purpose | Exact resource | Bundled PNG | Vanilla logical sampling/display |
|---|---|---:|---|
| Normal logo | `minecraft:textures/gui/title/minecraft.png` | 1024x256 | 256x64 texture space; displays 256x44 |
| Rare logo | `minecraft:textures/gui/title/minceraft.png` | 1024x256 | Same geometry; selected with probability `1.0E-4` |
| Edition label | `minecraft:textures/gui/title/edition.png` | 512x64 | 128x16 texture space; displays 128x14 |

Default title Y is 30. The edition layer is drawn at Y 67. Because only the top 44 of 64 logical logo pixels are displayed, future `UltimaCraft` art must be designed for that sampled safe region. Both `minecraft.png` and the rare `minceraft.png` must be replaced or the vanilla rare logo can still appear. The edition layer should be suppressed with a transparent resource rather than by replacing `TitleScreen`.

`TitleScreen#preloadResources` explicitly preloads both logo layers, the panorama overlay, and the cube map.

### Version subtitle hook

NeoForge posts `ScreenEvent.Render.Pre` before a screen is drawn and `ScreenEvent.Render.Post` afterward. `Render.Pre` is cancellable, but cancellation skips the complete screen draw and therefore is not suitable for preserving vanilla widgets. `Render.Post` is non-cancellable and can safely add `Version 18` when `event.getScreen() instanceof TitleScreen`.

The subtitle should be positioned relative to the known 256-pixel vanilla logo box and its calculated screen-center X, not a fixed window coordinate. It must remain client-only and resize/GUI-scale safe.

## Exact splash implementation

`net.minecraft.client.resources.SplashManager` loads:

```text
minecraft:texts/splashes.txt
```

The corresponding mod resource path is:

```text
src/main/resources/assets/minecraft/texts/splashes.txt
```

On resource reload, `SplashManager` opens the highest-priority resource, trims lines, filters the line whose Java hash is `125780783`, and stores the result. `TitleScreen#init` asks the manager for a splash. `SplashRenderer` retains the vanilla yellow, rotated, pulsing presentation at approximately X = center + 123 and Y = 69.

An exact resource override is the preferred architecture for the normal rotation: it removes normal vanilla lines while preserving vanilla random choice and animation. Three hardcoded calendar lines (December 24, January 1, October 31) and the rare username greeting bypass `splashes.txt`. If the owner later requires every possible line, including those exceptions, to be UltimaCraft-owned, a narrow title-screen splash renderer will be necessary.

## Exact panorama implementation and known risk

`net.minecraft.client.gui.screens.Screen` defines a `CubeMap` at:

```text
minecraft:textures/gui/title/background/panorama
```

which resolves to:

```text
minecraft:textures/gui/title/background/panorama_0.png
minecraft:textures/gui/title/background/panorama_1.png
minecraft:textures/gui/title/background/panorama_2.png
minecraft:textures/gui/title/background/panorama_3.png
minecraft:textures/gui/title/background/panorama_4.png
minecraft:textures/gui/title/background/panorama_5.png
```

`net.minecraft.client.renderer.PanoramaRenderer` then draws:

```text
minecraft:textures/gui/title/background/panorama_overlay.png
```

NeoForge issue [#3258](https://github.com/neoforged/NeoForge/issues/3258) is open as of 2026-08-11 and documents incorrect `panorama_overlay.png` alpha during the 1.21.1 early-window opening transition (reported with NeoForge 21.1.234). Client branding must not rely on overlay alpha to hide or compose required artwork. The preferred black-screen policy below uses opaque resources drawn after the panorama and does not modify the panorama overlay.

## Exact generic menu and list resources

### Standard screen background

`Screen#renderBackground` performs the following:

1. if `minecraft.level == null`, render the panorama;
2. apply the blur effect;
3. render a menu background selected by world state;
4. post `ScreenEvent.BackgroundRendered`.

Resources selected by `Screen`:

| State | Background | Header | Footer |
|---|---|---|---|
| No level | `minecraft:textures/gui/menu_background.png` | `minecraft:textures/gui/header_separator.png` | `minecraft:textures/gui/footer_separator.png` |
| Level loaded | `minecraft:textures/gui/inworld_menu_background.png` | `minecraft:textures/gui/inworld_header_separator.png` | `minecraft:textures/gui/inworld_footer_separator.png` |

Bundled pre-world dimensions are 16x16 for `menu_background.png` and 32x2 for each separator.

### Lists

`net.minecraft.client.gui.components.AbstractSelectionList` selects:

| State | List resource |
|---|---|
| No level | `minecraft:textures/gui/menu_list_background.png` |
| Level loaded | `minecraft:textures/gui/inworld_menu_list_background.png` |

The pre-world list PNG is 16x16. Lists use the same level-aware header/footer pair described above.

### Direct consumers and exceptions

Some classes bypass the level-aware selector:

- `CreateWorldScreen#renderMenuBackground` draws `minecraft:textures/gui/tab_header_background.png` (16x16) across its header, then uses the normal menu background below it.
- `TabButton` draws `Screen.MENU_BACKGROUND` directly.
- `TabNavigationBar` draws `Screen.HEADER_SEPARATOR` directly.
- NeoForge `ScrollPanel` draws `Screen.MENU_BACKGROUND` directly.
- `WinScreen` uses the normal `Screen.MENU_BACKGROUND` directly for non-poem credits even though the screen normally occurs after play; its poem path uses the end-portal render type.

The first four are aligned with the pre-world/Create World/Mods goals but require live verification. `WinScreen` is a deliberate policy decision: replacing `MENU_BACKGROUND` makes non-poem end credits black even if a level is still present. Required content is preserved, but this is the one known resource-first exception to a strict “all in-world pixels unchanged” rule.

`GenericMessageScreen` reimplements the usual panorama/blur/menu sequence instead of calling `super`, which is one concrete reason not to depend on `BackgroundRendered` events for coverage.

## NeoForge screen hooks

Relevant hooks in the pinned source are:

| Hook | Behavior | Branding use |
|---|---|---|
| `ScreenEvent.Opening` | Cancellable; exposes current and new screen | Observation or narrow replacement only; unnecessary for baseline plan |
| `ScreenEvent.Init.Pre/Post` | Before/after widget initialization | Not needed for background/logo resource replacement |
| `ScreenEvent.Render.Pre` | Cancellable; cancellation skips the whole screen | Not suitable for a background-only policy |
| `ScreenEvent.Render.Post` | Non-cancellable; after full screen draw | Preferred small `Version 18` title-only overlay |
| `ScreenEvent.BackgroundRendered` | After `Screen#renderBackground` | Deprecated for removal since 21.0 due inconsistent firing; do not use as primary policy |
| `ScreenEvent.Closing` | Screen close notification | No current use |

An event-only black background is not reliable: `Render.Pre` can only underlay a background that vanilla subsequently covers; cancelling it removes widgets; `Render.Post` would cover widgets; and `BackgroundRendered` is deprecated and demonstrably not fired by every screen implementation.

## Resource precedence

The pinned NeoForge `ResourcePackLoader` uses `Pack.Position.TOP` for mod packs and aggregates hidden client mod packs into required `mod_resources`, also at top position. `ClientModLoader` populates the client resource repository with `PackType.CLIENT_RESOURCES`. Therefore resources shipped at `src/main/resources/assets/minecraft/...` can override vanilla resources at the same ID.

External user-selected resource packs may still override mod resources depending on the user's pack stack. Acceptance testing should check the default pack stack; compatibility documentation should not promise control over arbitrary higher-priority user packs.

## Recommended architecture for later milestones

1. Preserve `TitleScreenBackgroundMixin` and `chest_sequence.png` byte-for-byte.
2. Replace both title logo resources with the selected transparent `UltimaCraft` wordmark and replace the edition resource with a transparent image.
3. Render `Version 18` in a client-only `ScreenEvent.Render.Post` listener restricted to `TitleScreen`.
4. Override `minecraft:texts/splashes.txt` for the normal splash corpus, with the hardcoded exceptions explicitly accepted or handled later.
5. Make the pre-world menu, list, separators, and Create World tab-header resources true opaque `#000000`, leaving all `inworld_*` resources untouched.
6. Do not replace panorama faces or `panorama_overlay.png` unless live transition testing proves an uncovered flash. Opaque menu resources should cover the panorama in normal non-title screen rendering.
7. Validate every direct consumer in `SCREEN_MATRIX.md`. Use a narrow client-only hook or mixin only for a proven exception that resources cannot address.

This plan does not require a new mixin for the basic title, splash, or normal pre-world background work. It retains the existing protected title mixin.

## Baseline constraints carried forward

- Client-only code must never be referenced from a common/server class.
- Functional widgets, icons, list entries, field backgrounds, focus states, and accessibility cues are not decorative background resources and must remain intact.
- Legal notices, copyright lines, third-party acknowledgements, accessibility text, NeoForge branding, and mandatory runtime attribution must remain visible.
- No production asset should be generated or integrated until its logical sampling area and alpha requirements are validated.
- Gate 0 discovered pre-existing build and asset warnings; details are in `IMPLEMENTATION_LOG.md` and must not be misattributed to later branding changes.
