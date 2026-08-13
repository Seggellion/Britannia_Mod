package com.seggellion.britannia_mod.grabbyhands;

/**
 * Why a placed Grabby object is about to change.
 *
 * <p>Policy is keyed on the reason, not on identity — the same shape
 * {@code farming.FlowerMutationReason} uses. That is what keeps "who may move this" from quietly
 * turning into "who may use this": there is no {@code USE} constant here, and there is no code path
 * that would accept one.
 */
public enum GrabbyMutationReason {
    /** Player picking the object up into inventory. */
    PICKUP,

    /** Player destroying the object with a recognized axe, after confirming (R-2.11). */
    AXE_DESTROY,

    /** Blast damage reaching the object. */
    EXPLOSION,

    /** Fluid flowing into the object's position. */
    FLUID,

    /** A piston attempting to push or pull the object. */
    PISTON,

    /** Staff removing an object deliberately. */
    ADMIN_REMOVE,

    /** The mod itself mutating state as part of an already-authorized operation. */
    SYSTEM_MUTATION;

    /** Reasons the mod authorizes itself; they bypass the ordinary actor checks. */
    public boolean systemAuthorized() {
        return this == SYSTEM_MUTATION;
    }

    /** Reasons driven by the environment rather than by a deliberate player action. */
    public boolean environmental() {
        return this == EXPLOSION || this == FLUID || this == PISTON;
    }
}
