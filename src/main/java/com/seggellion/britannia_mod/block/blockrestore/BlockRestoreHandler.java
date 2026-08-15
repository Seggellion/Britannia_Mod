package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.network.chat.Component;

import net.neoforged.bus.api.SubscribeEvent;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * Returns mined resource nodes to the world once their restoration delay has elapsed.
 *
 * <p>Milestone 7 kept this single existing scheduler — no second timer, no force-loading — and
 * corrected two ways it did not yet cooperate with the Mining system:
 *
 * <ul>
 *   <li><b>Every dimension, not only the Overworld.</b> Records are stored per level, but this loop
 *       used to read only {@code Level.OVERWORLD}, so a node mined anywhere else was scheduled and
 *       then never restored. That became load-bearing once Mining governed Basalt and Blackstone,
 *       which are Nether-native.</li>
 *   <li><b>Never overwrite what is standing there.</b> Restoration used to write the block back
 *       unconditionally, which could delete a player's construction or materialise stone inside a
 *       player or their animals. It now restores only into a genuinely free cell and otherwise
 *       waits, which is the "wait when the target is occupied" policy the design asks for.</li>
 * </ul>
 */
public class BlockRestoreHandler {

    private static final int RESTORE_HOURS = 6;
    /** Public so admin tooling reports the real delay instead of duplicating the constant. */
    public static final long RESTORE_DELAY = RESTORE_HOURS * 60L * 60L * 1000L;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Pre event) {
        MinecraftServer server = event.getServer();
        if (server == null) return;

        long now = System.currentTimeMillis();
        Map<UUID, Integer> playerRestoreCount = new HashMap<>();

        for (ServerLevel level : server.getAllLevels()) {
            restoreDueBlocks(level, now, playerRestoreCount);
        }

        // Notify each player of their specific restored blocks
        if (!playerRestoreCount.isEmpty()) {
            sendRestorationMessages(server, playerRestoreCount);
        }
    }

    private void restoreDueBlocks(ServerLevel level, long now, Map<UUID, Integer> playerRestoreCount) {
        BrokenBlockDataStorage storage = BrokenBlockDataStorage.get(level);
        Iterator<Map.Entry<BlockPos, BrokenBlockData>> iterator =
                storage.getBrokenBlocks().entrySet().iterator();
        boolean restoredAny = false;

        while (iterator.hasNext()) {
            Map.Entry<BlockPos, BrokenBlockData> entry = iterator.next();
            BrokenBlockData data = entry.getValue();

            if (now - data.brokenTime < RESTORE_DELAY) {
                continue;
            }
            // An unloaded cell is retried on a later tick rather than force-loaded.
            if (!level.isLoaded(data.pos)) {
                continue;
            }
            if (!canRestoreInto(level, data.pos)) {
                continue;
            }

            level.setBlockAndUpdate(data.pos, data.originalState);
            iterator.remove();
            restoredAny = true;
            playerRestoreCount.merge(data.playerUUID, 1, Integer::sum);
        }

        if (restoredAny) {
            storage.setDirty();
        }
    }

    /**
     * Whether the node may return without taking something else with it: the cell must still be
     * free (nothing was built there) and no entity may be standing in it.
     */
    public static boolean canRestoreInto(ServerLevel level, BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        if (!current.isAir() && !current.canBeReplaced()) {
            return false;
        }
        List<Entity> occupants = level.getEntitiesOfClass(Entity.class, new AABB(pos));
        return occupants.isEmpty();
    }

    /**
     * Sends a message to players notifying them of their restored blocks.
     */
    private void sendRestorationMessages(MinecraftServer server, Map<UUID, Integer> playerRestoreCount) {
        for (Map.Entry<UUID, Integer> entry : playerRestoreCount.entrySet()) {
            UUID playerUUID = entry.getKey();
            int restoredCount = entry.getValue();

            ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
            if (player != null) {
                Component message = Component.literal("You hear a cave collapse, " + restoredCount + " blocks fallen");
                player.sendSystemMessage(message);
            }
        }
    }
}
