package com.seggellion.britannia_mod.listeners;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "britannia_mod")
public class TimeEventHandler {

    @SubscribeEvent
    public static void onSleepFinished(SleepFinishedTimeEvent event) {
        // Cancel time advancement from sleeping
        event.setTimeAddition(0); // Set to 0 instead of huge number
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        // Check all levels for sleeping players
        for (ServerLevel level : event.getServer().getAllLevels()) {
            boolean anyPlayerSleeping = level.players().stream()
                    .anyMatch(player -> player.isSleeping());

            if (anyPlayerSleeping) {
                // Freeze time when sleeping
                level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, event.getServer());

                // Force players back to sleep if they wake up (aggressive approach)
                for (ServerPlayer player : level.players()) {
                    if (!player.isSleeping() && player.getSleepingPos().isPresent()) {
                        // Player has a sleep position but isn't sleeping - force them back
                        var sleepPos = player.getSleepingPos().get();
                        if (level.getBlockState(sleepPos).isBed(level, sleepPos, player)) {
                            player.startSleeping(sleepPos);
                        }
                    }
                }
                return;
            } else {
                // Re-enable time when no one is sleeping
                level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(true, event.getServer());
            }
        }
    }
}