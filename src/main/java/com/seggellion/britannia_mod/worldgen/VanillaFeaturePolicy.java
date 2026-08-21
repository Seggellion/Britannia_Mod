package com.seggellion.britannia_mod.worldgen;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * UltimaCraft's stated position on every vanilla geological feature it governs.
 *
 * <h2>Why this exists separately from the biome modifier</h2>
 * The biome modifier is the mechanism — it says what to remove. It cannot say what was
 * <em>deliberately kept</em>, and it cannot say what nobody has looked at yet. Those are the two
 * questions that actually go wrong: a Minecraft update adds an ore variant and nothing notices, or
 * somebody widens a removal and quietly deletes the rock the Mining ladder is built on.
 *
 * <p>So the classification is the source of truth and the modifier is derived from it. A test pins
 * the modifier's feature list against {@link #suppressed()} exactly, so the two cannot drift, and
 * another walks the live placed-feature registry to prove nothing relevant is unclassified.
 *
 * <h2>The three buckets</h2>
 * <ul>
 *   <li><b>suppressed</b> — vanilla economic minerals UltimaCraft intends to own. Removed from the
 *       {@code underground_ores} step in every Overworld biome.</li>
 *   <li><b>allowed</b> — features in the same step that must survive. Mostly the stone families the
 *       Mining catalogue lists as ACTIVE mineables: removing them would delete the mid-tier
 *       progression, which is the single easiest mistake to make here.</li>
 *   <li><b>out_of_scope</b> — features this milestone deliberately does not touch. The Nether set
 *       is all of it plus infested stone; the first-pass Nether policy is to suppress nothing.
 *       Recorded rather than omitted, so "we have not decided" is distinguishable from "we have not
 *       noticed".</li>
 *   <li><b>unreachable</b> -- vanilla mineral sources that are <em>not features at all</em>, so the
 *       biome modifier structurally cannot touch them. The chunk generator's noise ore veins are
 *       the whole of it today. They belong to families this policy does suppress, which is exactly
 *       why they have to be written down: the placed features are gone and the ore is still in the
 *       ground, and without this list that reads as the suppression having failed rather than the
 *       suppression not covering that route.</li>
 * </ul>
 *
 * <p>Pure Java over classpath JSON, the same pattern as {@code MineableCatalog} and
 * {@code ResourceCatalog}, so plain JUnit reads the shipped data without booting the game.
 */
public final class VanillaFeaturePolicy {

