package com.seggellion.britannia_mod.client.screen.bank;

import javax.annotation.Nullable;

import java.util.Objects;

/**
 * Milestone 12: the drag state machine -- every decision in the drag gesture, none of the drawing.
 *
 * <p>This is the milestone the playbook flags as the epic's highest risk, because the Bank Box is
 * a plain {@code Screen} with no vanilla {@code Slot} machinery, so the whole gesture is
 * hand-rolled. The risk was retired in two halves: hit testing and GUI-scale correctness live in
 * {@link BankGridGeometry}, exhaustively tested since Milestone 9 (coordinates arrive pre-scaled,
 * and the exclusive edges are what stop a drag straddling two grids); everything else -- the
 * threshold, the states, the cancellation rules, the one-handoff guarantee -- lives here, plain
 * and JUnit-tested (Architecture Decision 0). The screen translates mouse events into calls and
 * draws what the queries say; it decides nothing.
 *
 * <h2>The observable states</h2>
 * {@code IDLE → PRESSED_ON_SOURCE → DRAGGING_OVER_INVALID ⇄ DRAGGING_OVER_VALID → HANDOFF → IDLE},
 * with every cancellation path collapsing back to {@code IDLE}. Cancelled is an <b>outcome</b>,
 * not a resting state -- nothing observes "cancelled" a frame later; they observe idle.
 *
 * <h2>Decisions encoded here</h2>
 * <ul>
 *   <li><b>Ineligible stacks refuse the drag</b> (design §10.3 lists eligibility as a start
 *       condition; the playbook offered latitude, and refusing matches the shading -- a dimmed
 *       stack that will not lift is one message, a stack that lifts and then bounces is two).</li>
 *   <li><b>The valid target is the whole bank-grid region</b>, never a particular cell (design
 *       §10.5): the player requests "deposit this", not "place it at slot 14".</li>
 *   <li><b>Release before the threshold is a click, not a failed drag.</b> The threshold is
 *       {@value #DRAG_THRESHOLD} scaled pixels, Euclidean -- coordinates arrive already divided
 *       by GUI scale, so this is the same physical feel at every scale.</li>
 *   <li><b>Exactly one handoff per gesture.</b> A valid release arms {@link Phase#HANDOFF} and
 *       every further release returns {@link ReleaseOutcome#NONE} -- the machine-level half of
 *       "a duplicate mouse release must not send a duplicate request". Milestone 13 replaces the
 *       screen's immediate {@link #completeHandoff()} with the real packet-and-pending flow;
 *       the state exists now so 13 changes behaviour, not shape.</li>
 * </ul>
 *
 * <h2>What the source snapshot is</h2>
 * An opaque token the screen builds from the live source. The controller only ever compares it --
 * {@link #tick} cancels the drag the moment the source no longer holds what was pressed, which
 * covers "source slot becoming empty before request handoff" and every quieter mutation (a
 * hopper, a refresh) that swaps the source mid-gesture. Keeping it a string is what keeps this
 * class free of Minecraft types.
 *
 * <h2>Direction-agnostic since the drag-to-withdraw addendum</h2>
 * Built for one direction (pack → vault, Milestone 12), the machine itself never cared which way
 * the items flow -- only the drop region and the meaning of {@code sourceSlot} differ. The owner
 * commissioned drag-to-withdraw after the epic closed (2026-08-04, superseding design §3's
 * non-goal), so the region became the {@link DropRegion} seam and the Bank Box now runs two
 * instances of this one tested machine: pack cell → vault grid (deposit), and vault cell → the
 * pack's two grids (withdraw). Everything else -- threshold, watchdog, one-handoff rule,
 * Escape -- is shared by construction rather than duplicated.
 */
public final class BankDragController {

    /** Movement, in scaled pixels, that turns a press into a drag rather than a click. */
    public static final double DRAG_THRESHOLD = 4.0;

    /** Where a drag may end. The whole region is one target, never a particular cell (§10.5). */
    @FunctionalInterface
    public interface DropRegion {
        boolean contains(double mouseX, double mouseY);
    }

    /** The externally observable state, including hover validity while dragging. */
    public enum State {
        IDLE,
        PRESSED_ON_SOURCE,
        DRAGGING_OVER_INVALID,
        DRAGGING_OVER_VALID,
        HANDOFF
    }

    private enum Phase { IDLE, PRESSED, DRAGGING, HANDOFF }

    /** What a mouse release meant. Everything except {@link #NONE} ends a live gesture. */
    public enum ReleaseOutcome {
        /** Released before the threshold: an ordinary click, and the press is forgotten. */
        CLICK,
        /** Released over the drop region with a live, eligible source: the transfer gesture. */
        DROPPED_ON_TARGET,
        /** Released anywhere else while dragging: cancelled, nothing happens (design §10.6). */
        CANCELLED,
        /** No gesture was live -- a duplicate or stray release. Must never act. */
        NONE
    }

    private final DropRegion target;

    private Phase phase = Phase.IDLE;
    private int sourceSlot = -1;
    @Nullable
    private String sourceSnapshot;
    private double pressX;
    private double pressY;
    private double lastX;
    private double lastY;

    /**
     * @param target the drop region. A resize rebuilds the screen's layout and with it this
     *               controller, which is exactly the "cancel on resize/re-init" rule -- a fresh
     *               controller is idle by construction.
     */
    public BankDragController(DropRegion target) {
        this.target = Objects.requireNonNull(target, "target");
    }

    /** The original single-grid form, kept so Milestone 12's construction sites read unchanged. */
    public BankDragController(BankGridGeometry targetGrid) {
        this((DropRegion) Objects.requireNonNull(targetGrid, "targetGrid")::contains);
    }

