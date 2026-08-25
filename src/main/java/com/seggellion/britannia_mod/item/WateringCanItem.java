package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.block.OrangeTreeRootBlock;
import com.seggellion.britannia_mod.block.entity.OrangeTreeRootBlockEntity;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.farming.FarmingActionType;
import com.seggellion.britannia_mod.farming.FarmingSkill;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

public class WateringCanItem extends Item {
    public static final int MAX_WATER_CHARGES = 12;
    private static final String WATER_CHARGES_TAG = "WaterCharges";

    public WateringCanItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();

        if (level.getFluidState(pos).is(FluidTags.WATER)) {
            refillFromWater(level, pos, context.getPlayer(), stack);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof OrangeTreeRootBlock) {
            ItemInteractionResult result = waterOrangeTreeRoot(level, pos, context.getPlayer(), stack);
            return result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                    ? InteractionResult.PASS
                    : InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!(state.getBlock() instanceof FarmingBlock)) {
            return InteractionResult.PASS;
        }

        ItemInteractionResult result = waterFarmingBlock(level, pos, state, context.getPlayer(), stack);
        return result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                ? InteractionResult.PASS
                : InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hitResult.getType() == HitResult.Type.BLOCK && level.getFluidState(hitResult.getBlockPos()).is(FluidTags.WATER)) {
            refillFromWater(level, hitResult.getBlockPos(), player, stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    public static ItemInteractionResult waterFarmingBlock(Level level, BlockPos pos, BlockState state, Player player, ItemStack stack) {
        if (!(state.getBlock() instanceof FarmingBlock)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof FarmingBlockEntity farmBlockEntity)) {
            return ItemInteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            if (farmBlockEntity.getHydration() >= FarmingBlockEntity.MAX_HYDRATION) {
                if (player != null) {
                    player.displayClientMessage(Component.literal("The soil is already fully watered.").withStyle(ChatFormatting.YELLOW), true);
                }
                return ItemInteractionResult.SUCCESS;
            }

            int charges = getWaterCharges(stack);
            if (charges <= 0) {
                if (player != null) {
                    player.displayClientMessage(Component.literal("The watering can is empty.").withStyle(ChatFormatting.YELLOW), true);
                }
                return ItemInteractionResult.SUCCESS;
            }

            boolean improved = farmBlockEntity.water(1);
            if (!improved) {
                return ItemInteractionResult.SUCCESS;
            }

            level.setBlock(pos, state.setValue(FarmingBlock.HYDRATION, farmBlockEntity.getHydration()), 3);
            level.playSound(null, pos, ModSounds.WATERING_CAN_DISPENSE.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
            if (player == null || !player.getAbilities().instabuild) {
                setWaterCharges(stack, charges - 1);
            }
            if (player instanceof ServerPlayer serverPlayer) {
                FarmingSkill.award(serverPlayer, FarmingActionType.TEND, farmBlockEntity.cropTier(), 1.0f);
            }
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    public static ItemInteractionResult waterOrangeTreeRoot(Level level, BlockPos pos, Player player, ItemStack stack) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof OrangeTreeRootBlockEntity orangeRoot)) {
            return ItemInteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            if (orangeRoot.getHydrationLevel() >= FarmingBlockEntity.MAX_HYDRATION) {
                if (player != null) {
                    player.displayClientMessage(Component.literal("The " + orangeRoot.definition().displayName().toLowerCase() + " tree is already fully watered.").withStyle(ChatFormatting.YELLOW), true);
                }
                return ItemInteractionResult.SUCCESS;
            }

            int charges = getWaterCharges(stack);
            if (charges <= 0) {
                if (player != null) {
                    player.displayClientMessage(Component.literal("The watering can is empty.").withStyle(ChatFormatting.YELLOW), true);
                }
                return ItemInteractionResult.SUCCESS;
            }

            boolean improved = orangeRoot.water(1);
            if (!improved) {
                return ItemInteractionResult.SUCCESS;
            }

            level.playSound(null, pos, ModSounds.WATERING_CAN_DISPENSE.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
            if (player == null || !player.getAbilities().instabuild) {
                setWaterCharges(stack, charges - 1);
            }
            if (player instanceof ServerPlayer serverPlayer) {
                int tier = CropRegistry.byId(orangeRoot.getTreeTypeId()).map(crop -> crop.tier()).orElse(3);
                FarmingSkill.award(serverPlayer, FarmingActionType.TEND, tier, 1.0f);
            }
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void refillFromWater(Level level, BlockPos pos, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return;
        }
        int charges = getWaterCharges(stack);
        if (charges < MAX_WATER_CHARGES) {
            setWaterCharges(stack, MAX_WATER_CHARGES);
            level.playSound(null, pos, ModSounds.WATERING_CAN_FILL.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
            if (player != null) {
                player.displayClientMessage(Component.literal("Watering can refilled.").withStyle(ChatFormatting.AQUA), true);
            }
        } else if (player != null) {
            player.displayClientMessage(Component.literal("Watering can is already full.").withStyle(ChatFormatting.YELLOW), true);
        }
    }

    public static int getWaterCharges(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        if (!tag.contains(WATER_CHARGES_TAG)) {
            return MAX_WATER_CHARGES;
        }
        return Math.max(0, Math.min(MAX_WATER_CHARGES, tag.getInt(WATER_CHARGES_TAG)));
    }

    public static void setWaterCharges(ItemStack stack, int charges) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        tag.putInt(WATER_CHARGES_TAG, Math.max(0, Math.min(MAX_WATER_CHARGES, charges)));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.literal("Water: " + getWaterCharges(stack) + " / " + MAX_WATER_CHARGES).withStyle(ChatFormatting.AQUA));
    }
}
