package com.seggellion.britannia_mod.mining;

import com.seggellion.britannia_mod.registry.CityRegistry;
import com.seggellion.britannia_mod.resource.extraction.ManagedExtractionPolicy;
import com.seggellion.britannia_mod.resource.extraction.ManagedBreakAuthorization;

import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Optional;

/**
 * The server-authoritative Mining break gate (milestone 3), and the two policies that grew onto
 * it when adventure mining moved to the real break lifecycle.
 *
 * <p>Listens to {@link BlockEvent.BreakEvent} — the earliest cancelable server-side point before
 * any mutation (discovery report §1.5) — at {@link EventPriority#HIGH} for one load-bearing
 * reason: {@code CustomBlockBreakHandler} mutates the world <em>inside</em> its NORMAL-priority
 * listener, so a denial must land before NORMAL runs at all. Cancelling here also suppresses that
 * handler and the vanilla break path together (neither receives cancelled events), which closes
 * the vanilla-tool route that could previously destroy managed ore blocks droplessly.
 *
 * <h2>The first swing</h2>
 * Since the retirement of the Survival-switch workaround, an adventure miner digs through
 * vanilla's own destroy progress (the pickaxe carries a scoped CAN_BREAK predicate). The
 * {@code LeftClickBlock} preflight below refuses a dig that could never be permitted at the first
 * swing — the same courtesy the managed deposits give — so an under-skilled miner is told
 * immediately instead of holding a two-second dig and being refused at the end. The completed
 * break re-evaluates everything regardless: state can change during the dig, and the preflight is
 * a courtesy, never the authority.
 *
 * <h2>Creative</h2>
 * In creative the first swing <em>is</em> the break: vanilla answers a cancelled
 * {@code LeftClickBlock} by doing nothing, and an uncancelled one by destroying the block at once
 * through the same {@code BreakEvent} chain a survival dig ends in. Both listeners therefore ask
 * {@link ManagedExtractionPolicy#bypassesManagedExtraction} before anything else and stand aside
 * for a creative player who is not attacking with the Britannia pickaxe — no house or city
 * question, no evaluation, no denial, no resync — so catalogued stone and sited deposits break for
 * them exactly as every other block does in creative. A creative player attacking with the
 * Britannia pickaxe is a tester and gets the full gate, ladder included, so the managed flow can
 * be exercised without leaving creative. The decision is read from live server state at both
 * moments, never remembered from the first.
 *
 * <h2>City bounds</h2>
 * Forcing adventure inside city bounds used to make in-city mining impossible as a side effect —
 * no survival, no dig. With adventure mining now real, that accidental protection needs stating
 * explicitly: a Mining-ladder resource inside a city refuses extraction unless the actor is the
 * kind the old arrangement also let through (creative operators, and anyone the game itself lets
 * build here — {@code mayBuild}, which is how a house owner works catalogued stone inside their
 * own city house). The sediment beds are deliberately exempt: deposits have always been workable
 * wherever an administrator sited them, cities included, and their own handler owns their rules.
 *
 * <p>Deliberately not wired to commands, worldgen, restoration, explosions or pistons: none of
 * those fire {@code BreakEvent}, so they cannot traverse the player gate by construction.
 */
public class MiningGateHandler {

