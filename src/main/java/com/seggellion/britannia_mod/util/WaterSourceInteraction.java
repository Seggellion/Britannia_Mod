package com.seggellion.britannia_mod.util;

import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.client.gui.QuestScreenText;
import com.seggellion.britannia_mod.block.WaterWellBlock;
import com.seggellion.britannia_mod.bowlpreparation.BowlWaterFillingPlan;
import com.seggellion.britannia_mod.bowlpreparation.BowlWaterFillingService;
import com.seggellion.britannia_mod.item.PitcherItem;
import com.seggellion.britannia_mod.item.WateringCanItem;
import com.seggellion.britannia_mod.quest.action.QuestAction;
import com.seggellion.britannia_mod.quest.action.QuestActionEvents;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
            // Rowan farming questline M8 item 8. This refusal returned success-with-nothing and
            // said nothing at all, so a protected pond and a broken container looked identical.
            // Message only: the policy still decides and the return value is unchanged.
            //
            // One reason, one message. WaterSourceAccessPolicy is level.mayInteract and nothing
            // else -- a single DENIED_WORLD with no notion of buckets or wells -- so a message
            // that named a reason was inventing it from the item in hand, and inventing it wrong
            // both ways round: a bucket refused by spawn protection was told it "only fills at the
            // Water Well" when a well inside the same protection refuses too and outside it a
            // bucket fills anywhere, and a player refused standing on a Water Well was told to use
            // the public Water Well. The Adventure-mode rule that really does confine a bucket to
            // the well is vanilla's, enforced in BucketItem's own use-permission check, and is not
            // this refusal.
            player.displayClientMessage(
                    Component.translatable(QuestScreenText.WATER_PROTECTED)
                            .withStyle(ChatFormatting.YELLOW), true);
            return ItemInteractionResult.sidedSuccess(false);
        }

        // Rowan questline M5 (protocol section 2.1): the two sources the contract distinguishes.
        // A Water Well is the only unlimited source an Adventure player can reach, so which one
        // this was is part of the fact being reported, not a detail.
        String source = level.getBlockState(pos).getBlock() instanceof WaterWellBlock
                ? QuestAction.SOURCE_WELL
                : QuestAction.SOURCE_BLOCK;

        boolean applied = true;
        boolean wateringCanFill = false;
        String containerItemId = "";
        if (stack.getItem() instanceof WateringCanItem) {
            applied = WateringCanItem.getWaterCharges(stack) < WateringCanItem.MAX_WATER_CHARGES;
            if (applied) {
                WateringCanItem.setWaterCharges(stack, WateringCanItem.MAX_WATER_CHARGES);
                wateringCanFill = true;
                containerItemId = QuestActionEvents.itemId(stack);
            }
        } else if (stack.getItem() instanceof PitcherItem pitcher) {
            pitcher.fillWithWater(stack);
            containerItemId = QuestActionEvents.itemId(stack);
        } else if (stack.is(Items.BUCKET)) {
            player.setItemInHand(hand,
                    ItemUtils.createFilledResult(stack, player, new ItemStack(Items.WATER_BUCKET)));
            containerItemId = QuestActionEvents.itemId(Items.WATER_BUCKET);
        } else if (player instanceof ServerPlayer serverPlayer) {
            Optional<BowlWaterFillingPlan> plan = BowlWaterFillingService.plan(stack);
            applied = plan.isPresent()
                    && BowlWaterFillingService.apply(serverPlayer, hand, plan.orElseThrow(), source)
                    == BowlWaterFillingService.ApplyResult.APPLIED;
            if (applied) {
                containerItemId = QuestActionEvents.itemId(ItemRegistry.BOWL_OF_WATER.get());
            }
        } else {
            applied = false;
        }

        if (applied) {
            level.playSound(null, pos,
                    wateringCanFill ? ModSounds.WATERING_CAN_FILL.get() : SoundEvents.BUCKET_FILL,
                    SoundSource.BLOCKS, 0.8F, 1.0F);
            // Published only after the exchange itself succeeded: a full watering can, a refused
            // permission check and an unsupported stack all leave without reporting anything.
            QuestActionEvents.waterContainerFill(player, level, pos, containerItemId, source);
        }
        return ItemInteractionResult.sidedSuccess(false);
    }
}
