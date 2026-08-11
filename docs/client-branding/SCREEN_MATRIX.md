# UltimaCraft Client Branding Screen Matrix

## Policy key

The desired policy is state-based:

```text
TitleScreen                         -> preserve approved chest-sequence background
not TitleScreen and level == null  -> opaque black decorative background
level != null                      -> preserve vanilla/current behavior
```

“Resource-first” below means exact `assets/minecraft` overrides for the normal pre-world `menu_background`, `menu_list_background`, header/footer separators, and where needed `tab_header_background`. It does not mean changing button, slider, icon, text-field, focus, selection, or accessibility assets.

This matrix is source-verified for Minecraft 1.21.1/NeoForge 21.1.72. Live visual coverage is intentionally deferred to the implementation milestones; rows marked “verify” are explicit acceptance cases, not assumptions.

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
