package com.seggellion.britannia_mod.blockrestore;

import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.Resources;
import com.seggellion.britannia_mod.resource.deposit.DepositInstance;
import com.seggellion.britannia_mod.resource.deposit.DepositLedger;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Records that a managed resource cell was worked out, and when it is owed back.
 *
 * <h2>What milestone 4 added</h2>
 * The due moment and the owner, both resolved once here rather than recomputed on every visit:
 *
 * <ul>
 *   <li><b>Due time</b> from the resource's own regeneration policy — six hours for the ladder,
 *       twenty-four for silica. Milestone 2 made this resolve from the definition; milestone 4
 *       resolves it at the moment of extraction so the scheduler can order debts by time.</li>
 *   <li><b>Owning deposit</b>, when there is one. {@link DepositLedger#owning} answers from the
 *       chunk index, so it costs the number of deposits reaching into one chunk.</li>
 * </ul>
 *
 * <p>A cell with no owning deposit is recorded as genuinely unowned rather than given an invented
 * identity. That is the common case for existing worlds — every resource block placed before
 * milestone 4 has no ledger entry — and pretending otherwise would put false provenance into
 * durable storage. Milestone 8's retrofit is what adopts them.
 */
public class BrokenBlockTracker {

    /** Record a worked cell, resolving its due time and its owner. */
    public static void recordBrokenBlock(ServerLevel level, BlockPos pos, BlockState state, UUID playerUUID) {
        long now = System.currentTimeMillis();
        Optional<ResourceDefinition> resource = Resources.resolve(state);

        long delay = resource
                .map(ResourceDefinition::regenerationMillis)
                .orElse(BrokenBlockData.DEFAULT_RESTORE_DELAY);
        String resourceId = resource.map(ResourceDefinition::id).orElse("");
        long instanceId = resource
                .flatMap(definition -> DepositLedger.get(level).owning(pos, definition.id()))
                .map(DepositInstance::instanceId)
                .orElse(DepositInstance.NO_INSTANCE);

        BrokenBlockData data = new BrokenBlockData(
                pos, state, now, playerUUID, now + delay, instanceId, resourceId, 0L, 0);

        // add() tells the scheduler about the new debt, so a cell mined in an already-loaded chunk
        // is watched immediately rather than waiting for that chunk to cycle.
        BrokenBlockDataStorage.get(level).add(data);
    }

    /**
     * Every debt in the dimension, flattened.
     *
     * <p>O(all debts). Diagnostics and tests only — the scheduler uses the chunk index.
     */
    public static Map<BlockPos, BrokenBlockData> getBrokenBlocks(ServerLevel level) {
        return BrokenBlockDataStorage.get(level).getBrokenBlocks();
    }

    public static void removeBlock(ServerLevel level, BlockPos pos) {
        BrokenBlockDataStorage.get(level).remove(pos);
    }
}
