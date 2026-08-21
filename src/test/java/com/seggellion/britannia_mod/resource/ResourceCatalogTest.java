package com.seggellion.britannia_mod.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.mining.MineableCatalog;
import com.seggellion.britannia_mod.mining.MineableDefinition;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * OreVein milestone 2: the shipped catalogue loads, says what it is supposed to say, and does not
 * duplicate anything {@code MineableCatalog} already owns.
 *
 * <p>Pure JUnit against the real data, exactly as {@code MineableCatalogContractTest} works — no
 * Minecraft, no world, no source-text assertions.
 */
class ResourceCatalogTest {

    private static ResourceCatalog catalog() {
        return ResourceCatalog.instance();
    }

    private static ResourceDefinition byId(String id) {
        return catalog().byId(id).orElseThrow(() -> new AssertionError("no resource " + id));
    }

    /* ------------------------------------------------------------------ */
    /*  It loads, and it covers what it must                               */
    /* ------------------------------------------------------------------ */

    @Test
    void theShippedCatalogueLoadsAndCoversEveryFamily() {
        ResourceCatalog catalog = catalog();
        assertEquals(28, catalog.all().size(), "9 ores + 17 stones + 2 sediment beds");
        assertEquals(9, catalog.family(ResourceDefinition.Family.ORE).size());
        assertEquals(17, catalog.family(ResourceDefinition.Family.STONE).size());
        assertEquals(2, catalog.family(ResourceDefinition.Family.SEDIMENT).size());
    }

