package com.seggellion.britannia_mod.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import java.util.List;

public class WeightedWoodItem extends Item {
    private static final DataComponentType<CustomData> CUSTOM_DATA = DataComponents.CUSTOM_DATA;

    public WeightedWoodItem(Item.Properties properties) {
        super(properties);
    }

    private CustomData getOrCreateCustomData(ItemStack stack) {
        return stack.getOrDefault(CUSTOM_DATA, CustomData.of(new CompoundTag()));
    }

    private void updateCustomData(ItemStack stack, CustomData customData) {
        stack.set(CUSTOM_DATA, customData);
    }

    public void setWeight(ItemStack stack, double weight) {
        CustomData customData = getOrCreateCustomData(stack);
        CompoundTag tag = customData.copyTag();
        tag.putDouble("WoodWeight", weight);
        updateCustomData(stack, CustomData.of(tag));
    }

    public double getWeight(ItemStack stack) {
        CompoundTag tag = getOrCreateCustomData(stack).copyTag();
        return tag.contains("WoodWeight") ? tag.getDouble("WoodWeight") : 0.0;
    }

    public void setWoodType(ItemStack stack, String woodType) {
        CustomData customData = getOrCreateCustomData(stack);
        CompoundTag tag = customData.copyTag();
        tag.putString("WoodType", woodType);
        
        int customModelData = switch (woodType.toLowerCase()) {
            case "spruce" -> 1;
            case "birch" -> 2;
            case "jungle" -> 3;
            case "acacia" -> 4;
            case "dark_oak" -> 5;
            case "mangrove" -> 6;
            default -> 0; // If not recognized, default = oak
        };
        
        tag.putInt("CustomModelData", customModelData);
        updateCustomData(stack, CustomData.of(tag));
    }

    public String getWoodType(ItemStack stack) {
        CompoundTag tag = getOrCreateCustomData(stack).copyTag();
        return tag.contains("WoodType") ? tag.getString("WoodType") : "unknown";
    }

    @Override
    public Component getName(ItemStack stack) {
        Component baseName = super.getName(stack);
        double weight = getWeight(stack);
        return Component.literal(baseName.getString() + " (" + String.format("%.2f", weight) + " stones)");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String woodType = getWoodType(stack);
        if (!woodType.equals("unknown")) {
            tooltip.add(Component.literal("Type: " + woodType));
        }
        double weight = getWeight(stack);
        tooltip.add(Component.literal("Weight: " + String.format("%.2f", weight) + " stones"));
    }
}
