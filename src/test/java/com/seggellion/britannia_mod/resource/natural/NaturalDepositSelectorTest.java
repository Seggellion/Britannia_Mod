package com.seggellion.britannia_mod.resource.natural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.ResourceShape;
import com.seggellion.britannia_mod.resource.deposit.DepositIdentity;
import com.seggellion.britannia_mod.resource.shape.ShapeTuning;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Which natural deposits exist, decided from the seed and nothing else.
 *
 * <p>The property that matters most here is the one that makes multi-chunk deposits possible at
 * all: a candidate is a function of its owner cell, so every chunk the deposit reaches derives the
 * identical candidate without consulting any other chunk. Everything else — spacing, salting,
 * dimension separation — follows from the same design.
 */
class NaturalDepositSelectorTest {

    private static ResourceDefinition silica() {
        return ResourceCatalog.instance().byId("britannia_mod:silica_sand_deposit").orElseThrow();
    }

    private static NaturalGeneration natural() {
        return silica().natural().orElseThrow();
    }

    /** A candidate that definitely exists, for tests that need one. */
    private static NaturalDepositSelector.Candidate anyCandidate(long seed) {
        for (int cellX = 0; cellX < 50; cellX++) {
            for (int cellZ = 0; cellZ < 50; cellZ++) {
                Optional<NaturalDepositSelector.Candidate> candidate =
                        NaturalDepositSelector.candidateFor(seed, silica(), natural(), cellX, cellZ);
                if (candidate.isPresent()) {
                    return candidate.get();
                }
            }
        }
        throw new AssertionError("no candidate anywhere, which cannot be right");
    }

    /* ------------------------------------------------------------------ */
    /*  Determinism                                                        */
    /* ------------------------------------------------------------------ */

    @Test
    void theSameCellAlwaysGivesTheSameCandidate() {
        for (int cellX = -5; cellX <= 5; cellX++) {
            for (int cellZ = -5; cellZ <= 5; cellZ++) {
                Optional<NaturalDepositSelector.Candidate> first =
                        NaturalDepositSelector.candidateFor(99L, silica(), natural(), cellX, cellZ);
                Optional<NaturalDepositSelector.Candidate> second =
                        NaturalDepositSelector.candidateFor(99L, silica(), natural(), cellX, cellZ);
                assertEquals(first, second, "cell " + cellX + ',' + cellZ + " was not stable");
            }
        }
    }

