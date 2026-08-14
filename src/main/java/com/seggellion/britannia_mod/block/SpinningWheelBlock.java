package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.textile.TextileProcessing;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One-cell placeholder spinning wheel with immediate one-input/one-output processing. */
public final class SpinningWheelBlock extends DecorativePropBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final int ACTIVE_TICKS = 20;

    public SpinningWheelBlock(Properties properties, VoxelShape northShape) {
        super(properties, northShape, true);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        ItemInteractionResult result = TextileProcessing.spin(level, pos, player, hand, stack);
        if (!level.isClientSide && result.consumesAction()) {
            level.setBlock(pos, state.setValue(ACTIVE, true), UPDATE_CLIENTS);
            level.scheduleTick(pos, this, ACTIVE_TICKS);
        }
        return result;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(ACTIVE)) {
            level.setBlock(pos, state.setValue(ACTIVE, false), UPDATE_CLIENTS);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }
}
