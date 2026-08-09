package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.*;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.block.BannerBlock;
import com.seggellion.britannia_mod.banner.block.BannerPartBlock;
import com.seggellion.britannia_mod.banner.structure.BannerStructureTransform;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone13RenderFixtures;
import com.seggellion.britannia_mod.client.banner.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BannerPlacedRendererCacheAndIsolationTest {
    private static final Path MAIN = Path.of(System.getProperty("britannia.projectDir", "."),"src/main/java/com/seggellion/britannia_mod");

    @BeforeAll
    static void setup() {
        Milestone13RenderFixtures.ensureLoaded();
    }

    @Test
    void naturalAndDyedPlansUseTwoAndThreeOrderedPassesAndTintOnlyTheMask() {
        BannerPlacedRenderPlan natural = BannerPlacedRenderPlan.from(valid("cotton", "brass"));
        assertFalse(natural.fallback());
        assertEquals(List.of(
                        BannerPlacedRenderPass.Type.BASE_TEXTURE,
                        BannerPlacedRenderPass.Type.MOUNT),
                natural.passes().stream().map(BannerPlacedRenderPass::type).toList());
        assertEquals(List.of(false, false),
                natural.passes().stream().map(BannerPlacedRenderPass::tintableMask).toList());

        var definition = Milestone13RenderFixtures.definitionForGeometry("large");
        var cotton = Milestone13RenderFixtures.material("cotton");
        var dyedInstance = Milestone13RenderFixtures.state(definition, cotton,
                Milestone13RenderFixtures.dyed(cotton), Milestone13RenderFixtures.mount("brass"));
        BannerPlacedRenderState dyedState = Milestone13RenderFixtures.placed(
                Milestone13RenderFixtures.entity(BlockPos.ZERO, Direction.NORTH,
                    BannerOrientation.WALL_PARALLEL, 2, 2, dyedInstance), 4, 5);
        BannerPlacedRenderPlan dyed = BannerPlacedRenderPlan.from(dyedState);
        assertEquals(List.of(
                        BannerPlacedRenderPass.Type.BASE_TEXTURE,
                        BannerPlacedRenderPass.Type.DYE_MASK,
                        BannerPlacedRenderPass.Type.MOUNT),
                dyed.passes().stream().map(BannerPlacedRenderPass::type).toList());
        assertEquals(List.of(false, true, false),
                dyed.passes().stream().map(BannerPlacedRenderPass::tintableMask).toList());
        assertEquals(0xFFFFFFFF, dyed.passes().get(0).argb());
        assertEquals(dyedState.appearance().displayArgb(), dyed.passes().get(1).argb());
        assertEquals(0xFFFFFFFF, dyed.passes().get(2).argb());
        assertTrue(dyed.passes().get(0).depthOffset() < dyed.passes().get(1).depthOffset());
        assertTrue(dyed.passes().get(1).depthOffset() < dyed.passes().get(2).depthOffset());
    }

    @Test
    void colourChangesOnlyFabricMaskColourAndMountChangesOnlyMountAssets() {
        BannerPlacedRenderState natural = valid("cotton", "brass");
        var definition = Milestone13RenderFixtures.definitionForGeometry("large");
        var cotton = Milestone13RenderFixtures.material("cotton");
        var dyedInstance = Milestone13RenderFixtures.state(definition, cotton,
                Milestone13RenderFixtures.dyed(cotton), Milestone13RenderFixtures.mount("brass"));
        BannerPlacedRenderState dyed = Milestone13RenderFixtures.placed(
                Milestone13RenderFixtures.entity(BlockPos.ZERO, Direction.NORTH,
                    BannerOrientation.WALL_PARALLEL, 2, 2, dyedInstance), 4, 5);
        BannerPlacedRenderPlan naturalPlan = BannerPlacedRenderPlan.from(natural);
        BannerPlacedRenderPlan dyedPlan = BannerPlacedRenderPlan.from(dyed);
        assertEquals(2, naturalPlan.passes().size());
        assertEquals(3, dyedPlan.passes().size());
        assertEquals(naturalPlan.passes().getFirst(), dyedPlan.passes().getFirst());
        assertEquals(BannerPlacedRenderPass.Type.DYE_MASK, dyedPlan.passes().get(1).type());
        assertEquals(dyed.appearance().displayArgb(), dyedPlan.passes().get(1).argb());
        assertEquals(naturalPlan.passes().getLast(), dyedPlan.passes().getLast());

        BannerPlacedRenderPlan iron = BannerPlacedRenderPlan.from(valid("cotton", "iron"));
        assertEquals(naturalPlan.passes().getFirst(), iron.passes().getFirst());
        assertNotEquals(naturalPlan.passes().getLast().texture(), iron.passes().getLast().texture());
        assertEquals(0xFFFFFFFF, iron.passes().getLast().argb());
    }

    @Test
    void everyAppearanceAssetFailureProducesOneAnchorDiagnosticPass() {
        BannerPlacedRenderState base = valid("cotton", "brass");
        for (BannerRenderFailure appearanceFailure : List.of(
                BannerRenderFailure.MISSING_GEOMETRY,
                BannerRenderFailure.MISSING_BASE_TEXTURE,
                BannerRenderFailure.MISSING_DYE_MASK,
                BannerRenderFailure.MISSING_MOUNT_GEOMETRY,
                BannerRenderFailure.MISSING_MOUNT_TEXTURE)) {
            BannerAppearanceState appearance = BannerAppearanceResolver.fallback(null, 90, 91,
                    appearanceFailure, "server:test");
            BannerPlacedRenderState fallback = new BannerPlacedRenderState(
                    appearance, base.orientation(), base.facing(), base.persistedWidth(),
                    base.persistedHeight(), base.occupiedOffsets(), base.spanAxis(), base.verticalAxis(),
                    base.anchorConvention(), base.renderBounds(), base.lightingSamplePositions(),
                    base.orientationMountGeometry(), base.geometryFamily(),
                    BannerPlacedRenderFailure.APPEARANCE_FALLBACK,
                    appearanceFailure.name(), 91);
            BannerPlacedRenderPlan plan = BannerPlacedRenderPlan.from(fallback);
            assertTrue(plan.fallback());
            assertEquals(1, plan.passes().size());
            assertEquals(BannerPlacedRenderPass.Type.FALLBACK, plan.passes().getFirst().type());
            assertEquals(BannerAssetAvailability.MISSING_TEXTURE, plan.passes().getFirst().texture());
        }
    }

    @Test
    void sharedAndPlacedCachesReuseEntriesStayBoundedAndClearTogetherOnDataReload() {
        BannerPlacedRenderState state = valid("linen", "iron");
        BannerLayerPlan shared = BannerAppearanceCache.plan(state.appearance());
        assertSame(shared, BannerAppearanceCache.plan(state.appearance()));
        BannerPlacedRenderPlan placed = BannerPlacedRenderCache.resolve(state);
        assertSame(placed, BannerPlacedRenderCache.resolve(state));
        assertTrue(BannerAppearanceCache.entryCount() <= BannerAppearanceCache.MAX_ENTRIES);
        assertTrue(BannerPlacedRenderCache.entryCount() <= BannerPlacedRenderCache.MAX_ENTRIES);

        ClientBannerRenderData.replace(Milestone13RenderFixtures.renderData());
        assertEquals(0, BannerAppearanceCache.entryCount());
        assertEquals(0, BannerPlacedRenderCache.entryCount());
        assertEquals(0, BannerPlacedRenderCache.missingDiagnosticCount());
    }

    @Test
    void placedDiagnosticDeduplicationIsBounded() throws Exception {
        BannerPlacedRenderState base = valid("linen", "brass");
        var tracker = BannerPlacedRenderCache.class.getDeclaredMethod(
                "firstDiagnostic", BannerPlacedRenderState.class);
        tracker.setAccessible(true);
        for (int index = 0; index < BannerPlacedRenderCache.MAX_DIAGNOSTICS + 32; index++) {
            BannerAppearanceState appearance = BannerAppearanceResolver.fallback(
                    null, 100, 101, BannerRenderFailure.MISSING_DEFINITION, "server:missing_" + index);
            BannerPlacedRenderState fallback = new BannerPlacedRenderState(
                    appearance, base.orientation(), base.facing(), base.persistedWidth(),
                    base.persistedHeight(), base.occupiedOffsets(), base.spanAxis(), base.verticalAxis(),
                    base.anchorConvention(), base.renderBounds(), base.lightingSamplePositions(),
                    base.orientationMountGeometry(), base.geometryFamily(),
                    BannerPlacedRenderFailure.APPEARANCE_FALLBACK,
                    appearance.diagnosticId(), 101);
            assertTrue((boolean) tracker.invoke(null, fallback));
        }
        assertEquals(BannerPlacedRenderCache.MAX_DIAGNOSTICS,
                BannerPlacedRenderCache.missingDiagnosticCount());
        ClientBannerRenderData.replace(Milestone13RenderFixtures.renderData());
        assertEquals(0, BannerPlacedRenderCache.missingDiagnosticCount());
    }

    @Test
    void resourceReloadLifecycleClearsSharedItemAndPlacedCaches() throws Exception {
        BannerPlacedRenderState state = valid("wool", "brass");
        BannerAppearanceCache.plan(state.appearance());
        BannerPlacedRenderCache.resolve(state);
        var method = BannerRenderCache.class.getDeclaredMethod("onModelsReloaded");
        method.setAccessible(true);
        method.invoke(null);
        assertEquals(0, BannerAppearanceCache.entryCount());
        assertEquals(0, BannerPlacedRenderCache.entryCount());
        assertEquals(0, BannerRenderCache.entryCount());
    }

    @Test
    void genericPlacedCacheEvictsWithoutRetainingPositionsOrWorldObjects() {
        BannerPlacedRenderEntryCache<Object> cache = new BannerPlacedRenderEntryCache<>(2);
        BannerPlacedRenderKey one = valid("cotton", "brass").key();
        BannerPlacedRenderKey two = valid("cotton", "iron").key();
        BannerPlacedRenderKey three = withFacing(valid("cotton", "brass"), Direction.SOUTH).key();
        Object first = cache.getOrCreate(one, ignored -> new Object());
        assertSame(first, cache.getOrCreate(one, ignored -> new Object()));
        cache.getOrCreate(two, ignored -> new Object());
        cache.getOrCreate(three, ignored -> new Object());
        assertEquals(2, cache.size());
        String components = java.util.Arrays.stream(BannerPlacedRenderKey.class.getRecordComponents())
                .map(component -> component.getType().getName()).collect(java.util.stream.Collectors.joining(","));
        assertFalse(components
                .matches("(?is).*(BlockPos|Level|Player|BlockEntity|ItemStack).*"));
    }

    @Test
    void exactlyOneClientOnlyAnchorRendererAndNoPartRendererAreRegistered() throws Exception {
        String setup = Files.readString(MAIN.resolve("ClientModSetup.java"));
        assertEquals(1, count(setup,
                "BannerBlockRegistry\\.BANNER_BLOCK_ENTITY\\.get\\(\\), BannerBlockEntityRenderer::new"));
        assertEquals(0, count(setup, "BANNER_PART.*registerBlockEntityRenderer"));
        assertTrue(setup.contains("value = Dist.CLIENT"));
        assertFalse(Files.exists(MAIN.resolve(
                "client/banner/BannerPartBlockEntityRenderer.java")));
        String renderer = Files.readString(MAIN.resolve(
                "client/banner/BannerBlockEntityRenderer.java"));
        assertTrue(renderer.contains("implements BlockEntityRenderer<BannerBlockEntity>"));
        assertTrue(renderer.contains("getRenderBoundingBox"));
        assertTrue(renderer.contains("getViewDistance"));
        assertTrue(renderer.contains("RenderType.cutout()"));
        assertTrue(renderer.contains("RenderType.translucent()"));
        assertFalse(renderer.contains("FULL_BRIGHT"));
        assertFalse(renderer.contains("getChunk("));
        assertTrue(renderer.contains("hasChunkAt"));
        assertTrue(renderer.contains("sampled >= 6"));
    }

    @Test
    void anchorAndPartsAreInvisibleForRenderingButKeepTheirSelectionShapes() {
        BannerBlock anchor = new BannerBlock(BlockBehaviour.Properties.of());
        BannerPartBlock part = new BannerPartBlock(BlockBehaviour.Properties.of());
        assertEquals(RenderShape.INVISIBLE, anchor.defaultBlockState().getRenderShape());
        assertEquals(RenderShape.INVISIBLE, part.defaultBlockState().getRenderShape());
        assertFalse(anchor.defaultBlockState().getShape(
                net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
        assertFalse(part.defaultBlockState().getShape(
                net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
    }

    @Test
    void rendererUsesFiniteFullBoundsDistanceAndDoesNotRenderOnlyFromAnchorDistance() {
        var definition = Milestone13RenderFixtures.definitionForGeometry("large");
        var material = Milestone13RenderFixtures.material("cotton");
        var instance = Milestone13RenderFixtures.state(definition, material,
                Milestone13RenderFixtures.natural(material), Milestone13RenderFixtures.mount("brass"));
        var entity = Milestone13RenderFixtures.entity(new BlockPos(0, 64, 0), Direction.EAST,
                BannerOrientation.WALL_PARALLEL, 2, 2, instance);
        BannerBlockEntityRenderer renderer = new BannerBlockEntityRenderer(null);
        assertEquals(64, renderer.getViewDistance());
        assertEquals(BannerPlacedRenderBounds.from(entity), renderer.getRenderBoundingBox(entity));
        assertTrue(renderer.shouldRender(entity, new Vec3(0.5, 64.5, -65.0)));
        assertFalse(renderer.shouldRender(entity, new Vec3(0.5, 64.5, -80.0)));
    }

    @Test
    void commonStateAndPayloadRemainDedicatedServerSafe() throws Exception {
        String common = readTree(MAIN.resolve("banner/blockentity"))
                + readTree(MAIN.resolve("banner/renderdata"))
                + readTree(MAIN.resolve("network/payload/banner"))
                + Files.readString(MAIN.resolve("registry/BannerBlockRegistry.java"))
                + Files.readString(MAIN.resolve("BritanniaMod.java"));
        assertFalse(common.contains("net.minecraft.client"));
        assertFalse(common.contains("com.mojang.blaze3d"));
        assertFalse(common.contains("BannerBlockEntityRenderer"));
        assertFalse(common.contains("BannerPlacedRenderCache"));
    }

    @Test
    void staticDiagnosticsRemainPackagedButCannotOverlapNormalRendering() {
        assertTrue(Files.isRegularFile(Path.of(System.getProperty("britannia.projectDir", "."),
                "src/main/resources/assets/britannia_mod/models/block/banner.json")));
        assertTrue(Files.isRegularFile(Path.of(System.getProperty("britannia.projectDir", "."),
                "src/main/resources/assets/britannia_mod/models/block/banner_part.json")));
        assertEquals(RenderShape.INVISIBLE,
                new BannerBlock(BlockBehaviour.Properties.of()).defaultBlockState().getRenderShape());
        assertEquals(RenderShape.INVISIBLE,
                new BannerPartBlock(BlockBehaviour.Properties.of()).defaultBlockState().getRenderShape());
    }

    private static BannerPlacedRenderState valid(String materialPath, String mountPath) {
        var definition = Milestone13RenderFixtures.definitionForGeometry("large");
        var material = Milestone13RenderFixtures.material(materialPath);
        var instance = Milestone13RenderFixtures.state(definition, material,
                Milestone13RenderFixtures.natural(material), Milestone13RenderFixtures.mount(mountPath));
        return Milestone13RenderFixtures.placed(Milestone13RenderFixtures.entity(
                BlockPos.ZERO, Direction.NORTH, BannerOrientation.WALL_PARALLEL, 2, 2, instance), 4, 5);
    }

    private static BannerPlacedRenderState withFacing(BannerPlacedRenderState state, Direction facing) {
        return new BannerPlacedRenderState(state.appearance(), state.orientation(), facing,
                state.persistedWidth(), state.persistedHeight(), state.occupiedOffsets(),
                BannerStructureTransform.spanAxis(facing, state.orientation()), state.verticalAxis(),
                state.anchorConvention(), state.renderBounds(), state.lightingSamplePositions(),
                state.orientationMountGeometry(), state.geometryFamily(), state.failure(),
                state.diagnosticId(), state.resourceGeneration());
    }

    private static long count(String input, String regex) {
        return Pattern.compile(regex, Pattern.MULTILINE).matcher(input).results().count();
    }

    private static String readTree(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java")).map(path -> {
                try {
                    return Files.readString(path);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }).reduce("", String::concat);
        }
    }
}
