package com.seggellion.britannia_mod.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parses the {@code economic_npc_registry} bootstrap section (Vendor/Trader
 * Milestone 5). Follows {@link ServiceNpcRegistryParser}'s discipline: the
 * section is accepted or rejected WHOLESALE — a rejected section yields the
 * empty snapshot and never rejects unrelated bootstrap data, so a bad economic
 * payload can never take Service NPCs (or banking) down with it.
 */
public final class EconomicNpcRegistryParser {
    public static final String ROOT_KEY = "economic_npc_registry";
    public static final int MAX_TYPES = 1_024;
    public static final int MAX_KEY_BYTES = 64;
    public static final int MAX_LABEL_BYTES = 128;
    public static final int MAX_ENTITY_KEY_BYTES = 255;
    private static final Pattern TYPE_KEY = Pattern.compile("^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$");
    private static final Pattern ENTITY_KEY = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
    private static final Logger LOGGER = LogUtils.getLogger();

    private EconomicNpcRegistryParser() {
    }

    public static EconomicNpcRegistrySnapshot parseBootstrapRoot(JsonObject root) {
        if (root == null || !root.has(ROOT_KEY) || !root.get(ROOT_KEY).isJsonObject()) {
            return EconomicNpcRegistrySnapshot.empty();
        }
        try {
            return parseSection(root.getAsJsonObject(ROOT_KEY));
        } catch (RuntimeException exception) {
            LOGGER.warn("Rejected economic NPC registry without rejecting unrelated bootstrap data: {}",
                    exception.getMessage());
            return EconomicNpcRegistrySnapshot.empty();
        }
    }

    static EconomicNpcRegistrySnapshot parseSection(JsonObject section) {
        int schemaVersion = requiredInt(section, "schema_version");
        if (schemaVersion != EconomicNpcRegistrySnapshot.SUPPORTED_SCHEMA_VERSION) {
            throw invalid("unsupported schema_version " + schemaVersion);
        }
        String revision = requiredString(section, "revision", 128);

        if (!section.has("economic_npc_types") || !section.get("economic_npc_types").isJsonArray()) {
            throw invalid("economic_npc_types must be an array");
        }
        var values = section.getAsJsonArray("economic_npc_types");
        if (values.size() > MAX_TYPES) throw invalid("too many economic types");

        Map<String, EconomicNpcTypeDefinition> types = new LinkedHashMap<>();
        for (JsonElement element : values) {
            if (!element.isJsonObject()) throw invalid("economic type must be an object");
            JsonObject value = element.getAsJsonObject();
            String key = requiredString(value, "key", MAX_KEY_BYTES);
            if (!TYPE_KEY.matcher(key).matches()) throw invalid("invalid type key " + key);
            String entityKey = optionalString(value, "minecraft_entity_type_key", MAX_ENTITY_KEY_BYTES);
            if (entityKey != null && !ENTITY_KEY.matcher(entityKey).matches()) {
                throw invalid("invalid entity key for " + key);
            }
            EconomicNpcTypeDefinition definition = new EconomicNpcTypeDefinition(
                    key,
                    requiredString(value, "display_name", MAX_LABEL_BYTES),
                    requiredString(value, "kind", 16),
                    requiredString(value, "profession_key", MAX_KEY_BYTES),
                    entityKey,
                    requiredBoolean(value, "active"),
                    requiredBoolean(value, "spawnable"),
                    requiredLong(value, "definition_revision")
            );
            if (types.putIfAbsent(key, definition) != null) throw invalid("duplicate type key " + key);
        }
        return new EconomicNpcRegistrySnapshot(schemaVersion, revision, types);
    }

    private static String requiredString(JsonObject value, String member, int maxBytes) {
        String parsed = optionalString(value, member, maxBytes);
        if (parsed == null) throw invalid(member + " is required");
        return parsed;
    }

    private static String optionalString(JsonObject value, String member, int maxBytes) {
        if (!value.has(member) || value.get(member).isJsonNull()) return null;
        JsonElement element = value.get(member);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw invalid(member + " must be a string");
        }
        String parsed = element.getAsString();
        if (parsed.isEmpty() || parsed.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw invalid(member + " is out of bounds");
        }
        return parsed;
    }

    private static int requiredInt(JsonObject value, String member) {
        if (!value.has(member) || !value.get(member).isJsonPrimitive()
                || !value.get(member).getAsJsonPrimitive().isNumber()) {
            throw invalid(member + " must be a number");
        }
        return value.get(member).getAsInt();
    }

    private static long requiredLong(JsonObject value, String member) {
        if (!value.has(member) || !value.get(member).isJsonPrimitive()
                || !value.get(member).getAsJsonPrimitive().isNumber()) {
            throw invalid(member + " must be a number");
        }
        return value.get(member).getAsLong();
    }

    private static boolean requiredBoolean(JsonObject value, String member) {
        if (!value.has(member) || !value.get(member).isJsonPrimitive()
                || !value.get(member).getAsJsonPrimitive().isBoolean()) {
            throw invalid(member + " must be a boolean");
        }
        return value.get(member).getAsBoolean();
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
