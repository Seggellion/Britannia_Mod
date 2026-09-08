package com.seggellion.britannia_mod.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public record ClientQuestEntry(
        String questStateId,
        String questId,
        String questKey,
        String questGiverName,
        String name,
        String briefDescription,
        String acceptedAt,
        String status,
        /**
         * The environmental objectives pending on this quest's current node (Milestone 6).
         *
         * <p>Server-side only. {@code QuestEntryCodecs} writes an explicit field list that does
         * not include this, so quest solutions never reach a client -- and the client no longer
         * needs them, because it no longer decides when an objective is met.
         */
        QuestObjectiveTriggers triggers,
        /**
         * What the journal screen shows about this quest (Milestone 8, protocol section 3.1).
         *
         * <p>Client-visible, and carried by {@code QuestEntryCodecs} on purpose: the journal has to
         * name the quest number, the next action, the ordered progress and the return-to-giver
         * state, and none of that is derivable from the eight strings above.
         *
         * <p>It is a strict subset of the Rails journal entry. {@link #triggers} sits beside it and
         * still does not travel, and the two are separate fields rather than one blob precisely so
         * that adding a journal field can never accidentally widen what a client learns: the match
         * criteria, bound plot keys and crop-cycle UUIDs live only in {@code triggers}, and the
         * {@code done} flags below are the same booleans the player can already see on the plot.
         */
        JournalDetail detail
) {

    /**
     * The client-visible journal fields, parsed from a Rails journal entry.
     *
     * <p>Nested here rather than in {@code client.gui} because {@link ClientQuestEntry} is built and
     * held on the server as well, and server code must not reach into a client package.
     */
    public record JournalDetail(
            Stage stage,
            String questGiverProfession,
            String objective,
            List<ProgressStep> progress,
            List<RewardItem> rewardsOnAccept,
            List<RewardItem> rewardsOnComplete,
            List<Achievement> achievements,
            List<RewardItem> keepItems,
            boolean claimPending
    ) {
        public static final JournalDetail NONE = new JournalDetail(
                Stage.NONE, "", "", List.of(), List.of(), List.of(), List.of(), List.of(), false);

        public JournalDetail {
            questGiverProfession = clean(questGiverProfession);
            objective = clean(objective);
            stage = stage == null ? Stage.NONE : stage;
            progress = progress == null ? List.of() : List.copyOf(progress);
            rewardsOnAccept = rewardsOnAccept == null ? List.of() : List.copyOf(rewardsOnAccept);
            rewardsOnComplete = rewardsOnComplete == null ? List.of() : List.copyOf(rewardsOnComplete);
            achievements = achievements == null ? List.of() : List.copyOf(achievements);
            keepItems = keepItems == null ? List.of() : List.copyOf(keepItems);
        }

        /** How many ordered progress steps are already done. */
        public int completedSteps() {
            int done = 0;
            for (ProgressStep step : progress) if (step.done()) done++;
            return done;
        }

        /** Reads the Milestone 4 journal fields off a Rails journal entry. Never reads triggers. */
        public static JournalDetail fromJournalEntry(JsonObject entry) {
            if (entry == null) return NONE;
            JsonObject stage = object(entry, "stage");
            JsonObject giver = object(entry, "quest_giver");
            JsonObject rewards = object(entry, "rewards_preview");
            return new JournalDetail(
                    stage == null ? Stage.NONE : new Stage(
                            string(stage, "questline_key"), integer(stage, "index"),
                            integer(stage, "count"), string(stage, "label")),
                    giver == null ? "" : string(giver, "profession"),
                    string(entry, "objective"),
                    parseProgressSteps(array(entry, "progress")),
                    parseRewardItems(rewards == null ? null : array(rewards, "on_accept")),
                    parseRewardItems(rewards == null ? null : array(rewards, "on_complete")),
                    parseAchievements(rewards == null ? null : array(rewards, "achievements")),
                    parseRewardItems(array(entry, "keep_items")),
                    bool(entry, "claim_pending"));
        }
    }

    /** "Quest 3 of 5". {@code index} and {@code count} are zero when the entry names no stage. */
    public record Stage(String questlineKey, int index, int count, String label) {
        public static final Stage NONE = new Stage("", 0, 0, "");

        public Stage {
            questlineKey = clean(questlineKey);
            label = clean(label);
        }

        public boolean known() {
            return index > 0 && count > 0;
        }
    }

    /** One ordered step of the current node's objective, and whether the server has seen it done. */
    public record ProgressStep(String key, String label, boolean done) {
        public ProgressStep {
            key = clean(key);
            label = clean(label);
        }
    }

    /** One item in a reward or keep-items preview. */
    public record RewardItem(String id, int count) {
        public RewardItem {
            id = clean(id);
            count = Math.max(1, count);
        }
    }

    /** One achievement named in a rewards preview. */
    public record Achievement(String key, String title) {
        public Achievement {
            key = clean(key);
            title = clean(title);
        }
    }

    /** Journal entry without objectives: the client's view, and any caller that has none. */
    public ClientQuestEntry(String questStateId, String questId, String questKey,
                            String questGiverName, String name, String briefDescription,
                            String acceptedAt, String status) {
        this(questStateId, questId, questKey, questGiverName, name, briefDescription,
             acceptedAt, status, QuestObjectiveTriggers.NONE, JournalDetail.NONE);
    }

    /** Journal entry with objectives but no Milestone 8 detail. */
    public ClientQuestEntry(String questStateId, String questId, String questKey,
                            String questGiverName, String name, String briefDescription,
                            String acceptedAt, String status, QuestObjectiveTriggers triggers) {
        this(questStateId, questId, questKey, questGiverName, name, briefDescription,
             acceptedAt, status, triggers, JournalDetail.NONE);
    }

    public ClientQuestEntry withTriggers(QuestObjectiveTriggers updated) {
        return new ClientQuestEntry(questStateId, questId, questKey, questGiverName, name,
            briefDescription, acceptedAt, status,
            updated == null ? QuestObjectiveTriggers.NONE : updated, detail);
    }

    public ClientQuestEntry withDetail(JournalDetail updated) {
        return new ClientQuestEntry(questStateId, questId, questKey, questGiverName, name,
            briefDescription, acceptedAt, status, triggers,
            updated == null ? JournalDetail.NONE : updated);
    }

    public ClientQuestEntry {
        questStateId = clean(questStateId);
        questId = clean(questId);
        questKey = clean(questKey);
        questGiverName = displayName(clean(questGiverName));
        name = clean(name);
        briefDescription = clean(briefDescription);
        acceptedAt = clean(acceptedAt);
        status = clean(status);

        if (questKey.isBlank()) {
            questKey = questId;
        }
        if (triggers == null) {
            triggers = QuestObjectiveTriggers.NONE;
        }
        if (detail == null) {
            detail = JournalDetail.NONE;
        }
        if (name.isBlank()) {
            name = questKey.isBlank() ? "Quest" : "Quest " + questKey;
        }
    }

    public ClientQuestEntry(String id, String questKey, String name, String briefDescription, String acceptedAt) {
        this(id, "", questKey, "", name, briefDescription, acceptedAt, "");
    }

    /**
     * Backwards-compatible accessor. This ID is the Rails PlayerQuestState id,
     * not the quest definition id.
     */
    public String id() {
        return questStateId;
    }

    public String storageKey() {
        return questStateId;
    }

    public boolean hasKey() {
        return !questStateId.isBlank();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String displayName(String value) {
        if (value.isBlank()) return "";
        if (!value.contains(":")) return value;
        return value.split(":", 2)[0].trim();
    }

    // ------------------------------------------------------------ JSON helpers
    // Shared by JournalDetail#fromJournalEntry. Same shape as the readers in
    // QuestObjectiveTriggers and QuestEntryParser: a field of the wrong type is absent, never a
    // parse failure, because one malformed field must not cost the player their whole journal.

    private static List<ProgressStep> parseProgressSteps(JsonArray source) {
        if (source == null) return List.of();
        List<ProgressStep> steps = new ArrayList<>();
        for (JsonElement element : source) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject step = element.getAsJsonObject();
            String key = string(step, "key");
            String label = string(step, "label");
            // A step the journal cannot name is a blank bullet. Drop it rather than draw it.
            if (key.isBlank() && label.isBlank()) continue;
            steps.add(new ProgressStep(key, label, bool(step, "done")));
        }
        return List.copyOf(steps);
    }

    private static List<RewardItem> parseRewardItems(JsonArray source) {
        if (source == null) return List.of();
        List<RewardItem> items = new ArrayList<>();
        for (JsonElement element : source) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            String id = string(item, "id");
            if (id.isBlank()) continue;
            items.add(new RewardItem(id, integer(item, "count")));
        }
        return List.copyOf(items);
    }

    private static List<Achievement> parseAchievements(JsonArray source) {
        if (source == null) return List.of();
        List<Achievement> found = new ArrayList<>();
        for (JsonElement element : source) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            String key = string(entry, "key");
            String title = string(entry, "title");
            if (key.isBlank() && title.isBlank()) continue;
            found.add(new Achievement(key, title));
        }
        return List.copyOf(found);
    }

    private static JsonObject object(JsonObject root, String key) {
        return root != null && root.has(key) && root.get(key).isJsonObject()
                ? root.getAsJsonObject(key) : null;
    }

    private static JsonArray array(JsonObject root, String key) {
        return root != null && root.has(key) && root.get(key).isJsonArray()
                ? root.getAsJsonArray(key) : null;
    }

    private static String string(JsonObject root, String key) {
        if (root == null || !root.has(key)) return "";
        JsonElement value = root.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return "";
        return value.getAsString().trim();
    }

    private static int integer(JsonObject root, String key) {
        if (root == null || !root.has(key)) return 0;
        JsonElement value = root.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return 0;
        try {
            return value.getAsInt();
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean bool(JsonObject root, String key) {
        if (root == null || !root.has(key)) return false;
        JsonElement value = root.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return false;
        try {
            return value.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
