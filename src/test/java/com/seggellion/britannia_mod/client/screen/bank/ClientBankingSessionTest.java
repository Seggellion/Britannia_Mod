package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.network.payload.BankAccountOpenedS2CPayload;
import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload;
import com.seggellion.britannia_mod.service.banking.BankItemSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 2's required coverage for {@link ClientBankingSession}.
 *
 * <p>Two of the playbook's named tests -- "applying a newer refresh" and "rejecting or ignoring an
 * older refresh" -- were re-scoped by Milestone 1's D12 and owner approval on 2026-08-03. No
 * revision or sequence discriminator exists to order two refreshes by, and none is being added,
 * because pushes travel one ordered connection and each is a whole snapshot rather than a delta.
 * The property that actually needs guarding is therefore {@link #refreshReplacesStateWholesale}
 * (there is no partial merge to get wrong) and {@link
 * #resultArrivingAfterCloseIsDroppedRatherThanApplied} (a late result cannot resurrect a closed
 * session). Both are below.
 */
class ClientBankingSessionTest {

    private static final int TELLER = 42;
    private static final int OTHER_TELLER = 43;

    @BeforeEach
    @AfterEach
    void resetStaticSession() {
        ClientBankingSession.resetForTesting();
    }

    // ---------- Helpers ----------

    private static BankAccountOpenedS2CPayload account(int entityId, List<BankItemSummary> items) {
        return new BankAccountOpenedS2CPayload(
                "Aldric", "male", entityId, "Britain", 250, 12.5, 3, 47, 92, items
        );
    }

    private static BankAccountOpenedS2CPayload account(int entityId) {
        return account(entityId, List.of());
    }

    private static BankItemSummary item(UUID id) {
        return BankItemSummary.withoutIdentity(id, 2.5);
    }

    private static BankTransferResultS2CPayload rejection() {
        return new BankTransferResultS2CPayload(
                BankTransferResultS2CPayload.Operation.DEPOSIT,
                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
        );
    }

    // ---------- State creation from the account-open payload ----------

    @Test
    void createsSessionStateFromTheAccountOpenPayload() {
        ClientBankingSession session = ClientBankingSession.applyAccountOpened(account(TELLER));

        assertSame(session, ClientBankingSession.active());
        assertTrue(ClientBankingSession.isOpen());
        assertEquals(TELLER, session.tellerEntityId());
        assertEquals("Aldric", session.tellerName());
        assertEquals("male", session.tellerGender());
        assertEquals("Britain", session.cityDisplayName());
        assertEquals(3, session.goldBalance());
        assertEquals(47, session.silverBalance());
        assertEquals(92, session.copperBalance());
        assertEquals(12.5, session.currentWeight());
        assertEquals(250, session.weightLimit());
        assertEquals(List.of(), session.bankItems());
        assertNull(session.selectedStoredItem());
        assertFalse(session.isMutationPending());
        assertNull(session.lastResult());
    }

    @Test
    void carriesAbsentCityDisplayNameAsNullForGlobalMode() {
        ClientBankingSession session = ClientBankingSession.applyAccountOpened(
                new BankAccountOpenedS2CPayload("Aldric", "female", TELLER, null, 250, 0.0, 0, 0, 0, List.of())
        );
        assertNull(session.cityDisplayName());
    }

    @Test
    void isClosedBeforeAnyAccountIsOpened() {
        assertFalse(ClientBankingSession.isOpen());
        assertNull(ClientBankingSession.active());
    }

    // ---------- Navigation preserves banker and account identity ----------

    @Test
    void refreshForTheSameTellerKeepsTheSameSessionInstance() {
        ClientBankingSession first = ClientBankingSession.applyAccountOpened(account(TELLER));
        ClientBankingSession second = ClientBankingSession.applyAccountOpened(account(TELLER));

        // The same object survives, which is what lets a screen keep rendering across a refresh
        // instead of being rebuilt -- the whole point of Milestone 2.
        assertSame(first, second);
        assertEquals(TELLER, second.tellerEntityId());
    }

    @Test
    void openingADifferentTellerStartsAFreshSession() {
        UUID stored = UUID.randomUUID();
        ClientBankingSession first = ClientBankingSession.applyAccountOpened(account(TELLER, List.of(item(stored))));
        first.selectStoredItem(stored);
        first.beginPending(BankTransferResultS2CPayload.Operation.WITHDRAWAL);

        ClientBankingSession second = ClientBankingSession.applyAccountOpened(account(OTHER_TELLER));

        assertNotSame(first, second);
        assertEquals(OTHER_TELLER, second.tellerEntityId());
        // Neither a selection nor a lock may cross accounts: both referred to the previous one.
        assertNull(second.selectedStoredItem());
        assertFalse(second.isMutationPending());
    }

    // ---------- Refresh semantics (re-scoped per D12) ----------

    @Test
    void refreshReplacesStateWholesale() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ClientBankingSession session = ClientBankingSession.applyAccountOpened(account(TELLER, List.of(item(first))));

        ClientBankingSession.applyAccountOpened(new BankAccountOpenedS2CPayload(
                "Aldric", "male", TELLER, "Britain", 250, 40.0, 9, 8, 7, List.of(item(second))
        ));

        // Every value comes from the newest snapshot; nothing is merged from the previous one.
        assertEquals(9, session.goldBalance());
        assertEquals(8, session.silverBalance());
        assertEquals(7, session.copperBalance());
        assertEquals(40.0, session.currentWeight());
        assertEquals(1, session.bankItems().size());
        assertEquals(second, session.bankItems().get(0).publicId());
    }

    @Test
    void refreshClearsPendingBecauseItIsTheSuccessSignal() {
        ClientBankingSession session = ClientBankingSession.applyAccountOpened(account(TELLER));
        assertTrue(session.beginPending(BankTransferResultS2CPayload.Operation.DEPOSIT));
        assertTrue(session.isMutationPending());

        ClientBankingSession.applyAccountOpened(account(TELLER));

        // A confirmed mutation never sends BankTransferResultS2CPayload -- the refresh push is the
        // only thing that arrives, so it has to be what releases the lock.
        assertFalse(session.isMutationPending());
        assertNull(session.pendingOperation());
    }

    @Test
    void refreshClearsTheLastResultSoAStaleMessageDoesNotOutliveIt() {
        ClientBankingSession session = ClientBankingSession.applyAccountOpened(account(TELLER));
        ClientBankingSession.applyTransferResult(rejection());
        assertEquals(rejection(), session.lastResult());

        ClientBankingSession.applyAccountOpened(account(TELLER));

        assertNull(session.lastResult());
    }

    // ---------- Selection ----------

    @Test
    void selectsAStoredItemTheAccountActuallyHolds() {
        UUID stored = UUID.randomUUID();
        ClientBankingSession session = ClientBankingSession.applyAccountOpened(account(TELLER, List.of(item(stored))));

        assertTrue(session.selectStoredItem(stored));
        assertEquals(stored, session.selectedStoredItem());
    }

    @Test
    void ignoresSelectionOfAnItemTheAccountDoesNotHold() {
        ClientBankingSession session = ClientBankingSession.applyAccountOpened(account(TELLER, List.of()));

        assertFalse(session.selectStoredItem(UUID.randomUUID()));
        assertNull(session.selectedStoredItem());
    }

    @Test
    void reselectingTheSameItemReportsNoChange() {
        UUID stored = UUID.randomUUID();
        ClientBankingSession session = ClientBankingSession.applyAccountOpened(account(TELLER, List.of(item(stored))));

        assertTrue(session.selectStoredItem(stored));
        assertFalse(session.selectStoredItem(stored));
        assertEquals(stored, session.selectedStoredItem());
    }

    @Test
    void selectionSurvivesARefreshThatStillHoldsTheItem() {
        UUID kept = UUID.randomUUID();
        ClientBankingSession session = ClientBankingSession.applyAccountOpened(account(TELLER, List.of(item(kept))));
        session.selectStoredItem(kept);

        ClientBankingSession.applyAccountOpened(account(TELLER, List.of(item(UUID.randomUUID()), item(kept))));

        // Bound to the public id, not the grid position -- the item moved from row 0 to row 1 and
        // the selection is still correct.
        assertEquals(kept, session.selectedStoredItem());
    }

    @Test
    void selectionClearsAfterARefreshThatNoLongerHoldsTheItem() {
        UUID withdrawn = UUID.randomUUID();
        ClientBankingSession session = ClientBankingSession.applyAccountOpened(account(TELLER, List.of(item(withdrawn))));
        session.selectStoredItem(withdrawn);

        ClientBankingSession.applyAccountOpened(account(TELLER, List.of()));

        assertNull(session.selectedStoredItem());
    }

    @Test
    void clearSelectionRemovesIt() {
        UUID stored = UUID.randomUUID();
        ClientBankingSession session = ClientBankingSession.applyAccountOpened(account(TELLER, List.of(item(stored))));
        session.selectStoredItem(stored);

        session.clearSelection();

        assertNull(session.selectedStoredItem());
    }

    // ---------- Pending transitions ----------

    @Test
    void beginPendingClaimsTheLockOnce() {
        ClientBankingSession session = ClientBankingSession.applyAccountOpened(account(TELLER));

        assertTrue(session.beginPending(BankTransferResultS2CPayload.Operation.DEPOSIT));
        assertEquals(BankTransferResultS2CPayload.Operation.DEPOSIT, session.pendingOperation());
    }

    @Test
    void beginPendingRefusesASecondMutationWhileOneIsInFlight() {
        ClientBankingSession session = ClientBankingSession.applyAccountOpened(account(TELLER));
        session.beginPending(BankTransferResultS2CPayload.Operation.DEPOSIT);

        // Design §10.9: the lock spans screens, so a withdrawal cannot start behind a deposit.
        assertFalse(session.beginPending(BankTransferResultS2CPayload.Operation.WITHDRAWAL));
        assertEquals(BankTransferResultS2CPayload.Operation.DEPOSIT, session.pendingOperation());
    }

    @Test
    void beginPendingClearsAPreviousResultSoItDoesNotDescribeTheNewOperation() {
        ClientBankingSession session = ClientBankingSession.applyAccountOpened(account(TELLER));
        ClientBankingSession.applyTransferResult(rejection());

        session.beginPending(BankTransferResultS2CPayload.Operation.WITHDRAWAL);

        assertNull(session.lastResult());
    }

    @Test
    void transferResultReleasesTheLockAndRecordsTheOutcome() {
        ClientBankingSession session = ClientBankingSession.applyAccountOpened(account(TELLER));
        session.beginPending(BankTransferResultS2CPayload.Operation.DEPOSIT);

        assertTrue(ClientBankingSession.applyTransferResult(rejection()));

        assertFalse(session.isMutationPending());
        assertEquals(rejection(), session.lastResult());
    }

    @Test
    void abandonPendingReleasesTheLockWithoutARecordedOutcome() {
        ClientBankingSession session = ClientBankingSession.applyAccountOpened(account(TELLER));
        session.beginPending(BankTransferResultS2CPayload.Operation.CHEQUE_ISSUANCE);

        session.abandonPending();

        assertFalse(session.isMutationPending());
        assertNull(session.lastResult());
    }

    @Test
    void aLockReleasedByAResultCanBeClaimedAgain() {
        ClientBankingSession session = ClientBankingSession.applyAccountOpened(account(TELLER));
        session.beginPending(BankTransferResultS2CPayload.Operation.DEPOSIT);
        ClientBankingSession.applyTransferResult(rejection());

        assertTrue(session.beginPending(BankTransferResultS2CPayload.Operation.WITHDRAWAL));
    }

    // ---------- Session close ----------

    @Test
    void closeEndsTheInteraction() {
        ClientBankingSession.applyAccountOpened(account(TELLER));

        ClientBankingSession.close();

        assertFalse(ClientBankingSession.isOpen());
        assertNull(ClientBankingSession.active());
    }

    @Test
    void resultArrivingAfterCloseIsDroppedRatherThanApplied() {
        ClientBankingSession session = ClientBankingSession.applyAccountOpened(account(TELLER));
        session.beginPending(BankTransferResultS2CPayload.Operation.DEPOSIT);
        ClientBankingSession.close();

        // Design §5.3: closing does not cancel a request that already reached the server, and the
        // client must not invent a result afterwards. Dropping it is how both hold at once.
        assertFalse(ClientBankingSession.applyTransferResult(rejection()));
        assertNull(ClientBankingSession.active());
    }

    @Test
    void reopeningAfterCloseBuildsAFreshSessionRatherThanResurrectingTheOld() {
        UUID stored = UUID.randomUUID();
        ClientBankingSession first = ClientBankingSession.applyAccountOpened(account(TELLER, List.of(item(stored))));
        first.selectStoredItem(stored);
        ClientBankingSession.close();

        ClientBankingSession second = ClientBankingSession.applyAccountOpened(account(TELLER, List.of(item(stored))));

        assertNotSame(first, second);
        assertNull(second.selectedStoredItem());
        assertFalse(second.isMutationPending());
    }

    @Test
    void closeIsSafeWhenNothingIsOpen() {
        ClientBankingSession.close();
        assertFalse(ClientBankingSession.isOpen());
    }
}
