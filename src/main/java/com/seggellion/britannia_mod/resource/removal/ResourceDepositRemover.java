package com.seggellion.britannia_mod.resource.removal;

import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.mining.MiningProvenance;
import com.seggellion.britannia_mod.resource.Resources;
import com.seggellion.britannia_mod.resource.deposit.DepositLedger;
import com.seggellion.britannia_mod.resource.deposit.DepositRemovalRecord;
import com.seggellion.britannia_mod.resource.placement.MaterializationService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

/** Provenance-aware remover. Exact deterministic cells, never block type/radius/bounds alone. */
public final class ResourceDepositRemover {
    private ResourceDepositRemover() {}

    public static ResourceDepositRemovalProtocol.RemovalOutcome process(MinecraftServer server,
            ResourceDepositRemovalProtocol.RemovalOperation operation, UUID configuredServerKey) {
        if (configuredServerKey == null
                || !configuredServerKey.equals(operation.target().minecraftServerUuid())) {
            return new ResourceDepositRemovalProtocol.Failure("wrong_server",
                    "operation target does not match configured server identity", false);
        }
        ResourceLocation dimension = ResourceLocation.tryParse(operation.target().dimensionKey());
        if (dimension == null) {
            return new ResourceDepositRemovalProtocol.Failure("invalid_dimension",
                    "operation dimension is invalid", false);
        }
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimension);
        ServerLevel targetLevel = server.getLevel(dimensionKey);
        if (targetLevel == null) return new ResourceDepositRemovalProtocol.Failure(
                "dimension_unavailable", "operation dimension is not loaded", true);
        DepositRemovalRecord replay = DepositLedger.get(targetLevel)
                .removalByOperation(operation.operationUuid()).orElse(null);
        if (replay != null && replay.completed()) return removed(replay, true);

        ResourceDepositRemovalProtocol.PreviewRequest request = new ResourceDepositRemovalProtocol.PreviewRequest(
                operation.removalPreviewUuid(), operation.resourceDepositUuid(),
                operation.resourceDepositRevision(), operation.actualRevision(),
                operation.materializationOperationUuid(), operation.depositInstanceId(),
                operation.resourceDefinitionKey(), operation.target());
        ResourceDepositRemovalInspector.Resolution resolution =
                ResourceDepositRemovalInspector.resolve(server, request, configuredServerKey);
        if (resolution instanceof ResourceDepositRemovalInspector.FailedResolution failed) {
            return failed.failure();
        }
        ResourceDepositRemovalInspector.Ready ready =
                ((ResourceDepositRemovalInspector.ReadyResolution) resolution).ready();
        DepositLedger ledger = DepositLedger.get(ready.level());
        DepositRemovalRecord progress = ledger.removalByOperation(operation.operationUuid()).orElse(null);
        if (progress == null) {
            if (!ready.inspection().safeToRemove() || !ready.inspection().equals(operation.approvedPreview())) {
                return new ResourceDepositRemovalProtocol.Failure("removal_preview_stale",
                        "current world/provenance no longer matches the approved destructive preview", false);
            }
            int preserved = ready.inspection().playerModifiedCellCount()
                    + ready.inspection().unexpectedCellCount();
            DepositLedger.RemovalRegistration registration = ledger.beginRemoval(
                    operation.operationUuid(), ready.instance(), preserved);
            if (!registration.accepted()) {
                return new ResourceDepositRemovalProtocol.Failure("removal_identity_conflict",
                        registration.message(), false);
            }
            progress = registration.record();
        } else if (!progress.sameOperation(operation.operationUuid(), ready.instance())) {
            return new ResourceDepositRemovalProtocol.Failure("removal_identity_conflict",
                    "persisted removal operation does not match the active DepositInstance", false);
        } else if (!ready.inspection().protectionConflicts().isEmpty()
                || !ready.inspection().overlapConflicts().isEmpty()) {
            return new ResourceDepositRemovalProtocol.Failure("removal_revalidation_conflict",
                    "protection or managed-deposit overlap appeared during removal recovery", false);
        }

        Block expected = Resources.block(ready.resource().generation().orElseThrow().blockId());
        BrokenBlockDataStorage debts = BrokenBlockDataStorage.get(ready.level());
        int removed = progress.removedBlocks();
        int depleted = progress.depletedDebts();
        try {
            for (BlockPos pos : ready.plan().positions()) {
                if (MiningProvenance.isPlayerPlaced(ready.level(), pos)) continue;
                BrokenBlockData debt = debts.debtAt(pos);
                if (ready.level().getBlockState(pos).is(expected)) {
                    if (!ready.level().setBlock(pos, Blocks.AIR.defaultBlockState(),
                            MaterializationService.WRITE_FLAGS)) {
                        ledger.recordRemovalProgress(operation.operationUuid(), removed, depleted);
                        return ResourceDepositRemovalProtocol.Deferred.INSTANCE;
                    }
                    if (ready.level().getBlockState(pos).is(expected)) {
                        ledger.recordRemovalProgress(operation.operationUuid(), removed, depleted);
                        return ResourceDepositRemovalProtocol.Deferred.INSTANCE;
                    }
                    removed++;
                }
                if (debt != null && debt.instanceId == ready.instance().instanceId()
                        && debt.resourceId.equals(ready.instance().resourceId())) {
                    debts.remove(pos);
                    depleted++;
                }
            }
        } catch (RuntimeException uncertain) {
            ledger.recordRemovalProgress(operation.operationUuid(), removed, depleted);
            return ResourceDepositRemovalProtocol.Deferred.INSTANCE;
        }
        ledger.recordRemovalProgress(operation.operationUuid(), removed, depleted);
        DepositRemovalRecord tombstone = ledger.completeRemoval(operation.operationUuid(), System.currentTimeMillis());
        return removed(tombstone, false);
    }

    private static ResourceDepositRemovalProtocol.Removed removed(DepositRemovalRecord record,
                                                                  boolean replayed) {
        return new ResourceDepositRemovalProtocol.Removed(record.plannedCells(),
                record.removedBlocks(), record.depletedDebts(), record.preservedModified(),
                record.instanceId(), replayed);
    }
}
