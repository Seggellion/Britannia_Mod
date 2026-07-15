package com.seggellion.britannia_mod.service.spawn;

import java.util.Map;
import java.util.UUID;

public interface ServiceNpcSpawnPendingWorkSource {
    Map<UUID, ServiceNpcSpawnPendingRecord> snapshot();

    boolean acknowledgeIfMatches(
            UUID spawnPointId,
            ServiceNpcSpawnPendingOperation operation,
            long configurationRevision,
            long recordedAtEpochMillis
    );
}
