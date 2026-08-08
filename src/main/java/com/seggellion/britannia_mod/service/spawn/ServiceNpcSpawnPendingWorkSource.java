package com.seggellion.britannia_mod.service.spawn;

import java.util.Map;
import java.util.UUID;

public interface ServiceNpcSpawnPendingWorkSource {
    Map<UUID, ServiceNpcSpawnPendingRecord> snapshot();

    boolean acknowledgeSuccess(
            ServiceNpcSpawnPendingOperationToken token,
            ServiceNpcSpawnProtocolResponse response
    );
}
