package com.seggellion.britannia_mod.network;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The food-supply reading that gates merchant spawning, now that it no longer blocks the tick.
 *
 * <p>The three food-gated spawn blocks used to call Rails synchronously from {@code tick()} and act
 * on whatever came back. The value still gates the same decisions; what changed is that the tick
 * reads the last answer instead of waiting for the next one. These tests pin the parts of that
 * substitution which could silently change gameplay: that a city with no reading yet is left alone
 * rather than treated as starving, that a Rails-reported zero still reads as zero, and that a local
 * queue failure is not mistaken for a Rails verdict.
 */
final class CityFoodSupplyCacheTest {

    @BeforeEach
    void reset() {
        CityFoodSupplyCache.clear();
    }

    /** A transport whose completion the test drives by hand. */
    private static final class ManualTransport {
        private final AtomicInteger submissions = new AtomicInteger();
        private CompletableFuture<Double> pending;

        CompletableFuture<Double> submit() {
            submissions.incrementAndGet();
            pending = new CompletableFuture<>();
            return pending;
        }

        void answer(double food) {
            pending.complete(food);
        }

        void fail(Throwable cause) {
            pending.completeExceptionally(cause);
        }
    }

    @Test
    void theFirstPollReturnsNothingSoTheCallerSkipsRatherThanTreatingItAsStarvation() {
        ManualTransport transport = new ManualTransport();

        OptionalDouble first = CityFoodSupplyCache.poll("Britain", transport::submit);

        assertAll(
                () -> assertTrue(first.isEmpty(),
                        "a city Rails has not answered for yet must not read as a value"),
                () -> assertEquals(1, transport.submissions.get(), "the first poll must start a fetch"));
    }

    @Test
    void onceRailsAnswersTheReadingIsServedFromCache() {
        ManualTransport transport = new ManualTransport();

        CityFoodSupplyCache.poll("Britain", transport::submit);
        transport.answer(250.0);

        assertEquals(OptionalDouble.of(250.0), CityFoodSupplyCache.poll("Britain", transport::submit));
    }

    @Test
    void aPollWhileOneIsInFlightDoesNotStartASecondRequest() {
        ManualTransport transport = new ManualTransport();

        CityFoodSupplyCache.poll("Britain", transport::submit);
        CityFoodSupplyCache.poll("Britain", transport::submit);
        CityFoodSupplyCache.poll("Britain", transport::submit);

        assertEquals(1, transport.submissions.get(),
                "several spawn blocks in one city must share a single fetch");
    }

    @Test
    void aCompletedFetchReleasesTheGuardSoTheNextCycleRefreshes() {
        ManualTransport transport = new ManualTransport();

        CityFoodSupplyCache.poll("Britain", transport::submit);
        transport.answer(250.0);
        CityFoodSupplyCache.poll("Britain", transport::submit);

        assertEquals(2, transport.submissions.get(), "the guard must clear on success");
    }

    @Test
    void twoCitiesAreTrackedIndependently() {
        ManualTransport britain = new ManualTransport();
        ManualTransport minoc = new ManualTransport();

        CityFoodSupplyCache.poll("Britain", britain::submit);
        britain.answer(250.0);
        CityFoodSupplyCache.poll("Minoc", minoc::submit);

        assertAll(
                () -> assertEquals(OptionalDouble.of(250.0),
                        CityFoodSupplyCache.poll("Britain", britain::submit)),
                () -> assertTrue(CityFoodSupplyCache.poll("Minoc", minoc::submit).isEmpty(),
                        "Minoc has no reading of its own and must not inherit Britain's"));
    }

    @Test
    void aRailsReportedZeroIsStillZeroSoStarvationDespawnsAsBefore() {
        // fetchFoodSupply answers 0.0 for a Rails-side failure and callers read that as "starving".
        // That contract predates this cache and is deliberately unchanged.
        ManualTransport transport = new ManualTransport();

        CityFoodSupplyCache.poll("Britain", transport::submit);
        transport.answer(0.0);

        assertEquals(OptionalDouble.of(0.0), CityFoodSupplyCache.poll("Britain", transport::submit));
    }

    @Test
    void aRejectedSubmissionDoesNotFabricateAStarvingReading() {
        // Queue saturation says nothing about the city's food. Reporting it as 0.0 would despawn a
        // city's merchants over a local backlog.
        ManualTransport transport = new ManualTransport();

        CityFoodSupplyCache.poll("Britain", transport::submit);
        transport.fail(new RejectedExecutionException("queue full"));

        assertTrue(CityFoodSupplyCache.poll("Britain", transport::submit).isEmpty(),
                "a local transport failure must not be reported as a food reading");
    }

    @Test
    void aFailedRefreshLeavesTheLastGoodReadingInPlace() {
        ManualTransport transport = new ManualTransport();

        CityFoodSupplyCache.poll("Britain", transport::submit);
        transport.answer(250.0);
        CityFoodSupplyCache.poll("Britain", transport::submit);
        transport.fail(new RejectedExecutionException("queue full"));

        assertEquals(OptionalDouble.of(250.0), CityFoodSupplyCache.poll("Britain", transport::submit),
                "a failed refresh must not erase the reading the tick was already acting on");
    }

    @Test
    void aFailedFetchReleasesTheGuardSoItCanRetry() {
        ManualTransport transport = new ManualTransport();

        CityFoodSupplyCache.poll("Britain", transport::submit);
        transport.fail(new RejectedExecutionException("queue full"));
        CityFoodSupplyCache.poll("Britain", transport::submit);

        assertEquals(2, transport.submissions.get(),
                "a failure must not permanently suppress later refreshes");
    }

    @Test
    void aSubmitterThatThrowsOutrightDoesNotEscapeIntoTheTick() {
        // The caller is a block entity tick. A rejected executor must not become an exception
        // travelling up into the world loop.
        OptionalDouble reading = CityFoodSupplyCache.poll("Britain", () -> {
            throw new RejectedExecutionException("executor is shut down");
        });

        assertTrue(reading.isEmpty());
    }

    @Test
    void aSubmitterThatThrowsOutrightStillReleasesTheGuard() {
        CityFoodSupplyCache.poll("Britain", () -> {
            throw new RejectedExecutionException("executor is shut down");
        });

        // whenComplete never ran, so the guard has to be released on the throwing path or this city
        // would never refresh again.
        ManualTransport transport = new ManualTransport();
        CityFoodSupplyCache.poll("Britain", transport::submit);

        assertEquals(1, transport.submissions.get(), "the guard must not latch when submit throws");
    }

    @Test
    void pollNeverBlocksOnASlowTransport() {
        // The tick-facing property: a transport that never completes must not hold the caller.
        CompletableFuture<Double> neverCompletes = new CompletableFuture<>();

        long startedAt = System.nanoTime();
        OptionalDouble reading = CityFoodSupplyCache.poll("Britain", () -> neverCompletes);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertAll(
                () -> assertTrue(reading.isEmpty()),
                () -> assertTrue(elapsedMillis < 1000L,
                        "poll must return immediately, took " + elapsedMillis + "ms"),
                () -> assertFalse(neverCompletes.isDone()));
    }
}
