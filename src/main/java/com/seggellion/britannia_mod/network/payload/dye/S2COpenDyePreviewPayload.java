package com.seggellion.britannia_mod.network.payload.dye;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.dye.preview.DyePreviewDisplayData;
import com.seggellion.britannia_mod.banner.renderdata.BannerPreviewRenderState;
import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
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
        BannerPreviewRenderState currentRenderState,
        BannerPreviewRenderState proposedRenderState,
        long lifetimeMillis) implements CustomPacketPayload {
    public static final int MAX_ENCODED_BYTES = 4 * 1024;
    public static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath(
            BritanniaMod.MODID, "open_dye_preview");
    public static final Type<S2COpenDyePreviewPayload> TYPE = new Type<>(TYPE_ID);
    public static final StreamCodec<FriendlyByteBuf, S2COpenDyePreviewPayload> STREAM_CODEC = StreamCodec.of(
            S2COpenDyePreviewPayload::encode, S2COpenDyePreviewPayload::decode);

    public S2COpenDyePreviewPayload {
        java.util.Objects.requireNonNull(sessionId, "sessionId");
        java.util.Objects.requireNonNull(displayData, "displayData");
        java.util.Objects.requireNonNull(currentRenderState, "currentRenderState");
        java.util.Objects.requireNonNull(proposedRenderState, "proposedRenderState");
        if (lifetimeMillis <= 0) {
            throw new IllegalArgumentException("lifetimeMillis must be positive");
        }
    }

    private static void encode(FriendlyByteBuf buffer, S2COpenDyePreviewPayload payload) {
        int startIndex = buffer.writerIndex();
        DyePreviewDisplayData data = payload.displayData;
        buffer.writeUUID(payload.sessionId);
        buffer.writeUtf(data.bannerNameKey(), DyePreviewDisplayData.MAX_TRANSLATION_KEY_LENGTH);
        buffer.writeUtf(data.materialNameKey(), DyePreviewDisplayData.MAX_TRANSLATION_KEY_LENGTH);
        buffer.writeUtf(data.mountNameKey(), DyePreviewDisplayData.MAX_TRANSLATION_KEY_LENGTH);
        buffer.writeUtf(data.currentColourNameKey(), DyePreviewDisplayData.MAX_TRANSLATION_KEY_LENGTH);
        buffer.writeBoolean(data.currentPigmentNameKey().isPresent());
        data.currentPigmentNameKey().ifPresent(
                key -> buffer.writeUtf(key, DyePreviewDisplayData.MAX_TRANSLATION_KEY_LENGTH));
        buffer.writeUtf(data.tubPigmentNameKey(), DyePreviewDisplayData.MAX_TRANSLATION_KEY_LENGTH);
        buffer.writeUtf(data.newColourNameKey(), DyePreviewDisplayData.MAX_TRANSLATION_KEY_LENGTH);
        buffer.writeEnum(data.matchType());
        buffer.writeDouble(data.perceptualDistance());
        buffer.writeInt(data.currentSrgb());
        buffer.writeInt(data.newSrgb());
        buffer.writeBoolean(data.placeholder());
        buffer.writeBoolean(data.provisionalDimensions());
        writeRenderState(buffer, payload.currentRenderState);
        writeRenderState(buffer, payload.proposedRenderState);
        buffer.writeVarLong(payload.lifetimeMillis);
        int encodedBytes = buffer.writerIndex() - startIndex;
        if (encodedBytes > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Dye-preview payload exceeds "
                    + MAX_ENCODED_BYTES + " bytes: " + encodedBytes);
        }
    }

    private static S2COpenDyePreviewPayload decode(FriendlyByteBuf buffer) {
        UUID sessionId = buffer.readUUID();
        String banner = buffer.readUtf(DyePreviewDisplayData.MAX_TRANSLATION_KEY_LENGTH);
        String material = buffer.readUtf(DyePreviewDisplayData.MAX_TRANSLATION_KEY_LENGTH);
        String mount = buffer.readUtf(DyePreviewDisplayData.MAX_TRANSLATION_KEY_LENGTH);
        String currentColour = buffer.readUtf(DyePreviewDisplayData.MAX_TRANSLATION_KEY_LENGTH);
        Optional<String> currentPigment = buffer.readBoolean()
                ? Optional.of(buffer.readUtf(DyePreviewDisplayData.MAX_TRANSLATION_KEY_LENGTH))
                : Optional.empty();
        String tubPigment = buffer.readUtf(DyePreviewDisplayData.MAX_TRANSLATION_KEY_LENGTH);
        String newColour = buffer.readUtf(DyePreviewDisplayData.MAX_TRANSLATION_KEY_LENGTH);
        MatchType matchType = buffer.readEnum(MatchType.class);
        double distance = buffer.readDouble();
        int currentSrgb = buffer.readInt();
        int newSrgb = buffer.readInt();
        boolean placeholder = buffer.readBoolean();
        boolean provisional = buffer.readBoolean();
        BannerPreviewRenderState currentRenderState = readRenderState(buffer);
        BannerPreviewRenderState proposedRenderState = readRenderState(buffer);
        long lifetime = buffer.readVarLong();
        return new S2COpenDyePreviewPayload(sessionId,
                new DyePreviewDisplayData(banner, material, mount, currentColour, currentPigment,
                        tubPigment, newColour, matchType, distance, currentSrgb, newSrgb,
                        placeholder, provisional), currentRenderState, proposedRenderState, lifetime);
    }

    private static void writeRenderState(FriendlyByteBuf buffer, BannerPreviewRenderState state) {
        buffer.writeResourceLocation(state.bannerDefinitionId().value());
        buffer.writeResourceLocation(state.materialId().value());
        buffer.writeResourceLocation(state.resolvedColourId().value());
        buffer.writeBoolean(state.sourcePigmentId().isPresent());
        state.sourcePigmentId().ifPresent(id -> buffer.writeResourceLocation(id.value()));
        buffer.writeResourceLocation(state.mountId().value());
    }

    private static BannerPreviewRenderState readRenderState(FriendlyByteBuf buffer) {
        return new BannerPreviewRenderState(
                new BannerDefinitionId(buffer.readResourceLocation()),
                new FabricMaterialId(buffer.readResourceLocation()),
                new ResolvedColourId(buffer.readResourceLocation()),
                buffer.readBoolean()
                        ? Optional.of(new PigmentId(buffer.readResourceLocation()))
                        : Optional.empty(),
                new MountId(buffer.readResourceLocation()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
