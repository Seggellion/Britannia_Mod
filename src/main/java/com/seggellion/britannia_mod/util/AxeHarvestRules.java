package com.seggellion.britannia_mod.util;

import com.seggellion.britannia_mod.block.OrangeFruitBlock;
import com.seggellion.britannia_mod.block.OrangeTreeBranchBlock;
import com.seggellion.britannia_mod.block.OrangeTreeLeafBlock;
import com.seggellion.britannia_mod.block.OrangeTreeRootBlock;
import com.seggellion.britannia_mod.block.OrangeTreeTrunkBlock;
import com.seggellion.britannia_mod.block.WeightedWoodBlock;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

public final class AxeHarvestRules {
    private AxeHarvestRules() {
    }

    public static boolean isAllowedAxeHarvestBlock(BlockState state) {
        return isAllowedLogBlock(state) || isAllowedLeafBlock(state) || isAllowedFruitBlock(state)
                || isFruitTreeBlock(state) || isAllowedAxeSeverablePlant(state);
    }

    /**
     * A standing plant an axe may cut down, such as a grape vine.
     *
     * <p>Deliberately outside {@link #isAllowedLogBlock} and {@link #isAllowedLeafBlock}: those two
     * are what the wood economy routes on, and a severable plant yields no wood, no sapling and no
     * tree karma. It is a separate question with a separate answer, asked through
     * {@code #britannia_mod:axe_severable_plants} so the membership is data rather than a class
     * check.
     */
    public static boolean isAllowedAxeSeverablePlant(BlockState state) {
        return state.is(ModTags.Blocks.AXE_SEVERABLE_PLANTS);
    }

    public static boolean isAllowedLogBlock(BlockState state) {
        return state.is(BlockTags.LOGS)
                || state.is(ModTags.Blocks.FRUIT_TREE_LOGS)
                || state.is(ModTags.Blocks.FRUIT_TREE_TRUNKS)
                || state.is(ModTags.Blocks.FRUIT_TREE_BRANCHES)
                || state.getBlock() instanceof WeightedWoodBlock
                || state.getBlock() instanceof OrangeTreeRootBlock
                || state.getBlock() instanceof OrangeTreeTrunkBlock
                || state.getBlock() instanceof OrangeTreeBranchBlock;
    }

    public static boolean isAllowedLeafBlock(BlockState state) {
        return state.is(BlockTags.LEAVES)
                || state.is(ModTags.Blocks.FRUIT_TREE_LEAVES)
                || state.getBlock() instanceof OrangeTreeLeafBlock;
    }

    public static boolean isAllowedFruitBlock(BlockState state) {
        return state.is(ModTags.Blocks.FRUIT_TREE_FRUITS)
                || state.getBlock() instanceof OrangeFruitBlock;
    }

    public static boolean isFruitTreeBlock(BlockState state) {
        return state.is(ModTags.Blocks.FRUIT_TREE_BLOCKS)
                || state.getBlock() instanceof OrangeTreeRootBlock
                || state.getBlock() instanceof OrangeTreeTrunkBlock
                || state.getBlock() instanceof OrangeTreeBranchBlock
                || state.getBlock() instanceof OrangeTreeLeafBlock
                || state.getBlock() instanceof OrangeFruitBlock;
    }
}
