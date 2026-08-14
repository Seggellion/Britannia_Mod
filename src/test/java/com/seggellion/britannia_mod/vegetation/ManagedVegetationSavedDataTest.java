package com.seggellion.britannia_mod.vegetation;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedVegetationSavedDataTest {
    @Test
    void registerDuplicateRemoveAndChunkIndexAreConsistent() {
        ManagedVegetationSavedData data = new ManagedVegetationSavedData();
        BlockPos position = new BlockPos(33, 70, -2);
        ManagedVegetationNode node = ManagedVegetationNode.regrowing(position, 100L);

        assertTrue(data.register(node));
        assertFalse(data.register(node));
        assertEquals(1, data.nodesInChunk(new ChunkPos(position)).size());
        assertTrue(data.remove(position));
        assertFalse(data.remove(position));
        assertTrue(data.nodesInChunk(new ChunkPos(position)).isEmpty());
    }

    @Test
    void savedNodesReloadAndDueQueueDoesNotScanFutureNodes() {
        ManagedVegetationSavedData original = new ManagedVegetationSavedData();
        ManagedVegetationNode early = ManagedVegetationNode.regrowing(new BlockPos(1, 64, 1), 20L);
        ManagedVegetationNode late = ManagedVegetationNode.regrowing(new BlockPos(2, 64, 2), 200L);
        original.register(early);
        original.register(late);

        CompoundTag saved = original.save(new CompoundTag(), null);
        ManagedVegetationSavedData loaded = ManagedVegetationSavedData.load(saved, null);

        assertEquals(2, loaded.snapshot().size());
        assertEquals(java.util.List.of(early), loaded.pollDue(20L, 8));
        assertTrue(loaded.pollDue(20L, 8).isEmpty());
        assertEquals(java.util.List.of(late), loaded.pollDue(200L, 8));
    }

    @Test
    void invalidEntriesAreQuarantinedWithoutDiscardingValidNodes() {
        ManagedVegetationNode valid = ManagedVegetationNode.regrowing(new BlockPos(1, 64, 1), 20L);
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", ManagedVegetationSavedData.SCHEMA_VERSION);
        ListTag nodes = new ListTag();
        nodes.add(valid.toTag());
        CompoundTag invalid = new CompoundTag();
        invalid.putLong("Position", BlockPos.ZERO.asLong());
        invalid.putString("Lifecycle", "REMOVED_STATE");
        invalid.putLong("NextTransitionGameTime", 1L);
        nodes.add(invalid);
        root.put("Nodes", nodes);

        ManagedVegetationSavedData loaded = ManagedVegetationSavedData.load(root, null);

        assertEquals(java.util.List.of(valid), loaded.snapshot().stream().toList());
    }

    @Test
    void reschedulingRemovesStaleDueEntries() {
        ManagedVegetationSavedData data = new ManagedVegetationSavedData();
        ManagedVegetationNode original = ManagedVegetationNode.regrowing(BlockPos.ZERO, 10L);
        data.register(original);
        ManagedVegetationNode rescheduled = original.schedule(30L);

        data.update(rescheduled);

        assertTrue(data.pollDue(10L, 8).isEmpty());
        assertEquals(java.util.List.of(rescheduled), data.pollDue(30L, 8));
    }

    @Test
    void highCycleQueueKeepsOneCanonicalNodeWithoutAccumulatingTransitions() {
        ManagedVegetationSavedData data = new ManagedVegetationSavedData();
        ManagedVegetationNode current = ManagedVegetationNode.regrowing(BlockPos.ZERO, 1L);
        data.register(current);

        for (long cycle = 1L; cycle <= 10_000L; cycle++) {
            assertEquals(java.util.List.of(current), data.pollDue(cycle, 1));
            current = current.schedule(cycle + 1L);
            data.update(current);
            assertEquals(1, data.snapshot().size());
        }

        assertTrue(data.pollDue(10_000L, 1).isEmpty());
        assertEquals(java.util.List.of(current), data.pollDue(10_001L, 1));
    }
}
