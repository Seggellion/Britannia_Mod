package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.block.HorizontalFacingBlock;
import com.seggellion.britannia_mod.block.GravestoneBlock;
import com.seggellion.britannia_mod.block.DoubleBedBlock;
import com.seggellion.britannia_mod.block.MoongateBlock;
import com.seggellion.britannia_mod.block.BrickFoundationBlock;
import com.seggellion.britannia_mod.structure.HouseSignBlock;
import com.seggellion.britannia_mod.block.BaseOreBlock;
import com.seggellion.britannia_mod.block.MoongateTopBlock;
import com.seggellion.britannia_mod.block.CarpetTeleporterBlock;
import com.seggellion.britannia_mod.block.CarpetDummyBlock;
import com.seggellion.britannia_mod.block.SmallForgeBlock;
import com.seggellion.britannia_mod.block.StatueBlock;
import com.seggellion.britannia_mod.block.StatueCoupleBlock;
import com.seggellion.britannia_mod.block.LargeForgeBlock;
import com.seggellion.britannia_mod.block.BlueTentBlock;
import com.seggellion.britannia_mod.block.PurpleTentBlock;
import com.seggellion.britannia_mod.block.SmallForgeBlockEntity;
import com.seggellion.britannia_mod.block.LargeForgeBlockEntity;
import com.seggellion.britannia_mod.block.BlueTentBlockEntity;
import com.seggellion.britannia_mod.block.PurpleTentBlockEntity;
import com.seggellion.britannia_mod.block.ChairBlock;
import com.seggellion.britannia_mod.block.RotatableFurnitureBlock;
import com.seggellion.britannia_mod.block.LichSpawnBlock;
import com.seggellion.britannia_mod.block.WoodSpawnBlock;
import com.seggellion.britannia_mod.block.MetalSpawnBlock;
import com.seggellion.britannia_mod.block.StoneSpawnBlock;
import com.seggellion.britannia_mod.block.FishSpawnBlock;
import com.seggellion.britannia_mod.block.HorseSpawnBlock;
import com.seggellion.britannia_mod.block.BlacksmithSpawnBlock;
import com.seggellion.britannia_mod.block.CandelabraBlock;
import com.seggellion.britannia_mod.block.HalfBlock;
import com.seggellion.britannia_mod.block.QuarterBlock;
import com.seggellion.britannia_mod.block.ThreeQuarterBlock;
import com.seggellion.britannia_mod.block.HouseLotBlock;
import com.seggellion.britannia_mod.block.TopOnlySlabBlock;
import com.seggellion.britannia_mod.block.WindowCollisionBlock;
import com.seggellion.britannia_mod.block.Window2x3Block;
import com.seggellion.britannia_mod.block.StoneFloorBlock;
import com.seggellion.britannia_mod.block.CustomStoneStairsBlock;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.seggellion.britannia_mod.block.entity.LichSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.WoodSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.MetalSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.StoneSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.FishSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.HorseSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.AdaptiveRoofBlockEntity;
import com.seggellion.britannia_mod.block.entity.BlacksmithSpawnBlockEntity;
import com.seggellion.britannia_mod.block.ShadeSpawnBlock;
import com.seggellion.britannia_mod.block.entity.ShadeSpawnBlockEntity;
import com.seggellion.britannia_mod.block.DungeonMoongateBlock;
import com.seggellion.britannia_mod.block.DungeonMoongateTopBlock;
import com.seggellion.britannia_mod.block.entity.DungeonMoongateBlockEntity;
import com.seggellion.britannia_mod.block.TopOakWallBlock;
import com.seggellion.britannia_mod.block.MetalDoorBlock;
import com.seggellion.britannia_mod.block.ThinWall;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.SlabBlock;


import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.world.level.block.entity.BlockEntityType;


public class BlockRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(
            net.minecraft.core.registries.Registries.BLOCK, "britannia_mod");

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(
            net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, "britannia_mod");

    public static final DeferredHolder<Block, Block> SHADE_SPAWN_BLOCK = BLOCKS.register(
            "shade_spawn_block", ShadeSpawnBlock::new);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShadeSpawnBlockEntity>> SHADE_SPAWN_BLOCK_ENTITY_TYPE = BLOCK_ENTITY_TYPES.register(
            "shade_spawn_block_entity",
            () -> BlockEntityType.Builder.of(ShadeSpawnBlockEntity::new, SHADE_SPAWN_BLOCK.get()).build(null));

// Custom fraction blocks
        public static final DeferredHolder<Block, Block> QUARTER_DIRT_BLOCK = BLOCKS.register(
        "quarter_dirt_block", 
        () -> new QuarterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.5f))
        );

