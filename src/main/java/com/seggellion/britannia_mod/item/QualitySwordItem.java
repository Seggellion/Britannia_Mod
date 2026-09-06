package com.seggellion.britannia_mod.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.common.extensions.IItemExtension;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import java.util.List;

public class QualitySwordItem extends SwordItem implements IItemExtension {

    public QualitySwordItem(Tier tier, Item.Properties properties) {
        super(tier, properties);
    }

    // --- QUALITY METHODS ---
    public static int getQuality(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        if (tag.contains("Quality")) return tag.getInt("Quality");
        // Katana/halberd stacks crafted before their placeholder promotion already use
        // the catalogue metadata, without the older QualitySwordItem top-level keys.
        CompoundTag smithing = tag.getCompound("BritanniaBlacksmithing");
        return smithing.contains("quality") ? smithing.getInt("quality") : 1;
    }

    public static void setQuality(ItemStack stack, int quality) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        tag.putInt("Quality", quality);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // --- MATERIAL METHODS (NEW) ---
    public static String getMaterial(ItemStack stack) {
        String material = BlacksmithItemData.materialId(stack);
        return material == null ? "iron" : material.replace('_', ' ');
    }

    public static void setMaterial(ItemStack stack, UOMetalToolMaterial material) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        tag.putString("Material", material.getMetalName().toLowerCase()); // e.g. "valorite"
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        // getDamage(ItemStack) is durability WEAR, not attack damage. Use the actual
        // combat hook, preserving the legacy +1 per quality step (including old 1-4 stacks).
        // Until authored Minecraft balance is supplied, use the vanilla iron sword analogue.
        int qualityBonus = Math.clamp(getQuality(stack), 1, 4) - 1;
        return SwordItem.createAttributes(getTier(), 3 + qualityBonus, -2.4F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        if (stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains("BritanniaBlacksmithing")) {
            BlacksmithItemData.appendTooltip(stack, tooltip);
            return;
        }

        // Read dynamically directly from custom data
        String material = getMaterial(stack);
        int quality = getQuality(stack);
        String qualityName = getQualityName(quality);

        // Capitalize first letter of material for nicer tooltips
        String formattedMaterial = material.substring(0, 1).toUpperCase() + material.substring(1);

        tooltip.add(Component.literal("Material: " + formattedMaterial));
        tooltip.add(Component.literal("Quality: " + qualityName + " (" + quality + ")"));
    }

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
