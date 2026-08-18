package com.seggellion.britannia_mod.network;

import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The last food-supply reading Rails gave us for each city, refreshed off the server thread.
 *
 * <h2>Why this exists</h2>
 * {@code HorseSpawnBlockEntity}, {@code WoodSpawnBlockEntity} and {@code StoneSpawnBlockEntity} each
 * called {@link CityDataSync#fetchFoodSupply} directly from {@code tick()}. That request is bounded
 * ({@code BoundedHttp}: 5s connect, 10s read), but a bounded wait is still a wait, and it was being
 * taken on the thread that runs the world. Unlike a heartbeat the value cannot simply be dropped -
 * it decides whether merchants spawn or despawn - so the fix is to keep serving the last reading
 * while a fresh one is fetched behind it, not to stop fetching.
 *
 * <h2>The state model</h2>
 * Deliberately the smallest one that preserves the existing contract:
 * <ul>
 *   <li><b>no reading yet</b> - {@link #poll} returns empty and the caller skips the cycle. This is
 *       the one new behaviour: previously the tick blocked until an answer existed. It costs one
 *       spawn cycle after load and only that, because the block entities poll every 1000 ticks
 *       (50s) and a fetch is bounded well inside that.</li>
 *   <li><b>reading available</b> - returned immediately, and a refresh starts if none is in flight.
 *       The value can be up to one cycle old; on a 50-second spawn cadence that is immaterial.</li>
 *   <li><b>in flight</b> - no second request is issued for the same city, so several spawn blocks in
 *       one city now share a single fetch instead of each making their own.</li>
 * </ul>
 *
 * <h2>What counts as failure</h2>
 * {@link CityDataSync#fetchFoodSupply} already answers {@code 0.0} for any Rails-side failure, and
 * callers already read {@code 0.0} as "starving, despawn". That is existing behaviour and is kept
 * exactly: a failed fetch stores {@code 0.0} as it always did.
 *
 * <p>Executor rejection and the overall-timeout cut-off are treated differently, because they are
 * failure modes this class introduces rather than verdicts Rails returned. Storing {@code 0.0} for a
 * saturated queue would despawn a city's merchants over a local backlog that says nothing about that
 * city's food. Those outcomes leave the previous reading untouched and simply retry next cycle.
 */
public final class CityFoodSupplyCache {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();

    private CityFoodSupplyCache() {}

    private static final class Entry {
        private boolean pending;
        private boolean hasValue;
        private double value;
    }

    /**
     * The most recent food reading for {@code cityName}, starting a refresh when none is in flight.
     *
     * @return the reading, or empty when Rails has never answered for this city - in which case the
     *         caller must neither spawn nor despawn, exactly as it would not have acted on a value
     *         it did not have.
     */
    public static OptionalDouble poll(ServerLevel serverLevel, String cityName) {
        return poll(cityName, () -> ServerHttpExecutor.submit(serverLevel.getServer(),
                () -> CityDataSync.fetchFoodSupply(serverLevel, cityName)));
    }

    /**
     * Seam for tests. Identical logic, but the caller supplies the transport - which is what lets
     * the coalescing and failure rules be exercised without a running server.
     */
    static OptionalDouble poll(String cityName, Supplier<CompletableFuture<Double>> submitter) {
        Entry entry = ENTRIES.computeIfAbsent(cityName, ignored -> new Entry());

        boolean startFetch;
        OptionalDouble current;
        synchronized (entry) {
            startFetch = !entry.pending;
            if (startFetch) entry.pending = true;
            current = entry.hasValue ? OptionalDouble.of(entry.value) : OptionalDouble.empty();
        }

        if (startFetch) {
            try {
                submitter.get().whenComplete((food, failure) -> {
                    synchronized (entry) {
                        entry.pending = false;
                        if (failure == null && food != null) {
                            entry.hasValue = true;
                            entry.value = food;
                        }
                    }
                    if (failure != null) {
                        // Queue saturation or the overall-timeout cut-off - not a Rails verdict on
                        // this city, so the previous reading stands and the next cycle tries again.
                        // There is deliberately no immediate retry.
                        LOGGER.debug("City food supply refresh did not complete city={} reason={}",
                                cityName, failure.getClass().getSimpleName());
                    }
                });
            } catch (RuntimeException submitFailure) {
                // The submit itself threw, so whenComplete will never run: release the guard here or
                // this city would never refresh again. Deliberately not rethrown - the caller is a
                // block entity tick, and a rejected executor must not become an exception escaping
                // into the world loop.
                synchronized (entry) {
                    entry.pending = false;
                }
                LOGGER.debug("City food supply refresh could not be submitted city={} reason={}",
                        cityName, submitFailure.getClass().getSimpleName());
            }
        }

        return current;
    }

    /** Drops every cached reading. For tests and for server shutdown. */
    public static void clear() {
        ENTRIES.clear();
    }
}
