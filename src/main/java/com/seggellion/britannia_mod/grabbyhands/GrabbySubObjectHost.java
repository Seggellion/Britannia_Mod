package com.seggellion.britannia_mod.grabbyhands;

import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

/**
 * A block entity holding several objects a player sees separately.
 *
 * <h2>Why Grabby needs to be told</h2>
 *
 * <p>Grabby has always addressed one object per position: resolve the root, take what is there. A
 * compact crate column breaks that assumption without breaking the world's - three crates a player
 * can point at individually, one block entity, one position. Picking the position up would take all
 * three, which is not what anyone aiming at the middle one meant.
 *
 * <p>So a host that holds several objects says so, and hands over exactly one when asked. Everything
 * outside this interface stays as it was: an ordinary enrolled block has no sub-objects, is never
 * asked, and Grabby treats it exactly as before.
 *
 * <p>Transport, not destruction. Taking a sub-object moves it into the carried item and nothing is
 * dropped, spilled or duplicated - the contents exist in exactly one place at every moment.
 */
public interface GrabbySubObjectHost {

    /**
     * Why this particular sub-object refuses to be carried right now, if it does.
     *
     * <p>Scoped to the one object rather than the host: someone reading the top crate of a column is
     * no reason the bottom one cannot be moved.
     */
    Optional<GrabbyTransportRefusal> subObjectRefusal(int subObjectId);

    /**
     * The item one sub-object would be carried as, without taking it.
     *
     * <p>So a player with no room to hold it is turned away before it leaves the world, rather than
     * after.
     */
    Optional<ItemStack> previewSubObject(int subObjectId, HolderLookup.Provider registries);

    /**
     * Removes one sub-object and returns the item carrying it, contents and all.
     *
     * <p>Returns empty if the sub-object has already gone - which is an ordinary thing to find, not an
     * error, because two players can reach for the same crate. Whoever gets there first takes it and
     * the other finds nothing, rather than both being handed a copy.
     */
    Optional<ItemStack> takeSubObject(int subObjectId, HolderLookup.Provider registries);
}
