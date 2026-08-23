package com.seggellion.britannia_mod.resource.preview;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.resource.shape.ShapeRotation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** The bounded, versioned wire contract shared with Rails World Admin Map M4. */
public final class ResourceDepositPreviewProtocol {
    public static final int VERSION = 1;
    public static final int MAX_REQUESTS = 20;

    /**
     * Parse-level ceiling for an authored geometry radius. Syntactic only: the per-resource
     * bound lives in the catalogue and is enforced by the evaluator through
     * {@code PlacementPlanner.reject}, in the catalogue's own words. This constant merely keeps
     * an absurd number from travelling any further than the parser, and mirrors the 512 ceiling
     * Rails' result contract already applies to the radius it gets back.
     */
    public static final int MAX_GEOMETRY_RADIUS = 512;
    private static final Pattern RESOURCE_KEY = Pattern.compile("[a-z0-9_.-]{1,128}");
    private static final Pattern DIMENSION_KEY =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private ResourceDepositPreviewProtocol() {}

    public record Target(UUID shardUuid, UUID minecraftServerUuid,
                         String worldName, String dimensionKey) {}

    /**
     * Per-deposit geometry authored in Rails. Optional on the wire and in the record: an absent
     * geometry means "no extent was explicitly authored" and the evaluator keeps its original
     * conservative default, the resource's catalogue minimum. Only the extent scalar lives here
     * today; the object exists so later fields (rotation, and any future per-deposit dimension)
     * can join additively without reshaping the request. Resource-level shape character --
     * thickness, irregularity, gap chance -- deliberately does NOT belong here; that is
     * {@code ShapeTuning}, owned by the resource definition.
     */
    public record Geometry(int radius) {}

    public record Request(UUID previewUuid, UUID resourceDepositUuid,
                          long resourceDepositRevision, String resourceDefinitionKey,
                          Target target, int x, int z, Optional<Geometry> geometry) {
        public Request {
            Objects.requireNonNull(geometry, "geometry is optional but never null");
        }

        /**
         * The original seven-argument form, kept so that every caller written before authored
         * geometry existed still reads correctly -- the same convention {@code ShapeConfig} uses
         * for tuning. A request without geometry is the common case, not a special one.
         */
        public Request(UUID previewUuid, UUID resourceDepositUuid,
                       long resourceDepositRevision, String resourceDefinitionKey,
                       Target target, int x, int z) {
            this(previewUuid, resourceDepositUuid, resourceDepositRevision, resourceDefinitionKey,
                    target, x, z, Optional.empty());
        }
    }

