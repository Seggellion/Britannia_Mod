package com.seggellion.britannia_mod.resource.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.shape.ShapeRotation;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * OreVein milestone 3: planning a deposit into a place, and slicing it by chunk.
 *
 * <p>Everything here runs without a world. That is the point of the layer: a plan is produced from
 * a definition, an origin and a seed, and it can be sliced, unioned and compared before any chunk
 * exists — which is exactly why a deposit crossing a chunk border never obliges a neighbouring
 * chunk to be loaded.
 */
class PlacementPlannerTest {

    private static final String OVERWORLD = "minecraft:overworld";

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ResourceDefinition resource(String path) {
        return ResourceCatalog.instance().byPath(path).orElseThrow();
    }

    /* ------------------------------------------------------------------ */
    /*  Deterministic seeds                                                */
    /* ------------------------------------------------------------------ */

    /**
     * The same curated row derives the same seed, every time and in every session.
     *
     * <p>{@code /populateores} used to reroll a vein on every run because the shapes drew from the
     * level's RNG. The seed now comes only from things about the row that do not change.
     */
    @Test
    void aCuratedRowAlwaysDerivesTheSameSeed() {
        long first = DepositSeed.forCuratedVein(
                OVERWORLD, "britannia_mod:silver", 120, -50, 100, 50, ShapeRotation.ZW);
        long second = DepositSeed.forCuratedVein(
                OVERWORLD, "britannia_mod:silver", 120, -50, 100, 50, ShapeRotation.ZW);
        assertEquals(first, second);
    }

    /** Every input that identifies the row changes the seed, so two rows are two deposits. */
    @Test
    void everyIdentifyingInputChangesTheSeed() {
        long base = DepositSeed.forCuratedVein(
                OVERWORLD, "britannia_mod:silver", 120, -50, 100, 50, ShapeRotation.ZW);
        Set<Long> seeds = new HashSet<>();
        seeds.add(base);
        seeds.add(DepositSeed.forCuratedVein(
                "minecraft:the_nether", "britannia_mod:silver", 120, -50, 100, 50, ShapeRotation.ZW));
        seeds.add(DepositSeed.forCuratedVein(
                OVERWORLD, "britannia_mod:tin", 120, -50, 100, 50, ShapeRotation.ZW));
        seeds.add(DepositSeed.forCuratedVein(
                OVERWORLD, "britannia_mod:silver", 121, -50, 100, 50, ShapeRotation.ZW));
        seeds.add(DepositSeed.forCuratedVein(
                OVERWORLD, "britannia_mod:silver", 120, -49, 100, 50, ShapeRotation.ZW));
        seeds.add(DepositSeed.forCuratedVein(
                OVERWORLD, "britannia_mod:silver", 120, -50, 101, 50, ShapeRotation.ZW));
        seeds.add(DepositSeed.forCuratedVein(
                OVERWORLD, "britannia_mod:silver", 120, -50, 100, 51, ShapeRotation.ZW));
        seeds.add(DepositSeed.forCuratedVein(
                OVERWORLD, "britannia_mod:silver", 120, -50, 100, 50, ShapeRotation.XZ));
        assertEquals(8, seeds.size(), "two different rows must never share a seed");
    }

    /* ------------------------------------------------------------------ */
    /*  Deterministic plans                                                */
    /* ------------------------------------------------------------------ */

    @Test
    void replanningACuratedRowProducesTheIdenticalDeposit() {
        for (ResourceDefinition definition : ResourceCatalog.instance().generatable()) {
            int radius = definition.generation().orElseThrow().minRadius() + 2;
            BlockPos origin = new BlockPos(100, -30, -260);

            List<BlockPos> first = PlacementPlanner
                    .planCuratedVein(definition, OVERWORLD, origin, radius, ShapeRotation.XZ)
                    .positions();
            List<BlockPos> second = PlacementPlanner
                    .planCuratedVein(definition, OVERWORLD, origin, radius, ShapeRotation.XZ)
                    .positions();

            assertEquals(first, second, definition.id() + " rerolled between two identical runs");
            assertFalse(first.isEmpty(), definition.id() + " planned nothing");
        }
    }

