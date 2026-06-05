package com.seggellion.britannia_mod.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MerchantSpawnConfigC2SPayload(
        BlockPos pos,
        String merchantType,
        String cityName,
        int townPersonAmount
) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "merchant_spawn_config");

    public static final Type<MerchantSpawnConfigC2SPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, MerchantSpawnConfigC2SPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MerchantSpawnConfigC2SPayload decode(FriendlyByteBuf buf) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    String merchantType = ByteBufCodecs.STRING_UTF8.decode(buf);
                    String cityName = ByteBufCodecs.STRING_UTF8.decode(buf);
                    int townPersonAmount = ByteBufCodecs.VAR_INT.decode(buf);
                    return new MerchantSpawnConfigC2SPayload(pos, merchantType, cityName, townPersonAmount);
                }

                @Override
                public void encode(FriendlyByteBuf buf, MerchantSpawnConfigC2SPayload payload) {
                    BlockPos.STREAM_CODEC.encode(buf, payload.pos);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.merchantType);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.cityName);
                    ByteBufCodecs.VAR_INT.encode(buf, payload.townPersonAmount);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
