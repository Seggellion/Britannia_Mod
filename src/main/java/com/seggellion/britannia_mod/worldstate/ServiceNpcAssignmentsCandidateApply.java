package com.seggellion.britannia_mod.worldstate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentSpawnPointDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentWorldNpcDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsSnapshot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Milestone 13 NeoForge Slice 2: a pure transformation from a base {@link
 * ServiceNpcAssignmentsSnapshot} plus a validated batch of {@link WorldStateChangeRecord}s (Slice
 * 1's own output) to a new candidate snapshot -- {@code base} is never mutated, and nothing here
 * touches any live cache. Handles exactly the resource types Rails Milestone 13 Slice 3 actually
 * publishes: {@code service_npc_spawn_point} (created/updated upsert, closed removes), {@code
 * npc_spawn_assignment} (created/closed both upsert -- Rails keeps a closed assignment's record
 * with an updated status, it does not delete it), and {@code world_npc} (created only; no update
 * or retire path is wired on the Rails side yet).
 *
 * A spawn point's {@code minecraftServerPublicId} is read directly from the payload's own {@code
 * minecraft_server_public_id} field (Rails commit 608b978), the same key name {@code
 * ServiceNpcAssignmentsSerializer} already uses for the same concept in the bootstrap payload --
 * no fallback or substitution is needed, since Rails now always includes it.
 *
 * <h2>The three-policy distinction (Slice 2 Step 1)</h2>
 * This program now has three genuinely different "something is wrong with stored/incoming
 * world-state data" situations, and they are handled differently on purpose:
 * <ol>
 *   <li><b>The on-disk cache file itself is unreadable/corrupt/schema-mismatched at load time.</b>
 *       Unchanged from before this slice: {@code ServiceNpcAssignmentsCache#load} still discards
 *       to empty. This remains correct -- unlike a durable receipt, this cache is a pure
 *       read-optimization over data Rails always remains authoritative for, so an unrecoverable
 *       on-disk copy is no worse than no cache ever having existed; Slice 2 introduces no new
 *       reason to change that.</li>
 *   <li><b>A validated delta batch is internally invalid in a way Slice 1's own generic
 *       validation does not check</b> -- a per-resource-type payload missing a required field, an
 *       unparseable UUID, or an unrecognized change_type for that resource type. This should be
 *       rare-to-impossible given Slice 1's guarantees and Rails' own publishers, but this class
 *       still defines it precisely: {@link #apply} catches it and returns {@link Rejected} without
 *       ever touching the maps it was building. Nothing is partially applied and nothing is
 *       discarded -- the caller keeps using its existing base snapshot untouched.</li>
 *   <li><b>Every individual change applies cleanly, but the resulting candidate itself fails a
 *       post-apply sanity check</b> -- today, that a live batch never leaves a dangling
 *       assignment referencing a spawn point or World NPC that is not (or is no longer) present in
 *       the candidate. Same treatment as case 2: {@link Rejected}, base untouched.</li>
 * </ol>
 * Cases 2 and 3 are new in this slice; case 1 is untouched. Both new cases share one guarantee
 * this milestone's own invariant requires: the caller's prior state survives completely intact.
 */
public final class ServiceNpcAssignmentsCandidateApply {
    private ServiceNpcAssignmentsCandidateApply() {
    }

    public sealed interface Result permits Applied, Rejected {
    }

    public record Applied(ServiceNpcAssignmentsSnapshot candidate) implements Result {
    }

    public record Rejected(String reason) implements Result {
    }

    public static Result apply(ServiceNpcAssignmentsSnapshot base, List<WorldStateChangeRecord> changes) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(changes, "changes");

        Map<UUID, ServiceNpcAssignmentSpawnPointDefinition> spawnPoints = new LinkedHashMap<>(base.spawnPoints());
        Map<UUID, ServiceNpcAssignmentDefinition> assignments = new LinkedHashMap<>(base.assignments());
        Map<UUID, ServiceNpcAssignmentWorldNpcDefinition> worldNpcs = new LinkedHashMap<>(base.worldNpcs());

        try {
            for (WorldStateChangeRecord change : changes) {
                switch (change.resourceType()) {
                    case "service_npc_spawn_point" -> applySpawnPointChange(change, spawnPoints);
                    case "npc_spawn_assignment" -> applyAssignmentChange(change, assignments);
                    case "world_npc" -> applyWorldNpcChange(change, worldNpcs);
                    default -> {
                        // Forward-compatible: a resource type this build does not know about yet
                        // is skipped, not an error -- matching this program's established
                        // isolated-degrade convention elsewhere rather than failing the whole batch.
                    }
                }
            }
        } catch (RuntimeException malformed) {
            return new Rejected("malformed_change: " + malformed.getMessage());
        }

        // A removed spawn point can never leave a dangling assignment behind in the candidate,
        // even though real Rails traffic always closes the assignment first -- this candidate
        // must be internally consistent on its own terms, not by leaning on a publish-ordering
        // assumption this slice does not want to depend on.
        assignments.values().removeIf(assignment -> !spawnPoints.containsKey(assignment.spawnPointPublicId()));

        ServiceNpcAssignmentsSnapshot candidate = new ServiceNpcAssignmentsSnapshot(
                base.schemaVersion(), base.revision(), spawnPoints, assignments, worldNpcs
        );

        String sanityFailure = checkReferentialIntegrity(candidate);
        if (sanityFailure != null) return new Rejected(sanityFailure);

        return new Applied(candidate);
    }

    private static void applySpawnPointChange(
            WorldStateChangeRecord change, Map<UUID, ServiceNpcAssignmentSpawnPointDefinition> spawnPoints
    ) {
        UUID publicId = requireUuid(change.resourceId(), "resource_id");
        switch (change.changeType()) {
            case "created", "updated" -> {
                JsonObject payload = requirePayload(change);
                spawnPoints.put(publicId, new ServiceNpcAssignmentSpawnPointDefinition(
                        publicId,
                        requiredUuid(payload, "minecraft_server_public_id"),
                        optionalUuid(payload, "city_public_id"),
                        optionalString(payload, "service_npc_type_key"),
                        requiredString(payload, "world_name"),
                        requiredString(payload, "dimension_key"),
                        requiredInt(payload, "x"),
                        requiredInt(payload, "y"),
                        requiredInt(payload, "z"),
                        requiredBoolean(payload, "enabled"),
                        change.resourceRevision()
                ));
            }
            case "closed" -> spawnPoints.remove(publicId);
            default -> throw new IllegalArgumentException(
                    "unrecognized service_npc_spawn_point change_type: " + change.changeType());
        }
    }

    private static void applyAssignmentChange(
            WorldStateChangeRecord change, Map<UUID, ServiceNpcAssignmentDefinition> assignments
    ) {
        UUID publicId = requireUuid(change.resourceId(), "resource_id");
        switch (change.changeType()) {
            case "created", "closed" -> {
                JsonObject payload = requirePayload(change);
                ServiceNpcAssignmentDefinition existing = assignments.get(publicId);
                // assigned_at is not carried in the world_state_changes wire payload for either
                // change_type (confirmed by reading Rails' publish_world_state_change directly);
                // an already-known assignment preserves its real assigned_at, and a brand-new one
                // (this batch's first sight of it) uses this change's own created_at as the best
                // available substitute.
                String assignedAt = existing != null ? existing.assignedAt() : change.createdAt();
                assignments.put(publicId, new ServiceNpcAssignmentDefinition(
                        publicId,
                        requiredUuid(payload, "spawn_point_public_id"),
                        requiredUuid(payload, "world_npc_public_id"),
                        requiredString(payload, "status"),
                        change.resourceRevision(),
                        assignedAt
                ));
            }
            default -> throw new IllegalArgumentException(
                    "unrecognized npc_spawn_assignment change_type: " + change.changeType());
        }
    }

    private static void applyWorldNpcChange(
            WorldStateChangeRecord change, Map<UUID, ServiceNpcAssignmentWorldNpcDefinition> worldNpcs
    ) {
        UUID publicId = requireUuid(change.resourceId(), "resource_id");
        switch (change.changeType()) {
            case "created" -> {
                JsonObject payload = requirePayload(change);
                worldNpcs.put(publicId, new ServiceNpcAssignmentWorldNpcDefinition(
                        publicId,
                        requiredString(payload, "name"),
                        requiredString(payload, "gender_key"),
                        requiredString(payload, "profession_key"),
                        optionalString(payload, "service_npc_type_key"),
                        change.resourceRevision()
                ));
            }
            default -> throw new IllegalArgumentException("unrecognized world_npc change_type: " + change.changeType());
        }
    }

    private static String checkReferentialIntegrity(ServiceNpcAssignmentsSnapshot candidate) {
        for (ServiceNpcAssignmentDefinition assignment : candidate.assignments().values()) {
            if (!candidate.spawnPoints().containsKey(assignment.spawnPointPublicId())) {
                return "assignment " + assignment.publicId() + " references missing spawn point " + assignment.spawnPointPublicId();
            }
            if (!candidate.worldNpcs().containsKey(assignment.worldNpcPublicId())) {
                return "assignment " + assignment.publicId() + " references missing world NPC " + assignment.worldNpcPublicId();
            }
        }
        return null;
    }

    private static JsonObject requirePayload(WorldStateChangeRecord change) {
        JsonObject payload = change.payload();
        if (payload == null) throw new IllegalArgumentException("payload must be present for " + change.resourceType());
        return payload;
    }

    private static UUID requireUuid(String raw, String field) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException(field + " must be a UUID: " + raw);
        }
    }

    private static UUID requiredUuid(JsonObject value, String field) {
        return requireUuid(requiredString(value, field), field);
    }

    private static UUID optionalUuid(JsonObject value, String field) {
        if (!value.has(field) || value.get(field).isJsonNull()) return null;
        return requireUuid(requiredString(value, field), field);
    }

    private static String requiredString(JsonObject value, String field) {
        JsonElement element = value.get(field);
        if (element == null || !element.isJsonPrimitive()) throw new IllegalArgumentException(field + " must be a string");
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isString() || primitive.getAsString().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-empty string");
        }
        return primitive.getAsString();
    }

    private static String optionalString(JsonObject value, String field) {
        if (!value.has(field) || value.get(field).isJsonNull()) return null;
        return requiredString(value, field);
    }

    private static int requiredInt(JsonObject value, String field) {
        JsonElement element = value.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
    }

    private static boolean requiredBoolean(JsonObject value, String field) {
        JsonElement element = value.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return element.getAsBoolean();
    }
}
