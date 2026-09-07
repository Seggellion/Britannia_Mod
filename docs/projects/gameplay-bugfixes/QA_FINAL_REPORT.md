# UltimaCraft — three failed manual checks, corrected candidate

Code corrections and the complete automated mod baseline are complete in the isolated branch. The candidate is ready for manual retesting. **No new visual gate was personally completed.** Full-can artwork is absent (**PENDING_USER_ASSET**); the display-case and Photon visual gates remain open (**PENDING_MANUAL_RETEST**). No merge, push, deployment, production operation or Rails modification occurred.

## Root causes and focused commits

| Issue | Finding and resulting change | Commit |
| --- | --- | --- |
| Display-case ghost item | Empty live update tags were ignored by NeoForge 21.1.72, leaving the client block entity's renderer-facing stack stale. Explicit empty `DisplayedItem` data now clears it through the actual packet handler. Transaction/conservation/rotation behavior stays intact. | `e0b8598aa123b00b2afbee124a54e80d6d891286` |
| Identical can appearance | Only the base model/texture exists, with no override. The full property and normal component synchronization already work; new tests cover actual outgoing inventory packets and dropped/reloaded data. No missing-art reference or fabricated picture was shipped. | `750f823df9d49951fff64ba928870fc93c285f15` |
| Invisible placed Photon gate | Confirmed terrain/entity render-path mismatch: the BER requested BLOCK/terrain vertices, while the item uses NEW_ENTITY. The BER now uses the exact named item's translucent entity material. This is an evidence-supported compatibility correction; Photon pixels remain unverified. | `d257cab2cb929af406055cd7a5c1efcbe9c9fdec` |

