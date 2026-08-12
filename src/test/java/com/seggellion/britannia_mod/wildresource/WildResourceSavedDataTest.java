package com.seggellion.britannia_mod.wildresource;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildResourceSavedDataTest {
    private static final ResourceLocation ASH = ResourceLocation.fromNamespaceAndPath("britannia_mod", "ash");

    @Test
    void schedulingAndNodeAccountingSurviveReload() {
        WildResourceSavedData original = new WildResourceSavedData();
        ChunkPos chunk = new ChunkPos(4, -2);
        BlockPos position = chunk.getBlockAt(7, 65, 9);
        original.scheduleAttempt(chunk, ASH, 1234L);
        assertTrue(original.registerNode(new WildResourceNode(ASH, position, 1000L)));

        CompoundTag saved = original.save(new CompoundTag(), null);
        WildResourceSavedData loaded = WildResourceSavedData.load(saved, null);

        assertEquals(1234L, loaded.nextAttempt(chunk, ASH));
        assertEquals(1, loaded.countInChunk(chunk, ASH));
        assertEquals(position, loaded.nodeAt(position).orElseThrow().position());
        assertFalse(loaded.registerNode(new WildResourceNode(ASH, position, 1001L)));
    }

    @Test
    void spacingUsesSquaredEuclideanDistanceAndRemovalUpdatesAccounting() {
        WildResourceSavedData data = new WildResourceSavedData();
        BlockPos position = new BlockPos(15, 64, 15);
        data.registerNode(new WildResourceNode(ASH, position, 1L));

        assertTrue(data.hasSameTypeWithin(ASH, position.offset(3, 0, 4), 6));
        assertFalse(data.hasSameTypeWithin(ASH, position.offset(3, 0, 4), 5));
        assertTrue(data.removeNode(position).isPresent());
        assertEquals(0, data.countInChunk(new ChunkPos(position), ASH));
    }
}
