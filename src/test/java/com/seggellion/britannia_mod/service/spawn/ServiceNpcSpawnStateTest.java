package com.seggellion.britannia_mod.service.spawn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceNpcSpawnStateTest {
    @Test
    void registrationStateDecodingIsForwardSafe() {
        assertEquals(ServiceNpcSpawnRegistrationState.UNCONFIGURED,
                ServiceNpcSpawnRegistrationState.decode(null));
        assertEquals(ServiceNpcSpawnRegistrationState.REGISTERED,
                ServiceNpcSpawnRegistrationState.decode("registered"));
        assertEquals(ServiceNpcSpawnRegistrationState.ERROR,
                ServiceNpcSpawnRegistrationState.decode("future_state"));
    }

    @Test
    void revisionsAreCheckedAndStateTransitionsNeverRegisterLocally() {
        assertEquals(1L, ServiceNpcSpawnStateMachine.nextRevision(0L));
        assertThrows(ArithmeticException.class, () -> ServiceNpcSpawnStateMachine.nextRevision(Long.MAX_VALUE));
        assertEquals(ServiceNpcSpawnRegistrationState.PENDING_REGISTRATION,
                ServiceNpcSpawnStateMachine.afterAcceptedChange(ServiceNpcSpawnRegistrationState.UNCONFIGURED));
        assertEquals(ServiceNpcSpawnRegistrationState.PENDING_REGISTRATION,
                ServiceNpcSpawnStateMachine.afterAcceptedChange(ServiceNpcSpawnRegistrationState.ERROR));
        assertEquals(ServiceNpcSpawnRegistrationState.PENDING_UPDATE,
                ServiceNpcSpawnStateMachine.afterAcceptedChange(ServiceNpcSpawnRegistrationState.REGISTERED));
    }
}
