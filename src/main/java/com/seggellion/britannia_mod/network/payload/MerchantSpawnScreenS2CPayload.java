package com.seggellion.britannia_mod.network.payload;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.network.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public record MerchantSpawnScreenS2CPayload(
        BlockPos pos,
        String merchantType,
        String cityName,
        int townPersonAmount
) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "merchant_spawn_screen");
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Type<MerchantSpawnScreenS2CPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, MerchantSpawnScreenS2CPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MerchantSpawnScreenS2CPayload decode(FriendlyByteBuf buf) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    String merchantType = ByteBufCodecs.STRING_UTF8.decode(buf);
                    String cityName = ByteBufCodecs.STRING_UTF8.decode(buf);
                    int townPersonAmount = ByteBufCodecs.VAR_INT.decode(buf);
                    return new MerchantSpawnScreenS2CPayload(pos, merchantType, cityName, townPersonAmount);
                }

                @Override
                public void encode(FriendlyByteBuf buf, MerchantSpawnScreenS2CPayload payload) {
                    BlockPos.STREAM_CODEC.encode(buf, payload.pos);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.merchantType);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.cityName);
                    ByteBufCodecs.VAR_INT.encode(buf, payload.townPersonAmount);
                }
            };

    public static void send(ServerPlayer player, BlockPos pos, String merchantType, String cityName, int townPersonAmount) {
        LOGGER.info("Sent MerchantSpawnScreenS2CPayload");
        NetworkHandler.sendToPlayer(
                player,
                new MerchantSpawnScreenS2CPayload(pos, merchantType, cityName, townPersonAmount)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
