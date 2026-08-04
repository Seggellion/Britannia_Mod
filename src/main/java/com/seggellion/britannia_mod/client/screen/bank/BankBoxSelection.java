package com.seggellion.britannia_mod.client.screen.bank;

import java.util.List;
import java.util.Objects;

import com.seggellion.britannia_mod.service.banking.BankItemSummary;

/**
 * Milestone 11: what a left-click on the bank grid does.
 *
 * <p>Plain, so the whole click contract is JUnit-testable (Architecture Decision 0) -- the screen
 * translates a mouse event into one call here and obeys the answer. This is the interaction that
 * replaces the legacy 400 ms double-click: <b>one click selects</b>, and there is deliberately no
 * timer anywhere in it.
 *
 * <h2>The click semantics, stated once</h2>
 * <ul>
 *   <li>An occupied cell selects that item -- by public id, which the session then keeps correct
 *       across refreshes that reorder or remove rows.</li>
 *   <li>An empty cell (or one past the end of the contents) <b>clears</b> the selection --
 *       clicking empty space to deselect is the established inventory idiom, and design §9.3
 *       left the choice to the approved UX.</li>
 *   <li>Clicking the already-selected item keeps it selected. A toggle was considered and
 *       rejected: accidental double-clicks from players trained by the old interface would
 *       silently deselect, which is the worse surprise.</li>
 *   <li>While a mutation is pending nothing changes -- §9.3's "pending items cannot be selected
 *       again", applied to the whole grid for the same reason the mutation lock is global.</li>
 * </ul>
 */
public final class BankBoxSelection {

    /** What the click did. Everything except {@link #OUTSIDE} means the event was consumed. */
    public enum Result {
        /** An item became selected that was not before. */
        SELECTED,
        /** The selection was cleared by clicking empty space. */
        CLEARED,
        /** Inside the grid, but nothing changed -- e.g. re-clicking the selected item. */
        UNCHANGED,
        /** A mutation is pending; the grid is locked. */
        LOCKED,
        /** Not on the grid at all. The screen should pass the event on. */
        OUTSIDE
    }

    private BankBoxSelection() {
    }

    public static Result handleClick(
            ClientBankingSession session, BankGridGeometry grid, BankGridScroll scroll,
            double mouseX, double mouseY
    ) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(scroll, "scroll");

        Integer cell = grid.cellIndexAt(mouseX, mouseY);
        if (cell == null) return Result.OUTSIDE;
        if (session.isMutationPending()) return Result.LOCKED;

        List<BankItemSummary> items = session.bankItems();
        int itemIndex = scroll.itemIndexFor(cell, grid.columns(), items.size());
        if (itemIndex < 0) {
            boolean hadSelection = session.selectedStoredItem() != null;
            session.clearSelection();
            return hadSelection ? Result.CLEARED : Result.UNCHANGED;
        }

        boolean changed = session.selectStoredItem(items.get(itemIndex).publicId());
        return changed ? Result.SELECTED : Result.UNCHANGED;
    }
}
