package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ServiceNpcSpawnPendingDataTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");

    @Test
    void newerUpsertReplacesAndEqualSnapshotIsIdempotent() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingRecord revisionOne = upsert(id, 1L, "bank_teller", 10L);
        ServiceNpcSpawnPendingRecord sameRevision = upsert(id, 1L, "bank_teller", 20L);
        ServiceNpcSpawnPendingRecord revisionTwo = upsert(id, 2L, "bank_teller", 30L);

        assertEquals(ServiceNpcSpawnPendingData.MutationResult.ACCEPTED,
                ServiceNpcSpawnPendingData.decide(null, revisionOne));
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.IDEMPOTENT,
                ServiceNpcSpawnPendingData.decide(revisionOne, sameRevision));
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.ACCEPTED,
                ServiceNpcSpawnPendingData.decide(revisionOne, revisionTwo));
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.REJECTED_STALE,
                ServiceNpcSpawnPendingData.decide(revisionTwo, revisionOne));
    }

    @Test
    void equalRevisionConflictingUpsertIsRejected() {
        UUID id = UUID.randomUUID();
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.REJECTED_CONFLICT,
                ServiceNpcSpawnPendingData.decide(
                        upsert(id, 1L, "bank_teller", 10L),
                        upsert(id, 1L, "stablemaster", 20L)
                ));
    }

    @Test
    void removeIsTerminalAndUsesRevisionThenTimestamp() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingRecord remove = remove(id, 4L, 100L);
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.ACCEPTED,
                ServiceNpcSpawnPendingData.decide(upsert(id, 5L, "bank_teller", 50L), remove));
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.REJECTED_STALE,
                ServiceNpcSpawnPendingData.decide(remove, upsert(id, 6L, "bank_teller", 200L)));
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.REJECTED_STALE,
                ServiceNpcSpawnPendingData.decide(remove, remove(id, 4L, 99L)));
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.ACCEPTED,
                ServiceNpcSpawnPendingData.decide(remove, remove(id, 4L, 101L)));
    }

    @Test
    void codecRoundTripAndCorruptRecordQuarantine() {
        ServiceNpcSpawnPendingRecord record = upsert(UUID.randomUUID(), 2L, "bank_teller", 50L);
        assertEquals(record, ServiceNpcSpawnPendingRecord.fromNbt(record.toNbt()));

        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", ServiceNpcSpawnPendingData.SCHEMA_VERSION);
        ListTag records = new ListTag();
        records.add(record.toNbt());
        records.add(new CompoundTag());
        root.put("Records", records);

        ServiceNpcSpawnPendingData loaded = ServiceNpcSpawnPendingData.load(root, null);
        assertEquals(1, loaded.snapshot().size());
        CompoundTag saved = loaded.save(new CompoundTag(), null);
        assertEquals(2, saved.getList("Records", CompoundTag.TAG_COMPOUND).size());
    }

    @Test
    void futureSchemaIsReadOnlyAndPreserved() {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 99);
        root.putString("FutureField", "retain-me");
        ServiceNpcSpawnPendingData loaded = ServiceNpcSpawnPendingData.load(root, null);

        assertTrue(loaded.isReadOnlyFutureSchema());
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.READ_ONLY_SCHEMA,
                loaded.put(upsert(UUID.randomUUID(), 1L, "bank_teller", 10L)));
        assertEquals("retain-me", loaded.save(new CompoundTag(), null).getString("FutureField"));
    }

    private static ServiceNpcSpawnPendingRecord upsert(UUID id, long revision, String type, long timestamp) {
        return new ServiceNpcSpawnPendingRecord(
                ServiceNpcSpawnPendingOperation.UPSERT,
                id,
                "Britannia",
                location(),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                type,
                true,
                revision,
                timestamp
        );
    }

    private static ServiceNpcSpawnPendingRecord remove(UUID id, long revision, long timestamp) {
        return new ServiceNpcSpawnPendingRecord(
                ServiceNpcSpawnPendingOperation.REMOVE,
                id,
                "Britannia",
                location(),
                null,
                null,
                true,
                revision,
                timestamp
        );
    }

    private static ServiceNpcSpawnLocation location() {
        return new ServiceNpcSpawnLocation("world", OVERWORLD, new BlockPos(1, 64, 2));
    }
}
