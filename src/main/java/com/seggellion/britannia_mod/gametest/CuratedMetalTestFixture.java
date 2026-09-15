package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.placement.PlannedDeposit;
import com.seggellion.britannia_mod.resource.shape.ShapeRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/** Pure setup for a curated-metal lifecycle, not a test of every possible curated row. */
final class CuratedMetalTestFixture {
    private static final int MAX_ROWS = 256;

    private CuratedMetalTestFixture() {
    }

    record Fixture(CuratedDepositTestRows row, PlannedDeposit deposit, BlockPos cell) {
    }

    /**
     * Choose a valid input before testing materialisation or extraction. A chunk-edge test origin
     * can seed a wandering vein entirely outside its own chunk. Keep every world write inside
     * both that chunk and the template interior; the planner still decides cell membership.
     * Region names are deterministic curated-row parameters, never a replacement planner seed.
     * The bounded search reads no world state and cannot hide a failed lifecycle assertion.
     */
    static Fixture select(String path, String dimension, AABB interior, ChunkPos chunk,
                          int minBuildHeight, int maxBuildHeight) {
        int minX = Math.max((int) Math.ceil(interior.minX), chunk.getMinBlockX());
        int maxX = Math.min((int) Math.ceil(interior.maxX) - 1, chunk.getMaxBlockX());
        int minZ = Math.max((int) Math.ceil(interior.minZ), chunk.getMinBlockZ());
        int maxZ = Math.min((int) Math.ceil(interior.maxZ) - 1, chunk.getMaxBlockZ());
        int minY = Math.max((int) Math.ceil(interior.minY), minBuildHeight + 5);
        int maxY = Math.min((int) Math.ceil(interior.maxY) - 1, maxBuildHeight - 2);
        if (minX > maxX || minZ > maxZ || minY >= maxY) {
            throw new IllegalArgumentException("No usable template interior in the test's own chunk: "
                    + interior + " chunk=" + chunk);
        }
        BlockPos origin = new BlockPos(minX + (maxX - minX) / 2, minY,
                minZ + (maxZ - minZ) / 2);
        var resource = ResourceCatalog.instance().byPath(path).orElseThrow();
        int radius = Math.min(20, resource.generation().orElseThrow().maxRadius());
        for (int attempt = 0; attempt < MAX_ROWS; attempt++) {
            String region = attempt == 0 ? "gametest-region" : "gametest-metal-fixture-" + attempt;
            CuratedDepositTestRows row = new CuratedDepositTestRows(
                    resource, origin, radius, ShapeRotation.XZ, region);
            PlannedDeposit deposit = row.plan(dimension);
            BlockPos best = null;
            int bestDistance = Integer.MAX_VALUE;
            for (BlockPos candidate : deposit.positionsIn(chunk)) {
                if (candidate.getX() < minX || candidate.getX() > maxX
                        || candidate.getZ() < minZ || candidate.getZ() > maxZ
                        || candidate.getY() < minY || candidate.getY() > maxY) {
                    continue;
                }
                int distance = candidate.distManhattan(origin);
                if (distance < bestDistance) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
            if (best != null) {
                return new Fixture(row, deposit, best);
            }
        }
        throw new IllegalStateException("No curated " + path + " fixture cell in " + interior
                + " and chunk " + chunk + " after " + MAX_ROWS + " deterministic rows");
    }
}
