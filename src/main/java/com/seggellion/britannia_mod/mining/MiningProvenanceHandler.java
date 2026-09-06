package com.seggellion.britannia_mod.mining;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;

import com.seggellion.britannia_mod.resource.extraction.ManagedExtractionPolicy;

/**
 * Marks <em>survival</em>-placed mineables so the place-break loop cannot be farmed (milestone 7),
 * while leaving creative placements alone so they become real resource nodes.
 *
 * <p>Only catalogued mineables are tracked: placing dirt, planks or a chest costs nothing, because
 * those blocks were never Mining resources. Automation is excluded for the same reason the break
 * gate excludes it — a fake player has no authoritative human skill owner, and letting it mark
 * positions would give automation a lever over another player's Mining.
 *
 * <h2>Why the game mode decides</h2>
 * Creative placement is how an administrator manually sites a resource, and an owner-approved
 * requirement is that such a node is a <b>first-class mining resource</b>: same gate, same
 * canonical yield, same depletion, same restoration as a Rails-authored deposit. Marking it would
 * make it construction and hand back a block item instead of ore, which is exactly the confusion
 * this rule used to cause. Nothing extra is recorded to achieve that — an unmarked catalogued
 * block already <em>is</em> a resource node to the whole pipeline, so an admin-sited node needs no
 * ledger entry, no fabricated Rails deposit id, and cannot collide with one. Restoration is
 * position-keyed through {@code BrokenBlockTracker} and works the same for both origins.
 *
 * <p>Survival placement stays marked, and that is not symmetry for its own sake: it is the whole
 * anti-duplication rule. Ordinary stone is trivially farmable, so if a survival-placed block were
 * a resource node a player could convert cobblestone into graded stone and unlimited Mining
 * progression forever. Resource block items are not obtainable in survival, so the only way to
 * place one at all is to have been given it.
 */
public class MiningProvenanceHandler {

    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        if (Mineables.resolve(event.getPlacedBlock()).isEmpty()) {
            return;
        }
        // Creative siting is administration, not construction: leave it unmarked so the block is
        // an ordinary resource node. The server game mode is the authority here for the same
        // reason it is in MiningBreakGate -- Player#isCreative is a derived view that mock players
        // override, while gameMode.getGameModeForPlayer() is what the break pipeline itself reads.
        if (ManagedExtractionPolicy.isCreativeGameMode(player)) {
            return;
        }
        MiningProvenance.markPlayerPlaced(level, event.getPos());
    }
}
