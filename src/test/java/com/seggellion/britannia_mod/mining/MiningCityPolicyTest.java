package com.seggellion.britannia_mod.mining;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The explicit form of a protection that used to exist by accident.
 *
 * <p>While mining required the Survival switch, city bounds were safe as a side effect: the
 * retired {@code CityGameModeHandler} forced everyone inside a city into adventure, and adventure
 * could not dig. Adventure digs now, so {@code MiningGateHandler} states the rule directly, and
 * this pins its truth table: a Mining-ladder resource inside a city refuses extraction, except
 * for the actors the old arrangement also let through — creative operators, and whoever the game
 * itself lets build here ({@code mayBuild}, the lent right a house owner holds inside their own
 * walls).
 */
class MiningCityPolicyTest {

    @Test
    void insideACityAnOrdinaryMinerIsRefused() {
        assertTrue(MiningGateHandler.cityProtects(true, false, false));
    }

    @Test
    void outsideACityNobodyIsRefused() {
        assertFalse(MiningGateHandler.cityProtects(false, false, false));
        assertFalse(MiningGateHandler.cityProtects(false, true, false));
        assertFalse(MiningGateHandler.cityProtects(false, false, true));
        assertFalse(MiningGateHandler.cityProtects(false, true, true));
    }

    @Test
    void aCreativeOperatorMayStillWorkInsideACity() {
        assertFalse(MiningGateHandler.cityProtects(true, true, false));
    }

    @Test
    void aBuildRightInsideACityIsRespected() {
        // The lent house-owner ability: an owner may work catalogued stone in their own city
        // house, exactly as the mayBuild lifecycle allowed before the switch was retired.
        assertFalse(MiningGateHandler.cityProtects(true, false, true));
    }
}
