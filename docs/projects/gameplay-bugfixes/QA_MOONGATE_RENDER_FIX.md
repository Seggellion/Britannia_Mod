# Photon moongate follow-up — corrected candidate, visual gate open

The user's screenshot confirms the reported empty portal location with a selection outline. It is historical failure evidence, not a successful retest. The exact old candidate (`e25d2596...`) was found in the CurseForge `UltimaCraft - Britannia` instance alongside the specified Iris 1.8.0, Sodium 0.6.0 and Photon v1.1 inputs; its Iris settings select that pack with shaders enabled. Full input hashes and the screenshot copy are retained under `tmp/gameplay-bugfixes/qa-followup/`.

## Verified render-path mismatch and correction

The placed gate uses `RenderShape.INVISIBLE` plus `MoongateBlockEntityRenderer`. Its previous `RenderType.translucent()` buffer uses `DefaultVertexFormat.BLOCK` and the terrain translucent shader. The baked item model declares `minecraft:translucent`; NeoForge 21.1.72's `NamedRenderTypeManager` maps that name's item/entity material to `NeoForgeRenderTypes.ITEM_LAYERED_TRANSLUCENT`. That material uses `DefaultVertexFormat.NEW_ENTITY`, the entity translucent shader, the block atlas, alpha blending, lightmap, overlay, culling and sorted quads.

The exact Iris 1.8.0 JAR was inspected with JDK 21 `javap -p -c`: `MixinBufferBuilder.iris$extendFormat` maps BLOCK to Iris TERRAIN and NEW_ENTITY to Iris ENTITY. The latter carries the captured block-entity identity. `MixinBlockEntityRenderDispatcher` sets that identity and wraps the buffer source while the BER runs. Photon v1.1 contains `gbuffers_block_translucent` with its translucent program and a separate terrain program. [Iris's primary shader documentation](https://github.com/IrisShaders/ShaderDoc/blob/master/iris-features.md#block-entity-translucent-iris-16) confirms the separate translucent block-entity program. This supports correcting the BER's terrain/entity boundary; it does not prove the resulting Photon pixels without a client comparison.

The production change selects `NeoForgeRenderTypes.ITEM_LAYERED_TRANSLUCENT.get()` for the BER buffer, matching the existing item's material. No texture, model, item renderer, shader pack, block behavior or teleport code changed. The same block atlas UVs and animated sprites are used. Existing packed light/overlay and white tint are passed through; no new fullbright/emissive override or alpha cutoff was introduced. Culling, alpha blending, sorted quads and normal depth behavior remain enabled. Front/back source faces, billboard rotation, ground-anchored scale and bounds remain unchanged. The current model manager is queried each render, preserving resource-reload replacement rather than caching a stale baked model. The renderer does not depend on a private synchronized visibility flag.

All six source portal textures contain both transparent and opaque pixels; they are not fully transparent. Their alpha is currently binary (0/255), and their original hashes, animation metadata and dimensions are unchanged. Existing atlas alpha and translucent material intent are preserved; visual brightness/bloom under Photon remains a retest item.

## Automated evidence

With JDK 21, from the isolated worktree:

```powershell
.\gradlew.bat test --tests 'com.seggellion.britannia_mod.farming.PlantingPresentationTest' --tests 'com.seggellion.britannia_mod.moongate.*' --tests 'com.seggellion.britannia_mod.client.renderer.MoongateRenderBufferTest' runGameTestServer --no-configuration-cache --console=plain
```

**10 JUnit tests passed; all 1,156 required GameTests passed**, build successful in 3m25s. Log: `tmp/gameplay-bugfixes/qa-followup/can-moongate-focused.log`. An initial JUnit harness failure initialized client render classes before vanilla registries; adding the standard Minecraft bootstrap corrected the test setup and the full command passed on rerun.

The direct runtime test verifies the selected material uses NEW_ENTITY with overlay/light/normal elements, rejects the chunk material and successfully builds complete colored/alpha/UV/light/overlay/normal quads in that buffer. Model contracts cover standalone model/renderer registration, current model lookup after reload, every model corner at 72 camera headings inside the existing bounds, front/back faces, animation resources, one-cell and legacy-top behavior, and unchanged teleport call sites. Registered moongate GameTests continue to cover the actual one-cell/legacy-top/collision behavior. No test claims Photon pixels or actual mounted travel.

## Controlled visual execution

A disposable copy of the prior isolated test world (`tmp/gameplay-bugfixes/client/saves/QA Portal`) and a local-only datapack prepare a fixed-noon platform, the same gate at 15,130,15 across a chunk edge, a held item and fixed camera views. The exact old release was installed only in this isolated client. Its normal launch log reaches the title screen, but native screen capture showed the Windows PIN screen. UI input stopped immediately under the computer-use guidance; the user was asked to unlock. The owned title-screen client was then closed. No world scene, shader-off comparison, Photon comparison, reload, two-client display check or mounted travel was personally completed in this follow-up.

All three configurations remain **PENDING_MANUAL_RETEST**: no Iris/Sodium; Iris/Sodium with shaders off; Iris 1.8.0 + Sodium 0.6.0 + Photon v1.1. The latest user request explicitly permits delivery of a corrected candidate for retesting when controlled visual execution is unavailable. BUG-01 remains visually open.
