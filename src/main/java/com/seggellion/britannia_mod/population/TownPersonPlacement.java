package com.seggellion.britannia_mod.population;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/**
 * Vendor/Trader Milestone 20: decides WHERE a TownPerson may stand inside a
 * city region, applying every condition owner decision #12 requires — correct
 * region, loaded chunks only, valid ground, headroom, no solid or liquid
 * placement, a reasonable distance from players, and a local density cap.
 *
 * <p>Static and level-driven so GameTests can prove each rejection reason
 * against a real world rather than a mock.
 *
 * <h2>Never force-loads a chunk</h2>
 * Candidates in unloaded chunks are REJECTED, not loaded. Population is a
 * cosmetic expression of prosperity; it must never drag chunks into memory or
 * keep them there. That is why placement scans a bounded number of random
 * candidates and simply gives up for this cycle if none is valid.
 */
public final class TownPersonPlacement {
    /** How many random candidates one cycle will consider before giving up. */
    public static final int CANDIDATE_ATTEMPTS = 24;
    /** TownPersons never appear right on top of a player. */
    public static final double MIN_PLAYER_DISTANCE = 12.0D;
    /** Local crowding guard, independent of the city-wide target. */
    public static final double DENSITY_RADIUS = 8.0D;
    public static final int MAX_LOCAL_DENSITY = 4;

    public enum Rejection {
        OUTSIDE_REGION, CHUNK_NOT_LOADED, NO_SOLID_GROUND, FEET_BLOCKED, HEAD_BLOCKED,
        LIQUID, TOO_CLOSE_TO_PLAYER, TOO_CROWDED
    }

    private TownPersonPlacement() {
    }

    /**
     * Returns a valid spawn position, or null when this cycle should place
     * nobody. Giving up is a normal outcome, never an error.
     */
    @Nullable
    public static BlockPos findSpawnPosition(ServerLevel level, TownPersonPopulationPlan plan, RandomSource random) {
        if (!plan.placeable()) return null;

        for (int attempt = 0; attempt < CANDIDATE_ATTEMPTS; attempt++) {
            TownPersonPopulationPlan.Bounds bounds =
                    plan.regions().get(random.nextInt(plan.regions().size()));
            BlockPos candidate = new BlockPos(
                    randomBetween(random, bounds.minX(), bounds.maxX()),
                    randomBetween(random, bounds.minY(), bounds.maxY()),
                    randomBetween(random, bounds.minZ(), bounds.maxZ())
            );
            if (rejectionFor(level, plan, candidate) == null) return candidate;
        }
        return null;
    }

    /** The single validation authority; null means the position is usable. */
    @Nullable
    public static Rejection rejectionFor(ServerLevel level, TownPersonPopulationPlan plan, BlockPos pos) {
        if (!plan.containsPosition(pos)) return Rejection.OUTSIDE_REGION;
        // Loaded-chunks-only: checked BEFORE any block lookup, so a rejected
        // candidate never touches chunk generation or loading.
        if (!level.hasChunkAt(pos)) return Rejection.CHUNK_NOT_LOADED;

        BlockPos groundPos = pos.below();
        BlockState ground = level.getBlockState(groundPos);
        if (!ground.isFaceSturdy(level, groundPos, Direction.UP)) return Rejection.NO_SOLID_GROUND;

        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) {
            return Rejection.LIQUID;
        }
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return Rejection.FEET_BLOCKED;
        if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
            return Rejection.HEAD_BLOCKED;
        }

        if (level.hasNearbyAlivePlayer(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, MIN_PLAYER_DISTANCE)) {
            return Rejection.TOO_CLOSE_TO_PLAYER;
        }
        if (localDensity(level, pos) >= MAX_LOCAL_DENSITY) return Rejection.TOO_CROWDED;

        return null;
    }

    public static int localDensity(ServerLevel level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(DENSITY_RADIUS);
        return level.getEntitiesOfClass(
                com.seggellion.britannia_mod.entity.TownPersonEntity.class, area, Entity::isAlive).size();
    }

    private static int randomBetween(RandomSource random, int min, int max) {
        if (max <= min) return min;
        return min + random.nextInt(max - min + 1);
    }
}
