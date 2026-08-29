package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.CrateStackBlock;
import com.seggellion.britannia_mod.block.entity.CrateBlockEntity;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import com.seggellion.britannia_mod.crate.CrateFoundation;
import com.seggellion.britannia_mod.crate.CrateStackLayout;
import com.seggellion.britannia_mod.crate.CrateStackPlacement;
import com.seggellion.britannia_mod.crate.CrateStackTargetResolver;
import com.seggellion.britannia_mod.crate.CrateVariant;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

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
        BlockState targetState = level.getBlockState(target);
        if (CrateFoundation.isFoundation(targetState)) {
            return isCompactVariant()
                    ? ontoFoundation(context, level, target, targetState, player)
                    : largeOntoFoundation(context, level, target, targetState);
        }
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

    /**
     * Puts this crate on a large crate's lid, or on the column already standing there.
     *
     * <p>The large crate is a foundation, not a member: nothing is promoted and its inventory is never
     * touched. A first crate starts a new column two cells up whose origin is the lid's height; every
     * one after that is an ordinary append to that column, so the foundation offset keeps applying
     * without anything else having to know about it.
     */
    private InteractionResult ontoFoundation(
            UseOnContext context, ServerLevel level, BlockPos target, BlockState targetState,
            Player player) {

        Optional<CrateFoundation.Founded> standing =
                CrateFoundation.columnOn(level, target, targetState);
        if (standing.isPresent()) {
            CrateFoundation.Founded founded = standing.get();
            if (!CrateStackTargetResolver.isColumnTop(founded.stack(), founded.root(),
                    context.getClickedFace(), context.getClickLocation())) {
                return InteractionResult.PASS;
            }
            CrateStackPlacement.Result appended = CrateStackPlacement.place(
                    level, founded.root(), player, context.getItemInHand(),
                    context.getHorizontalDirection().getOpposite());
            return appended.succeeded() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }

        Optional<BlockPos> anchor = CrateFoundation.anchorAt(level, target, targetState);
        if (anchor.isEmpty() || !onLid(level, anchor.get(), targetState, context)) {
            return InteractionResult.PASS;
        }
        CrateStackPlacement.Result founded = CrateStackPlacement.placeOnFoundation(
                level, anchor.get(), player, context.getItemInHand());
        return founded.succeeded() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /**
     * Stands one large crate on another's lid.
     *
     * <p>The placement itself is the ordinary multiblock one: a click on the lid resolves to the cell
     * above it, which is the first level the upper crate is allowed to occupy, so the structure is
     * built exactly where it always would be. All that is added afterwards is the offset that says how
     * far below those cells the crate is actually drawn - which is the lid's height, the same origin a
     * compact column standing there would take.
     *
     * <p>Nothing is merged. Each crate keeps its own block entity and its own fifty-four slots; one of
     * them simply knows it is standing on the other.
     */
    private InteractionResult largeOntoFoundation(
            UseOnContext context, ServerLevel level, BlockPos target, BlockState targetState) {

        Optional<BlockPos> foundation = CrateFoundation.anchorAt(level, target, targetState);
        if (foundation.isEmpty() || !onLid(level, foundation.get(), targetState, context)) {
            return InteractionResult.PASS;
        }
        if (CrateFoundation.largeOn(level, target, targetState).isPresent()) {
            return InteractionResult.PASS;
        }
        InteractionResult placed = super.useOn(context);
        if (!placed.consumesAction()) {
            return placed;
        }
        BlockPos anchor = CrateFoundation.columnRootFor(foundation.get());
        if (level.getBlockEntity(anchor) instanceof CrateBlockEntity resting
                && level.getBlockState(anchor).getBlock() instanceof CrateBlock foundationCrate) {
            resting.setOriginHundredths(CrateFoundation.originFor(foundationCrate));
            // The crate is drawn by cells other than its own, so those have to be told as well.
            for (int cell = -1; cell <= 1; cell++) {
                BlockPos pos = anchor.above(cell);
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos),
                        Block.UPDATE_ALL);
            }
        }
        return placed;
    }

    /**
     * Whether a click landed on the foundation's actual lid rather than anywhere else on it.
     *
     * <p>A large crate presents several upward faces, and only the top of its lid is a place a crate
     * can rest. The height is measured against the crate's own authored top so the rule follows the
     * art rather than the two-block envelope the multiblock happens to occupy.
     */
    private static boolean onLid(
            ServerLevel level, BlockPos anchor, BlockState state, UseOnContext context) {
        if (!(state.getBlock() instanceof CrateBlock foundation)) {
            return false;
        }
        int height = (int) Math.round(
                (context.getClickLocation().y - anchor.getY())
                        * 16 * CrateStackLayout.HUNDREDTHS_PER_VOXEL);
        return Math.abs(height - CrateFoundation.topHundredths(foundation)) <= 1;
    }

    /**
     * A crate landing on another crate is this item's business, not Grabby's.
     *
     * <p>Grabby places on the sixteen-voxel grid: it works out the cell above the one clicked and puts
     * the structure there. That is the floating crate this line of work removed, and once a compact
     * column is standing in that cell it is occupied, so Grabby reads a real stacking request as a
     * failed placement and swallows it — which is how a crate clicked onto a large crate's lid stopped
     * reaching {@link #useOn} at all.
     *
     * <p>Saying no here is a statement about ownership rather than legality: the click still happens,
     * it simply reaches the crate's own placement path, which knows how to pack a column and where a
     * foundation's lid is. Every other target — ground, a table, anything that is not a crate — is
     * unaffected and still places through Grabby exactly as before.
     */
    @Override
    public boolean isPlacementGesture(BlockState clicked, BlockHitResult hit) {
        if (clicked.getBlock() instanceof CrateBlock || clicked.getBlock() instanceof CrateStackBlock) {
            return false;
        }
        return super.isPlacementGesture(clicked, hit);
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
        // The lid of a large crate holds up another large crate. A compact crate arriving here has
        // already been turned away by useOn, which packs those into a column instead.
        if (support.getBlock() instanceof CrateBlock lower
                && CrateFoundation.isLidCell(lower, support)) {
            return !isCompactVariant();
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
     * Puts a crate standing on a large crate's lid in the same place whichever part of the lid was
     * clicked.
     *
     * <p>Ordinary placement works outward from the cell the player hit, which is right when the
     * destination is a single block and wrong here: a large crate presents four cells of lid, so
     * clicking the far corner would build the crate above a corner rather than above the crate. The
     * anchor is taken from the crate underneath instead, so all four give one answer.
     */
    @Override
    public BlockPos grabbyPlacementRoot(BlockPlaceContext context) {
        if (context.getClickedFace() == Direction.UP && !isCompactVariant()) {
            BlockPos lid = context.getClickedPos().below();
            BlockState below = context.getLevel().getBlockState(lid);
            if (below.getBlock() instanceof CrateBlock lower
                    && CrateFoundation.isLidCell(lower, below)) {
                Optional<BlockPos> anchor =
                        CrateFoundation.anchorAt(context.getLevel(), lid, below);
                if (anchor.isPresent()) {
                    return CrateFoundation.columnRootFor(anchor.get());
                }
            }
        }
        return super.grabbyPlacementRoot(context);
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
