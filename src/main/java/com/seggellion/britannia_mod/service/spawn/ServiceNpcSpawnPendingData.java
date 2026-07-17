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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

public final class ServiceNpcSpawnPendingData extends SavedData implements ServiceNpcSpawnPendingWorkSource {
    public enum MutationResult {
        ACCEPTED,
        IDEMPOTENT,
        REJECTED_STALE,
        REJECTED_CONFLICT,
        READ_ONLY_SCHEMA
    }

    public record CollisionReplacementStage(
        MutationResult result, @Nullable ServiceNpcSpawnPendingRecord replacement
    ) {}

    public static final String DATA_NAME = "britannia_service_npc_spawn_pending";
    public static final int SCHEMA_VERSION = 2;
    static final int MAX_COLLECTION_ENTRIES = 16_384;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final LinkedHashMap<UUID, ServiceNpcSpawnPendingRecord> records = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, ServiceNpcSpawnAcknowledgementReceipt> acknowledgements = new LinkedHashMap<>();
    private final List<CompoundTag> quarantinedRecords = new ArrayList<>();
    private final List<CompoundTag> quarantinedAcknowledgements = new ArrayList<>();
    private boolean readOnlyFutureSchema;
    private CompoundTag futureRoot;
    private boolean warnedLargeRecords;
    private boolean warnedLargeAcknowledgements;

