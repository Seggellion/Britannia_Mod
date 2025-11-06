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

    public static void update(List<RegionData> newRegions) {
        regions.clear();
        regions.addAll(newRegions);
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
    }

    public static List<RegionData> all() {
        return Collections.unmodifiableList(new ArrayList<>(regions));
    }
}
