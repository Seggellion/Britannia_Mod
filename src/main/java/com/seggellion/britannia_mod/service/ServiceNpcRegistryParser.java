package com.seggellion.britannia_mod.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ServiceNpcRegistryParser {
    public static final String ROOT_KEY = "service_npc_registry";
    private static final Pattern DEFINITION_KEY = Pattern.compile("[a-z][a-z0-9]*(?:_[a-z0-9]+)*");
    private static final Pattern SERVICE_KEY = Pattern.compile("[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*");
    private static final Pattern ENTITY_TYPE_KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Set<String> SUPPORTED_SERVICE_KEYS =
            Set.of("bank.open", "bank.create_check", "guild.train");
    /**
     * Guildmaster milestone 1. A {@code skills.slug} as Rails' friendly_id actually produces
     * one: {@code parameterize} lowercases and joins words with {@code -}, so "Animal Taming"
     * becomes {@code animal-taming}. Underscores are accepted too because {@code slug} is a
     * plain string column an admin can also set by hand. Deliberately NOT
     * {@link #DEFINITION_KEY}: that pattern rejects hyphens, and reusing it here would make
     * every multi-word skill unpublishable.
     */
    private static final Pattern SKILL_SLUG = Pattern.compile("[a-z0-9]+(?:[-_][a-z0-9]+)*");
    private static final String GUILD_TRAIN_SERVICE_KEY = "guild.train";
    /** Bounded like every other parsed collection here; RunUO's largest guild teaches eleven. */
    private static final int MAX_TAUGHT_SKILLS = 64;
    private static final int MAX_SKILL_SLUG_LENGTH = 64;
    /**
     * A plain commodity name as Rails' {@code CityStaffing::EconomicEligibility::SUPPLY_COLUMNS}
     * keys them ({@code food}, {@code silver}, {@code alcohol}, …) — deliberately not the
     * {@code *_supply} column names, and deliberately not validated against a hardcoded list here:
     * Rails already validates the key set at save time, and duplicating that list in the mod would
     * make adding a commodity a two-repo change for no safety gain.
     */
    private static final Pattern SUPPLY_KEY = Pattern.compile("[a-z][a-z0-9_]*");
    /** Comfortably above the eleven real city supply columns. */
    private static final int MAX_SUPPLY_REQUIREMENTS = 32;
    private static final List<String> INTERPOLATION_TOKENS = List.of(
            "%{city_name}",
            "%{npc_name}",
            "%{profession_name}"
    );

    private ServiceNpcRegistryParser() {
    }

    public static ParseResult parseBootstrapRoot(JsonObject root) {
        if (root == null || !root.has(ROOT_KEY) || root.get(ROOT_KEY).isJsonNull()) {
            return ParseResult.missing();
        }
        if (!root.get(ROOT_KEY).isJsonObject()) {
            return ParseResult.rejected("service_npc_registry must be an object");
        }

        try {
            return ParseResult.accepted(parseRegistry(root.getAsJsonObject(ROOT_KEY)));
        } catch (RuntimeException exception) {
            return ParseResult.rejected(exception.getMessage());
        }
    }

    public static ServiceNpcRegistrySnapshot parseRegistry(JsonObject registry) {
        int schemaVersion = requiredInt(registry, "schema_version");
        if (schemaVersion != ServiceNpcRegistrySnapshot.SUPPORTED_SCHEMA_VERSION) {
            throw invalid("unsupported schema_version " + schemaVersion);
        }

        long revision = requiredLong(registry, "revision");
        if (revision < 0L) {
            throw invalid("revision must not be negative");
        }

        Map<String, ServiceActionDefinition> actions = parseActions(requiredArray(registry, "service_actions"));
        Map<String, ServiceDialogueSetDefinition> dialogues =
                parseDialogues(requiredArray(registry, "dialogue_sets"), actions);
        Map<String, ServiceNpcTypeDefinition> npcTypes =
                parseNpcTypes(requiredArray(registry, "service_npc_types"), actions, dialogues);

        return new ServiceNpcRegistrySnapshot(schemaVersion, revision, actions, npcTypes, dialogues);
    }

    private static Map<String, ServiceActionDefinition> parseActions(JsonArray values) {
        Map<String, ServiceActionDefinition> actions = new LinkedHashMap<>();
        for (int index = 0; index < values.size(); index++) {
            JsonObject value = requiredObject(values.get(index), "service_actions[" + index + "]");
            String key = requiredString(value, "key");
            validateServiceKey(key, "service action key");
            if (!SUPPORTED_SERVICE_KEYS.contains(key)) {
                throw invalid("unsupported service action " + key);
            }
            ServiceActionDefinition action = new ServiceActionDefinition(
                    key,
                    requiredString(value, "display_name"),
                    requiredString(value, "description")
            );
            if (actions.putIfAbsent(key, action) != null) {
                throw invalid("duplicate service action " + key);
            }
        }
        return actions;
    }

    private static Map<String, ServiceDialogueSetDefinition> parseDialogues(
            JsonArray values,
            Map<String, ServiceActionDefinition> actions
    ) {
        Map<String, ServiceDialogueSetDefinition> dialogues = new LinkedHashMap<>();
        for (int dialogueIndex = 0; dialogueIndex < values.size(); dialogueIndex++) {
            JsonObject value = requiredObject(values.get(dialogueIndex), "dialogue_sets[" + dialogueIndex + "]");
            String key = requiredDefinitionKey(value, "key");
            String entryNodeKey = requiredDefinitionKey(value, "entry_node_key");
            long definitionRevision = positiveRevision(value);
            JsonArray nodeValues = requiredArray(value, "nodes");
            if (nodeValues.isEmpty()) {
                throw invalid("dialogue " + key + " must contain at least one node");
            }

            List<ServiceDialogueNodeDefinition> nodes = new ArrayList<>();
            Set<String> nodeKeys = new HashSet<>();
            Set<String> optionIds = new HashSet<>();
            for (int nodeIndex = 0; nodeIndex < nodeValues.size(); nodeIndex++) {
                JsonObject nodeValue = requiredObject(nodeValues.get(nodeIndex),
                        "dialogue " + key + " node " + nodeIndex);
                String nodeKey = requiredDefinitionKey(nodeValue, "key");
                if (!nodeKeys.add(nodeKey)) {
                    throw invalid("dialogue " + key + " has duplicate node " + nodeKey);
                }
                String body = requiredString(nodeValue, "body");
                validateTemplate(body, "dialogue " + key + " node " + nodeKey + " body");
                JsonArray optionValues = requiredArray(nodeValue, "options");
                List<ServiceDialogueOptionDefinition> options = new ArrayList<>();
                for (int optionIndex = 0; optionIndex < optionValues.size(); optionIndex++) {
                    JsonObject optionValue = requiredObject(optionValues.get(optionIndex),
                            "dialogue " + key + " option " + optionIndex);
                    ServiceDialogueOptionDefinition option = parseOption(optionValue, key, actions);
                    if (!optionIds.add(option.id())) {
                        throw invalid("dialogue " + key + " has duplicate option " + option.id());
                    }
                    options.add(option);
                }
                nodes.add(new ServiceDialogueNodeDefinition(nodeKey, body, options));
            }

            if (!nodeKeys.contains(entryNodeKey)) {
                throw invalid("dialogue " + key + " entry node does not exist: " + entryNodeKey);
            }
            for (ServiceDialogueNodeDefinition node : nodes) {
                for (ServiceDialogueOptionDefinition option : node.options()) {
                    if (option.actionType() == DialogueActionType.NAVIGATE && !nodeKeys.contains(option.targetNodeKey())) {
                        throw invalid("dialogue " + key + " navigation target does not exist: " + option.targetNodeKey());
                    }
                }
            }

            ServiceDialogueSetDefinition dialogue =
                    new ServiceDialogueSetDefinition(key, entryNodeKey, nodes, definitionRevision);
            if (dialogues.putIfAbsent(key, dialogue) != null) {
                throw invalid("duplicate dialogue set " + key);
            }
        }
        return dialogues;
    }

    private static ServiceDialogueOptionDefinition parseOption(
            JsonObject value,
            String dialogueKey,
            Map<String, ServiceActionDefinition> actions
    ) {
        String id = requiredDefinitionKey(value, "id");
        String label = requiredString(value, "label");
        validateTemplate(label, "dialogue " + dialogueKey + " option " + id + " label");
        DialogueActionType actionType;
        try {
            actionType = DialogueActionType.fromWireName(requiredString(value, "action_type"));
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage());
        }

        String targetNodeKey = optionalString(value, "target_node_key");
        String serviceKey = optionalString(value, "service_key");
        switch (actionType) {
            case NAVIGATE -> {
                validateDefinitionKey(targetNodeKey, "navigation target");
                requireAbsent(serviceKey, "navigate service_key");
            }
            case INVOKE_SERVICE -> {
                validateServiceKey(serviceKey, "invoked service");
                if (!SUPPORTED_SERVICE_KEYS.contains(serviceKey) || !actions.containsKey(serviceKey)) {
                    throw invalid("dialogue " + dialogueKey + " references unavailable service " + serviceKey);
                }
                requireAbsent(targetNodeKey, "invoke_service target_node_key");
            }
            case CLOSE -> {
                requireAbsent(targetNodeKey, "close target_node_key");
                requireAbsent(serviceKey, "close service_key");
            }
        }

        return new ServiceDialogueOptionDefinition(id, label, actionType, targetNodeKey, serviceKey);
    }

    private static Map<String, ServiceNpcTypeDefinition> parseNpcTypes(
            JsonArray values,
            Map<String, ServiceActionDefinition> actions,
            Map<String, ServiceDialogueSetDefinition> dialogues
    ) {
        Map<String, ServiceNpcTypeDefinition> npcTypes = new LinkedHashMap<>();
        for (int index = 0; index < values.size(); index++) {
            JsonObject value = requiredObject(values.get(index), "service_npc_types[" + index + "]");
            String key = requiredDefinitionKey(value, "key");
            String professionKey = requiredDefinitionKey(value, "profession_key");
            String entityTypeKey = requiredString(value, "minecraft_entity_type_key");
            if (!ENTITY_TYPE_KEY.matcher(entityTypeKey).matches()) {
                throw invalid("invalid minecraft entity type key " + entityTypeKey);
            }
            String defaultDialogueKey = requiredDefinitionKey(value, "default_dialogue_key");
            ServiceDialogueSetDefinition defaultDialogue = dialogues.get(defaultDialogueKey);
            if (defaultDialogue == null) {
                throw invalid("Service NPC type " + key + " references missing dialogue " + defaultDialogueKey);
            }

            List<String> allowedServiceKeys = new ArrayList<>();
            Set<String> uniqueAllowedServices = new HashSet<>();
            for (JsonElement element : requiredArray(value, "allowed_service_keys")) {
                String serviceKey = requiredString(element, "allowed_service_keys item");
                validateServiceKey(serviceKey, "allowed service");
                if (!SUPPORTED_SERVICE_KEYS.contains(serviceKey) || !actions.containsKey(serviceKey)) {
                    throw invalid("Service NPC type " + key + " allows unavailable service " + serviceKey);
                }
                if (!uniqueAllowedServices.add(serviceKey)) {
                    throw invalid("Service NPC type " + key + " has duplicate allowed service " + serviceKey);
                }
                allowedServiceKeys.add(serviceKey);
            }

            Set<String> usedServices = new HashSet<>();
            for (ServiceDialogueNodeDefinition node : defaultDialogue.nodes()) {
                for (ServiceDialogueOptionDefinition option : node.options()) {
                    if (option.actionType() == DialogueActionType.INVOKE_SERVICE) {
                        usedServices.add(option.serviceKey());
                    }
                }
            }
            if (!uniqueAllowedServices.containsAll(usedServices)) {
                usedServices.removeAll(uniqueAllowedServices);
                throw invalid("Service NPC type " + key + " does not allow dialogue services " + usedServices);
            }

            List<String> taughtSkillSlugs = parseTaughtSkillSlugs(value, key);
            boolean teaches = uniqueAllowedServices.contains(GUILD_TRAIN_SERVICE_KEY);
            if (teaches && taughtSkillSlugs.isEmpty()) {
                throw invalid("Service NPC type " + key + " allows " + GUILD_TRAIN_SERVICE_KEY
                        + " but teaches no skills");
            }
            if (!teaches && !taughtSkillSlugs.isEmpty()) {
                throw invalid("Service NPC type " + key + " declares taught skills without allowing "
                        + GUILD_TRAIN_SERVICE_KEY);
            }

            ServiceNpcTypeDefinition npcType = new ServiceNpcTypeDefinition(
                    key,
                    requiredString(value, "display_name"),
                    professionKey,
                    entityTypeKey,
                    defaultDialogueKey,
                    allowedServiceKeys,
                    taughtSkillSlugs,
                    parseMinimumCitySupplies(value, key),
                    requiredBoolean(value, "active"),
                    requiredBoolean(value, "spawnable"),
                    positiveRevision(value)
            );
            if (npcTypes.putIfAbsent(key, npcType) != null) {
                throw invalid("duplicate Service NPC type " + key);
            }
        }
        return npcTypes;
    }

    /**
     * The optional {@code taught_skill_slugs} member. Absent or JSON null becomes the empty
     * list so a Rails build that predates the Guildmaster milestone still parses cleanly —
     * this member must never become required, or the mod would stop accepting an older
     * server's registry entirely (and take {@code bank_teller} down with it, since a rejected
     * registry falls back to the empty snapshot wholesale).
     */
    private static List<String> parseTaughtSkillSlugs(JsonObject value, String typeKey) {
        JsonElement element = value.get("taught_skill_slugs");
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw invalid("Service NPC type " + typeKey + " taught_skill_slugs must be an array");
        }

        JsonArray values = element.getAsJsonArray();
        if (values.size() > MAX_TAUGHT_SKILLS) {
            throw invalid("Service NPC type " + typeKey + " teaches too many skills");
        }

        List<String> slugs = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (JsonElement entry : values) {
            String slug = requiredString(entry, "Service NPC type " + typeKey + " taught skill");
            if (slug.length() > MAX_SKILL_SLUG_LENGTH || !SKILL_SLUG.matcher(slug).matches()) {
                throw invalid("Service NPC type " + typeKey + " has an invalid taught skill slug " + slug);
            }
            if (!unique.add(slug)) {
                throw invalid("Service NPC type " + typeKey + " has duplicate taught skill " + slug);
            }
            slugs.add(slug);
        }
        return slugs;
    }

    /**
     * The optional {@code minimum_city_supplies} member: minimum city commodity levels Rails
     * requires before it will staff this type. Optional for the same reason
     * {@code taught_skill_slugs} is — an older Rails server omits it, and requiring it would
     * reject the entire registry and take bank tellers down.
     *
     * <p>Values are read as {@code double} rather than the integer discipline used elsewhere in
     * this parser: the underlying {@code cities.*_supply} columns are Postgres floats, and
     * rounding a threshold here would silently disagree with the Rails-side check this readout
     * exists to mirror.
     */
    private static Map<String, Double> parseMinimumCitySupplies(JsonObject value, String typeKey) {
        JsonElement element = value.get("minimum_city_supplies");
        if (element == null || element.isJsonNull()) {
            return Map.of();
        }
        if (!element.isJsonObject()) {
            throw invalid("Service NPC type " + typeKey + " minimum_city_supplies must be an object");
        }

        JsonObject supplies = element.getAsJsonObject();
        if (supplies.size() > MAX_SUPPLY_REQUIREMENTS) {
            throw invalid("Service NPC type " + typeKey + " has too many supply requirements");
        }

        Map<String, Double> minimums = new LinkedHashMap<>();
        for (String supply : supplies.keySet()) {
            if (!SUPPLY_KEY.matcher(supply).matches()) {
                throw invalid("Service NPC type " + typeKey + " has an invalid supply key " + supply);
            }
            JsonElement raw = supplies.get(supply);
            if (raw == null || !raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) {
                throw invalid("Service NPC type " + typeKey + " supply " + supply + " must be a number");
            }
            double minimum = raw.getAsDouble();
            if (!Double.isFinite(minimum) || minimum < 0.0D) {
                throw invalid("Service NPC type " + typeKey + " supply " + supply
                        + " must be a non-negative finite number");
            }
            minimums.put(supply, minimum);
        }
        return minimums;
    }

    private static long positiveRevision(JsonObject value) {
        long revision = requiredLong(value, "definition_revision");
        if (revision < 1L) {
            throw invalid("definition_revision must be positive");
        }
        return revision;
    }

    private static String requiredDefinitionKey(JsonObject value, String field) {
        String key = requiredString(value, field);
        validateDefinitionKey(key, field);
        return key;
    }

    private static void validateDefinitionKey(String value, String context) {
        if (value == null || !DEFINITION_KEY.matcher(value).matches()) {
            throw invalid(context + " must be a lower-snake-case identifier");
        }
    }

    private static void validateServiceKey(String value, String context) {
        if (value == null || !SERVICE_KEY.matcher(value).matches()) {
            throw invalid(context + " must be a namespaced service key");
        }
    }

    private static void validateTemplate(String value, String context) {
        int cursor = 0;
        while (true) {
            int percent = value.indexOf('%', cursor);
            if (percent < 0) {
                return;
            }
            String token = null;
            for (String allowed : INTERPOLATION_TOKENS) {
                if (value.startsWith(allowed, percent)) {
                    token = allowed;
                    break;
                }
            }
            if (token == null) {
                throw invalid(context + " contains unsupported or malformed interpolation");
            }
            cursor = percent + token.length();
        }
    }

    private static void requireAbsent(String value, String context) {
        if (value != null && !value.isBlank()) {
            throw invalid(context + " must be absent");
        }
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
        return new IllegalArgumentException(message == null ? "invalid Service NPC registry" : message);
    }

    public enum ParseStatus {
        MISSING,
        ACCEPTED,
        REJECTED
    }

    public record ParseResult(ParseStatus status, ServiceNpcRegistrySnapshot snapshot, String error) {
        private static ParseResult missing() {
            return new ParseResult(ParseStatus.MISSING, ServiceNpcRegistrySnapshot.empty(), null);
        }

        private static ParseResult accepted(ServiceNpcRegistrySnapshot snapshot) {
            return new ParseResult(ParseStatus.ACCEPTED, snapshot, null);
        }

        private static ParseResult rejected(String error) {
            return new ParseResult(ParseStatus.REJECTED, ServiceNpcRegistrySnapshot.empty(), error);
        }
    }
}
