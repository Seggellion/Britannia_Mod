package com.seggellion.britannia_mod.player;

import net.minecraft.nbt.CompoundTag;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Optional; 
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;



/**
 * Example PlayerData that stores:
 * 1) Overall totalContributions + biggestFish data (original).
 * 2) City-based commodity contributions (NEW).
 */
public class PlayerData {
    private final UUID playerUUID;

    // Original: totalContributions & biggestFish
    private final Map<String, Double> totalContributions = new HashMap<>();
    private final Map<String, Double> biggestFish = new HashMap<>();
    private String playerName = "Avatar"; // or some default

    // NEW: City-based contributions
    // Map<cityName, (Map<commodityName, Double totalSold)>
    private final Map<String, Map<String, Double>> cityContributions = new HashMap<>();
    // Optional: If you also want "biggest item" per city and commodity:
    private final Map<String, Map<String, Double>> cityBiggest = new HashMap<>();


    // === NEW: shard_user fields ===
    private String gender = "female";
    private int fame = 0;
    private int karma = 0;
    private int murderCount = 0;
    // Store as JSON (string). Safer than ad-hoc NBT mapping for nested structures.
    private String inventoryJson = "{}";
    private String statsJson = "{}";

    // --- NEW: getters ---
    public String getGender() { return gender; }
    public int getFame() { return fame; }
    public int getKarma() { return karma; }
    public int getMurderCount() { return murderCount; }
    public JsonObject getInventory() { return JsonParser.parseString(inventoryJson).getAsJsonObject(); }
    public JsonObject getStats() { return JsonParser.parseString(statsJson).getAsJsonObject(); }

    // --- NEW: setters / sync method ---
    public void syncFromShardUser(String gender, int fame, int karma, int murderCount,
                                  JsonObject inventory, JsonObject stats) {
        if (gender != null && !gender.isBlank()) this.gender = gender;
        this.fame = fame;
        this.karma = karma;
        this.murderCount = murderCount;
        this.inventoryJson = (inventory != null) ? inventory.toString() : "{}";
        this.statsJson = (stats != null) ? stats.toString() : "{}";
    }

    // Convenience overload if you pass the record directly
    public void syncFromShardUser(com.seggellion.britannia_mod.sync.WorldBootstrapAPI.ShardUserData su) {
        if (su == null) return;
        syncFromShardUser(
            su.gender(), su.fame(), su.karma(), su.murderCount(),
            su.inventory(), su.stats()
        );
    }



    public static String getPlayerName(ServerLevel serverLevel, UUID playerUUID) {
    // Try to find an online player first
    PlayerList playerList = serverLevel.getServer().getPlayerList();
    ServerPlayer onlinePlayer = playerList.getPlayer(playerUUID);
    if (onlinePlayer != null) {
        return onlinePlayer.getGameProfile().getName();
    }

    // If the player is not online, attempt to resolve their name via the GameProfile cache
    Optional<GameProfile> profile = serverLevel.getServer().getProfileCache().get(playerUUID);
    return profile.map(GameProfile::getName).orElse("Avatar");
}


    public void setPlayerName(String name) {
        this.playerName = name;
    }

    public PlayerData(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }

    // ---------------------------------------------------------------------------------
    // 1) ORIGINAL METHODS: Overall "fish" contributions + biggest fish
    // ---------------------------------------------------------------------------------

    /**
     * @param fishType   e.g. "cod", "salmon"
     * @param amount     total weight contributed
     */
    public void addFishContribution(String fishType, double amount) {
        totalContributions.merge(fishType, amount, Double::sum);
    }

    public Map<String, Double> getTotalContributions() {
        return totalContributions;
    }

    public Map<String, Double> getBiggestFish() {
        return biggestFish;
    }

    public void updateBiggestFish(String fishType, double weight) {
        biggestFish.compute(fishType, (type, currentMax) ->
            (currentMax == null || weight > currentMax) ? weight : currentMax
        );
    }

    // ---------------------------------------------------------------------------------
    // 2) NEW METHODS: City-based contributions
    // ---------------------------------------------------------------------------------

    /**
     * Add a commodity contribution for a specific city.
     * E.g. cityName="Britain", commodity="oak_wood", amount=12.4 (stones).
     */
    public void addCityContribution(String cityName, String commodity, double amount) {
        // cityContributions[cityName][commodity] += amount
        cityContributions
            .computeIfAbsent(cityName, k -> new HashMap<>())
            .merge(commodity, amount, Double::sum);
    }

    /**
     * Update biggest single item for a specific city & commodity.
     * If you want to track "largest fish in Britain" or "heaviest oak log in Britain."
     */
    public void updateCityBiggest(String cityName, String commodity, double weight) {
        cityBiggest
            .computeIfAbsent(cityName, k -> new HashMap<>())
            .compute(commodity, (k, currentMax) ->
                (currentMax == null || weight > currentMax) ? weight : currentMax
            );
    }

    /**
     * @return The total city-based contribution for a given commodity.
     *   If either city or commodity is not found, returns 0.0
     */
    public double getCityContribution(String cityName, String commodity) {
        Map<String, Double> commodities = cityContributions.get(cityName);
        if (commodities == null) return 0.0;
        return commodities.getOrDefault(commodity, 0.0);
    }

    /**
     * @return The biggest single piece for this city & commodity, or 0.0 if not found.
     */
    public double getCityBiggest(String cityName, String commodity) {
        Map<String, Double> commodities = cityBiggest.get(cityName);
        if (commodities == null) return 0.0;
        return commodities.getOrDefault(commodity, 0.0);
    }

