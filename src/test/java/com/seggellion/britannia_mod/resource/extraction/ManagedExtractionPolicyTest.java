package com.seggellion.britannia_mod.resource.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.resource.extraction.ManagedExtractionPolicy.Actor;
import com.seggellion.britannia_mod.resource.extraction.ManagedExtractionPolicy.Verdict;

import org.junit.jupiter.api.Test;

/**
 * The actor rule, driven without a server.
 *
 * <p>Small on purpose. The behaviour that matters — a machine gets nothing, an operator mints
 * nothing, one swing costs one durability — is proved in
 * {@code ManagedExtractionPolicyGameTests} against a running world, because that is the only place
 * it can be proved honestly. What is worth pinning here is the shape of the decision itself: that
 * every actor is answered, that the answers are distinguishable, and that exactly one combination
 * earns.
 */
class ManagedExtractionPolicyTest {

    @Test
    void onlyARealPlayerOutsideCreativeMayExtract() {
        assertEquals(Verdict.ALLOWED, ManagedExtractionPolicy.decide(Actor.PLAYER, false));
        assertTrue(ManagedExtractionPolicy.decide(Actor.PLAYER, false).allowed());
    }

    @Test
    void aCreativePlayerIsAdministeringRatherThanMining() {
        assertEquals(Verdict.DENIED_CREATIVE, ManagedExtractionPolicy.decide(Actor.PLAYER, true));
        assertFalse(ManagedExtractionPolicy.decide(Actor.PLAYER, true).allowed());
    }

    /** Automation does not earn, and its game mode is not consulted on the way to saying so. */
    @Test
    void aFakePlayerIsDeniedInEitherGameMode() {
        assertEquals(Verdict.DENIED_FAKE_PLAYER, ManagedExtractionPolicy.decide(Actor.FAKE_PLAYER, false));
        assertEquals(Verdict.DENIED_FAKE_PLAYER, ManagedExtractionPolicy.decide(Actor.FAKE_PLAYER, true));
    }

    @Test
    void somethingThatIsNotAPlayerAtAllIsDenied() {
        assertEquals(Verdict.DENIED_NON_PLAYER, ManagedExtractionPolicy.decide(Actor.NON_PLAYER, false));
        assertEquals(Verdict.DENIED_NON_PLAYER, ManagedExtractionPolicy.decide(Actor.NON_PLAYER, true));
    }

    /**
     * Exactly one combination earns, out of every combination there is.
     *
     * <p>Written as a sweep rather than as four assertions so that adding an actor kind without
     * deciding what it may do fails here, instead of defaulting to whatever the switch falls
     * through to.
     */
    @Test
    void exactlyOneCombinationOfActorAndModeEarns() {
        int allowed = 0;
        for (Actor actor : Actor.values()) {
            for (boolean creative : new boolean[] {false, true}) {
                Verdict verdict = ManagedExtractionPolicy.decide(actor, creative);
                if (verdict.allowed()) {
                    allowed++;
                    assertEquals(Actor.PLAYER, actor, "a non-player was allowed to extract");
                    assertFalse(creative, "a creative actor was allowed to extract");
                }
            }
        }
        assertEquals(1, allowed, "exactly one actor/mode combination may produce economy");
    }

    /** Every denial says which denial it is, so an operator can be told what actually stopped them. */
    @Test
    void everyDenialIsDistinguishableFromTheOthers() {
        assertEquals(4, Verdict.values().length,
                "a verdict was added or removed without revisiting the extraction call sites");
        assertEquals(3, java.util.Arrays.stream(Verdict.values()).filter(v -> !v.allowed()).count(),
                "the denial reasons must stay distinguishable rather than collapsing into one");
    }
}