public static final DeferredHolder<Block, HouseSignBlock> HOUSE_SIGN_BLOCK =
    BLOCKS.register("house_sign", HouseSignBlock::new);


       public static final DeferredHolder<Block, Block> HALF_DIRT_BLOCK = BLOCKS.register(
        "half_dirt_block", 
        () -> new HalfBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.5f))
        );
        
        public static final DeferredHolder<Block, Block> THREE_QUARTER_DIRT_BLOCK = BLOCKS.register(
        "three_quarter_dirt_block", 
        () -> new ThreeQuarterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.5f))
        );

        public static final DeferredHolder<Block, Block> QUARTER_GRASS_BLOCK = BLOCKS.register(
        "quarter_grass_block", 
        () -> new QuarterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.5f))
        );

       public static final DeferredHolder<Block, Block> HALF_GRASS_BLOCK = BLOCKS.register(
        "half_grass_block", 
        () -> new HalfBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.5f))
        );
        
        public static final DeferredHolder<Block, Block> THREE_QUARTER_GRASS_BLOCK = BLOCKS.register(
        "three_quarter_grass_block", 
        () -> new ThreeQuarterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.5f))
        );

        public static final DeferredHolder<Block, Block> QUARTER_DEEPSLATE_COBBLESTONE_BLOCK = BLOCKS.register(
        "quarter_deepslate_cobblestone_block", 
        () -> new QuarterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(3.0f, 6.0f))
        );

        public static final DeferredHolder<Block, Block> HALF_DEEPSLATE_COBBLESTONE_BLOCK = BLOCKS.register(
        "half_deepslate_cobblestone_block", 
        () -> new HalfBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(3.0f, 6.0f))
        );

        
        public static final DeferredHolder<Block, Block> THREE_QUARTER_DEEPSLATE_COBBLESTONE_BLOCK = BLOCKS.register(
        "three_quarter_deepslate_cobblestone_block", 
        () -> new ThreeQuarterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(3.0f, 6.0f))
        );


