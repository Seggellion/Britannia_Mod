package com.seggellion.britannia_mod.item;

import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import org.slf4j.Logger;

import java.util.List;

public class PurityOreItem extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();

    public PurityOreItem(Item.Properties properties) {
        super(properties);
    }

    public void setPurity(ItemStack stack, int purity) {
        setCustomData(stack, "Purity", purity);

        // In this approach, we always start from 2000 and add 1..5
        // so final values = 2001..2005
        int clampedPurity = Math.max(1, Math.min(purity, 5));
        int customModelData = 2000 + clampedPurity;  // yields 2001..2005
        setCustomModelData(stack, customModelData);
    }

    public int getPurity(ItemStack stack) {
        return getCustomData(stack, "Purity", 0);
    }


      public void setOreType(ItemStack stack, String oreType) {
        setCustomData(stack, "OreType", oreType);

        // Recalc with new oreType if needed, or simply reuse existing purity
        int purity = getPurity(stack);
        int clampedPurity = Math.max(1, Math.min(purity, 5));
        int customModelData = 2000 + clampedPurity;  // same range 2001..2005
        setCustomModelData(stack, customModelData);
    }

    public String getOreType(ItemStack stack) {
        return getCustomData(stack, "OreType", "unknown");
    }

    private void setCustomModelData(ItemStack stack, int customModelData) {
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(customModelData));
    }

    public int getCustomModelData(ItemStack stack) {
        CustomModelData data = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.DEFAULT);
        int modelDataValue = data.value();
        return data.value();
    }

    private <T> void setCustomData(ItemStack stack, String key, T value) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        if (value instanceof Integer) {
            tag.putInt(key, (Integer) value);
        } else if (value instanceof String) {
            tag.putString(key, (String) value);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private <T> T getCustomData(ItemStack stack, String key, T defaultValue) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        if (!tag.contains(key)) {
            return defaultValue;
        }
        if (defaultValue instanceof Integer) {
            return (T) (Integer) tag.getInt(key);
        } else if (defaultValue instanceof String) {
            return (T) tag.getString(key);
        }
        return defaultValue;
    }

    private int calculateOreTypeModelData(String oreType) {
        return switch (oreType.toLowerCase()) {
            case "iron ore" -> 100;
            case "gold ore" -> 200;
            case "shadow iron ore" -> 300;
            case "valorite ore" -> 400;
            case "silver ore" -> 500;
            case "verite ore" -> 600;
            case "agapite ore" -> 700;
            case "copper ore" -> 800;
            case "tin ore" -> 900;
            default -> 0;
        };
    }

    @Override
    public Component getName(ItemStack stack) {
        Component baseName = super.getName(stack);
        int purity = getPurity(stack);
        return Component.literal(baseName.getString() + " (" + purity + " purity)");
    }

@Override
public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    // Call the superclass method
    super.appendHoverText(stack, context, tooltip, flag);

    // Add custom tooltip information
    String oreType = getOreType(stack);
    int purity = getPurity(stack);

    if (!oreType.equals("unknown")) {
        tooltip.add(Component.literal("Type: " + oreType));
    }
    tooltip.add(Component.literal("Purity: " + purity));
}

}