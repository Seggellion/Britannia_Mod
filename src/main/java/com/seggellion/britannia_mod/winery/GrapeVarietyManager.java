package com.seggellion.britannia_mod.winery;

import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.List;

public class GrapeVarietyManager {
    private static final Map<String, GrapeVariety> VARIETIES = new HashMap<>();
    private static final GrapeVariety FALLBACK_VARIETY = new GrapeVariety(
        "wild_grape", "Wild Grape", 3,
        0.5f, 0.5f, 0.5f, 0.5f,
        "Temperate", 60, 100,
        0x333333, 1, GrapeColor.PURPLE
    );

    // Called on Mod startup - creates a fallback or remains empty
    public static void init() {
        // Optional: Add a "Wild Grape" fallback so the game doesn't crash if offline
        register(FALLBACK_VARIETY);
    }

    // === NEW: Called by WorldBootstrapAPI ===
    public static void loadFromBootstrap(List<GrapeVariety> newVarieties) {
        VARIETIES.clear();
        for (GrapeVariety v : newVarieties) {
            register(v);
        }
        if (!VARIETIES.containsKey(FALLBACK_VARIETY.id())) {
            register(FALLBACK_VARIETY);
        }
    }

    private static void register(GrapeVariety variety) {
        VARIETIES.put(variety.id(), variety);
    }
    
    public static GrapeVariety getVariety(String id) {
        return VARIETIES.getOrDefault(id, FALLBACK_VARIETY);
    }

    public static GrapeVariety getVarietyOrNull(String id) {
        return VARIETIES.get(id);
    }

    public static String getDefaultVarietyIdForColor(GrapeColor color) {
        if (color == null) {
            return FALLBACK_VARIETY.id();
        }

        for (GrapeVariety variety : VARIETIES.values()) {
            if (variety.colorType() == color) {
                return variety.id();
            }
        }

        if (isDarkGrapeColor(color)) {
            for (GrapeVariety variety : VARIETIES.values()) {
                if (isDarkGrapeColor(variety.colorType())) {
                    return variety.id();
                }
            }
        }

        return FALLBACK_VARIETY.id();
    }

    public static boolean isDarkGrapeColor(GrapeColor color) {
        if (color == null) {
            return false;
        }
        return switch (color) {
            case BLUE, DARK_PURPLE, PURPLE, RED -> true;
            default -> false;
        };
    }

    public static Collection<GrapeVariety> getAllVarieties() {
        return VARIETIES.values();
    }
}
