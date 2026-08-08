package com.seggellion.britannia_mod.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Parses the additive {@code service_npc_assignments} bootstrap section in isolation,
 * mirroring {@link ServiceNpcRegistryParser}: an unsupported schema version or any
 * malformed content degrades this section alone to an empty snapshot without touching
 * cities, shard_user, fish, quests, or service_npc_registry.
 */
public final class ServiceNpcAssignmentsParser {
    public static final String ROOT_KEY = "service_npc_assignments";

    private ServiceNpcAssignmentsParser() {
    }

    public static ParseResult parseBootstrapRoot(JsonObject root) {
        if (root == null || !root.has(ROOT_KEY) || root.get(ROOT_KEY).isJsonNull()) {
            return ParseResult.missing();
        }
        if (!root.get(ROOT_KEY).isJsonObject()) {
            return ParseResult.rejected("service_npc_assignments must be an object");
        }

        try {
            return ParseResult.accepted(parseSection(root.getAsJsonObject(ROOT_KEY)));
        } catch (RuntimeException exception) {
            return ParseResult.rejected(exception.getMessage());
        }
    }

    static ServiceNpcAssignmentsSnapshot parseSection(JsonObject section) {
        int schemaVersion = requiredInt(section, "schema_version");
        if (schemaVersion != ServiceNpcAssignmentsSnapshot.SUPPORTED_SCHEMA_VERSION) {
            throw invalid("unsupported schema_version " + schemaVersion);
        }

        long revision = requiredLong(section, "revision");
        if (revision < 0L) {
            throw invalid("revision must not be negative");
        }

        Map<UUID, ServiceNpcAssignmentSpawnPointDefinition> spawnPoints =
                parseSpawnPoints(requiredArray(section, "spawn_points"));
        Map<UUID, ServiceNpcAssignmentWorldNpcDefinition> worldNpcs =
                parseWorldNpcs(requiredArray(section, "world_npcs"));
        Map<UUID, ServiceNpcAssignmentDefinition> assignments =
                parseAssignments(requiredArray(section, "assignments"), spawnPoints, worldNpcs);

        return new ServiceNpcAssignmentsSnapshot(schemaVersion, revision, spawnPoints, assignments, worldNpcs);
    }

    private static Map<UUID, ServiceNpcAssignmentSpawnPointDefinition> parseSpawnPoints(JsonArray values) {
        Map<UUID, ServiceNpcAssignmentSpawnPointDefinition> spawnPoints = new LinkedHashMap<>();
        for (int index = 0; index < values.size(); index++) {
            JsonObject value = requiredObject(values.get(index), "spawn_points[" + index + "]");
            UUID publicId = requiredUuid(value, "public_id");
            ServiceNpcAssignmentSpawnPointDefinition point = new ServiceNpcAssignmentSpawnPointDefinition(
                    publicId,
                    requiredUuid(value, "minecraft_server_public_id"),
                    optionalUuid(value, "city_public_id"),
                    optionalString(value, "service_npc_type_key"),
                    requiredString(value, "world_name"),
                    requiredString(value, "dimension_key"),
                    requiredInt(value, "x"),
                    requiredInt(value, "y"),
                    requiredInt(value, "z"),
                    requiredBoolean(value, "enabled"),
                    requiredLong(value, "revision")
            );
            if (spawnPoints.putIfAbsent(publicId, point) != null) {
                throw invalid("duplicate spawn point " + publicId);
            }
        }
        return spawnPoints;
    }

    private static Map<UUID, ServiceNpcAssignmentWorldNpcDefinition> parseWorldNpcs(JsonArray values) {
        Map<UUID, ServiceNpcAssignmentWorldNpcDefinition> worldNpcs = new LinkedHashMap<>();
        for (int index = 0; index < values.size(); index++) {
            JsonObject value = requiredObject(values.get(index), "world_npcs[" + index + "]");
            UUID publicId = requiredUuid(value, "public_id");
            ServiceNpcAssignmentWorldNpcDefinition npc = new ServiceNpcAssignmentWorldNpcDefinition(
                    publicId,
                    requiredString(value, "name"),
                    requiredString(value, "gender_key"),
                    requiredString(value, "profession_key"),
                    optionalString(value, "service_npc_type_key"),
                    requiredLong(value, "revision")
            );
            if (worldNpcs.putIfAbsent(publicId, npc) != null) {
                throw invalid("duplicate world NPC " + publicId);
            }
        }
        return worldNpcs;
    }

