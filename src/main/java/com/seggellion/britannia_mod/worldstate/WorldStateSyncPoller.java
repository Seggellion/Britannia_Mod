package com.seggellion.britannia_mod.worldstate;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsCache;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * Server-scoped poller for the Milestone 13 world-state change log. Registered per {@link
 * MinecraftServer} after {@code ServerStartedEvent} and stopped during {@code
 * ServerStoppingEvent}, exactly like {@code ServiceNpcSpawnDeliveryProcessor}'s own lifecycle
 * (see that class's doc, docs/service_npc_spawn_delivery_processor.md) -- this is the second
 * user of that same approved pattern, not a new one.
 *
 * SLICE 2 SCOPE (Milestone 13 NeoForge Slice 2)
 *
 * A validated response is now actually applied: {@link #responseApplier} builds a candidate
 * cache snapshot and, only if that candidate is itself internally consistent, durably commits it
 * -- see {@link ServiceNpcAssignmentsCandidateApply} and {@code
 * ServiceNpcAssignmentsCache#applyWorldStateChanges} for the mechanism and the three-policy
 * distinction (on-disk corruption vs. an invalid batch vs. a candidate that fails its own sanity
 * check) that governs it. This replaces Slice 1's "hold in memory and discard" placeholder.
 *
 * The version this poller requests each poll ({@link #lastKnownVersionSource}) is no longer a
 * field this class owns -- Slice 1 tracked it in memory only, which meant a restart silently
 * forgot how far this server had actually synced. It now reads {@code
 * ServiceNpcAssignmentsCache#lastAppliedWorldStateVersion()} directly in production, the same
 * durable value {@link #responseApplier} just committed, so there is exactly one authoritative
 * source for "how far has this server actually applied" -- this class never keeps its own copy
 * that could drift from it.
 *
 * SLICE 3 SCOPE (Milestone 13 NeoForge Slice 3)
 *
 * A validated response reporting {@code full_bootstrap_required} is dispatched to {@link
 * #fullBootstrapApplier} instead of {@link #responseApplier} -- its {@code changes} array is
 * guaranteed empty per the real Rails contract, so there is nothing to incrementally apply; see
 * {@link WorldStateFullBootstrapFallback} for the real fallback mechanism this reuses. This is
 * the last piece of Milestone 13's NeoForge side.
 *
 * CADENCE AND JITTER
 *
 * Base cadence is 5 minutes (6000 ticks at 20 ticks/second). A uniformly random jitter of 0-60
 * seconds (0-1200 ticks, ~20% of the base interval) is added on top and re-rolled after every
 * fire, including the very first one scheduled at construction time (registered from {@code
 * ServerStartedEvent}). This directly targets the milestone's own named risk: many Minecraft
 * servers on a shard tend to restart within a narrow window of each other (a scheduled
 * maintenance restart, a host reboot, a patch rollout), and a fixed cadence with no jitter would
 * have all of them poll Rails in lockstep every 5 minutes. Re-rolling the jitter every cycle
 * (not just once at startup) also prevents the population from re-converging over time the way a
 * single fixed offset per server eventually would under enough restarts. The worst-case
 * staleness this trades for is a bounded 6 minutes between polls, which is an acceptable cost
 * for data that does not need sub-minute recency.
 */
public final class WorldStateSyncPoller {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 5 minutes at 20 ticks/second. */
    public static final int BASE_CADENCE_TICKS = 6000;

    /** Up to 60 seconds of additional randomized delay, re-rolled every cycle. See class doc. */
    public static final int MAX_JITTER_TICKS = 1200;

    /**
     * 5 seconds. Bounds how often {@link #requestImmediatePoll} can actually reach Rails, so the
     * Refresh button on a Service NPC spawn post cannot be turned into a request amplifier by
     * holding it down. The menu behind that button already requires permission level 2, and this
     * cooldown is per server rather than per player, so the worst case for the whole shard is 12
     * polls a minute against an endpoint that permits 300 -- the guard is about not being
     * gratuitous, not about a real threat.
     */
    public static final int FORCED_POLL_COOLDOWN_TICKS = 100;

    private static final Map<MinecraftServer, WorldStateSyncPoller> ACTIVE = new HashMap<>();

    @FunctionalInterface
    public interface ResponseApplier {
        ServiceNpcAssignmentsCandidateApply.Result apply(MinecraftServer server, WorldStateChangesResponse response);
    }

    @FunctionalInterface
    public interface FullBootstrapApplier {
        CompletableFuture<WorldStateFullBootstrapFallback.Result> apply(MinecraftServer server, long targetVersion);
    }

    private final MinecraftServer server;
    private final WorldStateChangesClient client;
    private final IntSupplier jitterSource;
    private final Consumer<Runnable> tickThreadScheduler;
    private final LongSupplier lastKnownVersionSource;
    private final ResponseApplier responseApplier;
    private final FullBootstrapApplier fullBootstrapApplier;
    private final List<Runnable> settlementListeners = new ArrayList<>();
    private boolean stopped;
    private boolean pollInFlight;
    private int ticksUntilNextPoll;
    private int forcedPollCooldownTicks;
    private String pinnedShardPublicId;
    private volatile WorldStateSyncOutcome lastOutcome = WorldStateSyncOutcome.neverPolled();

    private WorldStateSyncPoller(
            MinecraftServer server, WorldStateChangesClient client, IntSupplier jitterSource,
            Consumer<Runnable> tickThreadScheduler, LongSupplier lastKnownVersionSource, ResponseApplier responseApplier,
            FullBootstrapApplier fullBootstrapApplier
    ) {
        this.server = server;
        this.client = Objects.requireNonNull(client, "client");
        this.jitterSource = Objects.requireNonNull(jitterSource, "jitterSource");
        this.tickThreadScheduler = Objects.requireNonNull(tickThreadScheduler, "tickThreadScheduler");
        this.lastKnownVersionSource = Objects.requireNonNull(lastKnownVersionSource, "lastKnownVersionSource");
        this.responseApplier = Objects.requireNonNull(responseApplier, "responseApplier");
        this.fullBootstrapApplier = Objects.requireNonNull(fullBootstrapApplier, "fullBootstrapApplier");
        this.ticksUntilNextPoll = BASE_CADENCE_TICKS + boundedJitter();
    }

    public static synchronized WorldStateSyncPoller start(MinecraftServer server) {
        return ACTIVE.computeIfAbsent(server, key -> {
            WorldStateSyncPoller poller = new WorldStateSyncPoller(
                    key, new WorldStateChangesClient(), WorldStateSyncPoller::rollJitter, key::execute,
                    () -> ServiceNpcAssignmentsCache.get(key.overworld()).lastAppliedWorldStateVersion(),
                    WorldStateSyncApply::applyAndCommit, WorldStateFullBootstrapFallback::triggerAndApply
            );
            LOGGER.info("World state sync poller started next_poll_in_ticks={}", poller.ticksUntilNextPoll);
            return poller;
        });
    }

    public static synchronized void tick(MinecraftServer server) {
        WorldStateSyncPoller poller = ACTIVE.get(server);
        if (poller != null) poller.onTick();
    }

    /**
     * Fires a poll right now instead of waiting out the remaining 5-6 minutes of the cadence, and
     * calls {@code onSettled} on the server thread once that poll reaches a terminal outcome --
     * applied, rejected, or failed. Returns false when no callback will ever come: no poller is
     * registered for this server, it is already stopping, or the forced-poll cooldown has not
     * expired. A caller that gets false is expected to carry on with whatever it already knows,
     * never to wait.
     *
     * <p>A poll already in flight is joined rather than duplicated -- {@code onSettled} is
     * attached to the one already running, which is the same answer a second request would have
     * fetched anyway.
     *
     * <p>The cadence is re-rolled from this moment, exactly as if the scheduled poll had fired:
     * a forced poll is a real poll, and following it 30 seconds later with the scheduled one
     * would be asking the same question twice for no reason.
     */
    public static synchronized boolean requestImmediatePoll(MinecraftServer server, @Nullable Runnable onSettled) {
        WorldStateSyncPoller poller = ACTIVE.get(server);
        return poller != null && poller.requestImmediatePoll(onSettled);
    }

    /**
     * The most recent poll's outcome for this server, or {@link WorldStateSyncOutcome.NeverPolled}
     * when no poller is registered (a client-only or not-yet-started server). Read by the Service
     * NPC spawn block's state payload; see {@link WorldStateSyncStatusText}.
     */
    public static synchronized WorldStateSyncOutcome lastOutcome(MinecraftServer server) {
        WorldStateSyncPoller poller = ACTIVE.get(server);
        return poller == null ? WorldStateSyncOutcome.neverPolled() : poller.lastOutcome;
    }

    public static synchronized void stop(MinecraftServer server) {
        WorldStateSyncPoller poller = ACTIVE.remove(server);
        if (poller != null) poller.close();
    }

    public static synchronized int activePollerCount() {
        return ACTIVE.size();
    }

    /** Test-only observation of the real, server-registered instance -- lets a GameTest inspect {@link #lastOutcomeForTest()} on the actual production poller instead of a {@link #newForTest} double. */
    public static synchronized WorldStateSyncPoller activePollerForTest(MinecraftServer server) {
        return ACTIVE.get(server);
    }

    /**
     * Test-only construction bypassing the server-keyed registry: no {@link MinecraftServer}
     * instance is required because every seam is fully substitutable, exactly like every other
     * Rails HTTP client in this codebase (see {@code BankingOpenClient}/{@code
     * ServiceNpcSpawnRegistrationClient}'s own test constructors).
     */
    public static WorldStateSyncPoller newForTest(
            WorldStateChangesClient client, IntSupplier jitterSource, Consumer<Runnable> tickThreadScheduler,
            LongSupplier lastKnownVersionSource, ResponseApplier responseApplier, FullBootstrapApplier fullBootstrapApplier
    ) {
        return new WorldStateSyncPoller(
                null, client, jitterSource, tickThreadScheduler, lastKnownVersionSource, responseApplier, fullBootstrapApplier
        );
    }

    boolean requestImmediatePoll(@Nullable Runnable onSettled) {
        if (stopped) return false;
        if (pollInFlight) {
            if (onSettled != null) settlementListeners.add(onSettled);
            return true;
        }
        if (forcedPollCooldownTicks > 0) return false;
        forcedPollCooldownTicks = FORCED_POLL_COOLDOWN_TICKS;
        if (onSettled != null) settlementListeners.add(onSettled);
        ticksUntilNextPoll = BASE_CADENCE_TICKS + boundedJitter();
        LOGGER.info("World state sync poll forced by request next_poll_in_ticks={}", ticksUntilNextPoll);
        firePoll();
        return true;
    }

    public void onTick() {
        if (stopped) return;
        if (forcedPollCooldownTicks > 0) forcedPollCooldownTicks--;
        if (--ticksUntilNextPoll > 0) return;
        ticksUntilNextPoll = BASE_CADENCE_TICKS + boundedJitter();
        firePoll();
    }

    private void firePoll() {
        if (stopped || pollInFlight) return;
        pollInFlight = true;
        long requestedFromVersion = lastKnownVersionSource.getAsLong();
        LOGGER.info("World state sync poll attempted from_version={}", requestedFromVersion);
        client.fetchChangesSince(server, requestedFromVersion).whenComplete((result, failure) ->
                tickThreadScheduler.accept(() -> complete(requestedFromVersion, result, failure)));
    }

    private void complete(long requestedFromVersion, WorldStateChangesClient.Result result, Throwable failure) {
        if (stopped) {
            pollInFlight = false;
            return;
        }

        if (failure != null || result == null) {
            LOGGER.warn("World state sync poll failed code=unexpected_error from_version={}", requestedFromVersion, failure);
            settle(WorldStateSyncOutcome.transportFailure("unexpected_error"));
            return;
        }
        if (result instanceof WorldStateChangesClient.Failure failureResult) {
            LOGGER.warn("World state sync poll failed code={} from_version={}", failureResult.safeCode(), requestedFromVersion);
            settle(WorldStateSyncOutcome.transportFailure(failureResult.safeCode()));
            return;
        }

        WorldStateChangesResponse response = ((WorldStateChangesClient.Success) result).response();
        WorldStateSyncValidator.Result validation =
                WorldStateSyncValidator.validate(response, requestedFromVersion, pinnedShardPublicId);
        if (validation instanceof WorldStateSyncValidator.Rejected rejected) {
            LOGGER.warn("World state sync poll rejected reason={} from_version={} schema_version={}",
                    rejected.reason(), requestedFromVersion, response.schemaVersion());
            settle(WorldStateSyncOutcome.rejected(rejected.reason()));
            return;
        }

        if (pinnedShardPublicId == null) pinnedShardPublicId = response.shardPublicId();
        LOGGER.info(
                "World state sync poll validated from_version={} to_version={} current_version={} changes={} full_bootstrap_required={}",
                response.fromVersion(), response.toVersion(), response.currentVersion(),
                response.changes().size(), response.fullBootstrapRequired()
        );

        if (response.fullBootstrapRequired()) {
            // The changes array is guaranteed empty for this response per the real Rails
            // contract (see WorldStateChanges::ChangesSince), so there is nothing for
            // responseApplier to meaningfully apply -- the only correct recovery is a full
            // fetch. This stays async (a second network round trip), so pollInFlight is
            // deliberately left true until completeFullBootstrap runs, not reset here.
            fullBootstrapApplier.apply(server, response.currentVersion()).whenComplete((fullBootstrapResult, fullBootstrapFailure) ->
                    tickThreadScheduler.accept(() -> completeFullBootstrap(fullBootstrapResult, fullBootstrapFailure)));
            return;
        }

        ServiceNpcAssignmentsCandidateApply.Result applyResult = responseApplier.apply(server, response);
        if (applyResult instanceof ServiceNpcAssignmentsCandidateApply.Rejected rejected) {
            LOGGER.warn("World state sync apply rejected reason={} from_version={} to_version={}",
                    rejected.reason(), requestedFromVersion, response.toVersion());
            settle(WorldStateSyncOutcome.applyRejected(rejected.reason()));
            return;
        }

        LOGGER.info("World state sync poll applied and committed to_version={}", response.toVersion());
        settle(WorldStateSyncOutcome.accepted(response));
    }

    private void completeFullBootstrap(WorldStateFullBootstrapFallback.Result result, Throwable failure) {
        if (stopped) {
            pollInFlight = false;
            return;
        }

        if (failure != null || result == null) {
            LOGGER.warn("World state full bootstrap fallback failed code=unexpected_error", failure);
            settle(WorldStateSyncOutcome.fullBootstrapDeferred("unexpected_error"));
            return;
        }
        if (result instanceof WorldStateFullBootstrapFallback.Applied applied) {
            LOGGER.info("World state full bootstrap fallback applied version={}", applied.version());
            settle(WorldStateSyncOutcome.fullBootstrapApplied(applied.version()));
        } else if (result instanceof WorldStateFullBootstrapFallback.NoPlayerOnline) {
            LOGGER.warn("World state full bootstrap fallback deferred reason=no_player_online");
            settle(WorldStateSyncOutcome.fullBootstrapDeferred("no_player_online"));
        } else if (result instanceof WorldStateFullBootstrapFallback.Failed failedResult) {
            LOGGER.warn("World state full bootstrap fallback deferred reason={}", failedResult.safeCode());
            settle(WorldStateSyncOutcome.fullBootstrapDeferred(failedResult.safeCode()));
        }
    }

    /**
     * The one place a poll ends. Every terminal path routes through here so {@link #pollInFlight},
     * {@link #lastOutcome} and the {@link #requestImmediatePoll} listeners can never disagree
     * about whether this cycle finished -- a listener left un-run by a path that forgot it would
     * leave the caller (a Refresh click) waiting for a redraw that never comes, and a
     * pollInFlight left true would wedge the poller permanently, silently skipping every
     * scheduled poll from then on.
     *
     * <p>Listeners are drained before they run, and each is isolated, so one throwing can neither
     * strand the others nor leave this poller wedged. The one deliberate path that does not come
     * through here is the stopped-server check at the top of {@link #complete} and {@link
     * #completeFullBootstrap}: a server on its way down has no outcome worth recording and nobody
     * left to tell.
     */
    private void settle(WorldStateSyncOutcome outcome) {
        pollInFlight = false;
        lastOutcome = outcome;
        if (settlementListeners.isEmpty()) return;
        List<Runnable> pending = List.copyOf(settlementListeners);
        settlementListeners.clear();
        for (Runnable listener : pending) {
            try {
                listener.run();
            } catch (RuntimeException listenerFailure) {
                LOGGER.warn("World state sync settlement listener failed", listenerFailure);
            }
        }
    }

    private void close() {
        if (stopped) return;
        stopped = true;
        // Nothing can settle after this, so anything still waiting for a callback is dropped
        // rather than left holding a menu on a server that is going away.
        settlementListeners.clear();
        LOGGER.info("World state sync poller stopped");
    }

    public boolean isStopped() {
        return stopped;
    }

    /** Test-only stop for instances built via {@link #newForTest}, which bypass the server-keyed registry {@link #stop} operates on. */
    public void stopForTest() {
        close();
    }

    public int ticksUntilNextPollForTest() {
        return ticksUntilNextPoll;
    }

    public WorldStateSyncOutcome lastOutcomeForTest() {
        return lastOutcome;
    }

    private int boundedJitter() {
        int candidate = jitterSource.getAsInt();
        if (candidate < 0 || candidate >= MAX_JITTER_TICKS) {
            throw new IllegalStateException("jitter source produced an out-of-bounds value: " + candidate);
        }
        return candidate;
    }

    /** Package-visible so {@code WorldStateSyncPollerTest} can prove the real default source is genuinely randomized, not a fixed offset. */
    static int rollJitter() {
        return ThreadLocalRandom.current().nextInt(MAX_JITTER_TICKS);
    }
}
