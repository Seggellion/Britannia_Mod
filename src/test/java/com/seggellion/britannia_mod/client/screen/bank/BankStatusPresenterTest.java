package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.network.payload.BankAccountOpenedS2CPayload;
import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload;
import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload.Kind;
import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload.Operation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankStatusPresenterTest {

    @AfterEach
    void resetSession() {
        ClientBankingSession.resetForTesting();
    }

    private static ClientBankingSession sessionWithClock(long[] now) {
        ClientBankingSession.useClockForTesting(() -> now[0]);
        return ClientBankingSession.applyAccountOpened(new BankAccountOpenedS2CPayload(
                "Aldric", "male", 42, null, 250, 0.0, 0, 0, 0, java.util.List.of(), false
        ));
    }

    // ---------- Milestone 17: statusFor -- one question for the whole status line ----------

    @Test
    void statusForShowsNothingForAQuietSessionAndNoSession() {
        long[] now = {0L};
        assertNull(BankStatusPresenter.statusFor(null));
        assertNull(BankStatusPresenter.statusFor(sessionWithClock(now)));
    }

    @Test
    void statusForNamesLongSilenceAsUncertain() {
        long[] now = {0L};
        ClientBankingSession session = sessionWithClock(now);
        session.beginPending(Operation.DEPOSIT);

        assertNull(BankStatusPresenter.statusFor(session), "patience first -- pending is not yet a status");
        now[0] += ClientBankingSession.UNCERTAIN_AFTER_MILLIS;
        assertEquals(BankStatusPresenter.UNCERTAIN, BankStatusPresenter.statusFor(session));
    }

    @Test
    void statusForPrefersARealResultOverTheSilence() {
        long[] now = {0L};
        ClientBankingSession session = sessionWithClock(now);
        session.beginPending(Operation.DEPOSIT);
        now[0] += ClientBankingSession.UNCERTAIN_AFTER_MILLIS * 2;
        ClientBankingSession.applyTransferResult(
                new BankTransferResultS2CPayload(Operation.DEPOSIT, Kind.INVENTORY_FULL));

        BankStatusPresenter.Status status = BankStatusPresenter.statusFor(session);
        assertEquals(
                BankStatusPresenter.forResult(Operation.DEPOSIT, Kind.INVENTORY_FULL), status,
                "an answer, however late, replaces the uncertainty"
        );
    }

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
                BankStatusPresenter.AMOUNT_BELOW_MINIMUM,
                BankStatusPresenter.NO_DENOMINATION_SELECTED,
                BankStatusPresenter.INSUFFICIENT_BALANCE,
                BankStatusPresenter.NOTHING_SELECTED,
                BankStatusPresenter.EMPTY_VAULT,
                BankStatusPresenter.UNCERTAIN
        }) {
            keys.add(status.translationKey());
        }
        // +8, not +9: the wire INSUFFICIENT_BALANCE and the client pre-check status deliberately
        // share one sentence (Milestone 16) -- one message for one fact, whichever side caught it
        // first. Every other client status must stay collision-free.
        assertEquals(fromResults + 8, keys.size(), "an unintended key collision between client and server statuses");
    }

    @Test
    void theTwoCapacityCeilingsAndTheFullPackStayDistinct() {
        // Three different facts -- the coin-count ceiling, the vault's weight ceiling, and the
        // player's own pack -- that would be genuinely misleading collapsed into one message.
        Set<String> keys = new HashSet<>();
        keys.add(BankStatusPresenter.forResult(Operation.DEPOSIT, Kind.BALANCE_CAPACITY_EXCEEDED).translationKey());
        keys.add(BankStatusPresenter.forResult(Operation.DEPOSIT, Kind.BANK_CAPACITY_EXCEEDED).translationKey());
        keys.add(BankStatusPresenter.forResult(Operation.WITHDRAWAL, Kind.INVENTORY_FULL).translationKey());
        assertEquals(3, keys.size());
    }

    @Test
    void anAlreadyGoneItemReadsAsInformationRatherThanRefusal() {
        // Nobody refused the player and nothing is theirs to fix -- the item left the vault from
        // another client, and the refreshed grid is the answer. Same reasoning that made
        // NOTHING_TO_DEPOSIT informational at Milestone 6b.
        BankStatusPresenter.Status status =
                BankStatusPresenter.forResult(Operation.WITHDRAWAL, Kind.STORED_ITEM_UNAVAILABLE);
        assertEquals(BankStatusPresenter.Severity.INFORMATIONAL, status.severity());
    }

    @Test
    void anIneligibleItemIsARejectionWithItsOwnSentence() {
        BankStatusPresenter.Status status =
                BankStatusPresenter.forResult(Operation.DEPOSIT, Kind.INELIGIBLE_ITEM);
        assertEquals(BankStatusPresenter.Severity.REJECTION, status.severity());
        assertNotEquals(
                BankStatusPresenter.forResult(Operation.DEPOSIT, Kind.CLEAN_REJECTION).translationKey(),
                status.translationKey()
        );
    }

    @Test
    void theWireInsufficientBalanceSharesTheSentenceButNotTheSeverity() {
        // The client pre-check is VALIDATION -- fix the form and try again. The server's answer is
        // a real REJECTION: the request was made and refused. Same words, different weight.
        BankStatusPresenter.Status wire =
                BankStatusPresenter.forResult(Operation.WITHDRAWAL, Kind.INSUFFICIENT_BALANCE);
        assertEquals(BankStatusPresenter.INSUFFICIENT_BALANCE.translationKey(), wire.translationKey());
        assertEquals(BankStatusPresenter.Severity.REJECTION, wire.severity());
        assertEquals(BankStatusPresenter.Severity.VALIDATION, BankStatusPresenter.INSUFFICIENT_BALANCE.severity());
    }
}
