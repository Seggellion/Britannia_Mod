package com.seggellion.britannia_mod.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.neoforged.neoforge.common.extensions.IItemExtension;

import java.util.List;

/**
 * A sword item whose “quality” is determined at creation (e.g., an NPC's skill).
 * The base stats (durability, mining level, etc.) come from the Tier (material).
 */
public class QualitySwordItem extends SwordItem implements IItemExtension {

    /**
     * Constructs a QualitySwordItem using the basic SwordItem(Tier, Properties) constructor.
     */
    public QualitySwordItem(Tier tier, Item.Properties properties) {
        super(tier, properties);
    }

    /**
     * Retrieve the “quality” of this sword from a CompoundTag in CUSTOM_DATA.
     * Defaults to 1 if not set.
     */
    public static int getQuality(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        return tag.contains("Quality") ? tag.getInt("Quality") : 1; // Default to 1 if missing
    }

    /**
     * Set the “quality” value in CUSTOM_DATA for this sword.
     */
    public static void setQuality(ItemStack stack, int quality) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        tag.putInt("Quality", quality);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /**
     * Computes total damage = Tier's baseDamage + (Quality - 1).
     */
    @Override
    public int getDamage(ItemStack stack) {
        // Base damage from the Tier
        int baseDamage = (int) this.getTier().getAttackDamageBonus();
        // Additional quality bonus
        int quality = getQuality(stack);
        return baseDamage + (quality - 1);
    }

    /**
 * Updates the custom model data based on the sword's material.
 */
public static void setMaterialModelData(ItemStack stack, UOMetalToolMaterial material) {
    int modelData = switch (material.getMetalName().toLowerCase()) {
        case "gold" -> 1001;
        case "iron" -> 1002;
        case "valorite" -> 1003;
        default -> 0;
    };
    stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(modelData));
}

public static String getMaterialFromModelData(int modelData) {
    return switch (modelData) {
        case 1001 -> "gold";
        case 1002 -> "iron";
        case 1003 -> "valorite";
        default -> null;
    };
}

@Override
public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    super.appendHoverText(stack, context, tooltip, flag);

    // Get material from custom model data
    int modelData = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.DEFAULT).value();
    String material = getMaterialFromModelData(modelData);
    int quality = getQuality(stack);
    String qualityName = getQualityName(quality);

    if (material != null) {
        tooltip.add(Component.literal("Material: " + material));
    }
    tooltip.add(Component.literal("Quality: " + qualityName + " (" + quality + ")"));
}

/**
 * Retrieves the quality name based on the quality level.
 */
public static String getQualityName(int quality) {
    return switch (quality) {
        case 1 -> "Crude";
        case 2 -> "Basic";
        case 3 -> "Fine";
        case 4 -> "Exceptional";
        default -> "Unknown";
    };
}


}
