package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.block.WeightedWoodBlock;
import com.seggellion.britannia_mod.block.entity.WeightedWoodBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

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
        WeightedWoodType definition = WeightedWoodType.byIdOrDefault(woodType);
        CustomData customData = getOrCreateCustomData(stack);
        CompoundTag tag = customData.copyTag();
        tag.putString("WoodType", definition.id());
        tag.putInt("CustomModelData", definition.customModelData());
        updateCustomData(stack, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(definition.customModelData()));
    }

    public String getWoodType(ItemStack stack) {
        CompoundTag tag = getOrCreateCustomData(stack).copyTag();
        return tag.contains("WoodType") ? tag.getString("WoodType") : "unknown";
    }

    public WeightedWoodType getWoodTypeDefinition(ItemStack stack) {
        return WeightedWoodType.byIdOrDefault(getWoodType(stack));
    }

    public static ItemStack createStack(WeightedWoodType woodType, double weight) {
        ItemStack stack = new ItemStack(com.seggellion.britannia_mod.registry.ItemRegistry.WEIGHTED_WOOD_ITEM.get());
        if (stack.getItem() instanceof WeightedWoodItem weightedWoodItem) {
            weightedWoodItem.setWoodType(stack, woodType.id());
            weightedWoodItem.setWeight(stack, weight);
        }
        return stack;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        BlockPos placePos = clickedState.canBeReplaced() ? clickedPos : clickedPos.relative(context.getClickedFace());
        BlockState placeState = level.getBlockState(placePos);

        if (!placeState.canBeReplaced()) {
            return InteractionResult.FAIL;
        }

        WeightedWoodType woodType = getWoodTypeDefinition(stack);
        Direction.Axis axis = context.getClickedFace().getAxis();
        BlockState weightedWoodState = BlockRegistry.WEIGHTED_WOOD_BLOCK.get()
                .defaultBlockState()
                .setValue(WeightedWoodBlock.WOOD_TYPE, woodType)
                .setValue(RotatedPillarBlock.AXIS, axis);

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!level.setBlock(placePos, weightedWoodState, 11)) {
            return InteractionResult.FAIL;
        }

        if (level.getBlockEntity(placePos) instanceof WeightedWoodBlockEntity weightedWoodBlockEntity) {
            weightedWoodBlockEntity.setWoodData(woodType.id(), getWeight(stack));
        }

        SoundType soundType = weightedWoodState.getSoundType();
        level.playSound(
                context.getPlayer(),
                placePos,
                soundType.getPlaceSound(),
                SoundSource.BLOCKS,
                (soundType.getVolume() + 1.0F) / 2.0F,
                soundType.getPitch() * 0.8F
        );

        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
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
            tooltip.add(Component.literal("Type: " + getWoodTypeDefinition(stack).displayName()));
        }
        double weight = getWeight(stack);
        tooltip.add(Component.literal("Weight: " + String.format("%.2f", weight) + " stones"));
    }
}
