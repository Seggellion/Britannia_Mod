package com.seggellion.britannia_mod.grabbyhands;

import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Resolves the canonical position that owns a Grabby object's state.
 *
 * <p>Most enrolled blocks occupy exactly one position, and for those the canonical root is the
 * clicked position. The crate family is the exception: a {@link DecorativeMultiblockBlock} spreads
 * one logical object across several cells, and only its anchor carries the block entity — so the
 * inventory, the provenance stamp and the mutation claim all live there.
 *
 * <p>This is the seam the transactions were written against: they operate on "the root", never on
 * "the position the player clicked", so enrolling a multi-cell object changed this resolver and
 * nothing else. Note that the armoire family is <em>not</em> such a case: it is a single position
 * wearing an oversized voxel shape.
 */
public final class GrabbyRootResolver {
    private GrabbyRootResolver() {
    }

    /**
     * @return the position owning this object's canonical state
     */
    public static BlockPos resolveRoot(GrabbyWorld world, BlockPos clickedPos) {
        BlockState state = world.blockState(clickedPos);
        if (state != null
                && state.getBlock() instanceof DecorativeMultiblockBlock multiblock
                && multiblock.hasValidPart(state)) {
            // Any cell answers for the whole object, so a player may grab a crate by the corner they
            // can actually see rather than having to find its anchor.
            return multiblock.anchorPosition(clickedPos, state).immutable();
        }
        return clickedPos.immutable();
    }
}
