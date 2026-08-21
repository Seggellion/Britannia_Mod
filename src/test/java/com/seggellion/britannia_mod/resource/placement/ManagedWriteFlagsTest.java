package com.seggellion.britannia_mod.resource.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.block.Block;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The flags managed writes use, pinned against the defect that found them.
 *
 * <h2>What went wrong</h2>
 * Natural deposits are written from {@code ChunkEvent.Load}, on the server thread, while the chunk
 * is still being promoted to full status by {@code ChunkStatusTasks.full}. The write path used
 * {@code UPDATE_CLIENTS} alone, on the stated belief that flag 2 meant "no neighbour updates".
 *
 * <p>It does not. Flag 2 suppresses neighbour <em>block</em> updates. Neighbour <em>shape</em>
 * updates are suppressed only by {@code UPDATE_KNOWN_SHAPE}. So every cell written ran
 * {@code updateNeighbourShapes}, which reads all six neighbours; a cell on a chunk border read a
 * block in the next chunk; and reading an unloaded chunk asked the chunk source to produce it. The
 * thread issuing that request was the same server thread that owed the answer, so it parked in
 * {@code ServerChunkCache$MainThreadExecutor.managedBlock} waiting for itself.
 *
 * <p>The server did not crash and logged nothing. It simply stopped, mid "Preparing spawn area".
 *
 * <h2>Why a flag assertion and not a world test</h2>
 * The failure needs a cell on a chunk border in a chunk that is mid-promotion with its neighbour
 * absent. A GameTest runs in a structure block inside an already-loaded neighbourhood, so the
 * border read is always satisfied from memory and the hang cannot be reproduced there — a world
 * test would pass whether or not the bug were present, which is worse than no test.
 *
 * <p>The flags are the whole of the fix, so the flags are what is pinned.
 */
@DisplayName("managed writes never provoke a cross-chunk read")
final class ManagedWriteFlagsTest {

    @Test
    @DisplayName("the shape update is suppressed, which is what kept the server thread off itself")
    void suppressesNeighbourShapeUpdates() {
        assertTrue((MaterializationService.WRITE_FLAGS & Block.UPDATE_KNOWN_SHAPE) != 0,
                "managed writes must set UPDATE_KNOWN_SHAPE. Without it every write reads its six"
                        + " neighbours, a write on a chunk border reads the next chunk, and during"
                        + " chunk load the server thread parks waiting for a chunk only it can"
                        + " generate. The symptom is a silent stall with no exception.");
    }

    @Test
    @DisplayName("clients are still told, and neighbours are still not asked to react")
    void tellsClientsWithoutRunningNeighbourReactions() {
        assertTrue((MaterializationService.WRITE_FLAGS & Block.UPDATE_CLIENTS) != 0,
                "a materialised deposit that clients are never told about is invisible until reload");
        assertEquals(0, MaterializationService.WRITE_FLAGS & Block.UPDATE_NEIGHBORS,
                "bulk terrain writes must not run neighbour reactions");
    }

    @Test
    @DisplayName("flag 2 on its own is the defect, and is rejected by name")
    void plainUpdateClientsIsNotEnough() {
        assertNotEquals(Block.UPDATE_CLIENTS, MaterializationService.WRITE_FLAGS,
                "UPDATE_CLIENTS alone was the original value and is the deadlock");
        assertEquals(Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE,
                MaterializationService.WRITE_FLAGS,
                "the intended value is exactly UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE (2 | 16)");
    }
}
