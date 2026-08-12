# UltimaCraft Client Branding Asset Manifest

## Manifest rules

This file records protected assets and the exact resource targets discovered in Milestone 0, together with runtime assets accepted by later gates. SHA-256 values are uppercase hexadecimal over the working-tree bytes on 2026-08-11.

Source/candidate images must not be placed under `src/main/resources`. Only selected, validated runtime assets belong in the packaged resource tree.

## Protected existing title background

| Role | Project path | Properties | SHA-256 | Milestone 0 state |
|---|---|---|---|---|
| Approved background animation | `src/main/resources/assets/britannia_mod/textures/screens/chest_sequence.png` | PNG, 992x6448, RGB/24-bit, no alpha, 5,303,243 bytes | `7187FB8CA89FF959CB57022BEC15C113EE44FC4F15B2839B02D0D6E7217B0B30` | Unmodified |
| Title background renderer | `src/main/java/com/seggellion/britannia_mod/mixin/TitleScreenBackgroundMixin.java` | Client mixin targeting `TitleScreen` | `70C9D1EAE4C95F42DC08E8029002FB890B782452E2517E263A3E2378C517AD62` | Unmodified |
| Mixin registration | `src/main/resources/britannia_mod.mixins.json` | Retains title mixin and registers the loading-screen panorama guard | `585E7F65AD1543F8B81F83FFD15702D54BB2042E25C5F994A2FA593E1B3098A6` | Milestone 5 correction; title registration retained |

### Protected render behavior

`TitleScreenBackgroundMixin` uses:

- resource id `britannia_mod:textures/screens/chest_sequence.png`;
- 15 vertically sampled frames;
- 100 ms per frame;
- animation reset at `TitleScreen#init` head;
- preferred draw immediately before `TitleScreen#render` calls `Screen#render`;
- a tail fallback only if the preferred injection did not run;
- full GUI-width/full GUI-height rendering;
- freeze on frame index 14 after the opening sequence completes.

Visual inspection shows a wooden chest opening to a red-lined medallion, surrounded by black. The image height is not evenly divisible by 15; the renderer samples normalized equal vertical bands. That behavior is part of the protected baseline and must not be “corrected” incidentally.

Any later milestone touching title rendering must re-run all three hashes above. A changed hash is a stop condition unless the change was explicitly approved as a necessary refactor that reproduces the same appearance.

## Title runtime assets

| Runtime role | Project path | Dimensions | SHA-256 | Milestone 2 state |
|---|---|---:|---|---|
| UltimaCraft normal wordmark | `src/main/resources/assets/minecraft/textures/gui/title/minecraft.png` | 1024x256 | `865EFD5DC70AEA7F1839919BC306B28C690F71DF33E50E5682C2BAB237655A3E` | Implemented; transparent original aged-gold wordmark |
| UltimaCraft rare-logo parity | `src/main/resources/assets/minecraft/textures/gui/title/minceraft.png` | 1024x256 | `865EFD5DC70AEA7F1839919BC306B28C690F71DF33E50E5682C2BAB237655A3E` | Implemented; byte-identical to normal path |
| Suppress edition layer | `src/main/resources/assets/minecraft/textures/gui/title/edition.png` | 512x64 | `CA5DE485B94EC28D85E83A70DF6704E4D08BF9340C5C7FF726242BB295B70235` | Implemented; every pixel fully transparent |

The vanilla renderer displays a 256x44 normal/rare title from a 256x64 logical texture area and a 128x14 edition layer from 128x16 logical space. `Version 18` must remain code-rendered text; it must not be baked into these PNGs.

### Wordmark generation contract

- Working canvas: 1024x256 transparent PNG.
- Exact visible text: `UltimaCraft`; no extra words or scenery.
- Direction: high-legibility original medieval/high-fantasy lettering, restrained carved stone/aged gold, strong horizontal silhouette, subtle depth, clean transparent edges.
- Exclusions: no Minecraft logo imitation, no official Ultima logo tracing, no landscape, shield, banner, character, background, or decorative frame.
- Selection criterion: live readability at the actual 256-pixel title display, not full-resolution detail.
- Final PNG must have real alpha and comfortable transparent padding; no accidental matte color or opaque canvas.

### Milestone 2 generation and selection record

