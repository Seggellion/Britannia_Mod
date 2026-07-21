package com.seggellion.britannia_mod.network.payload.dye;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.dye.preview.DyeApplicationResultCode;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2CDyeApplicationResultPayload(
        UUID sessionId,
        DyeApplicationResultCode result,
        boolean closeScreen) implements CustomPacketPayload {
    public static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath(
            BritanniaMod.MODID, "dye_application_result");
    public static final Type<S2CDyeApplicationResultPayload> TYPE = new Type<>(TYPE_ID);
    public static final StreamCodec<FriendlyByteBuf, S2CDyeApplicationResultPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.sessionId);
                buffer.writeEnum(payload.result);
                buffer.writeBoolean(payload.closeScreen);
            },
            buffer -> new S2CDyeApplicationResultPayload(
                    buffer.readUUID(), buffer.readEnum(DyeApplicationResultCode.class), buffer.readBoolean()));

    public S2CDyeApplicationResultPayload {
        java.util.Objects.requireNonNull(sessionId, "sessionId");
        java.util.Objects.requireNonNull(result, "result");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
