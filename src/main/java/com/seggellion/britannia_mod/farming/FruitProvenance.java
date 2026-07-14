package com.seggellion.britannia_mod.farming;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

public final class FruitProvenance {
    public static final String REGION_NAME_KEY = "FruitRegionName";
    private static final String UNKNOWN_REGION = "Unknown";

    private FruitProvenance() {
    }

    public static void setRegionName(ItemStack stack, String regionName) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        tag.putString(REGION_NAME_KEY, normalizeRegionName(regionName));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static String getRegionName(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        return tag.contains(REGION_NAME_KEY) ? normalizeRegionName(tag.getString(REGION_NAME_KEY)) : UNKNOWN_REGION;
    }

    public static boolean hasRegionName(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        return tag.contains(REGION_NAME_KEY);
    }

    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        if (hasRegionName(stack)) {
            tooltip.add(Component.literal("Region: " + getRegionName(stack)).withStyle(ChatFormatting.GRAY));
        }
    }

    private static String normalizeRegionName(String regionName) {
        return regionName == null || regionName.isBlank() ? UNKNOWN_REGION : regionName;
    }
}
