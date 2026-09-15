package com.seggellion.britannia_mod.quest.delivery;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A reward delivery as Rails publishes it (protocol section 1.3, 1.6): the stable identity of one
 * grant, the transition that produced it, and the items it carries. The same shape arrives inside
 * a transition response ({@code reward_delivery}), in the v2 pending listing, and in the world
 * bootstrap's {@code pending_reward_deliveries}; the parser decides which fields are mandatory
 * where, this record only holds the result.
 */
public record QuestRewardDelivery(
    UUID deliveryUuid,
    long questId,
    String questStateId,
    String transitionKey,
    List<QuestRewardDeliveryItem> items,
    String state,
    String createdAt
) {
    public static final int MAX_ITEMS = 32;
    public static final int MAX_TRANSITION_KEY_LENGTH = 200;
    public static final int MAX_QUEST_STATE_ID_LENGTH = 64;

    public QuestRewardDelivery {
        Objects.requireNonNull(deliveryUuid, "deliveryUuid");
        Objects.requireNonNull(items, "items");
        questStateId = questStateId == null ? "" : questStateId.trim();
        transitionKey = transitionKey == null ? "" : transitionKey.trim();
        state = state == null ? "" : state.trim();
        createdAt = createdAt == null ? "" : createdAt.trim();
        items = List.copyOf(items);
        if (items.isEmpty() || items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("a delivery carries 1.." + MAX_ITEMS + " items");
        }
        if (questId < 0) throw new IllegalArgumentException("questId must not be negative");
        if (transitionKey.length() > MAX_TRANSITION_KEY_LENGTH) {
            throw new IllegalArgumentException("transitionKey is too long");
        }
        if (questStateId.length() > MAX_QUEST_STATE_ID_LENGTH) {
            throw new IllegalArgumentException("questStateId is too long");
        }
    }

    /**
     * The delivery in its wire shape, so a delivery applied outside a transition response (login,
     * pending listing, ledger reconciliation) can still be offered to
     * {@link com.seggellion.britannia_mod.quest.QuestTemporaryItemPolicy} as
     * {@code reward_delivery} and yield the same temporary/permanent verdict it would have given
     * inside the original response.
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("protocol_version", QuestRewardDeliveryProtocol.PROTOCOL_VERSION);
        json.addProperty("delivery_uuid", deliveryUuid.toString());
        json.addProperty("quest_id", questId);
        json.addProperty("quest_state_id", questStateId);
        json.addProperty("transition_key", transitionKey);
        JsonArray array = new JsonArray();
        for (QuestRewardDeliveryItem item : items) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", item.id());
            entry.addProperty("count", item.count());
            if (item.temporary() != null) entry.addProperty("temporary", item.temporary());
            array.add(entry);
        }
        json.add("items", array);
        json.addProperty("state", state);
        json.addProperty("created_at", createdAt);
        return json;
    }

    /** A transition-response root carrying only this delivery; see {@link #toJson()}. */
    public JsonObject asTransitionRoot() {
        JsonObject root = new JsonObject();
        root.add("reward_delivery", toJson());
        return root;
    }
}
