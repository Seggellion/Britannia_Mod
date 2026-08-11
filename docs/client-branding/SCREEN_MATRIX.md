# UltimaCraft Client Branding Screen Matrix

## Policy key

The desired policy is state-based:

```text
TitleScreen                         -> preserve approved chest-sequence background
not TitleScreen and level == null  -> opaque black decorative background
level != null                      -> preserve vanilla/current behavior
```

“Resource-first” below means exact `assets/minecraft` overrides for the normal pre-world `menu_background`, `menu_list_background`, header/footer separators, and where needed `tab_header_background`. It does not mean changing button, slider, icon, text-field, focus, selection, or accessibility assets.

This matrix is source-verified for Minecraft 1.21.1/NeoForge 21.1.72. Milestone 1 live coverage is recorded below; remaining rows marked “verify” are explicit acceptance cases for later cumulative sweeps, not assumptions.

## Milestone 1 live verification

All captures used a fresh title-session route at GUI scale 2. Black backgrounds remained opaque at 1938x1038, while controls, labels, list content, focus outlines, scrollbars, pack icons, and world thumbnails remained visible.

| Screen/state | Milestone 1 result |
|---|---|
| Main title | Pass - protected chest/medallion animation and vanilla title UI remained visible; no black policy regression |
| Options | Pass - standard controls, FOV slider, and Done button remained legible |
| Language | Pass - language list, selected row, scrollbar, explanatory text, and buttons remained legible |
| Accessibility | Pass - controls, sliders, focus, and footer remained legible |
| Video Settings | Pass - option grid, sliders, scrollbar, and footer remained legible |
| Controls | Pass - action buttons, focus outline, and footer remained legible |
| Resource Packs | Pass - available/selected pack lists, icons, text, focus, and footer remained legible |
| Select World | Pass - populated list, thumbnail, metadata, search field, and action buttons remained legible |
| Create World | Pass - black tab header/body/footer with tabs, fields, and Create/Cancel controls visible |
| Join Multiplayer | Pass - server list/status area and enabled/disabled controls remained distinguishable |
| NeoForge Mods list | Pass - list, details, search field, and controls remained legible |
| Loaded single-player world | Pass - world, HUD, crosshair, onboarding hint, and normal gameplay rendering remained unchanged; no black overlay |

Ignored screenshots and the disposable validation world are runtime evidence only and are not packaged or committed. The deterministic policy test separately enforces the absent `inworld_*` and panorama overrides.

## Milestone 2 title matrix

Minecraft framebuffer screenshots, rather than desktop crops, verified the exact client render size in every case.

| Resolution | GUI scale | Result |
|---:|---:|---|
| 1280x720 | Auto | Pass - complete centered wordmark, exact spelling, no clipping, edition layer absent, controls visible |
| 1920x1080 | 2 | Pass - wordmark readable at the smaller logical title footprint; splash and controls preserved |
| 2560x1440 | 3 | Pass - title silhouette, transparent edges, protected background, and layout preserved |
| 3440x1440 | 4 | Pass - ultrawide centering and scale preserved without stretching or clipping |

Across the matrix, no vanilla Minecraft title artwork remained. The separate bottom-corner Minecraft/NeoForge version and copyright strings were intentionally preserved as runtime/legal attribution. The protected chest-to-medallion background, vanilla splash rendering, title buttons, mod-added button, language/accessibility controls, and attribution link remained present. `Version 18` is intentionally absent until Milestone 3.

## Milestone 3 subtitle matrix

Minecraft framebuffer screenshots again verified the exact client render size. Each capture was taken only after client resource initialization completed.

| Resolution | GUI scale | Result |
|---:|---:|---|
| 1280x720 | Auto | Pass - `Version 18` centered under the wordmark with clear space before the first button |
| 1920x1080 | 2 | Pass - subtitle remained readable and separate from the rotating splash |
| 2560x1440 | 3 | Pass - title-relative position and centered alignment remained stable |
| 3440x1440 | 4 | Pass - ultrawide layout preserved with no clipping, stretching, or collision |

