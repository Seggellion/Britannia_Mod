package com.seggellion.britannia_mod.player;

import net.minecraft.nbt.CompoundTag;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerData {
    private final UUID playerUUID;
    private final Map<String, Double> totalContributions = new HashMap<>();
    private final Map<String, Double> biggestFish = new HashMap<>();

    public PlayerData(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }


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
        biggestFish.compute(fishType, (type, currentMax) -> (currentMax == null || weight > currentMax) ? weight : currentMax);
    }

    public void save(CompoundTag tag) {
        CompoundTag contributionsTag = new CompoundTag();
        totalContributions.forEach(contributionsTag::putDouble);
        tag.put("TotalContributions", contributionsTag);

        CompoundTag biggestFishTag = new CompoundTag();
        biggestFish.forEach(biggestFishTag::putDouble);
        tag.put("BiggestFish", biggestFishTag);

        tag.putString("UUID", playerUUID.toString());
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public static PlayerData load(CompoundTag tag) {
        UUID uuid = UUID.fromString(tag.getString("UUID"));
        PlayerData data = new PlayerData(uuid);

        CompoundTag contributionsTag = tag.getCompound("TotalContributions");
        for (String fishType : contributionsTag.getAllKeys()) {
            data.totalContributions.put(fishType, contributionsTag.getDouble(fishType));
        }

        CompoundTag biggestFishTag = tag.getCompound("BiggestFish");
        for (String fishType : biggestFishTag.getAllKeys()) {
            data.biggestFish.put(fishType, biggestFishTag.getDouble(fishType));
        }

        return data;
    }
}
