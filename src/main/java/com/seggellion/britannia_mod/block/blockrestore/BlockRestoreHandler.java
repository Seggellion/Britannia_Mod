package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.blockrestore.RestorationScheduler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.network.chat.Component;

import net.neoforged.bus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Returns mined resource nodes to the world once their restoration delay has elapsed.
 *
 * <h2>What milestone 4 changed</h2>
 * This used to walk every restoration debt in every dimension on every server pre-tick, asking each
 * one whether it was ready. Its cost was the number of debts the world had ever accumulated,
 * multiplied by twenty per second, and almost all of that work was spent on debts in chunks nobody
 * had loaded.
 *
 * <p>It is now event-driven. {@link BrokenBlockDataStorage} indexes debts by chunk;
 * {@link RestorationScheduler} watches only the chunks that are loaded and orders them by due time.
 * A chunk load reads exactly that chunk's debts. A pass looks at the queue head and stops as soon
 * as the head is not ready, so a tick with nothing to do costs one comparison.
 *
 * <p>What deliberately did not change: the clock is still wall-clock epoch, so offline time counts;
 * an unloaded chunk is still never force-loaded; and the occupancy rules milestone 1 established
 * are untouched — fluids, block entities and occupied cells still block, and a blocked debt is
 * still kept rather than dropped or forced through.
 */
public class BlockRestoreHandler {

    /**
     * The historical global delay, still the fallback for a block with no resource policy.
     *
     * <p>Per-resource timing has resolved from the resource definition since milestone 2; milestone
     * 4 moved the resolution to the moment a debt is recorded rather than every time it is looked
     * at. This constant is what a block the catalogue no longer governs falls back to.
     */
    public static final long RESTORE_DELAY = BrokenBlockData.DEFAULT_RESTORE_DELAY;

    private int tickCounter;

    /* ------------------------------------------------------------------ */
    /*  Chunk lifecycle                                                    */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RestorationScheduler.of(level).activate(
                    BrokenBlockDataStorage.get(level), event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RestorationScheduler.of(level).deactivate(event.getChunk().getPos());
        }
    }

    /* ------------------------------------------------------------------ */
    /*  The cadenced pass                                                  */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Pre event) {
        MinecraftServer server = event.getServer();
        if (server == null) return;

        // Once a second, not twenty times. These are six-hour and twenty-four-hour timers.
        if (++tickCounter < RestorationScheduler.CADENCE_TICKS) {
            return;
        }
        tickCounter = 0;

        long now = System.currentTimeMillis();
        Map<UUID, Integer> playerRestoreCount = new HashMap<>();

        for (ServerLevel level : server.getAllLevels()) {
            RestorationScheduler scheduler = RestorationScheduler.of(level);
            scheduler.runPass(BrokenBlockDataStorage.get(level), now, debt -> {
                if (!canRestoreInto(level, debt.pos)) {
                    return false;
                }
                level.setBlockAndUpdate(debt.pos, debt.originalState);
                playerRestoreCount.merge(debt.playerUUID, 1, Integer::sum);
                return true;
            });
        }

        if (!playerRestoreCount.isEmpty()) {
            sendRestorationMessages(server, playerRestoreCount);
        }
    }

    /**
     * Whether the node may return without taking something else with it.
     *
     * <p>Unchanged since milestone 1, which narrowed it: it used to accept any cell that was air
     * <em>or</em> {@code canBeReplaced()}, and in 1.21.1 both water and lava are declared
     * replaceable — so a node mined in a shallow came back by deleting the fluid.
     *
     * <p>Milestone 4 changes only what happens when this says no: instead of the cell being
     * revisited twenty times a second forever, the debt backs off. The debt itself is never
     * dropped and the blocker is never overwritten.
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
