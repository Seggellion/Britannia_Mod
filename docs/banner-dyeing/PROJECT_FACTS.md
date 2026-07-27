# Banner and Dyeing Project Facts

Date: 2026-07-27

Milestone: 16 Batch 1A

Feature branch: `banners-dyetub`

## Milestone 16 Batch 1A Road Guard facts

- `britannia_mod:road_guard` is the sole integrated final-content proof of concept. Its stable catalogue index remains
  `27`, its approved display name is `Road Guard`, its approved dimensions are 1 x 1, and it supports both
  `wall_parallel` and `wall_perpendicular` with brass and iron mounts (brass default).
- The approved intake is
  `content/banner-final-intake/submissions/road_guard/road_guard.yml`; it was approved by `Product Owner` on
  `2026-07-27` and validates as `READY_FOR_INTEGRATION`.
- Runtime assets are `britannia_mod:banner/road_guard/base_texture` and
  `britannia_mod:banner/road_guard/dye_mask`, both 64 x 64 RGBA. Their approved SHA-256 values are
  `a75880a969570e47fdff0b15eb812eb9e94564f345c424c00af92fe3d4cb1240` and
  `89495bc3e8b9b8a4e98424d539536b57853f08bf20771cf3456151014120b669`.
- The product owner pre-positioned the runtime PNGs. Integration validates their hashes and metadata but scaffold
  generation never recreates or rewrites their bytes. Scaffold metadata records their approved hashes.
- Road Guard uses custom planar item geometry `britannia_mod:banner/road_guard/geometry`, adapted from the approved
  Blockbench source with the legacy mount group omitted. Placed rendering resolves custom geometry through the
  approved synchronized 1 x 1 footprint, without a banner-specific rendering branch.
- The approved placement profile reuses `britannia_mod:placeholder_x_small`. This is an approved profile convention,
  not a claim that the Road Guard artwork is placeholder content.
- Road Guard is `in_progress`; the other 32 definitions remain `placeholder`. No definition is `complete` or
  disabled. Manual item, preview, placed, persistence, reload, and visual-colour review remains unperformed, so Gate E
  is not ready.
- `BannerInstanceState`, its persistent/stream codecs, block-entity state, placed structure, stable IDs, and save
  semantics are unchanged.

## Milestone 16A two-file asset facts

- Every banner has one complete, full-colour `base_texture` and one grayscale-alpha `dye_mask`. There is one
  rendering strategy and no separate fixed-art layer.
- The base texture is the banner's complete default appearance: silhouette, transparency, native colours, cloth
  texture, heraldry, borders, fixed details, highlights, and shadows. It is never tinted by the resolved dye colour.
- A transparent mask pixel preserves the corresponding base pixel. An active mask pixel supplies grayscale
  brightness and texture, is multiplied by the resolved dye colour, and blends over the base according to mask
  alpha. Fixed artwork therefore remains in the base and has a transparent corresponding mask pixel.
- The unchanged natural/default state renders base plus mount. A source pigment, or an administratively selected
  resolved colour different from the material's natural colour, activates the tinted mask pass. A pigment resolving
  to the natural colour still activates the mask because a pigment source is present.
- Cotton, wool, linen, and silk remain persisted data identities rather than texture identities. They control natural
  colour, palette membership, dye resolution, tooltips, validation, and metadata; all use the same authored banner
  base by design.
- `BannerInstanceState`, placed-structure state, stable IDs, catalogue dimensions, orientations, mounts, placement
  profiles, and save semantics are unchanged. Definition schema version remains `1`; the existing display payload
  type has no independent numeric schema field and now projects only base and mask resource identities.
- All 33 controlled definitions use `base_texture` plus `dye_mask`. Road Guard is the sole approved
  `in_progress` entry; the other 32 remain `placeholder`. Milestone 17 has not started.

## Milestone 16 preparation facts

- Milestone 16 Batch 1 stopped without edits or a commit because none of the seven extra-small definitions had
  approved final owner decisions, original final artwork, provenance, or manual verification evidence. Stable IDs
  alone remain approved.
