package com.seggellion.britannia_mod.trader;

import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Central trader registry for identity, catalog/economy role names, and spawn defaults.
 * New trader types should be added here before wiring a renderer or custom role handler.
 */
public final class TraderTypes {
    public static final String WOOD = "wood_trader";
    public static final String FISH = "fish_trader";
    public static final String SALVAGE = "salvage_trader";

    private static final Map<String, TraderDefinition> DEFINITIONS = new LinkedHashMap<>();

    static {
        register(WOOD, "wood_trader", "Wood Trader",
                () -> EntityRegistry.WOOD_MERCHANT_ENTITY.get(), TraderSpawnSettings.standard(),
                TraderAppearance.outfit("wood_trader"));
        alias("wood_merchant", WOOD);

        register(FISH, "fish_trader", "Fish Trader",
                () -> EntityRegistry.FISH_TRADER.get(), TraderSpawnSettings.standard(),
                TraderAppearance.outfit("fish_trader"));

        register(SALVAGE, "salvage_trader", "Salvage Trader",
                () -> EntityRegistry.SALVAGE_TRADER.get(), TraderSpawnSettings.stationary(),
                TraderAppearance.outfit("salvage_trader"));

        register("alcohol_trader", "alcohol_trader", "Alcohol Trader",
                () -> EntityRegistry.ALCOHOL_TRADER.get(), TraderSpawnSettings.stationary(),
                TraderAppearance.outfit("alcohol_trader"));
        register("meat_trader", "meat_trader", "Meat Trader",
                () -> EntityRegistry.MEAT_TRADER.get(), TraderSpawnSettings.standard(),
                TraderAppearance.outfit("meat_trader"));
        register("metal_trader", "metal_trader", "Metal Trader",
                () -> EntityRegistry.METAL_MERCHANT_ENTITY.get(), TraderSpawnSettings.standard(),
                TraderAppearance.outfit("salvage_trader"));
        alias("metal_merchant", "metal_trader");
        register("stone_trader", "stone_trader", "Stone Trader",
                () -> EntityRegistry.STONE_MERCHANT_ENTITY.get(), TraderSpawnSettings.standard(),
                TraderAppearance.outfit("stone_trader"));
        alias("stone_merchant", "stone_trader");
    }

    private TraderTypes() {
    }

    public static TraderDefinition byId(String type) {
        TraderDefinition definition = DEFINITIONS.get(normalize(type));
        return definition == null ? DEFINITIONS.get(WOOD) : definition;
    }

    public static String normalize(String type) {
        if (type == null || type.isBlank()) return WOOD;
        return type.trim().toLowerCase(Locale.ROOT);
    }

    public static Collection<TraderDefinition> all() {
        return DEFINITIONS.values().stream().distinct().toList();
    }

    public static List<String> configKeys() {
        return all().stream().map(TraderDefinition::configKey).toList();
    }

    private static void register(String configKey, String npcType, String roleTitle,
                                 Supplier<EntityType<? extends Mob>> entityType,
                                 TraderSpawnSettings spawnSettings,
                                 TraderAppearance appearance) {
        DEFINITIONS.put(configKey, new TraderDefinition(configKey, npcType, roleTitle, entityType, spawnSettings, appearance));
    }

    private static void alias(String alias, String target) {
        DEFINITIONS.put(alias, DEFINITIONS.get(target));
    }
}
