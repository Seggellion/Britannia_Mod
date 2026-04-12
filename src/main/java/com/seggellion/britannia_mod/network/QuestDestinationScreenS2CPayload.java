package com.seggellion.britannia_mod.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record QuestDestinationScreenS2CPayload(
        BlockPos pos,
        String cityName
) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "quest_destination_screen");

    public static final Type<QuestDestinationScreenS2CPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, QuestDestinationScreenS2CPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public QuestDestinationScreenS2CPayload decode(FriendlyByteBuf buf) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    String cityName = ByteBufCodecs.STRING_UTF8.decode(buf);
                    return new QuestDestinationScreenS2CPayload(pos, cityName);
                }

                @Override
                public void encode(FriendlyByteBuf buf, QuestDestinationScreenS2CPayload payload) {
                    BlockPos.STREAM_CODEC.encode(buf, payload.pos);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.cityName);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Helper to easily send this to a specific player
    public static void send(ServerPlayer player, BlockPos pos, String cityName) {
        player.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(
            new QuestDestinationScreenS2CPayload(pos, cityName)
        ));
    }
}