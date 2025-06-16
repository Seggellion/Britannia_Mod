package com.seggellion.britannia_mod.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class OreVeinLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESOURCE_PATH = "/data/britannia_mod/ore_veins.json"; // ✅ New path inside JAR
    private static final Gson GSON = new Gson();
    private static List<OreVein> oreVeins = new ArrayList<>();

        public static void loadOreVeins() {
            LOGGER.info("Initializing OreVeinLoader");

            try (InputStream inputStream = OreVeinLoader.class.getResourceAsStream(RESOURCE_PATH)) {
                if (inputStream == null) {
                    LOGGER.info("Could not find ore_veins.json in resources! Using default values.");
                    oreVeins = getDefaultOreVeins();
                    return;
                }
            try (InputStreamReader reader = new InputStreamReader(inputStream)) {
                Type listType = new TypeToken<List<OreVein>>() {}.getType();
                oreVeins = GSON.fromJson(reader, listType);

                LOGGER.info("Loaded {} ore veins.", oreVeins.size());

                // ✅ Log the loaded ores
                for (OreVein vein : oreVeins) {
                    LOGGER.info("Ore Vein Loaded: {} at {} radius {}", vein.oreType, vein.getPosition(), vein.radius);
                }
            }
            } catch (IOException e) {
                LOGGER.error("Failed to load ore veins: {}", e.getMessage());
                oreVeins = getDefaultOreVeins();
            }

        }


    private static List<OreVein> getDefaultOreVeins() {
        return List.of(
            new OreVein("copper", 100, 64, -200, 5, "XZ"),
            new OreVein("iron", -50, 72, 300, 7, "XZ"),
            new OreVein("gold", 250, 40, -100, 4, "XZ"),
            new OreVein("silver", -200, 55, 150, 6, "XZ"),
            new OreVein("verite", 350, 30, -400, 3, "XZ"),
            new OreVein("valorite", 500, 20, 500, 8, "XZ")
        );
    }

    public static List<OreVein> getOreVeins() {
        return oreVeins;
    }

    public static class OreVein {
        public String oreType;
        public int x, y, z, radius;
        public String rotation;

        public OreVein(String oreType, int x, int y, int z, int radius, String rotation) {
            this.oreType = oreType;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.rotation = rotation != null ? rotation : "XZ";
        }

        public BlockPos getPosition() {
            return new BlockPos(x, y, z);
        }
    }
}
