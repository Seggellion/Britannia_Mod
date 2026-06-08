package com.seggellion.britannia_mod.quest;

import com.seggellion.britannia_mod.network.payload.ClientboundSyncQuestsPayload;
import com.seggellion.britannia_mod.quest.network.QuestServerAPI;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ServerQuestService {
    private ServerQuestService() {}

    public static void quitQuest(ServerPlayer player, String questStateId) {
        if (player == null || questStateId == null || questStateId.isBlank()) return;

        String normalizedId = questStateId.trim();
        if (!ServerQuestTable.hasActiveQuestState(player, normalizedId)) {
            player.sendSystemMessage(Component.literal("That quest is no longer active."));
            ClientboundSyncQuestsPayload.send(player, ServerQuestTable.snapshot(player));
            return;
        }

        QuestServerAPI.quitQuest(player.server, player.getStringUUID(), normalizedId, result -> {
            if (result.success()) {
                ClientQuestEntry quest = ServerQuestTable.get(player, normalizedId);
                QuestCleanupService.cleanupAfterQuestQuit(player, quest);
                ServerQuestTable.removeAfterRailsQuitSuccess(player, normalizedId);
                ClientboundSyncQuestsPayload.send(player, ServerQuestTable.snapshot(player));
                player.sendSystemMessage(Component.literal(result.messageOr("Quest quit.")));
            } else {
                player.sendSystemMessage(Component.literal(result.messageOr("That quest could not be quit.")));
            }
        });
    }
}
