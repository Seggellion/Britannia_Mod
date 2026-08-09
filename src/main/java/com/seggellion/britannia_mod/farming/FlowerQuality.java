package com.seggellion.britannia_mod.farming;

/** Existing farming quality semantics: an authoritative persisted score from 1 through 100. */
public record FlowerQuality(int value) {
    public static final int MINIMUM = 1;
    public static final int MAXIMUM = 100;
    public static final FlowerQuality DEFAULT = new FlowerQuality(50);

    public FlowerQuality {
        if (value < MINIMUM || value > MAXIMUM) {
            throw new IllegalArgumentException("Flower quality must use the crop quality range 1..100: " + value);
        }
    }

    public String label() {
        return CropQualityCalculator.qualityLabel(value);
    }
}
