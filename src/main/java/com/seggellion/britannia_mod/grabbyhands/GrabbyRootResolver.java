package com.seggellion.britannia_mod.grabbyhands;

import net.minecraft.core.BlockPos;

/**
 * Resolves the canonical position that owns a Grabby object's state.
 *
 * <p>Every block in the current enrollment set occupies exactly one position, so today the canonical
 * root is the clicked position and this resolver is an identity function.
 *
 * <p>It exists anyway because the transaction must be written against "the root", not "the position
 * the player clicked". When a genuinely multi-block object is enrolled, that logic lands here and the
 * transaction does not change. Note that the armoire family is <em>not</em> such a case: it is a
 * single position wearing an oversized voxel shape.
 *
 * <p>Deliberately not speculative — there is no footprint or anchor machinery here, because no
 * enrolled content has a footprint. Inventing it now would mean shipping untested code paths.
 */
public final class GrabbyRootResolver {
    private GrabbyRootResolver() {
    }

    /**
     * @return the position owning this object's canonical state
     */
    public static BlockPos resolveRoot(GrabbyWorld world, BlockPos clickedPos) {
        return clickedPos.immutable();
    }
}
