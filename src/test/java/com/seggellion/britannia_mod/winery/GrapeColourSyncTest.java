package com.seggellion.britannia_mod.winery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.farming.GrapeVisualResolver;
import com.seggellion.britannia_mod.network.payload.ClientboundSyncGrapeColorsPayload;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A dedicated client is the only process that renders a grape and the only one the shard catalogue
 * never reached: {@code loadFromBootstrap} needs a {@code ServerPlayer}, so a client held the two
 * compiled-in Concords and silently resolved every shard variety to Concord green.
 *
 * <p>These tests deliberately start from that state. Every other grape suite in the project calls
 * {@code loadFromBootstrap} in {@code @BeforeAll} and so pre-populates exactly the table production
 * clients lack, which is why the defect shipped against a green suite. The fixture here resets the
 * static registry around each test instead, mirroring a client connecting and disconnecting.
 */
class GrapeColourSyncTest {

    private static final String DARK_VARIETY = "pinot_noir";
    private static final String GREEN_VARIETY = "chardonnay";

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * The variety registry is static and the suite shares one JVM, so a neighbouring suite's
     * catalogue would otherwise be indistinguishable from a working fix.
     */
    @BeforeEach
    void startFromAFreshClient() {
        GrapeVarietyManager.resetToBuiltIns();
    }

    @AfterEach
    void leaveNothingBehind() {
        GrapeVarietyManager.resetToBuiltIns();
    }

    @Test
    void aClientWithOnlyTheBuiltInsCannotColourAShardVarietyUntilTheCatalogueArrives() {
        // Before: exactly the production dedicated-client state.
        GrapeVarietyManager.GrapeColorResolution before = GrapeVarietyManager.resolveColor(DARK_VARIETY);
        assertTrue(before.fallback(), "a built-in-only client must not claim to know " + DARK_VARIETY);
        assertEquals(GrapeColor.GREEN, before.color(), "the Concord fallback is what made every vine green");
        assertNull(GrapeVarietyManager.getVarietyOrNull(DARK_VARIETY), "no full record should be invented");

        GrapeVarietyManager.replaceSyncedColors(Map.of(DARK_VARIETY, GrapeColor.DARK_PURPLE));

        GrapeVarietyManager.GrapeColorResolution after = GrapeVarietyManager.resolveColor(DARK_VARIETY);
        assertFalse(after.fallback(), "the synced catalogue should resolve the variety outright");
        assertEquals(GrapeColor.DARK_PURPLE, after.color());
        assertEquals(DARK_VARIETY, after.varietyId());
        // Colour only: the client learns how to draw the grape and nothing about its agronomy.
        assertNull(GrapeVarietyManager.getVarietyOrNull(DARK_VARIETY),
                "a synced colour must not masquerade as a full variety record");
    }

    @Test
    void theCodecRoundTripsEveryColourWithItsVarietyId() {
        Map<String, GrapeColor> catalogue = new LinkedHashMap<>();
        for (GrapeColor color : GrapeColor.values()) {
            catalogue.put("variety_" + color.getSerializedName(), color);
        }

        Map<String, GrapeColor> decoded = roundTrip(ClientboundSyncGrapeColorsPayload.of(catalogue)).asCatalogue();

        assertEquals(catalogue, decoded, "every GrapeColor must survive the wire unchanged");
        assertEquals(GrapeColor.values().length, decoded.size());
    }

    @Test
    void aSecondCatalogueReplacesStaleEntriesAndTheBuiltInsAlwaysSurvive() {
        Map<String, GrapeColor> first = new LinkedHashMap<>();
        first.put(DARK_VARIETY, GrapeColor.DARK_PURPLE);
        first.put("retired_on_the_next_shard", GrapeColor.YELLOW);
        GrapeVarietyManager.replaceSyncedColors(first);
        assertEquals(GrapeColor.YELLOW, GrapeVarietyManager.resolveColor("retired_on_the_next_shard").color());

        // Reconnecting to a shard whose catalogue differs.
        GrapeVarietyManager.replaceSyncedColors(Map.of(DARK_VARIETY, GrapeColor.BLUE));

        assertEquals(GrapeColor.BLUE, GrapeVarietyManager.resolveColor(DARK_VARIETY).color(),
                "a re-synced variety must take the new shard's colour");
        assertEquals(1, GrapeVarietyManager.syncedColorCount(), "stale rows accumulated across reconnects");

        GrapeVarietyManager.GrapeColorResolution retired =
                GrapeVarietyManager.resolveColor("retired_on_the_next_shard");
        assertTrue(retired.fallback(), "a variety the new shard omits must stop resolving to its old colour");
        assertEquals(GrapeColor.GREEN, retired.color(), "an unknown id still falls back benignly");

        // The compiled-in Concords are a full record, so they outrank any synced colour and survive.
        assertEquals(GrapeColor.GREEN, GrapeVarietyManager.resolveColor("concord_green").color());
        assertEquals(GrapeColor.RED, GrapeVarietyManager.resolveColor("concord_red").color());
        assertFalse(GrapeVarietyManager.resolveColor("concord_red").fallback());
    }

