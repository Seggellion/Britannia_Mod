package com.seggellion.britannia_mod.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.saveddata.SavedData;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;  // Import Collectors

public class PlayerDataManager extends SavedData {  // Extend SavedData
    private static final String DATA_NAME = "britannia_player_data";
    private final Map<UUID, PlayerData> playerDataMap = new HashMap<>();

    public PlayerDataManager() {
    }
    

    public static PlayerDataManager get(ServerLevel level) {
        SavedData.Factory<PlayerDataManager> factory = new SavedData.Factory<>(
            PlayerDataManager::new,   // Supplier<PlayerDataManager> constructor
            PlayerDataManager::load   // BiFunction<CompoundTag, HolderLookup.Provider, PlayerDataManager> deserializer
        );
        return level.getDataStorage().computeIfAbsent(factory, DATA_NAME);
    }

    public static PlayerDataManager load(CompoundTag tag, HolderLookup.Provider provider) {
        PlayerDataManager manager = new PlayerDataManager();
        // Load player data from tag
        // Implement deserialization logic here
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        // Save player data to tag
        // Implement serialization logic here
        return tag;
    }

    public List<PlayerData> getAllPlayers() {
    return new ArrayList<>(playerDataMap.values());
}


public void recordSale(ServerPlayer player, Map<String, Double> fishTypeContributions) {
    UUID playerUUID = player.getUUID();
    PlayerData data = playerDataMap.computeIfAbsent(playerUUID, k -> new PlayerData(playerUUID));

    for (Map.Entry<String, Double> entry : fishTypeContributions.entrySet()) {
        String fishType = entry.getKey();
        double weightContributed = entry.getValue();

        // Update the player's total contributions for this fishType
        data.addFishContribution(fishType, weightContributed);

        data.updateBiggestFish(fishType, weightContributed);

    }

    this.setDirty(); // If needed, mark data to be saved.
}


    private String getFishType(Item item) {
        if (item == Items.COD) return "cod";
        if (item == Items.SALMON) return "salmon";
        if (item == Items.TROPICAL_FISH) return "tropical_fish";
        return "unknown";
    }

    public List<PlayerData> getTopContributors(String fishType, int limit) {
        return playerDataMap.values().stream()
                .sorted((a, b) -> Double.compare(
                        b.getTotalContributions().getOrDefault(fishType, 0.0),
                        a.getTotalContributions().getOrDefault(fishType, 0.0)))
                .limit(limit)
                .collect(Collectors.toList());  // Import Collectors to resolve error
    }

    public List<PlayerData> getBiggestFishCatchers(String fishType, int limit) {
        return playerDataMap.values().stream()
                .sorted((a, b) -> Double.compare(
                        b.getBiggestFish().getOrDefault(fishType, 0.0),
                        a.getBiggestFish().getOrDefault(fishType, 0.0)))
                .limit(limit)
                .collect(Collectors.toList());  // Import Collectors to resolve error
    }
}