Detailed evidence: [display case](QA_DISPLAY_CASE_FIX.md), [watering can](QA_WATERING_CAN_EVIDENCE.md), [moongate](QA_MOONGATE_RENDER_FIX.md), [baseline and original failures](QA_FOLLOWUP_2026-09-07.md). The exact NeoForge source and Iris 1.8.0 bytecode were checked; [Iris documents a separate translucent block-entity shader program](https://github.com/IrisShaders/ShaderDoc/blob/master/iris-features.md#block-entity-translucent-iris-16). Shader causality beyond the corrected mismatch is a manual gate.

## Files changed

Production and automated coverage:

- [src/main/java/com/seggellion/britannia_mod/block/entity/DisplayCaseBlockEntity.java](C:/projects/britannia/mod/Britannia_Mod/.claude/worktrees/patch18-gameplay-bugfixes/src/main/java/com/seggellion/britannia_mod/block/entity/DisplayCaseBlockEntity.java)
- [src/main/java/com/seggellion/britannia_mod/client/renderer/MoongateBlockEntityRenderer.java](C:/projects/britannia/mod/Britannia_Mod/.claude/worktrees/patch18-gameplay-bugfixes/src/main/java/com/seggellion/britannia_mod/client/renderer/MoongateBlockEntityRenderer.java)
- [src/main/java/com/seggellion/britannia_mod/gametest/GameplayDisplayCaseGameTests.java](C:/projects/britannia/mod/Britannia_Mod/.claude/worktrees/patch18-gameplay-bugfixes/src/main/java/com/seggellion/britannia_mod/gametest/GameplayDisplayCaseGameTests.java)
- [src/main/java/com/seggellion/britannia_mod/gametest/GameplayWateringCanSyncGameTests.java](C:/projects/britannia/mod/Britannia_Mod/.claude/worktrees/patch18-gameplay-bugfixes/src/main/java/com/seggellion/britannia_mod/gametest/GameplayWateringCanSyncGameTests.java)
- [src/test/java/com/seggellion/britannia_mod/client/renderer/MoongateRenderBufferTest.java](C:/projects/britannia/mod/Britannia_Mod/.claude/worktrees/patch18-gameplay-bugfixes/src/test/java/com/seggellion/britannia_mod/client/renderer/MoongateRenderBufferTest.java)
- [src/test/java/com/seggellion/britannia_mod/moongate/MoongateMilestoneTenContractTest.java](C:/projects/britannia/mod/Britannia_Mod/.claude/worktrees/patch18-gameplay-bugfixes/src/test/java/com/seggellion/britannia_mod/moongate/MoongateMilestoneTenContractTest.java)
- [src/test/java/com/seggellion/britannia_mod/structure/DisplayCaseBlockEntityTest.java](C:/projects/britannia/mod/Britannia_Mod/.claude/worktrees/patch18-gameplay-bugfixes/src/test/java/com/seggellion/britannia_mod/structure/DisplayCaseBlockEntityTest.java)
- [src/test/java/com/seggellion/britannia_mod/structure/DisplayCaseContractTest.java](C:/projects/britannia/mod/Britannia_Mod/.claude/worktrees/patch18-gameplay-bugfixes/src/test/java/com/seggellion/britannia_mod/structure/DisplayCaseContractTest.java)

Evidence documents: `QA_DISPLAY_CASE_FIX.md`, `QA_WATERING_CAN_EVIDENCE.md`, `QA_MOONGATE_RENDER_FIX.md`, `QA_FOLLOWUP_2026-09-07.md`, this report, acceptance draft, scratchpad, `M11_REGRESSION_EVIDENCE.md`, `M10_SHADER_ACCEPTANCE.md`, and `M11_RELEASE_IDENTITY.md`, all under `docs/projects/gameplay-bugfixes/`. The ignored release `FINAL_HANDOFF.md` is updated with this report; its earlier M11 contents remain in `FINAL_HANDOFF-M11-12ebe40c.md`.

## Exact automated commands and results

Working directory: `C:\projects\britannia\mod\Britannia_Mod\.claude\worktrees\patch18-gameplay-bugfixes`. JDK selection:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot'
```

```powershell
.\gradlew.bat test --tests 'com.seggellion.britannia_mod.structure.DisplayCaseBlockEntityTest' --no-configuration-cache --console=plain
.\gradlew.bat test --tests 'com.seggellion.britannia_mod.structure.DisplayCase*' runGameTestServer --no-configuration-cache --console=plain
.\gradlew.bat test --tests 'com.seggellion.britannia_mod.farming.PlantingPresentationTest' --tests 'com.seggellion.britannia_mod.moongate.*' --tests 'com.seggellion.britannia_mod.client.renderer.MoongateRenderBufferTest' runGameTestServer --no-configuration-cache --console=plain
.\gradlew.bat clean build artifactIdentity runGameTestServer --no-configuration-cache --console=plain
```

| Run | Actual result |
| --- | --- |
| Pre-fix packet regression | 8 JUnit tests; 1 expected ghost-item failure |
| Display focused, after correction | 12 JUnit passed; all 1,154 required GameTests passed |
| Can/moongate focused | 10 JUnit passed; all 1,156 required GameTests passed |
| Normal clean full release | **3,498 JUnit total: 3,481 passed, 17 inherited skips, zero failures/errors; 455 suites. All 1,156 required GameTests passed.** |

Logs and XML snapshots: `tmp/gameplay-bugfixes/qa-followup/`. Development encountered an obsolete parser string assertion and missing test bootstrap; both were corrected and focused commands rerun. The complete clean run has no failed tests. No skip was added. Rails was not rerun for this mod-only follow-up.

Display tests exercise 320 gestures across neighborhoods, both cells and hand/sneak combinations, exact legacy components/count, two receiving packet replicas, new insertion, duplicate suppression, failed spawn rollback and atomic rotation. Can tests decode outgoing slot/content packets, swap/move/drop/pick up, reload saved inventory into a fresh player, and fill/dispense all 12 charges in both hands. Renderer tests build actual NEW_ENTITY quads, check required attributes, model/renderer registration, animation/front-back resources and camera-heading bounds. These prove data/buffer contracts, not GPU appearance or actual mounted travel.

## Candidate identity and package audit

Download: [britannia_mod-0.1.8a-all-6414f54c.jar](C:/projects/britannia/mod/Britannia_Mod/.claude/worktrees/patch18-gameplay-bugfixes/tmp/gameplay-bugfixes/release/britannia_mod-0.1.8a-all-6414f54c.jar). Normal output: `C:\projects\britannia\mod\Britannia_Mod\.claude\worktrees\patch18-gameplay-bugfixes\build\libs\britannia_mod-0.1.8a-all.jar`.

- Size: **34,690,293 bytes**.
- SHA-256: `ec28f6afc371b7639b0007a9db264ff64e7ff83925c9c70dc6dc3bd841622641`.
- Embedded source: `6414f54cb6c31094f61314276c2bb2590f0574a9`.
- Branch: `codex/patch18-gameplay-bugfixes`; dirty: `false`.
- Build time UTC: `2026-09-07T18:10:36.382371100Z`.

The final documentation commit is later than the embedded source; it does not change runtime code or resources. Only the two production classes and build properties differ from the previous JAR. No entries were added or removed. All 7,372 packaged assets/data resources match the old JAR byte for byte, including excluded lettuce/green-onion artwork. Diagnostic/QA namespaces, GameTest classes and the temporary empty GameTest structure are absent. The corrected class references were checked in the archive.

The resource namespaces are `britannia_mod`, `c` and `minecraft`. Six existing shrine catalogue geometry/texture filenames contain `diagnostic`; they are referenced by production `ShrineMonolithDefinitions` and match the previous JAR byte for byte. They are not an injected diagnostic namespace or temporary test resource. Their exact paths are listed in the machine-readable audit.

## Can artwork and remaining visual gates

Distinct user full artwork: **absent**, so there is no full-art hash to compare and no full asset in the candidate. Existing assets under `src/main/resources/assets/britannia_mod/`:

| Resource | SHA-256 (source and final JAR) |
| --- | --- |
| `models/item/watering_can.json` | `6851ee34d51109eabeebcdee4b2612c44ef13bbe012be054eefd65ca3f0e0b78` |
| `textures/item/watering_can.png` | `7daa80cc4d6d6f94d94b4ac3bd25deb2dd8fea48c5c55a44081a9928afcd9014` |

Expected user paths: `assets/britannia_mod/models/item/watering_can_full.json` and `assets/britannia_mod/textures/item/watering_can_full.png`. Expected base override: `{"predicate":{"britannia_mod:full":1.0},"model":"britannia_mod:item/watering_can_full"}`, once actual distinct resources exist. Property registration already uses `britannia_mod:full`; 12/legacy missing data gives 1, 0–11 gives 0. No competing override exists.

The user's original screenshot is retained as `tmp/gameplay-bugfixes/qa-followup/user-photon-failure.png`. The exact previous JAR was found in the CurseForge `UltimaCraft - Britannia` instance with SHA-256 `e25d2596058f0ae78ba67bc87e4d2d76fc7e46fddbd4700f17c9b512d3373709`; its installed Iris/Sodium/Photon match the specified versions/hashes and configuration selects Photon. This is installation evidence, not independent proof of the screenshot's runtime identity.

Codex launched the old candidate in the disposable isolated client, but screen capture showed the Windows PIN screen. Input stopped, an unlock request was sent, and only the owned title-screen client was closed. The [computer-use skill](C:/Users/dusti/.codex/plugins/cache/openai-bundled/computer-use/26.901.41600/skills/computer-use/SKILL.md) requires its guidance: “If the Windows desktop is locked, stop immediately and ask the user to unlock the desktop.” No three-way scene, two-client visual check, actual reconnect/restart view or mounted trip was personally completed. The latest user instruction permits this corrected candidate for user retesting; **BUG-01 is not marked visually passed**.

## Manual retest — only these three issues

1. **Display case:** use the decorator offhand on each cell with a named/damaged/component-bearing item and a second tracking client. Confirm one exact drop, immediate blank on both clients, intact structure and no offhand rotation. Repeat while empty, insert a different item, then check chunk reload, reconnect and server restart. Reject a spawn and confirm the original item stays. Mainhand decorator must still rotate the whole pair atomically.
2. **Watering can:** until distinct full art is supplied and the override integrated, identical appearance remains expected and this gate stays PENDING_USER_ASSET. After integration, compare 0, 1, 11, 12 and legacy-missing stacks in inventory, both hands and dropped form; refill, dispense, move, swap, drop/pick up, reconnect and reload resources. Only 12 (including legacy default) must show full, with unchanged capacity/use.
3. **City moongate:** use this exact candidate and a fixed scene in all three configurations: no Iris/Sodium; Iris/Sodium with shaders off; Iris 1.8.0 + Sodium 0.6.0 + Photon v1.1 on. Compare placed/inventory/held views, front/back, near/far and chunk edge, animation/alpha/brightness, resource reload and chunk reload. Check selection/collision plus actual teleport, cooldown, passengers and mounts. Record the active JAR hash and shader settings; do not treat the old failure screenshot as a retest.

The exact corrected candidate also completed a startup-only check in the isolated no-Iris/Sodium client: its log reached completed atlas/model resource loading and entity-animation loading, and the process remained running. The installed test JAR hash matched the archive. Log: `tmp/gameplay-bugfixes/qa-followup/client-candidate-startup.log`. Only that owned client was then closed. This was a log-based startup check; no game-world view or Photon comparison was performed.

No merge, push, deployment, production operation, main-worktree edit or Rails modification occurred. All three focused changes are local commits in the authorized isolated mod branch.
