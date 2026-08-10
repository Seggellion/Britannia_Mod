package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.block.TrainingDummyBlock;
import com.seggellion.britannia_mod.training.TrainingDummyService;
import com.seggellion.britannia_mod.training.TrainingWeaponClassifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Intercepts supported main-hand strikes before block mining can damage the weapon. */
public final class TrainingDummyEventHandler {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!(state.getBlock() instanceof TrainingDummyBlock dummy)) {
            return;
        }

        ItemStack mainHand = event.getEntity().getItemInHand(InteractionHand.MAIN_HAND);
        if (event.getEntity().isShiftKeyDown()
                || TrainingWeaponClassifier.classify(mainHand).isEmpty()) {
            return;
        }

        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCanceled(true);
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)
                || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }

        TrainingDummyService.AttemptResult result = TrainingDummyService.attempt(player, mainHand);
        if (result.accepted()) {
            dummy.triggerHit(level, event.getPos(), state);
        }
    }
}
