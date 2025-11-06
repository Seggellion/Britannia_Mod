package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.List;

public class CobbleStoneWallBlock extends CustomWallBlock {

    public CobbleStoneWallBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public List<String> getTextureVariants() {
        return List.of(
            "cobblestone_foundation",
            "cobblestone_top",
            "cobblestone_bottom"
        );
    }
}