    /** Moving a deposit moves every cell with it, and nothing else changes. */
    @Test
    void theOriginTranslatesThePlanWithoutReshapingIt() {
        ResourceDefinition tin = resource("tin");
        PlannedDeposit here = PlacementPlanner.plan(
                tin, OVERWORLD, new BlockPos(0, 0, 0), 10, ShapeRotation.XZ, 42L);
        PlannedDeposit there = PlacementPlanner.plan(
                tin, OVERWORLD, new BlockPos(1000, 20, -3000), 10, ShapeRotation.XZ, 42L);

        assertEquals(here.count(), there.count());
        List<BlockPos> shifted = new ArrayList<>();
        for (BlockPos pos : here.positions()) {
            shifted.add(pos.offset(1000, 20, -3000));
        }
        assertEquals(shifted, there.positions(), "the same seed at a new origin must be a translation");
    }

    /* ------------------------------------------------------------------ */
    /*  Chunk slicing                                                      */
    /* ------------------------------------------------------------------ */

    /**
     * The union of every chunk slice is exactly the whole plan.
     *
     * <p>Slicing partitions the plan rather than re-planning it, which is what makes a deposit
     * spanning several chunks safe: each chunk can be served on its own, whenever it happens to be
     * generated, and the finished deposit is the same either way.
     */
    @Test
    void theUnionOfEveryChunkSliceIsTheWholePlan() {
        for (ResourceDefinition definition : ResourceCatalog.instance().generatable()) {
            int radius = Math.min(definition.generation().orElseThrow().maxRadius(),
                    Math.max(definition.generation().orElseThrow().minRadius(), 20));
            // An origin deliberately near a chunk corner, so the deposit really does straddle.
            PlannedDeposit deposit = PlacementPlanner.planCuratedVein(
                    definition, OVERWORLD, new BlockPos(15, 40, 15), radius, ShapeRotation.XZ);

            List<ChunkPos> chunks = deposit.touchedChunks();
            assertTrue(chunks.size() > 1,
                    definition.id() + " did not straddle a chunk border, so this proves nothing");

            Set<BlockPos> union = new LinkedHashSet<>();
            int sliced = 0;
            for (ChunkPos chunk : chunks) {
                List<BlockPos> slice = deposit.positionsIn(chunk);
                sliced += slice.size();
                union.addAll(slice);
            }
            assertEquals(deposit.count(), sliced,
                    definition.id() + ": the slices must partition the plan, not overlap it");
            assertEquals(new LinkedHashSet<>(deposit.positions()), union,
                    definition.id() + ": the union of the slices must be the whole deposit");
        }
    }

    /** Taking the slices in a different order changes nothing about the result. */
    @Test
    void chunkOrderDoesNotChangeTheOutcome() {
        PlannedDeposit deposit = PlacementPlanner.planCuratedVein(
                resource("silver"), OVERWORLD, new BlockPos(15, 40, 15), 20, ShapeRotation.ZW);

        List<ChunkPos> forwards = deposit.touchedChunks();
        List<ChunkPos> backwards = new ArrayList<>(forwards);
        java.util.Collections.reverse(backwards);

        Set<BlockPos> a = new HashSet<>();
        forwards.forEach(chunk -> a.addAll(deposit.positionsIn(chunk)));
        Set<BlockPos> b = new HashSet<>();
        backwards.forEach(chunk -> b.addAll(deposit.positionsIn(chunk)));

        assertEquals(a, b);
        assertEquals(deposit.count(), a.size());
    }

    /** A slice is computed from the plan alone, so an untouched chunk contributes nothing. */
    @Test
    void aChunkTheDepositDoesNotReachContributesNothing() {
        PlannedDeposit deposit = PlacementPlanner.planCuratedVein(
                resource("tin"), OVERWORLD, new BlockPos(8, 40, 8), 10, ShapeRotation.XZ);
        assertTrue(deposit.positionsIn(new ChunkPos(500, 500)).isEmpty());
        assertFalse(deposit.positionsIn(new ChunkPos(0, 0)).isEmpty());
    }

