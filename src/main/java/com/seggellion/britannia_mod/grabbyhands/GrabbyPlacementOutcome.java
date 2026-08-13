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
        return this != TYPE_NOT_ENROLLED;
    }

    public boolean placedSomething() {
        return this == SUCCESS || this == PLACED_WITHOUT_PROVENANCE;
    }
}
