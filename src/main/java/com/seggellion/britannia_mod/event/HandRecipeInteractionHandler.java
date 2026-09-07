package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.bowlpreparation.HandRecipeInteraction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Air-use only: block targets, protection and their established precedence run first. */
public final class HandRecipeInteractionHandler {
    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        var result = HandRecipeInteraction.use(event.getLevel(), event.getEntity(), event.getHand());
        if (result.getResult().consumesAction()) {
            event.setCanceled(true);
            event.setCancellationResult(result.getResult());
        }
    }
}
