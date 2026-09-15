package com.seggellion.britannia_mod.resource.extraction;

import com.seggellion.britannia_mod.mining.MiningProvenance;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.Resources;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Clearing a block is not deleting a deposit, whatever mode the breaker is in.
 *
 * <h2>The rule</h2>
 * Milestone 6 stopped a creative operator <em>earning</em> from a managed resource, but left the
 * break itself to vanilla — so the deposit was destroyed, permanently and silently, by an action
 * that looked like nothing more than clearing a block. A vein removed that way does not come back:
 * no restoration debt is filed, because no extraction happened.
 *
 * <p>The amended rule refuses the break instead. The cell stays where it is, and removing a deposit
 * becomes something an administrator has to ask for explicitly — through
 * {@code /manageddeposit remove} or {@code /populateores clear}, both of which the refusal names —
 * rather than something that happens as a side effect of building nearby. Nothing is yielded,
 * nothing is depleted, no debt is filed, no skill is awarded and no tool is worn, because nothing
 * happened at all.
 *
 * <h2>How this sits with the attacking-hand rule</h2>
 * The creative rule elsewhere in this package turns on the attacking hand: a creative player
 * holding the Britannia pickaxe is a <em>tester</em> and gets the whole managed ladder, while a
 * creative player without it is <em>administering</em> and every managed path stands aside for
 * them ({@link ManagedExtractionPolicy#bypassesManagedExtraction}). That rule is untouched here,
 * and this guard composes with it rather than competing:
 *
 * <ul>
 *   <li>A creative <b>tester</b> — attacking with the Britannia pickaxe — is never refused by this
 *       guard at all. {@link ManagedExtractionPolicy#evaluate} answers {@code ALLOWED} for them,
 *       not {@code DENIED_CREATIVE}, so the first branch below returns and the full ladder runs.
 *       The testing affordance is exactly as wide as it was.</li>
 *   <li>A creative <b>administrator</b> keeps the broad affordance the local rule gave them for
 *       <em>ordinary</em> blocks: everything that is not a sited deposit cell — vanilla stone,
 *       granite, deepslate, a misplaced chest, anything outside the catalogue — still breaks for
 *       them exactly as any block breaks in creative, minting no ore, no skill and no debt.</li>
 *   <li>What they no longer do is destroy a sited deposit by clicking it. That is the one narrow
 *       case where "stand aside and let vanilla break it" and "the vein is gone forever, silently"
 *       are the same sentence, and it is the case this guard exists for.</li>
 * </ul>
 *
 * <p>The two rules are reconcilable because they are about different things: the local rule is
 * about <em>who is earning</em>, and answers that from the hand; this guard is about <em>what may
 * be silently destroyed</em>, and answers that from the block. Neither reads the other's fact.
 *
 * <h2>What it deliberately does not cover</h2>
 * Ambient rock. {@code minecraft:stone}, {@code granite}, {@code deepslate} and the rest of the
 * vanilla STONE family are managed resources in the catalogue — they yield graded stone — but they
 * are the world's crust, not sited deposits. Refusing creative breaks on those would stop a builder
 * terraforming, cutting a cellar, or clearing ground for a house, which is not what protecting a
 * deposit means. {@link ManagedExtractionPolicy#isDepositCell} draws that line from the resource
 * family and the block's namespace rather than from a list of names, so a resource added later
 * inherits the right answer without being mentioned here.
 *
 * <p>Player-placed blocks are also left alone. A builder who placed a block may remove it; that is
 * the M3/M7 provenance rule, and it is asked here rather than assumed.
 *
 * <h2>Why a listener and not a branch in the gate</h2>
 * The protection spans both resource families, and neither family's handler is its natural owner:
 * {@code MiningGateHandler} deliberately excludes SEDIMENT (the beds have their own handler), and
 * {@code ManagedDepositInteractionHandler} deliberately only knows about the beds. A rule that has
 * to hold for an ore, a coal cell, a bed and a bespoke rock alike belongs in one place that sees
 * every break, which is here.
 *
 * <p>Standing outside those handlers is also what keeps the local rule's guarantee safe. The four
 * seams that make a creative removal mint nothing — the gate's two listeners, the gate's decision
 * core, and {@code CustomBlockBreakHandler}'s actor check — are not edited by this class and do
 * not know it exists. A cancelled event never reaches any of them, so a refusal cannot become a
 * partial transaction: there is no path on which this guard says no and something downstream still
 * files a debt or awards a skill.
 *
 * <p>{@link EventPriority#HIGHEST}, ahead of both the Mining gate and the deposit handler, because
 * the cleanest refusal is the one that happens before anything else has formed an opinion — and
 * because each of those handlers stands aside for an administering creative player at the top of
 * its own listener, so a refusal that ran later would have to be written into all of them.
 */
public final class ManagedResourceCreativeGuard {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        // Only an administering creative player. An operator playing in survival is an ordinary
        // miner and is left to the ordinary rules: they need the right tool and the skill, and
        // they are paid for the work. A creative player attacking with the Britannia pickaxe is a
        // tester, evaluates ALLOWED rather than DENIED_CREATIVE, and is likewise left alone here
        // so the full managed flow can run for them.
        if (ManagedExtractionPolicy.evaluate(player) != ManagedExtractionPolicy.Verdict.DENIED_CREATIVE) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        ResourceDefinition definition = Resources.resolve(state).orElse(null);
        // Not catalogued, or catalogued as ambient crust: an ordinary creative removal, which is
        // the affordance an administrator clearing a misplaced block depends on. Left entirely
        // alone -- no cancel, no message, no resync.
        if (definition == null || !ManagedExtractionPolicy.isDepositCell(definition, state)) {
            return;
        }
        if (MiningProvenance.isPlayerPlaced(level, pos)) {
            return;
        }

        event.setCanceled(true);
        player.displayClientMessage(
                Component.translatable("message.britannia_mod.deposit.creative_refused"), true);
        // The creative client removes the block the moment it is clicked, so without this the
        // player sees a hole that the server does not have.
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }
}
