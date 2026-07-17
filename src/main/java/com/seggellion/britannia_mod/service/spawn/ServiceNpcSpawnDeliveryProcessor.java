package com.seggellion.britannia_mod.service.spawn;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.auth.ServerCredentials;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.LongSupplier;

/** Server-scoped orchestration for the schema-2 durable spawn-operation outbox. */
public final class ServiceNpcSpawnDeliveryProcessor {
    public static final int TICK_CADENCE = 20;
    public static final int MAX_CANDIDATES_PER_CYCLE = 4;
    public static final int MAX_IN_FLIGHT = 2;
    private static final long CIRCUIT_MILLIS = ServiceNpcSpawnRetryPolicy.SLOW_RETRY_MILLIS;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<MinecraftServer, ServiceNpcSpawnDeliveryProcessor> ACTIVE = new HashMap<>();

    public interface Submission {
        CompletableFuture<ServiceNpcSpawnClientResult> future();
        boolean cancel();
    }

    @FunctionalInterface
    public interface Client {
        Submission submit(MinecraftServer server, ServiceNpcSpawnOperationRequest request);
    }

    private final MinecraftServer server;
    private final Client client;
    private final LongSupplier clock;
    private final String controlledShardName;
    private final Map<UUID, InFlight> inFlightByOperation = new HashMap<>();
    private final ServiceNpcSpawnMissingPostReconciler missingPostReconciler;
    private boolean stopped;
    private int ticksUntilCycle = TICK_CADENCE;
    private long cycleCount;
    private long circuitOpenUntilEpochMillis;

    private ServiceNpcSpawnDeliveryProcessor(
            MinecraftServer server, Client client, LongSupplier clock, String controlledShardName
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.client = Objects.requireNonNull(client, "client");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.controlledShardName = controlledShardName;
        this.missingPostReconciler = new ServiceNpcSpawnMissingPostReconciler(
            ServiceNpcSpawnMissingPostReconciler.realProbe(server), clock
        );
    }

    public static synchronized ServiceNpcSpawnDeliveryProcessor start(MinecraftServer server) {
        return ACTIVE.computeIfAbsent(server, key -> {
            ServiceNpcSpawnRegistrationClient registrationClient = new ServiceNpcSpawnRegistrationClient();
            ServiceNpcSpawnDeliveryProcessor processor = new ServiceNpcSpawnDeliveryProcessor(
                key,
                (activeServer, request) -> wrap(registrationClient.submit(activeServer, request)),
                System::currentTimeMillis,
                null
            );
            LOGGER.info("Service NPC spawn delivery processor started");
            return processor;
        });
    }

    /**
     * Replaces the active processor with a controlled transport. Intended for the bundled
     * GameTests; it does not alter credentials, protocol serialization, or durable data.
     */
    public static synchronized ServiceNpcSpawnDeliveryProcessor replaceForGameTest(
            MinecraftServer server, Client client, LongSupplier clock, String authenticatedShardName
    ) {
        stop(server);
        ServiceNpcSpawnDeliveryProcessor processor = new ServiceNpcSpawnDeliveryProcessor(
            server, client, clock, Objects.requireNonNull(authenticatedShardName, "authenticatedShardName")
        );
        ACTIVE.put(server, processor);
        return processor;
    }

    public static synchronized void tick(MinecraftServer server) {
        ServiceNpcSpawnDeliveryProcessor processor = ACTIVE.get(server);
        if (processor != null) processor.onTick();
    }

    public static synchronized void stop(MinecraftServer server) {
        ServiceNpcSpawnDeliveryProcessor processor = ACTIVE.remove(server);
        if (processor != null) processor.close();
    }

    public static synchronized int activeProcessorCount() {
        return ACTIVE.size();
    }

    public void processNowForGameTest() {
        processCycle();
    }

    public int inFlightCount() {
        return inFlightByOperation.size();
    }

    private void onTick() {
        if (stopped || --ticksUntilCycle > 0) return;
        ticksUntilCycle = TICK_CADENCE;
        processCycle();
    }

