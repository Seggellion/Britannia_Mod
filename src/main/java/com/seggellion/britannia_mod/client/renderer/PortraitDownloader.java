package com.seggellion.britannia_mod.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PortraitDownloader {
    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static final ResourceLocation FALLBACK = ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/portraits/generic_peasant.png");
        private static final Logger LOGGER = LogManager.getLogger();

    // Base URL up to the portraits folder
    private static final String GCS_BASE_URL = "https://storage.googleapis.com/ultimacraft/portraits/";

    // NEW: Accept gender as a parameter
    public static ResourceLocation getPortrait(String npcName, String gender) {
        String sanitizedName = npcName.replace(" ", "_");
        String safeGender = (gender == null || gender.isEmpty()) ? "unknown" : gender.toLowerCase();
        
        // Create a unique cache key (e.g., "male_lord_british")
        String cacheKey = (safeGender + "_" + sanitizedName).toLowerCase();
        if (CACHE.containsKey(cacheKey)) {
            return CACHE.get(cacheKey);
        }

        CACHE.put(cacheKey, FALLBACK);

        CompletableFuture.runAsync(() -> {
            try {
                // NEW: Build the URL with the gender folder included
                URL url = new URL(GCS_BASE_URL + safeGender + "/" + sanitizedName + ".png");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                if (connection.getResponseCode() == 200) {

                        LOGGER.info("CONNECTION SUCECSS");
                    try (InputStream is = connection.getInputStream()) {
                        NativeImage nativeImage = NativeImage.read(is);
                        
                        Minecraft.getInstance().execute(() -> {
                            DynamicTexture texture = new DynamicTexture(nativeImage);
                            ResourceLocation dynamicLocation = ResourceLocation.fromNamespaceAndPath("britannia_mod", "downloaded_portrait_" + cacheKey);
                            Minecraft.getInstance().getTextureManager().register(dynamicLocation, texture);
                            
                            CACHE.put(cacheKey, dynamicLocation);
                        });
                    }
                }
            } catch (Exception e) {
                    LOGGER.info("Failed to download portrait? {}",  npcName);

                System.out.println("Failed to download portrait for: " + npcName + " from GCS. Using fallback.");
            }
        });

        return FALLBACK; 
    }
}