// end of fraction blocks
   public static final DeferredHolder<Block, Block> LICH_SPAWN_BLOCK = BLOCKS.register(
            "lich_spawn_block", LichSpawnBlock::new);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LichSpawnBlockEntity>> LICH_SPAWN_BLOCK_ENTITY_TYPE = BLOCK_ENTITY_TYPES.register(
            "lich_spawn_block_entity",
            () -> BlockEntityType.Builder.of(LichSpawnBlockEntity::new, LICH_SPAWN_BLOCK.get()).build(null));


    public static final DeferredHolder<Block, Block> WOOD_SPAWN_BLOCK = BLOCKS.register(
            "wood_spawn_block",
            WoodSpawnBlock::new 
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WoodSpawnBlockEntity>> WOOD_SPAWN_BLOCK_ENTITY_TYPE = BLOCK_ENTITY_TYPES.register(
            "wood_spawn_block_entity",
            () -> BlockEntityType.Builder.of(WoodSpawnBlockEntity::new, WOOD_SPAWN_BLOCK.get()).build(null));


    public static final DeferredHolder<Block, Block> STONE_SPAWN_BLOCK = BLOCKS.register(
            "stone_spawn_block",
            StoneSpawnBlock::new 
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StoneSpawnBlockEntity>> STONE_SPAWN_BLOCK_ENTITY_TYPE = BLOCK_ENTITY_TYPES.register(
            "stone_spawn_block_entity",
            () -> BlockEntityType.Builder.of(StoneSpawnBlockEntity::new, STONE_SPAWN_BLOCK.get()).build(null));


    public static final DeferredHolder<Block, Block> METAL_SPAWN_BLOCK = BLOCKS.register(
            "metal_spawn_block",
            MetalSpawnBlock::new 
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MetalSpawnBlockEntity>> METAL_SPAWN_BLOCK_ENTITY_TYPE = BLOCK_ENTITY_TYPES.register(
            "metal_spawn_block_entity",
            () -> BlockEntityType.Builder.of(MetalSpawnBlockEntity::new, METAL_SPAWN_BLOCK.get()).build(null));




    public static final DeferredHolder<Block, Block> FISH_SPAWN_BLOCK = BLOCKS.register(
            "fish_spawn_block",
            FishSpawnBlock::new 
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FishSpawnBlockEntity>> FISH_SPAWN_BLOCK_ENTITY_TYPE = BLOCK_ENTITY_TYPES.register(
            "fish_spawn_block_entity",
            () -> BlockEntityType.Builder.of(FishSpawnBlockEntity::new, FISH_SPAWN_BLOCK.get()).build(null));


    public static final DeferredHolder<Block, Block> HORSE_SPAWN_BLOCK = BLOCKS.register(
            "horse_spawn_block",
            HorseSpawnBlock::new 
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HorseSpawnBlockEntity>> HORSE_SPAWN_BLOCK_ENTITY_TYPE = BLOCK_ENTITY_TYPES.register(
            "horse_spawn_block_entity",
            () -> BlockEntityType.Builder.of(HorseSpawnBlockEntity::new, HORSE_SPAWN_BLOCK.get()).build(null));

    public static final DeferredHolder<Block, Block> BLACKSMITH_SPAWN_BLOCK = BLOCKS.register(
            "blacksmith_spawn_block",
            BlacksmithSpawnBlock::new 
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlacksmithSpawnBlockEntity>> BLACKSMITH_SPAWN_BLOCK_ENTITY_TYPE = BLOCK_ENTITY_TYPES.register(
            "blacksmith_spawn_block_entity",
            () -> BlockEntityType.Builder.of(BlacksmithSpawnBlockEntity::new, BLACKSMITH_SPAWN_BLOCK.get()).build(null));

    // ✅ Register Large Forge Block
    public static final DeferredHolder<Block, Block> LARGE_FORGE_BLOCK = BLOCKS.register(
            "large_forge_block", LargeForgeBlock::new);

    // ✅ Register Large Forge Block Entity Type
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeForgeBlockEntity>> LARGE_FORGE_BLOCK_ENTITY_TYPE =
        BLOCK_ENTITY_TYPES.register("large_forge_block_entity",
            () -> BlockEntityType.Builder.of(LargeForgeBlockEntity::new, LARGE_FORGE_BLOCK.get()).build(null));


    public static final DeferredHolder<Block, Block> SMALL_FORGE_BLOCK = BLOCKS.register(
            "small_forge_block", SmallForgeBlock::new);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmallForgeBlockEntity>> SMALL_FORGE_BLOCK_ENTITY_TYPE =
        BLOCK_ENTITY_TYPES.register("small_forge_block_entity",
            () -> BlockEntityType.Builder.of(SmallForgeBlockEntity::new, SMALL_FORGE_BLOCK.get()).build(null));


//statues

public static final DeferredHolder<Block, Block> STATUE_STAND = BLOCKS.register("statue_stand", () ->
    new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE))
);


public static final DeferredHolder<Block, Block> STATUE_PILLAR = BLOCKS.register("statue_pillar", 
    () -> new StatueBlock(BlockBehaviour.Properties.of()
        .noOcclusion()
        .strength(1.0f))
);


public static final DeferredHolder<Block, Block> STATUE_WOMAN = BLOCKS.register("statue_woman", 
    () -> new StatueBlock(BlockBehaviour.Properties.of()
        .noOcclusion()
        .strength(1.0f))
);

public static final DeferredHolder<Block, Block> STATUE_COUPLE = BLOCKS.register("statue_couple", 
    () -> new StatueCoupleBlock(BlockBehaviour.Properties.of()
        .noOcclusion()
        .strength(1.0f))
);

public static final DeferredHolder<Block, Block> STATUE_MAN = BLOCKS.register("statue_man", 
    () -> new StatueBlock(BlockBehaviour.Properties.of()
        .noOcclusion()
        .strength(1.0f))
);


// Graveyard

public static final DeferredHolder<Block, Block> GRAVESTONE_TYPE_1 = BLOCKS.register("gravestone_type_1", GravestoneBlock::new);
public static final DeferredHolder<Block, Block> GRAVESTONE_TYPE_2 = BLOCKS.register("gravestone_type_2", GravestoneBlock::new);
public static final DeferredHolder<Block, Block> GRAVESTONE_TYPE_3 = BLOCKS.register("gravestone_type_3", GravestoneBlock::new);
public static final DeferredHolder<Block, Block> GRAVESTONE_TYPE_4 = BLOCKS.register("gravestone_type_4", GravestoneBlock::new);
public static final DeferredHolder<Block, Block> GRAVESTONE_TYPE_5 = BLOCKS.register("gravestone_type_5", GravestoneBlock::new);
public static final DeferredHolder<Block, Block> GRAVESTONE_TYPE_6 = BLOCKS.register("gravestone_type_6", GravestoneBlock::new);
public static final DeferredHolder<Block, Block> GRAVESTONE_TYPE_7 = BLOCKS.register("gravestone_type_7", GravestoneBlock::new);
public static final DeferredHolder<Block, Block> GRAVESTONE_TYPE_8 = BLOCKS.register("gravestone_type_8", GravestoneBlock::new);
public static final DeferredHolder<Block, Block> GRAVESTONE_TYPE_9 = BLOCKS.register("gravestone_type_9", GravestoneBlock::new);
public static final DeferredHolder<Block, Block> GRAVESTONE_TYPE_10 = BLOCKS.register("gravestone_type_10", GravestoneBlock::new);
public static final DeferredHolder<Block, Block> GRAVESTONE_TYPE_11 = BLOCKS.register("gravestone_type_11", GravestoneBlock::new);
public static final DeferredHolder<Block, Block> GRAVESTONE_TYPE_12 = BLOCKS.register("gravestone_type_12", GravestoneBlock::new);
public static final DeferredHolder<Block, Block> GRAVESTONE_TYPE_13 = BLOCKS.register("gravestone_type_13", GravestoneBlock::new);
public static final DeferredHolder<Block, Block> GRAVESTONE_TYPE_14 = BLOCKS.register("gravestone_type_14", GravestoneBlock::new);
public static final DeferredHolder<Block, Block> GRAVESTONE_TYPE_15 = BLOCKS.register("gravestone_type_15", GravestoneBlock::new);
public static final DeferredHolder<Block, Block> GRAVESTONE_TYPE_16 = BLOCKS.register("gravestone_type_16", GravestoneBlock::new);

public static final DeferredHolder<Block, Block> BROKEN_WOODEN_GRAVE_CROSS = BLOCKS.register("broken_wooden_grave_cross", () ->
    new HorizontalFacingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion()));
public static final DeferredHolder<Block, Block> DAMAGED_WOODEN_GRAVE_CROSS = BLOCKS.register("damaged_wooden_grave_cross", () ->
    new HorizontalFacingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion()));
public static final DeferredHolder<Block, Block> WOODEN_GRAVE_CROSS = BLOCKS.register("wooden_grave_cross", () ->
    new HorizontalFacingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion()));

public static final DeferredHolder<Block, Block> WOODEN_COFFIN = BLOCKS.register("wooden_coffin", () ->
    new HorizontalFacingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion()));
