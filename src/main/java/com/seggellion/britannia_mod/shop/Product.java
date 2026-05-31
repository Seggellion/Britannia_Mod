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

    // --- 1. Master Public Constructor ---
    // (Merged the private and public versions here)
    public Product(String itemId, String name, int price, String currency, ItemStack stack, ResourceLocation iconPath) {
        this.itemId = itemId;
        this.name = name;
        this.price = price;
        this.currency = (currency == null || currency.isBlank()) ? "copper" : currency.toLowerCase();
        this.stack = stack.copy();
        this.iconPath = iconPath;
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
    
    // Legacy Getter support
    public String getItemId() { return itemId; }

    // --- Identity ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Product p
                && itemId.equals(p.itemId)
                && name.equals(p.name)
                && price == p.price
                && currency.equals(p.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, name, price, currency);
    }

    private static ItemStack makeStackFromId(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (item == Items.AIR) item = Items.BARRIER;
        return new ItemStack(item);
    }
}
