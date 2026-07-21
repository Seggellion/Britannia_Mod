package com.seggellion.britannia_mod.banner.renderdata;

import com.seggellion.britannia_mod.bannerdyeing.registry.BannerDataRegistries;
import com.seggellion.britannia_mod.network.payload.banner.S2CBannerRenderDataPayload;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/** Sends display-only banner metadata after authoritative server data has loaded or changed. */
public final class BannerRenderDataSync {
    private BannerRenderDataSync() {
    }

    public static void register(IEventBus gameEventBus) {
        gameEventBus.addListener(BannerRenderDataSync::onDatapackSync);
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        BannerRenderDataSnapshot snapshot = BannerDataRegistries.isAvailable()
                ? BannerRenderDataSnapshot.fromRegistry(BannerDataRegistries.current())
                : BannerRenderDataSnapshot.empty();
        S2CBannerRenderDataPayload payload = new S2CBannerRenderDataPayload(snapshot);
        event.getRelevantPlayers().forEach(player ->
                player.connection.send(new ClientboundCustomPayloadPacket(payload)));
    }
}
