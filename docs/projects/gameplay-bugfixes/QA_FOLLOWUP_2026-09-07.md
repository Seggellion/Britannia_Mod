# Three failed manual checks — follow-up evidence

## Scope and starting checkpoint

The user reports the previous candidate failed manual acceptance: the display case ejects its stack but keeps drawing it; full and empty cans look identical; a placed city moongate is invisible with Photon while its selection outline and held item remain visible. These failures supersede any inferred visual acceptance in M0–M11. The original evidence remains historical. The user subsequently supplied `C:/Users/dusti/AppData/Local/Temp/codex-clipboard-f61f9789-652a-42da-bd3b-1f6001b106af.png`: it shows the selection outline with no visible portal surface inside or above it. A copy is retained at `tmp/gameplay-bugfixes/qa-followup/user-photon-failure.png`. It does not independently identify the JAR, shader settings or held-item appearance.

Work is confined to `C:/projects/britannia/mod/Britannia_Mod/.claude/worktrees/patch18-gameplay-bugfixes`, branch `codex/patch18-gameplay-bugfixes`. Starting HEAD: `12ebe40ccc1f33981b852e1f2aa36791b42c2518`. Initial `git status --short` was empty: no pre-existing tracked or untracked user changes. Recent commits: `12ebe40c`, `4ee0f5d9`, `991fb1f3`, `65bc1a6a`, `ea8499f7`. The playbook, acceptance draft, scratchpad, M11 regression evidence, M10 shader notes, release identity and archived final handoff were reviewed before editing.

The archived previous candidate was rehashed: `tmp/gameplay-bugfixes/release/britannia_mod-0.1.8a-all-4ee0f5d9.jar`, SHA-256 `e25d2596058f0ae78ba67bc87e4d2d76fc7e46fddbd4700f17c9b512d3373709`; embedded source `4ee0f5d90d70b2429c7d5cb8eacf100b838d0d67`, dirty `false`. The precise file used for the user's failed QA is not confirmed; clarification was requested while investigation continued. An old JAR in the default Minecraft directory is not assumed to be the QA instance.

No main-worktree, Rails, excluded lettuce/green-onion artwork, production, merge, push or deployment work is authorized for this follow-up.

## Display-case root cause

The root block entity owns the only merchandise field; the upper cell has no second storage entity. The renderer reads `displayedItem()` every frame and returns on empty. Successful ejection clears that field and calls `sendBlockUpdated(..., 3)`. Previously `getUpdateTag` used optional disk serialization and emitted a completely empty tag after ejection. In the exact NeoForge 21.1.72 source, `IBlockEntityExtension.onDataPacket` calls `loadWithComponents` only when `!compoundtag.isEmpty()`. Minecraft's `ClientPacketListener.handleBlockEntityData` uses this handler. Therefore tracking clients discard the empty update and keep the old renderer-facing stack. Chunk-tag handling does not have this guard, explaining why the old chunk-tag test missed the bug.

The correction sends an explicit empty `DisplayedItem` compound in live update tags. Empty and missing compounds use `ItemStack.parseOptional`; legacy disk omission remains readable. Transaction ownership, component conservation and both-cell resolution remain unchanged. See `QA_DISPLAY_CASE_FIX.md` for the failing-then-passing packet regression and 320-gesture GameTest matrix.

## Watering-can asset finding

Status: **PENDING_USER_ASSET**. No distinct full model/texture was found in the repository or the checked Downloads/attachment locations. The previous JAR contains only the base model and texture. The existing `britannia_mod:full` client property already returns 1 only at 12 charges, including missing legacy data; 0–11 return 0. There are no model overrides, hence no competing override ordering.

| Existing resource under `src/main/resources/` | SHA-256 |
| --- | --- |
| `assets/britannia_mod/models/item/watering_can.json` | `6851ee34d51109eabeebcdee4b2612c44ef13bbe012be054eefd65ca3f0e0b78` |
| `assets/britannia_mod/textures/item/watering_can.png` | `7daa80cc4d6d6f94d94b4ac3bd25deb2dd8fea48c5c55a44081a9928afcd9014` |