    private static Map<UUID, ServiceNpcAssignmentDefinition> parseAssignments(
            JsonArray values,
            Map<UUID, ServiceNpcAssignmentSpawnPointDefinition> spawnPoints,
            Map<UUID, ServiceNpcAssignmentWorldNpcDefinition> worldNpcs
    ) {
        Map<UUID, ServiceNpcAssignmentDefinition> assignments = new LinkedHashMap<>();
        for (int index = 0; index < values.size(); index++) {
            JsonObject value = requiredObject(values.get(index), "assignments[" + index + "]");
            UUID publicId = requiredUuid(value, "public_id");
            UUID spawnPointPublicId = requiredUuid(value, "spawn_point_public_id");
            UUID worldNpcPublicId = requiredUuid(value, "world_npc_public_id");
            if (!spawnPoints.containsKey(spawnPointPublicId)) {
                throw invalid("assignment " + publicId + " references unknown spawn point " + spawnPointPublicId);
            }
            if (!worldNpcs.containsKey(worldNpcPublicId)) {
                throw invalid("assignment " + publicId + " references unknown world NPC " + worldNpcPublicId);
            }
            ServiceNpcAssignmentDefinition assignment = new ServiceNpcAssignmentDefinition(
                    publicId,
                    spawnPointPublicId,
                    worldNpcPublicId,
                    requiredString(value, "status"),
                    requiredLong(value, "revision"),
                    requiredString(value, "assigned_at")
            );
            if (assignments.putIfAbsent(publicId, assignment) != null) {
                throw invalid("duplicate assignment " + publicId);
            }
        }
        return assignments;
    }

    private static JsonArray requiredArray(JsonObject value, String field) {
        JsonElement element = value.get(field);
        if (element == null || !element.isJsonArray()) {
            throw invalid(field + " must be an array");
        }
        return element.getAsJsonArray();
    }

    private static JsonObject requiredObject(JsonElement value, String context) {
        if (value == null || !value.isJsonObject()) {
            throw invalid(context + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static String requiredString(JsonObject value, String field) {
        return requiredString(value.get(field), field);
    }

    private static String requiredString(JsonElement value, String context) {
        if (value == null || !value.isJsonPrimitive()) {
            throw invalid(context + " must be a string");
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (!primitive.isString() || primitive.getAsString().isBlank()) {
            throw invalid(context + " must be a non-empty string");
        }
        return primitive.getAsString();
    }

    private static String optionalString(JsonObject value, String field) {
        if (!value.has(field) || value.get(field).isJsonNull()) {
            return null;
        }
        return requiredString(value, field);
    }

    private static UUID requiredUuid(JsonObject value, String field) {
        String raw = requiredString(value, field);
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            throw invalid(field + " must be a UUID");
        }
    }

    private static UUID optionalUuid(JsonObject value, String field) {
        if (!value.has(field) || value.get(field).isJsonNull()) {
            return null;
        }
        return requiredUuid(value, field);
    }

    private static int requiredInt(JsonObject value, String field) {
        long parsed = requiredLong(value, field);
        if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
            throw invalid(field + " is outside the integer range");
        }
        return (int) parsed;
    }

    private static long requiredLong(JsonObject value, String field) {
        JsonElement element = value.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw invalid(field + " must be an integer");
        }
        String rawValue = element.getAsString();
        if (!rawValue.matches("-?[0-9]+")) {
            throw invalid(field + " must be an integer");
        }
        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException exception) {
            throw invalid(field + " must be an integer");
        }
    }

    private static boolean requiredBoolean(JsonObject value, String field) {
        JsonElement element = value.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw invalid(field + " must be a boolean");
        }
        return element.getAsBoolean();
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message == null ? "invalid Service NPC assignments section" : message);
    }

    public enum ParseStatus {
        MISSING,
        ACCEPTED,
        REJECTED
    }

    public record ParseResult(ParseStatus status, ServiceNpcAssignmentsSnapshot snapshot, String error) {
        private static ParseResult missing() {
            return new ParseResult(ParseStatus.MISSING, ServiceNpcAssignmentsSnapshot.empty(), null);
        }

        private static ParseResult accepted(ServiceNpcAssignmentsSnapshot snapshot) {
            return new ParseResult(ParseStatus.ACCEPTED, snapshot, null);
        }

        private static ParseResult rejected(String error) {
            return new ParseResult(ParseStatus.REJECTED, ServiceNpcAssignmentsSnapshot.empty(), error);
        }
    }
}
