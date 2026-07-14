package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FruitTreeRegistry {
    public static final String DEFAULT_TREE_ID = "orange";
    private static final Map<String, FruitTreeDefinition> BY_ID = new LinkedHashMap<>();

    static {
        register(new FruitTreeDefinition("orange", "Orange",
                ItemRegistry.ORANGE::get, ItemRegistry.ORANGE_SEEDS::get,
                BlockRegistry.ORANGE_TREE_ROOT_BLOCK::get, BlockRegistry.ORANGE_TREE_TRUNK_BLOCK::get, BlockRegistry.ORANGE_TREE_BRANCH_BLOCK::get,
                BlockRegistry.ORANGE_TREE_LEAF_BLOCK::get, BlockRegistry.ORANGE_FRUIT_BLOCK::get,
                3, 10, 3, 3, 4, 3, 0.15f, 1.00f, 5, 10, 0.45f, 1.30f,
                climates(FarmingClimate.TROPICAL, FarmingClimate.TEMPERATE),
                climates(FarmingClimate.TROPICAL, FarmingClimate.TEMPERATE, FarmingClimate.WETLAND, FarmingClimate.MAGICAL),
                climates(FarmingClimate.ICE, FarmingClimate.FIRE), 50, 110));
        register(new FruitTreeDefinition("lemon", "Lemon",
                ItemRegistry.LEMON::get, ItemRegistry.LEMON_SEEDS::get,
                BlockRegistry.LEMON_TREE_ROOT_BLOCK::get, BlockRegistry.LEMON_TREE_TRUNK_BLOCK::get, BlockRegistry.LEMON_TREE_BRANCH_BLOCK::get,
                BlockRegistry.LEMON_TREE_LEAF_BLOCK::get, BlockRegistry.LEMON_FRUIT_BLOCK::get,
                3, 10, 3, 3, 4, 3, 0.12f, 0.95f, 4, 8, 0.55f, 1.15f,
                climates(FarmingClimate.TROPICAL, FarmingClimate.TEMPERATE),
                climates(FarmingClimate.TROPICAL, FarmingClimate.TEMPERATE, FarmingClimate.ARID, FarmingClimate.MAGICAL),
                climates(FarmingClimate.ICE), 45, 120));
        register(new FruitTreeDefinition("lime", "Lime",
                ItemRegistry.LIME::get, ItemRegistry.LIME_SEEDS::get,
                BlockRegistry.LIME_TREE_ROOT_BLOCK::get, BlockRegistry.LIME_TREE_TRUNK_BLOCK::get, BlockRegistry.LIME_TREE_BRANCH_BLOCK::get,
                BlockRegistry.LIME_TREE_LEAF_BLOCK::get, BlockRegistry.LIME_FRUIT_BLOCK::get,
                3, 10, 2, 3, 3, 3, 0.12f, 0.90f, 4, 8, 0.35f, 1.15f,
                climates(FarmingClimate.TROPICAL),
                climates(FarmingClimate.TROPICAL, FarmingClimate.WETLAND, FarmingClimate.TEMPERATE),
                climates(FarmingClimate.ICE, FarmingClimate.FIRE), 45, 100));
        register(new FruitTreeDefinition("pear", "Pear",
                ItemRegistry.PEARS::get, ItemRegistry.PEAR_SEEDS::get,
                BlockRegistry.PEAR_TREE_ROOT_BLOCK::get, BlockRegistry.PEAR_TREE_TRUNK_BLOCK::get, BlockRegistry.PEAR_TREE_BRANCH_BLOCK::get,
                BlockRegistry.PEAR_TREE_LEAF_BLOCK::get, BlockRegistry.PEAR_FRUIT_BLOCK::get,
                4, 10, 3, 3, 5, 3, 0.10f, 0.85f, 3, 6, 0.50f, 1.15f,
                climates(FarmingClimate.TEMPERATE),
                climates(FarmingClimate.TEMPERATE, FarmingClimate.WETLAND, FarmingClimate.MAGICAL),
                climates(FarmingClimate.FIRE), 50, 140));
        register(new FruitTreeDefinition("peach", "Peach",
                ItemRegistry.PEACHES::get, ItemRegistry.PEACH_SEEDS::get,
                BlockRegistry.PEACH_TREE_ROOT_BLOCK::get, BlockRegistry.PEACH_TREE_TRUNK_BLOCK::get, BlockRegistry.PEACH_TREE_BRANCH_BLOCK::get,
                BlockRegistry.PEACH_TREE_LEAF_BLOCK::get, BlockRegistry.PEACH_FRUIT_BLOCK::get,
                3, 10, 3, 4, 3, 4, 0.10f, 0.80f, 3, 6, 0.40f, 1.15f,
                climates(FarmingClimate.TEMPERATE, FarmingClimate.TROPICAL),
                climates(FarmingClimate.TEMPERATE, FarmingClimate.TROPICAL, FarmingClimate.MAGICAL),
                climates(FarmingClimate.ICE, FarmingClimate.FIRE), 50, 120));
        register(new FruitTreeDefinition("apple", "Apple",
                ItemRegistry.APPLE::get, ItemRegistry.APPLE_SEEDS::get,
                BlockRegistry.APPLE_TREE_ROOT_BLOCK::get, BlockRegistry.APPLE_TREE_TRUNK_BLOCK::get, BlockRegistry.APPLE_TREE_BRANCH_BLOCK::get,
                BlockRegistry.APPLE_TREE_LEAF_BLOCK::get, BlockRegistry.APPLE_FRUIT_BLOCK::get,
                4, 10, 3, 4, 4, 4, 0.12f, 0.90f, 3, 7, 0.65f, 1.15f,
                climates(FarmingClimate.TEMPERATE),
                climates(FarmingClimate.TEMPERATE, FarmingClimate.WETLAND, FarmingClimate.MAGICAL, FarmingClimate.ICE),
                climates(FarmingClimate.FIRE), 50, 150));
        register(new FruitTreeDefinition("cherries", "Cherries",
                ItemRegistry.CHERRIES::get, ItemRegistry.CHERRY_SEEDS::get,
                BlockRegistry.CHERRY_TREE_ROOT_BLOCK::get, BlockRegistry.CHERRY_TREE_TRUNK_BLOCK::get, BlockRegistry.CHERRY_TREE_BRANCH_BLOCK::get,
                BlockRegistry.CHERRY_TREE_LEAF_BLOCK::get, BlockRegistry.CHERRY_FRUIT_BLOCK::get,
                4, 10, 3, 4, 4, 4, 0.15f, 1.00f, 6, 12, 0.50f, 1.20f,
                climates(FarmingClimate.TEMPERATE),
                climates(FarmingClimate.TEMPERATE, FarmingClimate.MAGICAL, FarmingClimate.ICE),
                climates(FarmingClimate.FIRE), 60, 150));
        register(new FruitTreeDefinition("olive", "Olive",
                ItemRegistry.OLIVE::get, ItemRegistry.OLIVE_SEEDS::get,
                BlockRegistry.OLIVE_TREE_ROOT_BLOCK::get, BlockRegistry.OLIVE_TREE_TRUNK_BLOCK::get, BlockRegistry.OLIVE_TREE_BRANCH_BLOCK::get,
                BlockRegistry.OLIVE_TREE_LEAF_BLOCK::get, BlockRegistry.OLIVE_FRUIT_BLOCK::get,
                3, 10, 3, 4, 3, 4, 0.08f, 0.70f, 4, 9, 0.75f, 1.10f,
                climates(FarmingClimate.ARID, FarmingClimate.TEMPERATE),
                climates(FarmingClimate.ARID, FarmingClimate.TEMPERATE, FarmingClimate.TROPICAL),
                climates(FarmingClimate.ICE, FarmingClimate.WETLAND), 40, 130));
        register(new FruitTreeDefinition("plum", "Plum",
                ItemRegistry.PLUM::get, ItemRegistry.PLUM_SEEDS::get,
                BlockRegistry.PLUM_TREE_ROOT_BLOCK::get, BlockRegistry.PLUM_TREE_TRUNK_BLOCK::get, BlockRegistry.PLUM_TREE_BRANCH_BLOCK::get,
                BlockRegistry.PLUM_TREE_LEAF_BLOCK::get, BlockRegistry.PLUM_FRUIT_BLOCK::get,
                4, 10, 3, 4, 4, 4, 0.12f, 0.90f, 3, 7, 0.55f, 1.20f,
                climates(FarmingClimate.TEMPERATE),
                climates(FarmingClimate.TEMPERATE, FarmingClimate.WETLAND, FarmingClimate.MAGICAL, FarmingClimate.ICE),
                climates(FarmingClimate.FIRE), 50, 150));
    }

    private FruitTreeRegistry() {
    }

    public static Optional<FruitTreeDefinition> byId(String id) {
        return Optional.ofNullable(BY_ID.get(normalize(id)));
    }

    public static FruitTreeDefinition byIdOrDefault(String id) {
        return byId(id).orElse(BY_ID.get(DEFAULT_TREE_ID));
    }

    public static boolean isFruitTreeCrop(String cropId) {
        return BY_ID.containsKey(normalize(cropId));
    }

    public static Collection<FruitTreeDefinition> all() {
        return BY_ID.values();
    }

    private static void register(FruitTreeDefinition definition) {
        BY_ID.put(normalize(definition.id()), definition);
    }

    private static Set<FarmingClimate> climates(FarmingClimate... climates) {
        return Set.of(climates);
    }

    private static String normalize(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT);
    }
}
