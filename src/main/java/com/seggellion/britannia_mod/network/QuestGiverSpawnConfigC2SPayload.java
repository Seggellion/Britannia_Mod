package com.seggellion.britannia_mod.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record QuestGiverSpawnConfigC2SPayload(
        BlockPos pos,
        String npcName,
        String cityName,
        String customApiId,
        String gender
) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "quest_giver_spawn_config");

    public static final Type<QuestGiverSpawnConfigC2SPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, QuestGiverSpawnConfigC2SPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public QuestGiverSpawnConfigC2SPayload decode(FriendlyByteBuf buf) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    String npcName = ByteBufCodecs.STRING_UTF8.decode(buf);
                    String cityName = ByteBufCodecs.STRING_UTF8.decode(buf);
                    String customApiId = ByteBufCodecs.STRING_UTF8.decode(buf);
                    String gender = ByteBufCodecs.STRING_UTF8.decode(buf); // NEW: Decode gender
                    return new QuestGiverSpawnConfigC2SPayload(pos, npcName, cityName, customApiId, gender);
                }

                @Override
                public void encode(FriendlyByteBuf buf, QuestGiverSpawnConfigC2SPayload payload) {
                    BlockPos.STREAM_CODEC.encode(buf, payload.pos);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.npcName);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.cityName);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.customApiId);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.gender); // NEW: Encode gender
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}