package com.seggellion.britannia_mod.grabbyhands.destruction;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Coarse invalidation for outstanding destruction confirmations.
 *
 * <p>Ordinary expiry stays lazy and tick-free; these are the events after which a pending question no
 * longer makes sense at all. Copied in shape from {@code dye.preview.DyePreviewLifecycle}.
 */
public final class GrabbyDestructionLifecycle {
    private static boolean registered;

    private GrabbyDestructionLifecycle() {
    }

    public static synchronized void register() {
        if (!registered) {
            NeoForge.EVENT_BUS.register(GrabbyDestructionLifecycle.class);
            registered = true;
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        GrabbyDestructionSessions.invalidate(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        GrabbyDestructionSessions.invalidate(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GrabbyDestructionSessions.invalidate(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        GrabbyDestructionSessions.clear();
    }
}
