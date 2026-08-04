package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.client.screen.bank.BankDragController.ReleaseOutcome;
import com.seggellion.britannia_mod.client.screen.bank.BankDragController.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 12: the drag state machine, exercised through every transition the playbook names --
 * threshold, source identification, valid and invalid hover, release, all cancellation paths, and
 * duplicate mouse events. No client is involved anywhere; that is the point of the extraction.
 */
class BankDragControllerTest {

    /** The drop target: a 9x3 bank grid at (100,100), so 100..262 x 100..154. */
    private static final BankGridGeometry BANK = BankGridGeometry.of(100, 100, 9, 3);

    /** A press location comfortably outside the bank grid -- "in the player's inventory". */
    private static final double PRESS_X = 120;
    private static final double PRESS_Y = 300;

    private static final String SNAPSHOT = "minecraft:diamond x5";

    private BankDragController controller;

    @BeforeEach
    void freshController() {
        controller = new BankDragController(BANK);
    }

    private void press() {
        assertTrue(controller.onPress(12, true, SNAPSHOT, PRESS_X, PRESS_Y, false));
    }

    private void dragTo(double x, double y) {
        controller.onMove(x, y);
    }

    // ---------- Starting ----------

    @Test
    void aPressOnADepositableStackArmsTheGesture() {
        press();
        assertEquals(State.PRESSED_ON_SOURCE, controller.state());
        assertEquals(12, controller.sourceSlot());
    }

    @Test
    void anIneligibleStackRefusesTheDrag() {
        // Design §10.3 lists eligibility as a start condition. The stack is already dimmed; a
        // stack that lifts and then bounces would be a second, worse message.
        assertFalse(controller.onPress(12, false, SNAPSHOT, PRESS_X, PRESS_Y, false));
        assertEquals(State.IDLE, controller.state());
    }

    @Test
    void noGestureMayBeginWhileAMutationIsPending() {
        assertFalse(controller.onPress(12, true, SNAPSHOT, PRESS_X, PRESS_Y, true));
        assertEquals(State.IDLE, controller.state());
    }

    @Test
    void aSecondPressDuringALiveGestureIsIgnored() {
        press();
        assertFalse(controller.onPress(20, true, "minecraft:stick x1", PRESS_X, PRESS_Y, false));
        assertEquals(12, controller.sourceSlot(), "the original source must survive a duplicate press");
    }

    // ---------- The threshold ----------

    @Test
    void movementUnderTheThresholdStaysAPress() {
        press();
        dragTo(PRESS_X + 2, PRESS_Y + 2); // ~2.8px, under 4
        assertEquals(State.PRESSED_ON_SOURCE, controller.state());
    }

    @Test
    void crossingTheThresholdStartsTheDrag() {
        press();
        dragTo(PRESS_X + 4, PRESS_Y); // exactly 4
        assertTrue(controller.isDragging());
    }

    @Test
    void releaseBeforeTheThresholdIsAClickNotAFailedDrag() {
        press();
        dragTo(PRESS_X + 1, PRESS_Y);
        assertEquals(ReleaseOutcome.CLICK, controller.onRelease(PRESS_X + 1, PRESS_Y));
        assertEquals(State.IDLE, controller.state());
    }

    // ---------- Hover validity ----------

    @Test
    void draggingOutsideTheBankGridHoversInvalid() {
        press();
        dragTo(PRESS_X + 40, PRESS_Y); // still below the grid
        assertEquals(State.DRAGGING_OVER_INVALID, controller.state());
    }

    @Test
    void draggingOverTheBankGridHoversValid() {
        press();
        dragTo(150, 120); // inside 100..262 x 100..154
        assertEquals(State.DRAGGING_OVER_VALID, controller.state());
    }

    @Test
    void hoverValidityFollowsTheCursorBothWays() {
        press();
        dragTo(150, 120);
        assertEquals(State.DRAGGING_OVER_VALID, controller.state());
        dragTo(150, 200);
        assertEquals(State.DRAGGING_OVER_INVALID, controller.state());
        dragTo(150, 120);
        assertEquals(State.DRAGGING_OVER_VALID, controller.state());
    }

