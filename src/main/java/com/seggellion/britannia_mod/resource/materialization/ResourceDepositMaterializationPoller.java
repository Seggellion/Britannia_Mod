package com.seggellion.britannia_mod.resource.materialization;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Server-scoped asynchronous M5 operation poller; one bounded materialization pass per tick. */
public final class ResourceDepositMaterializationPoller {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int BASE_CADENCE_TICKS = 6_000;
    public static final int MAX_JITTER_TICKS = 1_200;
    private static final Map<MinecraftServer, ResourceDepositMaterializationPoller> ACTIVE =
            new HashMap<>();

    private final MinecraftServer server;
    private final ResourceDepositMaterializationClient client;
    private final ArrayDeque<ResourceDepositMaterializationProtocol.Operation> queue =
            new ArrayDeque<>();
    private UUID configuredServerKey;
    private boolean stopped;
    private boolean pollInFlight;
    private int submissionsInFlight;
    private int ticksUntilNextPoll;

    private ResourceDepositMaterializationPoller(MinecraftServer server) {
        this.server = server;
        this.client = new ResourceDepositMaterializationClient();
        this.ticksUntilNextPoll = BASE_CADENCE_TICKS + rollJitter();
    }

    public static synchronized void start(MinecraftServer server) {
        ACTIVE.computeIfAbsent(server, key -> {
            ResourceDepositMaterializationPoller poller =
                    new ResourceDepositMaterializationPoller(key);
            LOGGER.info("Resource deposit materialization poller started next_poll_in_ticks={}",
                    poller.ticksUntilNextPoll);
            return poller;
        });
    }

    public static synchronized void tick(MinecraftServer server) {
        ResourceDepositMaterializationPoller poller = ACTIVE.get(server);
        if (poller != null) poller.onTick();
    }

    public static synchronized void stop(MinecraftServer server) {
        ResourceDepositMaterializationPoller poller = ACTIVE.remove(server);
        if (poller != null) poller.close();
    }

    private void onTick() {
        if (stopped) return;
        if (!queue.isEmpty()) {
            processOne();
            return;
        }
        if (pollInFlight) return;
        if (--ticksUntilNextPoll > 0) return;
        ticksUntilNextPoll = BASE_CADENCE_TICKS + rollJitter();
        firePoll();
    }

    private void firePoll() {
        if (stopped || pollInFlight) return;
        pollInFlight = true;
        client.fetchPending(server).whenComplete((result, failure) ->
                server.execute(() -> completeFetch(result, failure)));
    }

    private void completeFetch(ResourceDepositMaterializationClient.FetchResult result,
                               Throwable failure) {
        if (stopped) { pollInFlight = false; return; }
        if (failure != null || result == null) {
            LOGGER.warn("Resource deposit materialization poll failed code=transport_error", failure);
            pollInFlight = false;
            return;
        }
        if (result instanceof ResourceDepositMaterializationClient.Failure failed) {
            LOGGER.warn("Resource deposit materialization poll failed code={}", failed.safeCode());
            pollInFlight = false;
            return;
        }
        ResourceDepositMaterializationClient.Pending pending =
                (ResourceDepositMaterializationClient.Pending) result;
        configuredServerKey = pending.configuredServerKey();
        queue.addAll(pending.operations());
        LOGGER.info("Resource deposit materialization poll accepted operations={}", queue.size());
        finishBatchIfComplete();
    }

    private void processOne() {
        ResourceDepositMaterializationProtocol.Operation operation = queue.removeFirst();
        ResourceDepositMaterializationProtocol.Outcome outcome;
        try {
            outcome = ResourceDepositMaterializer.process(server, operation, configuredServerKey);
        } catch (RuntimeException unexpected) {
            // Unknown failures after registration may have a recoverable partial pass. Keep the
            // same operation pending; never turn an uncertain write boundary into false failure.
            LOGGER.error("Materialization processing deferred operation_uuid={}",
                    operation.operationUuid(), unexpected);
            queue.addLast(operation);
            return;
        }
        if (outcome == ResourceDepositMaterializationProtocol.Deferred.INSTANCE) {
            queue.addLast(operation);
            return;
        }

        submissionsInFlight++;
        client.submitResult(server, operation, outcome).whenComplete((result, failure) ->
                server.execute(() -> completeSubmission(operation.operationUuid(), result, failure)));
    }

    private void completeSubmission(UUID operationUuid,
            ResourceDepositMaterializationClient.SubmitResult result, Throwable failure) {
        submissionsInFlight--;
        if (failure != null || result == null) {
            LOGGER.warn("Materialization callback failed operation_uuid={} code=transport_error",
                    operationUuid, failure);
        } else if (result instanceof ResourceDepositMaterializationClient.Failure failed) {
            LOGGER.warn("Materialization callback failed operation_uuid={} code={}",
                    operationUuid, failed.safeCode());
        } else {
            ResourceDepositMaterializationClient.Accepted accepted =
                    (ResourceDepositMaterializationClient.Accepted) result;
            LOGGER.info("Materialization callback accepted operation_uuid={} duplicate={}",
                    operationUuid, accepted.duplicate());
        }
        // A lost/rejected callback leaves Rails pending. It is deliberately rediscovered on the
        // next poll, where the persisted DepositInstance yields an idempotent replay success.
        finishBatchIfComplete();
    }

    private void finishBatchIfComplete() {
        if (queue.isEmpty() && submissionsInFlight == 0) {
            pollInFlight = false;
            configuredServerKey = null;
        }
    }

    private void close() {
        stopped = true;
        queue.clear();
        configuredServerKey = null;
        LOGGER.info("Resource deposit materialization poller stopped");
    }

    private static int rollJitter() {
        return ThreadLocalRandom.current().nextInt(MAX_JITTER_TICKS);
    }
}