public static final DeferredHolder<Block, Block> WOODEN_OPEN_COFFIN = BLOCKS.register("wooden_open_coffin", () ->
    new HorizontalFacingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion()));
public static final DeferredHolder<Block, Block> WOODEN_COFFIN_LID = BLOCKS.register("wooden_coffin_lid", () ->
    new HorizontalFacingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion()));
public static final DeferredHolder<Block, Block> WOODEN_COFFIN_SKELETON = BLOCKS.register("wooden_coffin_skeleton", () ->
    new HorizontalFacingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion()));

public static final DeferredHolder<Block, Block> IRON_CEMETERY_GATE_ARCH = BLOCKS.register("iron_cemetery_gate_arch", () ->
    new HorizontalFacingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));
public static final DeferredHolder<Block, Block> IRON_FENCE_1 = BLOCKS.register("iron_fence_1", () ->
    new HorizontalFacingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS).noOcclusion()));
public static final DeferredHolder<Block, Block> IRON_FENCE_2 = BLOCKS.register("iron_fence_2", () ->
    new HorizontalFacingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS).noOcclusion()));

public static final DeferredHolder<Block, Block> DEAD_GRAVE_FLOWER_VASE = BLOCKS.register("dead_grave_flower_vase", () ->
    new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).noOcclusion()));
public static final DeferredHolder<Block, Block> DEAD_GRAVE_FLOWERS = BLOCKS.register("dead_grave_flowers", () ->
    new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEAD_BUSH).noOcclusion()));
public static final DeferredHolder<Block, Block> GRAVE_FLOWERS = BLOCKS.register("grave_flowers", () ->
    new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.POPPY).noOcclusion()));
public static final DeferredHolder<Block, Block> WILTED_GRAVE_FLOWER_VASE = BLOCKS.register("wilted_grave_flower_vase", () ->
    new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).noOcclusion()));
public static final DeferredHolder<Block, Block> WILTED_GRAVE_FLOWERS = BLOCKS.register("wilted_grave_flowers", () ->
    new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEAD_BUSH).noOcclusion()));

public static final DeferredHolder<Block, Block> LYING_SKELETON = BLOCKS.register("lying_skeleton", () ->
    new HorizontalFacingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS).noOcclusion()));
public static final DeferredHolder<Block, Block> SITTING_SKELETON = BLOCKS.register("sitting_skeleton", () ->
    new HorizontalFacingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS).noOcclusion()));
public static final DeferredHolder<Block, Block> SKELETON_ARM = BLOCKS.register("skeleton_arm", () ->
    new HorizontalFacingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS).noOcclusion()));
public static final DeferredHolder<Block, Block> SKELETON_LEG = BLOCKS.register("skeleton_leg", () ->
    new HorizontalFacingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS).noOcclusion()));
public static final DeferredHolder<Block, Block> SKELETON_TORSO = BLOCKS.register("skeleton_torso", () ->
    new HorizontalFacingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS).noOcclusion()));



// House deeds

public static final DeferredHolder<Block, Block> HOUSE_LOT_BLOCK = BLOCKS.register(
    "house_lot_block", HouseLotBlock::new);

public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HouseLotBlockEntity>> HOUSE_LOT_BLOCK_ENTITY_TYPE =
    BLOCK_ENTITY_TYPES.register("house_lot_block_entity",
        () -> BlockEntityType.Builder.of(HouseLotBlockEntity::new, HOUSE_LOT_BLOCK.get()).build(null));


public static final DeferredHolder<Block, Block> BLUE_TENT = BLOCKS.register("blue_tent", 
    () -> new BlueTentBlock(BlockBehaviour.Properties.of()
        .noOcclusion()
        .strength(1.0f))
);


