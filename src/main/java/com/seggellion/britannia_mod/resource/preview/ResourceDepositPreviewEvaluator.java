package com.seggellion.britannia_mod.resource.preview;

import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.deposit.DepositInstance;
import com.seggellion.britannia_mod.resource.deposit.DepositLedger;
import com.seggellion.britannia_mod.resource.placement.MaterializationService;
import com.seggellion.britannia_mod.resource.placement.PlacementPlanner;
import com.seggellion.britannia_mod.resource.placement.PlannedDeposit;
import com.seggellion.britannia_mod.resource.shape.ShapeRotation;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only adapter from an M4 Rails request to the remediation branch's existing planner,
 * guarded host policy, structure index, and deposit ledger.
 *
 * <p>No method in this class calls materialisation, registration, {@code setBlock}, or restoration.
 * The selected radius is the resource definition's existing minimum -- a deterministic,
 * conservative footprint that adds no second tuning catalogue. Y is selected by comparing the
 * existing guarded-placement policy across the loaded build column and choosing the origin with
 * the most eligible hosts (ties prefer the origin nearest the column's mid-geology depth).
 */
public final class ResourceDepositPreviewEvaluator {
    private ResourceDepositPreviewEvaluator() {}

    public static ResourceDepositPreviewProtocol.Outcome evaluate(
            MinecraftServer server, ResourceDepositPreviewProtocol.Request request,
            UUID configuredServerKey) {
        return evaluate(server, request, configuredServerKey, DepositInstance.NO_INSTANCE);
    }

    /** M5 revalidation may ignore the exact instance created by the same idempotent operation. */
    public static ResourceDepositPreviewProtocol.Outcome evaluate(
            MinecraftServer server, ResourceDepositPreviewProtocol.Request request,
            UUID configuredServerKey, long ignoredInstanceId) {
        if (!request.target().minecraftServerUuid().equals(configuredServerKey)) {
            return failure("wrong_target", "preview target does not match this Minecraft server", false);
        }

        ResourceDefinition resource = ResourceCatalog.instance()
                .byPath(request.resourceDefinitionKey()).orElse(null);
        if (resource == null) {
            return failure("unknown_resource", "resource definition key is not in the NeoForge catalogue", false);
        }
        ResourceDefinition.Generation generation = resource.generation().orElse(null);
        if (generation == null) {
            return failure("resource_not_plannable", "resource definition has no managed-deposit geometry", false);
        }

        ResourceLocation dimensionId;
        try {
            dimensionId = ResourceLocation.parse(request.target().dimensionKey());
        } catch (RuntimeException malformed) {
            return failure("invalid_dimension", "target dimension key is malformed", false);
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return failure("dimension_unavailable", "target dimension is not available on this server", true);
        }

        int radius = generation.minRadius();
        long seed = previewSeed(request);
        ShapeRotation rotation = chooseRotation(generation, seed);
        int probeY = Math.max(level.getMinBuildHeight(), Math.min(0, level.getMaxBuildHeight() - 1));
        PlannedDeposit probe;
        try {
            probe = PlacementPlanner.plan(resource, dimensionId.toString(),
                    new BlockPos(request.x(), probeY, request.z()), radius, rotation, seed);
        } catch (RuntimeException invalidPlan) {
            return failure("planning_rejected", safeDetail(invalidPlan), false);
        }
        for (ChunkPos chunk : probe.touchedChunks()) {
            if (!level.hasChunk(chunk.x, chunk.z)) {
                return failure("chunks_not_loaded",
                        "preview footprint includes chunks that are not currently loaded", true);
            }
        }

        int minimumOriginY = level.getMinBuildHeight() - probe.plan().bounds().minY();
        int maximumOriginY = level.getMaxBuildHeight() - 1 - probe.plan().bounds().maxY();
        if (minimumOriginY > maximumOriginY) {
            return failure("shape_outside_build_height",
                    "resource shape cannot fit within this dimension's build height", false);
        }
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, request.x(), request.z()) - 1;
        int preferredY = clamp((level.getMinBuildHeight() + surface) / 2,
                minimumOriginY, maximumOriginY);

        ScoredPlan best = null;
        for (int y = minimumOriginY; y <= maximumOriginY; y++) {
            PlannedDeposit candidate = PlacementPlanner.plan(resource, dimensionId.toString(),
                    new BlockPos(request.x(), y, request.z()), radius, rotation, seed);
            MaterializationService.Inspection inspection = MaterializationService.inspect(level, candidate);
            ScoredPlan scored = new ScoredPlan(candidate, inspection, Math.abs(y - preferredY));
            if (best == null || scored.betterThan(best)) best = scored;
        }
        if (best == null) {
            return failure("no_build_height", "dimension exposes no candidate Y coordinate", false);
        }

        ResourceDepositPreviewProtocol.Bounds bounds = worldBounds(best.plan());
        List<String> warnings = warnings(best.inspection());
        List<String> conflicts = conflicts(level, bounds, ignoredInstanceId);
        if (best.inspection().validHosts() == 0) warnings.add("no_valid_hosts");
        boolean valid = best.inspection().validHosts() > 0 && conflicts.isEmpty();

        return new ResourceDepositPreviewProtocol.Evaluation(
                best.plan().origin().getY(), resource.revision(), generation.shape().id(), radius,
                rotation, bounds, best.inspection().planned(), best.inspection().validHosts(),
                best.inspection().rejectedHosts(), warnings, conflicts, valid);
    }

    private static ResourceDepositPreviewProtocol.Failure failure(
            String code, String detail, boolean retryable) {
        return new ResourceDepositPreviewProtocol.Failure(code, detail, retryable);
    }

    private static String safeDetail(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? "preview planning was rejected" : message;
    }

    public static long previewSeed(ResourceDepositPreviewProtocol.Request request) {
        long value = request.resourceDepositUuid().getMostSignificantBits()
                ^ Long.rotateLeft(request.resourceDepositUuid().getLeastSignificantBits(), 17)
                ^ request.resourceDepositRevision();
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        return value;
    }

    private static ShapeRotation chooseRotation(ResourceDefinition.Generation generation, long seed) {
        if (!generation.shape().planner().usesRotation()) return ShapeRotation.XZ;
        ShapeRotation[] rotations = ShapeRotation.values();
        return rotations[Math.floorMod((int) (seed ^ (seed >>> 32)), rotations.length)];
    }

    private static ResourceDepositPreviewProtocol.Bounds worldBounds(PlannedDeposit plan) {
        return new ResourceDepositPreviewProtocol.Bounds(
                plan.origin().getX() + plan.plan().bounds().minX(),
                plan.origin().getX() + plan.plan().bounds().maxX(),
                plan.origin().getY() + plan.plan().bounds().minY(),
                plan.origin().getY() + plan.plan().bounds().maxY(),
                plan.origin().getZ() + plan.plan().bounds().minZ(),
                plan.origin().getZ() + plan.plan().bounds().maxZ());
    }

    private static List<String> warnings(MaterializationService.Inspection inspection) {
        List<String> result = new ArrayList<>();
        inspection.rejections().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Comparator.comparing(entry -> entry.getKey().name()))
                .forEach(entry -> result.add("rejected_"
                        + entry.getKey().name().toLowerCase(Locale.ROOT) + ":" + entry.getValue()));
        return result;
    }

    private static List<String> conflicts(ServerLevel level,
            ResourceDepositPreviewProtocol.Bounds bounds, long ignoredInstanceId) {
        List<String> conflicts = new ArrayList<>();
        AABB footprint = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0);
        Set<UUID> structures = new HashSet<>();
        for (int chunkX = bounds.minX() >> 4; chunkX <= bounds.maxX() >> 4; chunkX++) {
            for (int chunkZ = bounds.minZ() >> 4; chunkZ <= bounds.maxZ() >> 4; chunkZ++) {
                for (StructureRecord record : StructureRegionManager.getStructuresInChunk(
                        level.dimension(), chunkX, chunkZ)) {
                    if (footprint.intersects(record.getFullBox()) && structures.add(record.getHouseUuid())) {
                        conflicts.add("structure:" + record.getHouseUuid());
                    }
                }
            }
        }
        for (DepositInstance existing : DepositLedger.get(level).all()) {
            if (existing.instanceId() == ignoredInstanceId) continue;
            if (overlaps(bounds, existing.boundsMin(), existing.boundsMax())) {
                conflicts.add("managed_deposit:" + Long.toHexString(existing.instanceId()));
            }
        }
        conflicts.sort(String::compareTo);
        return conflicts;
    }

    private static boolean overlaps(ResourceDepositPreviewProtocol.Bounds bounds,
                                    BlockPos minimum, BlockPos maximum) {
        return bounds.minX() <= maximum.getX() && bounds.maxX() >= minimum.getX()
                && bounds.minY() <= maximum.getY() && bounds.maxY() >= minimum.getY()
                && bounds.minZ() <= maximum.getZ() && bounds.maxZ() >= minimum.getZ();
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private record ScoredPlan(PlannedDeposit plan,
                              MaterializationService.Inspection inspection,
                              int distanceFromPreferredY) {
        boolean betterThan(ScoredPlan other) {
            if (inspection.validHosts() != other.inspection.validHosts()) {
                return inspection.validHosts() > other.inspection.validHosts();
            }
            if (inspection.rejectedHosts() != other.inspection.rejectedHosts()) {
                return inspection.rejectedHosts() < other.inspection.rejectedHosts();
            }
            if (distanceFromPreferredY != other.distanceFromPreferredY) {
                return distanceFromPreferredY < other.distanceFromPreferredY;
            }
            return plan.origin().getY() < other.plan.origin().getY();
        }
    }
}
