package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.List;

public class DungeonWallBlock extends CustomWallBlock {

    public DungeonWallBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public List<String> getTextureVariants() {
        return List.of(
            "dungeon_wall_0",
            "dungeon_wall_1",
            "dungeon_wall_2",
            "dungeon_wall_3"
        );
    }
}
