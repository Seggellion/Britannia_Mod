package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A window whose art is larger than the block it is placed in.
 *
 * <h2>The defect this fixes</h2>
 * {@code window_1x2}, {@code window_1x3}, {@code window_2x2}, {@code window_2x3} and the
 * {@code window_cross_*} models are authored two or three cells wide and two or three cells tall,
 * but they are rendered from one block position. A {@link net.minecraft.world.phys.shapes.VoxelShape}
 * cannot leave its own cell, so every cell of the art except the one the player clicked used to have
 * nothing behind it - a {@code window_2x2} presented four cells of frame and glass and collided in
 * one, and a player walked straight through the other three.
 *
 * <h2>How occupancy works</h2>
 * The block keeps rendering and colliding exactly as a {@link ThinWall} does in its own cell. Every
 * other cell of its {@link WindowFootprint} is backed by an invisible {@link WindowCollisionBlock}
 * carrying the same facing and the cell's {@link WindowCollisionSpan}, so each occupied cell
 * contributes the same edge slab the art draws there.
 *
 * <ul>
 *   <li><b>Placement</b> fills every footprint cell that is currently replaceable. Cells already
 *       holding something are left alone - that block brings its own collision, and stamping over a
 *       player's build to make room for an invisible helper would be worse than the hole it fills.
 *       Placement itself never fails, so putting one of these windows down is exactly as forgiving
 *       as it was before.</li>
 *   <li><b>Destruction</b> clears the helpers this window owns, unless another window still claims
 *       the cell.</li>
 *   <li><b>Repair</b> happens on any neighbour update, which is what backfills windows that were
 *       placed before this class existed and what recovers cells freed up after placement.</li>
 * </ul>
 */
public class MultiCellWindowBlock extends ThinWall {

    /**
     * How far a helper block has to look to find the window that owns it. Every footprint here
     * reaches one cell; {@code WindowCollisionContractTest} fails if one ever reaches further
     * without this growing to match.
     */
    public static final int SEARCH_RADIUS = 1;

    private final WindowFootprint footprint;

    public MultiCellWindowBlock(BlockBehaviour.Properties props, WindowFootprint footprint) {
        super(props);
        this.footprint = footprint;
    }

    public WindowFootprint footprint() {
        return this.footprint;
    }

    /* ─── occupancy ──────────────────────────────────────────── */

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            fillHelpers(level, pos, state);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            fillHelpers(level, pos, state);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            clearHelpers(level, pos, state);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private void fillHelpers(Level level, BlockPos origin, BlockState state) {
        Direction facing = state.getValue(FACING);
        for (WindowFootprint.Cell cell : this.footprint.cells()) {
            if (cell.isOrigin()) {
                continue;
            }
            BlockPos target = this.footprint.worldPos(origin, facing, cell);
            if (!level.isInWorldBounds(target)) {
                continue;
            }
            BlockState wanted = WindowCollisionBlock.stateFor(facing, cell.span());
            BlockState present = level.getBlockState(target);
            if (present == wanted) {
                continue;
            }
            // Only air and other replaceable blocks give way. Anything the player put there keeps
            // its place and its own collision.
            if (present.isAir() || present.canBeReplaced()) {
                level.setBlock(target, wanted, Block.UPDATE_CLIENTS);
            }
        }
    }

    private void clearHelpers(Level level, BlockPos origin, BlockState state) {
        Direction facing = state.getValue(FACING);
        for (WindowFootprint.Cell cell : this.footprint.cells()) {
            if (cell.isOrigin()) {
                continue;
            }
            BlockPos target = this.footprint.worldPos(origin, facing, cell);
            if (!(level.getBlockState(target).getBlock() instanceof WindowCollisionBlock)) {
                continue;
            }
            if (ownerOf(level, target, origin) != null) {
                continue; // A second window still needs this cell.
            }
            level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /* ─── ownership ──────────────────────────────────────────── */

    /**
     * The position of a window whose footprint covers {@code helperPos}, or {@code null} if the
     * helper has been orphaned. {@code ignore} lets a window that is in the middle of being removed
     * ask whether anybody <em>else</em> still claims one of its cells; pass {@code null} to search
     * for any owner at all.
     */
    public static BlockPos ownerOf(BlockGetter level, BlockPos helperPos, BlockPos ignore) {
        for (BlockPos candidate : BlockPos.betweenClosed(
                helperPos.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                helperPos.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS))) {
            if (candidate.equals(ignore)) {
                continue;
            }
            BlockState state = level.getBlockState(candidate);
            if (!(state.getBlock() instanceof MultiCellWindowBlock window)) {
                continue;
            }
            WindowFootprint.Cell cell = window.footprint.cellAt(
                candidate, state.getValue(FACING), helperPos);
            if (cell != null && !cell.isOrigin()) {
                return candidate.immutable();
            }
        }
        return null;
    }

}
