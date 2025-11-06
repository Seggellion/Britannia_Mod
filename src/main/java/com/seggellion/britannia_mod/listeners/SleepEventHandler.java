package com.seggellion.britannia_mod.listeners;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;

@EventBusSubscriber(modid = "britannia_mod")
public class SleepEventHandler {

    @SubscribeEvent
    public static void onCanPlayerSleep(CanPlayerSleepEvent event) {
        event.setProblem(null);
    }

    @SubscribeEvent
    public static void onCanContinueSleeping(CanContinueSleepingEvent event) {
        event.setContinueSleeping(true);
    }

}