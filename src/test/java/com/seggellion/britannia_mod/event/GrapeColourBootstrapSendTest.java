package com.seggellion.britannia_mod.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.winery.GrapeColor;
import com.seggellion.britannia_mod.winery.GrapeVariety;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The login order is the whole of this fix's correctness: a colour catalogue sent before the world
 * bootstrap is applied would carry the two compiled-in Concords and leave the client believing that
 * was everything the shard publishes -- the same two-entry view that made every variety render green.
 */
class GrapeColourBootstrapSendTest {

    private static final String DARK_VARIETY = "pinot_noir";
    private static final String GREEN_VARIETY = "chardonnay";

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void startFromAFreshProcess() {
        GrapeVarietyManager.resetToBuiltIns();
    }

    @AfterEach
    void leaveNothingBehind() {
        GrapeVarietyManager.resetToBuiltIns();
    }

    @Test
    void theCatalogueSentToAClientAlwaysDescribesTheCatalogueJustInstalled() {
        // Before the bootstrap lands, the process knows only the built-ins.
        assertEquals(2, GrapeVarietyManager.getAllVarieties().size());

        List<GrapeVariety> shardCatalogue = List.of(
                variety(DARK_VARIETY, GrapeColor.DARK_PURPLE),
                variety(GREEN_VARIETY, GrapeColor.LIGHT_GREEN));

        // The install and the snapshot are one call, so no ordering exists in which the payload is
        // built from the pre-bootstrap state.
        Map<String, GrapeColor> sent = WorldBootstrapHandler.installGrapeCatalogue(shardCatalogue);

        assertEquals(GrapeColor.DARK_PURPLE, sent.get(DARK_VARIETY),
                "the payload must describe the catalogue that was just installed");
        assertEquals(GrapeColor.LIGHT_GREEN, sent.get(GREEN_VARIETY));
        assertEquals(GrapeColor.DARK_PURPLE, GrapeVarietyManager.getVariety(DARK_VARIETY).colorType(),
                "the server's own catalogue must already be live when the payload is built");
        assertTrue(sent.containsKey("concord_green"),
                "the built-in fallbacks stay available to clients");
        assertTrue(sent.containsKey("concord_red"));
    }

    @Test
    void reapplyingABootstrapToAConnectedPlayerReplacesRatherThanAccumulates() {
        WorldBootstrapHandler.installGrapeCatalogue(List.of(
                variety(DARK_VARIETY, GrapeColor.DARK_PURPLE),
                variety("withdrawn_next_time", GrapeColor.YELLOW)));

        Map<String, GrapeColor> second = WorldBootstrapHandler.installGrapeCatalogue(List.of(
                variety(DARK_VARIETY, GrapeColor.BLUE)));

        assertEquals(GrapeColor.BLUE, second.get(DARK_VARIETY),
                "a refreshed bootstrap must send the new colour");
        assertTrue(!second.containsKey("withdrawn_next_time"),
                "a variety the refreshed catalogue drops must not still be sent");
        // Built-ins plus the one shard variety.
        assertEquals(3, second.size());
    }

    @Test
    void aBootstrapThatPublishedNoGrapesStillSendsTheBuiltInFallbacks() {
        Map<String, GrapeColor> sent = WorldBootstrapHandler.installGrapeCatalogue(List.of());

        assertEquals(2, sent.size(), "an offline or empty catalogue must still leave a usable fallback");
        assertEquals(GrapeColor.GREEN, sent.get("concord_green"));
        assertEquals(GrapeColor.RED, sent.get("concord_red"));
    }

    private static GrapeVariety variety(String id, GrapeColor color) {
        return new GrapeVariety(id, id, 3, 0.2f, 0.1f, 0.2f, 0.5f, "Temperate", 25, 625, 0xFFFFFF, 4, color);
    }
}
