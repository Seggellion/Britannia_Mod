package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class BaseOreBlock extends Block {
    public BaseOreBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0f, 3.0f));
    }
}
