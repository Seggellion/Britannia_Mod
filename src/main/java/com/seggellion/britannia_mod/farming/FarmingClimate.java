package com.seggellion.britannia_mod.farming;

import java.util.Locale;

public enum FarmingClimate {
    TEMPERATE("Temperate"),
    ICE("Ice"),
    FIRE("Fire"),
    WETLAND("Wetland"),
    TROPICAL("Tropical"),
    ARID("Arid"),
    MAGICAL("Magical"),
    UNDERGROUND("Underground"),
    COLD("Ice"),
    UNDERGROUND_OR_DARK("Underground");

    private final String displayName;

    FarmingClimate(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static FarmingClimate fromRailsClimate(String climate) {
        String normalized = normalize(climate);
        return switch (normalized) {
            case "ice", "cold", "arctic" -> ICE;
            case "fire", "hot", "volcanic" -> FIRE;
            case "wetland", "swamp" -> WETLAND;
            case "tropical", "jungle" -> TROPICAL;
            case "arid", "desert" -> ARID;
            case "magical", "magic" -> MAGICAL;
            case "underground", "underground_or_dark", "dark" -> UNDERGROUND;
            default -> TEMPERATE;
        };
    }

    private static String normalize(String climate) {
        if (climate == null || climate.isBlank()) {
            return "temperate";
        }
        return climate.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }
}
