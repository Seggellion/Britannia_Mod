package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record QuestTriggerResultS2CPayload(String responseJson, long questId, String triggerKey) implements CustomPacketPayload {
    public static final Type<QuestTriggerResultS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "quest_trigger_result"));

    public static final StreamCodec<FriendlyByteBuf, QuestTriggerResultS2CPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, QuestTriggerResultS2CPayload::responseJson,
            ByteBufCodecs.VAR_LONG, QuestTriggerResultS2CPayload::questId,
            ByteBufCodecs.STRING_UTF8, QuestTriggerResultS2CPayload::triggerKey,
            QuestTriggerResultS2CPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