    private void processCycle() {
        if (stopped) return;
        long now = nonNegativeNow();
        ServiceNpcSpawnCollisionRepairCoordinator.processCycle(server);
        ServiceNpcSpawnReceiptReconciler.reconcileReceipts(server);
        ServiceNpcSpawnPendingData data = ServiceNpcSpawnPendingData.get(server.overworld());
        missingPostReconciler.processCycle(data);
        if (now < circuitOpenUntilEpochMillis) return;

        Set<UUID> activeOperations = Set.copyOf(inFlightByOperation.keySet());
        Set<UUID> activeSpawns = new HashSet<>();
        inFlightByOperation.values().forEach(entry -> activeSpawns.add(entry.token.spawnPointId()));
        Collection<ServiceNpcSpawnPendingRecord> selectable = data.snapshot().values();
        if (controlledShardName != null) {
            selectable = selectable.stream()
                .filter(record -> controlledShardName.equals(record.shardName()))
                .toList();
        }
        List<ServiceNpcSpawnPendingRecord> candidates =
            selectCandidates(selectable, now, activeOperations, activeSpawns);

        if (++cycleCount % 60L == 1L) {
            LOGGER.info("Service NPC spawn outbox depth={} receipts={} in_flight={}",
                data.snapshot().size(), data.snapshotAcknowledgements().size(), inFlightByOperation.size());
        }
        for (ServiceNpcSpawnPendingRecord record : candidates) {
            if (stopped || inFlightByOperation.size() >= MAX_IN_FLIGHT
                    || inFlightByOperation.containsKey(record.operationId())
                    || containsSpawn(record.spawnPointId())) {
                continue;
            }
            if (!prepareAndSubmit(data, record, now)) {
                if (now < circuitOpenUntilEpochMillis) break;
            }
        }
    }

    private boolean prepareAndSubmit(
            ServiceNpcSpawnPendingData data, ServiceNpcSpawnPendingRecord record, long now
    ) {
        AuthGate auth = authenticationGate();
        if (!auth.available) {
            applyAuthenticationBlocked(data, record.token(), auth.failureCode, now);
            return false;
        }
        if (!record.shardName().equals(auth.shardName)) {
            applyAuthenticationBlocked(data, record.token(), ServiceNpcSpawnFailureCodes.SHARD_MISMATCH, now);
            return false;
        }

        final ServiceNpcSpawnOperationRequest request;
        try {
            request = ServiceNpcSpawnPendingRequestAdapter.adapt(record);
        } catch (RuntimeException invalid) {
            if (data.markPermanentFailure(record.token(), ServiceNpcSpawnFailureCodes.INVALID_LOCAL_OPERATION)) {
                reconcilePending(data, record.spawnPointId());
                LOGGER.warn("Service NPC spawn operation permanently rejected locally operation_id={} spawn_uuid={} code={}",
                    record.operationId(), record.spawnPointId(), ServiceNpcSpawnFailureCodes.INVALID_LOCAL_OPERATION);
            }
            return false;
        }

        int nextAttempt;
        try {
            nextAttempt = Math.incrementExact(record.attemptCount());
        } catch (ArithmeticException overflow) {
            data.markPermanentFailure(record.token(), ServiceNpcSpawnFailureCodes.INVALID_LOCAL_OPERATION);
            reconcilePending(data, record.spawnPointId());
            return false;
        }
        long provisional = ServiceNpcSpawnRetryPolicy.saturatingAdd(
            now, ServiceNpcSpawnRetryPolicy.backoffMillis(record.operationId(), nextAttempt)
        );
        ServiceNpcSpawnPendingOperationToken token = record.token();
        if (!data.markAttemptStarted(token, now, provisional)) return false;

        InFlight reservation = new InFlight(token, now, record.operation(), record.location());
        inFlightByOperation.put(token.operationId(), reservation);
        try {
            Submission submission = client.submit(server, request);
            if (submission == null) throw new IllegalStateException("submission_missing");
            reservation.submission = submission;
            submission.future().whenComplete((result, failure) ->
                server.execute(() -> complete(token, normalize(result, failure))));
            LOGGER.info("Submitted Service NPC spawn operation operation_id={} spawn_uuid={} operation={} revision={} attempt={} in_flight={}",
                token.operationId(), token.spawnPointId(), token.operation(), token.configurationRevision(),
                nextAttempt, inFlightByOperation.size());
            return true;
        } catch (RuntimeException submissionFailure) {
            String safeCode = submissionFailure instanceof java.util.concurrent.RejectedExecutionException
                ? "executor_saturated" : "transport_error";
            complete(token, new ServiceNpcSpawnClientResult.TransportFailure(safeCode));
            return false;
        }
    }

