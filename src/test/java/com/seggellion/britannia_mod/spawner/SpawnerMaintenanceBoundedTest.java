package com.seggellion.britannia_mod.spawner;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Both spawn block entities must route townsperson top-up through the bounded policy.
 *
 * <h2>Why this is a source-level test</h2>
 * {@link PopulationMaintenanceTest} proves the policy terminates, but it cannot prove the two
 * spawners actually use it — and the defect was not in the policy, it was that each class carried
 * its own unbounded loop. The behavioural alternative would be a GameTest that stands a spawner
 * somewhere nothing can spawn and waits, but against the unfixed code that test would not fail: it
 * would wedge the GameTest server's own thread inside a single tick, with no tick budget left to
 * time it out. A test that hangs CI rather than reddening it is worse than no test.
 *
 * <p>So this reads the two sources and asserts the shape directly. It is deliberately narrow: it
 * pins the absence of the specific construct that froze production and the presence of the bounded
 * call that replaced it, in both places, and says nothing about how either method is otherwise
 * written.
 */
final class SpawnerMaintenanceBoundedTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path SPAWNERS = PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/block/entity");

    private static final List<String> CLASSES =
            List.of("TraderSpawnBlockEntity.java", "MerchantSpawnBlockEntity.java");

    @Test
    void neitherSpawnerLoopsUntilTheShortfallIsGone() throws IOException {
        for (String className : CLASSES) {
            String source = Files.readString(SPAWNERS.resolve(className));
            assertFalse(source.contains("while (townNpcIds.size() < townPersonAmount)"),
                    className + " still fills its shortfall with a loop whose termination depends on "
                            + "spawnTownsperson succeeding. That loop freezes the server thread outright "
                            + "when no valid spawn position exists.");
        }
    }

    @Test
    void bothSpawnersTopUpThroughTheBoundedPolicy() throws IOException {
        for (String className : CLASSES) {
            String source = Files.readString(SPAWNERS.resolve(className));
            assertAll(className,
                    () -> assertTrue(source.contains("PopulationMaintenance.fill("),
                            className + " does not route townsperson top-up through PopulationMaintenance"),
                    () -> assertTrue(source.contains("MAX_TOWNSPERSON_SPAWN_ATTEMPTS_PER_CYCLE"),
                            className + " declares no per-cycle attempt ceiling"));
        }
    }

    @Test
    void bothSpawnTownspersonMethodsReportWhetherTheyPlacedAnyone() throws IOException {
        // The bound is only real if the policy can tell a success from a failure; a void method
        // would make every attempt look like progress.
        for (String className : CLASSES) {
            String source = Files.readString(SPAWNERS.resolve(className));
            assertTrue(source.contains("private boolean spawnTownsperson("),
                    className + " must return whether a townsperson was placed");
        }
    }
}
