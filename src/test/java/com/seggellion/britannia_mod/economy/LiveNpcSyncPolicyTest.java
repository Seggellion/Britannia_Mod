package com.seggellion.britannia_mod.economy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The identity contract between this server and Rails, asserted directly.
 *
 * <p>The defect these lock down: a Rails-authoritative Fish Trader was registered into Rails'
 * legacy live-NPC table under its Minecraft entity UUID, with that same UUID reported as its spawn
 * block. Every re-materialization of the mob therefore minted another permanent, active NPC row --
 * two "Chaney" fish traders on one post, and a city population counting both.
 */
class LiveNpcSyncPolicyTest {
    private static final UUID WORLD_NPC = UUID.fromString("2ee04fc1-b566-44ad-a115-ab305e115acb");

    @Test
    void anEconomicProjectionIsRailsAuthoritativeAndIsNotAnnouncedByThisServer() {
        assertTrue(LiveNpcSyncPolicy.railsAuthoritative(WORLD_NPC, "fish_trader"));
    }

    @Test
    void aLegacySpawnBlockMobIsNotRailsAuthoritative() {
        // No World NPC identity at all: a TownPerson or a legacy trader this world invented.
        assertFalse(LiveNpcSyncPolicy.railsAuthoritative(null, null));
        assertFalse(LiveNpcSyncPolicy.railsAuthoritative(null, "fish_trader"));
    }

    @Test
    void aPartiallyReconciledEntityIsNotTreatedAsAuthoritative() {
        // Both fields are written together from the assignment snapshot. One without the other is
        // an entity mid-reconciliation, and guessing about it is how duplicates start.
        assertFalse(LiveNpcSyncPolicy.railsAuthoritative(WORLD_NPC, null));
        assertFalse(LiveNpcSyncPolicy.railsAuthoritative(WORLD_NPC, ""));
        assertFalse(LiveNpcSyncPolicy.railsAuthoritative(WORLD_NPC, "   "));
    }

    @Test
    void aLegacyTraderReportsItsSpawnBlocksStableId() {
        // TraderSpawnBlockEntity.sourceTag(): the prefix plus its NBT-persisted SourceId, undashed.
        String sourceId = "e4986c0d7d9b46918183c437d03f1c68";

        assertEquals(sourceId, LiveNpcSyncPolicy.legacySpawnSourceId(
                List.of("britannia_trader_spawn", "trader_source_" + sourceId, "trader_type_fish_trader")));
    }

    @Test
    void anEntityWithNoOwningSpawnBlockReportsNoSpawnSource() {
        // The regression: this used to return the entity's own UUID, which Rails stored as the
        // NPC's permanent spawn identity. Every authoritative projection took this branch, which
        // is why the corrupted production rows have spawn_block_id == npc_id.
        assertNull(LiveNpcSyncPolicy.legacySpawnSourceId(Set.of()));
        assertNull(LiveNpcSyncPolicy.legacySpawnSourceId(Set.of("britannia_trader_spawn")));
        assertNull(LiveNpcSyncPolicy.legacySpawnSourceId(null));
    }

    @Test
    void anEmptySourceTagIsNotASpawnSource() {
        assertNull(LiveNpcSyncPolicy.legacySpawnSourceId(Set.of("trader_source_")));
    }

    @Test
    void theSpawnSourceIsStableAcrossEveryRestartOfTheSameBlock() {
        // The point of the whole contract: the same block's tag yields the same id every time,
        // where an entity UUID would not.
        Set<String> tags = Set.of("trader_source_abc123");

        assertEquals(LiveNpcSyncPolicy.legacySpawnSourceId(tags),
                LiveNpcSyncPolicy.legacySpawnSourceId(tags));
    }
}
