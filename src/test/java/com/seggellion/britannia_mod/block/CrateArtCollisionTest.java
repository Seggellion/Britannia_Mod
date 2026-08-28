package com.seggellion.britannia_mod.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.GameData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The crate shapes, held against the models they claim to describe.
 *
 * <p>Both single-cell crates shipped with collision inherited from art that no longer existed - the
 * medium crate still stood 14 voxels tall because {@code medium_crate.old} did, and the small crate
 * was three voxels too tall and five too deep. That is not cosmetic: {@code SupportType.FULL} reads
 * this shape when deciding whether a crate can carry another crate, and so does the player's raycast
 * when deciding which crate was clicked.
 *
 * <p>This suite is what notices if art and geometry part company again. It reads the model JSON
 * exactly as the game bakes it - element rotations included, which is why it cannot borrow
 * {@link WindowArt}, whose families have none.
 */
class CrateArtCollisionTest {

    /** How far a shape may sit outside the art before it counts as drift, in model pixels. */
    private static final double SLACK = 0.01D;

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path MODELS =
        PROJECT.resolve("src/main/resources/assets/britannia_mod/models/block/new_assets");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
    }

    /* ─── the contract ───────────────────────────────────────── */

    @Test
    void smallCrateCollisionIsTheBoundsOfItsModel() throws IOException {
        assertMatchesArt("small_crate", CrateShapes.SMALL);
    }

    @Test
    void mediumCrateCollisionIsTheBoundsOfItsModel() throws IOException {
        assertMatchesArt("medium_crate", CrateShapes.MEDIUM);
    }

    /**
     * The large crate spreads one model across eight cells, so each cell has to collide with exactly
     * the slice of the art that falls inside it - and with nothing beyond it.
     */
    @Test
    void everyLargeCrateCellCollidesWithItsOwnSliceOfTheModel() throws IOException {
        Box art = boundsOf("large_crate");
        for (int y = 0; y <= 1; y++) {
            for (int z = 0; z <= 1; z++) {
                for (int x = 0; x <= 1; x++) {
                    Box expected = art.clippedToCell(x, y, z);
                    String cell = "large_crate cell(" + x + "," + y + "," + z + ")";
                    assertTrue(expected != null, cell + " has no art and should not have been reserved");
                    assertBox(cell, expected, CrateShapes.largeCell(x, y, z));
                }
            }
        }
    }

    /**
     * The stale medium shape is the specific regression this suite exists for: it outlived
     * {@code medium_crate.old} by an entire art revision.
     */
    @Test
    void mediumCrateNoLongerCollidesAtTheHeightOfItsSupersededModel() throws IOException {
        double supersededHeight = boundsOf("medium_crate.old").y1();
        double current = CrateShapes.MEDIUM.max(Direction.Axis.Y) * 16.0D;

        assertEquals(14.0D, supersededHeight, 1.0E-6D, "medium_crate.old is no longer 14 voxels tall");
        assertTrue(current < supersededHeight - 1.0D,
            "medium crate still collides at the superseded 14-voxel height");
    }

    /** Rotating a crate must move its shape with it, or a rotated crate is clickable where it is not. */
    @Test
    void crateShapesFollowTheirFacing() {
        CrateBlock small = crate(9, 0, 0, 0, (x, y, z) -> CrateShapes.SMALL);
        VoxelShape north = shapeFor(small, Direction.NORTH);
        VoxelShape east = shapeFor(small, Direction.EAST);

        // A clockwise quarter turn sends (x, z) to (1 - z, x), so the crate's depth becomes its width
        // and is mirrored. The small crate is markedly shallower than it is wide, which is exactly why
        // a shape that failed to turn with it would leave the art clickable on the wrong side.
        assertEquals(1.0D - north.max(Direction.Axis.Z), east.min(Direction.Axis.X), 1.0E-6D);
        assertEquals(1.0D - north.min(Direction.Axis.Z), east.max(Direction.Axis.X), 1.0E-6D);
        assertEquals(north.min(Direction.Axis.X), east.min(Direction.Axis.Z), 1.0E-6D);
        assertEquals(north.max(Direction.Axis.X), east.max(Direction.Axis.Z), 1.0E-6D);
        assertEquals(north.min(Direction.Axis.Y), east.min(Direction.Axis.Y), 1.0E-6D);
        assertEquals(north.max(Direction.Axis.Y), east.max(Direction.Axis.Y), 1.0E-6D);

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = shapeFor(small, facing);
            assertTrue(shape.max(Direction.Axis.Y) > 0.0D, facing + " lost the crate's shape entirely");
            assertEquals(CrateShapes.SMALL.max(Direction.Axis.Y), shape.max(Direction.Axis.Y), 1.0E-6D,
                facing + " changed the crate's height");
        }
    }

    /** No crate may reach the top of its block, or it would never have needed a support exception. */
    @Test
    void noCrateFillsItsBlockVertically() {
        assertTrue(CrateShapes.SMALL.max(Direction.Axis.Y) < 1.0D);
        assertTrue(CrateShapes.MEDIUM.max(Direction.Axis.Y) < 1.0D);
        assertTrue(CrateShapes.largeCell(0, 1, 0).max(Direction.Axis.Y) < 1.0D);
    }

    /* ─── helpers ────────────────────────────────────────────── */

    private static void assertMatchesArt(String model, VoxelShape shape) throws IOException {
        assertBox(model, boundsOf(model), shape);
    }

    private static void assertBox(String what, Box expected, VoxelShape shape) {
        assertEquals(expected.x0(), shape.min(Direction.Axis.X) * 16.0D, SLACK, what + " min X");
        assertEquals(expected.x1(), shape.max(Direction.Axis.X) * 16.0D, SLACK, what + " max X");
        assertEquals(expected.z0(), shape.min(Direction.Axis.Z) * 16.0D, SLACK, what + " min Z");
        assertEquals(expected.z1(), shape.max(Direction.Axis.Z) * 16.0D, SLACK, what + " max Z");
        assertEquals(expected.y1(), shape.max(Direction.Axis.Y) * 16.0D, SLACK, what + " max Y");
        // A crate rests on the floor of its cell, so collision may start below the art but never above
        // it - a shape starting late leaves a lip the player falls through.
        assertTrue(shape.min(Direction.Axis.Y) * 16.0D <= expected.y0() + SLACK, what + " min Y");
    }

    private static CrateBlock crate(
            int slots, int maxX, int maxY, int maxZ, DecorativeMultiblockBlock.CellShapeFactory shapes) {
        return new CrateBlock(
            BlockBehaviour.Properties.of(), slots, "container.test.crate",
            0, maxX, 0, maxY, 0, maxZ, shapes);
    }

    private static VoxelShape shapeFor(CrateBlock block, Direction facing) {
        BlockState state = block.defaultBlockState().setValue(CrateBlock.FACING, facing);
        return block.getShape(state, EmptyBlockGetter.INSTANCE, null, CollisionContext.empty());
    }

    /** The model's bounds in block pixels, with element rotations baked in as the game bakes them. */
    private static Box boundsOf(String model) throws IOException {
        Path path = MODELS.resolve(model.endsWith(".old") ? model : model + ".json");
        JsonObject json = JsonParser
            .parseString(Files.readString(path, StandardCharsets.UTF_8))
            .getAsJsonObject();

        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE;
        double x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (var element : json.getAsJsonArray("elements")) {
            for (double[] corner : corners(element.getAsJsonObject())) {
                x0 = Math.min(x0, corner[0]); x1 = Math.max(x1, corner[0]);
                y0 = Math.min(y0, corner[1]); y1 = Math.max(y1, corner[1]);
                z0 = Math.min(z0, corner[2]); z1 = Math.max(z1, corner[2]);
            }
        }
        return new Box(x0, y0, z0, x1, y1, z1);
    }

    private static List<double[]> corners(JsonObject element) {
        JsonArray from = element.getAsJsonArray("from");
        JsonArray to = element.getAsJsonArray("to");
        List<double[]> points = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) {
            points.add(new double[] {
                (i & 1) == 0 ? from.get(0).getAsDouble() : to.get(0).getAsDouble(),
                (i & 2) == 0 ? from.get(1).getAsDouble() : to.get(1).getAsDouble(),
                (i & 4) == 0 ? from.get(2).getAsDouble() : to.get(2).getAsDouble()});
        }
        if (!element.has("rotation")) {
            return points;
        }
        JsonObject rotation = element.getAsJsonObject("rotation");
        double angle = Math.toRadians(rotation.get("angle").getAsDouble());
        if (angle == 0.0D) {
            return points;
        }
        JsonArray origin = rotation.getAsJsonArray("origin");
        String axis = rotation.get("axis").getAsString();
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        List<double[]> rotated = new ArrayList<>(points.size());
        for (double[] point : points) {
            double x = point[0] - origin.get(0).getAsDouble();
            double y = point[1] - origin.get(1).getAsDouble();
            double z = point[2] - origin.get(2).getAsDouble();
            double rx = x, ry = y, rz = z;
            switch (axis) {
                case "y" -> { rx = x * cos - z * sin; rz = x * sin + z * cos; }
                case "x" -> { ry = y * cos - z * sin; rz = y * sin + z * cos; }
                default -> { rx = x * cos - y * sin; ry = x * sin + y * cos; }
            }
            rotated.add(new double[] {
                rx + origin.get(0).getAsDouble(),
                ry + origin.get(1).getAsDouble(),
                rz + origin.get(2).getAsDouble()});
        }
        return rotated;
    }

    /** Model bounds in block pixels, where one cell spans 16. */
    private record Box(double x0, double y0, double z0, double x1, double y1, double z1) {

        /** The part of these bounds inside one cell, expressed in that cell's own pixels. */
        Box clippedToCell(int cellX, int cellY, int cellZ) {
            double nx0 = Math.max(x0, cellX * 16.0D);
            double nx1 = Math.min(x1, (cellX + 1) * 16.0D);
            double ny0 = Math.max(y0, cellY * 16.0D);
            double ny1 = Math.min(y1, (cellY + 1) * 16.0D);
            double nz0 = Math.max(z0, cellZ * 16.0D);
            double nz1 = Math.min(z1, (cellZ + 1) * 16.0D);
            if (nx1 <= nx0 || ny1 <= ny0 || nz1 <= nz0) {
                return null;
            }
            return new Box(
                nx0 - cellX * 16.0D, ny0 - cellY * 16.0D, nz0 - cellZ * 16.0D,
                nx1 - cellX * 16.0D, ny1 - cellY * 16.0D, nz1 - cellZ * 16.0D);
        }
    }
}
