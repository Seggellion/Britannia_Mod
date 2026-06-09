package com.seggellion.britannia_mod.registry;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CityRegistry {
    private static final Map<String, List<AABB>> CITIES = new HashMap<>();

    static {
        registerCity("Britain",
            box(4962, 61, 4015, 5521, 96, 4385), // Main area
             box(5112, 61, 4015, 5409, 96, 3763), // blackthorns
            box(4874, 61, 4386, 5173, 96, 4495)  // Secondary area
        );
        registerCity("Jhelom",
            box(5414, 76, 8710, 4763, 66, 9286), // Main area
            box(5429, 86, 9347, 5075, 62, 9593), // south island
            box(4728, 76, 8508, 4291, 64, 8791)  // north island
        );
        registerCity("Serpent's Hold",
            box(8984, 110, 7917, 8524, 66, 8395) // Main area
        );
    }

    private static void registerCity(String cityName, AABB... areas) {
        CITIES.put(cityName, List.of(areas));
    }

    private static AABB box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new AABB(
            Math.min(x1, x2),
            Math.min(y1, y2),
            Math.min(z1, z2),
            Math.max(x1, x2),
            Math.max(y1, y2),
            Math.max(z1, z2)
        );
    }

    public static List<AABB> getCityAreas(String cityName) {
        return CITIES.getOrDefault(cityName, List.of());
    }

    public static Map<String, List<AABB>> getAllCities() {
        return Map.copyOf(CITIES);
    }

    public static List<AABB> getAllCityAreas() {
        List<AABB> areas = new ArrayList<>();

        for (List<AABB> cityAreas : CITIES.values()) {
            areas.addAll(cityAreas);
        }

        return List.copyOf(areas);
    }

    public static boolean isPlayerInAnyCity(Vec3 playerPos) {
        return getCityNameAt(playerPos) != null;
    }

    public static String getCityNameAt(Vec3 pos) {
        for (Map.Entry<String, List<AABB>> entry : CITIES.entrySet()) {
            for (AABB area : entry.getValue()) {
                if (area.contains(pos)) {
                    return entry.getKey();
                }
            }
        }

        return null;
    }
}