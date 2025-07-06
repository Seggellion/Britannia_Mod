package com.seggellion.britannia_mod.network;

import com.google.gson.*;

import com.seggellion.britannia_mod.shop.Product;
import com.seggellion.britannia_mod.util.OLog;

import net.minecraft.resources.ResourceLocation;
import com.seggellion.britannia_mod.config.ModConfig;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class RailsCatalog {
  
   private static final Logger LOGGER = LogManager.getLogger();

    public static CompletableFuture<List<Product>>
           fetch(String city) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info("[RailsCatalog] LOADING!" );
                URL url = new URL(ModConfig.API_BASE_URL
                          + "products?npc_type=architect&city=" + city + "&shard="+ ModConfig.SHARD_NAME);
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("GET");
                /* token header identical to SendTransactionToAPI … */
                try (Reader r = new InputStreamReader(c.getInputStream(),
                                                      StandardCharsets.UTF_8)) {
                        LOGGER.info("[RailsCatalog] Reader! {}", r );
                    JsonArray arr = JsonParser.parseReader(r).getAsJsonArray();
                    List<Product> out = new ArrayList<>();
                                  LOGGER.info("[RailsCatalog] arr {}",  arr );

                    for (JsonElement e : arr) {
                        JsonObject o = e.getAsJsonObject();
                        out.add(new Product(
                            o.get("item_id").getAsString(),
                            o.get("item_name").getAsString(),
                            (int) Math.round(o.get("price").getAsDouble()),
                            ResourceLocation.parse(o.get("icon").getAsString())   // ← FIX
                    ));
                    }
                    return out;
                }
            } catch (Exception ex) {
                OLog.error(ex);
                return List.of();
            }
        });
    }
}
