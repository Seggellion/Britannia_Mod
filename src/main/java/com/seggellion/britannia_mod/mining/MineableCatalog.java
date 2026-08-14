package com.seggellion.britannia_mod.mining;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Canonical, data-driven Mining progression catalogue.
 *
 * <p>Mining milestone 2. Mirrors {@code CraftableRegistry}'s shape — classpath JSON, fail-fast
 * parse-and-validate, static lookups — because that is the established repository pattern for an
 * authoritative gameplay catalogue. Pure Java on purpose: JUnit contract tests load and validate
 * the real JSON without booting Minecraft. Nothing consumes resolution results yet; milestone 3
 * wires the break gate.
 */
public final class MineableCatalog {

    private static final String RESOURCE = "/data/britannia_mod/mining/mineables.json";
    private static final Pattern BLOCK_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_/.-]+");
    private static volatile MineableCatalog instance;

    private final List<MineableDefinition> definitions;
    private final Map<String, MineableDefinition> byId;
    private final Map<String, MineableDefinition> activeByBlockId;
    private final Map<String, MineableDefinition> anyByBlockId;

    private MineableCatalog(List<MineableDefinition> definitions) {
        this.definitions = List.copyOf(definitions);
        Map<String, MineableDefinition> ids = new LinkedHashMap<>();
        Map<String, MineableDefinition> activeBlocks = new LinkedHashMap<>();
        Map<String, MineableDefinition> allBlocks = new LinkedHashMap<>();
        for (MineableDefinition definition : this.definitions) {
            MineableDefinition duplicateId = ids.putIfAbsent(definition.id(), definition);
            if (duplicateId != null) {
                throw new IllegalStateException("Duplicate mineable definition id '" + definition.id() + "'");
            }
            for (String blockId : definition.blockIds()) {
                MineableDefinition claimed = allBlocks.putIfAbsent(blockId, definition);
                if (claimed != null) {
                    throw new IllegalStateException("Block " + blockId + " is claimed by both '"
                            + claimed.id() + "' and '" + definition.id() + "'");
                }
                if (definition.active()) {
                    activeBlocks.put(blockId, definition);
                }
            }
        }
        this.byId = Collections.unmodifiableMap(ids);
        this.activeByBlockId = Collections.unmodifiableMap(activeBlocks);
        this.anyByBlockId = Collections.unmodifiableMap(allBlocks);
        validate();
    }

    /** The shipped catalogue, loaded once from the classpath and validated fail-fast. */
    public static MineableCatalog instance() {
        MineableCatalog current = instance;
        if (current != null) return current;
        synchronized (MineableCatalog.class) {
            if (instance == null) {
                try (InputStream stream = MineableCatalog.class.getResourceAsStream(RESOURCE)) {
                    if (stream == null) throw new IllegalStateException("Missing mineable catalogue " + RESOURCE);
                    instance = parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
                } catch (RuntimeException exception) {
                    throw exception;
                } catch (Exception exception) {
                    throw new IllegalStateException("Unable to load mineable catalogue", exception);
                }
            }
            return instance;
        }
    }

    /** Parses and validates a catalogue from any reader; used by tests to prove extensibility. */
    public static MineableCatalog parse(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        if (!root.has("schema") || root.get("schema").getAsInt() != 1) {
            throw new IllegalStateException("Unsupported mineable catalogue schema");
        }
        List<MineableDefinition> parsed = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("mineables")) {
            parsed.add(parseDefinition(element.getAsJsonObject()));
        }
        return new MineableCatalog(parsed);
    }

    private static MineableDefinition parseDefinition(JsonObject json) {
        List<String> blocks = new ArrayList<>();
        for (JsonElement block : json.getAsJsonArray("blocks")) {
            blocks.add(block.getAsString());
        }
        return new MineableDefinition(
                requiredString(json, "id"),
                requiredString(json, "display_name"),
                MineableDefinition.Category.parse(requiredString(json, "category")),
                MineableDefinition.Status.parse(requiredString(json, "status")),
                json.get("required_mining").getAsFloat(),
                json.get("challenge").getAsFloat(),
                blocks,
                requiredString(json, "drop"),
                optionalString(json, "economy_commodity"),
                json.get("restorable").getAsBoolean(),
                json.has("owner_review") && json.get("owner_review").getAsBoolean(),
                optionalString(json, "notes"));
    }

    private static String requiredString(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            throw new IllegalStateException("Mineable definition is missing '" + key + "': " + json);
        }
        return json.get(key).getAsString();
    }

    private static Optional<String> optionalString(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) return Optional.empty();
        String value = json.get(key).getAsString();
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private void validate() {
        if (definitions.isEmpty()) throw new IllegalStateException("Mineable catalogue is empty");
        for (MineableDefinition definition : definitions) {
            String id = definition.id();
            if (id.isBlank() || !id.equals(id.toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("Mineable id must be non-blank lowercase: '" + id + "'");
            }
            if (definition.displayName().isBlank()) {
                throw new IllegalStateException("Blank display name for '" + id + "'");
            }
            if (definition.dropName().isBlank()) {
                throw new IllegalStateException("Blank drop name for '" + id + "'");
            }
            if (definition.blockIds().isEmpty()) {
                throw new IllegalStateException("Mineable '" + id + "' claims no blocks");
            }
            for (String blockId : definition.blockIds()) {
                if (!BLOCK_ID.matcher(blockId).matches()) {
                    throw new IllegalStateException("Malformed block id '" + blockId + "' in '" + id + "'");
                }
            }
            checkRange(id, "required_mining", definition.requiredMining());
            checkRange(id, "challenge", definition.challenge());
            // Impossible combination guard: every block the live break flow manages is restorable
            // today; an active-but-unrestorable row would silently change restoration scope.
            if (definition.active() && !definition.restorable()) {
                throw new IllegalStateException("Active mineable '" + id + "' must be restorable");
            }
        }
    }

    private static void checkRange(String id, String field, float value) {
        if (Float.isNaN(value) || value < 0.0f || value > 100.0f) {
            throw new IllegalStateException(field + " for '" + id + "' is out of range [0,100]: " + value);
        }
    }

    /** Resolution used by gameplay: ACTIVE definitions only. Empty = NOT_APPLICABLE. */
    public Optional<MineableDefinition> resolveBlock(String blockId) {
        return Optional.ofNullable(activeByBlockId.get(blockId));
    }

    /** Catalogue lookup that also sees DEFERRED entries (tooling/tests, never the gate). */
    public Optional<MineableDefinition> definitionForBlock(String blockId) {
        return Optional.ofNullable(anyByBlockId.get(blockId));
    }

    public Optional<MineableDefinition> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<MineableDefinition> all() {
        return definitions;
    }

    public List<MineableDefinition> active() {
        return definitions.stream().filter(MineableDefinition::active).toList();
    }

    public Map<String, MineableDefinition> activeBlockIds() {
        return activeByBlockId;
    }
}
