package com.seggellion.britannia_mod.grabbyhands;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;

/**
 * An item whose placement does not land a single block at the clicked position.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Grabby Hands places by building a plain {@code BlockPlaceContext} and calling
 * {@code BlockItem.place} directly, which is the right answer for the ordinary furniture that made up
 * the original enrollment set: one item, one block, at the position the context resolved.
 *
 * <p>It is the wrong answer for an item that overrides {@code useOn} to do something else — a crate
 * builds a whole cell structure transactionally, and a lifted item shifts its target upward. Calling
 * {@code place} on either would bypass that logic and put a single block somewhere the transaction
 * did not intend, silently, because the block really would be where the transaction then looked
 * for it.
 *
 * <p>Implementing this interface is the item's statement that it owns its own placement and can say
 * where the resulting object's canonical root will be. Grabby then runs the item's real placement
 * path and records that root, which is where {@link GrabbyProvenanceHolder} lives and what
 * {@link GrabbyRootResolver} will resolve back to on pickup.
 */
public interface GrabbyStructurePlacementItem {
    /**
     * The canonical root the placed object will occupy for this context.
     *
     * <p>Computed from the context alone, without mutating the world, so a caller can learn the
     * target before deciding whether to place.
     *
     * @return the position that will own the placed object's block entity and provenance
     */
    BlockPos grabbyPlacementRoot(BlockPlaceContext context);
}
