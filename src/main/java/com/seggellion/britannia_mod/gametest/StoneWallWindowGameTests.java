package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.RotatingStoneWallBlock;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server-level proof that the stone wall window's open light is really open to interaction: the
 * outline raycast - the one the game aims the crosshair with - passes through the light and lands
 * on the chest beyond it, from either side of the wall and in all four facings, while the sill
 * still selects the window and the collision ray still treats the wall as solid. The model hugs
 * the facing edge, so the chest stands against the slab's outer face first and against the open
 * half of the cell second.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class StoneWallWindowGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** Centre of the light: {@code x 3..13} between {@code y 2} and the {@code y 12} corbels. */
    private static final double LIGHT_Y = 7.0D / 16.0D;
    /** Centre height of the sill courses at {@code y 0..2}. */
    private static final double SILL_Y = 1.0D / 16.0D;

    private StoneWallWindowGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void crosshairReachesThroughTheLightFromEitherSide(GameTestHelper helper) {
        Block window = BlockRegistry.STONE_WALL_WINDOW.get();
        BlockPos base = helper.absolutePos(new BlockPos(4, 3, 4));

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            helper.getLevel().setBlock(base,
                window.defaultBlockState().setValue(RotatingStoneWallBlock.FACING, facing),
                Block.UPDATE_ALL);

            for (Direction chestSide : new Direction[] {facing, facing.getOpposite()}) {
                BlockPos chest = base.relative(chestSide);
                helper.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);

                String where = facing + "/" + chestSide;
                check(chest.equals(outlineHit(helper, base, chestSide, LIGHT_Y)),
                    where + ": the crosshair through the light missed the chest");
                check(base.equals(outlineHit(helper, base, chestSide, SILL_Y)),
                    where + ": the sill stopped selecting the window");
                check(base.equals(colliderHit(helper, base, chestSide, LIGHT_Y)),
                    where + ": the light let a physical ray through the wall");

                helper.getLevel().removeBlock(chest, false);
            }

            helper.getLevel().removeBlock(base, false);
        }
        helper.succeed();
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
     * side, through the centre line of the window at the given height above its base, and names
     * the block it stopped in, or {@code null} when it passed everything.
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
