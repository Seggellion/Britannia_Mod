package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.vegetation.ManagedVegetationService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Invisible in-world marker used only while a managed node is empty/regrowing. */
public final class ManagedVegetationControllerBlock extends Block {
    public ManagedVegetationControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos position, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, position, oldState, movedByPiston);
        if (!level.isClientSide && oldState.getBlock() != state.getBlock()
                && level instanceof ServerLevel serverLevel
                && !ManagedVegetationService.registerPlacedController(serverLevel, position)) {
            level.removeBlock(position, false);
        }
    }

    @Override
    protected VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos position, CollisionContext context
    ) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos position, CollisionContext context
    ) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos position) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos position) {
        return 1.0F;
    }
}
