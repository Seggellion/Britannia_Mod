package com.seggellion.britannia_mod.farming;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Hard-coded data bootstrap matching the repository's existing CropRegistry convention. */
public final class FlowerRegistry {
    public static final ResourceLocation POPPY = id("poppy");
    public static final ResourceLocation SNOWDROP = id("snowdrop");
    public static final ResourceLocation LILY = id("lily");
    public static final ResourceLocation FOXGLOVE = id("foxglove");
    public static final ResourceLocation CAMPION = id("campion");
    public static final ResourceLocation HYACINTH = id("hyacinth");
    public static final ResourceLocation ORFLUER = id("orfluer");
    public static final Set<ResourceLocation> INITIAL_SPECIES_IDS = Set.of(
            POPPY, SNOWDROP, LILY, FOXGLOVE, CAMPION, HYACINTH, ORFLUER
    );

    private static final FlowerRegistry INITIAL = bootstrap();

    private final Map<ResourceLocation, FlowerColorDefinition> colors;
    private final Map<ResourceLocation, FlowerDefinition> definitions;
    private final Map<ResourceLocation, ResourceLocation> speciesBySeedItem;
    private final Map<ResourceLocation, ResourceLocation> speciesByHarvestedItem;

    private FlowerRegistry(
            Map<ResourceLocation, FlowerColorDefinition> colors,
            Map<ResourceLocation, FlowerDefinition> definitions,
            Map<ResourceLocation, ResourceLocation> speciesBySeedItem,
            Map<ResourceLocation, ResourceLocation> speciesByHarvestedItem
    ) {
        this.colors = Map.copyOf(colors);
        this.definitions = Map.copyOf(definitions);
        this.speciesBySeedItem = Map.copyOf(speciesBySeedItem);
        this.speciesByHarvestedItem = Map.copyOf(speciesByHarvestedItem);
    }

