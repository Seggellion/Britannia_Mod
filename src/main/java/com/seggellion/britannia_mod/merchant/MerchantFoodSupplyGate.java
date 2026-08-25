package com.seggellion.britannia_mod.merchant;

import com.seggellion.britannia_mod.network.CityFoodSupplyCache;
import net.minecraft.server.level.ServerLevel;

import java.util.OptionalDouble;

/**
 * Legacy-path economic gate for spawn-block merchants whose definition carries a minimum food
 * supply (today only the Farmer, at {@link MerchantTypes#FARMER_MINIMUM_FOOD_SUPPLY}).
 *
 * <p>This is the same contract the food-gated sibling spawn blocks (wood, stone, metal, horse,
 * blacksmith) already follow against the shared {@link CityFoodSupplyCache} reading of the Rails
 * {@code food_supply} value: at or above the minimum the merchant is maintained, below it the
 * staff despawns, and a city Rails has never answered for is neither spawned nor despawned --
 * a missing reading is not a reading of zero. Rails enforces the identical floor for the
 * authoritative-post path through the economic NPC type's {@code default_requirements}
 * ({@code supply_gte food}), so this gate only matters for the window before a legacy block
 * migrates onto an authoritative post.
 */
public final class MerchantFoodSupplyGate {

    /** What a maintenance cycle may do with the merchant this gate protects. */
    public enum Decision { MAINTAIN, DESPAWN, SKIP }

    /** The reading source, isolated so GameTests can drive the gate without a Rails server. */
    @FunctionalInterface
    public interface Reader {
        OptionalDouble read(ServerLevel level, String cityName);
    }

    private static volatile Reader reader = CityFoodSupplyCache::poll;

    private MerchantFoodSupplyGate() {
    }

    public static Decision decide(ServerLevel level, MerchantDefinition definition, String cityName) {
        if (!definition.requiresFoodSupply()) return Decision.MAINTAIN;
        return decide(reader.read(level, cityName), definition.minimumFoodSupply());
    }

    /**
     * The pure rule: {@code minimum <= 0} never gates, an absent reading skips the cycle, and an
     * actual reading maintains at {@code >= minimum} and despawns below it.
     */
    public static Decision decide(OptionalDouble reading, double minimumFoodSupply) {
        if (minimumFoodSupply <= 0.0D) return Decision.MAINTAIN;
        if (reading.isEmpty()) return Decision.SKIP;
        return reading.getAsDouble() >= minimumFoodSupply ? Decision.MAINTAIN : Decision.DESPAWN;
    }

    // --- test seams -------------------------------------------------------------------------

    /** Installs a stand-in reading source. Tests only; always restore with {@link #resetReader()}. */
    public static void installReader(Reader replacement) {
        reader = replacement == null ? CityFoodSupplyCache::poll : replacement;
    }

    public static void resetReader() {
        reader = CityFoodSupplyCache::poll;
    }
}
