package com.seggellion.britannia_mod.client.branding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ClientBrandingBackgroundPolicyTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path GUI_TEXTURES = PROJECT.resolve("src/main/resources/assets/minecraft/textures/gui");
    private static final int OPAQUE_BLACK = 0xFF000000;

    private static final List<ExpectedAsset> BLACK_ASSETS = List.of(
            new ExpectedAsset("menu_background.png", 16, 16),
            new ExpectedAsset("menu_list_background.png", 16, 16),
            new ExpectedAsset("header_separator.png", 32, 2),
            new ExpectedAsset("footer_separator.png", 32, 2),
            new ExpectedAsset("tab_header_background.png", 16, 16));

    private static final List<String> PROTECTED_VANILLA_RESOURCES = List.of(
            "inworld_menu_background.png",
            "inworld_menu_list_background.png",
            "inworld_header_separator.png",
            "inworld_footer_separator.png",
            "title/background/panorama_overlay.png",
            "title/background/panorama_0.png",
            "title/background/panorama_1.png",
            "title/background/panorama_2.png",
            "title/background/panorama_3.png",
            "title/background/panorama_4.png",
            "title/background/panorama_5.png");

    @Test
    void preWorldBackgroundResourcesAreDimensionMatchedOpaqueBlackPngs() throws Exception {
        for (ExpectedAsset expected : BLACK_ASSETS) {
            Path path = GUI_TEXTURES.resolve(expected.name());
            assertTrue(Files.isRegularFile(path), () -> "Missing client-branding asset: " + path);

            BufferedImage image = ImageIO.read(path.toFile());
            assertNotNull(image, () -> "Unreadable PNG: " + path);
            assertEquals(expected.width(), image.getWidth(), () -> "Unexpected width for " + expected.name());
            assertEquals(expected.height(), image.getHeight(), () -> "Unexpected height for " + expected.name());

            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getRGB(x, y);
                    assertEquals(
                            OPAQUE_BLACK,
                            pixel,
                            () -> expected.name() + " must be opaque #000000 at every pixel");
                }
            }
        }
    }

    @Test
    void inWorldAndPanoramaResourcesRemainUnmodifiedByThePolicy() {
        for (String resource : PROTECTED_VANILLA_RESOURCES) {
            assertFalse(
                    Files.exists(GUI_TEXTURES.resolve(resource)),
                    () -> "Client branding must not override protected resource: " + resource);
        }
    }

    @Test
    void approvedTitleBackgroundFilesRemainByteIdentical() throws Exception {
        assertEquals(
                "7187fb8ca89ff959cb57022bec15c113ee44fc4f15b2839b02d0d6e7217b0b30",
                sha256("src/main/resources/assets/britannia_mod/textures/screens/chest_sequence.png"));
        assertEquals(
                "70c9d1eae4c95f42dc08e8029002fb890b782452e2517e263a3e2378c517ad62",
                sha256("src/main/java/com/seggellion/britannia_mod/mixin/TitleScreenBackgroundMixin.java"));
        assertEquals(
                "4c00ef6650fd7860c04a2898d3016a77524b514a4ec83cfb4132f92f38a1d729",
                sha256("src/main/resources/britannia_mod.mixins.json"));
    }

    private static String sha256(String relativePath) throws Exception {
        byte[] bytes = Files.readAllBytes(PROJECT.resolve(relativePath));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record ExpectedAsset(String name, int width, int height) {}
}
