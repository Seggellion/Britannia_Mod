package com.seggellion.britannia_mod.dye.preview;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Coarse lifecycle invalidation; normal expiry remains lazy and tick-free. */
public final class DyePreviewLifecycle {
    private DyePreviewLifecycle() {
    }

    public static void register(IEventBus eventBus) {
        eventBus.register(DyePreviewLifecycle.class);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        DyePreviewRuntime.invalidatePlayer(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        DyePreviewRuntime.invalidatePlayer(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DyePreviewRuntime.invalidatePlayer(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DyePreviewRuntime.clear();
    }
}
