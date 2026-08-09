package com.seggellion.britannia_mod.farming;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.client.renderer.FlowerVisualModels;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowerSpeciesContentTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir"));
    private static final Path RESOURCES = PROJECT.resolve("src/main/resources");
    private static final Path ASSETS = RESOURCES.resolve("assets/britannia_mod");
    private static final float EPSILON = 0.0001F;
    private static final Map<ResourceLocation, ExpectedSpecies> EXPECTED = expectedSpecies();

    private final FlowerRegistry registry = FlowerRegistry.initial();

    @Test
    void everyInitialSpeciesMatchesApprovedIdentityProfileTimingAndStageLimits() {
        assertEquals(FlowerRegistry.INITIAL_SPECIES_IDS, EXPECTED.keySet());
        assertEquals(EXPECTED.keySet(), registry.definitions().keySet());

        for (ExpectedSpecies expected : EXPECTED.values()) {
            FlowerDefinition definition = registry.byId(expected.id()).orElseThrow();
            FlowerGrowthProfile profile = definition.growthProfile();
            assertEquals(expected.flowerItem(), definition.harvestedItemId());
            assertEquals(expected.seedItem(), definition.seedItemId());
            assertEquals(definition, registry.byHarvestedItemId(expected.flowerItem()).orElseThrow());
            assertEquals(definition, registry.bySeedItemId(expected.seedItem()).orElseThrow());
            assertEquals(expected.naturalMaximum(), definition.naturalMaximumStage());
            assertEquals(expected.absoluteMaximum(), definition.absoluteMaximumStage());
            assertEquals(expected.baseGrowthTicks(), profile.baseGrowthTicks());
            assertEquals(expected.preferred(), profile.idealClimates());
            assertEquals(union(expected.preferred(), expected.tolerated()), profile.allowedClimates());
            assertEquals(expected.unsuitable(), profile.forbiddenClimates());
            assertEquals(expected.idealMinAltitude(), profile.idealMinAltitude());
            assertEquals(expected.idealMaxAltitude(), profile.idealMaxAltitude());
            assertEquals(expected.minAltitude(), profile.minAltitude());
            assertEquals(expected.maxAltitude(), profile.maxAltitude());

            float hydrationIdeal = midpoint(expected.hydrationIdealMin(), expected.hydrationIdealMax());
            assertEquals(hydrationIdeal, profile.hydrationIdeal(), EPSILON);
            assertEquals(expected.hydrationToleratedMin(), profile.minHydrationToGrow(), EPSILON);
            assertEquals(expected.hydrationToleratedMax(), profile.maxHydrationBeforeSeverePenalty(), EPSILON);
            assertEquals(hydrationIdeal - expected.hydrationToleratedMin(), profile.hydrationUnderTolerance(), EPSILON);
            assertEquals(expected.hydrationToleratedMax() - hydrationIdeal, profile.hydrationOverTolerance(), EPSILON);

            float nutrientIdeal = midpoint(expected.nutrientIdealMin(), expected.nutrientIdealMax());
            float sharedTolerance = Math.max(
                    nutrientIdeal - expected.nutrientToleratedMin(),
                    expected.nutrientToleratedMax() - nutrientIdeal
            );
            assertEquals(nutrientIdeal, profile.idealBoneMeal(), EPSILON);
            assertEquals(nutrientIdeal, profile.idealTurquoise(), EPSILON);
            assertEquals(nutrientIdeal, profile.idealSulphurousAsh(), EPSILON);
            assertEquals(nutrientIdeal, profile.idealRottenFlesh(), EPSILON);
            assertEquals(sharedTolerance, profile.nutrientTolerance(), EPSILON);
            assertEquals(NutrientPreferenceMode.BALANCED, profile.nutrientPreferenceMode());
        }
    }

    @Test
    void everyPaletteMatchesDesignCountsWeightsFallbackAndExplicitAllowlist() {
        assertTrue(FlowerColor.CAPACITY >= 2_000_000);
        WeightedFlowerColorSelector selector = new WeightedFlowerColorSelector(registry);

        for (ExpectedSpecies expected : EXPECTED.values()) {
            FlowerDefinition definition = registry.byId(expected.id()).orElseThrow();
            assertEquals(expected.paletteCount(), definition.palette().size(), definition.id().toString());
            assertEquals(expected.fallbackColor(), definition.fallbackColorId());
            assertEquals(100, definition.palette().stream().mapToInt(FlowerPaletteEntry::weight).sum());
            assertTrue(definition.palette().size() < registry.colors().size());

            Set<ResourceLocation> colorIds = new HashSet<>();
            Set<Integer> tintValues = new HashSet<>();
            Set<FlowerColor> allowed = new HashSet<>();
            for (FlowerPaletteEntry entry : definition.palette()) {
                assertTrue(entry.weight() > 0);
                assertFalse(entry.rarityLabel().isBlank());
                assertTrue(colorIds.add(entry.colorId()));
                FlowerColor color = registry.color(entry.colorId()).orElseThrow().color();
                assertTrue(color.isValidTint());
                assertTrue(tintValues.add(color.tintValue()));
                allowed.add(color);
            }
            assertTrue(colorIds.contains(definition.fallbackColorId()));
            assertTrue(allowed.contains(registry.fallbackColor(definition)));

            RandomSource random = RandomSource.create(0x5EEDL + definition.id().hashCode());
            FlowerPlantingContext planting = practicalContext(definition, true);
            for (int sample = 0; sample < 2_000; sample++) {
                FlowerColor selected = selector.select(
                        definition, planting, random, FlowerColorSelectionReason.SUCCESSFUL_SERVER_PLANTING
                );
                assertTrue(allowed.contains(selected), definition.id().toString());
            }
        }
    }

    @Test
    void everySpeciesHasPracticalPositiveGrowthAndMeaningfullyLowerConditions() {
        for (ExpectedSpecies expected : EXPECTED.values()) {
            FlowerDefinition definition = registry.byId(expected.id()).orElseThrow();
            FlowerPersistentState state = practicalState(definition);
            int altitude = midpoint(expected.idealMinAltitude(), expected.idealMaxAltitude());
            FarmingClimate preferred = expected.preferred().iterator().next();
            FarmingClimate tolerated = expected.tolerated().iterator().next();
            FarmingClimate unsuitable = expected.unsuitable().iterator().next();

            FlowerGrowthEvaluation ideal = FlowerGrowthEvaluator.evaluate(state, definition, preferred, altitude);
            FlowerGrowthEvaluation lower = FlowerGrowthEvaluator.evaluate(state, definition, tolerated, altitude);
            FlowerGrowthEvaluation blocked = FlowerGrowthEvaluator.evaluate(state, definition, unsuitable, altitude);
            assertTrue(ideal.multiplier() > 0.0F, definition.id().toString());
            assertTrue(lower.multiplier() > 0.0F, definition.id().toString());
            assertTrue(lower.multiplier() < ideal.multiplier(), definition.id().toString());
            assertEquals(0.0F, blocked.multiplier(), EPSILON);
            assertEquals(FlowerGrowthEvaluation.BlockReason.UNSUITABLE_CLIMATE, blocked.blockingReason());
            assertTrue(ideal.farmingContext().specialEnvironmentAllowed());
            assertTrue(ideal.farmingContext().latticeSatisfied());

            int evaluations = 0;
            while (!FlowerGrowthEvaluator.isMature(state, definition) && evaluations++ < 64) {
                state = FlowerGrowthEvaluator.advance(
                        state, FlowerGrowthEvaluator.evaluate(state, definition, preferred, altitude)
                );
            }
            assertTrue(FlowerGrowthEvaluator.isMature(state, definition), definition.id().toString());
            assertEquals(definition.naturalMaximumStage(), state.growthStage(), definition.id().toString());
        }
    }

    @Test
    void playerFacingItemsLocalizationCreativeGroupsAndSkinningKnifeAreComplete() throws IOException {
        JsonObject language = json(ASSETS.resolve("lang/en_us.json"));
        String items = source("registry/ItemRegistry.java");
        String creative = source("registry/CreativeTabRegistry.java");
        String seeds = between(creative, "FARMING_SEEDS = List.of(", "FARMING_PRODUCE = List.of(");
        String produce = creative.substring(creative.indexOf("FARMING_PRODUCE = List.of("));

        for (ExpectedSpecies expected : EXPECTED.values()) {
            String constant = expected.id().getPath().toUpperCase(java.util.Locale.ROOT);
            assertTrue(items.contains(constant + " = harvestedFlowerItem(\"" + expected.id().getPath() + "\")"));
            assertTrue(items.contains(constant + "_SEEDS = flowerContentItem(\"" + expected.id().getPath() + "_seeds\")"));
            assertTrue(language.has(languageKey(expected.flowerItem())));
            assertTrue(language.has(languageKey(expected.seedItem())));
            assertEquals(expected.displayName(), language.get(languageKey(expected.flowerItem())).getAsString());
            assertEquals(expected.displayName() + " Seeds", language.get(languageKey(expected.seedItem())).getAsString());
            assertEquals(1, count(seeds, "ItemRegistry." + constant + "_SEEDS"));
            assertEquals(1, count(produce, "ItemRegistry." + constant));
            assertItemAsset(expected.flowerItem());
            assertItemAsset(expected.seedItem());
        }

        assertEquals(EXPECTED.values().stream().map(value -> value.flowerItem().toString()).collect(java.util.stream.Collectors.toSet()),
                tagValues("flowers.json"));
        assertEquals(EXPECTED.values().stream().map(value -> value.seedItem().toString()).collect(java.util.stream.Collectors.toSet()),
                tagValues("flower_seeds.json"));
        assertTrue(items.contains("SKINNING_KNIFE = ITEMS.register(\"skinning_knife\""));
        assertTrue(items.contains("new Item.Properties().durability(128)"));
        assertTrue(creative.contains("safeAccept(output, ItemRegistry.SKINNING_KNIFE.get())"));
        assertEquals("Skinning Knife", language.get("item.britannia_mod.skinning_knife").getAsString());
        assertEquals(Set.of("britannia_mod:skinning_knife"), tagValuesAt(
                "data/britannia_mod/tags/item/skinning_knives.json"));
        assertItemAsset(id("skinning_knife"));
    }

    @Test
    void interactionFeedbackIsLocalizedAndHarvestSeedRecoveryRemainsSpeciesMapped() throws IOException {
        JsonObject language = json(ASSETS.resolve("lang/en_us.json"));
        String service = source("farming/FlowerInteractionService.java");
        String harvestedItem = source("item/HarvestedFlowerItem.java");
        assertEquals("That flower is protected.",
                language.get("message.britannia_mod.flower.protected").getAsString());
        assertEquals("Poppy stage 7 requires Farming 100 and an authorized skinning knife.",
                language.get("message.britannia_mod.flower.poppy_stage_seven_requirements").getAsString());
        assertTrue(service.contains("PROTECTED_MESSAGE_KEY = \"message.britannia_mod.flower.protected\""));
        assertTrue(service.contains("Component.translatable(messageKey)"));
        assertTrue(service.contains("Component.translatable(POPPY_STAGE_SEVEN_REQUIREMENTS_KEY)"));
        assertFalse(service.contains("Component.literal(\"That flower is protected."));
        assertFalse(service.contains("Component.literal(\"Poppy stage 7 requires"));
        assertTrue(service.contains("new ItemStack(BuiltInRegistries.ITEM.get(definition.harvestedItemId()))"));
        assertTrue(service.contains("CropQualityCalculator.applyQuality"));
        assertTrue(service.contains("FruitProvenance.setRegionName"));
        assertTrue(service.contains("harvestAndReset"));
        assertTrue(harvestedItem.contains("FlowerRegistry.initial().byHarvestedItemId"));
        assertTrue(harvestedItem.contains("new ItemStack(BuiltInRegistries.ITEM.get(definition.seedItemId()))"));
        assertTrue(harvestedItem.contains("CropSeedExtractor.tryExtractSeed"));
    }

    @Test
    void everySpeciesHasStagesOneThroughSevenAndExactlyTwoInWorldTexturesPerStage() throws IOException {
        Path models = ASSETS.resolve("models/block/flowers");
        Path textures = ASSETS.resolve("textures/block/flowers");
        assertEquals(49, countSuffix(models, ".json"));
        assertEquals(0, countSuffix(models, "_base.json"));
        assertEquals(0, countSuffix(models, "_dye_mask.json"));
        assertEquals(49, countSuffix(textures, "_base_texture.png"));
        assertEquals(49, countSuffix(textures, "_dye_mask.png"));

        for (ExpectedSpecies expected : EXPECTED.values()) {
            for (int stage = 1; stage <= 7; stage++) {
                FlowerVisualModels.StageModel model = FlowerVisualModels.stageModel(expected.id(), stage);
                assertTrue(Files.isRegularFile(models.resolve(model.canonicalId().getPath().substring("block/flowers/".length()) + ".json")));
                assertTrue(Files.isRegularFile(textures.resolve(expected.id().getPath() + "/stage_" + stage + "_base_texture.png")));
                assertTrue(Files.isRegularFile(textures.resolve(expected.id().getPath() + "/stage_" + stage + "_dye_mask.png")));
            }
            assertFalse(Files.exists(models.resolve(expected.id().getPath() + "/stage_8.json")));
            assertFalse(Files.exists(textures.resolve(expected.id().getPath() + "/stage_8_base_texture.png")));
        }
    }

    @Test
    void noUnapprovedRecipeLootOrWorldGenerationAcquisitionWasAdded() throws IOException {
        Set<String> ids = new HashSet<>();
        for (ExpectedSpecies expected : EXPECTED.values()) {
            ids.add(expected.flowerItem().toString());
            ids.add(expected.seedItem().toString());
        }
        ids.add("britannia_mod:skinning_knife");

        List<Path> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(RESOURCES.resolve("data"))) {
            for (Path path : paths.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(".json")).toList()) {
                String normalized = path.toString().replace('\\', '/');
                if (!(normalized.contains("/recipe/") || normalized.contains("/recipes/")
                        || normalized.contains("/loot_table/") || normalized.contains("/loot_tables/")
                        || normalized.contains("/worldgen/"))) {
                    continue;
                }
                String text = Files.readString(path, StandardCharsets.UTF_8);
                if (ids.stream().anyMatch(text::contains)) {
                    violations.add(PROJECT.relativize(path));
                }
            }
        }
        assertTrue(violations.isEmpty(), violations.toString());
    }

    private FlowerPersistentState practicalState(FlowerDefinition definition) {
        FlowerGrowthProfile profile = definition.growthProfile();
        int hydration = Math.max(1, Math.min(5, Math.round(profile.hydrationIdeal() * 5.0F)));
        FlowerSoilSnapshot soil = FlowerSoilSnapshot.privateSoil(
                hydration, 0,
                profile.idealBoneMeal(), profile.idealTurquoise(),
                profile.idealSulphurousAsh(), profile.idealRottenFlesh()
        );
        return new FlowerPersistentState(
                FlowerPersistentState.CURRENT_DATA_VERSION,
                definition.id(), registry.fallbackColor(definition), 1,
                FlowerPlantingOrigin.PLAYER, false,
                Optional.of(UUID.fromString("ce254c18-4c75-45a4-9764-d4b5855dd625")),
                new FlowerRegionProvenance("Practical test farmland", definition.growthProfile().idealClimates().iterator().next()),
                new FlowerQuality(50), soil, FlowerGrowthState.newlyPlanted()
        );
    }

    private static FlowerPlantingContext practicalContext(FlowerDefinition definition, boolean logicalServer) {
        FlowerGrowthProfile profile = definition.growthProfile();
        int hydration = Math.max(1, Math.min(5, Math.round(profile.hydrationIdeal() * 5.0F)));
        FarmingClimate climate = profile.idealClimates().iterator().next();
        return new FlowerPlantingContext(
                FlowerSoilSnapshot.privateSoil(
                        hydration, profile.idealBoneMeal(), profile.idealTurquoise(),
                        profile.idealSulphurousAsh(), profile.idealRottenFlesh()
                ),
                new FlowerRegionProvenance("Practical test farmland", climate),
                climate,
                midpoint(profile.idealMinAltitude(), profile.idealMaxAltitude()),
                FlowerPlantingOrigin.PLAYER,
                logicalServer
        );
    }

    private static Map<ResourceLocation, ExpectedSpecies> expectedSpecies() {
        Map<ResourceLocation, ExpectedSpecies> values = new LinkedHashMap<>();
        add(values, "poppy", "Poppy", 6, 7, 5, 8, "scarlet",
                .30F, .55F, .15F, .70F, .35F, .65F, .15F, .80F,
                45, 125, 20, 170, climates(FarmingClimate.TEMPERATE), climates(FarmingClimate.ARID));
        add(values, "snowdrop", "Snowdrop", 7, 7, 7, 4, "snow_white",
                .50F, .75F, .35F, .90F, .50F, .80F, .30F, .95F,
                55, 155, 30, 220, climates(FarmingClimate.ICE), climates(FarmingClimate.TEMPERATE, FarmingClimate.WETLAND));
        add(values, "lily", "Lily", 7, 7, 8, 10, "snow_white",
                .45F, .70F, .30F, .85F, .60F, .85F, .40F, 1.00F,
                45, 150, 20, 200, climates(FarmingClimate.TEMPERATE), climates(FarmingClimate.ICE));
        add(values, "foxglove", "Foxglove", 7, 7, 9, 9, "violet",
                .50F, .75F, .35F, .90F, .50F, .80F, .25F, .95F,
                70, 180, 40, 240, climates(FarmingClimate.TEMPERATE), climates(FarmingClimate.WETLAND));
        add(values, "campion", "Campion", 7, 7, 6, 6, "rose_pink",
                .45F, .70F, .25F, .85F, .40F, .70F, .20F, .90F,
                45, 150, 20, 210, climates(FarmingClimate.TEMPERATE), climates(FarmingClimate.WETLAND));
        add(values, "hyacinth", "Hyacinth", 7, 7, 7, 9, "hyacinth_blue",
                .35F, .60F, .20F, .75F, .50F, .75F, .30F, .90F,
                40, 120, 20, 170, climates(FarmingClimate.TEMPERATE), climates(FarmingClimate.ICE));
        add(values, "orfluer", "Orfluer", 7, 7, 9, 9, "lavender",
                .40F, .65F, .25F, .80F, .55F, .85F, .35F, 1.00F,
                110, 220, 70, 280, climates(FarmingClimate.MAGICAL, FarmingClimate.ICE), climates(FarmingClimate.TEMPERATE));
        return Map.copyOf(values);
    }

    private static void add(
            Map<ResourceLocation, ExpectedSpecies> values,
            String path, String displayName, int naturalMaximum, int absoluteMaximum,
            int baseGrowthTicks, int paletteCount, String fallbackColor,
            float hydrationIdealMin, float hydrationIdealMax,
            float hydrationToleratedMin, float hydrationToleratedMax,
            float nutrientIdealMin, float nutrientIdealMax,
            float nutrientToleratedMin, float nutrientToleratedMax,
            int idealMinAltitude, int idealMaxAltitude, int minAltitude, int maxAltitude,
            Set<FarmingClimate> preferred, Set<FarmingClimate> tolerated
    ) {
        ResourceLocation species = id(path);
        Set<FarmingClimate> unsuitable = EnumSet.copyOf(FlowerDefinitionValidator.canonicalClimates());
        unsuitable.removeAll(preferred);
        unsuitable.removeAll(tolerated);
        values.put(species, new ExpectedSpecies(
                species, species, id(path + "_seeds"), displayName,
                naturalMaximum, absoluteMaximum, baseGrowthTicks, paletteCount, id(fallbackColor),
                hydrationIdealMin, hydrationIdealMax, hydrationToleratedMin, hydrationToleratedMax,
                nutrientIdealMin, nutrientIdealMax, nutrientToleratedMin, nutrientToleratedMax,
                idealMinAltitude, idealMaxAltitude, minAltitude, maxAltitude,
                Set.copyOf(preferred), Set.copyOf(tolerated), Set.copyOf(unsuitable)
        ));
    }

    private static void assertItemAsset(ResourceLocation item) throws IOException {
        JsonObject model = json(ASSETS.resolve("models/item/" + item.getPath() + ".json"));
        String texture = model.getAsJsonObject("textures").get("layer0").getAsString();
        ResourceLocation textureId = ResourceLocation.parse(texture);
        Path png = RESOURCES.resolve("assets").resolve(textureId.getNamespace())
                .resolve("textures").resolve(textureId.getPath() + ".png");
        assertTrue(Files.isRegularFile(png), png.toString());
        assertNotNull(javax.imageio.ImageIO.read(png.toFile()));
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Set<String> tagValues(String filename) throws IOException {
        return tagValuesAt("data/britannia_mod/tags/items/" + filename);
    }

    private static Set<String> tagValuesAt(String relativePath) throws IOException {
        JsonArray values = json(RESOURCES.resolve(relativePath)).getAsJsonArray("values");
        Set<String> result = new HashSet<>();
        values.forEach(value -> assertTrue(result.add(value.getAsString())));
        return result;
    }

    private static String source(String relative) throws IOException {
        return Files.readString(PROJECT.resolve("src/main/java/com/seggellion/britannia_mod").resolve(relative),
                StandardCharsets.UTF_8);
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        assertTrue(from >= 0 && to > from);
        return source.substring(from, to);
    }

    private static long countSuffix(Path root, String suffix) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("stage_"))
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .count();
        }
    }

    private static int count(String text, String needle) {
        int matches = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            matches++;
            index += needle.length();
        }
        return matches;
    }

    private static String languageKey(ResourceLocation item) {
        return "item." + item.getNamespace() + "." + item.getPath();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", path);
    }

    private static Set<FarmingClimate> climates(FarmingClimate first, FarmingClimate... remaining) {
        EnumSet<FarmingClimate> values = EnumSet.of(first);
        values.addAll(List.of(remaining));
        return values;
    }

    private static Set<FarmingClimate> union(Set<FarmingClimate> first, Set<FarmingClimate> second) {
        EnumSet<FarmingClimate> values = EnumSet.copyOf(first);
        values.addAll(second);
        return values;
    }

    private static float midpoint(float minimum, float maximum) {
        return minimum + (maximum - minimum) / 2.0F;
    }

    private static int midpoint(int minimum, int maximum) {
        return minimum + (maximum - minimum) / 2;
    }

    private record ExpectedSpecies(
            ResourceLocation id,
            ResourceLocation flowerItem,
            ResourceLocation seedItem,
            String displayName,
            int naturalMaximum,
            int absoluteMaximum,
            int baseGrowthTicks,
            int paletteCount,
            ResourceLocation fallbackColor,
            float hydrationIdealMin,
            float hydrationIdealMax,
            float hydrationToleratedMin,
            float hydrationToleratedMax,
            float nutrientIdealMin,
            float nutrientIdealMax,
            float nutrientToleratedMin,
            float nutrientToleratedMax,
            int idealMinAltitude,
            int idealMaxAltitude,
            int minAltitude,
            int maxAltitude,
            Set<FarmingClimate> preferred,
            Set<FarmingClimate> tolerated,
            Set<FarmingClimate> unsuitable
    ) {
    }
}
