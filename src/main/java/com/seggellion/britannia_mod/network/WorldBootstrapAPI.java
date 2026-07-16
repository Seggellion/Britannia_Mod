package com.seggellion.britannia_mod.sync;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.util.FishCatalog;
import com.seggellion.britannia_mod.util.RegionData;
import com.seggellion.britannia_mod.util.RegionItemData;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.auth.ServerCredentials;
import com.seggellion.britannia_mod.server.http.CancellableHttpRequest;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;
import com.seggellion.britannia_mod.quest.QuestEntryParser;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryParser;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;

import com.mojang.authlib.GameProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldBootstrapAPI {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    public static final String BOOTSTRAP_PROFILE = "minecraft_server";

    public static WorldBootstrapData fetch(ServerPlayer player) {
        return fetch(player, new RequestHandle());
    }

    public static WorldBootstrapData fetch(ServerPlayer player, RequestHandle requestHandle) {
        try {
            Optional<ServerCredentials> configuredCredentials = ServerAuthRegistry.credentials(player.server);
            if (configuredCredentials.isEmpty()) {
                LOGGER.warn("Skipping world bootstrap because server authentication is unavailable");
                return WorldBootstrapData.empty();
            }
            ServerCredentials credentials = configuredCredentials.get();
            String shard = credentials.shardName();
            
            // 2. Resolve the authenticated Minecraft profile. Rails owns all identity linking.
            GameProfile profile = player.getGameProfile();
            UUID minecraftUuid = profile.getId();
            String playerName = profile.getName();
            if (minecraftUuid == null || playerName == null || playerName.isBlank()) {
                LOGGER.warn("Skipping world bootstrap for player with missing Minecraft profile data.");
                return WorldBootstrapData.empty();
            }

            String playerUuid = minecraftUuid.toString();

            var requestUri = credentials.apiUrls().resolve(
                    Endpoint.WORLD_BOOTSTRAP,
                    Map.of("shard", shard),
                    bootstrapQuery(playerUuid, playerName)
            );
            LOGGER.debug("Sending Rails request endpoint={}", Endpoint.WORLD_BOOTSTRAP.symbolicName());

            CancellableHttpRequest request = new CancellableHttpRequest(requestUri, MAX_RESPONSE_BYTES);
            requestHandle.attach(request);
            CancellableHttpRequest.Response response = request.execute(connection -> {
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                RailsRequestAuthenticator.apply(connection, credentials);
            });

            if (response.status() != 200) {
                String code = switch (response.status()) {
                    case 401, 403 -> "authentication_rejected";
                    case 404 -> "route_failure";
                    default -> "http_status_failure";
                };
                LOGGER.warn("World bootstrap failed code={} status={}", code, response.status());
                return WorldBootstrapData.failed(code);
            }

                JsonObject root = JsonParser.parseString(
                        new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();

                // 1) Fish catalog
                Map<ResourceLocation, FishCatalog.FishMeta> fishMap = new HashMap<>();
                if (root.has("fish") && root.get("fish").isJsonArray()) {
                    for (JsonElement el : root.getAsJsonArray("fish")) {
                        JsonObject f = el.getAsJsonObject();
                        String keyStr = f.get("item_key").getAsString();
                        ResourceLocation key = ResourceLocation.parse(keyStr);
                        String name = f.has("name") && !f.get("name").isJsonNull()
                                ? f.get("name").getAsString()
                                : key.getPath();
                        double minW = f.get("min_weight").getAsDouble();
                        double maxW = f.get("max_weight").getAsDouble();
                        int minSkill = f.has("min_skill") ? f.get("min_skill").getAsInt() : 0;
                        int rarity = f.has("rarity") ? f.get("rarity").getAsInt() : 0;
                        fishMap.put(key, new FishCatalog.FishMeta(name, minW, maxW, minSkill, rarity));
                    }
                }

                // 2) Regions
                List<RegionData> regions = new ArrayList<>();
                if (root.has("regions") && root.get("regions").isJsonArray()) {
                    for (JsonElement el : root.getAsJsonArray("regions")) {
                        JsonObject r = el.getAsJsonObject();
                        String name = r.get("name").getAsString();
                        int minX = r.get("min_x").getAsInt();
                        int maxX = r.get("max_x").getAsInt();
                        int minY = r.get("min_y").getAsInt();
                        int maxY = r.get("max_y").getAsInt();
                        int minZ = r.get("min_z").getAsInt();
                        int maxZ = r.get("max_z").getAsInt();

                        List<RegionItemData> items = new ArrayList<>();
                        if (r.has("items") && r.get("items").isJsonArray()) {
                            for (JsonElement ie : r.getAsJsonArray("items")) {
                                JsonObject io = ie.getAsJsonObject();
                                String type = io.get("type").getAsString();
                                String key = io.get("key").getAsString();
                                int weight = io.get("weight").getAsInt();
                                Integer minSkillOverride =
                                        io.has("min_skill_override") && !io.get("min_skill_override").isJsonNull()
                                                ? io.get("min_skill_override").getAsInt()
                                                : null;
                                int rarity = 0;
                                items.add(new RegionItemData(type, key, weight, minSkillOverride, rarity));
                            }
                        }
                        regions.add(new RegionData(name, minX, maxX, minY, maxY, minZ, maxZ, items));
                    }
                }

                // 3-4) Player, city, quest, and Service NPC data are parsed as one
                // application unit. A malformed player field therefore cannot produce
                // a partially applicable bootstrap result.
                CoreBootstrapData core = parseCore(root);

                // 5) Grape Varieties
                List<com.seggellion.britannia_mod.winery.GrapeVariety> grapesList = new ArrayList<>();
                if (root.has("grapes") && root.get("grapes").isJsonArray()) {
                    for (JsonElement el : root.getAsJsonArray("grapes")) {
                        JsonObject g = el.getAsJsonObject();
                        
                        String id = g.get("id").getAsString();
                        String displayName = g.get("display_name").getAsString();
                        int hydration = g.get("optimal_hydration").getAsInt();
                        
                        // Chemistry
                        JsonObject chem = g.getAsJsonObject("chemistry");
                        float n = chem.get("n").getAsFloat();
                        float p = chem.get("p").getAsFloat();
                        float k = chem.get("k").getAsFloat();
                        float om = chem.get("om").getAsFloat();

                        String region = g.has("region") ? g.get("region").getAsString() : "Temperate";
                        
                        // Altitude
                        JsonObject alt = g.getAsJsonObject("altitude");
                        int minAlt = alt.get("min").getAsInt();
                        int maxAlt = alt.get("max").getAsInt();

                        // --- FIX STARTS HERE ---
                        // Handle Hex Strings (0x...) or standard Integers
                        int color = 0xFFFFFF; // Default white
                        if (g.has("base_color")) {
                            JsonElement cEl = g.get("base_color");
                            if (cEl.getAsJsonPrimitive().isString()) {
                                try {
                                    // Integer.decode handles "0x", "#", and plain numbers automatically
                                    color = Integer.decode(cEl.getAsString());
                                } catch (NumberFormatException e) {
                                    LOGGER.warn("Invalid grape color hex: " + cEl.getAsString());
                                    color = 0xFFFFFF;
                                }
                            } else {
                                color = cEl.getAsInt();
                            }
                        }
                        // --- FIX ENDS HERE ---

                        int diff = g.get("difficulty").getAsInt();
                        
                        // Enum Parsing
                        String colorStr = g.has("grape_color") ? g.get("grape_color").getAsString() : "PURPLE";
                        com.seggellion.britannia_mod.winery.GrapeColor grapeColorEnum;
                        try {
                            grapeColorEnum = com.seggellion.britannia_mod.winery.GrapeColor.valueOf(colorStr.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            grapeColorEnum = com.seggellion.britannia_mod.winery.GrapeColor.PURPLE;
                        }

                        grapesList.add(new com.seggellion.britannia_mod.winery.GrapeVariety(
                            id, displayName, hydration, n, p, k, om, region, minAlt, maxAlt, color, diff, grapeColorEnum
                        ));
                    }
                }

                // Return
                return new WorldBootstrapData(
                        fishMap,
                        regions,
                        core.shardUser(),
                        core.cities(),
                        core.acceptedQuests(),
                        core.serviceNpcRegistry(),
                        grapesList,
                        true,
                        null
                );
        } catch (CancellableHttpRequest.RequestException classified) {
            String code = classified.code().safeCode();
            LOGGER.warn("World bootstrap failed code={}", code);
            return WorldBootstrapData.failed(code);
        } catch (BootstrapParseException malformed) {
            LOGGER.warn("World bootstrap malformed response: field={} expected={}",
                    malformed.field(), malformed.expected());
            return WorldBootstrapData.failed("malformed_response");
        } catch (JsonParseException | IllegalStateException malformed) {
            LOGGER.warn("World bootstrap failed code=malformed_response");
            return WorldBootstrapData.failed("malformed_response");
        } catch (Exception e) {
            LOGGER.error("World bootstrap failed code=unexpected_error", e);
            return WorldBootstrapData.failed("unexpected_error");
        }
    }

    // --- Data Records & Helpers ---

    public record WorldBootstrapData(
            Map<ResourceLocation, FishCatalog.FishMeta> fish,
            List<RegionData> regions, 
            ShardUserData shardUser,
            List<CityBootstrapData> cities,
            List<ClientQuestEntry> acceptedQuests,
            ServiceNpcRegistrySnapshot serviceNpcRegistry,
            List<com.seggellion.britannia_mod.winery.GrapeVariety> grapes,
            boolean successful,
            String failureCode
    ) {
        public static WorldBootstrapData empty() {
            return failed("fetch_failed");
        }

        public static WorldBootstrapData failed(String failureCode) {
            return new WorldBootstrapData(
                    Map.of(),
                    List.of(),
                    null,
                    List.of(),
                    List.of(),
                    ServiceNpcRegistrySnapshot.empty(),
                    List.of(),
                    false,
                    failureCode
            );
        }
    }

    public static final class RequestHandle {
        private final AtomicReference<CancellableHttpRequest> request = new AtomicReference<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();

        void attach(CancellableHttpRequest candidate) throws IOException {
            if (!request.compareAndSet(null, candidate)) {
                throw new IOException("bootstrap request handle is already attached");
            }
            if (cancelled.get()) candidate.cancel();
        }

        public void cancel() {
            cancelled.set(true);
            CancellableHttpRequest active = request.get();
            if (active != null) active.cancel();
        }

        public boolean isCancelled() {
            return cancelled.get();
        }
    }

    public record CityBootstrapData(
            String publicId,
            String name,
            double food, double wood, double metal, double stone, double textile, double alcohol, double tech,
            int gold, int silver, int copper,
            Map<String, Map<String, Map<String, Double>>> weights,
            Map<String, Map<String, Map<String, Integer>>> quantities
    ) {}

    record CoreBootstrapData(
            ShardUserData shardUser,
            List<CityBootstrapData> cities,
            List<ClientQuestEntry> acceptedQuests,
            ServiceNpcRegistrySnapshot serviceNpcRegistry
    ) {}

    static CoreBootstrapData parseCore(JsonObject root) {
        List<CityBootstrapData> cities = new ArrayList<>();
        if (root.has("cities") && root.get("cities").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("cities")) {
                cities.add(parseCity(element.getAsJsonObject()));
            }
        }

        ShardUserData shardUser = parseShardUser(root);
        List<ClientQuestEntry> acceptedQuests = QuestEntryParser.parseAcceptedQuests(root);
        ServiceNpcRegistryParser.ParseResult serviceNpcRegistry =
                ServiceNpcRegistryParser.parseBootstrapRoot(root);
        if (serviceNpcRegistry.status() == ServiceNpcRegistryParser.ParseStatus.REJECTED) {
            LOGGER.warn("Rejected Service NPC registry without rejecting unrelated bootstrap data: {}",
                    serviceNpcRegistry.error());
        }

        return new CoreBootstrapData(
                shardUser,
                List.copyOf(cities),
                List.copyOf(acceptedQuests),
                serviceNpcRegistry.snapshot()
        );
    }

    static CityBootstrapData parseCity(JsonObject city) {
        String publicId = city.has("public_id") && !city.get("public_id").isJsonNull()
                ? city.get("public_id").getAsString()
                : null;
        String name = city.get("name").getAsString();

        JsonObject supplies = city.getAsJsonObject("supplies");
        double food = supplies.get("food").getAsDouble();
        double wood = supplies.get("wood").getAsDouble();
        double metal = supplies.get("metal").getAsDouble();
        double stone = supplies.get("stone").getAsDouble();
        double textile = supplies.get("textile").getAsDouble();
        double alcohol = supplies.get("alcohol").getAsDouble();
        double technology = supplies.get("technology").getAsDouble();

        JsonObject treasury = city.getAsJsonObject("treasury");
        int gold = treasury.get("gold").getAsInt();
        int silver = treasury.get("silver").getAsInt();
        int copper = treasury.get("copper").getAsInt();

        return new CityBootstrapData(
                publicId, name, food, wood, metal, stone, textile, alcohol, technology,
                gold, silver, copper,
                parseWeights(city.getAsJsonObject("market_weights")),
                parseQuantities(city.getAsJsonObject("market_quantities"))
        );
    }

    static Map<String, String> bootstrapQuery(String playerUuid, String playerName) {
        return Map.of(
                "player_uuid", playerUuid,
                "minecraft_uuid", playerUuid,
                "minecraft_username", playerName,
                "profile", BOOTSTRAP_PROFILE
        );
    }

    static ShardUserData parseShardUser(JsonObject root) {
        JsonObject shardUser = optionalObject(root, "shard_user", "shard_user");
        if (shardUser == null) return null;

        return new ShardUserData(
                requiredString(shardUser, "gender", "shard_user.gender"),
                requiredInt(shardUser, "fame", "shard_user.fame"),
                requiredInt(shardUser, "karma", "shard_user.karma"),
                requiredInt(shardUser, "murder_count", "shard_user.murder_count"),
                objectOrEmpty(shardUser, "inventory", "shard_user.inventory"),
                objectOrEmpty(shardUser, "stats", "shard_user.stats")
        );
    }

    private static JsonObject objectOrEmpty(JsonObject parent, String key, String field) {
        JsonObject value = optionalObject(parent, key, field);
        return value == null ? new JsonObject() : value;
    }

    private static JsonObject optionalObject(JsonObject parent, String key, String field) {
        JsonElement value = parent.get(key);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonObject()) throw new BootstrapParseException(field, "object_or_null");
        return value.getAsJsonObject();
    }

    private static String requiredString(JsonObject parent, String key, String field) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new BootstrapParseException(field, "string");
        }
        return value.getAsString();
    }

    private static int requiredInt(JsonObject parent, String key, String field) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new BootstrapParseException(field, "integer");
        }
        try {
            return value.getAsInt();
        } catch (NumberFormatException invalidNumber) {
            throw new BootstrapParseException(field, "integer");
        }
    }

    static final class BootstrapParseException extends RuntimeException {
        private final String field;
        private final String expected;

        BootstrapParseException(String field, String expected) {
            super("field=" + field + " expected=" + expected);
            this.field = field;
            this.expected = expected;
        }

        String field() {
            return field;
        }

        String expected() {
            return expected;
        }
    }

    public record ShardUserData(
            String gender,
            int fame,
            int karma,
            int murderCount,
            JsonObject inventory,
            JsonObject stats
    ) {}

    private static Map<String, Map<String, Map<String, Double>>> parseWeights(JsonObject obj) {
        Map<String, Map<String, Map<String, Double>>> result = new HashMap<>();
        if (obj == null) return result;
        for (String cat : obj.keySet()) {
            Map<String, Map<String, Double>> subMap = new HashMap<>();
            JsonObject catObj = obj.getAsJsonObject(cat);
            for (String sub : catObj.keySet()) {
                Map<String, Double> itemMap = new HashMap<>();
                JsonObject subObj = catObj.getAsJsonObject(sub);
                for (String item : subObj.keySet()) {
                    itemMap.put(item, subObj.get(item).getAsDouble());
                }
                subMap.put(sub, itemMap);
            }
            result.put(cat, subMap);
        }
        return result;
    }
    
    private static Map<String, Map<String, Map<String, Integer>>> parseQuantities(JsonObject obj) {
        Map<String, Map<String, Map<String, Integer>>> result = new HashMap<>();
        if (obj == null) return result;
        for (String cat : obj.keySet()) {
            Map<String, Map<String, Integer>> subMap = new HashMap<>();
            JsonObject catObj = obj.getAsJsonObject(cat);
            for (String sub : catObj.keySet()) {
                Map<String, Integer> itemMap = new HashMap<>();
                JsonObject subObj = catObj.getAsJsonObject(sub);
                for (String item : subObj.keySet()) {
                    itemMap.put(item, subObj.get(item).getAsInt());
                }
                subMap.put(sub, itemMap);
            }
            result.put(cat, subMap);
        }
        return result;
    }
}
