package com.seggellion.britannia_mod.wildresource;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildResourceReconciliationTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("britannia_mod", "test");
    private static final BlockPos POSITION = new BlockPos(34, 65, 51);

    @Test
    void validAndTemporarilyIncompleteNodesRemainAccounted() {
        assertPreserved(WildResourceEntry.ExistingNodeState.VALID,
                WildResourceReconciliation.Outcome.PRESERVED);
        assertPreserved(WildResourceEntry.ExistingNodeState.DEFER,
                WildResourceReconciliation.Outcome.DEFERRED);
    }

    @Test
    void externallyRemovedOrEnvironmentallyInvalidNodesRecoverWithCooldown() {
        assertRemoved(WildResourceEntry.ExistingNodeState.MISSING_OR_REPLACED,
                WildResourceReconciliation.Outcome.LEDGER_REMOVED);
        assertRemoved(WildResourceEntry.ExistingNodeState.OWNED_INVALID,
                WildResourceReconciliation.Outcome.REMOVE_OWNED_BLOCK);
    }

    @Test
    void unknownPersistedResourceIdFailsSafely() {
        WildResourceSavedData data = dataWithNode();
        WildResourceNode node = data.nodeAt(POSITION).orElseThrow();

        assertEquals(
                WildResourceReconciliation.Outcome.UNKNOWN_REMOVED,
                WildResourceReconciliation.reconcile(
                        data, node, null, WildResourceEntry.ExistingNodeState.MISSING_OR_REPLACED, 500L
                )
        );
        assertTrue(data.nodeAt(POSITION).isEmpty());
    }

    private static void assertPreserved(
            WildResourceEntry.ExistingNodeState state,
            WildResourceReconciliation.Outcome expected
    ) {
        WildResourceSavedData data = dataWithNode();
        WildResourceNode node = data.nodeAt(POSITION).orElseThrow();
        assertEquals(expected, WildResourceReconciliation.reconcile(data, node, entry(), state, 500L));
        assertTrue(data.nodeAt(POSITION).isPresent());
        assertEquals(WildResourceSavedData.UNSCHEDULED, data.nextAttempt(new ChunkPos(POSITION), ID));
    }

    private static void assertRemoved(
            WildResourceEntry.ExistingNodeState state,
            WildResourceReconciliation.Outcome expected
    ) {
        WildResourceSavedData data = dataWithNode();
        WildResourceNode node = data.nodeAt(POSITION).orElseThrow();
        assertEquals(expected, WildResourceReconciliation.reconcile(data, node, entry(), state, 500L));
        assertTrue(data.nodeAt(POSITION).isEmpty());
        assertEquals(500L, data.nextAttempt(new ChunkPos(POSITION), ID));
    }

    private static WildResourceSavedData dataWithNode() {
        WildResourceSavedData data = new WildResourceSavedData();
        data.registerNode(new WildResourceNode(ID, POSITION, 1L));
        return data;
    }

    private static WildResourceEntry entry() {
        return new WildResourceEntry(
                ID, 1, 1, new WildResourceTuning(20, 20, 40, 40, 1, 0),
                (level, chunk, random) -> POSITION,
                WildResourceEntry.PlacementRule.ALLOW,
                WildResourceEntry.PlacementRule.ALLOW,
                WildResourceEntry.PlacementRule.ALLOW,
                WildResourceEntry.PlacementRule.ALLOW,
                (level, position) -> WildResourceEntry.ExistingNodeState.VALID,
                (level, position) -> true,
                WildResourceEntry.HarvestStrategy.DISABLED,
                WildResourceEntry.LootStrategy.NONE
        );
    }
}
