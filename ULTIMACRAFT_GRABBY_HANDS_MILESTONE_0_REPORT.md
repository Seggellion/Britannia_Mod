# UltimaCraft — Grabby Hands Milestone 0 Reconnaissance Report

**Date:** 2026-08-12
**Worktree:** `C:/projects/britannia/mod/Britannia_Mod-grabby-hands`
**Branch:** `grabby-hands` @ `50061f07`
**Companion docs:** `ULTIMACRAFT_GRABBY_HANDS_DESIGN.md`, `ULTIMACRAFT_GRABBY_HANDS_PLAYBOOK.md`

> **PARTIALLY SUPERSEDED.** `ULTIMACRAFT_GRABBY_HANDS_MILESTONE_0_ADDENDUM_AUTHORIZATION.md` replaces §12, §16 (adapter core), §17 (rows 5/6/12/13) and §19–§20 following the owner's direction on authorization, lifecycle ownership and provenance. Sections marked **[SUPERSEDED]** below are retained for their evidence only; read the addendum for the current design.

---

## 1. Milestone verdict

**PASS WITH DOCUMENTED RISKS.**

Every M0 gate item is answered from source. Baseline `test` is green (1679 tests, 0 failures, 0 errors, 17 skipped).

Three findings materially change the design and are flagged as risks, not blockers:

1. **The wine bottle is already a placeable block with a block entity and a working item↔block state transfer.** It is not the hard case the design assumed. The hard case is the *container*.
2. **The project already contains a near-complete Grabby Hands transaction service** — `ShrineLifecycleService` — built for multi-block shrines. → **Owner ruled: reference only.** No domain relationship; Grabby Hands owns its own named lifecycle/transaction services and neither generalises nor duplicates the shrine implementation. Addendum Part 2.
3. **The project's existing Adventure-mode strategy contradicts design invariant #1 ("do not switch game mode").** `CityGameModeHandler` flips players between ADVENTURE and SURVIVAL every tick based on held item. → **Owner ruled: preserve its gameplay purpose, do not design around it.** Full authorization path traced in addendum Part 1; the resulting design is game-mode independent and touches no existing authorization class.

---

## 2. Git / worktree facts

| Fact | Value |
|---|---|
| Repository root | `C:/projects/britannia/mod/Britannia_Mod` |
| Grabby Hands worktree | `C:/projects/britannia/mod/Britannia_Mod-grabby-hands` (created this milestone) |
| Branch | `grabby-hands` |
| HEAD | `50061f07` — *Fix server tick crash: NPC lifecycle sync without Rails credentials* |
| Base chosen | `patch-18-network-integration` |
| Working tree | Clean except `gradle/wrapper/gradle-wrapper.jar` (see §3 note) |
| Remote | `origin` → `https://github.com/Seggellion/Britannia_Mod.git` |

### Integration-base evidence

`main` (`62df1dc9`) is *not* the working integration base — every active feature branch is 200+ commits ahead of it and nothing merges back. The real trunk is a chain:

```
main 62df1dc9
  └── … → patch-18 5c9f4f2f
            └── patch-18-network-integration 50061f07   ← chosen base
                  ├── managed-vegetation      (+36)
                  ├── client-branding         (+31)
                  ├── feature/questengine-…   (+33)
                  └── codex/wild-reagents     (+40, contains managed-vegetation)
```

`git rev-list --left-right --count patch-18-network-integration...<branch>` returns `0 N` for all five, i.e. `50061f07` is a strict ancestor of every active feature branch. It is the newest point shared by all of them and carries no unrelated feature work of its own.

### Unrelated work protected

The primary worktree is on `codex/wild-reagents` with uncommitted Wild Reagents work (4 modified, 20 untracked). **Nothing in it was touched.** The new worktree is a separate checkout.

### Merge-order dependency — **[RESOLVED, no longer applies]**

`codex/wild-reagents` adds `mixin/client/ManagedVegetationAdventureModeMixin`, the only clean Adventure-mode *break* enabler in the repo (§12), and it is absent from the grabby-hands base. Originally flagged as an M8 merge-order dependency. **No longer applies:** Grabby Hands uses the right-click path and never touches the break pipeline, so it needs no mixin and no branch ordering (addendum §1.6).

---

## 3. Runtime / build facts

| Item | Value | Source |
|---|---|---|
| Minecraft | 1.21.1 | `gradle.properties:minecraft_version` |
| NeoForge | 21.1.72 | `gradle.properties:neo_version`, `build.gradle:38` |
| Java | 21 (toolchain); `JAVA_HOME` = Microsoft JDK 21.0.8.9 | `build.gradle:29-33` |
| GeckoLib | 4.6.6 | `gradle.properties:geckolib_version` |
| Mappings | Parchment 1.21 / 2024.07.28 | `gradle.properties` |
| Mod ID | `britannia_mod` | `gradle.properties:mod_id` |
| Package root | `com.seggellion.britannia_mod` | `gradle.properties:mod_group_id` |
| Mixin | 0.8.5 + MixinExtras 0.3.5 | `build.gradle:47-50` |
| Test framework | JUnit 5 (5.11.4) | `build.gradle:43-45` |
| Java sources | 1573 files (`src/main`), 215 (`src/test`) | |

### Commands

```bash
./gradlew test --no-configuration-cache --rerun-tasks     # JUnit unit/integration
./gradlew build --no-configuration-cache --rerun-tasks    # full build
./gradlew runClient                                       # client, GameTest ns enabled
./gradlew runServer                                       # dedicated server, --nogui
./gradlew runGameTestServer --no-configuration-cache --rerun-tasks
```

`runs { client/server }` in `build.gradle:70-92` both set `neoforge.enabledGameTestNamespaces = britannia_mod`.

### Environment traps confirmed

- **`gradle/wrapper/gradle-wrapper.jar` is a Git LFS pointer (133 bytes) on fresh worktree checkout.** LFS smudge does not run. Copied the real 43504-byte jar from the primary checkout. It now shows permanently as `M` in `git status` — **never stage it**.
- Default `java` on PATH is 1.8; the Gradle toolchain resolves 21 via `JAVA_HOME`. Fine as-is.
- `--no-configuration-cache --rerun-tasks` is required for trustworthy runs (configuration cache is on by default in `gradle.properties`).
- Concurrent Gradle daemons across worktrees lock `.gradle/repositories/.../client-extra.jar`. Run `./gradlew --stop` before a fresh run.

---

## 4. Architecture map

