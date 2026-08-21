package com.seggellion.britannia_mod.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.mining.MineableCatalog;
import com.seggellion.britannia_mod.mining.MineableDefinition;

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
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The canonical resource catalogue: one validated, data-driven definition per managed resource.
 *
 * <h2>Shape and why</h2>
 * Deliberately the same shape as {@link MineableCatalog} — classpath JSON, fail-fast
 * parse-and-validate, static lookups, pure Java so JUnit can load the real data without booting
 * Minecraft. Milestone 0 chose that over a datapack registry because it is the repository's
 * established pattern for an authoritative gameplay catalogue, it keeps validation at class-load
 * rather than world-load, and it stops a server-side pack silently redefining the economy.
 * Nothing found during milestone 2 argued against it.
 *
 * <h2>One authority per fact</h2>
 * This catalogue owns resource identity, the extraction tag, the yield, the depleted state, the
 * regeneration duration and the generation configuration. It owns <b>no</b> Mining numbers.
 * {@code required_mining} and {@code challenge} live in {@link MineableCatalog} and nowhere else;
 * a definition names a {@code mineable} and this class proves the reference is sound:
 *
 * <ul>
 *   <li>the referenced mineable exists and is ACTIVE;</li>
 *   <li>its category matches the resource's family, so an ORE cannot quietly reference a STONE;</li>
 *   <li>the two catalogues claim <em>exactly</em> the same block ids for that resource, so neither
 *       can drift a block into or out of the other's reach;</li>
 *   <li>every ACTIVE mineable is claimed by exactly one resource — no Mining resource can exist
 *       without an extraction tag, and none can be claimed twice.</li>
 * </ul>
 *
 * <p>That last check is what makes "no silent permissive fallback" structural rather than a
 * convention: the gate resolves its tool policy from a definition, and a mineable without one
 * fails the load instead of reaching a default.
 */
public final class ResourceCatalog {