Across the matrix, the exact visible copy was `Version 18`. The code-rendered line remained centered on the vanilla 256-pixel title box at GUI Y 76, two logical pixels below the title artwork. The wordmark, rotating splash, title controls, runtime/legal attribution, and protected chest-to-medallion background remained unchanged. Ignored evidence is stored under `build/client-branding-validation/milestone-3` and is not packaged or committed.

## Milestone 4 splash matrix

Each matrix capture came from an independent client start after the final resource-initialization signal. Minecraft framebuffer screenshots again verified exact dimensions.

| Resolution | GUI scale | Observed corpus line | Result |
|---:|---:|---|---|
| 1280x720 | Auto | `Valor trains offshore.` | Pass - readable, complete, and clear of `Version 18` |
| 1920x1080 | 2 | `Yew keeps the prison offshore.` | Pass - normal angle/pulse placement and no collision |
| 2560x1440 | 3 | `Skara Brae ferry departs eventually.` | Pass - complete text and stable title-relative layout |
| 3440x1440 | 4 | `Seekers read the fine print.` | Pass - ultrawide title layout and splash placement preserved |

Two additional independent 1280x720/Auto starts selected `Truth brought receipts.` and `Cove is not on the moongate menu.` All six observed values are exact, distinct rows in the shipped corpus. No vanilla corpus text, malformed character, empty splash, or username/date exception appeared during the August 11 validation session.

Across all six starts, the UltimaCraft wordmark, exact `Version 18` subtitle, title controls, runtime/legal attribution, and protected chest-to-medallion background remained present. The vanilla yellow, angled, pulsing splash treatment was intentionally retained. Ignored evidence is stored under `build/client-branding-validation/milestone-4`; the temporary capture harness was removed and the local development GUI scale was restored to 2.

## Milestone 5 complete pre-world sweep

The final sweep combined live 1920x1080 framebuffer inspection with Minecraft 1.21.1/NeoForge 21.1.72 source inspection. "Menu resource set" below means the five Milestone 1 opaque-black overrides: `menu_background`, `menu_list_background`, `header_separator`, `footer_separator`, and `tab_header_background`. A post-sweep live report found that loading/status frames could still expose the panorama before that resource layer covered it. The accepted correction adds a narrow `Screen#renderPanorama` mixin guard for loading/status classes; it adds no panorama or `inworld_*` resource and does not intercept the title, onboarding, ordinary menus, or portal/end branches.

### Main navigation and options

| Screen/state | Old source/renderer | Final mechanism | Black result and controls |
|---|---|---|---|
| Main title | Protected `TitleScreenBackgroundMixin`; `TitleScreen#renderBackground` is empty | Explicit title exception | Live pass: chest/medallion, wordmark, subtitle, splash, buttons, and attribution preserved |
| Title website entry | Vanilla `menu.online` button opens `RealmsMainScreen` | `ScreenEvent.Init.Post` replaces that exact button in place | Live pass: exact `Ultimacraft website` label in the Realms slot; click uses Minecraft's platform browser API for `https://www.ultimacraft.com` |
| Options from title | `Screen#renderBackground` -> panorama/blur -> `MENU_BACKGROUND` | Opaque `menu_background` | Live pass: black; sliders, buttons, disabled states, tooltips, and Done visible |
| Language | Standard background plus list | Menu resource set | Cumulative live pass: black list/body/separators; selected row, scrollbar, narration text, and buttons visible |
| Accessibility | `OptionsSubScreen` standard background | Menu resource set | Cumulative live pass: black; controls, sliders, focus, and footer visible |
| Video Settings | `OptionsSubScreen` standard background | Menu resource set | Cumulative live pass: black; option grid, sliders, scrollbar, and footer visible |
| Controls | `OptionsSubScreen` standard background | Menu resource set | Cumulative live pass: black; navigation controls and footer visible |
| Key Binds | Standard background plus keybind list | Menu/list/separator resources | Source-verified from the live Controls route; conflict text, focus, and list sprites are not overridden |
| Mouse Settings | `OptionsSubScreen` standard background | Opaque `menu_background` | Source-verified from the live Controls route; functional option widgets are not overridden |
| Sound Settings | `OptionsSubScreen` standard background | Opaque `menu_background` | Live pass: black; volume controls and Done remain visible |
| Chat Settings | `OptionsSubScreen` standard background | Opaque `menu_background` | Source-verified from live Options; chat widgets are outside the overridden asset set |
| Skin Customization | `OptionsSubScreen` standard background | Opaque `menu_background` | Live pass: black; model/part toggles and Done remain visible |
| Resource Packs | Standard background plus two pack lists | Menu/list/separator resources | Cumulative live pass: black; icons, compatibility text, list controls, and footer visible |
| Telemetry/Data Collection | Standard background plus disclosure list | Menu/list/separator resources | Source-verified; disabled in the development session, with disclosure/legal widgets untouched |
| Credits and Attribution selector | Standard inherited background | Opaque `menu_background` | Live pass: black; Credits, Attribution, Licenses, and Done visible |
| Credits roll | `WinScreen` non-poem path directly renders `Screen.MENU_BACKGROUND` | Opaque `menu_background` | Live pass: black rolling presentation; UltimaCraft heading and credit content retained |
| Online Options | Standard inherited background | Opaque `menu_background` | Live pass: black; Realms/server-listing controls and tooltip visible |
| NeoForge Mods list | NeoForge list screen with standard/direct menu consumers | Menu resource set | Live pass: black; mod list, search, sorting, Config, folder, and Done controls visible |

