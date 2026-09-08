package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.block.OrangeTreeRootBlock;
import com.seggellion.britannia_mod.block.entity.OrangeTreeRootBlockEntity;
import com.seggellion.britannia_mod.client.gui.QuestScreenText;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.farming.FarmingActionType;
import com.seggellion.britannia_mod.farming.FarmingSkill;
import com.seggellion.britannia_mod.quest.action.QuestActionEvents;
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
import org.jetbrains.annotations.Nullable;

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
                // M9 item 6: still a refusal at the hard ceiling, but it now names the crop's own
                // reading rather than only the ceiling, so a player who arrived after rain is told
                // the same thing as a player who arrived after watering.
                teachMoisture(player, farmBlockEntity, QuestScreenText.WATER_FULL);
                return ItemInteractionResult.SUCCESS;
            }

            int charges = getWaterCharges(stack);
            if (charges <= 0) {
                if (player != null) {
                    player.displayClientMessage(
                            Component.translatable(QuestScreenText.WATER_CAN_EMPTY)
                                    .withStyle(ChatFormatting.YELLOW), true);
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
            // Rowan questline M5 (protocol section 2.1): reported only when watering actually
            // raised the hydration and a charge was spent. Already-full soil and an empty can both
            // returned above. A plot with nothing growing in it has no cycle to report against, so
            // it reports nothing -- the contract requires crop_id and crop_cycle_uuid here.
            QuestActionEvents.cropWater(player, level, pos, farmBlockEntity.getPlantedCropId(),
                    farmBlockEntity.cropCycleAtCurrentPlot(), farmBlockEntity.getHydration(),
                    careState(farmBlockEntity));
            // M9 item 6: say what the soil is now, for this crop. The instruction the project
            // considered -- "water twice, then wait" -- would be false the moment rain, a bucket
            // or another player changed the hydration, and it is wrong for any crop whose ideal is
            // not the carrot's. This reads the plot and the crop that is actually in it.
            teachMoisture(player, farmBlockEntity, null);
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Tells the player where this plot's water now sits for the crop growing in it (M9 item 6).
     *
     * <p>Says nothing about how many times to water and counts nothing: it reports
     * {@link #careState} -- the same four words the action event carries and the same thresholds
     * {@code CropQualityCalculator.hydrationFit} scores against -- so the sentence stays true
     * however the water got there. Bare soil with nothing planted has no crop to have an opinion,
     * and falls back to {@code emptyPlotKey} (or says nothing when there is none).
     *
     * <p>Purely presentational: it does not change what watering does, when a charge is spent, or
     * what hydration a crop ends up with.
     */
    private static void teachMoisture(@Nullable Player player, FarmingBlockEntity farmBlockEntity,
                                      @Nullable String emptyPlotKey) {
        if (player == null) {
            return;
        }
        String key = moistureKey(careState(farmBlockEntity), emptyPlotKey);
        if (key == null) {
            return;
        }
        player.displayClientMessage(
                Component.translatable(key).withStyle(ChatFormatting.YELLOW), true);
    }

    /** Maps a {@link #careState} word to its lang key. Public so a unit test can walk every state. */
    @Nullable
    public static String moistureKey(String careState, @Nullable String emptyPlotKey) {
        return switch (careState) {
            case "dry" -> QuestScreenText.WATER_STATE_DRY;
            case "ideal" -> QuestScreenText.WATER_STATE_IDEAL;
            case "ok" -> QuestScreenText.WATER_STATE_OK;
            case "over" -> QuestScreenText.WATER_STATE_OVER;
            default -> emptyPlotKey;
        };
    }

    /**
     * How this plot's water level reads for the crop growing in it, in the four words protocol
     * section 2.1 allows for {@code care_state}. It reports the crop's OWN definition -- the same
     * thresholds {@code CropQualityCalculator.hydrationFit} scores against -- rather than a new
     * rule: nothing here changes what watering does, it only names the result.
     */
    private static String careState(FarmingBlockEntity farmBlockEntity) {
        CropDefinition crop = CropRegistry.byId(farmBlockEntity.getPlantedCropId()).orElse(null);
        if (crop == null) return "";
        float normalized = farmBlockEntity.getHydration() / (float) FarmingBlockEntity.MAX_HYDRATION;
        if (normalized < crop.minHydrationToGrow()) return "dry";
        if (normalized > crop.maxHydrationBeforeSeverePenalty()) return "over";
        return Math.abs(normalized - crop.hydrationIdeal()) <= crop.hydrationTolerance() ? "ideal" : "ok";
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
