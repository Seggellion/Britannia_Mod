package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.quest.ClientQuestEntry;
import com.seggellion.britannia_mod.quest.ClientQuestTable;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public record ClientboundSyncQuestsPayload(List<ClientQuestEntry> quests) implements CustomPacketPayload {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Type<ClientboundSyncQuestsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "sync_quests"));

    public static final StreamCodec<FriendlyByteBuf, ClientboundSyncQuestsPayload> STREAM_CODEC = StreamCodec.of(
            ClientboundSyncQuestsPayload::encode,
            ClientboundSyncQuestsPayload::decode
    );

    private static void encode(FriendlyByteBuf buf, ClientboundSyncQuestsPayload payload) {
        buf.writeVarInt(payload.quests().size());
        for (ClientQuestEntry quest : payload.quests()) {
            QuestEntryCodecs.writeEntry(buf, quest);
        }
    }

    private static ClientboundSyncQuestsPayload decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<ClientQuestEntry> quests = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            quests.add(QuestEntryCodecs.readEntry(buf));
        }
        return new ClientboundSyncQuestsPayload(quests);
    }

    public static void handle(ClientboundSyncQuestsPayload payload) {
        LOGGER.info("Replacing client quest table from authoritative server sync count={}", payload.quests().size());
        ClientQuestTable.replaceFromBootstrap(payload.quests());
    }

    public static void send(ServerPlayer player, List<ClientQuestEntry> quests) {
        PacketDistributor.sendToPlayer(player, new ClientboundSyncQuestsPayload(quests));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