### World selection and creation

| Screen/state | Old source/renderer | Final mechanism | Black result and controls |
|---|---|---|---|
| Select World | Standard background plus `WorldSelectionList` | Menu/list/separator resources | Live pass: black; thumbnail, metadata, search field, and enabled/disabled actions visible |
| Create World | Standard body/footer plus direct `tab_header_background` consumer | Menu resource set including tab header | Live pass: black header/body/footer; name field, tabs, Create, and Cancel visible |
| Game/World/More tabs | `CreateWorldScreen` tab content over the same background | Menu resource set | Live pass on all three tabs; tab focus and settings visible |
| Game Rules | Standard background and scrolling rule list | Menu/list/separator resources | Live pass: black; categories, validation tooltip, values, scrollbar, Done, and Cancel visible |
| Experiments | Standard background and experiment entries | Menu/list/separator resources | Live pass: black; warning copy, toggles, Done, and Cancel visible |
| Data Packs during creation | `PackSelectionScreen` lists | Menu/list/separator resources | Live pass: black; available/selected packs, icons, folder, and Done visible |
| Backup/restore/recreate confirmations | `ConfirmScreen` family | Opaque `menu_background` | Source-verified; destructive wording/buttons remain normal functional widgets |
| Data-pack load failure/safe mode | Standard or `GenericMessageScreen` menu draw | Opaque `menu_background` | Source-verified, including direct menu draw; status copy and actions remain above black |

### Multiplayer, Realms, messages, and notices

