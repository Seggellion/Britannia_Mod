package com.seggellion.britannia_mod.mining;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Marks player-placed mineables so the place-break loop cannot be farmed (milestone 7).
 *
 * <p>Only catalogued mineables are tracked: placing dirt, planks or a chest costs nothing, because
 * those blocks were never Mining resources. Automation is excluded for the same reason the break
 * gate excludes it — a fake player has no authoritative human skill owner, and letting it mark
 * positions would give automation a lever over another player's Mining.
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
        MiningProvenance.markPlayerPlaced(level, event.getPos());
    }
}
