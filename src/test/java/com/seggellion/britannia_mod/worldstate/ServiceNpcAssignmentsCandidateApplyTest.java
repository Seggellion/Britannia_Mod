package com.seggellion.britannia_mod.worldstate;

import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentSpawnPointDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentWorldNpcDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsSnapshot;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 13 NeoForge Slice 2: {@link ServiceNpcAssignmentsCandidateApply} is a pure function,
 * so every test here operates purely on snapshots/records -- no cache, no server, no HTTP.
 */
class ServiceNpcAssignmentsCandidateApplyTest {
    private static final UUID FIRST_SERVER_ID = UUID.fromString("99999999-9999-4999-8999-999999999999");
    private static final UUID SECOND_SERVER_ID = UUID.fromString("88888888-8888-4888-8888-888888888888");
    private static final UUID SPAWN_POINT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID SECOND_SPAWN_POINT = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID WORLD_NPC = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ASSIGNMENT = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Test
    void spawnPointCreatedUpsertsIntoAnEmptyCandidateUsingTheRealMinecraftServerPublicIdFromThePayload() {
        WorldStateChangeRecord change = spawnPointChange(1, "created", SPAWN_POINT, 5, true, FIRST_SERVER_ID);

        ServiceNpcAssignmentsCandidateApply.Result result =
                ServiceNpcAssignmentsCandidateApply.apply(ServiceNpcAssignmentsSnapshot.empty(), List.of(change));

        ServiceNpcAssignmentsSnapshot candidate = applied(result);
        ServiceNpcAssignmentSpawnPointDefinition point = candidate.spawnPoints().get(SPAWN_POINT);
        assertEquals(SPAWN_POINT, point.publicId());
        assertEquals(FIRST_SERVER_ID, point.minecraftServerPublicId());
        assertEquals("Britain", point.worldName());
        assertEquals("minecraft:overworld", point.dimensionKey());
        assertEquals(10, point.x());
        assertEquals(64, point.y());
        assertEquals(20, point.z());
        assertTrue(point.enabled());
        assertEquals(5, point.revision());
    }

    /**
     * Adapts {@code ServiceNpcAssignmentsSnapshotFilterTest}'s established two-Minecraft-server
     * bootstrap construction to a delta: instead of one bootstrap payload listing two servers'
     * spawn points, this is one world-state batch whose two "created" changes each carry their
     * own real minecraft_server_public_id. Directly disproves the old placeholder behavior (every
     * delta-created spawn point attributed to whichever server happened to be polling) -- neither
     * server here is "the polling server" in this pure function at all anymore, and each spawn
     * point still lands on its own correct, independent owner.
     */
    @Test
    void twoSpawnPointsCreatedInOneBatchForDifferentServersEachGetTheirOwnCorrectOwningServerIdentity() {
        List<WorldStateChangeRecord> batch = List.of(
                spawnPointChange(1, "created", SPAWN_POINT, 1, true, FIRST_SERVER_ID),
                spawnPointChange(2, "created", SECOND_SPAWN_POINT, 1, true, SECOND_SERVER_ID)
        );

        ServiceNpcAssignmentsSnapshot candidate =
                applied(ServiceNpcAssignmentsCandidateApply.apply(ServiceNpcAssignmentsSnapshot.empty(), batch));

        assertEquals(2, candidate.spawnPoints().size());
        assertEquals(FIRST_SERVER_ID, candidate.spawnPoints().get(SPAWN_POINT).minecraftServerPublicId());
        assertEquals(SECOND_SERVER_ID, candidate.spawnPoints().get(SECOND_SPAWN_POINT).minecraftServerPublicId());
        assertFalse(candidate.spawnPoints().get(SPAWN_POINT).minecraftServerPublicId()
                        .equals(candidate.spawnPoints().get(SECOND_SPAWN_POINT).minecraftServerPublicId()),
                "the two spawn points must retain their own distinct owning servers, not collapse onto one");
    }