| Responsibility | File / class | Notes |
|---|---|---|
| Item registration | `registry/ItemRegistry.java` | `DeferredRegister<Item>`, ~1900 lines |
| Weapon items | `registry/WeaponRegistry.java` | separate `DeferredRegister<Item>` |
| Tools | `registry/ToolRegistry.java` | |
| Block registration | `registry/BlockRegistry.java` | ~2100 lines; also hosts some `BLOCK_ENTITY_TYPES` |
| Block entities | `registry/BlockEntityRegistry.java` | main BE registry |
| Creative tabs | `registry/CreativeTabRegistry.java` | manual `safeAccept(output, …)` per item; 7 tabs |
| Data components | `registry/DataComponentRegistry.java` | `WINE_DATA`, `SHRINE_INSTANCE_STATE`, `BANNER_INSTANCE_STATE`, `DYE_TUB_STATE`, `BANK_CHEQUE_DATA` |
| Sounds | `ModSounds.java` (real registry) + `registry/SoundRegistry.java` (**empty shell**) | `assets/britannia_mod/sounds.json` |
| Tags | `util/ModTags.java` + `data/britannia_mod/tags/{block,blocks,item,items}` | hand-written JSON; **no datagen at all** (`GatherDataEvent` has zero hits) |
| Mod entrypoint / handler wiring | `BritanniaMod.java:120-200` | `NeoForge.EVENT_BUS.register(new …Handler())` |
| Interaction events | `event/PlayerEventHandler.java`, `event/FlowerInteractionHandler.java`, `event/ToolInteractionHandler.java`, `event/CustomBlockBreakHandler.java` | |
| Adventure-mode handling | `event/CityGameModeHandler.java`, `structure/SurvivalZoneHandler.java`, `event/PlayerEventHandler.java:155-183` | see §12 |
| Adventure-mode client mixin | `mixin/client/ManagedVegetationAdventureModeMixin.java` *(wild-reagents branch only)* | see §12 |
| Custom tool classification | `item/TwoHandedAxeItem.java`, `item/QualityToolItem.java`, `util/AxeHarvestRules.java` | see §10 |
| Containers | `block/BritanniaChestBlock.java`, `BritanniaLockableChestBlock.java`, `ArmoireBlock.java`, `TrashBarrelBlock.java` + `block/entity/*BlockEntity.java` | see §9 |
| Persistent world state | `SavedData` subclasses: `blockrestore/BrokenBlockDataStorage`, `city/CityManager`, `player/PlayerDataManager`, `service/spawn/ServiceNpcSpawnClaimData`, … | |
| Per-instance block state | `block/nudgeable/NudgeableBlockEntity.java` | offset + flags, versionless NBT |
| Housing | `structure/HouseActionHandler`, `HousePlacementHandler`, `HousePrivacyHandler`, `block/entity/HouseLotBlockEntity`, `item/HouseKeyItem` | |
| Region / structure protection | `structure/StructureRegionManager.java`, `structure/StructureProtectionHandler.java`, `structure/StructureRecord.java` | AABB + owner UUID per chunk |
| City boundaries | `registry/CityRegistry.java`, `city/CityManager.java` | |
| Multi-block | `structure/multiblock/*`, `structure/lifecycle/ShrineLifecycleService.java`, `structure/placement/Shrine*` | see §14 — **the key precedent** |
| Seating | `entity/LivingSeatEntity.java`, `block/ChairBlock.java:91-129` | see §6 |
| Networking | `network/NetworkHandler.java` + `network/payload/*Payload.java` | `CustomPacketPayload` records |
| Logging | `com.mojang.logging.LogUtils` → SLF4J, per-class `LOGGER` | |
| Rails boundary | `server/http/RailsApiUrlResolver`, `service/banking/*`, `network/RailsUpdateServer`, `economy/ServerEconomyService` | see §15 |
| Unit tests | `src/test/java/...` (215 files, 1679 tests) | plain JUnit 5, no MC bootstrap |
| GameTests | `gametest/*GameTests.java` (29 files) | banking, worldstate, service-NPC, creative-tab integrity |

---

## 5. Furniture / practical-item candidate matrix

Every candidate below **already has a BlockEntity**. This is the single most important structural finding: Grabby Hands does not need a generic placed-item host block for the initial content set.

| Registry ID(s) | Block class | Block entity | Container | Sittable | Directional | Multi-block | Shape | Sound type | Loot table | Creative tab | Adapter | Enroll |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `wooden_chair`, `straw_chair`, `chair_trinsic`, `chair_vesper`, `stool`, `footstool`, `bench`, `wooden_throne`, `magincia_style_throne` | `ChairBlock` (h=0.1) | `ChairBlockEntity` : `NudgeableBlockEntity` | no | **yes** | FACING (horiz) | no | **full cube** (no `getShape`) | STONE ⚠ | **none** | decor | native furniture | **now** (pilot: `wooden_chair`) |
| `lord_british_throne` | `ChairBlock` (h=0.45) | `ChairBlockEntity` | no | yes | FACING | no | full cube | STONE ⚠ | none | decor | native furniture | later (admin/scenery risk) |
| `yew_table`, `small_table`, `counter` | `RotatableFurnitureBlock` | `RotatableFurnitureBlockEntity` : `NudgeableBlockEntity` | no | no | FACING | no | **full cube** | STONE ⚠ | none | items | native furniture | **now** |
| `wine_bottle_green/brown/blue/clear` | `WineBottleBlock` | `WineBottleBlockEntity` | no | no | FACING + `LABEL` (12 values) | no | `box(6,0,6,10,10,10)` + `canSurvive` | **GLASS** ✔ | none | world | stateful practical item | **now** (mandatory proof case) |
| `chest_wooden`, `chest_metal`, `chest_metal_bronze` | `BritanniaChestBlock` | `BritanniaChestBlockEntity` (27 slots) | **yes** | no | FACING | no | full cube | STONE ⚠ | none (a stray `loot_table/entities/chest_*.json` exists — wrong dir, dead) | world | native container | M7 |
| `britannia_lockable_chest` | `BritanniaLockableChestBlock` | same BE (+lockId, locked, difficulty, key seeding) | **yes** | no | FACING | no | `box(1,0,1,15,14,15)` | STONE ⚠ | none | world | native container | M7, **carefully** (§9) |
| `armoire_brown`, `armoire_red`, `chest_of_drawers_brown`, `chest_of_drawers_red` | `ArmoireBlock` | `ArmoireBlockEntity` (27 slots) | **yes** | no | FACING | **pseudo** (1 pos, 2×3 VoxelShape, `RenderShape.INVISIBLE`, placement `+1 Y`) | oversized, facing-dependent | **WOOD** ✔ | none | world | native container + placement quirk | M7 |
| `trash_barrel` | `TrashBarrelBlock` | `TrashBarrelBlockEntity` (ticking, void chest) | yes | no | no | no | full cube | — | none | — | **exclude** (voids items) | no |
| `wine_barrel`, `water_barrel`, `water_trough`, `juice_press` | dedicated blocks + BEs | yes | fluid/process | no | varies | no | — | — | none | — | later (process machines) | no |
| `double_bed` | `DoubleBedBlock` | `DoubleBedBlockEntity` | no | sleep | FACING | 2-block | — | — | none | — | multi-block | M9 |
| `table_setting` | `HorizontalFacingBlock` | **none** | no | no | FACING | no | full copy of STONE | STONE | none | decor | generic item-only host | later |
| `spittoon`, `candelabra`, `pitcher_*` | plain `Block` / `PitcherBlock` | some | no | no | varies | no | — | — | none | varies | generic / later | later |
| `large_structure_anchor` + `large_structure_part` | shrine/monolith multiblock | `LargeStructureAnchorBlockEntity` | no | no | FACING | **yes (4 or 18 cells)** | cell | — | custom | — | **already solved** — reuse as template | n/a |

⚠ **Sound-type defect:** `BlockBehaviour.Properties.of()` defaults to `SoundType.STONE`. Chairs, tables and chests never call `.sound(SoundType.WOOD)`, so wooden furniture currently emits stone break/place sounds. Armoires and wine bottles are correct. See §11.

**No block loot tables exist for any furniture, container or wine bottle.** `data/britannia_mod/loot_table/blocks/` contains exactly one file (`service_npc_spawn_block.json`). Everything else drops nothing when broken. This is *helpful* for Grabby Hands (no competing vanilla drop path) but means pickup must construct the ItemStack explicitly.

