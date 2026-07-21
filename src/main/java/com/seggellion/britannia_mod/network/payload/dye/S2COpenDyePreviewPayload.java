package com.seggellion.britannia_mod.network.payload.dye;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.dye.preview.DyePreviewDisplayData;
import com.seggellion.britannia_mod.dye.service.MatchType;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Display-only authoritative preview data. Confirmation returns only {@link #sessionId()}. */
public record S2COpenDyePreviewPayload(
        UUID sessionId,
        DyePreviewDisplayData displayData,
        long lifetimeMillis) implements CustomPacketPayload {
    public static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath(
            BritanniaMod.MODID, "open_dye_preview");
    public static final Type<S2COpenDyePreviewPayload> TYPE = new Type<>(TYPE_ID);
    public static final StreamCodec<FriendlyByteBuf, S2COpenDyePreviewPayload> STREAM_CODEC = StreamCodec.of(
            S2COpenDyePreviewPayload::encode, S2COpenDyePreviewPayload::decode);

    public S2COpenDyePreviewPayload {
        java.util.Objects.requireNonNull(sessionId, "sessionId");
        java.util.Objects.requireNonNull(displayData, "displayData");
        if (lifetimeMillis <= 0) {
            throw new IllegalArgumentException("lifetimeMillis must be positive");
        }
    }

    private static void encode(FriendlyByteBuf buffer, S2COpenDyePreviewPayload payload) {
        DyePreviewDisplayData data = payload.displayData;
        buffer.writeUUID(payload.sessionId);
        buffer.writeUtf(data.bannerNameKey());
        buffer.writeUtf(data.materialNameKey());
        buffer.writeUtf(data.mountNameKey());
        buffer.writeUtf(data.currentColourNameKey());
        buffer.writeBoolean(data.currentPigmentNameKey().isPresent());
        data.currentPigmentNameKey().ifPresent(buffer::writeUtf);
        buffer.writeUtf(data.tubPigmentNameKey());
        buffer.writeUtf(data.newColourNameKey());
        buffer.writeEnum(data.matchType());
        buffer.writeDouble(data.perceptualDistance());
        buffer.writeInt(data.currentSrgb());
        buffer.writeInt(data.newSrgb());
        buffer.writeBoolean(data.placeholder());
        buffer.writeBoolean(data.provisionalDimensions());
        buffer.writeVarLong(payload.lifetimeMillis);
    }

    private static S2COpenDyePreviewPayload decode(FriendlyByteBuf buffer) {
        UUID sessionId = buffer.readUUID();
        String banner = buffer.readUtf();
        String material = buffer.readUtf();
        String mount = buffer.readUtf();
        String currentColour = buffer.readUtf();
        Optional<String> currentPigment = buffer.readBoolean() ? Optional.of(buffer.readUtf()) : Optional.empty();
        String tubPigment = buffer.readUtf();
        String newColour = buffer.readUtf();
        MatchType matchType = buffer.readEnum(MatchType.class);
        double distance = buffer.readDouble();
        int currentSrgb = buffer.readInt();
        int newSrgb = buffer.readInt();
        boolean placeholder = buffer.readBoolean();
        boolean provisional = buffer.readBoolean();
        long lifetime = buffer.readVarLong();
        return new S2COpenDyePreviewPayload(sessionId,
                new DyePreviewDisplayData(banner, material, mount, currentColour, currentPigment,
                        tubPigment, newColour, matchType, distance, currentSrgb, newSrgb,
                        placeholder, provisional), lifetime);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
