package com.seggellion.britannia_mod.roof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class RoofTextureMilestoneFourAssetTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("britannia.projectDir", "."));
    private static final Path ROOF_TEXTURES = PROJECT.resolve(
            "src/main/resources/assets/britannia_mod/textures/block/roof");
    private static final Path MANIFEST = PROJECT.resolve(
            "src/test/resources/roof/M4_TEXTURE_MANIFEST.json");
    private static final Set<String> EXPECTED_FILES = expectedFiles();

    @Test
    void productionSetHasExactlySixOrderedFilesPerMaterial() throws Exception {
        Set<String> actual;
        try (Stream<Path> files = Files.list(ROOF_TEXTURES)) {
            actual = files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("(?:sandstone|limestone)_roof_\\d+\\.png"))
                    .collect(java.util.stream.Collectors.toSet());
        }

        assertEquals(EXPECTED_FILES, actual);
    }

    @Test
    void everyTextureIsAValidOpaque64PixelRgbPng() throws Exception {
        for (String fileName : EXPECTED_FILES) {
            Path path = ROOF_TEXTURES.resolve(fileName);
            assertTrue(Files.isRegularFile(path), fileName);
            assertTrue(Files.size(path) > 0, fileName);
            try (InputStream input = Files.newInputStream(path)) {
                BufferedImage image = ImageIO.read(input);
                assertNotNull(image, fileName + " is not a decodable image");
                assertEquals(64, image.getWidth(), fileName);
                assertEquals(64, image.getHeight(), fileName);
                assertEquals(3, image.getColorModel().getNumColorComponents(), fileName);
                assertFalse(image.getColorModel().hasAlpha(), fileName);
            }
        }
    }

    @Test
    void duplicateGateReportsOnlyTheKnownSandstoneTwoAndFourPair() throws Exception {
        Map<String, String> hashes = new HashMap<>();
        for (String fileName : EXPECTED_FILES) {
            hashes.put(fileName, sha256(ROOF_TEXTURES.resolve(fileName)));
        }

        assertEquals(hashes.get("sandstone_roof_2.png"),
                hashes.get("sandstone_roof_4.png"));
        assertEquals(5, uniqueHashes(hashes, "sandstone").size());
        assertEquals(6, uniqueHashes(hashes, "limestone").size());
        assertEquals(11, new HashSet<>(hashes.values()).size());
    }

    @Test
    void manifestPinsSourceOrderNormalizationAndEveryOutputHash() throws Exception {
        JsonObject manifest = JsonParser.parseString(Files.readString(MANIFEST))
                .getAsJsonObject();
        assertEquals(1, manifest.get("schema").getAsInt());
        assertEquals(
                "9ae5a6a8b520d0710da961f0f637e246dcabefa949097df14eaeebb0354caf8a",
                manifest.getAsJsonObject("source").get("sha256").getAsString());
        JsonObject normalization = manifest.getAsJsonObject("normalization");
        assertEquals(64, normalization.get("width").getAsInt());
        assertEquals(64, normalization.get("height").getAsInt());
        assertEquals("RGB", normalization.get("mode").getAsString());
        assertTrue(normalization.get("algorithm").getAsString().contains("LANCZOS"));

        JsonObject duplicateGate = manifest.getAsJsonObject("duplicate_gate");
        assertEquals(5, duplicateGate.get("sandstone_unique_designs").getAsInt());
        assertEquals(List.of(2, 4), duplicateGate
                .getAsJsonArray("sandstone_duplicate_source_positions")
                .asList()
                .stream()
                .map(element -> element.getAsInt())
                .toList());
        assertEquals(6, duplicateGate.get("limestone_unique_designs").getAsInt());
        assertTrue(duplicateGate.get("final_sandstone_visual_acceptance_blocked")
                .getAsBoolean());

        JsonArray textures = manifest.getAsJsonArray("textures");
        assertEquals(12, textures.size());
        List<String> manifestFiles = new ArrayList<>();
        for (int index = 0; index < textures.size(); index++) {
            JsonObject texture = textures.get(index).getAsJsonObject();
            String material = index < 6 ? "sandstone" : "limestone";
            int sourcePosition = index % 6 + 1;
            String expectedXObject = "/Im" + index;
            String fileName = material + "_roof_" + sourcePosition + ".png";

            assertEquals(material, texture.get("material").getAsString());
            assertEquals(sourcePosition, texture.get("source_position").getAsInt());
            assertEquals(expectedXObject, texture.get("xobject").getAsString());
            assertEquals(1254, texture.get("source_width").getAsInt());
            assertEquals(1254, texture.get("source_height").getAsInt());
            assertEquals("RGB", texture.get("source_mode").getAsString());
            assertEquals(fileName, texture.get("output_file").getAsString());
            assertEquals(sha256(ROOF_TEXTURES.resolve(fileName)),
                    texture.get("output_png_sha256").getAsString());
            manifestFiles.add(fileName);
        }
        assertEquals(EXPECTED_FILES, Set.copyOf(manifestFiles));
    }

    private static Set<String> expectedFiles() {
        Set<String> names = new HashSet<>();
        for (String material : List.of("sandstone", "limestone")) {
            for (int variation = 1; variation <= 6; variation++) {
                names.add(material + "_roof_" + variation + ".png");
            }
        }
        return Set.copyOf(names);
    }

    private static Set<String> uniqueHashes(
            Map<String, String> hashes,
            String material) {
        Set<String> unique = new HashSet<>();
        hashes.forEach((name, hash) -> {
            if (name.startsWith(material + "_roof_")) {
                unique.add(hash);
            }
        });
        return unique;
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(Files.readAllBytes(path));
        return java.util.HexFormat.of().formatHex(hash);
    }
}
