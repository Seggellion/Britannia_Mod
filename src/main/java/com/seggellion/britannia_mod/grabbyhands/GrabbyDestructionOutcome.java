package com.seggellion.britannia_mod.grabbyhands;

/** Why an axe destruction ended the way it did. */
public enum GrabbyDestructionOutcome {
    /** The object is gone. Any contents it held have spilled exactly once. */
    SUCCESS(true),

    /** Nothing is at that position. */
    NOTHING_THERE(false),

    /** The block type is not in {@code britannia_mod:grabby_axe_destroyable}. */
    TYPE_NOT_ENROLLED(false),

    /**
     * The type is enrolled but this instance carries no player provenance.
     *
     * <p>Britannia scenery, structure-template furniture and admin decoration all land here. An axe
     * grants no authority over them, which is the whole point of keeping eligibility and instance
     * mobility separate.
     */
    NOT_GRABBY_MANAGED(false),

    /** The player is not holding a recognised axe. */
    NOT_AN_AXE(false),

    /** Region, house or reason policy refused this actor. */
    DENIED_BY_POLICY(false),

    /** The actor is too far away. Server-side check. */
    OUT_OF_REACH(false),

    /** The object refused for its own reasons — a container somebody has open, for instance. */
    REFUSED_BY_OBJECT(false),

    /**
     * The object is secured, and destroying it would bypass that security.
     *
     * <p>A locked chest. Deliberately treated as <em>not handled</em>, so the interaction falls
     * through to {@code LockpickingEventHandler} and a player can still pick the lock while holding
     * an axe. Consuming it here would make an axe in the hand silently disable lockpicking.
     */
    SECURED(false),

    /** The block could not be removed. Nothing changed. */
    REMOVAL_FAILED(false),

    /** Another transaction already owns this object. A pickup racing an axe resolves here. */
    ALREADY_IN_PROGRESS(false);

    private final boolean consumedObject;

    GrabbyDestructionOutcome(boolean consumedObject) {
        this.consumedObject = consumedObject;
    }

    public boolean consumedObject() {
        return consumedObject;
    }

    /**
     * Whether the interaction should be consumed rather than falling through to normal use.
     *
     * <p>A player who swung an axe at scenery should not find themselves sitting on it, so the axe
     * gesture is treated as handled even when it is refused — except when Grabby Hands does not own
     * the object at all, where the block's ordinary behaviour must still run.
     */
    public boolean handled() {
        return this != NOTHING_THERE && this != TYPE_NOT_ENROLLED && this != NOT_AN_AXE
                && this != SECURED;
    }
}
