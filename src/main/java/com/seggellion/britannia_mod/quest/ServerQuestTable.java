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

    /**
     * What this mirror can honestly say about one quest state.
     *
     * <p>The distinction that matters is {@link #UNKNOWN}. This table is RAM only and is filled
     * exclusively by the login bootstrap, which is allowed to fail. Between server start and a
     * player's bootstrap landing -- and for the whole session if that bootstrap failed -- the
     * table knows nothing about them. <b>"I have no record" is not "the quest is over"</b>, and a
     * caller that destroys persisted state must never conflate the two.
     */
    public enum JournalState {
        /** The journal is loaded for this player and lists this quest state. */
        ACTIVE,
        /** The journal is loaded for this player and does not list this quest state. */
        INACTIVE,
        /** The journal has not loaded for this player; nothing can be concluded. */
        UNKNOWN
    }

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
        return player != null && hasActiveQuestState(player.getUUID(), questStateId);
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
        return questStateStatus(playerUuid, questStateId) == JournalState.ACTIVE;
    }

    /**
     * The three-state answer. Prefer this over {@link #hasActiveQuestState} wherever a negative
     * answer would cause persisted state to be destroyed: a boolean cannot distinguish "this
     * quest ended" from "this journal has not loaded yet", and treating the second as the first
     * deleted live escort assignments on every server restart.
     */
    public static JournalState questStateStatus(UUID playerUuid, String questStateId) {
        if (playerUuid == null) return JournalState.UNKNOWN;

        Map<String, ClientQuestEntry> quests = QUESTS_BY_PLAYER.get(playerUuid);
        if (quests == null) return JournalState.UNKNOWN;

        String normalizedStateId = clean(questStateId);
        if (normalizedStateId.isBlank()) return JournalState.INACTIVE;
        return quests.containsKey(normalizedStateId) ? JournalState.ACTIVE : JournalState.INACTIVE;
    }

    /**
     * Drops everything known about a player, returning their journal to UNKNOWN.
     *
     * <p>Called on logout. This was unsafe before Milestone 5 -- an UNKNOWN journal simply
     * refused every quest action -- and is correct now that a miss fetches the journal instead.
     */
    public static void forget(UUID playerUuid) {
        if (playerUuid != null) QUESTS_BY_PLAYER.remove(playerUuid);
    }

    /** The environmental objectives pending on this quest, or NONE when it has none. */
    public static QuestObjectiveTriggers triggersFor(UUID playerUuid, String questStateId) {
        if (playerUuid == null) return QuestObjectiveTriggers.NONE;
        Map<String, ClientQuestEntry> quests = QUESTS_BY_PLAYER.get(playerUuid);
        if (quests == null) return QuestObjectiveTriggers.NONE;
        ClientQuestEntry entry = quests.get(clean(questStateId));
        return entry == null ? QuestObjectiveTriggers.NONE : entry.triggers();
    }

    /**
     * Replaces one quest's objectives after Rails advanced its node.
     *
     * <p>Without this the server would keep seeing the objective it just completed and fire it
     * again on the next tick, which is the storm finding Q-06 describes -- the old client-side
     * check re-sent a location trigger every second for as long as the player stood in the zone.
     */
    public static void updateTriggers(ServerPlayer player, String questStateId,
                                      QuestObjectiveTriggers triggers) {
        if (player == null) return;
        String key = clean(questStateId);
        if (key.isBlank()) return;

        QUESTS_BY_PLAYER.computeIfPresent(player.getUUID(), (uuid, existing) -> {
            ClientQuestEntry entry = existing.get(key);
            if (entry == null) return existing;
            Map<String, ClientQuestEntry> quests = new LinkedHashMap<>(existing);
            quests.put(key, entry.withTriggers(triggers));
            return quests;
        });
    }

    /** Whether this player's journal has been loaded at all in this server run. */
    public static boolean journalLoaded(UUID playerUuid) {
        return playerUuid != null && QUESTS_BY_PLAYER.containsKey(playerUuid);
    }

    /**
     * How many quests this player's journal holds, or {@code -1} when it has never loaded.
     * Logged on every rejection so "the journal was empty" and "the journal was never fetched"
     * are distinguishable after the fact.
     */
    public static int journalSize(UUID playerUuid) {
        if (playerUuid == null) return -1;
        Map<String, ClientQuestEntry> quests = QUESTS_BY_PLAYER.get(playerUuid);
        return quests == null ? -1 : quests.size();
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
