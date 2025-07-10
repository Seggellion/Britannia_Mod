package com.seggellion.britannia_mod.network.payload;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CloseScreenS2CPayload() implements CustomPacketPayload {

    public static final Type<CloseScreenS2CPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "close_screen"));

    public static final StreamCodec<FriendlyByteBuf, CloseScreenS2CPayload> STREAM_CODEC =
        StreamCodec.unit(new CloseScreenS2CPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Client-side handler
    public static void handle(CloseScreenS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                mc.setScreen(null); // Closes the screen
                mc.player.displayClientMessage(Component.literal("Transaction complete!"), false);
            }
        });
    }

    // Server-side call
    public static void send(ServerPlayer player) {
        player.connection.send(new ClientboundCustomPayloadPacket(new CloseScreenS2CPayload()));
    }
}
