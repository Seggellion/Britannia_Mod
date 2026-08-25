package com.seggellion.britannia_mod.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class DisplayCaseContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));

    @Test
    void oneFinalDecorativeIdHasNoStorageImplementation() throws Exception {
        String blockRegistry = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/registry/BlockRegistry.java"))
                .replace("\r\n", "\n");
        assertTrue(blockRegistry.contains("BLOCKS.register(\n            \"display_case\""));
        assertFalse(blockRegistry.contains("display_case_end"));
        assertFalse(blockRegistry.contains("display_case_middle"));
        assertFalse(blockRegistry.contains("display_case_corner"));
        assertTrue(blockRegistry.contains(".sound(SoundType.WOOD).noOcclusion().dynamicShape()"),
                "topology-aware upper collision must not be cached as an independent shape");

        String source = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/block/DisplayCaseBlock.java"));
        assertFalse(source.contains("EntityBlock"));
        assertFalse(source.contains("BlockEntity"));
        assertFalse(source.contains("Container"));
        assertTrue(source.contains("ConnectionForm.INDEPENDENT"));
        assertTrue(source.contains("ConnectionForm.END"));
        assertTrue(source.contains("ConnectionForm.MIDDLE"));
        assertTrue(source.contains("ConnectionForm.CORNER"));
        assertTrue(source.contains("return hasValidPart(state) ? RenderShape.MODEL : RenderShape.INVISIBLE"),
                "the upper cage cell must not inherit the shared non-root invisibility policy");
        assertTrue(source.contains("getBlockSupportShape"),
                "the short upper cage needs a dedicated logical support face");
        assertTrue(source.contains("UPPER_PLACEMENT_SURFACE"),
                "center clicks need to resolve to the occupied upper rendering cell");
        assertTrue(source.contains("getCollisionShape"),
                "the logical placement face must stay out of physical collision");
        assertFalse(source.contains("useItemOn("),
                "display-case placement must remain on Minecraft's ordinary item-use path");
        assertFalse(source.contains("useWithoutItem("),
                "the decorative display case must not consume empty-hand interaction");
    }

    @Test
    void ownerModelsCoverEveryTopologyAndStayWithinTwoBlocks() throws Exception {
        Path resources = PROJECT.resolve("src/main/resources");
        String[] relatives = {
            "assets/britannia_mod/blockstates/display_case.json",
            "assets/britannia_mod/models/block/new_assets/display_case_independent.json",
            "assets/britannia_mod/models/block/new_assets/display_case_base_independent.json",
            "assets/britannia_mod/models/block/new_assets/display_case_base_connected.json",
            "assets/britannia_mod/models/block/new_assets/display_case_frame_independent.json",
            "assets/britannia_mod/models/block/new_assets/display_case_frame_end.json",
            "assets/britannia_mod/models/block/new_assets/display_case_frame_straight.json",
            "assets/britannia_mod/models/block/new_assets/display_case_frame_corner.json",
            "assets/britannia_mod/models/block/new_assets/display_case_frame_tee.json",
            "assets/britannia_mod/models/block/new_assets/display_case_empty.json",
            "assets/britannia_mod/models/item/display_case.json",
            "data/britannia_mod/loot_table/blocks/display_case.json"
        };
        for (String relative : relatives) {
            JsonParser.parseString(Files.readString(resources.resolve(relative)));
        }

        String blockstate = Files.readString(resources.resolve(
                "assets/britannia_mod/blockstates/display_case.json"));
        assertEquals(4, count(blockstate, "display_case_base_independent"));
        assertEquals(4, count(blockstate, "display_case_base_connected"));
        assertEquals(1, count(blockstate, "display_case_frame_independent"));
        assertEquals(4, count(blockstate, "display_case_frame_end"));
        assertEquals(2, count(blockstate, "display_case_frame_straight"));
        assertEquals(4, count(blockstate, "display_case_frame_corner"));
        assertEquals(4, count(blockstate, "display_case_frame_tee"));
        assertTrue(blockstate.contains("display_case_empty"));
        assertFalse(blockstate.contains("display_case_connected"));
        assertFalse(blockstate.contains("display_case_corner.json"));
        assertTrue(blockstate.contains("\"facing\": \"north\""));
        assertTrue(blockstate.contains("\"facing\": \"east\""));
        assertTrue(blockstate.contains("\"facing\": \"south\""));
        assertTrue(blockstate.contains("\"facing\": \"west\""));
        assertTrue(blockstate.contains("\"y\": 90"));
        assertTrue(blockstate.contains("\"y\": 180"));
        assertTrue(blockstate.contains("\"y\": 270"));
        assertEquals(15, count(blockstate, "\"part\": \"1\""),
                "every cage topology must render from the upper cell for correct light sampling");

        for (String relative : relatives) {
            if (!relative.contains("models/block")) continue;
            JsonObject model = JsonParser.parseString(Files.readString(resources.resolve(relative)))
                    .getAsJsonObject();
            if (!model.has("elements")) continue;
            assertFalse(model.get("ambientocclusion").getAsBoolean(),
                    "display-case model retained blackening ambient occlusion");
            model.getAsJsonArray("elements").forEach(value -> {
                JsonObject element = value.getAsJsonObject();
                assertTrue(element.has("shade") && !element.get("shade").getAsBoolean(),
                        "display-case element retained directional blackening");
                for (String bound : new String[] {"from", "to"}) {
                    var coordinates = element.getAsJsonArray(bound);
                    double[] maximum = {16.0D, 32.0D, 16.0D};
                    for (int axis = 0; axis < coordinates.size(); axis++) {
                        double number = coordinates.get(axis).getAsDouble();
                        assertTrue(number >= 0.0D && number <= maximum[axis],
                                "display-case model escaped its 16x16x32 envelope");
                    }
                }
            });

            var bounds = new HashSet<String>();
            model.getAsJsonArray("elements").forEach(value -> {
                JsonObject element = value.getAsJsonObject();
                String signature = element.getAsJsonArray("from") + "|" + element.getAsJsonArray("to");
                assertTrue(bounds.add(signature), "display-case model retained coplanar duplicate bounds");
            });
        }
    }

    @Test
    void runtimeGeometryRetainsOwnerDimensionsTextureAndComponentCounts() throws Exception {
        Path assets = PROJECT.resolve("src/main/resources/assets/britannia_mod");
        JsonObject independent = model(assets, "display_case_independent.json");
        JsonObject independentBase = model(assets, "display_case_base_independent.json");
        JsonObject connectedBase = model(assets, "display_case_base_connected.json");
        JsonObject independentFrame = model(assets, "display_case_frame_independent.json");
        JsonObject endFrame = model(assets, "display_case_frame_end.json");
        JsonObject straightFrame = model(assets, "display_case_frame_straight.json");
        JsonObject cornerFrame = model(assets, "display_case_frame_corner.json");
        JsonObject teeFrame = model(assets, "display_case_frame_tee.json");

        assertEquals(13, independent.getAsJsonArray("elements").size());
        assertEquals(1, independentBase.getAsJsonArray("elements").size());
        assertEquals(1, connectedBase.getAsJsonArray("elements").size());
        assertEquals(12, independentFrame.getAsJsonArray("elements").size());
        assertEquals(10, endFrame.getAsJsonArray("elements").size());
        assertEquals(8, straightFrame.getAsJsonArray("elements").size());
        assertEquals(7, cornerFrame.getAsJsonArray("elements").size());
        assertEquals(4, teeFrame.getAsJsonArray("elements").size());
        for (JsonObject model : new JsonObject[] {
                independent, independentBase, connectedBase, independentFrame,
                endFrame, straightFrame, cornerFrame, teeFrame
        }) {
            assertEquals("britannia_mod:block/new_assets/display_case",
                    model.getAsJsonObject("textures").get("0").getAsString());
        }
        assertEquals(16.0D, maximum(connectedBase, 1));
        assertEquals(22.0D, maximum(independent, 1));
        assertEquals(6.0D, maximum(independentFrame, 1));
        assertEquals(6.0D, maximum(endFrame, 1));
        assertEquals(6.0D, maximum(straightFrame, 1));
        assertEquals(6.0D, maximum(cornerFrame, 1));
        assertEquals(6.0D, maximum(teeFrame, 1));
        assertEquals(0.0D, minimum(independentFrame, 1));
        assertEquals(0.0D, minimum(endFrame, 1));
        assertEquals(0.0D, minimum(straightFrame, 1));
        assertEquals(0.0D, minimum(cornerFrame, 1));
        assertEquals(0.0D, minimum(teeFrame, 1));
        assertEquals(1.0D, minimum(independent, 0));
        assertEquals(1.0D, minimum(independent, 2));
        assertEquals(15.0D, maximum(independent, 0));
        assertEquals(15.0D, maximum(independent, 2));
        assertEquals(0.0D, minimum(connectedBase, 0));
        assertEquals(16.0D, maximum(connectedBase, 0));
        assertEquals(0.0D, minimum(cornerFrame, 2));
        assertEquals(16.0D, maximum(cornerFrame, 2));

        BufferedImage texture = ImageIO.read(assets.resolve("textures/block/new_assets/display_case.png").toFile());
        assertEquals(128, texture.getWidth());
        assertEquals(128, texture.getHeight());
        for (int y = 0; y < texture.getHeight(); y++) {
            for (int x = 0; x < texture.getWidth(); x++) {
                assertEquals(255, texture.getColorModel().getAlpha(texture.getRaster().getDataElements(x, y, null)),
                        "the owner texture is expected to remain fully opaque");
            }
        }

        String item = Files.readString(assets.resolve("models/item/display_case.json"));
        assertTrue(item.contains("display_case_independent"));
        assertTrue(item.contains("\"gui\""));
        assertTrue(item.contains("\"scale\": [0.5, 0.5, 0.5]"));
    }

    private static JsonObject model(Path assets, String name) throws Exception {
        return JsonParser.parseString(Files.readString(
                assets.resolve("models/block/new_assets").resolve(name))).getAsJsonObject();
    }

    private static double minimum(JsonObject model, int axis) {
        double minimum = Double.POSITIVE_INFINITY;
        for (var value : model.getAsJsonArray("elements")) {
            minimum = Math.min(minimum,
                    value.getAsJsonObject().getAsJsonArray("from").get(axis).getAsDouble());
        }
        return minimum;
    }

    private static double maximum(JsonObject model, int axis) {
        double maximum = Double.NEGATIVE_INFINITY;
        for (var value : model.getAsJsonArray("elements")) {
            maximum = Math.max(maximum,
                    value.getAsJsonObject().getAsJsonArray("to").get(axis).getAsDouble());
        }
        return maximum;
    }

    private static int count(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
