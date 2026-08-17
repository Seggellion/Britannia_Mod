package com.seggellion.britannia_mod.economy;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The invariant the whole buyback quote rests on: every item inside one ItemStack is
 * economically identical.
 *
 * <p>Rails prices a weight-canonical row as {@code unit_price × delta}, where delta is the
 * stack's TOTAL weight. Deriving a per-item value as {@code line_total / quantity} is therefore
 * exact only if every item in that stack carries the same weight — and it does, because a stack
 * only merges when its data components match, and the weight lives in {@code CUSTOM_DATA}.
 * Two differently-weighted fish are two stacks, never one stack of two.
 *
 * <p>If this ever stops holding, a per-item price derived from an aggregate total becomes a
 * fiction and the client must stop deriving one — hence pinning it here rather than trusting it.
 */
class WeightedStackEconomicIdentityTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ItemStack weighted(double weight) {
        ItemStack stack = new ItemStack(Items.COD);
        CompoundTag tag = new CompoundTag();
        tag.putDouble("FishWeight", weight);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    @Test
    void differentlyWeightedItemsCannotShareAStack() {
        assertFalse(ItemStack.isSameItemSameComponents(weighted(1.5D), weighted(2.5D)),
                "two weights merged into one stack; a per-item price derived from an aggregate "
                        + "line_total would then be a fiction");
    }

    @Test
    void identicallyWeightedItemsMayShareAStackAndStayUniform() {
        assertTrue(ItemStack.isSameItemSameComponents(weighted(1.5D), weighted(1.5D)));
        // Which is the case that makes the derivation exact: a stack of 4 at 1.5 stones has a
        // total delta of 6.0, and line_total / quantity recovers the per-item value precisely.
        ItemStack stack = weighted(1.5D);
        stack.setCount(4);
        assertTrue(stack.getCount() * 1.5D == 6.0D);
    }

    /**
     * Untagged fish (creative, {@code /give}) carry no CUSTOM_DATA at all, so they stack freely
     * with each other — and are still uniform, which is what matters. They must not stack with
     * a tagged fish, or a weightless item would inherit a caught fish's price.
     */
    @Test
    void untaggedItemsStackWithEachOtherButNotWithTaggedOnes() {
        assertTrue(ItemStack.isSameItemSameComponents(new ItemStack(Items.COD), new ItemStack(Items.COD)));
        assertFalse(ItemStack.isSameItemSameComponents(new ItemStack(Items.COD), weighted(1.5D)));
    }
}
