package com.seggellion.britannia_mod.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IItemExtension;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;


import java.util.List;

public class QualityToolItem extends PickaxeItem implements IItemExtension {

    public QualityToolItem(Tier tier, Item.Properties properties) {
        super(tier, properties);
    }

    public static int getQuality(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        return tag.contains("Quality") ? tag.getInt("Quality") : 1;
    }

    public static void setQuality(ItemStack stack, int quality) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        tag.putInt("Quality", quality);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

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

        int modelData = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.DEFAULT).value();
        String material = getMaterialFromModelData(modelData);
        int quality = getQuality(stack);
        String qualityName = getQualityName(quality);

        if (material != null) {
            tooltip.add(Component.literal("Material: " + material));
        }
        tooltip.add(Component.literal("Quality: " + qualityName + " (" + quality + ")"));
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return state.is(Blocks.STONE) || state.is(BlockTags.STONE_ORE_REPLACEABLES) || super.isCorrectToolForDrops(stack, state);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (state.is(Blocks.STONE) || state.is(BlockTags.STONE_ORE_REPLACEABLES)) {
            return 2.0F + (getQuality(stack) * 0.5F); // Slight bonus per quality
        }
        return super.getDestroySpeed(stack, state);
    }
}
