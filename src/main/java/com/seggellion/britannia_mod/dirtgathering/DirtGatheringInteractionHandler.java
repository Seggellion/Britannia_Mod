package com.seggellion.britannia_mod.dirtgathering;

import com.seggellion.britannia_mod.registry.ToolRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Adventure-safe right-click endpoint for gathering loose dirt without changing terrain. */
public final class DirtGatheringInteractionHandler {
    private static final DirtGatheringInteractionHandler INSTANCE = new DirtGatheringInteractionHandler();
    private static boolean registered;

    private DirtGatheringInteractionHandler() {
    }

    public static synchronized void register() {
        if (!registered) {
            NeoForge.EVENT_BUS.register(INSTANCE);
            registered = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)
                || !level.getBlockState(event.getPos()).is(Blocks.DIRT)
                || !player.getMainHandItem().is(ToolRegistry.SHOVEL.get())) {
            return;
        }

        DirtGatheringService.Result result = DirtGatheringService.attempt(level, event.getPos(), player);
        if (!result.ownedGesture()) {
            return;
        }

        // Even a refusal owns this exact gesture. Returning SUCCESS prevents the block/item chain
        // from reaching ShovelItem.useOn and flattening the still-authoritative dirt into a path.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        DirtGatheringCooldown.copyToClone(event);
    }
}
