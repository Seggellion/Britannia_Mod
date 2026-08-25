package com.seggellion.britannia_mod.merchant;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static com.seggellion.britannia_mod.merchant.MerchantFoodSupplyGate.Decision.DESPAWN;
import static com.seggellion.britannia_mod.merchant.MerchantFoodSupplyGate.Decision.MAINTAIN;
import static com.seggellion.britannia_mod.merchant.MerchantFoodSupplyGate.Decision.SKIP;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The pure boundary of the farmer's food-supply gate: {@code 19 -> despawn, 20 -> maintain,
 * 21 -> maintain}, an absent Rails reading acts on nothing (the {@code CityFoodSupplyCache}
 * contract: a missing reading is not a reading of zero), and an ungated definition never
 * consults the reading at all.
 */
class MerchantFoodSupplyGateTest {

    private static final double FLOOR = 20.0D;

    @Test
    void nineteenIsBelowTheFloor() {
        assertEquals(DESPAWN, MerchantFoodSupplyGate.decide(OptionalDouble.of(19.0D), FLOOR));
    }

    @Test
    void twentyMeetsTheFloorExactly() {
        assertEquals(MAINTAIN, MerchantFoodSupplyGate.decide(OptionalDouble.of(20.0D), FLOOR));
    }

    @Test
    void twentyOneClearsTheFloor() {
        assertEquals(MAINTAIN, MerchantFoodSupplyGate.decide(OptionalDouble.of(21.0D), FLOOR));
    }

    @Test
    void aMissingReadingSkipsTheCycleEntirely() {
        assertEquals(SKIP, MerchantFoodSupplyGate.decide(OptionalDouble.empty(), FLOOR));
    }

    @Test
    void aStarvingReadingOfZeroDespawns() {
        assertEquals(DESPAWN, MerchantFoodSupplyGate.decide(OptionalDouble.of(0.0D), FLOOR));
    }

    @Test
    void ungatedTypesAlwaysMaintain() {
        assertEquals(MAINTAIN, MerchantFoodSupplyGate.decide(OptionalDouble.empty(), 0.0D));
        assertEquals(MAINTAIN, MerchantFoodSupplyGate.decide(OptionalDouble.of(0.0D), 0.0D));
        assertEquals(MAINTAIN, MerchantFoodSupplyGate.decide(OptionalDouble.of(999.0D), -1.0D));
    }
}
