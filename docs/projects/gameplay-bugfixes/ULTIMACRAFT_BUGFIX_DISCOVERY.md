# UltimaCraft gameplay bug-fix discovery

Completed September 6, 2026, America/Vancouver (September 7 UTC). Discovery only: no fixes, release designation, migration or implementation playbook approved. Requirements below come from the current user prompt; older project recommendations do not override them.

## Evidence and repository snapshot

**Source** means behavior traced at the full HEAD below. **Runtime** means a locally executed test with limits stated. **Hypothesis** is not a reproduced cause. Existing tests passing does not mean the requested fixes exist.

| Field | Verified value |
|---|---|
| Checkout | `C:/projects/britannia/mod/Britannia_Mod` |
| Branch / HEAD | `patch-18` / `421e27853dde4099d1d794568e33e6709507a53b` |
| Initial status | Only untracked `FARMING_GAMELOOP_DISCOVERY.md` and `FARMING_EMPTY_INVENTORY_TEST_CHECKLIST.md`; preserved |
| Instructions | No applicable AGENTS.md found in checked ancestors or checkout, including hidden paths; other nested worktrees excluded |
| Minecraft / loader | MC 1.21.1, NeoForge 21.1.72, FML runtime 4.0.29; NeoForge Gradle userdev 7.0.165 |
| Java / Gradle | Toolchain 21; tests selected `C:/Program Files/Eclipse Adoptium/jdk-21.0.9.10-hotspot` (21.0.9+10 LTS); wrapper 8.9. PATH Java is 8 |
| Mod | ID britannia_mod, name Britannia, version 0.1.8a |
| Dependencies | GeckoLib common/NeoForge 4.6.6 for MC 1.21.1; NanoHTTPD 2.2.0; Mixin 0.8.5; MixinExtras 0.3.5; JUnit 5.11.4 |
| Runs | client→run (username Dev); server→run/server (--nogui); gameTestServer→run/gametest. Tests→build/test-run. GameTest namespace normally britannia_mod |
| Pre-existing metadata discrepancy | Minecraft range `[1.21,1.21.1)` excludes configured 1.21.1. Actual historical logs and executed GameTests load 1.21.1, so the range alone is not proof of this report's failure |