    public static ServiceNpcSpawnPendingData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(ServiceNpcSpawnPendingData::new, ServiceNpcSpawnPendingData::load),
            DATA_NAME
        );
    }

    public static ServiceNpcSpawnPendingData load(CompoundTag tag, HolderLookup.Provider provider) {
        ServiceNpcSpawnPendingData data = new ServiceNpcSpawnPendingData();
        if (!tag.contains("SchemaVersion", Tag.TAG_INT)) return data.readOnly(tag, "missing_or_malformed");
        int schema = tag.getInt("SchemaVersion");
        if (schema != 1 && schema != SCHEMA_VERSION) {
            return data.readOnly(tag, "unsupported_" + schema);
        }

        Tag rawRecords = tag.get("Records");
        if (!(rawRecords instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            return data.readOnly(tag, "malformed_records_collection");
        }
        if (list.size() > MAX_COLLECTION_ENTRIES) return data.readOnly(tag, "records_over_limit");
        for (int index = 0; index < list.size(); index++) {
            CompoundTag recordTag = list.getCompound(index);
            try {
                ServiceNpcSpawnPendingRecord record = schema == 1
                    ? ServiceNpcSpawnPendingRecord.fromSchemaOneNbt(recordTag)
                    : ServiceNpcSpawnPendingRecord.fromNbt(recordTag);
                if (data.records.putIfAbsent(record.spawnPointId(), record) != null) {
                    throw new IllegalArgumentException("duplicate SpawnPointId");
                }
            } catch (RuntimeException exception) {
                data.quarantinedRecords.add(recordTag.copy());
                LOGGER.error("Quarantined corrupt Service NPC spawn pending record at index {}", index, exception);
            }
        }
        if (schema == SCHEMA_VERSION) {
            Tag rawAcknowledgements = tag.get("Acknowledgements");
            if (!(rawAcknowledgements instanceof ListTag receipts)
                    || (!receipts.isEmpty() && receipts.getElementType() != Tag.TAG_COMPOUND)) {
                return data.readOnly(tag, "malformed_acknowledgements_collection");
            }
            if (receipts.size() > MAX_COLLECTION_ENTRIES) return data.readOnly(tag, "acknowledgements_over_limit");
            for (int index = 0; index < receipts.size(); index++) {
                CompoundTag receiptTag = receipts.getCompound(index);
                try {
                    ServiceNpcSpawnAcknowledgementReceipt receipt =
                        ServiceNpcSpawnAcknowledgementReceipt.fromNbt(receiptTag);
                    if (!data.mergeAcknowledgement(receipt)) {
                        throw new IllegalArgumentException("conflicting acknowledgement");
                    }
                } catch (RuntimeException exception) {
                    data.quarantinedAcknowledgements.add(receiptTag.copy());
                    LOGGER.error("Quarantined corrupt Service NPC spawn acknowledgement at index {}", index, exception);
                }
            }
        } else {
            data.setDirty();
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
        ListTag receipts = new ListTag();
        acknowledgements.values().forEach(receipt -> receipts.add(receipt.toNbt()));
        quarantinedAcknowledgements.forEach(receipt -> receipts.add(receipt.copy()));
        tag.put("Acknowledgements", receipts);
        return tag;
    }

    public MutationResult put(ServiceNpcSpawnPendingRecord incoming) {
        if (incoming == null) throw new IllegalArgumentException("incoming record is required");
        if (readOnlyFutureSchema) return MutationResult.READ_ONLY_SCHEMA;
        ServiceNpcSpawnPendingRecord existing = records.get(incoming.spawnPointId());
        MutationResult decision = decide(existing, incoming);
        if (decision == MutationResult.ACCEPTED) {
            boolean replacesPriorState = existing != null || acknowledgements.containsKey(incoming.spawnPointId());
            ServiceNpcSpawnPendingRecord stored = replacesPriorState ? incoming.asFreshLocalOperation(existing) : incoming;
            records.put(incoming.spawnPointId(), stored);
            acknowledgements.remove(incoming.spawnPointId());
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

    public Map<UUID, ServiceNpcSpawnAcknowledgementReceipt> snapshotAcknowledgements() {
        return Map.copyOf(acknowledgements);
    }

    public ServiceNpcSpawnAcknowledgementReceipt findAcknowledgement(UUID spawnPointId) {
        return acknowledgements.get(spawnPointId);
    }

    public ServiceNpcSpawnPendingRecord findPending(UUID spawnPointId) {
        return records.get(spawnPointId);
    }

    public ServiceNpcSpawnPendingRecord findStagedReplacement(
            UUID supersededSpawnPointId, ServiceNpcSpawnLocation location
    ) {
        if (supersededSpawnPointId == null || location == null) return null;
        return records.values().stream()
            .filter(record -> record.disposition() == ServiceNpcSpawnPendingDisposition.COLLISION_REPAIR)
            .filter(record -> supersededSpawnPointId.equals(record.supersedesSpawnPointId()))
            .filter(record -> location.equals(record.location()))
            .sorted(Comparator
                .comparingLong(ServiceNpcSpawnPendingRecord::recordedAtEpochMillis)
                .thenComparing(ServiceNpcSpawnPendingRecord::spawnPointId)
                .thenComparing(ServiceNpcSpawnPendingRecord::operationId))
            .findFirst()
            .orElse(null);
    }

    public List<ServiceNpcSpawnPendingRecord> snapshotCollisionRepairs(int limit) {
        if (limit < 0) throw new IllegalArgumentException("negative collision-repair limit");
        return records.values().stream()
            .filter(record -> record.disposition() == ServiceNpcSpawnPendingDisposition.COLLISION_REPAIR)
            .sorted(Comparator
                .comparingLong(ServiceNpcSpawnPendingRecord::recordedAtEpochMillis)
                .thenComparing(ServiceNpcSpawnPendingRecord::spawnPointId)
                .thenComparing(ServiceNpcSpawnPendingRecord::operationId))
            .limit(limit)
            .toList();
    }

    public boolean isUuidAvailable(UUID candidate) {
        return candidate != null && !records.containsKey(candidate)
            && !acknowledgements.containsKey(candidate);
    }

    public boolean isOperationIdAvailable(UUID candidate) {
        return candidate != null
            && records.values().stream()
                .noneMatch(record -> record.operationId().equals(candidate))
            && acknowledgements.values().stream()
                .noneMatch(receipt -> receipt.operationId().equals(candidate));
    }

    public CollisionReplacementStage stageCollisionReplacement(
            ServiceNpcSpawnPendingOperationToken token,
            ServiceNpcSpawnCollisionEvidence evidence,
            UUID replacementSpawnPointId,
            UUID replacementOperationId,
            ServiceNpcSpawnLocation currentLocation,
            UUID cityPublicId,
            String serviceNpcTypeKey,
            boolean enabled,
            long recordedAtEpochMillis
    ) {
        if (readOnlyFutureSchema) {
            return new CollisionReplacementStage(MutationResult.READ_ONLY_SCHEMA, null);
        }
        ServiceNpcSpawnPendingRecord current = matching(token);
        if (current == null) {
            return new CollisionReplacementStage(MutationResult.REJECTED_STALE, null);
        }
        boolean exactSource = current.operation() == ServiceNpcSpawnPendingOperation.UPSERT
            && current.disposition() == ServiceNpcSpawnPendingDisposition.COLLISION_REPAIR
            && current.collisionEvidence() != null
            && current.collisionEvidence().equals(evidence)
            && evidence.replacementUuidRequired()
            && current.location().equals(currentLocation)
            && Objects.equals(current.cityPublicId(), cityPublicId)
            && Objects.equals(current.serviceNpcTypeKey(), serviceNpcTypeKey)
            && current.enabled() == enabled;
        if (!exactSource || current.collisionRepairCount() >= ServiceNpcSpawnCollisionRepairCoordinator.MAX_REPLACEMENTS
                || replacementSpawnPointId.equals(current.spawnPointId())
                || !isUuidAvailable(replacementSpawnPointId)
                || !isOperationIdAvailable(replacementOperationId)) {
            return new CollisionReplacementStage(MutationResult.REJECTED_CONFLICT, null);
        }
        ServiceNpcSpawnPendingRecord replacement = current.stagedCollisionReplacement(
            replacementSpawnPointId, replacementOperationId, recordedAtEpochMillis
        );
        ServiceNpcSpawnAcknowledgementReceipt oldReceipt = acknowledgements.get(current.spawnPointId());
        if (oldReceipt != null
                && oldReceipt.location().equals(current.location())
                && oldReceipt.configurationRevision() == current.configurationRevision()) {
            acknowledgements.remove(current.spawnPointId());
        }
        records.remove(current.spawnPointId());
        records.put(replacement.spawnPointId(), replacement);
        setDirty();
        checkWarningThreshold();
        return new CollisionReplacementStage(MutationResult.ACCEPTED, replacement);
    }

    public boolean markCollisionReplacementReady(
            ServiceNpcSpawnPendingOperationToken token, UUID expectedSupersededSpawnPointId
    ) {
        ServiceNpcSpawnPendingRecord current = matching(token);
        if (current == null
                || current.disposition() != ServiceNpcSpawnPendingDisposition.COLLISION_REPAIR
                || current.supersedesSpawnPointId() == null
                || !current.supersedesSpawnPointId().equals(expectedSupersededSpawnPointId)) {
            return false;
        }
        replace(current.withCollisionReplacementReady());
        return true;
    }

    public boolean markAttemptStarted(
            ServiceNpcSpawnPendingOperationToken token,
            long attemptedAtEpochMillis,
            long provisionalNextAttemptAtEpochMillis
    ) {
        if (attemptedAtEpochMillis < 0 || provisionalNextAttemptAtEpochMillis < 0) {
            throw new IllegalArgumentException("negative attempt timestamp");
        }
        ServiceNpcSpawnPendingRecord current = matching(token);
        if (current == null) return false;
        boolean eligible = current.disposition() == ServiceNpcSpawnPendingDisposition.READY
            || (current.disposition() == ServiceNpcSpawnPendingDisposition.RETRY_WAIT
                && current.nextAttemptAtEpochMillis() <= attemptedAtEpochMillis);
        if (!eligible || current.attemptCount() == Integer.MAX_VALUE) return false;
        replace(current.withAttemptStarted(attemptedAtEpochMillis, provisionalNextAttemptAtEpochMillis));
        return true;
    }

    public boolean markRetryWait(
            ServiceNpcSpawnPendingOperationToken token, String failureCode, long nextAttemptAtEpochMillis
    ) {
        if (nextAttemptAtEpochMillis < 0) throw new IllegalArgumentException("negative retry timestamp");
        ServiceNpcSpawnPendingRecord current = matching(token);
        if (current == null) return false;
        replace(current.withRetryWait(failureCode, nextAttemptAtEpochMillis));
        return true;
    }

    public boolean markPermanentFailure(ServiceNpcSpawnPendingOperationToken token, String failureCode) {
        ServiceNpcSpawnPendingRecord current = matching(token);
        if (current == null) return false;
        replace(current.withPermanentFailure(failureCode));
        return true;
    }

    public boolean markCollisionRepair(
            ServiceNpcSpawnPendingOperationToken token,
            ServiceNpcSpawnCollisionEvidence evidence,
            String failureCode
    ) {
        if (evidence == null || !evidence.replacementUuidRequired()) {
            throw new IllegalArgumentException("replacement collision evidence is required");
        }
        ServiceNpcSpawnPendingRecord current = matching(token);
        if (current == null) return false;
        replace(current.withCollisionRepair(evidence, failureCode));
        return true;
    }

    @Override
    public boolean acknowledgeSuccess(
            ServiceNpcSpawnPendingOperationToken token,
            ServiceNpcSpawnProtocolResponse response
    ) {
        ServiceNpcSpawnPendingRecord current = matching(token);
        if (current == null || response == null || !response.success()
                || response.protocolVersion() != ServiceNpcSpawnOperationRequest.PROTOCOL_VERSION
                || response.retryable()
                || (response.outcome() != ServiceNpcSpawnOutcome.APPLIED
                    && response.outcome() != ServiceNpcSpawnOutcome.ALREADY_APPLIED)
                || !token.operationId().equals(response.operationId())
                || !token.spawnPointId().equals(response.spawnUuid())
                || response.submittedRevision() == null
                || response.submittedRevision() != token.configurationRevision()
                || response.acknowledgedRevision() == null
                || response.registrationState() == null
                || response.acknowledgedAt() == null) {
            return false;
        }

        if (current.operation() == ServiceNpcSpawnPendingOperation.UPSERT) {
            if (response.acknowledgedRevision() != current.configurationRevision()
                    || response.registrationState() != ServiceNpcSpawnProtocolResponse.RegistrationState.LIVE) {
                return false;
            }
            long acknowledgedAt;
            try {
                acknowledgedAt = response.acknowledgedAt().toEpochMilli();
            } catch (ArithmeticException invalid) {
                return false;
            }
            if (acknowledgedAt < 0) return false;
            ServiceNpcSpawnAcknowledgementReceipt receipt = new ServiceNpcSpawnAcknowledgementReceipt(
                current.operationId(), current.spawnPointId(), current.operation(),
                current.configurationRevision(), current.recordedAtEpochMillis(), current.location(),
                response.acknowledgedRevision(), response.registrationState(), acknowledgedAt, response.outcome()
            );
            acknowledgements.put(current.spawnPointId(), receipt);
        } else {
            if (response.acknowledgedRevision() < current.configurationRevision()
                    || response.registrationState() != ServiceNpcSpawnProtocolResponse.RegistrationState.REMOVED) {
                return false;
            }
            acknowledgements.remove(current.spawnPointId());
        }
        records.remove(current.spawnPointId());
        setDirty();
        return true;
    }

    public boolean consumeAcknowledgementIfMatches(
            UUID spawnPointId,
            ServiceNpcSpawnLocation location,
            long configurationRevision,
            UUID expectedOperationId
    ) {
        if (readOnlyFutureSchema) return false;
        ServiceNpcSpawnAcknowledgementReceipt receipt = acknowledgements.get(spawnPointId);
        if (receipt == null || !receipt.matchesBlock(location, configurationRevision, expectedOperationId)) {
            return false;
        }
        acknowledgements.remove(spawnPointId);
        setDirty();
        return true;
    }

    public boolean isReadOnlyFutureSchema() {
        return readOnlyFutureSchema;
    }

    private ServiceNpcSpawnPendingRecord matching(ServiceNpcSpawnPendingOperationToken token) {
        if (readOnlyFutureSchema || token == null) return null;
        ServiceNpcSpawnPendingRecord current = records.get(token.spawnPointId());
        return current != null && current.token().equals(token) ? current : null;
    }

    private void replace(ServiceNpcSpawnPendingRecord record) {
        records.put(record.spawnPointId(), record);
        setDirty();
    }

    private boolean mergeAcknowledgement(ServiceNpcSpawnAcknowledgementReceipt incoming) {
        ServiceNpcSpawnAcknowledgementReceipt existing = acknowledgements.get(incoming.spawnPointId());
        if (existing == null) {
            acknowledgements.put(incoming.spawnPointId(), incoming);
            return true;
        }
        if (incoming.acknowledgedRevision() < existing.acknowledgedRevision()) return false;
        if (incoming.equals(existing)) return true;
        if (incoming.acknowledgedRevision() > existing.acknowledgedRevision()) {
            acknowledgements.put(incoming.spawnPointId(), incoming);
            return true;
        }
        boolean sameSnapshot = incoming.location().equals(existing.location())
            && incoming.configurationRevision() == existing.configurationRevision()
            && incoming.registrationState() == existing.registrationState();
        if (sameSnapshot && incoming.acknowledgedAtEpochMillis() >= existing.acknowledgedAtEpochMillis()) {
            acknowledgements.put(incoming.spawnPointId(), incoming);
            return true;
        }
        return false;
    }

    private ServiceNpcSpawnPendingData readOnly(CompoundTag tag, String reason) {
        readOnlyFutureSchema = true;
        futureRoot = tag.copy();
        records.clear();
        acknowledgements.clear();
        quarantinedRecords.clear();
        quarantinedAcknowledgements.clear();
        LOGGER.error("Service NPC spawn pending data is unsupported ({}); store is read-only", reason);
        return this;
    }

    private void checkWarningThreshold() {
        if (!warnedLargeRecords && records.size() >= MAX_COLLECTION_ENTRIES) {
            warnedLargeRecords = true;
            LOGGER.warn("Service NPC spawn pending store contains {} unacknowledged records", records.size());
        }
        if (!warnedLargeAcknowledgements && acknowledgements.size() >= MAX_COLLECTION_ENTRIES) {
            warnedLargeAcknowledgements = true;
            LOGGER.warn("Service NPC spawn pending store contains {} acknowledgement receipts", acknowledgements.size());
        }
    }
}
