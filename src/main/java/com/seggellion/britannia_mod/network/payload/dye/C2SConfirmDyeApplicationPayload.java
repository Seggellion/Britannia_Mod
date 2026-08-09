package com.seggellion.britannia_mod.network.payload.dye;

import com.seggellion.britannia_mod.BritanniaMod;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Player intent only. No pigment, material, colour, match, banner, or tub authority is accepted. */
public record C2SConfirmDyeApplicationPayload(UUID sessionId) implements CustomPacketPayload {
    public static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath(
            BritanniaMod.MODID, "confirm_dye_application");
    public static final Type<C2SConfirmDyeApplicationPayload> TYPE = new Type<>(TYPE_ID);
    public static final StreamCodec<FriendlyByteBuf, C2SConfirmDyeApplicationPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeUUID(payload.sessionId),
            buffer -> new C2SConfirmDyeApplicationPayload(buffer.readUUID()));

    public C2SConfirmDyeApplicationPayload {
        java.util.Objects.requireNonNull(sessionId, "sessionId");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
