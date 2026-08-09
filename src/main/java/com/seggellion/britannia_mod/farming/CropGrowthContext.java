package com.seggellion.britannia_mod.farming;

public record CropGrowthContext(
        float nutrientFit,
        float hydrationFit,
        float climateFit,
        FarmingClimate climate,
        boolean climateAllowed,
        boolean altitudeAllowed,
        boolean specialEnvironmentAllowed,
        boolean requiresDarknessOrUnderground,
        boolean darkEnough,
        boolean underground,
        int skyLight,
        int blockLight,
        boolean latticeSatisfied,
        boolean idealGrowth,
        float farmingSkill
) {
    public CropGrowthContext(
            float nutrientFit,
            float hydrationFit,
            float climateFit,
            FarmingClimate climate,
            boolean climateAllowed,
            boolean altitudeAllowed,
            boolean latticeSatisfied,
            boolean idealGrowth,
            float farmingSkill
    ) {
        this(nutrientFit, hydrationFit, climateFit, climate, climateAllowed, altitudeAllowed,
                true, false, false, false, 0, 0, latticeSatisfied, idealGrowth, farmingSkill);
    }

    public static CropGrowthContext neutral(float farmingSkill) {
        return new CropGrowthContext(0.50f, 0.50f, 0.50f, FarmingClimate.TEMPERATE, true, true, true, false, farmingSkill);
    }
}
