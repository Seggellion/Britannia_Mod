package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.structure.interaction.DisplayCaseDecoratorService;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** NORMAL priority: existing protection listeners retain first refusal. */
public final class DisplayCaseDecoratorInteractionHandler {
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!DisplayCaseDecoratorService.matches(event.getLevel(), event.getEntity(), event.getPos())) return;
        event.setCanceled(true);
        event.setCancellationResult(DisplayCaseDecoratorService.interact(
                event.getLevel(), event.getEntity(), event.getHand(), event.getHitVec()));
    }
}
