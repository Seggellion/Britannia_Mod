package com.seggellion.britannia_mod.network.payload.grabby;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/**
 * Player intent only. No position, no block, no target identity is accepted.
 *
 * <p>Everything about what is being destroyed is re-derived server-side from the session this id
 * refers to, and re-checked before anything happens. A forged, replayed, stale or out-of-order
 * confirmation therefore cannot destroy anything.
 *
 * <p>Deliberately the same shape as {@code C2SConfirmDyeApplicationPayload}, which the project
 * already uses for a confirmed destructive action.
 */
public record C2SConfirmGrabbyDestructionPayload(UUID sessionId) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "confirm_grabby_destruction");
    public static final Type<C2SConfirmGrabbyDestructionPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, C2SConfirmGrabbyDestructionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeUUID(payload.sessionId),
                    buffer -> new C2SConfirmGrabbyDestructionPayload(buffer.readUUID()));

    public C2SConfirmGrabbyDestructionPayload {
        Objects.requireNonNull(sessionId, "sessionId");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
