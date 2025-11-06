package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.List;

public class PlasterStoneWallBlock extends CustomWallBlock {

    public PlasterStoneWallBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public List<String> getTextureVariants() {
        return List.of(
            "plaster_stone_wall_foundation",
            "plaster_stone_wall_top",
            "plaster_stone_wall_bottom"
        );
    }
}
