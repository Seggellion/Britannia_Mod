# Banner and Dyeing Project Facts

Date: 2026-07-20

Milestone: 0 — Repository Discovery and Implementation Facts

Feature branch: `banners-dyetub`

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
| Models/assets | `assets/britannia_mod/models`, `textures`, `geo`, blockstates, language JSON, GeckoLib geometry, and a custom geometry loader registered in `ClientModSetup` | Place banner assets under `src/main/resources/assets/britannia_mod/...`; keep dyeable fabric, dye mask, static overlay, and mount resources separate as required by the specifications |
| Commands | Brigadier command classes with a static `register(CommandDispatcher<CommandSourceStack>)`, collected by `CommandRegistry` on `RegisterCommandsEvent` | Add later admin/debug commands as a focused command class registered by `CommandRegistry`, with permission predicates and registry-backed suggestions |
| Recipes | One static vanilla recipe override at `src/main/resources/data/minecraft/recipes/diamond_pickaxe.json`; blacksmith crafting uses the in-code `CraftableRegistry`, `CraftableDef`, payload, and server-side `BlacksmithCrafting` service | Reuse the blacksmith server-validation flow and material-from-input concept, but banner recipes require a fabric-specific identity rather than the metal enum |
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

Decision: do not extend either metal enum with cotton, wool, linen, or silk. Later banner milestones should use namespaced fabric material IDs in their own data-driven registry while reusing the blacksmithing interaction principles: determine material from authoritative recipe inputs, validate on the server, and store the resolved identity as typed item state.

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
