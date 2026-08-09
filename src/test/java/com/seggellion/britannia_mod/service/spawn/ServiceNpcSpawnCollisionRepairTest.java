package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ServiceNpcSpawnCollisionRepairTest {
    private static final UUID SERVER_KEY =
        UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID CITY =
        UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final ServiceNpcSpawnLocation LOCATION = new ServiceNpcSpawnLocation(
        "Britannia", ResourceLocation.parse("minecraft:overworld"), new BlockPos(10, 64, 20)
    );

    @Test
    void validatesOnlyAuthoritativeRepairableEvidence() {
        ServiceNpcSpawnPendingRecord same = collision(
            0, liveEvidence(SERVER_KEY, LOCATION.pos()), null
        );
        assertEquals(
            ServiceNpcSpawnCollisionRepairCoordinator.EvidenceVerdict.SAME_LOCATION,
            ServiceNpcSpawnCollisionRepairCoordinator.validateEvidence(same, SERVER_KEY)
        );

        ServiceNpcSpawnPendingRecord differentPosition = collision(
            0, liveEvidence(SERVER_KEY, LOCATION.pos().offset(1, 0, 0)), null
        );
        assertEquals(
            ServiceNpcSpawnCollisionRepairCoordinator.EvidenceVerdict.REPAIRABLE,
            ServiceNpcSpawnCollisionRepairCoordinator.validateEvidence(differentPosition, SERVER_KEY)
        );
        ServiceNpcSpawnPendingRecord differentServer = collision(
            0,
            liveEvidence(UUID.fromString("30000000-0000-4000-8000-000000000003"), LOCATION.pos()),
            null
        );
        assertEquals(
            ServiceNpcSpawnCollisionRepairCoordinator.EvidenceVerdict.REPAIRABLE,
            ServiceNpcSpawnCollisionRepairCoordinator.validateEvidence(differentServer, SERVER_KEY)
        );
        assertEquals(
            ServiceNpcSpawnCollisionRepairCoordinator.EvidenceVerdict.LOCAL_SERVER_KEY_UNAVAILABLE,
            ServiceNpcSpawnCollisionRepairCoordinator.validateEvidence(same, null)
        );

        assertEquals(
            ServiceNpcSpawnCollisionRepairCoordinator.EvidenceVerdict.REPAIRABLE,
            ServiceNpcSpawnCollisionRepairCoordinator.validateEvidence(
                collision(0, evidence(ServiceNpcSpawnCollisionEvidence.CollisionKind.TOMBSTONED), null),
                null
            )
        );
        assertEquals(
            ServiceNpcSpawnCollisionRepairCoordinator.EvidenceVerdict.REPAIRABLE,
            ServiceNpcSpawnCollisionRepairCoordinator.validateEvidence(
                collision(0, evidence(ServiceNpcSpawnCollisionEvidence.CollisionKind.REDACTED), null),
                null
            )
        );

        ServiceNpcSpawnCollisionEvidence notRequired = new ServiceNpcSpawnCollisionEvidence(
            ServiceNpcSpawnCollisionEvidence.CollisionKind.REDACTED, false, null, 1L
        );
        assertThrows(IllegalArgumentException.class, () -> new ServiceNpcSpawnPendingRecord(
            ServiceNpcSpawnPendingOperation.UPSERT, uuid(10), "Britannia", LOCATION,
            CITY, "bank_teller", true, 1L, 1L, uuid(11),
            ServiceNpcSpawnPendingDisposition.COLLISION_REPAIR, 0, null, 0L,
            null, null, 0, notRequired
        ));
        assertThrows(IllegalArgumentException.class, () -> new ServiceNpcSpawnCanonicalLocation(
            SERVER_KEY, "", ResourceLocation.parse("minecraft:overworld"), 0, 64, 0
        ));

        ServiceNpcSpawnClientResult genericConflict = new ServiceNpcSpawnClientResult.HttpFailure(
            "conflict", ServiceNpcSpawnClientResult.Disposition.PERMANENT, Duration.ZERO
        );
        assertEquals(ServiceNpcSpawnDeliveryDecision.PERMANENT_FAILURE,
            ServiceNpcSpawnDeliveryDecision.classify(genericConflict));
    }

    @Test
    void uuidGenerationIsVersionFourBoundedAndRejectsLocalUse() {
        ServiceNpcSpawnPendingData data = new ServiceNpcSpawnPendingData();
        ServiceNpcSpawnClaimData claims = new ServiceNpcSpawnClaimData();
        UUID oldId = uuid(1);
        UUID claimed = uuid(2);
        UUID pending = uuid(3);
        UUID valid = uuid(4);
        assertEquals(ServiceNpcSpawnClaimData.ClaimResult.CLAIMED, claims.claim(claimed, LOCATION));
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.ACCEPTED,
            data.put(ready(pending, uuid(30), 0)));

        Queue<UUID> candidates = new ArrayDeque<>(List.of(
            oldId,
            UUID.fromString("40000000-0000-3000-8000-000000000004"),
            claimed,
            pending,
            valid
        ));
        UUID generated = ServiceNpcSpawnCollisionRepairCoordinator.generateReplacementUuid(
            oldId, data, claims, candidates::remove
        );
        assertEquals(valid, generated);
        assertTrue(ServiceNpcSpawnCollisionRepairCoordinator.isVersionFour(generated));

        assertNull(ServiceNpcSpawnCollisionRepairCoordinator.generateReplacementUuid(
            oldId, data, claims, () -> oldId
        ));
    }

    @Test
    void atomicStagingReplacesExactAWithCrashSafeB() {
        ServiceNpcSpawnPendingData data = new ServiceNpcSpawnPendingData();
        ServiceNpcSpawnPendingRecord source = collision(
            0, evidence(ServiceNpcSpawnCollisionEvidence.CollisionKind.REDACTED), null
        );
        ServiceNpcSpawnPendingRecord unrelated = ready(uuid(50), uuid(51), 0);
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.ACCEPTED, data.put(source));
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.ACCEPTED, data.put(unrelated));

        UUID replacementId = uuid(20);
        UUID operationId = uuid(21);
        ServiceNpcSpawnPendingData.CollisionReplacementStage result = data.stageCollisionReplacement(
            source.token(), source.collisionEvidence(), replacementId, operationId,
            source.location(), source.cityPublicId(), source.serviceNpcTypeKey(),
            source.enabled(), 100L
        );
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.ACCEPTED, result.result());
        ServiceNpcSpawnPendingRecord staged = requireNonNull(result.replacement());
        assertNull(data.findPending(source.spawnPointId()));
        assertEquals(staged, data.findPending(replacementId));
        assertEquals(ServiceNpcSpawnPendingOperation.UPSERT, staged.operation());
        assertEquals(operationId, staged.operationId());
        assertEquals(1L, staged.configurationRevision());
        assertEquals(1, staged.collisionRepairCount());
        assertEquals(source.spawnPointId(), staged.supersedesSpawnPointId());
        assertEquals(ServiceNpcSpawnPendingDisposition.COLLISION_REPAIR, staged.disposition());
        assertEquals(0, staged.attemptCount());
        assertNull(staged.lastAttemptAtEpochMillis());
        assertEquals(0L, staged.nextAttemptAtEpochMillis());
        assertNull(staged.lastFailureCode());
        assertEquals(source.cityPublicId(), staged.cityPublicId());
        assertEquals(source.serviceNpcTypeKey(), staged.serviceNpcTypeKey());
        assertEquals(source.enabled(), staged.enabled());
        assertEquals(unrelated, data.findPending(unrelated.spawnPointId()));
        assertTrue(data.snapshot().values().stream()
            .noneMatch(record -> record.operation() == ServiceNpcSpawnPendingOperation.REMOVE));

        ServiceNpcSpawnPendingData.CollisionReplacementStage obsolete = data.stageCollisionReplacement(
            source.token(), source.collisionEvidence(), uuid(22), uuid(23),
            source.location(), source.cityPublicId(), source.serviceNpcTypeKey(),
            source.enabled(), 101L
        );
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.REJECTED_STALE, obsolete.result());

        CompoundTag persisted = data.save(new CompoundTag(), null);
        ServiceNpcSpawnPendingData reloaded = ServiceNpcSpawnPendingData.load(persisted, null);
        assertEquals(staged, reloaded.findPending(replacementId));
        assertNull(reloaded.findPending(source.spawnPointId()));
        assertEquals(2, reloaded.snapshot().size());
        assertEquals(ServiceNpcSpawnPendingData.SCHEMA_VERSION, persisted.getInt("SchemaVersion"));

        assertTrue(data.markCollisionReplacementReady(staged.token(), source.spawnPointId()));
        ServiceNpcSpawnPendingRecord ready = data.findPending(replacementId);
        assertEquals(ServiceNpcSpawnPendingDisposition.READY, ready.disposition());
        assertEquals(operationId, ready.operationId());
        assertEquals(source.spawnPointId(), ready.supersedesSpawnPointId());
        assertEquals(1, ready.collisionRepairCount());
        assertNull(ready.collisionEvidence());
        assertFalse(data.markCollisionReplacementReady(staged.token(), source.spawnPointId()));
    }

    @Test
    void repeatedCollisionsUseImmediateLineageAndStopBeforeFourthReplacement() {
        ServiceNpcSpawnPendingData data = new ServiceNpcSpawnPendingData();
        ServiceNpcSpawnPendingRecord current = collision(
            0, evidence(ServiceNpcSpawnCollisionEvidence.CollisionKind.REDACTED), null
        );
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.ACCEPTED, data.put(current));

        for (int expectedCount = 1; expectedCount <= 3; expectedCount++) {
            UUID oldId = current.spawnPointId();
            UUID nextId = uuid(100 + expectedCount);
            UUID operationId = uuid(110 + expectedCount);
            ServiceNpcSpawnPendingData.CollisionReplacementStage staged =
                data.stageCollisionReplacement(
                    current.token(), current.collisionEvidence(), nextId, operationId,
                    current.location(), current.cityPublicId(), current.serviceNpcTypeKey(),
                    current.enabled(), 200L + expectedCount
                );
            assertEquals(ServiceNpcSpawnPendingData.MutationResult.ACCEPTED, staged.result());
            current = requireNonNull(staged.replacement());
            assertEquals(expectedCount, current.collisionRepairCount());
            assertEquals(oldId, current.supersedesSpawnPointId());
            assertTrue(data.markCollisionReplacementReady(current.token(), oldId));
            current = data.findPending(nextId);
            assertTrue(data.markCollisionRepair(
                current.token(), evidence(ServiceNpcSpawnCollisionEvidence.CollisionKind.REDACTED),
                "uuid_collision_pending_repair"
            ));
            current = data.findPending(nextId);
            assertNull(current.supersedesSpawnPointId());
            assertEquals(expectedCount, current.collisionRepairCount());
        }

        ServiceNpcSpawnPendingData.CollisionReplacementStage fourth =
            data.stageCollisionReplacement(
                current.token(), current.collisionEvidence(), uuid(120), uuid(121),
                current.location(), current.cityPublicId(), current.serviceNpcTypeKey(),
                current.enabled(), 300L
            );
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.REJECTED_CONFLICT, fourth.result());
        assertEquals(current, data.findPending(current.spawnPointId()));
    }

    @Test
    void stagedReplacementIsGuardedUntilExactReadyTransition() {
        ServiceNpcSpawnPendingData data = new ServiceNpcSpawnPendingData();
        ServiceNpcSpawnPendingRecord source = collision(
            0, evidence(ServiceNpcSpawnCollisionEvidence.CollisionKind.TOMBSTONED), null
        );
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.ACCEPTED, data.put(source));
        UUID replacementId = uuid(60);
        ServiceNpcSpawnPendingData.CollisionReplacementStage stage = data.stageCollisionReplacement(
            source.token(), source.collisionEvidence(), replacementId, uuid(61),
            source.location(), source.cityPublicId(), source.serviceNpcTypeKey(),
            source.enabled(), 400L
        );
        ServiceNpcSpawnPendingRecord staged = requireNonNull(stage.replacement());
        assertTrue(ServiceNpcSpawnDeliveryProcessor.selectCandidates(
            data.snapshot().values(), 1_000L, java.util.Set.of(), java.util.Set.of()
        ).isEmpty());
        assertFalse(data.markCollisionReplacementReady(staged.token(), uuid(999)));
        assertTrue(data.markCollisionReplacementReady(staged.token(), source.spawnPointId()));
        ServiceNpcSpawnPendingRecord ready = data.findPending(replacementId);
        assertEquals(List.of(ready), ServiceNpcSpawnDeliveryProcessor.selectCandidates(
            data.snapshot().values(), 1_000L, java.util.Set.of(), java.util.Set.of()
        ));
        assertEquals(staged.operationId(), ready.operationId());
        assertEquals(staged.supersedesSpawnPointId(), ready.supersedesSpawnPointId());
        assertEquals(0, ready.attemptCount());
        assertNull(ready.lastAttemptAtEpochMillis());
        assertEquals(0L, ready.nextAttemptAtEpochMillis());
        assertNull(ready.lastFailureCode());

        ServiceNpcSpawnPendingData occupied = new ServiceNpcSpawnPendingData();
        ServiceNpcSpawnPendingRecord occupiedSource = collision(
            0, evidence(ServiceNpcSpawnCollisionEvidence.CollisionKind.REDACTED), null
        );
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.ACCEPTED, occupied.put(occupiedSource));
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.ACCEPTED,
            occupied.put(ready(uuid(62), uuid(63), 0)));
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.REJECTED_CONFLICT,
            occupied.stageCollisionReplacement(
                occupiedSource.token(), occupiedSource.collisionEvidence(), uuid(62), uuid(64),
                occupiedSource.location(), occupiedSource.cityPublicId(),
                occupiedSource.serviceNpcTypeKey(), occupiedSource.enabled(), 401L
            ).result());
    }

    @Test
    void claimOperationsNeverStealOrReleaseAnotherLocation() {
        ServiceNpcSpawnClaimData claims = new ServiceNpcSpawnClaimData();
        UUID id = uuid(70);
        ServiceNpcSpawnLocation elsewhere = new ServiceNpcSpawnLocation(
            LOCATION.worldName(), LOCATION.dimension(), LOCATION.pos().offset(4, 0, 0)
        );
        assertEquals(ServiceNpcSpawnClaimData.ClaimResult.CLAIMED, claims.claim(id, elsewhere));
        assertEquals(ServiceNpcSpawnClaimData.ClaimResult.CONFLICT, claims.claim(id, LOCATION));
        assertFalse(claims.releaseIfMatches(id, LOCATION));
        assertTrue(claims.claimMatches(id, elsewhere));
        assertFalse(claims.isUuidAvailable(id));
        assertTrue(claims.releaseIfMatches(id, elsewhere));
        assertTrue(claims.isUuidAvailable(id));
    }

    private static ServiceNpcSpawnPendingRecord collision(
            int repairCount,
            ServiceNpcSpawnCollisionEvidence evidence,
            UUID supersedes
    ) {
        return new ServiceNpcSpawnPendingRecord(
            ServiceNpcSpawnPendingOperation.UPSERT, uuid(1), "Britannia", LOCATION,
            CITY, "bank_teller", false, supersedes == null ? 7L : 1L, 10L, uuid(2),
            ServiceNpcSpawnPendingDisposition.COLLISION_REPAIR, 2, 8L, 9L,
            "uuid_collision_pending_repair", supersedes, repairCount, evidence
        );
    }

    private static ServiceNpcSpawnPendingRecord ready(UUID spawnId, UUID operationId, int repairCount) {
        return new ServiceNpcSpawnPendingRecord(
            ServiceNpcSpawnPendingOperation.UPSERT, spawnId, "Britannia", LOCATION,
            CITY, "bank_teller", true, 1L, 10L, operationId,
            ServiceNpcSpawnPendingDisposition.READY, 0, null, 0L,
            null, null, repairCount, null
        );
    }

    private static ServiceNpcSpawnCollisionEvidence evidence(
            ServiceNpcSpawnCollisionEvidence.CollisionKind kind
    ) {
        return new ServiceNpcSpawnCollisionEvidence(kind, true, null, 5L);
    }

    private static ServiceNpcSpawnCollisionEvidence liveEvidence(UUID serverKey, BlockPos pos) {
        return new ServiceNpcSpawnCollisionEvidence(
            ServiceNpcSpawnCollisionEvidence.CollisionKind.LIVE,
            true,
            new ServiceNpcSpawnCanonicalLocation(
                serverKey, "Diagnostic Name", LOCATION.dimension(),
                pos.getX(), pos.getY(), pos.getZ()
            ),
            5L
        );
    }

    private static UUID uuid(long suffix) {
        return new UUID(0x0000000000004000L, 0x8000000000000000L | suffix);
    }

    private static <T> T requireNonNull(T value) {
        return assertDoesNotThrow(() -> java.util.Objects.requireNonNull(value));
    }
}
