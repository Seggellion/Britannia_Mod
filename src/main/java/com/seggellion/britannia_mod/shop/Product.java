package com.seggellion.britannia_mod.shop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Objects;

/**
 * Immutable value-object representing a single item the Architect/Trader
 * can sell. Now supports specific currencies (gold/silver/copper).
 */
public final class Product {

    private final String itemId;
    private final String name;
    private final int price;
    private final String currency;
    private final ItemStack stack;
    private final ResourceLocation iconPath;
    private final long lineValueCopper;
    private final int quotedQuantity;

    // --- 1. Master Public Constructor ---
    // (Merged the private and public versions here)
    public Product(String itemId, String name, int price, String currency, ItemStack stack, ResourceLocation iconPath) {
        this(itemId, name, price, currency, stack, iconPath, 0L, 0);
    }

    /**
     * A row a server priced, carrying the exact value behind the rounded display price.
     *
     * <p>{@code price} is a per-item coin figure and cannot express a row worth 2.5 silver, so a
     * cart totalled from it drifts. {@code lineValueCopper} is the row's whole value in
     * hundredths of a copper -- the canonical unit Rails values in -- and {@code quotedQuantity}
     * the item count it covers, which together let the cart be totalled and rounded exactly once,
     * the way the settling authority does it.
     */
    public Product(String itemId, String name, int price, String currency, ItemStack stack,
                   ResourceLocation iconPath, long lineValueCopper, int quotedQuantity) {
        this.itemId = itemId;
        this.name = name;
        this.price = price;
        this.currency = (currency == null || currency.isBlank()) ? "copper" : currency.toLowerCase();
        this.stack = stack.copy();
        this.iconPath = iconPath;
        this.lineValueCopper = lineValueCopper;
        this.quotedQuantity = quotedQuantity;
    }

    // --- Auxiliary Constructors ---

    // 2. Simple constructor (Defaults to Copper)
    public Product(String itemId, String name, int price) {
        this(itemId, name, price, "copper", makeStackFromId(itemId), null);
    }

    // 3. Stack constructor (Used when you have a specific ItemStack, e.g. from Inventory)
    public Product(String itemId, String name, int price, String currency, ItemStack stack) {
        this(itemId, name, price, currency, stack, null);
    }

    // 4. Icon constructor
    public Product(String itemId, String name, int price, String currency, ResourceLocation iconPath) {
        this(itemId, name, price, currency, makeStackFromId(itemId), iconPath);
    }

    // --- Getters ---

    public String itemId()         { return itemId; }
    public String name()           { return name; }
    public int    price()          { return price; }
    public String currency()       { return currency; }
    public ItemStack stack()       { return stack; }
    public ResourceLocation iconPath() { return iconPath; }

    /** The row's total value in hundredths of a copper, or 0 when no server priced it. */
    public long lineValueCopper() { return lineValueCopper; }

    /** The item count {@link #lineValueCopper} covers, or 0 when no server priced it. */
    public int quotedQuantity()  { return quotedQuantity; }

    /**
     * Whether a server priced this row exactly. Merchant retail and legacy trader rows carry only
     * a per-item coin price, and must keep being totalled the old way.
     */
    public boolean isServerPriced() { return quotedQuantity > 0; }

    /**
     * The exact value of {@code count} of this row, in hundredths of a copper.
     *
     * <p>Dividing the line by its quantity is exact rather than approximate: a stack only merges
     * when its data components match, so every item inside one carries the same weight and the
     * same share of the line. See {@code WeightedStackEconomicIdentityTest}.
     */
    public long valueCopperFor(int count) {
        if (!isServerPriced() || count <= 0) return 0L;
        return Math.round((double) lineValueCopper * count / quotedQuantity);
    }

    // Legacy Getter support
    public String getItemId() { return itemId; }

    // --- Identity ---

    /**
     * Identity includes the exact value, not just the rounded display price.
     *
     * <p>Two stacks of the same fish at different weights can round to the same whole-coin
     * price while being worth different amounts. Without the value in the identity they compare
     * equal, and the cart -- a map keyed by Product -- silently folds one into the other, so the
     * player sells a fish at another fish's rate.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Product p
                && itemId.equals(p.itemId)
                && name.equals(p.name)
                && price == p.price
                && currency.equals(p.currency)
                && lineValueCopper == p.lineValueCopper
                && quotedQuantity == p.quotedQuantity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, name, price, currency, lineValueCopper, quotedQuantity);
    }

    private static ItemStack makeStackFromId(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (item == Items.AIR) item = Items.BARRIER;
        return new ItemStack(item);
    }
}
