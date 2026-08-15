package com.seggellion.britannia_mod.mining;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Milestone 4: the single-activation guard that keeps one break worth one Mining roll. */
class MiningSkillTest {

    private static final long POSITION = 1234L;
    private static final long OTHER_POSITION = 5678L;

    @BeforeEach
    void resetGuard() {
        MiningSkill.clearActivationHistory();
    }

    @Test
    void theSameBreakIsCountedOnlyOnce() {
        UUID player = UUID.randomUUID();
        assertTrue(MiningSkill.acceptActivation(player, POSITION, 100L), "first activation counts");
        assertFalse(MiningSkill.acceptActivation(player, POSITION, 100L),
                "a repeated callback for the same break must not count again");
        assertFalse(MiningSkill.acceptActivation(player, POSITION, 100L),
                "and must keep not counting however many callbacks fire");
    }

    @Test
    void distinctBreaksStillCount() {
        UUID player = UUID.randomUUID();
        assertTrue(MiningSkill.acceptActivation(player, POSITION, 100L));
        assertTrue(MiningSkill.acceptActivation(player, OTHER_POSITION, 100L),
                "a different node broken in the same tick is its own activation");
        assertTrue(MiningSkill.acceptActivation(player, POSITION, 101L),
                "the same node mined again on a later tick is its own activation");
    }

    @Test
    void playersDoNotShareTheGuard() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(MiningSkill.acceptActivation(first, POSITION, 100L));
        assertTrue(MiningSkill.acceptActivation(second, POSITION, 100L),
                "one miner's activation must never suppress another's");
    }
}
