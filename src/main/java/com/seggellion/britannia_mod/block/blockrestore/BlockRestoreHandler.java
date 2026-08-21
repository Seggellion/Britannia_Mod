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
     * Whether the node may return without taking something else with it.
     *
     * <p>Milestone 1 of the OreVein remediation narrowed this. It used to accept any cell that was
     * air <em>or</em> {@code canBeReplaced()}, and in 1.21.1 both {@code minecraft:water} and
     * {@code minecraft:lava} are declared {@code .replaceable()} — so a node mined in a shallow or
     * beside a lava fall came back by deleting the fluid. That is the restoration system quietly
     * editing the world outside its remit, and it is not what "the cell is still free" was ever
     * meant to mean.
     *
     * <p>The cell must now be genuinely empty of fluid, hold no block entity, be air or a
     * replaceable state that policy accepts, and have nothing standing in it. A cell that fails
     * any of those keeps its debt and is tried again later — the record is never dropped and the
     * occupying state is never destroyed.
     */
    public static boolean canRestoreInto(ServerLevel level, BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        boolean allowed = cellStateAllowsRestoration(
                !level.getFluidState(pos).isEmpty(),
                current.isAir(),
                current.canBeReplaced(),
                level.getBlockEntity(pos) != null);
        if (!allowed) {
            return false;
        }
        List<Entity> occupants = level.getEntitiesOfClass(Entity.class, new AABB(pos));
        return occupants.isEmpty();
    }

    /**
     * The occupancy policy itself, as a pure function of what is in the cell.
     *
     * <p>Split out so the rule can be driven exhaustively by a plain JUnit test without booting
     * Minecraft, the way {@code MiningBreakGate.evaluateResolved} is. The policy this states, in
     * order: fluid always blocks; a block entity always blocks; otherwise air and explicitly
     * replaceable states (grass, snow layers, and the like) accept the node back.
     *
     * @param fluidPresent      the cell holds water or lava, flowing or source
     * @param air               the cell is air
     * @param replaceable       the cell's state reports {@code canBeReplaced()}
     * @param blockEntityPresent something with stored contents or identity stands here
     */
    public static boolean cellStateAllowsRestoration(
            boolean fluidPresent, boolean air, boolean replaceable, boolean blockEntityPresent) {
        if (fluidPresent || blockEntityPresent) {
            return false;
        }
        return air || replaceable;
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
