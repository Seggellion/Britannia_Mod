package com.seggellion.britannia_mod.crate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Which crate each player is currently breaking.
 *
 * <h2>Why the target is captured, not recomputed</h2>
 *
 * <p>A player aims at a crate, holds the button, and the crate breaks a second later. In between, a
 * second player can remove a crate lower in the same column, and everything above it slides down. If
 * the destroyed crate were chosen at the end by looking at where the ray lands, the first player
 * would destroy whichever crate had moved into that height — somebody else's crate, with somebody
 * else's items in it. So the identity is taken when the swing starts and held until it finishes.
 *
 * <p>Entirely transient and server-side. Nothing here is written to the world, the column, or the
 * player; a target that outlives its swing is a bug, not a saved state, which is why every path that
 * ends a swing clears it.
 */
public final class CrateStackBreakTargets {

    /**
     * How long a captured target stays usable, in server ticks.
     *
     * <p>Long enough for any real break — a stone-tool player on a wooden crate is well under a
     * second — and short enough that a target abandoned without an abort cannot be waiting when the
     * same player starts breaking something else minutes later.
     */
    private static final long STALE_AFTER_TICKS = 200L;

    /**
     * How long after a completed break the same column refuses to start another, in ticks.
     *
     * <p>A held mouse button does not stop when a crate breaks: the client sees the block gone,
     * immediately begins destroying whatever it now aims at, and on a column that is the next crate
     * down. Four ticks is about a fifth of a second — under human reaction time, so deliberate
     * dismantling still feels continuous, but far longer than the single tick it takes a held button
     * to roll straight into the next crate.
     */
    private static final long CASCADE_GUARD_TICKS = 4L;

    private static final Map<UUID, CrateStackBreakTarget> TARGETS = new ConcurrentHashMap<>();
    private static final Map<UUID, Completion> COMPLETIONS = new ConcurrentHashMap<>();

    private CrateStackBreakTargets() {
    }

    /** A crate a player has started breaking. */
    public record CrateStackBreakTarget(BlockPos root, int crateId, BlockPos clickedCell, long startedAtTick) {
    }

    private record Completion(BlockPos root, long tick) {
    }

    /** Remembers what this player started breaking, replacing anything they were breaking before. */
    public static void capture(ServerPlayer player, BlockPos root, int crateId, BlockPos clickedCell) {
        TARGETS.put(player.getUUID(), new CrateStackBreakTarget(
                root.immutable(), crateId, clickedCell.immutable(), player.serverLevel().getGameTime()));
    }

    /**
     * What this player is breaking, if it is still theirs to break.
     *
     * <p>A target for a different column than the one being destroyed is not returned: it belongs to
     * a swing the player abandoned, and applying it here would destroy a crate they are no longer
     * even looking at.
     */
    public static Optional<CrateStackBreakTarget> current(ServerPlayer player, BlockPos root) {
        CrateStackBreakTarget target = TARGETS.get(player.getUUID());
        if (target == null || !target.root().equals(root)) {
            return Optional.empty();
        }
        if (player.serverLevel().getGameTime() - target.startedAtTick() > STALE_AFTER_TICKS) {
            TARGETS.remove(player.getUUID());
            return Optional.empty();
        }
        return Optional.of(target);
    }

    public static void clear(ServerPlayer player) {
        TARGETS.remove(player.getUUID());
    }

    public static void clear(UUID playerId) {
        TARGETS.remove(playerId);
        COMPLETIONS.remove(playerId);
    }

    /** Records that a break just finished, so a held button cannot roll straight into the next crate. */
    public static void recordCompletion(ServerPlayer player, BlockPos root) {
        COMPLETIONS.put(player.getUUID(),
                new Completion(root.immutable(), player.serverLevel().getGameTime()));
        TARGETS.remove(player.getUUID());
    }

    /**
     * Whether this player broke a crate out of this column a moment ago.
     *
     * <p>Checked when a swing starts rather than when it completes, so a refused start simply never
     * captures a target and the ordinary break gate does the rest — nothing has to be undone.
     */
    public static boolean withinCascadeGuard(ServerPlayer player, BlockPos root) {
        Completion completion = COMPLETIONS.get(player.getUUID());
        if (completion == null || !completion.root().equals(root)) {
            return false;
        }
        long elapsed = player.serverLevel().getGameTime() - completion.tick();
        if (elapsed > CASCADE_GUARD_TICKS) {
            COMPLETIONS.remove(player.getUUID());
            return false;
        }
        return true;
    }
}
