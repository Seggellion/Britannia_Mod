package com.seggellion.britannia_mod.item;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class WeightedFishItem extends Item {
    private static final DataComponentType<CustomData> CUSTOM_DATA = DataComponents.CUSTOM_DATA;

    private static final Logger LOGGER = LogManager.getLogger();

    public WeightedFishItem(Item.Properties properties) {
        super(properties);
    }

    public void setWeight(ItemStack stack, double weight) {
        CustomData customData = stack.getOrDefault(CUSTOM_DATA, CustomData.of(new CompoundTag()));
        CompoundTag tag = customData.copyTag();
        tag.putDouble("FishWeight", weight);
        CustomData updated = CustomData.of(tag);
        stack.set(CUSTOM_DATA, updated);
    }

    public double getWeight(ItemStack stack) {
        CustomData customData = stack.getOrDefault(CUSTOM_DATA, CustomData.of(new CompoundTag()));
        CompoundTag tag = customData.copyTag();
        if (tag.contains("FishWeight")) {
            return tag.getDouble("FishWeight");
        }
        return 0.0;
    }

    public void setFishType(ItemStack stack, String fishType) {
        CustomData customData = stack.getOrDefault(CUSTOM_DATA, CustomData.of(new CompoundTag()));
        CompoundTag tag = customData.copyTag();
        tag.putString("FishType", fishType);
        CustomData updated = CustomData.of(tag);
        stack.set(CUSTOM_DATA, updated);
    }

    public String getFishType(ItemStack stack) {
        CustomData customData = stack.getOrDefault(CUSTOM_DATA, CustomData.of(new CompoundTag()));
        CompoundTag tag = customData.copyTag();
        if (tag.contains("FishType")) {
            return tag.getString("FishType");
        }
        return "unknown";
    }

    @Override
    public Component getName(ItemStack stack) {
        Component baseName = super.getName(stack);
        double weight = getWeight(stack);
        String displayName = baseName.getString() + " (" + String.format("%.2f", weight) + " stones)";
        return Component.literal(displayName);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        String fishType = getFishType(stack);
        if (!fishType.equals("unknown")) {
            tooltip.add(Component.literal("Type: " + fishType));
        }

        double weight = getWeight(stack);
        tooltip.add(Component.literal("Weight: " + String.format("%.2f", weight) + " stones"));
    }
}
