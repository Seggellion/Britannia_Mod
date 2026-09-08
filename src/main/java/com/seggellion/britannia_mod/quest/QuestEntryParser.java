package com.seggellion.britannia_mod.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class QuestEntryParser {
    private static final Logger LOGGER = LogUtils.getLogger();

    private QuestEntryParser() {}

    public static List<ClientQuestEntry> parseAcceptedQuests(JsonObject root) {
        if (root == null) return List.of();

        List<ClientQuestEntry> entries = new ArrayList<>();

        JsonObject acceptedQuest = object(root, "accepted_quest");
        if (acceptedQuest != null) {
            addEntry(entries, acceptedQuest, "", "BOOTSTRAP");
            return entries;
        }

        JsonArray quests = firstArray(root, "accepted_quests", "current_quests", "active_quests");
        if (quests != null) {
            for (JsonElement element : quests) {
                if (element != null && element.isJsonObject()) {
                    addEntry(entries, element.getAsJsonObject(), "", "BOOTSTRAP");
                }
            }
            return entries;
        }

        return entries;
    }

    public static List<ClientQuestEntry> parseRailsAcceptSuccess(JsonObject root, String fallbackQuestGiverName) {
        if (root == null) return List.of();

        JsonObject acceptedQuest = object(root, "accepted_quest");
        if (acceptedQuest == null) {
            return List.of();
        }

        List<ClientQuestEntry> entries = new ArrayList<>();
        addEntry(entries, acceptedQuest, fallbackQuestGiverName, "RAILS_ACCEPT_SUCCESS");
        return entries;
    }

    private static ClientQuestEntry fromJson(JsonObject quest, String fallbackQuestGiverName, String source) {
        if (quest == null) return new ClientQuestEntry("", "", "", "", "", "", "", "");

        JsonObject entry = object(quest, "accepted_quest");
        if (entry == null) {
            entry = quest;
        }

        JsonObject nestedQuest = object(entry, "quest");
        String questStateId = firstString(entry,
                "quest_state_id", "player_quest_state_id", "accepted_quest_id");
        String questId = firstString(entry, "quest_id", "quest_definition_id", "definition_id");
        String questKey = firstString(entry, "quest_key", "key", "slug", "npc_api_id", "source_npc_id");
        String questGiverName = firstString(entry, "quest_giver_name", "npc_name", "giver_name", "quest_giver");
        String name = firstString(entry, "name", "title", "quest_name", "quest_title");
        String brief = firstString(entry, "brief_description", "description", "summary", "text", "body");
        String acceptedAt = firstString(entry, "accepted_at", "created_at", "started_at", "updated_at");
        String status = firstString(entry, "status", "state");

        if (nestedQuest != null) {
            if (isBlank(questId)) {
                questId = firstString(nestedQuest, "quest_id", "id");
            }
            if (isBlank(questKey)) {
                questKey = firstString(nestedQuest, "quest_key", "key", "slug", "npc_api_id", "source_npc_id");
            }
            if (isBlank(questGiverName)) {
                questGiverName = firstString(nestedQuest, "quest_giver_name", "npc_name", "giver_name", "quest_giver");
            }
            if (isBlank(name)) {
                name = firstString(nestedQuest, "name", "title", "quest_name", "quest_title");
            }
            if (isBlank(brief)) {
                brief = firstString(nestedQuest, "brief_description", "description", "summary", "text", "body");
            }
        }

        if (isBlank(questGiverName)) {
            questGiverName = fallbackQuestGiverName;
        }
        if (isBlank(questGiverName) && !isBlank(questStateId)) {
            LOGGER.warn("Accepted quest entry missing quest_giver_name source={} quest_state_id={} quest_id={} quest_key={}",
                    source, questStateId, questId, questKey);
        }
        if (isBlank(questKey)) {
            questKey = questId;
        }

        // Milestone 8: the journal fields the entry has carried since M4 -- stage, objective,
        // progress, rewards_preview, keep_items, claim_pending -- are read here alongside the
        // objectives. Two separate readers on purpose: triggers stays server-only, and detail is
        // the only half QuestEntryCodecs writes to a client.
        return new ClientQuestEntry(questStateId, questId, questKey, questGiverName, name, brief,
                acceptedAt, status, QuestObjectiveTriggers.fromJournalEntry(entry))
                .withDetail(ClientQuestEntry.JournalDetail.fromJournalEntry(entry));
    }

    private static void addEntry(List<ClientQuestEntry> entries, JsonObject quest, String fallbackQuestGiverName, String source) {
        ClientQuestEntry entry = fromJson(quest, fallbackQuestGiverName, source);
        if (entry.hasKey()) {
            entries.add(entry);
        } else {
            LOGGER.warn("Rejected non-authoritative quest journal entry without questStateId source={} questId={} questKey={}",
                    source, entry.questId(), entry.questKey());
        }
    }

    private static JsonArray firstArray(JsonObject root, String... keys) {
        for (String key : keys) {
            if (root.has(key) && root.get(key).isJsonArray()) {
                return root.getAsJsonArray(key);
            }
        }
        return null;
    }

    private static JsonObject object(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : null;
    }

    private static String firstString(JsonObject root, String... keys) {
        for (String key : keys) {
            if (!root.has(key)) continue;
            JsonElement value = root.get(key);
            if (value == null || value.isJsonNull()) continue;
            if (value.isJsonPrimitive()) {
                String text = value.getAsString();
                if (!isBlank(text)) return text;
            }
        }
        return "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