    @Test
    void theGridsExclusiveEdgeIsNotAValidTarget() {
        // The same exclusive-edge property the geometry suite pins, observed through the drag:
        // the bank grid's bottom boundary belongs to whatever is below it, not to the vault.
        press();
        dragTo(150, BANK.bottom());
        assertEquals(State.DRAGGING_OVER_INVALID, controller.state());
    }

    // ---------- Release ----------

    @Test
    void releaseOverTheBankGridIsTheDepositGestureAndArmsExactlyOneHandoff() {
        press();
        dragTo(150, 120);
        assertEquals(ReleaseOutcome.DROPPED_ON_BANK, controller.onRelease(150, 120));
        assertEquals(State.HANDOFF, controller.state());
        assertEquals(12, controller.sourceSlot(), "the handoff must still know its source");
    }

    @Test
    void releaseAnywhereElseCancelsAndNothingHappens() {
        press();
        dragTo(150, 200);
        assertEquals(ReleaseOutcome.CANCELLED, controller.onRelease(150, 200));
        assertEquals(State.IDLE, controller.state());
    }

    @Test
    void aDuplicateReleaseAfterTheHandoffIsInertNoise() {
        // The machine half of "a duplicate mouse release must not send a duplicate request":
        // only one DROPPED_ON_BANK can come out of one gesture, ever.
        press();
        dragTo(150, 120);
        controller.onRelease(150, 120);
        assertEquals(ReleaseOutcome.NONE, controller.onRelease(150, 120));
        assertEquals(ReleaseOutcome.NONE, controller.onRelease(150, 120));
        assertEquals(State.HANDOFF, controller.state(), "and the armed handoff is untouched by the noise");
    }

    @Test
    void aStrayReleaseWithNoGestureIsIgnored() {
        assertEquals(ReleaseOutcome.NONE, controller.onRelease(150, 120));
    }

    @Test
    void completingTheHandoffReturnsToIdleReadyForTheNextGesture() {
        press();
        dragTo(150, 120);
        controller.onRelease(150, 120);
        controller.completeHandoff();
        assertEquals(State.IDLE, controller.state());
        assertTrue(controller.onPress(3, true, "minecraft:stick x2", PRESS_X, PRESS_Y, false),
                "a new gesture must be possible immediately after");
    }

    // ---------- Cancellation ----------

    @Test
    void escapeCancelsALiveDragAndReportsItConsumedTheKey() {
        press();
        dragTo(150, 120);
        assertTrue(controller.cancel(), "the drag owns this Escape; the screen must not close");
        assertEquals(State.IDLE, controller.state());
    }

    @Test
    void escapeWithNoGestureBelongsToTheScreen() {
        assertFalse(controller.cancel());
    }

    @Test
    void escapeDoesNotCancelAnArmedHandoff() {
        // By handoff the gesture is over; what happens to it belongs to the code that armed it
        // (Milestone 13's packet-and-pending flow). Cancelling here could race the send.
        press();
        dragTo(150, 120);
        controller.onRelease(150, 120);
        assertFalse(controller.cancel());
        assertEquals(State.HANDOFF, controller.state());
    }

    @Test
    void theSourceChangingUnderTheGestureCancelsIt() {
        press();
        dragTo(150, 120);
        assertTrue(controller.tick("minecraft:diamond x3"),
                "three where five were pressed is not what the player picked up");
        assertEquals(State.IDLE, controller.state());
    }

    @Test
    void theSourceEmptyingCancelsIt() {
        press();
        dragTo(150, 120);
        assertTrue(controller.tick(null));
        assertEquals(State.IDLE, controller.state());
    }

    @Test
    void anUnchangedSourceTicksQuietly() {
        press();
        dragTo(150, 120);
        assertFalse(controller.tick(SNAPSHOT));
        assertEquals(State.DRAGGING_OVER_VALID, controller.state());
    }

    @Test
    void aFreshControllerAfterResizeIsIdleByConstruction() {
        // Resize re-inits the screen, which rebuilds layout and controller together -- the
        // "cancel on resize/re-init" rule falls out of construction rather than being a case.
        press();
        dragTo(150, 120);
        BankDragController rebuilt = new BankDragController(BANK.movedTo(50, 50));
        assertEquals(State.IDLE, rebuilt.state());
        assertEquals(-1, rebuilt.sourceSlot());
    }
}
