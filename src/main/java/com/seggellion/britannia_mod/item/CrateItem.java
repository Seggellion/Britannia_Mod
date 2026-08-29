package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.CrateStackBlock;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import com.seggellion.britannia_mod.crate.CrateStackPlacement;
import com.seggellion.britannia_mod.crate.CrateStackTargetResolver;
import com.seggellion.britannia_mod.crate.CrateVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
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

    /**
     * Stacks compactly onto a crate or a column, instead of leaving one floating above it.
     *
     * <p>This is where crate-on-crate placement stopped being one block per position. A supported
     * crate aimed at the top of another supported crate, or at the top of a column, becomes another
     * logical crate inside a single compact column — packed against the one below it rather than
     * sitting on Minecraft's sixteen-voxel grid.
     *
     * <p>The interception is deliberately narrow: both the held crate and the target have to be ones
     * compact columns carry. A large crate held over a small one, or a small crate held over a large
     * one, falls through to the ordinary structure placement below and behaves exactly as it did
     * before, because the large crate is a 2x2x2 multiblock that columns do not take.
     *
     * <p>A refused compact placement is a refusal, not a reason to fall back — falling back would put
     * the floating crate this milestone exists to remove right back into the world.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (context.getClickedFace() != Direction.UP
                || player == null
                || !(context.getLevel() instanceof ServerLevel level)) {
            return super.useOn(context);
        }
        BlockPos target = context.getClickedPos();
        if (!CrateStackPlacement.isCompactTarget(level, target)
                || !isCompactVariant()) {
            return super.useOn(context);
        }
        // A column only accepts a crate on its exposed lid; every other face is an interaction.
        if (level.getBlockEntity(CrateStackBlock.rootOf(target, level.getBlockState(target)))
                        instanceof CrateStackBlockEntity column
                && !CrateStackTargetResolver.isColumnTop(
                        column,
                        CrateStackBlock.rootOf(target, level.getBlockState(target)),
                        context.getClickedFace(),
                        context.getClickLocation())) {
            return InteractionResult.PASS;
        }

        CrateStackPlacement.Result result = CrateStackPlacement.place(
                level, target, player, context.getItemInHand(),
                context.getHorizontalDirection().getOpposite());
        return result.succeeded() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /** Whether this item's own crate is one a compact column carries. */
    private boolean isCompactVariant() {
        return getBlock() instanceof CrateBlock crate
                && crate.cells().size() == 1
                && CrateVariant.forSlotCount(crate.slotCount()).isPresent();
    }

    /**
     * A crate holds up a crate only when both are ones a compact column carries.
     *
     * <h2>Why the other combinations are refused rather than allowed</h2>
     *
     * <p>Ordinary placement puts a structure on the sixteen-voxel grid, one block above the block it
     * was dropped on. That is exactly right when the thing underneath fills its block, and wrong for
     * every crate, because no crate does. A compact pair never reaches this method — {@link #useOn}
     * takes those clicks and packs them into a column instead — so anything arriving here is a
     * combination columns do not carry, and the large crate is all of them: its art stops nineteen
     * voxels up inside a two-block-tall structure, so a crate placed on the grid above it hangs
     * thirteen voxels clear of the lid with daylight in between.
     *
     * <p>Refusing costs the player nothing. The support check runs before a single cell is written or
     * an item consumed, so a refused click leaves the world and the hand exactly as they were, and the
     * crate underneath still opens on any other face. A visibly wrong placement is worse than none:
     * the player can see the gap, and nothing about it suggests the game meant it.
     */
    @Override
    protected boolean mayUseSupport(
            UseOnContext context, Player player, BlockPos supportPosition, ItemStack stack) {
        BlockState support = context.getLevel().getBlockState(supportPosition);
        if (isCompactSupport(support)) {
            return isCompactVariant();
        }
        if (isCrate(support)) {
            return false;
        }
        return super.mayUseSupport(context, player, supportPosition, stack);
    }

    /** Whether this is a crate a column packs against, rather than one it leaves on the grid. */
    private static boolean isCompactSupport(BlockState state) {
        if (state.getBlock() instanceof CrateStackBlock) {
            return true;
        }
        return state.getBlock() instanceof CrateBlock crate
                && crate.hasValidPart(state)
                && crate.cells().size() == 1
                && CrateVariant.forSlotCount(crate.slotCount()).isPresent();
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
