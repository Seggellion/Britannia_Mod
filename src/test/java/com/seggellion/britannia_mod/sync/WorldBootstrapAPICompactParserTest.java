package com.seggellion.britannia_mod.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.server.http.CancellableHttpRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorldBootstrapAPICompactParserTest {
    @Test
    void serverBootstrapQueryUsesTheClosedCompactProfileWithoutCredentials() {
        Map<String, String> query = WorldBootstrapAPI.bootstrapQuery("player-uuid", "Dev +");

        assertEquals("minecraft_server", query.get("profile"));
        assertEquals("player-uuid", query.get("player_uuid"));
        assertEquals("player-uuid", query.get("minecraft_uuid"));
        assertEquals("Dev +", query.get("minecraft_username"));
        assertEquals(4, query.size());
        assertFalse(query.containsKey("shard_secret"));
    }

    @Test
    void compactCityWithoutCommoditiesRetainsIdentityMarketAndTreasuryData() {
        JsonObject city = cityJson();
        assertFalse(city.has("commodities"));

        WorldBootstrapAPI.CityBootstrapData parsed = WorldBootstrapAPI.parseCity(city);

        assertEquals("0de41982-7807-47ed-9802-4921e17f0d97", parsed.publicId());
        assertEquals("Britain", parsed.name());
        assertEquals(7.5, parsed.food());
        assertEquals(41, parsed.gold());
        assertEquals(12.5, parsed.weights().get("food").get("grain").get("wheat"));
        assertEquals(7, parsed.quantities().get("food").get("grain").get("wheat"));
    }

    @Test
    void fullCityWithCommoditiesRemainsParseableWithoutChangingRetainedFields() {
        JsonObject city = cityJson();
        city.add("commodities", JsonParser.parseString("[{\"item_name\":\"wheat\"}]").getAsJsonArray());

        WorldBootstrapAPI.CityBootstrapData parsed = WorldBootstrapAPI.parseCity(city);

        assertEquals("Britain", parsed.name());
        assertEquals(7, parsed.quantities().get("food").get("grain").get("wheat"));
    }

    @Test
    void malformedRetainedMarketFieldFailsBeforeAnyApplication() {
        JsonObject city = cityJson();
        city.addProperty("market_weights", "not-an-object");

        assertThrows(RuntimeException.class, () -> WorldBootstrapAPI.parseCity(city));
    }

    @Test
    void requestHandleCancellationIsIdempotentBeforeAndAfterAttach() throws IOException {
        WorldBootstrapAPI.RequestHandle beforeAttach = new WorldBootstrapAPI.RequestHandle();
        beforeAttach.cancel();
        CancellableHttpRequest preCancelled = new CancellableHttpRequest(
            URI.create("http://127.0.0.1/bootstrap"), 16);
        beforeAttach.attach(preCancelled);
        assertTrue(preCancelled.isCancelled());

        WorldBootstrapAPI.RequestHandle afterAttach = new WorldBootstrapAPI.RequestHandle();
        CancellableHttpRequest active = new CancellableHttpRequest(
            URI.create("http://127.0.0.1/bootstrap"), 16);
        afterAttach.attach(active);
        afterAttach.cancel();
        afterAttach.cancel();
        assertTrue(active.isCancelled());
    }

    private static JsonObject cityJson() {
        return JsonParser.parseString("""
            {
              "public_id": "0de41982-7807-47ed-9802-4921e17f0d97",
              "name": "Britain",
              "supplies": {
                "food": 7.5, "wood": 2, "metal": 3, "stone": 4,
                "textile": 5, "alcohol": 6, "technology": 7
              },
              "treasury": { "gold": 41, "silver": 23, "copper": 9 },
              "market_weights": { "food": { "grain": { "wheat": 12.5 } } },
              "market_quantities": { "food": { "grain": { "wheat": 7 } } },
              "npcs": [{ "npc_id": "banker-one" }]
            }
            """).getAsJsonObject();
    }
}
