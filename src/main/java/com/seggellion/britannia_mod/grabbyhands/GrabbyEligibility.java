package com.seggellion.britannia_mod.grabbyhands;

import com.seggellion.britannia_mod.util.ModTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Type-level enrollment: may this <em>kind</em> of block participate in Grabby Hands at all?
 *
 * <p>Answering yes here is necessary but never sufficient. Whether one particular placed block may
 * actually be moved or destroyed is a separate question answered by {@link GrabbyInstanceState}
 * provenance and {@link GrabbyPolicy}. Keeping the two apart is what lets a {@code wooden_chair}
 * be player furniture in one place and permanent Britannia scenery in another.
 */
public final class GrabbyEligibility {
    private GrabbyEligibility() {
    }

    /** Whether this block type supports pickup and placement through Grabby Hands. */
    public static boolean movableType(@Nullable BlockState state) {
        return state != null && !deedPlaced(state) && state.is(ModTags.Blocks.GRABBY_MOVABLE);
    }

    /** Whether this block type may be destroyed by an axe through Grabby Hands. */
    public static boolean axeDestroyableType(@Nullable BlockState state) {
        return state != null && !deedPlaced(state) && state.is(ModTags.Blocks.GRABBY_AXE_DESTROYABLE);
    }

    /**
     * Whether this block is a house fixture that arrives with a deed.
     *
     * <p>Deliberately checked <em>before</em> the movable tag rather than alongside it, so the veto
     * wins even if the block is also listed as movable. Enrollment is a hand-edited tag file; the
     * rule should not depend on nobody ever making that mistake.
     */
    public static boolean deedPlaced(@Nullable BlockState state) {
        return state != null && state.is(ModTags.Blocks.GRABBY_DEED_PLACED);
    }

    /**
     * Whether this item may be set down in the world through the generic host.
     *
     * <p>Only for items with no block form. Anything that is already a {@code BlockItem} places as
     * its own block instead, so it is excluded here even if somebody adds it to the tag.
     */
    public static boolean hostablePlainItem(@Nullable ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && !(stack.getItem() instanceof net.minecraft.world.item.BlockItem)
                && stack.is(ModTags.Items.GRABBY_PLACEABLE_ITEMS);
    }

    /** Whether this block type participates in Grabby Hands in any capacity. */
    public static boolean enrolledType(@Nullable BlockState state) {
        return movableType(state) || axeDestroyableType(state);
    }
}
