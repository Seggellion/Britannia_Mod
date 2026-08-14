package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.ManagedFlowerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Visual host for managed wild flowers; it has no farming soil or ownership semantics. */
public final class ManagedFlowerBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 14.0D, 14.0D);

    public ManagedFlowerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos position, CollisionContext context
    ) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new ManagedFlowerBlockEntity(position, state);
    }
}
