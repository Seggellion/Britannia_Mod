package com.seggellion.britannia_mod.resource.removal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.resource.preview.ResourceDepositPreviewProtocol;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Bounded M6 protocol for non-mutating destructive previews and removal operations. */
public final class ResourceDepositRemovalProtocol {
    public static final int VERSION = 1;
    public static final int MAX_ROWS = 20;
    private static final Pattern RESOURCE = Pattern.compile("[a-z0-9_.-]{1,128}");
    private static final Pattern DIMENSION = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern HEX_ID = Pattern.compile("[0-9a-f]{1,16}");
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");

    private ResourceDepositRemovalProtocol() {}

    public record Target(UUID shardUuid, UUID minecraftServerUuid, String worldName,
                         String dimensionKey) {}

    public record PreviewRequest(UUID previewUuid, UUID resourceDepositUuid,
                                 long resourceDepositRevision, long actualRevision,
                                 UUID materializationOperationUuid, String depositInstanceId,
                                 String resourceDefinitionKey, Target target) {}

    public sealed interface PreviewOutcome permits Inspection, Failure {}

    public record Inspection(ResourceDepositPreviewProtocol.Bounds bounds, int ownedCellCount,
                             int matchingCellCount, int depletedCellCount,
                             int playerModifiedCellCount, int unexpectedCellCount,
                             int affectedBlockCount, List<String> warnings,
                             List<String> protectionConflicts, List<String> overlapConflicts,
                             boolean safeToRemove, String worldStateToken) implements PreviewOutcome {
        public Inspection {
            warnings = List.copyOf(warnings);
            protectionConflicts = List.copyOf(protectionConflicts);
            overlapConflicts = List.copyOf(overlapConflicts);
            if (matchingCellCount + depletedCellCount + playerModifiedCellCount
                    + unexpectedCellCount != ownedCellCount || affectedBlockCount != matchingCellCount) {
                throw new IllegalArgumentException("removal preview cell counts do not balance");
            }
        }
    }

    public record RemovalOperation(UUID operationUuid, UUID resourceDepositUuid,
                                   long resourceDepositRevision, long actualRevision,
                                   UUID materializationOperationUuid, String depositInstanceId,
                                   UUID removalPreviewUuid, String previewFingerprint,
                                   String resourceDefinitionKey, Target target,
                                   Inspection approvedPreview) {}

    public sealed interface RemovalOutcome permits Removed, Failure, Deferred {}
    public record Removed(int plannedCellCount, int removedBlockCount,
                          int depletedDebtCount, int preservedModifiedCount,
                          long tombstoneInstanceId, boolean replayed) implements RemovalOutcome {}
    public record Failure(String code, String detail, boolean retryable)
            implements PreviewOutcome, RemovalOutcome {}
    public enum Deferred implements RemovalOutcome { INSTANCE }

    public static List<PreviewRequest> parsePendingPreviews(byte[] json) {
        JsonObject root = root(json);
        requireInteger(root, "protocol_version", VERSION, VERSION);
        JsonArray rows = requireArray(root, "removal_preview_requests");
        if (rows.size() > MAX_ROWS) malformed("too many removal previews");
        List<PreviewRequest> result = new ArrayList<>();
        for (JsonElement row : rows) result.add(parsePreview(row.getAsJsonObject()));
        return List.copyOf(result);
    }

    public static List<RemovalOperation> parsePendingRemovals(byte[] json) {
        JsonObject root = root(json);
        requireInteger(root, "protocol_version", VERSION, VERSION);
        JsonArray rows = requireArray(root, "removal_operations");
        if (rows.size() > MAX_ROWS) malformed("too many removal operations");
        List<RemovalOperation> result = new ArrayList<>();
        for (JsonElement row : rows) result.add(parseRemoval(row.getAsJsonObject()));
        return List.copyOf(result);
    }

    public static byte[] encodePreviewResult(PreviewRequest request, PreviewOutcome outcome) {
        JsonObject root = previewCorrelation(request);
        if (outcome instanceof Inspection inspection) {
            root.addProperty("status", "completed");
            root.add("result", inspectionJson(inspection));
        } else addFailure(root, (Failure) outcome);
        return bytes(root);
    }

    public static byte[] encodeRemovalResult(RemovalOperation operation, RemovalOutcome outcome) {
        if (outcome == Deferred.INSTANCE) throw new IllegalArgumentException("deferred has no callback");
        JsonObject root = new JsonObject();
        root.addProperty("protocol_version", VERSION);
        root.addProperty("operation_uuid", operation.operationUuid().toString());
        root.addProperty("resource_deposit_uuid", operation.resourceDepositUuid().toString());
        root.addProperty("resource_deposit_revision", operation.resourceDepositRevision());
        root.addProperty("actual_revision", operation.actualRevision());
        root.addProperty("materialization_operation_uuid", operation.materializationOperationUuid().toString());
        root.addProperty("deposit_instance_id", operation.depositInstanceId());
        root.addProperty("removal_preview_uuid", operation.removalPreviewUuid().toString());
        root.addProperty("preview_fingerprint", operation.previewFingerprint());
        if (outcome instanceof Removed removed) {
            root.addProperty("status", "removed");
            JsonObject result = new JsonObject();
            result.addProperty("planned_cell_count", removed.plannedCellCount());
            result.addProperty("removed_block_count", removed.removedBlockCount());
            result.addProperty("depleted_debt_count", removed.depletedDebtCount());
            result.addProperty("preserved_modified_count", removed.preservedModifiedCount());
            result.addProperty("tombstone_instance_id", Long.toUnsignedString(removed.tombstoneInstanceId(), 16));
            result.addProperty("replayed", removed.replayed());
            root.add("result", result);
        } else addFailure(root, (Failure) outcome);
        return bytes(root);
    }

