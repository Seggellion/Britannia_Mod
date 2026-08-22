package com.seggellion.britannia_mod.resource.materialization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.resource.preview.ResourceDepositPreviewProtocol;
import com.seggellion.britannia_mod.resource.shape.ShapeRotation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Bounded M5 extension of the M4 deposit protocol. */
public final class ResourceDepositMaterializationProtocol {
    public static final int VERSION = 1;
    public static final int MAX_OPERATIONS = 20;
    private static final Pattern RESOURCE_KEY = Pattern.compile("[a-z0-9_.-]{1,128}");
    private static final Pattern DIMENSION_KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");

    private ResourceDepositMaterializationProtocol() {}

    public record Target(UUID shardUuid, UUID minecraftServerUuid,
                         String worldName, String dimensionKey) {}

    public record Operation(UUID operationUuid, UUID resourceDepositUuid, long resourceDepositRevision,
                            UUID previewUuid, String previewFingerprint,
                            String resourceDefinitionKey, Target target, int x, int z,
                            ResourceDepositPreviewProtocol.Evaluation approvedPreview) {}

    public sealed interface Outcome permits Generated, Failure, Deferred {}

    public record Generated(long depositInstanceId, int resourceDefinitionRevision,
                            int resolvedY, ResourceDepositPreviewProtocol.Bounds footprint,
                            int plannedBlockCount, int materializedBlockCount,
                            int blockedBlockCount, int materializationVersion,
                            boolean replayed) implements Outcome {
        public Generated {
            if (plannedBlockCount < 1 || materializedBlockCount < 1 || blockedBlockCount < 0
                    || materializedBlockCount + blockedBlockCount != plannedBlockCount) {
                throw new IllegalArgumentException("generated materialization counts do not balance");
            }
        }
    }

    public record Failure(String code, String detail, boolean retryable) implements Outcome {}

    /** Internal bounded-progress outcome; it is never serialized as success or failure. */
    public enum Deferred implements Outcome { INSTANCE }

    public static List<Operation> parsePending(byte[] json) {
        JsonObject root = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
        requireInteger(root, "protocol_version", VERSION, VERSION);
        JsonArray rows = requireArray(root, "materialization_operations");
        if (rows.size() > MAX_OPERATIONS) malformed("too many materialization operations");
        List<Operation> result = new ArrayList<>(rows.size());
        for (JsonElement row : rows) {
            if (!row.isJsonObject()) malformed("materialization operation must be an object");
            result.add(parseOperation(row.getAsJsonObject()));
        }
        return List.copyOf(result);
    }

