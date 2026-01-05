package com.seggellion.britannia_mod.winery;

import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.List;

public class GrapeVarietyManager {
    private static final Map<String, GrapeVariety> VARIETIES = new HashMap<>();

    // Called on Mod startup - creates a fallback or remains empty
    public static void init() {
        // Optional: Add a "Wild Grape" fallback so the game doesn't crash if offline
        register(new GrapeVariety(
            "wild_grape", "Wild Grape", 3, 
            0.5f, 0.5f, 0.5f, 0.5f, 
            "Temperate", 60, 100, 
            0x333333, 1, GrapeColor.PURPLE
        ));
    }

    // === NEW: Called by WorldBootstrapAPI ===
    public static void loadFromBootstrap(List<GrapeVariety> newVarieties) {
        VARIETIES.clear();
        for (GrapeVariety v : newVarieties) {
            register(v);
        }
        // System.out.println("Loaded " + VARIETIES.size() + " grape varieties from Rails.");
    }

    private static void register(GrapeVariety variety) {
        VARIETIES.put(variety.id(), variety);
    }
    
    public static GrapeVariety getVariety(String id) {
        return VARIETIES.getOrDefault(id, VARIETIES.get("wild_grape"));
    }

    public static Collection<GrapeVariety> getAllVarieties() {
        return VARIETIES.values();
    }
}