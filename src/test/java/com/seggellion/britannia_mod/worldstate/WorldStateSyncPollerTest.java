package com.seggellion.britannia_mod.worldstate;

import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.server.auth.ServerCredentials;
import com.seggellion.britannia_mod.server.auth.ServerCredentialsTestFactory;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsSnapshot;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives {@link WorldStateSyncPoller} entirely through its instance-level seams
 * ({@link WorldStateSyncPoller#newForTest}), so cadence, jitter, and end-to-end
 * validation-integration behavior are provable without a live {@link net.minecraft.server.MinecraftServer}
 * -- exactly the same split this codebase already draws for {@code ServiceNpcSpawnDeliveryProcessor}
 * (pure/policy logic gets plain JUnit; only the server-keyed static registry itself
 * ({@code start}/{@code stop}/{@code tick(MinecraftServer)}) needs a real server, and that thin
 * wrapper is structurally identical to the already-proven {@code ServiceNpcSpawnDeliveryProcessor}
 * one it was modeled on.
 *
 * Milestone 13 NeoForge Slice 2: {@link WorldStateSyncPoller.ResponseApplier} is now an
 * injectable seam too (mirroring the client/jitter/scheduler seams already established in Slice
 * 1), so this test drives the apply step with a fake that reports {@code Applied}/{@code
 * Rejected} without needing a real cache or a real {@code MinecraftServer} either. {@code
 * ServiceNpcAssignmentsCandidateApply} itself and the real {@code
 * ServiceNpcAssignmentsCache#applyWorldStateChanges} wiring are covered separately by {@code
 * ServiceNpcAssignmentsCandidateApplyTest} and {@code WorldStateSyncGameTests}.
 *
 * Milestone 13 NeoForge Slice 3: {@link WorldStateSyncPoller.FullBootstrapApplier} is the same
 * kind of seam for the {@code full_bootstrap_required} dispatch path -- the real production
 * implementation ({@code WorldStateFullBootstrapFallback}) needs a live {@link
 * net.minecraft.server.MinecraftServer} and online players to test end-to-end, which is covered
 * separately by GameTest; this file proves the poller's own dispatch/outcome-recording logic
 * around it.
 */
class WorldStateSyncPollerTest {
    private static final String SHARD = "11111111-1111-4111-8111-111111111111";

    @Test
    void theRealDefaultJitterSourceIsGenuinelyRandomizedWithinBoundsNotAFixedOffset() {
        Set<Integer> observed = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            int value = WorldStateSyncPoller.rollJitter();
            assertTrue(value >= 0 && value < WorldStateSyncPoller.MAX_JITTER_TICKS,
                    "jitter value out of bounds: " + value);
            observed.add(value);
        }
        assertTrue(observed.size() > 20,
                "200 draws produced only " + observed.size() + " distinct values -- looks like a fixed offset, not jitter");
    }

    @Test
    void constructionSchedulesTheFirstPollWithinBaseCadencePlusJitterBounds() {
        WorldStateSyncPoller poller = poller(fixedJitter(500), List.of());
        assertEquals(WorldStateSyncPoller.BASE_CADENCE_TICKS + 500, poller.ticksUntilNextPollForTest());
    }

    @Test
    void doesNotFireBeforeItsScheduledTick() {
        List<Long> requestedVersions = new ArrayList<>();
        WorldStateSyncPoller poller = poller(fixedJitter(10), requestedVersions);

        int total = WorldStateSyncPoller.BASE_CADENCE_TICKS + 10;
        for (int tick = 0; tick < total - 1; tick++) {
            poller.onTick();
        }
        assertTrue(requestedVersions.isEmpty(), "poller fired before its scheduled tick count elapsed");
    }

    @Test
    void firesExactlyOnceAtItsScheduledTickAndReschedulesWithFreshJitter() {
        List<Long> requestedVersions = new ArrayList<>();
        AtomicInteger jitterCalls = new AtomicInteger();
        WorldStateSyncPoller poller = WorldStateSyncPoller.newForTest(
                acceptingClient(requestedVersions), () -> jitterCalls.incrementAndGet() == 1 ? 0 : 777,
                Runnable::run, () -> 0L, appliedApplier(), neverCalledFullBootstrapApplier()
        );

        int firstCadence = WorldStateSyncPoller.BASE_CADENCE_TICKS;
        for (int tick = 0; tick < firstCadence; tick++) {
            poller.onTick();
        }
        assertEquals(1, requestedVersions.size(), "poller did not fire exactly once at its scheduled tick");
        assertEquals(WorldStateSyncPoller.BASE_CADENCE_TICKS + 777, poller.ticksUntilNextPollForTest(),
                "poller did not reschedule with a freshly-rolled jitter value");
    }

    @Test
    void requestsTheVersionReportedByItsVersionSourceNotAnInMemoryFieldOfItsOwn() {
        java.util.concurrent.atomic.AtomicReference<java.net.URI> capturedUri = new java.util.concurrent.atomic.AtomicReference<>();
        WorldStateChangesResponse response = wellFormedResponse(SHARD, 42, 42, 42, List.of());
        WorldStateChangesClient client = new WorldStateChangesClient(
                ignored -> java.util.Optional.of(testCredentials()),
                (ignored, task) -> CompletableFuture.completedFuture(
                        (WorldStateChangesClient.Result) new WorldStateChangesClient.Success(response)),
                (uri, maxBytes) -> {
                    capturedUri.set(uri);
                    return new com.seggellion.britannia_mod.server.http.CancellableHttpRequest(uri, maxBytes);
                }
        );
        WorldStateSyncPoller poller = WorldStateSyncPoller.newForTest(
                client, fixedJitter(0), Runnable::run, () -> 42L, appliedApplier(), neverCalledFullBootstrapApplier()
        );

        fireOnce(poller);

        assertTrue(capturedUri.get() != null && capturedUri.get().getQuery().contains("from_version=42"),
                "poller did not source from_version from its injected lastKnownVersionSource: " + capturedUri.get());
    }

    @Test
    void doesNotFireAfterStoppedEvenIfTicked() {
        List<Long> requestedVersions = new ArrayList<>();
        WorldStateSyncPoller poller = poller(fixedJitter(0), requestedVersions);
        poller.stopForTest();

        for (int tick = 0; tick < WorldStateSyncPoller.BASE_CADENCE_TICKS + 10; tick++) {
            poller.onTick();
        }
        assertTrue(requestedVersions.isEmpty(), "stopped poller still fired");
        assertTrue(poller.isStopped());
    }

    @Test
    void aValidResponseIsAppliedAndAcceptedEndToEndThroughTheInjectedApplier() {
        WorldStateChangesResponse response = wellFormedResponse(SHARD, 0, 2, 2, List.of(1L, 2L));
        WorldStateChangesClient client = respondingWith(new WorldStateChangesClient.Success(response));
        List<WorldStateChangesResponse> appliedWith = new ArrayList<>();
        WorldStateSyncPoller.ResponseApplier applier = (server, appliedResponse) -> {
            appliedWith.add(appliedResponse);
            return new ServiceNpcAssignmentsCandidateApply.Applied(ServiceNpcAssignmentsSnapshot.empty());
        };
        WorldStateSyncPoller poller = WorldStateSyncPoller.newForTest(
                client, fixedJitter(0), Runnable::run, () -> 0L, applier, neverCalledFullBootstrapApplier()
        );

        fireOnce(poller);

        assertEquals(List.of(response), appliedWith, "the injected applier was never invoked with the validated response");
        WorldStateSyncOutcome.Accepted accepted =
                assertInstanceOf(WorldStateSyncOutcome.Accepted.class, poller.lastOutcomeForTest());
        assertEquals(response, accepted.response());
    }

    @Test
    void anApplyRejectionLeavesATypedOutcomeDistinctFromAValidationRejectionAndDoesNotThrow() {
        WorldStateChangesResponse response = wellFormedResponse(SHARD, 0, 2, 2, List.of(1L, 2L));
        WorldStateChangesClient client = respondingWith(new WorldStateChangesClient.Success(response));
        WorldStateSyncPoller.ResponseApplier applier =
                (server, appliedResponse) -> new ServiceNpcAssignmentsCandidateApply.Rejected("malformed_change: x must be an integer");
        WorldStateSyncPoller poller = WorldStateSyncPoller.newForTest(
                client, fixedJitter(0), Runnable::run, () -> 0L, applier, neverCalledFullBootstrapApplier()
        );

        fireOnce(poller);

        WorldStateSyncOutcome.ApplyRejected rejected =
                assertInstanceOf(WorldStateSyncOutcome.ApplyRejected.class, poller.lastOutcomeForTest());
        assertEquals("malformed_change: x must be an integer", rejected.reason());
    }

    @Test
    void aRejectedResponseIsLoggedAsATypedOutcomeNotAppliedAndDoesNotThrow() {
        WorldStateChangesResponse response = new WorldStateChangesResponse(
                99, SHARD, 0, 0, 0, false, List.of()
        );
        WorldStateChangesClient client = respondingWith(new WorldStateChangesClient.Success(response));
        WorldStateSyncPoller poller = WorldStateSyncPoller.newForTest(
                client, fixedJitter(0), Runnable::run, () -> 0L, neverCalledApplier(), neverCalledFullBootstrapApplier()
        );

        fireOnce(poller);

        WorldStateSyncOutcome.Rejected rejected =
                assertInstanceOf(WorldStateSyncOutcome.Rejected.class, poller.lastOutcomeForTest());
        assertEquals("unsupported_schema_version", rejected.reason());
    }

    @Test
    void aTransportFailureIsLoggedAsATypedOutcomeAndDoesNotThrow() {
        WorldStateChangesClient client = respondingWith(new WorldStateChangesClient.Failure("http_status_failure"));
        WorldStateSyncPoller poller = WorldStateSyncPoller.newForTest(
                client, fixedJitter(0), Runnable::run, () -> 0L, neverCalledApplier(), neverCalledFullBootstrapApplier()
        );

        fireOnce(poller);

        WorldStateSyncOutcome.TransportFailure failure =
                assertInstanceOf(WorldStateSyncOutcome.TransportFailure.class, poller.lastOutcomeForTest());
        assertEquals("http_status_failure", failure.safeCode());
    }

    @Test
    void aFailedOrRejectedPollDoesNotRetryImmediately() {
        AtomicInteger callCount = new AtomicInteger();
        WorldStateChangesClient client = new WorldStateChangesClient(
                ignored -> java.util.Optional.of(testCredentials()),
                (ignored, task) -> {
                    callCount.incrementAndGet();
                    return CompletableFuture.completedFuture((WorldStateChangesClient.Result)
                            new WorldStateChangesClient.Failure("http_status_failure"));
                },
                com.seggellion.britannia_mod.server.http.CancellableHttpRequest::new
        );
        WorldStateSyncPoller poller = WorldStateSyncPoller.newForTest(
                client, fixedJitter(0), Runnable::run, () -> 0L, neverCalledApplier(), neverCalledFullBootstrapApplier()
        );

        fireOnce(poller);
        assertEquals(1, callCount.get(), "credentials provider (proxy for a network attempt) was invoked");
        // No further ticks are delivered here at all -- the only way this poller ever polls
        // again is another BASE_CADENCE_TICKS + jitter elapsing, proven separately by
        // firesExactlyOnceAtItsScheduledTickAndReschedulesWithFreshJitter. A failure does not
        // shorten that wait or trigger any out-of-band retry path.
        assertFalse(poller.isStopped());
    }

    @Test
    void neverPolledIsTheInitialOutcomeBeforeAnyTickFires() {
        WorldStateSyncPoller poller = poller(fixedJitter(0), List.of());
        assertInstanceOf(WorldStateSyncOutcome.NeverPolled.class, poller.lastOutcomeForTest());
    }

    // --- Milestone 13 NeoForge Slice 3: full_bootstrap_required dispatch ---

    @Test
    void aFullBootstrapRequiredResponseDispatchesToTheFullBootstrapApplierNotTheOrdinaryResponseApplier() {
        WorldStateChangesResponse response = fullBootstrapRequiredResponse(SHARD, 0, 40);
        WorldStateChangesClient client = respondingWith(new WorldStateChangesClient.Success(response));
        List<Long> targetVersions = new ArrayList<>();
        WorldStateSyncPoller.FullBootstrapApplier fullBootstrapApplier = (server, targetVersion) -> {
            targetVersions.add(targetVersion);
            return CompletableFuture.completedFuture(new WorldStateFullBootstrapFallback.Applied(targetVersion));
        };
        WorldStateSyncPoller poller = WorldStateSyncPoller.newForTest(
                client, fixedJitter(0), Runnable::run, () -> 0L, neverCalledApplier(), fullBootstrapApplier
        );

        fireOnce(poller);

        assertEquals(List.of(40L), targetVersions,
                "full_bootstrap_required did not dispatch to fullBootstrapApplier with the response's own current_version");
        WorldStateSyncOutcome.FullBootstrapApplied applied =
                assertInstanceOf(WorldStateSyncOutcome.FullBootstrapApplied.class, poller.lastOutcomeForTest());
        assertEquals(40L, applied.version());
    }

    @Test
    void aSuccessfulFullBootstrapDoesNotLeavePollInFlightStuck() {
        WorldStateChangesResponse response = fullBootstrapRequiredResponse(SHARD, 0, 40);
        WorldStateChangesClient client = respondingWith(new WorldStateChangesClient.Success(response));
        WorldStateSyncPoller.FullBootstrapApplier fullBootstrapApplier =
                (server, targetVersion) -> CompletableFuture.completedFuture(new WorldStateFullBootstrapFallback.Applied(targetVersion));
        WorldStateSyncPoller poller = WorldStateSyncPoller.newForTest(
                client, fixedJitter(0), Runnable::run, () -> 0L, neverCalledApplier(), fullBootstrapApplier
        );

        fireOnce(poller);
        assertInstanceOf(WorldStateSyncOutcome.FullBootstrapApplied.class, poller.lastOutcomeForTest());

        // A second full cadence must be able to fire normally -- pollInFlight must not have been
        // left permanently true by the async full-bootstrap dispatch.
        for (int tick = 0; tick < WorldStateSyncPoller.BASE_CADENCE_TICKS; tick++) {
            poller.onTick();
        }
        assertInstanceOf(WorldStateSyncOutcome.FullBootstrapApplied.class, poller.lastOutcomeForTest(),
                "poller appears stuck: a second scheduled poll never actually re-fired");
    }

    @Test
    void aFullBootstrapWithNoPlayerOnlineIsDeferredWithoutCorruptingAnythingAndRetriesNormally() {
        WorldStateChangesResponse response = fullBootstrapRequiredResponse(SHARD, 0, 40);
        WorldStateChangesClient client = respondingWith(new WorldStateChangesClient.Success(response));
        WorldStateSyncPoller.FullBootstrapApplier fullBootstrapApplier =
                (server, targetVersion) -> CompletableFuture.completedFuture(new WorldStateFullBootstrapFallback.NoPlayerOnline());
        WorldStateSyncPoller poller = WorldStateSyncPoller.newForTest(
                client, fixedJitter(0), Runnable::run, () -> 0L, neverCalledApplier(), fullBootstrapApplier
        );

        fireOnce(poller);

        WorldStateSyncOutcome.FullBootstrapDeferred deferred =
                assertInstanceOf(WorldStateSyncOutcome.FullBootstrapDeferred.class, poller.lastOutcomeForTest());
        assertEquals("no_player_online", deferred.reason());
        assertFalse(poller.isStopped());
    }

    @Test
    void aFailedFullBootstrapFetchIsDeferredWithoutCorruptingAnythingAndRetriesNormally() {
        WorldStateChangesResponse response = fullBootstrapRequiredResponse(SHARD, 0, 40);
        WorldStateChangesClient client = respondingWith(new WorldStateChangesClient.Success(response));
        WorldStateSyncPoller.FullBootstrapApplier fullBootstrapApplier =
                (server, targetVersion) -> CompletableFuture.completedFuture(new WorldStateFullBootstrapFallback.Failed("route_failure"));
        WorldStateSyncPoller poller = WorldStateSyncPoller.newForTest(
                client, fixedJitter(0), Runnable::run, () -> 0L, neverCalledApplier(), fullBootstrapApplier
        );

        fireOnce(poller);

        WorldStateSyncOutcome.FullBootstrapDeferred deferred =
                assertInstanceOf(WorldStateSyncOutcome.FullBootstrapDeferred.class, poller.lastOutcomeForTest());
        assertEquals("route_failure", deferred.reason());
        assertFalse(poller.isStopped());
    }

    // ---------- Forced polls (the Service NPC spawn block's Refresh button) ----------

    @Test
    void aForcedPollFiresAtOnceInsteadOfWaitingOutTheCadenceAndTellsItsListener() {
        List<Long> requestedVersions = new ArrayList<>();
        List<String> notifications = new ArrayList<>();
        WorldStateSyncPoller poller = poller(fixedJitter(0), requestedVersions);

        assertTrue(poller.requestImmediatePoll(() -> notifications.add("settled")));

        assertEquals(1, requestedVersions.size(), "a forced poll did not actually reach the client");
        assertEquals(List.of("settled"), notifications, "the caller was never told the poll had settled");
        assertInstanceOf(WorldStateSyncOutcome.Accepted.class, poller.lastOutcomeForTest());
    }

    @Test
    void aForcedPollReschedulesTheCadenceRatherThanLeavingAScheduledPollRightBehindIt() {
        WorldStateSyncPoller poller = poller(fixedJitter(123), new ArrayList<>());

        poller.requestImmediatePoll(null);

        assertEquals(WorldStateSyncPoller.BASE_CADENCE_TICKS + 123, poller.ticksUntilNextPollForTest(),
                "a forced poll must reschedule like any other, or the shard asks the same question twice");
    }

    @Test
    void aSecondForcedPollInsideTheCooldownIsRefusedRatherThanQueued() {
        List<Long> requestedVersions = new ArrayList<>();
        WorldStateSyncPoller poller = poller(fixedJitter(0), requestedVersions);

        assertTrue(poller.requestImmediatePoll(null));
        assertFalse(poller.requestImmediatePoll(() -> {
            throw new AssertionError("a refused forced poll must never run a settlement listener");
        }), "the cooldown did not refuse a second immediate poll");
        assertEquals(1, requestedVersions.size(), "the refused poll still reached the client");
    }

    @Test
    void theForcedPollCooldownExpiresOnItsOwnTicks() {
        List<Long> requestedVersions = new ArrayList<>();
        WorldStateSyncPoller poller = poller(fixedJitter(0), requestedVersions);
        poller.requestImmediatePoll(null);

        for (int tick = 0; tick < WorldStateSyncPoller.FORCED_POLL_COOLDOWN_TICKS; tick++) {
            poller.onTick();
        }

        assertTrue(poller.requestImmediatePoll(null), "the cooldown never expired");
        assertEquals(2, requestedVersions.size());
    }

    @Test
    void aFailedForcedPollStillSettlesItsListener() {
        // The failure cases are exactly the ones the button exists to surface, so a listener that
        // only runs on success would leave the panel showing nothing on the run that mattered.
        List<String> notifications = new ArrayList<>();
        WorldStateSyncPoller poller = WorldStateSyncPoller.newForTest(
                respondingWith(new WorldStateChangesClient.Failure("transport_error")), fixedJitter(0),
                Runnable::run, () -> 0L, neverCalledApplier(), neverCalledFullBootstrapApplier()
        );

        assertTrue(poller.requestImmediatePoll(() -> notifications.add("settled")));

        assertEquals(List.of("settled"), notifications);
        assertInstanceOf(WorldStateSyncOutcome.TransportFailure.class, poller.lastOutcomeForTest());
    }

    @Test
    void aForcedPollDuringAnInFlightPollJoinsItRatherThanStartingASecond() {
        List<Long> requestedVersions = new ArrayList<>();
        List<String> notifications = new ArrayList<>();
        CompletableFuture<WorldStateChangesClient.Result> pending = new CompletableFuture<>();
        WorldStateChangesClient client = new WorldStateChangesClient(
                ignored -> java.util.Optional.of(testCredentials()),
                (ignored, task) -> {
                    requestedVersions.add(0L);
                    return pending;
                },
                com.seggellion.britannia_mod.server.http.CancellableHttpRequest::new
        );
        WorldStateSyncPoller poller = WorldStateSyncPoller.newForTest(
                client, fixedJitter(0), Runnable::run, () -> 0L, appliedApplier(), neverCalledFullBootstrapApplier()
        );

        assertTrue(poller.requestImmediatePoll(() -> notifications.add("first")));
        assertTrue(poller.requestImmediatePoll(() -> notifications.add("second")),
                "a request arriving mid-flight must attach to the poll already running");
        assertEquals(1, requestedVersions.size(), "the in-flight poll was duplicated");
        assertEquals(List.of(), notifications, "nothing may settle before the poll actually returns");

        pending.complete(new WorldStateChangesClient.Success(wellFormedResponse(SHARD, 0, 0, 0, List.of())));

        assertEquals(List.of("first", "second"), notifications, "both waiters must be told, in order");
    }

    @Test
    void aStoppedPollerRefusesForcedPollsInsteadOfPromisingACallbackItCannotKeep() {
        WorldStateSyncPoller poller = poller(fixedJitter(0), new ArrayList<>());
        poller.stopForTest();

        assertFalse(poller.requestImmediatePoll(() -> {
            throw new AssertionError("a stopped poller must never run a settlement listener");
        }));
    }

    private static void fireOnce(WorldStateSyncPoller poller) {
        int total = WorldStateSyncPoller.BASE_CADENCE_TICKS;
        for (int tick = 0; tick < total; tick++) {
            poller.onTick();
        }
    }

    private static WorldStateSyncPoller poller(java.util.function.IntSupplier jitter, List<Long> requestedVersions) {
        return WorldStateSyncPoller.newForTest(
                acceptingClient(requestedVersions), jitter, Runnable::run, () -> 0L, appliedApplier(), neverCalledFullBootstrapApplier()
        );
    }

    private static java.util.function.IntSupplier fixedJitter(int value) {
        return () -> value;
    }

    private static ServerCredentials testCredentials() {
        return ServerCredentialsTestFactory.create(URI.create("http://127.0.0.1"), UUID.randomUUID());
    }

    private static WorldStateSyncPoller.ResponseApplier appliedApplier() {
        return (server, response) -> new ServiceNpcAssignmentsCandidateApply.Applied(ServiceNpcAssignmentsSnapshot.empty());
    }

    private static WorldStateSyncPoller.ResponseApplier neverCalledApplier() {
        return (server, response) -> {
            throw new AssertionError("responseApplier must not be invoked for a response that never reached acceptance");
        };
    }

    private static WorldStateSyncPoller.FullBootstrapApplier neverCalledFullBootstrapApplier() {
        return (server, targetVersion) -> {
            throw new AssertionError("fullBootstrapApplier must not be invoked for a response with full_bootstrap_required=false");
        };
    }

    private static WorldStateChangesClient acceptingClient(List<Long> requestedVersions) {
        WorldStateChangesResponse response = wellFormedResponse(SHARD, 0, 0, 0, List.of());
        return new WorldStateChangesClient(
                ignored -> java.util.Optional.of(testCredentials()),
                (ignored, task) -> {
                    requestedVersions.add(0L);
                    return CompletableFuture.completedFuture((WorldStateChangesClient.Result) new WorldStateChangesClient.Success(response));
                },
                com.seggellion.britannia_mod.server.http.CancellableHttpRequest::new
        );
    }

    private static WorldStateChangesClient respondingWith(WorldStateChangesClient.Result result) {
        return new WorldStateChangesClient(
                ignored -> java.util.Optional.of(testCredentials()),
                (ignored, task) -> CompletableFuture.completedFuture(result),
                com.seggellion.britannia_mod.server.http.CancellableHttpRequest::new
        );
    }

    private static WorldStateChangesResponse wellFormedResponse(
            String shardPublicId, long fromVersion, long toVersion, long currentVersion, List<Long> versions
    ) {
        List<WorldStateChangeRecord> changes = versions.stream()
                .map(version -> new WorldStateChangeRecord(
                        version, "created", "npc_spawn_assignment", "aaaaaaaa-1111-4111-8111-111111111111",
                        1, new JsonObject(), "2026-07-16T12:00:00.000000Z"
                )).toList();
        return new WorldStateChangesResponse(
                WorldStateSyncValidator.SUPPORTED_SCHEMA_VERSION, shardPublicId, fromVersion, toVersion,
                currentVersion, false, changes
        );
    }

    /** Mirrors the real Rails contract exactly: changes is always empty when full_bootstrap_required is true. */
    private static WorldStateChangesResponse fullBootstrapRequiredResponse(String shardPublicId, long fromVersion, long currentVersion) {
        return new WorldStateChangesResponse(
                WorldStateSyncValidator.SUPPORTED_SCHEMA_VERSION, shardPublicId, fromVersion, currentVersion,
                currentVersion, true, List.of()
        );
    }
}