- Generation mode: built-in image generation with a flat `#00FF00` chroma-key background, followed by the installed `remove_chroma_key.py` helper.
- Three exact-spelling candidates were generated: charcoal stone/aged gold, bright aged gold/dark bronze, and ivory stone/antique brass.
- The aged-gold/dark-bronze candidate was selected because it retained the strongest silhouette, widest useful coverage, and clearest letter identity at the actual 256x64 logical texture size.
- Selected generated source: 1774x887 RGBA after conversion; source alpha bounds `(82,307)` through `(1692,582)` after one-pixel edge contraction.
- Shipped wordmark: normalized to a 1024x256 RGBA canvas; visible alpha bounds `(43,8)` through `(979,167)`; 82,544 non-zero-alpha pixels and 17,530 antialiased edge pixels.
- Rows 176 through 255 are fully transparent because vanilla displays only the top 44/64 logical rows.
- Candidate sources, keyed images, normalized previews, contact sheet, and live screenshots remain under ignored `build` output and are not packaged.
- Exact live framebuffer validation passed at 1280x720/Auto, 1920x1080/2, 2560x1440/3, and 3440x1440/4.

`ClientBrandingTitleAssetTest` locks the three runtime hashes, dimensions, alpha contract, safe bounds, normal/rare parity, edition transparency, and exact title-directory file set.

### Code-rendered title subtitle

Milestone 3 adds no image asset. `TitleBrandingRenderer` listens only to client `ScreenEvent.Render.Post` events for `TitleScreen` and draws the literal `Version 18` through the vanilla font. `TitleBrandingLayout` centers the line from the current GUI width and fixes its baseline at GUI Y 76: vanilla logo top 30 + logo height 44 + a 2-pixel gap. The color is opaque warm parchment `#F0E2B6` with the vanilla text shadow. This keeps the subtitle responsive to framebuffer and GUI-scale changes without baking text into either wordmark PNG.

Exact live framebuffer validation passed at 1280x720/Auto, 1920x1080/2, 2560x1440/3, and 3440x1440/4. The three title PNG hashes and all three protected-background hashes remained unchanged after Milestone 3.

## Black pre-world resources

All black assets must be true opaque `#000000` with no alpha variation, gradient, noise, color profile surprise, or decorative marks.

| Purpose | Runtime project path | Dimensions | SHA-256 | Milestone 1 state |
|---|---|---:|---|---|
| Standard pre-world menu | `src/main/resources/assets/minecraft/textures/gui/menu_background.png` | 16x16 | `60A2CE1FED7CF673C4A141192E2CF99828A85787770D2F60B8B9422112A8E26C` | Implemented; `inworld_menu_background.png` remains absent |
| Pre-world list body | `src/main/resources/assets/minecraft/textures/gui/menu_list_background.png` | 16x16 | `60A2CE1FED7CF673C4A141192E2CF99828A85787770D2F60B8B9422112A8E26C` | Implemented; `inworld_menu_list_background.png` remains absent |
| Pre-world header separator | `src/main/resources/assets/minecraft/textures/gui/header_separator.png` | 32x2 | `33F00038999705206F7D85B1185F402D01180692AF08346D22DCC047917D950B` | Implemented; `inworld_header_separator.png` remains absent |
| Pre-world footer separator | `src/main/resources/assets/minecraft/textures/gui/footer_separator.png` | 32x2 | `33F00038999705206F7D85B1185F402D01180692AF08346D22DCC047917D950B` | Implemented; `inworld_footer_separator.png` remains absent |
| Create World tab header | `src/main/resources/assets/minecraft/textures/gui/tab_header_background.png` | 16x16 | `60A2CE1FED7CF673C4A141192E2CF99828A85787770D2F60B8B9422112A8E26C` | Implemented; tab labels and focus remain legible |

All five files are deterministic, opaque-black PNGs: every pixel is exactly ARGB `0xFF000000`. `ClientBrandingBackgroundPolicyTest` enforces the dimensions and pixel contract, rejects any project override of the four `inworld_*` resources or seven panorama resources, and rechecks the three protected title hashes.

The assembled Milestone 1 JAR contains all five paths above and no client-branding `inworld_*` or panorama override.

Milestone 5 completed the full pre-world screen sweep without discovering another runtime asset target. A later live issue report established that loading/status frames could still expose the panorama before the normal opaque menu texture covered it. The correction adds no image asset: `LoadingScreenPanoramaMixin` replaces the panorama call with an immediate opaque-black fill only for the six loading/status screen families recorded in `SCREEN_MATRIX.md`. Title, onboarding, ordinary menus, portal/end branches, all `inworld_*` resources, and all panorama resources remain untouched. The accepted runtime asset set therefore remains exactly these five black PNGs.

