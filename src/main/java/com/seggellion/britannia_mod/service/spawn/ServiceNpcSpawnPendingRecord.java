package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record ServiceNpcSpawnPendingRecord(
        ServiceNpcSpawnPendingOperation operation,
        UUID spawnPointId,
        String shardName,
        ServiceNpcSpawnLocation location,
        @Nullable UUID cityPublicId,
        @Nullable String serviceNpcTypeKey,
        boolean enabled,
        long configurationRevision,
        long recordedAtEpochMillis,
        UUID operationId,
        ServiceNpcSpawnPendingDisposition disposition,
        int attemptCount,
        @Nullable Long lastAttemptAtEpochMillis,
        long nextAttemptAtEpochMillis,
        @Nullable String lastFailureCode,
        @Nullable UUID supersedesSpawnPointId,
        int collisionRepairCount,
        @Nullable ServiceNpcSpawnCollisionEvidence collisionEvidence
) {
    public static final int MAX_SHARD_NAME_BYTES = 128;
    public static final int MAX_TYPE_KEY_BYTES = 64;
    public static final int MAX_COLLISION_REPAIR_COUNT = 16;
    private static final Pattern SAFE_FAILURE_CODE = Pattern.compile("[a-z0-9_]{1,64}");
    private static final String MIGRATION_NAMESPACE = "britannia:service_npc_spawn_pending:v1-to-v2:";

    public ServiceNpcSpawnPendingRecord {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(spawnPointId, "spawnPointId");
        Objects.requireNonNull(shardName, "shardName");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(disposition, "disposition");
        if (!ServiceNpcSpawnOperationRequest.isProtocolUuid(operationId)
                || !ServiceNpcSpawnOperationRequest.isProtocolUuid(spawnPointId)) {
            throw new IllegalArgumentException("operationId is not a protocol UUID");
        }
        requireBounded(shardName, MAX_SHARD_NAME_BYTES, "shardName");
        if (shardName.isBlank()) throw new IllegalArgumentException("shardName must not be blank");
        if (serviceNpcTypeKey != null) {
            requireBounded(serviceNpcTypeKey, MAX_TYPE_KEY_BYTES, "serviceNpcTypeKey");
        }
        if (configurationRevision < 0L || recordedAtEpochMillis < 0L
                || attemptCount < 0 || nextAttemptAtEpochMillis < 0L
                || (lastAttemptAtEpochMillis != null && lastAttemptAtEpochMillis < 0L)
                || collisionRepairCount < 0 || collisionRepairCount > MAX_COLLISION_REPAIR_COUNT) {
            throw new IllegalArgumentException("pending metadata must be non-negative and bounded");
        }
        if (lastFailureCode != null && !SAFE_FAILURE_CODE.matcher(lastFailureCode).matches()) {
            throw new IllegalArgumentException("invalid safe failure code");
        }
        if (operation == ServiceNpcSpawnPendingOperation.UPSERT
                && (cityPublicId == null || serviceNpcTypeKey == null || serviceNpcTypeKey.isBlank())) {
            throw new IllegalArgumentException("UPSERT requires city and service type");
        }
        BlockPos pos = location.pos();
        if (pos.getX() < ServiceNpcSpawnOperationRequest.MIN_XZ || pos.getX() > ServiceNpcSpawnOperationRequest.MAX_XZ
                || pos.getZ() < ServiceNpcSpawnOperationRequest.MIN_XZ || pos.getZ() > ServiceNpcSpawnOperationRequest.MAX_XZ
                || pos.getY() < ServiceNpcSpawnOperationRequest.MIN_Y || pos.getY() > ServiceNpcSpawnOperationRequest.MAX_Y) {
            throw new IllegalArgumentException("pending location is outside protocol bounds");
        }
        if (location.dimension().toString().getBytes(StandardCharsets.US_ASCII).length
                > ServiceNpcSpawnOperationRequest.MAX_DIMENSION_BYTES || location.worldName().indexOf('\0') >= 0) {
            throw new IllegalArgumentException("pending location string is outside protocol bounds");
        }
        if (disposition == ServiceNpcSpawnPendingDisposition.COLLISION_REPAIR
                && (collisionEvidence == null || !collisionEvidence.replacementUuidRequired())) {
            throw new IllegalArgumentException("collision repair requires replacement evidence");
        }
        if (disposition != ServiceNpcSpawnPendingDisposition.COLLISION_REPAIR && collisionEvidence != null) {
            throw new IllegalArgumentException("collision evidence requires collision-repair disposition");
        }
    }

    /** Compatibility constructor for new local Milestone 4 enqueue callers. */
    public ServiceNpcSpawnPendingRecord(
            ServiceNpcSpawnPendingOperation operation,
            UUID spawnPointId,
            String shardName,
            ServiceNpcSpawnLocation location,
            @Nullable UUID cityPublicId,
            @Nullable String serviceNpcTypeKey,
            boolean enabled,
            long configurationRevision,
            long recordedAtEpochMillis
    ) {
        this(operation, spawnPointId, shardName, location, cityPublicId, serviceNpcTypeKey, enabled,
            configurationRevision, recordedAtEpochMillis, UUID.randomUUID(),
            ServiceNpcSpawnPendingDisposition.READY, 0, null, 0L, null, null, 0, null);
    }

    public boolean sameSnapshot(ServiceNpcSpawnPendingRecord other) {
        return other != null
            && operation == other.operation
            && spawnPointId.equals(other.spawnPointId)
            && shardName.equals(other.shardName)
            && location.equals(other.location)
            && Objects.equals(cityPublicId, other.cityPublicId)
            && Objects.equals(serviceNpcTypeKey, other.serviceNpcTypeKey)
            && enabled == other.enabled
            && configurationRevision == other.configurationRevision;
    }

    public ServiceNpcSpawnPendingOperationToken token() {
        return new ServiceNpcSpawnPendingOperationToken(
            operationId, spawnPointId, operation, configurationRevision, recordedAtEpochMillis
        );
    }

    ServiceNpcSpawnPendingRecord asFreshLocalOperation(@Nullable ServiceNpcSpawnPendingRecord previous) {
        boolean retainLineage = previous != null && previous.spawnPointId.equals(spawnPointId);
        return new ServiceNpcSpawnPendingRecord(
            operation, spawnPointId, shardName, location, cityPublicId, serviceNpcTypeKey, enabled,
            configurationRevision, recordedAtEpochMillis, UUID.randomUUID(),
            ServiceNpcSpawnPendingDisposition.READY, 0, null, 0L, null,
            retainLineage ? previous.supersedesSpawnPointId : null,
            retainLineage ? previous.collisionRepairCount : 0,
            null
        );
    }

    ServiceNpcSpawnPendingRecord withAttemptStarted(long attemptedAt, long provisionalNextAttemptAt) {
        if (attemptCount == Integer.MAX_VALUE) throw new ArithmeticException("attempt count overflow");
        ServiceNpcSpawnPendingDisposition nextDisposition = provisionalNextAttemptAt > attemptedAt
            ? ServiceNpcSpawnPendingDisposition.RETRY_WAIT : disposition;
        return copy(nextDisposition, attemptCount + 1, attemptedAt, provisionalNextAttemptAt,
            null, collisionEvidence);
    }

    ServiceNpcSpawnPendingRecord withRetryWait(String failureCode, long nextAttemptAt) {
        return copy(ServiceNpcSpawnPendingDisposition.RETRY_WAIT, attemptCount,
            lastAttemptAtEpochMillis, nextAttemptAt, failureCode, null);
    }

    ServiceNpcSpawnPendingRecord withPermanentFailure(String failureCode) {
        return copy(ServiceNpcSpawnPendingDisposition.PERMANENT_FAILURE, attemptCount,
            lastAttemptAtEpochMillis, 0L, failureCode, null);
    }

    ServiceNpcSpawnPendingRecord withCollisionRepair(
            ServiceNpcSpawnCollisionEvidence evidence, String failureCode
    ) {
        return new ServiceNpcSpawnPendingRecord(
            operation, spawnPointId, shardName, location, cityPublicId, serviceNpcTypeKey, enabled,
            configurationRevision, recordedAtEpochMillis, operationId,
            ServiceNpcSpawnPendingDisposition.COLLISION_REPAIR,
            attemptCount, lastAttemptAtEpochMillis, 0L, failureCode,
            null, collisionRepairCount, evidence
        );
    }

    ServiceNpcSpawnPendingRecord stagedCollisionReplacement(
            UUID replacementSpawnPointId,
            UUID replacementOperationId,
            long replacementRecordedAtEpochMillis
    ) {
        if (operation != ServiceNpcSpawnPendingOperation.UPSERT
                || disposition != ServiceNpcSpawnPendingDisposition.COLLISION_REPAIR
                || collisionEvidence == null
                || !collisionEvidence.replacementUuidRequired()
                || replacementSpawnPointId.equals(spawnPointId)) {
            throw new IllegalStateException("invalid collision replacement source");
        }
        return new ServiceNpcSpawnPendingRecord(
            ServiceNpcSpawnPendingOperation.UPSERT,
            replacementSpawnPointId,
            shardName,
            location,
            cityPublicId,
            serviceNpcTypeKey,
            enabled,
            1L,
            replacementRecordedAtEpochMillis,
            replacementOperationId,
            ServiceNpcSpawnPendingDisposition.COLLISION_REPAIR,
            0,
            null,
            0L,
            null,
            spawnPointId,
            Math.incrementExact(collisionRepairCount),
            collisionEvidence
        );
    }

    ServiceNpcSpawnPendingRecord withCollisionReplacementReady() {
        return copy(ServiceNpcSpawnPendingDisposition.READY, 0, null, 0L, null, null);
    }

    private ServiceNpcSpawnPendingRecord copy(
            ServiceNpcSpawnPendingDisposition nextDisposition,
            int nextAttemptCount,
            @Nullable Long nextLastAttempt,
            long nextAttemptAt,
            @Nullable String nextFailureCode,
            @Nullable ServiceNpcSpawnCollisionEvidence nextEvidence
    ) {
        return new ServiceNpcSpawnPendingRecord(
            operation, spawnPointId, shardName, location, cityPublicId, serviceNpcTypeKey, enabled,
            configurationRevision, recordedAtEpochMillis, operationId, nextDisposition,
            nextAttemptCount, nextLastAttempt, nextAttemptAt, nextFailureCode,
            supersedesSpawnPointId, collisionRepairCount, nextEvidence
        );
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("OperationId", operationId);
        tag.putString("Operation", operation.name());
        tag.putUUID("SpawnPointId", spawnPointId);
        tag.putString("ShardName", shardName);
        tag.putString("WorldName", location.worldName());
        tag.putString("Dimension", location.dimension().toString());
        tag.putInt("X", location.pos().getX());
        tag.putInt("Y", location.pos().getY());
        tag.putInt("Z", location.pos().getZ());
        if (cityPublicId != null) tag.putUUID("CityPublicId", cityPublicId);
        if (serviceNpcTypeKey != null) tag.putString("ServiceNpcTypeKey", serviceNpcTypeKey);
        tag.putBoolean("Enabled", enabled);
        tag.putLong("ConfigurationRevision", configurationRevision);
        tag.putLong("RecordedAtEpochMillis", recordedAtEpochMillis);
        tag.putString("Disposition", disposition.name());
        tag.putInt("AttemptCount", attemptCount);
        if (lastAttemptAtEpochMillis != null) tag.putLong("LastAttemptAtEpochMillis", lastAttemptAtEpochMillis);
        tag.putLong("NextAttemptAtEpochMillis", nextAttemptAtEpochMillis);
        if (lastFailureCode != null) tag.putString("LastFailureCode", lastFailureCode);
        if (supersedesSpawnPointId != null) tag.putUUID("SupersedesSpawnPointId", supersedesSpawnPointId);
        tag.putInt("CollisionRepairCount", collisionRepairCount);
        if (collisionEvidence != null) tag.put("CollisionEvidence", collisionEvidence.toNbt());
        return tag;
    }

    public static ServiceNpcSpawnPendingRecord fromNbt(CompoundTag tag) {
        if (!tag.hasUUID("OperationId") || !tag.hasUUID("SpawnPointId")
                || !tag.contains("Disposition", Tag.TAG_STRING)) {
            throw new IllegalArgumentException("missing schema-2 pending identity");
        }
        DecodedIntent intent = decodeIntent(tag);
        Long lastAttempt = tag.contains("LastAttemptAtEpochMillis", Tag.TAG_LONG)
            ? tag.getLong("LastAttemptAtEpochMillis") : null;
        String failure = tag.contains("LastFailureCode", Tag.TAG_STRING)
            ? tag.getString("LastFailureCode") : null;
        UUID supersedes = tag.hasUUID("SupersedesSpawnPointId") ? tag.getUUID("SupersedesSpawnPointId") : null;
        ServiceNpcSpawnCollisionEvidence evidence =
            tag.contains("CollisionEvidence", Tag.TAG_COMPOUND)
                ? ServiceNpcSpawnCollisionEvidence.fromNbt(tag.getCompound("CollisionEvidence")) : null;
        return new ServiceNpcSpawnPendingRecord(
            intent.operation, intent.spawnPointId, intent.shardName, intent.location,
            intent.cityPublicId, intent.serviceType, intent.enabled, intent.revision, intent.recordedAt,
            tag.getUUID("OperationId"),
            ServiceNpcSpawnPendingDisposition.valueOf(tag.getString("Disposition")),
            tag.getInt("AttemptCount"), lastAttempt, tag.getLong("NextAttemptAtEpochMillis"),
            failure, supersedes, tag.getInt("CollisionRepairCount"), evidence
        );
    }

    static ServiceNpcSpawnPendingRecord fromSchemaOneNbt(CompoundTag tag) {
        DecodedIntent intent = decodeIntent(tag);
        String identity = MIGRATION_NAMESPACE
            + intent.spawnPointId + "|" + intent.operation + "|" + intent.revision + "|" + intent.recordedAt
            + "|" + intent.location.worldName() + "|" + intent.location.dimension() + "|"
            + intent.location.pos().getX() + "|" + intent.location.pos().getY() + "|" + intent.location.pos().getZ();
        UUID operationId = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        return new ServiceNpcSpawnPendingRecord(
            intent.operation, intent.spawnPointId, intent.shardName, intent.location,
            intent.cityPublicId, intent.serviceType, intent.enabled, intent.revision, intent.recordedAt,
            operationId, ServiceNpcSpawnPendingDisposition.READY, 0, null, 0L,
            null, null, 0, null
        );
    }

    private static DecodedIntent decodeIntent(CompoundTag tag) {
        if (!tag.hasUUID("SpawnPointId")) throw new IllegalArgumentException("missing SpawnPointId");
        ServiceNpcSpawnPendingOperation operation = ServiceNpcSpawnPendingOperation.valueOf(tag.getString("Operation"));
        UUID spawnPointId = tag.getUUID("SpawnPointId");
        String shardName = tag.getString("ShardName");
        String worldName = tag.getString("WorldName");
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null) throw new IllegalArgumentException("invalid dimension");
        ServiceNpcSpawnLocation location = new ServiceNpcSpawnLocation(
            worldName, dimension, new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"))
        );
        UUID cityPublicId = tag.hasUUID("CityPublicId") ? tag.getUUID("CityPublicId") : null;
        String serviceType = tag.contains("ServiceNpcTypeKey") ? tag.getString("ServiceNpcTypeKey") : null;
        return new DecodedIntent(
            operation, spawnPointId, shardName, location, cityPublicId, serviceType,
            tag.getBoolean("Enabled"), tag.getLong("ConfigurationRevision"),
            tag.getLong("RecordedAtEpochMillis")
        );
    }

    private static void requireBounded(String value, int maxBytes, String field) {
        Objects.requireNonNull(value, field);
        if (value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException(field + " is too long");
        }
    }

    private record DecodedIntent(
        ServiceNpcSpawnPendingOperation operation,
        UUID spawnPointId,
        String shardName,
        ServiceNpcSpawnLocation location,
        UUID cityPublicId,
        String serviceType,
        boolean enabled,
        long revision,
        long recordedAt
    ) {}
}