    private void complete(
            ServiceNpcSpawnPendingOperationToken token, ServiceNpcSpawnClientResult result
    ) {
        InFlight active = inFlightByOperation.remove(token.operationId());
        if (stopped || active == null || !active.token.equals(token)) return;
        ServiceNpcSpawnPendingData data = ServiceNpcSpawnPendingData.get(server.overworld());
        ServiceNpcSpawnPendingRecord current = data.snapshot().get(token.spawnPointId());
        if (current == null || !current.token().equals(token)) {
            LOGGER.info("Ignored obsolete Service NPC spawn completion operation_id={} spawn_uuid={}",
                token.operationId(), token.spawnPointId());
            return;
        }
        if (result instanceof ServiceNpcSpawnClientResult.Protocol) {
            circuitOpenUntilEpochMillis = 0L;
        }
        ServiceNpcSpawnDeliveryDecision decision = ServiceNpcSpawnDeliveryDecision.classify(result);
        long now = nonNegativeNow();
        String code = ServiceNpcSpawnFailureCodes.from(result);
        switch (decision) {
            case ACKNOWLEDGE_SUCCESS -> acknowledge(data, current, result);
            case RETRY_WITH_BACKOFF -> {
                Duration retryAfter = result instanceof ServiceNpcSpawnClientResult.HttpFailure http
                    ? http.retryAfter() : null;
                long delay = ServiceNpcSpawnRetryPolicy.effectiveDelayMillis(
                    current.operationId(), current.attemptCount(), retryAfter
                );
                long next = ServiceNpcSpawnRetryPolicy.saturatingAdd(now, delay);
                if (data.markRetryWait(token, code, next)) {
                    reconcilePending(data, token.spawnPointId());
                    LOGGER.info("Scheduled Service NPC spawn retry operation_id={} spawn_uuid={} code={} next_attempt_at={}",
                        token.operationId(), token.spawnPointId(), code, next);
                }
            }
            case RETRY_AT_SLOW_INTERVAL -> {
                long next = ServiceNpcSpawnRetryPolicy.saturatingAdd(now, ServiceNpcSpawnRetryPolicy.slowRetryMillis());
                if (data.markRetryWait(token, code, next)) {
                    reconcilePending(data, token.spawnPointId());
                    LOGGER.info("Scheduled slow Service NPC spawn retry operation_id={} spawn_uuid={} code={} next_attempt_at={}",
                        token.operationId(), token.spawnPointId(), code, next);
                }
            }
            case AUTHENTICATION_BLOCKED -> applyAuthenticationBlocked(data, token, code, now);
            case PERMANENT_FAILURE -> {
                if (data.markPermanentFailure(token, code)) {
                    reconcilePending(data, token.spawnPointId());
                    LOGGER.warn("Service NPC spawn operation permanently failed operation_id={} spawn_uuid={} code={}",
                        token.operationId(), token.spawnPointId(), code);
                }
            }
            case COLLISION_REPAIR -> applyCollision(data, token, result, now);
            case NO_CHANGE -> {
                // Attempt-start already contains the crash-safe provisional retry time.
            }
        }
    }

