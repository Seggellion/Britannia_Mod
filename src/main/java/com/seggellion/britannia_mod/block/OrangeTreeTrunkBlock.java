package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.RotatedPillarBlock;

public class OrangeTreeTrunkBlock extends RotatedPillarBlock {
    private final String treeTypeId;

    public OrangeTreeTrunkBlock(Properties properties) {
        this("orange", properties);
    }

    public OrangeTreeTrunkBlock(String treeTypeId, Properties properties) {
        super(properties);
        this.treeTypeId = treeTypeId;
    }

    public String treeTypeId() {
        return treeTypeId;
    }
}
