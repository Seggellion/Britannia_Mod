package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.client.RegionCache;
import com.seggellion.britannia_mod.util.RegionData;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Optional;

public final class FarmingClimateResolver {
    private FarmingClimateResolver() {
    }

    public static FarmingClimate resolve(Level level, BlockPos pos) {
        Optional<RegionData> region = findRegionAt(level, pos);
        if (region.isPresent()) {
            return FarmingClimate.fromRailsClimate(region.get().climate());
        }

        if (level.getBrightness(LightLayer.SKY, pos.above()) <= 3 || pos.getY() < level.getSeaLevel() - 12) {
            return FarmingClimate.UNDERGROUND;
        }

        var holder = level.getBiome(pos);
        Biome biome = holder.value();
        float temperature = biome.getBaseTemperature();
        float downfall = biome.getModifiedClimateSettings().downfall();

        if (holder.is(BiomeTags.IS_JUNGLE) || temperature >= 0.95f) {
            return FarmingClimate.TROPICAL;
        }
        if (holder.is(BiomeTags.IS_BADLANDS) || downfall <= 0.15f) {
            return FarmingClimate.ARID;
        }
        if (holder.is(BiomeTags.IS_TAIGA) || temperature <= 0.25f) {
            return FarmingClimate.ICE;
        }
        if (downfall >= 0.85f || isNearWater(level, pos)) {
            return FarmingClimate.WETLAND;
        }
        return FarmingClimate.TEMPERATE;
    }

    public static String resolveClimateName(Level level, BlockPos pos) {
        return resolve(level, pos).displayName();
    }

    public static Optional<RegionData> findRegionAt(Level level, BlockPos pos) {
        List<RegionData> regions = RegionCache.all();
        if (regions.isEmpty()) {
            return Optional.empty();
        }
        return regions.stream()
                .filter(region -> region.contains(pos))
                .findFirst();
    }

    public static String resolveRegionDebugReason(Level level, BlockPos pos) {
        List<RegionData> regions = RegionCache.all();
        if (regions.isEmpty()) {
            return "No server-side bootstrap regions are cached. " + RegionCache.lastStatus();
        }
        return findRegionAt(level, pos)
                .map(region -> "Matched Rails region '" + region.name + "' climate=" + region.climate())
                .orElse("No cached Rails region contains " + pos.toShortString() + "; using biome fallback.");
    }

    public static boolean isDarkOrMagical(Level level, BlockPos pos) {
        FarmingClimate climate = resolve(level, pos);
        return climate == FarmingClimate.UNDERGROUND
                || climate == FarmingClimate.UNDERGROUND_OR_DARK
                || level.getBrightness(LightLayer.BLOCK, pos.above()) >= 12;
    }

    private static boolean isNearWater(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 1; y++) {
                    mutable.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    if (level.getFluidState(mutable).is(net.minecraft.tags.FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
