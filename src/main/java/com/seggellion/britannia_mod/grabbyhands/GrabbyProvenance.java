package com.seggellion.britannia_mod.grabbyhands;

/**
 * How a particular placed world object came to exist.
 *
 * <p>This is the exact-instance half of the eligibility/mobility split. The block tag
 * {@code britannia_mod:grabby_movable} answers "may this <em>type</em> participate in Grabby Hands";
 * this enum answers "may this <em>one placed block</em> be moved". Both must agree before anything
 * is picked up or destroyed.
 *
 * <p>{@link #WORLD} is the decode-time default whenever no Grabby state is present, which is what
 * makes every pre-existing block in every existing save protected without any migration pass. Only
 * the Grabby Hands placement transaction ever writes {@link #PLAYER}.
 */
public enum GrabbyProvenance {
    /**
     * Britannia scenery, structure-template contents, admin decoration, and anything placed by
     * ordinary Creative building. Immovable and indestructible through Grabby Hands by default.
     */
    WORLD,

    /** Placed by a player through the Grabby Hands placement transaction. Movable, subject to policy. */
    PLAYER;

    /**
     * Legacy-tolerant lookup. An unrecognised name resolves to the protected {@link #WORLD} default
     * rather than throwing, so a malformed or future-versioned tag can never make an object movable.
     */
    public static GrabbyProvenance byNameOrWorld(String name) {
        if (name == null) {
            return WORLD;
        }
        for (GrabbyProvenance value : values()) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        return WORLD;
    }

    public boolean playerPlaced() {
        return this == PLAYER;
    }
}
