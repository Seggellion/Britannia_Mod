package com.seggellion.britannia_mod.resource.preview;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Server-scoped, non-overlapping M4 preview poller.
 *
 * <p>It follows the established world-state poller's five-minute cadence plus up to one minute
 * of jitter. HTTP runs on the bounded server HTTP executor; exactly one world evaluation runs on
 * each server tick, so a batch cannot turn into an unbounded single-tick scan. A request remains
 * pending in Rails until its signed callback succeeds, which makes transport failures retryable
 * without any local durable operation state.
 */
public final class ResourceDepositPreviewPoller {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int BASE_CADENCE_TICKS = 6_000;
    public static final int MAX_JITTER_TICKS = 1_200;
    private static final Map<MinecraftServer, ResourceDepositPreviewPoller> ACTIVE = new HashMap<>();

    @FunctionalInterface
    public interface Evaluator {
        ResourceDepositPreviewProtocol.Outcome evaluate(
                MinecraftServer server, ResourceDepositPreviewProtocol.Request request,
                UUID configuredServerKey);
    }

    private final MinecraftServer server;
    private final ResourceDepositPreviewClient client;
    private final IntSupplier jitterSource;
    private final Consumer<Runnable> tickThreadScheduler;
    private final Evaluator evaluator;
    private final ArrayDeque<ResourceDepositPreviewProtocol.Request> evaluationQueue = new ArrayDeque<>();
    private UUID configuredServerKey;
    private boolean stopped;
    private boolean pollInFlight;
    private int submissionsInFlight;
    private int ticksUntilNextPoll;
    private String lastOutcome = "never_polled";

    private ResourceDepositPreviewPoller(
            MinecraftServer server, ResourceDepositPreviewClient client,
            IntSupplier jitterSource, Consumer<Runnable> tickThreadScheduler,
            Evaluator evaluator) {
        this.server = server;
        this.client = Objects.requireNonNull(client, "client");
        this.jitterSource = Objects.requireNonNull(jitterSource, "jitterSource");
        this.tickThreadScheduler = Objects.requireNonNull(tickThreadScheduler, "tickThreadScheduler");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.ticksUntilNextPoll = BASE_CADENCE_TICKS + boundedJitter();
    }

    public static synchronized ResourceDepositPreviewPoller start(MinecraftServer server) {
        return ACTIVE.computeIfAbsent(server, key -> {
            ResourceDepositPreviewPoller poller = new ResourceDepositPreviewPoller(
                    key, new ResourceDepositPreviewClient(),
                    ResourceDepositPreviewPoller::rollJitter, key::execute,
                    ResourceDepositPreviewEvaluator::evaluate);
            LOGGER.info("Resource deposit preview poller started next_poll_in_ticks={}",
                    poller.ticksUntilNextPoll);
            return poller;
        });
    }

    public static synchronized void tick(MinecraftServer server) {
        ResourceDepositPreviewPoller poller = ACTIVE.get(server);
        if (poller != null) poller.onTick();
    }

    public static synchronized void stop(MinecraftServer server) {
        ResourceDepositPreviewPoller poller = ACTIVE.remove(server);
        if (poller != null) poller.close();
    }

    public static ResourceDepositPreviewPoller newForTest(
            ResourceDepositPreviewClient client, IntSupplier jitterSource,
            Consumer<Runnable> tickThreadScheduler, Evaluator evaluator) {
        return new ResourceDepositPreviewPoller(
                null, client, jitterSource, tickThreadScheduler, evaluator);
    }

    public void onTick() {
        if (stopped) return;
        if (!evaluationQueue.isEmpty()) {
            evaluateOne();
            return;
        }
        if (pollInFlight) return;
        if (--ticksUntilNextPoll > 0) return;
        ticksUntilNextPoll = BASE_CADENCE_TICKS + boundedJitter();
        firePoll();
    }

    private void firePoll() {
        if (stopped || pollInFlight) return;
        pollInFlight = true;
        lastOutcome = "polling";
        client.fetchPending(server).whenComplete((result, failure) ->
                tickThreadScheduler.accept(() -> completeFetch(result, failure)));
    }

