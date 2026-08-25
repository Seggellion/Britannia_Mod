package com.seggellion.britannia_mod.merchant;

import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.trader.TraderAppearance;
import com.seggellion.britannia_mod.trader.TraderSpawnSettings;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public final class MerchantTypes {
    public static final String BAKER = "baker";
    public static final String TAVERNKEEPER = "tavernkeeper";
    public static final String COSTERMONGER = "costermonger";
    public static final String FARMER = "farmer";

    /**
     * Food-supply floor for the Farmer's legacy spawn maintenance. The value a city must show on
     * the same Rails reading the food-gated spawn blocks already poll before the block will keep
     * a Farmer staffed; the Rails staffing sweep enforces the same floor for authoritative posts.
     */
    public static final double FARMER_MINIMUM_FOOD_SUPPLY = 20.0D;

    private static final Map<String, MerchantDefinition> DEFINITIONS = new LinkedHashMap<>();

    static {
        register(BAKER, "baker", "Baker",
                () -> EntityRegistry.BAKER.get(), TraderSpawnSettings.standard(),
                TraderAppearance.outfit("baker"));

        register(TAVERNKEEPER, "tavernkeeper", "Tavernkeeper",
                () -> EntityRegistry.TAVERNKEEPER.get(), TraderSpawnSettings.standard(),
                TraderAppearance.outfit("tavernkeeper"));
        alias("tavern_keeper", TAVERNKEEPER);
        alias("tavern", TAVERNKEEPER);

        register(COSTERMONGER, "costermonger", "Costermonger",
                () -> EntityRegistry.COSTERMONGER.get(), TraderSpawnSettings.standard(),
                TraderAppearance.outfit("costermonger"));
        alias("produce_merchant", COSTERMONGER);

        register(FARMER, "farmer", "Farmer",
                () -> EntityRegistry.FARMER.get(), TraderSpawnSettings.standard(),
                TraderAppearance.outfit("farmer"), FARMER_MINIMUM_FOOD_SUPPLY);
    }

    private MerchantTypes() {
    }

    public static MerchantDefinition byId(String type) {
        MerchantDefinition definition = DEFINITIONS.get(normalize(type));
        return definition == null ? DEFINITIONS.get(BAKER) : definition;
    }

    public static String normalize(String type) {
        if (type == null || type.isBlank()) return BAKER;
        return type.trim().toLowerCase(Locale.ROOT);
    }

    public static Collection<MerchantDefinition> all() {
        return DEFINITIONS.values().stream().distinct().toList();
    }

    public static List<String> configKeys() {
        return all().stream().map(MerchantDefinition::configKey).toList();
    }

    private static void register(String configKey, String npcType, String roleTitle,
                                 Supplier<EntityType<? extends Mob>> entityType,
                                 TraderSpawnSettings spawnSettings,
                                 TraderAppearance appearance) {
        register(configKey, npcType, roleTitle, entityType, spawnSettings, appearance, 0.0D);
    }

    private static void register(String configKey, String npcType, String roleTitle,
                                 Supplier<EntityType<? extends Mob>> entityType,
                                 TraderSpawnSettings spawnSettings,
                                 TraderAppearance appearance,
                                 double minimumFoodSupply) {
        DEFINITIONS.put(configKey, new MerchantDefinition(
                configKey, npcType, roleTitle, entityType, spawnSettings, appearance, minimumFoodSupply));
    }

    private static void alias(String alias, String target) {
        DEFINITIONS.put(alias, DEFINITIONS.get(target));
    }
}
