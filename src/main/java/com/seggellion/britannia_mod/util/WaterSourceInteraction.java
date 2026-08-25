package com.seggellion.britannia_mod.util;

import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.bowlpreparation.BowlWaterFillingPlan;
import com.seggellion.britannia_mod.bowlpreparation.BowlWaterFillingService;
import com.seggellion.britannia_mod.item.PitcherItem;
import com.seggellion.britannia_mod.item.WateringCanItem;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Shared, server-authoritative fill transaction for an unlimited water source. */
public final class WaterSourceInteraction {
    private WaterSourceInteraction() {
    }

    public static boolean supports(ItemStack stack) {
        return stack.getItem() instanceof WateringCanItem
                || stack.getItem() instanceof PitcherItem
                || stack.is(Items.BUCKET)
                || BowlWaterFillingService.supports(stack);
    }

    public static ItemInteractionResult fillFromSource(
            Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack) {
        if (!supports(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (WaterSourceAccessPolicy.evaluate(level, pos, player)
                != WaterSourceAccessPolicy.Decision.ALLOWED) {
            return ItemInteractionResult.sidedSuccess(false);
        }

        boolean applied = true;
        boolean wateringCanFill = false;
        if (stack.getItem() instanceof WateringCanItem) {
            applied = WateringCanItem.getWaterCharges(stack) < WateringCanItem.MAX_WATER_CHARGES;
            if (applied) {
                WateringCanItem.setWaterCharges(stack, WateringCanItem.MAX_WATER_CHARGES);
                wateringCanFill = true;
            }
        } else if (stack.getItem() instanceof PitcherItem pitcher) {
            pitcher.fillWithWater(stack);
        } else if (stack.is(Items.BUCKET)) {
            player.setItemInHand(hand,
                    ItemUtils.createFilledResult(stack, player, new ItemStack(Items.WATER_BUCKET)));
        } else if (player instanceof ServerPlayer serverPlayer) {
            Optional<BowlWaterFillingPlan> plan = BowlWaterFillingService.plan(stack);
            applied = plan.isPresent()
                    && BowlWaterFillingService.apply(serverPlayer, hand, plan.orElseThrow())
                    == BowlWaterFillingService.ApplyResult.APPLIED;
        } else {
            applied = false;
        }

        if (applied) {
            level.playSound(null, pos,
                    wateringCanFill ? ModSounds.WATERING_CAN_FILL.get() : SoundEvents.BUCKET_FILL,
                    SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(false);
    }
}
