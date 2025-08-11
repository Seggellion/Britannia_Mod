package com.seggellion.britannia_mod.util;

public class RegionItemData implements WeightedPicker.HasWeight {
    public final String type;     // "fish"
    public final String key;      // namespaced id "mod:fish"
    public final int weight;      // spawn weight
    public final Integer minSkillOverride; // nullable

    public RegionItemData(String type, String key, int weight, Integer minSkillOverride) {
        this.type = type;
        this.key = key;
        this.weight = weight;
        this.minSkillOverride = minSkillOverride;
    }

        @Override
    public int weight() {
        return weight;
    }
}