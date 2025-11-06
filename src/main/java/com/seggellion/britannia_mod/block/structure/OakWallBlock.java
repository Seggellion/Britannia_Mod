package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.List;

public class OakWallBlock extends CustomWallBlock {

    public OakWallBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public List<String> getTextureVariants() {
        return List.of(
            "oak_wall_top",
            "oak_wall_bottom"
        );
    }
}
