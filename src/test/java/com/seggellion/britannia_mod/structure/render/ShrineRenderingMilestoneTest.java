package com.seggellion.britannia_mod.structure.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ContentStatus;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.DisplayResolution;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ResourceAvailability;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.multiblock.ShrineRenderTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class ShrineRenderingMilestoneTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets");
    private static final Path JAVA = Path.of("src/main/java/com/seggellion/britannia_mod");
    private static final List<String> IDS = List.of(
            "honesty", "compassion", "valor", "justice", "sacrifice",
            "honor", "spirituality", "humility", "chaos");

    @Test
    void manifestContainsNineApprovedEnabledLocalizedVariantsAndRealFiles() throws IOException {
        var shrine = ShrineMonolithDefinitions.catalogue()
                .family(ShrineMonolithDefinitions.SHRINE).orElseThrow();
        assertEquals(IDS, shrine.variants().stream().map(variant -> variant.id().value()).toList());
        assertEquals(9, new HashSet<>(IDS).size());
        assertEquals(1, shrine.variants().stream().map(variant -> variant.model().logicalIdentity()).distinct().count());

        String language = Files.readString(ASSETS.resolve("britannia_mod/lang/en_us.json"));
        var textures = new HashSet<String>();
        for (var variant : shrine.variants()) {
            assertTrue(variant.enabled());
            assertEquals(ContentStatus.APPROVED, variant.contentStatus());
            assertEquals(ResourceAvailability.AVAILABLE, variant.model().availability());
            assertEquals(ResourceAvailability.AVAILABLE, variant.texture().availability());
            assertEquals(DisplayResolution.RESOLVED, variant.displayName().resolution());
            String key = variant.displayName().translationKey().orElseThrow();
            assertTrue(language.contains("\"" + key + "\""));

            var model = variant.model().location().orElseThrow();
            var texture = variant.texture().location().orElseThrow();
            assertEquals("britannia_mod", model.namespace());
            assertEquals("britannia_mod", texture.namespace());
            assertTrue(Files.isRegularFile(ASSETS.resolve(model.namespace()).resolve(model.path())));
            Path textureFile = ASSETS.resolve(texture.namespace()).resolve(texture.path());
            assertTrue(Files.isRegularFile(textureFile));
            BufferedImage image = ImageIO.read(textureFile.toFile());
            assertEquals(128, image.getWidth());
            assertEquals(128, image.getHeight());
            textures.add(texture.path());
        }
        assertEquals(9, textures.size());
    }

    @Test
    void everyVariantResolvesOneSharedGeometryAndItsOwnTexture() {
        for (String id : IDS) {
            ShrineRenderSelection selection = ShrineRenderSelection.resolve(
                    ShrineMonolithDefinitions.SHRINE, new VariantId(id));
            assertEquals(ShrineRenderSelection.Status.READY, selection.status());
            assertEquals("shrine_shared_geometry", selection.geometry().orElseThrow().logicalIdentity());
            assertEquals("shrine_texture_" + id, selection.texture().orElseThrow().logicalIdentity());
            assertEquals("textures/block/shrine/" + id + ".png",
                    selection.texture().orElseThrow().location().orElseThrow().path());
        }
    }

    @Test
    void unknownIdsResolveDiagnosticsWithoutSubstitutingAnotherVariant() {
        ShrineRenderSelection family = ShrineRenderSelection.resolve(
                new FamilyId("foreign"), new VariantId("honesty"));
        assertEquals(ShrineRenderSelection.Status.UNKNOWN_FAMILY, family.status());
        assertTrue(family.geometry().isEmpty());
        assertTrue(family.texture().isEmpty());

        ShrineRenderSelection variant = ShrineRenderSelection.resolve(
                ShrineMonolithDefinitions.SHRINE, new VariantId("unknown_saved_identity"));
        assertEquals(ShrineRenderSelection.Status.UNKNOWN_VARIANT, variant.status());
        assertEquals("unknown_saved_identity", variant.variantId().value());
        assertTrue(variant.texture().isEmpty());
    }

    @Test
    void rotationsAreExactAndVerticalFacingsAreRejected() {
        assertEquals(0.0F, ShrineRenderTransform.yRotationDegrees(Direction.NORTH));
        assertEquals(-90.0F, ShrineRenderTransform.yRotationDegrees(Direction.EAST));
        assertEquals(180.0F, ShrineRenderTransform.yRotationDegrees(Direction.SOUTH));
        assertEquals(90.0F, ShrineRenderTransform.yRotationDegrees(Direction.WEST));
        assertThrows(IllegalArgumentException.class,
                () -> ShrineRenderTransform.yRotationDegrees(Direction.UP));
    }

    @Test
    void finiteBoundsCoverEveryRotatedFootprintWithoutChangingRenderOffset() {
        var bounds = ShrineRenderTransform.ALL_FACINGS_LOCAL_BOUNDS;
        assertEquals(-1.0 - ShrineRenderTransform.TOLERANCE, bounds.minX);
        assertEquals(-1.0 - ShrineRenderTransform.TOLERANCE, bounds.minZ);
        assertEquals(2.0 + ShrineRenderTransform.TOLERANCE, bounds.maxX);
        assertEquals(2.0 + ShrineRenderTransform.TOLERANCE, bounds.maxZ);
        assertEquals(-ShrineRenderTransform.TOLERANCE, bounds.minY);
        assertEquals(1.0 + ShrineRenderTransform.TOLERANCE, bounds.maxY);
        assertEquals(bounds.move(new BlockPos(12, 30, -4)),
                ShrineRenderTransform.worldBounds(new BlockPos(12, 30, -4)));
        assertEquals(0, ShrineMonolithDefinitions.SHRINE_RENDER_OFFSET.y());
    }

    @Test
    void registrationIsAnchorOnlyAndCommonCodeHasNoClientRendererImports() throws IOException {
        String setup = Files.readString(JAVA.resolve("ClientModSetup.java"));
        assertEquals(1, occurrences(setup,
                "registerBlockEntityRenderer(LargeStructureRegistry.LARGE_STRUCTURE.get(), ShrineRenderer::new)"));
        assertFalse(setup.contains("LARGE_STRUCTURE_PART.get(), ShrineRenderer"));

        String part = Files.readString(JAVA.resolve("structure/multiblock/LargeStructurePartBlock.java"));
        assertTrue(part.contains("return RenderShape.INVISIBLE"));
        assertFalse(part.contains("newBlockEntity"));

        String anchor = Files.readString(JAVA.resolve("structure/multiblock/LargeStructureAnchorBlock.java"));
        assertTrue(anchor.contains("return RenderShape.INVISIBLE"));

        String geoModel = Files.readString(JAVA.resolve("client/renderer/shrine/ShrineGeoModel.java"));
        assertTrue(geoModel.contains("private static final int MAX_DIAGNOSTICS = 128"));
        assertTrue(geoModel.contains("DIAGNOSTICS.size() > MAX_DIAGNOSTICS"));
        assertTrue(Files.isRegularFile(ASSETS.resolve("britannia_mod/geo/shrine_missing.geo.json")));

        for (String packagePath : List.of("structure/definition", "structure/multiblock", "structure/render")) {
            try (var paths = Files.walk(JAVA.resolve(packagePath))) {
                for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    assertFalse(source.contains("net.minecraft.client.renderer"), file.toString());
                    assertFalse(source.contains("software.bernie.geckolib.renderer"), file.toString());
                }
            }
        }
    }

    private static int occurrences(String source, String needle) {
        return (source.length() - source.replace(needle, "").length()) / needle.length();
    }
}