Build references: `build.gradle:1` plugin, `:28` toolchain, `:34` dependencies, `:48` test directory, `:63` runs; `gradle.properties:1`; `gradle/wrapper/gradle-wrapper.properties:1`. Every repository-relative path here expands under the absolute checkout above. **J/** means `src/main/java/com/seggellion/britannia_mod/`; **A/** means `src/main/resources/assets/britannia_mod/`. Source line references refer to this HEAD, except explicitly generated/temporary evidence.

Registered worktrees, unchanged:

| Absolute path | Full HEAD | Branch |
|---|---|---|
| C:/projects/britannia/mod/Britannia_Mod | 421e27853dde4099d1d794568e33e6709507a53b | patch-18 |
| C:/projects/britannia/mod/Britannia_Mod/.claude/worktrees/rowan-farmer-exploration-97e04a | 0cd566dd811bb717c6bda498ac3bed38be64b95e | claude/rowan-farmer-exploration-97e04a |
| C:/projects/britannia/patch18-closeout/20260904-223405-m0-db16bd5d/candidate/patch18-m2 | 625269d3bb5abb560a901f7e9198abe910eb482a | claude/patch18-m2-candidate |
| C:/projects/britannia/patch18-closeout/20260904-223405-m0-db16bd5d/publication/public-release | 5c0b917226848192e40b376cae719057e70cd662 | public/release |
| C:/projects/britannia/release-builds/starfarer-m11 | 7a4d2fc768c38969eec7e7be9412974c3ab2ff2f | detached |

### Runtime identity and rendering evidence

Pre-existing build/libs JARs embed git.head `a2f6391cdcc61fe8563545169af69145fd7d94f3`, branch patch-18, dirty=true, timestamp `2026-09-06T17:38:03.377915Z`. They predate inspected HEAD and are not proven to be deployed client/server artifacts. `build/libs/britannia_mod-0.1.8a-all.jar`: 34,602,148 bytes, SHA256 `1d47325df71c8f5f755252b7f12d83a3ad014f878a0683d4fcc6715f56c27613`; `build/libs/britannia_mod-0.1.8a.jar`: 34,030,510 bytes, SHA256 `fe1dd151a66624e2163297b8aa0b83583f239e4a5b7f43b9c06319afd26fab3f`. No release JAR was rebuilt or deployed.

| Local artifact | Observed value |
|---|---|
| run/mods/iris-neoforge-1.8.0+mc1.21.1.jar | Iris 1.8.0 metadata; SHA256 0e8ae2864f2ba144cc59fdb56a8ac00b88c0a96afcd986c54f3fb2b7dbcc81f5 |
| run/mods/sodium-neoforge-0.6.0+mc1.21.1.jar | Sodium 0.6.0 metadata; SHA256 fb178004f4a942735029c57ef5556bc3c83ce6c1d8344b6ecdfcc651148535f6 |
| run/mods/worldedit-mod-7.3.8.jar | Present, 6,222,854 bytes |
| run/shaderpacks/photon_v1.1.zip | Present; SHA256 1228172bfb0ee49de3d2b6268390bac52c9a57dd0aa92c8903e57cf14583203a |
| run/config/iris.properties | enableShaders=true, photon_v1.1.zip, maxShadowRenderDistance=32, colorSpace=SRGB |
| run/options.txt | ao=true, graphicsMode=1, mipmapLevels=4, render/simulation distance=12, resourcePacks=[] |
| run/logs/latest.log, August 30 | Iris/Sodium versions above; Photon high profile (+0 options), lines 86–91; MC 1.21.1/NeoForge 21.1.72; Intel UHD Graphics OpenGL 4.6 build 32.0.101.5972, lines 74–76; i7-13620H/Windows 11, lines 134–137 |

Iris/Sodium are local run mods, not Gradle dependencies. These settings/logs are historical, not an observed reproduction. GPU and shader files exist; missing evidence is an **executed controlled client comparison**, not proven absence of hardware. Missing: actual reported block/tool IDs, deployed JAR hashes/build identity, client/server configs/modpack/resource-pack inventory and screenshots. No production world or settings were changed.

Relevant history: farming `65039307f5f4215338350908247af5a91d04b0fa`; corrective fence/assets `317a9bd76f32a47b4176fdcc7a30af09025a3f36`; asset refinement `ffd2660e4cbb474717678cb84f18f2b6b18f879f`; moongate summon base/hum `25886a7072305277ae50d6bcd1be8a14136af952`. These are clues, not a release designation or current test baseline.

## Source index and state architecture

| Alias | Authoritative source |
|---|---|
| F | J/block/FarmingBlock.java:111 interactions; :350 random tick; :447 planting; :542 harvest; :659 tall forwarding; :792 break |
| BE | J/block/entity/FarmingBlockEntity.java:38 constants; :155 planting; :261 hydration; :280 uses; :316 deadline; :385 reclaim; :548 growth; :653 save/load; :805 sync |
| CR | J/farming/CropRegistry.java:98–164 all 67 definitions; :368 lifecycle; :439–486 tools/perennials |
| Gate | J/farming/FarmingCultivationGate.java:142 exceptions; :154 readiness; :158 threshold; :179 server actor; :197 denial |
| Skills | J/skill/SkillManager.java:61 states; :232 login; :269 server merge; :313 unavailable; :625 set; :633 snapshot; :650 fishing RNG convention |
| Soil | J/item/FarmingHoeItem.java:33; J/item/FertilizedDirtItem.java:30; J/block/CommunityHoedFarmBlock.java:44; J/block/entity/CommunityFarmBlockEntity.java:15/:36/:59 |
| Care | J/farming/FarmingSoilCare.java:14; J/event/FarmingEventHandler.java:15; F:229 bucket/:252 nutrients/:308 rain |
| Crop render | J/farming/CropVisualModels.java:14/:116; J/client/renderer/FarmingBlockEntityRenderer.java:31 |
| Moongate | J/block/MoongateBlock.java:38; J/client/renderer/MoongateBlockEntityRenderer.java:19; J/ClientModSetup.java:254/:493/:734 |
| Fence | J/block/WoodenFenceBlock.java:29–238; A/blockstates/wooden_fence.json:1; A/models/block/structure/wooden_fence/ |
| Decoration | J/item/DecorativeMultiblockItem.java:66; J/item/AdventureScarecrowItem.java:19; J/block/DecorativeMultiblockBlock.java:275 |
| Can | J/item/WateringCanItem.java:38/:86/:170/:186; J/util/WaterSourceInteraction.java:48 |
| Registrations | J/registry/BlockRegistry.java:248/:267 soil, :1187 moongate, :2492 fence, :2646 scarecrow; J/registry/ItemRegistry.java:947 soil, :993 can, :1028 lettuce, :1030 green onion, :2175 moongate, :2396 fence, :2451 scarecrow |

Normal flow: community soil → hoe preparation → fertilizer conversion → server-gated planting → watering/rain → random-tick growth → harvest. Fruit trees and flowers branch into separate blocks/BE/harvest routes. Crop thresholds are source data; server player skill is loaded from Rails; client caches are presentation only.

### Current transitions (not proposed policy)

| Start | Action / guard | Result / persisted state | Timing and issues |
|---|---|---|---|
| britannia_mod:community_farm_block | Custom hoe, rights | community_hoed_farm_block; PreparedExpiresAt=now+3600 | Server BE ticker, three simulation minutes; 03/09 |
| community_hoed_farm_block | fertilized_dirt | farming_block; CommunityPlot=true; Hydration=1; seed deadline=now+1200; fertile uses=5 | Empty deadline tested only on random ticks; 03/05/09 |
| minecraft:dirt | Same item, exact identity | farming_block; Hydration=1; uses=5; no community deadline | Violates prepared-only rule; ordinary expiry absent; 03/09 |
| Coarse dirt / vanilla farmland / occupied farm | Same item | PASS, no conversion/consumption | Coarse report unverified in this HEAD |
| Empty farming_block | Seed, rights, readiness/threshold, support/space | Crop ID/progress/age/variety; HAS_SEEDS=true; clear deadline; consume one unless Creative | No success message; grapes separate ungated path; 02/08 |
| Occupied farm | Another seed | Occupancy denial | BE + blockstate guard; duplicate/stale checks needed; 02 |
| Soil | Can / bucket / outdoor rain | Hydration +1 / 5 / at least 2, clamped 0..5 | Initial fertilizer already gives 1; 05/14 |
| Planted crop | Random tick | Possible hydration decay, rain, then growth if hydration>0 | Callback-driven growth; 05 |
| Mature annual | Valid tool harvest | Yield/seeds/straw, tool damage, skill gain, one use spent, crop clear | Community restarts seed window while uses remain; 02/03/08 |
| Mature perennial / trellis | Harvest | Regrow at configured age, roots retained, use spent | Separate from timeout; 08 |
| Tracked uses exhausted | Fifth successful harvest | Ordinary→dirt; community→community_farm_block; crop/tree cleanup | House excluded from ordinary exhaustion |
| House plot | Assigned manager planting | Shared farming plus ownership/lifecycle | Preserve paid/owned state |
| Flower on soil | Transactional planting | flower_block, FlowerPersistentState + original soil snapshot/planter/admin origin | Restore soil on uproot; separate representation |

BE:653–677 persists N/P/K/organic matter, Hydration, StoredSeed, PlantedCropId, GrowthProgress, Stage, AgeVersion=3, TickProgress, RootEstablishedGameTime, Mature, GrowthBlocked, CommunityPlot, SeedableUntilGameTime, RemainingFertileHarvests when tracked, OwnerUUID and VisualRotation. BE:805–822 sends flag-3 block updates and update packets/tags. Hydration/HAS_SEEDS blockstates summarize BE authority. Legacy age migration exists; offline catch-up growth does not. Old untracked fertile uses use -1, so do not initialize/exhaust all existing plots silently.

Bone meal/turquoise powder/sulphurous ash/rotten flesh are **nutrient inputs**, not fertilized_dirt conversion. Registered FarmingEventHandler:15–59 intercepts nutrient right-clicks before F's care branch, mutates before its client/server guard and consumes on server. F:252's nicer change detection alone is not the full live route. Record this synchronization risk for tests; it does not authorize unrelated care changes.

## BUG-01 — Moongate invisible with Iris/Photon

**Report / expectation:** placed portal invisible while item/mainhand works; make it render with the specified stack and retain function. **ID:** primary candidate britannia_mod:moongate_block (BlockRegistry:1187, ItemRegistry:2175), BE moongate_block_entity. Dungeon moongate and obsolete top are distinct; reported ID remains unverified.

**Source:** MoongateBlock:49 returns RenderShape.INVISIBLE; placed appearance is exclusively BER. Item model A/models/item/moongate_block.json:1 uses static block/moongate_block and display transforms. ClientModSetup:254 registers standalone block/moongate_billboard; :493 the BER. Chunk translucent registration :734 cannot replace the invisible block's BER. Renderer:36 silently returns on a missing baked model; centers/scales 1.35 and billboards toward camera; :47 uses `RenderType.translucent()` in MultiBufferSource, RGB=1 and supplied packed light. :62 bounds cover approximately 2.7 blocks high. Block light emission is 15.

A/models/block/moongate_billboard.json:1 has translucent render type, AO=false, shade=false faces, no neighbor cullface, five thin overlapping planes (some double-sided). Bounds x=1.25..14.75, y=0..32, z≈6.99..9.01. Portal/core/outline/outer textures exist, plus unused floor references. portal_texture and texture3 are 128×1536 (12 square frames); texture4 64×960 (15 frames); frametime=1. Other portal assets are static. Pixel alpha/metadata inventory is in tmp/gameplay-bugfixes/asset-inspection.json. Existence of visible pixels is not runtime compatibility.

**Ranked hypotheses:** (1) chunk/BLOCK-format translucent RenderType submitted through a BER/Iris entity pipeline; (2) standalone model registration/resource reload or missing-model fallback; (3) alpha/depth/sorting, normals/material classification or culling/bounds. Generated version-matched RenderType.java:65 uses BLOCK for translucent; entity translucent :163 uses NEW_ENTITY. Photon v1.1 archive has a separate gbuffers_entities_translucent.vsh forwarding to gbuffers_all_translucent and no Britannia block.properties mapping. Neither proves Iris's actual selected pipeline.

Official [NeoForge 1.21.1 model documentation](https://docs.neoforged.net/docs/1.21.1/resources/client/models/) describes chunk versus item/entity render types; [1.21.1 BER documentation](https://docs.neoforged.net/docs/1.21.1/blockentities/ber/) describes BER registration and separate item rendering. These match the API generation; they do not certify Iris 1.8.0/Photon v1.1. Attempted upstream tag retrieval was unavailable; no upstream resolution is claimed.

**Runtime:** existing single-cell/legacy-top/empty-collision GameTest passed. No shader comparison or live teleport executed. Placement canSurvive=true, collision empty, and server entityInside teleport dispatch at MoongateBlock:86–137 are separate from rendering; invisibility need not prevent teleport, but functional independence needs client testing.

| Disposable client configuration | Compare at identical scene/settings | Evidence |
|---|---|---|
| NeoForge, no Iris/Sodium shaders | Placed, inventory, mainhand; player/mount teleport | Pending |
| Iris 1.8.0 + Sodium 0.6.0, shaders disabled | Same surfaces, front/back, distance | Pending |
| Same + Photon v1.1 high | Same plus resource reload, chunk edge, animation and packed-light capture | Historical files/settings only; pending reproduction |

**Confidence:** high separate paths, medium render-type hypothesis, low exact shader cause. **Next:** capture this matrix with actual JAR hashes, packs, GPU/driver/settings and images/logs. Another shader is useful only if Photon-specific behavior needs isolation.

## BUG-02 — Planting confirmation and occupied plots

**Requirement:** confirmed planting says “You skillfully planted the <crop name> seed.” Occupancy must be discoverable before sprouts. **Source:** F:447–539 checks rights/skill/support/occupancy, mutates BE and HAS_SEEDS, consumes seed unless Creative, sounds/awards planting gain; no success message. F:485–491 already sends “A crop is already planted here” on repeat seed use. Grapes also use F:166–198 and J/item/GrapeSeedsItem.java:62–110. Flowers have a separate transaction replacing the soil.

Existing farming feedback uses actionbar displayClientMessage(...,true). Crop displayName literals are English; seed/harvest items have translation keys in A/lang/en_us.json (lettuce/green_onion included). Resolve actual server species to a translatable crop/harvest-item name, adding a species key where needed; unidentified client seed presentation must not determine confirmation identity. J/ManaOverlayScreen.java:37/:50 demonstrates RenderGuiLayerEvent UI conventions (package ui, file at J root). No occupied-farm inspection overlay found.

**Direction:** one server actionbar after committed planting; small aim-at-plot indicator “Planted: Lettuce — germinating / growing / ready,” including resolved trellis/tall root. Read synced BE occupancy, independent of mesh visibility. Refresh on block packets, reconnect/chunk load; clear after actual removal, use unknown/loading rather than false empty. No modal/new UI framework.

Ordinary planting lacks a shared hand/transaction guard and does not verify every setBlock result. Re-read live state/BE at commit, prevent stale callbacks/duplicate seed consumption, overwrite, gain or success. Server serial execution helps common duplicates but does not prove main/offhand behavior. Test two hands, two players, old client state and success followed immediately by removal. Existing flower transaction gate is a bounded pattern, not proof ordinary crops already use it.

**Confidence:** high absent message/current occupancy check, UI untested. **Next:** server counters for exactly-one commit/consumption/message, then two-client planting/inspection/reload.

## BUG-03 — Fertilizer does not revert after 60 seconds

**Requirement:** temporary fertilizer state expires in 60 seconds. **Source:** hoe preparation's 3600-tick server-BE timer differs from community fertilizer's 1200-tick seed window. Ordinary dirt fertilization has no deadline. Empty-community reclaim BE:385 is evaluated only by F.randomTick:352. Planting clears deadline (BE:164). Repeated canonical fertilizer on farming_block returns PASS, no renewal; nutrients/watering do not renew it. Successful annual community harvest reopens seedability if uses remain.

**Runtime:** overdue empty community soil survived five server ticks at randomTickSpeed=0; explicit randomTick reverted it to community_farm_block. Planted and ordinary plots had deadline=0. This confirms trigger behavior, not a stopwatch test of 1200 naturally elapsed ticks.

| Situation | Current behavior |
|---|---|
| Loaded random ticks | Reclaim can lag deadline until block randomly selected |
| randomTickSpeed=0 | No normal fertilizer reclaim; separate hoe BE timer still ticks |
| Low TPS | 1200 game ticks take longer than 60 wall-clock seconds |
| Chunk unloaded/reloaded | No local evaluation; global gameTime may advance; reclaim on later random tick |
| Restart | Deadline saved; gameTime resumes; downtime not counted |
| Planted / ordinary fertilized plot | Deadline canceled / never started; five-use lifecycle independent |

**Recommended contract pending D1/D2:** 1200 simulation ticks for empty seedability, deterministic evaluation and load check; preserve planted crops, moisture/nutrients/ownership. Distinguish community base restoration from private prepared-soil restoration and paid five-use entitlement. Real elapsed time would require timestamps/downtime/clock-change policy. Planted behavior and exact private reversion target are unresolved; unconditional deletion is not approved.

**Confidence:** high source + runtime. **Next:** resolve contract, then 1199/1200/1201, randomTickSpeed=0, low TPS, chunk/restart and old-world cases.

## BUG-04 — Lettuce phase 0 silver

**ID/assets:** lettuce, CR:102; britannia_mod:lettuce_seeds; CropVisualModels:116 selects lettuce_age_0→lettuce_1. Ages 0..7 map to [1,2,2,3,4,4,5,5]. A/models/block/crops/lettuce/lettuce_1.json:1 is four thin planes, y=-1..14, no explicit tint/cullface overrides.

**Actual image:** A/textures/block/crops/lettuce/lettuce_1.png is 32×32 with nine opaque pixels at x=4..12/y=14..15. Visible RGB spans (193,176,153)–(255,247,221): pale cream/tan seeds, not green. lettuce_2 has 21 green opaque pixels (70,117,49)–(144,178,96). Original images were viewed and measured; no asset modified. Renderer uses RGB=1 and soil BE light with cutout, no lettuce-specific biome tint.

**Leading cause:** pale phase-zero art/UV sampling, potentially accentuated by light/shader; missing/wrong textures rank lower because mapping/resources resolve. Silver appearance itself has no controlled runtime image. **Target:** intentional nonmetallic lettuce seed/seedling stage coherent with muted-green phase 1, preserved progression. **Confidence:** high source art, medium perceptual cause. **Next:** fixed day/light baseline/Photon age-0/1 comparison before choosing recolor versus early-stage art adjustment.

## BUG-05 — Lettuce grows without watering

**Source:** fertilizer initializes water=1; rain raises to at least 2; can +1; bucket 5. BE moisture persists and reconciles stale blockstate. No adjacent-water/farmland hydration scan or offline catch-up in normal lettuce flow. The only production BE.tickGrowth caller is F:388 after hydration>0. Growth multiplier can retain a positive floor at poor hydration fit; directly invoking BE.tickGrowth bypasses the outer gate and is not normal gameplay. Lettuce baseGrowthTicks=4 means growth callbacks, not four server ticks. Water is checked, not spent per growth stage; separate 10% random decay may subtract it before growth.

**Controlled runtime:** test cleared 5×5×5 volume, y=80, clear weather, no water in that volume, randomTickSpeed=0; dispatched real BlockState.randomTick 200 times with seed 12345. Dry lettuce progress=0; initial-water-1 lettuce advanced to 0.22864172 then dried to 0. Dry carrot control=0; initial-water-1 carrot reached 0.16141915 then dried. Batched callbacks ran within one test invocation (all five diagnostics: 1.472 seconds), **not 200 elapsed game ticks or a natural-time growth cycle**. No timed natural client growth/reload was executed.

This supports “no watering can used” via initialization and does not reproduce zero-effective-water growth. Behavior is shared with carrot. Fruit trees have separate OrangeTreeRootBlock/root phase growth, flowers separate growth service; do not extend lettuce's proof to them.

**Confidence:** high normal zero-water gate and initialization explanation; reported deployed runtime unknown. **Next:** natural-time lettuce/carrot control under roof, clear weather, record initial moisture/nearby water/nutrients/climate/y, randomTickSpeed, gameTime and wall time at every transition. D3 decides whether initialization/rain should count as water or explicit player irrigation is intended.

## BUG-06 — Green onion phase 0 rough

**ID/assets:** green_onion CR:104, britannia_mod:green_onion_seeds; A/models/block/crops/green_onion/green_onion_age_0.json:1→green_onion_1→minecraft:block/cross. Actual 32×32 green_onion_1.png has 66 opaque green pixels in x=10..20/y=19..27 (11×9), binary alpha only; no source antialias fringe.

**Precise visual description:** sparse stepped green sprout low on crossed planes, most atlas transparent; coarse silhouette close up, then a substantial mesh change to phase 1's 18 elements. Pixel edges can be intentional. Mipmapping/filtering, distance and plane crossing are hypotheses, not proven defects.

**Target:** recognizable small scallion, intentional pixel steps, grounded roots, no square/fringe/flickering seam, coherent scale with next phase; no arbitrary smoothing/resolution increase. **Confidence:** high selection/pixels, medium-low subjective cause. **Next:** fixed-scale phase-0/1 near/play-distance views at existing mipmap=4, shaders off/on. Request report screenshot only if this does not identify “rough.”

## BUG-07 — Green onion phase 1 dark/black

**Source:** green_onion_age_1.json:1 directly contains 18 geometry elements using green_onion.png, not old green_onion_2. Ages 1..7 use new exported geometry; legacy cross/.old assets are not selected. format_version=1.21.11 is exporter metadata against game 1.21.1, not alone proof of parse failure; gui_light=front is not a world-BER lighting fix.

Actual 32×32 atlas has 396 opaque green/cream pixels, RGB minima (42,86,25), maxima (245,245,238), not black. Model includes y=-3..3 root planes, intersections, fractional UV strips and degenerate side UVs. No crop tint found. FarmingBlockEntityRenderer:31–76 uses soil-origin packed light, RGB 1, chunk RenderType.cutout, translation and crop rotation. Generated MC ModelBlockRenderer.java:383–448 submits quads with supplied light and does not run neighbor terrain AO. Missing ambientocclusion=false alone is therefore not a demonstrated cause.

**Ranked causes:** geometry/UV/normals/light sample; shader handling of chunk cutout in BER; deployed resource mismatch; source-color issue lower. BUG-04's pale art and BUG-06's silhouette remain separate despite shared renderer.

**Confidence:** high selected asset/nonblack pixels, medium hypotheses. **Next:** consistent day/night/cardinal/rotated phase-0/1/2 views with shaders off/Photon; capture selected model/packed light diagnostics. Acceptance: green leaves/pale roots, no black faces or angle-dependent disappearance.

## BUG-08 — Harvest skill gate and failure/destruction

**Required:** harvest threshold equals planting threshold; add failed/destroyed roll. Probability, destruction scope and exemptions are not supplied. Existing yield/seed RNG and skill-gain RNG are not the requested outcome mechanic.

**Authority:** CR minimumFarmingSkill feeds J/farming/FarmingSkillRequirementResolver.java:19–58 identity map and Gate. Normal planting requires AVAILABLE server SkillSnapshot, threshold inclusive equality. Rails loads values asynchronously and merges on server; NOT_LOADED/LOADING/UNAVAILABLE fail closed even at minimum=0. Creative or permission>=2 bypasses; normal fake/nonplayer refused. Grapes return NOT_APPLICABLE at Gate:142 and also have direct planting paths despite definition=80. Snapshot Skills:633 has readiness/value, no freshness TTL/revision; AVAILABLE is not proof of current out-of-band Rails edits. Reuse snapshot, not client cache or zero fallback. No backend crop threshold override found; variety agronomy is separate.

**Current F:542–657 harvest:** maturity/support/tool/tall integrity → server yield/quality → drops :603, straw/seed roll :607 → durability :615 → fertile use :618 → clear/regrow/exhaust → sound and HARVEST gain :638. No Farming eligibility or success/destruction roll. Creative skips tool damage but can receive drops/gain and spend fertile uses. HAND permits a held item; BARE_HAND requires empty. Current potato is ROOT_SHOVEL despite old hand-harvest prose.

**Recommended authoritative boundary:** resolve live root/soil/species, recheck occupancy/maturity/rights/tool/readiness, evaluate minimum eligibility, then roll once server-side and commit success or approved destruction once. Emit yield/seed return/quest-success/gain only from the appropriate committed outcome. Keep below-minimum refusal distinct from an eligible failed roll. Inject RNG for tests; fishing logistic catchChance at Skills:650 is a convention to inspect, not an approved Farming formula. D4 determines tool/use costs and destroyed plant/seeds/perennial semantics; no percentage is approved.

### Every harvest-capable route and coverage legend

| Code | Actual source / route | Consequence |
|---|---|---|
| P | F:447 | Standard gated planting |
| G | F:166; J/item/GrapeSeedsItem.java:62 | Ungated grapes; harvest-only 80 would violate matching effective requirements |
| H | F:542 via F:205/:778 | Standard item/empty-hand harvest; central normal boundary |
| T | J/block/TrellisBlock.java:68–85/:103 | Resolve underlying farm for plant/care/harvest; no second roll |
| S | J/block/CornStalkBlock.java:49/:56→F:659 | Corn/banana selectable segments resolve soil and maturity |
| A | J/block/GrapeArborBlock.java:89/:98→F:659 | Grape segments resolve same root |
| F | J/block/OrangeFruitBlock.java:53–102 | Nine fruit trees, ripe/scissors/root/house rights; inventory insert/drop fallback; one soil use per fruit click, no skill gate |
| X | J/event/WoodChopEventHandler.java:66–104/:123–132→OrangeFruitBlock:113/root cleanupTree | Two-handed axe break and Adventure forwarding can drop ripe/whole-tree fruit without normal Farming gate or fertile-use accounting |
| V | Native items away from custom soil | Vanilla potato/wheat/pumpkin/melon/mushroom routes are different; custom definitions do not gate all vanilla planting/breaking |
| Flower | J/farming/FlowerPlantingService.java:73; FlowerInteractionService.java:185 | Separate persistent system; scissors/maturity/protection, reset then inventory/drop and gain; no ordinary harvest gate/roll |
| Incidental destruction | F:792–848, tall cleanup/tree cleanup | Crop clearing is not automatically player harvest; ordinary cleanup does not request fruit yield. No dedicated hopper/dispenser harvest handler found |

Managed break authorization on axe paths is not Farming eligibility. Route player economic fruit drops through the decision point or an agreed non-yield destruction rule. Do not invent player-skill gates for explosions/support loss. Fake-player behavior remains explicit. Inventory overflow must not reroll or duplicate fallback drops; test root/part clicks, left/right actions and two players. No custom normal farming soil loot table was found in resource searches; vanilla crop loot is not evidence for mod crop rewards.

### Complete crop table

Every threshold below comes from CR at the stated line; routes/tools/exceptions are current source, not new approved rules. All H/F/X routes lack the requested gate/outcome. ROOT_SHOVEL uses root_crop_shovels (currently Britannia shovel only); GRAIN_BLADE uses its blade tag including vanilla swords and specified custom blades; SCISSORS is the custom item. Tree fruit requires scissors at runtime despite registry HAND.

| Crop ID / name | Required Farming (definition) / source line | Seed supplier | Plant / harvest routes | Tool | Exceptions |
|---|---:|---|---|---|---|
| `squash` / Squash | 10 / CR:98 | `ItemRegistry.SQUASH_SEEDS::get` | P/H | SCISSORS | Annual |
| `carrot` / Carrot | 0 / CR:99 | `ItemRegistry.CARROT_SEEDS::get` | P/H | HAND | Annual |
| `corn` / Corn | 30 / CR:100 | `ItemRegistry.CORN_SEEDS::get` | P/H+S | SCISSORS | Tall annual |
| `cabbage` / Cabbage | 10 / CR:101 | `ItemRegistry.CABBAGE_SEEDS::get` | P/H | SCISSORS | Annual |
| `lettuce` / Lettuce | 0 / CR:102 | `ItemRegistry.LETTUCE_SEEDS::get` | P/H | SCISSORS | Annual |
| `yellow_onion` / Yellow Onion | 10 / CR:103 | `ItemRegistry.YELLOW_ONION_SEEDS::get` | P/H | SCISSORS | Annual |
| `green_onion` / Green Onion | 0 / CR:104 | `ItemRegistry.GREEN_ONION_SEEDS::get` | P/H | SCISSORS | Annual |
| `pumpkin` / Pumpkin | 20 / CR:105 | `ItemRegistry.PUMPKIN_SEEDS_CUSTOM::get` | P/H | SCISSORS | Annual |
| `potato` / Potato | 5 / CR:106 | `ItemRegistry.POTATO_SEED::get` | P/H | ROOT_SHOVEL | Annual |
| `watermelon` / Watermelon | 50 / CR:107 | `ItemRegistry.WATERMELON_SEEDS::get` | P/H | SCISSORS | Annual |
| `vanilla_potato` / Vanilla Potato | 5 / CR:108 | `() -> Items.POTATO` | P/H | HAND | Annual; V outside custom soil |
| `wheat` / Wheat | 0 / CR:109 | `() -> Items.WHEAT_SEEDS` | P/H | GRAIN_BLADE | Annual; V outside custom soil |
| `rye` / Rye | 10 / CR:110 | `ItemRegistry.RYE_SEEDS::get` | P/H | GRAIN_BLADE | Annual |
| `barley` / Barley | 15 / CR:111 | `ItemRegistry.BARLEY_SEEDS::get` | P/H | GRAIN_BLADE | Annual |
| `oats` / Oats | 15 / CR:112 | `ItemRegistry.OAT_SEEDS::get` | P/H | GRAIN_BLADE | Annual |
| `mustard` / Mustard | 30 / CR:113 | `ItemRegistry.MUSTARD_SEEDS::get` | P/H | GRAIN_BLADE | Annual |
| `beans` / Beans | 25 / CR:114 | `ItemRegistry.BEAN_SEEDS::get` | P/H | GRAIN_BLADE | Annual |
| `rice` / Rice | 75 / CR:115 | `ItemRegistry.RICE_SEEDS::get` | P/H | HAND | Annual |
| `tomato` / Tomato | 40 / CR:116 | `ItemRegistry.TOMATO_SEEDS::get` | P+T/H+T | SCISSORS | Trellis perennial |
| `garlic` / Garlic | 20 / CR:117 | `ItemRegistry.GARLIC_SEEDS::get` | P/H | ROOT_SHOVEL | Annual |
| `ginseng` / Ginseng | 70 / CR:118 | `ItemRegistry.GINSENG_SEEDS::get` | P/H | SCISSORS | Annual |
| `mandrake` / Mandrake | 90 / CR:119 | `ItemRegistry.MANDRAKE_SEEDS::get` | P/H | ROOT_SHOVEL | Annual |
| `nightshade` / Nightshade | 85 / CR:120 | `ItemRegistry.NIGHTSHADE_SEEDS::get` | P/H | GRAIN_BLADE | Annual |
| `brown_mushroom` / Brown Mushroom | 55 / CR:121 | `() -> Items.BROWN_MUSHROOM` | P/H | HAND | Annual; V outside custom soil |
| `red_mushroom` / Red Mushroom | 60 / CR:122 | `() -> Items.RED_MUSHROOM` | P/H | HAND | Annual; V outside custom soil |
| `vanilla_pumpkin` / Vanilla Pumpkin | 25 / CR:123 | `() -> Items.PUMPKIN_SEEDS` | P/H | HAND | Annual; V outside custom soil |
| `vanilla_melon` / Vanilla Melon | 45 / CR:124 | `() -> Items.MELON_SEEDS` | P/H | HAND | Annual; V outside custom soil |
| `pineapple` / Pineapple | 65 / CR:125 | `ItemRegistry.PINEAPPLE_SEEDS::get` | P/H | BARE_HAND | Annual |
| `strawberry` / Strawberry | 15 / CR:126 | `ItemRegistry.STRAWBERRY_SEEDS::get` | P/H | BARE_HAND | Annual |
| `blueberry` / Blueberry | 35 / CR:127 | `ItemRegistry.BLUEBERRY_SEEDS::get` | P/H | BARE_HAND | Annual |
| `raspberry` / Raspberry | 25 / CR:128 | `ItemRegistry.RASPBERRY_SEEDS::get` | P/H | BARE_HAND | Annual |
| `cranberry` / Cranberry | 70 / CR:129 | `ItemRegistry.CRANBERRY_SEEDS::get` | P/H | BARE_HAND | Annual |
| `blackberry` / Blackberry | 30 / CR:130 | `ItemRegistry.BLACKBERRY_SEEDS::get` | P/H | BARE_HAND | Annual |
| `huckleberry` / Huckleberry | 40 / CR:131 | `ItemRegistry.HUCKLEBERRY_SEEDS::get` | P/H | BARE_HAND | Annual |
| `mulberry` / Mulberry | 45 / CR:132 | `ItemRegistry.MULBERRY_SEEDS::get` | P/H | BARE_HAND | Annual |
| `elderberry` / Elderberry | 50 / CR:133 | `ItemRegistry.ELDERBERRY_SEEDS::get` | P/H | BARE_HAND | Annual |
| `cherries` / Cherries | 60 / CR:134 | `ItemRegistry.CHERRY_SEEDS::get` | P/F+X | SCISSORS; axe alternate | Tree; registry HAND overridden by fruit route |
| `cotton` / Cotton | 40 / CR:135 | `ItemRegistry.COTTON_SEEDS::get` | P/H | SCISSORS | Annual |
| `flax` / Flax | 20 / CR:136 | `ItemRegistry.FLAX_SEEDS::get` | P/H | GRAIN_BLADE | Annual |
| `hemp` / Hemp | 50 / CR:137 | `ItemRegistry.HEMP_SEEDS::get` | P/H | SCISSORS | Annual |
| `hops` / Hops | 60 / CR:138 | `ItemRegistry.HOPS_SEEDS::get` | P+T/H+T | SCISSORS | Trellis perennial |
| `snow_peas` / Snow Peas | 20 / CR:139 | `ItemRegistry.SNOW_PEA_SEEDS::get` | P/H | SCISSORS | Annual |
| `peas` / Peas | 5 / CR:140 | `ItemRegistry.PEA_SEEDS::get` | P/H | HAND | Annual |
| `turnips` / Turnips | 5 / CR:141 | `ItemRegistry.TURNIP_SEEDS::get` | P/H | SCISSORS | Annual |
| `apple` / Apple | 55 / CR:142 | `ItemRegistry.APPLE_SEEDS::get` | P/F+X | SCISSORS; axe alternate | Tree; registry HAND overridden by fruit route |
| `pear` / Pear | 60 / CR:143 | `ItemRegistry.PEAR_SEEDS::get` | P/F+X | SCISSORS; axe alternate | Tree; registry HAND overridden by fruit route |
| `peach` / Peach | 55 / CR:144 | `ItemRegistry.PEACH_SEEDS::get` | P/F+X | SCISSORS; axe alternate | Tree; registry HAND overridden by fruit route |
| `lemon` / Lemon | 70 / CR:145 | `ItemRegistry.LEMON_SEEDS::get` | P/F+X | SCISSORS; axe alternate | Tree; registry HAND overridden by fruit route |
| `lime` / Lime | 75 / CR:146 | `ItemRegistry.LIME_SEEDS::get` | P/F+X | SCISSORS; axe alternate | Tree; registry HAND overridden by fruit route |
| `orange` / Orange | 70 / CR:147 | `ItemRegistry.ORANGE_SEEDS::get` | P/F+X | SCISSORS; axe alternate | Tree; registry HAND overridden by fruit route |
| `olive` / Olive | 75 / CR:148 | `ItemRegistry.OLIVE_SEEDS::get` | P/F+X | SCISSORS; axe alternate | Tree; registry HAND overridden by fruit route |
| `plum` / Plum | 65 / CR:149 | `ItemRegistry.PLUM_SEEDS::get` | P/F+X | SCISSORS; axe alternate | Tree; registry HAND overridden by fruit route |
| `bell_peppers` / Bell Peppers | 45 / CR:150 | `ItemRegistry.BELL_PEPPER_SEEDS::get` | P+T/H+T | SCISSORS | Trellis perennial |
| `cucumbers` / Cucumbers | 35 / CR:151 | `ItemRegistry.CUCUMBER_SEEDS::get` | P+T/H+T | SCISSORS | Trellis perennial |
| `honeydew` / Honeydew | 60 / CR:152 | `ItemRegistry.HONEYDEW_SEEDS::get` | P+T/H+T | SCISSORS | Trellis perennial |
| `cantaloupe` / Cantaloupe | 55 / CR:153 | `ItemRegistry.CANTALOUPE_SEEDS::get` | P+T/H+T | SCISSORS | Trellis perennial |
| `banana` / Banana | 80 / CR:154 | `ItemRegistry.BANANA_SEEDS::get` | P/H+S | BARE_HAND | Tall perennial |
| `broccoli` / Broccoli | 25 / CR:155 | `ItemRegistry.BROCCOLI_SEEDS::get` | P/H | SCISSORS | Annual |
| `cauliflower` / Cauliflower | 30 / CR:156 | `ItemRegistry.CAULIFLOWER_SEEDS::get` | P/H | SCISSORS | Annual |
| `rhubarb` / Rhubarb | 45 / CR:157 | `ItemRegistry.RHUBARB_SEEDS::get` | P/H | SCISSORS | Annual |
| `celery` / Celery | 40 / CR:158 | `ItemRegistry.CELERY_SEEDS::get` | P/H | SCISSORS | Annual |
| `tobacco` / Tobacco | 65 / CR:159 | `ItemRegistry.TOBACCO_SEEDS::get` | P/H | SCISSORS | Annual |
| `radish` / Radish | 5 / CR:160 | `ItemRegistry.RADISH_SEEDS::get` | P/H | SCISSORS | Annual |
| `parsnip` / Parsnip | 15 / CR:161 | `ItemRegistry.PARSNIP_SEEDS::get` | P/H | SCISSORS | Annual |
| `yam` / Yam | 35 / CR:162 | `ItemRegistry.YAM_SEEDS::get` | P/H | ROOT_SHOVEL | Annual |
| `rutabaga` / Rutabaga | 35 / CR:163 | `ItemRegistry.RUTABAGA_SEEDS::get` | P/H | SCISSORS | Annual |
| `grapes` / Grapes | 80 / CR:164 | `ItemRegistry.GRAPE_SEEDS::get` | G/H+A | SCISSORS | Perennial; definition 80, planting gate bypassed |


Additional cultivated family outside those 67 rows: poppy=20 (FlowerRegistry:98), snowdrop=40 (:109), lily=50 (:118), foxglove=65 (:130), campion=10 (:142), hyacinth=30 (:152), orfluer=95 (:164). Plant via FlowerPlantingService, scissors harvest via FlowerInteractionService. Preserve protected/admin origins and separate poppy stage-7 knife/Farming-100 mechanic. Recommend cultivated flowers share eligibility while retaining lifecycle; vanilla out-of-plot plants require an explicit scope decision, not a false coverage claim.

### Rowan / quest rewards

Read prior discovery/planning/acceptance in `C:/projects/britannia/mod/Britannia_Mod/.claude/worktrees/rowan-farmer-exploration-97e04a/docs/projects/rowan-farming-questline/`. Those cite mod 0cd566d and a separate Rails revision; backend claims are historical, not current verified backend state. Proposed “From Soil to Supper” ends with carrots, discusses a new action trigger/CropHarvestedEvent/PlanterUUID. These are not implemented at this HEAD and do not override destructive failure.

J/quest/QuestObjectiveTriggers.java:20 has location/pickup/destroy metadata. QuestObjectiveWatcher:69–76 observes actual item pickup, wired by J/quest/events/QuestEventHandlers.java:90; no crop-action event found. Soil OwnerUUID is not planter UUID; flowers do persist their own planter. Future Rowan harvest event belongs after committed success once at root; failure/destruction must not count as successful harvest/reward. Existing pickup quests can count legitimate produce drops; direct fruit/flower inventory insertion does not inherently fire item-entity pickup. Coordinate attribution/idempotency with Rowan/Rails; no quest seeds/DB/backend changes authorized here.

**Confidence:** high missing gate/roll and route inventory. **Next:** D4/D5, then below/exact/above/readiness and deterministic outcomes across every route, multiplayer/reload and future quest signal.

## BUG-09 — Fertilizer targets

**Required:** only hoed/prepared dirt; no regular/coarse conversion or consumption. J/item/FertilizedDirtItem.java:35 accepts CommunityHoedFarmBlock; :40 denies base community; :49/:72 accepts exact Blocks.DIRT, not a broad dirt tag. That exact branch violates ordinary-dirt rule. Coarse dirt and vanilla farmland are already rejected; occupied farming/house plots not conversion targets. Coarse report needs actual JAR/ID.

Server conversion consumes one except Creative and initializes water/five uses; client does not perform canonical conversion. Validate whitelist on both sides and at commit. Nutrients are separate. Hoe target policy is also separate: FarmingHoeItem:33–56 prepares community only, not vanilla dirt/coarse; house clearing is another action. Removing ordinary dirt without a private prepared route can remove private fertilization entirely.

**Confidence:** high ordinary defect/current coarse denial. **Next:** D2 private vanilla-farmland scope, then ordinary/coarse/farmland/base/hoed/occupied/house matrix, both hands, client/server inventory counts.

## BUG-10 — Creative placement, scarecrow and other decorations

**Required:** Creative chosen surfaces without gameplay placement restrictions. Scarecrow ID britannia_mod:scarecrow, BlockRegistry:2646, AdventureScarecrowItem ItemRegistry:2451. Two columns×two rows (four cells), including a visually empty lower cell. DecorativeMultiblockItem:93/:135 requires sturdy UP support for every bottom cell, no Creative exemption. It rejects before assembly; it does not check grass specifically. All-cell bounds/loaded/replaceable/no-BE validation precedes transactional assembly/rollback.

AdventureScarecrowItem:19–43 adds community-soil rights/support in Adventure, checks CommunityFarmBlock base/hoed (not fertilized FarmingBlock), otherwise delegates. StructureProtectionHandler:149 Creative protection bypass cannot bypass item substrate rules. DecorativeMultiblockBlock:275–310 checks parts/facing after updates, not substrate.

**Runtime:** actual item.useOn, Creative server player: full grass SUCCESS, full stone SUCCESS, default lower oak slab FAIL; count remains 1. Thus broad surface restriction reproduced, grass-only disproved for current HEAD.

| Other audited registered families | Restriction / later survival risk |
|---|---|
| Carts/stalls/fountain/dress_form/loom/display_case/water_well/training_dummy, scarecrow | ItemRegistry:2439–2461 common DecorativeMultiblockItem:66 UP gesture/all bottom sturdy. No substrate survival at block level; no new persisted origin needed just for this exemption |
| fern | DecorativePlantBlock:21 DIRT/farmland/moss; :35 removal on lower-neighbor invalidity |
| hedge_bush | HedgeBushBlock:41 soil or another hedge; :57 survival/removal plus segment rules |
| pool_of_blood | PoolOfBloodBlock:63 center support; :75 support-loss removal |
| stalactite | StalactiteBlock:27 ceiling center support; :39 removal, tall footprint remains |
| WineBottle variants | WineBottleBlock:73 floor-center support at placement; no matching substrate-update override found in class |
| triple_metal_door | TripleMetalDoorBlock:65 three-cell space; :89 floor; :122 recurring survival and piece integrity |
| Other props/facing-only blocks/fence | No universal grass rule. Preserve bounds, occupied-cell replacement restrictions and structure consistency |

**Direction:** shared Creative decorative support policy, preserve technical validity and Survival/Adventure gameplay rules. Support-sensitive classes need a persisted Creative-origin/state mechanism or bounded equivalent, default old-world state to existing behavior; temporary permission alone fails player-less canSurvive after updates. Keep structural part checks. Existing protected flower origin is a pattern, not permission to turn crops into unrestricted decorations.

**Confidence:** high source/runtime scarecrow cause. **Next:** surfaces/neighbor/removal/reload across families, actual client Creative versus Survival/Adventure.

## BUG-11 — Fence corner construction order

**Candidate:** britannia_mod:wooden_fence, BlockRegistry:2492; IronFenceBlock is a different vertical family. Exact reported ID unverified. WoodenFenceBlock extends Block; FACING+N/E/S/W flags, connects only to its class. Placement :59 and horizontal updateShape :66 derive :74, vertical updates keep state. A/blockstates/wooden_fence.json has 64 explicit variants (4×16), not multipart, choosing existing isolated/end/end_mirrored/straight/corner/t_junction/cross assets and rotations.

Bits below are NESW. I=initial/current facing; R=compatible neighbor run edge else compatible current else N for E/W or W for N/S. Existing JSON geometry was inspected: corner and t_junction each have ten elements and share five exactly; end and straight have five identical elements; end_mirrored also has five, isolated six, cross eleven. All use minecraft:block/block and ambientocclusion=false. Rendered posts reach y=18 while collision reaches16. Corner and T contain different second-run geometry; the assets needed for additional joins already exist. This supports inspecting their actual rendered alignment before proposing new geometry. Diagnostic inventory: tmp/gameplay-bugfixes/fence-model-inventory.json (computed bounds are a source-geometry estimate, not a baked-model screenshot).

| NESW | Topology | Current facing / model |
|---|---|---|
| 0000 | isolated | I / isolated |
| 1000 | N end | R / end or end_mirrored |
| 0100 | E end | R / end or end_mirrored |
| 0010 | S end | R / end or end_mirrored |
| 0001 | W end | R / end or end_mirrored |
| 1010 | N/S straight | R / straight |
| 0101 | E/W straight | R / straight |
| 1100 | N/E corner | S / corner y270 |
| 0110 | E/S corner | N / corner y0 |
| 0011 | S/W corner | N / corner y90 |
| 1001 | N/W corner | S / corner y180 |
| 1110 | T missing W | W / t_junction y270 |
| 1101 | T missing S | S / t_junction y180 |
| 1011 | T missing E | E / t_junction y90 |
| 0111 | T missing N | N / t_junction y0 |
| 1111 | cross | I / rotated cross |

**Runtime:** getStateForPlacement plus real block/neighbor updates; four L→T→L orientations, all center/arm blockstates recorded at diagnostic.log:541–548 and diagnostic-evidence.txt. This is headless placement-state reproduction, not mouse/visual proof.

| L arms; temporary addition | Center direct→T→returned L | Arm result |
|---|---|---|
| N/E; add S | 1100 F=S→1110 F=W→1100 F=S | unchanged |
| E/S; add W | 0110 F=N→0111 F=N→0110 F=N | unchanged |
| S/W; add N | 0011 F=N→1011 F=E→0011 F=N | **South arm F=W→E→E**, N connection remains true |
| W/N; add E | 1001 F=S→1101 F=S→1001 F=S | unchanged |

**Confirmed cause:** inheritedRunEdge:119–135 changes the south end when T has compatible E facing. After removal, corner N is on an incompatible edge axis, so end retains current E. Center recomputes; adjacent arm preserves history with identical final neighborhood. Different arm facing selects/rotates model and collision, plausibly explaining the report, but desired visible join is still unobserved. No need to assume a stale center or missing asset.

Actual chunk/restart reload and mirror/reverse construction were not executed. Serialized facing normally persists until updates; do not call that a runtime reload test. rotate/mirror :208/:221 transform flags/facing. Final connected state must converge from final neighborhood without an unbounded neighbor-update loop; preserve intentional isolated orientation.

**Confidence:** high reproduced state dependence, medium exact visual match. **Next:** screenshots direct/T/final in four rotations/mirrors, inspect end/corner/T joins, record all states after actual chunk reload.

## BUG-12 — Jumping fences

**Required:** normal level-ground jump behaves like vanilla fence. WoodenFenceBlock:40–43 edge strips are 4 pixels wide and 16 high; collision :202 reuses outline. Corners/T/cross combine strips; rendered brace geometry is independent. Generated MC 1.21.1 FenceBlock.java:37 and CrossCollisionBlock:38–41 use 24-high collision with 16-high outline.

**Runtime:** all four diagnostic corners maxY=1.0 versus vanilla oak fence=1.5. Existing tests check edge width/topology, not jump height. This confirms a collision discrepancy; player movement itself not tested. **Direction:** separate collision/outline; vanilla-height barrier across all intended segments with gaps/ends checked, after BUG-11 connection determinism. **Confidence:** high. **Next:** Survival/Adventure normal jump/walk/cardinal/corner approaches beside vanilla control; flight/boosts/raised terrain are outside normal-level-jump requirement.

## BUG-13 — Fence and path

Meaning unresolved: existing path underneath, making path under/next to fence, or collision/connection. No custom path ID found in BlockRegistry; diagnostic uses minecraft:dirt_path.

Version-matched generated sources under `build/neoform/neoFormJoined1.21.1-20240808.144430/steps/unzipSources/unpacked/`: DirtPathBlock.java:44–62 schedules dirt conversion for solid block above except FenceGateBlock. BlockBehaviour.java:466–491 considers height>=1 collision solid despite noOcclusion. Vanilla oak fence forceSolidOn at Blocks.java:2257. Therefore custom fence above invalidates path, as vanilla fence does.

| Plausible action | Supported behavior |
|---|---|
| Custom fence on existing path | No custom substrate rejection; runtime path→dirt within five ticks |
| Vanilla controls | Oak fence also→dirt; oak fence gate preserves path |
| Vanilla shovel below existing fence | ShovelItem.java:39–70 needs air above for flatten; blocked for both fences |
| Shovel adjacent exposed ground | Vanilla route can work with air above; adjacent custom fence no special veto |
| Britannia shovel on dirt/coarse | Different route: J/item/QualityShovelItem.java:95–108 suppresses vanilla path prediction for ToolRegistry.SHOVEL mainhand gather target; J/dirtgathering/DirtGatheringTarget.java:11 accepts dirt/coarse; DirtGatheringInteractionHandler:29–46 owns/cancels server gather gesture and is registered at J/BritanniaMod.java:157 |
| Walking/visual join | Fence connects only to own class; no client path-edge/movement reproduction |

**Confidence:** high reversion/baseline, low intended interpretation. **Next:** D6 exact action/path/tool; preserving path under fence is an intentional vanilla departure needing bounded support/solidity behavior and side-effect checks. Vanilla inheritance alone does not solve it.

## BUG-14 — Full watering-can texture

**ID/storage:** britannia_mod:watering_can, ItemRegistry:993 stacksTo=1. WateringCanItem:38–40 capacity=12, CUSTOM_DATA WaterCharges. :186–198 clamps 0..12, preserves other custom data, missing tag defaults **full 12**. Tooltip :201 Water X/12. Filling :170–184 sets12; useOn clicked water-fluid route versus use source-only ray; wells/troughs via WaterSourceInteraction:48–51. Soil/tree/flower consume only on improved hydration; Creative avoids consumption but positive-charge guard remains.

Only A/models/item/watering_can.json and 64×64 A/textures/item/watering_can.png found; no full texture/predicate/overrides. Original atlas viewed: dark metal body/handle/spout and dark opening. Inventory/held/dropped share baked item rendering from components, no special can BE renderer.

**Minimal contract:** 0 empty/inoperative, 1..11 partial/usable, 12 full/usable with visible water surface or clear marker. Empty/partial may use existing art plus numeric tooltip; separate partial texture optional. Full model/asset required, none generated here. Derive predicate exactly from getWaterCharges, including legacy default full, not a duplicate boolean. Standard component sync is the basis for containers/items/saves; no new capacity/network system justified.

**Confidence:** high absent state. **Next:** predicate checks and client inventory/held/offhand/dropped 0/1/11/12 after fill/dispense/movement/pickup/reconnect/resource reload; preserve capacity/consumption.

## Executed verification and limits

All commands from verified checkout. Used git status/branch/revision/worktree/log/show; targeted rg; numbered source reads; JAR/ZIP metadata; original image viewing and Pillow measurement. Some exploratory bad paths/PowerShell glob forms were corrected, not test failures. Git warned about unreadable `C:/Users/dusti/.config/git/ignore`; status/comparisons still worked.

Representative actual discovery commands:

```powershell
git status --short
git branch --show-current
git rev-parse HEAD
git worktree list --porcelain
rg --files --hidden -g AGENTS.md -g '!.git' -g '!.claude/worktrees/**'
rg -n 'canSurvive|isFaceSturdy' src/main/java/com/seggellion/britannia_mod/block -g '*Block.java'
rg -n 'tickGrowth\(' src/main/java/com/seggellion/britannia_mod -g '!**/gametest/**'
rg -n 'SHOVEL_FLATTEN|DIRT_PATH|getToolModifiedState' src/main/java/com/seggellion/britannia_mod -g '!**/gametest/**'
python -X utf8 tmp/gameplay-bugfixes/inspect_assets_and_coverage.py
```

Exact test invocations, JAVA_HOME selected in each launch:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot'
# Initial blocked attempts; zero tests executed:
.\gradlew.bat test --tests 'com.seggellion.britannia_mod.farming.*' --tests 'com.seggellion.britannia_mod.patch18.*' --tests 'com.seggellion.britannia_mod.woodenfence.*' --tests 'com.seggellion.britannia_mod.moongate.*' --offline --no-configuration-cache --console=plain
# Successful focused retry with dependency access:
.\gradlew.bat test --tests 'com.seggellion.britannia_mod.farming.*' --tests 'com.seggellion.britannia_mod.patch18.*' --tests 'com.seggellion.britannia_mod.woodenfence.*' --tests 'com.seggellion.britannia_mod.moongate.*' --no-configuration-cache --console=plain --stacktrace
# Existing full baseline, isolated world:
.\gradlew.bat test runGameTestServer --init-script tmp/gameplay-bugfixes/isolated-gametest.gradle --no-configuration-cache --console=plain
# Five temporary diagnostics, separate namespace/world:
.\gradlew.bat runGameTestServer --init-script tmp/gameplay-bugfixes/diagnostic.gradle --no-configuration-cache --console=plain
```

| Run | Result | tmp/gameplay-bugfixes evidence |
|---|---|---|
| Initial sandbox/offline | Wrapper getsockopt permission error; no tests | Tool output |
| Allowed offline retry | cacheVersionExecutableServer1.21.1 artifact unavailable offline; no tests | focused-offline-blocked.log |
| Focused JUnit | BUILD SUCCESSFUL 2m17s; 27 suites, 172 total, 6 skipped, **166 executed passes**, zero fail/error | focused-tests.log, focused-xml/, focused-summary.json |
| Full JUnit | 449 suites, 3479 total, 17 skipped, **3462 executed passes**, zero fail/error | baseline.log, baseline-xml/, baseline-summary.json |
| Existing GameTests | **1102 required pass**, 51.92s; combined build4m21s | baseline.log:34334–34335 |
| Temporary diagnostics | **5 required pass**, 1.472s; build1m58s | diagnostic.log:595–617, diagnostic-evidence.txt |

GameTests require @GameTestHolder/@GameTest and enabled namespace; existing template service_npc_spawn_test_empty generated from src/gametest base64 into build/generated/gametest, generally PrefixGameTestTemplate(false). Baseline init changes only task workingDir to tmp/gameplay-bugfixes/gametest-run; baseline.log:48 confirms config there. Existing run/gametest world unused. Diagnostic init opts in tmp Java/resource input, namespace bugfix_discovery and separate diagnostic-run; existing empty structure copied under test namespace. Five diagnostic sources are opt-in only; regular build source configuration unchanged. The opt-in command regenerated main build outputs/resources: do not distribute an artifact from diagnostic build outputs.

Executed existing relevant coverage includes cultivation readiness/threshold/presentation, flower contracts/transactions, fertile five uses/BE serialize-recreate/two players/perennial/fruit, fence topology/edge width, and moongate single-root/top cleanup/empty collision. JSON/source-contract checks cannot prove appearance. Existing BE serialize/recreate is not a full restart. Temporary tests probe **current** behavior, not desired-regression acceptance: mock players, seeded/batched growth, placement-state+neighbor fence path, actual scarecrow item, scheduled path transitions. No client shaders, actual jumping, offhand packets, chunk unload/reload or full server restart exercised.

## Shared risks and boundaries

- 02/03/05/09 share soil authority/initialization/occupancy, but remain distinct requirements. Resolve target/expiry/moisture before freezing UI/persistence acceptance.
- 08 spans normal crops, roots/parts, fruit/axe, flowers, paid soil uses and future Rowan success. One normal-harvest edit leaves bypasses.
- 01/07 share suspicious chunk RenderTypes in BER but different cutout/translucent material paths. 04 pale art and 06 silhouette are separate. No broad renderer rewrite or blanket fullbright/AO fix justified.
- 11/12 use facing-dependent model/collision; resolve deterministic state first. 13 path solidity may change with collision.
- 10 may need persisted Creative origin in survival-sensitive decorations; scarecrow's shared multiblock path does not itself need a new flag. Keep old-world default and piece integrity.
- Compatibility fixtures: untracked uses, legacy seed/stage, owner/house plots, flower soil snapshots, perennial roots, fence states, missing WaterCharges. No migration authorized by field existence alone.
- Backend unnecessary for local thresholds/visuals; Rowan action attribution and out-of-band skill refresh policy may need coordination. No Rails write/access, seed or database changes performed.

## Progress / resume scratchpad

- Completed: repository/build/runtime inventory, all fourteen source traces, original assets, 67-crop/seven-flower coverage, Rowan comparison, official versioned render docs, full baseline and five isolated probes, three discovery deliverables.
- Confirmed runtime: history-dependent fence arm; 1.0 versus1.5 collision; path reversion matching vanilla; Creative stone/grass scarecrow success/slab denial; expired community waits for random tick; dry lettuce/carrot do not grow through normal dispatch.
- Unresolved: deployed JAR/IDs, client shader/texture comparison, natural-time growth, decisions D1–D6, actual chunk/restart/movement/multiplayer client acceptance.
- Next: user decisions then a separate implementation playbook. Do not treat preliminary milestone proposal as authorization to implement.
- Changes: three documentation deliverables; ignored tmp/gameplay-bugfixes scripts, copied template, XML/logs and disposable worlds/configs; ordinary Gradle build/cache outputs. Original two untracked farming docs preserved. No production source/assets/configs/worlds/seeds/DB modified; no commit/push/branch/deployment/new worktree.

## 2026-09-06 supplemental scope and decision addendum

**This addendum supersedes pending decisions and contrary recommendations above; the preceding original discovery is preserved as historical evidence.** Mod revision remains patch-18 / `421e27853dde4099d1d794568e33e6709507a53b`. Independent Rails discovery inspected Ubuntu WSL `/home/dusti/ultimacraft-website`, release/public / `a9425ca41afa9b4ba7539426044e7b50e70a3966`. No implementation or production state change occurred.

Current scope is **19 tracked reports,16 functional issues in scope,3 excluded artwork reports**. BUG-04/06/07 receive no further investigation task, milestone or aesthetic gate. Historical asset evidence above does not restore them to scope. User supplies full watering-can art; state selection/sync remains code work. Functional moongate shader compatibility and fence model selection remain scope.

| Adopted decision | Explicitly superseded original guidance |
|---|---|
| D1 | Empty-only60second expiry restores previous **hoed soil**, never ordinary dirt/unprepared community base as timeout target; downtime excluded; planted crops protected. Earlier unanswered planted/private-reversion questions are closed. Deterministic online timing and preparation-budget restoration proposal is in supplemental discovery. Remaining entitlement accounting is separately identified there. |
| D2 | Prepared community plus minecraft:farmland inside acting player's owned house. Reject ordinary/coarse and farmland elsewhere. Earlier generic-private/community-only alternatives no longer apply; authoritative dimension/owner/fullBox required. |
| D3 | Buckets/cans/**bowls**/rain intended; rain qualifies without manual watering. Do not change initial fertilizer moisture. Earlier dry-fertilizer/manual-only alternatives superseded; bowl hydration now confirmed missing. |
| D4 | Eligible failure destroys produce with lifecycle-appropriate plant consumption/regrowth; carrots/green onions consumed; no universal perennial uproot or usable produce refund. Minimum refusal distinct. Numeric odds/costs/gains/Creative remain open. |
| D5 | Grapes must obey planting and harvest restrictions; both bypasses must be addressed. Cultivated flowers use existing species values. Earlier option to retain grape exemption is superseded; native outside-system scope remains separate. |
| D6 | Fence on existing vanilla-shovel-made dirt_path must preserve path, intentionally departing from baseline. Retain L→T→L arm defect and vanilla-height collision. Earlier ambiguity about exact requested path action is closed; making path under existing fence is not a new requirement. |

