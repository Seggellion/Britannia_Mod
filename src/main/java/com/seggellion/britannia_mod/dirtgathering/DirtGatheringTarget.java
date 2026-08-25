package com.seggellion.britannia_mod.dirtgathering;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Closed terrain rule for the renewable loose-dirt gathering interaction. */
public final class DirtGatheringTarget {
    private DirtGatheringTarget() {
    }

    public static boolean isGatherable(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT);
    }
}