    private void acknowledge(
            ServiceNpcSpawnPendingData data,
            ServiceNpcSpawnPendingRecord current,
            ServiceNpcSpawnClientResult result
    ) {
        if (!(result instanceof ServiceNpcSpawnClientResult.Protocol protocol)) return;
        if (!data.acknowledgeSuccess(current.token(), protocol.response())) return;
        if (current.operation() == ServiceNpcSpawnPendingOperation.UPSERT) {
            ServiceNpcSpawnAcknowledgementReceipt receipt = data.findAcknowledgement(current.spawnPointId());
            if (receipt != null) ServiceNpcSpawnReceiptReconciler.reconcileLocation(server, receipt);
        }
        LOGGER.info("Acknowledged Service NPC spawn operation operation_id={} spawn_uuid={} operation={} revision={}",
            current.operationId(), current.spawnPointId(), current.operation(), current.configurationRevision());
    }

    private void applyAuthenticationBlocked(
            ServiceNpcSpawnPendingData data,
            ServiceNpcSpawnPendingOperationToken token,
            String code,
            long now
    ) {
        String safeCode = ServiceNpcSpawnFailureCodes.SHARD_MISMATCH.equals(code)
            ? code : ServiceNpcSpawnFailureCodes.AUTHENTICATION_BLOCKED;
        long next = ServiceNpcSpawnRetryPolicy.saturatingAdd(now, CIRCUIT_MILLIS);
        if (data.markRetryWait(token, safeCode, next)) reconcilePending(data, token.spawnPointId());
        openCircuit(now, safeCode);
    }

    private void applyCollision(
            ServiceNpcSpawnPendingData data,
            ServiceNpcSpawnPendingOperationToken token,
            ServiceNpcSpawnClientResult result,
            long now
    ) {
        if (!(result instanceof ServiceNpcSpawnClientResult.Protocol protocol)
                || protocol.response().outcome() != ServiceNpcSpawnOutcome.UUID_COLLISION
                || protocol.response().collisionKind() == null
                || !Boolean.TRUE.equals(protocol.response().replacementUuidRequired())) {
            return;
        }
        ServiceNpcSpawnCollisionEvidence evidence = new ServiceNpcSpawnCollisionEvidence(
            ServiceNpcSpawnCollisionEvidence.CollisionKind.valueOf(protocol.response().collisionKind().name()),
            true,
            protocol.response().canonicalLocation(),
            now
        );
        if (data.markCollisionRepair(
                token, evidence, ServiceNpcSpawnFailureCodes.UUID_COLLISION_PENDING_REPAIR)) {
            reconcilePending(data, token.spawnPointId());
            LOGGER.warn("Service NPC spawn UUID collision awaiting repair operation_id={} spawn_uuid={}",
                token.operationId(), token.spawnPointId());
        }
    }

    private void reconcilePending(ServiceNpcSpawnPendingData data, UUID spawnPointId) {
        ServiceNpcSpawnPendingRecord updated = data.snapshot().get(spawnPointId);
        if (updated == null) return;
        ServiceNpcSpawnReceiptReconciler.reconcilePendingLocation(server, updated);
    }

    private void openCircuit(long now, String code) {
        boolean newlyOpened = shouldLogCircuitOpen(now, circuitOpenUntilEpochMillis);
        circuitOpenUntilEpochMillis = extendCircuit(now, circuitOpenUntilEpochMillis);
        if (newlyOpened) {
            LOGGER.warn("Service NPC spawn authentication circuit opened code={} until={}",
                code, circuitOpenUntilEpochMillis);
        }
    }

    private AuthGate authenticationGate() {
        if (controlledShardName != null) return new AuthGate(true, controlledShardName, null);
        ServerCredentials credentials = ServerAuthRegistry.credentials(server).orElse(null);
        if (credentials == null) {
            return new AuthGate(false, null, ServiceNpcSpawnFailureCodes.AUTHENTICATION_BLOCKED);
        }
        if (credentials.minecraftServerKey().isEmpty()) {
            return new AuthGate(false, null, credentials.minecraftServerKeyUnavailableCode());
        }
        return new AuthGate(true, credentials.shardName(), null);
    }