    private static PreviewRequest parsePreview(JsonObject json) {
        requireInteger(json, "protocol_version", VERSION, VERSION);
        UUID preview = requireUuid(json, "removal_preview_uuid");
        UUID deposit = requireUuid(json, "resource_deposit_uuid");
        long revision = requireLong(json, "resource_deposit_revision", 1, Long.MAX_VALUE);
        long actual = requireLong(json, "actual_revision", 1, Long.MAX_VALUE);
        UUID materialization = requireUuid(json, "materialization_operation_uuid");
        String instance = requireString(json, "deposit_instance_id", 32);
        if (!HEX_ID.matcher(instance).matches()) malformed("invalid deposit instance id");
        String resource = requireString(json, "resource_definition_key", 128);
        if (!RESOURCE.matcher(resource).matches()) malformed("invalid resource key");
        return new PreviewRequest(preview, deposit, revision, actual, materialization,
                instance, resource, parseTarget(requireObject(json, "target")));
    }

    private static RemovalOperation parseRemoval(JsonObject json) {
        requireInteger(json, "protocol_version", VERSION, VERSION);
        PreviewRequest base = new PreviewRequest(UUID.randomUUID(),
                requireUuid(json, "resource_deposit_uuid"),
                requireLong(json, "resource_deposit_revision", 1, Long.MAX_VALUE),
                requireLong(json, "actual_revision", 1, Long.MAX_VALUE),
                requireUuid(json, "materialization_operation_uuid"),
                requireString(json, "deposit_instance_id", 32),
                requireString(json, "resource_definition_key", 128),
                parseTarget(requireObject(json, "target")));
        if (!HEX_ID.matcher(base.depositInstanceId()).matches()) malformed("invalid deposit instance id");
        if (!RESOURCE.matcher(base.resourceDefinitionKey()).matches()) malformed("invalid resource key");
        JsonObject approved = requireObject(json, "approved_preview");
        UUID preview = requireUuid(approved, "removal_preview_uuid");
        String fingerprint = requireString(approved, "result_fingerprint", 64);
        if (!FINGERPRINT.matcher(fingerprint).matches()) malformed("invalid preview fingerprint");
        Inspection inspection = parseInspection(requireObject(approved, "result"));
        if (!inspection.safeToRemove()) malformed("approved removal preview must be safe");
        return new RemovalOperation(requireUuid(json, "operation_uuid"), base.resourceDepositUuid(),
                base.resourceDepositRevision(), base.actualRevision(), base.materializationOperationUuid(),
                base.depositInstanceId(), preview, fingerprint, base.resourceDefinitionKey(),
                base.target(), inspection);
    }

    private static Inspection parseInspection(JsonObject json) {
        int owned = requireInteger(json, "owned_cell_count", 1, 10_000_000);
        return new Inspection(parseBounds(requireObject(json, "bounds")), owned,
                requireInteger(json, "matching_cell_count", 0, owned),
                requireInteger(json, "depleted_cell_count", 0, owned),
                requireInteger(json, "player_modified_cell_count", 0, owned),
                requireInteger(json, "unexpected_cell_count", 0, owned),
                requireInteger(json, "affected_block_count", 0, owned),
                requireStrings(json, "warnings"), requireStrings(json, "protection_conflicts"),
                requireStrings(json, "overlap_conflicts"), requireBoolean(json, "safe_to_remove"),
                requirePattern(json, "world_state_token", 64, FINGERPRINT));
    }

    private static Target parseTarget(JsonObject json) {
        String dimension = requireString(json, "dimension_key", 255);
        if (!DIMENSION.matcher(dimension).matches()) malformed("invalid dimension key");
        return new Target(requireUuid(json, "shard_uuid"), requireUuid(json, "minecraft_server_uuid"),
                requireString(json, "world_name", 128), dimension);
    }

    private static JsonObject previewCorrelation(PreviewRequest request) {
        JsonObject root = new JsonObject();
        root.addProperty("protocol_version", VERSION);
        root.addProperty("resource_deposit_uuid", request.resourceDepositUuid().toString());
        root.addProperty("resource_deposit_revision", request.resourceDepositRevision());
        root.addProperty("actual_revision", request.actualRevision());
        root.addProperty("materialization_operation_uuid", request.materializationOperationUuid().toString());
        root.addProperty("deposit_instance_id", request.depositInstanceId());
        return root;
    }

