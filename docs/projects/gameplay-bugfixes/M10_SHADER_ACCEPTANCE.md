# M10 shader acceptance — controlled comparison pending

## Current failed-QA follow-up

2026-09-07 failed-QA follow-up: the display-case empty-packet fix and moongate entity-material correction are implemented. The clean release passed **3,498 JUnit tests reported: 3,481 passed, 17 inherited skips, zero failures/errors; all 1,156 required GameTests passed**. Candidate `britannia_mod-0.1.8a-all-6414f54c.jar` embeds clean source `6414f54cb6c31094f61314276c2bb2590f0574a9`. **No new visual gate was personally completed**: Windows was locked during the controlled client attempt. Display-case visual synchronization and all three moongate configurations remain **PENDING_MANUAL_RETEST**; full-can art remains **PENDING_USER_ASSET**. See [current three-issue report](QA_FINAL_REPORT.md), [root-cause evidence](QA_FOLLOWUP_2026-09-07.md), and [release identity](M11_RELEASE_IDENTITY.md). Only the isolated mod worktree was used; Rails was not modified or rerun. No merge, push, deployment or production operation occurred.

The material below is the preserved historical M0–M11 checkpoint. In particular, earlier statements that the moongate renderer was unchanged describe the old candidate and no longer describe this correction. The original failed manual checks are retained in the current evidence.

Status: **PENDING M10-SHADER-3WAY**. No renderer fix is claimed. Source inspection identifies `britannia_mod:moongate_block` as the city gate: one invisible logical block, a registered block entity renderer and separately registered `block/moongate_billboard` model. The renderer submits that baked model to `RenderType.translucent()`. A BLOCK/chunk stream inside a BER remains a plausible cause; it has not been isolated by the required three-configuration comparison. Changing that boundary without the controlled evidence would contradict the playbook.

## Clean packaged baseline

- Mod source: `65bc1a6a21f04df2a222c971c1494b1729c275d4`, branch `codex/patch18-gameplay-bugfixes`, embedded dirty=false.
- Normal command: JDK21, `gradlew.bat build artifactIdentity -x test --no-configuration-cache --console=plain`; successful1m45s. Log `tmp/gameplay-bugfixes/m10-baseline-build.log`.
- `britannia_mod-0.1.8a-all.jar`:34,690,265 bytes; SHA256 `8794a00fb6d288937eb6b83fc5802076873f5e842218cff9c745f5e316706c48`.
- Timestamp `2026-09-07T14:26:33.897174900Z`. Archive `tmp/gameplay-bugfixes/m10-baseline-65bc1a6a-all.jar`.
- Bundled GeckoLib4.6.6 and nanohttpd2.2.0 verified in JarJar metadata. All `gametest` classes and the generated empty structure are absent. No temporary source-set injection.

The packaged baseline loaded in an isolated offline client using the installed NeoForge21.1.72 launcher metadata,90 cached libraries and eight native DLLs. No original game config, credentials, saved servers or worlds were copied. First two launch attempts included the parent Minecraft JAR twice and failed with a duplicate module export; the isolated launcher was corrected without modifying the mod. The third attempt reached the welcome screen and a fresh normal singleplayer world. The actual baseline directory contains only the bundled mod JAR, no Iris/Sodium. Startup/source/model regressions are distinct from visual moongate acceptance.

Fresh hardware observation in `tmp/gameplay-bugfixes/client/logs/latest.log`, Sep7 07:31:12 Pacific: Intel UHD Graphics, OpenGL4.6.0, driver32.0.101.5972; LWJGL3.3.3+5. Scene options were written only in the isolated directory:render/simulation12,graphics1,AOtrue,mipmap4,guiScale3,window1280x800,no resource packs. The fresh normal world's recorded seed is `-6858865061500343773`, noise overworld generator. This establishes a new normal world can load; it does not prove jungle feature absence or client gameplay acceptance.

## Why the matrix remains pending

The prescribed computer-use skill and @oai/sky runtime initialized; a first app inventory timeout recovered on one retry. Minecraft was targetable and observed in the foreground. Repeated concurrent desktop input subsequently interrupted control, and captures of the occluded client returned an overlying application. A coordination question was sent; client input was paused to avoid competing with the user's desktop. The client remains a disposable local acceptance instance, not a production connection. No image from an overlying app is treated as Minecraft evidence.

The normal world started with commands disabled. An attempted `/gamemode creative` was refused before execution; no commands changed it. An uninstalled fixture pack is prepared at `tmp/gameplay-bugfixes/scene-pack` for a fixed-noon city gate at15,130,15, held-item control and front/back/near/far camera positions. It is fixture preparation, not an observed result. Test-world command setup must remain separate from ordinary gestures.

## Named remaining check

Run **M10-SHADER-3WAY** with the final candidate, recording its exact hash and the scene/settings above:

1. No Iris/Sodium: placed, inventory, mainhand, front/back, near/far, animation, chunk edge and F3+T resource reload.
2. Iris1.8.0+Sodium0.6.0, shaders disabled: same scene/views.
3. Same mods with Photonv1.1: same scene/views and reload. Record selected pack/profile/GPU/driver and preserve Minecraft screenshots/logs.

Use the previously hashed local files: Iris `0e8ae2864f2ba144cc59fdb56a8ac00b88c0a96afcd986c54f3fb2b7dbcc81f5`; Sodium `fb178004f4a942735029c57ef5556bc3c83ce6c1d8344b6ecdfcc651148535f6`; Photon `1228172bfb0ee49de3d2b6268390bac52c9a57dd0aa92c8903e57cf14583203a`. These hashes identify available inputs, not completed shader runs.

Only if the comparison supports the BER material boundary, apply the smallest appropriate entity/BER render-type change and repeat all three configurations. Preserve packed light, animation, camera-facing scale/bounds, registration/reload and artwork. Separately demonstrate actual player and mounted teleport/cooldown behavior. Existing model/texture/source contract tests and the registered one-cell/legacy-top/collision GameTest remain relevant automated regressions; they cannot certify visible Photon rendering or actual mounted transport.
