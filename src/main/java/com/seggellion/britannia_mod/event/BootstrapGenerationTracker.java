package com.seggellion.britannia_mod.event;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class BootstrapGenerationTracker implements AutoCloseable {
    private final Map<UUID, Long> generations = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    long next(UUID playerId) {
        if (closed.get()) return -1L;
        return generations.merge(playerId, 1L, Long::sum);
    }

    boolean isCurrent(UUID playerId, long generation) {
        return !closed.get() && generation > 0L
            && generations.getOrDefault(playerId, -1L) == generation;
    }

    void invalidate(UUID playerId) {
        if (!closed.get()) generations.merge(playerId, 1L, Long::sum);
    }

    @Override
    public void close() {
        closed.set(true);
        generations.clear();
    }
}
