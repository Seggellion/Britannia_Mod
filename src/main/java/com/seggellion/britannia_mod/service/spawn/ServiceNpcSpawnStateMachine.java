package com.seggellion.britannia_mod.service.spawn;

public final class ServiceNpcSpawnStateMachine {
    private ServiceNpcSpawnStateMachine() {
    }

    public static long nextRevision(long current) {
        if (current < 0L) throw new IllegalArgumentException("revision must be non-negative");
        return Math.incrementExact(current);
    }

    public static ServiceNpcSpawnRegistrationState afterAcceptedChange(
            ServiceNpcSpawnRegistrationState current
    ) {
        return switch (current) {
            case REGISTERED, PENDING_UPDATE -> ServiceNpcSpawnRegistrationState.PENDING_UPDATE;
            default -> ServiceNpcSpawnRegistrationState.PENDING_REGISTRATION;
        };
    }
}
