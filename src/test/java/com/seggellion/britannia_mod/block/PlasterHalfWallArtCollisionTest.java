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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The half walls, held to the same rule as the full-height family: the collision strips have to sit
 * where the model draws wall, and nowhere else.
 *
 * <p>They failed it in a way no amount of connection logic could fix. Each half family shipped with
 * a single model - the straight run - and all twenty-four of its blockstate variants pointed at it.
 * A corner or a junction therefore drew a plain run while {@link PlasterWallHalfBlock} went on
 * collidng along the branch edge as well, so the player was stopped by nothing there, and worse,
 * the arm the junction was supposed to draw was simply absent: the neighbour's run reaches the face
 * they share and then stops eleven voxels short of the run inside the junction's own block. That is
 * the gap where two half walls meet at right angles.
 *
 * <p>The junction models are now cut from the full-height ones by
 * {@code tools/generate_half_walls.py}. This is what notices if a family ever loses them again -
 * collision standing where the art draws nothing is exactly the shape of that defect.
 */
class PlasterHalfWallArtCollisionTest {

    /** How far collision may sit outside the art, in model pixels, before it counts as a defect. */
    private static final double SLACK = 2.5D;

    private static final List<String> FAMILIES = List.of(
        "plaster_wall_blank_half",
        "plaster_wall_and_support_blank_half",
        "plaster_wall_support_diagonal_east_half",
        "plaster_wall_support_diagonal_south_half");

