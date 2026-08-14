package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * A quest intent from the client.
 *
 * <p>{@code requestId} is the client-local sequence that matches a response back to its pending
 * callback. {@code requestUuid} is the correlation id: the SAME value appears in this server's
 * structured quest logs and in the Rails request, so one failed turn-in can be traced end to end
 * instead of inferred from scattered lines. It carries no authority -- the server trusts nothing
 * in this payload beyond its shape.
 */
public record QuestActionC2SPayload(long requestId, Action action, long questId, String argument,
                                    int questGiverEntityId, UUID questGiverUuid,
                                    String requestUuid) implements CustomPacketPayload {
    public enum Action { START, TRIGGER, CHOOSE, ABANDON, INTERACT }
    /** A canonical UUID string; anything longer is refused at the codec boundary. */
    public static final int MAX_REQUEST_UUID_LENGTH = 36;
    public static final Type<QuestActionC2SPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("britannia_mod", "quest_action_request"));
    public static final StreamCodec<FriendlyByteBuf, QuestActionC2SPayload> STREAM_CODEC = StreamCodec.of(
        QuestActionC2SPayload::encode, QuestActionC2SPayload::decode);

    private static QuestActionC2SPayload decode(FriendlyByteBuf buffer) {
        return new QuestActionC2SPayload(buffer.readVarLong(), buffer.readEnum(Action.class), buffer.readVarLong(),
            buffer.readUtf(128), buffer.readVarInt(), buffer.readUUID(),
            buffer.readUtf(MAX_REQUEST_UUID_LENGTH));
    }

    private static void encode(FriendlyByteBuf buffer, QuestActionC2SPayload payload) {
        buffer.writeVarLong(payload.requestId());
        buffer.writeEnum(payload.action());
        buffer.writeVarLong(payload.questId());
        buffer.writeUtf(payload.argument(), 128);
        buffer.writeVarInt(payload.questGiverEntityId());
        buffer.writeUUID(payload.questGiverUuid());
        buffer.writeUtf(payload.requestUuid() == null ? "" : payload.requestUuid(), MAX_REQUEST_UUID_LENGTH);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