    private static JsonObject inspectionJson(Inspection value) {
        JsonObject json = new JsonObject();
        json.add("bounds", boundsJson(value.bounds()));
        json.addProperty("owned_cell_count", value.ownedCellCount());
        json.addProperty("matching_cell_count", value.matchingCellCount());
        json.addProperty("depleted_cell_count", value.depletedCellCount());
        json.addProperty("player_modified_cell_count", value.playerModifiedCellCount());
        json.addProperty("unexpected_cell_count", value.unexpectedCellCount());
        json.addProperty("affected_block_count", value.affectedBlockCount());
        json.add("warnings", stringsJson(value.warnings()));
        json.add("protection_conflicts", stringsJson(value.protectionConflicts()));
        json.add("overlap_conflicts", stringsJson(value.overlapConflicts()));
        json.addProperty("safe_to_remove", value.safeToRemove());
        json.addProperty("world_state_token", value.worldStateToken());
        return json;
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

    private static JsonObject boundsJson(ResourceDepositPreviewProtocol.Bounds b) {
        JsonObject json = new JsonObject();
        json.addProperty("min_x", b.minX()); json.addProperty("max_x", b.maxX());
        json.addProperty("min_y", b.minY()); json.addProperty("max_y", b.maxY());
        json.addProperty("min_z", b.minZ()); json.addProperty("max_z", b.maxZ());
        return json;
    }

    private static void addFailure(JsonObject root, Failure failure) {
        root.addProperty("status", "failed");
        root.addProperty("error_code", bounded(failure.code(), 128));
        root.addProperty("error_detail", bounded(failure.detail(), 1024));
        root.addProperty("retryable", failure.retryable());
    }
    private static JsonArray stringsJson(List<String> values) {
        JsonArray array = new JsonArray(); values.forEach(array::add); return array;
    }
    private static List<String> requireStrings(JsonObject parent, String key) {
        JsonArray array = requireArray(parent, key);
        if (array.size() > 128) malformed(key + " has too many entries");
        List<String> result = new ArrayList<>();
        for (JsonElement element : array) result.add(bounded(element.getAsString(), 512));
        return List.copyOf(result);
    }
    private static JsonObject root(byte[] json) {
        return JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static byte[] bytes(JsonObject json) { return json.toString().getBytes(StandardCharsets.UTF_8); }
    private static JsonObject requireObject(JsonObject p, String k) {
        if (!p.has(k) || !p.get(k).isJsonObject()) malformed(k + " must be an object"); return p.getAsJsonObject(k);
    }
    private static JsonArray requireArray(JsonObject p, String k) {
        if (!p.has(k) || !p.get(k).isJsonArray()) malformed(k + " must be an array"); return p.getAsJsonArray(k);
    }
    private static String requirePattern(JsonObject p, String k, int max, Pattern pattern) {
        String value = requireString(p, k, max); if (!pattern.matcher(value).matches()) malformed(k + " is invalid"); return value;
    }
    private static String requireString(JsonObject p, String k, int max) {
        if (!p.has(k) || !p.get(k).isJsonPrimitive() || !p.getAsJsonPrimitive(k).isString()) malformed(k + " must be a string");
        String value = p.get(k).getAsString(); if (value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length > max) malformed(k + " is outside its bound"); return value;
    }
    private static UUID requireUuid(JsonObject p, String k) {
        try { return UUID.fromString(requireString(p, k, 36)); } catch (IllegalArgumentException e) { malformed(k + " must be a UUID"); throw new AssertionError(); }
    }
    private static int requireInteger(JsonObject p, String k, int min, int max) { return (int) requireLong(p, k, min, max); }
    private static long requireLong(JsonObject p, String k, long min, long max) {
        try { String raw = p.get(k).getAsString(); if (!raw.matches("-?(0|[1-9][0-9]*)")) malformed(k + " must be integer"); long value = Long.parseLong(raw); if (value < min || value > max) malformed(k + " outside bound"); return value; }
        catch (RuntimeException e) { malformed(k + " must be integer"); throw new AssertionError(); }
    }
    private static boolean requireBoolean(JsonObject p, String k) {
        if (!p.has(k) || !p.get(k).isJsonPrimitive() || !p.getAsJsonPrimitive(k).isBoolean()) malformed(k + " must be boolean"); return p.get(k).getAsBoolean();
    }
    private static String bounded(String value, int max) {
        if (value == null || value.isBlank()) return "unknown"; byte[] bytes = value.getBytes(StandardCharsets.UTF_8); return bytes.length <= max ? value : new String(bytes, 0, max, StandardCharsets.UTF_8).replace('\uFFFD', '?');
    }
    private static void malformed(String message) { throw new MalformedProtocolException(message); }
    public static final class MalformedProtocolException extends IllegalArgumentException { public MalformedProtocolException(String message) { super(message); } }
}