    private boolean containsSpawn(UUID spawnPointId) {
        return inFlightByOperation.values().stream()
            .anyMatch(entry -> entry.token.spawnPointId().equals(spawnPointId));
    }

    private long nonNegativeNow() {
        return Math.max(0L, clock.getAsLong());
    }

    private void close() {
        if (stopped) return;
        stopped = true;
        List<InFlight> active = new ArrayList<>(inFlightByOperation.values());
        active.forEach(entry -> {
            if (entry.submission != null && entry.submission.cancel()) {
                LOGGER.info("Cancelled Service NPC spawn operation during shutdown operation_id={} spawn_uuid={}",
                    entry.token.operationId(), entry.token.spawnPointId());
            }
        });
        inFlightByOperation.clear();
        LOGGER.info("Service NPC spawn delivery processor stopped");
    }

    static List<ServiceNpcSpawnPendingRecord> selectCandidates(
            Collection<ServiceNpcSpawnPendingRecord> records,
            long now,
            Set<UUID> activeOperations,
            Set<UUID> activeSpawns
    ) {
        return records.stream()
            .filter(record -> record.disposition() == ServiceNpcSpawnPendingDisposition.READY
                || record.disposition() == ServiceNpcSpawnPendingDisposition.RETRY_WAIT
                    && record.nextAttemptAtEpochMillis() <= now)
            .filter(record -> !activeOperations.contains(record.operationId()))
            .filter(record -> !activeSpawns.contains(record.spawnPointId()))
            .sorted(Comparator
                .comparingLong(ServiceNpcSpawnPendingRecord::nextAttemptAtEpochMillis)
                .thenComparingLong(ServiceNpcSpawnPendingRecord::recordedAtEpochMillis)
                .thenComparing(ServiceNpcSpawnPendingRecord::spawnPointId)
                .thenComparing(ServiceNpcSpawnPendingRecord::operationId))
            .limit(MAX_CANDIDATES_PER_CYCLE)
            .toList();
    }

    static boolean isCircuitOpen(long now, long openUntil) {
        return now < openUntil;
    }

    static boolean shouldLogCircuitOpen(long now, long openUntil) {
        return !isCircuitOpen(now, openUntil);
    }

    static long extendCircuit(long now, long currentOpenUntil) {
        long requested = ServiceNpcSpawnRetryPolicy.saturatingAdd(now, CIRCUIT_MILLIS);
        return Math.max(currentOpenUntil, requested);
    }

    private static ServiceNpcSpawnClientResult normalize(
            ServiceNpcSpawnClientResult result, Throwable failure
    ) {
        if (failure == null && result != null) return result;
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
            ? failure.getCause() : failure;
        if (cause instanceof java.util.concurrent.CancellationException) {
            return new ServiceNpcSpawnClientResult.Cancelled();
        }
        return new ServiceNpcSpawnClientResult.TransportFailure(
            "transport_error", ServiceNpcSpawnClientResult.Disposition.RETRYABLE
        );
    }

    private static Submission wrap(ServiceNpcSpawnRequestHandle handle) {
        return new Submission() {
            @Override public CompletableFuture<ServiceNpcSpawnClientResult> future() { return handle.future(); }
            @Override public boolean cancel() { return handle.cancel(); }
        };
    }

    private record AuthGate(boolean available, String shardName, String failureCode) {}

    private static final class InFlight {
        private final ServiceNpcSpawnPendingOperationToken token;
        private final long submittedAtEpochMillis;
        private final ServiceNpcSpawnPendingOperation operation;
        private final ServiceNpcSpawnLocation location;
        private Submission submission;

        private InFlight(
                ServiceNpcSpawnPendingOperationToken token,
                long submittedAtEpochMillis,
                ServiceNpcSpawnPendingOperation operation,
                ServiceNpcSpawnLocation location
        ) {
            this.token = token;
            this.submittedAtEpochMillis = submittedAtEpochMillis;
            this.operation = operation;
            this.location = location;
        }
    }
}
