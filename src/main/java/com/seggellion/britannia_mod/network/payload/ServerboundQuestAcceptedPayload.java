package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.quest.ClientQuestEntry;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerboundQuestAcceptedPayload(ClientQuestEntry quest) implements CustomPacketPayload {
    public static final Type<ServerboundQuestAcceptedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "quest_accepted"));

    public static final StreamCodec<FriendlyByteBuf, ServerboundQuestAcceptedPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> QuestEntryCodecs.writeEntry(buf, payload.quest()),
            buf -> new ServerboundQuestAcceptedPayload(QuestEntryCodecs.readEntry(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
