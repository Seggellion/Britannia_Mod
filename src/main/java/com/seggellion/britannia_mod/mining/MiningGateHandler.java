package com.seggellion.britannia_mod.mining;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The server-authoritative Mining break gate (milestone 3).
 *
 * <p>Listens to {@link BlockEvent.BreakEvent} — the earliest cancelable server-side point before
 * any mutation (discovery report §1.5) — at {@link EventPriority#HIGH} for one load-bearing
 * reason: {@code CustomBlockBreakHandler} mutates the world <em>inside</em> its NORMAL-priority
 * listener, so a denial must land before NORMAL runs at all. Cancelling here also suppresses that
 * handler and the vanilla break path together (neither receives cancelled events), which closes
 * the vanilla-tool route that could previously destroy managed ore blocks droplessly.
 *
 * <p>Deliberately not wired to commands, worldgen, restoration, explosions or pistons: none of
 * those fire {@code BreakEvent}, so they cannot traverse the player gate by construction.
 *
 * <p>No Mining skill is awarded anywhere on this path — awards are milestone 4.
 */
public class MiningGateHandler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        Player player = event.getPlayer();
        MiningBreakGate.Evaluation evaluation =
                MiningBreakGate.evaluate(player, event.getState(), serverLevel, event.getPos());
        if (evaluation.permitsBreak()) {
            return;
        }
        event.setCanceled(true);
        MiningBreakGate.sendDenialFeedback(player, evaluation);
        MiningBreakGate.synchronizeDeniedBreak(player, serverLevel, event.getPos());
    }

    /**
     * Clears the player-placed marker once a break has actually been allowed to happen, so the
     * provenance set tracks standing construction instead of growing forever.
     *
     * <p>Deliberately {@link EventPriority#LOWEST} and a separate listener: every reader — the gate
     * above, the managed flow, and the skill award — must still see the marker while they decide.
     * Forgetting it in the HIGH-priority gate would erase the evidence before the managed flow
     * consulted it, which would hand the place-break loop straight back. Cancelled events do not
     * reach this listener, so a denied break leaves the marker untouched.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            MiningProvenance.forget(serverLevel, event.getPos());
        }
    }
}
