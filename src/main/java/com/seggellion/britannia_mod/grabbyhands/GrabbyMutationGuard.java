package com.seggellion.britannia_mod.grabbyhands;

import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Ensures one object is only ever inside one Grabby transaction at a time.
 *
 * <p>This is what makes "two players race to pick up the same chair" resolve to exactly one winner,
 * and what stops a duplicated or replayed interaction packet running the transaction twice. The
 * loser is refused outright rather than queued: a second pickup of the same object is never a
 * legitimate outcome.
 *
 * <p>Keyed on {@code (level identity, position)}. Grabby objects occupy a single position, so unlike
 * a multi-block system this needs no anchor resolution to key correctly.
 *
 * <p>Claims are strictly scoped to one transaction via {@link #claim(Object, BlockPos)} in a
 * try-with-resources block, so an exception mid-transaction cannot strand a permanent lock.
 */
public final class GrabbyMutationGuard {
    private static final Set<Key> IN_PROGRESS = new HashSet<>();

    private GrabbyMutationGuard() {
    }

    /**
     * Attempts to take exclusive ownership of an object.
     *
     * @return a claim that is {@link Claim#held()} only for the winner; always close it
     */
    public static Claim claim(Object levelIdentity, BlockPos pos) {
        Key key = new Key(levelIdentity, pos);
        synchronized (GrabbyMutationGuard.class) {
            if (IN_PROGRESS.contains(key)) {
                return new Claim(null);
            }
            IN_PROGRESS.add(key);
            return new Claim(key);
        }
    }

    /** Test seam: forget every outstanding claim. */
    static void reset() {
        synchronized (GrabbyMutationGuard.class) {
            IN_PROGRESS.clear();
        }
    }

    static int outstandingClaims() {
        synchronized (GrabbyMutationGuard.class) {
            return IN_PROGRESS.size();
        }
    }

    /** An exclusive hold on one object, released on {@link #close()}. */
    public static final class Claim implements AutoCloseable {
        private final Key key;
        private boolean released;

        private Claim(Key key) {
            this.key = key;
        }

        public boolean held() {
            return key != null;
        }

        @Override
        public void close() {
            if (key == null || released) {
                return;
            }
            released = true;
            synchronized (GrabbyMutationGuard.class) {
                IN_PROGRESS.remove(key);
            }
        }
    }

    private record Key(Object levelIdentity, BlockPos pos) {
        private Key {
            Objects.requireNonNull(levelIdentity, "levelIdentity");
            pos = Objects.requireNonNull(pos, "pos").immutable();
        }

        @Override
        public boolean equals(Object other) {
            // Level identity is compared by reference on purpose: two ServerLevels are the same level
            // only if they are the same object.
            return other instanceof Key key
                    && levelIdentity == key.levelIdentity
                    && pos.equals(key.pos);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(levelIdentity) + pos.hashCode();
        }
    }
}