Expected user asset integration: `assets/britannia_mod/models/item/watering_can_full.json`, referencing the user's distinct `britannia_mod:item/watering_can_full` texture at `assets/britannia_mod/textures/item/watering_can_full.png`. Once those real resources exist, add the base model override `{"predicate":{"britannia_mod:full":1.0},"model":"britannia_mod:item/watering_can_full"}`. Do not ship a missing-model reference or fabricate artwork. Functional synchronization and final-JAR checks are recorded below when complete.

## Moongate investigation

The placed block uses an invisible baked-block render shape and a dedicated billboard block-entity renderer. It previously obtained `RenderType.translucent()`, the chunk/BLOCK vertex-format path. The corrected renderer uses the same named translucent entity material as the visible item, `NeoForgeRenderTypes.ITEM_LAYERED_TRANSLUCENT`. The exact NeoForge source and Iris 1.8.0 bytecode confirm the incompatible terrain/entity format boundary; `QA_MOONGATE_RENDER_FIX.md` records the evidence and limits. The latest user request explicitly permits a corrected candidate for user retesting if controlled three-configuration visual execution is unavailable; no automated check is a Photon visual pass.

## Focused validation and commits

JDK 21 was selected using `$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot'`. Commands run from the isolated worktree:

```powershell
.\gradlew.bat test --tests 'com.seggellion.britannia_mod.structure.DisplayCaseBlockEntityTest' --no-configuration-cache --console=plain
.\gradlew.bat test --tests 'com.seggellion.britannia_mod.structure.DisplayCase*' runGameTestServer --no-configuration-cache --console=plain
.\gradlew.bat test --tests 'com.seggellion.britannia_mod.farming.PlantingPresentationTest' --tests 'com.seggellion.britannia_mod.moongate.*' --tests 'com.seggellion.britannia_mod.client.renderer.MoongateRenderBufferTest' runGameTestServer --no-configuration-cache --console=plain
```

The first command reproduced the ghost before the production fix: 8 tests, 1 expected failure. After correction, the second command passed 12 JUnit tests and all 1,154 required GameTests. The third passed 10 JUnit tests and all 1,156 required GameTests, including the two new can packet/transport tests. Logs and focused JUnit XML snapshots are retained in `tmp/gameplay-bugfixes/qa-followup/`. One old parser source-string assertion and the direct render-buffer test bootstrap were corrected during development; final focused runs are green.

| Focus | Local commit |
| --- | --- |
| Display-case empty-state packet fix | `e0b8598a` |
| Can synchronization tests and missing-art integration evidence | `750f823d` |
| Moongate entity material and renderer/model regressions | `d257cab2` |

## Exact available QA installation and visual limits

Read-only inspection found the old candidate in `C:/Users/dusti/curseforge/minecraft/Instances/UltimaCraft - Britannia/mods/britannia_mod-0.1.8a-all-4ee0f5d9.jar`, with the exact expected `e25d2596...` hash and size 34,690,262 bytes. The same instance contains the specified Iris/Sodium/Photon input hashes recorded in M10; `config/iris.properties` selects `photon_v1.1.zip` with `enableShaders=true`. This establishes the available installation, not the capture time or complete settings of the user's screenshot.

The isolated test client launched the exact old JAR to its title screen according to its log. Native capture showed the Windows PIN screen, so UI input stopped and an unlock request was sent. The owned title-screen client was closed. No new client visual gate was personally completed. The can artwork remains **PENDING_USER_ASSET**; display immediate blank/two-client/restart visuals and all three moongate configurations remain **PENDING_MANUAL_RETEST**. Full regression, final release identity and the three-issue checklist will be appended after the clean release run.
