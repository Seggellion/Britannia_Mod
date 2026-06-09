package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TriggerQuestS2CPayload(long questId, String triggerKey) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TriggerQuestS2CPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "trigger_quest_s2c"));

    public static final StreamCodec<FriendlyByteBuf, TriggerQuestS2CPayload> STREAM_CODEC = StreamCodec.composite(
        net.minecraft.network.codec.ByteBufCodecs.VAR_LONG, TriggerQuestS2CPayload::questId,
        net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, TriggerQuestS2CPayload::triggerKey,
        TriggerQuestS2CPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}