package com.seggellion.britannia_mod.grabbyhands.destruction;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.grabbyhands.GrabbyActor;
import com.seggellion.britannia_mod.grabbyhands.GrabbyDestructionOutcome;
import com.seggellion.britannia_mod.grabbyhands.GrabbyDestructionResult;
import com.seggellion.britannia_mod.grabbyhands.GrabbyDestructionTransaction;
import com.seggellion.britannia_mod.grabbyhands.GrabbyWorld;
import com.seggellion.britannia_mod.network.ClientNetworkDispatch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * The two halves of R-2.11: ask the question, then honour the answer.
 *
 * <h2>Confirmation is an accident guard, not an authorisation step</h2>
 *
 * <p>The order is fixed and matters: <b>policy first, prompt second, re-validate, then act.</b> A
 * protected object produces no prompt at all — the player is never offered a choice they are not
 * permitted to make — and answering yes can never destroy something the pre-check would have refused.
 *
 * <h2>The client is told nothing it could lie with</h2>
 *
 * <p>The prompt carries a session id and display text. The confirmation carries a session id and
 * nothing else — no position, no block, no target identity. Everything is re-derived server-side from
 * the session and re-checked, so a forged, replayed, stale or out-of-order confirmation is rejected.
 * Same discipline as {@code dye.preview}, whose payload documents itself as "player intent only".
 */
public final class GrabbyDestructionService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private GrabbyDestructionService() {
    }

    /**
     * Asks the player to confirm, if and only if the destruction would actually be permitted.
     *
     * @return empty if a prompt was issued, otherwise the reason no question was asked
     */
    public static Optional<GrabbyDestructionOutcome> requestConfirmation(
            ServerLevel level, ServerPlayer player, BlockPos pos) {
        GrabbyWorld world = GrabbyWorld.of(level);
        GrabbyActor actor = GrabbyActor.of(player);
        ItemStack tool = player.getMainHandItem();

        Optional<GrabbyDestructionOutcome> refusal =
                GrabbyDestructionTransaction.refusalFor(world, actor, tool, pos);
        if (refusal.isPresent()) {
            // The caller decides whether to consume the interaction. Some refusals - a locked chest -
            // must fall through so the system that owns that rule still gets its turn.
            return refusal;
        }

        BlockState state = world.blockState(pos);
        GrabbyDestructionSession session = GrabbyDestructionSessions.issue(
                player.getUUID(), pos, state, tool, System.currentTimeMillis());

        ClientNetworkDispatch.openGrabbyDestructionPrompt(
                player,
                session.sessionId(),
                state.getBlock().getName().getString(),
                world.occupiedSlotCount(pos));
        return Optional.empty();
    }

    /**
     * Honours a confirmation, after re-establishing every fact it was issued against.
     *
     * @return the destruction result, or empty if the confirmation itself was not acceptable
     */
    public static Optional<GrabbyDestructionResult> confirm(ServerPlayer player, UUID sessionId) {
        Optional<GrabbyDestructionSession> found = GrabbyDestructionSessions.consume(
                player.getUUID(), sessionId, System.currentTimeMillis());
        if (found.isEmpty()) {
            // Unknown, expired, already answered, or somebody else's. All indistinguishable and all
            // equally ignorable.
            return Optional.empty();
        }
        GrabbyDestructionSession session = found.get();
        ServerLevel level = player.serverLevel();
        GrabbyWorld world = GrabbyWorld.of(level);

        if (!session.stateUnchanged(world.blockState(session.position()))) {
            LOGGER.debug("[grabby-hands] Destruction confirmation ignored; the block at {} changed",
                    session.position());
            return Optional.empty();
        }
        if (!session.toolUnchanged(player.getMainHandItem())) {
            LOGGER.debug("[grabby-hands] Destruction confirmation ignored; the axe changed");
            return Optional.empty();
        }

        // The transaction re-runs every policy, provenance and reach check of its own accord.
        return Optional.of(GrabbyDestructionTransaction.execute(
                world, GrabbyActor.of(player), player.getMainHandItem(), session.position()));
    }
}
