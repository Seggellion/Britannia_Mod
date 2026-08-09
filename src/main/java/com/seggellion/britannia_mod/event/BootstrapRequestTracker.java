package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.sync.WorldBootstrapAPI;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class BootstrapRequestTracker implements AutoCloseable {
    private final Map<UUID, WorldBootstrapAPI.RequestHandle> requests = new ConcurrentHashMap<>();

    WorldBootstrapAPI.RequestHandle register(UUID playerId) {
        WorldBootstrapAPI.RequestHandle request = new WorldBootstrapAPI.RequestHandle();
        WorldBootstrapAPI.RequestHandle superseded = requests.put(playerId, request);
        if (superseded != null) superseded.cancel();
        return request;
    }

    void complete(UUID playerId, WorldBootstrapAPI.RequestHandle request) {
        requests.remove(playerId, request);
    }

    void cancel(UUID playerId) {
        WorldBootstrapAPI.RequestHandle request = requests.remove(playerId);
        if (request != null) request.cancel();
    }

    @Override
    public void close() {
        requests.values().forEach(WorldBootstrapAPI.RequestHandle::cancel);
        requests.clear();
    }
}