- The owner-facing workflow is now documented in `FINAL_CONTENT_INTAKE.md` and
  `FINAL_CONTENT_REVIEW_CHECKLIST.md`. Unapproved JSON-compatible YAML templates live under
  `content/banner-final-intake/`, outside runtime resources.
- `tools\scaffold_banners.bat --check-final-intake <path>` invokes a read-only validator from the existing scaffold
  source set. It reads one intake, the live catalogue identity list, and referenced source files; it never writes,
  copies, generates, stages, publishes, or changes content.
- Validator statuses are `NOT_READY`, `READY_FOR_INTEGRATION`, and `INVALID`. Readiness requires explicit approval,
  complete supported decisions, unambiguous non-placeholder asset mappings, existing source files with matching
  SHA-256 values, 8-bit RGBA layer PNGs, original-art provenance, and distribution permission. Manual in-game
  verification is deliberately not required to begin integration but remains mandatory before `complete`.
- Normal scaffold generation and `--check` behavior are unchanged. Intake files are not under
  `src/main/resources`, are not a registry domain, and cannot modify or augment the 33-definition runtime dataset.
- No banner definition, catalogue value, content status, runtime asset, registry, renderer, placement rule, dyeing
  behavior, persistence contract, command, or acquisition behavior changed. Milestone 16 remains pending actual
  approved content, and Milestone 17 has not started.
- Banner crafting remains rejected and product-disabled. Intake contains no recipe, pattern, crafting-input, price,
  NPC/shop, arbitrary-NBT, runtime-component, source-pigment, or item-orientation fields.

## Milestone 15 command and service integration facts

- `CommandRegistry` subscribes once to NeoForge's common/server `RegisterCommandsEvent`. Milestone 15 registers one
  coherent `britannia` root from that existing event path; it does not register a client command or custom command
  packet.
- The established production administrator convention is `CommandSourceStack.hasPermission(2)`. The requirement is
  placed on the `britannia` root before registry access, so every banner-give, dye-tub-give, validation, placeholder,
  and dye-resolution branch inherits the same operator/game-master permission.
- Existing repository commands are separate top-level literals and provide no shared mod administration root.
  `britannia` is therefore the provisional root chosen from the mod identity. Registry suggestions are computed from
  the current immutable `BannerDataRegistries` publication for every request and are never cached across reloads.
- Existing item delivery convention is normal inventory insertion followed by `ServerPlayer.drop(stack, false)` for
  a non-inserted remainder. Milestone 15 centralizes that policy behind `AdminItemDelivery`: each target receives an
  independent stack, only a non-empty remainder is dropped, and one target's failure does not roll back another.
- Command registration/permission/tree shape is exercised through the actual Brigadier dispatcher in plain JUnit.
  Item construction, delivery, diagnostics, suggestions, and read-only projections use real production services and
  registered production-shaped `ItemStack` fixtures. The repository still has no GameTest command-source bootstrap,
  so no live dedicated-server command execution is claimed.
- No existing NPC, merchant, shop, or service-provider registry offers a dependency-free pigment-source extension
  point. `PigmentSourceService` is therefore exposed as a standalone common-side API with stable IDs, authoritative
  registered-item resolution, typed failures, count validation, and immutable deterministic listing. No adapter
  modifies an NPC or shop.
- The public administration syntax is `/britannia banner ...` and `/britannia dye ...`. This is development/operator
  acquisition only; it adds no survival distribution, crafting, loot, NPC inventory, shop, economy, or dialogue path.

## Corrective Milestone 14R product boundary

- Milestones 0 through 13 remain implemented.
- Milestone 14 introduced a banner-pattern and banner-recipe system from the draft playbook in commit
  `6803f5ee232800f780712dd2c6cdc8b34cd84988`. The product owner rejected that system before release.
- Banner crafting is not implemented. There is no banner pattern item/component, banner recipe serializer, banner
  recipe JSON, banner crafting tag, or standalone cloth/mount crafting-input item.
