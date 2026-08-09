package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.Block;

public class OrangeTreeLeafBlock extends Block {
    private final String treeTypeId;

    public OrangeTreeLeafBlock(Properties properties) {
        this("orange", properties);
    }

    public OrangeTreeLeafBlock(String treeTypeId, Properties properties) {
        super(properties);
        this.treeTypeId = treeTypeId;
    }

    public String treeTypeId() {
        return treeTypeId;
    }
}
