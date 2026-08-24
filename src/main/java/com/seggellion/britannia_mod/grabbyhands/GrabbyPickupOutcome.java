package com.seggellion.britannia_mod.grabbyhands;

/**
 * Why a pickup ended the way it did.
 *
 * <p>Split finely on purpose: tests assert the exact refusal, and the interaction handler can decide
 * whether to consume the interaction or let the object's normal use proceed.
 */
public enum GrabbyPickupOutcome {
    /** The object is in the player's inventory. Both audio stages fired. */
    SUCCESS(true),

    /**
     * The object left the world but inventory insertion failed unexpectedly, so it was dropped at the
     * player's feet instead. Nothing was lost and nothing was duplicated, but the stow cue did not
     * fire because nothing was stowed.
     */
    DROPPED_AT_FEET(true),

    /** Nothing is at that position. */
    NOTHING_THERE(false),

    /** The block type is not enrolled in {@code britannia_mod:grabby_movable}. */
    TYPE_NOT_ENROLLED(false),

    /**
     * The type is enrolled but this exact instance carries no player provenance — Britannia scenery,
     * structure-template furniture, or admin decoration. The protected default.
     */
    NOT_GRABBY_MANAGED(false),

    /** Region, house, or reason policy refused this actor. */
    DENIED_BY_POLICY(false),

    /** The actor is too far away. Server-side check; the client's claim is not trusted. */
    OUT_OF_REACH(false),

    /** Nothing could represent this object as an item. */
    NO_PORTABLE_FORM(false),

    /**
     * Somebody has this object open. Nothing was touched.
     *
     * <p>Refusing beats racing an open menu: removing a container out from under a viewer leaves a
     * window in which they can move items into a block that no longer exists.
     */
    IN_USE(false),

    /**
     * This object is holding another object that itself has contents.
     *
     * <p>Allowing it would make a single inventory slot hold unbounded storage.
     */
    NESTED_CONTAINER(false),

    /** The inventory is full. The world object is untouched and no audio fired. */
    INVENTORY_FULL(false),

    /** The block could not be removed. The world object is untouched and no audio fired. */
    REMOVAL_FAILED(false),

    /** Another transaction already owns this object. The loser of a race, or a replayed packet. */
    ALREADY_IN_PROGRESS(false);

    private final boolean consumedObject;

    GrabbyPickupOutcome(boolean consumedObject) {
        this.consumedObject = consumedObject;
    }

    /** Whether the world object was removed. Exactly the outcomes that mutated the world. */
    public boolean consumedObject() {
        return consumedObject;
    }

    /** Whether the interaction should be treated as handled rather than falling through to normal use. */
    public boolean handled() {
        return this != NOTHING_THERE && this != TYPE_NOT_ENROLLED && this != NOT_GRABBY_MANAGED;
    }

    /**
     * The translation key explaining this refusal to the player, or {@code null} when there is
     * nothing to say.
     *
     * <p>Every refusal used to be silent. That is correct for the three fall-through outcomes --
     * the object simply is not Grabby content and its own behaviour runs instead -- but for the
     * rest it meant a player whose pickup was refused for a reason they could act on saw exactly
     * what a broken feature looks like: nothing at all. The reports that Grabby Hands "does
     * nothing" are indistinguishable from these, which is why they now speak.
     */
    public String refusalMessageKey() {
        return switch (this) {
            case SUCCESS, DROPPED_AT_FEET, NOTHING_THERE, TYPE_NOT_ENROLLED, NOT_GRABBY_MANAGED -> null;
            case DENIED_BY_POLICY -> "message.britannia_mod.grabby.pickup.denied";
            case OUT_OF_REACH -> "message.britannia_mod.grabby.pickup.out_of_reach";
            case NO_PORTABLE_FORM -> "message.britannia_mod.grabby.pickup.no_portable_form";
            case IN_USE -> "message.britannia_mod.grabby.pickup.in_use";
            case NESTED_CONTAINER -> "message.britannia_mod.grabby.pickup.nested_container";
            case INVENTORY_FULL -> "message.britannia_mod.grabby.pickup.inventory_full";
            case REMOVAL_FAILED, ALREADY_IN_PROGRESS -> "message.britannia_mod.grabby.pickup.busy";
        };
    }
}