    @Test
    void spawnPointUpdateSourcesMinecraftServerPublicIdFreshFromThePayloadEachTime() {
        // Rails' own minecraft_server association for a spawn point is effectively immutable in
        // practice, but this proves the mechanism itself no longer special-cases "preserve
        // whatever was already known" the way the pre-fix placeholder logic had to -- every field,
        // including this one, is now read directly from each change's own payload, exactly like
        // world_name/x/y/z/enabled already were.
        ServiceNpcAssignmentsSnapshot base = snapshotWith(Map.of(
                SPAWN_POINT, new ServiceNpcAssignmentSpawnPointDefinition(
                        SPAWN_POINT, FIRST_SERVER_ID, null, null, "Britain", "minecraft:overworld", 1, 1, 1, true, 1L
                )
        ), Map.of(), Map.of());
        WorldStateChangeRecord change = spawnPointChange(2, "updated", SPAWN_POINT, 6, false, SECOND_SERVER_ID);

        ServiceNpcAssignmentsSnapshot candidate = applied(
                ServiceNpcAssignmentsCandidateApply.apply(base, List.of(change))
        );

        ServiceNpcAssignmentSpawnPointDefinition point = candidate.spawnPoints().get(SPAWN_POINT);
        assertEquals(SECOND_SERVER_ID, point.minecraftServerPublicId());
        assertEquals(10, point.x());
        assertFalse(point.enabled());
        assertEquals(6, point.revision());
    }

    @Test
    void spawnPointClosedRemovesItFromTheCandidate() {
        ServiceNpcAssignmentsSnapshot base = snapshotWith(Map.of(
                SPAWN_POINT, new ServiceNpcAssignmentSpawnPointDefinition(
                        SPAWN_POINT, FIRST_SERVER_ID, null, null, "Britain", "minecraft:overworld", 1, 1, 1, true, 1L
                )
        ), Map.of(), Map.of());
        WorldStateChangeRecord change = new WorldStateChangeRecord(
                3, "closed", "service_npc_spawn_point", SPAWN_POINT.toString(), 1, new JsonObject(), "2026-07-16T12:00:00.000000Z"
        );

        ServiceNpcAssignmentsSnapshot candidate = applied(
                ServiceNpcAssignmentsCandidateApply.apply(base, List.of(change))
        );

        assertFalse(candidate.spawnPoints().containsKey(SPAWN_POINT));
    }

    @Test
    void spawnPointClosedCascadesToRemoveAnAssignmentThatReferencedIt() {
        ServiceNpcAssignmentsSnapshot base = snapshotWith(
                Map.of(SPAWN_POINT, spawnPoint(SPAWN_POINT)),
                Map.of(ASSIGNMENT, new ServiceNpcAssignmentDefinition(ASSIGNMENT, SPAWN_POINT, WORLD_NPC, "active", 1L, "2026-07-16T12:00:00Z")),
                Map.of(WORLD_NPC, worldNpc(WORLD_NPC))
        );
        WorldStateChangeRecord change = new WorldStateChangeRecord(
                4, "closed", "service_npc_spawn_point", SPAWN_POINT.toString(), 1, new JsonObject(), "2026-07-16T12:00:00.000000Z"
        );

        ServiceNpcAssignmentsSnapshot candidate = applied(
                ServiceNpcAssignmentsCandidateApply.apply(base, List.of(change))
        );

        assertFalse(candidate.spawnPoints().containsKey(SPAWN_POINT));
        assertTrue(candidate.assignments().isEmpty(), "closing the spawn point must cascade-remove the assignment that referenced it");
        assertTrue(candidate.worldNpcs().containsKey(WORLD_NPC), "the world NPC itself must survive -- only the dangling assignment is cascaded away");
    }

    @Test
    void assignmentCreatedUpsertsUsingThisChangesCreatedAtAsAssignedAt() {
        ServiceNpcAssignmentsSnapshot base = snapshotWith(
                Map.of(SPAWN_POINT, spawnPoint(SPAWN_POINT)), Map.of(), Map.of(WORLD_NPC, worldNpc(WORLD_NPC))
        );
        WorldStateChangeRecord change = assignmentCreatedChange(5, "2026-07-16T13:00:00.000000Z");

        ServiceNpcAssignmentsSnapshot candidate = applied(
                ServiceNpcAssignmentsCandidateApply.apply(base, List.of(change))
        );

        ServiceNpcAssignmentDefinition assignment = candidate.assignments().get(ASSIGNMENT);
        assertEquals("active", assignment.status());
        assertEquals(SPAWN_POINT, assignment.spawnPointPublicId());
        assertEquals(WORLD_NPC, assignment.worldNpcPublicId());
        assertEquals(5, assignment.revision());
        assertEquals("2026-07-16T13:00:00.000000Z", assignment.assignedAt());
    }