    // ---------- Events ----------

    /**
     * A left press on a source cell -- a pack slot for a deposit drag, a vault cell for a
     * withdraw drag.
     *
     * @param slotIndex       the pressed source index (vanilla slot, or vault cell)
     * @param draggable       whether the pressed source may be dragged at all (for a deposit,
     *                        the {@link BankDepositHint} verdict; for a withdrawal, any stored row)
     * @param snapshot        opaque token for the live stack, for {@link #tick} to compare
     * @param mutationPending the session's lock -- no gesture may begin while anything is in
     *                        flight (design §10.3)
     * @return whether the press armed a potential drag (and should be consumed)
     */
    public boolean onPress(int slotIndex, boolean draggable, String snapshot,
                           double mouseX, double mouseY, boolean mutationPending) {
        if (phase != Phase.IDLE) return false;
        if (mutationPending) return false;
        if (!draggable) return false;
        if (slotIndex < 0) return false;

        phase = Phase.PRESSED;
        sourceSlot = slotIndex;
        sourceSnapshot = Objects.requireNonNull(snapshot, "snapshot");
        pressX = mouseX;
        pressY = mouseY;
        lastX = mouseX;
        lastY = mouseY;
        return true;
    }

    /** Cursor movement while the button is down. Crossing the threshold starts the drag. */
    public void onMove(double mouseX, double mouseY) {
        if (phase != Phase.PRESSED && phase != Phase.DRAGGING) return;
        lastX = mouseX;
        lastY = mouseY;
        if (phase == Phase.PRESSED) {
            double dx = mouseX - pressX;
            double dy = mouseY - pressY;
            if ((dx * dx) + (dy * dy) >= DRAG_THRESHOLD * DRAG_THRESHOLD) {
                phase = Phase.DRAGGING;
            }
        }
    }

    /** The button came up. See {@link ReleaseOutcome} for what the answer obliges. */
    public ReleaseOutcome onRelease(double mouseX, double mouseY) {
        switch (phase) {
            case PRESSED -> {
                reset();
                return ReleaseOutcome.CLICK;
            }
            case DRAGGING -> {
                lastX = mouseX;
                lastY = mouseY;
                if (target.contains(mouseX, mouseY)) {
                    phase = Phase.HANDOFF;
                    return ReleaseOutcome.DROPPED_ON_TARGET;
                }
                reset();
                return ReleaseOutcome.CANCELLED;
            }
            default -> {
                return ReleaseOutcome.NONE;
            }
        }
    }

    /**
     * Cancels a live gesture -- Escape, the screen closing, or anything else that must not turn
     * into a deposit. A {@link Phase#HANDOFF} is deliberately not cancellable from here: by then
     * the gesture is over, and what happens to the handoff belongs to the code that armed it.
     *
     * @return whether there was a live gesture to cancel (so Escape can be consumed by the drag
     *         rather than closing the screen)
     */
    public boolean cancel() {
        if (phase != Phase.PRESSED && phase != Phase.DRAGGING) return false;
        reset();
        return true;
    }

    /**
     * Called every frame with the current token for the source slot. A mismatch means the slot no
     * longer holds what was pressed -- emptied, shrunk, or swapped -- and the gesture dies on the
     * spot rather than depositing something the player never picked up.
     *
     * @return whether this tick cancelled the gesture
     */
    public boolean tick(@Nullable String currentSourceSnapshot) {
        if (phase != Phase.PRESSED && phase != Phase.DRAGGING) return false;
        if (Objects.equals(sourceSnapshot, currentSourceSnapshot)) return false;
        reset();
        return true;
    }

    /** Milestone 13's seam: the handoff is done (packet sent, or refused and dropped). */
    public void completeHandoff() {
        if (phase == Phase.HANDOFF) reset();
    }

    /**
     * Milestone 13: design §10.6's release-time re-check -- "verify the source slot still
     * contains a compatible live stack" -- as a question the armed handoff can answer.
     *
     * <p>The per-frame watchdog already killed any gesture whose source changed <i>while
     * dragging</i>, but the release itself is an instant the watchdog does not cover: the frame
     * between the last tick and the mouse-up. Only the code deciding whether to send should ask
     * this, and only in {@link Phase#HANDOFF}; anywhere else the answer is meaningless and
     * {@code false} refuses safely.
     */
    public boolean handoffSourceUnchanged(@Nullable String currentSourceSnapshot) {
        return phase == Phase.HANDOFF && Objects.equals(sourceSnapshot, currentSourceSnapshot);
    }

    // ---------- Queries ----------

    public State state() {
        return switch (phase) {
            case IDLE -> State.IDLE;
            case PRESSED -> State.PRESSED_ON_SOURCE;
            case DRAGGING -> target.contains(lastX, lastY)
                    ? State.DRAGGING_OVER_VALID
                    : State.DRAGGING_OVER_INVALID;
            case HANDOFF -> State.HANDOFF;
        };
    }

    /** True while the ghost stack should follow the cursor. */
    public boolean isDragging() {
        return phase == Phase.DRAGGING;
    }

    /** True while Escape belongs to the gesture rather than the screen. */
    public boolean isGestureLive() {
        return phase == Phase.PRESSED || phase == Phase.DRAGGING;
    }

    /** The pressed inventory slot, or -1 outside a gesture. */
    public int sourceSlot() {
        return phase == Phase.IDLE ? -1 : sourceSlot;
    }

    public double lastMouseX() {
        return lastX;
    }

    public double lastMouseY() {
        return lastY;
    }

    private void reset() {
        phase = Phase.IDLE;
        sourceSlot = -1;
        sourceSnapshot = null;
    }
}
