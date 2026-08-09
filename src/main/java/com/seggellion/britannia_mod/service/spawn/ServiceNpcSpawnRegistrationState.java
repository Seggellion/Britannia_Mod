package com.seggellion.britannia_mod.service.spawn;

import java.util.Locale;

public enum ServiceNpcSpawnRegistrationState {
    UNCONFIGURED,
    PENDING_REGISTRATION,
    PENDING_UPDATE,
    REGISTERED,
    ERROR;

    public static ServiceNpcSpawnRegistrationState decode(String value) {
        if (value == null || value.isBlank()) return UNCONFIGURED;
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ERROR;
        }
    }
}