- Banner acquisition is deferred to approved admin/development tooling and future product decisions. Corrective
  Milestone 14R does not provide a replacement acquisition path and does not begin Milestone 15.
- The authoritative feature registration boundary is nine items: one shared banner, one dye tub, and seven pigments.
  The three feature-related registered data components remain unrelated wine data, dye-tub state, and banner-instance
  state.
- The 33 banner definitions, four fabrics, four palettes, seven pigments, two mounts, placement state, rendering,
  dyeing, persistence, and `BannerItemFactory` remain authoritative and unchanged.

## Milestone 13 placed-rendering and synchronization facts

- NeoForge 21.1.72 registers block-entity renderers through the client-only
  `EntityRenderersEvent.RegisterRenderers` event. Britannia registers one renderer for the authoritative
  `britannia_mod:banner` anchor block entity and no renderer for `banner_part`.
- `IBlockEntityRendererExtension.getRenderBoundingBox` supplies the dynamic multi-cell frustum bounds. The renderer
  uses a finite 64-block view distance and measures distance to the full placed bounds rather than only the anchor.
- Placed cloth is generated client-side as bounded, two-sided geometry for the five existing footprint families and
  both persisted orientations. Milestone 16A supersedes Milestone 13's former three-image placeholder contract with
  a complete base texture, selective dye mask, brass/iron mounts, and the missing-content texture.
- Item and placed rendering share immutable appearance resolution and appearance keys. Only an active dye-mask pass
  receives the resolved material colour; the complete base and brass/iron mounts remain untinted.
- Appearance and placed-plan caches are independently bounded at 256 entries, as is placed diagnostic de-duplication.
  Client banner-data publication and resource/model reloads advance generation state and clear all banner render
  caches, so removed or restored content is not retained indefinitely.
- Placed light is the maximum block and sky light sampled from the anchor plus at most the six already-loaded
  occupied cells. Rendering never requests or force-loads a chunk and does not use full-bright lighting.
- The anchor block entity's update tag and `ClientboundBlockEntityDataPacket` carry the same banner and placed
  structure state used for disk persistence. Server-side live replacement marks the anchor changed and emits a block
  update; the client renderer extracts current block-entity state on each render and changes keys without replacing
  blocks or child cells.
- Anchor and part blocks explicitly use `RenderShape.INVISIBLE`. Their established selection/collision shapes remain
  diagnostic and only the anchor renderer emits the complete visual.

## Milestone 12 orientation and preview integration facts

- `BannerOrientation` is a stable string-serializable blockstate property. The selected orientation is ephemeral
  server-owned per-player state, not part of `BannerInstanceState`; only an S2C display update is required because
  standard item `useOn` already reaches the server with sneak state.
- The authoritative persisted placement record stores orientation with its ordered footprint. Parallel and
  perpendicular transforms, inverse part lookup, support positions, and per-cell shapes are centralized in
  `BannerStructureTransform` and shared by placement, lifecycle, integrity, and preview code.
- Parallel width follows viewer-right (`FACING.getCounterClockWise()`) and its top row requires backing faces.
  Perpendicular width follows outward `FACING` and only the anchor requires a backing face.
- NeoForge 21.1.72 exposes `RenderLevelStageEvent` with `AFTER_PARTICLES`, camera pose/rotation, `RenderType.lines()`,
  `LevelRenderer.renderLineBox`, and `Font.drawInBatch`. The placement ghost uses these client-only APIs and only
  checks chunks already reported loaded by the client level.
- Client registry projection now includes definition dimensions, supported orientations, and supported mounts. It is
  display-only and cannot authorize placement. Client-success previews are classified as unknown for server-only
  protection until the server revalidates normal placement.
- Brass and iron are both verified mount IDs through item state, render projection, placement plans, persistence, and
  previews. M12 does not give mounts different footprint/support behavior and does not add a placed banner renderer.

## Milestone 11 placement and lifecycle integration facts

- Registered structure content is `britannia_mod:banner` (authoritative anchor),
  `britannia_mod:banner_part` (one generic occupied-part block), and
  `britannia_mod:banner` (the existing anchor-only block-entity type). The part has neither a block entity nor a
  `BlockItem`.
