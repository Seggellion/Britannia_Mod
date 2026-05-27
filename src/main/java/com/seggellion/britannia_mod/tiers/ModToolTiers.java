package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

public class ModToolTiers {

    public static final Tier TWO_HANDED_AXE_TIER = new SimpleTier(
            BlockTags.INCORRECT_FOR_IRON_TOOL,
            500,
            2.0F,
            7.0F,
            15,
            () -> Ingredient.of(ItemRegistry.GOLD_COIN.get())
    );

    public static final Tier PICKAXE_TIER = new SimpleTier(
            BlockTags.INCORRECT_FOR_IRON_TOOL,
            500,
            2.0F,
            7.0F,
            15,
            () -> Ingredient.of(ItemRegistry.GOLD_COIN.get())
    );
}