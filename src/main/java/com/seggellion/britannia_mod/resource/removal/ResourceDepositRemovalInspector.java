package com.seggellion.britannia_mod.resource.removal;

import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.mining.MiningProvenance;
import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.Resources;
import com.seggellion.britannia_mod.resource.deposit.DepositIdentity;
import com.seggellion.britannia_mod.resource.deposit.DepositInstance;
import com.seggellion.britannia_mod.resource.deposit.DepositLedger;
import com.seggellion.britannia_mod.resource.deposit.DepositSource;
import com.seggellion.britannia_mod.resource.placement.PlacementPlanner;
import com.seggellion.britannia_mod.resource.placement.PlannedDeposit;
import com.seggellion.britannia_mod.resource.preview.ResourceDepositPreviewProtocol;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Reconstructs exact DepositInstance geometry and inspects current world state without mutation. */
public final class ResourceDepositRemovalInspector {
    private ResourceDepositRemovalInspector() {}

    record Ready(ServerLevel level, DepositInstance instance, PlannedDeposit plan,
                 ResourceDefinition resource, ResourceDepositRemovalProtocol.Inspection inspection) {}
    sealed interface Resolution permits ReadyResolution, FailedResolution {}
    record ReadyResolution(Ready ready) implements Resolution {}
    record FailedResolution(ResourceDepositRemovalProtocol.Failure failure) implements Resolution {}

    public static ResourceDepositRemovalProtocol.PreviewOutcome evaluate(MinecraftServer server,
            ResourceDepositRemovalProtocol.PreviewRequest request, UUID configuredServerKey) {
        Resolution resolution = resolve(server, request, configuredServerKey);
        return resolution instanceof ReadyResolution ready ? ready.ready().inspection()
                : ((FailedResolution) resolution).failure();
    }

    static Resolution resolve(MinecraftServer server,
            ResourceDepositRemovalProtocol.PreviewRequest request, UUID configuredServerKey) {
        if (!request.target().minecraftServerUuid().equals(configuredServerKey)) {
            return failed("wrong_target", "removal preview target does not match this server", false);
        }
        ResourceDefinition resource = ResourceCatalog.instance()
                .byPath(request.resourceDefinitionKey()).orElse(null);
        if (resource == null || resource.generation().isEmpty()) {
            return failed("unknown_resource", "resource definition is unavailable or not plannable", false);
        }
        ResourceLocation dimensionId;
        try { dimensionId = ResourceLocation.parse(request.target().dimensionKey()); }
        catch (RuntimeException invalid) { return failed("invalid_dimension", "dimension key is malformed", false); }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (level == null) return failed("dimension_unavailable", "target dimension is unavailable", true);

        long suppliedInstance;
        try { suppliedInstance = Long.parseUnsignedLong(request.depositInstanceId(), 16); }
        catch (NumberFormatException invalid) { return failed("invalid_instance", "DepositInstance id is malformed", false); }
        long expectedInstance = DepositIdentity.worldAdmin(request.materializationOperationUuid(),
                request.resourceDepositUuid(), request.actualRevision(), dimensionId.toString(), resource.id());
        if (suppliedInstance != expectedInstance) {
            return failed("instance_correlation_mismatch", "DepositInstance id does not match materialization identity", false);
        }
        DepositLedger ledger = DepositLedger.get(level);
        if (ledger.removalByInstance(suppliedInstance).filter(record -> record.completed()).isPresent()) {
            return failed("already_removed", "DepositInstance is already tombstoned", false);
        }
        DepositInstance instance = ledger.byId(suppliedInstance).orElse(null);
        if (instance == null) return failed("instance_not_found", "active DepositInstance is not in this dimension ledger", false);
        String expectedSource = DepositIdentity.worldAdminEncoding(request.materializationOperationUuid(),
                request.resourceDepositUuid(), request.actualRevision(), dimensionId.toString(), resource.id());
        if (instance.source() != DepositSource.ADMIN || !instance.sourceIdentity().equals(expectedSource)
                || !instance.resourceId().equals(resource.id())) {
            return failed("instance_provenance_mismatch", "ledger source identity does not match the Rails materialization", false);
        }
        if (instance.definitionRevision() != resource.revision()) {
            return failed("definition_revision_mismatch", "resource definition changed since materialization", false);
        }

        PlannedDeposit plan;
        try {
            plan = PlacementPlanner.plan(resource, dimensionId.toString(), instance.origin(),
                    instance.radius(), instance.rotation(), instance.seed());
        } catch (RuntimeException invalid) {
            return failed("plan_reconstruction_failed", "persistent DepositInstance geometry cannot be reconstructed", false);
        }
        if (plan.count() != instance.plannedCells() || !boundsMatch(plan, instance)) {
            return failed("plan_provenance_mismatch", "reconstructed cells do not match persistent DepositInstance metadata", false);
        }
        for (ChunkPos chunk : plan.touchedChunks()) {
            if (!level.hasChunk(chunk.x, chunk.z)) {
                return failed("chunks_not_loaded", "DepositInstance footprint includes unloaded chunks", true);
            }
        }

        Block expectedBlock = Resources.block(resource.generation().orElseThrow().blockId());
        BrokenBlockDataStorage debts = BrokenBlockDataStorage.get(level);
        int matching = 0, depleted = 0, playerModified = 0, unexpected = 0;
        MessageDigest digest = sha256();
        for (BlockPos pos : plan.positions()) {
            BlockState current = level.getBlockState(pos);
            BrokenBlockData debt = debts.debtAt(pos);
            boolean player = MiningProvenance.isPlayerPlaced(level, pos);
            updateToken(digest, pos, current, debt, player);
            if (player) playerModified++;
            else if (current.is(expectedBlock)) matching++;
            else if (debt != null && debt.instanceId == instance.instanceId()
                    && debt.resourceId.equals(instance.resourceId())) depleted++;
            else unexpected++;
        }

        ResourceDepositPreviewProtocol.Bounds bounds = bounds(instance);
        List<String> protections = protectionConflicts(level, bounds);
        List<String> overlaps = overlapConflicts(level, instance, plan);
        List<String> warnings = new ArrayList<>();
        if (depleted > 0) warnings.add("depleted_cells:" + depleted);
        if (playerModified > 0) warnings.add("player_modified_cells_preserved:" + playerModified);
        if (unexpected > 0) warnings.add("unexpected_cells_preserved:" + unexpected);
        boolean safe = protections.isEmpty() && overlaps.isEmpty();
        ResourceDepositRemovalProtocol.Inspection inspection = new ResourceDepositRemovalProtocol.Inspection(
                bounds, plan.count(), matching, depleted, playerModified, unexpected, matching,
                warnings, protections, overlaps, safe, HexFormat.of().formatHex(digest.digest()));
        return new ReadyResolution(new Ready(level, instance, plan, resource, inspection));
    }