- NeoForge 21.1.72 calls `IBlockExtension.onDestroyedByPlayer` while the block and its block entity are intact, before
  vanilla removes the selected block and later invokes `playerDestroy`. Banner anchor and part blocks use that hook
  to claim whole-structure removal; their vanilla `playerDestroy`/loot paths are intentionally empty.
- `BlockBehaviour.onExplosionHit` evaluates loot before invoking `onBlockExploded`. Both banner blocks override the
  former and route directly to the lifecycle service. The selected Milestone 11 policy is no configured banner drop
  from explosions, so callbacks for several occupied cells cannot duplicate an item.
- `PushReaction.BLOCK` is the platform-supported immovable reaction for both pushing and sticky-piston pulling; both
  anchor and part properties use it.
- `ChunkEvent.Load` explicitly warns against immediate level access before promotion to a full chunk. Banner integrity
  work is therefore queued and processed after `ServerTickEvent.Post`, using `getChunkNow`/`hasChunk` only. Placement,
  resolution, and repair never request or force-load a chunk.
- The clicked wall-adjacent target is the top-left anchor viewed from the banner front. `FACING` points outward toward
  the viewer; viewer-right is `FACING.getCounterClockWise()`. Local horizontal offsets increase viewer-right and local
  vertical offsets increase downward.

## Evidence basis

These facts are grounded in the two root specifications, repository files at commit `62df1dc97c5113a86f9c0f258cb90538f31efe89`, Git output after `git fetch --prune origin`, and the baseline Gradle output recorded in `IMPLEMENTATION_LOG.md`.

The authoritative specifications were read in full before the feature branch or these documents were created:

- `UltimaCraft_Banner_Dyeing_LLM_Build_Spec.md`
- `UltimaCraft_Banner_and_Dyeing_System_Design.md`

## Resolved repository tokens

| Token | Resolved value | Evidence |
|---|---|---|
| `<MOD_ID>` | `britannia_mod` | `gradle.properties` (`mod_id`) and `BritanniaMod.MODID` |
| `<NAMESPACE>` | `com.seggellion.britannia_mod` | `gradle.properties` (`mod_group_id`) and `src/main/java/com/seggellion/britannia_mod/` |
| `<SOURCE_ROOT>` | `src/main/java` | Gradle Java source set and 634 Java source files |
| `<RESOURCE_ROOT>` | `src/main/resources` | `build.gradle` main resource source set |
| `<TEST_ROOT>` | `src/test/java` (conventional path, currently absent) | Gradle `test` task exists; `compileTestJava` and `test` report `NO-SOURCE` |
| `<GAMETEST_ROOT>` | No source root implemented | `runGameTestServer` exists and run configurations set `neoforge.enabledGameTestNamespaces=britannia_mod`, but there are no GameTest sources |
| `<GENERATED_ROOT>` | `src/generated/resources` (configured, currently absent) | `build.gradle` adds it to `sourceSets.main.resources` |
| `<DOCS_ROOT>` | `docs` | Existing `docs/quest_destroy_manual_test_checklist.md` |

## Build and platform

- Build system: Gradle wrapper 8.9 with Groovy `build.gradle` and NeoGradle UserDev plugin `net.neoforged.gradle.userdev` 7.0.165.
- Language: Java only. No Kotlin source files exist.
- Java target/toolchain: Java 21 (`JavaLanguageVersion.of(21)`). The verified Gradle launcher was Microsoft Java 21.0.8. The shell-level `java` command resolves to Java 8, so later commands must continue to use the wrapper/toolchain rather than assume the shell JVM.
- Minecraft: 1.21.1.
- Loader/framework: NeoForge 21.1.72, Java FML loader 4+.
- Mappings: the active UserDev pipeline applies official mappings (`neoFormApplyOfficialMappings` in build output). `gradle.properties` contains Parchment version properties, but `build.gradle` does not configure or consume them; Parchment is therefore not the active mapping layer.
- Packaged libraries: GeckoLib 4.6.6 and NanoHTTPD 2.2.0 are included through `jarJar`; Mixin 0.8.5 and MixinExtras 0.3.5 are configured.
- Mod version at the branch point: `0.1.7k`.
- Metadata generation: `generateModMetadata` expands `src/main/templates/META-INF/neoforge.mods.toml` into `build/generated/sources/modMetadata`.

