package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.deposit.DepositIdentity;
import com.seggellion.britannia_mod.resource.deposit.DepositInstance;
import com.seggellion.britannia_mod.resource.deposit.DepositRegistrar;
import com.seggellion.britannia_mod.resource.deposit.DepositSource;
import com.seggellion.britannia_mod.resource.placement.PlacementPlanner;
import com.seggellion.britannia_mod.resource.placement.PlannedDeposit;
import com.seggellion.britannia_mod.resource.shape.ShapeRotation;

import net.minecraft.core.BlockPos;

/**
 * A curated Rails deposit row, as the tests use one.
 *
 * <h2>Why this exists</h2>
 * Milestone 11's amendment made Rails the only thing that decides a deposit exists. Before it, the
 * lifecycle tests reached for a world-seed candidate to get a radius and a planner seed; there is
 * no such thing any more, and there should not be a second way to obtain them.
 *
 * <p>So the tests build a row the way {@code /populateores} does — resource, origin, radius,
 * rotation, shard, region — and let {@link PlacementPlanner#planCuratedVein} derive the identity
 * and the seed from those immutable parameters. That means the tests exercise the real contract
 * rather than a convenient approximation of it, and a change to how a Rails row becomes geometry
 * fails them.
 *
 * <p>The row carries no randomness. The same parameters always produce the same identity, the same
 * seed and therefore the same cells, which is the property {@code RailsDepositAuthorityGameTests}
 * asserts directly.
 */
public record CuratedDepositTestRows(
        ResourceDefinition resource,
        BlockPos origin,
        int radius,
        ShapeRotation rotation,
        String region) {

    /** The shard a test row belongs to. Any stable string does; this one names itself. */
    public static final String SHARD = "gametest";

    public static CuratedDepositTestRows of(String path, BlockPos origin, int radius) {
        return new CuratedDepositTestRows(
                ResourceCatalog.instance().byPath(path).orElseThrow(
                        () -> new IllegalStateException("no resource " + path)),
                origin, radius, ShapeRotation.XZ, "gametest-region");
    }

    /** The stable id this row's immutable parameters hash to. */
    public long identity(String dimensionId) {
        return DepositIdentity.rails(SHARD, dimensionId, resource.id(),
                origin.getX(), origin.getY(), origin.getZ(), radius, rotation, region);
    }

    /** The identity's readable encoding, which the ledger stores for auditing. */
    public String encoding(String dimensionId) {
        return DepositIdentity.railsEncoding(SHARD, dimensionId, resource.id(),
                origin.getX(), origin.getY(), origin.getZ(), radius, rotation, region);
    }

    /** The deterministic geometry these parameters describe. */
    public PlannedDeposit plan(String dimensionId) {
        return PlacementPlanner.planCuratedVein(
                resource, SHARD, dimensionId, origin, radius, rotation, region);
    }

    /** The ledger record for this row, ready to register. */
    public DepositInstance describe(String dimensionId) {
        return DepositRegistrar.describe(
                plan(dimensionId), identity(dimensionId), DepositSource.RAILS, encoding(dimensionId));
    }
}
