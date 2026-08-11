package com.seggellion.britannia_mod.client.branding;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ClientBrandingTitleAssetTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path TITLE_TEXTURES =
            PROJECT.resolve("src/main/resources/assets/minecraft/textures/gui/title");
    private static final String WORDMARK_SHA256 =
            "865efd5dc70aea7f1839919bc306b28c690f71df33e50e5682c2bab237655a3e";
    private static final String EDITION_SHA256 =
            "ca5de485b94ec28d85e83a70df6704e4d08bf9340c5c7ff726242bb295b70235";

    @Test
    void runtimeTitleDirectoryContainsOnlyTheSelectedProductionAssets() throws Exception {
        try (var files = Files.list(TITLE_TEXTURES)) {
            assertEquals(
                    List.of("edition.png", "minceraft.png", "minecraft.png"),
                    files.filter(Files::isRegularFile)
                            .map(path -> path.getFileName().toString())
                            .sorted()
                            .toList());
        }
    }

    @Test
    void normalAndRareWordmarksAreByteIdenticalAndDimensionMatched() throws Exception {
        Path normal = TITLE_TEXTURES.resolve("minecraft.png");
        Path rare = TITLE_TEXTURES.resolve("minceraft.png");
        BufferedImage image = ImageIO.read(normal.toFile());

        assertEquals(1024, image.getWidth());
        assertEquals(256, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());
        assertArrayEquals(Files.readAllBytes(normal), Files.readAllBytes(rare));
        assertEquals(WORDMARK_SHA256, sha256(normal));
        assertEquals(WORDMARK_SHA256, sha256(rare));
    }

    @Test
    void wordmarkUsesTheVisibleVanillaSampleAreaWithCleanTransparency() throws Exception {
        BufferedImage image = ImageIO.read(TITLE_TEXTURES.resolve("minecraft.png").toFile());
        int visiblePixels = 0;
        int partiallyTransparentPixels = 0;
        int greenKeyPixels = 0;
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = argb >>> 24;
                if (alpha == 0) {
                    continue;
                }

                visiblePixels++;
                if (alpha < 255) {
                    partiallyTransparentPixels++;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);

                int red = argb >> 16 & 0xFF;
                int green = argb >> 8 & 0xFF;
                int blue = argb & 0xFF;
                if (alpha >= 32 && green > red + 30 && green > blue + 30) {
                    greenKeyPixels++;
                }
            }
        }

        assertTrue(visiblePixels > 50_000, "Wordmark must retain substantial readable coverage");
        assertTrue(partiallyTransparentPixels > 0, "Wordmark must retain antialiased alpha edges");
        assertEquals(0, greenKeyPixels, "Chroma-key green must not remain in materially visible pixels");
        assertTrue(minX >= 32 && maxX <= 991, "Wordmark must retain horizontal safe padding");
        assertTrue(minY >= 8 && maxY <= 167, "Wordmark must stay inside the authored safe area");
        assertTrue(maxY < 176, "The bottom 80 rows are outside vanilla's visible 44/64 sample");
        assertEquals(0, image.getRGB(0, 0) >>> 24);
        assertEquals(0, image.getRGB(image.getWidth() - 1, image.getHeight() - 1) >>> 24);
    }

    @Test
    void editionLayerIsFullyTransparentAtVanillaDimensions() throws Exception {
        Path edition = TITLE_TEXTURES.resolve("edition.png");
        BufferedImage image = ImageIO.read(edition.toFile());

        assertEquals(512, image.getWidth());
        assertEquals(64, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                assertEquals(0, image.getRGB(x, y) >>> 24, "Edition layer must be fully transparent");
            }
        }
        assertFalse(Files.isSameFile(edition, TITLE_TEXTURES.resolve("minecraft.png")));
        assertEquals(EDITION_SHA256, sha256(edition));
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