## Framework APIs and integration paths

| Concern | Repository API/convention | Later banner/dyeing integration path |
|---|---|---|
| Item and block registration | NeoForge `DeferredRegister`, `DeferredHolder`, and the mod event bus | Register through focused feature registries called from `BritanniaMod`, following `registry/ItemRegistry.java` and `registry/BlockRegistry.java`; do not add a large banner combination switch |
| Item instance data | Registered `DataComponentType<T>` with a persistent Mojang `Codec` and network `StreamCodec`; `WineData` is the working precedent | Add typed, versioned banner and dye-tub components through `registry/DataComponentRegistry.java`; prefer this over raw `DataComponents.CUSTOM_DATA` used by older quality/material items |
| Stable identifiers | Minecraft `ResourceLocation.fromNamespaceAndPath` / `ResourceLocation.parse` | Use namespaced `ResourceLocation` values under `britannia_mod` for definitions, materials, pigments, colours, mounts, placement profiles, packets, models, and textures |
| Block entities | `DeferredRegister<BlockEntityType<?>>` plus `BlockEntityType.Builder.of` in `registry/BlockEntityRegistry.java` | Register a banner anchor block entity there or in a feature-scoped registry registered from `BritanniaMod` |
| Block-entity persistence | `saveAdditional` / `loadAdditional` with `HolderLookup.Provider`; `setChanged`; `sendBlockUpdated`; `getUpdateTag`; `ClientboundBlockEntityDataPacket.create` | Store versioned banner instance state on the anchor and use the established update-tag/update-packet pattern for client rendering |
| Networking | NeoForge `RegisterPayloadHandlersEvent`, versioned `PayloadRegistrar`, `CustomPacketPayload` records, `StreamCodec`, `playToServer` / `playToClient`, and `IPayloadContext.enqueueWork` | Define banner/dye payload records under `network/payload`, register them in `NetworkHandler.register`, and keep all mutation and colour resolution server-authoritative |
| Screen flow | Custom client `Screen` instances opened by an S2C payload in `ClientNetworkHandler`, with C2S payloads for actions; blacksmithing is the closest precedent | Use an S2C preview payload to open a client-only dye preview `Screen` and a C2S confirm payload that revalidates both hands and resolves the result again on the server |
| Container menus | Only vanilla `ChestMenu.threeRows` is used by inventory block entities; no custom `MenuType` is registered | The dye preview has no inventory slots, so the repository's direct `Screen` + payload pattern is the selected convention unless a later requirement introduces server-managed slots |
| Client-only separation | One Java source set, client packages, `@EventBusSubscriber(... value = Dist.CLIENT)`, `@OnlyIn(Dist.CLIENT)`, and `FMLLoader.getDist().isClient()` guards | Put screens/models/renderers under `com.seggellion.britannia_mod.client`; register them from the `Dist.CLIENT` subscriber; common state, codecs, registries, and packet definitions must not import `net.minecraft.client` |
| Placed rendering | `EntityRenderersEvent.RegisterRenderers` and `registerBlockEntityRenderer` in `ClientModSetup` | Register the anchor renderer through `ClientModSetup`; only the anchor renders the complete multi-block banner |
| Item rendering | NeoForge 21.1.72 `ModelEvent.RegisterAdditional` + `ModelEvent.ModifyBakingResult`, vanilla `ItemOverrides`, and `IBakedModelExtension.getRenderPasses` support a current dynamic baked-model path | The shared banner item installs one wrapper model at bake/reload time, resolves immutable stack appearance keys through overrides, and returns family/mount baked passes; do not copy deprecated `Item.initializeClient` |
| Models/assets | `assets/britannia_mod/models`, `textures`, `geo`, blockstates, language JSON, GeckoLib geometry, and a custom geometry loader registered in `ClientModSetup` | Place banner assets under `src/main/resources/assets/britannia_mod/...`; each banner uses one complete base texture and one selective grayscale-alpha dye mask, while mount resources remain separate |
| Commands | Brigadier command classes with a static `register(CommandDispatcher<CommandSourceStack>)`, collected by `CommandRegistry` on `RegisterCommandsEvent` | Add later admin/debug commands as a focused command class registered by `CommandRegistry`, with permission predicates and registry-backed suggestions |
| Recipes | One static vanilla recipe override at `src/main/resources/data/minecraft/recipes/diamond_pickaxe.json`; blacksmith crafting uses the in-code `CraftableRegistry`, `CraftableDef`, payload, and server-side `BlacksmithCrafting` service | Banner crafting is not an approved feature; any future acquisition or crafting design requires a new product decision |
| Data generation | `runData` exists and `src/generated/resources` is configured, but there are no providers, `GatherDataEvent` listeners, or generated resources | The first banner data-generation milestone must establish the repository's first provider/check workflow; until then, authored JSON belongs under `src/main/resources/data/britannia_mod` |
| Tests | Gradle `test` and NeoForge `runGameTestServer` tasks exist; there are no unit or GameTest sources and no explicit test dependencies in `build.gradle` | Add deterministic codec/colour tests under `src/test/java`; use `runGameTestServer` for placement/persistence tests once a GameTest source/configuration is established |