    private static final String RESOURCE = "/data/britannia_mod/worldgen/vanilla_feature_policy.json";
    private static final Pattern FEATURE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_/.-]+");
    /** The one decoration step this milestone touches. */
    public static final String GOVERNED_STEP = "underground_ores";

    private static volatile VanillaFeaturePolicy instance;

    /** One classified vanilla feature. */
    public record Entry(String feature, String family, Optional<String> step, String reason) {
    }

    /**
     * A vanilla mineral source the biome modifier cannot remove, because the chunk generator writes
     * it directly instead of running a placed feature.
     *
     * @param mechanism what produces it, named so it cannot be mistaken for a feature id
     * @param family    the suppressed family it leaks into
     * @param produces  the blocks it actually writes, which is what an audit scan has to look for
     * @param minY      lowest block Y it can occupy
     * @param maxY      highest block Y it can occupy
     */
    public record UnreachableSource(String mechanism, String family, List<String> produces,
                                    int minY, int maxY, String reason, String resolvedBy) {
        public UnreachableSource {
            produces = List.copyOf(produces);
        }
    }

    private final List<Entry> suppressed;
    private final List<Entry> allowed;
    private final List<Entry> outOfScope;
    private final List<UnreachableSource> unreachable;
    private final Map<String, String> bucketByFeature;

    private VanillaFeaturePolicy(List<Entry> suppressed, List<Entry> allowed, List<Entry> outOfScope,
                                 List<UnreachableSource> unreachable) {
        this.suppressed = List.copyOf(suppressed);
        this.allowed = List.copyOf(allowed);
        this.outOfScope = List.copyOf(outOfScope);
        this.unreachable = List.copyOf(unreachable);

        Map<String, String> buckets = new LinkedHashMap<>();
        claim(buckets, suppressed, "suppressed");
        claim(buckets, allowed, "allowed");
        claim(buckets, outOfScope, "out_of_scope");
        this.bucketByFeature = Collections.unmodifiableMap(buckets);
        validate();
    }

    private static void claim(Map<String, String> buckets, List<Entry> entries, String bucket) {
        for (Entry entry : entries) {
            String previous = buckets.putIfAbsent(entry.feature(), bucket);
            if (previous != null) {
                throw new IllegalStateException("Feature " + entry.feature()
                        + " is classified both '" + previous + "' and '" + bucket + "'");
            }
        }
    }

    public static VanillaFeaturePolicy instance() {
        VanillaFeaturePolicy current = instance;
        if (current != null) return current;
        synchronized (VanillaFeaturePolicy.class) {
            if (instance == null) {
                try (InputStream stream = VanillaFeaturePolicy.class.getResourceAsStream(RESOURCE)) {
                    if (stream == null) {
                        throw new IllegalStateException("Missing vanilla feature policy " + RESOURCE);
                    }
                    instance = parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
                } catch (RuntimeException exception) {
                    throw exception;
                } catch (Exception exception) {
                    throw new IllegalStateException("Unable to load vanilla feature policy", exception);
                }
            }
            return instance;
        }
    }

    public static VanillaFeaturePolicy parse(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        if (!root.has("schema") || root.get("schema").getAsInt() != 1) {
            throw new IllegalStateException("Unsupported vanilla feature policy schema");
        }
        return new VanillaFeaturePolicy(
                readBucket(root, "suppressed"),
                readBucket(root, "allowed"),
                readBucket(root, "out_of_scope"),
                readUnreachable(root));
    }

    private static List<Entry> readBucket(JsonObject root, String name) {
        List<Entry> entries = new ArrayList<>();
        if (!root.has(name)) {
            throw new IllegalStateException("Vanilla feature policy is missing '" + name + "'");
        }
        for (JsonElement element : root.getAsJsonArray(name)) {
            JsonObject json = element.getAsJsonObject();
            entries.add(new Entry(
                    required(json, "feature"),
                    required(json, "family"),
                    json.has("step") ? Optional.of(json.get("step").getAsString()) : Optional.empty(),
                    json.has("reason") ? json.get("reason").getAsString() : ""));
        }
        return entries;
    }

    private static List<UnreachableSource> readUnreachable(JsonObject root) {
        List<UnreachableSource> sources = new ArrayList<>();
        if (!root.has("unreachable")) {
            return sources;
        }
        for (JsonElement element : root.getAsJsonArray("unreachable")) {
            JsonObject json = element.getAsJsonObject();
            List<String> produces = new ArrayList<>();
            for (JsonElement block : json.getAsJsonArray("produces")) {
                produces.add(block.getAsString());
            }
            sources.add(new UnreachableSource(
                    required(json, "mechanism"),
                    required(json, "family"),
                    produces,
                    json.get("min_y").getAsInt(),
                    json.get("max_y").getAsInt(),
                    json.has("reason") ? json.get("reason").getAsString() : "",
                    json.has("resolved_by") ? json.get("resolved_by").getAsString() : ""));
        }
        return sources;
    }

    private static String required(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            throw new IllegalStateException("Policy entry is missing '" + key + "': " + json);
        }
        return json.get(key).getAsString();
    }

    private void validate() {
        if (suppressed.isEmpty()) {
            throw new IllegalStateException("A policy that suppresses nothing is not a policy");
        }
        for (Entry entry : allEntries()) {
            if (!FEATURE_ID.matcher(entry.feature()).matches()) {
                throw new IllegalStateException("Malformed feature id '" + entry.feature() + "'");
            }
            if (entry.family().isBlank()) {
                throw new IllegalStateException("Blank family for " + entry.feature());
            }
        }
        // A suppressed feature must name the step it is being removed from, and it must be the one
        // step this milestone governs -- removing from a step we do not own would be a surprise.
        for (Entry entry : suppressed) {
            if (!GOVERNED_STEP.equals(entry.step().orElse(null))) {
                throw new IllegalStateException("Suppressed feature " + entry.feature()
                        + " must declare step '" + GOVERNED_STEP + "', found " + entry.step());
            }
        }
        // An unreachable source is only worth recording when it leaks into a family we claimed to
        // own. Anything else would be a note about vanilla, not a gap in this policy.
        Set<String> suppressedFamilies = families(suppressed);
        for (UnreachableSource source : unreachable) {
            if (!suppressedFamilies.contains(source.family())) {
                throw new IllegalStateException("Unreachable source " + source.mechanism()
                        + " names family '" + source.family() + "', which is not suppressed");
            }
            if (source.produces().isEmpty()) {
                throw new IllegalStateException("Unreachable source " + source.mechanism()
                        + " must name the blocks it produces, or an audit cannot look for them");
            }
            if (source.minY() > source.maxY()) {
                throw new IllegalStateException("Unreachable source " + source.mechanism()
                        + " has an inverted height range");
            }
            if (bucketByFeature.containsKey(source.mechanism())) {
                throw new IllegalStateException("Unreachable source " + source.mechanism()
                        + " collides with a classified feature id");
            }
        }
    }

    private static Set<String> families(List<Entry> entries) {
        Set<String> found = new LinkedHashSet<>();
        entries.forEach(entry -> found.add(entry.family()));
        return found;
    }

    public List<Entry> suppressedEntries() {
        return suppressed;
    }

    public List<Entry> allowedEntries() {
        return allowed;
    }

    public List<Entry> outOfScopeEntries() {
        return outOfScope;
    }

    /** Vanilla mineral sources the biome modifier structurally cannot remove. */
    public List<UnreachableSource> unreachableSources() {
        return unreachable;
    }

    /** Suppressed families whose ore still reaches the ground by a route this policy cannot close. */
    public Set<String> leakingFamilies() {
        Set<String> leaking = new LinkedHashSet<>();
        unreachable.forEach(source -> leaking.add(source.family()));
        return Collections.unmodifiableSet(leaking);
    }

    /**
     * Whether every recorded unreachable source now names how it was brought under control.
     *
     * <p>The category was created at milestone 5 to record a route the biome modifier could not
     * reach. Milestone 8.5 closed it by a different mechanism, so the entries stay -- the route is
     * still unreachable <em>by feature removal</em>, which is the fact worth keeping -- but each now
     * says what does control it. An entry without a resolution is an open gap.
     */
    public boolean everyUnreachableSourceIsResolved() {
        return unreachable.stream().allMatch(source -> !source.resolvedBy().isBlank());
    }

    /** Every block an unreachable source can still write, for an audit scan to expect. */
    public Set<String> unreachableBlocks() {
        Set<String> blocks = new LinkedHashSet<>();
        unreachable.forEach(source -> blocks.addAll(source.produces()));
        return Collections.unmodifiableSet(blocks);
    }

    public List<Entry> allEntries() {
        List<Entry> all = new ArrayList<>(suppressed);
        all.addAll(allowed);
        all.addAll(outOfScope);
        return all;
    }

    /** Feature ids UltimaCraft removes from Overworld generation. */
    public Set<String> suppressed() {
        return ids(suppressed);
    }

    /** Feature ids in the governed step that must survive. */
    public Set<String> allowed() {
        return ids(allowed);
    }

    /** Feature ids deliberately left alone, including the whole Nether set. */
    public Set<String> outOfScope() {
        return ids(outOfScope);
    }

    /** Every feature that has been looked at, whatever the verdict. */
    public Set<String> classified() {
        return Collections.unmodifiableSet(bucketByFeature.keySet());
    }

    /** Which bucket a feature is in, or empty when nobody has classified it. */
    public Optional<String> bucketOf(String feature) {
        return Optional.ofNullable(bucketByFeature.get(feature));
    }

    private static Set<String> ids(List<Entry> entries) {
        Set<String> ids = new LinkedHashSet<>();
        entries.forEach(entry -> ids.add(entry.feature()));
        return Collections.unmodifiableSet(ids);
    }
}
