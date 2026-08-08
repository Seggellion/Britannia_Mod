package com.seggellion.britannia_mod.client.screen.bank;

/**
 * Milestone 17 gate corrective: detects the double-click that cashes a cheque from the Bank
 * Box's pack grid.
 *
 * <p>The owner's ADR-016 override split intent in two: dragging a cheque to the vault stores
 * it, and cashing it is this deliberate second gesture. The epic removed the legacy screen's
 * 400ms double-click as a <em>selection</em> mechanism (design §2); it returns here for one
 * narrow job -- an explicit, destructive-ish action on an inventory cheque -- and keeps the
 * same 400ms window players already knew.
 *
 * <p>Plain state with an injected clock value (Architecture Decision 0): the screen feeds it
 * mouse-presses on pack cells, and it answers whether this press completed a double-click.
 * It knows nothing about items -- the screen only consults it for cells that hold a cheque,
 * and the server re-validates the slot regardless.
 */
public final class BankChequeDoubleClick {

    /** The legacy Bank Screen's own double-click window, kept for familiarity. */
    public static final long WINDOW_MILLIS = 400;

    private int lastSlot = -1;
    private long lastAtMillis;

    /**
     * Registers a press on inventory slot {@code slot}.
     *
     * @return true when this press is the second on the same slot within {@link #WINDOW_MILLIS}
     *         -- a completed double-click. The tracker resets after firing, so a triple-click
     *         is one double-click plus a fresh first press, never two.
     */
    public boolean register(int slot, long nowMillis) {
        if (slot >= 0 && slot == lastSlot && nowMillis - lastAtMillis <= WINDOW_MILLIS) {
            reset();
            return true;
        }
        lastSlot = slot;
        lastAtMillis = nowMillis;
        return false;
    }

    /**
     * Forgets the pending first press. Called when the gesture stops being a click -- a drag
     * passing its movement threshold -- so returning the mouse to the same cell and pressing
     * again reads as a first press, not the tail of a double-click.
     */
    public void reset() {
        lastSlot = -1;
    }
}