    public static FlowerRegistry initial() {
        return INITIAL;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<ResourceLocation, FlowerColorDefinition> colors() {
        return colors;
    }

    public Map<ResourceLocation, FlowerDefinition> definitions() {
        return definitions;
    }

    public Optional<FlowerColorDefinition> color(ResourceLocation id) {
        return Optional.ofNullable(colors.get(id));
    }

    public Optional<FlowerDefinition> byId(ResourceLocation id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Optional<FlowerDefinition> bySeedItemId(ResourceLocation seedItemId) {
        ResourceLocation speciesId = speciesBySeedItem.get(seedItemId);
        return speciesId == null ? Optional.empty() : byId(speciesId);
    }

    public Optional<FlowerDefinition> byHarvestedItemId(ResourceLocation harvestedItemId) {
        ResourceLocation speciesId = speciesByHarvestedItem.get(harvestedItemId);
        return speciesId == null ? Optional.empty() : byId(speciesId);
    }

    public FlowerColor fallbackColor(FlowerDefinition definition) {
        return color(definition.fallbackColorId())
                .orElseThrow(() -> new IllegalStateException("Validated fallback color is missing: " + definition.fallbackColorId()))
                .color();
    }

    public boolean isAllowedColor(FlowerDefinition definition, FlowerColor color) {
        return definition.palette().stream()
                .map(FlowerPaletteEntry::colorId)
                .map(colors::get)
                .filter(java.util.Objects::nonNull)
                .anyMatch(candidate -> candidate.color().equals(color));
    }

    private static FlowerRegistry bootstrap() {
        Builder builder = builder();
        registerInitialColors(builder);

        builder.registerDefinition(flower(
                POPPY, 20.0f, 6, 7,
                profile(5, .30f, .55f, .15f, .70f, .35f, .65f, .15f, .80f, 45, 125, 20, 170,
                        climates(FarmingClimate.TEMPERATE), climates(FarmingClimate.ARID)),
                entries(
                        entry("scarlet", 35, "Common"), entry("crimson", 20, "Common"),
                        entry("orange_red", 15, "Common"), entry("salmon", 10, "Uncommon"),
                        entry("blush_pink", 8, "Uncommon"), entry("snow_white", 5, "Rare"),
                        entry("lavender", 4, "Rare"), entry("burgundy", 3, "Very rare")
                ), "scarlet"
        ));
        builder.registerDefinition(flower(
                SNOWDROP, 40.0f, 7, 7,
                profile(7, .50f, .75f, .35f, .90f, .50f, .80f, .30f, .95f, 55, 155, 30, 220,
                        climates(FarmingClimate.ICE), climates(FarmingClimate.TEMPERATE, FarmingClimate.WETLAND)),
                entries(
                        entry("snow_white", 78, "Common"), entry("ivory", 15, "Uncommon"),
                        entry("pale_green", 5, "Rare"), entry("primrose_yellow", 2, "Very rare")
                ), "snow_white"
        ));
        builder.registerDefinition(flower(
                LILY, 50.0f, 7, 7,
                profile(8, .45f, .70f, .30f, .85f, .60f, .85f, .40f, 1.00f, 45, 150, 20, 200,
                        climates(FarmingClimate.TEMPERATE), climates(FarmingClimate.ICE)),
                entries(
                        entry("snow_white", 16, "Common"), entry("cream", 10, "Common"),
                        entry("primrose_yellow", 14, "Common"), entry("orange", 14, "Common"),
                        entry("rose_pink", 14, "Common"), entry("salmon", 10, "Uncommon"),
                        entry("scarlet", 8, "Uncommon"), entry("burgundy", 5, "Rare"),
                        entry("lavender", 5, "Rare"), entry("purple", 4, "Very rare")
                ), "snow_white"
        ));
        builder.registerDefinition(flower(
                FOXGLOVE, 65.0f, 7, 7,
                profile(9, .50f, .75f, .35f, .90f, .50f, .80f, .25f, .95f, 70, 180, 40, 240,
                        climates(FarmingClimate.TEMPERATE), climates(FarmingClimate.WETLAND)),
                entries(
                        entry("violet", 25, "Common"), entry("rose_pink", 22, "Common"),
                        entry("blush_pink", 16, "Common"), entry("purple", 12, "Uncommon"),
                        entry("snow_white", 10, "Uncommon"), entry("cream", 6, "Rare"),
                        entry("apricot", 4, "Rare"), entry("peach", 3, "Very rare"),
                        entry("primrose_yellow", 2, "Very rare")
                ), "violet"
        ));
        builder.registerDefinition(flower(
                CAMPION, 10.0f, 7, 7,
                profile(6, .45f, .70f, .25f, .85f, .40f, .70f, .20f, .90f, 45, 150, 20, 210,
                        climates(FarmingClimate.TEMPERATE), climates(FarmingClimate.WETLAND)),
                entries(
                        entry("rose_pink", 40, "Common"), entry("magenta", 22, "Common"),
                        entry("blush_pink", 15, "Common"), entry("scarlet", 10, "Uncommon"),
                        entry("snow_white", 9, "Rare"), entry("lavender", 4, "Very rare")
                ), "rose_pink"
        ));
        builder.registerDefinition(flower(
                HYACINTH, 30.0f, 7, 7,
                profile(7, .35f, .60f, .20f, .75f, .50f, .75f, .30f, .90f, 40, 120, 20, 170,
                        climates(FarmingClimate.TEMPERATE), climates(FarmingClimate.ICE)),
                entries(
                        entry("hyacinth_blue", 22, "Common"), entry("violet", 20, "Common"),
                        entry("purple", 15, "Common"), entry("rose_pink", 14, "Common"),
                        entry("snow_white", 11, "Uncommon"), entry("deep_blue", 7, "Uncommon"),
                        entry("crimson", 5, "Rare"), entry("primrose_yellow", 4, "Rare"),
                        entry("apricot", 2, "Very rare")
                ), "hyacinth_blue"
        ));
        builder.registerDefinition(flower(
                ORFLUER, 95.0f, 7, 7,
                profile(9, .40f, .65f, .25f, .80f, .55f, .85f, .35f, 1.00f, 110, 220, 70, 280,
                        climates(FarmingClimate.MAGICAL, FarmingClimate.ICE), climates(FarmingClimate.TEMPERATE)),
                entries(
                        entry("lavender", 20, "Common"), entry("ivory", 18, "Common"),
                        entry("violet", 17, "Common"), entry("sky_blue", 14, "Uncommon"),
                        entry("rose_pink", 10, "Uncommon"), entry("burgundy", 7, "Rare"),
                        entry("golden_yellow", 7, "Rare"), entry("pale_green", 5, "Very rare"),
                        entry("deep_blue", 2, "Very rare")
                ), "lavender"
        ));

        return builder.buildInitial();
    }

    private static void registerInitialColors(Builder builder) {
        builder.registerColor(color("snow_white", "#F3F1E8"));
        builder.registerColor(color("ivory", "#E3D9C2"));
        builder.registerColor(color("cream", "#D7C493"));
        builder.registerColor(color("primrose_yellow", "#D9BE59"));
        builder.registerColor(color("golden_yellow", "#C99B35"));
        builder.registerColor(color("apricot", "#D99667"));
        builder.registerColor(color("peach", "#E4A07F"));
        builder.registerColor(color("orange", "#DC6A32"));
        builder.registerColor(color("orange_red", "#D94B2B"));
        builder.registerColor(color("scarlet", "#C82A2F"));
        builder.registerColor(color("crimson", "#981F35"));
        builder.registerColor(color("burgundy", "#641F3A"));
        builder.registerColor(color("blush_pink", "#E4A0B5"));
        builder.registerColor(color("rose_pink", "#CF5B87"));
        builder.registerColor(color("magenta", "#AD2F74"));
        builder.registerColor(color("salmon", "#DF8574"));
        builder.registerColor(color("lavender", "#A48BC0"));
        builder.registerColor(color("violet", "#73509A"));
        builder.registerColor(color("purple", "#533675"));
        builder.registerColor(color("sky_blue", "#789EC1"));
        builder.registerColor(color("hyacinth_blue", "#526FA6"));
        builder.registerColor(color("deep_blue", "#374E82"));
        builder.registerColor(color("pale_green", "#B8C19B"));
    }

    private static FlowerColorDefinition color(String path, String hex) {
        return new FlowerColorDefinition(id(path), FlowerColor.fromHex(hex));
    }

    private static FlowerPaletteEntry entry(String colorPath, int weight, String rarity) {
        return new FlowerPaletteEntry(id(colorPath), weight, rarity);
    }

    @SafeVarargs
    private static List<FlowerPaletteEntry> entries(FlowerPaletteEntry... entries) {
        return List.of(entries);
    }

    private static FlowerDefinition flower(
            ResourceLocation speciesId,
            float minimumFarmingSkill,
            int naturalMaximumStage,
            int absoluteMaximumStage,
            FlowerGrowthProfile profile,
            List<FlowerPaletteEntry> palette,
            String fallbackColor
    ) {
        return new FlowerDefinition(
                speciesId,
                minimumFarmingSkill,
                id(speciesId.getPath() + "_seeds"),
                speciesId,
                naturalMaximumStage,
                absoluteMaximumStage,
                profile,
                palette,
                id(fallbackColor),
                FlowerColorLifecycle.SELECT_ON_SERVER_PLANTING_AND_STORE
        );
    }

    private static FlowerGrowthProfile profile(
            int baseGrowthTicks,
            float hydrationIdealMin,
            float hydrationIdealMax,
            float hydrationToleratedMin,
            float hydrationToleratedMax,
            float nutrientIdealMin,
            float nutrientIdealMax,
            float nutrientToleratedMin,
            float nutrientToleratedMax,
            int altitudeIdealMin,
            int altitudeIdealMax,
            int altitudeToleratedMin,
            int altitudeToleratedMax,
            Set<FarmingClimate> preferredClimates,
            Set<FarmingClimate> toleratedClimates
    ) {
        float hydrationIdeal = midpoint(hydrationIdealMin, hydrationIdealMax);
        float nutrientIdeal = midpoint(nutrientIdealMin, nutrientIdealMax);
        float nutrientTolerance = Math.max(
                nutrientIdeal - nutrientToleratedMin,
                nutrientToleratedMax - nutrientIdeal
        );
        Set<FarmingClimate> allowed = EnumSet.copyOf(preferredClimates);
        allowed.addAll(toleratedClimates);
        Set<FarmingClimate> forbidden = EnumSet.copyOf(FlowerDefinitionValidator.canonicalClimates());
        forbidden.removeAll(allowed);
        return new FlowerGrowthProfile(
                baseGrowthTicks,
                nutrientIdeal, nutrientIdeal, nutrientIdeal, nutrientIdeal,
                nutrientTolerance,
                1.0f, 1.0f, 1.0f, 1.0f,
                hydrationIdeal,
                hydrationIdeal - hydrationToleratedMin,
                hydrationToleratedMax - hydrationIdeal,
                hydrationToleratedMin,
                hydrationToleratedMax,
                preferredClimates,
                allowed,
                forbidden,
                altitudeIdealMin,
                altitudeIdealMax,
                altitudeToleratedMin,
                altitudeToleratedMax,
                0.65f,
                1.0f,
                NutrientPreferenceMode.BALANCED
        );
    }

    private static float midpoint(float minimum, float maximum) {
        return minimum + (maximum - minimum) / 2.0f;
    }

    private static Set<FarmingClimate> climates(FarmingClimate first, FarmingClimate... remaining) {
        EnumSet<FarmingClimate> climates = EnumSet.of(first);
        for (FarmingClimate climate : remaining) {
            climates.add(climate);
        }
        return climates;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", path);
    }

    public static final class Builder {
        private final Map<ResourceLocation, FlowerColorDefinition> colors = new LinkedHashMap<>();
        private final Map<ResourceLocation, FlowerDefinition> definitions = new LinkedHashMap<>();
        private final Map<ResourceLocation, ResourceLocation> speciesBySeedItem = new LinkedHashMap<>();
        private final Map<ResourceLocation, ResourceLocation> speciesByHarvestedItem = new LinkedHashMap<>();

        public Builder registerColor(FlowerColorDefinition color) {
            FlowerColorDefinition previous = colors.putIfAbsent(color.id(), color);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate flower color ID: " + color.id());
            }
            return this;
        }

        public Builder registerDefinition(FlowerDefinition definition) {
            FlowerDefinition previous = definitions.putIfAbsent(definition.id(), definition);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate flower species ID: " + definition.id());
            }
            ResourceLocation previousSpecies = speciesBySeedItem.putIfAbsent(definition.seedItemId(), definition.id());
            if (previousSpecies != null) {
                definitions.remove(definition.id());
                throw new IllegalArgumentException("Duplicate flower seed mapping " + definition.seedItemId()
                        + " for " + previousSpecies + " and " + definition.id());
            }
            ResourceLocation previousHarvest = speciesByHarvestedItem.putIfAbsent(
                    definition.harvestedItemId(), definition.id()
            );
            if (previousHarvest != null) {
                throw new IllegalArgumentException("Duplicate harvested flower mapping "
                        + definition.harvestedItemId() + " for " + previousHarvest + " and " + definition.id());
            }
            return this;
        }

        public FlowerRegistry build() {
            return build(false);
        }

        private FlowerRegistry buildInitial() {
            if (!definitions.keySet().equals(INITIAL_SPECIES_IDS)) {
                Set<ResourceLocation> missing = new java.util.HashSet<>(INITIAL_SPECIES_IDS);
                missing.removeAll(definitions.keySet());
                throw new IllegalArgumentException("Initial flower registry is missing species: " + missing);
            }
            return build(true);
        }

        private FlowerRegistry build(boolean requireInitialSpecies) {
            FlowerDefinitionValidator.validateRegistry(colors, definitions, requireInitialSpecies);
            return new FlowerRegistry(colors, definitions, speciesBySeedItem, speciesByHarvestedItem);
        }
    }
}
