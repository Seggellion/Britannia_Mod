package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record QuestActionResultS2CPayload(long requestId, int statusCode, String responseJson)
    implements CustomPacketPayload {
    public static final int MAX_RESPONSE_CHARS = 262_144;
    public static final Type<QuestActionResultS2CPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("britannia_mod", "quest_action_result"));
    public static final StreamCodec<FriendlyByteBuf, QuestActionResultS2CPayload> STREAM_CODEC = StreamCodec.of(
        QuestActionResultS2CPayload::encode, QuestActionResultS2CPayload::decode);

    private static QuestActionResultS2CPayload decode(FriendlyByteBuf buffer) {
        return new QuestActionResultS2CPayload(buffer.readVarLong(), buffer.readVarInt(),
            buffer.readUtf(MAX_RESPONSE_CHARS));
    }

    private static void encode(FriendlyByteBuf buffer, QuestActionResultS2CPayload payload) {
        buffer.writeVarLong(payload.requestId());
        buffer.writeVarInt(payload.statusCode());
        String bounded = payload.responseJson().length() > MAX_RESPONSE_CHARS
            ? "{\"success\":false,\"error\":\"response_too_large\"}" : payload.responseJson();
        buffer.writeUtf(bounded, MAX_RESPONSE_CHARS);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
