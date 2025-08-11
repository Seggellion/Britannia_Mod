// FishCatalog.java
package com.seggellion.britannia_mod.util;

import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class FishCatalog {
    public static final class FishMeta {
        public final String name;
        public final double minWeight, maxWeight;
        public final int minSkill;
        public FishMeta(String name, double minWeight, double maxWeight, int minSkill) {
            this.name = name;
            this.minWeight = minWeight;
            this.maxWeight = maxWeight;
            this.minSkill = minSkill;
        }
    }

    private static final Map<ResourceLocation, FishMeta> BY_KEY = new HashMap<>();

    public static void clear() { BY_KEY.clear(); }
    public static void put(ResourceLocation key, FishMeta meta) { BY_KEY.put(key, meta); }
    public static FishMeta get(ResourceLocation key) { return BY_KEY.get(key); }

    public static Map<ResourceLocation, FishMeta> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(BY_KEY));
    }

}
