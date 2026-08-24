package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.TallThinBlock;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server-level proof that the dark stone window's open light is really open to interaction: the
 * outline raycast - the one the game aims the crosshair with - passes through the light and lands
 * on the chest beyond it, from either side of the wall and in all four facings, while the frame
 * around the light still selects the window and the collision ray still treats the wall as solid.
 * The panel-side pass for north and south runs with {@code FILLED} set by the chest itself, which
 * is exactly the state that used to select as a full invisible cube.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class DarkStoneWindowGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** Centre of the lower half's light: {@code y 6..16} of the bottom model. */
    private static final double LOWER_LIGHT_Y = 11.0D / 16.0D;
    /** Centre of the upper half's light: {@code y 0..10} of the top model, one block up. */
    private static final double UPPER_LIGHT_Y = 1.0D + 5.0D / 16.0D;
    /** Centre of the sill: {@code y 0..6} of the bottom model. */
    private static final double SILL_Y = 3.0D / 16.0D;
    /** Centre of the lintel: {@code y 10..16} of the top model, one block up. */
    private static final double LINTEL_Y = 1.0D + 13.0D / 16.0D;

    private DarkStoneWindowGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void crosshairReachesThroughTheLightFromEitherSide(GameTestHelper helper) {
        TallThinBlock window = BlockRegistry.DARK_STONE_WINDOW.get();
        BlockPos base = helper.absolutePos(new BlockPos(4, 3, 4));

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            placeWindow(helper, window, base, facing);

            // The chest stands against the panel's outer face first, then against the opposite
            // neighbour, and the ray comes from the other side each time. For north and south the
            // panel-side cell is also the rear-gap cell, so that pass runs with FILLED true; for
            // east and west it is the opposite pass. Both flag values get exercised per facing.
            for (Direction chestSide : new Direction[] {
                    panelSideOf(facing), panelSideOf(facing).getOpposite()}) {
                BlockPos chest = base.relative(chestSide);
                BlockPos backing = chest.above();
                helper.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
                helper.getLevel().setBlock(backing, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

                String where = facing + "/" + chestSide;
                boolean filled = chestSide == facing.getOpposite();
                check(helper.getLevel().getBlockState(base).getValue(TallThinBlock.FILLED) == filled,
                    where + ": the filled flag should be " + filled + " with the chest there");

                check(chest.equals(outlineHit(helper, base, chestSide, LOWER_LIGHT_Y)),
                    where + ": the crosshair through the lower light missed the chest");
                check(backing.equals(outlineHit(helper, base, chestSide, UPPER_LIGHT_Y)),
                    where + ": the crosshair through the upper light missed the backing block");
                check(base.equals(outlineHit(helper, base, chestSide, SILL_Y)),
                    where + ": the sill stopped selecting the window");
                check(base.above().equals(outlineHit(helper, base, chestSide, LINTEL_Y)),
                    where + ": the lintel stopped selecting the window");
                check(base.equals(colliderHit(helper, base, chestSide, LOWER_LIGHT_Y)),
                    where + ": the light let a physical ray through the wall");

                helper.getLevel().removeBlock(backing, false);
                helper.getLevel().removeBlock(chest, false);
            }

            // Dropping the upper half makes the lower break itself, the way the pair always dies.
            helper.getLevel().removeBlock(base.above(), false);
            helper.getLevel().removeBlock(base, false);
        }
        helper.succeed();
    }

    private static void placeWindow(GameTestHelper helper, TallThinBlock window, BlockPos base,
                                    Direction facing) {
        BlockState lower = window.defaultBlockState().setValue(TallThinBlock.FACING, facing);
        helper.getLevel().setBlock(base, lower, Block.UPDATE_ALL);
        helper.getLevel().setBlock(base.above(),
            lower.setValue(TallThinBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /**
     * The neighbour touching the panel's outer face. The art hugs the far edge for north and south
     * and the near edge for east and west - the double inversion documented on
     * {@code TallThinBlock#panelFor}.
     */
    private static Direction panelSideOf(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.SOUTH;
            case SOUTH -> Direction.NORTH;
            default -> facing;
        };
    }

    private static BlockPos outlineHit(GameTestHelper helper, BlockPos base, Direction toward,
                                       double yOffset) {
        return hit(helper, base, toward, yOffset, ClipContext.Block.OUTLINE);
    }

    private static BlockPos colliderHit(GameTestHelper helper, BlockPos base, Direction toward,
                                        double yOffset) {
        return hit(helper, base, toward, yOffset, ClipContext.Block.COLLIDER);
    }

    /**
     * Fires a ray from two blocks out on the far side of the window straight toward the target
     * side, through the centre line of the window column at the given height above its base, and
     * names the block it stopped in, or {@code null} when it passed everything.
     */
    private static BlockPos hit(GameTestHelper helper, BlockPos base, Direction toward,
                                double yOffset, ClipContext.Block mode) {
        Vec3 from = pointAlong(base, toward, -2.0D, yOffset);
        Vec3 to = pointAlong(base, toward, 2.0D, yOffset);
        BlockHitResult result = helper.getLevel().clip(
            new ClipContext(from, to, mode, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return result.getType() == HitResult.Type.MISS ? null : result.getBlockPos();
    }

    private static Vec3 pointAlong(BlockPos base, Direction axis, double along, double yOffset) {
        return new Vec3(
            base.getX() + 0.5D + axis.getStepX() * along,
            base.getY() + yOffset,
            base.getZ() + 0.5D + axis.getStepZ() * along);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
