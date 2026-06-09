package com.seggellion.britannia_mod.trader;

public record TraderSpawnSettings(
        int defaultSpawnRadius,
        int homeRestrictionRadius,
        int maxMerchants
) {
    public static TraderSpawnSettings standard() {
        return new TraderSpawnSettings(5, 2, 1);
    }

    public static TraderSpawnSettings stationary() {
        return new TraderSpawnSettings(5, 1, 1);
    }
}
