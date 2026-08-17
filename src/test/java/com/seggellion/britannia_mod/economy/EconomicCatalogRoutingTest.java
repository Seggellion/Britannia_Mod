package com.seggellion.britannia_mod.economy;

import com.seggellion.britannia_mod.npc.NpcType;
import com.seggellion.britannia_mod.service.EconomicNpcRegistryCache;
import com.seggellion.britannia_mod.service.EconomicNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.EconomicNpcTypeDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which catalog an economic projection asks for.
 *
 * <p>The original defect was a routing question answered with the wrong fact. Every economic NPC
 * carries a type key, so keying the retail branch on "does it have one" sent Traders to the
 * NPC-sells-to-player catalog — which holds no listing for any trader key and answered every one
 * with an empty rows array, surfacing as "I am not interested in anything you have" for the whole
 * class of NPC. The registry's {@code kind} is the fact that actually distinguishes them.
 */
class EconomicCatalogRoutingTest {

    @AfterEach
    void resetRegistry() {
        EconomicNpcRegistryCache.clear();
    }

    private static void register(String key, String kind) {
        EconomicNpcRegistryCache.replace(new EconomicNpcRegistrySnapshot(
                EconomicNpcRegistrySnapshot.SUPPORTED_SCHEMA_VERSION, "test-revision",
                Map.of(key, new EconomicNpcTypeDefinition(
                        key, key, kind, "fisher", "britannia_mod:" + key, true, true, 1L))));
    }

    @Test
    void aTraderKindQuotesTheBuybackCatalog() {
        register("fish_trader", "trader");
        assertTrue(ServerCatalogService.tradesWithPlayers("fish_trader", NpcType.TRADER));
    }

    @Test
    void aVendorKindKeepsTheRetailCatalog() {
        register("baker_vendor", "vendor");
        assertFalse(ServerCatalogService.tradesWithPlayers("baker_vendor", NpcType.MERCHANT));
    }

    /**
     * {@code kind} outranks the entity. A Trader materialized through a Vendor entity type — which
     * Shard Admin can do by editing {@code minecraft_entity_type_key} without a code change —
     * still buys from players.
     */
    @Test
    void theRegistryOutranksTheEntityType() {
        register("fish_trader", "trader");
        assertTrue(ServerCatalogService.tradesWithPlayers("fish_trader", NpcType.MERCHANT));

        register("baker_vendor", "vendor");
        assertFalse(ServerCatalogService.tradesWithPlayers("baker_vendor", NpcType.TRADER));
    }

    /**
     * Before the registry bootstrap lands there is no kind to read. Falling back to the entity
     * type keeps a Trader buying rather than silently reverting to the retail endpoint that
     * caused the bug — the reconciler only ever spawns Traders as trader entities.
     */
    @Test
    void anUnsyncedRegistryFallsBackToTheEntityType() {
        EconomicNpcRegistryCache.clear();
        assertTrue(ServerCatalogService.tradesWithPlayers("fish_trader", NpcType.TRADER));
        assertFalse(ServerCatalogService.tradesWithPlayers("baker_vendor", NpcType.MERCHANT));
    }

    /**
     * Exhaustive over the routing input space: {@link NpcType} has exactly two values and
     * {@code kind} exactly two (constrained at model and DB level), so these four cases plus the
     * two unsynced ones above are every reachable combination. Neither misroute — vendor to
     * buyback, or trader to retail — is producible from any of them.
     */
    @Test
    void noCombinationOfKindAndEntityTypeCanMisroute() {
        for (NpcType entityType : NpcType.values()) {
            register("some_trader", "trader");
            assertTrue(ServerCatalogService.tradesWithPlayers("some_trader", entityType),
                    "a trader must never be sent to the retail catalog (entity " + entityType + ")");

            register("some_vendor", "vendor");
            assertFalse(ServerCatalogService.tradesWithPlayers("some_vendor", entityType),
                    "a vendor must never be sent to the buyback catalog (entity " + entityType + ")");
        }
    }

    /**
     * A key the registry does not carry falls back rather than throwing — the snapshot is
     * replaced wholesale, so a type added in Rails is briefly absent here.
     */
    @Test
    void anAbsentKeyInAPopulatedRegistryStillFallsBackSafely() {
        register("fish_trader", "trader");
        assertTrue(ServerCatalogService.tradesWithPlayers("brand_new_trader", NpcType.TRADER));
        assertFalse(ServerCatalogService.tradesWithPlayers("brand_new_vendor", NpcType.MERCHANT));
    }
}
