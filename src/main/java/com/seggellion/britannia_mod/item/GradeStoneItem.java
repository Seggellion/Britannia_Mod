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

public class GradeStoneItem extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();

    public GradeStoneItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * Sets the grade value (1-5) and updates model data.
     */
    public void setGradeValue(ItemStack stack, int gradeValue) {
        setCustomData(stack, "GradeValue", gradeValue);

        int customModelData = calculateGradeModelData(gradeValue);
        setCustomModelData(stack, customModelData);
    }

    /**
     * Retrieves the grade value.
     */
    public int getGradeValue(ItemStack stack) {
        return getCustomData(stack, "GradeValue", 0);
    }

    /**
     * Sets the stone type (e.g., marble, sandstone, etc.).
     */
    public void setStoneType(ItemStack stack, String stoneType) {
        setCustomData(stack, "StoneType", stoneType);
    }


    /**
     * Retrieves the stone type.
     */
    public String getStoneType(ItemStack stack) {
        return getCustomData(stack, "StoneType", "Unknown");
    }

    /**
     * Sets the CustomModelData for texture predicates.
     */
    private void setCustomModelData(ItemStack stack, int customModelData) {
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(customModelData));
    }

    /**
     * Retrieves the current CustomModelData.
     */
    public int getCustomModelData(ItemStack stack) {
        CustomModelData data = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.DEFAULT);
        int modelDataValue = data.value();
        return data.value();
    }

    /**
     * Calculates the CustomModelData value based on the grade (1-5).
     */
    private int calculateGradeModelData(int gradeValue) {
        return switch (gradeValue) {
            case 1 -> 1001;
            case 2 -> 1002;
            case 3 -> 1003;
            case 4 -> 1004;
            case 5 -> 1005;
            default -> 1000;
        };
    }

    /**
     * Retrieves or sets custom data in the stack.
     */
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

    /**
     * Generates the display name based on type and grade.
     */
    @Override
    public Component getName(ItemStack stack) {
        String stoneType = getStoneType(stack);
        String gradeName = getGradeName(stack);
        return Component.literal(super.getName(stack).getString() + " (" + stoneType + ", " + gradeName + ")");
    }

    /**
     * Retrieves the grade name based on grade value.
     */
    public String getGradeName(ItemStack stack) {
        int gradeValue = getGradeValue(stack);
        return switch (gradeValue) {
            case 1 -> "Low Grade";
            case 2 -> "Medium Grade";
            case 3 -> "High Grade";
            case 4 -> "Fine Grade";
            case 5 -> "Exquisite Grade";
            default -> "Unknown Grade";
        };
    }

    /**
     * Adds additional information to the item tooltip.
     */
  @Override
public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    super.appendHoverText(stack, context, tooltip, flag);

    String stoneType = getStoneType(stack);
    int gradeValue = getGradeValue(stack);
    String gradeName = getGradeName(stack);

    if (!stoneType.equals("Unknown")) {
        tooltip.add(Component.literal("Type: " + stoneType));
    }
    tooltip.add(Component.literal("Grade: " + gradeName + " (" + gradeValue + ")"));
}


}
