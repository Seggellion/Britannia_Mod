package com.seggellion.britannia_mod.banner.placement;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Bounds the runtime preference map to the lifetime of connected server players. */
public final class BannerOrientationPreferenceLifecycle {
    private BannerOrientationPreferenceLifecycle() {
    }

    public static void register(IEventBus eventBus) {
        eventBus.register(BannerOrientationPreferenceLifecycle.class);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BannerOrientationPreferenceService.clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BannerOrientationPreferenceService.clearAll();
    }
}
