package com.seggellion.britannia_mod.service.spawn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceNpcSpawnMenuValidationTest {
    @Test
    void validSessionPasses() {
        assertEquals(ServiceNpcSpawnValidationError.NONE,
                ServiceNpcSpawnMenuRequestValidator.validate(allTrue()));
    }

    @Test
    void authorizationDistanceDimensionUuidAndRevisionAreEnforced() {
        assertEquals(ServiceNpcSpawnValidationError.UNAUTHORIZED,
                validate(false, true, true, true));
        assertEquals(ServiceNpcSpawnValidationError.WRONG_DIMENSION,
                validate(true, false, true, true));
        assertEquals(ServiceNpcSpawnValidationError.TOO_FAR,
                validate(true, true, false, true));
        assertEquals(ServiceNpcSpawnValidationError.UUID_MISMATCH,
                validate(true, true, true, false));

        ServiceNpcSpawnMenuRequestValidator.Facts stale = allTrue();
        stale = new ServiceNpcSpawnMenuRequestValidator.Facts(
                stale.authenticated(), stale.authorized(), stale.correctMenu(), stale.correctOwner(),
                stale.containerMatches(), stale.dimensionMatches(), stale.withinDistance(), stale.positionMatches(),
                stale.blockPresent(), stale.correctBlock(), stale.correctBlockEntity(), stale.uuidMatches(),
                stale.menuStillValid(), false
        );
        assertEquals(ServiceNpcSpawnValidationError.STALE_REVISION,
                ServiceNpcSpawnMenuRequestValidator.validate(stale));
    }

    private static ServiceNpcSpawnValidationError validate(
            boolean authorized,
            boolean dimension,
            boolean distance,
            boolean uuid
    ) {
        ServiceNpcSpawnMenuRequestValidator.Facts facts = allTrue();
        return ServiceNpcSpawnMenuRequestValidator.validate(new ServiceNpcSpawnMenuRequestValidator.Facts(
                facts.authenticated(), authorized, facts.correctMenu(), facts.correctOwner(), facts.containerMatches(),
                dimension, distance, facts.positionMatches(), facts.blockPresent(), facts.correctBlock(),
                facts.correctBlockEntity(), uuid, facts.menuStillValid(), facts.revisionMatches()
        ));
    }

    private static ServiceNpcSpawnMenuRequestValidator.Facts allTrue() {
        return new ServiceNpcSpawnMenuRequestValidator.Facts(
                true, true, true, true, true, true, true,
                true, true, true, true, true, true, true
        );
    }
}
