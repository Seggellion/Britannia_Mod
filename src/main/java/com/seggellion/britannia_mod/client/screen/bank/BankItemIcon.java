package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.service.banking.BankItemSummary;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Objects;

/**
 * Milestone 10: turns a stored item's {@code item_key} into the stack the grid draws.
 *
 * <p>This is the one place that decides what a malformed key means, and the answer is always the
 * same: <b>that one cell falls back to the unknown icon; nothing ever fails the grid</b> (design
 * §9.5.2). The parser and the wire deliberately pass the string through untouched so there is a
 * single definition of invalid rather than three drifting ones.
 *
 * <p>Icons render from the registry id alone -- design §9.5.3, decided and closed. A renamed,
 * enchanted or damaged sword draws as a plain sword of the right type; {@code displayName}
 * carries what makes it special, in text. The fallback for an unresolvable key is the barrier
 * icon: unmistakably "the client cannot show this", never mistakable for a real stored item.
 *
 * <p><b>Render data only.</b> The stack built here exists to be drawn (and, from Milestone 11,
 * hovered). It is never sent anywhere, never compared against anything authoritative, and
 * withdrawal continues to reference the public id (§9.5.4).
 */
public final class BankItemIcon {

    private BankItemIcon() {
    }

    /** True when {@code summary} will draw as a real item rather than the unknown fallback. */
    public static boolean resolves(BankItemSummary summary) {
        return resolveItem(summary) != null;
    }

    /**
     * The stack to draw for {@code summary} -- the resolved item carrying the stored count, or a
     * single barrier when the key is absent, unparseable, or names nothing in this client's
     * registry (an item from a mod this client does not have, or a future one).
     *
     * <p>The count is carried onto the stack so vanilla's decoration pass draws the same corner
     * numeral it draws in every inventory. It is display data from the summary, not a claim --
     * the same count {@code describe()} already prints in text.
     */
    public static ItemStack iconFor(BankItemSummary summary) {
        Objects.requireNonNull(summary, "summary");
        Item item = resolveItem(summary);
        if (item == null) {
            return new ItemStack(Items.BARRIER);
        }
        ItemStack stack = new ItemStack(item);
        if (summary.count() != null && summary.count() > 1) {
            stack.setCount(summary.count());
        }
        return stack;
    }

    private static Item resolveItem(BankItemSummary summary) {
        if (summary.itemKey() == null) return null;
        ResourceLocation key = ResourceLocation.tryParse(summary.itemKey());
        if (key == null) return null;
        // getOptional rather than get: an unregistered id must become the fallback, not the
        // registry's own AIR default -- an empty stack would draw as an empty cell, which reads
        // as "nothing stored here" and is precisely the misreading the identity program fixed.
        Item item = BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        return item == null || item == Items.AIR ? null : item;
    }
}