    private static final String RESOURCE = "/data/britannia_mod/resources/resources.json";
    private static final Pattern REGISTRY_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_/.-]+");
    /** A year. Past this a "regenerating" resource is a one-shot, which is a different design. */
    private static final int MAX_REGENERATION_HOURS = 8_760;

    private static volatile ResourceCatalog instance;

    private final List<ResourceDefinition> definitions;
    private final Map<String, ResourceDefinition> byId;
    private final Map<String, ResourceDefinition> byBlockId;

    private ResourceCatalog(List<ResourceDefinition> definitions) {
        this.definitions = List.copyOf(definitions);
        Map<String, ResourceDefinition> ids = new LinkedHashMap<>();
        Map<String, ResourceDefinition> blocks = new LinkedHashMap<>();
        for (ResourceDefinition definition : this.definitions) {
            ResourceDefinition duplicate = ids.putIfAbsent(definition.id(), definition);
            if (duplicate != null) {
                throw new IllegalStateException("Duplicate resource definition id '" + definition.id() + "'");
            }
            for (String blockId : definition.blockIds()) {
                ResourceDefinition claimed = blocks.putIfAbsent(blockId, definition);
                if (claimed != null) {
                    throw new IllegalStateException("Block " + blockId + " is claimed by both resource '"
                            + claimed.id() + "' and '" + definition.id() + "'");
                }
            }
        }
        this.byId = Collections.unmodifiableMap(ids);
        this.byBlockId = Collections.unmodifiableMap(blocks);
        validate();
    }

    /** The shipped catalogue, loaded once from the classpath and validated fail-fast. */
    public static ResourceCatalog instance() {
        ResourceCatalog current = instance;
        if (current != null) return current;
        synchronized (ResourceCatalog.class) {
            if (instance == null) {
                try (InputStream stream = ResourceCatalog.class.getResourceAsStream(RESOURCE)) {
                    if (stream == null) throw new IllegalStateException("Missing resource catalogue " + RESOURCE);
                    ResourceCatalog loaded = parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
                    // Completeness is a property of the shipped catalogue, not of every catalogue:
                    // a test fixture is allowed to describe one resource. Checked here so the
                    // running game still cannot boot with a mineable that has no extraction tag.
                    loaded.validateCoversEveryActiveMineable();
                    instance = loaded;
                } catch (RuntimeException exception) {
                    throw exception;
                } catch (Exception exception) {
                    throw new IllegalStateException("Unable to load resource catalogue", exception);
                }
            }
            return instance;
        }
    }

    /**
     * Parses and validates a catalogue from any reader.
     *
     * <p>Used by tests to prove a new resource can be added through data alone — which is the
     * milestone's central claim, and only demonstrable if the loader accepts data the shipped file
     * does not contain.
     */
    public static ResourceCatalog parse(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        if (!root.has("schema") || root.get("schema").getAsInt() != 1) {
            throw new IllegalStateException("Unsupported resource catalogue schema");
        }
        List<ResourceDefinition> parsed = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("resources")) {
            parsed.add(parseDefinition(element.getAsJsonObject()));
        }
        return new ResourceCatalog(parsed);
    }

    private static ResourceDefinition parseDefinition(JsonObject json) {
        List<String> blocks = new ArrayList<>();
        for (JsonElement block : requiredArrayHolder(json, "blocks")) {
            blocks.add(block.getAsString());
        }
        return new ResourceDefinition(
                requiredString(json, "id"),
                requiredString(json, "display_name"),
                ResourceDefinition.Family.parse(requiredString(json, "family")),
                blocks,
                optionalString(json, "mineable"),
                requiredString(json, "extraction_tool"),
                parseYield(requiredObject(json, "yield")),
                ResourceDefinition.DepletedState.parse(requiredString(json, "depleted")),
                requiredObject(json, "regeneration").get("hours").getAsInt(),
                json.has("generation")
                        ? Optional.of(parseGeneration(json.getAsJsonObject("generation")))
                        : Optional.empty(),
                json.get("revision").getAsInt());
    }

    private static ResourceDefinition.Yield parseYield(JsonObject json) {
        return new ResourceDefinition.Yield(
                ResourceDefinition.Yield.Mode.parse(requiredString(json, "mode")),
                optionalString(json, "item"),
                json.has("count") ? json.get("count").getAsInt() : 1);
    }

    private static ResourceDefinition.Generation parseGeneration(JsonObject json) {
        String shapeId = requiredString(json, "shape");
        ResourceShape shape = ResourceShape.byId(shapeId).orElseThrow(
                () -> new IllegalStateException("Unknown generation shape '" + shapeId + "'"));
        return new ResourceDefinition.Generation(
                shape,
                requiredString(json, "block"),
                json.get("min_radius").getAsInt(),
                json.get("max_radius").getAsInt(),
                requiredString(json, "host"));
    }

    private static com.google.gson.JsonArray requiredArrayHolder(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            throw new IllegalStateException("Resource definition is missing array '" + key + "': " + json);
        }
        return json.getAsJsonArray(key);
    }

    private static JsonObject requiredObject(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonObject()) {
            throw new IllegalStateException("Resource definition is missing object '" + key + "': " + json);
        }
        return json.getAsJsonObject(key);
    }

    private static String requiredString(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            throw new IllegalStateException("Resource definition is missing '" + key + "': " + json);
        }
        return json.get(key).getAsString();
    }

    private static Optional<String> optionalString(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) return Optional.empty();
        String value = json.get(key).getAsString();
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private void validate() {
        if (definitions.isEmpty()) throw new IllegalStateException("Resource catalogue is empty");
        MineableCatalog mineables = MineableCatalog.instance();

        for (ResourceDefinition definition : definitions) {
            String id = definition.id();
            if (!REGISTRY_ID.matcher(id).matches()) {
                throw new IllegalStateException("Resource id must be a namespaced lowercase id: '" + id + "'");
            }
            if (definition.displayName().isBlank()) {
                throw new IllegalStateException("Blank display name for '" + id + "'");
            }
            if (definition.blockIds().isEmpty()) {
                throw new IllegalStateException("Resource '" + id + "' claims no blocks");
            }
            for (String blockId : definition.blockIds()) {
                if (!REGISTRY_ID.matcher(blockId).matches()) {
                    throw new IllegalStateException("Malformed block id '" + blockId + "' in '" + id + "'");
                }
            }
            if (!REGISTRY_ID.matcher(definition.extractionToolTag()).matches()) {
                throw new IllegalStateException(
                        "Malformed extraction tool tag '" + definition.extractionToolTag() + "' in '" + id + "'");
            }
            if (definition.revision() < 1) {
                throw new IllegalStateException("Resource '" + id + "' must carry a revision of at least 1");
            }
            validateRegeneration(definition);
            validateYield(definition);
            validateGeneration(definition);
            validateMineableReference(definition, mineables);
        }
        validateNoMineableIsClaimedTwice();
    }

    private static void validateRegeneration(ResourceDefinition definition) {
        int hours = definition.regenerationHours();
        if (hours <= 0 || hours > MAX_REGENERATION_HOURS) {
            throw new IllegalStateException("Regeneration for '" + definition.id() + "' must be within [1, "
                    + MAX_REGENERATION_HOURS + "] hours, found " + hours);
        }
    }

    private static void validateYield(ResourceDefinition definition) {
        ResourceDefinition.Yield yield = definition.yield();
        if (yield.count() <= 0) {
            throw new IllegalStateException("Yield count for '" + definition.id() + "' must be positive");
        }
        // The three modes are not interchangeable: each is implemented by exactly one flow, and
        // pairing a family with the wrong one would resolve to a yield nothing knows how to make.
        ResourceDefinition.Yield.Mode expected = switch (definition.family()) {
            case ORE -> ResourceDefinition.Yield.Mode.PURITY_ORE;
            case STONE -> ResourceDefinition.Yield.Mode.GRADED_STONE;
            case SEDIMENT -> ResourceDefinition.Yield.Mode.ITEM;
        };
        if (yield.mode() != expected) {
            throw new IllegalStateException("Resource '" + definition.id() + "' is family "
                    + definition.family() + " and must use yield mode " + expected.name().toLowerCase(Locale.ROOT)
                    + ", found " + yield.mode().name().toLowerCase(Locale.ROOT));
        }
        if (yield.mode() == ResourceDefinition.Yield.Mode.ITEM) {
            String item = yield.itemId().orElseThrow(() -> new IllegalStateException(
                    "Resource '" + definition.id() + "' yields an item and must name it"));
            if (!REGISTRY_ID.matcher(item).matches()) {
                throw new IllegalStateException("Malformed yield item '" + item + "' in '" + definition.id() + "'");
            }
        } else if (yield.itemId().isPresent()) {
            throw new IllegalStateException("Resource '" + definition.id() + "' derives its yield from the block "
                    + "and must not also name an item");
        }
    }

    private static void validateGeneration(ResourceDefinition definition) {
        Optional<ResourceDefinition.Generation> maybe = definition.generation();
        if (maybe.isEmpty()) return;
        ResourceDefinition.Generation generation = maybe.get();
        String id = definition.id();

        if (!definition.blockIds().contains(generation.blockId())) {
            throw new IllegalStateException("Resource '" + id + "' generates " + generation.blockId()
                    + ", which is not one of the blocks it governs " + definition.blockIds());
        }
        if (generation.minRadius() < generation.shape().minimumRadius()) {
            throw new IllegalStateException("Resource '" + id + "' configures a minimum radius of "
                    + generation.minRadius() + ", below the " + generation.shape().id()
                    + " shape's own minimum of " + generation.shape().minimumRadius()
                    + "; data may narrow a shape's range but never widen it");
        }
        if (!REGISTRY_ID.matcher(generation.hostTag()).matches()) {
            throw new IllegalStateException("Malformed host tag '" + generation.hostTag()
                    + "' in '" + id + "'");
        }
        if (generation.maxRadius() < generation.minRadius()) {
            throw new IllegalStateException("Resource '" + id + "' configures max radius "
                    + generation.maxRadius() + " below its min radius " + generation.minRadius());
        }
        if (definition.isSediment()) {
            throw new IllegalStateException("Resource '" + id + "' is a hand-placed deposit and must not "
                    + "configure vein generation; sedimentary generation arrives at milestone 7");
        }
    }

    private static void validateMineableReference(ResourceDefinition definition, MineableCatalog mineables) {
        String id = definition.id();
        Optional<String> reference = definition.mineableId();

        if (!definition.family().isMiningGoverned()) {
            if (reference.isPresent()) {
                throw new IllegalStateException("Sediment resource '" + id
                        + "' must not reference a Mining requirement; a shovel bed is not on the Mining ladder");
            }
            if (definition.blockIds().size() != 1) {
                throw new IllegalStateException("Sediment resource '" + id
                        + "' must govern exactly one block, found " + definition.blockIds());
            }
            return;
        }

        String mineableId = reference.orElseThrow(() -> new IllegalStateException(
                "Resource '" + id + "' is Mining-governed and must reference a mineable"));
        MineableDefinition mineable = mineables.byId(mineableId).orElseThrow(() -> new IllegalStateException(
                "Resource '" + id + "' references unknown mineable '" + mineableId + "'"));
        if (!mineable.active()) {
            throw new IllegalStateException("Resource '" + id + "' references mineable '" + mineableId
                    + "', which is not ACTIVE");
        }
        ResourceDefinition.Family expected = switch (mineable.category()) {
            case ORE -> ResourceDefinition.Family.ORE;
            case STONE -> ResourceDefinition.Family.STONE;
        };
        if (definition.family() != expected) {
            throw new IllegalStateException("Resource '" + id + "' is family " + definition.family()
                    + " but mineable '" + mineableId + "' is category " + mineable.category());
        }
        // The strongest anti-drift check available: if either catalogue gains or loses a block for
        // this resource without the other, the load fails rather than the two quietly disagreeing
        // about which blocks the Mining requirement covers.
        Set<String> ours = new TreeSet<>(definition.blockIds());
        Set<String> theirs = new TreeSet<>(mineable.blockIds());
        if (!ours.equals(theirs)) {
            throw new IllegalStateException("Resource '" + id + "' governs " + ours
                    + " but mineable '" + mineableId + "' claims " + theirs + "; they must match exactly");
        }
    }

    /** Two resources must never claim the same Mining requirement between them. */
    private void validateNoMineableIsClaimedTwice() {
        Map<String, String> claimedBy = new LinkedHashMap<>();
        for (ResourceDefinition definition : definitions) {
            Optional<String> reference = definition.mineableId();
            if (reference.isEmpty()) continue;
            String previous = claimedBy.putIfAbsent(reference.get(), definition.id());
            if (previous != null) {
                throw new IllegalStateException("Mineable '" + reference.get() + "' is claimed by both resource '"
                        + previous + "' and '" + definition.id() + "'");
            }
        }
    }

    /**
     * Every ACTIVE mineable must be claimed by exactly one resource.
     *
     * <p>This is what makes "no silent permissive fallback" structural. The break gate resolves its
     * tool policy from a resource definition; a mineable without one would have no extraction tag,
     * and the gate would have to either invent a default or refuse everybody. Failing the load is
     * the third option, and the only honest one.
     *
     * <p>Separate from the constructor's validation because it is a property of the <em>shipped</em>
     * catalogue. {@link #parse} is used by tests to prove a single new resource can be described in
     * data, and a one-entry fixture is not a broken catalogue.
     */
    public void validateCoversEveryActiveMineable() {
        Set<String> claimed = new java.util.HashSet<>();
        for (ResourceDefinition definition : definitions) {
            definition.mineableId().ifPresent(claimed::add);
        }
        List<String> unclaimed = MineableCatalog.instance().active().stream()
                .map(MineableDefinition::id)
                .filter(id -> !claimed.contains(id))
                .toList();
        if (!unclaimed.isEmpty()) {
            throw new IllegalStateException("Every ACTIVE mineable needs a resource definition so that its "
                    + "extraction tool is configured rather than assumed; missing: " + unclaimed);
        }
    }

    /** The resource governing this block id, or empty when the block is ordinary world. */
    public Optional<ResourceDefinition> byBlockId(String blockId) {
        return Optional.ofNullable(byBlockId.get(blockId));
    }

    public Optional<ResourceDefinition> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Resource whose id path matches, e.g. {@code copper} for {@code britannia_mod:copper}. */
    public Optional<ResourceDefinition> byPath(String path) {
        if (path == null || path.isBlank()) return Optional.empty();
        String wanted = path.toLowerCase(Locale.ROOT);
        return definitions.stream().filter(definition -> definition.path().equals(wanted)).findFirst();
    }

    public List<ResourceDefinition> all() {
        return definitions;
    }

    public List<ResourceDefinition> family(ResourceDefinition.Family family) {
        return definitions.stream().filter(definition -> definition.family() == family).toList();
    }

    /** Every resource the legacy vein command can materialise, in id order. */
    public List<ResourceDefinition> generatable() {
        return definitions.stream()
                .filter(definition -> definition.generation().isPresent())
                .sorted(java.util.Comparator.comparing(ResourceDefinition::path))
                .toList();
    }

    public Map<String, ResourceDefinition> blockIds() {
        return byBlockId;
    }
}