| Screen/state | Old source/renderer | Final mechanism | Black result and controls |
|---|---|---|---|
| Multiplayer safety warning | `SafetyScreen` standard background | Opaque `menu_background` | Live pass: black; warning, checkbox, Proceed, and Back visible and operable |
| Join Multiplayer | Standard background plus server list | Menu/list/separator resources | Live pass: black; scanning status and enabled/disabled server actions visible |
| Add/Edit Server | Standard background with fields | Opaque `menu_background` | Live pass: black; name/address fields, resource-pack control, Done, and Cancel visible |
| Direct Connection | Standard background with address field | Opaque `menu_background` | Live pass: black; address field, Join, and Cancel visible |
| Connecting | `ConnectScreen` standard background/status | Loading panorama guard plus opaque `menu_background` | Policy-tested: panorama call becomes immediate black; status/cancel widgets remain untouched |
| Disconnected/error | `DisconnectedScreen` standard background | Opaque `menu_background` | Live pass using closed loopback port: black; reason and Back to Server List visible |
| Receiving/loading level, reason `OTHER` | Explicit panorama/blur/menu draw | Loading panorama guard plus opaque `menu_background` | Policy-tested: panorama call becomes immediate black; portal/end reasons never call it and retain their special branches |
| Generic waiting/progress | Standard background/status draw | Loading panorama guard plus opaque `menu_background` | Policy-tested; status text, progress, actions, and narration remain functional |
| Realms main/error | `RealmsScreen` standard rendering | Opaque `menu_background` | Live pass on the reachable invalid-session state: black; error copy and OK visible |
| Realms configuration/invite/reset/backup | Realms `Screen` subclasses with standard backgrounds/lists | Menu resource set | Source-verified; authenticated-only variants were not reachable in the offline development session |
| Realms notification overlay on title | Transparent `RealmsNotificationsScreen` over title | Explicit transparent-title exception | Source-verified: no background draw, so the protected title remains visible |
| Generic message/notice | `GenericMessageScreen` explicitly draws panorama/blur/menu | Loading panorama guard plus opaque `menu_background` | Live pass during world open: solid black with progress/status content; message/narration remains above it |
| Confirmation/alert | `ConfirmScreen`/`AlertScreen` standard background | Opaque `menu_background` | Source-verified; action semantics and widgets remain untouched |
| Accessibility onboarding | Standard pre-world flow | Opaque `menu_background` | Live pass: black; guidance, narration affordances, and actions visible |
| Quick Play failure/warning | Standard/generic message flow | Opaque `menu_background` | Source-verified through the standard/generic routes; launch-only variant was not forced |
| Mojang/NeoForge startup/error notices | Loader/runtime-owned renderer | No broad replacement | Safely replaceable standard screens inherit black; required loader diagnostics outside Minecraft `Screen` remain intentionally untouched |

### Transition and gameplay guards

- A title-to-Options transition was sampled for 40 consecutive desktop frames at 25 ms intervals. Five exposed-background points per frame (200 samples total) were exactly RGB `(0,0,0)`; no one-frame panorama flash appeared.
- `LoadingScreenPanoramaMixin` intercepts only the panorama call for `GenericMessageScreen`, `ReceivingLevelScreen`, `ConnectScreen`, `LevelLoadingScreen`, `GenericWaitingScreen`, and `ProgressScreen`, drawing opaque black before any status content. Live world-open evidence shows a solid-black progress screen with no panorama.
- Live in-world captures passed for the normal world/HUD, inventory, pause menu, and Options opened from pause. They retained the world-backed/blurred vanilla presentation rather than becoming black.
- Container rendering remains on `AbstractContainerScreen`'s world/GUI path. Project-owned gameplay screens either supply their own renderer or, as with `MenuScreen`, deliberately suppress inherited background rendering. No broad post-render fill can reach them.
- All four `inworld_*` resources and all panorama resources remain absent. Portal/end transition branches and the end-poem portal presentation remain unchanged.

Gate 5 coverage, including the post-sweep correction, therefore closes every row in this matrix as live-passed, cumulatively live-passed, or explicitly policy/source-verified where authentication/launch/error setup was impractical. Correction evidence is stored under `build/client-branding-validation/milestone-5-correction` and is not packaged or committed.

## Main and core navigation

