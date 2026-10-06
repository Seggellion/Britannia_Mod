package com.seggellion.britannia_mod.gametest;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.entity.AlligatorEntity;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("britannia_alligator")
@PrefixGameTestTemplate(false)
public final class AlligatorMovementGameTests {
    private static final String TEMPLATE = "alligator_tank";

    @GameTestGenerator
    public static java.util.Collection<TestFunction> depthMatrix() {
        var tests = new java.util.ArrayList<TestFunction>();
        Vec3[] starts = {new Vec3(4, 7, 4), new Vec3(4, 4, 4), new Vec3(6, 3, 6)};
        Vec3[] ends = {new Vec3(10, 3, 10), new Vec3(11, 4, 4), new Vec3(6, 8.3, 6)};
        String[] names = {"diagonal", "depth", "ascent"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            tests.add(new TestFunction("alligator", "alligator." + names[i], "britannia_alligator:" + TEMPLATE,
                    260, 0, true, h -> route(h, starts[index], ends[index], names[index])));
        }
        return tests;
    }

    private static void route(GameTestHelper h, Vec3 start, Vec3 end, String label) {
        tank(h, true);
        AlligatorEntity a = spawn(h, start, true);
        Vec3 target = h.absoluteVec(end);
        h.runAtTickTime(5, () -> a.getNavigation().moveTo(target.x, target.y, target.z, 1));
        for (int t = 10; t <= 250; t += 10) {
            int tick = t;
            h.runAtTickTime(t, () -> trace(a, label, tick, target));
        }
        h.runAtTickTime(250, () -> {
            h.assertTrue(a.isAlive() && Math.abs(a.getY() - target.y) <= .5
                    && a.position().subtract(target).horizontalDistance() <= .8,
                    label + " failed: " + a.position() + " target=" + target);
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1220, batch = "alligator-performance")
    public static void twentyEntityPerformance(GameTestHelper h) {
        tank(h, true);
        for (int i = 0; i < 20; i++) {
            var a = spawn(h, new Vec3(2 + (i % 5) * 2.4, 7.5, 2 + (i / 5) * 3), false);
            a.getRandom().setSeed(1800 + i);
        }
        var durations = new java.util.ArrayList<Long>();
        final long[] last = {System.nanoTime()};
        h.onEachTick(() -> {
            long now = System.nanoTime();
            if (h.getTick() > 100) durations.add(now - last[0]);
            last[0] = now;
        });
        h.runAtTickTime(1200, () -> {
            durations.sort(Long::compare);
            LogUtils.getLogger().info("ALLIGATOR performance entities=20 ticks=1200 medianWallTickNs={} samples={} serverAverageMs={}",
                    durations.get(durations.size() / 2), durations.size(), h.getLevel().getServer().getAverageTickTimeNanos() / 1_000_000.0);
            h.succeed();
        });
    }

    private static void tank(GameTestHelper h, boolean water) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y < 12; y++) h.setBlock(new BlockPos(x, y, z),
                    water && y <= 8 ? (x == 0 || x == 15 || z == 0 || z == 15 ? Blocks.GLASS : Blocks.WATER) : Blocks.AIR);
        }
    }

    private static AlligatorEntity spawn(GameTestHelper h, Vec3 local, boolean forced) {
        AlligatorEntity a = h.spawn(EntityRegistry.ALLIGATOR_ENTITY.get(), local);
        a.getRandom().setSeed(18);
        a.setPersistenceRequired();
        a.setDeltaMovement(Vec3.ZERO);
        if (forced) {
            // FloatGoal owns JUMP and deliberately remains in the pre-fix reproduction.
            a.goalSelector.removeAllGoals(g -> g.getFlags().contains(Goal.Flag.MOVE));
            a.targetSelector.removeAllGoals(g -> g.getFlags().contains(Goal.Flag.TARGET));
        }
        return a;
    }

    private static void trace(AlligatorEntity a, String label, int tick, Vec3 target) {
        var path = a.getNavigation().getPath();
        LogUtils.getLogger().info("ALLIGATOR {} tick={} target={} pos={} air={} eyes={} velocity={} input={},{},{} wantedY={} path={} reachable={} next={}",
                label, tick, target, a.position(), a.getAirSupply(), a.isUnderWater(), a.getDeltaMovement(),
                a.xxa, a.yya, a.zza, a.getMoveControl().getWantedY(), path == null ? "null" : path.getEndNode(),
                path != null && path.canReach(), path == null || path.isDone() ? "done" : path.getNextNodePos());
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 225)
    public static void verticalDescent(GameTestHelper h) {
        tank(h, true);
        AlligatorEntity a = spawn(h, new Vec3(6, 7, 6), true);
        Vec3 target = Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(6, 3, 6)));
        h.runAtTickTime(5, () -> a.getNavigation().moveTo(target.x, target.y, target.z, 1));
        for (int t = 10; t <= 200; t += 10) {
            int tick = t;
            h.runAtTickTime(t, () -> trace(a, "descent", tick, target));
        }
        h.runAtTickTime(200, () -> {
            h.assertTrue(Math.abs(a.getY() - target.y) <= .5 && a.position().subtract(target).horizontalDistance() <= .8,
                    "Four-block descent failed: " + a.position() + " target=" + target);
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 260)
    public static void eightBlockLandCourse(GameTestHelper h) {
        tank(h, false);
        AlligatorEntity a = spawn(h, new Vec3(3, 1, 6), true);
        Vec3 target = Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(11, 1, 6)));
        // Width 1.6 means the evaluator's integer node has a +1 X/Z entity center.
        // Request that node; measure the actual eight-block physical course below.
        h.runAtTickTime(5, () -> a.getNavigation().moveTo(target.x - 1, target.y, target.z - 1, 0, 1));
        for (int t = 6; t <= 250; t++) {
            int tick = t;
            h.runAtTickTime(t, () -> {
                if (tick % 25 == 0) trace(a, "land-progress", tick - 5, target);
                if (a.position().subtract(target).horizontalDistance() <= 1) {
                    trace(a, "land-arrival", tick - 5, target);
                    h.succeed();
                }
            });
        }
    }
}
