package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.nbt.CompoundTag;
import java.util.Objects;
import java.util.UUID;

public record ServiceNpcSpawnAcknowledgementReceipt(
    UUID operationId,
    UUID spawnPointId,
    ServiceNpcSpawnPendingOperation operation,
    long configurationRevision,
    long recordedAtEpochMillis,
    ServiceNpcSpawnLocation location,
    long acknowledgedRevision,
    ServiceNpcSpawnProtocolResponse.RegistrationState registrationState,
    long acknowledgedAtEpochMillis,
    ServiceNpcSpawnOutcome outcome
) {
    public ServiceNpcSpawnAcknowledgementReceipt {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(spawnPointId, "spawnPointId");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(registrationState, "registrationState");
        Objects.requireNonNull(outcome, "outcome");
        if (!ServiceNpcSpawnOperationRequest.isProtocolUuid(operationId)
            || !ServiceNpcSpawnOperationRequest.isProtocolUuid(spawnPointId)) {
            throw new IllegalArgumentException("receipt UUID is not protocol-valid");
        }
        if (operation != ServiceNpcSpawnPendingOperation.UPSERT
            || registrationState != ServiceNpcSpawnProtocolResponse.RegistrationState.LIVE
            || (outcome != ServiceNpcSpawnOutcome.APPLIED && outcome != ServiceNpcSpawnOutcome.ALREADY_APPLIED)) {
            throw new IllegalArgumentException("invalid acknowledgement receipt classification");
        }
        if (configurationRevision < 0 || recordedAtEpochMillis < 0
            || acknowledgedRevision != configurationRevision || acknowledgedAtEpochMillis < 0) {
            throw new IllegalArgumentException("invalid acknowledgement receipt revision or timestamp");
        }
        var pos = location.pos();
        if (pos.getX() < ServiceNpcSpawnOperationRequest.MIN_XZ || pos.getX() > ServiceNpcSpawnOperationRequest.MAX_XZ
            || pos.getZ() < ServiceNpcSpawnOperationRequest.MIN_XZ || pos.getZ() > ServiceNpcSpawnOperationRequest.MAX_XZ
            || pos.getY() < ServiceNpcSpawnOperationRequest.MIN_Y || pos.getY() > ServiceNpcSpawnOperationRequest.MAX_Y) {
            throw new IllegalArgumentException("receipt location is outside protocol bounds");
        }
    }

    public ServiceNpcSpawnPendingOperationToken token() {
        return new ServiceNpcSpawnPendingOperationToken(
            operationId, spawnPointId, operation, configurationRevision, recordedAtEpochMillis
        );
    }

    public boolean matchesBlock(ServiceNpcSpawnLocation expectedLocation, long expectedRevision, UUID expectedOperationId) {
        return location.equals(expectedLocation)
            && configurationRevision == expectedRevision
            && operationId.equals(expectedOperationId);
    }

    CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("OperationId", operationId);
        tag.putUUID("SpawnPointId", spawnPointId);
        tag.putString("Operation", operation.name());
        tag.putLong("ConfigurationRevision", configurationRevision);
        tag.putLong("RecordedAtEpochMillis", recordedAtEpochMillis);
        tag.putString("WorldName", location.worldName());
        tag.putString("Dimension", location.dimension().toString());
        tag.putInt("X", location.pos().getX());
        tag.putInt("Y", location.pos().getY());
        tag.putInt("Z", location.pos().getZ());
        tag.putLong("AcknowledgedRevision", acknowledgedRevision);
        tag.putString("RegistrationState", registrationState.name());
        tag.putLong("AcknowledgedAtEpochMillis", acknowledgedAtEpochMillis);
        tag.putString("Outcome", outcome.name());
        return tag;
    }

    static ServiceNpcSpawnAcknowledgementReceipt fromNbt(CompoundTag tag) {
        if (!tag.hasUUID("OperationId") || !tag.hasUUID("SpawnPointId")) {
            throw new IllegalArgumentException("missing receipt UUID");
        }
        var dimension = net.minecraft.resources.ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null) throw new IllegalArgumentException("invalid receipt dimension");
        return new ServiceNpcSpawnAcknowledgementReceipt(
            tag.getUUID("OperationId"),
            tag.getUUID("SpawnPointId"),
            ServiceNpcSpawnPendingOperation.valueOf(tag.getString("Operation")),
            tag.getLong("ConfigurationRevision"),
            tag.getLong("RecordedAtEpochMillis"),
            new ServiceNpcSpawnLocation(
                tag.getString("WorldName"), dimension,
                new net.minecraft.core.BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"))
            ),
            tag.getLong("AcknowledgedRevision"),
            ServiceNpcSpawnProtocolResponse.RegistrationState.valueOf(tag.getString("RegistrationState")),
            tag.getLong("AcknowledgedAtEpochMillis"),
            ServiceNpcSpawnOutcome.valueOf(tag.getString("Outcome"))
        );
    }
}
