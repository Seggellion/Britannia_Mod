package com.seggellion.britannia_mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Regression coverage for the finished two-block sandstone architectural family. */
class SandstoneFamilyCompletionTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path ASSETS = PROJECT.resolve("src/main/resources/assets/britannia_mod");
    private static final Path MODELS = ASSETS.resolve("models/block/structure/sandstone");
    private static final Path TEXTURES = ASSETS.resolve("textures/block/structure/sandstone");
    private static final String MATERIAL = "britannia_mod:block/structure/sandstone/custom_sandstone_brick_0";
    private static final String TOP = "britannia_mod:block/structure/sandstone/custom_sandstone_brick_top_0";
    private static final List<String> IDS = List.of(
        "ornate_sandstone_wall", "regular_sandstone_wall", "sandstone_block_wall",
        "ornate_sandstone_window", "sandstone_window", "sandstone_post",
        "ornate_sandstone_post", "sandstone_battlement", "sandstone_column");
    private static final List<String> SHAPES = List.of(
        "straight", "corner", "corner_branch_right", "t_junction", "t_junction_branch_right");

    @Test
    void allNineArchitecturalIdsKeepCompleteDirectionalStateAndModelCoverage() throws Exception {
        for (String id : IDS) {
            JsonObject variants = json(ASSETS.resolve("blockstates/" + id + ".json"))
                .getAsJsonObject("variants");
            assertEquals(48, variants.size(), id + " must cover every inherited DoubleWallBlock state");
            for (String shape : SHAPES) {
                assertTrue(Files.isRegularFile(MODELS.resolve(id + "_" + shape + ".json")),
                    "missing " + id + " model for " + shape);
            }
        }
    }

    @Test
    void jsonGeometryDrawsEveryFaceFromTheCustomSandstoneMaterialSet() throws Exception {
        Map<String, String> expectedTops = Map.of(
            "ornate_sandstone_wall", texture("custom_sandstone_ornate_1"),
            "ornate_sandstone_post", texture("custom_sandstone_ornate_1"),
            "ornate_sandstone_window", texture("custom_sandstone_ornate_2"));

        for (String id : IDS) {
            if (id.equals("sandstone_battlement")) continue;
            for (String shape : SHAPES) {
                JsonObject model = json(MODELS.resolve(id + "_" + shape + ".json"));
                JsonObject textures = model.getAsJsonObject("textures");
                assertEquals(MATERIAL, textures.get("side").getAsString());
                assertEquals(expectedTops.getOrDefault(id, TOP), textures.get("top").getAsString());
                // Patch 18 rewrote the straight/corner shapes of several ids onto the
                // side/top pair alone; shapes not yet migrated keep a dedicated bottom.
                boolean dedicatedBottom = textures.has("bottom");
                if (dedicatedBottom) assertEquals(MATERIAL, textures.get("bottom").getAsString());
                for (JsonElement elementValue : model.getAsJsonArray("elements")) {
                    JsonObject element = elementValue.getAsJsonObject();
                    assertTrue(element.getAsJsonArray("to").get(1).getAsDouble() <= 32.0D);
                    JsonObject faces = element.getAsJsonObject("faces");
                    for (String direction : List.of("north", "south", "east", "west")) {
                        if (!faces.has(direction)) continue;
                        String face = faceTexture(faces, direction);
                        if (dedicatedBottom) assertEquals("#side", face);
                        else assertTrue(List.of("#side", "#top").contains(face),
                            id + "_" + shape + " " + direction + " face uses " + face);
                    }
                    if (faces.has("up")) assertEquals("#top", faceTexture(faces, "up"));
                    if (faces.has("down")) {
                        String face = faceTexture(faces, "down");
                        if (dedicatedBottom) assertEquals("#bottom", face);
                        else assertTrue(List.of("#side", "#top").contains(face),
                            id + "_" + shape + " down face uses " + face);
                    }
                }
            }
        }
    }

    @Test
    void bothWindowModelsContainARealOpening() throws Exception {
        // The sandstone window draws a stepped arch: the light spans x 2..14 above its y 9 sill
        // and corbels in to x 6..10 under the lintel at y 28. The ornate window keeps a squared
        // x 3..13 light over a taller y 11 sill. Each rectangle below is a part of a light that
        // must stay clear of geometry for the window to be a real opening and not decorated wall.
        assertLightStaysClear("sandstone_window", 2.0D, 9.0D, 14.0D, 18.0D);
        assertLightStaysClear("sandstone_window", 6.0D, 9.0D, 10.0D, 28.0D);
        assertLightStaysClear("ornate_sandstone_window", 3.0D, 11.0D, 13.0D, 26.0D);

        String block = Files.readString(PROJECT.resolve(
            "src/main/java/com/seggellion/britannia_mod/block/SandstoneWindowBlock.java"));
        assertTrue(block.contains("archedMainRun"));
        assertTrue(block.contains("straightLower"));
        assertTrue(block.contains("getCollisionShape"));
    }

    private static void assertLightStaysClear(String id, double x0, double y0, double x1, double y1)
            throws Exception {
        JsonArray elements = json(MODELS.resolve(id + "_straight.json")).getAsJsonArray("elements");
        assertTrue(elements.size() >= 4);
        for (JsonElement value : elements) {
            JsonObject element = value.getAsJsonObject();
            JsonArray from = element.getAsJsonArray("from");
            JsonArray to = element.getAsJsonArray("to");
            boolean intrudesIntoOpening = from.get(0).getAsDouble() < x1
                && to.get(0).getAsDouble() > x0
                && from.get(1).getAsDouble() < y1
                && to.get(1).getAsDouble() > y0;
            assertFalse(intrudesIntoOpening, id + " has opaque geometry inside its light over x "
                + x0 + ".." + x1 + ", y " + y0 + ".." + y1);
        }
    }

    @Test
    void battlementsUseTrueObjSlopesAndCornerMeshesAcrossTheTwoBlockEnvelope() throws Exception {
        for (String shape : SHAPES) {
            JsonObject model = json(MODELS.resolve("sandstone_battlement_" + shape + ".json"));
            assertEquals("neoforge:obj", model.get("loader").getAsString());
            assertFalse(model.get("automatic_culling").getAsBoolean());
            JsonObject textures = model.getAsJsonObject("textures");
            assertEquals(MATERIAL, textures.get("side").getAsString());
            assertEquals(TOP, textures.get("top").getAsString());
            assertEquals(MATERIAL, textures.get("bottom").getAsString());
            String mesh = model.get("model").getAsString();
            Path meshPath = ASSETS.resolve(mesh.substring(mesh.indexOf("models/")));
            assertTrue(Files.isRegularFile(meshPath), "missing battlement mesh " + meshPath);
            String obj = Files.readString(meshPath);
            assertTrue(obj.contains("v 0.000000 0.000000 0.000000"));
            assertTrue(obj.contains("2.000000"), "battlement must reach the full 32-voxel envelope");
            assertTrue(obj.contains("usemtl side"));
            assertTrue(obj.contains("usemtl top"));
            assertTrue(obj.contains("usemtl bottom"));
        }
        assertFalse(Files.readString(MODELS.resolve("geometry/sandstone_battlement_corner.obj"))
            .equals(Files.readString(MODELS.resolve("geometry/sandstone_battlement_straight.obj"))),
            "corner must be a converging wedge rather than a rotated straight mesh");
    }

    @Test
    void suppliedSixtyFourPixelMaterialAndOrnateTexturesArePresent() throws Exception {
        for (String name : List.of("custom_sandstone_brick_0", "custom_sandstone_brick_top_0",
                                   "custom_sandstone_ornate_1", "custom_sandstone_ornate_2")) {
            var image = ImageIO.read(TEXTURES.resolve(name + ".png").toFile());
            assertTrue(image != null, "unreadable texture " + name);
            assertEquals(64, image.getWidth());
            assertEquals(64, image.getHeight());
        }
    }

    private static String texture(String name) {
        return "britannia_mod:block/structure/sandstone/" + name;
    }

    private static String faceTexture(JsonObject faces, String direction) {
        return faces.getAsJsonObject(direction).get("texture").getAsString();
    }

    private static JsonObject json(Path path) throws Exception {
        assertTrue(Files.isRegularFile(path), "missing resource " + path);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
