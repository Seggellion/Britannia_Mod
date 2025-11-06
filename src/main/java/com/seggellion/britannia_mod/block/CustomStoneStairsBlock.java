package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class CustomStoneStairsBlock extends StairBlock {
    public CustomStoneStairsBlock() {
        super(
            Blocks.STONE.defaultBlockState(), // base shape
            BlockBehaviour.Properties.of()
                .strength(1.5f, 6.0f) // similar to stone
                .requiresCorrectToolForDrops()
                .noOcclusion()
        );
    }
}
