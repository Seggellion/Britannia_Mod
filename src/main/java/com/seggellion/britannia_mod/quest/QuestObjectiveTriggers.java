package com.seggellion.britannia_mod.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The environmental objectives pending on a quest's CURRENT node (Milestone 6, finding Q-02).
 *
 * <p>Until this milestone these lived only in the client's copy of the last Rails response, and
 * the client decided when they were met. That had three consequences: after a relog nothing fired
 * at all (the client's single {@code QuestManager.currentQuestState} is never restored at login),
 * with two active quests only the most recently touched one could progress, and a modified client
 * could assert any objective it liked (finding Q-05).
 *
 * <p>They now travel with the server's journal, so the server decides. Deliberately NOT sent to
 * the client: {@code QuestEntryCodecs} writes an explicit field list and this is not in it, which
 * keeps quest solutions -- the exact volume that completes a delivery, the item that must be
 * destroyed -- off the wire.
 */
public record QuestObjectiveTriggers(Location location, Pickup pickup, Destroy destroy,
                                     ActionTrigger action, List<ActionStep> steps) {
    public static final QuestObjectiveTriggers NONE = new QuestObjectiveTriggers(null, null, null, null, List.of());

    public QuestObjectiveTriggers {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /** The three legacy observers, for every caller that has no farming action. */
    public QuestObjectiveTriggers(Location location, Pickup pickup, Destroy destroy) {
        this(location, pickup, destroy, null, List.of());
    }

    /** Standing inside a volume completes the objective. */
    public record Location(String triggerKey, BlockPos min, BlockPos max) {
        public boolean contains(BlockPos pos) {
            if (min.equals(BlockPos.ZERO) && max.equals(BlockPos.ZERO)) return false;
            return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }
    }

    /** Picking the item up completes the objective. */
    public record Pickup(String triggerKey, String itemTag) {}

    /** Destroying the item inside a volume completes the objective. */
    public record Destroy(String triggerKey, String itemTag, BlockPos min, BlockPos max) {
        public boolean contains(BlockPos pos) {
            return new Location(triggerKey, min, max).contains(pos);
        }
    }

    /**
     * The node's advancing farming objective (protocol section 2.1's {@code action_trigger}), as
     * the journal serializer publishes it: placeholders already resolved, {@code require_bound}
     * already reduced to the values currently bound.
     *
     * <p>{@code match} and {@code requireBound} are compared as text against the event's subject,
     * which is why the values are held as strings: the published forms are strings and booleans
     * only, and a boolean's text is exactly {@code "true"}/{@code "false"} on both sides.
     * {@code requirePlanter} is the one comparison that is not against the subject alone -- it
     * asks whether {@code subject.planter_uuid} is the acting player.
     */
    public record ActionTrigger(String triggerKey, String action, Map<String, String> match,
                                boolean requirePlanter, Map<String, String> requireBound) {
        public ActionTrigger {
            match = match == null ? Map.of() : Map.copyOf(match);
            requireBound = requireBound == null ? Map.of() : Map.copyOf(requireBound);
        }
    }

    /** One ordered, non-advancing progress step (section 2.1's {@code action_steps}). */
    public record ActionStep(String key, String action, Map<String, String> match, boolean requirePlanter,
                             Map<String, String> requireBound, boolean done) {
        public ActionStep {
            match = match == null ? Map.of() : Map.copyOf(match);
            requireBound = requireBound == null ? Map.of() : Map.copyOf(requireBound);
        }
    }

    public boolean isEmpty() {
        return location == null && pickup == null && destroy == null && action == null && steps.isEmpty();
    }

    /** Whether anything on this node subscribes to farming actions at all. */
    public boolean hasActionSubscriptions() {
        return action != null || !steps.isEmpty();
    }

    /**
     * Reads the shape Rails publishes on a node: {@code location_trigger}, {@code pickup_trigger}
     * and {@code destroy_trigger} objects, each with a {@code trigger_key} and its own bounds or
     * item tag. A member missing its trigger key is dropped rather than half-built -- an
     * objective the server cannot name is one it must never claim to have seen.
     */
    public static QuestObjectiveTriggers fromNodeMetadata(JsonObject metadata) {
        if (metadata == null) return NONE;
        return new QuestObjectiveTriggers(
            location(object(metadata, "location_trigger")),
            pickup(object(metadata, "pickup_trigger")),
            destroy(object(metadata, "destroy_trigger")),
            actionTrigger(object(metadata, "action_trigger")),
            actionSteps(array(metadata, "action_steps")));
    }

    /** Reads the {@code triggers} object the quest journal serializer publishes. */
    public static QuestObjectiveTriggers fromJournalEntry(JsonObject entry) {
        if (entry == null) return NONE;
        JsonObject triggers = object(entry, "triggers");
        if (triggers == null) return NONE;
        return new QuestObjectiveTriggers(
            location(object(triggers, "location")),
            pickup(object(triggers, "pickup")),
            destroy(object(triggers, "destroy")),
            actionTrigger(object(triggers, "action")),
            actionSteps(array(triggers, "steps")));
    }

    /**
     * A farming objective the mod cannot name -- no trigger key or step key, or no {@code action}
     * -- is dropped rather than half-built, the same rule the three legacy observers follow: an
     * objective the server cannot name is one it must never claim to have seen.
     */
    private static ActionTrigger actionTrigger(JsonObject source) {
        if (source == null) return null;
        String key = triggerKey(source);
        String action = string(source, "action");
        if (key.isBlank() || action.isBlank()) return null;
        JsonObject match = object(source, "match");
        return new ActionTrigger(key, action, comparableEntries(match), booleanValue(match, "require_planter"),
            comparableEntries(object(source, "require_bound")));
    }

    private static List<ActionStep> actionSteps(JsonArray source) {
        if (source == null) return List.of();
        List<ActionStep> steps = new ArrayList<>();
        for (JsonElement element : source) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject step = element.getAsJsonObject();
            String key = string(step, "key");
            String action = string(step, "action");
            if (key.isBlank() || action.isBlank()) continue;
            JsonObject match = object(step, "match");
            steps.add(new ActionStep(key, action, comparableEntries(match),
                booleanValue(match, "require_planter"), comparableEntries(object(step, "require_bound")),
                booleanValue(step, "done")));
        }
        return List.copyOf(steps);
    }

    /**
     * Every scalar of a {@code match}/{@code require_bound} object as text. {@code require_planter}
     * is lifted out by the caller; anything that is not a scalar is skipped, because a condition
     * the mod cannot evaluate must not silently pass.
     */
    private static Map<String, String> comparableEntries(JsonObject source) {
        if (source == null) return Map.of();
        Map<String, String> entries = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> field : source.entrySet()) {
            if ("require_planter".equals(field.getKey())) continue;
            JsonElement value = field.getValue();
            if (value == null || !value.isJsonPrimitive()) continue;
            try {
                entries.put(field.getKey(), value.getAsString().trim());
            } catch (RuntimeException ignored) {
                // Not a scalar the mod can compare; leaving it out keeps the condition unmet.
            }
        }
        return Map.copyOf(entries);
    }

    private static boolean booleanValue(JsonObject source, String key) {
        if (source == null || !source.has(key) || !source.get(key).isJsonPrimitive()) return false;
        try {
            return source.get(key).getAsBoolean();
        } catch (RuntimeException notABoolean) {
            return false;
        }
    }

    private static JsonArray array(JsonObject root, String key) {
        return root != null && root.has(key) && root.get(key).isJsonArray()
            ? root.getAsJsonArray(key) : null;
    }

    private static Location location(JsonObject source) {
        String key = triggerKey(source);
        if (key.isBlank()) return null;
        return new Location(key, min(source), max(source));
    }

    private static Pickup pickup(JsonObject source) {
        String key = triggerKey(source);
        String itemTag = string(source, "item_tag");
        if (key.isBlank() || itemTag.isBlank()) return null;
        return new Pickup(key, itemTag);
    }

    private static Destroy destroy(JsonObject source) {
        String key = triggerKey(source);
        String itemTag = string(source, "item_tag");
        if (key.isBlank() || itemTag.isBlank()) return null;
        return new Destroy(key, itemTag, min(source), max(source));
    }

    private static BlockPos min(JsonObject source) {
        return new BlockPos(integer(source, "min_x"), integer(source, "min_y"), integer(source, "min_z"));
    }

    private static BlockPos max(JsonObject source) {
        return new BlockPos(integer(source, "max_x"), integer(source, "max_y"), integer(source, "max_z"));
    }

    private static String triggerKey(JsonObject source) {
        return string(source, "trigger_key");
    }

    private static JsonObject object(JsonObject root, String key) {
        return root != null && root.has(key) && root.get(key).isJsonObject()
            ? root.getAsJsonObject(key) : null;
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

    /** Rails has published these as numbers and as numeric strings; both are accepted. */
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