public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlueTentBlockEntity>> BLUE_TENT_BLOCK_ENTITY_TYPE =
    BLOCK_ENTITY_TYPES.register("blue_tent_block_entity",
        () -> BlockEntityType.Builder.of(BlueTentBlockEntity::new, BLUE_TENT.get()).build(null));


public static final DeferredHolder<Block, Block> PURPLE_TENT = BLOCKS.register("purple_tent", 
    () -> new PurpleTentBlock(BlockBehaviour.Properties.of()
        .noOcclusion()
        .strength(1.0f))
);

public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PurpleTentBlockEntity>> PURPLE_TENT_BLOCK_ENTITY_TYPE =
    BLOCK_ENTITY_TYPES.register("purple_tent_block_entity",
        () -> BlockEntityType.Builder.of(PurpleTentBlockEntity::new, PURPLE_TENT.get()).build(null));



//end of house deeds

// teleporters

public static final DeferredHolder<Block, Block> DUNGEON_MOONGATE_BLOCK = BLOCKS.register(
        "dungeon_moongate_block", DungeonMoongateBlock::new);

public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DungeonMoongateBlockEntity>> DUNGEON_MOONGATE_BLOCK_ENTITY_TYPE = BLOCK_ENTITY_TYPES.register(
        "dungeon_moongate_block_entity",
        () -> BlockEntityType.Builder.of(DungeonMoongateBlockEntity::new, DUNGEON_MOONGATE_BLOCK.get()).build(null));

public static final DeferredHolder<Block, Block> DUNGEON_MOONGATE_TOP = BLOCKS.register(
    "dungeon_moongate_top", DungeonMoongateTopBlock::new);


public static final DeferredHolder<Block, Block> MOONGATE_BLOCK = BLOCKS.register(
            "moongate_block", MoongateBlock::new);

    public static final DeferredHolder<Block, Block> MOONGATE_TOP = BLOCKS.register(
            "moongate_top", MoongateTopBlock::new);


public static final DeferredHolder<Block, Block> CARPET_TELEPORTER_BLOCK = BLOCKS.register(
    "carpet_teleporter_block", CarpetTeleporterBlock::new);

public static final DeferredHolder<Block, Block> CARPET_DUMMY_BLOCK = BLOCKS.register(
    "carpet_dummy_block", CarpetDummyBlock::new);

// light sources


public static final DeferredHolder<Block, Block> CANDELABRA_SMALL = BLOCKS.register(
    "candelabra_small",
    () -> new CandelabraBlock(BlockBehaviour.Properties.of()
        .mapColor(MapColor.METAL)
        .strength(0.3f)
        .noOcclusion()
        .lightLevel(state -> 15))
);

public static final DeferredHolder<Block, Block> CANDELABRA_TALL = BLOCKS.register(
    "candelabra_tall",
    () -> new CandelabraBlock(BlockBehaviour.Properties.of()
        .mapColor(MapColor.METAL)
        .strength(0.3f)
        .noOcclusion()
        .lightLevel(state -> 15))
);


public static final DeferredHolder<Block, Block> LAMP_POST_REGULAR = BLOCKS.register(
    "lamp_post_regular",
    () -> new CandelabraBlock(BlockBehaviour.Properties.of()
        .mapColor(MapColor.METAL)
        .strength(0.3f)
        .noOcclusion()
        .lightLevel(state -> 15))
);

public static final DeferredHolder<Block, Block> LAMP_POST_FANCY = BLOCKS.register(
    "lamp_post_fancy",
    () -> new CandelabraBlock(BlockBehaviour.Properties.of()
        .mapColor(MapColor.METAL)
        .strength(0.3f)
        .noOcclusion()
        .lightLevel(state -> 15))
);


public static final DeferredHolder<Block, Block> TORCH_WALL = BLOCKS.register(
    "torch_wall",
    () -> new CandelabraBlock(BlockBehaviour.Properties.of()
        .mapColor(MapColor.METAL)
        .strength(0.3f)
        .noOcclusion()
        .lightLevel(state -> 15))
);

public static final DeferredHolder<Block, Block> TORCH_STANDING = BLOCKS.register(
    "torch_standing",
    () -> new CandelabraBlock(BlockBehaviour.Properties.of()
        .mapColor(MapColor.METAL)
        .strength(0.3f)
        .noOcclusion()
        .lightLevel(state -> 15))
);

public static final DeferredHolder<Block, Block> BRAZIER_SMALL = BLOCKS.register(
    "brazier_small",
    () -> new CandelabraBlock(BlockBehaviour.Properties.of()
        .mapColor(MapColor.METAL)
        .strength(0.3f)
        .noOcclusion()
        .lightLevel(state -> 15))
);

