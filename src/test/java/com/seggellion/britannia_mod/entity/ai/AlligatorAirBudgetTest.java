package com.seggellion.britannia_mod.entity.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AlligatorAirBudgetTest {
    @Test void reserveIncludesReturnAndOptionalOutboundTravel() {
        assertEquals(160, AlligatorAirBudget.returnTicks(6, 0));
        assertFalse(AlligatorAirBudget.canDive(160, 6, 0));
        assertTrue(AlligatorAirBudget.canDive(300, 2, 2));
        assertFalse(AlligatorAirBudget.canDive(100, 2, 2));
    }
    @Test void longerDetoursAndDepthCannotMakeADiveSafer() {
        assertTrue(AlligatorAirBudget.returnTicks(4, 8) > AlligatorAirBudget.returnTicks(4, 2));
        assertTrue(AlligatorAirBudget.returnTicks(8, 2) > AlligatorAirBudget.returnTicks(4, 2));
        assertFalse(AlligatorAirBudget.canDive(300, 3, 12));
    }
    @Test void invalidOrOverflowingCostsFailClosed() {
        assertFalse(AlligatorAirBudget.canDive(300, Double.NaN, 0));
        assertFalse(AlligatorAirBudget.canDive(300, 1, Double.POSITIVE_INFINITY));
        assertFalse(AlligatorAirBudget.canDive(300, Double.MAX_VALUE, Double.MAX_VALUE));
        assertEquals(60, AlligatorAirBudget.returnTicks(-1, -1));
    }
    @Test void slowedOrImmobileAnimalsCannotSpendAnUnscaledAirBudget() {
        assertEquals(260, AlligatorAirBudget.returnTicks(6, 0, .5));
        assertFalse(AlligatorAirBudget.canDive(300, 2, 2, .1));
        assertFalse(AlligatorAirBudget.canDive(300, 2, 2, 0));
        assertFalse(AlligatorAirBudget.canDive(300, 2, 2, Double.NaN));
    }
}
