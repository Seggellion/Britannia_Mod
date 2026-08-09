package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.RotatedPillarBlock;

public class OrangeTreeBranchBlock extends RotatedPillarBlock {
    private final String treeTypeId;

    public OrangeTreeBranchBlock(Properties properties) {
        this("orange", properties);
    }

    public OrangeTreeBranchBlock(String treeTypeId, Properties properties) {
        super(properties);
        this.treeTypeId = treeTypeId;
    }

    public String treeTypeId() {
        return treeTypeId;
    }
}
