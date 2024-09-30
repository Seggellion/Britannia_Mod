// CityCoordinates.java
package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class CityCoordinates {

    // A map to store city names and their corresponding coordinates
    private static final Map<String, BlockPos> CITY_COORDINATES = new HashMap<>();

    static {
        // Initialize city names with coordinates
        CITY_COORDINATES.put("Britain", new BlockPos(5000, 70, 4869));
        CITY_COORDINATES.put("Moonglow", new BlockPos(12179, 70, 3212));
        CITY_COORDINATES.put("Yew", new BlockPos(3663, 117, 1941));
        CITY_COORDINATES.put("Minoc", new BlockPos(8225, 68, 1803));
        CITY_COORDINATES.put("Trinsic", new BlockPos(6171, 63, 7045));
        CITY_COORDINATES.put("Skara Brae", new BlockPos(3403, 70, 5077));
        CITY_COORDINATES.put("Jhelom", new BlockPos(5369, 70, 8946));
        CITY_COORDINATES.put("Magincia", new BlockPos(10124, 73, 5194));
    }

    // Method to get the coordinates of a city by name
    public static BlockPos getCityCoordinates(String cityName) {
        return CITY_COORDINATES.get(cityName);
    }
}
