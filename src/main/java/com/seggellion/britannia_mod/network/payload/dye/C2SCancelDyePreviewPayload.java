package com.seggellion.britannia_mod.network.payload.dye;

import com.seggellion.britannia_mod.BritanniaMod;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Cancellation intent only; it can never mutate either held stack. */
public record C2SCancelDyePreviewPayload(UUID sessionId) implements CustomPacketPayload {
    public static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath(
            BritanniaMod.MODID, "cancel_dye_preview");
    public static final Type<C2SCancelDyePreviewPayload> TYPE = new Type<>(TYPE_ID);
    public static final StreamCodec<FriendlyByteBuf, C2SCancelDyePreviewPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeUUID(payload.sessionId),
            buffer -> new C2SCancelDyePreviewPayload(buffer.readUUID()));

    public C2SCancelDyePreviewPayload {
        java.util.Objects.requireNonNull(sessionId, "sessionId");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