    @Test
    void malformedCatalogueDataIsRejectedOrIgnoredWithoutReachingTheRenderPath() {
        // An unknown colour name decodes to the bootstrap parser's own fallback rather than throwing,
        // so a shard that adds a colour cannot drop an older client's connection.
        FriendlyByteBuf lenientBuf = buffer();
        lenientBuf.writeVarInt(2);
        lenientBuf.writeUtf("sent_from_the_future", ClientboundSyncGrapeColorsPayload.MAX_ID_LENGTH);
        lenientBuf.writeUtf("ultraviolet", ClientboundSyncGrapeColorsPayload.MAX_ID_LENGTH);
        lenientBuf.writeUtf("", ClientboundSyncGrapeColorsPayload.MAX_ID_LENGTH);
        lenientBuf.writeUtf("green", ClientboundSyncGrapeColorsPayload.MAX_ID_LENGTH);
        ClientboundSyncGrapeColorsPayload lenient =
                ClientboundSyncGrapeColorsPayload.STREAM_CODEC.decode(lenientBuf);
        assertEquals(1, lenient.entries().size(), "a blank variety id must be dropped, not stored");
        assertEquals(ClientboundSyncGrapeColorsPayload.UNKNOWN_COLOR_FALLBACK,
                lenient.entries().get(0).color());

        // A declared count beyond the bound is refused outright rather than allocated.
        FriendlyByteBuf oversized = buffer();
        oversized.writeVarInt(ClientboundSyncGrapeColorsPayload.MAX_ENTRIES + 1);
        assertThrows(DecoderException.class,
                () -> ClientboundSyncGrapeColorsPayload.STREAM_CODEC.decode(oversized));

        // Blank and null rows never reach the client-side table.
        Map<String, GrapeColor> ragged = new LinkedHashMap<>();
        ragged.put(DARK_VARIETY, GrapeColor.DARK_PURPLE);
        ragged.put("  ", GrapeColor.RED);
        ragged.put("no_colour", null);
        GrapeVarietyManager.replaceSyncedColors(ragged);
        assertEquals(1, GrapeVarietyManager.syncedColorCount());
        assertEquals(GrapeColor.DARK_PURPLE, GrapeVarietyManager.resolveColor(DARK_VARIETY).color());

        GrapeVarietyManager.replaceSyncedColors(null);
        assertEquals(0, GrapeVarietyManager.syncedColorCount(), "a null catalogue must clear, not crash");
    }

    @Test
    void theProductionCatalogueSizeFitsInsideThePayloadBound() {
        Map<String, GrapeColor> catalogue = new LinkedHashMap<>();
        for (int i = 0; i < 945; i++) {
            catalogue.put("shard_variety_" + i, GrapeColor.values()[i % GrapeColor.values().length]);
        }

        ClientboundSyncGrapeColorsPayload payload = ClientboundSyncGrapeColorsPayload.of(catalogue);
        assertEquals(945, payload.entries().size(), "the live catalogue must not be truncated");
        assertTrue(945 < ClientboundSyncGrapeColorsPayload.MAX_ENTRIES,
                "the bound must leave headroom above the live catalogue");

        FriendlyByteBuf buf = buffer();
        ClientboundSyncGrapeColorsPayload.STREAM_CODEC.encode(buf, payload);
        assertTrue(buf.writerIndex() < 1048576,
                "the encoded catalogue must stay inside the vanilla custom-payload ceiling, was "
                        + buf.writerIndex() + " bytes");
        assertEquals(catalogue, ClientboundSyncGrapeColorsPayload.STREAM_CODEC.decode(buf).asCatalogue());
    }