## Milestone 9 item-rendering and client-data decision

The actual NeoForge 21.1.72 source JAR marks `Item.initializeClient` deprecated for removal and directs callers to
`RegisterClientExtensionsEvent`. A client extension/BEWLR is unnecessary for banners: the current baked-model API
provides `ModelEvent.RegisterAdditional`, `ModelEvent.ModifyBakingResult`, stack-aware `ItemOverrides`, standard
`applyTransform(ItemDisplayContext, ...)`, and `IBakedModelExtension.getRenderPasses`. Milestone 9 therefore uses one
client-only baked-model wrapper for the one shared item and keeps repository-standard transforms for GUI, hand,
ground, and fixed contexts.

Packaged models and textures remain client resources, while authoritative definition assets, material palette
display sRGB values, mount assets, and placeholder status are projected from the server registry into a display-only
`S2CBannerRenderDataPayload` on login and data-pack synchronization. Client publication is atomic and generation
tracked. It cannot resolve dyes or authorize mutation. A server override may reference a client-packaged asset; an
unknown or absent asset is intentionally diagnostic because server data packs do not distribute client resources.

## Item data decision

`DataComponentRegistry.WINE_DATA` is the closest safe precedent. It registers a typed component using `DataComponentType.builder().persistent(WineData.CODEC).networkSynchronized(WineData.STREAM_CODEC)`, and `WineBottleItem` reads and writes that component directly on `ItemStack`.

Later milestones should therefore create typed, immutable data components for banner instance state and dye-tub state. Older `QualitySwordItem` and `MaterialQualityJewelryItem` code stores strings and integers inside `DataComponents.CUSTOM_DATA`; it is useful migration evidence but is not the selected architecture for a new versioned feature.

## Block-entity decision

The banner block entity should follow `WineBottleBlockEntity` for persistence and synchronization:

- Register with `BlockEntityRegistry` / `BlockEntityType.Builder.of`.
- Persist through `saveAdditional` and `loadAdditional` using `HolderLookup.Provider`.
- Call `setChanged()` for mutations.
- Send a block update after authoritative state changes.
- Supply `getUpdateTag()` and `getUpdatePacket()` for tracking clients.

For multi-block placement, only the anchor will own the complete banner instance state. Child cells should contain only the minimum anchor reference required by the specification. Gameplay code for this is deferred to its assigned milestones.

## Networking and screen decision

`NetworkHandler` registers typed custom payloads with protocol registrar version `"1"`. Server-bound handlers enqueue work and validate/mutate using `ServerPlayer`; client-bound screen handlers are selected only on the client distribution and call `Minecraft.setScreen` from `ClientNetworkHandler`.

