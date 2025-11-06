package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.List;

public class PlasterWoodWallBlock extends CustomWallBlock {

    public PlasterWoodWallBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public List<String> getTextureVariants() {
        return List.of(
            "plaster_wood_wall_foundation",
            "plaster_wood_wall_top",
            "plaster_wood_wall_bottom"
        );
    }
}
