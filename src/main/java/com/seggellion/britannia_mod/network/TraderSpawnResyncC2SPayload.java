package com.seggellion.britannia_mod.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TraderSpawnResyncC2SPayload(BlockPos pos) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "trader_spawn_resync");

    public static final Type<TraderSpawnResyncC2SPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, TraderSpawnResyncC2SPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public TraderSpawnResyncC2SPayload decode(FriendlyByteBuf buf) {
                    return new TraderSpawnResyncC2SPayload(BlockPos.STREAM_CODEC.decode(buf));
                }

                @Override
                public void encode(FriendlyByteBuf buf, TraderSpawnResyncC2SPayload payload) {
                    BlockPos.STREAM_CODEC.encode(buf, payload.pos);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
