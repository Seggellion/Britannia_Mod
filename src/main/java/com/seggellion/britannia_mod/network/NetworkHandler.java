package com.seggellion.britannia_mod.network;

import com.seggellion.britannia_mod.ui.ManaOverlayScreen;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                ManaSyncPayload.TYPE,
                ManaSyncPayload.STREAM_CODEC,
                new IPayloadHandler<>() {
                    @Override
                    public void handle(ManaSyncPayload data, IPayloadContext context) {
                        handleDataOnClient(data, context);
                    }
                }
        );

        // Ensure server-side handling if necessary
        if (FMLLoader.getDist().isDedicatedServer()) {
            // Add server-side payload handling here if needed
        }
    }

    public static void sendToPlayer(ServerPlayer player, ManaSyncPayload payload) {
        player.connection.send(new ClientboundCustomPayloadPacket(payload));
    }

    public static void handleDataOnClient(ManaSyncPayload data, IPayloadContext context) {
        context.enqueueWork(() -> {
            System.out.println("Received ManaSyncPayload on Client: " + data.mana());

            // Update the clientMana in ManaOverlayScreen
            ManaOverlayScreen overlay = ManaOverlayScreen.getInstance();
            if (overlay != null) {
                overlay.updateMana(data.mana());
            } else {
                System.out.println("ManaOverlayScreen instance is null");
            }
        });
    }
}