// furniture


        public static final DeferredHolder<Block, Block> YEW_TABLE = BLOCKS.register(
        "yew_table",
        () -> new RotatableFurnitureBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(0.3f)
                .noOcclusion())
        );


// Roof blocks

public static final DeferredHolder<Block, Block> TILE_ROOF_BASE = BLOCKS.register("tile_roof_base", () ->
    new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE))
);


public static final DeferredHolder<Block, Block> THATCH_ROOF_BASE = BLOCKS.register("thatch_roof_base", () ->
    new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.HAY_BLOCK))
);

public static final DeferredHolder<Block, StairBlock> THATCH_ROOF = BLOCKS.register("thatch_roof", () ->
    new StairBlock(THATCH_ROOF_BASE.get().defaultBlockState(),
        BlockBehaviour.Properties.ofFullCopy(Blocks.BRICK_STAIRS))
);

public static final DeferredHolder<Block, Block> SLATE_ROOF_BASE = BLOCKS.register("slate_roof_base", () ->
    new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE))
);

public static final DeferredHolder<Block, StairBlock> SLATE_ROOF = BLOCKS.register("slate_roof", () ->
    new StairBlock(SLATE_ROOF_BASE.get().defaultBlockState(),
        BlockBehaviour.Properties.ofFullCopy(Blocks.BRICK_STAIRS))
);

public static final DeferredHolder<Block, Block> CEDAR_ROOF_BASE = BLOCKS.register("cedar_roof_base", () ->
    new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE))
);

public static final DeferredHolder<Block, StairBlock> CEDAR_ROOF = BLOCKS.register("cedar_roof", () ->
    new StairBlock(CEDAR_ROOF_BASE.get().defaultBlockState(),
        BlockBehaviour.Properties.ofFullCopy(Blocks.BRICK_STAIRS))
);


public static final DeferredHolder<Block, StairBlock> TILE_ROOF = BLOCKS.register("tile_roof", () ->
    new StairBlock(TILE_ROOF_BASE.get().defaultBlockState(),
        BlockBehaviour.Properties.ofFullCopy(Blocks.BRICK_STAIRS))
);


public static final DeferredHolder<Block, TopOnlySlabBlock> TILE_ROOF_FLAT = BLOCKS.register("tile_roof_flat", () ->
    new TopOnlySlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB))
);


public static final DeferredHolder<Block, TopOnlySlabBlock> SLATE_ROOF_FLAT = BLOCKS.register("slate_roof_flat", () ->
    new TopOnlySlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB))
);

public static final DeferredHolder<Block, TopOnlySlabBlock> SLATE_ROOF_1_FLAT = BLOCKS.register("slate_roof_1_flat", () ->
    new TopOnlySlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB))
);

public static final DeferredHolder<Block, TopOnlySlabBlock> SLATE_ROOF_2_FLAT = BLOCKS.register("slate_roof_2_flat", () ->
    new TopOnlySlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB))
);


public static final DeferredHolder<Block, TopOnlySlabBlock> THATCH_ROOF_FLAT = BLOCKS.register("thatch_roof_flat", () ->
    new TopOnlySlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB))
);

public static final DeferredHolder<Block, TopOnlySlabBlock> CEDAR_ROOF_FLAT = BLOCKS.register("cedar_roof_flat", () ->
    new TopOnlySlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB))
);

// structure blocks

// Bottom Oak Wall
//public static final DeferredHolder<Block, OffsetQuarterPaneBlock> OAK_WALL_BOTTOM =
//        BLOCKS.register("oak_wall_bottom", OffsetQuarterPaneBlock::new);

public static final DeferredHolder<Block, Block> BRICK_FOUNDATION_OAK = BLOCKS.register(
    "brick_foundation_oak",
    () -> new Block(Block.Properties.of().strength(2.0f).requiresCorrectToolForDrops())
);

public static final DeferredHolder<Block, Block> BRICK_FOUNDATION_SPRUCE = BLOCKS.register(
    "brick_foundation_spruce",
    () -> new Block(Block.Properties.of().strength(2.0f).requiresCorrectToolForDrops())
);


public static final DeferredHolder<Block, Block> STONE_FOUNDATION = BLOCKS.register(
    "stone_foundation",
    () -> new Block(BlockBehaviour.Properties.of()
        .mapColor(MapColor.STONE)
        .strength(2.0f, 6.0f)
        .sound(SoundType.STONE))
);

public static final DeferredHolder<Block, ThinWall> WINDOW_1X1 =
    BLOCKS.register("window_1x1", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(1.0f)
            .sound(SoundType.WOOD)
            .noOcclusion()
        )
    );

public static final DeferredHolder<Block, ThinWall> WINDOW_1X2 =
    BLOCKS.register("window_1x2", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(1.0f)
            .sound(SoundType.WOOD)
            .noOcclusion()
        )
    );

public static final DeferredHolder<Block, ThinWall> WINDOW_1X3 =
    BLOCKS.register("window_1x3", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(1.0f)
            .sound(SoundType.WOOD)
            .noOcclusion()
        )
    );

