package com.seggellion.britannia_mod.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TraderSpawnConfigC2SPayload(
        BlockPos pos,
        String traderType,
        String cityName,
        int townPersonAmount
) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "trader_spawn_config");

    public static final Type<TraderSpawnConfigC2SPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, TraderSpawnConfigC2SPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public TraderSpawnConfigC2SPayload decode(FriendlyByteBuf buf) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    String traderType = ByteBufCodecs.STRING_UTF8.decode(buf);
                    String cityName = ByteBufCodecs.STRING_UTF8.decode(buf);
                    int townPersonAmount = ByteBufCodecs.VAR_INT.decode(buf);
                    return new TraderSpawnConfigC2SPayload(pos, traderType, cityName, townPersonAmount);
                }

                @Override
                public void encode(FriendlyByteBuf buf, TraderSpawnConfigC2SPayload payload) {
                    BlockPos.STREAM_CODEC.encode(buf, payload.pos);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.traderType);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.cityName);
                    ByteBufCodecs.VAR_INT.encode(buf, payload.townPersonAmount);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