    // Example: Possibly retrieve all city contributions, if needed
    public Map<String, Map<String, Double>> getCityContributions() {
        return cityContributions;
    }

    public Map<String, Map<String, Double>> getCityBiggest() {
        return cityBiggest;
    }

    // ---------------------------------------------------------------------------------
    // 3) PERSISTENCE: Save & load from NBT
    // ---------------------------------------------------------------------------------

    /**
     * Save data to the provided NBT tag.
     */
    public void save(CompoundTag tag) {
        // 3.1) Original fields
        CompoundTag contributionsTag = new CompoundTag();
        totalContributions.forEach(contributionsTag::putDouble);
        tag.put("TotalContributions", contributionsTag);

        CompoundTag biggestFishTag = new CompoundTag();
        biggestFish.forEach(biggestFishTag::putDouble);
        tag.put("BiggestFish", biggestFishTag);

        tag.putString("UUID", playerUUID.toString());

       CompoundTag su = new CompoundTag();
        su.putString("Gender", gender);
        su.putInt("Fame", fame);
        su.putInt("Karma", karma);
        su.putInt("MurderCount", murderCount);
        su.putString("InventoryJson", inventoryJson);
        su.putString("StatsJson", statsJson);
        tag.put("ShardUser", su);

        // 3.2) NEW: cityContributions
        // We'll store as: "CityContributions" -> cityName -> commodityMap -> commodityName -> double
        CompoundTag cityContribTag = new CompoundTag();
        for (Map.Entry<String, Map<String, Double>> cityEntry : cityContributions.entrySet()) {
            String cName = cityEntry.getKey();
            CompoundTag commodityMapTag = new CompoundTag();
            for (Map.Entry<String, Double> commodityEntry : cityEntry.getValue().entrySet()) {
                commodityMapTag.putDouble(commodityEntry.getKey(), commodityEntry.getValue());
            }
            cityContribTag.put(cName, commodityMapTag);
        }
        tag.put("CityContributions", cityContribTag);

        // 3.3) NEW: cityBiggest
        CompoundTag cityBiggestTag = new CompoundTag();
        for (Map.Entry<String, Map<String, Double>> cityEntry : cityBiggest.entrySet()) {
            String cName = cityEntry.getKey();
            CompoundTag commodityMapTag = new CompoundTag();
            for (Map.Entry<String, Double> commodityEntry : cityEntry.getValue().entrySet()) {
                commodityMapTag.putDouble(commodityEntry.getKey(), commodityEntry.getValue());
            }
            cityBiggestTag.put(cName, commodityMapTag);
        }
        tag.put("CityBiggest", cityBiggestTag);
    }

    /**
     * Load data from the NBT tag.
     */
    public static PlayerData load(CompoundTag tag) {
        UUID uuid = UUID.fromString(tag.getString("UUID"));
        PlayerData data = new PlayerData(uuid);

  // NEW: shard_user
        if (tag.contains("ShardUser")) {
            CompoundTag su = tag.getCompound("ShardUser");
            data.gender = su.getString("Gender");
            data.fame = su.getInt("Fame");
            data.karma = su.getInt("Karma");
            data.murderCount = su.getInt("MurderCount");
            data.inventoryJson = su.getString("InventoryJson");
            if (data.inventoryJson == null || data.inventoryJson.isBlank()) data.inventoryJson = "{}";
            data.statsJson = su.getString("StatsJson");
            if (data.statsJson == null || data.statsJson.isBlank()) data.statsJson = "{}";
        }

        // Original overall fields
        CompoundTag contributionsTag = tag.getCompound("TotalContributions");
        for (String fishType : contributionsTag.getAllKeys()) {
            data.totalContributions.put(fishType, contributionsTag.getDouble(fishType));
        }

        CompoundTag biggestFishTag = tag.getCompound("BiggestFish");
        for (String fishType : biggestFishTag.getAllKeys()) {
            data.biggestFish.put(fishType, biggestFishTag.getDouble(fishType));
        }

        // NEW: cityContributions
        if (tag.contains("CityContributions")) {
            CompoundTag cityContribTag = tag.getCompound("CityContributions");
            for (String cityName : cityContribTag.getAllKeys()) {
                CompoundTag commodityMapTag = cityContribTag.getCompound(cityName);
                Map<String, Double> commodityMap = new HashMap<>();
                for (String commodityName : commodityMapTag.getAllKeys()) {
                    double amount = commodityMapTag.getDouble(commodityName);
                    commodityMap.put(commodityName, amount);
                }
                data.cityContributions.put(cityName, commodityMap);
            }
        }

        // NEW: cityBiggest
        if (tag.contains("CityBiggest")) {
            CompoundTag cityBiggestTag = tag.getCompound("CityBiggest");
            for (String cityName : cityBiggestTag.getAllKeys()) {
                CompoundTag commodityMapTag = cityBiggestTag.getCompound(cityName);
                Map<String, Double> commodityMap = new HashMap<>();
                for (String commodityName : commodityMapTag.getAllKeys()) {
                    double amount = commodityMapTag.getDouble(commodityName);
                    commodityMap.put(commodityName, amount);
                }
                data.cityBiggest.put(cityName, commodityMap);
            }
        }

        return data;
    }

    public String inventoryOneLine() {
        String s = inventoryJson != null ? inventoryJson : "{}";
        return s.length() > 200 ? s.substring(0, 197) + "…" : s;
    }
    public String statsOneLine() {
        String s = statsJson != null ? statsJson : "{}";
        return s.length() > 200 ? s.substring(0, 197) + "…" : s;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }
}
