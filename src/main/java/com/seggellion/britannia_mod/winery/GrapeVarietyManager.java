package com.seggellion.britannia_mod.winery;

import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.List;

public class GrapeVarietyManager {
    private static final Map<String, GrapeVariety> VARIETIES = new HashMap<>();
    /**
     * The variety a grape falls back to whenever the shard has published none, mirroring the
     * catalogue's own Concord records. Concord is the mass-market grape: forgiving chemistry, a wide
     * altitude band and a cool climate, so a fallback plant behaves like something a player could
     * reasonably have grown rather than like a placeholder.
     */
    private static final GrapeVariety CONCORD_GREEN = new GrapeVariety(
        "concord_green", "Concord Green", 3,
        0.22f, 0.13f, 0.25f, 0.56f,
        "Cool", 25, 625,
        0xA8B85A, 4, GrapeColor.GREEN
    );

    private static final GrapeVariety CONCORD_RED = new GrapeVariety(
        "concord_red", "Concord Red", 3,
        0.22f, 0.13f, 0.25f, 0.56f,
        "Cool", 25, 625,
        0x87364F, 4, GrapeColor.RED
    );

    private static final GrapeVariety FALLBACK_VARIETY = CONCORD_GREEN;

    /** Both Concords, so an offline shard still offers more than a single colour. */
    private static final List<GrapeVariety> BUILT_IN_VARIETIES = List.of(CONCORD_GREEN, CONCORD_RED);

    // Called on Mod startup - seeds the built-in Concords so grapes work before any shard data lands
    public static void init() {
        BUILT_IN_VARIETIES.forEach(GrapeVarietyManager::register);
    }

    // === NEW: Called by WorldBootstrapAPI ===
    public static void loadFromBootstrap(List<GrapeVariety> newVarieties) {
        VARIETIES.clear();
        for (GrapeVariety v : newVarieties) {
            register(v);
        }
        // A shard that publishes its own Concords wins; otherwise the built-ins stay available so
        // there is always something for an unknown or missing variety to resolve to.
        for (GrapeVariety builtIn : BUILT_IN_VARIETIES) {
            if (!VARIETIES.containsKey(builtIn.id())) {
                register(builtIn);
            }
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
