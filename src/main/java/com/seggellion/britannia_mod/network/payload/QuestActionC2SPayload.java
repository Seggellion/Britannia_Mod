package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record QuestActionC2SPayload(long requestId, Action action, long questId, String argument,
                                    int questGiverEntityId, UUID questGiverUuid) implements CustomPacketPayload {
    public enum Action { START, TRIGGER, CHOOSE, ABANDON, INTERACT }
    public static final Type<QuestActionC2SPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("britannia_mod", "quest_action_request"));
    public static final StreamCodec<FriendlyByteBuf, QuestActionC2SPayload> STREAM_CODEC = StreamCodec.of(
        QuestActionC2SPayload::encode, QuestActionC2SPayload::decode);

    private static QuestActionC2SPayload decode(FriendlyByteBuf buffer) {
        return new QuestActionC2SPayload(buffer.readVarLong(), buffer.readEnum(Action.class), buffer.readVarLong(),
            buffer.readUtf(128), buffer.readVarInt(), buffer.readUUID());
    }

    private static void encode(FriendlyByteBuf buffer, QuestActionC2SPayload payload) {
        buffer.writeVarLong(payload.requestId());
        buffer.writeEnum(payload.action());
        buffer.writeVarLong(payload.questId());
        buffer.writeUtf(payload.argument(), 128);
        buffer.writeVarInt(payload.questGiverEntityId());
        buffer.writeUUID(payload.questGiverUuid());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
