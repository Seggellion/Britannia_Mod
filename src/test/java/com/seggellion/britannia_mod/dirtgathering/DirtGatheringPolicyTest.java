package com.seggellion.britannia_mod.dirtgathering;

import com.seggellion.britannia_mod.resource.extraction.ManagedExtractionPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirtGatheringPolicyTest {
    @Test
    void survivalAndAdventureShareTheSameRealPlayerPermissionRule() {
        assertEquals(DirtGatheringPolicy.Decision.ALLOWED,
                decide(ManagedExtractionPolicy.Actor.PLAYER, false, false, true, true, true));
    }

    @Test
    void automationAndAdministrativeModesCannotCreateDirtEconomy() {
        assertEquals(DirtGatheringPolicy.Decision.DENIED_NON_PLAYER,
                decide(ManagedExtractionPolicy.Actor.NON_PLAYER, false, false, true, true, true));
        assertEquals(DirtGatheringPolicy.Decision.DENIED_FAKE_PLAYER,
                decide(ManagedExtractionPolicy.Actor.FAKE_PLAYER, false, false, true, true, true));
        assertEquals(DirtGatheringPolicy.Decision.DENIED_CREATIVE,
                decide(ManagedExtractionPolicy.Actor.PLAYER, true, false, true, true, true));
        assertEquals(DirtGatheringPolicy.Decision.DENIED_SPECTATOR,
                decide(ManagedExtractionPolicy.Actor.PLAYER, false, true, true, true, true));
    }

    @Test
    void reachWorldAndHouseChecksAreAllRequired() {
        assertEquals(DirtGatheringPolicy.Decision.DENIED_REACH,
                decide(ManagedExtractionPolicy.Actor.PLAYER, false, false, false, true, true));
        assertEquals(DirtGatheringPolicy.Decision.DENIED_WORLD,
                decide(ManagedExtractionPolicy.Actor.PLAYER, false, false, true, false, true));
        assertEquals(DirtGatheringPolicy.Decision.DENIED_HOUSE,
                decide(ManagedExtractionPolicy.Actor.PLAYER, false, false, true, true, false));
    }

    @Test
    void actorAndModeDenialsOutrankLocationDetails() {
        assertEquals(DirtGatheringPolicy.Decision.DENIED_FAKE_PLAYER,
                decide(ManagedExtractionPolicy.Actor.FAKE_PLAYER, true, true, false, false, false));
        assertEquals(DirtGatheringPolicy.Decision.DENIED_CREATIVE,
                decide(ManagedExtractionPolicy.Actor.PLAYER, true, true, false, false, false));
        assertEquals(DirtGatheringPolicy.Decision.DENIED_SPECTATOR,
                decide(ManagedExtractionPolicy.Actor.PLAYER, false, true, false, false, false));
    }

    private static DirtGatheringPolicy.Decision decide(
            ManagedExtractionPolicy.Actor actor,
            boolean creative,
            boolean spectator,
            boolean reach,
            boolean world,
            boolean house
    ) {
        return DirtGatheringPolicy.decide(actor, creative, spectator, reach, world, house);
    }
}
