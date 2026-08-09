package com.seggellion.britannia_mod.client;

import net.minecraft.core.BlockPos;
import com.seggellion.britannia_mod.util.RegionItemData;
import com.seggellion.britannia_mod.util.RegionData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Collections;

public class RegionCache {
    private static final List<RegionData> regions = new ArrayList<>();
    private static String lastShard = "<none>";
    private static String lastStatus = "No bootstrap response yet.";
    private static int lastHttpStatus = -1;
    private static int lastParsedRegionCount = 0;
    private static long lastUpdatedGameTime = -1L;

    public static void update(List<RegionData> newRegions) {
        update(newRegions, "<unknown>", -1, "Updated region cache.");
    }

    public static void update(List<RegionData> newRegions, String shard, int httpStatus, String status) {
        regions.clear();
        regions.addAll(newRegions == null ? List.of() : newRegions);
        lastShard = shard == null || shard.isBlank() ? "<unknown>" : shard;
        lastHttpStatus = httpStatus;
        lastParsedRegionCount = regions.size();
        lastStatus = status == null || status.isBlank() ? "Updated region cache." : status;
        lastUpdatedGameTime = System.currentTimeMillis();
    }

    public static void retainExisting(String shard, int httpStatus, String status) {
        lastShard = shard == null || shard.isBlank() ? "<unknown>" : shard;
        lastHttpStatus = httpStatus;
        lastStatus = status == null || status.isBlank() ? "Retained existing region cache." : status;
        lastUpdatedGameTime = System.currentTimeMillis();
    }

    public static Optional<RegionData> findRegion(BlockPos pos) {
        return regions.stream().filter(r -> r.contains(pos)).findFirst();
    }

    public static List<RegionItemData> itemsFor(BlockPos pos, String type) {
        return findRegion(pos)
                .map(r -> r.items.stream().filter(i -> i.type.equalsIgnoreCase(type)).toList())
                .orElse(List.of());
    }

    public static void clear() {
        regions.clear();
        lastStatus = "Region cache cleared.";
        lastParsedRegionCount = 0;
        lastUpdatedGameTime = System.currentTimeMillis();
    }

    public static List<RegionData> all() {
        return Collections.unmodifiableList(new ArrayList<>(regions));
    }

    public static int count() {
        return regions.size();
    }

    public static String lastShard() {
        return lastShard;
    }

    public static String lastStatus() {
        return lastStatus;
    }

    public static int lastHttpStatus() {
        return lastHttpStatus;
    }

    public static int lastParsedRegionCount() {
        return lastParsedRegionCount;
    }

    public static long lastUpdatedGameTime() {
        return lastUpdatedGameTime;
    }
}
