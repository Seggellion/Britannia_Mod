package com.seggellion.britannia_mod.resource.materialization;

import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.deposit.DepositIdentity;
import com.seggellion.britannia_mod.resource.deposit.DepositInstance;
import com.seggellion.britannia_mod.resource.deposit.DepositLedger;
import com.seggellion.britannia_mod.resource.deposit.DepositRegistrar;
import com.seggellion.britannia_mod.resource.deposit.DepositSource;
import com.seggellion.britannia_mod.resource.placement.MaterializationService;
import com.seggellion.britannia_mod.resource.placement.PlacementPlanner;
import com.seggellion.britannia_mod.resource.placement.PlannedDeposit;
import com.seggellion.britannia_mod.resource.preview.ResourceDepositPreviewEvaluator;
import com.seggellion.britannia_mod.resource.preview.ResourceDepositPreviewProtocol;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * The smallest M5 adapter from an approved M4 preview to the existing managed-deposit platform.
 * Registration precedes every block write; success follows only a complete recorded pass.
 */
public final class ResourceDepositMaterializer {
    private ResourceDepositMaterializer() {}

    public static ResourceDepositMaterializationProtocol.Outcome process(
            MinecraftServer server, ResourceDepositMaterializationProtocol.Operation operation,
            UUID configuredServerKey) {
        if (!operation.target().minecraftServerUuid().equals(configuredServerKey)) {
            return failure("wrong_target", "materialization target does not match this server", false);
        }

        ResourceDefinition resource = ResourceCatalog.instance()
                .byPath(operation.resourceDefinitionKey()).orElse(null);
        if (resource == null) return failure("unknown_resource", "resource definition is unavailable", false);
        if (resource.generation().isEmpty()) {
            return failure("resource_not_plannable", "resource has no managed-deposit geometry", false);
        }

        ResourceLocation dimensionId;
        try {
            dimensionId = ResourceLocation.parse(operation.target().dimensionKey());
        } catch (RuntimeException invalid) {
            return failure("invalid_dimension", "target dimension key is malformed", false);
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel level = server.getLevel(dimension);
        if (level == null) return failure("dimension_unavailable", "target dimension is unavailable", true);

        ResourceDepositPreviewProtocol.Request previewRequest = previewRequest(operation);
        long seed = ResourceDepositPreviewEvaluator.previewSeed(previewRequest);
        ResourceDepositPreviewProtocol.Evaluation approved = operation.approvedPreview();
        PlannedDeposit plan;
        try {
            plan = PlacementPlanner.plan(resource, dimensionId.toString(),
                    new BlockPos(operation.x(), approved.resolvedY(), operation.z()),
                    approved.radius(), approved.rotation(), seed);
        } catch (RuntimeException rejected) {
            return failure("planning_rejected", "approved geometry can no longer be planned", false);
        }
        if (plan.count() != approved.plannedBlockCount() ||
                !bounds(plan).equals(approved.footprint()) ||
                resource.revision() != approved.resourceDefinitionRevision()) {
            return failure("approved_plan_mismatch", "approved preview does not match the deterministic planner", false);
        }
        for (ChunkPos chunk : plan.touchedChunks()) {
            if (!level.hasChunk(chunk.x, chunk.z)) {
                return failure("chunks_not_loaded", "materialization footprint includes unloaded chunks", true);
            }
        }

        long instanceId = DepositIdentity.worldAdmin(operation.operationUuid(),
                operation.resourceDepositUuid(), operation.resourceDepositRevision(),
                dimensionId.toString(), resource.id());
        String sourceIdentity = DepositIdentity.worldAdminEncoding(operation.operationUuid(),
                operation.resourceDepositUuid(), operation.resourceDepositRevision(),
                dimensionId.toString(), resource.id());
        DepositInstance candidate = DepositRegistrar.describe(
                plan, instanceId, DepositSource.ADMIN, sourceIdentity);
        DepositLedger ledger = DepositLedger.get(level);
        DepositInstance existing = ledger.byId(instanceId).orElse(null);

        if (existing == null) {
            ResourceDepositPreviewProtocol.Outcome current = ResourceDepositPreviewEvaluator.evaluate(
                    server, previewRequest, configuredServerKey);
            if (current instanceof ResourceDepositPreviewProtocol.Failure failed) {
                return failure(failed.code(), failed.detail(), failed.retryable());
            }
            if (!approved.equals(current)) {
                return failure("preview_world_changed",
                        "world/geology/conflict state no longer matches the approved preview", false);
            }
        } else {
            DepositLedger.Registration replayRegistration = ledger.register(candidate);
            if (!replayRegistration.mayMaterialize()) {
                return failure("deposit_identity_conflict", replayRegistration.message(), false);
            }
            if (existing.progressKnown()) {
                return existing.materializedCells() > 0
                        ? generated(existing, true)
                        : ResourceDepositMaterializationProtocol.Deferred.INSTANCE;
            }

            // A crash may have registered and partly written the exact same operation. Ignore only
            // that instance while checking new external conflicts; never acknowledge a partial pass.
            ResourceDepositPreviewProtocol.Outcome replayCheck = ResourceDepositPreviewEvaluator.evaluate(
                    server, previewRequest, configuredServerKey, instanceId);
            if (replayCheck instanceof ResourceDepositPreviewProtocol.Failure) {
                return ResourceDepositMaterializationProtocol.Deferred.INSTANCE;
            }
            ResourceDepositPreviewProtocol.Evaluation replayEvaluation =
                    (ResourceDepositPreviewProtocol.Evaluation) replayCheck;
            if (!replayEvaluation.conflicts().isEmpty()) {
                return ResourceDepositMaterializationProtocol.Deferred.INSTANCE;
            }
        }

        DepositLedger.Registration registration = ledger.register(candidate);
        if (!registration.mayMaterialize()) {
            return failure("deposit_identity_conflict", registration.message(), false);
        }

        try {
            MaterializationService.Result pass = MaterializationService.materialize(level, plan);
            DepositRegistrar.recordCompletePass(level, instanceId, pass, plan.count());
            if (pass.truncated()) return ResourceDepositMaterializationProtocol.Deferred.INSTANCE;
        } catch (RuntimeException unexpectedAfterRegistration) {
            // The persistent instance is the recovery marker. Do not report failure or success;
            // the same operation resumes idempotently on the next tick/poll.
            return ResourceDepositMaterializationProtocol.Deferred.INSTANCE;
        }

        DepositInstance completed = ledger.byId(instanceId).orElseThrow();
        if (!completed.progressKnown() || completed.materializedCells() <= 0) {
            return ResourceDepositMaterializationProtocol.Deferred.INSTANCE;
        }
        return generated(completed, existing != null ||
                registration.outcome() == DepositLedger.Outcome.ALREADY_REGISTERED);
    }

    private static ResourceDepositPreviewProtocol.Request previewRequest(
            ResourceDepositMaterializationProtocol.Operation operation) {
        return new ResourceDepositPreviewProtocol.Request(operation.previewUuid(),
                operation.resourceDepositUuid(), operation.resourceDepositRevision(),
                operation.resourceDefinitionKey(),
                new ResourceDepositPreviewProtocol.Target(operation.target().shardUuid(),
                        operation.target().minecraftServerUuid(), operation.target().worldName(),
                        operation.target().dimensionKey()), operation.x(), operation.z());
    }

    private static ResourceDepositPreviewProtocol.Bounds bounds(PlannedDeposit plan) {
        return new ResourceDepositPreviewProtocol.Bounds(
                plan.origin().getX() + plan.plan().bounds().minX(),
                plan.origin().getX() + plan.plan().bounds().maxX(),
                plan.origin().getY() + plan.plan().bounds().minY(),
                plan.origin().getY() + plan.plan().bounds().maxY(),
                plan.origin().getZ() + plan.plan().bounds().minZ(),
                plan.origin().getZ() + plan.plan().bounds().maxZ());
    }

    private static ResourceDepositMaterializationProtocol.Generated generated(
            DepositInstance instance, boolean replayed) {
        return new ResourceDepositMaterializationProtocol.Generated(instance.instanceId(),
                instance.definitionRevision(), instance.origin().getY(),
                new ResourceDepositPreviewProtocol.Bounds(instance.boundsMin().getX(),
                        instance.boundsMax().getX(), instance.boundsMin().getY(),
                        instance.boundsMax().getY(), instance.boundsMin().getZ(),
                        instance.boundsMax().getZ()), instance.plannedCells(),
                instance.materializedCells(), instance.blockedCells(),
                instance.materializationVersion(), replayed);
    }

    private static ResourceDepositMaterializationProtocol.Failure failure(
            String code, String detail, boolean retryable) {
        return new ResourceDepositMaterializationProtocol.Failure(code, detail, retryable);
    }
}
