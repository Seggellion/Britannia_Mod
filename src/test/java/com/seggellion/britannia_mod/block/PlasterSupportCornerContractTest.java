package com.seggellion.britannia_mod.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.GameData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Independent one-post junction contract, not a snapshot of generated output.
 * The optional model directory allows the same assertions to be run on retained old art.
 * Rendering/UV appearance and actual mouse movement remain client acceptance gates.
 */
class PlasterSupportCornerContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path MODELS = Path.of(System.getProperty("britannia.plasterCornerModelDir",
        PROJECT.resolve("src/main/resources/assets/britannia_mod/models/block/structure/plaster").toString()));
    private static final String FAMILY = "plaster_wall_and_support_blank";
    private static MirrorableWallBlock wall;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
        wall = new MirrorableWallBlock(BlockBehaviour.Properties.of().noOcclusion());
    }

    private static JsonObject model(String suffix) throws IOException {
        return JsonParser.parseString(Files.readString(MODELS.resolve(FAMILY + suffix + ".json"))).getAsJsonObject();
    }

    private static WindowArt.Box bounds(JsonObject element) {
        var a = element.getAsJsonArray("from");
        var b = element.getAsJsonArray("to");
        return new WindowArt.Box(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(),
            b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble());
    }

    private static List<JsonObject> posts(JsonObject doc) {
        return doc.getAsJsonArray("elements").asList().stream().map(JsonElement::getAsJsonObject)
            .filter(e -> bounds(e).y1() - bounds(e).y0() >= 15)
            .filter(e -> e.getAsJsonObject("faces").entrySet().stream()
                .allMatch(f -> f.getValue().getAsJsonObject().get("texture").getAsString().equals("#2")))
            .toList();
    }

    @Test
    void singleJunctionPillarAcrossAllAffectedRotationsMirrorsAndVerticalSections() throws IOException {
        for (boolean mirrored : new boolean[] {false, true}) {
            JsonObject doc = model(mirrored ? "_corner_mirrored" : "_corner");
            assertEquals(1, posts(doc).size(), "one post relocation, not a two-post replacement");
            WindowArt.Box authored = bounds(posts(doc).getFirst());
            WindowArt.Box expected = mirrored
                ? new WindowArt.Box(9.3, 0, -0.1, 16.8, 32, 2.9)
                : new WindowArt.Box(-0.8, 0, -0.1, 6.7, 32, 2.9);
            for (int turn : new int[] {0, 90, 180, 270}) {
                assertEquals(expected.rotatedY(turn), authored.rotatedY(turn),
                    "junction timber: mirrored=" + mirrored + ", turn=" + turn);
                assertTrue(authored.y0() <= 0 && authored.y1() >= 32,
                    "lower model supplies timber below and above Y16");
            }
        }
    }

    private static boolean contains(WindowArt.Box b, double x, double y, double z) {
        return x >= b.x0() && x <= b.x1() && y >= b.y0() && y <= b.y1() && z >= b.z0() && z <= b.z1();
    }

    @Test
    void finishedPlasterCoversTheVacatedPostSegmentAndBranch() throws IOException {
        for (boolean mirrored : new boolean[] {false, true}) {
            JsonObject doc = model(mirrored ? "_corner_mirrored" : "_corner");
            var plaster = doc.getAsJsonArray("elements").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(e -> e.getAsJsonObject("faces").entrySet().stream()
                    .allMatch(f -> f.getValue().getAsJsonObject().get("texture").getAsString().equals("#1")))
                .map(PlasterSupportCornerContractTest::bounds).toList();
            for (double x : new double[] {0.01, 2.5, 4.99}) {
                double probeX = mirrored ? 16 - x : x;
                for (double y : new double[] {0.5, 8, 15.9, 16.1, 31.8}) {
                    for (double z : new double[] {3.0, 4.9, 5.1, 12.9, 13.5, 15.99}) {
                        assertTrue(plaster.stream().anyMatch(b -> contains(b, probeX, y, z)),
                            "finished branch plaster missing at " + probeX + "," + y + "," + z);
                    }
                }
            }
        }
    }

    @Test
    void reflectedCornerPreservesFacesAndTextureGrainSemantics() throws IOException {
        var original = model("_corner").getAsJsonArray("elements");
        var reflected = model("_corner_mirrored").getAsJsonArray("elements");
        assertEquals(original.size(), reflected.size());
        for (int i = 0; i < original.size(); i++) {
            var source = original.get(i).getAsJsonObject();
            var target = reflected.get(i).getAsJsonObject();
            WindowArt.Box b = bounds(source);
            assertEquals(new WindowArt.Box(16 - b.x1(), b.y0(), b.z0(), 16 - b.x0(), b.y1(), b.z1()), bounds(target));
            var targetFaces = target.getAsJsonObject("faces");
            assertEquals(source.getAsJsonObject("faces").size(), targetFaces.size());
            for (Map.Entry<String, JsonElement> entry : source.getAsJsonObject("faces").entrySet()) {
                String side = switch (entry.getKey()) { case "east" -> "west"; case "west" -> "east"; default -> entry.getKey(); };
                var a = entry.getValue().getAsJsonObject();
                var c = targetFaces.getAsJsonObject(side);
                assertNotNull(c);
                assertEquals(a.get("texture"), c.get("texture"));
                var uv = a.getAsJsonArray("uv");
                var mirroredUv = c.getAsJsonArray("uv");
                for (int j = 0; j < 4; j++) {
                    assertEquals(uv.get(switch (j) {case 0 -> 2; case 2 -> 0; default -> j;}), mirroredUv.get(j));
                }
                int rotation = a.has("rotation") ? a.get("rotation").getAsInt() : 0;
                assertEquals((360 - rotation) % 360, c.has("rotation") ? c.get("rotation").getAsInt() : 0);
            }
        }
    }

    @Test
    void halfCornerHasJunctionTimberAndExplicitCutCaps() throws IOException {
        JsonObject doc = model("_half_corner");
        assertEquals(1, posts(doc).size());
        assertEquals(new WindowArt.Box(-0.8, 0, -0.1, 6.7, 16, 2.9), bounds(posts(doc).getFirst()));
        for (JsonElement value : doc.getAsJsonArray("elements")) {
            var element = value.getAsJsonObject();
            assertTrue(bounds(element).y0() >= 0 && bounds(element).y1() <= 16);
            assertTrue(element.getAsJsonObject("faces").has("up"), "slice needs an explicit visible top cap");
        }
        for (WindowArt.Variant variant : WindowArt.variantsOf(FAMILY + "_half")) {
            assertFalse(variant.key().contains("mirrored=") || variant.key().contains("half="));
            assertTrue(Files.exists(PROJECT.resolve("src/main/resources/assets/britannia_mod/models/")
                .resolve(variant.model().split(":", 2)[1] + ".json")));
        }
    }

    private static VoxelShape edge(Direction direction) {
        return switch (direction) {
            case NORTH -> Block.box(0, 0, 0, 16, 16, 7);
            case SOUTH -> Block.box(0, 0, 9, 16, 16, 16);
            case EAST -> Block.box(9, 0, 0, 16, 16, 16);
            case WEST -> Block.box(0, 0, 0, 7, 16, 16);
            default -> throw new IllegalArgumentException();
        };
    }

    @Test
    void allSavedMappingsAndSevenPixelShapesRetainTheirContract() throws IOException {
        var variants = WindowArt.variantsOf(FAMILY);
        assertEquals(96, variants.size());
        var keys = new HashSet<String>();
        int upperCorners = 0;
        for (var variant : variants) {
            assertTrue(keys.add(variant.key()));
            var values = new java.util.HashMap<String, String>();
            for (String pair : variant.key().split(",")) { var p = pair.split("="); values.put(p[0], p[1]); }
            var facing = Direction.byName(values.get("facing"));
            assertNotNull(facing);
            var shape = WallShape.valueOf(values.get("shape").toUpperCase(java.util.Locale.ROOT));
            boolean branch = Boolean.parseBoolean(values.get("branch_right"));
            boolean mirror = Boolean.parseBoolean(values.get("mirrored"));
            boolean upper = values.get("half").equals("upper");
            var state = wall.defaultBlockState().setValue(DoubleWallBlock.FACING, facing)
                .setValue(DoubleWallBlock.SHAPE, shape).setValue(DoubleWallBlock.BRANCH_RIGHT, branch)
                .setValue(MirrorableWallBlock.MIRRORED, mirror)
                .setValue(DoubleWallBlock.HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
            VoxelShape expected = edge(facing);
            if (shape != WallShape.STRAIGHT) {
                expected = Shapes.or(expected, edge(branch ^ mirror ? facing.getClockWise() : facing.getCounterClockWise()));
            }
            assertFalse(Shapes.joinIsNotEmpty(expected,
                state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()), BooleanOp.NOT_SAME));
            assertFalse(Shapes.joinIsNotEmpty(expected,
                state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()), BooleanOp.NOT_SAME));
            if (upper) {
                assertEquals("minecraft:block/air", variant.model());
                if (shape == WallShape.CORNER) upperCorners++;
            } else {
                String suffix = "_" + values.get("shape");
                if (shape != WallShape.STRAIGHT && branch) suffix += "_branch_right";
                if (mirror) suffix += "_mirrored";
                assertEquals("britannia_mod:block/structure/plaster/" + FAMILY + suffix, variant.model());
                assertEquals(switch (facing) {case NORTH -> 0; case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> -1;}, variant.yRotation());
            }
        }
        assertEquals(16, upperCorners);
    }
}
