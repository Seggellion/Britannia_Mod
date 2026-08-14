package com.seggellion.britannia_mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Regression contracts for the alternating sandstone-brick row artwork. */
class SandstoneBrickStackContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path ASSETS = PROJECT.resolve("src/main/resources/assets/britannia_mod");

    @Test
    void stackStateAlternatesFromTheLowestContiguousBrick() throws Exception {
        String block = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/block/CustomSandstoneBrickBlock.java"));

        assertTrue(block.contains("BooleanProperty TOP_ROW"));
        assertTrue(block.contains("context.getClickedPos().below()"));
        assertTrue(block.contains("direction == Direction.DOWN"));
        assertTrue(block.contains("below.is(this) && !below.getValue(TOP_ROW)"));
        assertTrue(block.contains(
                "random.nextInt(TEXTURE_VARIANT.getPossibleValues().size())"));
    }

    @Test
    void allFourVariantsExistForBothRowGroups() throws Exception {
        JsonObject variants = json(ASSETS.resolve("blockstates/custom_sandstone_brick.json"))
                .getAsJsonObject("variants");
        assertEquals(32, variants.size());

        Path modelDirectory = ASSETS.resolve("models/block/structure/sandstone");
        Path textureDirectory = ASSETS.resolve("textures/block/structure/sandstone");
        for (int variant = 0; variant < 4; variant++) {
            assertTrue(Files.isRegularFile(modelDirectory.resolve(
                    "custom_sandstone_brick_" + variant + ".json")));
            assertTrue(Files.isRegularFile(modelDirectory.resolve(
                    "custom_sandstone_brick_top_" + variant + ".json")));

            BufferedImage bottom = ImageIO.read(textureDirectory.resolve(
                    "custom_sandstone_brick_" + variant + ".png").toFile());
            BufferedImage top = ImageIO.read(textureDirectory.resolve(
                    "custom_sandstone_brick_top_" + variant + ".png").toFile());
            assertTrue(bottom != null);
            assertTrue(top != null);
            assertEquals(bottom.getWidth(), top.getWidth());
            assertEquals(bottom.getHeight(), top.getHeight());
        }
    }

    private static JsonObject json(Path path) throws Exception {
        assertTrue(Files.isRegularFile(path), "missing resource " + path);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
