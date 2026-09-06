package com.seggellion.britannia_mod.resource.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.resource.extraction.ManagedExtractionPolicy.Actor;
import com.seggellion.britannia_mod.resource.extraction.ManagedExtractionPolicy.Verdict;

import org.junit.jupiter.api.Test;

/**
 * The actor rule and the creative rule, driven without a server.
 *
 * <p>Small on purpose. The behaviour that matters — a machine gets nothing, a creative builder
 * breaks a block plainly, a creative tester runs the whole flow, one swing costs one durability —
 * is proved in {@code ManagedExtractionPolicyGameTests} and {@code CreativeMiningBypassGameTests}
 * against a running world, because that is the only place it can be proved honestly. What is
 * worth pinning here is the shape of the decisions themselves: that every actor is answered, that
 * the answers are distinguishable, that exactly one combination earns, and that the creative
 * bypass turns on exactly two facts.
 */
class ManagedExtractionPolicyTest {

    @Test
    void onlyARealPlayerWhoIsNotBypassingMayExtract() {
        assertEquals(Verdict.ALLOWED, ManagedExtractionPolicy.decide(Actor.PLAYER, false));
        assertTrue(ManagedExtractionPolicy.decide(Actor.PLAYER, false).allowed());
    }

    @Test
    void aBypassingCreativePlayerIsAdministeringRatherThanMining() {
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
    void exactlyOneCombinationOfActorAndBypassEarns() {
        int allowed = 0;
        for (Actor actor : Actor.values()) {
            for (boolean creativeBypass : new boolean[] {false, true}) {
                Verdict verdict = ManagedExtractionPolicy.decide(actor, creativeBypass);
                if (verdict.allowed()) {
                    allowed++;
                    assertEquals(Actor.PLAYER, actor, "a non-player was allowed to extract");
                    assertFalse(creativeBypass, "a bypassing creative actor was allowed to extract");
                }
            }
        }
        assertEquals(1, allowed, "exactly one actor/bypass combination may produce economy");
    }

    /** Every denial says which denial it is, so an operator can be told what actually stopped them. */
    @Test
    void everyDenialIsDistinguishableFromTheOthers() {
        assertEquals(4, Verdict.values().length,
                "a verdict was added or removed without revisiting the extraction call sites");
        assertEquals(3, java.util.Arrays.stream(Verdict.values()).filter(v -> !v.allowed()).count(),
                "the denial reasons must stay distinguishable rather than collapsing into one");
    }

    // ---------------------------------------------------------------------------------------
    // The creative rule: in creative, and not attacking with the Britannia pickaxe.
    // ---------------------------------------------------------------------------------------

    /** The whole truth table, because it is four rows and every one of them is load-bearing. */
    @Test
    void theCreativeBypassTurnsOnExactlyTwoFacts() {
        assertTrue(ManagedExtractionPolicy.creativeBypasses(true, false),
                "creative without the Britannia pickaxe must stand every managed path aside");
        assertFalse(ManagedExtractionPolicy.creativeBypasses(true, true),
                "creative attacking with the Britannia pickaxe is a tester and gets the real rules");
        assertFalse(ManagedExtractionPolicy.creativeBypasses(false, false),
                "survival or adventure without the pickaxe is an ordinary miner, judged by the tool rule");
        assertFalse(ManagedExtractionPolicy.creativeBypasses(false, true),
                "survival or adventure with the pickaxe is an ordinary miner");
    }

    /** The bypass is a creative fact and a hand fact; no other input exists to widen it. */
    @Test
    void nothingButTheGameModeAndTheAttackingHandCanOpenTheBypass() {
        // The pure rule takes exactly the two facts. This pins that shape: any future input --
        // permission level, a tag, an offhand, a name -- has to be added here, in a test, on
        // purpose, rather than slipping into the live predicate alone.
        for (boolean creative : new boolean[] {false, true}) {
            for (boolean pickaxe : new boolean[] {false, true}) {
                assertEquals(creative && !pickaxe,
                        ManagedExtractionPolicy.creativeBypasses(creative, pickaxe),
                        "creative=" + creative + ", pickaxe=" + pickaxe);
            }
        }
    }
}
