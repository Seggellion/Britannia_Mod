package com.seggellion.britannia_mod.service.spawn;

import java.time.Duration;

public sealed interface ServiceNpcSpawnClientResult permits
    ServiceNpcSpawnClientResult.Protocol,
    ServiceNpcSpawnClientResult.HttpFailure,
    ServiceNpcSpawnClientResult.TransportFailure,
    ServiceNpcSpawnClientResult.LocalFailure,
    ServiceNpcSpawnClientResult.Cancelled {

    Disposition disposition();
    default boolean retryable() { return disposition() == Disposition.RETRYABLE; }

    enum Disposition {
        SUCCESS, RETRYABLE, PERMANENT, AUTHENTICATION_BLOCKED, CONFIGURATION_BLOCKED,
        PROTOCOL_INCOMPATIBLE, UUID_COLLISION, LOCATION_OCCUPIED, CANCELLED
    }

    record Protocol(ServiceNpcSpawnProtocolResponse response, Disposition disposition)
        implements ServiceNpcSpawnClientResult {
        public Protocol {
            if (response == null || disposition == null) throw new IllegalArgumentException("invalid_protocol_result");
        }
    }

    record HttpFailure(String safeCode, Disposition disposition, Duration retryAfter)
        implements ServiceNpcSpawnClientResult {}

    record TransportFailure(String safeCode, Disposition disposition)
        implements ServiceNpcSpawnClientResult {
        public TransportFailure(String safeCode) { this(safeCode, Disposition.RETRYABLE); }
    }

    record LocalFailure(String safeCode, Disposition disposition)
        implements ServiceNpcSpawnClientResult {
        public LocalFailure(String safeCode) { this(safeCode, Disposition.CONFIGURATION_BLOCKED); }
    }

    record Cancelled(String safeCode) implements ServiceNpcSpawnClientResult {
        public Cancelled() { this("cancelled"); }
        @Override public Disposition disposition() { return Disposition.CANCELLED; }
        @Override public boolean retryable() { return false; }
    }
}
