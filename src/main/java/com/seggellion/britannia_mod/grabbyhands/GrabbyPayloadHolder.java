package com.seggellion.britannia_mod.grabbyhands;

import net.minecraft.world.item.ItemStack;

/**
 * A block entity that holds an item Grabby Hands is responsible for, and that would otherwise drop
 * that item from its own removal path.
 *
 * <p>This exists to close a duplication hazard rather than to model anything conceptual. The placed
 * item host drops its payload when something <em>else</em> destroys it — an explosion, an admin in
 * Creative — because otherwise the item would silently vanish. But a Grabby pickup also removes the
 * block, and if the payload were still attached the player would get the item twice: once in the
 * inventory, once on the floor.
 *
 * <p>The transaction therefore detaches the payload immediately before removal, once every check has
 * already passed. See {@link GrabbyWorld#detachPayloadBeforeRemoval}.
 *
 * <p>Containers face the same hazard in a larger form — {@code BritanniaChestBlock.onRemove} spills a
 * whole inventory — which is why this seam is generic rather than specific to the host.
 */
public interface GrabbyPayloadHolder extends GrabbyDetachable {
    /** The stored item. Never null; {@link ItemStack#EMPTY} once detached. */
    ItemStack grabbyPayload();

    void setGrabbyPayload(ItemStack payload);

    /**
     * Gives up the payload so the block's own removal path has nothing left to drop.
     *
     * <p>Called only after the transaction has committed to removing the object.
     */
    void detachGrabbyPayload();

    @Override
    default boolean detachForTransport() {
        if (grabbyPayload().isEmpty()) {
            return false;
        }
        detachGrabbyPayload();
        return true;
    }
}
