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
import com.seggellion.britannia_mod.ModSounds;


public record TransactionSuccessS2CPayload() implements CustomPacketPayload {

    public static final Type<TransactionSuccessS2CPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "transaction_success"));

    public static final StreamCodec<FriendlyByteBuf, TransactionSuccessS2CPayload> STREAM_CODEC =
        StreamCodec.unit(new TransactionSuccessS2CPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TransactionSuccessS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) mc.setScreen(null);
            if (mc.player != null) {
                mc.player.playSound(ModSounds.TRANSACTION.get(), 1.0F, 1.0F);
                mc.player.displayClientMessage(Component.literal("Transaction complete!"), false);
            }
        });
    }

    public static void send(ServerPlayer player) {
        player.connection.send(new ClientboundCustomPayloadPacket(new TransactionSuccessS2CPayload()));
    }
}
