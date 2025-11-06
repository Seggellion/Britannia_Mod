package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.List;

public class StoneWallBlock extends CustomWallBlock {

    public StoneWallBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public List<String> getTextureVariants() {
        return List.of(
            "stone_wall_foundation",
            "stone_wall_top",
            "stone_wall_bottom"
        );
    }
}
