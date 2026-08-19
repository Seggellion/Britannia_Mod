package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.winery.GrapeVariety;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;

import java.util.Set;

/**
 * Applies a planted grape variety's own agronomy to the grape crop.
 *
 * <p>The shard defines hundreds of grape varieties, each with its own nutrient targets, optimal
 * hydration, altitude band and climate. Without this they would all grow identically and differ only
 * in name and colour, which is exactly the distinction the catalogue exists to make: a Pinot Noir
 * that wants Y 150–578 and a lean nitrogen diet should not thrive wherever a table grape does.
 *
 * <p>Everything a variety does not describe stays a property of the crop — growth rate, yield,
 * harvest tooling, the three-block structure — because a variety refines a grape rather than
 * redefining one. Non-grape crops and unrecognised varieties pass straight through untouched, so
 * this cannot perturb the rest of the farming table.
 */
public final class GrapeVarietyAgronomy {
    private static final String GRAPE_CROP_ID = "grapes";

    private GrapeVarietyAgronomy() {
    }

    /** The crop a plot should actually be judged against, given what is planted in it. */
    public static CropDefinition effectiveCrop(CropDefinition crop, FarmingBlockEntity plot) {
        return plot == null ? crop : effectiveCrop(crop, plot.getStoredSeed());
    }

    public static CropDefinition effectiveCrop(CropDefinition crop, String varietyId) {
        if (crop == null || !GRAPE_CROP_ID.equals(crop.id()) || varietyId == null || varietyId.isBlank()) {
            return crop;
        }
        GrapeVariety variety = GrapeVarietyManager.getVarietyOrNull(varietyId);
        if (variety == null) {
            // An id the shard no longer publishes: keep the crop's own agronomy rather than
            // inventing requirements the player has no way to discover.
            return crop;
        }
        return crop.withGrapeVariety(
                variety.requiredNitrogen(),
                variety.requiredPhosphorus(),
                variety.requiredPotassium(),
                variety.requiredOrganicMatter(),
                hydrationIdeal(variety),
                variety.minAltitude(),
                variety.maxAltitude(),
                Set.of(FarmingClimate.fromRailsClimate(variety.climate())));
    }

    /**
     * Varieties record optimal hydration on the plot's own 0..{@link FarmingBlockEntity#MAX_HYDRATION}
     * scale, while crops carry it as a fraction. A variety that names a level outside the plot's
     * range is clamped rather than rejected, so a catalogue edit can never make a grape unplantable.
     */
    public static float hydrationIdeal(GrapeVariety variety) {
        float scaled = variety.optimalHydration() / (float) FarmingBlockEntity.MAX_HYDRATION;
        return Math.max(0.0f, Math.min(1.0f, scaled));
    }
}
