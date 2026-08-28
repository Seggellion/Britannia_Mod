package com.seggellion.britannia_mod.grabbyhands;

/** Why a placement ended the way it did. */
public enum GrabbyPlacementOutcome {
    /** The object is in the world and carries player provenance. */
    SUCCESS,

    /**
     * The block was placed, but provenance could not be stamped onto it.
     *
     * <p>The object therefore reads as {@link GrabbyProvenance#WORLD} — protected, and not pickable
     * by the player who just placed it. That is the deliberate fail-closed choice: Grabby Hands will
     * not mark something movable it could not verify, and it will not silently destroy a block the
     * player's own item just produced. Every enrolled type has a block entity, so reaching this state
     * means something is genuinely wrong and the warning in the log is the useful artefact.
     */
    PLACED_WITHOUT_PROVENANCE,

    /** The held item is not an enrolled Grabby type. The interaction is left entirely alone. */
    TYPE_NOT_ENROLLED,

    /**
     * The held item is enrolled, but this click is not the gesture it places on.
     *
     * <p>Distinct from every refusal below it, and the distinction is the point. Those are answers to
     * a placement the player asked for — the spot is protected, unsupported, obstructed, out of reach —
     * and consuming the click is right, because something was attempted and denied. This one means no
     * placement was ever asked for, so consuming the click would swallow an interaction the player
     * meant for the block instead: holding a crate and clicking the side of another crate is a request
     * to open it, not a failed attempt to stack it.
     *
     * <p>Like {@link #TYPE_NOT_ENROLLED}, it therefore falls through to the block's own behaviour.
     */
    NOT_A_PLACEMENT_GESTURE,

    /** Nothing could be placed against that hit — no replaceable destination. */
    NO_VALID_TARGET,

    /** The destination is out of reach. Server-side check. */
    OUT_OF_REACH,

    /** Region or house policy refused this actor at that destination. */
    DENIED_BY_POLICY,

    /**
     * The block's own placement rules refused: obstructed, unsupported, or an invalid footprint.
     *
     * <p>This is vanilla's answer, not ours — an invalid stacking attempt lands here because
     * {@code canSurvive} and {@code isUnobstructed} said no.
     */
    REFUSED_BY_BLOCK,

    /** Another transaction already owns that position. */
    ALREADY_IN_PROGRESS;

    /** Whether the interaction should be consumed rather than falling through to normal use. */
    public boolean handled() {
        return this != TYPE_NOT_ENROLLED && this != NOT_A_PLACEMENT_GESTURE;
    }

    public boolean placedSomething() {
        return this == SUCCESS || this == PLACED_WITHOUT_PROVENANCE;
    }
}
