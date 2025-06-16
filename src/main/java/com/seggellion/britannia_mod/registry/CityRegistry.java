
package com.seggellion.britannia_mod.registry;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CityRegistry {
    private static final Map<String, List<AABB>> cities = new HashMap<>();

    static {
        // Define Britain city with multiple areas
        cities.put("Britain", List.of(
            new AABB(new Vec3(4962, 96, 4015), new Vec3(5521, 61, 4385)), // Main area
            new AABB(new Vec3(4874, 96, 4386), new Vec3(5173, 61, 4495))  // Adjusted secondary area
        ));
        // Add more cities as needed
        // cities.put("OtherCity", List.of(...));
    }

    public static List<AABB> getCityAreas(String cityName) {
        return cities.getOrDefault(cityName, List.of());
    }

    public static boolean isPlayerInAnyCity(Vec3 playerPos) {
        for (List<AABB> cityAreas : cities.values()) {
            for (AABB area : cityAreas) {
                if (area.contains(playerPos)) {
                    return true;
                }
            }
        }
        return false;
    }
}
