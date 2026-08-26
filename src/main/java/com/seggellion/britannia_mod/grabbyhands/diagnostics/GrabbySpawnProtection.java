package com.seggellion.britannia_mod.grabbyhands.diagnostics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla spawn protection: the one branch on the whole Grabby path that answers differently on a
 * dedicated server than it does in single player.
 *
 * <h2>Why this class exists</h2>
 *
 * <p>{@code ServerGamePacketListenerImpl.handleUseItemOn} calls
 * {@code ServerLevel.mayInteract(player, pos)} before it calls
 * {@code ServerPlayerGameMode.useItemOn}, and {@code mayInteract} delegates to
 * {@code MinecraftServer.isUnderSpawnProtection}. That method is a bare {@code return false} on
 * {@code MinecraftServer} — which is what an integrated single-player server and a
 * {@code GameTestServer} both are — and is overridden with a real check only on
 * {@code DedicatedServer}.
 *
 * <p>When it answers true the packet is dropped where it stands. {@code useItemOn} is never called,
 * so NeoForge never posts {@code PlayerInteractEvent.RightClickBlock}, so
 * {@code GrabbyInteractionHandler} never runs and cannot refuse, log, or say anything. The player
 * sees a feature that does nothing at all, and the server log is silent. That is precisely the shape
 * of "works in single player, dead on the real server", and no test this project can run is capable
 * of reproducing it: every GameTest runs on a {@code GameTestServer}, which inherits the
 * {@code return false}.
 *
 * <p>Nothing here changes the rule. Grabby Hands must not quietly switch vanilla protection off — a
 * server owner who set a spawn radius meant it. This is the diagnosis, so the failure can be seen
 * instead of guessed at, and so {@link GrabbyEnvironmentReport} can say it out loud at boot.
 */
public final class GrabbySpawnProtection {
    private GrabbySpawnProtection() {
    }

    /**
     * The rule itself, in primitives, exactly as {@code DedicatedServer.isUnderSpawnProtection}
     * evaluates it — so it can be unit-tested without a server of either kind.
     *
     * @param dedicatedServer          the server is a {@code DedicatedServer}; nothing else applies it
     * @param overworld                the position is in the Overworld; other dimensions are exempt
     * @param operatorListEmpty        the ops list is empty, which disables the rule entirely
     * @param playerIsOperator         the acting player is on the ops list and is therefore exempt
     * @param protectionRadius         {@code spawn-protection} from {@code server.properties}
     * @param chebyshevDistanceToSpawn {@link #chebyshevDistanceToSpawn}, on the X/Z plane only
     */
    public static boolean blocksInteraction(
            boolean dedicatedServer,
            boolean overworld,
            boolean operatorListEmpty,
            boolean playerIsOperator,
            int protectionRadius,
            int chebyshevDistanceToSpawn) {
        return dedicatedServer
                && overworld
                && !operatorListEmpty
                && !playerIsOperator
                && protectionRadius > 0
                && chebyshevDistanceToSpawn <= protectionRadius;
    }

    /** X/Z only, and the larger of the two axes, which is how vanilla measures the radius. */
    public static int chebyshevDistanceToSpawn(BlockPos pos, BlockPos spawn) {
        return Math.max(Math.abs(pos.getX() - spawn.getX()), Math.abs(pos.getZ() - spawn.getZ()));
    }

    /**
     * Whether this server is configured such that spawn protection can refuse anybody.
     *
     * <p>Position- and player-independent: it answers "is this hazard armed", which is the question
     * worth asking once at boot rather than once per click.
     */
    public static boolean armed(@Nullable MinecraftServer server) {
        return server instanceof DedicatedServer dedicated
                && dedicated.getSpawnProtectionRadius() > 0
                && !dedicated.getPlayerList().getOps().isEmpty();
    }

    /** {@code spawn-protection}, or {@code 0} when there is no server to ask. */
    public static int radius(@Nullable MinecraftServer server) {
        return server == null ? 0 : server.getSpawnProtectionRadius();
    }

    /**
     * The live answer for one player at one position, without going through {@code mayInteract} —
     * so a caller can tell spawn protection apart from the world border, which
     * {@code mayInteract} folds into the same boolean.
     */
    public static boolean blocksInteraction(
            @Nullable ServerLevel level, @Nullable Player player, @Nullable BlockPos pos) {
        if (level == null || player == null || pos == null) {
            return false;
        }
        MinecraftServer server = level.getServer();
        if (!(server instanceof DedicatedServer dedicated)) {
            return false;
        }
        return blocksInteraction(
                true,
                level.dimension() == Level.OVERWORLD,
                dedicated.getPlayerList().getOps().isEmpty(),
                dedicated.getPlayerList().isOp(player.getGameProfile()),
                dedicated.getSpawnProtectionRadius(),
                chebyshevDistanceToSpawn(pos, level.getSharedSpawnPos()));
    }
}
