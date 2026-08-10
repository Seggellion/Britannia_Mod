package com.seggellion.britannia_mod.service.guild;

import com.seggellion.britannia_mod.economy.CoinConversion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The two rules that stand between a committed Rails write and a player's purse. */
class GuildTrainingChargeTest {
    private static int gold(int amount) {
        return amount * CoinConversion.COPPER_PER_GOLD;
    }

    @Test
    void chargesExactlyWhatRailsGranted() {
        GuildTrainingCharge.Plan plan = GuildTrainingCharge.decide(73, gold(500));

        assertTrue(plan.charge());
        assertEquals(73, plan.gold());
        assertEquals(gold(73), plan.copper());
    }

    @Test
    void chargesTheGrantedAmountNotTheRequestedOne() {
        // Rails clamps to the real headroom when the skill moved between quote and commit. Paying
        // for the request would charge for training that was never given.
        GuildTrainingCharge.Plan plan = GuildTrainingCharge.decide(5, gold(400));

        assertEquals(5, plan.gold(), "granted, not the 400 the quote may have asked for");
    }

    @Test
    void exactFundsAreChargeable() {
        GuildTrainingCharge.Plan plan = GuildTrainingCharge.decide(400, gold(400));

        assertTrue(plan.charge());
        assertEquals(gold(400), plan.copper());
    }

    @Test
    void abandonsWhenTheCoinsAreGone() {
        // The player spent, dropped or banked the gold during the HTTP round trip. Charging now
        // would take whatever is left instead of what they agreed to.
        GuildTrainingCharge.Plan plan = GuildTrainingCharge.decide(400, gold(399));

        assertFalse(plan.charge());
        assertEquals(0, plan.copper(), "an abandoned charge must move nothing");
    }

    @Test
    void abandonsWhenSubGoldChangeIsAllThatRemains() {
        GuildTrainingCharge.Plan plan =
                GuildTrainingCharge.decide(1, CoinConversion.COPPER_PER_GOLD - 1);

        assertFalse(plan.charge());
    }

    @Test
    void abandonsOnANonPositiveGrant() {
        // Rails should never report this alongside APPLIED, but charging on it would be a free
        // deduction, so it is refused rather than trusted.
        assertFalse(GuildTrainingCharge.decide(0, gold(500)).charge());
        assertFalse(GuildTrainingCharge.decide(-5, gold(500)).charge());
    }

    @Test
    void aPlayerCarryingNothingIsNeverCharged() {
        assertFalse(GuildTrainingCharge.decide(1, 0).charge());
    }
}
