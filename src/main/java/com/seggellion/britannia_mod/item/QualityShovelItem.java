package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.dirtgathering.DirtGatheringTarget;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IItemExtension;

import java.util.List;

public class QualityShovelItem extends ShovelItem implements IItemExtension {
    public QualityShovelItem(Tier tier, Item.Properties properties) {
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

    public static String getMaterial(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        return tag.contains("Material") ? tag.getString("Material") : "iron";
    }

    public static void setMaterial(ItemStack stack, UOMetalToolMaterial material) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        tag.putString("Material", material.getMetalName().toLowerCase());
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

        String material = getMaterial(stack);
        int quality = getQuality(stack);
        String qualityName = getQualityName(quality);
        String formattedMaterial = material.substring(0, 1).toUpperCase() + material.substring(1);

        tooltip.add(Component.literal("Material: " + formattedMaterial));
        tooltip.add(Component.literal("Quality: " + qualityName + " (" + quality + ")"));
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_SHOVEL) ? 2.0F + (getQuality(stack) * 0.5F) : super.getDestroySpeed(stack, state);
    }

    /**
     * Suppress vanilla dirt-path prediction for Patch 18's exact gather gesture.
     *
     * <p>The server grant lives exclusively in {@code DirtGatheringInteractionHandler}; this
     * override never creates an item. Adventure can skip item use entirely, while Survival reaches
     * this method on the logical client after its right-click packet has already been sent. Returning
     * success there keeps the client from briefly replacing the dirt with a ghost path.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getHand() == InteractionHand.MAIN_HAND
                && context.getItemInHand().is(ToolRegistry.SHOVEL.get())
                && DirtGatheringTarget.isGatherable(
                        context.getLevel().getBlockState(context.getClickedPos()))) {
            return context.getLevel().isClientSide ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return super.useOn(context);
    }
}