    /**
     * First-swing courtesy refusal. START only — the server raises this event once per dig
     * attempt — and only for Mining-ladder resources: the deposit handler preflights the beds
     * with their own richer chain (house rights before tool), and player-placed construction is
     * vanilla's business, not a deposit.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)
                || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        // Asked before the block is even resolved: a bypassing creative player is not mining,
        // whatever they are pointing at, and nothing below may form an opinion about them.
        if (ManagedExtractionPolicy.bypassesManagedExtraction(player)) {
            return;
        }
        BlockState state = level.getBlockState(event.getPos());
        Optional<MineableDefinition> resolved = Mineables.resolve(state);
        if (resolved.isEmpty()
                || resolved.get().category() == MineableDefinition.Category.SEDIMENT
                || MiningProvenance.isPlayerPlaced(level, event.getPos())) {
            return;
        }
        if (houseRefuses(level, event.getPos(), player)) {
            event.setCanceled(true);
            return;
        }
        if (cityBoundsRefuse(player, event.getPos())) {
            event.setCanceled(true);
            sendCityRefusal(player);
            return;
        }
        MiningBreakGate.Evaluation evaluation =
                MiningBreakGate.evaluate(player, state, level, event.getPos());
        if (!evaluation.permitsBreak()) {
            event.setCanceled(true);
            MiningBreakGate.sendDenialFeedback(player, evaluation);
            // Resynchronise here as well as at the completed break. Since the adventure-lifecycle
            // change, the pickaxe carries a can_break predicate covering every catalogued block
            // whatever the holder's skill -- deliberately, because the predicate is a lifecycle
            // key and not an authority -- so a client may locally predict and animate a full dig
            // of a resource the server will never let it have. Cancelling the swing without this
            // leaves that prediction standing: the block appears to break, nothing drops and no
            // skill moves, which is exactly the "I mined it and got nothing" report. The server
            // state was always correct; only the client's picture of it was not.
            MiningBreakGate.synchronizeDeniedBreak(player, level, event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        Player player = event.getPlayer();
        // Judged again from live state at completion, not remembered from the first swing: a
        // survival dig whose digger switched to creative and put the pickaxe away before it
        // finished is an ordinary creative removal now, and one who picked the pickaxe up is a
        // tester now.
        if (player instanceof ServerPlayer serverPlayer
                && ManagedExtractionPolicy.bypassesManagedExtraction(serverPlayer)) {
            return;
        }
        boolean managedLadderResource = Mineables.resolve(event.getState())
                .filter(definition -> definition.category() != MineableDefinition.Category.SEDIMENT)
                .isPresent()
                && !MiningProvenance.isPlayerPlaced(serverLevel, event.getPos());

        if (player instanceof ServerPlayer serverPlayer && managedLadderResource) {
            // Whose ground is this? Asked here, at HIGH, because the answer has to land before
            // CustomBlockBreakHandler empties the cell from its own NORMAL listener -- a cancelled
            // event never reaches StructureProtectionHandler, so that class cannot be the house
            // authority for a managed resource. Same reasoning, and the same authority, as
            // ManagedDepositExtraction's own call.
            if (houseRefuses(serverLevel, event.getPos(), serverPlayer)) {
                event.setCanceled(true);
                MiningBreakGate.synchronizeDeniedBreak(serverPlayer, serverLevel, event.getPos());
                return;
            }
            // The explicit form of the protection the forced-adventure city rule used to provide
            // by accident. Checked at completion as well as at the first swing, because a dig can
            // start outside a boundary question and finish inside a different answer.
            if (cityBoundsRefuse(serverPlayer, event.getPos())) {
                event.setCanceled(true);
                sendCityRefusal(serverPlayer);
                MiningBreakGate.synchronizeDeniedBreak(serverPlayer, serverLevel, event.getPos());
                return;
            }
        }
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
     * Whether the house standing here refuses this player, telling them why.
     *
     * <p>Delegates to {@link ManagedBreakAuthorization}, which consumes the one canonical
     * HouseBuildRights decision the break rule, the place rule and the deposit service all
     * share — no second approximation of a house boundary is made here. Ordinary open ground answers
     * OUTSIDE_ANY_HOUSE and passes; an owner working inside their own walls answers ALLOWED and
     * passes; a stranger, the perimeter foundation and the lot block are refused.
     */
    static boolean houseRefuses(ServerLevel level, BlockPos pos, ServerPlayer player) {
        return ManagedBreakAuthorization.refuses(level, pos, player);
    }

    /**
     * Whether city bounds refuse this actor's extraction at this position.
     *
     * <p>The block's position decides, not the player's: the old rule keyed on where the player
     * stood, which let a miner at the boundary reach across it. The exemptions are the actors the
     * old arrangement also allowed — see {@link #cityProtects}.
     */
    static boolean cityBoundsRefuse(ServerPlayer player, BlockPos pos) {
        return cityProtects(
                CityRegistry.isPlayerInAnyCity(pos.getCenter()),
                ManagedExtractionPolicy.isCreativeGameMode(player),
                player.getAbilities().mayBuild);
    }

    /**
     * The decision itself, over nothing but the three facts it depends on — pure, so the truth
     * table is unit-testable without a level or a city registry.
     */
    static boolean cityProtects(boolean insideCity, boolean creative, boolean mayBuild) {
        return insideCity && !creative && !mayBuild;
    }

    private static void sendCityRefusal(ServerPlayer player) {
        MiningBreakGate.sendThrottledDenial(player, "CITY_PROTECTED",
                Component.translatable("message.britannia_mod.mining.city_protected"));
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
