package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.textile.TextileProcessing;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One-cell placeholder spinning wheel with immediate one-input/one-output processing. */
public final class SpinningWheelBlock extends DecorativePropBlock {
    public SpinningWheelBlock(Properties properties, VoxelShape northShape) {
        super(properties, northShape, true);
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
        return TextileProcessing.spin(level, pos, player, hand, stack);
    }
}
