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
 * An ordinary block break is not an administrative delete, whatever mode the breaker is in.
 *
 * <h2>The rule</h2>
 * Milestone 6 stopped a creative operator <em>earning</em> from a managed resource, but left the
 * break itself to vanilla — so the deposit was destroyed, permanently and silently, by an action
 * that looked like nothing more than clearing a block. A vein removed that way does not come back:
 * no restoration debt is filed, because no extraction happened.
 *
 * <p>The amended rule refuses the break instead. The cell stays where it is, and removing a deposit
 * becomes something an administrator has to ask for explicitly — through
 * {@code /manageddeposit} or {@code /populateores} — rather than something that happens as a side
 * effect of building nearby. Nothing is yielded, nothing is depleted, no debt is filed, no skill is
 * awarded and no tool is worn, because nothing happened at all.
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
 * <p>{@link EventPriority#HIGHEST}, ahead of both the Mining gate and the deposit handler, because
 * the cleanest refusal is the one that happens before anything else has formed an opinion.
 */
public final class ManagedResourceCreativeGuard {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        // Only creative. An operator playing in survival is an ordinary miner and is left to the
        // ordinary rules: they need the right tool and the skill, and they are paid for the work.
        if (ManagedExtractionPolicy.evaluate(player) != ManagedExtractionPolicy.Verdict.DENIED_CREATIVE) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        ResourceDefinition definition = Resources.resolve(state).orElse(null);
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
