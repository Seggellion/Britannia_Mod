package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;


public class DungeonMoongateTopBlock extends Block {
    public DungeonMoongateTopBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(-1.0F, 3600000.0F)
                .noLootTable()
                .noCollission()
                .lightLevel((state) -> 10)
                .sound(SoundType.GLASS)
        );
    }
}
