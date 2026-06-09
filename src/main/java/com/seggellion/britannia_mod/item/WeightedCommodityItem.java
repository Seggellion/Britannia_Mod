package com.seggellion.britannia_mod.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

public class WeightedCommodityItem extends Item {
    public WeightedCommodityItem(Properties properties) {
        super(properties);
    }

    public static void setWeight(ItemStack stack, double weight) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        tag.putDouble("CommodityWeight", Math.max(0.0D, weight));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static double getWeight(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        return tag.contains("CommodityWeight") ? tag.getDouble("CommodityWeight") : 1.0D;
    }

    public static boolean hasWeight(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        return tag.contains("CommodityWeight");
    }

    public static void setQuality(ItemStack stack, int quality) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        tag.putInt("CommodityQuality", Math.max(1, Math.min(100, quality)));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int getQuality(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        return tag.contains("CommodityQuality") ? tag.getInt("CommodityQuality") : 50;
    }

    public static boolean hasQuality(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        return tag.contains("CommodityQuality");
    }

    public static ItemStack withGeneratedData(Item item, int count, net.minecraft.util.RandomSource random, double minWeight, double maxWeight) {
        ItemStack stack = new ItemStack(item, count);
        double weight = minWeight + random.nextDouble() * Math.max(0.0D, maxWeight - minWeight);
        setWeight(stack, weight);
        setQuality(stack, 35 + random.nextInt(51));
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        Component baseName = super.getName(stack);
        return Component.literal(baseName.getString() + " (" + String.format("%.2f", getWeight(stack)) + " stones)");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.literal("Weight: " + String.format("%.2f", getWeight(stack)) + " stones"));
        if (hasQuality(stack)) {
            tooltip.add(Component.literal("Quality: " + getQuality(stack)));
        }
    }
}
