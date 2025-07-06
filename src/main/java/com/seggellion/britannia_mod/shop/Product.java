package com.seggellion.britannia_mod.shop;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Immutable value-object representing a single item the Architect
 * can sell.  Equality is based solely on the registry id so the
 * cart Map in ArchitectScreen works as intended.
 */
public final class Product {

    private final String itemId;
    private final String name;
    private final int price;
    private final ItemStack stack;
    private final ResourceLocation iconPath;

    // Main private constructor used by all others
    private Product(String itemId, String name, int price, ItemStack stack, ResourceLocation iconPath) {
        this.itemId = itemId;
        this.name = name;
        this.price = price;
        this.stack = stack.copy();
        this.iconPath = iconPath;
    }

    public Product(String itemId, String name, int price) {
        this(itemId, name, price, makeStackFromId(itemId), null);
    }

    public Product(String itemId, String name, int price, ItemStack stack) {
        this(itemId, name, price, stack, null);
    }

    public Product(String itemId, String name, int price, ResourceLocation iconPath) {
        this(itemId, name, price, makeStackFromId(itemId), iconPath);
    }

    public String itemId()     { return itemId; }
    public String name()       { return name; }
    public int    price()      { return price; }
    public ItemStack stack()   { return stack; }
    public ResourceLocation iconPath() { return iconPath; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Product p && itemId.equals(p.itemId);
    }

    @Override
    public int hashCode() {
        return itemId.hashCode();
    }

    private static ItemStack makeStackFromId(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (item == Items.AIR) item = Items.BARRIER;
        return new ItemStack(item);
    }
}
