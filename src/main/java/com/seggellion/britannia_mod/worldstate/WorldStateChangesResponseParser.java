package com.seggellion.britannia_mod.worldstate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the JSON body of {@code GET /api/world_state_changes/:shard} into a {@link
 * WorldStateChangesResponse}. Any missing or wrong-typed field is a {@link
 * MalformedResponseException}, never a crash -- the required* helpers below are modeled
 * directly on {@link com.seggellion.britannia_mod.service.ServiceNpcRegistryParser}'s own,
 * duplicated rather than extracted for the same reason {@code WorldBootstrapAPI}'s copy is: no
 * shared parsing-helper abstraction exists yet in this codebase for this to join.
 */
public final class WorldStateChangesResponseParser {
    private WorldStateChangesResponseParser() {}

    public static WorldStateChangesResponse parse(JsonObject root) {
        List<WorldStateChangeRecord> changes = new ArrayList<>();
        JsonArray rawChanges = requiredArray(root, "changes");
        for (int index = 0; index < rawChanges.size(); index++) {
            changes.add(parseChange(requiredObject(rawChanges.get(index), "changes[" + index + "]")));
        }
        return new WorldStateChangesResponse(
                requiredInt(root, "schema_version"),
                requiredString(root, "shard_public_id"),
                requiredLong(root, "from_version"),
                requiredLong(root, "to_version"),
                requiredLong(root, "current_version"),
                requiredBoolean(root, "full_bootstrap_required"),
                List.copyOf(changes)
        );
    }

    private static WorldStateChangeRecord parseChange(JsonObject value) {
        return new WorldStateChangeRecord(
                requiredLong(value, "version"),
                requiredString(value, "change_type"),
                requiredString(value, "resource_type"),
                requiredString(value, "resource_id"),
                requiredLong(value, "resource_revision"),
                requiredObject(value.get("payload"), "payload"),
                requiredString(value, "created_at")
        );
    }

    private static JsonArray requiredArray(JsonObject value, String field) {
        JsonElement element = value.get(field);
        if (element == null || !element.isJsonArray()) {
            throw malformed(field, "array");
        }
        return element.getAsJsonArray();
    }

    private static JsonObject requiredObject(JsonElement value, String context) {
        if (value == null || !value.isJsonObject()) {
            throw malformed(context, "object");
        }
        return value.getAsJsonObject();
    }

    private static String requiredString(JsonObject value, String field) {
        JsonElement element = value.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            throw malformed(field, "string");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isString() || primitive.getAsString().isBlank()) {
            throw malformed(field, "non-empty string");
        }
        return primitive.getAsString();
    }

    private static int requiredInt(JsonObject value, String field) {
        long parsed = requiredLong(value, field);
        if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
            throw malformed(field, "integer");
        }
        return (int) parsed;
    }

    private static long requiredLong(JsonObject value, String field) {
        JsonElement element = value.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw malformed(field, "integer");
        }
        String rawValue = element.getAsString();
        if (!rawValue.matches("-?[0-9]+")) {
            throw malformed(field, "integer");
        }
        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException exception) {
            throw malformed(field, "integer");
        }
    }

    private static boolean requiredBoolean(JsonObject value, String field) {
        JsonElement element = value.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw malformed(field, "boolean");
        }
        return element.getAsBoolean();
    }

    private static MalformedResponseException malformed(String field, String expected) {
        return new MalformedResponseException(field, expected);
    }

    public static final class MalformedResponseException extends RuntimeException {
        private final String field;
        private final String expected;

        MalformedResponseException(String field, String expected) {
            super("field=" + field + " expected=" + expected);
            this.field = field;
            this.expected = expected;
        }

        public String field() {
            return field;
        }

        public String expected() {
            return expected;
        }
    }
}
