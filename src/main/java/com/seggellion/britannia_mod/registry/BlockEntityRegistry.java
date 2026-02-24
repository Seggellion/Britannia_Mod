package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.block.entity.AdaptiveRoofBlockEntity;
import com.seggellion.britannia_mod.block.entity.CarpetTeleporterBlockEntity;
import com.seggellion.britannia_mod.block.entity.DoubleBedBlockEntity;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.seggellion.britannia_mod.block.entity.StoreSignBlockEntity;
import com.seggellion.britannia_mod.registry.FishRegistry;
import com.seggellion.britannia_mod.block.entity.FishBlockEntity;
import com.seggellion.britannia_mod.block.entity.TraderSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.MonsterSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.ArchitectSpawnBlockEntity;
import com.seggellion.britannia_mod.block.nudgeable.block_entities.CandelabraBlockEntity;
import com.seggellion.britannia_mod.block.nudgeable.block_entities.ChairBlockEntity;
import com.seggellion.britannia_mod.block.nudgeable.block_entities.RotatableFurnitureBlockEntity;
import com.seggellion.britannia_mod.structure.HouseSignBlockEntity;
import com.seggellion.britannia_mod.block.entity.LockableDoorBlockEntity;
import com.seggellion.britannia_mod.block.entity.ThreeHeightLightBlockEntity;
import com.seggellion.britannia_mod.block.entity.GrapeVineBlockEntity;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.block.entity.WineBarrelBlockEntity;
import com.seggellion.britannia_mod.block.entity.JuicePressBlockEntity;
import com.seggellion.britannia_mod.block.entity.WineBottleBlockEntity;