    /** Every planned cell really is inside the chunk that claimed it. */
    @Test
    void everySlicedCellBelongsToTheChunkThatClaimedIt() {
        PlannedDeposit deposit = PlacementPlanner.planCuratedVein(
                resource("copper"), OVERWORLD, new BlockPos(15, 40, 15), 12, ShapeRotation.XZ);
        for (ChunkPos chunk : deposit.touchedChunks()) {
            for (BlockPos pos : deposit.positionsIn(chunk)) {
                assertEquals(chunk.x, pos.getX() >> 4);
                assertEquals(chunk.z, pos.getZ() >> 4);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Configuration refusal                                              */
    /* ------------------------------------------------------------------ */

    @Test
    void aRadiusOutsideTheResourcesConfiguredRangeIsRefused() {
        ResourceDefinition agapite = resource("agapite");
        assertTrue(PlacementPlanner.reject(agapite, 2).isPresent(), "below the configured minimum");
        assertTrue(PlacementPlanner.reject(agapite, 55).isPresent(),
                "the legacy 55 was never a geode radius");
        assertTrue(PlacementPlanner.reject(agapite, 8).isEmpty());

        assertThrows(IllegalArgumentException.class, () -> PlacementPlanner.plan(
                agapite, OVERWORLD, BlockPos.ZERO, 55, ShapeRotation.XZ, 1L));
    }

    /** A resource with no generation configuration cannot be planned at all. */
    @Test
    void aResourceWithNoGenerationCannotBePlanned() {
        ResourceDefinition clay = ResourceCatalog.instance()
                .byId("britannia_mod:clay_deposit").orElseThrow();
        assertTrue(PlacementPlanner.reject(clay, 5).isPresent());
        assertThrows(IllegalArgumentException.class, () -> PlacementPlanner.plan(
                clay, OVERWORLD, BlockPos.ZERO, 5, ShapeRotation.XZ, 1L));
    }

    /* ------------------------------------------------------------------ */
    /*  Every current resource still plans                                 */
    /* ------------------------------------------------------------------ */

    /**
     * Every approved resource that had a legacy shape still resolves through its definition and its
     * planner, at the radius the shipped curated rows actually use.
     */
    @Test
    void everyShippedCuratedRowStillPlansThroughItsDefinition() {
        record Row(String ore, int radius, ShapeRotation rotation) {
        }
        List<Row> shipped = List.of(
                new Row("shadow_iron", 35, ShapeRotation.XZ),
                new Row("tin", 35, ShapeRotation.XZ),
                new Row("agapite", 8, ShapeRotation.XZ),
                new Row("copper", 15, ShapeRotation.XZ),
                new Row("iron", 90, ShapeRotation.XZ),
                new Row("gold", 50, ShapeRotation.XZ),
                new Row("silver", 50, ShapeRotation.ZW),
                new Row("verite", 20, ShapeRotation.XZ),
                new Row("valorite", 50, ShapeRotation.XZ));

        for (Row row : shipped) {
            PlannedDeposit deposit = PlacementPlanner.planCuratedVein(
                    resource(row.ore()), OVERWORLD, new BlockPos(0, 40, 0), row.radius(), row.rotation());
            assertTrue(deposit.count() > 0, row.ore() + " planned nothing");
            assertEquals(deposit.count(), new HashSet<>(deposit.positions()).size(),
                    row.ore() + " planned a duplicate cell");
        }
    }

    /**
     * A resource the command has never heard of plans through the same path, with no Java change.
     *
     * <p>Milestone 2 proved a new resource needs no {@code switch} to be defined. This proves the
     * same for generation: the shape is selected by the definition, and the planner is reached
     * without anything anywhere knowing the resource's name.
     */
    @Test
    void aResourceReusingAnExistingShapeNeedsNoJavaToBePlanned() {
        ResourceCatalog fixture = ResourceCatalog.parse(new java.io.StringReader("""
                {"schema": 1, "resources": [{
                  "id": "britannia_mod:tin",
                  "display_name": "Tin ore",
                  "family": "ore",
                  "blocks": ["britannia_mod:tin_ore"],
                  "mineable": "tin",
                  "extraction_tool": "britannia_mod:mining_pickaxes",
                  "yield": {"mode": "purity_ore"},
                  "depleted": "fluid_aware_air",
                  "regeneration": {"hours": 6},
                  "generation": {"shape": "geode", "block": "britannia_mod:tin_ore", "min_radius": 4, "max_radius": 12, "host": "britannia_mod:ore_hosts"},
                  "revision": 1
                }]}"""));

        ResourceDefinition invented = fixture.byPath("tin").orElseThrow();
        PlannedDeposit deposit = PlacementPlanner.planCuratedVein(
                invented, OVERWORLD, new BlockPos(0, 40, 0), 6, ShapeRotation.XZ);

        assertTrue(deposit.count() > 0, "a definition alone must be enough to plan a deposit");
        assertNotEquals(
                PlacementPlanner.planCuratedVein(
                        resource("tin"), OVERWORLD, new BlockPos(0, 40, 0), 6, ShapeRotation.XZ).count(),
                deposit.count(),
                "it chose the geode by naming it, not by being called tin");
    }
}
