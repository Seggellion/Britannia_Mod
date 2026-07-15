package com.seggellion.britannia_mod.service.spawn;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ServiceNpcSpawnPendingData extends SavedData implements ServiceNpcSpawnPendingWorkSource {
    public enum MutationResult {
        ACCEPTED,
        IDEMPOTENT,
        REJECTED_STALE,
        REJECTED_CONFLICT,
        READ_ONLY_SCHEMA
    }

    public static final String DATA_NAME = "britannia_service_npc_spawn_pending";
    public static final int SCHEMA_VERSION = 1;
    private static final int WARNING_RECORD_COUNT = 16_384;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final LinkedHashMap<UUID, ServiceNpcSpawnPendingRecord> records = new LinkedHashMap<>();
    private final List<CompoundTag> quarantinedRecords = new ArrayList<>();
    private boolean readOnlyFutureSchema;
    private CompoundTag futureRoot;
    private boolean warnedLarge;

    public static ServiceNpcSpawnPendingData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ServiceNpcSpawnPendingData::new, ServiceNpcSpawnPendingData::load),
                DATA_NAME
        );
    }

    public static ServiceNpcSpawnPendingData load(CompoundTag tag, HolderLookup.Provider provider) {
        ServiceNpcSpawnPendingData data = new ServiceNpcSpawnPendingData();
        int schema = tag.getInt("SchemaVersion");
        if (schema != SCHEMA_VERSION) {
            data.readOnlyFutureSchema = true;
            data.futureRoot = tag.copy();
            LOGGER.error("Service NPC spawn pending data schema {} is unsupported; store is read-only", schema);
            return data;
        }

        ListTag list = tag.getList("Records", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag recordTag = list.getCompound(index);
            try {
                ServiceNpcSpawnPendingRecord record = ServiceNpcSpawnPendingRecord.fromNbt(recordTag);
                if (data.records.putIfAbsent(record.spawnPointId(), record) != null) {
                    throw new IllegalArgumentException("duplicate SpawnPointId");
                }
            } catch (RuntimeException exception) {
                data.quarantinedRecords.add(recordTag.copy());
                LOGGER.error("Quarantined corrupt Service NPC spawn pending record at index {}", index, exception);
            }
        }
        data.checkWarningThreshold();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (readOnlyFutureSchema && futureRoot != null) return futureRoot.copy();
        tag.putInt("SchemaVersion", SCHEMA_VERSION);
        ListTag list = new ListTag();
        records.values().forEach(record -> list.add(record.toNbt()));
        quarantinedRecords.forEach(record -> list.add(record.copy()));
        tag.put("Records", list);
        return tag;
    }

    public MutationResult put(ServiceNpcSpawnPendingRecord incoming) {
        if (readOnlyFutureSchema) return MutationResult.READ_ONLY_SCHEMA;
        ServiceNpcSpawnPendingRecord existing = records.get(incoming.spawnPointId());
        MutationResult decision = decide(existing, incoming);
        if (decision == MutationResult.ACCEPTED) {
            records.put(incoming.spawnPointId(), incoming);
            setDirty();
            checkWarningThreshold();
        }
        return decision;
    }

    static MutationResult decide(ServiceNpcSpawnPendingRecord existing, ServiceNpcSpawnPendingRecord incoming) {
        if (existing == null) return MutationResult.ACCEPTED;

        if (existing.operation() == ServiceNpcSpawnPendingOperation.REMOVE) {
            if (incoming.operation() == ServiceNpcSpawnPendingOperation.UPSERT) {
                return MutationResult.REJECTED_STALE;
            }
            int revisionComparison = Long.compare(incoming.configurationRevision(), existing.configurationRevision());
            if (revisionComparison < 0) return MutationResult.REJECTED_STALE;
            if (revisionComparison > 0) return MutationResult.ACCEPTED;
            if (incoming.recordedAtEpochMillis() < existing.recordedAtEpochMillis()) {
                return MutationResult.REJECTED_STALE;
            }
            if (incoming.recordedAtEpochMillis() > existing.recordedAtEpochMillis()) {
                return MutationResult.ACCEPTED;
            }
            return incoming.sameSnapshot(existing) ? MutationResult.IDEMPOTENT : MutationResult.REJECTED_CONFLICT;
        }

        if (incoming.operation() == ServiceNpcSpawnPendingOperation.REMOVE) {
            return MutationResult.ACCEPTED;
        }

        int revisionComparison = Long.compare(incoming.configurationRevision(), existing.configurationRevision());
        if (revisionComparison < 0) return MutationResult.REJECTED_STALE;
        if (revisionComparison > 0) return MutationResult.ACCEPTED;
        return incoming.sameSnapshot(existing) ? MutationResult.IDEMPOTENT : MutationResult.REJECTED_CONFLICT;
    }

    @Override
    public Map<UUID, ServiceNpcSpawnPendingRecord> snapshot() {
        return Map.copyOf(records);
    }

    @Override
    public boolean acknowledgeIfMatches(
            UUID spawnPointId,
            ServiceNpcSpawnPendingOperation operation,
            long configurationRevision,
            long recordedAtEpochMillis
    ) {
        if (readOnlyFutureSchema) return false;
        ServiceNpcSpawnPendingRecord existing = records.get(spawnPointId);
        if (existing == null
                || existing.operation() != operation
                || existing.configurationRevision() != configurationRevision
                || existing.recordedAtEpochMillis() != recordedAtEpochMillis) {
            return false;
        }
        records.remove(spawnPointId);
        setDirty();
        return true;
    }

    public boolean isReadOnlyFutureSchema() {
        return readOnlyFutureSchema;
    }

    private void checkWarningThreshold() {
        if (!warnedLarge && records.size() >= WARNING_RECORD_COUNT) {
            warnedLarge = true;
            LOGGER.warn("Service NPC spawn pending store contains {} unacknowledged records", records.size());
        }
    }
}