    /**
     * Every chunk a deposit reaches derives the same candidate.
     *
     * <p>This is the whole basis of the multi-chunk design. If a chunk derived a candidate from
     * itself rather than from the owner cell, four chunks would produce four deposits.
     */
    @Test
    void everyChunkTheDepositReachesDerivesTheIdenticalCandidate() {
        NaturalDepositSelector.Candidate candidate = anyCandidate(2024L);
        int chunkX = Math.floorDiv(candidate.originX(), 16);
        int chunkZ = Math.floorDiv(candidate.originZ(), 16);

        Set<Long> ids = new HashSet<>();
        int chunksSeeingIt = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                List<NaturalDepositSelector.Candidate> reaching =
                        NaturalDepositSelector.candidatesReaching(
                                2024L, silica(), natural(), chunkX + dx, chunkZ + dz);
                for (NaturalDepositSelector.Candidate seen : reaching) {
                    if (seen.instanceId() == candidate.instanceId()) {
                        chunksSeeingIt++;
                        ids.add(seen.instanceId());
                        assertEquals(candidate, seen,
                                "a neighbouring chunk saw a different version of the same deposit");
                    }
                }
            }
        }
        assertTrue(chunksSeeingIt >= 4,
                "only " + chunksSeeingIt + " chunks saw the deposit; it is not spanning any border");
        assertEquals(1, ids.size(), "the same deposit resolved to more than one identity");
    }

    @Test
    void theIdentityIsTheMilestoneFourNaturalContract() {
        NaturalDepositSelector.Candidate candidate = anyCandidate(7L);
        long expected = DepositIdentity.natural(
                7L, natural().dimensionId(), silica().id(),
                candidate.cellX(), candidate.cellZ(), natural().salt());
        assertEquals(expected, candidate.instanceId(),
                "natural identity was derived some other way than the milestone 4 contract");
    }

    /* ------------------------------------------------------------------ */
    /*  Independence                                                       */
    /* ------------------------------------------------------------------ */

    /** A different world seed is a different world. */
    @Test
    void adifferentWorldSeedGivesADifferentDistribution() {
        int agree = 0;
        int cells = 0;
        for (int cellX = 0; cellX < 40; cellX++) {
            for (int cellZ = 0; cellZ < 40; cellZ++) {
                cells++;
                boolean a = NaturalDepositSelector
                        .candidateFor(1L, silica(), natural(), cellX, cellZ).isPresent();
                boolean b = NaturalDepositSelector
                        .candidateFor(2L, silica(), natural(), cellX, cellZ).isPresent();
                if (a == b) {
                    agree++;
                }
            }
        }
        double agreement = agree / (double) cells;
        assertTrue(agreement < 0.75,
                "two world seeds agreed on " + Math.round(agreement * 100)
                        + "% of cells; the seed is barely being used");
    }

    /**
     * Two resources with different salts are distributed independently.
     *
     * <p>Without this a second resource would land on exactly the same cells as silica, and the
     * world would have two deposits stacked on every silica bed.
     */
    @Test
    void adifferentSaltGivesAnIndependentDistribution() {
        NaturalGeneration other = withSalt(natural(), natural().salt() + 1);
        int cells = 0;
        int onlyA = 0;
        int onlyB = 0;
        int both = 0;
        for (int cellX = 0; cellX < 60; cellX++) {
            for (int cellZ = 0; cellZ < 60; cellZ++) {
                cells++;
                boolean a = NaturalDepositSelector
                        .candidateFor(5L, silica(), natural(), cellX, cellZ).isPresent();
                boolean b = NaturalDepositSelector
                        .candidateFor(5L, silica(), other, cellX, cellZ).isPresent();
                if (a && b) both++;
                else if (a) onlyA++;
                else if (b) onlyB++;
            }
        }
        // Independence is not "they rarely coincide" -- at a high occurrence chance they must
        // coincide often, because both are common. It is that they coincide exactly as often as
        // chance alone predicts, and that each still occurs where the other does not.
        double rateA = (both + onlyA) / (double) cells;
        double rateB = (both + onlyB) / (double) cells;
        double observed = both / (double) cells;
        double expected = rateA * rateB;
        assertTrue(Math.abs(observed - expected) < 0.05,
                "two salts coincided in " + Math.round(observed * 100) + "% of cells against the "
                        + Math.round(expected * 100) + "% independence predicts; they are correlated");
        assertTrue(onlyA > cells / 20 && onlyB > cells / 20,
                "one salt never occurs without the other (" + onlyA + " / " + onlyB + " cells)");
    }

    /** A different dimension is a different set of deposits, even at the same cell. */
    @Test
    void adifferentDimensionGivesADifferentIdentity() {
        NaturalGeneration overworld = natural();
        NaturalGeneration nether = new NaturalGeneration(
                "minecraft:the_nether", overworld.biomeTag(), overworld.cellChunks(),
                overworld.chance(), overworld.minRadius(), overworld.maxRadius(),
                overworld.depth(), overworld.depthJitter(), overworld.minY(), overworld.maxY(),
                overworld.salt(), overworld.tuning());

        long here = DepositIdentity.natural(3L, overworld.dimensionId(), silica().id(), 4, 5,
                overworld.salt());
        long there = DepositIdentity.natural(3L, nether.dimensionId(), silica().id(), 4, 5,
                nether.salt());
        assertNotEquals(here, there, "the same cell in two dimensions is the same deposit");
    }

    /* ------------------------------------------------------------------ */
    /*  Placement guarantees                                               */
    /* ------------------------------------------------------------------ */

    /** A candidate always sits inside its own cell, with margin to spare. */
    @Test
    void everyCandidateSitsInsideItsOwnCell() {
        NaturalGeneration natural = natural();
        for (int cellX = -20; cellX <= 20; cellX++) {
            for (int cellZ = -20; cellZ <= 20; cellZ++) {
                int x = cellX;
                int z = cellZ;
                NaturalDepositSelector.candidateFor(11L, silica(), natural, cellX, cellZ)
                        .ifPresent(candidate -> {
                            assertEquals(x, Math.floorDiv(candidate.originX(), natural.cellBlocks()),
                                    "candidate escaped its cell on X");
                            assertEquals(z, Math.floorDiv(candidate.originZ(), natural.cellBlocks()),
                                    "candidate escaped its cell on Z");
                        });
            }
        }
    }

    /** Negative coordinates behave the same as positive ones. */
    @Test
    void theGridWorksTheSameInEveryQuadrant() {
        NaturalGeneration natural = natural();
        for (int[] cell : new int[][] {{-30, -30}, {-30, 30}, {30, -30}, {30, 30}}) {
            int x = cell[0];
            int z = cell[1];
            NaturalDepositSelector.candidateFor(13L, silica(), natural, x, z)
                    .ifPresent(candidate -> {
                        assertEquals(x, Math.floorDiv(candidate.originX(), natural.cellBlocks()));
                        assertEquals(z, Math.floorDiv(candidate.originZ(), natural.cellBlocks()));
                        assertTrue(candidate.radius() >= natural.minRadius());
                    });
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Configuration validity                                             */
    /* ------------------------------------------------------------------ */

    @Test
    void anImpossibleDistributionIsRefusedByTheConfigurationItself() {
        assertThrows(IllegalStateException.class, () -> new NaturalGeneration(
                "minecraft:overworld", "britannia_mod:x", 0, 0.5, 8, 13, 5, 2, 40, 96, 1,
                ShapeTuning.DEFAULT), "a zero-chunk cell was accepted");
        assertThrows(IllegalStateException.class, () -> new NaturalGeneration(
                "minecraft:overworld", "britannia_mod:x", 24, 0.0, 8, 13, 5, 2, 40, 96, 1,
                ShapeTuning.DEFAULT), "a zero occurrence chance was accepted");
        assertThrows(IllegalStateException.class, () -> new NaturalGeneration(
                "minecraft:overworld", "britannia_mod:x", 24, 1.5, 8, 13, 5, 2, 40, 96, 1,
                ShapeTuning.DEFAULT), "an impossible occurrence chance was accepted");
        assertThrows(IllegalStateException.class, () -> new NaturalGeneration(
                "minecraft:overworld", "britannia_mod:x", 24, 0.5, 13, 8, 5, 2, 40, 96, 1,
                ShapeTuning.DEFAULT), "an inverted radius range was accepted");
        assertThrows(IllegalStateException.class, () -> new NaturalGeneration(
                "minecraft:overworld", "britannia_mod:x", 24, 0.5, 8, 13, 5, 2, 96, 40, 1,
                ShapeTuning.DEFAULT), "an inverted altitude band was accepted");
        assertThrows(IllegalStateException.class, () -> new NaturalGeneration(
                "minecraft:overworld", "britannia_mod:x", 5000, 0.5, 8, 13, 5, 2, 40, 96, 1,
                ShapeTuning.DEFAULT), "an absurd cell size was accepted");
    }

    /* ------------------------------------------------------------------ */
    /*  The seam is general                                                */
    /* ------------------------------------------------------------------ */

    /**
     * A second resource uses the same machinery with no Java written for it.
     *
     * <p>The milestone's real deliverable is the seam, not silica. This drives the selector with a
     * synthetic resource that shares nothing with silica but the platform: a different id, a
     * different salt, a different shape, a different grid. If any part of the selector had grown a
     * {@code if (resource == silica)} it could not answer this at all.
     */
    @Test
    void aSyntheticSecondResourceDistributesWithoutAnyCodeOfItsOwn() {
        ResourceDefinition synthetic = new ResourceDefinition(
                "britannia_mod:test_gravel_lens",
                "Test Gravel Lens",
                ResourceDefinition.Family.SEDIMENT,
                List.of("britannia_mod:silica_sand_deposit"),
                Optional.empty(),
                "britannia_mod:silica_shovels",
                new ResourceDefinition.Yield(
                        ResourceDefinition.Yield.Mode.ITEM, Optional.of("minecraft:gravel"), 1),
                ResourceDefinition.DepletedState.FLUID_AWARE_AIR,
                6,
                Optional.of(new ResourceDefinition.Generation(
                        ResourceShape.SEDIMENTARY_LENS, "britannia_mod:silica_sand_deposit",
                        6, 9, "britannia_mod:silica_hosts",
                        Optional.of(new NaturalGeneration(
                                "minecraft:overworld", "britannia_mod:has_silica_deposits",
                                16, 0.5, 6, 9, 3, 1, 30, 90, 991,
                                new ShapeTuning(3, 0.25, 0.2))))),
                1);

        NaturalGeneration natural = synthetic.natural().orElseThrow();
        int produced = 0;
        for (int cellX = 0; cellX < 40; cellX++) {
            for (int cellZ = 0; cellZ < 40; cellZ++) {
                Optional<NaturalDepositSelector.Candidate> candidate =
                        NaturalDepositSelector.candidateFor(77L, synthetic, natural, cellX, cellZ);
                if (candidate.isEmpty()) {
                    continue;
                }
                produced++;
                assertEquals(synthetic.id(), candidate.get().resourceId());
                assertTrue(candidate.get().radius() >= 6 && candidate.get().radius() <= 9);
                assertEquals(DepositIdentity.natural(77L, "minecraft:overworld", synthetic.id(),
                                cellX, cellZ, 991),
                        candidate.get().instanceId(),
                        "the synthetic resource did not get the standard natural identity");
            }
        }
        assertTrue(produced > 500,
                "the synthetic resource produced only " + produced + " candidates in 1600 cells");

        // And its distribution is its own. The two use different cell sizes, so the meaningful
        // check is that the synthetic resource's origins do not simply reproduce silica's.
        int matchingOrigins = 0;
        int compared = 0;
        for (int cellX = 0; cellX < 40; cellX++) {
            for (int cellZ = 0; cellZ < 40; cellZ++) {
                Optional<NaturalDepositSelector.Candidate> a =
                        NaturalDepositSelector.candidateFor(77L, synthetic, natural, cellX, cellZ);
                Optional<NaturalDepositSelector.Candidate> b =
                        NaturalDepositSelector.candidateFor(77L, silica(), natural(), cellX, cellZ);
                if (a.isEmpty() || b.isEmpty()) {
                    continue;
                }
                compared++;
                if (a.get().originX() == b.get().originX() && a.get().originZ() == b.get().originZ()) {
                    matchingOrigins++;
                }
                assertNotEquals(a.get().instanceId(), b.get().instanceId(),
                        "the synthetic resource shares silica's deposit identity");
            }
        }
        assertTrue(compared > 100, "not enough overlapping cells to compare");
        assertTrue(matchingOrigins < compared / 10,
                matchingOrigins + " of " + compared + " origins coincided exactly with silica's");
    }

    private static NaturalGeneration withSalt(NaturalGeneration source, int salt) {
        return new NaturalGeneration(source.dimensionId(), source.biomeTag(), source.cellChunks(),
                source.chance(), source.minRadius(), source.maxRadius(), source.depth(),
                source.depthJitter(), source.minY(), source.maxY(), salt, source.tuning());
    }
}
