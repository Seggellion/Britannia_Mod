package com.seggellion.britannia_mod.util;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.HttpURLConnection;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OreVeinFetcher {
    private static final Logger LOGGER = LogManager.getLogger();

    public static class OreVein {
        public final String oreType;
        public final BlockPos position;
        public final int radius;
        public final String rotation;
        public final String region;

        public OreVein(String oreType, BlockPos position, int radius, String rotation, String region) {
            this.oreType = oreType;
            this.position = position;
            this.radius = radius;
            this.rotation = rotation;
            this.region = region;
        }

        public BlockPos getPosition() {
            return position;
        }
    }

    public static List<OreVein> fetchOreVeins(MinecraftServer server, String shardName) {
        List<OreVein> veins = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            var credentials = ServerAuthRegistry.credentials(server).orElseThrow();
            var requestUri = credentials.apiUrls().resolveQuery(
                    Endpoint.ORE_VEINS, Map.of("shard", credentials.shardName()));
            conn = (HttpURLConnection) requestUri.toURL().openConnection();
            BoundedHttp.configure(conn);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            if (!RailsRequestAuthenticator.apply(conn, server, new byte[0])) throw new IllegalStateException("Server authentication unavailable");

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                LOGGER.error("Failed to fetch ore veins. HTTP error code: {}", responseCode);
                return veins;
            }

            JsonArray jsonArray = JsonParser.parseString(
                    BoundedHttp.readUtf8(conn.getInputStream(), 1_048_576)).getAsJsonArray();
            for (JsonElement element : jsonArray) {
                JsonObject obj = element.getAsJsonObject();
                String oreType = obj.get("ore_type").getAsString();
                int x = obj.get("x").getAsInt();
                int y = obj.get("y").getAsInt();
                int z = obj.get("z").getAsInt();
                int radius = obj.get("radius").getAsInt();
                String rotation = obj.get("rotation").getAsString();
                String region = obj.get("region").getAsString();

                veins.add(new OreVein(oreType, new BlockPos(x, y, z), radius, rotation, region));
            }
        } catch (Exception e) {
            LOGGER.error("Exception while fetching ore veins: ", e);
        } finally {
            if (conn != null) conn.disconnect();
        }

        return veins;
    }
}
