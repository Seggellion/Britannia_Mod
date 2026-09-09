package com.seggellion.britannia_mod.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.quest.network.QuestModels;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Optional;

/**
 * Decides whether a granted reward is TEMPORARY, i.e. exists for an objective and is taken back by
 * {@link QuestCleanupService} when its quest ends (Rowan farming questline M1, protocol section 1.5).
 *
 * <p>The rule, frozen at M0 and shared with Rails:
 * <ol>
 *   <li>When the response carries {@code reward_delivery.items[].temporary} for the item, Rails has
 *       decided and that flag wins outright.</li>
 *   <li>Otherwise (every response today) the item is temporary exactly when the destination node
 *       returned in the same response carries a {@code destroy_trigger} or {@code pickup_trigger}
 *       whose {@code item_tag} names the item, bare or namespaced. This reproduces the only
 *       legitimate use of the stamp before M1: the magic ring that must be cast into the fire.</li>
 *   <li>Everything else -- coins, tools, seeds, bowls, buckets, produce -- is permanent.</li>
 * </ol>
 *
 * <p>A temporary decision always names a trigger key, because cleanup keys on it. When Rails marks
 * an item temporary without any objective naming it, the key is {@link #UNNAMED_TEMPORARY_TRIGGER_KEY}.
 */
public final class QuestTemporaryItemPolicy {
    /** Stamped when Rails says {@code temporary: true} but no pickup/destroy objective names the item. */
    public static final String UNNAMED_TEMPORARY_TRIGGER_KEY = "temporary_item";
    static final String DEFAULT_NAMESPACE = "britannia_mod";

    /** Where the decision came from, for the log line. */
    public enum Source { DELIVERY, DESTINATION_NODE, NONE }

    /** The destroy objective's volume, carried on the stamp so the lava path can check it. */
    public record Volume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

    public record Decision(boolean temporary, String triggerKey, String itemTag, Volume volume, Source source) {
        public static final Decision PERMANENT = new Decision(false, "", "", null, Source.NONE);

        static Decision permanentBy(Source source) {
            return new Decision(false, "", "", null, source);
        }

        static Decision temporary(Objective objective, Source source) {
            return new Decision(true, objective.triggerKey(), objective.itemTag(), objective.volume(), source);
        }
    }

    /** A pickup or destroy objective on the destination node that names the granted item. */
    record Objective(String triggerKey, String itemTag, Volume volume) {}

    private QuestTemporaryItemPolicy() {}

    /**
     * @param rewardId    the id Rails granted, bare or namespaced
     * @param registryId  the registry id the reward resolved to, so a tag naming the real item
     *                    matches too; may be null
     * @param response    the parsed response; its destination node supplies the heuristic
     * @param rawResponse the response body when the caller still has it, for
     *                    {@code reward_delivery}; may be null
     */
    public static Decision decide(String rewardId, ResourceLocation registryId,
                                  QuestModels.QuestResponse response, JsonObject rawResponse) {
        if (rewardId == null || rewardId.isBlank()) return Decision.PERMANENT;

        Objective objective = objectiveNaming(rewardId, registryId, response);
        Optional<Boolean> railsSays = deliveryFlag(rewardId, registryId, rawResponse);
        if (railsSays.isPresent()) {
            if (!railsSays.get()) return Decision.permanentBy(Source.DELIVERY);
            return objective != null
                ? Decision.temporary(objective, Source.DELIVERY)
                : new Decision(true, UNNAMED_TEMPORARY_TRIGGER_KEY, rewardId, null, Source.DELIVERY);
        }
        return objective != null ? Decision.temporary(objective, Source.DESTINATION_NODE) : Decision.PERMANENT;
    }

    /** The destroy objective first (it carries a volume), then pickup; null when neither names the item. */
    static Objective objectiveNaming(String rewardId, ResourceLocation registryId, QuestModels.QuestResponse response) {
        JsonObject metadata = response == null || response.currentNode == null ? null : response.currentNode.metadata;
        if (metadata == null) return null;

        JsonObject destroy = object(metadata, "destroy_trigger");
        if (names(destroy, rewardId, registryId)) {
            return new Objective(string(destroy, "trigger_key"), string(destroy, "item_tag"),
                new Volume(integer(destroy, "min_x"), integer(destroy, "min_y"), integer(destroy, "min_z"),
                    integer(destroy, "max_x"), integer(destroy, "max_y"), integer(destroy, "max_z")));
        }
        JsonObject pickup = object(metadata, "pickup_trigger");
        if (names(pickup, rewardId, registryId)) {
            return new Objective(string(pickup, "trigger_key"), string(pickup, "item_tag"), null);
        }
        return null;
    }

    private static boolean names(JsonObject trigger, String rewardId, ResourceLocation registryId) {
        return trigger != null
            && !string(trigger, "trigger_key").isBlank()
            && matches(string(trigger, "item_tag"), rewardId, registryId);
    }

    /**
     * Rails' verdict for this item, when the response carries one. Anything malformed reads as
     * "no verdict" so the heuristic decides, never as "temporary".
     */
    static Optional<Boolean> deliveryFlag(String rewardId, ResourceLocation registryId, JsonObject rawResponse) {
        try {
            JsonObject delivery = object(rawResponse, "reward_delivery");
            if (delivery == null || !delivery.has("items") || !delivery.get("items").isJsonArray()) {
                return Optional.empty();
            }
            for (JsonElement element : delivery.getAsJsonArray("items")) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                if (!matches(string(item, "id"), rewardId, registryId)) continue;
                if (!item.has("temporary") || !item.get("temporary").isJsonPrimitive()
                    || !item.getAsJsonPrimitive("temporary").isBoolean()) {
                    return Optional.empty();
                }
                return Optional.of(item.get("temporary").getAsBoolean());
            }
        } catch (RuntimeException ignored) {
            // A response that does not parse as a delivery is a response without one.
        }
        return Optional.empty();
    }

    /** Bare and namespaced spellings of the same id agree; the resolved registry id counts too. */
    static boolean matches(String candidate, String rewardId, ResourceLocation registryId) {
        String tag = normalize(candidate);
        if (tag.isEmpty()) return false;
        if (tag.equals(normalize(rewardId))) return true;
        return registryId != null && tag.equals(registryId.toString().toLowerCase(Locale.ROOT));
    }

    /** Lower-cased, trimmed, and namespaced to {@code britannia_mod:} when bare -- as Rails does. */
    static String normalize(String id) {
        if (id == null) return "";
        String trimmed = id.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) return "";
        return trimmed.contains(":") ? trimmed : DEFAULT_NAMESPACE + ":" + trimmed;
    }

    private static JsonObject object(JsonObject root, String key) {
        return root != null && root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : null;
    }

    private static String string(JsonObject source, String key) {
        if (source == null || !source.has(key) || source.get(key).isJsonNull()) return "";
        try {
            String value = source.get(key).getAsString();
            return value == null ? "" : value.trim();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    /** Rails has published bounds as numbers and as numeric strings; both are accepted. */
    private static int integer(JsonObject source, String key) {
        if (source == null || !source.has(key) || source.get(key).isJsonNull()) return 0;
        try {
            return source.get(key).getAsInt();
        } catch (RuntimeException notANumber) {
            try {
                return Integer.parseInt(source.get(key).getAsString().trim());
            } catch (RuntimeException ignored) {
                return 0;
            }
        }
    }
}
