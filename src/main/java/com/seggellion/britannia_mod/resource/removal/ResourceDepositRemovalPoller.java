package com.seggellion.britannia_mod.resource.removal;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Server-scoped M6 poller; evaluates or mutates at most one removal work item per tick. */
public final class ResourceDepositRemovalPoller {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int BASE_CADENCE_TICKS = 6_000;
    public static final int MAX_JITTER_TICKS = 1_200;
    private static final Map<MinecraftServer, ResourceDepositRemovalPoller> ACTIVE = new HashMap<>();

    private final MinecraftServer server;
    private final ResourceDepositRemovalClient client = new ResourceDepositRemovalClient();
    private final ArrayDeque<ResourceDepositRemovalProtocol.PreviewRequest> previews = new ArrayDeque<>();
    private final ArrayDeque<ResourceDepositRemovalProtocol.RemovalOperation> removals = new ArrayDeque<>();
    private UUID configuredServerKey;
    private boolean stopped;
    private int pollsInFlight;
    private int submissionsInFlight;
    private int ticksUntilNextPoll;

    private ResourceDepositRemovalPoller(MinecraftServer server) {
        this.server = server;
        this.ticksUntilNextPoll = BASE_CADENCE_TICKS + rollJitter();
    }

    public static synchronized void start(MinecraftServer server) {
        ACTIVE.computeIfAbsent(server, ResourceDepositRemovalPoller::new);
    }

    public static synchronized void tick(MinecraftServer server) {
        ResourceDepositRemovalPoller poller = ACTIVE.get(server);
        if (poller != null) poller.onTick();
    }

    public static synchronized void stop(MinecraftServer server) {
        ResourceDepositRemovalPoller poller = ACTIVE.remove(server);
        if (poller != null) poller.close();
    }

    private void onTick() {
        if (stopped) return;
        if (!removals.isEmpty()) { processRemoval(); return; }
        if (!previews.isEmpty()) { processPreview(); return; }
        if (pollsInFlight > 0) return;
        if (--ticksUntilNextPoll > 0) return;
        ticksUntilNextPoll = BASE_CADENCE_TICKS + rollJitter();
        firePolls();
    }

    private void firePolls() {
        pollsInFlight = 2;
        client.fetchPendingPreviews(server).whenComplete((result, failure) ->
                server.execute(() -> completeFetch(result, failure)));
        client.fetchPendingRemovals(server).whenComplete((result, failure) ->
                server.execute(() -> completeFetch(result, failure)));
    }

    private void completeFetch(ResourceDepositRemovalClient.FetchResult result, Throwable failure) {
        pollsInFlight--;
        if (stopped || failure != null || result == null) {
            if (failure != null) LOGGER.warn("Resource deposit removal poll failed", failure);
            return;
        }
        if (result instanceof ResourceDepositRemovalClient.Failure failed) {
            LOGGER.warn("Resource deposit removal poll failed code={}", failed.safeCode());
        } else if (result instanceof ResourceDepositRemovalClient.PendingPreviews pending) {
            configuredServerKey = reconcileServerKey(configuredServerKey, pending.configuredServerKey());
            previews.addAll(pending.requests());
        } else {
            ResourceDepositRemovalClient.PendingRemovals pending =
                    (ResourceDepositRemovalClient.PendingRemovals) result;
            configuredServerKey = reconcileServerKey(configuredServerKey, pending.configuredServerKey());
            removals.addAll(pending.operations());
        }
        finishBatchIfComplete();
    }

    private void processPreview() {
        ResourceDepositRemovalProtocol.PreviewRequest request = previews.removeFirst();
        ResourceDepositRemovalProtocol.PreviewOutcome outcome;
        try {
            outcome = ResourceDepositRemovalInspector.evaluate(server, request, configuredServerKey);
        } catch (RuntimeException unexpected) {
            outcome = new ResourceDepositRemovalProtocol.Failure(
                    "inspection_error", "destructive preview failed safely", true);
            LOGGER.error("Removal preview failed preview_uuid={}", request.previewUuid(), unexpected);
        }
        submissionsInFlight++;
        client.submitPreviewResult(server, request, outcome).whenComplete((result, failure) ->
                server.execute(() -> completeSubmission("preview", request.previewUuid(), result, failure)));
    }

    private void processRemoval() {
        ResourceDepositRemovalProtocol.RemovalOperation operation = removals.removeFirst();
        ResourceDepositRemovalProtocol.RemovalOutcome outcome;
        try {
            outcome = ResourceDepositRemover.process(server, operation, configuredServerKey);
        } catch (RuntimeException uncertain) {
            LOGGER.error("Removal deferred at uncertain write boundary operation_uuid={}",
                    operation.operationUuid(), uncertain);
            removals.addLast(operation);
            return;
        }
        if (outcome == ResourceDepositRemovalProtocol.Deferred.INSTANCE) {
            removals.addLast(operation);
            return;
        }
        submissionsInFlight++;
        client.submitRemovalResult(server, operation, outcome).whenComplete((result, failure) ->
                server.execute(() -> completeSubmission("operation", operation.operationUuid(), result, failure)));
    }

    private void completeSubmission(String kind, UUID id,
            ResourceDepositRemovalClient.SubmitResult result, Throwable failure) {
        submissionsInFlight--;
        if (failure != null || result == null) {
            LOGGER.warn("Resource deposit removal callback failed kind={} id={}", kind, id, failure);
        } else if (result instanceof ResourceDepositRemovalClient.Failure failed) {
            LOGGER.warn("Resource deposit removal callback failed kind={} id={} code={}",
                    kind, id, failed.safeCode());
        } else {
            LOGGER.info("Resource deposit removal callback accepted kind={} id={} duplicate={}",
                    kind, id, ((ResourceDepositRemovalClient.Accepted) result).duplicate());
        }
        finishBatchIfComplete();
    }

    private void finishBatchIfComplete() {
        if (pollsInFlight == 0 && submissionsInFlight == 0 && previews.isEmpty() && removals.isEmpty()) {
            configuredServerKey = null;
        }
    }

    private static UUID reconcileServerKey(UUID existing, UUID incoming) {
        if (existing != null && !existing.equals(incoming)) {
            throw new IllegalStateException("configured server identity changed inside one poll batch");
        }
        return incoming;
    }

    private void close() {
        stopped = true;
        previews.clear();
        removals.clear();
        configuredServerKey = null;
    }

    private static int rollJitter() {
        return ThreadLocalRandom.current().nextInt(MAX_JITTER_TICKS);
    }
}
