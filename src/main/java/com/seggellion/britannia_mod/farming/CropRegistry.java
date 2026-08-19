package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.item.GrapeSeedsItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public final class CropRegistry {
    public static final int VANILLA_STYLE_AGE_COUNT = 8; // ages 0..7, matching CropBlock#getMaxAge() == 7
    private static final float DEFAULT_NUTRIENT_TOLERANCE = 0.35f;
    private static final float DEFAULT_HYDRATION_TOLERANCE = 0.30f;
    private static final List<CropDefinition> CROPS = new ArrayList<>();
    private static final Map<String, CropDefinition> BY_ID = new HashMap<>();
    private static volatile boolean bootstrapped = false;
    private static boolean bootstrapping = false; // guarded by the class lock

    private CropRegistry() {
    }

    public static Optional<CropDefinition> byId(String id) {
        bootstrap();
        return Optional.ofNullable(BY_ID.get(normalize(id)));
    }

    public static Optional<CropDefinition> bySeed(Item item) {
        bootstrap();
        for (CropDefinition crop : CROPS) {
            if (crop.seedItem().get() == item) {
                return Optional.of(crop);
            }
        }
        return Optional.empty();
    }

    public static List<CropDefinition> all() {
        bootstrap();
        return Collections.unmodifiableList(CROPS);
    }

    /**
     * Builds the crop table on first use, atomically.
     *
     * <p>This used to set {@code bootstrapped} before defining anything, which meant a caller that
     * arrived too early - before deferred item registration had settled - could abort partway and
     * leave the flag set over an empty table. Every later call then took the fast path and saw no
     * crops at all, for the rest of the JVM's life. On a client that reached the mod through
     * {@code ItemStack#getHoverName}, which any mod may call during startup, so the corruption was
     * triggered by load order rather than by anything the player did, and surfaced much later as a
     * definition-count mismatch that named neither the real cause nor the real moment.
     *
     * <p>The flag is now set only on success and partial state is discarded on failure, so an early
     * call fails and is retried rather than latching a broken registry. It is {@code volatile} so
     * the fast path cannot observe the flag ahead of the table it publishes.
     */
    private static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        synchronized (CropRegistry.class) {
            if (bootstrapped) {
                return;
            }
            if (bootstrapping) {
                // Re-entered through a crop definition. Recursing would duplicate every crop, and
                // returning would hand back a half-built table as though it were complete.
                throw new IllegalStateException(
                        "CropRegistry.bootstrap() was re-entered before it finished; "
                                + "crop definitions are incomplete");
            }
            bootstrapping = true;
            boolean completed = false;
            try {
                defineCrops();
                completed = true;
            } finally {
                bootstrapping = false;
                if (completed) {
                    bootstrapped = true;
                } else {
                    CROPS.clear();
                    BY_ID.clear();
                }
            }
        }
    }

    private static void defineCrops() {
        crop("squash", 10.0f, "Squash", ItemRegistry.SQUASH_SEEDS::get, ItemRegistry.SQUASH::get, 1, 5, VANILLA_STYLE_AGE_COUNT, 0.60f, 0.30f, 0.35f, 0.70f, 0.65f, climate(FarmingClimate.TEMPERATE, FarmingClimate.TROPICAL), weights(1.20f, 0.80f, 0.90f, 1.45f), yields(1, 3), 0.30f, 1.15f, "Heavy one-block annual vine crop; age 0..7 mapped across five visual assets.");
        crop("carrot", 0.0f, "Carrot", ItemRegistry.CARROT_SEEDS::get, ItemRegistry.CARROTS::get, 1, 5, VANILLA_STYLE_AGE_COUNT, 0.50f, 0.25f, 0.25f, 0.45f, 0.55f, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(1.10f, 0.80f, 0.80f, 1.10f), yields(3, 3), 0.25f, 0.85f, "Cool temperate root crop; age 0..7, hand harvest for exactly 3 carrots.");
        crop("corn", 30.0f, "Corn", ItemRegistry.CORN_SEEDS::get, ItemRegistry.CORN::get, 2, 7, VANILLA_STYLE_AGE_COUNT, 0.75f, 0.35f, 0.40f, 0.90f, 0.60f, 0.30f, 0.25f, climate(FarmingClimate.TEMPERATE), weights(1.25f, 0.60f, 0.75f, 1.75f), false, false, true, 4, false, yields(2, 5), 0.25f, 1.15f, "Heavy-feeding tall crop; maxHeight is total visible height: base render plus up to 3 selectable stalk blocks.");
        crop("cabbage", 10.0f, "Cabbage", ItemRegistry.CABBAGE_SEEDS::get, ItemRegistry.CABBAGE::get, 1, 6, VANILLA_STYLE_AGE_COUNT, 0.45f, 0.25f, 0.50f, 0.75f, 0.65f, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(1.15f, 0.75f, 1.10f, 1.45f), yields(1, 3), 0.30f, "Cool-weather leafy brassica; age 0..7 with five visual assets mapped across ages.");
        crop("lettuce", 0.0f, "Lettuce", ItemRegistry.LETTUCE_SEEDS::get, ItemRegistry.LETTUCE::get, 1, 4, VANILLA_STYLE_AGE_COUNT, 0.30f, 0.25f, 0.25f, 0.55f, 0.80f, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(0.85f, 0.85f, 0.75f, 1.20f), yields(1, 3), 0.30f, 1.25f, "Fast cool-weather leaf crop; age 0..7 with five visual assets mapped across ages.");
        crop("yellow_onion", 10.0f, "Yellow Onion", ItemRegistry.YELLOW_ONION_SEEDS::get, ItemRegistry.YELLOW_ONION::get, 1, 5, VANILLA_STYLE_AGE_COUNT, 0.40f, 0.20f, 0.75f, 0.35f, 0.45f, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(0.90f, 0.70f, 1.70f, 0.80f), yields(1, 3), 0.30f, 1.10f, "Cool-tolerant sulphur-loving allium; age 0..7 mapped across five visual assets.");
        crop("green_onion", 0.0f, "Green Onion", ItemRegistry.GREEN_ONION_SEEDS::get, ItemRegistry.GREEN_ONION::get, 1, 4, VANILLA_STYLE_AGE_COUNT, 0.35f, 0.20f, 0.70f, 0.45f, 0.55f, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(0.80f, 0.70f, 1.60f, 1.00f), yields(1, 3), 0.30f, 1.15f, "Fast cool-weather scallion; age 0..7 mapped across five visual assets.");
        crop("pumpkin", 20.0f, "Pumpkin", ItemRegistry.PUMPKIN_SEEDS_CUSTOM::get, ItemRegistry.PUMPKIN::get, 1, 7, VANILLA_STYLE_AGE_COUNT, 0.70f, 0.30f, 0.35f, 0.80f, 0.70f, climate(FarmingClimate.TEMPERATE, FarmingClimate.TROPICAL), weights(1.30f, 0.75f, 0.90f, 1.65f), yields(1, 2), 0.25f, 1.25f, "Rich-soil annual gourd; age 0..7, one-block custom crop harvested with scissors.");
        crop("potato", 5.0f, "Potato", ItemRegistry.POTATO_SEED::get, ItemRegistry.POTATO::get, 1, 5, VANILLA_STYLE_AGE_COUNT, 0.55f, 0.25f, 0.30f, 0.55f, 0.55f, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(1.15f, 0.75f, 0.85f, 1.20f), yields(4, 4), 0.25f, 0.90f, "Hardy cool temperate tuber; age 0..7, hand harvest for exactly 4 potatoes.");
        crop("watermelon", 50.0f, "Watermelon", ItemRegistry.WATERMELON_SEEDS::get, ItemRegistry.WATERMELON::get, 1, 7, VANILLA_STYLE_AGE_COUNT, 0.55f, 0.35f, 0.35f, 0.75f, 0.85f, climate(FarmingClimate.TROPICAL, FarmingClimate.TEMPERATE), weights(1.00f, 0.85f, 0.85f, 1.55f), yields(1, 2), 0.25f, 1.40f, "Hydration-sensitive warm annual melon; age 0..7, one-block custom crop harvested with scissors.");
        crop("vanilla_potato", 5.0f, "Vanilla Potato", () -> Items.POTATO, () -> Items.POTATO, 1, 5, 4, 0.55f, 0.25f, 0.30f, 0.50f, 0.60f, climate(FarmingClimate.TEMPERATE), weights(1.30f, 0.80f, 1.00f, 1.00f), true, yields(1, 3), 0.0f, "Vanilla produce-as-seed compatibility.");
        crop("wheat", 0.0f, "Wheat", () -> Items.WHEAT_SEEDS, () -> Items.WHEAT, 1, 5, VANILLA_STYLE_AGE_COUNT, 0.45f, 0.20f, 0.25f, 0.45f, 0.55f, climate(FarmingClimate.TEMPERATE), weights(1.00f, 0.80f, 0.90f, 1.00f), true, yields(6, 6), 0.35f, "Temperate grain crop; age 0..7, harvested with grain blade for 6 grain plus straw.");
        crop("rye", 10.0f, "Rye", ItemRegistry.RYE_SEEDS::get, ItemRegistry.RYE::get, 1, 5, VANILLA_STYLE_AGE_COUNT, 0.35f, 0.20f, 0.25f, 0.40f, 0.45f, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(0.75f, 0.70f, 0.80f, 0.90f), true, yields(6, 6), 0.30f, "Hardy cool-weather grain; age 0..7, harvested with grain blade for 6 grain plus straw.");
        crop("barley", 15.0f, "Barley", ItemRegistry.BARLEY_SEEDS::get, ItemRegistry.BARLEY::get, 1, 5, VANILLA_STYLE_AGE_COUNT, 0.40f, 0.20f, 0.30f, 0.40f, 0.48f, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(0.90f, 0.70f, 1.00f, 0.85f), true, yields(6, 6), 0.30f, "Drought-tolerant cool grain; age 0..7, harvested with grain blade for 6 grain plus straw.");
        crop("oats", 15.0f, "Oats", ItemRegistry.OAT_SEEDS::get, ItemRegistry.OATS::get, 1, 5, VANILLA_STYLE_AGE_COUNT, 0.35f, 0.20f, 0.25f, 0.50f, 0.62f, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(0.85f, 0.70f, 0.80f, 1.10f), true, yields(6, 6), 0.30f, "Cool moist grain; age 0..7, harvested with grain blade for 6 grain plus straw.");
        crop("mustard", 30.0f, "Mustard", ItemRegistry.MUSTARD_SEEDS::get, ItemRegistry.MUSTARD_SEEDS::get, 2, 5, VANILLA_STYLE_AGE_COUNT, 0.40f, 0.25f, 0.35f, 0.45f, 0.50f, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(0.95f, 0.75f, 1.05f, 1.00f), yields(6, 6), 0.30f, 1.05f, "Cool-season mustard seed crop; age 0..7, harvested with grain blade for 6 mustard seeds plus straw.");
        crop("beans", 25.0f, "Beans", ItemRegistry.BEAN_SEEDS::get, ItemRegistry.BEANS::get, 2, 6, VANILLA_STYLE_AGE_COUNT, 0.35f, 0.30f, 0.25f, 0.35f, 0.60f, climate(FarmingClimate.TEMPERATE, FarmingClimate.TROPICAL), weights(0.80f, 0.90f, 0.75f, 0.80f), yields(6, 6), 0.25f, "Temperate warm grain legume; age 0..7, harvested with grain blade for 6 beans plus straw.");
        crop("rice", 75.0f, "Rice", ItemRegistry.RICE_SEEDS::get, ItemRegistry.RICE::get, 3, 7, 5, 0.35f, 0.25f, 0.25f, 0.65f, 0.95f, 0.35f, 0.20f, climate(FarmingClimate.WETLAND), weights(0.80f, 0.80f, 0.80f, 1.30f), false, false, false, 1, false, yields(2, 4), 0.30f, 1.10f, "Requires very high hydration.");
        crop("tomato", 40.0f, "Tomato", ItemRegistry.TOMATO_SEEDS::get, ItemRegistry.TOMATO::get, 2, 6, VANILLA_STYLE_AGE_COUNT, 0.65f, 0.35f, 0.45f, 0.70f, 0.72f, climate(FarmingClimate.TEMPERATE, FarmingClimate.TROPICAL), weights(1.30f, 1.00f, 1.10f, 1.40f), true, false, false, 1, false, yields(2, 4), 0.25f, 1.20f, "Perennial trellis crop; requires trellis support, regrows after fruit harvest.");
        crop("garlic", 20.0f, "Garlic", ItemRegistry.GARLIC_SEEDS::get, ItemRegistry.GARLIC::get, 3, 7, VANILLA_STYLE_AGE_COUNT, 0.45f, 0.25f, 0.80f, 0.35f, 0.45f, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(0.90f, 0.80f, 1.80f, 0.80f), yields(2, 4), 0.20f, 1.15f, "Cool-tolerant sulphur-loving allium; age 0..7 mapped across five visual assets.");
        crop("ginseng", 70.0f, "Ginseng", ItemRegistry.GINSENG_SEEDS::get, ItemRegistry.GINSENG::get, 4, 9, VANILLA_STYLE_AGE_COUNT, 0.55f, 0.75f, 0.45f, 0.35f, 0.55f, climate(FarmingClimate.MAGICAL, FarmingClimate.TEMPERATE), weights(1.10f, 1.80f, 1.20f, 0.70f), yields(1, 2), 0.15f, 1.45f, "Demanding medicinal root; age 0..7 mapped across five visual assets.");
        crop("mandrake", 90.0f, "Mandrake", ItemRegistry.MANDRAKE_SEEDS::get, ItemRegistry.MANDRAKE::get, 4, 10, VANILLA_STYLE_AGE_COUNT, 1.00f, 1.00f, 1.00f, 1.00f, 0.55f, 0.50f, DEFAULT_HYDRATION_TOLERANCE, climate(FarmingClimate.MAGICAL, FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(1.50f, 1.50f, 1.50f, 1.50f), false, false, false, 1, false, yields(1, 2), 0.15f, 1.55f, "Slow magical annual root; age 0..7, uniquely prefers maximum nutrient saturation.");
        crop("nightshade", 85.0f, "Nightshade", ItemRegistry.NIGHTSHADE_SEEDS::get, ItemRegistry.NIGHTSHADE::get, 4, 9, VANILLA_STYLE_AGE_COUNT, 1.00f, 1.00f, 1.00f, 1.00f, 0.45f, 0.50f, DEFAULT_HYDRATION_TOLERANCE, climate(FarmingClimate.MAGICAL, FarmingClimate.UNDERGROUND), weights(1.50f, 1.50f, 1.50f, 1.50f), false, false, false, 1, false, yields(1, 2), 0.15f, 1.50f, "Rare magical annual crop; age 0..7, prefers maximum nutrient saturation and grows only in darkness or underground.");
        crop("brown_mushroom", 55.0f, "Brown Mushroom", () -> Items.BROWN_MUSHROOM, () -> Items.BROWN_MUSHROOM, 2, 6, 4, 0.20f, 0.45f, 0.35f, 0.70f, 0.75f, climate(FarmingClimate.UNDERGROUND, FarmingClimate.WETLAND), weights(0.60f, 1.20f, 1.00f, 1.60f), true, yields(1, 3), 0.20f, "Vanilla mushroom compatibility.");
        crop("red_mushroom", 60.0f, "Red Mushroom", () -> Items.RED_MUSHROOM, () -> Items.RED_MUSHROOM, 2, 6, 4, 0.20f, 0.45f, 0.35f, 0.70f, 0.75f, climate(FarmingClimate.UNDERGROUND, FarmingClimate.WETLAND), weights(0.60f, 1.20f, 1.00f, 1.60f), true, yields(1, 3), 0.20f, "Vanilla mushroom compatibility.");
        crop("vanilla_pumpkin", 25.0f, "Vanilla Pumpkin", () -> Items.PUMPKIN_SEEDS, () -> Items.PUMPKIN, 1, 6, 4, 0.65f, 0.30f, 0.35f, 0.75f, 0.65f, climate(FarmingClimate.TEMPERATE), weights(1.20f, 0.80f, 0.90f, 1.50f), true, yields(1, 2), 0.30f, "Vanilla pumpkin seed compatibility.");
        crop("vanilla_melon", 45.0f, "Vanilla Melon", () -> Items.MELON_SEEDS, () -> Items.MELON_SLICE, 1, 6, 4, 0.55f, 0.30f, 0.30f, 0.70f, 0.75f, climate(FarmingClimate.TEMPERATE, FarmingClimate.TROPICAL), weights(1.00f, 0.80f, 0.80f, 1.40f), true, yields(2, 5), 0.30f, "Vanilla melon seed compatibility.");
        crop("pineapple", 65.0f, "Pineapple", ItemRegistry.PINEAPPLE_SEEDS::get, ItemRegistry.PINEAPPLE::get, 3, 8, VANILLA_STYLE_AGE_COUNT, 0.65f, 0.35f, 0.45f, 0.70f, 0.60f, 0.35f, DEFAULT_HYDRATION_TOLERANCE, climate(FarmingClimate.TROPICAL, FarmingClimate.TEMPERATE), weights(1.25f, 0.85f, 1.00f, 1.35f), false, false, false, 1, false, yields(1, 1), 0.20f, 1.45f, "Slow warm-climate fruiting annual; age 0..7, bare-hand harvest only.");
        crop("strawberry", 15.0f, "Strawberry", ItemRegistry.STRAWBERRY_SEEDS::get, ItemRegistry.STRAWBERRY::get, 2, 6, VANILLA_STYLE_AGE_COUNT, 0.50f, 0.35f, 0.35f, 0.55f, 0.65f, 0.35f, DEFAULT_HYDRATION_TOLERANCE, climate(FarmingClimate.TEMPERATE), weights(1.05f, 1.05f, 0.90f, 1.15f), false, false, false, 1, false, yields(4, 4), 0.25f, 1.25f, "Temperate fruiting annual; age 0..7, bare-hand harvest only.");
        crop("blueberry", 35.0f, "Blueberry", ItemRegistry.BLUEBERRY_SEEDS::get, ItemRegistry.BLUEBERRY::get, 2, 7, VANILLA_STYLE_AGE_COUNT, 0.40f, 0.60f, 0.30f, 0.45f, 0.72f, 0.30f, 0.25f, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(0.85f, 1.70f, 0.75f, 0.95f), false, false, false, 1, false, yields(6, 6), 0.25f, 1.35f, "Cool mineral-sensitive berry annual; age 0..7, bare-hand harvest only.");
        crop("raspberry", 25.0f, "Raspberry", ItemRegistry.RASPBERRY_SEEDS::get, ItemRegistry.RASPBERRY::get, 2, 6, VANILLA_STYLE_AGE_COUNT, 0.45f, 0.35f, 0.35f, 0.55f, 0.62f, 0.35f, DEFAULT_HYDRATION_TOLERANCE, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(0.95f, 1.00f, 0.85f, 1.15f), false, false, false, 1, false, yields(5, 5), 0.25f, 1.20f, "Cool temperate cane berry annual; age 0..7, bare-hand harvest only.");
        crop("cranberry", 70.0f, "Cranberry", ItemRegistry.CRANBERRY_SEEDS::get, ItemRegistry.CRANBERRY::get, 2, 7, VANILLA_STYLE_AGE_COUNT, 0.35f, 0.45f, 0.30f, 0.55f, 0.88f, 0.30f, 0.15f, climate(FarmingClimate.WETLAND, FarmingClimate.TEMPERATE), weights(0.80f, 1.10f, 0.75f, 1.30f), false, false, false, 1, false, yields(6, 6), 0.25f, 1.45f, "High-hydration lowland berry annual; age 0..7, bare-hand harvest only.");
        crop("blackberry", 30.0f, "Blackberry", ItemRegistry.BLACKBERRY_SEEDS::get, ItemRegistry.BLACKBERRY::get, 2, 6, VANILLA_STYLE_AGE_COUNT, 0.50f, 0.35f, 0.35f, 0.60f, 0.62f, 0.35f, DEFAULT_HYDRATION_TOLERANCE, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(1.00f, 0.95f, 0.85f, 1.20f), false, false, false, 1, false, yields(5, 5), 0.25f, 1.15f, "Hardy temperate cane berry annual; age 0..7, bare-hand harvest only.");
        crop("huckleberry", 40.0f, "Huckleberry", ItemRegistry.HUCKLEBERRY_SEEDS::get, ItemRegistry.HUCKLEBERRY::get, 2, 7, VANILLA_STYLE_AGE_COUNT, 0.35f, 0.45f, 0.25f, 0.40f, 0.58f, 0.35f, DEFAULT_HYDRATION_TOLERANCE, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(0.80f, 1.20f, 0.70f, 0.90f), false, false, false, 1, false, yields(5, 5), 0.25f, 1.35f, "Mid-altitude cool berry annual; age 0..7, bare-hand harvest only.");
        crop("mulberry", 45.0f, "Mulberry", ItemRegistry.MULBERRY_SEEDS::get, ItemRegistry.MULBERRY::get, 2, 6, VANILLA_STYLE_AGE_COUNT, 0.50f, 0.35f, 0.40f, 0.55f, 0.60f, 0.35f, DEFAULT_HYDRATION_TOLERANCE, climate(FarmingClimate.TEMPERATE, FarmingClimate.TROPICAL), weights(1.05f, 0.90f, 0.95f, 1.10f), false, false, false, 1, false, yields(5, 5), 0.25f, 1.15f, "Warm temperate berry annual; age 0..7, bare-hand harvest only.");
        crop("elderberry", 50.0f, "Elderberry", ItemRegistry.ELDERBERRY_SEEDS::get, ItemRegistry.ELDERBERRY::get, 2, 6, VANILLA_STYLE_AGE_COUNT, 0.45f, 0.40f, 0.35f, 0.60f, 0.70f, 0.30f, DEFAULT_HYDRATION_TOLERANCE, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(0.95f, 1.00f, 0.85f, 1.25f), false, false, false, 1, false, yields(5, 5), 0.25f, 1.30f, "Moist cool-temperate berry annual; age 0..7, bare-hand harvest only.");
        crop("cherries", 60.0f, "Cherries", ItemRegistry.CHERRY_SEEDS::get, ItemRegistry.CHERRIES::get, 3, 10, 4, 0.65f, 0.45f, 0.35f, 0.45f, 0.60f, climate(FarmingClimate.TEMPERATE), weights(1.40f, 1.20f, 0.90f, 0.90f), false, true, false, 1, false, yields(2, 5), 0.10f, 1.20f, "Tree crop foundation.");
        crop("cotton", 40.0f, "Cotton", ItemRegistry.COTTON_SEEDS::get, ItemRegistry.COTTON::get, 2, 8, VANILLA_STYLE_AGE_COUNT, 0.65f, 0.35f, 0.45f, 0.70f, 0.50f, 0.45f, 0.25f, climate(FarmingClimate.TEMPERATE, FarmingClimate.TROPICAL), weights(1.30f, 0.80f, 0.95f, 1.35f), false, false, false, 1, false, yields(2, 4), 0.20f, 1.25f, "Industrial warm fiber crop; age 0..7, sensitive to cold and poor nutrients.");
        crop("flax", 20.0f, "Flax", ItemRegistry.FLAX_SEEDS::get, ItemRegistry.FLAX::get, 2, 6, VANILLA_STYLE_AGE_COUNT, 0.35f, 0.25f, 0.30f, 0.45f, 0.55f, 0.40f, DEFAULT_HYDRATION_TOLERANCE, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(0.85f, 0.70f, 0.80f, 1.00f), false, false, false, 1, false, yields(2, 4), 0.25f, 1.15f, "Cool temperate industrial fiber crop; age 0..7.");
        crop("hemp", 50.0f, "Hemp", ItemRegistry.HEMP_SEEDS::get, ItemRegistry.HEMP::get, 2, 6, VANILLA_STYLE_AGE_COUNT, 0.60f, 0.35f, 0.40f, 0.70f, 0.55f, 0.45f, DEFAULT_HYDRATION_TOLERANCE, climate(FarmingClimate.TEMPERATE, FarmingClimate.TROPICAL), weights(1.25f, 0.85f, 0.90f, 1.35f), false, false, false, 1, false, yields(2, 4), 0.20f, 1.20f, "Industrial fiber crop only; age 0..7 with no consumption effects.");
        crop("hops", 60.0f, "Hops", ItemRegistry.HOPS_SEEDS::get, ItemRegistry.HOPS::get, 3, 7, VANILLA_STYLE_AGE_COUNT, 0.55f, 0.45f, 0.65f, 0.75f, 0.70f, climate(FarmingClimate.TEMPERATE), weights(1.00f, 1.20f, 1.60f, 1.60f), true, false, false, 2, false, yields(2, 4), 0.25f, 1.20f, "Perennial trellis bine; age 0..7, two-block model from age 4, cones from age 5, regrows from fruitless age 4 after scissor harvest.");
        crop("snow_peas", 20.0f, "Snow Peas", ItemRegistry.SNOW_PEA_SEEDS::get, ItemRegistry.SNOW_PEAS::get, 2, 5, VANILLA_STYLE_AGE_COUNT, 0.35f, 0.30f, 0.25f, 0.40f, 0.60f, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(0.85f, 0.85f, 0.75f, 0.90f), yields(2, 4), 0.25f, 1.20f, "Cool temperate annual vegetable; age 0..7, no trellis required.");
        crop("peas", 5.0f, "Peas", ItemRegistry.PEA_SEEDS::get, ItemRegistry.PEAS::get, 2, 6, 4, 0.30f, 0.30f, 0.25f, 0.35f, 0.60f, climate(FarmingClimate.TEMPERATE), yields(2, 4), 0.25f, "");
        crop("turnips", 5.0f, "Turnips", ItemRegistry.TURNIP_SEEDS::get, ItemRegistry.TURNIPS::get, 1, 5, VANILLA_STYLE_AGE_COUNT, 0.55f, 0.20f, 0.25f, 0.45f, 0.55f, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(1.30f, 0.70f, 0.80f, 1.00f), yields(1, 3), 0.30f, 0.90f, "Tolerant cool-weather root crop; age 0..7 mapped across five visual assets.");
        crop("apple", 55.0f, "Apple", ItemRegistry.APPLE_SEEDS::get, ItemRegistry.APPLE::get, 3, 10, 4, 0.65f, 0.35f, 0.30f, 0.45f, 0.60f, climate(FarmingClimate.TEMPERATE), weights(1.40f, 1.00f, 0.80f, 0.90f), false, true, false, 1, false, yields(2, 5), 0.10f, 1.15f, "Tree crop foundation.");
        crop("pear", 60.0f, "Pear", ItemRegistry.PEAR_SEEDS::get, ItemRegistry.PEARS::get, 3, 10, 4, 0.60f, 0.35f, 0.30f, 0.45f, 0.65f, climate(FarmingClimate.TEMPERATE), weights(1.30f, 1.00f, 0.80f, 0.90f), false, true, false, 1, false, yields(2, 5), 0.10f, 1.15f, "Tree crop foundation.");
        crop("peach", 55.0f, "Peach", ItemRegistry.PEACH_SEEDS::get, ItemRegistry.PEACHES::get, 3, 10, 4, 0.65f, 0.40f, 0.35f, 0.45f, 0.65f, climate(FarmingClimate.TEMPERATE), weights(1.40f, 1.10f, 0.90f, 0.90f), false, true, false, 1, false, yields(2, 5), 0.10f, 1.15f, "Tree crop foundation.");
        crop("lemon", 70.0f, "Lemon", ItemRegistry.LEMON_SEEDS::get, ItemRegistry.LEMON::get, 3, 10, 4, 0.60f, 0.45f, 0.50f, 0.45f, 0.55f, climate(FarmingClimate.TROPICAL), weights(1.20f, 1.20f, 1.30f, 0.90f), false, true, false, 1, false, yields(2, 5), 0.10f, 1.15f, "Warm citrus tree.");
        crop("lime", 75.0f, "Lime", ItemRegistry.LIME_SEEDS::get, ItemRegistry.LIME::get, 3, 10, 4, 0.50f, 0.45f, 0.50f, 0.45f, 0.70f, climate(FarmingClimate.TROPICAL), weights(1.10f, 1.10f, 1.20f, 0.90f), false, true, false, 1, false, yields(2, 5), 0.10f, 1.15f, "Warm citrus tree.");
        crop("orange", 70.0f, "Orange", ItemRegistry.ORANGE_SEEDS::get, ItemRegistry.ORANGE::get, 3, 10, 4, 0.60f, 0.45f, 0.45f, 0.45f, 0.60f, climate(FarmingClimate.TROPICAL, FarmingClimate.TEMPERATE), weights(1.25f, 1.00f, 1.00f, 0.85f), false, true, false, 1, false, yields(2, 5), 0.10f, 1.30f, "Warm citrus tree.");
        crop("olive", 75.0f, "Olive", ItemRegistry.OLIVE_SEEDS::get, ItemRegistry.OLIVE::get, 3, 10, 4, 0.45f, 0.35f, 0.45f, 0.30f, 0.40f, climate(FarmingClimate.ARID, FarmingClimate.TEMPERATE), weights(1.00f, 0.90f, 1.10f, 0.70f), false, true, false, 1, false, yields(2, 5), 0.10f, 1.10f, "Dry-climate fruit tree.");
        crop("plum", 65.0f, "Plum", ItemRegistry.PLUM_SEEDS::get, ItemRegistry.PLUM::get, 3, 9, 4, 0.55f, 0.40f, 0.35f, 0.45f, 0.62f, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(1.15f, 1.00f, 0.85f, 0.95f), false, true, false, 1, false, yields(2, 5), 0.10f, 1.20f, "Temperate fruit tree; slightly cooler-tolerant than citrus.");
        crop("bell_peppers", 45.0f, "Bell Peppers", ItemRegistry.BELL_PEPPER_SEEDS::get, ItemRegistry.BELL_PEPPERS::get, 2, 6, VANILLA_STYLE_AGE_COUNT, 0.55f, 0.30f, 0.65f, 0.58f, 0.62f, climate(FarmingClimate.TROPICAL, FarmingClimate.TEMPERATE), weights(1.05f, 0.90f, 1.55f, 1.10f), true, false, false, 1, false, yields(2, 4), 0.25f, 1.15f, "Perennial trellis pepper; ash/potash leaning, regrows after harvest.");
        crop("cucumbers", 35.0f, "Cucumbers", ItemRegistry.CUCUMBER_SEEDS::get, ItemRegistry.CUCUMBERS::get, 2, 6, VANILLA_STYLE_AGE_COUNT, 0.50f, 0.25f, 0.25f, 0.65f, 0.82f, climate(FarmingClimate.TEMPERATE, FarmingClimate.TROPICAL), weights(1.00f, 0.80f, 0.80f, 1.35f), true, false, false, 1, false, yields(2, 4), 0.25f, 1.25f, "Perennial high-hydration trellis crop; regrows after harvest.");
        crop("honeydew", 60.0f, "Honeydew", ItemRegistry.HONEYDEW_SEEDS::get, ItemRegistry.HONEYDEW::get, 2, 7, VANILLA_STYLE_AGE_COUNT, 0.55f, 0.30f, 0.30f, 0.70f, 0.86f, climate(FarmingClimate.TROPICAL, FarmingClimate.TEMPERATE), weights(1.10f, 0.85f, 0.85f, 1.45f), true, false, false, 1, false, yields(1, 3), 0.20f, 1.35f, "Warm perennial trellis melon; high hydration, regrows after fruit harvest.");
        crop("cantaloupe", 55.0f, "Cantaloupe", ItemRegistry.CANTALOUPE_SEEDS::get, ItemRegistry.CANTALOUPE::get, 2, 7, VANILLA_STYLE_AGE_COUNT, 0.58f, 0.30f, 0.35f, 0.68f, 0.74f, climate(FarmingClimate.TROPICAL, FarmingClimate.TEMPERATE, FarmingClimate.ARID), weights(1.15f, 0.85f, 0.95f, 1.35f), true, false, false, 1, false, yields(1, 3), 0.20f, 1.30f, "Warm perennial trellis melon; more arid-tolerant than honeydew.");
        crop("banana", 80.0f, "Banana", ItemRegistry.BANANA_SEEDS::get, ItemRegistry.BANANA::get, 3, 9, VANILLA_STYLE_AGE_COUNT, 0.75f, 0.40f, 0.45f, 0.85f, 0.88f, 0.35f, 0.20f, climate(FarmingClimate.TROPICAL), weights(1.50f, 0.95f, 1.00f, 1.70f), false, false, true, 4, false, yields(2, 5), 0.15f, 1.45f, "Small 4-block perennial fruit tree crop using TallCropSupport; regrows after fruit harvest.");
        crop("broccoli", 25.0f, "Broccoli", ItemRegistry.BROCCOLI_SEEDS::get, ItemRegistry.BROCCOLI::get, 2, 6, VANILLA_STYLE_AGE_COUNT, 0.55f, 0.25f, 0.45f, 0.75f, 0.70f, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(1.55f, 0.75f, 1.00f, 1.35f), yields(2, 4), 0.25f, "Cool-weather brassica; age 0..7 with five visual assets mapped across ages.");
        crop("cauliflower", 30.0f, "Cauliflower", ItemRegistry.CAULIFLOWER_SEEDS::get, ItemRegistry.CAULIFLOWER::get, 2, 7, VANILLA_STYLE_AGE_COUNT, 0.45f, 0.35f, 0.45f, 0.70f, 0.70f, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(1.05f, 1.05f, 1.05f, 1.20f), yields(1, 3), 0.25f, 1.30f, "Cool-weather brassica; age 0..7 with five visual assets mapped across ages.");
        crop("rhubarb", 45.0f, "Rhubarb", ItemRegistry.RHUBARB_SEEDS::get, ItemRegistry.RHUBARB::get, 2, 6, VANILLA_STYLE_AGE_COUNT, 0.45f, 0.35f, 0.40f, 0.65f, 0.60f, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(1.10f, 1.00f, 0.95f, 1.25f), yields(2, 4), 0.25f, 1.10f, "Hardy cool-weather stalk crop; age 0..7 with five visual assets mapped across ages.");
        crop("celery", 40.0f, "Celery", ItemRegistry.CELERY_SEEDS::get, ItemRegistry.CELERY::get, 2, 6, VANILLA_STYLE_AGE_COUNT, 0.30f, 0.20f, 0.30f, 0.70f, 0.85f, climate(FarmingClimate.WETLAND, FarmingClimate.TEMPERATE), weights(0.70f, 0.70f, 0.90f, 1.70f), yields(2, 4), 0.25f, 1.35f, "Hydration-sensitive stalk crop; age 0..7 mapped across five visual assets.");
        crop("tobacco", 65.0f, "Tobacco", ItemRegistry.TOBACCO_SEEDS::get, ItemRegistry.TOBACCO::get, 3, 7, VANILLA_STYLE_AGE_COUNT, 0.40f, 0.35f, 0.70f, 0.80f, 0.55f, climate(FarmingClimate.TROPICAL, FarmingClimate.TEMPERATE), weights(0.80f, 1.00f, 1.70f, 1.70f), yields(2, 4), 0.20f, 1.25f, "Warm-climate ash and nitrogen heavy leaf crop; age 0..7 mapped across five visual assets.");
        crop("radish", 5.0f, "Radish", ItemRegistry.RADISH_SEEDS::get, ItemRegistry.RADISH::get, 1, 4, VANILLA_STYLE_AGE_COUNT, 0.35f, 0.20f, 0.25f, 0.35f, 0.55f, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(0.85f, 0.70f, 0.70f, 0.80f), yields(1, 3), 0.35f, 0.85f, "Fast tolerant cool-weather root crop; age 0..7 mapped across five visual assets.");
        crop("parsnip", 15.0f, "Parsnip", ItemRegistry.PARSNIP_SEEDS::get, ItemRegistry.PARSNIP::get, 1, 5, VANILLA_STYLE_AGE_COUNT, 0.45f, 0.20f, 0.25f, 0.45f, 0.55f, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(1.10f, 0.70f, 0.80f, 1.00f), yields(1, 3), 0.30f, 0.95f, "Cold-tolerant root crop; age 0..7 mapped across five visual assets.");
        crop("yam", 35.0f, "Yam", ItemRegistry.YAM_SEEDS::get, ItemRegistry.YAM::get, 2, 6, VANILLA_STYLE_AGE_COUNT, 0.50f, 0.25f, 0.30f, 0.55f, 0.60f, climate(FarmingClimate.TROPICAL, FarmingClimate.TEMPERATE), weights(1.15f, 0.75f, 0.85f, 1.10f), yields(1, 3), 0.25f, 1.10f, "Warm-weather one-block annual root crop; age 0..7 mapped across five visual assets.");
        crop("rutabaga", 35.0f, "Rutabaga", ItemRegistry.RUTABAGA_SEEDS::get, ItemRegistry.RUTABAGA::get, 1, 6, VANILLA_STYLE_AGE_COUNT, 0.50f, 0.20f, 0.25f, 0.45f, 0.55f, climate(FarmingClimate.TEMPERATE, FarmingClimate.ICE), weights(1.20f, 0.70f, 0.80f, 1.00f), yields(1, 3), 0.30f, 0.90f, "Durable cool-weather root crop; age 0..7 mapped across five visual assets.");
        crop("grapes", 80.0f, "Grapes", ItemRegistry.GRAPE_SEEDS::get, ItemRegistry.GRAPES::get, 3, 8, VANILLA_STYLE_AGE_COUNT, 0.65f, 0.65f, 0.45f, 0.40f, 0.60f, climate(FarmingClimate.TEMPERATE), weights(1.45f, 1.80f, 1.05f, 0.80f), false, false, true, 4, false, yields(8, 12), 0.20f, 1.50f, "Perennial three-block grape arbor; variety-aware, planted only into Britannia farming plots.");
    }

    private static void crop(String id, float minimumFarmingSkill, String name, Supplier<? extends Item> seed, Supplier<? extends Item> harvest, int tier,
                             int growthTicks, int stages, float boneMeal, float turquoise, float ash, float rottenFlesh,
                             float hydration, Set<FarmingClimate> climates, Yield yield, float seedReturnChance, String notes) {
        crop(id, minimumFarmingSkill, name, seed, harvest, tier, growthTicks, stages, boneMeal, turquoise, ash, rottenFlesh, hydration,
                DEFAULT_NUTRIENT_TOLERANCE, DEFAULT_HYDRATION_TOLERANCE, climates, weights(1.0f, 1.0f, 1.0f, 1.0f),
                false, false, false, 1, false, yield, seedReturnChance, 1.0f, notes);
    }

    private static void crop(String id, float minimumFarmingSkill, String name, Supplier<? extends Item> seed, Supplier<? extends Item> harvest, int tier,
                             int growthTicks, int stages, float boneMeal, float turquoise, float ash, float rottenFlesh,
                             float hydration, Set<FarmingClimate> climates, Weights weights, Yield yield,
                             float seedReturnChance, String notes) {
        crop(id, minimumFarmingSkill, name, seed, harvest, tier, growthTicks, stages, boneMeal, turquoise, ash, rottenFlesh, hydration,
                DEFAULT_NUTRIENT_TOLERANCE, DEFAULT_HYDRATION_TOLERANCE, climates, weights,
                false, false, false, 1, false, yield, seedReturnChance, 1.0f, notes);
    }

    private static void crop(String id, float minimumFarmingSkill, String name, Supplier<? extends Item> seed, Supplier<? extends Item> harvest, int tier,
                             int growthTicks, int stages, float boneMeal, float turquoise, float ash, float rottenFlesh,
                             float hydration, Set<FarmingClimate> climates, Weights weights, Yield yield,
                             float seedReturnChance, float qualitySensitivity, String notes) {
        crop(id, minimumFarmingSkill, name, seed, harvest, tier, growthTicks, stages, boneMeal, turquoise, ash, rottenFlesh, hydration,
                DEFAULT_NUTRIENT_TOLERANCE, DEFAULT_HYDRATION_TOLERANCE, climates, weights,
                false, false, false, 1, false, yield, seedReturnChance, qualitySensitivity, notes);
    }

    private static void crop(String id, float minimumFarmingSkill, String name, Supplier<? extends Item> seed, Supplier<? extends Item> harvest, int tier,
                             int growthTicks, int stages, float boneMeal, float turquoise, float ash, float rottenFlesh,
                             float hydration, Set<FarmingClimate> climates, boolean vanillaMapped, Yield yield,
                             float seedReturnChance, String notes) {
        crop(id, minimumFarmingSkill, name, seed, harvest, tier, growthTicks, stages, boneMeal, turquoise, ash, rottenFlesh, hydration,
                DEFAULT_NUTRIENT_TOLERANCE, DEFAULT_HYDRATION_TOLERANCE, climates, weights(1.0f, 1.0f, 1.0f, 1.0f),
                false, false, false, 1, vanillaMapped, yield, seedReturnChance, 1.0f, notes);
    }

    private static void crop(String id, float minimumFarmingSkill, String name, Supplier<? extends Item> seed, Supplier<? extends Item> harvest, int tier,
                             int growthTicks, int stages, float boneMeal, float turquoise, float ash, float rottenFlesh,
                             float hydration, Set<FarmingClimate> climates, Weights weights, boolean vanillaMapped,
                             Yield yield, float seedReturnChance, String notes) {
        crop(id, minimumFarmingSkill, name, seed, harvest, tier, growthTicks, stages, boneMeal, turquoise, ash, rottenFlesh, hydration,
                DEFAULT_NUTRIENT_TOLERANCE, DEFAULT_HYDRATION_TOLERANCE, climates, weights,
                false, false, false, 1, vanillaMapped, yield, seedReturnChance, 1.0f, notes);
    }

    private static void crop(String id, float minimumFarmingSkill, String name, Supplier<? extends Item> seed, Supplier<? extends Item> harvest, int tier,
                             int growthTicks, int stages, float boneMeal, float turquoise, float ash, float rottenFlesh,
                             float hydration, Set<FarmingClimate> climates, Weights weights, boolean requiresLattice,
                             boolean treeCrop, boolean tallCrop, int maxHeight, boolean vanillaMapped, Yield yield,
                             float seedReturnChance, float qualitySensitivity, String notes) {
        crop(id, minimumFarmingSkill, name, seed, harvest, tier, growthTicks, stages, boneMeal, turquoise, ash, rottenFlesh, hydration,
                DEFAULT_NUTRIENT_TOLERANCE, DEFAULT_HYDRATION_TOLERANCE, climates, weights,
                requiresLattice, treeCrop, tallCrop, maxHeight, vanillaMapped, yield, seedReturnChance, qualitySensitivity, notes);
    }

    private static void crop(String id, float minimumFarmingSkill, String name, Supplier<? extends Item> seed, Supplier<? extends Item> harvest, int tier,
                             int growthTicks, int stages, float boneMeal, float turquoise, float ash, float rottenFlesh,
                             float hydration, float nutrientTolerance, float hydrationTolerance, Set<FarmingClimate> climates,
                             Weights weights, boolean requiresLattice, boolean treeCrop, boolean tallCrop, int maxHeight,
                             boolean vanillaMapped, Yield yield, float seedReturnChance, float qualitySensitivity, String notes) {
        register(new CropDefinition(
                id,
                minimumFarmingSkill,
                name,
                seed,
                harvest,
                tier,
                growthTicks,
                stages,
                boneMeal,
                turquoise,
                ash,
                rottenFlesh,
                nutrientTolerance,
                weights.boneMeal,
                weights.turquoise,
                weights.ash,
                weights.rottenFlesh,
                hydration,
                hydrationTolerance,
                hydrationUnderTolerance(id, hydration, hydrationTolerance),
                hydrationOverTolerance(id, hydration, hydrationTolerance),
                minHydrationToGrow(id, hydration, hydrationTolerance),
                maxHydrationBeforeSeverePenalty(id, hydration, hydrationTolerance),
                climates,
                allowedClimates(id),
                forbiddenClimates(id),
                minAltitude(id),
                maxAltitude(id),
                0.55f,
                requiresLattice,
                treeCrop,
                tallCrop,
                Math.max(1, maxHeight),
                vanillaMapped,
                yield.min,
                yield.max,
                1.0f,
                seedReturnChance,
                qualitySensitivity,
                nutrientPreferenceMode(id),
                cropLifecycle(id, requiresLattice, treeCrop),
                growthHabit(id, requiresLattice, treeCrop, tallCrop),
                supportRequirement(id, requiresLattice, treeCrop),
                postHarvestRegrowthAge(id),
                rootAgeContributesToQuality(id),
                rootAgeBonusMaturityDays(id),
                maxRootAgeQualityBonus(id),
                harvestTool(id),
                notes
        ));
    }

    private static Set<FarmingClimate> climate(FarmingClimate... climates) {
        return Set.of(climates);
    }

    private static Set<FarmingClimate> allowedClimates(String id) {
        if (FruitTreeRegistry.byId(id).isPresent()) {
            return FruitTreeRegistry.byIdOrDefault(id).allowedClimates();
        }
        return Set.of();
    }

    private static Set<FarmingClimate> forbiddenClimates(String id) {
        return switch (id) {
            case "corn" -> Set.of(FarmingClimate.ICE);
            case "pumpkin", "watermelon" -> Set.of(FarmingClimate.ICE, FarmingClimate.FIRE);
            case "broccoli", "cauliflower", "lettuce", "rhubarb", "cabbage",
                    "yellow_onion", "green_onion", "garlic", "turnips", "radish", "parsnip", "rutabaga",
                    "carrot", "potato" -> Set.of(FarmingClimate.FIRE);
            case "tobacco", "yam", "cotton", "hemp", "pineapple",
                    "banana", "honeydew", "cantaloupe" -> Set.of(FarmingClimate.ICE);
            case "snow_peas", "flax", "strawberry", "blueberry", "raspberry", "blackberry",
                    "huckleberry", "elderberry" -> Set.of(FarmingClimate.FIRE);
            case "celery", "cranberry" -> Set.of(FarmingClimate.FIRE, FarmingClimate.ARID);
            case "orange", "lime", "peach" -> Set.of(FarmingClimate.ICE, FarmingClimate.FIRE);
            case "lemon" -> Set.of(FarmingClimate.ICE);
            case "pear", "apple", "cherries", "plum" -> Set.of(FarmingClimate.FIRE);
            case "olive" -> Set.of(FarmingClimate.ICE, FarmingClimate.WETLAND);
            case "wheat", "rye", "barley", "oats", "mustard" -> Set.of(FarmingClimate.FIRE);
            case "beans" -> Set.of(FarmingClimate.ICE, FarmingClimate.FIRE);
            default -> Set.of();
        };
    }

    private static int minAltitude(String id) {
        return switch (id) {
            case "wheat", "barley", "oats", "rye", "mustard", "corn" -> 50;
            case "pumpkin", "watermelon" -> 45;
            case "lettuce", "celery", "yam", "cotton", "snow_peas", "flax", "hemp",
                    "strawberry", "blueberry", "raspberry", "cranberry", "blackberry",
                    "mulberry", "elderberry" -> 40;
            case "pineapple", "banana", "honeydew", "cantaloupe" -> 35;
            case "broccoli", "cauliflower", "cabbage" -> 45;
            case "rhubarb" -> 55;
            case "orange", "pear", "peach", "apple", "plum" -> 50;
            case "lime", "lemon" -> 45;
            case "cherries" -> 60;
            case "olive" -> 40;
            case "rice" -> 45;
            case "ginseng" -> 60;
            case "nightshade", "brown_mushroom", "red_mushroom" -> -64;
            case "mandrake" -> 50;
            case "grapes", "huckleberry" -> 55;
            default -> 45;
        };
    }

    private static int maxAltitude(String id) {
        return switch (id) {
            case "wheat", "barley", "oats", "rye", "mustard", "corn" -> 120;
            case "pumpkin", "potato", "carrot" -> 130;
            case "watermelon", "pineapple", "cranberry", "honeydew", "cantaloupe" -> 100;
            case "lettuce", "yellow_onion", "green_onion", "garlic", "turnips", "radish", "parsnip", "rutabaga" -> 120;
            case "squash", "tobacco" -> 130;
            case "celery", "yam", "cotton" -> 100;
            case "strawberry", "blueberry", "raspberry", "blackberry", "huckleberry",
                    "mulberry", "elderberry" -> 130;
            case "broccoli", "cauliflower", "cabbage" -> 140;
            case "rhubarb" -> 160;
            case "rice", "nightshade", "brown_mushroom", "red_mushroom" -> 80;
            case "ginseng" -> 125;
            case "orange", "banana" -> 110;
            case "lime", "mandrake" -> 100;
            case "lemon", "peach" -> 120;
            case "pear" -> 140;
            case "apple", "cherries", "plum" -> 150;
            case "olive" -> 130;
            case "grapes" -> 140;
            default -> 140;
        };
    }

    private static Weights weights(float boneMeal, float turquoise, float ash, float rottenFlesh) {
        return new Weights(boneMeal, turquoise, ash, rottenFlesh);
    }

    private static Yield yields(int min, int max) {
        return new Yield(min, max);
    }

    private static CropLifecycle cropLifecycle(String id, boolean requiresLattice, boolean treeCrop) {
        if (perennialTrellisCrop(id)) {
            return CropLifecycle.TRELLIS;
        }
        if ("grapes".equals(id) || "banana".equals(id)) {
            return CropLifecycle.PERENNIAL;
        }
        if (treeCrop) {
            return CropLifecycle.TREE;
        }
        return CropLifecycle.ANNUAL;
    }

    private static CropGrowthHabit growthHabit(String id, boolean requiresLattice, boolean treeCrop, boolean tallCrop) {
        if ("grapes".equals(id)) {
            return CropGrowthHabit.GRAPE_VINE;
        }
        if (perennialTrellisCrop(id)) {
            return CropGrowthHabit.TRELLIS_CROP;
        }
        if ("banana".equals(id)) {
            return CropGrowthHabit.SMALL_FRUIT_TREE;
        }
        if (treeCrop) {
            return CropGrowthHabit.FRUIT_TREE;
        }
        if (tallCrop) {
            return CropGrowthHabit.TALL_CROP;
        }
        return CropGrowthHabit.GROUND;
    }

    private static CropSupportRequirement supportRequirement(String id, boolean requiresLattice, boolean treeCrop) {
        if (perennialTrellisCrop(id)) {
            return CropSupportRequirement.TRELLIS;
        }
        if (treeCrop) {
            return CropSupportRequirement.TREE_STRUCTURE;
        }
        return requiresLattice ? CropSupportRequirement.LATTICE : CropSupportRequirement.NONE;
    }

    private static int postHarvestRegrowthAge(String id) {
        return switch (id) {
            case "grapes", "hops", "tomato", "bell_peppers", "cucumbers", "honeydew", "cantaloupe" -> 4;
            case "banana" -> 5;
            default -> 0;
        };
    }

    private static boolean rootAgeContributesToQuality(String id) {
        return "grapes".equals(id) || "banana".equals(id) || perennialTrellisCrop(id);
    }

    private static int rootAgeBonusMaturityDays(String id) {
        return switch (id) {
            case "grapes" -> 45;
            case "banana" -> 60;
            case "tomato", "bell_peppers", "hops", "cucumbers", "honeydew", "cantaloupe" -> 30;
            default -> 0;
        };
    }

    private static int maxRootAgeQualityBonus(String id) {
        return switch (id) {
            case "grapes", "banana" -> 15;
            case "tomato", "bell_peppers",  "hops","cucumbers", "honeydew", "cantaloupe" -> 12;
            default -> 0;
        };
    }

    private static boolean perennialTrellisCrop(String id) {
        return switch (id) {
            case "tomato", "bell_peppers",  "hops", "cucumbers", "honeydew", "cantaloupe" -> true;
            default -> false;
        };
    }

    private static NutrientPreferenceMode nutrientPreferenceMode(String id) {
        return switch (id) {
            case "mandrake", "nightshade" -> NutrientPreferenceMode.MAXIMUM;
            default -> NutrientPreferenceMode.BALANCED;
        };
    }

    private static boolean scissorsHarvestCrop(String id) {
        return switch (id) {
            case "corn", "hops", "broccoli", "cauliflower", "lettuce", "rhubarb", "cabbage",
                    "squash", "yellow_onion", "green_onion", "ginseng", "turnips",
                    "celery", "tobacco", "radish", "parsnip", "rutabaga",
                    "pumpkin", "watermelon", "snow_peas", "cotton", "hemp", "grapes" -> true;
            default -> false;
        };
    }

    private static CropHarvestTool harvestTool(String id) {
        if (perennialTrellisCrop(id)) {
            return CropHarvestTool.SCISSORS;
        }
        if (bareHandHarvestCrop(id)) {
            return CropHarvestTool.BARE_HAND;
        }
        if (RootCropShovelTools.isRootCrop(id)) {
            return CropHarvestTool.ROOT_SHOVEL;
        }
        if (GrainHarvestTools.isGrainCrop(id)) {
            return CropHarvestTool.GRAIN_BLADE;
        }
        return scissorsHarvestCrop(id) ? CropHarvestTool.SCISSORS : CropHarvestTool.HAND;
    }

    private static boolean bareHandHarvestCrop(String id) {
        // Grapes are deliberately absent: bunches are cut from the arbor with scissors, as they were
        // on the vine this crop replaced.
        return switch (id) {
            case "pineapple", "strawberry", "blueberry", "raspberry", "cranberry",
                    "blackberry", "huckleberry", "mulberry", "elderberry",
                    "banana" -> true;
            default -> false;
        };
    }

    private static float hydrationUnderTolerance(String id, float hydration, float hydrationTolerance) {
        if ("corn".equals(id)) {
            return 0.40f;
        }
        if (FruitTreeRegistry.byId(id).isPresent()) {
            return 0.35f;
        }
        return hydrationTolerance;
    }

    private static float hydrationOverTolerance(String id, float hydration, float hydrationTolerance) {
        if ("corn".equals(id)) {
            return 0.25f;
        }
        if (FruitTreeRegistry.byId(id).isPresent()) {
            return 0.25f;
        }
        return Math.max(0.15f, hydrationTolerance * 0.75f);
    }

    private static float minHydrationToGrow(String id, float hydration, float hydrationTolerance) {
        if ("corn".equals(id)) {
            return 0.20f;
        }
        return Math.max(0.0f, hydration - hydrationTolerance);
    }

    private static float maxHydrationBeforeSeverePenalty(String id, float hydration, float hydrationTolerance) {
        if ("corn".equals(id)) {
            return 0.90f;
        }
        return Math.min(1.0f, hydration + hydrationTolerance);
    }

    private static void register(CropDefinition crop) {
        CROPS.add(crop);
        BY_ID.put(normalize(crop.id()), crop);
        if ("carrot".equals(crop.id())) {
            BY_ID.put("carrots", crop);
        }
        if ("yam".equals(crop.id())) {
            BY_ID.put("sweet_potato", crop);
        }
        if ("mandrake".equals(crop.id())) {
            BY_ID.put("mandrake_root", crop);
        }
        if ("strawberry".equals(crop.id())) {
            BY_ID.put("strawberries", crop);
        }
        if ("blueberry".equals(crop.id())) {
            BY_ID.put("blueberries", crop);
        }
    }

    private static String normalize(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT);
    }

    private record Weights(float boneMeal, float turquoise, float ash, float rottenFlesh) {
    }

    private record Yield(int min, int max) {
    }
}