New findings: BUG-15 native landscape feature/stem/structure routes confirmed in development runtime; BUG-16 three bowl recipes/seven pigment variants mainhand-only; BUG-17 final output is compatible but directly assigned to exhausted mainhand; BUG-18 canonical broccoli/orange excluded by missing commodity mappings before Rails and absent from seed source (live catalog not verified); BUG-19 empty-mainhand inventory extraction and decorator-triggered case dismantling reproduced. Full count/components conserved in corrected16-gesture case probe; no evidence of merchandise loss from the out-of-active-area diagnostic query failure.

New evidence:83 existing JUnit passes,0failures/0skips. Supplemental6tests:5pass/1failed assumption exposing dismantling. Follow-up initially1pass/1fixture failure, corrected active-area2pass. Original3462JUnit/17skip,1102GameTest and5temporary results remain historical, not rerun totals. No Rails integration or real-client acceptance claimed. Temporary inputs/generated outputs are not a clean release build.

See [supplemental discovery](C:/projects/britannia/mod/Britannia_Mod/docs/projects/gameplay-bugfixes/ULTIMACRAFT_BUGFIX_SUPPLEMENTAL_DISCOVERY.md), [revised planning inputs](C:/projects/britannia/mod/Britannia_Mod/docs/projects/gameplay-bugfixes/ULTIMACRAFT_BUGFIX_PLANNING_INPUTS.md) and [unchecked acceptance draft](C:/projects/britannia/mod/Britannia_Mod/docs/projects/gameplay-bugfixes/ULTIMACRAFT_BUGFIX_ACCEPTANCE_DRAFT.md). They define current scope and proposed dependencies for a later playbook; no fix implementation starts here.
