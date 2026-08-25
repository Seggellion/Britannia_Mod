package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.block.entity.AdaptiveRoofBlockEntity;
import com.seggellion.britannia_mod.block.entity.CarpetTeleporterBlockEntity;
import com.seggellion.britannia_mod.block.entity.DoubleBedBlockEntity;
import com.seggellion.britannia_mod.block.entity.DisplayCaseBlockEntity;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.seggellion.britannia_mod.block.entity.StoreSignBlockEntity;
import com.seggellion.britannia_mod.registry.FishRegistry;
import com.seggellion.britannia_mod.block.entity.FishBlockEntity;
import com.seggellion.britannia_mod.block.entity.TraderSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.MerchantSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.QuestGiverSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.ServiceNpcSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.QuestDestinationBlockEntity;
import com.seggellion.britannia_mod.block.entity.BritanniaSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.ArchitectSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.ChessBoardBlockEntity;
import com.seggellion.britannia_mod.block.nudgeable.block_entities.CandelabraBlockEntity;
import com.seggellion.britannia_mod.block.nudgeable.block_entities.ChairBlockEntity;
import com.seggellion.britannia_mod.block.nudgeable.block_entities.RotatableFurnitureBlockEntity;
import com.seggellion.britannia_mod.structure.HouseSignBlockEntity;
import com.seggellion.britannia_mod.block.entity.LockableDoorBlockEntity;
import com.seggellion.britannia_mod.block.entity.ThreeHeightLightBlockEntity;
import com.seggellion.britannia_mod.block.entity.GrapeVineBlockEntity;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.block.entity.FlowerBlockEntity;
import com.seggellion.britannia_mod.block.entity.HouseFarmPlotBlockEntity;
import com.seggellion.britannia_mod.block.entity.ManagedFlowerBlockEntity;
import com.seggellion.britannia_mod.block.entity.CommunityFarmBlockEntity;
import com.seggellion.britannia_mod.block.entity.OrangeTreeRootBlockEntity;
import com.seggellion.britannia_mod.block.entity.WeightedWoodBlockEntity;
import com.seggellion.britannia_mod.block.entity.WineBarrelBlockEntity;
import com.seggellion.britannia_mod.block.entity.TrashBarrelBlockEntity;
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DisplayCaseBlockEntity>> DISPLAY_CASE =
            BLOCK_ENTITIES.register("display_case", () -> BlockEntityType.Builder.of(
                    DisplayCaseBlockEntity::new, BlockRegistry.DISPLAY_CASE.get()).build(null));

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
                            BlockRegistry.VILLA_LAMP_POST.get(),
                            BlockRegistry.TORCH_WALL.get(),
                        BlockRegistry.WOODEN_CHANDELIER.get(),
                        BlockRegistry.LARGE_WOODEN_CHANDELIER.get(),
                        BlockRegistry.SMALL_WOODEN_CHANDELIER.get(),
                        BlockRegistry.LARGE_IRON_CHANDELIER.get(),
                        BlockRegistry.SMALL_IRON_CHANDELIER.get(),
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlowerBlockEntity>> FLOWER_BLOCK_BE =
            BLOCK_ENTITIES.register("flower_block_be",
                    () -> BlockEntityType.Builder.of(
                            FlowerBlockEntity::new,
                            BlockRegistry.FLOWER_BLOCK.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HouseFarmPlotBlockEntity>> HOUSE_FARM_PLOT_BE =
            BLOCK_ENTITIES.register("house_farm_plot_be",
                    () -> BlockEntityType.Builder.of(
                            HouseFarmPlotBlockEntity::new,
                            BlockRegistry.HOUSE_FARM_PLOT.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ManagedFlowerBlockEntity>> MANAGED_FLOWER_BE =
            BLOCK_ENTITIES.register("managed_flower_be",
                    () -> BlockEntityType.Builder.of(
                            ManagedFlowerBlockEntity::new,
                            BlockRegistry.MANAGED_FLOWER.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CommunityFarmBlockEntity>> COMMUNITY_FARM_BLOCK_BE =
            BLOCK_ENTITIES.register("community_farm_block_be",
                    () -> BlockEntityType.Builder.of(
                            CommunityFarmBlockEntity::new,
                            BlockRegistry.COMMUNITY_FARM_BLOCK.get(),
                            BlockRegistry.COMMUNITY_HOED_FARM_BLOCK.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OrangeTreeRootBlockEntity>> ORANGE_TREE_ROOT_BE =
            BLOCK_ENTITIES.register("orange_tree_root_be",
                    () -> BlockEntityType.Builder.of(
                            OrangeTreeRootBlockEntity::new,
                            BlockRegistry.ORANGE_TREE_ROOT_BLOCK.get(),
                            BlockRegistry.LEMON_TREE_ROOT_BLOCK.get(),
                            BlockRegistry.LIME_TREE_ROOT_BLOCK.get(),
                            BlockRegistry.PEAR_TREE_ROOT_BLOCK.get(),
                            BlockRegistry.PEACH_TREE_ROOT_BLOCK.get(),
                            BlockRegistry.APPLE_TREE_ROOT_BLOCK.get(),
                            BlockRegistry.CHERRY_TREE_ROOT_BLOCK.get(),
                            BlockRegistry.OLIVE_TREE_ROOT_BLOCK.get(),
                            BlockRegistry.PLUM_TREE_ROOT_BLOCK.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WeightedWoodBlockEntity>> WEIGHTED_WOOD_BE =
            BLOCK_ENTITIES.register("weighted_wood_be",
                    () -> BlockEntityType.Builder.of(
                            WeightedWoodBlockEntity::new,
                            BlockRegistry.WEIGHTED_WOOD_BLOCK.get()
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MerchantSpawnBlockEntity>>
            MERCHANT_SPAWN_BLOCK_ENTITY_TYPE = BLOCK_ENTITIES.register(
                "merchant_spawn_block_entity",
                () -> BlockEntityType.Builder.of(
                        MerchantSpawnBlockEntity::new,
                        BlockRegistry.MERCHANT_SPAWN_BLOCK.get()
                ).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuestGiverSpawnBlockEntity>>
            QUEST_GIVER_SPAWN_BLOCK_ENTITY_TYPE = BLOCK_ENTITIES.register(
                "quest_giver_spawn_block_entity",
                () -> BlockEntityType.Builder.of(
                        QuestGiverSpawnBlockEntity::new,
                        BlockRegistry.QUEST_GIVER_SPAWN_BLOCK.get()
                ).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ServiceNpcSpawnBlockEntity>>
            SERVICE_NPC_SPAWN_BLOCK_ENTITY_TYPE = BLOCK_ENTITIES.register(
                "service_npc_spawn_block_entity",
                () -> BlockEntityType.Builder.of(
                        ServiceNpcSpawnBlockEntity::new,
                        BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get()
                ).build(null)
            );

public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuestDestinationBlockEntity>> QUEST_DESTINATION_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("quest_destination_block_entity", () ->
                    BlockEntityType.Builder.of(QuestDestinationBlockEntity::new, BlockRegistry.QUEST_DESTINATION_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BritanniaSpawnBlockEntity>> BRITANNIA_SPAWN_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("britannia_spawn_block_entity",
                    () -> BlockEntityType.Builder.of(BritanniaSpawnBlockEntity::new, BlockRegistry.BRITANNIA_SPAWN_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrashBarrelBlockEntity>> TRASH_BARREL_BE =
            BLOCK_ENTITIES.register("trash_barrel_block_entity",
                    () -> BlockEntityType.Builder.of(TrashBarrelBlockEntity::new, BlockRegistry.TRASH_BARREL_BLOCK.get()).build(null));


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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChessBoardBlockEntity>> CHESS_BOARD =
        BLOCK_ENTITIES.register("chess_board", () ->
            BlockEntityType.Builder.of(ChessBoardBlockEntity::new, BlockRegistry.CHESS_BOARD.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