    private void completeFetch(ResourceDepositPreviewClient.FetchResult result, Throwable failure) {
        if (stopped) {
            pollInFlight = false;
            return;
        }
        if (failure != null || result == null) {
            lastOutcome = "transport_error";
            pollInFlight = false;
            LOGGER.warn("Resource deposit preview poll failed code=transport_error", failure);
            return;
        }
        if (result instanceof ResourceDepositPreviewClient.Failure failed) {
            lastOutcome = failed.safeCode();
            pollInFlight = false;
            LOGGER.warn("Resource deposit preview poll failed code={}", failed.safeCode());
            return;
        }

        ResourceDepositPreviewClient.Pending pending =
                (ResourceDepositPreviewClient.Pending) result;
        configuredServerKey = pending.configuredServerKey();
        evaluationQueue.addAll(pending.requests());
        lastOutcome = pending.requests().isEmpty() ? "empty" : "evaluating";
        LOGGER.info("Resource deposit preview poll accepted requests={}", pending.requests().size());
        finishBatchIfComplete();
    }

    private void evaluateOne() {
        ResourceDepositPreviewProtocol.Request request = evaluationQueue.removeFirst();
        ResourceDepositPreviewProtocol.Outcome outcome;
        try {
            outcome = evaluator.evaluate(server, request, configuredServerKey);
        } catch (RuntimeException unexpected) {
            outcome = new ResourceDepositPreviewProtocol.Failure(
                    "evaluation_error", "preview evaluation failed safely", true);
            LOGGER.error("Resource deposit preview evaluation failed preview_uuid={}",
                    request.previewUuid(), unexpected);
        }

        submissionsInFlight++;
        CompletableFuture<ResourceDepositPreviewClient.SubmitResult> submission =
                client.submitResult(server, request, outcome);
        submission.whenComplete((result, failure) -> tickThreadScheduler.accept(
                () -> completeSubmission(request.previewUuid(), result, failure)));
    }

    private void completeSubmission(UUID previewUuid,
            ResourceDepositPreviewClient.SubmitResult result, Throwable failure) {
        submissionsInFlight--;
        if (failure != null || result == null) {
            lastOutcome = "result_transport_error";
            LOGGER.warn("Resource deposit preview callback failed preview_uuid={} code=transport_error",
                    previewUuid, failure);
        } else if (result instanceof ResourceDepositPreviewClient.Failure failed) {
            lastOutcome = failed.safeCode();
            LOGGER.warn("Resource deposit preview callback failed preview_uuid={} code={}",
                    previewUuid, failed.safeCode());
        } else {
            ResourceDepositPreviewClient.Accepted accepted =
                    (ResourceDepositPreviewClient.Accepted) result;
            lastOutcome = accepted.duplicate() ? "duplicate_accepted" : "accepted";
            LOGGER.info("Resource deposit preview callback accepted preview_uuid={} duplicate={}",
                    previewUuid, accepted.duplicate());
        }
        finishBatchIfComplete();
    }

    private void finishBatchIfComplete() {
        if (evaluationQueue.isEmpty() && submissionsInFlight == 0) {
            pollInFlight = false;
            configuredServerKey = null;
        }
    }

    private void close() {
        stopped = true;
        evaluationQueue.clear();
        configuredServerKey = null;
        LOGGER.info("Resource deposit preview poller stopped");
    }

    public boolean isStopped() { return stopped; }
    public boolean pollInFlightForTest() { return pollInFlight; }
    public int queuedForTest() { return evaluationQueue.size(); }
    public int ticksUntilNextPollForTest() { return ticksUntilNextPoll; }
    public String lastOutcomeForTest() { return lastOutcome; }
    public void stopForTest() { close(); }

    private int boundedJitter() {
        int candidate = jitterSource.getAsInt();
        if (candidate < 0 || candidate >= MAX_JITTER_TICKS) {
            throw new IllegalStateException("jitter source produced an out-of-bounds value: " + candidate);
        }
        return candidate;
    }

    static int rollJitter() {
        return ThreadLocalRandom.current().nextInt(MAX_JITTER_TICKS);
    }
}
