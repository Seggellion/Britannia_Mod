package com.seggellion.britannia_mod.server.http;

import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class ServerHttpExecutor {
    static final int QUEUE_CAPACITY = 32;
    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();
    private static final Map<MinecraftServer, ThreadPoolExecutor> EXECUTORS = new ConcurrentHashMap<>();

    private ServerHttpExecutor() {}

    public static <T> CompletableFuture<T> submit(MinecraftServer server, Supplier<T> task) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(task, "task");
        ThreadPoolExecutor executor = EXECUTORS.computeIfAbsent(server, ignored -> newExecutor());
        try {
            return CompletableFuture.supplyAsync(task, executor)
                .orTimeout(BoundedHttp.OVERALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.failedFuture(rejected);
        }
    }

    public static CompletableFuture<Void> run(MinecraftServer server, Runnable task) {
        Objects.requireNonNull(task, "task");
        return submit(server, () -> {
            task.run();
            return null;
        });
    }

    public static void shutdown(MinecraftServer server) {
        ThreadPoolExecutor executor = EXECUTORS.remove(server);
        if (executor != null) executor.shutdownNow();
    }

    static ThreadPoolExecutor newExecutor() {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "britannia-server-http-" + THREAD_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
            1, 2, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY), factory,
            new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
