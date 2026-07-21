package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.BritanniaMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Prevents render metadata from one server being reused while connecting to another. */
@EventBusSubscriber(modid = BritanniaMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class BannerClientConnectionEvents {
    private BannerClientConnectionEvents() {
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBannerRenderData.clear();
    }
}
