package com.seggellion.britannia_mod.quest.delivery;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Reads reward deliveries off the three places Rails publishes them (protocol sections 1.3 and
 * 1.6) without breaking a legacy response that carries none.
 *
 * <ul>
 *   <li>{@link #parseTransition}: the {@code reward_delivery} of a transition response. Absent or
 *       JSON {@code null} is a legacy response ({@link Absent}); a present but malformed object is
 *       {@link Malformed} and the caller must grant NOTHING -- not even {@code granted_items} --
 *       because a delivery Rails recorded but this side could not read would otherwise be applied
 *       immediately now and again from the pending listing later.</li>
 *   <li>{@link #parsePendingListing}: the v2 listing envelope, strict.</li>
 *   <li>{@link #parseBootstrapPending}: the bootstrap's {@code pending_reward_deliveries}, tolerant
 *       per entry like every other bootstrap section: one bad element is logged and skipped, the
 *       rest of the bootstrap still applies.</li>
 * </ul>
 */
public final class QuestRewardDeliveryParser {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_STATE_LENGTH = 32;
    private static final int MAX_TIMESTAMP_LENGTH = 64;

    private QuestRewardDeliveryParser() {}

    public sealed interface TransitionResult permits Present, Absent, Malformed {}

    /** A delivery the response carries; {@code replayed} mirrors the response's top-level flag. */
    public record Present(QuestRewardDelivery delivery, boolean replayed) implements TransitionResult {
        public Present {
            Objects.requireNonNull(delivery, "delivery");
        }
    }

    /** No {@code reward_delivery}: old Rails, or a transition that granted nothing. */
    public record Absent() implements TransitionResult {}

    /** A {@code reward_delivery} that does not satisfy the contract. */
    public record Malformed(String reason) implements TransitionResult {
        public Malformed {
            reason = reason == null || reason.isBlank() ? "malformed_reward_delivery" : reason;
        }
    }

    public record PendingListing(UUID playerUuid, List<QuestRewardDelivery> deliveries) {
        public PendingListing {
            deliveries = List.copyOf(deliveries);
        }
    }

    public static TransitionResult parseTransition(JsonObject root) {
        if (root == null || !root.has("reward_delivery") || root.get("reward_delivery").isJsonNull()) {
            return new Absent();
        }
        JsonElement element = root.get("reward_delivery");
        if (!element.isJsonObject()) return new Malformed("reward_delivery_not_an_object");
        try {
            QuestRewardDelivery delivery = parseDelivery(element.getAsJsonObject(), true);
            return new Present(delivery, booleanFlag(root, "replayed"));
        } catch (MalformedDeliveryException malformed) {
            return new Malformed(malformed.reason());
        }
    }

    /** The v2 pending listing (section 1.6). Strict: any defect rejects the whole listing. */
    public static PendingListing parsePendingListing(JsonObject root) {
        if (root == null) throw new MalformedDeliveryException("listing_not_an_object");
        requireProtocolVersion(root, true);
        UUID playerUuid = parseUuid(root, "player_uuid");
        if (playerUuid == null) throw new MalformedDeliveryException("player_uuid_invalid");
        if (!root.has("deliveries") || !root.get("deliveries").isJsonArray()) {
            throw new MalformedDeliveryException("deliveries_not_an_array");
        }
        JsonArray array = root.getAsJsonArray("deliveries");
        if (array.size() > QuestRewardDeliveryProtocol.MAX_PENDING_DELIVERIES) {
            throw new MalformedDeliveryException("too_many_deliveries");
        }
        List<QuestRewardDelivery> deliveries = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonObject()) throw new MalformedDeliveryException("delivery_not_an_object");
            deliveries.add(parseDelivery(element.getAsJsonObject(), false));
        }
        return new PendingListing(playerUuid, deliveries);
    }

    /** The bootstrap's {@code pending_reward_deliveries}; absent on an old Rails, so empty then. */
    public static List<QuestRewardDelivery> parseBootstrapPending(JsonObject root) {
        if (root == null || !root.has("pending_reward_deliveries")
            || !root.get("pending_reward_deliveries").isJsonArray()) {
            return List.of();
        }
        JsonArray array = root.getAsJsonArray("pending_reward_deliveries");
        List<QuestRewardDelivery> deliveries = new ArrayList<>();
        int index = 0;
        for (JsonElement element : array) {
            if (index++ >= QuestRewardDeliveryProtocol.MAX_PENDING_DELIVERIES) {
                LOGGER.warn("event=quest_delivery_rejected source=bootstrap reason=too_many_deliveries index={}", index - 1);
                break;
            }
            if (!element.isJsonObject()) {
                LOGGER.warn("event=quest_delivery_rejected source=bootstrap reason=delivery_not_an_object index={}", index - 1);
                continue;
            }
            try {
                deliveries.add(parseDelivery(element.getAsJsonObject(), false));
            } catch (MalformedDeliveryException malformed) {
                LOGGER.warn("event=quest_delivery_rejected source=bootstrap reason={} index={}",
                    malformed.reason(), index - 1);
            }
        }
        return List.copyOf(deliveries);
    }

    /**
     * One delivery object. {@code protocol_version} is mandatory inside a transition response and
     * optional (but, when present, still version 1) on a listing element, which the contract
     * publishes without it.
     */
    public static QuestRewardDelivery parseDelivery(JsonObject json, boolean requireProtocolVersion) {
        if (json == null) throw new MalformedDeliveryException("delivery_not_an_object");
        requireProtocolVersion(json, requireProtocolVersion);

        UUID deliveryUuid = parseUuid(json, "delivery_uuid");
        if (deliveryUuid == null) throw new MalformedDeliveryException("delivery_uuid_invalid");

        long questId = parseQuestId(json);
        String questStateId = parseText(json, "quest_state_id", QuestRewardDelivery.MAX_QUEST_STATE_ID_LENGTH);
        String transitionKey = parseText(json, "transition_key", QuestRewardDelivery.MAX_TRANSITION_KEY_LENGTH);
        List<QuestRewardDeliveryItem> items = parseItems(json);
        String state = parseText(json, "state", MAX_STATE_LENGTH);
        String createdAt = parseText(json, "created_at", MAX_TIMESTAMP_LENGTH);

        try {
            return new QuestRewardDelivery(deliveryUuid, questId, questStateId, transitionKey, items,
                state.isEmpty() ? "pending" : state, createdAt);
        } catch (IllegalArgumentException invalid) {
            throw new MalformedDeliveryException("delivery_invalid");
        }
    }

    private static void requireProtocolVersion(JsonObject json, boolean required) {
        boolean present = json.has("protocol_version") && !json.get("protocol_version").isJsonNull();
        if (!present) {
            if (required) throw new MalformedDeliveryException("protocol_version_missing");
            return;
        }
        JsonElement element = json.get("protocol_version");
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()
            || !element.getAsString().matches("[0-9]{1,6}")
            || Integer.parseInt(element.getAsString()) != QuestRewardDeliveryProtocol.PROTOCOL_VERSION) {
            throw new MalformedDeliveryException("protocol_version_unsupported");
        }
    }

    private static UUID parseUuid(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isString()) {
            return null;
        }
        return QuestRewardDeliveryProtocol.parseCanonicalUuid(json.get(key).getAsString());
    }

    /** Rails publishes {@code quest_id} as a number; a numeric string is tolerated, nothing else. */
    private static long parseQuestId(JsonObject json) {
        if (!json.has("quest_id") || json.get("quest_id").isJsonNull()) return 0L;
        JsonElement element = json.get("quest_id");
        if (!element.isJsonPrimitive()) throw new MalformedDeliveryException("quest_id_invalid");
        String raw = element.getAsString().trim();
        if (!raw.matches("[0-9]{1,18}")) throw new MalformedDeliveryException("quest_id_invalid");
        return Long.parseLong(raw);
    }

    /** A string (or a number, which Rails uses for ids) bounded in length and free of control characters. */
    private static String parseText(JsonObject json, String key, int maxLength) {
        if (!json.has(key) || json.get(key).isJsonNull()) return "";
        JsonElement element = json.get(key);
        if (!element.isJsonPrimitive()) throw new MalformedDeliveryException(key + "_invalid");
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) throw new MalformedDeliveryException(key + "_invalid");
        String value = primitive.getAsString().trim();
        if (value.length() > maxLength || value.chars().anyMatch(Character::isISOControl)) {
            throw new MalformedDeliveryException(key + "_invalid");
        }
        return value;
    }

    private static List<QuestRewardDeliveryItem> parseItems(JsonObject json) {
        if (!json.has("items") || !json.get("items").isJsonArray()) {
            throw new MalformedDeliveryException("items_not_an_array");
        }
        JsonArray array = json.getAsJsonArray("items");
        if (array.isEmpty()) throw new MalformedDeliveryException("items_empty");
        if (array.size() > QuestRewardDelivery.MAX_ITEMS) throw new MalformedDeliveryException("items_too_many");

        List<QuestRewardDeliveryItem> items = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonObject()) throw new MalformedDeliveryException("item_not_an_object");
            JsonObject item = element.getAsJsonObject();

            if (!item.has("id") || !item.get("id").isJsonPrimitive() || !item.getAsJsonPrimitive("id").isString()) {
                throw new MalformedDeliveryException("item_id_invalid");
            }
            String id = item.get("id").getAsString().trim();
            if (!QuestRewardDeliveryItem.validId(id)) throw new MalformedDeliveryException("item_id_invalid");

            if (!item.has("count") || !item.get("count").isJsonPrimitive() || !item.getAsJsonPrimitive("count").isNumber()
                || !item.get("count").getAsString().matches("-?[0-9]{1,9}")) {
                throw new MalformedDeliveryException("item_count_invalid");
            }
            int count = Integer.parseInt(item.get("count").getAsString());
            if (count < 1 || count > QuestRewardDeliveryItem.MAX_COUNT) {
                throw new MalformedDeliveryException("item_count_out_of_range");
            }

            Boolean temporary = null;
            if (item.has("temporary") && !item.get("temporary").isJsonNull()) {
                JsonElement flag = item.get("temporary");
                if (!flag.isJsonPrimitive() || !flag.getAsJsonPrimitive().isBoolean()) {
                    throw new MalformedDeliveryException("item_temporary_invalid");
                }
                temporary = flag.getAsBoolean();
            }
            items.add(new QuestRewardDeliveryItem(id, count, temporary));
        }
        return items;
    }

    private static boolean booleanFlag(JsonObject root, String key) {
        if (!root.has(key) || root.get(key).isJsonNull()) return false;
        JsonElement element = root.get(key);
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean() && element.getAsBoolean();
    }

    /** Why a delivery was refused; the reason is a log token, never a player-facing text. */
    public static final class MalformedDeliveryException extends RuntimeException {
        private final String reason;

        public MalformedDeliveryException(String reason) {
            super(reason);
            this.reason = reason == null || reason.isBlank() ? "malformed_reward_delivery" : reason;
        }

        public String reason() {
            return reason;
        }
    }
}