    @Test
    void assignmentClosedUpsertsWithUpdatedStatusAndPreservesTheOriginalAssignedAt() {
        ServiceNpcAssignmentsSnapshot base = snapshotWith(
                Map.of(SPAWN_POINT, spawnPoint(SPAWN_POINT)),
                Map.of(ASSIGNMENT, new ServiceNpcAssignmentDefinition(ASSIGNMENT, SPAWN_POINT, WORLD_NPC, "active", 5L, "2026-07-16T13:00:00Z")),
                Map.of(WORLD_NPC, worldNpc(WORLD_NPC))
        );
        WorldStateChangeRecord change = assignmentClosedChange(6, "2026-07-16T14:00:00.000000Z");

        ServiceNpcAssignmentsSnapshot candidate = applied(
                ServiceNpcAssignmentsCandidateApply.apply(base, List.of(change))
        );

        ServiceNpcAssignmentDefinition assignment = candidate.assignments().get(ASSIGNMENT);
        assertEquals("closed", assignment.status());
        assertEquals(6, assignment.revision());
        assertEquals("2026-07-16T13:00:00Z", assignment.assignedAt(), "closing must preserve the original assigned_at, not substitute this change's own created_at");
        ServiceNpcAssignmentWorldNpcDefinition npc = candidate.worldNpcs().get(WORLD_NPC);
        assertEquals("Alice", npc.name(), "closing carries no identity fields -- the World NPC entry from base must survive completely untouched");
    }

    /**
     * Milestone 13 NeoForge follow-up (Rails commit 777ad27): there is no standalone {@code
     * world_npc} resource type on the wire anymore. An assignment's own "created" change payload
     * now carries the assigned World NPC's identity inline, and this is the only way a World NPC
     * ever enters the candidate. world_npc_definition_revision is asserted as a distinct value
     * from the change's own resourceRevision (the assignment's revision) to prove the World NPC's
     * revision is read from its own dedicated field, not confused with the assignment's.
     */
    @Test
    void assignmentCreatedUpsertsAFullyFormedWorldNpcEntryFromEmbeddedIdentity() {
        ServiceNpcAssignmentsSnapshot base = snapshotWith(
                Map.of(SPAWN_POINT, spawnPoint(SPAWN_POINT)), Map.of(), Map.of()
        );
        WorldStateChangeRecord change = assignmentCreatedChange(7, "2026-07-16T12:00:00.000000Z");

        ServiceNpcAssignmentsSnapshot candidate = applied(
                ServiceNpcAssignmentsCandidateApply.apply(base, List.of(change))
        );

        ServiceNpcAssignmentWorldNpcDefinition npc = candidate.worldNpcs().get(WORLD_NPC);
        assertEquals("Alice", npc.name());
        assertEquals("female", npc.genderKey());
        assertEquals("banker", npc.professionKey());
        assertEquals("bank_teller", npc.serviceNpcTypeKey());
        assertEquals(3L, npc.revision(),
                "the World NPC's own revision must come from world_npc_definition_revision (3), not the assignment change's own resourceRevision (7)");
    }

