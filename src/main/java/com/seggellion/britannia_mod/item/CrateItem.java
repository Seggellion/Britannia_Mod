package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.block.CrateBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Placement item for the crate family, where a crate top counts as support.
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@link DecorativeMultiblockItem} requires a sturdy upward face beneath the structure, which
 * {@code SupportType.FULL} only grants to a collision shape reaching the top of its block. Crates are
 * deliberately partial-height, so a crate can never support a crate under that rule and stacking
 * fails with no message. This is the same shape of exception {@code AdventureScarecrowItem} already
 * makes for community-farm soil.
 *
 * <p>The exception lives on the item rather than in the shared base class for two reasons: it must
 * not turn every partial-height block in the game into valid support, and both ordinary hand
 * placement and Grabby Hands run through this one placement path — so they cannot disagree about
 * whether a stack is legal.
 */
public final class CrateItem extends DecorativeMultiblockItem {
    public CrateItem(CrateBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean mayUseSupport(
            UseOnContext context, Player player, BlockPos supportPosition, ItemStack stack) {
        return isCrate(context.getLevel().getBlockState(supportPosition))
                || super.mayUseSupport(context, player, supportPosition, stack);
    }

    /**
     * Adopts the supporting crate's orientation so a stack reads as one object.
     *
     * <p>Falls back to the player's facing whenever the crate is not being stacked, which keeps
     * ordinary placement exactly as it was.
     */
    @Override
    protected Direction placementFacing(BlockPlaceContext context) {
        BlockState support = context.getLevel().getBlockState(context.getClickedPos().below());
        if (isCrate(support)) {
            return support.getValue(CrateBlock.FACING);
        }
        return super.placementFacing(context);
    }

    private static boolean isCrate(BlockState state) {
        return state.getBlock() instanceof CrateBlock crate && crate.hasValidPart(state);
    }
}
