package com.seggellion.britannia_mod.resource.placement;

import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.deposit.DepositIdentity;
import com.seggellion.britannia_mod.resource.shape.ShapeConfig;
import com.seggellion.britannia_mod.resource.shape.ShapePlan;
import com.seggellion.britannia_mod.resource.shape.ShapePlanner;
import com.seggellion.britannia_mod.resource.shape.ShapeRotation;

import net.minecraft.core.BlockPos;

import java.util.Optional;

/**
 * Turns a resource definition and a place into a plan.
 *
 * <p>The layer that knows both halves: it reads the resource's configured shape, range and
 * rotation from the catalogue, derives or accepts a deterministic seed, and hands the pure planner
 * a {@link ShapeConfig}. It reads no blocks, loads no chunks and writes nothing — a plan can be
 * produced for a place that has never been generated.
 *
 * <p>Milestone 4 will pass a persisted deposit-instance seed here instead of deriving one. The
 * signature already takes a seed for exactly that reason; {@link #planCuratedVein} is the
 * compatibility entry that derives one for a Rails row, and is the only thing that will need to
 * change.
 */
public final class PlacementPlanner {

    private PlacementPlanner() {
    }

    /** Why a placement could not be planned at all, in words an operator can act on. */
    public record Rejection(String reason) {
    }

    /**
     * Plan a deposit at an explicit seed.
     *
     * @throws IllegalArgumentException if the resource has no generation configuration, or the
     *                                  radius falls outside what the resource and its shape allow
     */
    public static PlannedDeposit plan(
            ResourceDefinition resource,
            String dimensionId,
            BlockPos origin,
            int radius,
            ShapeRotation rotation,
            long seed) {

        ResourceDefinition.Generation generation = resource.generation().orElseThrow(
                () -> new IllegalArgumentException(
                        "Resource " + resource.id() + " has no generation configuration"));
        checkRadius(resource, generation, radius);

        ShapeConfig config = new ShapeConfig(radius, rotation, seed);
        ShapePlanner planner = generation.shape().planner();
        ShapePlan plan = planner.plan(config);
        return new PlannedDeposit(resource, dimensionId, origin, config, plan);
    }

    /**
     * Plan a curated Rails vein, deriving its seed from the row's immutable identity.
     *
     * <p>The compatibility path. Running the same row twice re-derives the same identity, therefore
     * the same seed, therefore the same cells — which is what stopped {@code /populateores}
     * rerolling a vein every time it was run.
     *
     * <p>Milestone 4 folded the seed derivation into {@link DepositIdentity}, so the id a deposit is
     * registered under and the seed its geometry is drawn from now come from one contract rather
     * than from two functions that happened to agree.
     */
    public static PlannedDeposit planCuratedVein(
            ResourceDefinition resource,
            String dimensionId,
            BlockPos origin,
            int radius,
            ShapeRotation rotation) {
        return planCuratedVein(resource, "", dimensionId, origin, radius, rotation, "");
    }

    /** The same, with the shard and region a real curated row carries. */
    public static PlannedDeposit planCuratedVein(
            ResourceDefinition resource,
            String shard,
            String dimensionId,
            BlockPos origin,
            int radius,
            ShapeRotation rotation,
            String region) {
        long instanceId = DepositIdentity.rails(shard, dimensionId, resource.id(),
                origin.getX(), origin.getY(), origin.getZ(), radius, rotation, region);
        return plan(resource, dimensionId, origin, radius, rotation,
                DepositIdentity.plannerSeed(instanceId));
    }

    /**
     * Whether this radius may be planned, without planning it.
     *
     * <p>Used by the command so a bad curated row is reported and skipped rather than throwing out
     * of a half-finished run. The planner itself re-checks its own minimum regardless — that check
     * is the shape's contract with every caller, not a courtesy this layer performs on its behalf.
     */
    public static Optional<Rejection> reject(ResourceDefinition resource, int radius) {
        ResourceDefinition.Generation generation = resource.generation().orElse(null);
        if (generation == null) {
            return Optional.of(new Rejection(
                    "resource " + resource.id() + " has no generation configuration"));
        }
        try {
            checkRadius(resource, generation, radius);
            return Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.of(new Rejection(exception.getMessage()));
        }
    }

    private static void checkRadius(
            ResourceDefinition resource, ResourceDefinition.Generation generation, int radius) {
        String shapeName = generation.shape().id();
        if (radius < generation.minRadius()) {
            throw new IllegalArgumentException("radius " + radius + " is below the minimum "
                    + generation.minRadius() + " configured for " + resource.path() + "'s "
                    + shapeName + " shape");
        }
        if (radius > generation.maxRadius()) {
            throw new IllegalArgumentException("radius " + radius + " exceeds the maximum "
                    + generation.maxRadius() + " configured for " + resource.path() + "'s "
                    + shapeName + " shape");
        }
    }
}