    @Test
    void assignmentCreatedWithoutAServiceNpcTypeKeyLeavesItNull() {
        ServiceNpcAssignmentsSnapshot base = snapshotWith(
                Map.of(SPAWN_POINT, spawnPoint(SPAWN_POINT)), Map.of(), Map.of()
        );
        JsonObject payload = new JsonObject();
        payload.addProperty("status", "active");
        payload.addProperty("spawn_point_public_id", SPAWN_POINT.toString());
        payload.addProperty("world_npc_public_id", WORLD_NPC.toString());
        payload.addProperty("world_npc_name", "Alice");
        payload.addProperty("world_npc_gender_key", "female");
        payload.addProperty("world_npc_profession_key", "banker");
        payload.addProperty("world_npc_definition_revision", 1L);
        // world_npc_service_npc_type_key deliberately omitted -- Rails' own payload emits this as
        // null whenever the World NPC has no service_npc_type association (@world_npc.service_npc_type&.key).
        WorldStateChangeRecord change = new WorldStateChangeRecord(
                1, "created", "npc_spawn_assignment", ASSIGNMENT.toString(), 1, payload, "2026-07-16T12:00:00.000000Z"
        );

        ServiceNpcAssignmentsSnapshot candidate = applied(
                ServiceNpcAssignmentsCandidateApply.apply(base, List.of(change))
        );

        assertEquals(null, candidate.worldNpcs().get(WORLD_NPC).serviceNpcTypeKey());
    }

    @Test
    void reapplyingTheIdenticalBatchProducesAByteIdenticalResultingState() {
        List<WorldStateChangeRecord> batch = List.of(
                spawnPointChange(1, "created", SPAWN_POINT, 1, true, FIRST_SERVER_ID),
                assignmentCreatedChange(2, "2026-07-16T12:00:00.000000Z")
        );

        ServiceNpcAssignmentsSnapshot firstApplication = applied(
                ServiceNpcAssignmentsCandidateApply.apply(ServiceNpcAssignmentsSnapshot.empty(), batch)
        );
        ServiceNpcAssignmentsSnapshot secondApplication = applied(
                ServiceNpcAssignmentsCandidateApply.apply(firstApplication, batch)
        );

        assertEquals(firstApplication, secondApplication,
                "re-applying the identical batch to its own prior result must be a no-op producing identical state");
    }

    @Test
    void reapplyingTheIdenticalBatchFromTheOriginalBaseAlsoMatchesTheFirstApplication() {
        List<WorldStateChangeRecord> batch = List.of(
                spawnPointChange(1, "created", SPAWN_POINT, 1, true, FIRST_SERVER_ID),
                assignmentCreatedChange(2, "2026-07-16T12:00:00.000000Z")
        );

        ServiceNpcAssignmentsSnapshot fromEmpty = applied(
                ServiceNpcAssignmentsCandidateApply.apply(ServiceNpcAssignmentsSnapshot.empty(), batch)
        );
        ServiceNpcAssignmentsSnapshot appliedAgainFromEmpty = applied(
                ServiceNpcAssignmentsCandidateApply.apply(ServiceNpcAssignmentsSnapshot.empty(), batch)
        );

        assertEquals(fromEmpty, appliedAgainFromEmpty, "apply() is not a pure deterministic function of its inputs");
    }

    @Test
    void aScrambledInternalOrderDoesNotCorruptTheCandidateEvenThoughSlice1sValidationWouldNeverLetThisThrough() {
        // Slice 1's WorldStateSyncValidator already rejects any response whose changes are not
        // strictly increasing by shard-wide version before this class ever sees it -- this test
        // exists purely as a defensive backstop proving apply() itself cannot be corrupted by
        // input ordering, not because this input is expected to occur in practice.
        List<WorldStateChangeRecord> scrambled = List.of(
                assignmentCreatedChange(8, "2026-07-16T12:00:00.000000Z"),
                spawnPointChange(7, "created", SPAWN_POINT, 1, true, FIRST_SERVER_ID)
        );

        ServiceNpcAssignmentsCandidateApply.Result result =
                ServiceNpcAssignmentsCandidateApply.apply(ServiceNpcAssignmentsSnapshot.empty(), scrambled);

        ServiceNpcAssignmentsSnapshot candidate = applied(result);
        assertTrue(candidate.spawnPoints().containsKey(SPAWN_POINT));
        assertTrue(candidate.worldNpcs().containsKey(WORLD_NPC));
        assertTrue(candidate.assignments().containsKey(ASSIGNMENT));
    }

