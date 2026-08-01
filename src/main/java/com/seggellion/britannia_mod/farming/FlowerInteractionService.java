package com.seggellion.britannia_mod.farming;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.FlowerBlock;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.block.entity.FlowerBlockEntity;
import com.seggellion.britannia_mod.item.WateringCanItem;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.skill.SkillManager;
import com.seggellion.britannia_mod.util.ModTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/** Server-authoritative flower interaction and restoration transaction boundary. */
public final class FlowerInteractionService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROTECTED_MESSAGE_KEY = "message.britannia_mod.flower.protected";
    private static final String POPPY_STAGE_SEVEN_REQUIREMENTS_KEY =
            "message.britannia_mod.flower.poppy_stage_seven_requirements";
    public static final float POPPY_STAGE_SEVEN_SKILL = 100.0F;

    private FlowerInteractionService() {
    }

    public static ItemInteractionResult interact(
            Level level,
            BlockPos pos,
            BlockState state,
            Player player,
            InteractionHand hand,
            ItemStack stack,
            FlowerBlockEntity flower
    ) {
        FlowerPersistentState persistent = flower.flowerState().orElse(null);
        if (persistent == null) {
            return ItemInteractionResult.FAIL;
        }

        if (isCareItem(stack)) {
            return care(level, pos, state, player, hand, stack, flower, persistent);
        }
        if (stack.is(ItemRegistry.SCISSORS.get())) {
            return harvest(level, pos, player, hand, stack, flower, persistent);
        }
        if (stack.is(ModTags.Items.SKINNING_KNIVES)) {
            return advancePoppy(level, pos, player, hand, stack, flower, persistent);
        }
        if (isUprootingTool(stack)) {
            return uproot(level, pos, player, hand, stack, flower, persistent);
        }
        if (persistent.protectedFlower() && stack.getItem() instanceof BlockItem
                && !FlowerProtectionService.mayMutate(persistent, player, FlowerMutationReason.REPLACEMENT)) {
            deny(player, level, PROTECTED_MESSAGE_KEY);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    public static boolean cutBack(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack blade) {
        if (!(level.getBlockEntity(pos) instanceof FlowerBlockEntity flower)) {
            return false;
        }
        FlowerPersistentState state = flower.flowerState().orElse(null);
        if (state == null || state.growthStage() <= 1 || !GrainHarvestTools.isGrainHarvestBlade(blade)
                || !FlowerProtectionService.mayMutate(state, player, FlowerMutationReason.SWORD_CUTBACK)) {
            return false;
        }
        if (!flower.resetToStageOne(FlowerResetReason.SWORD_CUTBACK)) {
            return false;
        }
        damageAfterSuccess(player, InteractionHand.MAIN_HAND, blade);
        level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
        return true;
    }

    public static boolean restoreAfterNormalBreak(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof FlowerBlockEntity flower)) {
            return false;
        }
        FlowerPersistentState state = flower.flowerState().orElse(null);
        if (state == null || !FlowerProtectionService.mayMutate(state, player, FlowerMutationReason.NORMAL_BREAK)) {
            return false;
        }
        return restoreUnderlyingSoil(level, pos, flower, state);
    }

    public static boolean isMature(FlowerPersistentState state, FlowerDefinition definition) {
        return state != null && definition != null
                && (state.growthStage() >= definition.naturalMaximumStage()
                || state.growthStage() >= definition.absoluteMaximumStage());
    }

    public static boolean isUprootingTool(ItemStack stack) {
        return stack.is(ItemRegistry.FARMING_HOE.get()) || RootCropShovelTools.isRootCropShovel(stack);
    }

    public static boolean canAdvancePoppy(FlowerPersistentState state, float farmingSkill, boolean taggedKnife) {
        return state != null && FlowerRegistry.POPPY.equals(state.speciesId())
                && state.growthStage() == 6 && farmingSkill >= POPPY_STAGE_SEVEN_SKILL && taggedKnife;
    }

    public static boolean isCareItem(ItemStack stack) {
        return stack.is(ItemRegistry.WATERING_CAN.get())
                || stack.is(Items.WATER_BUCKET)
                || FarmingSoilCare.fertilizerFor(stack).isPresent();
    }

    private static ItemInteractionResult care(
            Level level, BlockPos pos, BlockState blockState, Player player, InteractionHand hand,
            ItemStack stack, FlowerBlockEntity flower, FlowerPersistentState persistent
    ) {
        if (!FlowerProtectionService.mayMutate(persistent, player, FlowerMutationReason.CARE)) {
            deny(player, level, PROTECTED_MESSAGE_KEY);
            return ItemInteractionResult.SUCCESS;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }

        FlowerSoilSnapshot soil = persistent.soil();
        if (stack.is(ItemRegistry.WATERING_CAN.get())) {
            int charges = WateringCanItem.getWaterCharges(stack);
            if (soil.hydration() >= FarmingBlockEntity.MAX_HYDRATION || charges <= 0) {
                return ItemInteractionResult.SUCCESS;
            }
            if (flower.replaceSoil(soil.withHydration(soil.hydration() + 1))) {
                if (!player.getAbilities().instabuild) {
                    WateringCanItem.setWaterCharges(stack, charges - 1);
                }
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6F, 1.3F);
                awardTending(player, 1.0F);
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (stack.is(Items.WATER_BUCKET)) {
            if (soil.hydration() >= FarmingBlockEntity.MAX_HYDRATION) {
                return ItemInteractionResult.SUCCESS;
            }
            if (flower.replaceSoil(soil.withHydration(FarmingBlockEntity.MAX_HYDRATION))) {
                if (!player.getAbilities().instabuild) {
                    player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
                }
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                awardTending(player, 1.0F);
            }
            return ItemInteractionResult.SUCCESS;
        }

        FarmingSoilCare.Fertilizer fertilizer = FarmingSoilCare.fertilizerFor(stack).orElse(null);
        if (fertilizer == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        FlowerSoilSnapshot fertilized = FarmingSoilCare.apply(soil, fertilizer);
        if (!fertilized.equals(soil) && flower.replaceSoil(fertilized)) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            awardTending(player, 0.25F);
        }
        return ItemInteractionResult.SUCCESS;
    }

    private static ItemInteractionResult harvest(
            Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack,
            FlowerBlockEntity flower, FlowerPersistentState persistent
    ) {
        FlowerDefinition definition = FlowerRegistry.initial().byId(persistent.speciesId()).orElse(null);
        if (definition == null || !isMature(persistent, definition)
                || !FlowerProtectionService.mayMutate(persistent, player, FlowerMutationReason.HARVEST)) {
            if (persistent.protectedFlower()) {
                deny(player, level, PROTECTED_MESSAGE_KEY);
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        FlowerGrowthEvaluation evaluation = flower.evaluateGrowth((ServerLevel) level).orElse(null);
        if (evaluation == null) {
            return ItemInteractionResult.SUCCESS;
        }
        int quality = CropQualityCalculator.calculateQuality(definition.growthProfile(), evaluation.farmingContext());
        ItemStack harvested = new ItemStack(BuiltInRegistries.ITEM.get(definition.harvestedItemId()));
        CropQualityCalculator.applyQuality(harvested, definition.id(), quality);
        FruitProvenance.setRegionName(harvested, FarmingClimateResolver.findRegionAt(level, pos)
                .map(region -> region.name).orElse("Unknown"));
        if (!flower.harvestAndReset(quality)) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!player.getInventory().add(harvested)) {
            player.drop(harvested, false);
        }
        damageAfterSuccess(player, hand, stack);
        level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (player instanceof ServerPlayer serverPlayer) {
            FarmingSkill.award(serverPlayer, FarmingActionType.HARVEST, 1, 1.0F);
        }
        return ItemInteractionResult.SUCCESS;
    }

    private static ItemInteractionResult advancePoppy(
            Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack,
            FlowerBlockEntity flower, FlowerPersistentState persistent
    ) {
        float skill = player instanceof ServerPlayer serverPlayer
                ? SkillManager.getSkill(serverPlayer, FarmingSkill.SKILL_ID) : 0.0F;
        if (!canAdvancePoppy(persistent, skill, stack.is(ModTags.Items.SKINNING_KNIVES))
                || !FlowerProtectionService.mayMutate(persistent, player, FlowerMutationReason.POPPY_STAGE_SEVEN)) {
            if (!level.isClientSide && FlowerRegistry.POPPY.equals(persistent.speciesId()) && persistent.growthStage() == 6) {
                player.displayClientMessage(Component.translatable(POPPY_STAGE_SEVEN_REQUIREMENTS_KEY)
                        .withStyle(ChatFormatting.YELLOW), true);
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (flower.advancePoppyToStageSeven()) {
            damageAfterSuccess(player, hand, stack);
            level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.9F, 1.2F);
        }
        return ItemInteractionResult.SUCCESS;
    }

    private static ItemInteractionResult uproot(
            Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack,
            FlowerBlockEntity flower, FlowerPersistentState persistent
    ) {
        if (!FlowerProtectionService.mayMutate(persistent, player, FlowerMutationReason.PERMANENT_UPROOT)) {
            deny(player, level, PROTECTED_MESSAGE_KEY);
            return ItemInteractionResult.SUCCESS;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (restoreUnderlyingSoil((ServerLevel) level, pos, flower, persistent)) {
            damageAfterSuccess(player, hand, stack);
            level.playSound(null, pos, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.SUCCESS;
    }

    private static boolean restoreUnderlyingSoil(
            ServerLevel level, BlockPos pos, FlowerBlockEntity flower, FlowerPersistentState persistent
    ) {
        BlockState originalBlock = level.getBlockState(pos);
        FlowerSoilSnapshot soil = persistent.soil();
        try {
            if (soil.origin() == FlowerSoilOrigin.COMMUNITY_PLOT) {
                FlowerCommunityRestoration restoration = soil.communityRestoration().orElseThrow();
                if (!FlowerCommunityRestoration.COMMUNITY_FARM_BLOCK_ID.equals(restoration.blockId())) {
                    return false;
                }
                return level.setBlock(pos, BlockRegistry.COMMUNITY_FARM_BLOCK.get().defaultBlockState(), 3);
            }

            BlockState restoredState = BlockRegistry.FARMING_BLOCK.get().defaultBlockState()
                    .setValue(FarmingBlock.HYDRATION, soil.hydration())
                    .setValue(FarmingBlock.FERTILIZER, soil.fertilizerLevel())
                    .setValue(FarmingBlock.HAS_SEEDS, false);
            if (!level.setBlock(pos, restoredState, 3)) {
                return false;
            }
            BlockEntity restoredEntity = level.getBlockEntity(pos);
            if (!(restoredEntity instanceof FarmingBlockEntity farming)) {
                throw new IllegalStateException("Restored FarmingBlock is missing FarmingBlockEntity");
            }
            farming.restoreUprootedFlowerSoil(soil);
            return true;
        } catch (RuntimeException failure) {
            LOGGER.warn("[flower uproot] restoration failed at {}: {}", pos, failure.getMessage());
            if (level.setBlock(pos, originalBlock, 3)
                    && level.getBlockEntity(pos) instanceof FlowerBlockEntity rollback
                    && rollback.initialize(persistent)) {
                rollback.setChangedAndSync();
            } else {
                LOGGER.error("[flower uproot] rollback failed at {}", pos);
            }
            return false;
        }
    }

    private static void damageAfterSuccess(Player player, InteractionHand hand, ItemStack stack) {
        if (!player.getAbilities().instabuild && !stack.isEmpty()) {
            stack.hurtAndBreak(1, player, Player.getSlotForHand(hand));
        }
    }

    private static void awardTending(Player player, float modifier) {
        if (player instanceof ServerPlayer serverPlayer) {
            FarmingSkill.award(serverPlayer, FarmingActionType.TEND, 1, modifier);
        }
    }

    private static void deny(Player player, Level level, String messageKey) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.RED), true);
        }
    }
}
