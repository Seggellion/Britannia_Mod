package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.client.screen.bank.BankDragController.ReleaseOutcome;
import com.seggellion.britannia_mod.client.screen.bank.BankDragController.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drag-to-withdraw (owner addendum, 2026-08-04): the same machine {@link BankDragControllerTest}
 * proves, configured the way the Bank Box runs it in the other direction -- source is a vault
 * cell, and the drop region is the pack's TWO grids as one target. What is worth proving here is
 * exactly the configuration, not the machine again: the union region, the gap between the two
 * grids, and the identity-token watchdog the withdraw gesture uses instead of a stack snapshot.
 */
class BankWithdrawDragTest {

    /** The vault: a 9x2 grid at (100,100). */
    private static final BankGridGeometry VAULT = BankGridGeometry.of(100, 100, 9, 2);
    /** The pack's main grid at (100,300) and hotbar at (100,360) -- with a real gap between. */
    private static final BankGridGeometry INVENTORY = BankGridGeometry.of(100, 300, 9, 3);
    private static final BankGridGeometry HOTBAR = BankGridGeometry.of(100, 360, 9, 1);

    private static final String ITEM_ID = UUID.randomUUID().toString();
    /** A press inside the vault grid. */
    private static final double PRESS_X = 120;
    private static final double PRESS_Y = 110;

    private BankDragController controller;

    @BeforeEach
    void freshController() {
        // The exact shape BankBoxScreen builds: two rects, one DropRegion.
        controller = new BankDragController(
                (x, y) -> INVENTORY.contains(x, y) || HOTBAR.contains(x, y));
    }

    private void press() {
        assertTrue(controller.onPress(3, true, ITEM_ID, PRESS_X, PRESS_Y, false));
    }

    private void dragTo(double x, double y) {
        controller.onMove(x, y);
    }

    // ---------- The union drop region ----------

    @Test
    void droppingOnTheMainInventoryGridWithdraws() {
        press();
        dragTo(130, 320);
        assertEquals(State.DRAGGING_OVER_VALID, controller.state());
        assertEquals(ReleaseOutcome.DROPPED_ON_TARGET, controller.onRelease(130, 320));
    }

    @Test
    void droppingOnTheHotbarWithdrawsToo() {
        // The hotbar is part of the pack; a player dropping there means the same thing.
        press();
        dragTo(130, 365);
        assertEquals(State.DRAGGING_OVER_VALID, controller.state());
        assertEquals(ReleaseOutcome.DROPPED_ON_TARGET, controller.onRelease(130, 365));
    }

    @Test
    void theGapBetweenTheTwoGridsIsNotATarget() {
        // 300 + 3*18 = 354; the hotbar starts at 360. The seam between them must read as
        // invalid, not silently snap to either neighbour -- same exclusive-edge discipline the
        // grids themselves keep.
        press();
        dragTo(130, 357);
        assertEquals(State.DRAGGING_OVER_INVALID, controller.state());
        assertEquals(ReleaseOutcome.CANCELLED, controller.onRelease(130, 357));
    }

    @Test
    void droppingBackOnTheVaultCancelsRatherThanWithdraws() {
        // The source region is not the target region in this direction.
        press();
        dragTo(200, 120);
        assertEquals(State.DRAGGING_OVER_INVALID, controller.state());
        assertEquals(ReleaseOutcome.CANCELLED, controller.onRelease(200, 120));
    }

    // ---------- The identity watchdog ----------

    @Test
    void theRowVanishingMidDragKillsTheGesture() {
        // The screen's token is the row's public id while the session still holds it, and null
        // the moment a refresh removes it -- withdrawn or cashed from another client. The
        // machine's snapshot comparison does the rest.
        press();
        dragTo(130, 320);
        assertTrue(controller.tick(null), "a vanished row must cancel the gesture");
        assertEquals(State.IDLE, controller.state());
        assertEquals(ReleaseOutcome.NONE, controller.onRelease(130, 320));
    }

    @Test
    void theRowStillPresentKeepsTheGestureAlive() {
        press();
        dragTo(130, 320);
        assertFalse(controller.tick(ITEM_ID));
        assertEquals(State.DRAGGING_OVER_VALID, controller.state());
    }

    @Test
    void theReleaseFrameRecheckRefusesAVanishedRow() {
        // The frame between the last tick and the mouse-up: handoff armed, then the row is gone.
        press();
        dragTo(130, 320);
        assertEquals(ReleaseOutcome.DROPPED_ON_TARGET, controller.onRelease(130, 320));
        assertFalse(controller.handoffSourceUnchanged(null), "a vanished row must refuse the handoff");
        // The question does not consume the handoff -- asked again with the row present, it
        // still answers honestly, and only completeHandoff ends the state.
        assertTrue(controller.handoffSourceUnchanged(ITEM_ID));
        controller.completeHandoff();
        assertEquals(State.IDLE, controller.state());
    }

    // ---------- Locks and clicks ----------

    @Test
    void noGestureStartsWhileAMutationIsPending() {
        assertFalse(controller.onPress(3, true, ITEM_ID, PRESS_X, PRESS_Y, true));
        assertEquals(State.IDLE, controller.state());
    }

    @Test
    void aReleaseUnderTheThresholdStaysAClickSoSelectionIsTheWholeStory() {
        press();
        dragTo(PRESS_X + 2, PRESS_Y + 1);
        assertEquals(ReleaseOutcome.CLICK, controller.onRelease(PRESS_X + 2, PRESS_Y + 1));
        assertEquals(State.IDLE, controller.state());
    }
}
