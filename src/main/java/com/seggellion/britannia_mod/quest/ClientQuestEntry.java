package com.seggellion.britannia_mod.quest;

public record ClientQuestEntry(
        String questStateId,
        String questId,
        String questKey,
        String questGiverName,
        String name,
        String briefDescription,
        String acceptedAt,
        String status
) {
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
}
