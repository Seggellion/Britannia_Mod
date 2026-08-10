package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.TrainingDummyBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Two-wide, three-high atomic multiblock whose root owns the synchronized animation. */
public final class TrainingDummyBlock extends DecorativeMultiblockBlock implements EntityBlock {
    public TrainingDummyBlock(Properties properties) {
        super(properties, 0, 1, 0, 2, 0, 0, TrainingDummyBlock::cellShape);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isRoot(state) ? new TrainingDummyBlockEntity(pos, state) : null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return isRoot(state) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    public boolean triggerHit(ServerLevel level, BlockPos struckPosition, BlockState struckState) {
        if (!hasValidPart(struckState)) {
            return false;
        }
        BlockPos anchor = anchorPosition(struckPosition, struckState);
        if (level.getBlockEntity(anchor) instanceof TrainingDummyBlockEntity dummy) {
            dummy.triggerHitAnimation();
            return true;
        }
        return false;
    }

    private static VoxelShape cellShape(int x, int y, int z) {
        if (y == 0) {
            return Shapes.or(
                    Block.box(2, 0, 4, 14, 16, 12),
                    Block.box(x == 0 ? 8 : 0, 0, 6, x == 0 ? 16 : 8, 5, 10));
        }
        if (y == 1) {
            return Block.box(0, 0, 4, 16, 16, 12);
        }
        return Shapes.or(
                Block.box(0, 0, 6, 16, 12, 10),
                Block.box(x == 0 ? 7 : 0, 12, 6, x == 0 ? 16 : 9, 16, 10));
    }
}
