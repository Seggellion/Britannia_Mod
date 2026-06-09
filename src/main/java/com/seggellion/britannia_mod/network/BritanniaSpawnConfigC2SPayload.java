package com.seggellion.britannia_mod.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BritanniaSpawnConfigC2SPayload(
        BlockPos pos,
        ResourceLocation entityId,
        int radius,
        int minTicks,
        int maxTicks,
        boolean nightOnly,
        int maxEntities
) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "britannia_spawn_config");

    public static final Type<BritanniaSpawnConfigC2SPayload> TYPE = new Type<>(TYPE_ID);

    // manual codec because we have 7 fields (composite only supports up to 6)
    public static final StreamCodec<FriendlyByteBuf, BritanniaSpawnConfigC2SPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public BritanniaSpawnConfigC2SPayload decode(FriendlyByteBuf buf) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buf);
                    int radius = ByteBufCodecs.VAR_INT.decode(buf);
                    int minTicks = ByteBufCodecs.VAR_INT.decode(buf);
                    int maxTicks = ByteBufCodecs.VAR_INT.decode(buf);
                    boolean nightOnly = ByteBufCodecs.BOOL.decode(buf);
                    int maxEntities = ByteBufCodecs.VAR_INT.decode(buf);
                    return new BritanniaSpawnConfigC2SPayload(pos, id, radius, minTicks, maxTicks, nightOnly, maxEntities);
                }

                @Override
                public void encode(FriendlyByteBuf buf, BritanniaSpawnConfigC2SPayload payload) {
                    BlockPos.STREAM_CODEC.encode(buf, payload.pos);
                    ResourceLocation.STREAM_CODEC.encode(buf, payload.entityId);
                    ByteBufCodecs.VAR_INT.encode(buf, payload.radius);
                    ByteBufCodecs.VAR_INT.encode(buf, payload.minTicks);
                    ByteBufCodecs.VAR_INT.encode(buf, payload.maxTicks);
                    ByteBufCodecs.BOOL.encode(buf, payload.nightOnly);
                    ByteBufCodecs.VAR_INT.encode(buf, payload.maxEntities);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
