package com.seggellion.britannia_mod.util;

public class RegionItemData implements WeightedPicker.HasWeight {
    public final String type;   // "fish", "mushroom", etc.
    public final String key;    // namespaced ID: britannia_mod:trout
    public final int weight;

    @Override public int weight() { return weight; }


    public RegionItemData(String type, String key, int weight) {
        this.type = type;
        this.key  = key;
        this.weight = weight;
    }
}
