package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.List;

public class BrickWallBlock extends CustomWallBlock {

    public BrickWallBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public List<String> getTextureVariants() {
        return List.of(
            "brick_wall_top",
            "brick_wall_bottom"
        );
    }
}
