package com.seggellion.britannia_mod.network.payload;

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

public record QuestGiverSpawnScreenS2CPayload(
        BlockPos pos,
        String npcName,
        String cityName,
        String customApiId,
        String gender
) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "quest_giver_spawn_screen");
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Type<QuestGiverSpawnScreenS2CPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, QuestGiverSpawnScreenS2CPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public QuestGiverSpawnScreenS2CPayload decode(FriendlyByteBuf buf) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    String npcName = ByteBufCodecs.STRING_UTF8.decode(buf);
                    String cityName = ByteBufCodecs.STRING_UTF8.decode(buf);
                    String customApiId = ByteBufCodecs.STRING_UTF8.decode(buf);
                    String gender = ByteBufCodecs.STRING_UTF8.decode(buf); 
                    return new QuestGiverSpawnScreenS2CPayload(pos, npcName, cityName, customApiId, gender);
                }

                @Override
                public void encode(FriendlyByteBuf buf, QuestGiverSpawnScreenS2CPayload payload) {
                    BlockPos.STREAM_CODEC.encode(buf, payload.pos);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.npcName);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.cityName);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.customApiId);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.gender); 
                }
            };

    public static void send(ServerPlayer player, BlockPos pos, String npcName, String cityName, String customApiId, String gender) {
        LOGGER.info("Sent QuestGiverSpawnScreenS2CPayload");
        NetworkHandler.sendToPlayer(
                player,
                new QuestGiverSpawnScreenS2CPayload(pos, npcName, cityName, customApiId, gender)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}