| Screen/state | Representative class | Current background mechanism | Desired result | Later implementation/verification |
|---|---|---|---|---|
| Main title | `TitleScreen` | `TitleScreen#renderBackground` is empty; protected `TitleScreenBackgroundMixin` draws `chest_sequence.png` during `render` | Existing animation unchanged | Do not apply black resources or a generic event overlay here; re-hash and live-compare |
| Options from title | `OptionsScreen` | Inherited `Screen#renderBackground`; panorama, blur, pre-world menu texture | Black | Resource-first; verify no title art/panorama flash |
| Language | `LanguageSelectScreen` | Standard background plus language selection list | Black | Resource-first menu/list/separators; verify selection/focus remains visible |
| Accessibility | `AccessibilityOptionsScreen` | `OptionsSubScreen`/standard background | Black | Resource-first; preserve narration/accessibility text and controls |
| Video Settings | `VideoSettingsScreen` | `OptionsSubScreen`/standard background | Black | Resource-first; preserve sliders, cycling buttons, tooltips |
| Controls | `ControlsScreen` | `OptionsSubScreen`/standard background | Black | Resource-first |
| Key Binds | `KeyBindsScreen` | Options background plus keybind list | Black | Resource-first menu/list/separators; verify conflict coloring |
| Mouse Settings | `MouseSettingsScreen` | `OptionsSubScreen`/standard background | Black | Resource-first |
| Sound Settings | `SoundOptionsScreen` | `OptionsSubScreen`/standard background | Black | Resource-first |
| Chat Settings | `ChatOptionsScreen` | `OptionsSubScreen`/standard background | Black | Resource-first |
| Skin Customization | `SkinCustomizationScreen` | `OptionsSubScreen`/standard background | Black | Resource-first |
| Resource Packs | `PackSelectionScreen` | Standard background plus two selection lists | Black | Resource-first menu/list/separators; preserve pack icons and compatibility warnings |
| Telemetry/Data Collection | `TelemetryInfoScreen` | Standard background plus content list | Black | Resource-first; preserve disclosure/legal content |
| Credits and Attribution | `CreditsAndAttributionScreen` | Inherited standard background and scrolling content/widgets | Black | Resource-first; preserve every attribution line and link |
| NeoForge Mods list | `net.neoforged.neoforge.client.gui.ModListScreen` | Standard background; NeoForge scroll panels may draw `Screen.MENU_BACKGROUND` directly | Black | Resource-first; verify mod list, logos, links, config buttons |

## Single-player and world creation

| Screen/state | Representative class | Current background mechanism | Desired result | Later implementation/verification |
|---|---|---|---|---|
| Select World | `SelectWorldScreen` | Standard background plus `WorldSelectionList` | Black | Resource-first menu/list/separators; preserve world thumbnails/status icons |
| Create World | `CreateWorldScreen` | Custom header `tab_header_background.png`, normal menu below, footer separator, tab bar/buttons | Black | Override/verify tab header plus normal resources; `TabButton` and `TabNavigationBar` are direct consumers |
| Create World: World/Game/More tabs | `CreateWorldScreen` tab content | Same Create World header/body | Black | Verify each tab, focus order, validation/error text |
| Game Rules | `EditGameRulesScreen` | Standard background and rule list | Black | Resource-first menu/list/separators; preserve validation state |
| Experiments | `ExperimentsScreen` | Standard background and pack/experiment entries | Black | Resource-first; preserve warning content |
| Data Packs during creation | `PackSelectionScreen`/world creation flow | Standard background and lists | Black | Same pack-screen policy |
| Backup/restore/recreate confirmations | `ConfirmScreen` and world-selection variants | Standard background | Black | Resource-first; preserve destructive-action wording |
| Data-pack load failure/safe mode | `DatapackLoadFailureScreen` and generic message flows | Standard or reimplemented panorama/blur/menu sequence | Black | Resource-first; verify because generic message screens do not all fire `BackgroundRendered` |

## Multiplayer, Realms, and network status