    @Test
    void aMalformedSpawnPointPayloadMissingARequiredFieldIsRejectedWithoutTouchingTheBase() {
        JsonObject incompletePayload = new JsonObject();
        incompletePayload.addProperty("minecraft_server_public_id", FIRST_SERVER_ID.toString());
        incompletePayload.addProperty("world_name", "Britain");
        // deliberately missing dimension_key/x/y/z/enabled
        WorldStateChangeRecord change = new WorldStateChangeRecord(
                1, "created", "service_npc_spawn_point", SPAWN_POINT.toString(), 1, incompletePayload, "2026-07-16T12:00:00.000000Z"
        );
        ServiceNpcAssignmentsSnapshot base = ServiceNpcAssignmentsSnapshot.empty();

        ServiceNpcAssignmentsCandidateApply.Result result =
                ServiceNpcAssignmentsCandidateApply.apply(base, List.of(change));

        ServiceNpcAssignmentsCandidateApply.Rejected rejected =
                assertInstanceOf(ServiceNpcAssignmentsCandidateApply.Rejected.class, result);
        assertTrue(rejected.reason().startsWith("malformed_change:"), rejected.reason());
        assertTrue(base.isEmpty(), "the base snapshot must remain completely untouched after a rejected batch");
    }

    @Test
    void aSpawnPointPayloadMissingMinecraftServerPublicIdIsRejectedWithoutTouchingTheBase() {
        JsonObject payloadMissingServerId = new JsonObject();
        payloadMissingServerId.addProperty("world_name", "Britain");
        payloadMissingServerId.addProperty("dimension_key", "minecraft:overworld");
        payloadMissingServerId.addProperty("x", 1);
        payloadMissingServerId.addProperty("y", 1);
        payloadMissingServerId.addProperty("z", 1);
        payloadMissingServerId.addProperty("enabled", true);
        WorldStateChangeRecord change = new WorldStateChangeRecord(
                1, "created", "service_npc_spawn_point", SPAWN_POINT.toString(), 1, payloadMissingServerId, "2026-07-16T12:00:00.000000Z"
        );
        ServiceNpcAssignmentsSnapshot base = ServiceNpcAssignmentsSnapshot.empty();

        ServiceNpcAssignmentsCandidateApply.Result result =
                ServiceNpcAssignmentsCandidateApply.apply(base, List.of(change));

        ServiceNpcAssignmentsCandidateApply.Rejected rejected =
                assertInstanceOf(ServiceNpcAssignmentsCandidateApply.Rejected.class, result);
        assertTrue(rejected.reason().startsWith("malformed_change:"), rejected.reason());
        assertTrue(base.isEmpty(), "the base snapshot must remain completely untouched after a rejected batch");
    }

    @Test
    void anUnrecognizedChangeTypeIsRejectedWithoutTouchingTheBase() {
        WorldStateChangeRecord change = new WorldStateChangeRecord(
                1, "reassigned_somehow", "npc_spawn_assignment", ASSIGNMENT.toString(), 1, new JsonObject(), "2026-07-16T12:00:00.000000Z"
        );
        ServiceNpcAssignmentsSnapshot base = snapshotWith(
                Map.of(SPAWN_POINT, spawnPoint(SPAWN_POINT)),
                Map.of(ASSIGNMENT, new ServiceNpcAssignmentDefinition(ASSIGNMENT, SPAWN_POINT, WORLD_NPC, "active", 1L, "2026-07-16T12:00:00Z")),
                Map.of(WORLD_NPC, worldNpc(WORLD_NPC))
        );

        ServiceNpcAssignmentsCandidateApply.Result result =
                ServiceNpcAssignmentsCandidateApply.apply(base, List.of(change));

        ServiceNpcAssignmentsCandidateApply.Rejected rejected =
                assertInstanceOf(ServiceNpcAssignmentsCandidateApply.Rejected.class, result);
        assertTrue(rejected.reason().contains("unrecognized"), rejected.reason());
        assertEquals(1, base.assignments().size(), "base must be completely unaffected by a rejected batch");
        assertEquals("active", base.assignments().get(ASSIGNMENT).status(), "base's own content must be byte-identical to before the rejected attempt");
    }

