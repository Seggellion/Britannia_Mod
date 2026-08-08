package com.seggellion.britannia_mod.service.spawn;

import javax.annotation.Nullable;

public final class ServiceNpcSpawnIdentityResolver {
    public enum Decision {
        CANONICAL,
        REKEY_COPY
    }

    private ServiceNpcSpawnIdentityResolver() {
    }

    public static Decision decide(
            ServiceNpcSpawnLocation current,
            @Nullable ServiceNpcSpawnClaim existingClaim,
            @Nullable ServiceNpcSpawnLocation identityOrigin
    ) {
        if (existingClaim != null) {
            return existingClaim.location().equals(current) ? Decision.CANONICAL : Decision.REKEY_COPY;
        }
        if (identityOrigin != null && !identityOrigin.equals(current)) {
            return Decision.REKEY_COPY;
        }
        return Decision.CANONICAL;
    }
}
