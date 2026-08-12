# UltimaCraft client-branding validation

Date: 2026-08-11

Milestone: 7 — live client acceptance

Status: **Gate 7 passed — owner visually approved**

Starting commit: `9b1530769597bdb8b9b312315fbd3b485520420a`

Milestone 8 has not started. The owner approved the evidence below on 2026-08-11.

## Environment and method

- Minecraft `1.21.1`
- NeoForge `21.1.72`
- Britannia/UltimaCraft mod `0.1.8`
- Microsoft OpenJDK `21.0.8+9-LTS`
- Windows 11, Intel UHD Graphics, OpenGL `4.6`
- Launch command: pinned Gradle 8.9 `runClient -Pdev --no-configuration-cache --console=plain`
- Screens were captured from Minecraft's framebuffer with `F2` after setting the exact client-area dimensions. Loading transitions were captured as a rapid desktop-frame burst because Minecraft's screenshot action is unavailable while those screens own the render loop.
- Evidence is intentionally ignored under `build/client-branding-validation/milestone-7`; it is not production or release content.

The existing local validation world, `New World`, was used for the in-world smoke pass. Each client run stopped cleanly with `BUILD SUCCESSFUL`. The original `run/options.txt` was restored byte-for-byte after validation; its before/after SHA-256 is `DCB76C9729A6C4F275D656F0BCF42706DECB7B4EC89E15A90159980D13790AC7`.

## Title-screen resolution and GUI-scale matrix

| Client area | GUI scale | Evidence | SHA-256 | Result |
| --- | --- | --- | --- | --- |
| 1280×720 | Auto | `title-1280x720-auto-framebuffer.png` | `1D80910B329AA6BA312537288E839C9AAF604E10E8BC69DC1D9BA0F8DB84967E` | Pass |
| 1920×1080 | 2 | `title-1920x1080-scale2-framebuffer.png` | `C406B9E282733D22324AEA5D1904DAFD5609C7C92A81ED2C63A512CBEADDA71B` | Pass |
| 2560×1440 | 3 | `title-2560x1440-scale3-framebuffer.png` | `ADC6FAF5387227455882937D21F4EC95123727509E0F03F07FEEDAE31C1A568C` | Pass |
| 3440×1440 | 4 | `title-3440x1440-scale4-framebuffer.png` | `E5B02D34EBEC884A84BE9CA0ED8BFCB9D7D6002190D031A16E510A915C147699` | Pass |

At every matrix point:

- the protected chest/medallion background is centered, un-stretched, and free of panorama imagery;
- the complete UltimaCraft wordmark and `Version 18` are visible;
- a rotating custom splash is visible and does not collide with the wordmark, version, controls, or framebuffer edge;
- `Ultimacraft website` occupies the former Realms slot;
- Singleplayer, Multiplayer, Mods, Options, language, accessibility, Quit, version/mod attribution, and copyright controls remain visible and readable;
- resizing did not expose seams, stale frames, clipped controls, or vanilla panorama content.

The exact website label, `https://www.ultimacraft.com` URI, supported Minecraft platform-browser call, Realms-only replacement identity, and preserved button geometry remain covered by the green Milestone 5 tests. Live navigation exercised Singleplayer, Options, Accessibility, Resource Packs, Select World, and return/escape paths without failure.

## Resource reload

`F3+T` completed successfully. The log recorded the expected resource set (`vanilla`, `mod_resources`, Britannia, GeckoLib, and NeoForge), rebuilt the GUI atlas, and returned to the branded title screen. `title-after-resource-reload-1920x1080.png` (SHA-256 `15ECEF8AD5B25DB3449C17045A4E325E467285703847A1E418FB828E4708AFEB`) confirms that the title background, wordmark, version, splash, website button, and remaining controls survived reload without a panorama fallback, seam, or matte.

The development client's existing missing-model, missing-sound, optional-config, and development-refmap warnings remained the inherited repository baseline. No branding-specific runtime error occurred.

## Pre-world menus

Exact 1920×1080 framebuffer captures were reviewed for:

- `preworld-options-1920x1080.png`;
- `preworld-accessibility-1920x1080.png`;
- `preworld-resource-packs-1920x1080.png`;
- `preworld-select-world-1920x1080.png`.

All four use the required opaque black pre-world background. Labels, sliders, pack icons, world preview/list content, focus state, and action controls remain visible and readable. No title artwork, panorama, partially cleared frame, or black overlay over functional content was observed. This final spot-check supplements the complete pre-world screen matrix validated during Milestone 5.

## Loading transition

`loading-world-00.png` through `loading-world-49.png` form a continuous 50-frame burst from Select World into `New World`. The sequence contains:

- `Preparing Resources…` on opaque black;
- a fully black intermediate frame;
- `0%` on opaque black;
- the white progress-square state through `100%` on opaque black;
- no panorama, title art, stale world frame, or non-black corner flash.

A programmatic audit sampled `(100,100)`, `(100,600)`, `(1180,100)`, and `(1180,600)` in every 1280×720 frame: **50 frames, 200 samples, 0 non-black samples**. Status text and progress rendering remain intact.

## In-world regression smoke pass

The disposable local world loaded and ran normally. The following exact 1280×720 framebuffer captures were inspected:

- `inworld-hud-1280x720.png`: normal world render, hand, HUD, hotbar, health, hunger, and onboarding toast;
- `inworld-inventory-1280x720.png`: normal inventory UI over the dimmed world;
- `inworld-pause-1280x720.png`: normal blurred/dimmed world-backed pause menu and controls.

The black pre-world/loading treatment did not leak into gameplay, inventory, or pause rendering. Joining, pausing, inventory navigation, saving, and shutdown completed without a branding regression.

## Carry-forward automated and protection evidence

Milestone 7 changes documentation only; it does not alter production Java, resources, build configuration, or gameplay behavior. The immediately preceding Gate 6 evidence remains applicable:

- all 24 focused branding tests passed;
- the clean build reproduced only the known 21 inherited banner/scaffold failures out of 1,812 tests;
- the dedicated GameTest server loaded in `Env=SERVER` and all 349 required tests passed;
- the regular JAR contained the approved branding resources/classes and no candidate or test-only files;
- protected hashes were `7187FB8CA89FF959CB57022BEC15C113EE44FC4F15B2839B02D0D6E7217B0B30` for `chest_sequence.png`, `70C9D1EAE4C95F42DC08E8029002FB890B782452E2517E263A3E2378C517AD62` for `TitleScreenBackgroundMixin.java`, and `585E7F65AD1543F8B81F83FFD15702D54BB2042E25C5F994A2FA593E1B3098A6` for `britannia_mod.mixins.json`.

## Gate 7 owner checklist

- [x] Four required resolution/GUI-scale combinations captured and internally reviewed.
- [x] Protected title background, UltimaCraft wordmark, `Version 18`, splash, and controls remain collision-free.
- [x] Vanilla panorama absent from the title and loading flow.
- [x] `Ultimacraft website` replacement is present; its exact target/action remain locked by tests.
- [x] Representative pre-world menus are opaque black and readable.
- [x] Resource reload preserves branding.
- [x] Fifty-frame world-loading transition contains no non-black corner flash.
- [x] In-world HUD, inventory, and pause rendering remain normal.
- [x] Local options restored exactly and temporary control tooling removed.
- [x] Owner has visually approved the evidence.

Owner decision: **Approved on 2026-08-11**