    /**
     * Milestone 13 NeoForge follow-up (Rails commit 777ad27): an assignment "created" change can
     * no longer fail this check on its own -- it upserts its own World NPC entry from its own
     * embedded identity in the same step, so the map it is checked against is always already
     * populated for it. The check remains meaningful for a "closed" change whose assignment was
     * never actually established in this candidate's own history (a malformed/out-of-order batch,
     * or a corrupted base) -- a real case distinct from (and not repaired by) the spawn-point
     * cascade a few lines above it in apply(), since nothing analogous exists for world NPCs.
     */
    @Test
    void anAssignmentClosedReferencingAWorldNpcNeverEstablishedFailsTheSanityCheckAndLeavesTheBaseUntouched() {
        ServiceNpcAssignmentsSnapshot base = snapshotWith(Map.of(SPAWN_POINT, spawnPoint(SPAWN_POINT)), Map.of(), Map.of());
        WorldStateChangeRecord change = assignmentClosedChange(1, "2026-07-16T12:00:00.000000Z");

        ServiceNpcAssignmentsCandidateApply.Result result =
                ServiceNpcAssignmentsCandidateApply.apply(base, List.of(change));

        ServiceNpcAssignmentsCandidateApply.Rejected rejected =
                assertInstanceOf(ServiceNpcAssignmentsCandidateApply.Rejected.class, result);
        assertTrue(rejected.reason().contains("missing world NPC"), rejected.reason());
        assertTrue(base.assignments().isEmpty(), "base must remain exactly as it was -- no dangling assignment ever committed to it");
    }

    /**
     * Milestone 13 NeoForge follow-up (reordering fix): before this fix, apply()'s spawn-point
     * cascade ran before checkReferentialIntegrity and unconditionally pruned ANY assignment whose
     * spawn point was missing, for any reason -- so this exact scenario could never actually reach
     * the check; the missing-spawn-point branch was dead code. checkReferentialIntegrity now runs
     * first, against the batch's real, un-cascaded state: SPAWN_POINT is never created in this
     * batch or present in base, so it is not among spawnPointsClosedThisBatch either -- a
     * genuinely missing reference, not a legitimate in-batch closure, and it is now caught here
     * instead of being silently swept away. Mirrors
     * anAssignmentClosedReferencingAWorldNpcNeverEstablishedFailsTheSanityCheckAndLeavesTheBaseUntouched
     * exactly, for the spawn-point side of the same check.
     */
    @Test
    void anAssignmentReferencingASpawnPointThatWasNeverEstablishedFailsTheSanityCheckAndLeavesTheBaseUntouched() {
        ServiceNpcAssignmentsSnapshot base = ServiceNpcAssignmentsSnapshot.empty();
        WorldStateChangeRecord change = assignmentCreatedChange(1, "2026-07-16T12:00:00.000000Z");

        ServiceNpcAssignmentsCandidateApply.Result result =
                ServiceNpcAssignmentsCandidateApply.apply(base, List.of(change));

        ServiceNpcAssignmentsCandidateApply.Rejected rejected =
                assertInstanceOf(ServiceNpcAssignmentsCandidateApply.Rejected.class, result);
        assertTrue(rejected.reason().contains("missing spawn point"), rejected.reason());
        assertTrue(base.isEmpty(), "base must remain exactly as it was -- no dangling assignment ever committed to it");
    }

    /**
     * A "closed" change carries no identity fields (see assignmentClosedChange), so this proves
     * the missing-spawn-point branch fires the same way for "closed" as it does for "created" --
     * neither change_type gets a pass just because the spawn point happens to be missing for a
     * different reason than an in-batch closure.
     */
    @Test
    void anAssignmentClosedReferencingASpawnPointThatWasNeverEstablishedFailsTheSanityCheckAndLeavesTheBaseUntouched() {
        ServiceNpcAssignmentsSnapshot base = ServiceNpcAssignmentsSnapshot.empty();
        WorldStateChangeRecord change = assignmentClosedChange(1, "2026-07-16T12:00:00.000000Z");

        ServiceNpcAssignmentsCandidateApply.Result result =
                ServiceNpcAssignmentsCandidateApply.apply(base, List.of(change));

        ServiceNpcAssignmentsCandidateApply.Rejected rejected =
                assertInstanceOf(ServiceNpcAssignmentsCandidateApply.Rejected.class, result);
        assertTrue(rejected.reason().contains("missing spawn point"), rejected.reason());
        assertTrue(base.isEmpty(), "base must remain exactly as it was -- no dangling assignment ever committed to it");
    }

