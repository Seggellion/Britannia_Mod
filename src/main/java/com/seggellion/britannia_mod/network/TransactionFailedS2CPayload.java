package com.seggellion.britannia_mod.network.payload;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.server.level.ServerPlayer;

public record TransactionFailedS2CPayload(String reason) implements CustomPacketPayload {

    public static final Type<TransactionFailedS2CPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "transaction_failed"));

    public static final StreamCodec<FriendlyByteBuf, TransactionFailedS2CPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.reason, 256),
            buf -> new TransactionFailedS2CPayload(buf.readUtf(256))
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TransactionFailedS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                    Component.literal(payload.reason).withStyle(style -> style.withColor(0xFF5555)), // red text
                    false
                );
            }
        });
    }

    public static void send(ServerPlayer player, String reason) {
        player.connection.send(new ClientboundCustomPayloadPacket(new TransactionFailedS2CPayload(reason)));
    }
}