    /**
     * Every Mining resource has a configured extraction tag, because every ACTIVE mineable is
     * claimed. This is the check that makes a permissive fallback impossible: the gate reads its
     * tool policy from a definition, so a mineable without one would have to be defaulted, and
     * instead it fails the load.
     */
    @Test
    void everyActiveMineableIsClaimedByExactlyOneResource() {
        catalog().validateCoversEveryActiveMineable();

        Map<String, Long> claims = catalog().all().stream()
                .map(ResourceDefinition::mineableId)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));
        for (MineableDefinition mineable : MineableCatalog.instance().active()) {
            assertEquals(1L, claims.getOrDefault(mineable.id(), 0L),
                    mineable.id() + " must be claimed by exactly one resource definition");
        }
    }

    /* ------------------------------------------------------------------ */
    /*  One authority per fact                                             */
    /* ------------------------------------------------------------------ */

    /**
     * The whole point of composing rather than copying: a resource definition carries no Mining
     * number at all, so two files cannot state different requirements for one resource.
     */
    @Test
    void noResourceDefinitionCarriesAMiningNumber() {
        for (java.lang.reflect.RecordComponent component : ResourceDefinition.class.getRecordComponents()) {
            String name = component.getName().toLowerCase(java.util.Locale.ROOT);
            assertFalse(name.contains("mining") && !name.equals("mineableid"),
                    "ResourceDefinition must reference Mining, never restate it: found " + component.getName());
            assertFalse(name.contains("required") || name.contains("challenge"),
                    "Mining progression belongs to MineableCatalog alone: found " + component.getName());
        }
    }

    /**
     * Two distinct resources may absolutely resolve to the same numeric Mining requirement.
     *
     * <p>Clarifying what milestone 2 meant when it reported that "two resources sharing one Mining
     * requirement is unrepresentable". That was about sharing a <em>Mineable identity</em> — two
     * definitions pointing at the same catalogue row, which would make one row's blocks and level
     * belong to two resources at once. Sharing a <em>value</em> is ordinary and always worked, and
     * the shipped catalogue is full of it: three resources sit at 30.0 and two each at 5.0, 35.0,
     * 40.0 and 45.0.
     *
     * <p>Worth pinning rather than merely stating, because the looser reading would have been a
     * real limitation on a platform meant to carry 25-50 resources — a new rock could not have
     * been priced the same as an existing one.
     */
    @Test
    void distinctResourcesMayShareANumericMiningRequirement() {
        Map<Float, List<String>> byRequirement = new java.util.TreeMap<>();
        for (ResourceDefinition definition : catalog().all()) {
            Optional<String> reference = definition.mineableId();
            if (reference.isEmpty()) continue;
            float required = MineableCatalog.instance().byId(reference.get())
                    .orElseThrow().requiredMining();
            byRequirement.computeIfAbsent(required, key -> new java.util.ArrayList<>())
                    .add(definition.id());
        }
        List<String> shared = byRequirement.values().stream()
                .filter(ids -> ids.size() > 1)
                .map(Object::toString)
                .toList();
        assertFalse(shared.isEmpty(),
                "the shipped catalogue is expected to contain resources priced identically");
        assertTrue(shared.size() >= 5,
                "several requirement values are shared today; found only " + shared);

        // Named, so the claim is concrete rather than statistical.
        assertEquals(30.0f, MineableCatalog.instance().byId("deepslate").orElseThrow().requiredMining());
        assertEquals(30.0f, MineableCatalog.instance().byId("metamorphic_rock").orElseThrow().requiredMining());
        assertTrue(catalog().byId("britannia_mod:deepslate").isPresent());
        assertTrue(catalog().byId("britannia_mod:metamorphic_rock").isPresent());
    }

    /** Both catalogues must agree, block for block, about what each resource covers. */
    @Test
    void theTwoCataloguesClaimIdenticalBlocksForEveryMiningResource() {
        for (ResourceDefinition definition : catalog().all()) {
            Optional<String> reference = definition.mineableId();
            if (reference.isEmpty()) continue;
            MineableDefinition mineable = MineableCatalog.instance().byId(reference.get()).orElseThrow();
            assertEquals(Set.copyOf(mineable.blockIds()), Set.copyOf(definition.blockIds()),
                    definition.id() + " and mineable " + reference.get() + " must cover the same blocks");
        }
    }

    /**
     * The approved Mining levels are unchanged by the migration. Pinned by value here rather than
     * by "the file still parses", because a silent edit to a requirement is exactly the drift the
     * composition model exists to prevent.
     */
    @Test
    void existingMiningLevelsAreUnchanged() {
        Map<String, Float> approved = Map.ofEntries(
                Map.entry("iron", 0.0f), Map.entry("silver", 55.0f), Map.entry("tin", 65.0f),
                Map.entry("shadow_iron", 70.0f), Map.entry("copper", 75.0f), Map.entry("gold", 85.0f),
                Map.entry("agapite", 90.0f), Map.entry("verite", 95.0f), Map.entry("valorite", 99.0f),
                Map.entry("stone", 0.0f), Map.entry("calcite", 5.0f), Map.entry("sandstone", 5.0f),
                Map.entry("diorite", 10.0f), Map.entry("andesite", 15.0f), Map.entry("granite", 20.0f),
                Map.entry("tuff", 25.0f), Map.entry("deepslate", 30.0f), Map.entry("dripstone", 35.0f),
                Map.entry("basalt", 40.0f), Map.entry("blackstone", 45.0f));
        approved.forEach((id, required) -> assertEquals(required,
                MineableCatalog.instance().byId(id).orElseThrow().requiredMining(), 0.0f,
                id + "'s approved Mining requirement must not have moved"));
    }

    /* ------------------------------------------------------------------ */
    /*  Extraction: the pickaxe and the shovel are parallel                */
    /* ------------------------------------------------------------------ */

    @Test
    void everyMiningResourceNamesThePickaxeTagAndEverySedimentBedAShovelTag() {
        for (ResourceDefinition definition : catalog().all()) {
            if (definition.isSediment()) {
                assertTrue(definition.extractionToolTag().endsWith("_shovels"),
                        definition.id() + " is worked with a shovel");
            } else {
                assertEquals("britannia_mod:mining_pickaxes", definition.extractionToolTag(),
                        definition.id() + " is worked with the Mining pickaxe tag");
            }
        }
        assertEquals("britannia_mod:clay_shovels", byId("britannia_mod:clay_deposit").extractionToolTag());
        assertEquals("britannia_mod:silica_shovels",
                byId("britannia_mod:silica_sand_deposit").extractionToolTag());
    }

    /**
     * The two families use different tags, which is the structural reason a pickaxe has no
     * authority over a bed and a shovel none over an ore. Neither is a subset of the other.
     */
    @Test
    void theOreAndSedimentTagsAreDisjointNames() {
        Set<String> mining = catalog().all().stream()
                .filter(definition -> !definition.isSediment())
                .map(ResourceDefinition::extractionToolTag).collect(Collectors.toSet());
        Set<String> sediment = catalog().all().stream()
                .filter(ResourceDefinition::isSediment)
                .map(ResourceDefinition::extractionToolTag).collect(Collectors.toSet());
        assertTrue(java.util.Collections.disjoint(mining, sediment),
                "a tool family must never be authorised for both by sharing a tag");
    }

    /* ------------------------------------------------------------------ */
    /*  Yields, regeneration, depletion                                    */
    /* ------------------------------------------------------------------ */

    @Test
    void existingYieldIdentitiesAreUnchanged() {
        ResourceDefinition clay = byId("britannia_mod:clay_deposit");
        assertEquals(ResourceDefinition.Yield.Mode.ITEM, clay.yield().mode());
        assertEquals(Optional.of("minecraft:clay_ball"), clay.yield().itemId());
        assertEquals(1, clay.yield().count());

        ResourceDefinition silica = byId("britannia_mod:silica_sand_deposit");
        assertEquals(Optional.of("britannia_mod:silica_sand"), silica.yield().itemId());
        assertEquals(1, silica.yield().count());

        assertEquals(ResourceDefinition.Yield.Mode.PURITY_ORE, byId("britannia_mod:silver").yield().mode());
        assertEquals(ResourceDefinition.Yield.Mode.GRADED_STONE, byId("britannia_mod:granite").yield().mode());
    }

    /** Silica is the first resource whose regeneration differs from the historical global six. */
    @Test
    void silicaResolvesTwentyFourHoursAndEverythingElseKeepsSix() {
        assertEquals(24, byId("britannia_mod:silica_sand_deposit").regenerationHours());
        assertEquals(24L * 60 * 60 * 1000,
                byId("britannia_mod:silica_sand_deposit").regenerationMillis());

        for (ResourceDefinition definition : catalog().all()) {
            if (definition.id().equals("britannia_mod:silica_sand_deposit")) continue;
            assertEquals(6, definition.regenerationHours(),
                    definition.id() + " must keep the historical six-hour delay");
        }
        assertEquals(6L * 60 * 60 * 1000, byId("britannia_mod:clay_deposit").regenerationMillis());
    }

    @Test
    void everyResourceDeclaresItsDepletedState() {
        for (ResourceDefinition definition : catalog().all()) {
            assertSame(ResourceDefinition.DepletedState.FLUID_AWARE_AIR, definition.depleted(),
                    definition.id() + " must declare what stands in its cell once worked");
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Generation configuration                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Everything that can be placed, with the shape each one uses.
     *
     * <p>The nine ores are milestone 1's table, moved into data. Silica joined them at milestone 7
     * as the first sediment bed with a geometry of its own; it is listed here rather than tested
     * separately because "which resources can be placed" is one question and should have one
     * answer.
     */
    @Test
    void everyPlaceableResourceCarriesItsShapeConfiguration() {
        Map<String, ResourceShape> expected = Map.ofEntries(
                Map.entry("copper", ResourceShape.CLUSTER),
                Map.entry("verite", ResourceShape.CLUSTER),
                Map.entry("iron", ResourceShape.VERTICAL),
                Map.entry("valorite", ResourceShape.VERTICAL),
                Map.entry("shadow_iron", ResourceShape.VERTICAL),
                Map.entry("gold", ResourceShape.SNAKE),
                Map.entry("agapite", ResourceShape.GEODE),
                Map.entry("silver", ResourceShape.VERTICAL_LAYERED),
                Map.entry("tin", ResourceShape.LAYERED),
                Map.entry("silica_sand_deposit", ResourceShape.SEDIMENTARY_LENS));

        List<ResourceDefinition> generatable = catalog().generatable();
        assertEquals(expected.keySet(),
                generatable.stream().map(ResourceDefinition::path).collect(Collectors.toSet()));
        for (ResourceDefinition definition : generatable) {
            ResourceDefinition.Generation generation = definition.generation().orElseThrow();
            assertEquals(expected.get(definition.path()), generation.shape(), definition.id());
            assertTrue(definition.blockIds().contains(generation.blockId()),
                    definition.id() + " must generate a block it governs");
            assertTrue(generation.minRadius() >= generation.shape().minimumRadius(),
                    definition.id() + " must not configure a radius below its shape's own minimum");
        }
    }

    /**
     * Every shape's minimum comes from its planner, and nowhere else.
     *
     * <p>Milestone 2 kept these numbers here as "hard floors" measured from where the legacy
     * algorithms threw. Milestone 3 deleted those algorithms, so a minimum is now a property of the
     * geometry — the smallest radius at which the shape means anything — and it is declared once,
     * by the planner that draws it. Snake's dropped from ten to four because ten was an artefact of
     * the {@code nextInt(radius - 9)} bug rather than a design decision; gold's configured minimum
     * is still ten, which is data narrowing a shape's range as it is allowed to.
     */
    @Test
    void everyShapeTakesItsMinimumFromItsPlanner() {
        for (ResourceShape shape : ResourceShape.values()) {
            assertEquals(shape.planner().minimumRadius(), shape.minimumRadius(),
                    shape + " must not hold a second opinion about its own minimum");
            assertTrue(shape.minimumRadius() >= 1, shape + " must need at least one cell of radius");
        }
        assertEquals(4, ResourceShape.SNAKE.minimumRadius(), "a snake needs length to wind");
        assertEquals(3, ResourceShape.GEODE.minimumRadius(), "a nodule needs a crust and a hollow");
        assertEquals(1, ResourceShape.CLUSTER.minimumRadius());
    }

    /** Only the shapes that are actually directional consult the rotation. */
    @Test
    void onlyDirectionalShapesUseRotation() {
        assertTrue(ResourceShape.VERTICAL_LAYERED.usesRotation(), "a standing sheet has an orientation");
        for (ResourceShape shape : java.util.List.of(ResourceShape.CLUSTER, ResourceShape.GEODE,
                ResourceShape.LAYERED, ResourceShape.VERTICAL)) {
            assertFalse(shape.usesRotation(),
                    shape + " is not directional and must say so rather than silently ignoring it");
        }
    }

    /**
     * Silica is the one sediment bed that generates; clay is still placed by hand.
     *
     * <p>Milestone 7 is where this changed. It used to read "sediment beds carry no generation",
     * which was true and is the sort of assertion that quietly becomes a rule: silica gaining a
     * sedimentary lens is the whole point of the milestone, and clay staying hand-placed is a
     * deliberate decision rather than an oversight, so both halves are now stated.
     */
    @Test
    void silicaGeneratesAndClayIsStillPlacedByHand() {
        ResourceDefinition silica = catalog().byId("britannia_mod:silica_sand_deposit").orElseThrow();
        ResourceDefinition clay = catalog().byId("britannia_mod:clay_deposit").orElseThrow();

        assertTrue(silica.generation().isPresent(), "silica lost its generation configuration");
        assertEquals(ResourceShape.SEDIMENTARY_LENS, silica.generation().orElseThrow().shape());
        assertTrue(silica.natural().isPresent(), "silica lost its natural distribution");
        assertTrue(clay.generation().isEmpty(), "clay is placed by hand, not generated");
        assertTrue(clay.natural().isEmpty(), "clay gained a natural distribution nobody asked for");
    }

    /* ------------------------------------------------------------------ */
    /*  Identity stability                                                 */
    /* ------------------------------------------------------------------ */

    /**
     * The deposit ids are load-bearing economic identifiers — the housing material data names
     * {@code britannia_mod:clay_deposit} as a feedstock — so the migration must not have tidied
     * them into something prettier.
     */
    @Test
    void depositIdsAreUnchangedBecauseTheEconomyReferencesThem() {
        assertTrue(catalog().byId("britannia_mod:clay_deposit").isPresent());
        assertTrue(catalog().byId("britannia_mod:silica_sand_deposit").isPresent());
    }

    /** A Rails row spells the ore's path, so the path must remain the legacy ore-type name. */
    @Test
    void oreResourcePathsStillMatchTheLegacyOreTypeNames() {
        for (String legacy : List.of("copper", "tin", "silver", "gold", "iron",
                "shadow_iron", "agapite", "verite", "valorite")) {
            assertTrue(catalog().byPath(legacy).isPresent(), legacy + " must resolve by its legacy name");
        }
        assertTrue(catalog().byPath("coal").isEmpty(),
                "coal has no definition, which is what keeps it out of the placement route");
    }
}
