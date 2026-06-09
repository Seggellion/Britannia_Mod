package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.quest.ClientQuestEntry;

import net.minecraft.network.FriendlyByteBuf;

final class QuestEntryCodecs {
    private QuestEntryCodecs() {}

    static void writeEntry(FriendlyByteBuf buf, ClientQuestEntry entry) {
        buf.writeUtf(entry.questStateId());
        buf.writeUtf(entry.questId());
        buf.writeUtf(entry.questKey());
        buf.writeUtf(entry.questGiverName());
        buf.writeUtf(entry.name());
        buf.writeUtf(entry.briefDescription());
        buf.writeUtf(entry.acceptedAt());
        buf.writeUtf(entry.status());
    }

    static ClientQuestEntry readEntry(FriendlyByteBuf buf) {
        return new ClientQuestEntry(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf()
        );
    }
}
