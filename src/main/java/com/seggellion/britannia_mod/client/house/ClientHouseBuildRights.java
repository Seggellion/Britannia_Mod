package com.seggellion.britannia_mod.client.house;

/**
 * The client's copy of "the server has lent me the right to build here".
 *
 * <p>Read by {@code HouseBuildRightsAdventureModeMixin} so a house owner's left-click actually
 * leaves the client and reaches the server, which is the only place the decision is really made.
 * Deliberately a single boolean with no position, house identity or ownership in it: the client is
 * not being told who owns what, only that it should stop pre-refusing on the server's behalf.
 *
 * <p>Defaults to {@code false} and is cleared on disconnect, so a client that never hears from the
 * server behaves exactly as an unmodified Adventure-mode client does today.
 */
public final class ClientHouseBuildRights {
    private static volatile boolean granted;

    private ClientHouseBuildRights() {
    }

    public static void set(boolean value) {
        granted = value;
    }

    public static boolean granted() {
        return granted;
    }

    /** Called on disconnect. A stale grant must never survive into the next world. */
    public static void clear() {
        granted = false;
    }
}
