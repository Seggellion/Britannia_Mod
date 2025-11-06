package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.network.NetworkHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;


public record TraderSpawnScreenS2CPayload(
        BlockPos pos,
        String traderType,
        String cityName,
        int townPersonAmount
) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "trader_spawn_screen");
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Type<TraderSpawnScreenS2CPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, TraderSpawnScreenS2CPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public TraderSpawnScreenS2CPayload decode(FriendlyByteBuf buf) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    String traderType = ByteBufCodecs.STRING_UTF8.decode(buf);
                    String cityName = ByteBufCodecs.STRING_UTF8.decode(buf);
                    int townPersonAmount = ByteBufCodecs.VAR_INT.decode(buf);
                    return new TraderSpawnScreenS2CPayload(pos, traderType, cityName, townPersonAmount);
                }

                @Override
                public void encode(FriendlyByteBuf buf, TraderSpawnScreenS2CPayload payload) {
                    BlockPos.STREAM_CODEC.encode(buf, payload.pos);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.traderType);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.cityName);
                    ByteBufCodecs.VAR_INT.encode(buf, payload.townPersonAmount);
                }
            };

 public static void send(ServerPlayer player,
                            BlockPos pos,
                            String traderType,
                            String cityName,
                            int townPersonAmount) {
                                        LOGGER.info("Sent TraderSpawnScreenS2CPayload");
        NetworkHandler.sendToPlayer(
                player,
                new TraderSpawnScreenS2CPayload(pos, traderType, cityName, townPersonAmount)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