The vanilla pre-world menu/list assets are translucent; replacing them with opaque black hides the panorama drawn beneath standard non-title screens. No panorama face or overlay asset is planned at baseline because relying on `panorama_overlay.png` alpha is specifically avoided.

## Splash corpus target

| Role | Exact project path | Contract | Milestone 4 state |
|---|---|---|---|
| Normal title splash corpus | `src/main/resources/assets/minecraft/texts/splashes.txt` | UTF-8 text, one trimmed non-empty original line per row; no normal vanilla corpus lines | Implemented; 160 lines, maximum 45 code points, SHA-256 `02A45F05AA83B51792C8D5D093FC7619E64B572274730881CB4383A6D32A7E17` |

The corpus is original microcopy researched and reviewed in `SPLASH_RESEARCH.md` and `SPLASH_EDITORIAL.md`. Minecraft's existing `SplashManager` continues to trim the resource, randomly select normal lines, and hand them to the vanilla yellow, angled, pulsing `SplashRenderer`; no title renderer or coordinate code changed.

`ClientBrandingSplashCorpusTest` rejects a UTF-8 byte-order mark, malformed UTF-8, empty or untrimmed rows, control characters, exact/case/canonical duplicates, lines over the 55-code-point editorial limit, and normal vanilla 1.21.1 leakage. Its test-only fixture contains 441 sorted canonical SHA-256 prefixes derived from all 446 vanilla source rows, SHA-256 `FA7B8DE5D990BFA63A6C96271704BCF22BF85CCD8AE398DA87BB261A32167136`; it contains no vanilla copy and is not packaged.

The regular assembled JAR contains exactly one `assets/minecraft/texts/splashes.txt` entry. Its extracted bytes match the working-tree corpus hash. The test fixture and both research documents are absent from the runtime JAR.

Six independent live starts produced six different corpus lines across the required title matrix and two additional 1280x720/Auto rotations. Every observed line was readable, correctly encoded, and clear of `Version 18`. Hardcoded Christmas, New Year, Halloween, and rare username greetings are not assets; those existing renderer exceptions remain intentionally unchanged and are documented in `INVENTORY.md`.

## Vanilla baseline resources intentionally not targeted

| Resource family | Exact IDs | Reason |
|---|---|---|
| Panorama faces | `minecraft:textures/gui/title/background/panorama_0.png` through `_5.png` | Opaque menu resources should cover them on nested screens; protected title uses a custom renderer |
| Panorama overlay | `minecraft:textures/gui/title/background/panorama_overlay.png` | Avoid NeoForge 1.21.1 early-window alpha bug; not needed for chosen architecture |
| In-world backgrounds | `inworld_menu_background.png`, `inworld_menu_list_background.png`, `inworld_header_separator.png`, `inworld_footer_separator.png` | Must remain vanilla/current to preserve in-world UI |
| Functional GUI atlas/sprites | buttons, sliders, fields, list entries, icons, focus/selection/accessibility sprites | Outside branding scope and required for usability |

## Gate 6 automated validation coverage

- `ClientBrandingTitleAssetTest` parses the three title PNGs, locks their physical dimensions and hashes, verifies useful alpha/transparency and safe-area coverage, and requires the normal and rare wordmarks to be byte-identical.
- `ClientBrandingBackgroundPolicyTest` parses all five black PNGs, checks every pixel is opaque RGB `(0,0,0)`, locks dimensions, rejects `inworld_*` and panorama overrides, and protects the approved title-background hashes.
- `ClientBrandingSplashCorpusTest` validates strict UTF-8, size, trimming, readability, uniqueness, the 55-code-point editorial ceiling, and absence of every normal vanilla 1.21.1 splash.
- `ClientBrandingRuntimeContractTest` locks all eleven exact runtime resource paths, allows only the eight approved files under the mod's `assets/minecraft/textures/gui` override tree and only `splashes.txt` under its text tree, requires event subscribers to declare `Dist.CLIENT`, and requires both branding mixins to remain exclusively in the mixin configuration's `client` section.
- The regular production JAR contains the selected branding assets/classes and mixin registration. It contains no branding test class, vanilla-hash fixture, research document, candidate, prompt, contact sheet, or source-format image.
- The CI-defined dedicated GameTest server loaded the mod in `Env=SERVER`, passed all 349 required tests, and shut down without loading client-only branding code.

Resource reload and the complete resolution/GUI-scale visual matrix remain owner-facing Milestone 7 work and were not started by Gate 6.
