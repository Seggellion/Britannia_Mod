package com.seggellion.britannia_mod.grabbyhands;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * A block entity that carries more state than its item form can express by default.
 *
 * <p>Wine solved this inside the wine block, because a bottle's extra state is one item. A container
 * cannot: twenty-seven stacks do not fit in a clone-stack. So the object writes its own state into the
 * portable item, and reads it back afterwards.
 *
 * <h2>Why this is not on the clone-stack path</h2>
 *
 * <p>{@code getCloneItemStack} is also what creative middle-click uses. If a chest's clone-stack
 * carried its contents, a creative player could duplicate an inventory by middle-clicking. Keeping
 * transport state on a separate interface leaves middle-click exactly as it was.
 *
 * <h2>Restoration is usually free</h2>
 *
 * <p>Implementations are encouraged to write into {@code DataComponents.BLOCK_ENTITY_DATA} via
 * {@code BlockItem.setBlockEntityData}, because {@code BlockItem.place} already restores that
 * component through {@code updateCustomBlockEntityTag}. Placement then needs no Grabby-specific step
 * at all, which is the same "let vanilla do it" principle the rest of this system runs on.
 *
 * <p>{@link #restorePortableState} therefore exists mainly for recovery: if removal fails after the
 * object has already given up its contents, the transaction puts them back.
 */
public interface GrabbyPortableState extends GrabbyDetachable {
    /**
     * Why this object refuses to be transported right now, if it does.
     *
     * <p>Checked before anything is captured or mutated, so a refusal costs nothing.
     */
    default Optional<GrabbyTransportRefusal> transportRefusal() {
        return Optional.empty();
    }

    /**
     * How many slots this object is holding something in, for a confirmation prompt to warn about.
     *
     * <p>Zero for anything that is not a container, which is most things.
     */
    default int occupiedSlotCount() {
        return 0;
    }

    /**
     * Whether this object's own security must not be circumvented by destroying it.
     *
     * <p>A locked chest is the case this exists for. Its lock is enforced by
     * {@code LockpickingEventHandler}, which wants a matching key or a successful skill check against
     * the chest's difficulty. Chopping the chest would spill the contents onto the floor with no key,
     * no lockpicks and no skill, which would make the entire lock system decorative.
     *
     * <p>Carrying a locked chest away is still allowed. The lock protects the <em>contents</em>, and a
     * thief who walks off with the box still cannot open it.
     */
    default boolean securedAgainstDestruction() {
        return false;
    }

    /** Writes everything this object must carry into the portable item. Must not mutate the object. */
    void writePortableState(ItemStack portable, HolderLookup.Provider registries);

    /** Reads state back out of a portable item. Used to undo a detach that could not be completed. */
    void restorePortableState(ItemStack portable, HolderLookup.Provider registries);
}