    private static PlasterWallHalfBlock halfWall;
    private static PlasterWallBlankHalfBlock blankHalfWall;
    private static DoubleWallBlock fullWall;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
        halfWall = new PlasterWallHalfBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(2.0F).sound(SoundType.STONE).noOcclusion());
        blankHalfWall = new PlasterWallBlankHalfBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(2.0F).sound(SoundType.STONE).noOcclusion());
        fullWall = new DoubleWallBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(2.0F).sound(SoundType.STONE).noOcclusion());
    }

    /* ─── the contract ───────────────────────────────────────── */

    @Test
    void collisionCoversEveryWallTheArtDraws() throws IOException {
        for (String family : FAMILIES) {
            for (WindowArt.Variant variant : WindowArt.variantsOf(family)) {
                List<WindowArt.Box> art = WindowArt.solidElements(variant);
                VoxelShape collision = collisionFor(variant);

                for (WindowArt.Box box : art) {
                    WindowArt.Box clipped = box.eroded().clippedToCell(0, 0, 0);
                    if (clipped == null || clipped.thinnestSide() <= 1.0D) {
                        continue; // skirting beads and other trim, not wall a player walks into
                    }
                    assertTrue(covered(clipped, collision),
                        family + " " + variant.key() + " draws wall at " + clipped
                            + " that collision " + collision.toAabbs() + " leaves open");
                }
            }
        }
    }

    @Test
    void collisionDoesNotStandWhereTheArtDrawsNothing() throws IOException {
        for (String family : FAMILIES) {
            for (WindowArt.Variant variant : WindowArt.variantsOf(family)) {
                List<WindowArt.Box> art = WindowArt.solidElements(variant);
                VoxelShape collision = collisionFor(variant);
                assertFalse(Shapes.joinIsNotEmpty(collision, artEnvelope(art), BooleanOp.ONLY_FIRST),
                    family + " " + variant.key() + " collides at " + collision.toAabbs()
                        + " outside everything the model draws, even allowing " + SLACK
                        + " pixels - the state has no art of its own");
            }
        }
    }

    @Test
    void aCornerOrJunctionDrawsSomethingAStraightRunDoesNot() throws IOException {
        // The defect in one line: every variant pointed at the straight model, so a junction and a
        // straight were the same picture. Whatever the art becomes, those two cannot be equal.
        for (String family : FAMILIES) {
            Set<String> straight = new LinkedHashSet<>();
            Set<String> junction = new LinkedHashSet<>();
            for (WindowArt.Variant variant : WindowArt.variantsOf(family)) {
                (variant.key().contains("shape=straight") ? straight : junction).add(variant.model());
            }
            assertEquals(1, straight.size(), family + " should have one straight model");
            assertFalse(junction.isEmpty(), family + " has no corner or junction variants");
            assertTrue(java.util.Collections.disjoint(straight, junction),
                family + " maps a corner or junction back onto its straight model " + straight
                    + " - that state would draw a plain run and never reach its neighbour");
        }
    }

    @Test
    void everyStateStillCollidesAndStaysInsideItsOwnBlock() {
        for (BlockState state : halfWall.getStateDefinition().getPossibleStates()) {
            VoxelShape shape = state.getCollisionShape(
                EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
            assertFalse(shape.isEmpty(), state + " has no collision at all");
            AABB bounds = shape.bounds();
            assertTrue(bounds.minX >= -1.0E-6D && bounds.minZ >= -1.0E-6D
                    && bounds.maxX <= 1.0D + 1.0E-6D && bounds.maxZ <= 1.0D + 1.0E-6D,
                state + " collides outside its own block: " + bounds);
        }
    }

    @Test
    void formsACornerWhereAHalfWallRunMeetsAPerpendicularFullPlasterWall() {
        for (Direction fullWallSide : Direction.Plane.HORIZONTAL) {
            Direction halfEdge = fullWallSide.getClockWise();
            Direction fullEdge = fullWallSide.getOpposite();

            BlockScene scene = new BlockScene()
                .put(BlockPos.ZERO.relative(fullWallSide.getOpposite()), halfWall.defaultBlockState()
                    .setValue(PlasterWallHalfBlock.FACING, halfEdge))
                .put(BlockPos.ZERO.relative(fullWallSide), fullWall.defaultBlockState()
                    .setValue(DoubleWallBlock.FACING, fullEdge));

            BlockState result = halfWall.derive(
                halfWall.defaultBlockState().setValue(PlasterWallHalfBlock.FACING, halfEdge),
                scene, BlockPos.ZERO);

            assertEquals(WallShape.CORNER, result.getValue(PlasterWallHalfBlock.SHAPE),
                "half wall did not form a corner into its " + fullWallSide + " full wall");
            WallConnection run = new WallConnection(
                result.getValue(PlasterWallHalfBlock.SHAPE),
                result.getValue(PlasterWallHalfBlock.FACING),
                result.getValue(PlasterWallHalfBlock.BRANCH_RIGHT));
            assertEquals(fullWallSide, run.secondary(),
                "corner return points away from its " + fullWallSide + " full-wall neighbour");
        }
    }

    @Test
    void aLonePerpendicularFullPlasterWallProducesAVisibleReturn() {
        for (Direction neighbourSide : Direction.Plane.HORIZONTAL) {
            Direction halfEdge = neighbourSide.getCounterClockWise();
            Direction fullEdge = neighbourSide.getOpposite();
            BlockScene scene = new BlockScene().put(
                BlockPos.ZERO.relative(neighbourSide),
                fullWall.defaultBlockState().setValue(DoubleWallBlock.FACING, fullEdge));

            BlockState result = halfWall.derive(
                halfWall.defaultBlockState().setValue(PlasterWallHalfBlock.FACING, halfEdge),
                scene, BlockPos.ZERO);

            assertEquals(WallShape.CORNER, result.getValue(PlasterWallHalfBlock.SHAPE),
                "half wall did not return into its " + neighbourSide + " full-wall neighbour");
            assertEquals(halfEdge, result.getValue(PlasterWallHalfBlock.FACING),
                "forming the return moved the half wall's player-placed main run");
            assertEquals(neighbourSide == halfEdge.getClockWise(),
                result.getValue(PlasterWallHalfBlock.BRANCH_RIGHT),
                "return was placed on the wrong edge for " + neighbourSide);
            WallConnection run = new WallConnection(
                result.getValue(PlasterWallHalfBlock.SHAPE),
                result.getValue(PlasterWallHalfBlock.FACING),
                result.getValue(PlasterWallHalfBlock.BRANCH_RIGHT));
            assertEquals(neighbourSide, run.secondary(),
                "return does not occupy the shared face toward " + neighbourSide);
        }
    }

    @Test
    void anOffsetPerpendicularFullWallStillProducesTheCornerReturn() {
        // The full wall is on the far edge of its own block in this layout. That offset is exactly
        // why the half wall needs its corner arm; requiring the full wall to occupy the shared face
        // reduced this state to a straight and recreated the visible gap from the client screenshot.
        for (Direction neighbourSide : Direction.Plane.HORIZONTAL) {
            Direction halfEdge = neighbourSide.getClockWise();
            Direction fullFarEdge = neighbourSide;
            BlockScene scene = new BlockScene().put(
                BlockPos.ZERO.relative(neighbourSide),
                fullWall.defaultBlockState().setValue(DoubleWallBlock.FACING, fullFarEdge));

            BlockState result = halfWall.derive(
                halfWall.defaultBlockState().setValue(PlasterWallHalfBlock.FACING, halfEdge),
                scene, BlockPos.ZERO);

            assertEquals(WallShape.CORNER, result.getValue(PlasterWallHalfBlock.SHAPE),
                "offset full wall suppressed the half-wall corner toward " + neighbourSide);
            WallConnection run = new WallConnection(
                result.getValue(PlasterWallHalfBlock.SHAPE),
                result.getValue(PlasterWallHalfBlock.FACING),
                result.getValue(PlasterWallHalfBlock.BRANCH_RIGHT));
            assertEquals(neighbourSide, run.secondary(),
                "offset corner return points away from its " + neighbourSide + " neighbour");
        }
    }

    @Test
    void blankHalfWallExtendsItsOffsetReturnAcrossTheFarEdgeGapOnly() {
        for (Direction neighbourSide : Direction.Plane.HORIZONTAL) {
            Direction halfEdge = neighbourSide.getClockWise();
            BlockPos neighbourPos = BlockPos.ZERO.relative(neighbourSide);

            BlockScene farScene = new BlockScene().put(neighbourPos,
                fullWall.defaultBlockState().setValue(DoubleWallBlock.FACING, neighbourSide));
            BlockState far = blankHalfWall.derive(
                blankHalfWall.defaultBlockState().setValue(PlasterWallHalfBlock.FACING, halfEdge),
                farScene, BlockPos.ZERO);

            assertTrue(far.getValue(PlasterWallBlankHalfBlock.OFFSET_RETURN),
                "far-edge full wall did not select offset return toward " + neighbourSide);
            AABB bounds = far.getShape(farScene, BlockPos.ZERO, CollisionContext.empty()).bounds();
            boolean reachesNeighbour = switch (neighbourSide) {
                case WEST -> bounds.minX < 0.0D;
                case EAST -> bounds.maxX > 1.0D;
                case NORTH -> bounds.minZ < 0.0D;
                case SOUTH -> bounds.maxZ > 1.0D;
                default -> false;
            };
            assertTrue(reachesNeighbour,
                "offset return collision does not cross the " + neighbourSide + " boundary: " + bounds);

            BlockScene nearScene = new BlockScene().put(neighbourPos,
                fullWall.defaultBlockState().setValue(
                    DoubleWallBlock.FACING, neighbourSide.getOpposite()));
            BlockState near = blankHalfWall.derive(
                blankHalfWall.defaultBlockState().setValue(PlasterWallHalfBlock.FACING, halfEdge),
                nearScene, BlockPos.ZERO);
            assertFalse(near.getValue(PlasterWallBlankHalfBlock.OFFSET_RETURN),
                "ordinary shared-face corner incorrectly selected the extended model toward "
                    + neighbourSide);
        }
    }

    @Test
    void offsetReturnModelsActuallyDrawAcrossTheElevenPixelGap() throws IOException {
        int offsetCorners = 0;
        for (WindowArt.Variant variant : WindowArt.variantsOf("plaster_wall_blank_half")) {
            boolean offsetCorner = variant.key().contains("shape=corner")
                && variant.key().contains("offset_return=true");
            if (!offsetCorner) {
                assertFalse(variant.model().endsWith("_offset_return"),
                    "ordinary half-wall state unexpectedly uses extended art: " + variant.key());
                continue;
            }

            offsetCorners++;
            assertTrue(variant.model().endsWith("_offset_return"),
                "offset state maps to an ordinary corner model: " + variant.key());
            boolean reachesFarEdge = WindowArt.solidElements(variant).stream().anyMatch(box ->
                box.x0() <= -10.0D || box.x1() >= 26.0D
                    || box.z0() <= -10.0D || box.z1() >= 26.0D);
            assertTrue(reachesFarEdge,
                "offset corner art stops at its block boundary: " + variant.key());
        }
        assertEquals(8, offsetCorners, "expected both corner hands in all four rotations");
    }

    /* ─── helpers ────────────────────────────────────────────── */

    private static VoxelShape collisionFor(WindowArt.Variant variant) {
        BlockState state = halfWall.defaultBlockState();
        for (String pair : variant.key().split(",")) {
            String[] kv = pair.split("=", 2);
            state = switch (kv[0]) {
                case "facing" -> state.setValue(PlasterWallHalfBlock.FACING, Direction.byName(kv[1]));
                case "shape" -> state.setValue(PlasterWallHalfBlock.SHAPE,
                    WallShape.valueOf(kv[1].toUpperCase(Locale.ROOT)));
                case "branch_right" -> state.setValue(PlasterWallHalfBlock.BRANCH_RIGHT,
                    Boolean.parseBoolean(kv[1]));
                default -> state;
            };
        }
        return state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
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
}
