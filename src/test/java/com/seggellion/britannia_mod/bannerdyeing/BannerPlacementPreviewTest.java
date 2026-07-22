package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.item.BannerItemFactory;
import com.seggellion.britannia_mod.banner.preview.BannerPlacementPreviewPlanner;
import com.seggellion.britannia_mod.banner.preview.BannerPlacementPreviewStatus;
import com.seggellion.britannia_mod.banner.preview.BannerPlacementPreviewWorld;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderDataSnapshot;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.banner.structure.BannerCellRole;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone7RegisteredTestContent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BannerPlacementPreviewTest {
    private static RegistrySnapshot production;
    private static BannerRenderDataSnapshot renderData;
    private static BannerItem item;
    private static BannerItemFactory factory;

    @BeforeAll
    static void setup() throws Exception {
        Milestone7RegisteredTestContent.ensureRegistered();
        production = DyeResolverFixtures.productionSnapshot();
        renderData = BannerRenderDataSnapshot.fromRegistry(production);
        item = Milestone7RegisteredTestContent.banner();
        factory = new BannerItemFactory(item, item.stateAccess());
    }

    @Test
    void parallelAndPerpendicularExposeDimensionsOrientationMountAndOrderedCells() {
        ItemStack stack = stack("large_01");
        BannerInstanceState before = item.stateAccess().read(stack).orElseThrow();
        for (BannerOrientation orientation : BannerOrientation.values()) {
            PreviewWorld world = new PreviewWorld();
            world.protectionKnown = true;
            var preview = preview(stack, orientation, world);
            assertEquals(BannerPlacementPreviewStatus.VALID, preview.status());
            assertEquals(3, preview.dimensions().widthBlocks());
            assertEquals(2, preview.dimensions().heightBlocks());
            assertEquals(6, preview.cells().size());
            assertEquals(BannerCellRole.ANCHOR, preview.cells().getFirst().role());
            assertEquals(5, preview.cells().stream().filter(cell -> cell.role() == BannerCellRole.PART).count());
            assertEquals(orientation, preview.orientation());
            assertEquals(before.mountId(), preview.mountId());
            assertEquals(before, item.stateAccess().read(stack).orElseThrow());
        }
    }

    @Test
    void ordinaryClientSuccessRemainsUnknownForServerOnlyProtection() {
        var preview = preview(stack("joined_wards"), BannerOrientation.WALL_PERPENDICULAR, new PreviewWorld());
        assertEquals(BannerPlacementPreviewStatus.UNKNOWN_SERVER_PROTECTION, preview.status());
        assertTrue(preview.locallyPlaceable());
        assertEquals(1, preview.requiredSupportPositions().size());
    }

    @Test
    void blockedCellsAndInvalidSupportsAreClassifiedDeterministically() {
        ItemStack stack = stack("large_01");
        PreviewWorld baselineWorld = new PreviewWorld(); baselineWorld.protectionKnown = true;
        var baseline = preview(stack, BannerOrientation.WALL_PARALLEL, baselineWorld);
        PreviewWorld blocked = new PreviewWorld(); blocked.protectionKnown = true;
        blocked.blocked.add(baseline.cells().get(2).worldPosition());
        blocked.blocked.add(baseline.cells().get(5).worldPosition());
        var blockedPreview = preview(stack, BannerOrientation.WALL_PARALLEL, blocked);
        assertEquals(BannerPlacementPreviewStatus.BLOCKED_CELL, blockedPreview.status());
        assertEquals(baseline.cells().get(2).worldPosition(),
                blockedPreview.firstFailurePosition().orElseThrow());
        assertEquals(2, blockedPreview.cells().stream().filter(cell -> cell.blocked()).count());

        PreviewWorld support = new PreviewWorld(); support.protectionKnown = true;
        support.invalidSupports.add(baseline.requiredSupportPositions().get(1));
        var supportPreview = preview(stack, BannerOrientation.WALL_PARALLEL, support);
        assertEquals(BannerPlacementPreviewStatus.INVALID_SUPPORT, supportPreview.status());
        assertEquals(List.of(baseline.requiredSupportPositions().get(1)),
                supportPreview.invalidSupportPositions());
    }

    @Test
    void chunkAndBoundsFailuresAreTypedWithoutReadingUnavailableCells() {
        ItemStack stack = stack("large_01");
        PreviewWorld unavailable = new PreviewWorld(); unavailable.loaded = false;
        assertEquals(BannerPlacementPreviewStatus.REQUIRED_CHUNK_UNAVAILABLE,
                preview(stack, BannerOrientation.WALL_PERPENDICULAR, unavailable).status());
        assertEquals(0, unavailable.replaceableReads);

        PreviewWorld bounds = new PreviewWorld(); bounds.inBounds = false;
        assertEquals(BannerPlacementPreviewStatus.WORLD_BOUNDS,
                preview(stack, BannerOrientation.WALL_PARALLEL, bounds).status());
    }

    @Test
    void missingRenderDataProducesNoPlanAndRendererHasNoMutationOrPerFramePacket() throws Exception {
        BannerInstanceState state = item.stateAccess().read(stack("large_01")).orElseThrow();
        assertTrue(BannerPlacementPreviewPlanner.plan(null, state, BannerOrientation.WALL_PARALLEL,
                BlockPos.ZERO, Direction.NORTH, new PreviewWorld()).isEmpty());
        String renderer = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/client/banner/BannerPlacementGhostRenderer.java"));
        assertFalse(renderer.contains("setBlock("));
        assertFalse(renderer.contains("connection.send"));
        assertFalse(renderer.contains("PacketDistributor"));
        assertTrue(renderer.contains("UNKNOWN_SERVER_PROTECTION") || renderer.contains("serverProtectionKnownAllowed"));
    }

    private static com.seggellion.britannia_mod.banner.preview.BannerPlacementPreview preview(
            ItemStack stack, BannerOrientation orientation, PreviewWorld world) {
        BannerInstanceState state = item.stateAccess().read(stack).orElseThrow();
        return BannerPlacementPreviewPlanner.plan(renderData.banners().get(state.bannerDefinitionId()), state,
                orientation, BlockPos.ZERO, Direction.NORTH, world).orElseThrow();
    }

    private static ItemStack stack(String path) {
        return factory.naturalCottonAdminBanner(
                BannerDefinitionId.parse("britannia_mod:" + path), production, true).stack().orElseThrow();
    }

    private static final class PreviewWorld implements BannerPlacementPreviewWorld {
        final Set<BlockPos> blocked = new HashSet<>();
        final Set<BlockPos> invalidSupports = new HashSet<>();
        boolean loaded = true;
        boolean inBounds = true;
        boolean protectionKnown;
        int replaceableReads;
        @Override public boolean inWorldBounds(BlockPos position) { return inBounds; }
        @Override public boolean chunkLoaded(BlockPos position) { return loaded; }
        @Override public boolean replaceable(BlockPos position) {
            replaceableReads++; return !blocked.contains(position);
        }
        @Override public boolean validWallSupport(BlockPos position, Direction facing) {
            return !invalidSupports.contains(position);
        }
        @Override public boolean serverProtectionKnownAllowed() { return protectionKnown; }
    }
}
