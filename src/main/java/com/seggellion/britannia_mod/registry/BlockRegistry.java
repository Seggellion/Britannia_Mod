// BlockRegistry.java
package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.block.MoongateBlock;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class BlockRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(
            net.minecraft.core.registries.Registries.BLOCK, "britannia_mod");

    public static final DeferredHolder<Block, Block> MOONGATE_BLOCK = BLOCKS.register(
            "moongate_block", MoongateBlock::new);

    public static final DeferredHolder<Block, Block> MOONGATE_TOP = BLOCKS.register(
            "moongate_top", MoongateBlock::new);

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
