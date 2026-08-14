package com.seggellion.britannia_mod.worldstate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentSpawnPointDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentWorldNpcDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsSnapshot;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Milestone 13 NeoForge Slice 2: a pure transformation from a base {@link
 * ServiceNpcAssignmentsSnapshot} plus a validated batch of {@link WorldStateChangeRecord}s (Slice
 * 1's own output) to a new candidate snapshot -- {@code base} is never mutated, and nothing here
 * touches any live cache. Handles exactly the resource types Rails Milestone 13 actually publishes
 * on the delta channel today: {@code service_npc_spawn_point} (created/updated upsert, closed
 * removes) and {@code npc_spawn_assignment} (created/closed both upsert -- Rails keeps a closed
 * assignment's record with an updated status, it does not delete it).
 *
 * A spawn point's {@code minecraftServerPublicId} is read directly from the payload's own {@code
 * minecraft_server_public_id} field (Rails commit 608b978), the same key name {@code
 * ServiceNpcAssignmentsSerializer} already uses for the same concept in the bootstrap payload --
 * no fallback or substitution is needed, since Rails now always includes it.
 *
 * <h2>World NPC identity travels inline on the assignment change (Rails commit 777ad27)</h2>
 * There is no standalone {@code world_npc} resource type on the wire anymore, and no {@code
 * applyWorldNpcChange} here. Rails' own {@code WorldNpcs::Create} used to publish a standalone
 * "world_npc created" change unconditionally, regardless of whether an assignment existed yet --
 * a real, reachable unassigned-NPC dump through the delta channel, the same invariant Gate 13
 * already required the bootstrap serializer to respect. Rails removed that publish entirely; a
 * {@code npc_spawn_assignment} "created" change's own payload now carries the assigned World
 * NPC's {@code world_npc_name}/{@code world_npc_gender_key}/{@code world_npc_profession_key}/
 * {@code world_npc_service_npc_type_key}/{@code world_npc_definition_revision} fields inline, and
 * {@link #applyAssignmentChange} upserts {@code worldNpcs} from those fields in the same step as
 * the assignment itself -- exactly mirroring the bootstrap payload's own "derived from
 * assignments, never independently listed" shape. A {@code npc_spawn_assignment} "closed" change
 * carries no identity fields (Rails never re-sends them on close) and does not touch {@code
 * worldNpcs} at all; a client only ever learns a World NPC's identity from the "created" change
 * that first introduced its assignment.
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
 *       assignment referencing a spawn point that never existed in base or this batch at all, or a
 *       World NPC that is not present in the candidate. A spawn point this exact batch legitimately
 *       closed is the one exception -- its now-dangling assignment is cleaned up by the cascade
 *       that runs after this check, not treated as a failure (see {@link #checkReferentialIntegrity}).
 *       Same treatment as case 2: {@link Rejected}, base untouched.</li>
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
        Set<UUID> spawnPointsClosedThisBatch = new LinkedHashSet<>();

        try {
            for (WorldStateChangeRecord change : changes) {
                switch (change.resourceType()) {
                    case "service_npc_spawn_point" -> applySpawnPointChange(change, spawnPoints, spawnPointsClosedThisBatch);
                    case "npc_spawn_assignment" -> applyAssignmentChange(change, assignments, worldNpcs);
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

        // checkReferentialIntegrity runs first, against this batch's real, un-cascaded state --
        // an assignment referencing a spawn point that never existed in base or this batch (not
        // even via a "closed" change) is a genuinely malformed batch and must be caught here, not
        // silently pruned away before anything ever looked at it. spawnPointsClosedThisBatch is
        // passed through so the check can still tell that apart from the one case that is not
        // malformed: a spawn point this exact batch legitimately closed, whose now-dangling
        // assignment reference is expected and handled by the cascade below, not an error.
        ServiceNpcAssignmentsSnapshot preCascadeCandidate = new ServiceNpcAssignmentsSnapshot(
                base.schemaVersion(), base.revision(), spawnPoints, assignments, worldNpcs
        );

        String sanityFailure = checkReferentialIntegrity(preCascadeCandidate, spawnPointsClosedThisBatch);
        if (sanityFailure != null) return new Rejected(sanityFailure);

        // A removed spawn point can never leave a dangling assignment behind in the candidate,
        // even though real Rails traffic always closes the assignment first -- this candidate
        // must be internally consistent on its own terms, not by leaning on a publish-ordering
        // assumption this slice does not want to depend on. Runs only now, after
        // checkReferentialIntegrity has already confirmed every remaining dangling reference here
        // belongs to a spawn point this exact batch legitimately closed -- anything else would
        // already have been rejected above.
        assignments.values().removeIf(assignment -> !spawnPoints.containsKey(assignment.spawnPointPublicId()));

        ServiceNpcAssignmentsSnapshot candidate = new ServiceNpcAssignmentsSnapshot(
                base.schemaVersion(), base.revision(), spawnPoints, assignments, worldNpcs
        );

        return new Applied(candidate);
    }

    private static void applySpawnPointChange(
            WorldStateChangeRecord change,
            Map<UUID, ServiceNpcAssignmentSpawnPointDefinition> spawnPoints,
            Set<UUID> closedThisBatch
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
                        optionalString(payload, "economic_npc_type_key"),
                        requiredString(payload, "world_name"),
                        requiredString(payload, "dimension_key"),
                        requiredInt(payload, "x"),
                        requiredInt(payload, "y"),
                        requiredInt(payload, "z"),
                        requiredBoolean(payload, "enabled"),
                        change.resourceRevision()
                ));
            }
            case "closed" -> {
                spawnPoints.remove(publicId);
                closedThisBatch.add(publicId);
            }
            default -> throw new IllegalArgumentException(
                    "unrecognized service_npc_spawn_point change_type: " + change.changeType());
        }
    }

    private static void applyAssignmentChange(
            WorldStateChangeRecord change,
            Map<UUID, ServiceNpcAssignmentDefinition> assignments,
            Map<UUID, ServiceNpcAssignmentWorldNpcDefinition> worldNpcs
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
                UUID worldNpcPublicId = requiredUuid(payload, "world_npc_public_id");
                assignments.put(publicId, new ServiceNpcAssignmentDefinition(
                        publicId,
                        requiredUuid(payload, "spawn_point_public_id"),
                        worldNpcPublicId,
                        requiredString(payload, "status"),
                        change.resourceRevision(),
                        assignedAt
                ));

                // Only "created" carries the assigned World NPC's identity (Rails commit
                // 777ad27); "closed" re-publishes the same status/spawn_point/world_npc trio but
                // never identity, since the client already learned it from the earlier "created".
                if (change.changeType().equals("created")) {
                    worldNpcs.put(worldNpcPublicId, new ServiceNpcAssignmentWorldNpcDefinition(
                            worldNpcPublicId,
                            requiredString(payload, "world_npc_name"),
                            requiredString(payload, "world_npc_gender_key"),
                            requiredString(payload, "world_npc_profession_key"),
                            optionalString(payload, "world_npc_service_npc_type_key"),
                            requiredLong(payload, "world_npc_definition_revision")
                    ));
                }
            }
            default -> throw new IllegalArgumentException(
                    "unrecognized npc_spawn_assignment change_type: " + change.changeType());
        }
    }

    // Runs against the pre-cascade candidate (see apply()), so both branches below see the
    // batch's real, unmodified state:
    //
    // The missing-spawn-point branch now genuinely runs -- previously the cascade in apply() ran
    // before this check and unconditionally pruned any assignment whose spawn point was missing
    // for ANY reason, so this branch could never fire; it was dead code. spawnPointsClosedThisBatch
    // is the one legitimate exception: a spawn point this exact batch actually closed leaves its
    // referencing assignment dangling on purpose (real Rails traffic does not always close the
    // assignment first), and that case is not an error -- it is left for apply()'s cascade, which
    // runs only after this check has passed, to clean up. Anything else -- a spawn point that
    // never existed in base or this batch at all -- is a genuinely malformed batch and is now
    // caught here instead of being silently swept away.
    //
    // The missing-world-NPC branch is no longer reachable via an ordinary "created" assignment
    // change -- applyAssignmentChange now upserts worldNpcs from that same change's own embedded
    // identity, so a "created" assignment can never leave its World NPC missing. It remains a
    // real, meaningful backstop against a "closed" change arriving for an assignment whose
    // "created" never actually populated worldNpcs in this candidate's own history (a malformed
    // or out-of-order batch, or a corrupted base) -- see
    // anAssignmentClosedReferencingAWorldNpcNeverEstablishedFailsTheSanityCheckAndLeavesTheBaseUntouched.
    // There is no cascade for world NPCs, so no analogous exemption is needed here.
    private static String checkReferentialIntegrity(
            ServiceNpcAssignmentsSnapshot candidate, Set<UUID> spawnPointsClosedThisBatch
    ) {
        for (ServiceNpcAssignmentDefinition assignment : candidate.assignments().values()) {
            if (!candidate.spawnPoints().containsKey(assignment.spawnPointPublicId())
                    && !spawnPointsClosedThisBatch.contains(assignment.spawnPointPublicId())) {
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

    private static long requiredLong(JsonObject value, String field) {
        JsonElement element = value.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        try {
            return element.getAsLong();
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
