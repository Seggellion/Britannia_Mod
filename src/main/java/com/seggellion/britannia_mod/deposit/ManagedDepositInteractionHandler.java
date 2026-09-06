package com.seggellion.britannia_mod.deposit;

import com.seggellion.britannia_mod.mining.MiningProvenance;
import com.seggellion.britannia_mod.resource.extraction.ManagedExtractionPolicy;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The two moments a player meets a deposit — the first swing, and the completed break — and what
 * each is allowed to decide.
 *
 * <h2>Left click: a question, never an extraction</h2>
 * This used to be the extraction: adventure mode left the beds inert, so the first click cancelled
 * the event and called the service directly, the way an oyster or a flower is harvested. That gave
 * a deposit none of Minecraft's breaking lifecycle — no destroy progress, no duration, no
 * {@code BreakEvent} — and therefore none of the gates that hang off it: the click "broke" the bed
 * instantly, and because nothing on the path asked about Mining, a digger at 0.0 emptied a bed
 * with a requirement they had never met.
 *
 * <p>Now the first swing only <em>asks</em>: {@link ManagedDepositExtraction#preflight} runs the
 * full refusal chain — actor, house, tool, Mining — without writing anything. A refusal cancels
 * the event, so the client never begins digging and the digger hears why at the first click. An
 * eligible digger's event is left alone, and the ordinary vanilla dig proceeds.
 *
 * <p>Adventure can proceed because the authorized tool carries a {@code minecraft:can_break}
 * predicate for exactly the deposit blocks
 * ({@link com.seggellion.britannia_mod.resource.extraction.ExtractionToolPredicates}) —
 * vanilla's own adventure CanDestroy semantics, on both sides of the wire. Note what is still
 * <em>not</em> done: nothing grants {@code mayBuild}, changes a game mode, or relaxes adventure
 * generally. The deposit is an authorized exception inside adventure, scoped to two authored
 * block types, rather than a hole in it.
 *
 * <h2>Break: the one extraction, at the end of a real dig</h2>
 * The completed break — adventure or survival, they now share one lifecycle — is taken over
 * rather than allowed: the event is cancelled and the same service commits the one accounted
 * extraction, which re-runs the refusal chain before it writes. The server's own destroy-progress
 * bookkeeping is what fired the event, so a client that spams clicks or forges an early
 * {@code STOP_DESTROY_BLOCK} gains nothing: vanilla refuses the premature completion, and even a
 * completion it accepted would still meet the skill gate here. One {@code BreakEvent} per broken
 * block is what makes the yield exactly-once.
 *
 * <p>A bed the player placed themselves is construction, not a deposit (Mining milestone 7): both
 * listeners step aside on provenance, so dismantling your own placed block follows vanilla rules
 * and mints nothing.
 *
 * <p>A creative player who is not attacking with the Britannia pickaxe is administering, not
 * digging, and the break listener stands aside for them the same way every managed path does
 * ({@link ManagedExtractionPolicy#bypassesManagedExtraction}): the bed is removed as an ordinary
 * creative break, with no yield, no debt and no message. A creative player attacking with the
 * Britannia pickaxe is a tester and is taken through the real refusal chain, where the pickaxe is
 * the wrong tool for a bed and is told so. The block has no loot table, so a cancelled break
 * cannot drop anything either way.
 *
 * <p>{@link EventPriority#HIGH} for the same reason the Mining gate uses it: it must land before
 * {@code CustomBlockBreakHandler}, which mutates the world inside its NORMAL-priority listener.
 * {@code MiningGateHandler} registers first at the same priority, so an under-skilled break is
 * usually cancelled there; the extraction's own gate makes the outcome identical either way.
 */
public final class ManagedDepositInteractionHandler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)
                || player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE
                || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        if (MiningProvenance.isPlayerPlaced(level, event.getPos())) {
            return;
        }
        ManagedDepositExtraction.Result result = ManagedDepositExtraction.preflight(
                level, event.getPos(), player, player.getMainHandItem());
        if (result.concernsADeposit() && !result.permitsBreak()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || ManagedDeposits.resolve(event.getState()).isEmpty()) {
            return;
        }
        if (ManagedExtractionPolicy.bypassesManagedExtraction(player)) {
            return;
        }
        if (MiningProvenance.isPlayerPlaced(level, event.getPos())) {
            return;
        }
        event.setCanceled(true);
        ManagedDepositExtraction.extract(level, event.getPos(), player, player.getMainHandItem());
    }
}