    private static ServiceNpcAssignmentsSnapshot applied(ServiceNpcAssignmentsCandidateApply.Result result) {
        return assertInstanceOf(ServiceNpcAssignmentsCandidateApply.Applied.class, result).candidate();
    }

    private static ServiceNpcAssignmentsSnapshot snapshotWith(
            Map<UUID, ServiceNpcAssignmentSpawnPointDefinition> spawnPoints,
            Map<UUID, ServiceNpcAssignmentDefinition> assignments,
            Map<UUID, ServiceNpcAssignmentWorldNpcDefinition> worldNpcs
    ) {
        return new ServiceNpcAssignmentsSnapshot(1, 0L, new LinkedHashMap<>(spawnPoints), new LinkedHashMap<>(assignments), new LinkedHashMap<>(worldNpcs));
    }

    private static ServiceNpcAssignmentSpawnPointDefinition spawnPoint(UUID publicId) {
        return new ServiceNpcAssignmentSpawnPointDefinition(
                publicId, FIRST_SERVER_ID, null, null, "Britain", "minecraft:overworld", 1, 1, 1, true, 1L
        );
    }

    private static ServiceNpcAssignmentWorldNpcDefinition worldNpc(UUID publicId) {
        return new ServiceNpcAssignmentWorldNpcDefinition(publicId, "Alice", "female", "banker", "bank_teller", 1L);
    }

    private static WorldStateChangeRecord spawnPointChange(
            long version, String changeType, UUID publicId, long revision, boolean enabled, UUID minecraftServerPublicId
    ) {
        JsonObject payload = new JsonObject();
        payload.addProperty("minecraft_server_public_id", minecraftServerPublicId.toString());
        payload.addProperty("world_name", "Britain");
        payload.addProperty("dimension_key", "minecraft:overworld");
        payload.addProperty("x", 10);
        payload.addProperty("y", 64);
        payload.addProperty("z", 20);
        payload.addProperty("enabled", enabled);
        return new WorldStateChangeRecord(
                version, changeType, "service_npc_spawn_point", publicId.toString(), revision, payload, "2026-07-16T12:00:00.000000Z"
        );
    }

    // Milestone 13 NeoForge follow-up (Rails commit 777ad27): a "created" assignment change's own
    // payload carries the assigned World NPC's identity inline -- world_npc_definition_revision is
    // fixed at 3 here, deliberately distinct from every version value these tests use for the
    // change itself, so a test asserting on npc.revision() proves the two are read from separate
    // fields rather than one being silently substituted for the other.
    private static WorldStateChangeRecord assignmentCreatedChange(long version, String createdAt) {
        JsonObject payload = new JsonObject();
        payload.addProperty("status", "active");
        payload.addProperty("spawn_point_public_id", SPAWN_POINT.toString());
        payload.addProperty("world_npc_public_id", WORLD_NPC.toString());
        payload.addProperty("world_npc_name", "Alice");
        payload.addProperty("world_npc_gender_key", "female");
        payload.addProperty("world_npc_profession_key", "banker");
        payload.addProperty("world_npc_service_npc_type_key", "bank_teller");
        payload.addProperty("world_npc_definition_revision", 3L);
        return new WorldStateChangeRecord(version, "created", "npc_spawn_assignment", ASSIGNMENT.toString(), version, payload, createdAt);
    }

    // Rails' own NpcSpawnAssignments::Close never re-publishes identity fields -- a client that
    // already saw the "created" change does not need them again, and one that has not (a malformed
    // or out-of-order batch) is exactly what anAssignmentClosedReferencingAWorldNpcNeverEstablished...
    // exists to catch.
    private static WorldStateChangeRecord assignmentClosedChange(long version, String createdAt) {
        JsonObject payload = new JsonObject();
        payload.addProperty("status", "closed");
        payload.addProperty("spawn_point_public_id", SPAWN_POINT.toString());
        payload.addProperty("world_npc_public_id", WORLD_NPC.toString());
        return new WorldStateChangeRecord(version, "closed", "npc_spawn_assignment", ASSIGNMENT.toString(), version, payload, createdAt);
    }
}
