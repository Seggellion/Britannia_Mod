package com.seggellion.britannia_mod.bank.item;

import net.minecraft.world.item.ItemStack;

/**
 * Whether two ItemStacks represent the same bankable value.
 *
 * <p>This is a thin wrapper around vanilla's own
 * {@link ItemStack#matches(ItemStack, ItemStack)} (same count, plus
 * {@link ItemStack#isSameItemSameComponents(ItemStack, ItemStack)} over the complete data
 * component map) -- there is no manually selected field list here, unlike the existing
 * bespoke per-item-type comparator in {@code ServerEconomyService.matchesRequest}. Vanilla's
 * {@code isSameItemSameComponents} already does genuine structural equality over the full
 * component patch (backed by {@code Map.equals}, which is order-independent), so it is the
 * correct general-purpose primitive to build on rather than reinvent.
 *
 * <p>This program's requirement is that two stacks are semantically equal if and only if
 * they produce the same {@link BankItemFingerprint#fingerprint}; the test suite proves that
 * agreement directly rather than assuming it.
 */
public final class BankItemEquality {
    private BankItemEquality() {
    }

    public static boolean semanticEquals(ItemStack a, ItemStack b) {
        return ItemStack.matches(a, b);
    }
}
