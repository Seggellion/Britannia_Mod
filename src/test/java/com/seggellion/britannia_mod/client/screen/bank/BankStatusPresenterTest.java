package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload;
import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload.Kind;
import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload.Operation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankStatusPresenterTest {

    @Test
    void mapsEveryOperationAndKindPairWithoutFallingThrough() {
        for (Operation operation : Operation.values()) {
            for (Kind kind : Kind.values()) {
                BankStatusPresenter.Status status = BankStatusPresenter.forResult(operation, kind);
                assertTrue(
                        status.translationKey().startsWith("screen.britannia_mod.bank.status."),
                        operation + "/" + kind + " produced " + status.translationKey()
                );
            }
        }
    }

    @Test
    void givesEachKindItsOwnMessage() {
        Set<String> keys = new HashSet<>();
        for (Kind kind : Kind.values()) {
            keys.add(BankStatusPresenter.forResult(Operation.CHEQUE_REDEMPTION, kind).translationKey());
        }
        // Milestone 0 §3.5: BankScreen collapses PENDING_DELIVERY and all four cheque kinds onto
        // the clean-rejection message. Distinct keys is the fix, and this is its guard.
        assertEquals(Kind.values().length, keys.size(), "two kinds share one message");
    }

    @Test
    void readsReconciliationAsMoreSeriousThanAnOrdinaryRejection() {
        BankStatusPresenter.Status rejection =
                BankStatusPresenter.forResult(Operation.DEPOSIT, Kind.CLEAN_REJECTION);
        BankStatusPresenter.Status reconciliation =
                BankStatusPresenter.forResult(Operation.DEPOSIT, Kind.RECONCILIATION_REQUIRED);

        assertEquals(BankStatusPresenter.Severity.REJECTION, rejection.severity());
        assertEquals(BankStatusPresenter.Severity.RECONCILIATION, reconciliation.severity());
    }

    @Test
    void distinguishesReconciliationFromRejectionWithoutRelyingOnColour() {
        BankStatusPresenter.Severity rejection = BankStatusPresenter.Severity.REJECTION;
        BankStatusPresenter.Severity reconciliation = BankStatusPresenter.Severity.RECONCILIATION;

        // Design §16: colour must not be the only state indicator. Both of the other two signals
        // must differ, or a colour-blind player sees one message where there are two.
        assertNotEquals(rejection.markerWidth(), reconciliation.markerWidth());
        assertNotEquals(rejection.bold(), reconciliation.bold());
        assertTrue(reconciliation.bold());
    }

    @Test
    void doesNotPresentPendingDeliveryAsARejection() {
        BankStatusPresenter.Status status =
                BankStatusPresenter.forResult(Operation.CHEQUE_ISSUANCE, Kind.PENDING_DELIVERY);

        // Rails already confirmed; the cheque is real and a retry delivers it. BankScreen renders
        // this as a rejection today, which both alarms the player and hides that nothing failed.
        assertEquals(BankStatusPresenter.Severity.INFORMATIONAL, status.severity());
        assertNotEquals(
                BankStatusPresenter.forResult(Operation.CHEQUE_ISSUANCE, Kind.CLEAN_REJECTION).translationKey(),
                status.translationKey()
        );
    }

    @Test
    void doesNotPresentAnEmptyPurseAsARefusal() {
        BankStatusPresenter.Status status =
                BankStatusPresenter.forResult(Operation.DEPOSIT, Kind.NOTHING_TO_DEPOSIT);

        // Nothing failed and nobody refused the player -- they simply had no coins. Design §15
        // lists this as its own category precisely so it does not read as the bank turning them
        // away, which is what CLEAN_REJECTION's wording says.
        assertEquals(BankStatusPresenter.Severity.INFORMATIONAL, status.severity());
        assertNotEquals(
                BankStatusPresenter.forResult(Operation.DEPOSIT, Kind.CLEAN_REJECTION).translationKey(),
                status.translationKey()
        );
    }

    @Test
    void presentsABalanceCeilingAsItsOwnActionableRefusal() {
        BankStatusPresenter.Status status =
                BankStatusPresenter.forResult(Operation.DEPOSIT, Kind.BALANCE_CAPACITY_EXCEEDED);

        // A refusal, but one the player can do something about -- withdraw or spend, then retry.
        // A generic rejection would not tell them that.
        assertEquals(BankStatusPresenter.Severity.REJECTION, status.severity());
        assertNotEquals(
                BankStatusPresenter.forResult(Operation.DEPOSIT, Kind.CLEAN_REJECTION).translationKey(),
                status.translationKey()
        );
        assertNotEquals(
                BankStatusPresenter.forResult(Operation.DEPOSIT, Kind.NOTHING_TO_DEPOSIT).translationKey(),
                status.translationKey()
        );
    }

    @Test
    void keepsTheFourChequeOutcomesDistinctFromEachOther() {
        Set<String> keys = new HashSet<>();
        for (Kind kind : new Kind[]{
                Kind.CHEQUE_NOT_FOUND, Kind.CHEQUE_ALREADY_REDEEMED, Kind.CHEQUE_CANCELLED, Kind.CHEQUE_VOIDED
        }) {
            keys.add(BankStatusPresenter.forResult(Operation.CHEQUE_REDEMPTION, kind).translationKey());
        }
        assertEquals(4, keys.size());
    }

    @Test
    void mapsTheSameKindIdenticallyRegardlessOfOperation() {
        // The lock is global and the vocabulary is shared; a clean rejection reads the same
        // whichever operation produced it.
        String fromDeposit = BankStatusPresenter.forResult(Operation.DEPOSIT, Kind.CLEAN_REJECTION).translationKey();
        String fromWithdrawal = BankStatusPresenter.forResult(Operation.WITHDRAWAL, Kind.CLEAN_REJECTION).translationKey();
        assertEquals(fromDeposit, fromWithdrawal);
    }

    @Test
    void everyDarkGroundColourIsActuallyReadableOnADarkGround() {
        // The Bank Box renders status on a near-black panel. The parchment palette was chosen
        // against a light ground -- INFORMATIONAL is 0x111111 there and would simply vanish.
        for (BankStatusPresenter.Severity severity : BankStatusPresenter.Severity.values()) {
            int rgb = severity.colorOnDark() & 0xFFFFFF;
            int brightness = ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
            assertTrue(brightness >= 300,
                    severity + "'s dark-ground colour is too dark to read: " + Integer.toHexString(rgb));
        }
    }

    @Test
    void darkGroundColoursStayDistinctFromEachOther() {
        // Same requirement the light palette carries: two severities sharing a colour would make
        // the marker and bold flags the only difference, which §16 permits but the palette should
        // not lean on.
        Set<Integer> colours = new HashSet<>();
        for (BankStatusPresenter.Severity severity : BankStatusPresenter.Severity.values()) {
            colours.add(severity.colorOnDark());
        }
        assertEquals(BankStatusPresenter.Severity.values().length, colours.size(),
                "two severities share a dark-ground colour");
    }

    @Test
    void returnsNullForNoPayload() {
        assertNull(BankStatusPresenter.forResult(null));
    }

    @Test
    void mapsAPayloadTheSameWayAsItsParts() {
        BankTransferResultS2CPayload payload =
                new BankTransferResultS2CPayload(Operation.WITHDRAWAL, Kind.RECONCILIATION_REQUIRED);
        assertEquals(
                BankStatusPresenter.forResult(Operation.WITHDRAWAL, Kind.RECONCILIATION_REQUIRED),
                BankStatusPresenter.forResult(payload)
        );
    }

    @Test
    void marksClientValidationMessagesAsValidationNotRejection() {
        for (BankStatusPresenter.Status status : new BankStatusPresenter.Status[]{
                BankStatusPresenter.EMPTY_AMOUNT,
                BankStatusPresenter.INVALID_AMOUNT,
                BankStatusPresenter.AMOUNT_TOO_LARGE,
                BankStatusPresenter.NO_DENOMINATION_SELECTED,
                BankStatusPresenter.INSUFFICIENT_BALANCE
        }) {
            assertEquals(BankStatusPresenter.Severity.VALIDATION, status.severity(), status.translationKey());
        }
    }

    @Test
    void givesEveryDeclaredStatusAUniqueKey() {
        Set<String> keys = new HashSet<>();
        for (Operation operation : Operation.values()) {
            for (Kind kind : Kind.values()) {
                keys.add(BankStatusPresenter.forResult(operation, kind).translationKey());
            }
        }
        int fromResults = keys.size();
        for (BankStatusPresenter.Status status : new BankStatusPresenter.Status[]{
                BankStatusPresenter.EMPTY_AMOUNT,
                BankStatusPresenter.INVALID_AMOUNT,
                BankStatusPresenter.AMOUNT_TOO_LARGE,
                BankStatusPresenter.NO_DENOMINATION_SELECTED,
                BankStatusPresenter.INSUFFICIENT_BALANCE,
                BankStatusPresenter.NOTHING_SELECTED,
                BankStatusPresenter.EMPTY_VAULT
        }) {
            keys.add(status.translationKey());
        }
        assertEquals(fromResults + 7, keys.size(), "a client status collided with a server one");
    }
}
