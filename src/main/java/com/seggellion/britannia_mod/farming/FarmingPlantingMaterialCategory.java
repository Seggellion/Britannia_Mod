package com.seggellion.britannia_mod.farming;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Approved unidentified-name category for a resolved planting item. */
public enum FarmingPlantingMaterialCategory {
    NOT_APPLICABLE(null),
    SEEDS("item.britannia_mod.unidentified_seeds"),
    FLOWER_SEEDS("item.britannia_mod.unidentified_flower_seeds"),
    PLANTING_MATERIAL("item.britannia_mod.unidentified_planting_material"),
    GRAPES("item.britannia_mod.unidentified_grape_seed"),
    NATIVE_VANILLA_OUTSIDE_SCOPE(null);

    private final String unidentifiedNameKey;

    FarmingPlantingMaterialCategory(String unidentifiedNameKey) {
        this.unidentifiedNameKey = unidentifiedNameKey;
    }

    public boolean hasUnidentifiedPresentation() {
        return unidentifiedNameKey != null;
    }

    public String unidentifiedNameKey() {
        if (unidentifiedNameKey == null) {
            throw new IllegalStateException(name() + " has no unidentified presentation");
        }
        return unidentifiedNameKey;
    }

    public static FarmingPlantingMaterialCategory resolve(
            FarmingSkillRequirementResolver.ResolvedRequirement resolved,
            Item plantingItem
    ) {
        if ("grapes".equals(resolved.speciesId())) {
            return GRAPES;
        }
        if (isNativeVanillaCompatibilityItem(plantingItem)) {
            return NATIVE_VANILLA_OUTSIDE_SCOPE;
        }
        if (resolved.requirement() instanceof FlowerDefinition) {
            return FLOWER_SEEDS;
        }
        return SEEDS;
    }

    public static boolean isNativeVanillaCompatibilityItem(Item item) {
        return item == Items.POTATO
                || item == Items.WHEAT_SEEDS
                || item == Items.BROWN_MUSHROOM
                || item == Items.RED_MUSHROOM
                || item == Items.PUMPKIN_SEEDS
                || item == Items.MELON_SEEDS;
    }
}
