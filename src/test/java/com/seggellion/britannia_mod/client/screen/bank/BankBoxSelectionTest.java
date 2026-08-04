package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.network.payload.BankAccountOpenedS2CPayload;
import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload;
import com.seggellion.britannia_mod.service.banking.BankItemSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Milestone 11: the single-click selection contract -- the interaction that retires the legacy
 * 400 ms double-click. There is no timer anywhere in this file, which is the point.
 */
class BankBoxSelectionTest {

    /** A 9x2 grid at a known origin; clicks are aimed at cell centres. */
    private static final BankGridGeometry GRID = BankGridGeometry.of(100, 100, 9, 2);

    @BeforeEach
    @AfterEach
    void resetSession() {
        ClientBankingSession.resetForTesting();
    }

    private static List<BankItemSummary> items(int count) {
        List<BankItemSummary> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(BankItemSummary.withoutIdentity(UUID.randomUUID(), 1.0));
        }
        return items;
    }

    private static ClientBankingSession openWith(List<BankItemSummary> items) {
        return ClientBankingSession.applyAccountOpened(new BankAccountOpenedS2CPayload(
                "Aldric", "male", 42, "Britain", 250, 12.5, 3, 47, 92, items, false
        ));
    }

    private static double cellCentreX(int cell) {
        return GRID.cellLeft(cell) + (GRID.cellPitch() / 2.0);
    }

    private static double cellCentreY(int cell) {
        return GRID.cellTop(cell) + (GRID.cellPitch() / 2.0);
    }

    // ---------- Selecting ----------

    @Test
    void oneClickOnAnOccupiedCellSelectsThatItem() {
        List<BankItemSummary> items = items(5);
        ClientBankingSession session = openWith(items);

        BankBoxSelection.Result result = BankBoxSelection.handleClick(
                session, GRID, BankGridScroll.TOP, cellCentreX(3), cellCentreY(3));

        assertEquals(BankBoxSelection.Result.SELECTED, result);
        assertEquals(items.get(3).publicId(), session.selectedStoredItem());
    }

    @Test
    void selectionIsByPublicIdThroughTheScrolledView() {
        // 20 items, scrolled one row down: visible cell 0 is item 9. The selection must land on
        // the item the player is looking at, not on the raw cell index.
        List<BankItemSummary> items = items(20);
        ClientBankingSession session = openWith(items);
        BankGridScroll scrolled = new BankGridScroll(1);

        BankBoxSelection.handleClick(session, GRID, scrolled, cellCentreX(0), cellCentreY(0));

        assertEquals(items.get(9).publicId(), session.selectedStoredItem());
    }

    @Test
    void reclickingTheSelectedItemKeepsItSelected() {
        // Not a toggle: players trained by the old double-click interface will double-click, and
        // a toggle would read that as select-then-deselect.
        List<BankItemSummary> items = items(3);
        ClientBankingSession session = openWith(items);

        BankBoxSelection.handleClick(session, GRID, BankGridScroll.TOP, cellCentreX(1), cellCentreY(1));
        BankBoxSelection.Result second = BankBoxSelection.handleClick(
                session, GRID, BankGridScroll.TOP, cellCentreX(1), cellCentreY(1));

        assertEquals(BankBoxSelection.Result.UNCHANGED, second);
        assertEquals(items.get(1).publicId(), session.selectedStoredItem());
    }

    // ---------- Clearing ----------

    @Test
    void clickingAnEmptyCellClearsTheSelection() {
        List<BankItemSummary> items = items(3);
        ClientBankingSession session = openWith(items);
        BankBoxSelection.handleClick(session, GRID, BankGridScroll.TOP, cellCentreX(0), cellCentreY(0));

        BankBoxSelection.Result result = BankBoxSelection.handleClick(
                session, GRID, BankGridScroll.TOP, cellCentreX(7), cellCentreY(7));

        assertEquals(BankBoxSelection.Result.CLEARED, result);
        assertNull(session.selectedStoredItem());
    }

    @Test
    void clickingEmptySpaceWithNothingSelectedIsAHandledNoOp() {
        ClientBankingSession session = openWith(items(0));

        BankBoxSelection.Result result = BankBoxSelection.handleClick(
                session, GRID, BankGridScroll.TOP, cellCentreX(4), cellCentreY(4));

        assertEquals(BankBoxSelection.Result.UNCHANGED, result,
                "handled -- the click was on the grid -- but there was nothing to change");
    }

    // ---------- Boundaries ----------

    @Test
    void aClickOutsideTheGridIsNotConsumedAndChangesNothing() {
        List<BankItemSummary> items = items(3);
        ClientBankingSession session = openWith(items);
        BankBoxSelection.handleClick(session, GRID, BankGridScroll.TOP, cellCentreX(0), cellCentreY(0));

        BankBoxSelection.Result result = BankBoxSelection.handleClick(
                session, GRID, BankGridScroll.TOP, GRID.left() - 5, GRID.top() - 5);

        assertEquals(BankBoxSelection.Result.OUTSIDE, result);
        assertEquals(items.get(0).publicId(), session.selectedStoredItem(), "selection must survive");
    }

    @Test
    void theSharedEdgeWithTheGridBelowBelongsToExactlyOneGrid() {
        // The Bank Box stacks grids; BankGridGeometry's exclusive bottom edge is what stops one
        // click landing in two of them. Clicking exactly on the boundary is OUTSIDE this grid.
        ClientBankingSession session = openWith(items(18));

        BankBoxSelection.Result result = BankBoxSelection.handleClick(
                session, GRID, BankGridScroll.TOP, cellCentreX(0), GRID.bottom());

        assertEquals(BankBoxSelection.Result.OUTSIDE, result);
    }

    // ---------- The pending lock ----------

    @Test
    void theGridLocksWhileAMutationIsPending() {
        List<BankItemSummary> items = items(3);
        ClientBankingSession session = openWith(items);
        BankBoxSelection.handleClick(session, GRID, BankGridScroll.TOP, cellCentreX(0), cellCentreY(0));
        session.beginPending(BankTransferResultS2CPayload.Operation.WITHDRAWAL);

        BankBoxSelection.Result result = BankBoxSelection.handleClick(
                session, GRID, BankGridScroll.TOP, cellCentreX(2), cellCentreY(2));

        assertEquals(BankBoxSelection.Result.LOCKED, result, "the event is consumed, nothing changes");
        assertEquals(items.get(0).publicId(), session.selectedStoredItem(),
                "the pending operation's own selection must not be swapped out from under it");
    }

    // ---------- Refresh interplay ----------

    @Test
    void aRefreshThatRemovesTheSelectedItemClearsTheSelectionTheGridShows() {
        // The session owns this rule and is tested for it; this pins the interaction-level
        // consequence: after the refresh, the grid draws no selection ring and a Withdraw wired
        // at Milestone 15 has nothing stale to act on.
        List<BankItemSummary> items = items(2);
        ClientBankingSession session = openWith(items);
        BankBoxSelection.handleClick(session, GRID, BankGridScroll.TOP, cellCentreX(1), cellCentreY(1));

        openWith(List.of(items.get(0)));

        assertNull(session.selectedStoredItem());
    }
}
