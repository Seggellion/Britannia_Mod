package com.seggellion.britannia_mod.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;
import com.seggellion.britannia_mod.registry.ItemRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public enum UOMetalToolMaterial {
    IRON("iron", () -> Items.IRON_INGOT, new SimpleTier(
        BlockTags.MINEABLE_WITH_PICKAXE, 250, 6.0F, 2.0F, 14, 
        () -> Ingredient.of(Items.IRON_INGOT)
    )),
    GOLD("gold", () -> Items.GOLD_INGOT, new SimpleTier(
        BlockTags.MINEABLE_WITH_PICKAXE, 32, 12.0F, 0.0F, 22, 
        () -> Ingredient.of(Items.GOLD_INGOT)
    )),
    SHADOW_IRON("shadow iron", ItemRegistry.SHADOW_IRON_INGOT, new SimpleTier(
        BlockTags.MINEABLE_WITH_PICKAXE, 400, 6.5F, 2.5F, 14, 
        () -> Ingredient.of(ItemRegistry.SHADOW_IRON_INGOT.get())
    )),
    COPPER("copper", ItemRegistry.COPPER_INGOT, new SimpleTier(
        BlockTags.MINEABLE_WITH_PICKAXE, 175, 5.0F, 1.5F, 10, 
        () -> Ingredient.of(ItemRegistry.COPPER_INGOT.get())
    )),
    TIN("tin", ItemRegistry.TIN_INGOT, new SimpleTier(
        BlockTags.MINEABLE_WITH_PICKAXE, 150, 4.5F, 1.0F, 8, 
        () -> Ingredient.of(ItemRegistry.TIN_INGOT.get())
    )),
    SILVER("silver", ItemRegistry.SILVER_INGOT, new SimpleTier(
        BlockTags.MINEABLE_WITH_PICKAXE, 100, 8.0F, 1.5F, 20, 
        () -> Ingredient.of(ItemRegistry.SILVER_INGOT.get())
    )),
    AGAPITE("agapite", ItemRegistry.AGAPITE_INGOT, new SimpleTier(
        BlockTags.MINEABLE_WITH_PICKAXE, 800, 7.0F, 3.0F, 12, 
        () -> Ingredient.of(ItemRegistry.AGAPITE_INGOT.get())
    )),
    VERITE("verite", ItemRegistry.VERITE_INGOT, new SimpleTier(
        BlockTags.MINEABLE_WITH_PICKAXE, 1000, 7.5F, 3.5F, 11, 
        () -> Ingredient.of(ItemRegistry.VERITE_INGOT.get())
    )),
    VALORITE("valorite", ItemRegistry.VALORITE_INGOT, new SimpleTier(
        BlockTags.MINEABLE_WITH_PICKAXE, 1200, 8.0F, 4.0F, 10, 
        () -> Ingredient.of(ItemRegistry.VALORITE_INGOT.get())
    ));

    private final String metalName;
    private final Supplier<Item> ingotSupplier;
    private final Tier tier;

    UOMetalToolMaterial(String metalName, Supplier<Item> ingotSupplier, Tier tier) {
        this.metalName = metalName;
        this.ingotSupplier = ingotSupplier;
        this.tier = tier;
    }

    public String getMetalName() { return this.metalName; }
    public Supplier<Item> getIngotSupplier() { return this.ingotSupplier; }
    public Tier getTier() { return this.tier; }

    // --- Lookups ---
    private static final Map<String, UOMetalToolMaterial> NAME_LOOKUP = new HashMap<>();

    static {
        for (UOMetalToolMaterial mat : UOMetalToolMaterial.values()) {
            NAME_LOOKUP.put(mat.metalName.toLowerCase(), mat);
        }
    }

    public static UOMetalToolMaterial getMaterialByName(String name) {
        return NAME_LOOKUP.getOrDefault(name.toLowerCase(), null);
    }

    /**
     * Finds the matching metal enum based on the ingot item held in the player's hand.
     */
    public static UOMetalToolMaterial getMaterialByIngot(Item item) {
        for (UOMetalToolMaterial mat : UOMetalToolMaterial.values()) {
            if (mat.getIngotSupplier().get() == item) {
                return mat;
            }
        }
        return null;
    }
}