package com.seggellion.britannia_mod.network.payload.grabby;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Display data for the "do you wish to destroy this?" question.
 *
 * <p>Carries nothing the client could act on by itself: a session id it can only hand back, the name
 * of the object so the question is specific, and how many slots are occupied so a container prompt
 * can warn about spilling. The server holds every fact that matters.
 */
public record S2COpenGrabbyDestructionPromptPayload(
        java.util.UUID sessionId,
        String objectName,
        int occupiedSlots
) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "open_grabby_destruction_prompt");
    public static final Type<S2COpenGrabbyDestructionPromptPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, S2COpenGrabbyDestructionPromptPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeUUID(payload.sessionId);
                        buffer.writeUtf(payload.objectName, 128);
                        buffer.writeVarInt(payload.occupiedSlots);
                    },
                    buffer -> new S2COpenGrabbyDestructionPromptPayload(
                            buffer.readUUID(), buffer.readUtf(128), buffer.readVarInt()));

    public S2COpenGrabbyDestructionPromptPayload {
        Objects.requireNonNull(sessionId, "sessionId");
        objectName = objectName == null ? "" : objectName;
        occupiedSlots = Math.max(0, occupiedSlots);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
