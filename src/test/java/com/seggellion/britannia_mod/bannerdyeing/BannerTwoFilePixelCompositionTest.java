package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.client.banner.BannerPixelComposition;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class BannerTwoFilePixelCompositionTest {
    private static final int BLUE = 0x002060E0;
    private static final Path PLACEHOLDER =
            Path.of(System.getProperty("britannia.projectDir", "."),"src/main/resources/assets/britannia_mod/textures/banner/placeholder");

    @Test
    void syntheticHighlightShadowFixedPartialAndTransparentPixelsFollowFormula() {
        int yellowHighlight = 0xFFFFE060;
        int yellowShadow = 0xFF705010;
        int brightMask = 0xFFF0F0F0;
        int darkMask = 0xFF505050;
        int highlight = BannerPixelComposition.recolour(yellowHighlight, brightMask, BLUE);
        int shadow = BannerPixelComposition.recolour(yellowShadow, darkMask, BLUE);
        assertEquals(0xFF1E5AD3, highlight);
        assertEquals(0xFF0A1E46, shadow);
        assertTrue((highlight & 0xFF) > (shadow & 0xFF));

        int charcoal = 0xFF292929;
        assertEquals(charcoal, BannerPixelComposition.recolour(charcoal, 0x00808080, BLUE));

        int partial = BannerPixelComposition.recolour(0xFFCCAA44, 0x80808080, BLUE);
        assertEquals(0xFF6E6D5A, partial);

        int transparent = 0x00112233;
        assertEquals(transparent, BannerPixelComposition.recolour(transparent, 0x00000000, BLUE));
    }

    @Test
    void defaultRenderingReturnsEveryBasePixelExactly() throws Exception {
        BufferedImage base = ImageIO.read(PLACEHOLDER.resolve("base_texture.png").toFile());
        BufferedImage mask = ImageIO.read(PLACEHOLDER.resolve("dye_mask.png").toFile());
        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                assertEquals(base.getRGB(x, y),
                        BannerPixelComposition.render(base.getRGB(x, y), mask.getRGB(x, y), BLUE, false));
            }
        }
    }

    @Test
    void placeholderRecolourChangesOnlyActiveMaskPixelsAndKeepsDiagnosticMarkFixed() throws Exception {
        BufferedImage base = ImageIO.read(PLACEHOLDER.resolve("base_texture.png").toFile());
        BufferedImage mask = ImageIO.read(PLACEHOLDER.resolve("dye_mask.png").toFile());
        boolean changed = false;
        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                int output = BannerPixelComposition.render(
                        base.getRGB(x, y), mask.getRGB(x, y), BLUE, true);
                int alpha = mask.getRGB(x, y) >>> 24;
                if (alpha == 0) {
                    assertEquals(base.getRGB(x, y), output, x + "," + y);
                } else {
                    changed |= output != base.getRGB(x, y);
                }
                assertEquals(base.getRGB(x, y) >>> 24, output >>> 24);
            }
        }
        assertTrue(changed);
        assertEquals(0, mask.getRGB(6, 6) >>> 24);
        assertEquals(base.getRGB(6, 6),
                BannerPixelComposition.recolour(base.getRGB(6, 6), mask.getRGB(6, 6), BLUE));
        assertEquals(0, mask.getRGB(0, 0) >>> 24);
    }

    @Test
    void placeholderFilesAndRemovedResourcesMatchTwoFileContract() throws Exception {
        Path basePath = PLACEHOLDER.resolve("base_texture.png");
        Path maskPath = PLACEHOLDER.resolve("dye_mask.png");
        assertTrue(Files.isRegularFile(basePath));
        assertTrue(Files.isRegularFile(maskPath));
        assertTrue(ImageIO.read(basePath.toFile()) != null);
        assertTrue(ImageIO.read(maskPath.toFile()) != null);
        assertEquals(ImageIO.read(basePath.toFile()).getWidth(), ImageIO.read(maskPath.toFile()).getWidth());
        assertEquals(ImageIO.read(basePath.toFile()).getHeight(), ImageIO.read(maskPath.toFile()).getHeight());
        assertTrue(Files.notExists(PLACEHOLDER.resolve("fabric_base.png")));
        assertTrue(Files.notExists(PLACEHOLDER.resolve("static_overlay.png")));
        assertNotEquals(ImageIO.read(basePath.toFile()).getRGB(4, 4),
                BannerPixelComposition.recolour(
                        ImageIO.read(basePath.toFile()).getRGB(4, 4),
                        ImageIO.read(maskPath.toFile()).getRGB(4, 4), BLUE));
    }
}
