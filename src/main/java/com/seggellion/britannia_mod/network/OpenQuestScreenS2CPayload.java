package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// CHANGED: questId is now a long
public record OpenQuestScreenS2CPayload(long questId, String triggerKey) implements CustomPacketPayload {
    
    public static final Type<OpenQuestScreenS2CPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "open_quest_screen"));

    // CHANGED: We now use ByteBufCodecs.VAR_LONG for the questId serialization
    public static final StreamCodec<FriendlyByteBuf, OpenQuestScreenS2CPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG, OpenQuestScreenS2CPayload::questId,
        ByteBufCodecs.STRING_UTF8, OpenQuestScreenS2CPayload::triggerKey,
        OpenQuestScreenS2CPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}