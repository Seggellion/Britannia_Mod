package com.seggellion.britannia_mod.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record QuestDestinationConfigC2SPayload(
        BlockPos pos,
        String cityName
) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "quest_destination_config");

    public static final Type<QuestDestinationConfigC2SPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, QuestDestinationConfigC2SPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public QuestDestinationConfigC2SPayload decode(FriendlyByteBuf buf) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    String cityName = ByteBufCodecs.STRING_UTF8.decode(buf);
                    return new QuestDestinationConfigC2SPayload(pos, cityName);
                }

                @Override
                public void encode(FriendlyByteBuf buf, QuestDestinationConfigC2SPayload payload) {
                    BlockPos.STREAM_CODEC.encode(buf, payload.pos);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.cityName);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}