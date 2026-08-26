package com.seggellion.britannia_mod.client.sound;

import com.seggellion.britannia_mod.BritanniaMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Drops all retained Moongate sounds when the client leaves its current world. */
@EventBusSubscriber(modid = BritanniaMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class MoongateAmbientSoundEvents {
    private MoongateAmbientSoundEvents() {
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        MoongateAmbientSoundManager.stopAll();
    }
}
