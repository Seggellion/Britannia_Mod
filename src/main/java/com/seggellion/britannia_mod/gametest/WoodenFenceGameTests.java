package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.WoodenFenceBlock;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-level connection update and collision coverage for the edge-mounted wooden fence. */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class WoodenFenceGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private WoodenFenceGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void derivesEveryConnectionTopologyAndDisconnectsAfterRemoval(GameTestHelper helper) {
        WoodenFenceBlock fence = BlockRegistry.WOODEN_FENCE.get();

        BlockPos single = absolute(helper, 2, 3, 2);
        place(helper, fence, single, Direction.NORTH);
        check(topology(helper, single).equals("isolated"), "single fence was not isolated");

        BlockPos straight = absolute(helper, 2, 3, 5);
        for (int offset = 0; offset < 4; offset++) {
            place(helper, fence, straight.east(offset), Direction.NORTH);
        }
        check(topology(helper, straight).equals("end"), "straight run west end was not an end");
        check(topology(helper, straight.east()).equals("straight"), "straight run middle was not straight");
        check(topology(helper, straight.east(2)).equals("straight"), "second middle was not straight");
        check(topology(helper, straight.east(3)).equals("end"), "straight run east end was not an end");
        helper.getLevel().removeBlock(straight.east(2), false);
        check(topology(helper, straight.east()).equals("end"), "removal did not terminate the remaining run");
        check(topology(helper, straight.east(3)).equals("isolated"), "removal did not isolate the far section");
        place(helper, fence, straight.east(2), Direction.SOUTH);
        check(topology(helper, straight.east()).equals("straight"), "replacement did not restore the run");

        BlockPos corner = absolute(helper, 8, 3, 2);
        place(helper, fence, corner, Direction.NORTH);
        place(helper, fence, corner.east(), Direction.NORTH);
        place(helper, fence, corner.south(), Direction.WEST);
        check(topology(helper, corner).equals("corner"), "adjacent neighbours did not form a corner");

        BlockPos tee = absolute(helper, 9, 3, 7);
        place(helper, fence, tee, Direction.NORTH);
        place(helper, fence, tee.east(), Direction.NORTH);
        place(helper, fence, tee.west(), Direction.NORTH);
        place(helper, fence, tee.south(), Direction.WEST);
        check(topology(helper, tee).equals("t_junction"), "three neighbours did not form a T junction");

        BlockPos cross = absolute(helper, 5, 3, 10);
        place(helper, fence, cross, Direction.NORTH);
        place(helper, fence, cross.north(), Direction.WEST);
        place(helper, fence, cross.east(), Direction.NORTH);
        place(helper, fence, cross.south(), Direction.WEST);
        place(helper, fence, cross.west(), Direction.NORTH);
        check(topology(helper, cross).equals("cross"), "four neighbours did not form a cross");

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void collisionTracksAllFourEdgesAndJunctionStrips(GameTestHelper helper) {
        WoodenFenceBlock fence = BlockRegistry.WOODEN_FENCE.get();
        BlockPos pos = absolute(helper, 4, 3, 4);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState state = fence.defaultBlockState().setValue(WoodenFenceBlock.FACING, facing);
            AABB bounds = state.getCollisionShape(helper.getLevel(), pos).bounds();
            switch (facing) {
                case NORTH -> check(bounds.minZ == 0.0D && bounds.maxZ == 0.25D, "north collision drifted from edge");
                case EAST -> check(bounds.minX == 0.75D && bounds.maxX == 1.0D, "east collision drifted from edge");
                case SOUTH -> check(bounds.minZ == 0.75D && bounds.maxZ == 1.0D, "south collision drifted from edge");
                case WEST -> check(bounds.minX == 0.0D && bounds.maxX == 0.25D, "west collision drifted from edge");
                default -> throw new GameTestAssertException("unexpected vertical facing");
            }
            check(bounds.getXsize() < 1.0D || bounds.getZsize() < 1.0D,
                "isolated collision became a full cube");
        }

        place(helper, fence, pos, Direction.NORTH);
        place(helper, fence, pos.east(), Direction.NORTH);
        place(helper, fence, pos.south(), Direction.WEST);
        var cornerBoxes = helper.getLevel().getBlockState(pos)
            .getCollisionShape(helper.getLevel(), pos).toAabbs();
        check(cornerBoxes.size() == 2, "corner collision did not contain two edge strips");
        helper.succeed();
    }

    private static void place(GameTestHelper helper, WoodenFenceBlock fence,
                              BlockPos pos, Direction facing) {
        helper.getLevel().setBlock(pos,
            fence.defaultBlockState().setValue(WoodenFenceBlock.FACING, facing), Block.UPDATE_ALL);
    }

    private static BlockPos absolute(GameTestHelper helper, int x, int y, int z) {
        return helper.absolutePos(new BlockPos(x, y, z));
    }

    private static String topology(GameTestHelper helper, BlockPos pos) {
        BlockState state = helper.getLevel().getBlockState(pos);
        check(state.is(BlockRegistry.WOODEN_FENCE.get()), "expected wooden fence at " + pos);
        int count = WoodenFenceBlock.connectionCount(state);
        if (count == 0) return "isolated";
        if (count == 1) return "end";
        if (count == 2) {
            boolean opposite = state.getValue(WoodenFenceBlock.NORTH) && state.getValue(WoodenFenceBlock.SOUTH)
                || state.getValue(WoodenFenceBlock.EAST) && state.getValue(WoodenFenceBlock.WEST);
            return opposite ? "straight" : "corner";
        }
        return count == 3 ? "t_junction" : "cross";
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