    public record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        public Bounds {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("preview bounds are inverted");
            }
        }
    }

    public sealed interface Outcome permits Evaluation, Failure {}

    public record Evaluation(
            int resolvedY, int resourceDefinitionRevision, String shape, int radius,
            ShapeRotation rotation, Bounds footprint, int plannedBlockCount,
            int validHostCount, int rejectedHostCount, List<String> warnings,
            List<String> conflicts, boolean valid) implements Outcome {
        public Evaluation {
            warnings = List.copyOf(warnings);
            conflicts = List.copyOf(conflicts);
            if (plannedBlockCount < 0 || validHostCount < 0 || rejectedHostCount < 0
                    || validHostCount + rejectedHostCount != plannedBlockCount) {
                throw new IllegalArgumentException("preview host counts do not balance");
            }
        }
    }

    public record Failure(String code, String detail, boolean retryable) implements Outcome {}

    public static List<Request> parsePending(byte[] json) {
        JsonObject root = JsonParser.parseString(new String(json, StandardCharsets.UTF_8))
                .getAsJsonObject();
        requireInteger(root, "protocol_version", VERSION, VERSION);
        JsonArray requests = requireArray(root, "preview_requests");
        if (requests.size() > MAX_REQUESTS) malformed("too many preview requests");

        List<Request> parsed = new ArrayList<>(requests.size());
        for (JsonElement element : requests) {
            if (!element.isJsonObject()) malformed("preview request must be an object");
            parsed.add(parseRequest(element.getAsJsonObject()));
        }
        return List.copyOf(parsed);
    }

    public static byte[] encodeResult(Request request, Outcome outcome) {
        JsonObject root = new JsonObject();
        root.addProperty("protocol_version", VERSION);
        root.addProperty("resource_deposit_uuid", request.resourceDepositUuid().toString());
        root.addProperty("resource_deposit_revision", request.resourceDepositRevision());
        if (outcome instanceof Evaluation evaluation) {
            root.addProperty("status", "completed");
            root.add("result", evaluationJson(evaluation));
        } else {
            Failure failure = (Failure) outcome;
            root.addProperty("status", "failed");
            root.addProperty("error_code", boundedOutput(failure.code(), 128));
            root.addProperty("error_detail", boundedOutput(failure.detail(), 2_000));
            root.addProperty("retryable", failure.retryable());
        }
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Request parseRequest(JsonObject json) {
        requireInteger(json, "protocol_version", VERSION, VERSION);
        UUID previewUuid = requireUuid(json, "preview_uuid");
        UUID depositUuid = requireUuid(json, "resource_deposit_uuid");
        long revision = requireLong(json, "resource_deposit_revision", 1, Long.MAX_VALUE);
        String resourceKey = requireString(json, "resource_definition_key", 128);
        if (!RESOURCE_KEY.matcher(resourceKey).matches()) malformed("invalid resource_definition_key");

        JsonObject targetJson = requireObject(json, "target");
        Target target = new Target(
                requireUuid(targetJson, "shard_uuid"),
                requireUuid(targetJson, "minecraft_server_uuid"),
                requireString(targetJson, "world_name", 128),
                requireString(targetJson, "dimension_key", 255));
        if (!DIMENSION_KEY.matcher(target.dimensionKey()).matches()) malformed("invalid dimension_key");

        JsonObject coordinates = requireObject(json, "coordinates");
        int x = requireInteger(coordinates, "x", -30_000_000, 30_000_000);
        int z = requireInteger(coordinates, "z", -30_000_000, 30_000_000);

        // Additive: a request without the key is exactly the pre-geometry request. When the key
        // is present it must be a well-formed object with a radius -- a malformed geometry is
        // refused rather than silently treated as unauthored, because falling back to the default
        // here would evaluate a different deposit than the one Rails asked about.
        Optional<Geometry> geometry = Optional.empty();
        if (json.has("geometry")) {
            JsonObject geometryJson = requireObject(json, "geometry");
            geometry = Optional.of(new Geometry(
                    requireInteger(geometryJson, "radius", 1, MAX_GEOMETRY_RADIUS)));
        }
        return new Request(previewUuid, depositUuid, revision, resourceKey, target, x, z, geometry);
    }

    private static JsonObject evaluationJson(Evaluation value) {
        JsonObject json = new JsonObject();
        json.addProperty("resolved_y", value.resolvedY());
        json.addProperty("resource_definition_revision", value.resourceDefinitionRevision());
        json.addProperty("shape", value.shape());
        json.addProperty("radius", value.radius());
        json.addProperty("rotation", value.rotation().name().toLowerCase(Locale.ROOT));
        JsonObject bounds = new JsonObject();
        bounds.addProperty("min_x", value.footprint().minX());
        bounds.addProperty("max_x", value.footprint().maxX());
        bounds.addProperty("min_y", value.footprint().minY());
        bounds.addProperty("max_y", value.footprint().maxY());
        bounds.addProperty("min_z", value.footprint().minZ());
        bounds.addProperty("max_z", value.footprint().maxZ());
        json.add("footprint", bounds);
        json.addProperty("planned_block_count", value.plannedBlockCount());
        json.addProperty("valid_host_count", value.validHostCount());
        json.addProperty("rejected_host_count", value.rejectedHostCount());
        json.add("warnings", strings(value.warnings()));
        json.add("conflicts", strings(value.conflicts()));
        json.addProperty("valid", value.valid());
        return json;
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.stream().limit(100).forEach(value -> array.add(boundedOutput(value, 255)));
        return array;
    }

    private static String boundedOutput(String value, int maxBytes) {
        if (value == null || value.isBlank()) return "unknown";
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) return value;
        return new String(bytes, 0, maxBytes, StandardCharsets.UTF_8).replace('\uFFFD', '?');
    }

    private static JsonObject requireObject(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) malformed(key + " must be an object");
        return parent.getAsJsonObject(key);
    }

    private static JsonArray requireArray(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonArray()) malformed(key + " must be an array");
        return parent.getAsJsonArray(key);
    }

    private static String requireString(JsonObject parent, String key, int maxBytes) {
        if (!parent.has(key) || !parent.get(key).isJsonPrimitive()
                || !parent.getAsJsonPrimitive(key).isString()) malformed(key + " must be a string");
        String value = parent.get(key).getAsString();
        int size = value.getBytes(StandardCharsets.UTF_8).length;
        if (value.isBlank() || size > maxBytes) malformed(key + " is outside its size bound");
        return value;
    }

    private static UUID requireUuid(JsonObject parent, String key) {
        try {
            return UUID.fromString(requireString(parent, key, 36));
        } catch (IllegalArgumentException invalid) {
            malformed(key + " must be a UUID");
            throw new AssertionError();
        }
    }

    private static int requireInteger(JsonObject parent, String key, int min, int max) {
        long value = requireLong(parent, key, min, max);
        return (int) value;
    }

    private static long requireLong(JsonObject parent, String key, long min, long max) {
        try {
            if (!parent.has(key) || !parent.get(key).isJsonPrimitive()
                    || !parent.getAsJsonPrimitive(key).isNumber()) malformed(key + " must be an integer");
            String raw = parent.get(key).getAsString();
            if (!raw.matches("-?(0|[1-9][0-9]*)")) malformed(key + " must be an integer");
            long value = Long.parseLong(raw);
            if (value < min || value > max) malformed(key + " is outside its bound");
            return value;
        } catch (NumberFormatException | ArithmeticException invalid) {
            malformed(key + " must be an integer");
            throw new AssertionError();
        }
    }

    private static void malformed(String message) {
        throw new MalformedProtocolException(message);
    }

    public static final class MalformedProtocolException extends IllegalArgumentException {
        public MalformedProtocolException(String message) { super(message); }
    }
}
