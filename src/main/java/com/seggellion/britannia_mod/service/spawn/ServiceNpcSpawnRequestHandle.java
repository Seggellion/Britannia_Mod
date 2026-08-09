package com.seggellion.britannia_mod.service.spawn;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ServiceNpcSpawnRequestHandle {
    private final CompletableFuture<ServiceNpcSpawnClientResult> future;
    private final Runnable cancellation;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    ServiceNpcSpawnRequestHandle(CompletableFuture<ServiceNpcSpawnClientResult> future, Runnable cancellation) {
        this.future = Objects.requireNonNull(future, "future");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
    }

    public CompletableFuture<ServiceNpcSpawnClientResult> future() { return future; }

    public boolean cancel() {
        if (!cancelled.compareAndSet(false, true) || future.isDone()) return false;
        cancellation.run();
        return true;
    }

    public boolean isCancelled() { return cancelled.get(); }
}
