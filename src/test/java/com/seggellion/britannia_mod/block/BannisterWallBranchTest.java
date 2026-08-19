package com.seggellion.britannia_mod.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.GameData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * The bannister's wall-facing return: the configuration the block could not express before, which
 * is a straight run with railing either side and a wall across the block from it.
 *
 * <p>Everything here is asked semantically - "does this arrangement of neighbours produce a return,
 * and does it point at the wall" - rather than by naming models or box coordinates, so the art can
 * be re-cut without the suite going red for no reason. The one thing pinned numerically is that
 * collision never leaves the block, because letting the *model* leave it is the whole point.
 */
class BannisterWallBranchTest {

    private static BannisterBlock bannister;
    private static DoubleWallBlock wall;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();

        bannister = new BannisterBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion());
        wall = new DoubleWallBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(2.0F).sound(SoundType.STONE).noOcclusion());
    }

    /* ─── the new configuration ──────────────────────────────── */

    @Test
    void aRunWithRailingEitherSideAndAWallOppositeGrowsAReturn() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState state = runFacing(facing).withWall(facing.getOpposite()).settle(facing);

            assertEquals(WallShape.STRAIGHT, state.getValue(BannisterBlock.SHAPE),
                facing + ": the run is still straight - a wall is not a bannister");
            assertTrue(state.getValue(BannisterBlock.WALL_BRANCH),
                facing + ": a wall across the block should raise the return");
            assertEquals(facing, state.getValue(BannisterBlock.FACING),
                facing + ": the run should keep hugging the edge it was placed on");
        }
    }

    @Test
    void theReturnReachesTheEdgeFacingTheWallAndAPlainRunDoesNot() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState returned = runFacing(facing).withWall(facing.getOpposite()).settle(facing);
            BlockState plain = runFacing(facing).settle(facing);

            Direction branch = returned.getValue(BannisterBlock.BRANCH_RIGHT)
                ? facing.getClockWise() : facing.getCounterClockWise();
            assertTrue(branch.getAxis() != facing.getAxis(),
                facing + ": the return must be perpendicular to the run");

            assertTrue(reaches(shapeOf(returned), branch),
                facing + ": the return should occupy the " + branch + " edge");
            assertFalse(reaches(shapeOf(plain), branch),
                facing + ": a plain run occupies only its own edge");
        }
    }

    /* ─── the configurations it must stay off ────────────────── */

    @Test
    void aLoneBannisterAgainstAWallGrowsNothing() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertFalse(new Scene().withWall(facing.getOpposite()).settle(facing)
                    .getValue(BannisterBlock.WALL_BRANCH),
                facing + ": a single bannister is a loose end, not the middle of a run");
        }
    }

    @Test
    void aRunSetAgainstTheWallItHugsStaysFlat() {
        // The ordinary way to fence a landing: the railing hugs the wall behind it. Growing a stub
        // into that wall would be wrong on every one of them.
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertFalse(runFacing(facing).withWall(facing).settle(facing)
                    .getValue(BannisterBlock.WALL_BRANCH),
                facing + ": a wall behind the run must not raise a return");
        }
    }

    @Test
    void aRunWithRailingOnOneSideOnlyGrowsNothing() {
        Direction facing = Direction.SOUTH;
        Scene scene = new Scene()
            .put(BlockPos.ZERO.relative(facing.getClockWise()), bannister.defaultBlockState())
            .withWall(facing.getOpposite());
        assertFalse(scene.settle(facing).getValue(BannisterBlock.WALL_BRANCH),
            "a run that stops here is an end, and its end post already meets the wall");
    }

    @Test
    void aCornerOrJunctionKeepsItsOwnBranch() {
        // A real bannister on the perpendicular axis owns that edge; the wall does not get it too.
        Direction facing = Direction.SOUTH;
        Scene scene = runFacing(facing).withWall(facing.getOpposite())
            .put(BlockPos.ZERO.relative(facing), bannister.defaultBlockState());

        BlockState state = scene.settle(facing);
        assertTrue(state.getValue(BannisterBlock.SHAPE) != WallShape.STRAIGHT,
            "three bannister neighbours make a junction");
        assertFalse(state.getValue(BannisterBlock.WALL_BRANCH),
            "the junction's own branch is the perpendicular run; the wall must not add a second");
    }

    @Test
    void anythingThatIsNotAnArchitecturalWallIsNotAWall() {
        Direction facing = Direction.SOUTH;
        for (BlockState neighbour : new BlockState[] {
                Blocks.AIR.defaultBlockState(),
                Blocks.STONE.defaultBlockState(),
                Blocks.OAK_FENCE.defaultBlockState()}) {
            Scene scene = runFacing(facing)
                .put(BlockPos.ZERO.relative(facing.getOpposite()), neighbour);
            assertFalse(scene.settle(facing).getValue(BannisterBlock.WALL_BRANCH),
                neighbour.getBlock() + " is not an architectural wall and must not raise a return");
        }
    }

    /* ─── neighbour changes ──────────────────────────────────── */

    @Test
    void takingTheWallAwayTakesTheReturnWithIt() {
        Direction facing = Direction.SOUTH;
        BlockPos wallPos = BlockPos.ZERO.relative(facing.getOpposite());

        Scene scene = runFacing(facing).withWall(facing.getOpposite());
        BlockState withWall = scene.settle(facing);
        assertTrue(withWall.getValue(BannisterBlock.WALL_BRANCH), "precondition: the return is up");

        scene.put(wallPos, Blocks.AIR.defaultBlockState());
        BlockState after = scene.update(withWall);
        assertFalse(after.getValue(BannisterBlock.WALL_BRANCH),
            "the return has to come down with the wall, without the player touching the bannister");

        scene.put(wallPos, wall.defaultBlockState());
        assertTrue(scene.update(after).getValue(BannisterBlock.WALL_BRANCH),
            "and go back up when the wall is replaced");
    }

    @Test
    void takingAwayTheRailingEitherSideTakesTheReturnWithIt() {
        Direction facing = Direction.SOUTH;
        Scene scene = runFacing(facing).withWall(facing.getOpposite());
        BlockState state = scene.settle(facing);

        scene.put(BlockPos.ZERO.relative(facing.getClockWise()), Blocks.AIR.defaultBlockState());
        assertFalse(scene.update(state).getValue(BannisterBlock.WALL_BRANCH),
            "with the run broken this is an end again");
    }

    /* ─── rotation, mirroring and shape hygiene ──────────────── */

    @Test
    void everyRotationOfTheArrangementResolvesTheSameWay() {
        BlockState north = runFacing(Direction.NORTH).withWall(Direction.SOUTH).settle(Direction.NORTH);

        for (Rotation rotation : Rotation.values()) {
            Direction facing = rotation.rotate(Direction.NORTH);
            BlockState derived = runFacing(facing).withWall(facing.getOpposite()).settle(facing);
            BlockState turned = bannister.rotate(north, rotation);

            assertEquals(turned.getValue(BannisterBlock.FACING),
                derived.getValue(BannisterBlock.FACING),
                rotation + ": turning the state should agree with deriving the turned world");
            assertEquals(turned.getValue(BannisterBlock.WALL_BRANCH),
                derived.getValue(BannisterBlock.WALL_BRANCH),
                rotation + ": the return survives rotation");
            assertEquals(turned.getValue(BannisterBlock.BRANCH_RIGHT),
                derived.getValue(BannisterBlock.BRANCH_RIGHT),
                rotation + ": BRANCH_RIGHT is relative to FACING, so it should not move");

            Direction branch = derived.getValue(BannisterBlock.BRANCH_RIGHT)
                ? facing.getClockWise() : facing.getCounterClockWise();
            assertTrue(reaches(shapeOf(derived), branch),
                rotation + ": the shape turns with the block");
        }
    }

    @Test
    void mirroringSwapsTheSideTheReturnHugs() {
        BlockState state = runFacing(Direction.NORTH).withWall(Direction.SOUTH).settle(Direction.NORTH);
        BlockState flipped = bannister.mirror(state, Mirror.LEFT_RIGHT);

        assertEquals(!state.getValue(BannisterBlock.BRANCH_RIGHT),
            flipped.getValue(BannisterBlock.BRANCH_RIGHT),
            "reflecting swaps the clockwise and counter-clockwise edges for the return too");
        assertTrue(flipped.getValue(BannisterBlock.WALL_BRANCH), "the return survives a reflection");
    }

    @Test
    void collisionNeverLeavesTheBlockEvenThoughTheModelDoes() {
        for (BlockState state : bannister.getStateDefinition().getPossibleStates()) {
            VoxelShape shape = shapeOf(state);
            assertFalse(shape.isEmpty(), state + " has no collision at all");
            AABB bounds = shape.bounds();
            assertTrue(bounds.minX >= -1.0E-6D && bounds.minY >= -1.0E-6D && bounds.minZ >= -1.0E-6D
                    && bounds.maxX <= 1.0D + 1.0E-6D && bounds.maxY <= 1.0D + 1.0E-6D
                    && bounds.maxZ <= 1.0D + 1.0E-6D,
                state + " collides outside its own block: " + bounds
                    + " - the wall next door already collides for its own space");
        }
    }

    @Test
    void everyStateTheBlockstateNamesIsOneTheBlockCanHold() {
        // 4 facings x 3 shapes x branch side x return. The blockstate file has to map all of them,
        // including the combinations derive() never produces, or the model loader logs a hole.
        assertEquals(48, bannister.getStateDefinition().getPossibleStates().size(),
            "bannister.json is generated against this count");
    }

    /* ─── scaffolding ────────────────────────────────────────── */

    /** The bannister under test sits at the origin; neighbours are whatever is put around it. */
    private static final class Scene implements BlockGetter {

        private final Map<BlockPos, BlockState> blocks = new HashMap<>();

        Scene put(BlockPos pos, BlockState state) {
            this.blocks.put(pos, state);
            return this;
        }

        Scene withWall(Direction side) {
            return put(BlockPos.ZERO.relative(side), wall.defaultBlockState());
        }

        /** The state a bannister placed at the origin facing {@code facing} settles into. */
        BlockState settle(Direction facing) {
            return update(bannister.defaultBlockState().setValue(BannisterBlock.FACING, facing));
        }

        /**
         * What the connection rule makes of a state already in the world. This is the same call
         * {@code updateShape} and {@code getStateForPlacement} both make, so a neighbour changing
         * and a block being placed are the same test.
         */
        BlockState update(BlockState state) {
            return bannister.derive(state, this, BlockPos.ZERO);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }

    /** A straight run: railing on both sides of the origin, along the edge {@code facing} names. */
    private static Scene runFacing(Direction facing) {
        Direction along = facing.getClockWise();
        return new Scene()
            .put(BlockPos.ZERO.relative(along), bannister.defaultBlockState())
            .put(BlockPos.ZERO.relative(along.getOpposite()), bannister.defaultBlockState());
    }

    private static VoxelShape shapeOf(BlockState state) {
        return state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
    }

    /** True when {@code shape} occupies the middle of the strip of block against {@code side}. */
    private static boolean reaches(VoxelShape shape, Direction side) {
        AABB probe = switch (side) {
            case NORTH -> new AABB(0.40D, 0.40D, 0.00D, 0.60D, 0.60D, 0.05D);
            case SOUTH -> new AABB(0.40D, 0.40D, 0.95D, 0.60D, 0.60D, 1.00D);
            case WEST  -> new AABB(0.00D, 0.40D, 0.40D, 0.05D, 0.60D, 0.60D);
            case EAST  -> new AABB(0.95D, 0.40D, 0.40D, 1.00D, 0.60D, 0.60D);
            default    -> throw new IllegalArgumentException(side.toString());
        };
        return shape.toAabbs().stream().anyMatch(box -> box.intersects(probe));
    }
}