public static final DeferredHolder<Block, ThinWall> WINDOW_2X2 =
    BLOCKS.register("window_2x2", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(1.0f)
            .sound(SoundType.WOOD)
            .noOcclusion()
        )
    );

public static final DeferredHolder<Block, Window2x3Block> WINDOW_2X3 =
    BLOCKS.register("window_2x3", 
        () -> new Window2x3Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(1.0f)
            .sound(SoundType.WOOD)
            .noOcclusion()
        )
    );

    public static final DeferredHolder<Block, Block> WINDOW_COLLISION = BLOCKS.register(
        "window_collision", WindowCollisionBlock::new);
                

    public static final DeferredHolder<Block, ThinWall> WINDOW_CROSS_1X1 =
    BLOCKS.register("window_cross_1x1", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(1.0f)
            .sound(SoundType.WOOD)
            .noOcclusion()
        )
    );


    public static final DeferredHolder<Block, ThinWall> WINDOW_CROSS_1X2 =
    BLOCKS.register("window_cross_1x2", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(1.0f)
            .sound(SoundType.WOOD)
            .noOcclusion()
        )
    );


    public static final DeferredHolder<Block, ThinWall> WINDOW_CROSS_1X3 =
    BLOCKS.register("window_cross_1x3", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(1.0f)
            .sound(SoundType.WOOD)
            .noOcclusion()
        )
    );
    
    public static final DeferredHolder<Block, ThinWall> WINDOW_CROSS_2X2 =
    BLOCKS.register("window_cross_2x2", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(1.0f)
            .sound(SoundType.WOOD)
            .noOcclusion()
        )
    );

    
    public static final DeferredHolder<Block, ThinWall> WINDOW_CROSS_2X3 =
    BLOCKS.register("window_cross_2x3", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(1.0f)
            .sound(SoundType.WOOD)
            .noOcclusion()
        )
    );

    public static final DeferredHolder<Block, ThinWall> WINDOW_BIRCH_1X1 =
    BLOCKS.register("window_birch_1x1", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(1.0f)
            .sound(SoundType.WOOD)
            .noOcclusion()
        )
    );

public static final DeferredHolder<Block, ThinWall> OAK_WALL_BOTTOM =
    BLOCKS.register("oak_wall_bottom", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(1.0f)
            .sound(SoundType.WOOD)
            .noOcclusion()
        )
    );

    public static final DeferredHolder<Block, ThinWall> OAK_WALL_TOP =
    BLOCKS.register("oak_wall_top", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(1.0f)
            .sound(SoundType.WOOD)
            .noOcclusion()
        )
    );

    public static final DeferredHolder<Block, ThinWall> BRICK_WALL_BOTTOM =
    BLOCKS.register("brick_wall_bottom", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(1.0f)
            .sound(SoundType.WOOD)
            .noOcclusion()
        )
    );

    public static final DeferredHolder<Block, ThinWall> BRICK_WALL_TOP =
    BLOCKS.register("brick_wall_top", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(1.0f)
            .sound(SoundType.WOOD)
            .noOcclusion()
        )
    );

public static final DeferredHolder<Block, Block> STONE_FLOOR =
    BLOCKS.register("stone_floor",
        StoneFloorBlock::new
    );

public static final DeferredHolder<Block, Block> STONE_FLOOR_POLISHED =
    BLOCKS.register("stone_floor_polished",
        () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(1.5F, 6.0F)
            .requiresCorrectToolForDrops()));

public static final DeferredHolder<Block, Block> CUSTOM_STONE_STAIRS = BLOCKS.register(
    "custom_stone_stairs", CustomStoneStairsBlock::new
);


public static final DeferredHolder<Block, ThinWall> STONE_WALL_BOTTOM =
    BLOCKS.register("stone_wall_bottom", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0f)
            .sound(SoundType.STONE)
            .noOcclusion()
        )
    );

    public static final DeferredHolder<Block, ThinWall> STONE_WALL_TOP =
    BLOCKS.register("stone_wall_top", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0f)
            .sound(SoundType.STONE)
            .noOcclusion()
        )
    );

    public static final DeferredHolder<Block, ThinWall> COBBLESTONE_FOUNDATION =
    BLOCKS.register("cobblestone_foundation", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0f)
            .sound(SoundType.STONE)
            .noOcclusion()
        )
    );

