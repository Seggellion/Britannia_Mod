package com.seggellion.britannia_mod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record ManaSyncPayload(int mana) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ManaSyncPayload> TYPE = new CustomPacketPayload.Type<>(
        ResourceLocation.fromNamespaceAndPath("britannia_mod", "mana_sync")
    );

    public static final StreamCodec<FriendlyByteBuf, ManaSyncPayload> STREAM_CODEC = StreamCodec.of(
        ManaSyncPayload::encode,
        ManaSyncPayload::decode
    );

    // Decoder method
    public static ManaSyncPayload decode(FriendlyByteBuf buf) {
        int mana = buf.readInt();
        return new ManaSyncPayload(mana);
    }

    // Encoder method
    public static void encode(FriendlyByteBuf buf, ManaSyncPayload payload) {
        buf.writeInt(payload.mana());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
