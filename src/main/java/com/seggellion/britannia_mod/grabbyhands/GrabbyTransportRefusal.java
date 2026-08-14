package com.seggellion.britannia_mod.grabbyhands;

/**
 * A reason an object gives for not being transportable right now.
 *
 * <p>These are the object's own rules, not the transport layer's. A container knows that somebody is
 * looking inside it and that it is holding another container; Grabby Hands does not, and should not
 * have to learn.
 */
public enum GrabbyTransportRefusal {
    /**
     * Somebody has this object open.
     *
     * <p>Removing a container out from under an open menu leaves a one-tick window in which a viewer
     * can move items into a block that no longer exists. Refusing is simpler to reason about than
     * racing the close, and it reads naturally in play: you cannot pocket a chest somebody is
     * rummaging through.
     */
    IN_USE,

    /**
     * This object holds another object that is itself carrying contents.
     *
     * <p>Without this, a chest inside a chest inside a chest is unbounded storage in a single
     * inventory slot. Vanilla applies the same rule to shulker boxes.
     */
    NESTED_CONTAINER
}
