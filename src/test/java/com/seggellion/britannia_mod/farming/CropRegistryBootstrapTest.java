package com.seggellion.britannia_mod.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The crop table must publish completely or not at all.
 *
 * <p>Published {@code patch-18} set the {@code bootstrapped} flag before defining any crop, so a
 * caller arriving before deferred item registration had settled could abort partway and leave the
 * flag set over an empty table. Every later call took the fast path and saw zero crops for the rest
 * of the JVM's life. In production a client reached this through {@code ItemStack#getHoverName} -
 * a call any mod may legally make during startup, and FreeCam did - so load order alone was enough
 * to permanently empty the registry. It surfaced much later as
 * "Incomplete Farming progression definitions: species=7, crops=0" from a validator that was merely
 * the first thing to notice, naming neither the cause nor the moment.
 */
class CropRegistryBootstrapTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final int EXPECTED_CROPS = FarmingSkillRequirementValidator.EXPECTED_CROPS;

    /**
     * Required before touching the registry: {@code ItemRegistry}'s deferred holders cannot bind
     * without this, and a failed class initialisation is permanent for the JVM, which would break
     * every later test sharing it.
     */
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Without the {@code @BeforeAll} bootstrap above, {@code ItemRegistry} cannot initialise and the
     * crop build fails on its first definition - which is exactly the too-early call that broke the
     * shipped client, and is how this defect was reproduced. That failure arrives as an
     * {@code ExceptionInInitializerError} and thereafter {@code NoClassDefFoundError}: both
     * {@link Error}s rather than {@link RuntimeException}s, which is why {@code bootstrap()} has to
     * protect itself with {@code finally} rather than by catching a narrower type.
     */
    @Test
    void theRegistryPublishesEveryCropAndKeepsPublishingIt() {
        // Asserted unconditionally rather than tolerating a build failure, so that a regression in
        // bootstrapping fails here instead of quietly weakening every assertion below it.
        List<CropDefinition> first = CropRegistry.all();
        assertEquals(EXPECTED_CROPS, first.size(), "the crop table the shipped build found empty");
        assertEquals(EXPECTED_CROPS, CropRegistry.all().size(), "a later call saw a different table");
        assertSame(first.get(0), CropRegistry.all().get(0), "bootstrap rebuilt definitions");
        assertTrue(CropRegistry.byId("barley").isPresent(), "barley missing from a complete table");
        assertFalse(CropRegistry.all().isEmpty(), "the exact state the shipped client latched");
    }

    @Test
    void noConcurrentCallerEverObservesAnEmptyTable() throws Exception {
        // Whether the build succeeds or fails here, the one outcome that must never occur is a
        // caller receiving an empty list as though it were the finished article.
        int threads = 16;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Set<String> observed = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < threads; i++) {
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                    observed.add("size=" + CropRegistry.all().size());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (Throwable failed) {
                    observed.add("threw:" + failed.getClass().getSimpleName());
                } finally {
                    done.countDown();
                }
            });
            worker.setDaemon(true);
            worker.start();
        }

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "bootstrap deadlocked under concurrent access");
        assertEquals(Set.of("size=" + EXPECTED_CROPS), observed,
                "callers disagreed about the crop table, or one saw a partial build: " + observed);
    }

    @Test
    void theFlagIsPublishedOnlyAfterASuccessfulBuild() throws Exception {
        // Behavioural coverage cannot force an early failure here without live Minecraft
        // registries, so the ordering that caused the outage is pinned structurally.
        String source = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/farming/CropRegistry.java"));

        assertTrue(source.contains("private static volatile boolean bootstrapped"),
                "the flag must be volatile so the fast path cannot race the table it publishes");
        assertTrue(source.contains("if (completed) {")
                        && source.contains("bootstrapped = true;"),
                "the flag must be set only after the build completes");
        assertTrue(source.contains("CROPS.clear();") && source.contains("BY_ID.clear();"),
                "a failed build must discard partial state so the next call retries");

        int flagAssignment = source.indexOf("bootstrapped = true;");
        int firstCropDefinition = source.indexOf("crop(\"squash\"");
        assertTrue(flagAssignment < firstCropDefinition,
                "the success flag must live in bootstrap(), ahead of the definitions it guards");
        assertTrue(source.indexOf("defineCrops();") < firstCropDefinition,
                "definitions must be built through defineCrops() so failure can be caught");
    }

    @Test
    void nameLookupFailuresAreReportedRatherThanSwallowed() throws Exception {
        // The presentation layer sits on ItemStack#getHoverName and legitimately fails closed, but
        // it used to discard the exception, which is why the real cause never reached any log.
        String source = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/farming/FarmingPlantingItemPresentation.java"));
        int catchBlock = source.indexOf("catch (RuntimeException unresolvedAmbiguity)");
        assertTrue(catchBlock >= 0, "the fail-closed catch is gone");
        int fallback = source.indexOf("unidentifiedNameKey()", catchBlock);
        assertTrue(source.substring(catchBlock, fallback).contains("LOGGER.error"),
                "a species-resolution failure must be logged before falling back to a generic name");
    }
}
