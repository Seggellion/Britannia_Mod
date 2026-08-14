package com.seggellion.britannia_mod.grabbyhands;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The one place Grabby Hands decides whether something counts as an axe.
 *
 * <h2>Why the {@code instanceof} arm is not redundant</h2>
 *
 * <p>{@code britannia_mod:two_handed_axe} — the mod's only registered axe — is <em>not</em> in
 * {@link ItemTags#AXES}. The mod ships no {@code data/minecraft/tags/} directory at all, so nothing
 * adds it. A check written as {@code stack.is(ItemTags.AXES)} alone would happily accept every
 * vanilla axe while silently rejecting the axe UltimaCraft players actually carry.
 *
 * <p>{@code TwoHandedAxeItem extends AxeItem}, so the {@code instanceof} arm catches it and any
 * future axe subclass without anyone having to remember to update a tag file.
 *
 * <h2>Scope</h2>
 *
 * <p>This classifier is used only by Grabby Hands' own right-click handler. It grants no breaking
 * authority: Grabby Hands never touches {@code BreakEvent}, {@code PlayerEvent.BreakSpeed},
 * {@code AxeHarvestRules}, or {@code blockActionRestricted}. What an axe may break elsewhere in the
 * mod is entirely unchanged by this class.
 */
public final class GrabbyAxes {
    private GrabbyAxes() {
    }

    /** Whether this stack is an axe for Grabby Hands purposes. */
    public static boolean isAxe(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.getItem() instanceof AxeItem || stack.is(ItemTags.AXES);
    }
}
