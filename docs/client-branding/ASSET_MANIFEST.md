# UltimaCraft Client Branding Asset Manifest

## Manifest rules

This file records protected assets and the exact resource targets discovered in Milestone 0, together with runtime assets accepted by later gates. SHA-256 values are uppercase hexadecimal over the working-tree bytes on 2026-08-11.

Source/candidate images must not be placed under `src/main/resources`. Only selected, validated runtime assets belong in the packaged resource tree.

## Protected existing title background

| Role | Project path | Properties | SHA-256 | Milestone 0 state |
|---|---|---|---|---|
| Approved background animation | `src/main/resources/assets/britannia_mod/textures/screens/chest_sequence.png` | PNG, 992x6448, RGB/24-bit, no alpha, 5,303,243 bytes | `7187FB8CA89FF959CB57022BEC15C113EE44FC4F15B2839B02D0D6E7217B0B30` | Unmodified |
| Title background renderer | `src/main/java/com/seggellion/britannia_mod/mixin/TitleScreenBackgroundMixin.java` | Client mixin targeting `TitleScreen` | `70C9D1EAE4C95F42DC08E8029002FB890B782452E2517E263A3E2378C517AD62` | Unmodified |
| Mixin registration | `src/main/resources/britannia_mod.mixins.json` | Registers title mixin in client list | `436DB321C6FB7CF10CA81849D367148D71E42AAEC757EC39AE2FE574FF9BF1B1` | Unmodified |

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

The vanilla pre-world menu/list assets are translucent; replacing them with opaque black hides the panorama drawn beneath standard non-title screens. No panorama face or overlay asset is planned at baseline because relying on `panorama_overlay.png` alpha is specifically avoided.

## Splash corpus target

| Role | Exact future project path | Contract | Status |
|---|---|---|---|
| Normal title splash corpus | `src/main/resources/assets/minecraft/texts/splashes.txt` | UTF-8 text, one trimmed non-empty original line per row; no normal vanilla corpus lines | Not created |

The future validator must reject empty lines, duplicates, case-insensitive duplicates, trailing whitespace, malformed encoding, over-limit lines, and known vanilla corpus leakage. Hardcoded Christmas/New Year/Halloween and username exceptions are not assets and are documented in `INVENTORY.md`.

## Vanilla baseline resources intentionally not targeted

| Resource family | Exact IDs | Reason |
|---|---|---|
| Panorama faces | `minecraft:textures/gui/title/background/panorama_0.png` through `_5.png` | Opaque menu resources should cover them on nested screens; protected title uses a custom renderer |
| Panorama overlay | `minecraft:textures/gui/title/background/panorama_overlay.png` | Avoid NeoForge 1.21.1 early-window alpha bug; not needed for chosen architecture |
| In-world backgrounds | `inworld_menu_background.png`, `inworld_menu_list_background.png`, `inworld_header_separator.png`, `inworld_footer_separator.png` | Must remain vanilla/current to preserve in-world UI |
| Functional GUI atlas/sprites | buttons, sliders, fields, list entries, icons, focus/selection/accessibility sprites | Outside branding scope and required for usability |

## Future asset validation checklist

- PNG parses successfully and reports the intended physical dimensions.
- Title images contain useful transparent pixels and no opaque background.
- Black assets are 100% opaque and every pixel is exactly RGB `(0,0,0)`.
- Normal and rare title assets have identical approved content/layout.
- No source-sized candidate, prompt output, contact sheet, or temporary image is packaged.
- Generated JAR contains only the selected runtime assets at their exact paths.
- Protected title hashes still match this manifest.
- Resource reload and multiple GUI scales do not reveal seams, matte edges, or one-frame panorama flashes.