public static final DeferredHolder<Block, ThinWall> COBBLESTONE_WALL_BOTTOM =
    BLOCKS.register("cobblestone_wall_bottom", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0f)
            .sound(SoundType.STONE)
            .noOcclusion()
        )
    );

    public static final DeferredHolder<Block, ThinWall> COBBLESTONE_WALL_TOP =
    BLOCKS.register("cobblestone_wall_top", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0f)
            .sound(SoundType.STONE)
            .noOcclusion()
        )
    );

    public static final DeferredHolder<Block, ThinWall> BIRCH_WALL =
    BLOCKS.register("birch_wall", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(1.0f)
            .sound(SoundType.WOOD)
            .noOcclusion()
        )
    );

    
    public static final DeferredHolder<Block, ThinWall> LOG_WALL =
    BLOCKS.register("log_wall", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(1.0f)
            .sound(SoundType.WOOD)
            .noOcclusion()
        )
    );

public static final DeferredHolder<Block, ThinWall> PLASTER_STONE_FOUNDATION =
    BLOCKS.register("plaster_stone_foundation", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0f)
            .sound(SoundType.STONE)
            .noOcclusion()
        )
    );

public static final DeferredHolder<Block, ThinWall> PLASTER_STONE_WALL_BOTTOM =
    BLOCKS.register("plaster_stone_wall_bottom", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0f)
            .sound(SoundType.STONE)
            .noOcclusion()
        )
    );

    public static final DeferredHolder<Block, ThinWall> PLASTER_STONE_WALL_TOP =
    BLOCKS.register("plaster_stone_wall_top", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0f)
            .sound(SoundType.STONE)
            .noOcclusion()
        )
    );

public static final DeferredHolder<Block, ThinWall> PLASTER_WOOD_FOUNDATION =
    BLOCKS.register("plaster_wood_foundation", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0f)
            .sound(SoundType.STONE)
            .noOcclusion()
        )
    );

public static final DeferredHolder<Block, ThinWall> PLASTER_WOOD_WALL_BOTTOM =
    BLOCKS.register("plaster_wood_wall_bottom", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0f)
            .sound(SoundType.STONE)
            .noOcclusion()
        )
    );

    public static final DeferredHolder<Block, ThinWall> PLASTER_WOOD_WALL_TOP =
    BLOCKS.register("plaster_wood_wall_top", 
        () -> new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0f)
            .sound(SoundType.STONE)
            .noOcclusion()
        )
    );

// Furniture

        public static final DeferredHolder<Block, Block> MAGINCIA_STYLE_THRONE = BLOCKS.register(
            "magincia_style_throne",
            () -> new ChairBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(0.3f)
                    .noOcclusion()
            )
        );

    public static final DeferredHolder<Block, Block> DOUBLE_BED =
            BLOCKS.register("double_bed",
                    () -> new DoubleBedBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_BLUE) // pick any colour you like
                            .strength(0.2F)
                            .noOcclusion()));

        public static final DeferredHolder<Block, Block> LORD_BRITISH_THRONE = BLOCKS.register(
            "lord_british_throne",
            () -> new ChairBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(0.3f)
                    .noOcclusion()
            )
        );

// Ores


        public static final DeferredHolder<Block, Block> COPPER_ORE = BLOCKS.register(
                "copper_ore", BaseOreBlock::new);
                
        public static final DeferredHolder<Block, Block> TIN_ORE = BLOCKS.register(
                "tin_ore", BaseOreBlock::new);

        public static final DeferredHolder<Block, Block> SILVER_ORE = BLOCKS.register(
                "silver_ore", BaseOreBlock::new);

        public static final DeferredHolder<Block, Block> GOLD_ORE = BLOCKS.register(
                "gold_ore", BaseOreBlock::new);

        public static final DeferredHolder<Block, Block> SHADOW_IRON_ORE = BLOCKS.register(
                "shadow_iron_ore", BaseOreBlock::new);

        public static final DeferredHolder<Block, Block> AGAPITE_ORE = BLOCKS.register(
                "agapite_ore", BaseOreBlock::new);

        public static final DeferredHolder<Block, Block> VERITE_ORE = BLOCKS.register(
                "verite_ore", BaseOreBlock::new);

        public static final DeferredHolder<Block, Block> VALORITE_ORE = BLOCKS.register(
                "valorite_ore", BaseOreBlock::new);

        public static final DeferredHolder<Block, Block> HIGH_PURITY_SILVER_ORE = BLOCKS.register(
                "high_purity_silver_ore", BaseOreBlock::new);

        public static final DeferredHolder<Block, Block> IGNEOUS_ROCK = BLOCKS.register(
                "igneous_rock", BaseOreBlock::new);

        public static final DeferredHolder<Block, Block> METAMORPHIC_ROCK = BLOCKS.register(
                "metamorphic_rock", BaseOreBlock::new);

        public static final DeferredHolder<Block, Block> VOLCANIC_ROCK = BLOCKS.register(
                "volcanic_rock", BaseOreBlock::new);

        public static final DeferredHolder<Block, Block> GLACIAL_ROCK = BLOCKS.register(
                "glacial_rock", BaseOreBlock::new);

        public static final DeferredHolder<Block, Block> METAL_DOOR = BLOCKS.register(
        "metal_door", MetalDoorBlock::new);


    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);

    }
}
