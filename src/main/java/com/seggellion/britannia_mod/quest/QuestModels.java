package com.seggellion.britannia_mod.quest.network;

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
