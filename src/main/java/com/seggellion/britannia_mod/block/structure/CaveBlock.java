package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.List;

public class CaveBlock extends CustomWallBlock {

    public CaveBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public List<String> getTextureVariants() {
        return List.of(
            "cave_0",
            "cave_1",
            "cave_2",
            "cave_3",
            "cave_4"
        );
    }
}
