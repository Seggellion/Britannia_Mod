package com.seggellion.britannia_mod.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WaterSourceAccessPolicyTest {
    @Test
    void allowedWorldPositionPermitsUnlimitedWaterAccess() {
        assertEquals(
                WaterSourceAccessPolicy.Decision.ALLOWED,
                WaterSourceAccessPolicy.fromWorldPermission(true));
    }

    @Test
    void protectedWorldPositionDeniesAccess() {
        assertEquals(
                WaterSourceAccessPolicy.Decision.DENIED_WORLD,
                WaterSourceAccessPolicy.fromWorldPermission(false));
    }
}
