package com.seggellion.britannia_mod.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.component.WineData;
import com.seggellion.britannia_mod.config.ModConfig; // Assuming this exists based on context
import com.seggellion.britannia_mod.item.WineBottleItem;
import net.minecraft.world.item.ItemStack;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class WineryNetworkClient {

    private static final String BASE_URL = ModConfig.API_BASE_URL; // e.g., "http://localhost:3000/api/"
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Gson GSON = new Gson();

    // ========================================================================
    // DATA TRANSFER OBJECTS (Internal Records)
    // ========================================================================
    public record WineSellPayload(
        String winery_name,
        String grape_type,
        int year,
        String region,
        int quality_score,
        String owner_city_id // Optional: if you track which city the player is in
    ) {}

    public record TransactionResult(boolean success, int gold, int silver, int copper, String message) {}

    // ========================================================================
    // POST: SELL WINE
    // ========================================================================
    public static CompletableFuture<TransactionResult> sellWineBottle(ItemStack stack, String cityId) {
        WineData data = WineBottleItem.getWineData(stack);

        // 1. Serialize Data
        WineSellPayload payload = new WineSellPayload(
            data.wineryName(),
            data.grapeType(),
            data.year(),
            data.region(),
            data.quality(),
            cityId
        );

        String jsonBody = GSON.toJson(payload);

        // 2. Build Request
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "wines")) // Maps to POST /api/wines
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + getAuthToken()) // Helper to get token
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        // 3. Send Async
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() == 200) {
                    // Parse successful payout
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    JsonObject payout = json.getAsJsonObject("payout"); // Assuming Rails sends { payout: { gold: 1, ... } }
                    
                    int gold = payout.has("gold") ? payout.get("gold").getAsInt() : 0;
                    int silver = payout.has("silver") ? payout.get("silver").getAsInt() : 0;
                    int copper = payout.has("copper") ? payout.get("copper").getAsInt() : 0;
                    
                    return new TransactionResult(true, gold, silver, copper, "Wine sold successfully!");
                } else {
                    // Parse error message
                    return new TransactionResult(false, 0, 0, 0, "Market rejected the bottle: " + response.statusCode());
                }
            })
            .exceptionally(ex -> new TransactionResult(false, 0, 0, 0, "Connection failed: " + ex.getMessage()));
    }

    // ========================================================================
    // GET: BUYING CATALOG
    // ========================================================================
    public static CompletableFuture<String> getAvailableWines() {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "wines/available"))
            .header("Authorization", "Bearer " + getAuthToken())
            .GET()
            .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(HttpResponse::body);
    }

    // Helper to grab token (mimicking your existing CityAPITokenData logic)
    private static String getAuthToken() {
        // Implementation depends on where you store the token
        // return CityAPITokenData.getClientToken(); 
        return "placeholder_token"; 
    }
}