---

## 6. Sitting architecture findings

**Implementation:** `block/ChairBlock.java:91-129` (`useWithoutItem`) + `entity/LivingSeatEntity.java`.

| Question | Answer |
|---|---|
| Which classes use it? | `ChairBlock` only. 10 registered blocks share it (`BlockEntityRegistry.java:128-141`). |
| Trigger | `BlockState.useWithoutItem` — plain right-click, server side only (`!level.isClientSide()`). |
| Seat mechanism | A real entity: `EntityRegistry.SEAT_ENTITY` (`LivingSeatEntity extends Entity`), spawned via `MobSpawnType.TRIGGERED`, then `player.startRiding(seat)`. |
| Seat position | `pos + (0.5, sittingHeight + offsetY, 0.5) + nudge offset`. `sittingHeight` is a **constructor arg** (0.1 for chairs, 0.45 for Lord British's throne). The nudge offset comes from `ChairBlockEntity.getOffset()` (`NudgeableBlockEntity`). |
| Does facing affect seat position? | **No.** `FACING` drives the model/blockstate only. Seat position is always block-centre + heights. |
| Reuse / multiple players | Existing seats within `AABB(pos).inflate(1.0)` are **reused and repositioned**, so two adjacent chairs share one seat entity. `player.startRiding` only runs `if (!player.isPassenger())`. Seat entity `kill()`s itself in `removePassenger`. |
| Multi-block seat owner | N/A — no multi-block chairs today. |
| Interior-decorator interaction | `useWithoutItem` returns `FAIL` if the **offhand** holds `InteriorDecoratorToolItem`, so decorating does not seat you. |
| Existing tests | **None.** No unit test or GameTest covers `ChairBlock`, `LivingSeatEntity`, or seating. |

### What breaks if Grabby Hands uses raw `setBlock`

Nothing in the seat path itself — seat position derives from `BlockPos` + block instance + BE offset, all of which survive a plain `setBlock`. **But** a raw `setBlock` would lose:

- `ChairBlockEntity.offset` (the nudge), unless explicitly restored;
- `FACING`, unless explicitly restored;
- `setPlacedBy` side effects on other block types (`WineBottleBlock` transfers `WineData` there — §8).

**Recommendation:** never reimplement seating. Route placement through `BlockItem.place(BlockPlaceContext)` so `getStateForPlacement` + `setPlacedBy` both run, then restore BE payload.

### Public-usability verdict for chairs

`ChairBlock.useWithoutItem` has **no owner or placer check** of any kind. Any player can sit on any chair. Design requirement 2.3 is satisfied by *not adding anything*. The corresponding acceptance test is "assert no regression", not "add a permission".

---

## 7. Furniture stacking findings

**Stacking works today, and it works for a boring reason: the furniture blocks are full cubes.**

- `ChairBlock` — no `getShape` / `getCollisionShape` override, and it extends `HorizontalDirectionalBlock` → `BlockBehaviour` default `Shapes.block()`.
- `RotatableFurnitureBlock` — same.
- `BritanniaChestBlock` — same.
- `.noOcclusion()` affects *rendering/light only*, not collision or support.

So a chair on a table, a bottle on a counter, a chair on a chair — all resolve through **plain vanilla support checks against a full cube**. There is no custom placement helper, no sturdy-face logic, and no test coverage.

### Concrete rules

| Target | Can support something on top? | Why |
|---|---|---|
| chair / stool / bench / throne | **yes** | full cube, sturdy UP face |
| yew_table / small_table / counter | **yes** | full cube |
| chest_wooden / chest_metal / chest_metal_bronze | **yes** | full cube |
| `britannia_lockable_chest` | **no** | `getShape` = `box(1,0,1,15,14,15)`; top face at y=14, so `SupportType.CENTER` is not sturdy |
| wine bottle | **no** | `box(6,0,6,10,10,10)` |
| armoire / chest of drawers | complicated | oversized 2×3 shape at one position; `getStateForPlacement` shifts to `clickedPos.above()` and returns `null` if that is not replaceable |

`WineBottleBlock.canSurvive` (`WineBottleBlock.java:73-75`) is the only custom support rule found:

```java
return Block.canSupportCenter(level, pos.below(), Direction.UP);
```

Verified against decompiled `Block.canSupportCenter` → `blockstate.isFaceSturdy(level, pos, UP, SupportType.CENTER)`. So a bottle can stand on a chair, table, counter or full chest, but **not** on a lockable chest or another bottle.

### Requirement for Grabby Hands placement

Placement **must** go through `BlockItem.place(BlockPlaceContext)` (or `BlockPlaceContext` directly), not a hand-rolled `clickedPos.above()` routine. That single choice preserves stacking, `canSurvive`, the armoire's `+1 Y` quirk, `canBeReplaced`, and `getStateForPlacement` facing — all for free.

---

## 8. Wine bottle deep audit

**Headline: the wine bottle is already a fully-formed placeable stateful block. Grabby Hands has to *unlock the gate*, not *build the object*.**

### Identity / registration

| | |
|---|---|
| Items | `britannia_mod:wine_bottle_green` / `_brown` / `_blue` / `_clear` — `item/WineBottleBlockItem` **extends `BlockItem`**, `stacksTo(16)` (`ItemRegistry.java:678-692`) |
| Blocks | `britannia_mod:wine_bottle_green` / `_brown` / `_blue` / `_clear` — `block/WineBottleBlock` (`BlockRegistry.java:195-205`) |
| Block entity | `block/entity/WineBottleBlockEntity` |
| Variants | **4 separate registered item+block pairs** (glass colour), each with a 12-value `LABEL` blockstate |
| Unrelated class | `item/WineBottleItem extends Item` — a plain non-block wine item with the same helpers. Not referenced by any registration found; treat as legacy until proven otherwise. |

### Rich state — every field found in source

Component `britannia_mod:wine_data` → `component/WineData` (record; file lives at `components/WineData.java`, package `…component` — directory/package mismatch, compiles fine, cosmetic only).

| Property | Type | Item storage | Block storage |
|---|---|---|---|
| `wineryName` | String | `DataComponentRegistry.WINE_DATA` | BE NBT `WineryName` |
| `grapeType` | String | same | `GrapeType` |
| `year` | int | same | `Year` |
| `quality` | int (0–100) | same | `Quality` |
| `region` | String | same | `Region` |
| `labelColor` | String (12 values) | same | `LabelColor` **and** the `LABEL` blockstate |

`WineData.CODEC` (persistent) + `WineData.STREAM_CODEC` (network-synced) registered at `DataComponentRegistry.java:19-25`. No version field.

### Behaviour

| Question | Answer |
|---|---|
| Right-click use | `BlockItem` default → **places the block**. No drink/consume behaviour exists. |
| Placed-block right-click | No `useWithoutItem` / `useItemOn` override on `WineBottleBlock` → nothing happens. |
| Can another player use it? | Nothing restricts anything. No ACL exists to accidentally create. |
| Consumable / refillable | No. |
| Does state mutate through gameplay? | Not after creation. Set once at bottling (`network/ServerPayloadHandler.java:62`), then immutable. |
| Stackable | `stacksTo(16)`; different `WineData` values do **not** merge (data components participate in `ItemStack` equality). |
| Tooltip | `WineBottleBlockItem.appendHoverText` — Vintner / Region / Varietal / Vintage / Quality. |

### Economy / Rails integration (real, and it matters)

- `economy/ServerEconomyService.java:413-423` — on sale, serialises **all six** wine fields into the Rails item payload (`winery_name`, `grape_type`, `year`, `region`, `quality`, `label_color`), category `alcohol` / subcategory `wine`.
- `economy/ServerEconomyService.java:313` — NBT-matched buy requests key off `WINE_DATA`.
- `npc/AlcoholTraderRoleHandler.java:34` — alcohol traders filter on `WINE_DATA`.
- `client/gui/NpcCatalogScreen.java:152-195` — reconstructs `WineData` on templates and matches inventory stacks against it.
- `bank/item/BankItemCodec.java:28` — explicitly names `WINE_DATA` as state the bank envelope must round-trip.
- Existing coverage: `gametest/BankItemCodecGameTests.java:608` builds a non-default bottle — `("Britannia Vintners", "Merlot", year, 82, "Trinsic", "green")`. **Reuse this exact fixture for the M6 matrix.**

**Consequence:** losing wine state is not cosmetic. It changes sale price, trader eligibility, and bank round-trip identity.

### Existing world↔inventory transfer (already implemented, both directions)

- **Item → block:** `WineBottleBlock.setPlacedBy` (lines 79-106) reads `WINE_DATA`, maps `labelColor` → `LabelColor` enum, `setBlock` with the `LABEL` state, then `be.setWineData(...)`.
- **Block → item:** `WineBottleBlock.getCloneItemStack` (lines 110-126) builds `new ItemStack(this.asItem())` and copies all six fields from the BE.

`WineBottleBlockEntity` writes/reads all six via `saveAdditional`/`loadAdditional`, syncs with `getUpdateTag` + `ClientboundBlockEntityDataPacket`.

### Rendering

- Blockstate: `assets/…/blockstates/wine_bottle_{green,brown,blue,clear}.json` — 4 facings × 12 labels = 48 variants each, mapping to `models/block/wine_bottles/wine_bottle_<glass>_<label>.json`.
- Item model: `models/item/wine_bottle_<glass>.json`.
- BER: `client/renderer/WineBottleBlockEntityRenderer` — draws a floating 4-line label (Winery / Varietal / Year / Region) in the `britannia_mod:uo_classic` font, **only when the player is looking directly at that block**, suppressed for empty data.
- Model is already an upright bottle at the right scale for a world prop. **No new art or renderer is needed.**

### Persistence risk

| Risk | Verdict |
|---|---|
| Would recreating from registry ID lose data? | **Yes** — all six `WineData` fields. |
| Is `ItemStack` the safest canonical payload? | **Yes.** Wine state lives entirely in one data component. Copy the whole `ItemStack`, not field-by-field. |
| Is any state stored elsewhere? | No. Rails receives wine data on sale but is not authoritative for it. |
| Known asymmetries to fix | (a) `getCloneItemStack` copies **only** `WINE_DATA` — a custom name / other components on the source stack are lost across place→pickup. (b) `WineBottleBlockEntity.loadAdditional` defaults a missing `LabelColor` tag to `"red"` while `WineData.EMPTY` uses `"none"`. (c) No `dataVersion` on `WineData`, unlike `FlowerPersistentState` and `ShrineItemState`. |

### Recommendation

Adapter: **native block adapter, with the whole `ItemStack` as the opaque payload.**

- Pickup: capture the *entire* source `ItemStack` (extend `getCloneItemStack`'s logic, but preserve all components rather than only `WINE_DATA`), then remove the block.
- Placement: `BlockItem.place(...)` — `setPlacedBy` already restores everything correctly.
- Do **not** enumerate wine fields in the transport layer; a future 7th field must not silently break pickup.

---

## 9. Container deep audit

**This, not wine, is the genuinely dangerous case.**

### The stack

| Layer | Class |
|---|---|
| Base block | `block/BritanniaChestBlock extends BaseEntityBlock` |
| Locked variant | `block/BritanniaLockableChestBlock extends BritanniaChestBlock` |
| Armoire family | `block/ArmoireBlock extends BaseEntityBlock` (independent, same shape of implementation) |
| Block entities | `block/entity/BritanniaChestBlockEntity`, `block/entity/ArmoireBlockEntity` — both `implements MenuProvider, Container` |
| Storage | `NonNullList<ItemStack>` size 27, `ContainerHelper.saveAllItems` / `loadAllItems` |
| Menu | vanilla `ChestMenu.threeRows` — **no custom menu type** |
| Viewers | vanilla `ContainerOpenersCounter`, `startOpen`/`stopOpen`, `ModSounds.CHEST_OPEN/CLOSE` |
| Capability | none — raw `Container`, no `IItemHandler` |
| Custom name | **not supported** — `getDisplayName()` returns a hard-coded `Component.translatable("container.britannia_chest")` |

### Lock / security state (lockable chest only)

`BritanniaChestBlockEntity` fields: `lockId` (UUID), `chestKeySeeded` (boolean), `locked` (boolean), `lockDifficulty` (int 1-9). NBT keys `LockId` / `ChestKeySeeded` / `Locked` / `LockDifficulty`. Related: `item/ChestKeyItem`, `event/LockpickingEventHandler`.

Note the constructor (`BritanniaChestBlockEntity.java:68-76`) **mints a fresh random `lockId` and seeds a new key into slot 0** whenever a `BritanniaLockableChestBlock` BE is created and `state.getBlock() instanceof BritanniaLockableChestBlock`. On placement this is desirable; on a Grabby Hands *restore* it would issue a duplicate key and orphan the original. **Placement of a locked chest must suppress or overwrite this seeding.**

### Duplication hazards — the critical list

1. **`onRemove` spills contents.** `BritanniaChestBlock.onRemove` (lines 63-72) and `ArmoireBlock.onRemove` (lines 122-132) both call `Containers.dropContents(level, pos, be)` whenever the block changes to a different block. **Any** `level.removeBlock(pos, false)` or `setBlock(pos, AIR)` from Grabby Hands will dump the entire inventory on the floor. Combined with a serialised container ItemStack in the player's hand, that is a full inventory duplication.
   → Pickup must either clear the container *before* removing the block, or use a suppression flag consulted by `onRemove`. `farming/FlowerInteractionTransactionGate` (used by `FlowerInteractionHandler.onRightClickBlock`) is the project's existing precedent for exactly this kind of re-entrancy suppression.
2. **Open viewers.** `ContainerOpenersCounter` state is not persisted. Removing the block while a player has the menu open leaves a menu bound to a dead BE. `Container.stillValidBlockEntity` protects reads, but pickup should still force-close viewers first (`openersCounter` is private — needs an accessor or `player.closeContainer()` on each viewer).
3. **Key re-seeding on restore** (above).
4. **Anti-nesting.** There is **no** capacity, weight, or nesting rule anywhere in these classes. A serialised full chest could be placed inside another chest, recursively. `bank/item/BankItemWeight` exists for the bank but is not wired to containers. This needs an explicit policy decision in M7.
5. **No portable/shulker-like component exists today.** `BankItemCodec` (`bank/item/`) is the closest thing — it round-trips arbitrary `ItemStack` state including `WINE_DATA` and custom data — and is worth reading before designing the container payload.
6. **Armoire placement offset.** `ArmoireBlock.getStateForPlacement` returns `null` unless `clickedPos.above()` is replaceable, and places there. Pickup must resolve the *actual* occupied position, not the clicked one.

### Sittable / multi-block containers

None. No container is sittable. The armoire is a pseudo-multiblock (one position, oversized shape) — no secondary parts to orphan.

---

## 10. Axe classification findings

**The trap the kickoff warned about is real and present.**

| Question | Answer |
|---|---|
| Vanilla tag available | `net.minecraft.tags.ItemTags.AXES` — used at `block/GrapeVineBlock.java:85,219` and `block/TrellisBlock.java:45,115` |
| Custom axe class | `item/TwoHandedAxeItem extends net.minecraft.world.item.AxeItem` |
| Registered custom axes | exactly one: `britannia_mod:two_handed_axe` (`ItemRegistry.java:1586-1593`, tier `ModToolTiers.TWO_HANDED_AXE_TIER`) |
| Is it in `ItemTags.AXES`? | **NO.** There is no `data/minecraft/tags/` directory in the mod at all. `TwoHandedAxeItem` is an `AxeItem` subclass but carries no tag. |
| Other axe families | None as tools. `models/item/{battle_axe,war_axe,double_axe,executioners_axe,ornate_axe,…}.json` exist but grep finds **zero Java registrations** for them — art assets only. |
| Detection in practice | Everywhere by `instanceof TwoHandedAxeItem`: `CityGameModeHandler:45`, `BreakSpeedHandler:25,46`, `ToolInteractionHandler:28,58`, `StructureProtectionHandler:35`, `SurvivalZoneHandler:45`, `ClientEventHandler:329`. Plus `ItemRegistry.TWO_HANDED_AXE.get()` identity comparison at `PlayerEventHandler:166`. |
| Harvest eligibility | `util/AxeHarvestRules` — logs / leaves / fruit-tree blocks only; `TwoHandedAxeItem.isCorrectToolForDrops` delegates to it, and `getDestroySpeed` returns **0.0F** for anything else. |
| Durability | Standard `AxeItem`/`DiggerItem` behaviour. `ChestHandler.createSeggellionsAxe` sets `setDamageValue(max/2)`. No custom durability logic. |
| Existing axe-target path | `event/WoodChopEventHandler.handleAxeHarvest(serverLevel, pos, state, player)`, invoked from `PlayerEventHandler.onLeftClickBlock:176`. **This is the pattern M8 should mirror.** |
| Chop sounds centralised? | No. |
| `TwoHandedAxeItem.useOn` | returns `InteractionResult.PASS` — right-click with the axe is deliberately inert, so it is free for Grabby Hands to claim if needed. |

### Required M1 deliverable

One shared classifier, e.g. `GrabbyAxes.isAxe(ItemStack)` returning
`stack.is(ItemTags.AXES) || stack.getItem() instanceof AxeItem`
(the `instanceof AxeItem` arm catches `TwoHandedAxeItem` and any future subclass without needing a tag). Optionally *also* add `britannia_mod:two_handed_axe` to a `data/minecraft/tags/items/axes.json` so the four existing `ItemTags.AXES` call sites in `GrapeVineBlock`/`TrellisBlock` start recognising it too — but that is a behaviour change outside Grabby Hands scope and should be raised with the owner separately.

**Failure mode to test explicitly:** a check written as `stack.is(ItemTags.AXES)` alone would support vanilla axes and silently exclude UltimaCraft's only real axe.

---

## 11. Sound inventory and recommendations

### Infrastructure

- **`ModSounds.java`** is the real registry — `DeferredRegister<SoundEvent>` on `britannia_mod`, with a `registerEntitySounds(name)` helper generating ambient/angry/attack/hurt/death groups, plus one-off events including `CHEST_OPEN` / `CHEST_CLOSE`.
- **`registry/SoundRegistry.java`** declares a second `DeferredRegister<SoundEvent>` with **zero entries** — an empty shell. Do not add to it; use `ModSounds`.
- `assets/britannia_mod/sounds.json` maps names to `.ogg` files.

### Role recommendations — no new assets required

| Role | Recommendation | Rationale |
|---|---|---|
| **Grab / lift** | `SoundEvents.ITEM_FRAME_REMOVE_ITEM` (or `SoundEvents.ARMOR_EQUIP_LEATHER` for a softer variant) | short, physical, clearly not a UI click; already the vanilla "took a thing off the world" cue |
| **Stow / inventory** | `SoundEvents.ITEM_PICKUP` at `SoundSource.PLAYERS`, sent only to the acting player | exactly the vanilla "entered inventory" cue; distinct from grab; players already read it as success |
| **Placement** | `state.getSoundType().getPlaceSound()` via the native `BlockItem.place` path | already correct and free — no explicit call needed |
| **Wood/furniture destroy** | `state.getSoundType().getBreakSound()` | correct **once the sound-type defect below is fixed**; armoires already give WOOD |
| **Glass/bottle destroy** | `state.getSoundType().getBreakSound()` | already correct — `bottleProps()` sets `SoundType.GLASS` (`BlockRegistry.java:191`) |
| **Metal chest destroy** | `state.getSoundType().getBreakSound()` | needs `.sound(SoundType.METAL)` added to `chest_metal*` — same defect |

### Sound-type defect (in scope for M11, flag now)

`BlockBehaviour.Properties.of()` defaults to `SoundType.STONE`. These never set `.sound(...)`:

- all 10 `ChairBlock` registrations
- `yew_table`, `small_table`, `counter`
- `chest_wooden`, `chest_metal`, `chest_metal_bronze`, `britannia_lockable_chest`

Result: wooden furniture currently breaks and places with **stone** sounds. Deriving destruction audio from `getSoundType()` is the right architecture; the fix is a one-line `.sound(SoundType.WOOD)` per registration, which also improves existing gameplay. The alternative (a Grabby-Hands-only material tag) duplicates data the block already claims to own and is not recommended.

**No new `.ogg` assets and no new `SoundEvent` registrations are needed for Grabby Hands.**

---

## 12. Adventure-mode restriction findings

Verified directly against decompiled 1.21.1 sources in `build/neoForm/neoFormJoined1.21.1-20240808.144430/steps/decompile/output.jar`.

### The placement gate — exactly one line

`ItemStack.useOn(UseOnContext)`:

```java
if (player != null && !player.getAbilities().mayBuild
        && !this.canPlaceOnBlockInAdventureMode(new BlockInWorld(context.getLevel(), blockpos, false))) {
    return InteractionResult.PASS;
}
```

Same predicate in `Player.mayUseItemAt`. `canPlaceOnBlockInAdventureMode` consults the vanilla `minecraft:can_place_on` data component (`AdventureModePredicate`).

### What is **not** gated — the key enabling discovery

`ServerPlayerGameMode.useItemOn` runs, in order:

1. `blockstate.useItemOn(...)`
2. `blockstate.useWithoutItem(...)` (main hand only, if step 1 returned `PASS_TO_DEFAULT_BLOCK_INTERACTION`)
3. **then** `stack.useOn(useoncontext)` — the only step carrying the Adventure gate

So **block-side right-click callbacks already execute normally in Adventure mode.** That is why chairs are already sittable and chests already openable for Adventure players today. Client-side, `MultiPlayerGameMode.useItemOn` sends `ServerboundUseItemOnPacket` unconditionally — no client gate on use.

Also verified: `flag1 = player.isSecondaryUseActive() && (!mainHand.isEmpty() || !offhand.isEmpty())`. With **both hands empty**, `flag1` is false, so **sneak + right-click with empty hands still reaches `blockstate.useWithoutItem` on the server.**

→ **The proposed pickup gesture (sneak + right-click, empty main hand) needs no mixin, no capability change, and no bypass at all.** It is a server-side `useWithoutItem` handler, or a `PlayerInteractEvent.RightClickBlock` handler at `EventPriority.HIGHEST` following the `FlowerInteractionHandler.onRightClickBlock` precedent. Caveat: require the **offhand empty too**, otherwise sneak short-circuits step 2.

### The break gate — genuinely restrictive

`MultiPlayerGameMode.startDestroyBlock` returns `false` immediately if `player.blockActionRestricted(level, pos, gameType)`, so in Adventure **no packet is sent at all**. `Player.blockActionRestricted` returns true unless `mayBuild` or the held stack passes `canBreakBlockInAdventureMode` (the vanilla `minecraft:can_break` component).

### How the project works around it today — three different mechanisms

| Mechanism | Location | Assessment |
|---|---|---|
| **Game-mode flipping** | `event/CityGameModeHandler.java:19-61` — every `PlayerTickEvent.Post`, holding a `QualityToolItem` or `TwoHandedAxeItem` forces SURVIVAL; releasing it forces ADVENTURE. Duplicated in `structure/SurvivalZoneHandler.java:53,78`. | **Directly contradicts design invariant #1.** Grabby Hands must not extend it. Also means an Adventure player holding an axe is *actually in Survival* — with all that implies for other protections. Needs an owner decision. |
| **Client mixin redirect** | `mixin/client/ManagedVegetationAdventureModeMixin` — `@Redirect` on the `blockActionRestricted` call inside `MultiPlayerGameMode.startDestroyBlock`, returning `false` for specific block/tool pairs. Paired with the `AdventureHarvestableBlock` marker interface ("client-visible hint only; the server always revalidates"). | **The correct pattern.** Narrow, per-block-type, server revalidates. *Only exists on `codex/wild-reagents`.* |
| **Server-side event cancel** | `event/PlayerEventHandler.onLeftClickBlock:155-183` — in Adventure, with `TWO_HANDED_AXE`, calls `WoodChopEventHandler.handleAxeHarvest` and cancels. ⚠ **Correction (addendum §1.5): this branch is unreachable dead code on this base.** `LeftClickBlock` is fired *inside* `startDestroyBlock` after the `blockActionRestricted` gate, so it never fires in Adventure; and outside cities `CityGameModeHandler` has already forced SURVIVAL, so the `== ADVENTURE` guard fails. Live tree harvesting runs through the Survival `BreakEvent` path in `WoodChopEventHandler.onBlockBreak`. | evidence only |

### Smallest server-authoritative hooks for Grabby Hands — **[SUPERSEDED]**

The table that stood here proposed a client-mixin + `LeftClickBlock` route for axe destruction. **Superseded by addendum §1.6–§1.7:** `PlayerInteractEvent.RightClickBlock` fires as the first statement of `ServerPlayerGameMode.useItemOn`, before the spectator branch, before block use, and before the `mayBuild` gate. All three Grabby operations — place, pickup, **and axe destruction** — use that one hook at `EventPriority.HIGHEST`. No mixin, no `LeftClickBlock`, no break-pipeline involvement, no game-mode dependency.

**No global `mayBuild`, no global uncancel, no game-mode switching, and no client mixin is required anywhere.**

---

## 13. Static-vs-movable instance strategy

### How static decoration gets into the world

| Path | Evidence |
|---|---|
| Structure NBT templates | `data/britannia_mod/structures/*.nbt` (castle, houses, cottage), placed via `structure/StructurePlacer` / `HousePlacementHandler` |
| Spawner / bootstrap systems | `event/WorldBootstrapHandler`, `spawner/*`, `CitySpawner`, `BritainCemetarySpawner` |
| Admin commands | `commands/*` (`CityCommand`, etc.) |
| Creative placement by staff | `CreativeTabRegistry` exposes all furniture; `FlowerProtectionService.isAdministrator` = creative **or** permission level ≥ 2 |
| Structure regions | `structure/StructureRegionManager` — AABB + owner UUID per chunk, consulted by `StructureProtectionHandler` |

Block type alone is worthless as a signal: `wooden_chair` will legitimately exist both as Britannia scenery and as player furniture.

### The project already has the right pattern — reuse it

`farming/FlowerPersistentState` is a schema-versioned, NBT-round-tripped record carrying exactly the distinction Grabby Hands needs:

```java
int dataVersion,                    // CURRENT_DATA_VERSION = 1
FlowerPlantingOrigin plantingOrigin, // ADMIN | PLAYER
boolean protectedFlower,
Optional<UUID> planterUuid,
FlowerRegionProvenance regionProvenance,
…
```

with compact-constructor invariants (`ADMIN` ⇒ protected; `PLAYER` ⇒ not admin-protected), a `toClientTag()` that **omits the planter UUID from client sync**, and legacy-tolerant `fromTag`. `FlowerProtectionService.mayMutate(state, actor, reason)` is the matching policy service, keyed on an enum of *mutation reasons* (`NORMAL_BREAK`, `EXPLOSION`, `FLUID`, `PISTON`, system-authorized) — not on identity.

### Recommendation

Introduce `GrabbyInstanceState` modelled on `FlowerPersistentState`:

```
int schemaVersion
GrabbyOrigin origin        // WORLD | PLAYER      (default WORLD)
boolean immovable
Optional<UUID> placerUuid  // audit + movement policy ONLY
```

- Stored on the **existing** BlockEntity of each enrolled block (`ChairBlockEntity`, `RotatableFurnitureBlockEntity`, `WineBottleBlockEntity`, `BritanniaChestBlockEntity`, `ArmoireBlockEntity`) — no new host block, no new SavedData, no per-tick scan.
- **Absence of the tag means `WORLD` / immovable.** Every pre-existing block in the world — all Britannia scenery — is therefore protected by default with zero migration.
- Only `setPlacedBy` through the Grabby Hands placement transaction writes `origin = PLAYER`.
- Mirror `toClientTag()`: never sync `placerUuid` to clients.
- Policy service `GrabbyPolicy.mayMove(state, actor, reason)` mirrors `FlowerProtectionService`, delegating region/house questions to `StructureRegionManager` / `HouseLotBlockEntity` rather than duplicating ownership data.
- **`placerUuid` must never be read by any `useWithoutItem` / `useItemOn` path.** Add a test that asserts this (a non-placer can sit / open / interact).

---

## 14. Multi-block findings

### Real multi-block systems present

| System | Root | Parts | State |
|---|---|---|---|
| Shrines / monoliths | `LargeStructureAnchorBlock` + `LargeStructureAnchorBlockEntity` | `LargeStructurePartBlock` (encodes `FACING` + `LocalOffset`, derives anchor position) | `PlacedStructureState` (schema-versioned Codec: family, variant, facing, ordered 4- or 18-cell footprint) |
| Double bed | `DoubleBedBlock` + `DoubleBedBlockEntity` | 2 positions | — |
| Armoire family | **pseudo** — one position, oversized VoxelShape, `+1 Y` placement | none | — |

**No furniture in the current enrollment candidate set is a true multi-block.** M9 is therefore lower risk than assumed, and the double bed is the only near-term candidate.

### `ShrineLifecycleService` is a working Grabby Hands transaction engine

`structure/lifecycle/ShrineLifecycleService.java` already implements, correctly, nearly everything M2/M9 asks for:

- `resolve(world, sourcePos, sourceState)` → canonical anchor from **any** part, with an explicit `ResolutionStatus` enum (`VALID`, `ANCHOR_CHUNK_UNLOADED`, `MISSING_ANCHOR`, `INVALID_MEMBERSHIP`) and full membership validation (facing match, footprint containment, expected-cell check, round-trip position check).
- **Re-entrancy / race guard:** `synchronized` `IN_PROGRESS` and `PLACING` sets keyed by `(levelIdentity, anchorPos)`. A second concurrent attempt returns `RemovalResult.unclaimed`. **This is exactly the "two players race, one winner" primitive M2 and M12 require.**
- `RemovalResult(claimed, removedCells, drops, cause)` — drops counted, so "exactly once" is assertable.
- `ShrineRemovalCause.dropsConfiguredItem()` — cause-driven drop policy, the natural place for "axe destruction consumes rather than returns".
- **Never force-loads a chunk**; explicitly returns `ANCHOR_CHUNK_UNLOADED` instead.
- `interface WorldAccess` — a testable mutation boundary, which is how this gets unit-tested without a live level.
- `pick(...)` — a clone-stack path usable from both sides.
- Portable payload: `ShrineItemState` (schema-versioned Codec + StreamCodec) on a data component, with `ShrineItemTransfer` / `ShrineItemStateAccess` helpers.

**Recommendation:** Grabby Hands' transaction service should be modelled on this class — ideally by generalising it rather than writing a parallel one. That decision belongs to M1 and should be an explicit owner call, because generalising `ShrineLifecycleService` touches shrine code.

---

## 15. Rails authority findings

**No relevant state is Rails-authoritative. Do not create endpoints.**

| Domain | Rails involvement |
|---|---|
| Banking / cheques | Yes — `service/banking/*Client.java`, signed via `server/auth/RequestSignature` + `server/http/RailsApiUrlResolver` |
| Economy / commodities | Yes — `economy/ServerEconomyService`, `network/SendTransactionToAPI`, `network/RailsUpdateServer` |
| Service NPCs / world state | Yes — `service/*`, `worldstate/*` |
| Cities | Yes — `city/CityDataSync`, bootstrap caches |
| **Wine bottle state** | **No.** Wine data is sent *to* Rails as sale metadata (`ServerEconomyService.java:413-423`) but is never read back or owned there. |
| **Furniture / containers / block placement** | **No.** Zero Rails references. |
| **Houses** | Local — `StructureRegionManager` + `HouseLotBlockEntity`; deeds go through `network/DeedHttpServer` but placement/ownership is local. |

**Verdict:** placed practical objects use local Minecraft persistence (BlockEntity NBT). Design §17 preference confirmed by evidence.

---

## 16. Recommended adapter architecture (corrected to actual code)

The design's five adapters collapse to **three**, because every candidate already has a BlockEntity and a native placement path.

### Shared core (M1–M2) — **[core row SUPERSEDED, see addendum Part 2]**

| Component | Build on |
|---|---|
| `GrabbyEligibility` | new block tag `britannia_mod:grabby_movable` (+ `grabby_axe_destroyable`) under `data/britannia_mod/tags/block/` — hand-written JSON, matching `ModTags` conventions. **No datagen exists; do not introduce it.** |
| `GrabbyInstanceState` | record modelled on `FlowerPersistentState`; stored in each block's existing BE (§13). **Moved into M1** per owner direction — see addendum Part 3. |
| `GrabbyPolicy` | modelled on `FlowerProtectionService`; delegates to `StructureRegionManager`, `HouseLotBlockEntity`, `CityRegistry` |
| ~~`GrabbyTransaction` modelled on `ShrineLifecycleService`~~ | **SUPERSEDED.** Grabby Hands owns `GrabbyPickupTransaction` / `GrabbyPlacementTransaction` / `GrabbyDestructionTransaction` and a `GrabbyWorld` seam, named and written for its own domain. `ShrineLifecycleService` is **reference only** — not generalised, not modified, not copied. Addendum Part 2. |
| `GrabbyAxes` | `stack.is(ItemTags.AXES) \|\| stack.getItem() instanceof AxeItem` (§10); scoped to Grabby's handler only — addendum §1.9 |
| `GrabbySoundRoles` | grab = `ITEM_FRAME_REMOVE_ITEM`, stow = `ITEM_PICKUP` (to the acting player only), destroy = `state.getSoundType().getBreakSound()` (§11) |
| Re-entrancy suppression | `FlowerInteractionTransactionGate` precedent, needed for container `onRemove` (§9) |
| `GrabbyInteractionHandler` | **one** `PlayerInteractEvent.RightClickBlock` subscriber @ `HIGHEST` for place / pickup / destroy — addendum §1.7 |

### Adapter 1 — native stateless furniture (chairs, tables, thrones, stools, benches, counters)

Portable payload: `new ItemStack(block.asItem())` + `ChairBlockEntity`/`RotatableFurnitureBlockEntity` nudge offset. Placement via `BlockItem.place(BlockPlaceContext)` so `getStateForPlacement` restores FACING and vanilla support/stacking is preserved. Seating untouched.

### Adapter 2 — native stateful practical item (wine bottles)

Portable payload: **the complete source `ItemStack`, opaque.** Pickup extends `getCloneItemStack` to preserve all components, not just `WINE_DATA`. Placement via `BlockItem.place(...)` — `setPlacedBy` already restores `LABEL` + BE data. No new renderer, no new model. This adapter generalises to any future data-component-carrying block item.

### Adapter 3 — native container (chests, lockable chest, armoires)

Portable payload: `ItemStack` + serialized BE NBT (contents via `ContainerHelper`, plus lock fields). Pickup must: force-close viewers → snapshot → **clear the container** → remove block (so `onRemove`'s `Containers.dropContents` is a no-op) → insert. Placement must suppress the lockable-chest key-seeding constructor path. Explicit anti-nesting policy required.

### Not needed for the initial content set

- **Generic placed-item host block** — deferred. Only `table_setting`, `spittoon` and similar BE-less decorations would need it. M5 should be re-scoped to "deferred unless enrollment expands", per its own "only if M0 proves one is needed" clause.
- **Multi-block adapter** — no candidate is a true multi-block (§14). M9 reduces to the double bed, or reuse of `ShrineLifecycleService`.

---

## 17. Primary duplication / state-loss risks

| # | Risk | Where | Severity |
|---|---|---|---|
| 1 | Container `onRemove` → `Containers.dropContents` spills the full inventory on **any** block removal, duplicating a serialized container in hand | `BritanniaChestBlock.java:63-72`, `ArmoireBlock.java:122-132` | **critical** |
| 2 | Lockable chest constructor mints a fresh `lockId` + seeds a new `ChestKeyItem` into slot 0 on BE creation — restore would duplicate keys and orphan the original | `BritanniaChestBlockEntity.java:68-76,118-131` | **high** |
| 3 | Hand-rolled `setBlock` placement would bypass `setPlacedBy` and lose `WineData`, `LABEL`, nudge offset, and the armoire `+1 Y` offset | `WineBottleBlock.setPlacedBy`, `ArmoireBlock.getStateForPlacement` | **high** |
| 4 | `getCloneItemStack` copies only `WINE_DATA` — other item components (custom name, etc.) are lost across a place→pickup cycle | `WineBottleBlock.java:110-126` | medium |
| 5 | ~~`CityGameModeHandler` game-mode flipping defeats Adventure-based assumptions~~ → **RESOLVED.** Grabby Hands is game-mode independent: the `RightClickBlock` hook precedes every Adventure gate, so no assumption is made. `CityGameModeHandler` and `SurvivalZoneHandler` are untouched. Addendum §1.2, §1.7 | — | resolved |
| 6 | Axe check written as `stack.is(ItemTags.AXES)` silently excludes `two_handed_axe`, the mod's only real axe | §10, addendum §1.9 | **high** |
| 7 | Open container viewers when the block is removed — `ContainerOpenersCounter` is not persisted and `openersCounter` is private | `BritanniaChestBlockEntity.java:44-62` | medium |
| 8 | No anti-nesting rule anywhere — a full chest could be stored inside a chest, recursively | §9 | medium |
| 9 | No `dataVersion` on `WineData`, unlike `FlowerPersistentState` / `ShrineItemState`. Any future field addition risks silent loss | `component/WineData.java` | medium |
| 10 | `WineBottleBlockEntity.loadAdditional` defaults missing `LabelColor` to `"red"`; `WineData.EMPTY` uses `"none"` | `WineBottleBlockEntity.java:53` | low |
| 11 | Wooden furniture declares `SoundType.STONE`; deriving destroy audio from `getSoundType()` gives the wrong material until fixed | §11 | low |
| 12 | Two concurrent pickups, or pickup racing axe destruction, with no guard | Grabby's own `(ServerLevel, BlockPos)`-keyed guard in its own transaction services (addendum Part 2) | **high if not designed in** |
| 13 | ~~Merge-order dependency on `ManagedVegetationAdventureModeMixin` for Adventure breaking~~ → **RESOLVED.** No mixin needed; Grabby Hands never uses the break pipeline. Addendum §1.6 | — | resolved |
| 14 | Right-clicking a chair/container with an axe currently seats the player / opens the menu. Grabby's handler must cancel at `HIGHEST` before `useItemOn`/`useWithoutItem`, and must ignore interactions where either hand holds `InteriorDecoratorToolItem` | addendum §1.8 | medium |

---

## 18. Baseline validation results

Executed on `grabby-hands` @ `50061f07`:

```bash
./gradlew --stop
./gradlew test --no-configuration-cache --rerun-tasks --console=plain
```

| Result | |
|---|---|
| Exit code | **0 — BUILD SUCCESSFUL in 3m 44s** |
| Test classes | 201 |
| Tests | **1679** |
| Failures | **0** |
| Errors | **0** |
| Skipped | 17 |
| Compilation | clean; 36 warnings, all `[removal] makeMockServerPlayerInLevel()` deprecations in `gametest/*` |
| Pre-existing failures | none |
| Working-tree side effects | only `gradle/wrapper/gradle-wrapper.jar` (LFS pointer replaced with the real jar — required to run Gradle at all; never stage) |

GameTests (`runGameTestServer`) were **not** run — they boot a full server and the existing suite covers banking / world-state / service-NPC, none of which Grabby Hands touches yet. Recommend running once at M2 when the first Grabby Hands GameTest exists.

---

## 19. Proposed Milestone 1 scope — **[SUPERSEDED]**

Replaced by `ULTIMACRAFT_GRABBY_HANDS_MILESTONE_0_ADDENDUM_AUTHORIZATION.md` Part 4. The material changes: provenance storage moves into M1 and is wired into all five existing BlockEntities; the transaction service is Grabby-owned rather than modelled on `ShrineLifecycleService`; and a set of authorization-regression tests is added asserting that no Grabby class references `setGameMode`, `mayBuild`, `BreakEvent`, `PlayerEvent.BreakSpeed`, or `blockActionRestricted`.

---

## 20. Open blockers — **[RESOLVED]**

Both original blockers were decided by the owner on 2026-08-12.

**OQ-1 — `CityGameModeHandler` game-mode flipping. RESOLVED.** Not to be treated as unrelated debt or designed around. Its gameplay purpose stands: axes must not confer general block-breaking authority, especially in cities, and tree/log/leaf harvesting must keep working. Full authorization path traced in addendum Part 1. Outcome: Grabby Hands adds one `PlayerInteractEvent.RightClickBlock` subscriber and never touches the break pipeline, so it cannot grant break authority; zero existing authorization classes are modified.

**OQ-2 — `ShrineLifecycleService`. RESOLVED.** Not to be generalised or modified; no domain relationship to portable world objects. Study as a reference implementation only, and do not blindly duplicate it. Grabby Hands owns appropriately named lifecycle/transaction services (addendum Part 2). A generic low-level primitive is extracted only if both systems later prove they need the identical thing.

**Provenance. DIRECTED INTO CORE.** Moved from M8 to M1 (addendum Part 3). Every Grabby-placed object is positively marked player-placed with placer UUID; absence of the mark means protected; no backwards migration required.

Remaining non-blocking questions — OQ-3 (container nesting) and OQ-5 (destroying a player-placed container inside a city) — are carried in addendum Part 5. OQ-4 is withdrawn.

---

## Appendix A — files read for this report

Build: `build.gradle`, `gradle.properties`, `settings.gradle`
Wine: `block/WineBottleBlock`, `block/entity/WineBottleBlockEntity`, `item/WineBottleBlockItem`, `item/WineBottleItem`, `components/WineData`, `util/LabelColor`, `client/renderer/WineBottleBlockEntityRenderer`, `registry/DataComponentRegistry`, `assets/…/blockstates/wine_bottle_*.json`
Furniture: `block/ChairBlock`, `block/RotatableFurnitureBlock`, `block/nudgeable/{INudgeable,NudgeableBlockEntity}`, `block/nudgeable/block_entities/ChairBlockEntity`, `entity/LivingSeatEntity`, `item/InteriorDecoratorToolItem`
Containers: `block/BritanniaChestBlock`, `block/BritanniaLockableChestBlock`, `block/entity/BritanniaChestBlockEntity`, `block/ArmoireBlock`, `block/entity/ArmoireBlockEntity`, `block/TrashBarrelBlock`
Axes: `item/TwoHandedAxeItem`, `util/AxeHarvestRules`, `event/{BreakSpeedHandler,ToolInteractionHandler,WoodChopEventHandler}`
Adventure: `event/{PlayerEventHandler,CityGameModeHandler,FlowerInteractionHandler,CustomBlockBreakHandler,ChestHandler}`, `structure/{SurvivalZoneHandler,StructureProtectionHandler,HangingItemBlock,HousePrivacyHandler}`, `mixin/client/ManagedVegetationAdventureModeMixin` *(wild-reagents)*, `wildresource/WildResourceInteractionHandler` *(wild-reagents)*, `block/AdventureHarvestableBlock` *(wild-reagents)*
Precedents: `farming/{FlowerPersistentState,FlowerProtectionService,FlowerRegionProvenance}`, `structure/lifecycle/ShrineLifecycleService`, `structure/multiblock/{PlacedStructureState,LargeStructureAnchorBlock}`, `structure/item/{ShrineItemState,ConfiguredStructureItem}`
Registries: `registry/{BlockRegistry,ItemRegistry,BlockEntityRegistry,CreativeTabRegistry,SoundRegistry,WeaponRegistry}`, `ModSounds`, `util/ModTags`, `BritanniaMod`
Vanilla (decompiled, `neoFormJoined1.21.1-20240808.144430`): `ItemStack`, `Player`, `ServerPlayerGameMode`, `MultiPlayerGameMode`, `Block`, `Minecraft`
