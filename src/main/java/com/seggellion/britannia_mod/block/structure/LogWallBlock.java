package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.List;

public class LogWallBlock extends CustomWallBlock {

    public LogWallBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public List<String> getTextureVariants() {
        return List.of(
            "log_wall"
        );
    }
}