The dye preview/confirm flow should use this same pattern. The client will send intent only. The server must read both hands, the tub pigment, banner material, and current state; calculate the resolved colour; and revalidate everything when confirmation arrives.

## Material and blacksmithing integration facts

- `UOMetalToolMaterial` is a metal-specific enum tied to ingot suppliers and Minecraft `Tier`. It resolves material from the held ingot by item identity.
- `BlacksmithCrafting.processCraftRequest` is server-side, re-reads the off-hand stack after the client request, determines `UOMetalToolMaterial`, checks/consumes ingredients, rolls skill/quality, then calls `WeaponRegistry.createWeapon`.
- `CraftableRegistry` is an in-code `ConcurrentHashMap<String, CraftableDef>` populated at startup. Its IDs are strings; results use `ResourceLocation`.
- Crafted weapon material is currently serialized as a lower-case string in `DataComponents.CUSTOM_DATA` by `QualitySwordItem`.
- `MaterialQualityJewelryItem` defines a second, separate metal enum, confirming that the repository has no general-purpose material-ID abstraction shared across crafting systems.

Decision: do not extend either metal enum with cotton, wool, linen, or silk. The existing namespaced fabric-material
registry remains authoritative for banner state, dyeing, validation, and rendering. It does not imply a crafting
input mapping. Any future banner acquisition or crafting design requires a new product decision.

## Recipe and data facts

- The only vanilla JSON recipe file is `src/main/resources/data/minecraft/recipes/diamond_pickaxe.json`.
- Blacksmithing recipes are not vanilla `Recipe` implementations; they are Java records registered in `CraftableRegistry` and invoked through a custom screen/payload flow.
- `LocalRecipes` is an immutable economy-cost lookup and is not a Minecraft crafting recipe system.
- `OreVeinLoader` reads one classpath Gson resource at startup; no server resource-reload listener exists.
- `src/generated/resources` is configured but does not exist at this branch point.

The banner catalogue must remain data-driven as specified. Introducing a validated reloadable definition layer and a data/scaffold workflow is work for later milestones, not Milestone 0.

## Test and verification facts

- `test`, `testJunit`, and `runGameTestServer` tasks are available.
- `src/test`, `src/gametest`, and test classes are absent.
- The baseline `test` task succeeds with `NO-SOURCE`.
- The project compiles and packages when `clean` and `build` are run as separate Gradle invocations.
- A combined parallel `clean build` invocation reproducibly fails during `neoFormPatch`; details and the safe diagnostic workaround are in `IMPLEMENTATION_LOG.md`.

## Branch, commit, and release conventions

- Remote: `origin` at `https://github.com/Seggellion/Britannia_Mod.git`.
- Release integration branch for this project: `patch-18`.
- Verified `patch-18` and `origin/patch-18` tip: `62df1dc97c5113a86f9c0f258cb90538f31efe89`, zero divergence after fetch.
- Feature branch: `banners-dyetub`, created at the same commit with zero divergence at creation.
- Current CI: `.github/workflows/build.yml` runs Gradle `build` on pushes and pull requests with Temurin Java 21.
- The history commonly uses issue-number subjects and version branches (`0.1.x`); newer feature branches also use Conventional Commit-style subjects. This project follows the build specification's milestone commit subjects.
- Required integration path: `banners-dyetub` → review/PR or merge into `patch-18` → integration and release QA on `patch-18` → live promotion only after explicit approval.
- No merge, push, or pull request is part of Milestone 0.

## Catalogue rule

The release catalogue is non-negotiable:

- It contains exactly 33 banner entries.
- Unnamed source banners are not excluded.
- Every unnamed banner receives a stable provisional ID and a visible `Name Required` (or equivalent) status.
- Provisional names and dimensions are implementation placeholders, not final lore or approved content.
- Final names, dimensions, and content may be supplied later without changing the system architecture.

Milestone 0 does not scaffold or register those entries.
