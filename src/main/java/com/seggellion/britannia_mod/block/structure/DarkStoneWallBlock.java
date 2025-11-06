package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.List;

public class DarkStoneWallBlock extends CustomWallBlock {

    public DarkStoneWallBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public List<String> getTextureVariants() {
        return List.of(
            "dark_stone_wall_top",
            "dark_stone_wall_bottom"
        );
    }
}
