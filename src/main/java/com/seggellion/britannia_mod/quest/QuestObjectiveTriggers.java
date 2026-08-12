package com.seggellion.britannia_mod.quest;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;

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
public record QuestObjectiveTriggers(Location location, Pickup pickup, Destroy destroy) {
    public static final QuestObjectiveTriggers NONE = new QuestObjectiveTriggers(null, null, null);

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

    public boolean isEmpty() {
        return location == null && pickup == null && destroy == null;
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
            destroy(object(metadata, "destroy_trigger")));
    }

    /** Reads the {@code triggers} object the quest journal serializer publishes. */
    public static QuestObjectiveTriggers fromJournalEntry(JsonObject entry) {
        if (entry == null) return NONE;
        JsonObject triggers = object(entry, "triggers");
        if (triggers == null) return NONE;
        return new QuestObjectiveTriggers(
            location(object(triggers, "location")),
            pickup(object(triggers, "pickup")),
            destroy(object(triggers, "destroy")));
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
