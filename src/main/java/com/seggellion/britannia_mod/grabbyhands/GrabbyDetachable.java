package com.seggellion.britannia_mod.grabbyhands;

/**
 * A block entity that would drop something from its own removal path.
 *
 * <p>Two very different objects need this for the same reason. The placed-item host drops its single
 * payload from {@code onRemove} so an explosion cannot make a player's item vanish. A container spills
 * its whole inventory from {@code onRemove} for the same reason. Both are correct behaviour when
 * something <em>else</em> destroys the object — and both would hand the player their belongings twice
 * during a Grabby pickup, once into the inventory and once onto the floor.
 *
 * <p>So the transaction asks the object to give up what it is holding immediately before removal, once
 * every check has already passed and the state has already been captured.
 */
public interface GrabbyDetachable {
    /**
     * Gives up whatever this object's own removal path would otherwise drop.
     *
     * @return {@code true} if something was actually given up
     */
    boolean detachForTransport();
}
