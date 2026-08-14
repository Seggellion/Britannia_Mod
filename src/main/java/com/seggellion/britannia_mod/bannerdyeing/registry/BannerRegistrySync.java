package com.seggellion.britannia_mod.bannerdyeing.registry;

import com.seggellion.britannia_mod.network.payload.banner.S2CBannerRegistrySyncPayload;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * Sends the full banner-dyeing registry snapshot after authoritative server data has loaded or
 * changed -- the same trigger and guard shape as
 * {@link com.seggellion.britannia_mod.banner.renderdata.BannerRenderDataSync}, which sends the
 * display-geometry subset. Both payloads ride the same event so a remote client's registry view
 * and render view can never come from different datapack generations.
 */
public final class BannerRegistrySync {
    private BannerRegistrySync() {
    }

    public static void register(IEventBus gameEventBus) {
        gameEventBus.addListener(BannerRegistrySync::onDatapackSync);
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        S2CBannerRegistrySyncPayload payload = new S2CBannerRegistrySyncPayload(
                BannerDataRegistries.isAvailable(),
                BannerDataRegistries.isAvailable() ? BannerDataRegistries.current() : RegistrySnapshot.empty());
        event.getRelevantPlayers().forEach(player -> {
            // Skip connections that negotiated no britannia channels (gametest mock players)
            // instead of letting NeoForge throw.
            if (!player.connection.hasChannel(S2CBannerRegistrySyncPayload.TYPE)) {
                return;
            }
            player.connection.send(new ClientboundCustomPayloadPacket(payload));
        });
    }
}
