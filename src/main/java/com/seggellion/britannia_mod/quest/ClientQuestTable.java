package com.seggellion.britannia_mod.quest;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client-side accepted quest mirror, refreshed from server sync payloads. */
public final class ClientQuestTable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, ClientQuestEntry> QUESTS = new LinkedHashMap<>();

    private ClientQuestTable() {}

    public static synchronized void replaceFromBootstrap(Collection<ClientQuestEntry> quests) {
        QUESTS.clear();
        if (quests == null) return;

        for (ClientQuestEntry quest : quests) {
            addInternal(quest, "bootstrap/full sync");
        }
    }

    public static synchronized void addFromRailsAcceptSuccess(ClientQuestEntry quest) {
        addInternal(quest, "Rails accept success");
    }

    public static synchronized void removeAfterRailsQuitSuccess(String questStateId) {
        if (questStateId == null || questStateId.isBlank()) return;
        QUESTS.remove(questStateId.trim());
    }

    public static synchronized void removeAfterRailsCompletionSuccessByQuestId(String questId) {
        if (questId == null || questId.isBlank()) return;
        String normalizedId = questId.trim();
        QUESTS.entrySet().removeIf(entry -> normalizedId.equals(entry.getValue().questId()));
    }

    public static synchronized ClientQuestEntry findByQuestId(String questId) {
        if (questId == null || questId.isBlank()) return null;
        String normalizedId = questId.trim();
        for (ClientQuestEntry quest : QUESTS.values()) {
            if (normalizedId.equals(quest.questId())) {
                return quest;
            }
        }
        return null;
    }

    public static synchronized List<ClientQuestEntry> snapshot() {
        return List.copyOf(new ArrayList<>(QUESTS.values()));
    }

    public static synchronized void clear() {
        QUESTS.clear();
    }

    private static void addInternal(ClientQuestEntry quest, String source) {
        if (quest == null) return;
        if (!quest.hasKey()) {
            LOGGER.warn("Rejected non-authoritative quest journal entry without questStateId source={} questId={} questKey={}",
                    source, quest.questId(), quest.questKey());
            return;
        }
        QUESTS.put(quest.storageKey(), quest);
    }
}
