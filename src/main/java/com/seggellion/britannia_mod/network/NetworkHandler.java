package com.seggellion.britannia_mod.network;

import com.seggellion.britannia_mod.ui.ManaOverlayScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import com.seggellion.britannia_mod.client.gui.HouseManagementScreen;
import com.seggellion.britannia_mod.network.ManaSyncPayload;

import com.seggellion.britannia_mod.network.HouseManagementScreenPayload;

import com.seggellion.britannia_mod.structure.HouseActionHandler;


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
            (payload, context) -> {
                if (FMLLoader.getDist().isClient()) {
                    ClientNetworkHandler.handleManaSyncOnClient(payload, context);
                }
            }
        );

        // ✅ Register: Spell cast (client → server)
        registrar.playToServer(
            SpellCastPayload.TYPE,
            SpellCastPayload.STREAM_CODEC,
            (data, context) -> handleSpellCastOnServer(data, context)
        );

        // Register for house renaming
        registrar.playToServer(
            RenameHousePayload.TYPE,
            RenameHousePayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer serverPlayer) {
                    RenameHouseHandler.handle(payload, serverPlayer);
                }
            })
        );

registrar.playToClient(
    HouseManagementScreenPayload.TYPE,
    HouseManagementScreenPayload.STREAM_CODEC,
    (payload, context) -> {
        if (FMLLoader.getDist().isClient()) {
            ClientNetworkHandler.handleHouseScreenOnClient(payload, context);
        }
    }
);

        // Register for sign style update (sign_type + holder_type)
        registrar.playToServer(
            UpdateSignStylePayload.TYPE,
            UpdateSignStylePayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer serverPlayer) {
                    UpdateSignStyleHandler.handle(payload, serverPlayer);
                }
            })
        );

        // Register HouseManagementActionPayload (client → server)
        registrar.playToServer(
            HouseManagementActionPayload.TYPE,
            HouseManagementActionPayload.STREAM_CODEC,
            (data, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    if (data.action() == HouseManagementActionPayload.Action.REDEED) {
                        HouseActionHandler.handleRedeed(player);
                    }
                }
            })
        );

        // ✅ Do NOT register clientbound packets here! See ClientEventHandler.
    }

public static void sendToServer(SpellCastPayload payload) {
    if (FMLLoader.getDist().isClient()) {
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(payload));
    }
}



    // Method to send packets from server → client
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        player.connection.send(new ClientboundCustomPayloadPacket(payload));
    }
        // ✅ Handle spell casting on server
    public static void handleSpellCastOnServer(SpellCastPayload data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player playerEntity = context.player();
            if (playerEntity instanceof ServerPlayer player) {
                data.handleOnServer(player);
            }
        });
    }
}