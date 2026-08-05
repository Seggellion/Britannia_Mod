package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload.Operation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Which noise a successful mutation makes (owner, 2026-08-04). */
class BankMutationCueTest {

    @Test
    void coinsJingleWhicheverWayTheyMove() {
        assertEquals(BankMutationCue.COIN, BankMutationCue.forSuccess(Operation.DEPOSIT, true));
        assertEquals(BankMutationCue.COIN, BankMutationCue.forSuccess(Operation.WITHDRAWAL, true));
    }

    @Test
    void itemsGetTheirOwnSoundInEachDirection() {
        // The direction matters for items and not for coins, which is the whole reason this is a
        // (operation, movesCurrency) pair rather than one flag.
        assertEquals(BankMutationCue.ITEM_DEPOSITED, BankMutationCue.forSuccess(Operation.DEPOSIT, false));
        assertEquals(BankMutationCue.ITEM_WITHDRAWN, BankMutationCue.forSuccess(Operation.WITHDRAWAL, false));
    }

    @Test
    void chequeOperationsSayNothingEitherWay() {
        // Both already produce their own visible outcome; a thud would claim an item moved.
        for (boolean movesCurrency : new boolean[]{true, false}) {
            assertEquals(BankMutationCue.SILENT,
                    BankMutationCue.forSuccess(Operation.CHEQUE_ISSUANCE, movesCurrency));
            assertEquals(BankMutationCue.SILENT,
                    BankMutationCue.forSuccess(Operation.CHEQUE_REDEMPTION, movesCurrency));
        }
    }

    @Test
    void nothingPendingIsSilentRatherThanAGuess() {
        // A refresh with no in-flight request of ours -- another client's deposit, or a plain
        // reopen -- must not make a noise about someone else's business.
        assertEquals(BankMutationCue.SILENT, BankMutationCue.forSuccess(null, false));
        assertEquals(BankMutationCue.SILENT, BankMutationCue.forSuccess(null, true));
    }

    @Test
    void everyOperationIsMappedInBothStates() {
        for (Operation operation : Operation.values()) {
            for (boolean movesCurrency : new boolean[]{true, false}) {
                assertEquals(true, BankMutationCue.forSuccess(operation, movesCurrency) != null,
                        operation + "/" + movesCurrency);
            }
        }
    }
}
