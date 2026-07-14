package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.block.entity.OrangeTreeRootBlockEntity;
import com.seggellion.britannia_mod.block.OrangeFruitBlock;
import com.seggellion.britannia_mod.block.OrangeTreeBranchBlock;
import com.seggellion.britannia_mod.block.OrangeTreeLeafBlock;
import com.seggellion.britannia_mod.block.OrangeTreeTrunkBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

public final class OrangeTreeUtils {
    private OrangeTreeUtils() {
    }

    public static Optional<OrangeTreeRootBlockEntity> findRoot(Level level, BlockPos pos) {
        BlockEntity direct = level.getBlockEntity(pos);
        if (direct instanceof OrangeTreeRootBlockEntity root) {
            return Optional.of(root);
        }

        BlockState clickedState = level.getBlockState(pos);
        if (!isOrangeTreeBody(clickedState)) {
            return Optional.empty();
        }

        int radius = 6;
        int height = 10;
        for (BlockPos candidate : BlockPos.betweenClosed(
                pos.offset(-radius, -height, -radius),
                pos.offset(radius, 0, radius))) {
            BlockEntity blockEntity = level.getBlockEntity(candidate);
            if (blockEntity instanceof OrangeTreeRootBlockEntity root) {
                OrangeTreeStructurePlanner.Plan plan = OrangeTreeStructurePlanner.plan(root.definition(), root.getTreeSeed(), root.definition().maxGrowthStep(), candidate);
                if (plan.owns(pos)) {
                    return Optional.of(root);
                }
            }
        }
        return Optional.empty();
    }

    public static boolean isOrangeTreeBody(BlockState state) {
        return state.getBlock() instanceof OrangeTreeBranchBlock
                || state.getBlock() instanceof OrangeTreeTrunkBlock
                || state.getBlock() instanceof OrangeTreeLeafBlock
                || state.getBlock() instanceof OrangeFruitBlock;
    }
}