    @Test
    void afterSyncADarkVineRendersDarkAndAGreenOneStaysGreenAtEveryFruitingAge() {
        CropDefinition grapes = grapeCrop();
        Map<String, GrapeColor> catalogue = new LinkedHashMap<>();
        catalogue.put(DARK_VARIETY, GrapeColor.DARK_PURPLE);
        catalogue.put(GREEN_VARIETY, GrapeColor.GREEN);
        GrapeVarietyManager.replaceSyncedColors(catalogue);

        for (int age = 6; age <= 7; age++) {
            assertEquals(
                    model("grape_vine_stage_" + age + "_dark_purple"),
                    GrapeVisualResolver.modelLocation(grapes, age, DARK_VARIETY),
                    "dark variety rendered the wrong model at age " + age);
            assertEquals(
                    model("grape_vine_stage_" + age + "_green"),
                    GrapeVisualResolver.modelLocation(grapes, age, GREEN_VARIETY),
                    "green variety rendered the wrong model at age " + age);
            // The pair matters: a green variety resolves correctly even while the bug is present,
            // so only the contrast proves the catalogue is being read.
            assertNotEquals(
                    GrapeVisualResolver.modelLocation(grapes, age, GREEN_VARIETY),
                    GrapeVisualResolver.modelLocation(grapes, age, DARK_VARIETY),
                    "the two varieties collapsed onto one model at age " + age);
        }

        // Colourless stages must stay colour-invariant.
        for (int age = 0; age <= 5; age++) {
            assertEquals(
                    GrapeVisualResolver.modelLocation(grapes, age, GREEN_VARIETY),
                    GrapeVisualResolver.modelLocation(grapes, age, DARK_VARIETY),
                    "age " + age + " must not carry colour");
        }
    }

    @Test
    void theHarvestedItemTakesTheDarkBranchOnlyOnceTheCatalogueHasArrived() {
        // Mirrors the britannia_mod:grape_type item property in ClientEventHandler.
        assertFalse(isDarkItem(DARK_VARIETY),
                "before sync the item property cannot know the variety is dark");

        Map<String, GrapeColor> catalogue = new LinkedHashMap<>();
        catalogue.put(DARK_VARIETY, GrapeColor.DARK_PURPLE);
        catalogue.put(GREEN_VARIETY, GrapeColor.GREEN);
        GrapeVarietyManager.replaceSyncedColors(catalogue);

        assertTrue(isDarkItem(DARK_VARIETY), "harvested dark grapes must use the dark item texture");
        assertFalse(isDarkItem(GREEN_VARIETY), "a green variety must stay on the green item texture");
        assertFalse(isDarkItem("an_id_no_shard_publishes"), "an unknown id keeps the benign fallback");
    }

    @Test
    void aFullServerRecordAlwaysOutranksASyncedColour() {
        // Single-player shares this map in-process, so the integrated server's own catalogue must
        // win over anything a colour sync installed.
        GrapeVarietyManager.loadFromBootstrap(List.of(variety(DARK_VARIETY, GrapeColor.DARK_PURPLE)));
        GrapeVarietyManager.replaceSyncedColors(Map.of(DARK_VARIETY, GrapeColor.YELLOW));

        assertEquals(GrapeColor.DARK_PURPLE, GrapeVarietyManager.resolveColor(DARK_VARIETY).color());
        assertSame(GrapeVarietyManager.getVariety(DARK_VARIETY).colorType(),
                GrapeVarietyManager.resolveColor(DARK_VARIETY).color());
    }

    /** The production decision behind the {@code britannia_mod:grape_type} item property. */
    private static boolean isDarkItem(String varietyId) {
        return GrapeVarietyManager.isDarkGrapeVariety(varietyId);
    }

    private static ClientboundSyncGrapeColorsPayload roundTrip(ClientboundSyncGrapeColorsPayload payload) {
        FriendlyByteBuf buf = buffer();
        ClientboundSyncGrapeColorsPayload.STREAM_CODEC.encode(buf, payload);
        return ClientboundSyncGrapeColorsPayload.STREAM_CODEC.decode(buf);
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    private static GrapeVariety variety(String id, GrapeColor color) {
        return new GrapeVariety(id, id, 3, 0.2f, 0.1f, 0.2f, 0.5f, "Temperate", 25, 625, 0xFFFFFF, 4, color);
    }

    private static CropDefinition grapeCrop() {
        return CropRegistry.byId("grapes").orElseThrow();
    }

    private static ResourceLocation model(String name) {
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", "block/crops/grapes/" + name);
    }
}
