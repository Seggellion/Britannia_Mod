package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.util.PickaxeMiningRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IItemExtension;

import java.util.List;

public class QualityToolItem extends PickaxeItem implements IItemExtension {

    public QualityToolItem(Tier tier, Item.Properties properties) {
        super(tier, properties);
    }

    // --- QUALITY METHODS ---
    public static int getQuality(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        return tag.contains("Quality") ? tag.getInt("Quality") : 1;
    }

    public static void setQuality(ItemStack stack, int quality) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        tag.putInt("Quality", quality);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // --- MATERIAL METHODS (NEW) ---
    public static String getMaterial(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        return tag.contains("Material") ? tag.getString("Material") : "iron"; // Default to iron
    }

    public static void setMaterial(ItemStack stack, UOMetalToolMaterial material) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        tag.putString("Material", material.getMetalName().toLowerCase()); // e.g. "valorite"
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        // Read dynamically directly from custom data
        String material = getMaterial(stack);
        int quality = getQuality(stack);
        String qualityName = getQualityName(quality);

        // Capitalize first letter of material for nicer tooltips
        String formattedMaterial = material.substring(0, 1).toUpperCase() + material.substring(1);

        tooltip.add(Component.literal("Material: " + formattedMaterial));
        tooltip.add(Component.literal("Quality: " + qualityName + " (" + quality + ")"));
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return PickaxeMiningRules.isAllowedMineableBlock(state);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return PickaxeMiningRules.isAllowedMineableBlock(state) ? 2.0F + (getQuality(stack) * 0.5F) : 0.0F;
    }
}
