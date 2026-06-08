package com.seggellion.britannia_mod.util;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

public final class AxeHarvestRules {
    private AxeHarvestRules() {}

    public static boolean isAllowedAxeHarvestBlock(BlockState state) {
        return isAllowedLogBlock(state) || isAllowedLeafBlock(state);
    }

    public static boolean isAllowedLogBlock(BlockState state) {
        return state.is(BlockTags.LOGS);
    }

    public static boolean isAllowedLeafBlock(BlockState state) {
        return state.is(BlockTags.LEAVES);
    }
}
