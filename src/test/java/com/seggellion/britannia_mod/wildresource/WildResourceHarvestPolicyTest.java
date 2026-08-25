package com.seggellion.britannia_mod.wildresource;

import com.seggellion.britannia_mod.resource.extraction.ManagedExtractionPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WildResourceHarvestPolicyTest {
    @Test
    void onlyRealNonCreativePlayersWithReachAndPermissionMayHarvest() {
        assertEquals(WildResourceHarvestPolicy.Decision.ALLOWED,
                decide(ManagedExtractionPolicy.Actor.PLAYER, false, true, true, true));
        assertEquals(WildResourceHarvestPolicy.Decision.DENIED_NON_PLAYER,
                decide(ManagedExtractionPolicy.Actor.NON_PLAYER, false, true, true, true));
        assertEquals(WildResourceHarvestPolicy.Decision.DENIED_FAKE_PLAYER,
                decide(ManagedExtractionPolicy.Actor.FAKE_PLAYER, false, true, true, true));
        assertEquals(WildResourceHarvestPolicy.Decision.DENIED_CREATIVE,
                decide(ManagedExtractionPolicy.Actor.PLAYER, true, true, true, true));
        assertEquals(WildResourceHarvestPolicy.Decision.DENIED_REACH,
                decide(ManagedExtractionPolicy.Actor.PLAYER, false, false, true, true));
        assertEquals(WildResourceHarvestPolicy.Decision.DENIED_WORLD,
                decide(ManagedExtractionPolicy.Actor.PLAYER, false, true, false, true));
        assertEquals(WildResourceHarvestPolicy.Decision.DENIED_HOUSE,
                decide(ManagedExtractionPolicy.Actor.PLAYER, false, true, true, false));
    }

    @Test
    void actorAndCreativeDenialsOutrankLocationDetails() {
        assertEquals(WildResourceHarvestPolicy.Decision.DENIED_FAKE_PLAYER,
                decide(ManagedExtractionPolicy.Actor.FAKE_PLAYER, true, false, false, false));
        assertEquals(WildResourceHarvestPolicy.Decision.DENIED_CREATIVE,
                decide(ManagedExtractionPolicy.Actor.PLAYER, true, false, false, false));
        assertEquals(WildResourceHarvestPolicy.Decision.DENIED_REACH,
                decide(ManagedExtractionPolicy.Actor.PLAYER, false, false, false, false));
    }

    private static WildResourceHarvestPolicy.Decision decide(
            ManagedExtractionPolicy.Actor actor,
            boolean creative,
            boolean reach,
            boolean world,
            boolean house
    ) {
        return WildResourceHarvestPolicy.decide(actor, creative, reach, world, house);
    }
}
