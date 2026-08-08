package com.seggellion.britannia_mod.server.http;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the exact executor {@code ServerHttpExecutor.submit(MinecraftServer, ...)} hands work
 * to in production: {@code newExecutor()} is the same factory {@code submit} installs into
 * {@code EXECUTORS} via {@code computeIfAbsent}, so asserting its thread-naming and daemon
 * behavior here is equivalent to asserting it for the real per-server executor, without needing
 * a live {@link net.minecraft.server.MinecraftServer} instance to key that map with.
 */
class ServerHttpExecutorThreadNamingTest {
    @Test
    void newExecutorRunsWorkOnADaemonThreadNamedForThisPoolNotTheCallingThread() throws Exception {
        String callingThreadName = Thread.currentThread().getName();
        ThreadPoolExecutor executor = ServerHttpExecutor.newExecutor();
        try {
            AtomicReference<String> observedName = new AtomicReference<>();
            AtomicReference<Boolean> observedDaemon = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            executor.submit(() -> {
                observedName.set(Thread.currentThread().getName());
                observedDaemon.set(Thread.currentThread().isDaemon());
                done.countDown();
            });
            assertTrue(done.await(5, TimeUnit.SECONDS), "submitted work never ran");
            assertTrue(observedName.get().startsWith("britannia-server-http-"),
                "expected the production thread-naming prefix, got " + observedName.get());
            assertNotEquals(callingThreadName, observedName.get(),
                "work ran on the calling thread instead of a pool thread");
            assertEquals(Boolean.TRUE, observedDaemon.get(), "executor threads must be daemon threads");
        } finally {
            executor.shutdownNow();
        }
    }
}
