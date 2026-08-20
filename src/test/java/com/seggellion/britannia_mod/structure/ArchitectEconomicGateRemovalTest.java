package com.seggellion.britannia_mod.structure;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 8: the Architect's local economy is gone, and stays gone.
 *
 * <p>The block used to require 400 food and 200 wood in the city before it would spawn an
 * Architect, and would despawn one when either fell. That was a second opinion capable of
 * disagreeing with Rails in both directions, and the milestone's whole point is that there is
 * now one authority. The count of economic thresholds in mod production code is zero.
 *
 * <p>Asserted against the source rather than through behaviour because the claim is an absence,
 * and an absence has no behaviour to observe. The same approach {@code GrabbyProtectionAuditTest}
 * already uses for a rule that must not be re-implemented locally.
 */
class ArchitectEconomicGateRemovalTest {

    private static final Path BLOCK_ENTITY = Path.of(System.getProperty("britannia.projectDir", "."))
            .resolve("src/main/java/com/seggellion/britannia_mod/block/entity/ArchitectSpawnBlockEntity.java");

    @Test
    void theArchitectBlockNeverAsksAboutCitySupply() throws IOException {
        String source = Files.readString(BLOCK_ENTITY);
        for (String forbidden : List.of(
                "CityFoodSupplyCache", "REQUIRED_FOOD", "REQUIRED_WOOD",
                "pollFoodAndWood", "currentFood", "currentWood")) {
            assertFalse(source.contains(forbidden),
                    "ArchitectSpawnBlockEntity still mentions " + forbidden + ". Whether a city has "
                            + "earned an Architect is Rails' decision now; a local reading of city "
                            + "supply can only disagree with it.");
        }
    }

    @Test
    void theArchitectBlockSpawnsNothingOnItsOwnAuthority() throws IOException {
        String source = Files.readString(BLOCK_ENTITY);
        for (String forbidden : List.of("addFreshEntity", "spawnArchitect", "spawnTownspersons",
                "TOWNSPERSON_COUNT", "MAX_ARCHITECTS")) {
            assertFalse(source.contains(forbidden),
                    "ArchitectSpawnBlockEntity still contains " + forbidden + ". The only thing that "
                            + "may put an Architect in the world is the assignment reconciler acting "
                            + "on a Rails assignment.");
        }
    }

    @Test
    void itMigratesOntoTheAuthoritativePostArchitecture() throws IOException {
        String source = Files.readString(BLOCK_ENTITY);
        assertTrue(source.contains("LegacySpawnBlockMigrator.migrateArchitectBlock"),
                "the Architect block no longer migrates itself, so its post would never register "
                        + "with Rails and no assignment could ever reach it");
    }

    /**
     * The supply cache itself stays. Five other spawn blocks still read it, and this milestone
     * is about the Architect's authority rather than about deleting shared infrastructure.
     */
    @Test
    void theCitySupplyCacheSurvivesForItsOtherConsumers() throws IOException {
        Path root = Path.of(System.getProperty("britannia.projectDir", "."))
                .resolve("src/main/java/com/seggellion/britannia_mod");
        assertTrue(Files.exists(root.resolve("network/CityFoodSupplyCache.java")),
                "CityFoodSupplyCache was removed, but the Architect was not its only consumer");

        long consumers;
        try (var files = Files.walk(root)) {
            consumers = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith("CityFoodSupplyCache.java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains("CityFoodSupplyCache");
                        } catch (IOException unreadable) {
                            return false;
                        }
                    })
                    .count();
        }
        assertTrue(consumers >= 4,
                "expected the blacksmith, horse, wood, metal and stone spawn blocks to still read "
                        + "the supply cache; found " + consumers + " consumers");
    }
}