    public static byte[] encodeResult(Operation operation, Outcome outcome) {
        if (outcome == Deferred.INSTANCE) {
            throw new IllegalArgumentException("deferred materialization has no callback payload");
        }
        JsonObject root = new JsonObject();
        root.addProperty("protocol_version", VERSION);
        root.addProperty("operation_uuid", operation.operationUuid().toString());
        root.addProperty("resource_deposit_uuid", operation.resourceDepositUuid().toString());
        root.addProperty("resource_deposit_revision", operation.resourceDepositRevision());
        root.addProperty("preview_uuid", operation.previewUuid().toString());
        root.addProperty("preview_fingerprint", operation.previewFingerprint());
        if (outcome instanceof Generated generated) {
            root.addProperty("status", "generated");
            root.add("result", generatedJson(generated));
        } else {
            Failure failure = (Failure) outcome;
            root.addProperty("status", "failed");
            root.addProperty("error_code", boundedOutput(failure.code(), 128));
            root.addProperty("error_detail", boundedOutput(failure.detail(), 2_000));
            root.addProperty("retryable", failure.retryable());
        }
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Operation parseOperation(JsonObject json) {
        requireInteger(json, "protocol_version", VERSION, VERSION);
        UUID operationUuid = requireUuid(json, "operation_uuid");
        UUID depositUuid = requireUuid(json, "resource_deposit_uuid");
        long revision = requireLong(json, "resource_deposit_revision", 1, Long.MAX_VALUE);
        String resourceKey = requireString(json, "resource_definition_key", 128);
        if (!RESOURCE_KEY.matcher(resourceKey).matches()) malformed("invalid resource_definition_key");

        JsonObject targetJson = requireObject(json, "target");
        Target target = new Target(requireUuid(targetJson, "shard_uuid"),
                requireUuid(targetJson, "minecraft_server_uuid"),
                requireString(targetJson, "world_name", 128),
                requireString(targetJson, "dimension_key", 255));
        if (!DIMENSION_KEY.matcher(target.dimensionKey()).matches()) malformed("invalid dimension_key");

        JsonObject coordinates = requireObject(json, "coordinates");
        int x = requireInteger(coordinates, "x", -30_000_000, 30_000_000);
        int z = requireInteger(coordinates, "z", -30_000_000, 30_000_000);

        JsonObject approved = requireObject(json, "approved_preview");
        UUID previewUuid = requireUuid(approved, "preview_uuid");
        String fingerprint = requireString(approved, "result_fingerprint", 64);
        if (!FINGERPRINT.matcher(fingerprint).matches()) malformed("invalid preview fingerprint");
        ResourceDepositPreviewProtocol.Evaluation evaluation = parseEvaluation(
                requireObject(approved, "result"));
        if (!evaluation.valid()) malformed("approved preview must be valid");
        return new Operation(operationUuid, depositUuid, revision, previewUuid, fingerprint,
                resourceKey, target, x, z, evaluation);
    }

    private static ResourceDepositPreviewProtocol.Evaluation parseEvaluation(JsonObject json) {
        ResourceDepositPreviewProtocol.Bounds bounds = parseBounds(requireObject(json, "footprint"));
        int planned = requireInteger(json, "planned_block_count", 0, 10_000_000);
        int valid = requireInteger(json, "valid_host_count", 0, planned);
        int rejected = requireInteger(json, "rejected_host_count", 0, planned);
        if (valid + rejected != planned) malformed("approved preview host counts do not balance");
        String rotationId = requireString(json, "rotation", 16);
        ShapeRotation rotation = ShapeRotation.byId(rotationId).orElseThrow(() ->
                new MalformedProtocolException("invalid preview rotation"));
        return new ResourceDepositPreviewProtocol.Evaluation(
                requireInteger(json, "resolved_y", -2_048, 2_047),
                requireInteger(json, "resource_definition_revision", 1, Integer.MAX_VALUE),
                requireString(json, "shape", 64),
                requireInteger(json, "radius", 1, 512), rotation, bounds, planned, valid, rejected,
                requireStrings(json, "warnings"), requireStrings(json, "conflicts"),
                requireBoolean(json, "valid"));
    }

    private static ResourceDepositPreviewProtocol.Bounds parseBounds(JsonObject json) {
        return new ResourceDepositPreviewProtocol.Bounds(
                requireInteger(json, "min_x", -30_000_000, 30_000_000),
                requireInteger(json, "max_x", -30_000_000, 30_000_000),
                requireInteger(json, "min_y", -2_048, 2_047),
                requireInteger(json, "max_y", -2_048, 2_047),
                requireInteger(json, "min_z", -30_000_000, 30_000_000),
                requireInteger(json, "max_z", -30_000_000, 30_000_000));
    }

    private static JsonObject generatedJson(Generated value) {
        JsonObject json = new JsonObject();
        json.addProperty("deposit_instance_id", Long.toUnsignedString(value.depositInstanceId(), 16));
        json.addProperty("resource_definition_revision", value.resourceDefinitionRevision());
        json.addProperty("resolved_y", value.resolvedY());
        json.add("footprint", boundsJson(value.footprint()));
        json.addProperty("planned_block_count", value.plannedBlockCount());
        json.addProperty("materialized_block_count", value.materializedBlockCount());
        json.addProperty("blocked_block_count", value.blockedBlockCount());
        json.addProperty("materialization_version", value.materializationVersion());
        json.addProperty("replayed", value.replayed());
        return json;
    }

    private static JsonObject boundsJson(ResourceDepositPreviewProtocol.Bounds value) {
        JsonObject json = new JsonObject();
        json.addProperty("min_x", value.minX()); json.addProperty("max_x", value.maxX());
        json.addProperty("min_y", value.minY()); json.addProperty("max_y", value.maxY());
        json.addProperty("min_z", value.minZ()); json.addProperty("max_z", value.maxZ());
        return json;
    }

    private static List<String> requireStrings(JsonObject parent, String key) {
        JsonArray array = requireArray(parent, key);
        if (array.size() > 100) malformed(key + " has too many entries");
        List<String> values = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                malformed(key + " must contain strings");
            }
            String value = element.getAsString();
            if (value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length > 255) {
                malformed(key + " entry is outside its bound");
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    private static boolean requireBoolean(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonPrimitive()
                || !parent.getAsJsonPrimitive(key).isBoolean()) malformed(key + " must be boolean");
        return parent.get(key).getAsBoolean();
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
        if (value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            malformed(key + " is outside its size bound");
        }
        return value;
    }

    private static UUID requireUuid(JsonObject parent, String key) {
        try { return UUID.fromString(requireString(parent, key, 36)); }
        catch (IllegalArgumentException invalid) { malformed(key + " must be a UUID"); throw new AssertionError(); }
    }

    private static int requireInteger(JsonObject parent, String key, int min, int max) {
        return (int) requireLong(parent, key, min, max);
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
        } catch (NumberFormatException invalid) {
            malformed(key + " must be an integer"); throw new AssertionError();
        }
    }

    private static String boundedOutput(String value, int maxBytes) {
        if (value == null || value.isBlank()) return "unknown";
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) return value;
        return new String(bytes, 0, maxBytes, StandardCharsets.UTF_8).replace('\uFFFD', '?');
    }

    private static void malformed(String message) { throw new MalformedProtocolException(message); }

    public static final class MalformedProtocolException extends IllegalArgumentException {
        public MalformedProtocolException(String message) { super(message); }
    }
}
