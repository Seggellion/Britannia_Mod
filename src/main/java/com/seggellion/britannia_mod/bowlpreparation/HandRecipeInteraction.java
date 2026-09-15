package com.seggellion.britannia_mod.bowlpreparation;

import com.seggellion.britannia_mod.registry.DyeItemRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Main phase owns a matching gesture; a redundant/reversed OFF phase never commits a recipe. */
public final class HandRecipeInteraction {
    private HandRecipeInteraction() {}

    public static InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (player.isSpectator()) return InteractionResultHolder.pass(held);
        var dry = BowlPreparationService.plan(player.getMainHandItem(), player.getOffhandItem());
        var mix = FertileDirtMixingService.plan(player.getMainHandItem(), player.getOffhandItem());
        InteractionHand tub = player.getMainHandItem().is(DyeItemRegistry.DYE_TUB.get()) ? InteractionHand.MAIN_HAND
                : player.getOffhandItem().is(DyeItemRegistry.DYE_TUB.get()) ? InteractionHand.OFF_HAND : null;
        InteractionHand pigment = tub == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean dye = tub != null && DyeItemRegistry.pigmentId(player.getItemInHand(pigment).getItem()).isPresent();
        int matches = (dry.isPresent()?1:0) + (mix.isPresent()?1:0) + (dye?1:0);
        if (matches == 0) return InteractionResultHolder.pass(held);
        if (matches != 1 || hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
        // Water-source filling has precedence over dry bowl recipes in either arrangement.
        if (dry.isPresent()) {
            InteractionHand driver = dry.orElseThrow().driverHand();
            if (BowlPreparationItem.tryFillFromSource(level, player, driver)) return InteractionResultHolder.sidedSuccess(player.getMainHandItem(), level.isClientSide);
        }
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
        if (dye) {
            var roles = new HandRecipeRoles(tub, player.getMainHandItem(), player.getOffhandItem());
            DyeItemRegistry.DYE_TUB.get().loadFromHands(level, player, roles);
        } else {
            boolean applied = dry.isPresent()
                    ? BowlPreparationService.apply(serverPlayer, dry.orElseThrow()) == BowlPreparationService.ApplyResult.APPLIED
                    : FertileDirtMixingService.apply(serverPlayer, mix.orElseThrow()) == FertileDirtMixingService.ApplyResult.APPLIED;
            if (applied) level.playSound(null, player.blockPosition(), SoundEvents.COMPOSTER_FILL_SUCCESS, SoundSource.PLAYERS, .7f, 1f);
        }
        serverPlayer.containerMenu.broadcastChanges();
        return InteractionResultHolder.sidedSuccess(player.getMainHandItem(), false);
    }
}
