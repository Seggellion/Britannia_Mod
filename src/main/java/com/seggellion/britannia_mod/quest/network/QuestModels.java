package com.seggellion.britannia_mod.quest.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import java.util.List;

public class QuestModels {

    public static class QuestResponse {
        public boolean success; // Added to check for successful HTTP responses
        
        public long quest_id; // Added for trigger payload

        @SerializedName("quest_state_id")
        public String questStateId;

        @SerializedName("quest_giver_name")
        public String questGiverName;
        
        @SerializedName("player_uuid")
        public String playerUuid;
        
        @SerializedName("quest_title")
        public String questTitle;
        
        @SerializedName("node") 
        public QuestNode currentNode;
        
        public List<QuestChoice> choices;
        
        // NEW: Array to hold the list of items the server wants to give the player
        public List<ItemData> granted_items; 
        public List<ClientAction> client_actions;

        public boolean completed;
        public String error;

        /**
         * Rowan farming questline M3 (protocol section 1.3): the durable delivery a transition
         * produced, when Rails publishes one, and whether the response is a replay of an earlier
         * transition. Carried here so a trigger result -- which reaches
         * {@code QuestRewardService} only as this parsed object -- can still be applied through
         * the delivery ledger rather than granting {@code granted_items} a second time.
         *
         * <p>Declared as {@link JsonElement}, not {@code JsonObject}: Rails may send JSON
         * {@code null} for a transition that granted nothing, and Gson refuses to bind a null
         * element to a {@code JsonObject} field -- which would throw while parsing a response that
         * is perfectly legal. The delivery parser already reads a null element as "no delivery".
         */
        public JsonElement reward_delivery;
        public Boolean replayed;
    }

public static class ClientAction {
        public String type;
        public String name;
        public String sound;
        public String action;
        public String entity_type;
        public int karma;
        public int fame;
    }

public static class ItemData {
        public String id;
        public int count;
    }

    public static class QuestNode {
        public long id;
        public String title;
        
        @SerializedName("text") 
        public String body;
        
        @SerializedName("type")
        public String nodeType;
        
        public JsonObject metadata;
    }

    public static class QuestChoice {
        public String id; 
        public String text;
        
        @SerializedName("is_locked")
        public boolean isLocked;
    }
}
