package com.seggellion.britannia_mod.network.payload.banner;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Display-only synchronization of the server-owned runtime placement preference. */
public record S2CBannerPlacementOrientationPayload(BannerOrientation orientation)
        implements CustomPacketPayload {
    public static final Type<S2CBannerPlacementOrientationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "banner_placement_orientation"));
    public static final StreamCodec<FriendlyByteBuf, S2CBannerPlacementOrientationPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeEnum(payload.orientation),
                    buffer -> new S2CBannerPlacementOrientationPayload(buffer.readEnum(BannerOrientation.class)));

    public S2CBannerPlacementOrientationPayload {
        java.util.Objects.requireNonNull(orientation, "orientation");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
