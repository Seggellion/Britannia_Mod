package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.util.ModTags;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

public class ModToolTiers {
    public static final Tier TWO_HANDED_AXE_TIER = new SimpleTier(
        ModTags.Blocks.LOGS, // Custom logs tag
        500, // Durability
        2.0F, // Mining speed multiplier
        7.0F, // Attack damage bonus
        15, // Enchantment value
        () -> Ingredient.of(ItemRegistry.GOLD_COIN.get()) // Repair material
    );
}