| Screen/state | Representative class | Current background mechanism | Desired result | Later implementation/verification |
|---|---|---|---|---|
| Multiplayer safety warning | `SafetyScreen` | Standard background | Black | Resource-first; preserve warning text and links |
| Join Multiplayer | `JoinMultiplayerScreen` | Standard background plus server selection list | Black | Resource-first menu/list/separators; preserve server icons/status/ping |
| Add/Edit Server | `EditServerScreen` | Standard background plus labels and edit fields | Black | Resource-first; preserve field backgrounds and validation |
| Direct Connection | `DirectJoinServerScreen` | Standard background plus address field | Black | Resource-first |
| Connecting | `ConnectScreen` | Standard background plus status/narration | Black | Resource-first; verify transitions and cancellation button |
| Disconnected/error | `DisconnectedScreen` and generic error screens | Inherited standard background | Black | Resource-first; preserve reason text and reconnect/back controls |
| Receiving/loading level before world | `ReceivingLevelScreen` with `Reason.OTHER` | Panorama, blur, menu background; portal reasons use special transition art | Black where `level == null` | Verify pre-world connection path; do not suppress portal/end transitions when a level exists |
| Generic waiting/progress | `GenericWaitingScreen`, `ProgressScreen` | Standard background/status rendering | Black | Resource-first; verify progress/narration visibility |
| Realms main | `RealmsMainScreen` | Calls standard screen rendering; Realms list/content layered above | Black | Resource-first; verify subscription/error/trial states when available |
| Realms configuration/invite/reset/backup screens | Realms `Screen` subclasses | Mostly standard background and lists | Black | Resource-first; enumerate live-accessible variants during sweep |
| Realms notification overlay on title | `RealmsNotificationsScreen` | Empty/transparent background layered with title context | Preserve title background | Do not independently blacken; verify it remains an overlay rather than a full screen |

## Messages, onboarding, and required notices

| Screen/state | Representative class | Current background mechanism | Desired result | Later implementation/verification |
|---|---|---|---|---|
| Generic message/notice | `GenericMessageScreen` | Reimplements panorama, blur, and menu background | Black | Resource-first works because it still consumes `MENU_BACKGROUND`; do not rely on background event |
| Confirmation/alert | `ConfirmScreen`, `AlertScreen` | Standard background | Black | Resource-first; preserve all text and action semantics |
| Accessibility onboarding | accessibility onboarding screen | Standard pre-world screen flow | Black | Resource-first; preserve accessibility guidance and narration |
| Quick Play failure/warning | Quick Play error screens | Standard/generic message flow | Black | Resource-first; verify reachable launch variants |
| Mojang/NeoForge startup/error notices | Loader/runtime-owned screens | Loader-specific rendering; may precede Minecraft `Screen` | Black only where safely replaceable | Do not remove required marks, legal text, loader diagnostics, or accessibility content |

## Credits exception

| Screen/state | Representative class | Current background mechanism | Desired result | Later implementation/verification |
|---|---|---|---|---|
| End poem | `WinScreen` with `poem == true` | End-portal render type | Unchanged unless separately approved | Resource override does not affect this path |
| End credits after play | `WinScreen` with `poem == false` | Calls standard background but directly renders `Screen.MENU_BACKGROUND` | Product decision required: black satisfies credits branding, but is an in-world-state exception | Verify content and decide whether black end credits are accepted under the non-game boundary |

## Explicit in-world exclusions

| Screen/state | Representative class | Current background mechanism | Required result | Guard |
|---|---|---|---|---|
| Pause menu | `PauseScreen` | In-world screen/transparent or in-world treatment | Unchanged | Never apply global post-render black fill |
| Options opened from pause | `OptionsScreen` while `level != null` | `inworld_menu_background.png` chosen by `Screen` | Unchanged | Leave every `inworld_*` resource untouched |
| In-world lists/options submenus | Standard screen/list classes while `level != null` | `inworld_menu_*` resources | Unchanged | Level-aware vanilla selector remains intact |
| Inventory/containers | Container screens | World/GUI-specific rendering | Unchanged | Not part of generic menu resource set |
| UltimaCraft custom gameplay UI | Project-owned client screens | Project-specific textures/renderers | Unchanged | No broad `Screen` replacement or event cover |
| Portal/end transitions | `ReceivingLevelScreen` portal reasons and related renderers | Special transition art | Unchanged | State/reason-specific guard |

## Live sweep protocol for later gates

For every reachable black-policy row:

1. enter from a fresh title session with no level loaded;
2. confirm all four corners and transition frames are opaque `#000000` outside functional UI;
3. resize the window and change GUI scale;
4. exercise lists, tabs, fields, buttons, tooltips, narration, and back navigation;
5. record any direct resource consumer or custom `renderBackground` implementation;
6. open the corresponding screen from an active world when possible and confirm its in-world background is unchanged.

The title row additionally requires an animation-frame comparison and hash check of the protected mixin/resource before and after each implementation milestone.
