package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ServiceNpcSpawnDeliveryProcessorPolicyTest {
    @Test
    void selectionIsEligibleDeterministicAndBoundedToFour() {
        List<ServiceNpcSpawnPendingRecord> records = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            records.add(record(index + 1L, index + 10L, ServiceNpcSpawnPendingDisposition.READY, 0L));
        }
        records.add(record(0L, 1L, ServiceNpcSpawnPendingDisposition.RETRY_WAIT, 101L));
        records.add(record(0L, 2L, ServiceNpcSpawnPendingDisposition.PERMANENT_FAILURE, 0L));
        records.add(record(0L, 3L, ServiceNpcSpawnPendingDisposition.COLLISION_REPAIR, 0L));

        List<ServiceNpcSpawnPendingRecord> selected =
            ServiceNpcSpawnDeliveryProcessor.selectCandidates(records, 100L, Set.of(), Set.of());

        assertEquals(4, selected.size());
        assertEquals(List.of(10L, 11L, 12L, 13L),
            selected.stream().map(ServiceNpcSpawnPendingRecord::recordedAtEpochMillis).toList());
        assertTrue(selected.stream().allMatch(record ->
            record.disposition() == ServiceNpcSpawnPendingDisposition.READY));
    }

    @Test
    void selectionSkipsFutureRetryAndBothDuplicateIndexes() {
        ServiceNpcSpawnPendingRecord due =
            record(1L, 1L, ServiceNpcSpawnPendingDisposition.RETRY_WAIT, 100L);
        ServiceNpcSpawnPendingRecord blockedByOperation =
            record(2L, 2L, ServiceNpcSpawnPendingDisposition.READY, 0L);
        ServiceNpcSpawnPendingRecord blockedBySpawn =
            record(3L, 3L, ServiceNpcSpawnPendingDisposition.READY, 0L);

        List<ServiceNpcSpawnPendingRecord> selected =
            ServiceNpcSpawnDeliveryProcessor.selectCandidates(
                List.of(due, blockedByOperation, blockedBySpawn),
                100L,
                Set.of(blockedByOperation.operationId()),
                Set.of(blockedBySpawn.spawnPointId())
            );

        assertEquals(List.of(due), selected);
    }

    @Test
    void attemptStartPersistsCrashSafeRetryAndClearsPriorFailure() {
        ServiceNpcSpawnPendingData data = new ServiceNpcSpawnPendingData();
        ServiceNpcSpawnPendingRecord initial =
            record(1L, 1L, ServiceNpcSpawnPendingDisposition.READY, 0L);
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.ACCEPTED, data.put(initial));
        assertTrue(data.markRetryWait(initial.token(), "transport_error", 10L));
        assertTrue(data.markAttemptStarted(initial.token(), 10L, 15L));

        ServiceNpcSpawnPendingRecord started = data.snapshot().get(initial.spawnPointId());
        assertEquals(1, started.attemptCount());
        assertEquals(10L, started.lastAttemptAtEpochMillis());
        assertEquals(15L, started.nextAttemptAtEpochMillis());
        assertEquals(ServiceNpcSpawnPendingDisposition.RETRY_WAIT, started.disposition());
        assertNull(started.lastFailureCode());
        assertFalse(data.markAttemptStarted(initial.token(), 11L, 16L));
    }

    @Test
    void safeFailureRegistryNeverPersistsForeignText() {
        assertEquals("connect_timeout", ServiceNpcSpawnFailureCodes.from(
            new ServiceNpcSpawnClientResult.TransportFailure("connect_timeout")));
        assertEquals("transport_error", ServiceNpcSpawnFailureCodes.from(
            new ServiceNpcSpawnClientResult.TransportFailure("https://secret.invalid/raw")));
        assertEquals("authentication_blocked", ServiceNpcSpawnFailureCodes.from(
            new ServiceNpcSpawnClientResult.LocalFailure("minecraft_server_key_missing")));
        assertEquals("protocol_incompatible", ServiceNpcSpawnFailureCodes.from(
            new ServiceNpcSpawnClientResult.HttpFailure(
                "future_raw_code", ServiceNpcSpawnClientResult.Disposition.PROTOCOL_INCOMPATIBLE, null
            )));
        assertEquals("invalid_city", ServiceNpcSpawnFailureCodes.from(protocol(ServiceNpcSpawnOutcome.INVALID_CITY)));
        assertEquals("uuid_collision_pending_repair",
            ServiceNpcSpawnFailureCodes.from(protocol(ServiceNpcSpawnOutcome.UUID_COLLISION)));
    }

    @Test
    void retryAfterAndCircuitBoundariesRemainFiveMinutes() {
        UUID operation = UUID.fromString("11111111-2222-4333-8444-555555555555");
        assertEquals(ServiceNpcSpawnRetryPolicy.MAX_DELAY_MILLIS,
            ServiceNpcSpawnRetryPolicy.effectiveDelayMillis(
                operation, 1, Duration.ofDays(30)
            ));
        assertTrue(ServiceNpcSpawnDeliveryProcessor.isCircuitOpen(99L, 100L));
        assertFalse(ServiceNpcSpawnDeliveryProcessor.isCircuitOpen(100L, 100L));
        assertTrue(ServiceNpcSpawnDeliveryProcessor.shouldLogCircuitOpen(100L, 100L));
        assertFalse(ServiceNpcSpawnDeliveryProcessor.shouldLogCircuitOpen(99L, 100L));
        assertEquals(300_100L,
            ServiceNpcSpawnDeliveryProcessor.extendCircuit(100L, 0L));
        assertEquals(400_000L,
            ServiceNpcSpawnDeliveryProcessor.extendCircuit(100L, 400_000L));
    }

    private static ServiceNpcSpawnClientResult.Protocol protocol(ServiceNpcSpawnOutcome outcome) {
        ServiceNpcSpawnProtocolResponse response = new ServiceNpcSpawnProtocolResponse(
            1, false, outcome, false, UUID.randomUUID(), UUID.randomUUID(), 1L,
            null, null, null, null, null, null,
            outcome == ServiceNpcSpawnOutcome.UUID_COLLISION
                ? ServiceNpcSpawnProtocolResponse.CollisionKind.REDACTED : null,
            outcome == ServiceNpcSpawnOutcome.UUID_COLLISION ? true : null,
            null, null
        );
        return new ServiceNpcSpawnClientResult.Protocol(
            response,
            outcome == ServiceNpcSpawnOutcome.UUID_COLLISION
                ? ServiceNpcSpawnClientResult.Disposition.UUID_COLLISION
                : ServiceNpcSpawnClientResult.Disposition.PERMANENT
        );
    }

    private static ServiceNpcSpawnPendingRecord record(
            long uuidSuffix,
            long recordedAt,
            ServiceNpcSpawnPendingDisposition disposition,
            long nextAttemptAt
    ) {
        UUID spawn = new UUID(0x0000000000004000L, 0x8000000000000000L | uuidSuffix);
        UUID operation = new UUID(0x0000000000004001L, 0x8000000000000000L | uuidSuffix);
        ServiceNpcSpawnCollisionEvidence evidence = disposition == ServiceNpcSpawnPendingDisposition.COLLISION_REPAIR
            ? new ServiceNpcSpawnCollisionEvidence(
                ServiceNpcSpawnCollisionEvidence.CollisionKind.REDACTED, true, null, 1L
            ) : null;
        return new ServiceNpcSpawnPendingRecord(
            ServiceNpcSpawnPendingOperation.UPSERT, spawn, "Britannia",
            new ServiceNpcSpawnLocation(
                "Britannia", ResourceLocation.parse("minecraft:overworld"), new BlockPos(1, 64, 1)
            ),
            UUID.fromString("12345678-1234-4234-8234-123456789abc"),
            "bank_teller", true, 1L, recordedAt, operation, disposition,
            0, null, nextAttemptAt,
            disposition == ServiceNpcSpawnPendingDisposition.PERMANENT_FAILURE ? "invalid_city"
                : disposition == ServiceNpcSpawnPendingDisposition.COLLISION_REPAIR
                    ? "uuid_collision_pending_repair" : null,
            null, 0, evidence
        );
    }
}