import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BlockEntityRegistry {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "britannia_mod");

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HouseLotBlockEntity>> HOUSE_LOT =
        BLOCK_ENTITIES.register("house_lot", () ->
            BlockEntityType.Builder.of(HouseLotBlockEntity::new, BlockRegistry.HOUSE_LOT_BLOCK.get()).build(null));

    /* ---------- STORE-SIGN  ✅ NEW IMPLEMENTATION ---------- */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StoreSignBlockEntity>> STORE_SIGN =
            BLOCK_ENTITIES.register("store_sign", () -> BlockEntityType.Builder.of(
                    StoreSignBlockEntity::new,
                    /*  pull every sign block AFTER SignBlockRegistry has run  */
                    SignBlockRegistry.STORE_SIGN_BLOCKS.values().stream()
                            .map(DeferredHolder::get)
                            .toArray(Block[]::new)
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FishBlockEntity>> FISH_BLOCK =
        BLOCK_ENTITIES.register("fish_block", () ->
            BlockEntityType.Builder.of(
                FishBlockEntity::new,
                com.seggellion.britannia_mod.registry.FishRegistry.FISH_BLOCKS
                    .values().stream().map(DeferredHolder::get).toArray(Block[]::new)
            ).build(null)
        );
public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdaptiveRoofBlockEntity>> ADAPTIVE_ROOF =
    BLOCK_ENTITIES.register("adaptive_roof", () ->
        BlockEntityType.Builder.of(
            AdaptiveRoofBlockEntity::new,
            BlockRegistry.TILE_ROOF_FLAT.get(),
            BlockRegistry.CEDAR_ROOF_FLAT.get(),
            BlockRegistry.SLATE_ROOF_FLAT.get(),
            BlockRegistry.SLATE_ROOF_1_FLAT.get(),
            BlockRegistry.SLATE_ROOF_2_FLAT.get(),
            BlockRegistry.THATCH_ROOF_FLAT.get()
        ).build(null)
    );

public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CarpetTeleporterBlockEntity>> CARPET_TELEPORTER_BLOCK_ENTITY_TYPE =
    BLOCK_ENTITIES.register("carpet_teleporter_block_entity", () ->
        BlockEntityType.Builder.of(CarpetTeleporterBlockEntity::new,
                                   BlockRegistry.CARPET_TELEPORTER_BLOCK.get())
                               .build(null));

public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ThreeHeightLightBlockEntity>> THREE_HEIGHT_LIGHT_BLOCK_ENTITY_TYPE =
    BLOCK_ENTITIES.register("three_height_light_block_entity", () ->
        BlockEntityType.Builder.of(
                ThreeHeightLightBlockEntity::new,
                BlockRegistry.WOODEN_LAMP_POST.get()
        ).build(null));


    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HouseSignBlockEntity>> HOUSE_SIGN =
        BLOCK_ENTITIES.register("house_sign", () ->
            BlockEntityType.Builder.of(HouseSignBlockEntity::new, BlockRegistry.HOUSE_SIGN_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CandelabraBlockEntity>> CANDELABRA =
            BLOCK_ENTITIES.register("candelabra", () ->
                    BlockEntityType.Builder.of(CandelabraBlockEntity::new,
                                BlockRegistry.WALL_SCONCE.get(),
                        BlockRegistry.CANDLE.get(),
                            BlockRegistry.CANDELABRA_SMALL.get(),
                            BlockRegistry.CANDELABRA_TALL.get(),
                            BlockRegistry.LAMP_POST_REGULAR.get(),
                            BlockRegistry.LAMP_POST_FANCY.get(),
                            BlockRegistry.TORCH_WALL.get(),
                            BlockRegistry.TORCH_STANDING.get(),
                            BlockRegistry.BRAZIER_SMALL.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RotatableFurnitureBlockEntity>> ROTATABLE_FURNITURE =
            BLOCK_ENTITIES.register("rotatable_furniture", () ->
                    BlockEntityType.Builder.of(RotatableFurnitureBlockEntity::new,
                            BlockRegistry.YEW_TABLE.get(),
                            BlockRegistry.SMALL_TABLE.get(),
                            BlockRegistry.COUNTER.get()
                    ).build(null));



    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChairBlockEntity>> CHAIR =
            BLOCK_ENTITIES.register("chair", () ->
                    BlockEntityType.Builder.of(ChairBlockEntity::new,
                            BlockRegistry.MAGINCIA_STYLE_THRONE.get(),
                                BlockRegistry.WOODEN_CHAIR.get(),
                                BlockRegistry.WOODEN_THRONE.get(),
                                BlockRegistry.STOOL.get(),
                                BlockRegistry.FOOTSTOOL.get(),
                                BlockRegistry.BENCH.get(),
                                BlockRegistry.CHAIR_TRINSIC.get(),
                                BlockRegistry.STRAW_CHAIR.get(),
                                BlockRegistry.CHAIR_VESPER.get(),
                            BlockRegistry.LORD_BRITISH_THRONE.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<DoubleBedBlockEntity>> DOUBLE_BED =
            BLOCK_ENTITIES.register("double_bed",
                    () -> BlockEntityType.Builder
                            .of(DoubleBedBlockEntity::new, BlockRegistry.DOUBLE_BED.get())
                            .build(null));
// === Winery Block Entities ===

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GrapeVineBlockEntity>> GRAPE_VINE_BE =
            BLOCK_ENTITIES.register("grape_vine_be",
                    () -> BlockEntityType.Builder.of(
                            GrapeVineBlockEntity::new,
                            BlockRegistry.GRAPE_VINE_BLOCK.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FarmingBlockEntity>> FARMING_BLOCK_BE =
            BLOCK_ENTITIES.register("farming_block_be", // NOTE: Ensure you use BLOCK_ENTITIES here if that is your register name
                    () -> BlockEntityType.Builder.of(
                            FarmingBlockEntity::new,
                            BlockRegistry.FARMING_BLOCK.get()
                    ).build(null));


                    // Inside your BlockRegistry class, in the BLOCK_ENTITIES section:

public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<JuicePressBlockEntity>> JUICE_PRESS_BE =
        BLOCK_ENTITIES.register("juice_press_be",
                () -> BlockEntityType.Builder.of(
                        JuicePressBlockEntity::new,
                        BlockRegistry.JUICE_PRESS.get()
                ).build(null));

public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WineBarrelBlockEntity>> WINE_BARREL_BE =
        BLOCK_ENTITIES.register("wine_barrel_be",
                () -> BlockEntityType.Builder.of(
                        WineBarrelBlockEntity::new,
                        BlockRegistry.WINE_BARREL.get()
                ).build(null));

public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WineBottleBlockEntity>> WINE_BOTTLE_BE = BLOCK_ENTITIES.register("wine_bottle",
    () -> BlockEntityType.Builder.of(
        WineBottleBlockEntity::new,
        BlockRegistry.WINE_BOTTLE_GREEN_BLOCK.get(),
        BlockRegistry.WINE_BOTTLE_BROWN_BLOCK.get(),
        BlockRegistry.WINE_BOTTLE_BLUE_BLOCK.get(),
        BlockRegistry.WINE_BOTTLE_CLEAR_BLOCK.get()
    ).build(null)
);

// Spawn Blocks


    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TraderSpawnBlockEntity>>
            TRADER_SPAWN_BLOCK_ENTITY_TYPE = BLOCK_ENTITIES.register(
                "trader_spawn_block_entity",
                () -> BlockEntityType.Builder.of(
                        TraderSpawnBlockEntity::new,
                        BlockRegistry.TRADER_SPAWN_BLOCK.get()
                ).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MonsterSpawnBlockEntity>> MONSTER_SPAWN_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("monster_spawn_block_entity",
                    () -> BlockEntityType.Builder.of(MonsterSpawnBlockEntity::new, BlockRegistry.MONSTER_SPAWN_BLOCK.get()).build(null));

        public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArchitectSpawnBlockEntity>> ARCHITECT_SPAWN_BLOCK_ENTITY_TYPE =
        BLOCK_ENTITIES.register("architect_spawn_block_entity",
                () -> BlockEntityType.Builder.of(ArchitectSpawnBlockEntity::new,
                BlockRegistry.ARCHITECT_SPAWN_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LockableDoorBlockEntity>> LOCKABLE_DOOR =
        BLOCK_ENTITIES.register("lockable_door", () ->
            BlockEntityType.Builder.of(
                    LockableDoorBlockEntity::new,
                    /* All door blocks that should carry locks go here */
                    BlockRegistry.LOCKABLE_METAL_DOOR.get(),
                   BlockRegistry.LOCKABLE_WOOD_DOOR.get()
            ).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}