package com.seggellion.britannia_mod.structure.interaction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DecoratorAuthorizationTest {
    @Test
    void serverValidatedCreativeOrPermissionLevelTwoIsAuthorized() {
        assertTrue(DecoratorAuthorization.evaluate(true, true, false));
        assertTrue(DecoratorAuthorization.evaluate(true, false, true));
        assertTrue(DecoratorAuthorization.evaluate(true, true, true));
    }

    @Test
    void unauthorizedAndClientSideClaimsAreRejected() {
        assertFalse(DecoratorAuthorization.evaluate(true, false, false));
        assertFalse(DecoratorAuthorization.evaluate(false, true, true));
        assertFalse(DecoratorAuthorization.evaluate(false, false, false));
    }
}
