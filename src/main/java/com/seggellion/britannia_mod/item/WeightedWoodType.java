package com.seggellion.britannia_mod.item;

import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum WeightedWoodType implements StringRepresentable {
    OAK("oak", "Oak Wood", 0, 4.5D, 5.5D),
    SPRUCE("spruce", "Spruce Wood", 1, 3.5D, 4.5D),
    BIRCH("birch", "Birch Wood", 2, 3.0D, 4.0D),
    JUNGLE("jungle", "Jungle Wood", 3, 4.0D, 5.0D),
    ACACIA("acacia", "Acacia Wood", 4, 4.0D, 5.0D),
    DARK_OAK("dark_oak", "Dark Oak Wood", 5, 5.0D, 6.0D),
    MANGROVE("mangrove", "Mangrove Wood", 6, 4.8D, 5.8D),
    CHERRY("cherry", "Cherry Wood", 7, 3.4D, 4.4D),
    BAMBOO("bamboo", "Bamboo", 8, 2.0D, 3.0D),
    CRIMSON("crimson", "Crimson Stem", 9, 4.5D, 5.5D),
    WARPED("warped", "Warped Stem", 10, 4.5D, 5.5D),
    ORANGE("orange", "Orange Wood", 100, 3.0D, 8.0D),
    LEMON("lemon", "Lemon Wood", 101, 3.0D, 7.0D),
    LIME("lime", "Lime Wood", 102, 2.8D, 6.5D),
    PEAR("pear", "Pear Wood", 103, 3.2D, 7.2D),
    PEACH("peach", "Peach Wood", 104, 2.8D, 6.8D),
    APPLE("apple", "Apple Wood", 105, 3.4D, 7.5D),
    CHERRIES("cherries", "Cherries Wood", 106, 3.0D, 6.8D),
    OLIVE("olive", "Olive Wood", 107, 4.0D, 8.0D),
    PLUM("plum", "Plum Wood", 108, 3.2D, 7.2D);

    private static final Map<String, WeightedWoodType> BY_ID = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(WeightedWoodType::id, Function.identity()));

    private final String id;
    private final String displayName;
    private final int customModelData;
    private final double minWeight;
    private final double maxWeight;

    WeightedWoodType(String id, String displayName, int customModelData, double minWeight, double maxWeight) {
        this.id = id;
        this.displayName = displayName;
        this.customModelData = customModelData;
        this.minWeight = minWeight;
        this.maxWeight = maxWeight;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int customModelData() {
        return customModelData;
    }

    public double minWeight() {
        return minWeight;
    }

    public double maxWeight() {
        return maxWeight;
    }

    public double averageWeight() {
        return (minWeight + maxWeight) * 0.5D;
    }

    public double randomWeight(RandomSource random) {
        return minWeight + random.nextDouble() * (maxWeight - minWeight);
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public static Optional<WeightedWoodType> byId(String id) {
        return Optional.ofNullable(BY_ID.get(normalize(id)));
    }

    public static WeightedWoodType byIdOrDefault(String id) {
        return byId(id).orElse(OAK);
    }

    public static String normalize(String id) {
        if (id == null) {
            return "";
        }
        String normalized = id.toLowerCase(Locale.ROOT).trim();
        if (normalized.equals("dark oak")) {
            return "dark_oak";
        }
        if (normalized.equals("cherry_tree") || normalized.equals("cherries_tree")) {
            return "cherries";
        }
        return normalized;
    }
}
