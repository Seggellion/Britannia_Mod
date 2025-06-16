package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

import java.util.HashMap;
import java.util.Map;

/**
 * Enum to unify metal types with their Tiers
 * so we can easily map "iron" -> IRON (enum), which holds a Tier
 */
public enum UOMetalToolMaterial {
    IRON("iron", new SimpleTier(
        BlockTags.MINEABLE_WITH_PICKAXE, 
        250,    // Durability
        6.0F,   // Mining Speed
        2.0F,   // Attack Damage Bonus
        14,     // Enchantment Value
        () -> Ingredient.of(ItemRegistry.GOLD_COIN.get())  // Replace with real iron ingot
    )),
    VALORITE("valorite", new SimpleTier(
        BlockTags.MINEABLE_WITH_PICKAXE,
        1200,  
        8.0F,  
        4.0F,  
        10,  
        () -> Ingredient.of(ItemRegistry.GOLD_COIN.get()) // Replace with real valorite ingot
    )),

        GOLD("gold", new SimpleTier(
        BlockTags.MINEABLE_WITH_PICKAXE,
        1200,  
        8.0F,  
        4.0F,  
        10,  
        () -> Ingredient.of(ItemRegistry.GOLD_COIN.get()) // Replace with real gold ingot
    ));

    private final String metalName;
    private final Tier tier;

    UOMetalToolMaterial(String metalName, Tier tier) {
        this.metalName = metalName;
        this.tier = tier;
    }

    public Tier getTier() {
        return this.tier;
    }

    public String getMetalName() {
        return this.metalName;
    }

    // --------------------------------------------------
    //  Static lookup: "iron" -> IRON, "valorite" -> VALORITE, etc.
    // --------------------------------------------------
    private static final Map<String, UOMetalToolMaterial> LOOKUP = new HashMap<>();

    static {
        for (UOMetalToolMaterial mat : UOMetalToolMaterial.values()) {
            LOOKUP.put(mat.metalName.toLowerCase(), mat);
        }
    }

    /**
     * Returns the enum constant for a given metal name, or null if not found.
     */
    public static UOMetalToolMaterial getMaterialByName(String name) {
        return LOOKUP.getOrDefault(name.toLowerCase(), null);
    }
}
