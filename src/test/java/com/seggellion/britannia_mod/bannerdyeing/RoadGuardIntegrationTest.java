package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.banner.renderdata.BannerPreviewRenderState;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderDataSnapshot;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone13RenderFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone7RegisteredTestContent;
import com.seggellion.britannia_mod.client.banner.BannerAssetAvailability;
import com.seggellion.britannia_mod.client.banner.BannerItemRenderState;
import com.seggellion.britannia_mod.client.banner.BannerLayerPlan;
import com.seggellion.britannia_mod.client.banner.BannerPlacedGeometryFamily;
import com.seggellion.britannia_mod.client.banner.BannerPlacedRenderPass;
import com.seggellion.britannia_mod.client.banner.BannerPlacedRenderPlan;
import com.seggellion.britannia_mod.client.banner.BannerPlacedRenderState;
import com.seggellion.britannia_mod.client.banner.BannerPreviewStacks;
import com.seggellion.britannia_mod.client.banner.BannerRenderFailure;
import com.seggellion.britannia_mod.client.banner.BannerRenderLayer;
import com.seggellion.britannia_mod.client.banner.BannerRenderStateExtractor;
import com.seggellion.britannia_mod.client.banner.ClientBannerRenderPublication;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import com.seggellion.britannia_mod.tools.BannerScaffoldTool;
import com.seggellion.britannia_mod.tools.FinalContentIntakeValidator;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RoadGuardIntegrationTest {
    private static final BannerDefinitionId ROAD_GUARD = BannerDefinitionId.parse("britannia_mod:road_guard");
    private static final ResourceLocation GEOMETRY =
            ResourceLocation.parse("britannia_mod:banner/road_guard/geometry");
    private static final ResourceLocation BASE =
            ResourceLocation.parse("britannia_mod:banner/road_guard/base_texture");
    private static final ResourceLocation MASK =
            ResourceLocation.parse("britannia_mod:banner/road_guard/dye_mask");
    private static final Path INTAKE =
            Path.of("content/banner-final-intake/submissions/road_guard/road_guard.yml");
    private static final Path GATE_E_REVIEW =
            Path.of("content/banner-final-intake/submissions/road_guard/GATE_E_REVIEW.md");
    private static final Path BASE_PATH = Path.of(
            "src/main/resources/assets/britannia_mod/textures/banner/road_guard/base_texture.png");
    private static final Path MASK_PATH = Path.of(
            "src/main/resources/assets/britannia_mod/textures/banner/road_guard/dye_mask.png");
    private static final Path GEOMETRY_PATH = Path.of(
            "src/main/resources/assets/britannia_mod/models/banner/road_guard/geometry.json");
    private static final String BASE_HASH =
            "46ce83a31b9954cea1b3934249eab919ca9408658772b96b0e2752c6aaa48b2d";
    private static final String MASK_HASH =
            "8efeff71ca5c8689fef725c7fc3172b783687ffcb3c9dd0bf978874cad048f77";

    private static BannerRenderDataSnapshot renderData;
    private static ClientBannerRenderPublication publication;

    @BeforeAll
    static void loadProduction() throws Exception {
        Milestone7RegisteredTestContent.ensureRegistered();
        Milestone13RenderFixtures.ensureLoaded();
        renderData = BannerRenderDataSnapshot.fromRegistry(DyeResolverFixtures.productionSnapshot());
        publication = new ClientBannerRenderPublication(renderData, 1601, true);
    }

    @Test
    void approvedIntakeCatalogueAndGeneratedDefinitionAgree() throws Exception {
        FinalContentIntakeValidator.Result intake = FinalContentIntakeValidator.validate(
                Path.of(".").toAbsolutePath().normalize(), INTAKE.toAbsolutePath().normalize());
        assertEquals(FinalContentIntakeValidator.Status.READY_FOR_INTEGRATION, intake.status(),
                intake.issues().toString());
        assertEquals("britannia_mod:road_guard", intake.stableId());

        BannerScaffoldTool.Manifest manifest = BannerScaffoldTool.readAndValidateManifest(
                Path.of(BannerScaffoldTool.MANIFEST_PATH));
        List<BannerScaffoldTool.BannerEntry> matching = manifest.banners().stream()
                .filter(entry -> entry.id().equals("road_guard")).toList();
        assertEquals(1, matching.size());
        BannerScaffoldTool.BannerEntry roadGuard = matching.getFirst();
        assertEquals(27, roadGuard.index());
        assertEquals("Road Guard", roadGuard.displayName());
        assertEquals("complete", roadGuard.contentStatus());
        assertEquals(1, roadGuard.widthBlocks());
        assertEquals(1, roadGuard.heightBlocks());
        assertEquals(Boolean.FALSE, roadGuard.dimensionsProvisional());
        assertEquals(List.of("wall_parallel", "wall_perpendicular"), roadGuard.supportedOrientations());
        assertEquals(List.of("britannia_mod:brass", "britannia_mod:iron"), roadGuard.supportedMounts());
        assertEquals("britannia_mod:brass", roadGuard.defaultMount());
        assertEquals(GEOMETRY.toString(), roadGuard.geometry());
        assertEquals(BASE.toString(), roadGuard.baseTexture());
        assertEquals(MASK.toString(), roadGuard.dyeMask());
        assertEquals("britannia_mod:extra_small", roadGuard.placementProfile());

        JsonObject definition = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/britannia_mod/banner_definitions/road_guard.json")))
                .getAsJsonObject();
        JsonObject assets = definition.getAsJsonObject("assets");
        assertEquals(3, assets.size());
        assertEquals(GEOMETRY.toString(), assets.get("geometry").getAsString());
        assertEquals(BASE.toString(), assets.get("base_texture").getAsString());
        assertEquals(MASK.toString(), assets.get("dye_mask").getAsString());
        String rawDefinition = definition.toString();
        assertFalse(rawDefinition.contains("fabric_base"));
        assertFalse(rawDefinition.contains("static_overlay"));
        assertFalse(rawDefinition.contains("render_strategy"));
        assertFalse(rawDefinition.contains("optional_overlay"));

        assertEquals(6, renderData.banners().values().stream()
                .filter(value -> value.contentStatus() == BannerContentStatus.PLACEHOLDER).count());
        assertEquals(0, renderData.banners().values().stream()
                .filter(value -> value.contentStatus() == BannerContentStatus.IN_PROGRESS).count());
        assertEquals(29, renderData.banners().values().stream()
                .filter(value -> value.contentStatus() == BannerContentStatus.COMPLETE).count());
        assertTrue(renderData.banners().values().stream()
                .filter(value -> value.contentStatus() == BannerContentStatus.PLACEHOLDER)
                .allMatch(value -> value.dimensions().provisional()
                        && value.assets().baseTexture().equals(BannerAssetAvailability.BASE_TEXTURE)
                        && value.assets().dyeMask().equals(BannerAssetAvailability.DYE_MASK)));
    }

    @Test
    void roadGuardPreservesHistoricalEvidenceAndRecordsCurrentGateEApproval() throws Exception {
        JsonObject intake = JsonParser.parseString(Files.readString(INTAKE)).getAsJsonObject();
        JsonObject provenance = intake.getAsJsonObject("provenance");
        assertTrue(provenance.get("original_art").getAsBoolean());
        assertTrue(provenance.get("distribution_permission_confirmed").getAsBoolean());
        assertFalse(provenance.get("copied_from_reference_art").getAsBoolean());
        assertFalse(provenance.get("creator").getAsString().isBlank());
        assertFalse(provenance.get("creation_method").getAsString().isBlank());

        String review = Files.readString(GATE_E_REVIEW);
        String currentMarker = "## 2026-07-28 authoritative 128 x 128 family review";
        int currentOffset = review.indexOf(currentMarker);
        assertTrue(currentOffset > 0);
        String historicalReview = review.substring(0, currentOffset);
        String currentReview = review.substring(currentOffset);
        assertEquals(47, historicalReview.lines().filter(line -> line.endsWith(": PASS")).count());
        assertEquals(15, currentReview.lines().filter(line -> line.endsWith(": PASS")).count());
        assertTrue(historicalReview.contains(
                "Commit tested: `d03fe2985baf6886cbb109765e68aac9f0a36ac7`"));
        assertTrue(historicalReview.contains("Overall Road Guard visual approval: APPROVED"));
        assertTrue(historicalReview.contains("This Gate E record is preserved as historical evidence"));
        assertTrue(historicalReview.contains(
                "Road Guard returned to `in_progress` pending renewed live review"));
        assertTrue(currentReview.contains(
                "Tested commit: `bf68e4b0025f1aed9a905904a669a09f39e06d31`"));
        assertTrue(currentReview.contains(
                "Base SHA-256: `46ce83a31b9954cea1b3934249eab919ca9408658772b96b0e2752c6aaa48b2d`"));
        assertTrue(currentReview.contains(
                "Mask SHA-256: `8efeff71ca5c8689fef725c7fc3172b783687ffcb3c9dd0bf978874cad048f77`"));
        assertTrue(currentReview.contains("Natural appearance: PASS"));
        assertTrue(currentReview.contains("Fixed regions: PASS"));
        assertTrue(currentReview.contains("Brass: PASS"));
        assertTrue(currentReview.contains("Iron: PASS"));
        assertTrue(currentReview.contains("Overall approval: APPROVED"));
        assertTrue(currentReview.contains("Gate E result: PASS"));
        assertFalse(currentReview.contains(": FAIL"));
        assertFalse(currentReview.contains("NOT PERFORMED"));
        assertFalse(currentReview.contains("unresolved"));
        assertFalse(review.contains("[ENTER"));
        assertFalse(review.contains("[YYYY"));
    }

    @Test
    void approvedRuntimeAssetsRetainHashesMetadataAndSemanticPixels() throws Exception {
        assertEquals(BASE_HASH, sha256(BASE_PATH));
        assertEquals(MASK_HASH, sha256(MASK_PATH));
        BufferedImage base = ImageIO.read(BASE_PATH.toFile());
        BufferedImage mask = ImageIO.read(MASK_PATH.toFile());
        assertEquals(128, base.getWidth());
        assertEquals(128, base.getHeight());
        assertEquals(base.getWidth(), mask.getWidth());
        assertEquals(base.getHeight(), mask.getHeight());
        assertTrue(base.getColorModel().hasAlpha());
        assertTrue(mask.getColorModel().hasAlpha());
        assertEquals(4, base.getColorModel().getNumComponents());
        assertEquals(4, mask.getColorModel().getNumComponents());

        int active = 0;
        int transparent = 0;
        int partial = 0;
        for (int y = 0; y < mask.getHeight(); y++) {
            for (int x = 0; x < mask.getWidth(); x++) {
                int maskArgb = mask.getRGB(x, y);
                int alpha = alpha(maskArgb);
                int red = channel(maskArgb, 16);
                assertEquals(red, channel(maskArgb, 8), x + "," + y);
                assertEquals(red, channel(maskArgb, 0), x + "," + y);
                if (alpha == 0) {
                    transparent++;
                } else {
                    active++;
                    assertEquals(255, red, "active mask RGB must be white at " + x + "," + y);
                    assertTrue(alpha <= alpha(base.getRGB(x, y)),
                            "mask alpha exceeds base alpha at " + x + "," + y);
                    if (alpha < 255) {
                        partial++;
                    }
                }
            }
        }
        assertEquals(1482, active);
        assertEquals(14902, transparent);
        assertTrue(partial > 0, "anti-aliased authored edges must retain partial alpha");

        assertFalse(Files.exists(BASE_PATH.resolveSibling("static_overlay.png")));
        JsonObject model = JsonParser.parseString(Files.readString(GEOMETRY_PATH)).getAsJsonObject();
        assertEquals(2, model.getAsJsonArray("elements").size());
        assertFalse(model.getAsJsonObject("textures").has("mount"));
        assertEquals(BASE.toString(), model.getAsJsonObject("textures").get("base_texture").getAsString());
        assertEquals(MASK.toString(), model.getAsJsonObject("textures").get("dye_mask").getAsString());
    }

    @Test
    void naturalRecolouredPreviewAndMaterialStatesUseOneAppearanceContract() {
        ResourceLocation commonBase = null;
        for (String materialPath : List.of("cotton", "wool", "linen", "silk")) {
            FabricMaterialId material = FabricMaterialId.parse("britannia_mod:" + materialPath);
            BannerItemRenderState natural = extract(stack(
                    material, natural(material), Optional.empty(), MountId.parse("britannia_mod:brass")));
            assertFalse(natural.fallback(), materialPath);
            assertFalse(natural.recolourActive(), materialPath);
            assertEquals(BASE, natural.baseTexture().orElseThrow());
            assertEquals(MASK, natural.dyeMask().orElseThrow());
            assertEquals(List.of(BannerRenderLayer.Type.BASE_TEXTURE, BannerRenderLayer.Type.MOUNT),
                    types(BannerLayerPlan.from(natural)));
            assertEquals(BannerRenderLayer.NO_TINT,
                    BannerLayerPlan.from(natural).layers().getFirst().tintIndex());
            assertEquals(material, natural.materialId().orElseThrow());
            if (commonBase == null) {
                commonBase = natural.baseTexture().orElseThrow();
            } else {
                assertEquals(commonBase, natural.baseTexture().orElseThrow());
            }

            ResolvedColourId dyedColour = nonNatural(material);
            BannerItemRenderState dyed = extract(stack(
                    material, dyedColour, Optional.empty(), MountId.parse("britannia_mod:brass")));
            assertTrue(dyed.recolourActive(), materialPath);
            assertEquals(List.of(BannerRenderLayer.Type.BASE_TEXTURE, BannerRenderLayer.Type.DYE_MASK,
                    BannerRenderLayer.Type.MOUNT), types(BannerLayerPlan.from(dyed)));
            assertEquals(MASK, BannerLayerPlan.from(dyed).layers().get(1).assetId());
            assertEquals(BannerRenderLayer.DYE_MASK_TINT_INDEX,
                    BannerLayerPlan.from(dyed).layers().get(1).tintIndex());
            assertEquals(renderData.materials().get(material).displaySrgbByColour().get(dyedColour).intValue(),
                    dyed.displaySrgb());
        }

        FabricMaterialId cotton = FabricMaterialId.parse("britannia_mod:cotton");
        MountId brass = MountId.parse("britannia_mod:brass");
        ItemStack natural = stack(cotton, natural(cotton), Optional.empty(), brass);
        ItemStack naturalPigment = stack(cotton, natural(cotton),
                Optional.of(PigmentId.parse("britannia_mod:woad_blue")), brass);
        ItemStack administrative = stack(cotton, ResolvedColourId.parse("britannia_mod:cotton_red"),
                Optional.empty(), brass);
        assertFalse(extract(natural).recolourActive());
        assertTrue(extract(naturalPigment).recolourActive());
        assertTrue(extract(administrative).recolourActive());

        BannerPreviewStacks preview = BannerPreviewStacks.create(
                Milestone7RegisteredTestContent.banner(), Milestone7RegisteredTestContent.component(),
                BannerPreviewRenderState.from(natural.get(Milestone7RegisteredTestContent.component())),
                BannerPreviewRenderState.from(administrative.get(Milestone7RegisteredTestContent.component())));
        assertFalse(extract(preview.current()).recolourActive());
        assertTrue(extract(preview.proposed()).recolourActive());
        assertEquals(BASE, extract(preview.current()).baseTexture().orElseThrow());
        assertEquals(MASK, extract(preview.proposed()).dyeMask().orElseThrow());

        ItemStack redyed = stack(cotton, ResolvedColourId.parse("britannia_mod:cotton_blue"),
                Optional.of(PigmentId.parse("britannia_mod:woad_blue")), brass);
        assertNotEquals(extract(administrative).key(8), extract(redyed).key(8));
        ItemStack sameColourOtherPigment = stack(cotton, natural(cotton),
                Optional.of(PigmentId.parse("britannia_mod:madder_red")), brass);
        assertEquals(extract(naturalPigment).key(8), extract(sameColourOtherPigment).key(8));
    }

    @Test
    void bothMountsOrientationsFacingsAndPlacedPlansUseRoadGuardAssets() {
        FabricMaterialId cotton = FabricMaterialId.parse("britannia_mod:cotton");
        for (String mountPath : List.of("brass", "iron")) {
            MountId mount = MountId.parse("britannia_mod:" + mountPath);
            BannerItemRenderState item = extract(stack(cotton, natural(cotton), Optional.empty(), mount));
            BannerLayerPlan itemPlan = BannerLayerPlan.from(item);
            assertEquals(BannerRenderLayer.Type.MOUNT, itemPlan.layers().getLast().type());
            assertEquals(BannerRenderLayer.NO_TINT, itemPlan.layers().getLast().tintIndex());
            for (BannerOrientation orientation : BannerOrientation.values()) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    BannerInstanceState natural = state(cotton, natural(cotton), Optional.empty(), mount);
                    BannerPlacedRenderState placed = Milestone13RenderFixtures.placed(
                            Milestone13RenderFixtures.entity(
                                    BlockPos.ZERO, facing, orientation, 1, 1, natural), 16, 17);
                    assertFalse(placed.fallback(), orientation + " " + facing);
                    assertEquals(BannerPlacedGeometryFamily.ROAD_GUARD, placed.geometryFamily());
                    assertEquals(BASE, placed.appearance().baseTexture().orElseThrow());
                    assertEquals(MASK, placed.appearance().dyeMask().orElseThrow());
                    BannerPlacedRenderPlan naturalPlan = BannerPlacedRenderPlan.from(placed);
                    assertEquals(List.of(BannerPlacedRenderPass.Type.BASE_TEXTURE,
                            BannerPlacedRenderPass.Type.MOUNT),
                            naturalPlan.passes().stream().map(BannerPlacedRenderPass::type).toList());

                    BannerInstanceState dyed = state(cotton, nonNatural(cotton), Optional.empty(), mount);
                    BannerPlacedRenderState dyedPlaced = Milestone13RenderFixtures.placed(
                            Milestone13RenderFixtures.entity(
                                    BlockPos.ZERO, facing, orientation, 1, 1, dyed), 16, 17);
                    BannerPlacedRenderPlan dyedPlan = BannerPlacedRenderPlan.from(dyedPlaced);
                    assertEquals(List.of(BannerPlacedRenderPass.Type.BASE_TEXTURE,
                            BannerPlacedRenderPass.Type.DYE_MASK, BannerPlacedRenderPass.Type.MOUNT),
                            dyedPlan.passes().stream().map(BannerPlacedRenderPass::type).toList());
                    assertEquals(0xFFFFFFFF, dyedPlan.passes().getFirst().argb());
                    assertEquals(dyedPlaced.appearance().displayArgb(), dyedPlan.passes().get(1).argb());
                    assertEquals(0xFFFFFFFF, dyedPlan.passes().getLast().argb());
                }
            }
        }
    }

    @Test
    void missingRoadGuardResourcesFallBackWithoutMutationAndRecover() {
        FabricMaterialId cotton = FabricMaterialId.parse("britannia_mod:cotton");
        ItemStack stack = stack(cotton, natural(cotton), Optional.empty(), MountId.parse("britannia_mod:brass"));
        BannerInstanceState before = stack.get(Milestone7RegisteredTestContent.component());

        for (var missing : List.of(
                new Missing(BASE, false, BannerRenderFailure.MISSING_BASE_TEXTURE),
                new Missing(MASK, false, BannerRenderFailure.MISSING_DYE_MASK),
                new Missing(GEOMETRY, true, BannerRenderFailure.MISSING_GEOMETRY))) {
            var models = new java.util.LinkedHashSet<>(BannerAssetAvailability.EXPECTED_MODELS);
            var textures = new java.util.LinkedHashSet<>(BannerAssetAvailability.EXPECTED_TEXTURES);
            (missing.model ? models : textures).remove(missing.id);
            BannerItemRenderState fallback = BannerRenderStateExtractor.extract(
                    stack, Milestone7RegisteredTestContent.banner(),
                    Milestone7RegisteredTestContent.component(), publication,
                    new BannerAssetAvailability(models, textures), 18);
            assertEquals(missing.failure, fallback.failure());
            assertEquals(ROAD_GUARD, fallback.definitionId().orElseThrow());
            assertEquals(before, stack.get(Milestone7RegisteredTestContent.component()));
        }

        BannerItemRenderState unavailable = BannerRenderStateExtractor.extract(
                stack, Milestone7RegisteredTestContent.banner(), Milestone7RegisteredTestContent.component(),
                new ClientBannerRenderPublication(BannerRenderDataSnapshot.empty(), 19, false),
                BannerAssetAvailability.allExpected(), 18);
        assertEquals(BannerRenderFailure.REGISTRY_UNAVAILABLE, unavailable.failure());
        assertEquals(before, stack.get(Milestone7RegisteredTestContent.component()));

        BannerItemRenderState recovered = extract(stack);
        assertFalse(recovered.fallback());
        assertEquals(BASE, recovered.baseTexture().orElseThrow());
        assertEquals(before, stack.get(Milestone7RegisteredTestContent.component()));
    }

    private static BannerItemRenderState extract(ItemStack stack) {
        return BannerRenderStateExtractor.extract(
                stack, Milestone7RegisteredTestContent.banner(), Milestone7RegisteredTestContent.component(),
                publication, BannerAssetAvailability.allExpected(), 1602);
    }

    private static ItemStack stack(
            FabricMaterialId material,
            ResolvedColourId colour,
            Optional<PigmentId> pigment,
            MountId mount) {
        ItemStack stack = new ItemStack(Milestone7RegisteredTestContent.banner());
        stack.set(Milestone7RegisteredTestContent.component(), state(material, colour, pigment, mount));
        return stack;
    }

    private static BannerInstanceState state(
            FabricMaterialId material,
            ResolvedColourId colour,
            Optional<PigmentId> pigment,
            MountId mount) {
        return new BannerInstanceState(1, ROAD_GUARD, material, colour, pigment, mount);
    }

    private static ResolvedColourId natural(FabricMaterialId material) {
        return renderData.materials().get(material).naturalColourId();
    }

    private static ResolvedColourId nonNatural(FabricMaterialId material) {
        return renderData.materials().get(material).displaySrgbByColour().keySet().stream()
                .filter(colour -> !colour.equals(natural(material))).findFirst().orElseThrow();
    }

    private static List<BannerRenderLayer.Type> types(BannerLayerPlan plan) {
        return plan.layers().stream().map(BannerRenderLayer::type).toList();
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static int compose(int baseArgb, int maskArgb, int dyeRgb) {
        int maskAlpha = alpha(maskArgb);
        int output = alpha(baseArgb) << 24;
        for (int shift : List.of(16, 8, 0)) {
            int base = channel(baseArgb, shift);
            int tinted = (channel(maskArgb, shift) * channel(dyeRgb, shift) + 127) / 255;
            int blended = (base * (255 - maskAlpha) + tinted * maskAlpha + 127) / 255;
            output |= blended << shift;
        }
        return output;
    }

    private static int brightness(int argb) {
        return channel(argb, 16) + channel(argb, 8) + channel(argb, 0);
    }

    private static int alpha(int argb) {
        return channel(argb, 24);
    }

    private static int channel(int argb, int shift) {
        return (argb >>> shift) & 0xFF;
    }

    private record Missing(ResourceLocation id, boolean model, BannerRenderFailure failure) {
    }
}
