// BlockRegistry.java
package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.block.MoongateBlock;
import com.seggellion.britannia_mod.block.LichSpawnBlock;
import com.seggellion.britannia_mod.block.entity.LichSpawnBlockEntity;
import com.seggellion.britannia_mod.block.ShadeSpawnBlock;
import com.seggellion.britannia_mod.block.entity.ShadeSpawnBlockEntity;
import net.minecraft.world.level.block.Block;
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


public static final DeferredHolder<Block, Block> MOONGATE_BLOCK = BLOCKS.register(
            "moongate_block", MoongateBlock::new);

    public static final DeferredHolder<Block, Block> MOONGATE_TOP = BLOCKS.register(
            "moongate_top", MoongateBlock::new);

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);

    }
}
