package com.seggellion.britannia_mod.resource.placement;

import com.seggellion.britannia_mod.resource.shape.ShapeRotation;

import java.nio.charset.StandardCharsets;

/**
 * The deterministic seed a deposit's geometry is drawn from.
 *
 * <h2>What this is, and what it is not</h2>
 * Milestone 4 gives deposits persistent identity — a stable instance id, stored in a ledger, with
 * duplicate detection across sessions. <b>This is not that.</b> It is the smallest thing milestone 3
 * needs: a seed derived from inputs that do not change between two runs of the same placement, so
 * that running the same command twice produces the same vein instead of a second, different one.
 *
 * <p>The distinction matters and should not be blurred. A seed makes geometry <em>reproducible</em>;
 * an instance ledger makes a deposit <em>known</em>. Today, placing the same curated row twice
 * re-plans the identical cells and writes nothing new, because every cell already holds what it
 * should — that is placement-level idempotence. It is not duplicate detection: nothing yet records
 * that the deposit exists, so nothing could report it, relocate it, or refuse a second one at a
 * different origin.
 *
 * <h2>What it is derived from</h2>
 * Only immutable properties of the placement: the dimension, the resource, the origin, the
 * configured radius and the rotation. Deliberately not the world seed, the game time, the command's
 * execution time or any RNG — the legacy shapes drew from {@code ServerLevel#getRandom()}, which is
 * exactly why the same command never produced the same vein twice.
 *
 * <p>When milestone 4 arrives, a deposit instance will carry its own persisted seed and
 * {@code PlacementPlanner} will take it directly. Nothing here needs to change for that; this
 * becomes the derivation used when a curated row is first imported, and the persisted value takes
 * over afterwards.
 */
public final class DepositSeed {

    private DepositSeed() {
    }

    /**
     * A stable seed for a curated vein row.
     *
     * <p>If Rails ever supplies a stable row id — which milestone 0 recorded that it does not — that
     * id should be mixed in here in place of the coordinates, so that moving a curated vein keeps
     * its character instead of re-rolling it. Until then the coordinates are the identity, which
     * means editing a row's position or size does re-roll its geometry. That is a known and
     * acceptable limitation of a compatibility path, not a property of the platform.
     */
    public static long forCuratedVein(
            String dimensionId,
            String resourceId,
            int x, int y, int z,
            int radius,
            ShapeRotation rotation) {
        String identity = dimensionId + '|' + resourceId + '|'
                + x + ',' + y + ',' + z + '|' + radius + '|' + rotation.id();
        return hash64(identity);
    }

    /**
     * FNV-1a over UTF-8, written out rather than delegated.
     *
     * <p>{@code String.hashCode} is only 32 bits and clusters badly; a library hash could change
     * between versions. These seeds decide what a persistent world looks like, so the function has
     * to be one that cannot drift.
     */
    public static long hash64(String identity) {
        final long offsetBasis = 0xCBF29CE484222325L;
        final long prime = 0x100000001B3L;
        long hash = offsetBasis;
        for (byte b : identity.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xFF);
            hash *= prime;
        }
        return hash;
    }
}
