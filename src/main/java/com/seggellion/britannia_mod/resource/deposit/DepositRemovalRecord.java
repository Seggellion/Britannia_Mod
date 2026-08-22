package com.seggellion.britannia_mod.resource.deposit;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;
import java.util.UUID;

/** Durable write-ahead identity and tombstone for one managed-deposit removal operation. */
public record DepositRemovalRecord(
        UUID operationUuid,
        long instanceId,
        String resourceId,
        String sourceIdentity,
        int plannedCells,
        int removedBlocks,
        int depletedDebts,
        int preservedModified,
        boolean completed,
        long completedAt) {

    public DepositRemovalRecord {
        Objects.requireNonNull(operationUuid, "operation UUID is required");
        Objects.requireNonNull(resourceId, "resource id is required");
        Objects.requireNonNull(sourceIdentity, "source identity is required");
        if (instanceId == DepositInstance.NO_INSTANCE || plannedCells < 1
                || removedBlocks < 0 || depletedDebts < 0 || preservedModified < 0) {
            throw new IllegalArgumentException("invalid removal record");
        }
    }

    public boolean sameOperation(UUID operation, DepositInstance instance) {
        return operationUuid.equals(operation)
                && instanceId == instance.instanceId()
                && resourceId.equals(instance.resourceId())
                && sourceIdentity.equals(instance.sourceIdentity());
    }

    public DepositRemovalRecord withProgress(int removed, int debts) {
        return new DepositRemovalRecord(operationUuid, instanceId, resourceId, sourceIdentity,
                plannedCells, Math.max(removedBlocks, removed), Math.max(depletedDebts, debts),
                preservedModified, completed, completedAt);
    }

    public DepositRemovalRecord complete(long at) {
        return new DepositRemovalRecord(operationUuid, instanceId, resourceId, sourceIdentity,
                plannedCells, removedBlocks, depletedDebts, preservedModified, true, at);
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("operation", operationUuid);
        tag.putLong("instance", instanceId);
        tag.putString("resource", resourceId);
        tag.putString("identity", sourceIdentity);
        tag.putInt("planned", plannedCells);
        tag.putInt("removed", removedBlocks);
        tag.putInt("debts", depletedDebts);
        tag.putInt("preserved", preservedModified);
        tag.putBoolean("completed", completed);
        if (completed) tag.putLong("completedAt", completedAt);
        return tag;
    }

    public static DepositRemovalRecord fromNbt(CompoundTag tag) {
        return new DepositRemovalRecord(tag.getUUID("operation"), tag.getLong("instance"),
                tag.getString("resource"), tag.getString("identity"), tag.getInt("planned"),
                tag.getInt("removed"), tag.getInt("debts"), tag.getInt("preserved"),
                tag.getBoolean("completed"), tag.getLong("completedAt"));
    }
}
