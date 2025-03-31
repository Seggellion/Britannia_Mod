// BlockRegistry.java
package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.block.MoongateBlock;
import com.seggellion.britannia_mod.block.BaseOreBlock;
import com.seggellion.britannia_mod.block.MoongateTopBlock;
import com.seggellion.britannia_mod.block.SmallForgeBlock;
import com.seggellion.britannia_mod.block.LargeForgeBlock;
import com.seggellion.britannia_mod.block.SmallForgeBlockEntity;
import com.seggellion.britannia_mod.block.LargeForgeBlockEntity;
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
import com.seggellion.britannia_mod.block.entity.LichSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.WoodSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.MetalSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.StoneSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.FishSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.HorseSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.BlacksmithSpawnBlockEntity;
import com.seggellion.britannia_mod.block.ShadeSpawnBlock;
import com.seggellion.britannia_mod.block.entity.ShadeSpawnBlockEntity;
import com.seggellion.britannia_mod.block.DungeonMoongateBlock;
import com.seggellion.britannia_mod.block.DungeonMoongateTopBlock;
import com.seggellion.britannia_mod.block.entity.DungeonMoongateBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

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


        public static final DeferredHolder<Block, Block> YEW_TABLE = BLOCKS.register(
        "yew_table",
        () -> new RotatableFurnitureBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(0.3f)
                .noOcclusion())
        );


                public static final DeferredHolder<Block, Block> MAGINCIA_STYLE_THRONE = BLOCKS.register(
        "magincia_style_throne",
         () -> new ChairBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(0.3f)
                .noOcclusion()
        )
        );



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



    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);

    }
}
