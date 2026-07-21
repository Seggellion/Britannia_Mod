package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.banner.renderdata.BannerPreviewRenderState;
import com.seggellion.britannia_mod.dye.preview.DyeApplicationResultCode;
import com.seggellion.britannia_mod.dye.preview.DyePreviewDisplayData;
import com.seggellion.britannia_mod.dye.preview.DyePreviewViewModel;
import com.seggellion.britannia_mod.dye.service.MatchType;
import com.seggellion.britannia_mod.network.payload.dye.C2SCancelDyePreviewPayload;
import com.seggellion.britannia_mod.network.payload.dye.C2SConfirmDyeApplicationPayload;
import com.seggellion.britannia_mod.network.payload.dye.S2CDyeApplicationResultPayload;
import com.seggellion.britannia_mod.network.payload.dye.S2COpenDyePreviewPayload;
import io.netty.buffer.Unpooled;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class DyePreviewPayloadAndViewModelTest {
    private static final UUID SESSION = UUID.fromString("f112c615-6768-44d5-8c65-4922dbf1540a");

    @Test
    void payloadIdsArePersistentAndDirectionsHaveMinimalFields() {
        assertEquals("britannia_mod:open_dye_preview", S2COpenDyePreviewPayload.TYPE_ID.toString());
        assertEquals("britannia_mod:confirm_dye_application", C2SConfirmDyeApplicationPayload.TYPE_ID.toString());
        assertEquals("britannia_mod:cancel_dye_preview", C2SCancelDyePreviewPayload.TYPE_ID.toString());
        assertEquals("britannia_mod:dye_application_result", S2CDyeApplicationResultPayload.TYPE_ID.toString());
        assertArrayEquals(new Class<?>[] {UUID.class}, componentTypes(C2SConfirmDyeApplicationPayload.class));
        assertArrayEquals(new Class<?>[] {UUID.class}, componentTypes(C2SCancelDyePreviewPayload.class));
        assertFalse(List.of(C2SConfirmDyeApplicationPayload.class.getRecordComponents()).toString()
                .matches("(?is).*(colour|pigment|material|banner|tub|match|distance).*"));
    }

    @Test
    void allFourStreamCodecsRoundTrip() {
        S2COpenDyePreviewPayload open = new S2COpenDyePreviewPayload(
                SESSION, explicit(), currentRender(), proposedRender(), 30_000);
        assertEquals(open, roundTrip(open, S2COpenDyePreviewPayload.STREAM_CODEC));
        C2SConfirmDyeApplicationPayload confirm = new C2SConfirmDyeApplicationPayload(SESSION);
        assertEquals(confirm, roundTrip(confirm, C2SConfirmDyeApplicationPayload.STREAM_CODEC));
        C2SCancelDyePreviewPayload cancel = new C2SCancelDyePreviewPayload(SESSION);
        assertEquals(cancel, roundTrip(cancel, C2SCancelDyePreviewPayload.STREAM_CODEC));
        S2CDyeApplicationResultPayload result = new S2CDyeApplicationResultPayload(
                SESSION, DyeApplicationResultCode.SUCCESS, true);
        assertEquals(result, roundTrip(result, S2CDyeApplicationResultPayload.STREAM_CODEC));
    }

    @Test
    void malformedConstructionIsRejectedSafely() {
        assertThrows(NullPointerException.class, () -> new C2SConfirmDyeApplicationPayload(null));
        assertThrows(NullPointerException.class, () -> new C2SCancelDyePreviewPayload(null));
        assertThrows(IllegalArgumentException.class, () -> new S2COpenDyePreviewPayload(
                SESSION, explicit(), currentRender(), proposedRender(), 0));
        assertThrows(IllegalArgumentException.class, () -> new DyePreviewDisplayData(
                "banner", "material", "mount", "current", Optional.empty(), "pigment", "new",
                MatchType.NEAREST_COLOUR, Double.NaN, 0, 0, false, false));
    }

    @Test
    void viewModelProjectsExplicitNearestSwatchesAndButtonLifecycle() {
        AtomicLong now = new AtomicLong(1_000);
        DyePreviewViewModel explicit = new DyePreviewViewModel(SESSION, explicit(), 30_000, now::get);
        assertEquals("screen.britannia_mod.dye_preview.exact_match", explicit.matchLabelKey());
        assertEquals(0xAA1122, explicit.displayData().currentSrgb());
        assertEquals(0xCC3344, explicit.displayData().newSrgb());
        assertTrue(explicit.displayData().placeholder());
        assertTrue(explicit.applyEnabled());
        assertTrue(explicit.beginConfirmation());
        assertFalse(explicit.applyEnabled());
        assertFalse(explicit.beginConfirmation());

        DyePreviewDisplayData nearestData = new DyePreviewDisplayData(
                "banner", "material", "mount", "current", Optional.empty(), "pigment", "new",
                MatchType.NEAREST_COLOUR, 0.2, 0x010203, 0x040506, false, true);
        DyePreviewViewModel nearest = new DyePreviewViewModel(UUID.randomUUID(), nearestData, 30_000, now::get);
        assertEquals("screen.britannia_mod.dye_preview.closest_match", nearest.matchLabelKey());
        now.addAndGet(30_000);
        assertTrue(nearest.expired());
        assertFalse(nearest.applyEnabled());
    }

    @Test
    void localizationScreenScopeHandlerEnqueueAndDedicatedServerIsolationAreAudited() throws Exception {
        JsonObject lang = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/britannia_mod/lang/en_us.json"))).getAsJsonObject();
        for (String key : List.of(
                "screen.britannia_mod.dye_preview.title", "screen.britannia_mod.dye_preview.banner",
                "screen.britannia_mod.dye_preview.material", "screen.britannia_mod.dye_preview.mount",
                "screen.britannia_mod.dye_preview.current_colour", "screen.britannia_mod.dye_preview.current_pigment",
                "screen.britannia_mod.dye_preview.tub_pigment", "screen.britannia_mod.dye_preview.new_colour",
                "screen.britannia_mod.dye_preview.exact_match", "screen.britannia_mod.dye_preview.closest_match",
                "screen.britannia_mod.dye_preview.apply", "screen.britannia_mod.dye_preview.cancel",
                "message.britannia_mod.dye_preview.expired", "message.britannia_mod.dye_preview.stale",
                "message.britannia_mod.dye_preview.hand_changed", "message.britannia_mod.dye_preview.tub_changed",
                "message.britannia_mod.dye_preview.banner_changed", "message.britannia_mod.dye_preview.registry_changed",
                "message.britannia_mod.dye_preview.already_dyed", "message.britannia_mod.dye_preview.applied",
                "message.britannia_mod.dye_preview.tub_empty", "message.britannia_mod.dye_preview.tub_depleted",
                "message.britannia_mod.dye_preview.banner_unconfigured",
                "message.britannia_mod.dye_preview.no_compatible_colour",
                "message.britannia_mod.dye_preview.application_failure")) {
            assertTrue(lang.has(key), key);
        }

        String payloadSources = readTree(Path.of(
                "src/main/java/com/seggellion/britannia_mod/network/payload/dye"));
        assertFalse(payloadSources.contains("net.minecraft.client"));
        assertFalse(payloadSources.contains("DyePreviewScreen"));
        assertFalse(payloadSources.contains("Minecraft.getInstance"));
        String commonPreviewSources = readTree(Path.of(
                "src/main/java/com/seggellion/britannia_mod/dye/preview"));
        assertFalse(commonPreviewSources.contains("net.minecraft.client"));
        assertFalse(commonPreviewSources.contains("com.mojang.blaze3d"));

        String screen = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/client/screen/DyePreviewScreen.java"));
        assertTrue(screen.contains("renderItem"));
        assertTrue(screen.contains("graphics.fill"));
        assertFalse(screen.matches("(?is).*(GeoItemRenderer|BlockEntityWithoutLevelRenderer|dye_mask|static_overlay).*"));
        String network = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/network/NetworkHandler.java"));
        assertTrue(network.contains("C2SConfirmDyeApplicationPayload"));
        assertTrue(network.contains("C2SCancelDyePreviewPayload"));
        assertTrue(network.contains("context.enqueueWork"));
        assertTrue(network.contains("FMLLoader.getDist().isClient()"));
    }

    private static DyePreviewDisplayData explicit() {
        return new DyePreviewDisplayData(
                "banner.key", "material.key", "mount.key", "current.key", Optional.of("source.key"),
                "tub.key", "new.key", MatchType.EXPLICIT_MAPPING, 0.1,
                0xAA1122, 0xCC3344, true, false);
    }

    private static BannerPreviewRenderState currentRender() {
        return new BannerPreviewRenderState(
                com.seggellion.britannia_mod.banner.api.BannerDefinitionId.parse("britannia_mod:ward_of_serpents"),
                com.seggellion.britannia_mod.dye.api.FabricMaterialId.parse("britannia_mod:cotton"),
                com.seggellion.britannia_mod.dye.api.ResolvedColourId.parse("britannia_mod:cotton_natural"),
                com.seggellion.britannia_mod.banner.api.MountId.parse("britannia_mod:brass"));
    }

    private static BannerPreviewRenderState proposedRender() {
        BannerPreviewRenderState current = currentRender();
        return new BannerPreviewRenderState(current.bannerDefinitionId(), current.materialId(),
                com.seggellion.britannia_mod.dye.api.ResolvedColourId.parse("britannia_mod:cotton_red"),
                current.mountId());
    }

    private static Class<?>[] componentTypes(Class<?> recordType) {
        return java.util.Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getType)
                .toArray(Class<?>[]::new);
    }

    private static <T> T roundTrip(T value, net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, T> codec) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buffer, value);
            return codec.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static String readTree(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java")).map(path -> {
                try { return Files.readString(path); } catch (Exception exception) { throw new RuntimeException(exception); }
            }).reduce("", String::concat);
        }
    }
}
