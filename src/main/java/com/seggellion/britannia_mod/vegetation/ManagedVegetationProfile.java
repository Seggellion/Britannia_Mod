package com.seggellion.britannia_mod.vegetation;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Validated weighted table; selection is independent of the number or identity of entries. */
public final class ManagedVegetationProfile {
    public static final ResourceLocation GRASS_FAMILY_ID = id("grass_family");
    public static final ResourceLocation FERN_ID = id("fern");
    public static final ResourceLocation FLOWER_ID = id("random_flower");
    public static final ResourceLocation BLOOD_MOSS_ID = id("blood_moss");

    private final List<ManagedVegetationEntry> entries;
    private final int totalWeight;

    public ManagedVegetationProfile(List<ManagedVegetationEntry> entries) {
        Objects.requireNonNull(entries, "Managed vegetation entries are required");
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Managed vegetation profile cannot be empty");
        }
        Set<ResourceLocation> ids = new HashSet<>();
        long total = 0L;
        for (ManagedVegetationEntry entry : entries) {
            Objects.requireNonNull(entry, "Managed vegetation entry cannot be null");
            if (!ids.add(entry.id())) {
                throw new IllegalArgumentException("Duplicate managed vegetation entry ID: " + entry.id());
            }
            total += entry.weight();
        }
        if (total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Managed vegetation total weight is too large");
        }
        this.entries = List.copyOf(entries);
        this.totalWeight = (int) total;
    }

    public static ManagedVegetationProfile defaults() {
        return ofWeights(
                ManagedVegetationConfig.DEFAULT_GRASS_WEIGHT,
                ManagedVegetationConfig.DEFAULT_FERN_WEIGHT,
                ManagedVegetationConfig.DEFAULT_FLOWER_WEIGHT
        );
    }

    public static ManagedVegetationProfile configured() {
        return configured(false);
    }

    public static ManagedVegetationProfile configured(boolean swamp) {
        return ofWeights(
                ManagedVegetationConfig.grassWeight(),
                ManagedVegetationConfig.fernWeight(),
                ManagedVegetationConfig.flowerWeight(),
                swamp
        );
    }

    public static ManagedVegetationProfile ofWeights(
            int grassWeight,
            int fernWeight,
            int flowerWeight,
            boolean swamp
    ) {
        if (swamp) {
            return new ManagedVegetationProfile(List.of(
                    new ManagedVegetationEntry(
                            GRASS_FAMILY_ID,
                            grassWeight,
                            ManagedVegetationGrowthStrategy.GRASS_FAMILY
                    ),
                    new ManagedVegetationEntry(
                            FERN_ID,
                            fernWeight,
                            ManagedVegetationGrowthStrategy.STATIC_FERN
                    ),
                    new ManagedVegetationEntry(
                            BLOOD_MOSS_ID,
                            flowerWeight,
                            ManagedVegetationGrowthStrategy.STATIC_BLOOD_MOSS
                    )
            ));
        }
        return ofWeights(grassWeight, fernWeight, flowerWeight);
    }

    public static ManagedVegetationProfile ofWeights(int grassWeight, int fernWeight, int flowerWeight) {
        return new ManagedVegetationProfile(List.of(
                new ManagedVegetationEntry(
                        GRASS_FAMILY_ID, grassWeight, ManagedVegetationGrowthStrategy.GRASS_FAMILY
                ),
                new ManagedVegetationEntry(
                        FERN_ID, fernWeight, ManagedVegetationGrowthStrategy.STATIC_FERN
                ),
                new ManagedVegetationEntry(
                        FLOWER_ID, flowerWeight, ManagedVegetationGrowthStrategy.FLOWER_STAGES
                )
        ));
    }

    public ManagedVegetationEntry select(RandomSource random) {
        Objects.requireNonNull(random, "Managed vegetation random source is required");
        return selectByRoll(random.nextInt(totalWeight));
    }

    public ManagedVegetationEntry selectByRoll(int roll) {
        if (roll < 0 || roll >= totalWeight) {
            throw new IllegalArgumentException("Managed vegetation roll must be in 0.." + (totalWeight - 1));
        }
        int cursor = 0;
        for (ManagedVegetationEntry entry : entries) {
            cursor += entry.weight();
            if (roll < cursor) {
                return entry;
            }
        }
        throw new IllegalStateException("Validated managed vegetation weights did not resolve roll " + roll);
    }

    public Optional<ManagedVegetationEntry> byId(ResourceLocation id) {
        return entries.stream().filter(entry -> entry.id().equals(id)).findFirst();
    }

    public List<ManagedVegetationEntry> entries() {
        return entries;
    }

    public int totalWeight() {
        return totalWeight;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, path);
    }
}
