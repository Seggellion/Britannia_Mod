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
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsParser;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsSnapshot;
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
                RailsRequestAuthenticator.apply(connection, credentials, new byte[0]);
            });

            if (response.status() == 304) {
                // Not Modified: the caller keeps every previously applied cache untouched.
                LOGGER.info("World bootstrap not modified for shard {}; retaining cached data.", shard);
                return WorldBootstrapData.failed("not_modified", shard, response.status(),
                        "HTTP 304 Not Modified; retaining existing cached bootstrap data.");
            }

            if (response.status() != 200) {
                String code = switch (response.status()) {
                    case 401, 403 -> "authentication_rejected";
                    case 404 -> "route_failure";
                    default -> "http_status_failure";
                };
                LOGGER.warn("World bootstrap failed code={} status={}", code, response.status());
                return WorldBootstrapData.failed(code, shard, response.status(),
                        "HTTP " + response.status() + " bootstrap failure; retained existing cached data.");
            }

                JsonObject root = JsonParser.parseString(
                        new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();

                // 1) Fish catalog (per-entry tolerant parsing)
                Map<ResourceLocation, FishCatalog.FishMeta> fishMap = parseFish(root);

                // 2) Regions (per-entry tolerant parsing; carries climate)
                List<RegionData> regions = parseRegions(root, shard);

                // 3-4) Player, city, quest, and Service NPC data are parsed as one
                // application unit. A malformed player field therefore cannot produce
                // a partially applicable bootstrap result.
                CoreBootstrapData core = parseCore(root);

                // Bootstrap has no per-server credential, so the parsed section spans
                // every Minecraft server on the shard; narrow it to this server's own
                // entries before it ever leaves this method.
                ServiceNpcAssignmentsSnapshot serviceNpcAssignments = core.serviceNpcAssignments()
                        .filteredForServer(credentials.minecraftServerKey().orElse(null));

                // 5) Grape Varieties (per-entry tolerant parsing)
                List<com.seggellion.britannia_mod.winery.GrapeVariety> grapesList = parseGrapes(root);

                // Return
                return new WorldBootstrapData(
                        fishMap,
                        regions,
                        core.shardUser(),
                        core.cities(),
                        core.acceptedQuests(),
                        core.serviceNpcRegistry(),
                        serviceNpcAssignments,
                        grapesList,
                        true,
                        null,
                        shard,
                        response.status(),
                        "Bootstrap success: fish=" + fishMap.size()
                                + ", regions=" + regions.size()
                                + ", cities=" + core.cities().size()
                                + ", grapes=" + grapesList.size()
                                + ", quests=" + core.acceptedQuests().size()
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
            ServiceNpcAssignmentsSnapshot serviceNpcAssignments,
            List<com.seggellion.britannia_mod.winery.GrapeVariety> grapes,
            boolean successful,
            String failureCode,
            String shard,
            int httpStatus,
            String status
    ) {
        public static WorldBootstrapData empty() {
            return failed("fetch_failed");
        }

        public static WorldBootstrapData failed(String failureCode) {
            return failed(failureCode, "<unknown>", -1, failureCode);
        }

        public static WorldBootstrapData failed(String failureCode, String shard, int httpStatus, String status) {
            return new WorldBootstrapData(
                    Map.of(),
                    List.of(),
                    null,
                    List.of(),
                    List.of(),
                    ServiceNpcRegistrySnapshot.empty(),
                    ServiceNpcAssignmentsSnapshot.empty(),
                    List.of(),
                    false,
                    failureCode,
                    shard,
                    httpStatus,
                    status
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
            ServiceNpcRegistrySnapshot serviceNpcRegistry,
            ServiceNpcAssignmentsSnapshot serviceNpcAssignments
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

        ServiceNpcAssignmentsParser.ParseResult serviceNpcAssignments =
                ServiceNpcAssignmentsParser.parseBootstrapRoot(root);
        if (serviceNpcAssignments.status() == ServiceNpcAssignmentsParser.ParseStatus.REJECTED) {
            LOGGER.warn("Rejected Service NPC assignments without rejecting unrelated bootstrap data: {}",
                    serviceNpcAssignments.error());
        }

        return new CoreBootstrapData(
                shardUser,
                List.copyOf(cities),
                List.copyOf(acceptedQuests),
                serviceNpcRegistry.snapshot(),
                serviceNpcAssignments.snapshot()
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

    // --- Tolerant catalog parsing (fish, regions, grapes): a malformed entry is
    // skipped with a warning instead of rejecting the whole bootstrap. Core data
    // (shard user, cities, quests, Service NPCs) intentionally stays atomic above.

    private static Map<ResourceLocation, FishCatalog.FishMeta> parseFish(JsonObject root) {
        Map<ResourceLocation, FishCatalog.FishMeta> fishMap = new HashMap<>();
        if (!hasArray(root, "fish")) {
            return fishMap;
        }

        for (JsonElement element : root.getAsJsonArray("fish")) {
            if (element == null || !element.isJsonObject()) {
                LOGGER.warn("Skipping malformed fish bootstrap entry: not an object.");
                continue;
            }

            try {
                JsonObject fish = element.getAsJsonObject();
                String keyString = getStringOrDefault(fish, "item_key", "");
                if (keyString.isBlank()) {
                    LOGGER.warn("Skipping malformed fish bootstrap entry: missing item_key.");
                    continue;
                }
                ResourceLocation key = ResourceLocation.parse(keyString);
                String name = getStringOrDefault(fish, "name", key.getPath());
                double minWeight = getDoubleOrDefault(fish, "min_weight", 0.0D);
                double maxWeight = getDoubleOrDefault(fish, "max_weight", minWeight);
                int minSkill = getIntOrDefault(fish, "min_skill", 0);
                int rarity = getIntOrDefault(fish, "rarity", 0);
                fishMap.put(key, new FishCatalog.FishMeta(name, minWeight, maxWeight, minSkill, rarity));
            } catch (Exception ex) {
                LOGGER.warn("Skipping malformed fish bootstrap entry: {}", ex.getMessage());
            }
        }
        return fishMap;
    }

    private static List<RegionData> parseRegions(JsonObject root, String shard) {
        List<RegionData> regions = new ArrayList<>();
        int rawRegionCount = hasArray(root, "regions") ? root.getAsJsonArray("regions").size() : 0;
        LOGGER.info("World bootstrap received {} raw regions for shard {}", rawRegionCount, shard);
        if (!hasArray(root, "regions")) {
            LOGGER.warn("World bootstrap response for shard {} did not include a regions array.", shard);
            return regions;
        }

        for (JsonElement element : root.getAsJsonArray("regions")) {
            if (element == null || !element.isJsonObject()) {
                LOGGER.warn("Skipping malformed region bootstrap entry: not an object.");
                continue;
            }

            try {
                JsonObject region = element.getAsJsonObject();
                OptionalInt minX = getRequiredInt(region, "min_x");
                OptionalInt maxX = getRequiredInt(region, "max_x");
                OptionalInt minY = getRequiredInt(region, "min_y");
                OptionalInt maxY = getRequiredInt(region, "max_y");
                OptionalInt minZ = getRequiredInt(region, "min_z");
                OptionalInt maxZ = getRequiredInt(region, "max_z");
                if (minX.isEmpty() || maxX.isEmpty() || minY.isEmpty() || maxY.isEmpty() || minZ.isEmpty() || maxZ.isEmpty()) {
                    LOGGER.warn("Skipping malformed region bootstrap entry: missing or invalid bounds.");
                    continue;
                }

                String name = getStringOrDefault(region, "name", "Unnamed Region");
                String climate = getStringOrDefault(region, "climate", "Temperate");
                List<RegionItemData> items = parseRegionItems(region);
                regions.add(new RegionData(
                        name,
                        climate,
                        minX.getAsInt(),
                        maxX.getAsInt(),
                        minY.getAsInt(),
                        maxY.getAsInt(),
                        minZ.getAsInt(),
                        maxZ.getAsInt(),
                        items
                ));
            } catch (Exception ex) {
                LOGGER.warn("Skipping malformed region bootstrap entry: {}", ex.getMessage());
            }
        }

        LOGGER.info("World bootstrap parsed {} regions for shard {}", regions.size(), shard);
        return regions;
    }

    private static List<RegionItemData> parseRegionItems(JsonObject region) {
        List<RegionItemData> items = new ArrayList<>();
        if (!hasArray(region, "items")) {
            return items;
        }

        for (JsonElement itemElement : region.getAsJsonArray("items")) {
            if (itemElement == null || !itemElement.isJsonObject()) {
                LOGGER.warn("Skipping malformed region item bootstrap entry: not an object.");
                continue;
            }

            try {
                JsonObject item = itemElement.getAsJsonObject();
                String type = getStringOrDefault(item, "type", "");
                String key = getStringOrDefault(item, "key", "");
                if (type.isBlank() || key.isBlank()) {
                    LOGGER.warn("Skipping malformed region item bootstrap entry: missing type or key.");
                    continue;
                }
                int weight = getIntOrDefault(item, "weight", 0);
                Integer minSkillOverride = item.has("min_skill_override") && !item.get("min_skill_override").isJsonNull()
                        ? getIntOrDefault(item, "min_skill_override", 0)
                        : null;
                int rarity = getIntOrDefault(item, "rarity", 0);
                items.add(new RegionItemData(type, key, weight, minSkillOverride, rarity));
            } catch (Exception ex) {
                LOGGER.warn("Skipping malformed region item bootstrap entry: {}", ex.getMessage());
            }
        }
        return items;
    }

    private static List<com.seggellion.britannia_mod.winery.GrapeVariety> parseGrapes(JsonObject root) {
        List<com.seggellion.britannia_mod.winery.GrapeVariety> grapesList = new ArrayList<>();
        if (!hasArray(root, "grapes")) {
            return grapesList;
        }

        for (JsonElement element : root.getAsJsonArray("grapes")) {
            if (element == null || !element.isJsonObject()) {
                LOGGER.warn("Skipping malformed grape bootstrap entry: not an object.");
                continue;
            }

            try {
                JsonObject grape = element.getAsJsonObject();
                String id = getStringOrDefault(grape, "id", "");
                if (id.isBlank()) {
                    LOGGER.warn("Skipping malformed grape bootstrap entry: missing id.");
                    continue;
                }
                String displayName = getStringOrDefault(grape, "display_name", id);
                int hydration = getIntOrDefault(grape, "optimal_hydration", 3);

                JsonObject chemistry = getObjectOrEmpty(grape, "chemistry");
                float n = getFloatOrDefault(chemistry, "n", 0.0f);
                float p = getFloatOrDefault(chemistry, "p", 0.0f);
                float k = getFloatOrDefault(chemistry, "k", 0.0f);
                float om = getFloatOrDefault(chemistry, "om", 0.0f);

                String climate = getStringOrDefault(grape, "climate",
                        getStringOrDefault(grape, "region", "Temperate"));

                JsonObject altitude = getObjectOrEmpty(grape, "altitude");
                int minAltitude = getIntOrDefault(altitude, "min", -64);
                int maxAltitude = getIntOrDefault(altitude, "max", 320);
                int color = parseColor(grape);
                int difficulty = getIntOrDefault(grape, "difficulty", 1);

                String colorString = getStringOrDefault(grape, "grape_color", "PURPLE");
                com.seggellion.britannia_mod.winery.GrapeColor grapeColor;
                try {
                    grapeColor = com.seggellion.britannia_mod.winery.GrapeColor.valueOf(colorString.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    LOGGER.warn("Invalid grape color enum for {}: {}", id, colorString);
                    grapeColor = com.seggellion.britannia_mod.winery.GrapeColor.PURPLE;
                }

                grapesList.add(new com.seggellion.britannia_mod.winery.GrapeVariety(
                        id, displayName, hydration, n, p, k, om, climate, minAltitude, maxAltitude, color, difficulty, grapeColor
                ));
            } catch (Exception ex) {
                LOGGER.warn("Skipping malformed grape bootstrap entry: {}", ex.getMessage());
            }
        }
        return grapesList;
    }

    private static int parseColor(JsonObject grape) {
        JsonElement colorElement = grape == null ? null : grape.get("base_color");
        if (colorElement == null || colorElement.isJsonNull() || !colorElement.isJsonPrimitive()) {
            return 0xFFFFFF;
        }

        try {
            JsonPrimitive primitive = colorElement.getAsJsonPrimitive();
            if (primitive.isString()) {
                return Integer.decode(primitive.getAsString());
            }
            return primitive.getAsInt();
        } catch (Exception ex) {
            LOGGER.warn("Invalid grape color value: {}", colorElement);
            return 0xFFFFFF;
        }
    }

    private static boolean hasObject(JsonObject obj, String key) {
        return obj != null
                && obj.has(key)
                && !obj.get(key).isJsonNull()
                && obj.get(key).isJsonObject();
    }

    private static boolean hasArray(JsonObject obj, String key) {
        return obj != null
                && obj.has(key)
                && !obj.get(key).isJsonNull()
                && obj.get(key).isJsonArray();
    }

    private static JsonObject getObjectOrEmpty(JsonObject obj, String key) {
        return hasObject(obj, key) ? obj.getAsJsonObject(key) : new JsonObject();
    }

    private static String getStringOrDefault(JsonObject obj, String key, String fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        try {
            return obj.get(key).getAsString();
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static int getIntOrDefault(JsonObject obj, String key, int fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        try {
            return obj.get(key).getAsInt();
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static OptionalInt getRequiredInt(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return OptionalInt.empty();
        try {
            return OptionalInt.of(obj.get(key).getAsInt());
        } catch (Exception ex) {
            return OptionalInt.empty();
        }
    }

    private static double getDoubleOrDefault(JsonObject obj, String key, double fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        try {
            return obj.get(key).getAsDouble();
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static float getFloatOrDefault(JsonObject obj, String key, float fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        try {
            return obj.get(key).getAsFloat();
        } catch (Exception ex) {
            return fallback;
        }
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
