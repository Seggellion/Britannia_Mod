package com.seggellion.britannia_mod.resource.preview;

import com.seggellion.britannia_mod.server.auth.ServerCredentials;
import com.seggellion.britannia_mod.server.auth.ServerCredentialsTestFactory;
import com.seggellion.britannia_mod.server.http.CancellableHttpRequest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceDepositPreviewPollerTest {
    private static final UUID SERVER = UUID.fromString("44444444-4444-4444-8444-444444444444");

    @Test
    void usesFiveMinuteJitteredCadenceAndEvaluatesAtMostOneRequestPerTick() {
        ResourceDepositPreviewProtocol.Request request = request();
        ServerCredentials credentials = ServerCredentialsTestFactory.create(
                URI.create("https://rails.example.test"), SERVER);
        List<URI> requestedUris = new ArrayList<>();
        AtomicInteger evaluations = new AtomicInteger();
        AtomicInteger submissions = new AtomicInteger();

        ResourceDepositPreviewClient client = new ResourceDepositPreviewClient(
                ignored -> Optional.of(credentials),
                (ignored, task) -> CompletableFuture.completedFuture(
                        new ResourceDepositPreviewClient.Pending(List.of(request), SERVER)),
                (ignored, task) -> {
                    submissions.incrementAndGet();
                    return CompletableFuture.completedFuture(new ResourceDepositPreviewClient.Accepted(false));
                },
                (uri, maximum) -> {
                    requestedUris.add(uri);
                    return new CancellableHttpRequest(uri, maximum);
                });
        ResourceDepositPreviewPoller poller = ResourceDepositPreviewPoller.newForTest(
                client, () -> 0, Runnable::run, (server, pending, key) -> {
                    evaluations.incrementAndGet();
                    assertEquals(SERVER, key);
                    return new ResourceDepositPreviewProtocol.Failure(
                            "chunks_not_loaded", "unloaded", true);
                });

        for (int tick = 0; tick < ResourceDepositPreviewPoller.BASE_CADENCE_TICKS; tick++) {
            poller.onTick();
        }
        assertTrue(poller.pollInFlightForTest());
        assertEquals(1, poller.queuedForTest());
        assertEquals(0, evaluations.get(), "world evaluation must not run in the HTTP completion");

        poller.onTick();
        assertEquals(1, evaluations.get());
        assertEquals(1, submissions.get());
        assertFalse(poller.pollInFlightForTest());
        assertEquals("accepted", poller.lastOutcomeForTest());
        assertEquals("/api/v2/resource_deposit_previews/pending", requestedUris.get(0).getPath());
        assertEquals("/api/v2/resource_deposit_previews/" + request.previewUuid() + "/result",
                requestedUris.get(1).getPath());
    }

    @Test
    void refusesOutOfBoundsJitterAndStopsWithoutPolling() {
        ResourceDepositPreviewClient client = new ResourceDepositPreviewClient(
                ignored -> Optional.empty(),
                (ignored, task) -> CompletableFuture.completedFuture(
                        new ResourceDepositPreviewClient.Failure("not_called")),
                (ignored, task) -> CompletableFuture.completedFuture(
                        new ResourceDepositPreviewClient.Failure("not_called")),
                CancellableHttpRequest::new);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> ResourceDepositPreviewPoller.newForTest(
                        client, () -> ResourceDepositPreviewPoller.MAX_JITTER_TICKS,
                        Runnable::run, (server, request, key) -> null));

        ResourceDepositPreviewPoller stopped = ResourceDepositPreviewPoller.newForTest(
                client, () -> 0, Runnable::run, (server, request, key) -> null);
        stopped.stopForTest();
        for (int i = 0; i < ResourceDepositPreviewPoller.BASE_CADENCE_TICKS + 1; i++) stopped.onTick();
        assertTrue(stopped.isStopped());
        assertEquals("never_polled", stopped.lastOutcomeForTest());
    }

    private static ResourceDepositPreviewProtocol.Request request() {
        return new ResourceDepositPreviewProtocol.Request(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                1, "iron",
                new ResourceDepositPreviewProtocol.Target(
                        UUID.fromString("33333333-3333-4333-8333-333333333333"),
                        SERVER, "Britannia", "minecraft:overworld"),
                10, 20);
    }
}
