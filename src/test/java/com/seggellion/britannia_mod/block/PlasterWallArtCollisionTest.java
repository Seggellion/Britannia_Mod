package com.seggellion.britannia_mod.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.GameData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds {@code plaster_wall_blank} to the rule the whole {@link DoubleWallBlock} family lives by:
 * the collision strips have to sit where the model draws wall.
 *
 * <p>This family is the one that broke it. Its {@code _t_junction_branch_right} model was
 * re-authored as a single run on the edge <em>opposite</em> {@code facing}, with the perpendicular
 * branch removed, while the shared shape code went on returning the canonical "facing edge plus
 * branch edge". The player was stopped by nothing along two edges and walked through the wall they
 * could see on a third. {@link WallArtProfile} is what tells the shape code otherwise, and this is
 * what proves it still agrees with the JSON.
 *
 * <p>The expectation is read out of the model file rather than restated here, so re-cutting the art
 * again fails this test instead of quietly reintroducing the same defect.
 */
class PlasterWallArtCollisionTest {

    /** How far collision may sit outside the art, in model pixels, before it counts as a defect. */
    private static final double SLACK = 2.5D;

    private static DoubleWallBlock blankPlaster;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
        blankPlaster = new DoubleWallBlock(stoneProps());
    }

    private static BlockBehaviour.Properties stoneProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0F)
            .sound(SoundType.STONE).noOcclusion();
    }

    /* ─── the contract ───────────────────────────────────────── */

    @Test
    void collisionCoversEveryWallTheArtDraws() throws IOException {
        for (WindowArt.Variant variant : WindowArt.variantsOf("plaster_wall_blank")) {
            List<WindowArt.Box> art = WindowArt.solidElements(variant);
            if (art.isEmpty()) {
                continue; // the upper half draws nothing of its own
            }
            VoxelShape collision = collisionFor(variant);

            for (WindowArt.Box box : art) {
                WindowArt.Box clipped = box.eroded().clippedToCell(0, 0, 0);
                if (clipped == null || clipped.thinnestSide() <= 1.0D) {
                    continue; // skirting beads and other trim, not wall a player walks into
                }
                assertTrue(covered(clipped, collision),
                    variant.key() + " draws wall at " + clipped + " that collision "
                        + collision.toAabbs() + " leaves open");
            }
        }
    }

    @Test
    void collisionDoesNotStandWhereTheArtDrawsNothing() throws IOException {
        for (WindowArt.Variant variant : WindowArt.variantsOf("plaster_wall_blank")) {
            List<WindowArt.Box> art = WindowArt.solidElements(variant);
            if (art.isEmpty()) {
                continue;
            }
            VoxelShape collision = collisionFor(variant);
            assertFalse(Shapes.joinIsNotEmpty(collision, artEnvelope(art), BooleanOp.ONLY_FIRST),
                variant.key() + " collides at " + collision.toAabbs()
                    + " outside everything the model draws, even allowing " + SLACK + " pixels");
        }
    }

    /* ─── the junction the manual edit had flattened ─────────── */

    @Test
    void theBranchRightJunctionDrawsBothItsRuns() {
        // plaster_wall_blank_t_junction_branch_right.json had been re-authored as a single run on
        // the far edge with no branch at all, as a way of papering over the junction misalignment
        // that WallJunctionAlignmentTest now covers properly. With that fixed the model has to be
        // back on the family's convention, or the junction has nothing to reach its third
        // neighbour with.
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = shapeOf(junction(facing, true));
            assertTrue(reaches(shape, facing),
                facing + ": the main run hugs the facing edge");
            assertTrue(reaches(shape, facing.getClockWise()),
                facing + ": the branch hugs the clockwise edge when branch_right is set");
            assertFalse(reaches(shape, facing.getOpposite()),
                facing + ": nothing is drawn on the far edge");
        }
    }

    @Test
    void theJunctionShapeTurnsWithItsFacing() {
        VoxelShape canonical = shapeOf(junction(Direction.NORTH, true));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertShapesMatch("blank junction " + facing,
                HorizontalShape.rotateFromNorth(canonical, facing),
                shapeOf(junction(facing, true)));
        }
    }

    /* ─── helpers ────────────────────────────────────────────── */

    private static BlockState junction(Direction facing, boolean branchRight) {
        return state(facing, WallShape.T_JUNCTION, branchRight);
    }

    private static BlockState state(Direction facing, WallShape shape, boolean branchRight) {
        return blankPlaster.defaultBlockState()
            .setValue(DoubleWallBlock.FACING, facing)
            .setValue(DoubleWallBlock.SHAPE, shape)
            .setValue(DoubleWallBlock.BRANCH_RIGHT, branchRight)
            .setValue(DoubleWallBlock.HALF, DoubleBlockHalf.LOWER);
    }

    private static VoxelShape shapeOf(BlockState state) {
        return state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
    }

    /** The collision of the state a blockstate variant key names. */
    private static VoxelShape collisionFor(WindowArt.Variant variant) {
        BlockState state = blankPlaster.defaultBlockState();
        for (String pair : variant.key().split(",")) {
            String[] kv = pair.split("=", 2);
            state = switch (kv[0]) {
                case "facing" -> state.setValue(DoubleWallBlock.FACING,
                    Direction.byName(kv[1]));
                case "shape" -> state.setValue(DoubleWallBlock.SHAPE,
                    WallShape.valueOf(kv[1].toUpperCase(java.util.Locale.ROOT)));
                case "branch_right" -> state.setValue(DoubleWallBlock.BRANCH_RIGHT,
                    Boolean.parseBoolean(kv[1]));
                case "half" -> state.setValue(DoubleWallBlock.HALF,
                    kv[1].equals("upper") ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
                default -> state;
            };
        }
        return shapeOf(state);
    }

    /** True when no part of an art box sticks out of the collision shape. */
    private static boolean covered(WindowArt.Box art, VoxelShape collision) {
        VoxelShape solid = Shapes.create(new AABB(
            art.x0() / WindowArt.BLOCK, art.y0() / WindowArt.BLOCK, art.z0() / WindowArt.BLOCK,
            art.x1() / WindowArt.BLOCK, art.y1() / WindowArt.BLOCK, art.z1() / WindowArt.BLOCK));
        return !Shapes.joinIsNotEmpty(solid, collision, BooleanOp.ONLY_FIRST);
    }

    /**
     * The art of a variant, every box grown by {@link #SLACK} and unioned. Collision is allowed to
     * be coarser than the model - these families collide as whole edge strips on purpose - but not
     * to stand somewhere the model draws nothing at all.
     */
    private static VoxelShape artEnvelope(List<WindowArt.Box> art) {
        double pad = SLACK / WindowArt.BLOCK;
        VoxelShape envelope = Shapes.empty();
        for (WindowArt.Box a : art) {
            envelope = Shapes.or(envelope, Shapes.create(new AABB(
                a.x0() / WindowArt.BLOCK - pad, a.y0() / WindowArt.BLOCK - pad, a.z0() / WindowArt.BLOCK - pad,
                a.x1() / WindowArt.BLOCK + pad, a.y1() / WindowArt.BLOCK + pad, a.z1() / WindowArt.BLOCK + pad)));
        }
        return envelope;
    }

    /**
     * True when the shape fills the middle of the edge strip against {@code side}.
     *
     * <p>The probe sits a third of the way into the strip and centred on the other two axes, which
     * is inside that strip and outside all three others - a strip spans the full block the other
     * way, so anything nearer a corner would answer for two edges at once.
     */
    private static boolean reaches(VoxelShape shape, Direction side) {
        double near = 0.10D;
        double c = 0.50D;
        AABB probe = switch (side) {
            case NORTH -> new AABB(c, c, near, c, c, near);
            case SOUTH -> new AABB(c, c, 1.0D - near, c, c, 1.0D - near);
            case WEST  -> new AABB(near, c, c, near, c, c);
            case EAST  -> new AABB(1.0D - near, c, c, 1.0D - near, c, c);
            default    -> throw new IllegalArgumentException(side.toString());
        };
        return shape.toAabbs().stream().anyMatch(box ->
            box.minX <= probe.minX && box.maxX >= probe.maxX
                && box.minY <= probe.minY && box.maxY >= probe.maxY
                && box.minZ <= probe.minZ && box.maxZ >= probe.maxZ);
    }

    private static void assertShapesMatch(String where, VoxelShape expected, VoxelShape actual) {
        List<AABB> want = sorted(expected);
        List<AABB> got = sorted(actual);
        assertEquals(want.size(), got.size(), where + ": " + want + " vs " + got);
        for (int i = 0; i < want.size(); i++) {
            AABB a = want.get(i);
            AABB b = got.get(i);
            assertTrue(Math.abs(a.minX - b.minX) < 0.02D && Math.abs(a.minY - b.minY) < 0.02D
                    && Math.abs(a.minZ - b.minZ) < 0.02D && Math.abs(a.maxX - b.maxX) < 0.02D
                    && Math.abs(a.maxY - b.maxY) < 0.02D && Math.abs(a.maxZ - b.maxZ) < 0.02D,
                where + " expected " + a + " but got " + b);
        }
    }

    private static List<AABB> sorted(VoxelShape shape) {
        List<AABB> boxes = new ArrayList<>(shape.toAabbs());
        boxes.sort((a, b) -> {
            int cmp = Double.compare(a.minX, b.minX);
            if (cmp != 0) return cmp;
            cmp = Double.compare(a.minY, b.minY);
            if (cmp != 0) return cmp;
            return Double.compare(a.minZ, b.minZ);
        });
        return boxes;
    }
}
