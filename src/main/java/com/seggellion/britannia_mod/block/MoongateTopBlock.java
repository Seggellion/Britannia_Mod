package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class MoongateTopBlock extends Block {
    public MoongateTopBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLUE)
                .strength(-1.0F, 3600000.0F) // Unbreakable and explosion-proof
                .noLootTable() // No drops when broken
                .noCollission() // Players can walk through
                .lightLevel((state) -> 15) // Emits maximum light
                .sound(SoundType.GLASS) // Sound type when interacted
        );
    }
}
