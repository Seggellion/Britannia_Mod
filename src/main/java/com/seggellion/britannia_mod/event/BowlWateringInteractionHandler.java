package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.entity.FlowerBlockEntity;
import com.seggellion.britannia_mod.farming.BowlWateringService;
import com.seggellion.britannia_mod.farming.FlowerInteractionService;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** The main interaction phase owns bowl care even when the water is held offhand. */
public final class BowlWateringInteractionHandler {
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var level = event.getLevel();
        var pos = event.getPos();
        var state = level.getBlockState(pos);
        var be = level.getBlockEntity(pos);
        if (!(state.getBlock() instanceof FarmingBlock) && !(be instanceof FlowerBlockEntity)) return;
        var player = event.getEntity();
        InteractionHand role = player.getMainHandItem().is(ItemRegistry.BOWL_OF_WATER.get()) ? InteractionHand.MAIN_HAND
                : player.getOffhandItem().is(ItemRegistry.BOWL_OF_WATER.get()) ? InteractionHand.OFF_HAND : null;
        if (role == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        var stack = player.getItemInHand(role);
        if (be instanceof FlowerBlockEntity flower && flower.isInitialized()) {
            FlowerInteractionService.interact(level, pos, state, player, role, stack, flower);
        } else {
            BowlWateringService.waterFarm(level, pos, player, role, stack);
        }
    }
}
