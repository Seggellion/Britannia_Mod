package com.seggellion.britannia_mod.bowlpreparation;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Main-hand driver for the final fertile-dirt bowl mix. */
public final class FertileDirtMixingItem extends Item {
    public FertileDirtMixingItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(held);
        }

        Optional<FertileDirtMixingPlan> candidate =
                FertileDirtMixingService.plan(held, player.getOffhandItem());
        if (candidate.isEmpty()) {
            // M8 item 8: name the failure instead of passing in silence. Message only -- the
            // return value, and therefore what counts as a valid mix, is unchanged.
            if (!level.isClientSide) {
                BowlMixingMessages.send(player,
                        FertileDirtMixingService.diagnose(held, player.getOffhandItem()));
            }
            return InteractionResultHolder.pass(held);
        }
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(held, true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(held, false);
        }

        FertileDirtMixingService.ApplyResult result =
                FertileDirtMixingService.apply(serverPlayer, candidate.orElseThrow());
        if (result == FertileDirtMixingService.ApplyResult.APPLIED
                && level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.COMPOSTER_FILL_SUCCESS,
                    SoundSource.PLAYERS,
                    0.7F,
                    1.0F);
        }

        // A stale accepted plan still owns the packet and cannot fall through to the offhand.
        return InteractionResultHolder.sidedSuccess(player.getMainHandItem(), false);
    }
}
