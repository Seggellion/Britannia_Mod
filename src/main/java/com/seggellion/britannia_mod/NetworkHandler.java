package com.seggellion.britannia_mod.network;

import com.seggellion.britannia_mod.ui.ManaOverlayScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.fml.loading.FMLLoader;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

public class NetworkHandler {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // Existing ManaSyncPayload registration
        registrar.playToClient(
            ManaSyncPayload.TYPE,
            ManaSyncPayload.STREAM_CODEC,
            new IPayloadHandler<ManaSyncPayload>() {
                @Override
                public void handle(ManaSyncPayload data, IPayloadContext context) {
                    handleDataOnClient(data, context);
                }
            }
        );

        // Register SpellCastPayload to receive from client
        registrar.playToServer(
            SpellCastPayload.TYPE,
            SpellCastPayload.STREAM_CODEC,
            new IPayloadHandler<SpellCastPayload>() {
                @Override
                public void handle(SpellCastPayload data, IPayloadContext context) {
                    handleSpellCastOnServer(data, context);
                }
            }
        );
    }

    // Existing method to send ManaSyncPayload to player
    public static void sendToPlayer(ServerPlayer player, ManaSyncPayload payload) {
        player.connection.send(new ClientboundCustomPayloadPacket(payload));
    }

    // Handle ManaSyncPayload on client
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

    // Method to handle SpellCastPayload on server
    public static void handleSpellCastOnServer(SpellCastPayload data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player playerEntity = context.player();
            if (playerEntity instanceof ServerPlayer player) {
                data.handleOnServer(player);
            } else {
                // Handle the case where the player is not a ServerPlayer
                System.err.println("Payload player is not a ServerPlayer: " + playerEntity);
            }
        });
    }

    // Method to send SpellCastPayload from client to server
    public static void sendToServer(SpellCastPayload payload) {
        // Ensure we're on the client side
        if (FMLLoader.getDist().isClient()) {
            // Send the packet to the server
            Minecraft.getInstance().getConnection().send(
                new ServerboundCustomPayloadPacket(payload)
            );
        }
    }
}
