package com.seggellion.britannia_mod.population;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;

/**
 * Vendor/Trader Milestone 20: Rails' authoritative population decision for one
 * city, plus the city REGION bounds its TownPersons live within (owner decision
 * #12: TownPersons belong to the city region, never to a merchant or trader
 * spawn block). Minecraft never computes the desired number — it only converges
 * toward it, at the rate Rails allows.
 */
public record TownPersonPopulationPlan(
        UUID cityPublicId,
        String cityName,
        int desiredPopulation,
        int maxSpawnPerCycle,
        int maxDespawnPerCycle,
        List<Bounds> regions
) {
    /** An inclusive city region box. */
    public record Bounds(String name, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        public boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
    }

    public TownPersonPopulationPlan {
        regions = List.copyOf(regions);
        desiredPopulation = Math.max(0, desiredPopulation);
        maxSpawnPerCycle = Math.max(0, maxSpawnPerCycle);
        maxDespawnPerCycle = Math.max(0, maxDespawnPerCycle);
    }

    /** A city with no region has nowhere to place anyone: explicit, not hidden. */
    public boolean placeable() {
        return !regions.isEmpty();
    }

    public boolean containsPosition(BlockPos pos) {
        for (Bounds bounds : regions) {
            if (bounds.contains(pos)) return true;
        }
        return false;
    }
}
