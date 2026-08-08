package com.seggellion.britannia_mod.client.screen.bank;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 17 gate corrective: the double-click that cashes a cheque, as plain timing logic.
 * The screen half -- that this only consults for cells holding a cheque, and that a fired
 * double-click claims the pending lock before the packet -- is the owner visual gate's, like
 * every screen behaviour (Architecture Decision 0).
 */
class BankChequeDoubleClickTest {

    @Test
    void aSecondPressOnTheSameSlotWithinTheWindowFires() {
        BankChequeDoubleClick tracker = new BankChequeDoubleClick();
        assertFalse(tracker.register(3, 1_000));
        assertTrue(tracker.register(3, 1_000 + BankChequeDoubleClick.WINDOW_MILLIS));
    }

    @Test
    void aLatePressIsAFreshFirstPressNotACompletion() {
        BankChequeDoubleClick tracker = new BankChequeDoubleClick();
        assertFalse(tracker.register(3, 1_000));
        assertFalse(tracker.register(3, 1_001 + BankChequeDoubleClick.WINDOW_MILLIS));
        // ...and it re-armed: the next press within the window completes against IT.
        assertTrue(tracker.register(3, 1_100 + BankChequeDoubleClick.WINDOW_MILLIS));
    }

    @Test
    void aPressOnADifferentSlotDoesNotCompleteAndReArms() {
        BankChequeDoubleClick tracker = new BankChequeDoubleClick();
        assertFalse(tracker.register(3, 1_000));
        assertFalse(tracker.register(4, 1_100), "two different cheques are not one gesture");
        assertTrue(tracker.register(4, 1_200), "but the second slot armed itself");
    }

    @Test
    void aTripleClickIsOneCashingPlusAFreshFirstPress() {
        BankChequeDoubleClick tracker = new BankChequeDoubleClick();
        assertFalse(tracker.register(3, 1_000));
        assertTrue(tracker.register(3, 1_100));
        // The tracker reset on firing: click three must not cash a second cheque.
        assertFalse(tracker.register(3, 1_200));
    }

    @Test
    void aDragResetsThePendingFirstPress() {
        BankChequeDoubleClick tracker = new BankChequeDoubleClick();
        assertFalse(tracker.register(3, 1_000));
        tracker.reset();
        // Returning to the same cell after a drag is a first press, not a completion --
        // otherwise store-then-press would cash a cheque the player meant to keep.
        assertFalse(tracker.register(3, 1_050));
    }
}
