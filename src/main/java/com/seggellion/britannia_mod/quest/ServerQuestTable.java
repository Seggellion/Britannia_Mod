package com.seggellion.britannia_mod.quest;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;

/** In-memory per-player accepted quest mirror. Rails remains the source of truth. */
public final class ServerQuestTable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Map<String, ClientQuestEntry>> QUESTS_BY_PLAYER = new ConcurrentHashMap<>();

    private ServerQuestTable() {}

    public static void replaceFromBootstrap(ServerPlayer player, Collection<ClientQuestEntry> quests) {
        if (player == null) return;

        Map<String, ClientQuestEntry> updated = new LinkedHashMap<>();
        if (quests != null) {
            for (ClientQuestEntry quest : quests) {
                if (quest == null) continue;
                if (!quest.hasKey()) {
                    LOGGER.warn("Rejected non-authoritative quest journal entry without questStateId source=BOOTSTRAP player={} questId={} questKey={}",
                            player.getStringUUID(), quest.questId(), quest.questKey());
                    continue;
                }
                updated.put(quest.storageKey(), quest);
            }
        }
        QUESTS_BY_PLAYER.put(player.getUUID(), updated);
    }

    public static void addFromRailsAcceptSuccess(ServerPlayer player, ClientQuestEntry quest) {
        if (player == null || quest == null) return;
        if (!quest.hasKey()) {
            LOGGER.warn("Rejected non-authoritative quest journal entry without questStateId source=RAILS_ACCEPT_SUCCESS player={} questId={} questKey={}",
                    player.getStringUUID(), quest.questId(), quest.questKey());
            return;
        }

        QUESTS_BY_PLAYER.compute(player.getUUID(), (uuid, existing) -> {
            Map<String, ClientQuestEntry> quests = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
            quests.put(quest.storageKey(), quest);
            return quests;
        });
    }

    public static boolean hasActiveQuestState(ServerPlayer player, String questStateId) {
        if (player == null || questStateId == null || questStateId.isBlank()) return false;
        Map<String, ClientQuestEntry> quests = QUESTS_BY_PLAYER.get(player.getUUID());
        return quests != null && quests.containsKey(questStateId.trim());
    }

    public static boolean hasActiveQuestId(ServerPlayer player, long questId) {
        if (player == null || questId <= 0) return false;
        Map<String, ClientQuestEntry> quests = QUESTS_BY_PLAYER.get(player.getUUID());
        return quests != null && quests.values().stream().anyMatch(entry -> {
            try { return Long.parseLong(entry.questId()) == questId; }
            catch (NumberFormatException ignored) { return false; }
        });
    }

    public static ClientQuestEntry findByQuestId(ServerPlayer player, long questId) {
        if (player == null || questId <= 0) return null;
        Map<String, ClientQuestEntry> quests = QUESTS_BY_PLAYER.get(player.getUUID());
        if (quests == null) return null;
        return quests.values().stream().filter(entry -> {
            try { return Long.parseLong(entry.questId()) == questId; }
            catch (NumberFormatException ignored) { return false; }
        }).findFirst().orElse(null);
    }

    public static void removeAfterRailsCompletionSuccessByQuestId(ServerPlayer player, long questId) {
        if (player == null || questId <= 0) return;
        QUESTS_BY_PLAYER.computeIfPresent(player.getUUID(), (uuid, existing) -> {
            Map<String, ClientQuestEntry> quests = new LinkedHashMap<>(existing);
            quests.values().removeIf(entry -> {
                try { return Long.parseLong(entry.questId()) == questId; }
                catch (NumberFormatException ignored) { return false; }
            });
            return quests;
        });
    }

    public static ClientQuestEntry get(ServerPlayer player, String questStateId) {
        if (player == null || questStateId == null || questStateId.isBlank()) return null;
        Map<String, ClientQuestEntry> quests = QUESTS_BY_PLAYER.get(player.getUUID());
        return quests != null ? quests.get(questStateId.trim()) : null;
    }

    public static boolean hasActiveQuestState(UUID playerUuid, String questStateId) {
        if (playerUuid == null) return false;
        Map<String, ClientQuestEntry> quests = QUESTS_BY_PLAYER.get(playerUuid);
        if (quests == null || quests.isEmpty()) return false;

        String normalizedStateId = clean(questStateId);
        return !normalizedStateId.isBlank() && quests.containsKey(normalizedStateId);
    }

    public static void removeAfterRailsQuitSuccess(ServerPlayer player, String questStateId) {
        if (player == null || questStateId == null || questStateId.isBlank()) return;

        QUESTS_BY_PLAYER.computeIfPresent(player.getUUID(), (uuid, existing) -> {
            Map<String, ClientQuestEntry> quests = new LinkedHashMap<>(existing);
            quests.remove(questStateId.trim());
            return quests;
        });
    }

    public static List<ClientQuestEntry> snapshot(ServerPlayer player) {
        if (player == null) return List.of();
        Map<String, ClientQuestEntry> quests = QUESTS_BY_PLAYER.get(player.getUUID());
        if (quests == null || quests.isEmpty()) return List.of();
        return List.copyOf(new ArrayList<>(quests.values()));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