    private static List<String> overlapConflicts(ServerLevel level, DepositInstance target,
                                                  PlannedDeposit targetPlan) {
        Set<Long> targetCells = new HashSet<>();
        targetPlan.positions().forEach(pos -> targetCells.add(pos.asLong()));
        List<String> conflicts = new ArrayList<>();
        for (DepositInstance other : DepositLedger.get(level).all()) {
            if (other.instanceId() == target.instanceId()) continue;
            if (!boxesOverlap(target, other)) continue;
            ResourceDefinition definition = ResourceCatalog.instance().byId(other.resourceId()).orElse(null);
            if (definition == null || definition.generation().isEmpty()) {
                conflicts.add("managed_deposit_unverifiable:" + Long.toUnsignedString(other.instanceId(), 16));
                continue;
            }
            try {
                PlannedDeposit otherPlan = PlacementPlanner.plan(definition, level.dimension().location().toString(),
                        other.origin(), other.radius(), other.rotation(), other.seed());
                if (otherPlan.positions().stream().anyMatch(pos -> targetCells.contains(pos.asLong()))) {
                    conflicts.add("managed_deposit:" + Long.toUnsignedString(other.instanceId(), 16));
                }
            } catch (RuntimeException invalid) {
                conflicts.add("managed_deposit_unverifiable:" + Long.toUnsignedString(other.instanceId(), 16));
            }
        }
        conflicts.sort(String::compareTo);
        return conflicts;
    }

    private static List<String> protectionConflicts(ServerLevel level,
            ResourceDepositPreviewProtocol.Bounds bounds) {
        AABB footprint = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0);
        Set<UUID> found = new HashSet<>();
        for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++) {
            for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++) {
                for (StructureRecord record : StructureRegionManager.getStructuresInChunk(level.dimension(), cx, cz)) {
                    if (footprint.intersects(record.getFullBox())) found.add(record.getHouseUuid());
                }
            }
        }
        return found.stream().map(uuid -> "structure:" + uuid).sorted().toList();
    }

    private static void updateToken(MessageDigest digest, BlockPos pos, BlockState state,
                                    BrokenBlockData debt, boolean player) {
        String row = pos.asLong() + "|" + BuiltInRegistries.BLOCK.getKey(state.getBlock()) + "|"
                + (debt == null ? "none" : Long.toUnsignedString(debt.instanceId, 16)) + "|" + player + "\n";
        digest.update(row.getBytes(StandardCharsets.UTF_8));
    }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static boolean boundsMatch(PlannedDeposit plan, DepositInstance instance) {
        var b = plan.plan().bounds(); BlockPos o = plan.origin();
        return instance.boundsMin().equals(o.offset(b.minX(), b.minY(), b.minZ()))
                && instance.boundsMax().equals(o.offset(b.maxX(), b.maxY(), b.maxZ()));
    }
    private static ResourceDepositPreviewProtocol.Bounds bounds(DepositInstance i) {
        return new ResourceDepositPreviewProtocol.Bounds(i.boundsMin().getX(), i.boundsMax().getX(),
                i.boundsMin().getY(), i.boundsMax().getY(), i.boundsMin().getZ(), i.boundsMax().getZ());
    }
    private static boolean boxesOverlap(DepositInstance a, DepositInstance b) {
        return a.boundsMin().getX() <= b.boundsMax().getX() && a.boundsMax().getX() >= b.boundsMin().getX()
                && a.boundsMin().getY() <= b.boundsMax().getY() && a.boundsMax().getY() >= b.boundsMin().getY()
                && a.boundsMin().getZ() <= b.boundsMax().getZ() && a.boundsMax().getZ() >= b.boundsMin().getZ();
    }
    private static FailedResolution failed(String code, String detail, boolean retryable) {
        return new FailedResolution(new ResourceDepositRemovalProtocol.Failure(code, detail, retryable));
    }
}
