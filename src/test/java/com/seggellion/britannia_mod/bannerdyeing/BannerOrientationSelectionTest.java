package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.placement.BannerOrientationPreferenceService;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.network.payload.banner.S2CBannerPlacementOrientationPayload;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BannerOrientationSelectionTest {
    private final UUID player = UUID.randomUUID();

    @AfterEach
    void clear() {
        BannerOrientationPreferenceService.clearAll();
    }

    @Test
    void stableOrderDefaultsParallelAndCyclesDeterministically() {
        List<BannerOrientation> reversed = List.of(
                BannerOrientation.WALL_PERPENDICULAR, BannerOrientation.WALL_PARALLEL);
        assertEquals(List.of(BannerOrientation.WALL_PARALLEL, BannerOrientation.WALL_PERPENDICULAR),
                BannerOrientation.orderedSupported(reversed));
        assertEquals(BannerOrientation.WALL_PARALLEL,
                BannerOrientationPreferenceService.currentNormalized(player, reversed));
        assertEquals(BannerOrientation.WALL_PERPENDICULAR,
                BannerOrientationPreferenceService.cycle(player, reversed).orientation());
        assertEquals(BannerOrientation.WALL_PARALLEL,
                BannerOrientationPreferenceService.cycle(player, reversed).orientation());
    }

    @Test
    void singleOrientationReportsOnlyModeAndDefinitionChangeNormalizes() {
        var perpendicular = BannerOrientationPreferenceService.cycle(
                player, List.of(BannerOrientation.WALL_PERPENDICULAR));
        assertEquals(BannerOrientation.WALL_PERPENDICULAR, perpendicular.orientation());
        assertTrue(perpendicular.onlySupportedMode());
        assertFalse(perpendicular.changed());

        assertEquals(BannerOrientation.WALL_PARALLEL,
                BannerOrientationPreferenceService.currentNormalized(
                        player, List.of(BannerOrientation.WALL_PARALLEL)));
    }

    @Test
    void clearRemovesRuntimePreferenceAndItemStateHasNoOrientationField() {
        BannerOrientationPreferenceService.cycle(player, List.of(
                BannerOrientation.WALL_PARALLEL, BannerOrientation.WALL_PERPENDICULAR));
        BannerOrientationPreferenceService.clear(player);
        assertEquals(BannerOrientation.WALL_PARALLEL,
                BannerOrientationPreferenceService.currentNormalized(player, List.of(
                        BannerOrientation.WALL_PARALLEL, BannerOrientation.WALL_PERPENDICULAR)));
        assertFalse(Arrays.stream(BannerInstanceState.class.getRecordComponents())
                .anyMatch(component -> component.getName().toLowerCase().contains("orientation")));
    }

    @Test
    void selectedOrientationPayloadContainsDisplayStateOnlyAndRoundTrips() {
        var payload = new S2CBannerPlacementOrientationPayload(BannerOrientation.WALL_PERPENDICULAR);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            S2CBannerPlacementOrientationPayload.STREAM_CODEC.encode(buffer, payload);
            assertEquals(payload, S2CBannerPlacementOrientationPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
        assertEquals("britannia_mod:banner_placement_orientation", payload.type().id().toString());
    }

    @Test
    void sneakUseCyclesOnServerWithS2CDisplaySyncAndNoC2SOrientationPacket() throws Exception {
        Path root = Path.of(System.getProperty("britannia.projectDir", "."),"src/main/java/com/seggellion/britannia_mod");
        String service = Files.readString(root.resolve("banner/placement/BannerPlacementService.java"));
        assertTrue(service.contains("player.isShiftKeyDown()"));
        assertTrue(service.contains("BannerOrientationPreferenceService.cycle"));
        assertTrue(service.contains("S2CBannerPlacementOrientationPayload"));
        String network = Files.readString(root.resolve("network/NetworkHandler.java"));
        assertFalse(network.contains("C2SBannerPlacementOrientation"));
        assertFalse(Files.exists(root.resolve(
                "network/payload/banner/C2SBannerPlacementOrientationPayload.java")));

        String lifecycle = Files.readString(
                root.resolve("banner/placement/BannerOrientationPreferenceLifecycle.java"));
        assertTrue(lifecycle.contains("PlayerLoggedOutEvent"));
        assertTrue(lifecycle.contains("ServerStoppingEvent"));
        assertTrue(lifecycle.contains("clearAll"));
    }
}
