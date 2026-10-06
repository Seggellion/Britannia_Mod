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

    @GameTest(template = TEMPLATE, templateNamespace = "britannia_alligator_performance", timeoutTicks = 1220, batch = "alligator-performance")
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

    static void tank(GameTestHelper h, boolean water) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y < 12; y++) h.setBlock(new BlockPos(x, y, z),
                    water && y <= 8 ? (x == 0 || x == 15 || z == 0 || z == 15 ? Blocks.GLASS : Blocks.WATER) : Blocks.AIR);
        }
    }

    static AlligatorEntity spawn(GameTestHelper h, Vec3 local, boolean forced) {
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
        for (int t = 180; t < 200; t++) h.runAtTickTime(t, () ->
                h.assertTrue(Math.abs(a.getY() - target.y) <= .5, "Descent must hold for 20 ticks"));
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
                    h.assertTrue(tick - 5 <= 44, "Land course regressed beyond 10% of the 40-tick baseline");
                    trace(a, "land-arrival", tick - 5, target);
                    h.succeed();
                }
            });
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 260)
    public static void bankExit(GameTestHelper h) { bank(h, false); }

    @GameTest(template = TEMPLATE, timeoutTicks = 260)
    public static void bankEntry(GameTestHelper h) { bank(h, true); }

    private static void bank(GameTestHelper h, boolean entering) {
        tank(h, true);
        for (int x = 10; x < 15; x++) for (int z = 1; z < 15; z++)
            for (int y = 1; y <= 8; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        AlligatorEntity a = spawn(h, entering ? new Vec3(12, 9, 6) : new Vec3(6, 7, 6), true);
        Vec3 target = h.absoluteVec(entering ? new Vec3(6, 3, 6) : new Vec3(12, 9, 6));
        h.runAtTickTime(5, () -> a.getNavigation().moveTo(target.x, target.y, target.z, 1));
        for (int t = 25; t <= 250; t += 25) {
            int tick = t;
            h.runAtTickTime(t, () -> trace(a, entering ? "bank-entry" : "bank-exit", tick, target));
        }
        h.runAtTickTime(250, () -> {
            h.assertTrue(Math.abs(a.getY() - target.y) <= .5
                    && a.position().subtract(target).horizontalDistance() <= 1.5, "Shore transition failed: " + a.position());
            h.assertTrue(a.isInWater() == entering, "Shore transition did not change water state");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 210)
    public static void oneBlockGroundJump(GameTestHelper h) {
        tank(h, false);
        for (int x = 7; x < 15; x++) for (int z = 1; z < 15; z++)
            h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        AlligatorEntity a = spawn(h, new Vec3(3, 1, 6), true);
        Vec3 target = h.absoluteVec(new Vec3(11, 2, 6));
        h.runAtTickTime(5, () -> a.getNavigation().moveTo(target.x - 1, target.y, target.z - 1, 0, 1));
        h.runAtTickTime(200, () -> {
            trace(a, "ground-jump", 200, target);
            h.assertTrue(Math.abs(a.getY() - target.y) <= .1 && a.position().subtract(target).horizontalDistance() < 1,
                    "Vanilla obstacle jump delegation failed");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 210)
    public static void depthHoldWithLookAndKnockback(GameTestHelper h) {
        tank(h, true);
        AlligatorEntity a = spawn(h, new Vec3(6, 4, 6), true);
        Vec3 target = h.absoluteVec(new Vec3(6, 4, 6));
        h.runAtTickTime(5, () -> a.getNavigation().moveTo(target.x, target.y, target.z, 1));
        for (int t = 40; t <= 140; t++) h.runAtTickTime(t, () -> {
            a.getLookControl().setLookAt(a.getX() + 4, a.getY() + 7, a.getZ() - 4);
            h.assertTrue(Math.abs(a.getY() - target.y) <= .5, "Look pitch changed depth hold");
        });
        h.runAtTickTime(145, () -> {
            a.getNavigation().stop();
            a.setDeltaMovement(new Vec3(.5, .2, 0));
        });
        h.runAtTickTime(146, () -> {
            h.assertTrue(a.getDeltaMovement().x > .2, "Water stop erased external impulse");
            h.assertTrue(a.yya == 0 && a.xxa == 0 && a.zza == 0, "Stopped navigation left stale input");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 260)
    public static void submergedObstacleBypass(GameTestHelper h) {
        tank(h, true);
        for (int z = 1; z <= 9; z++) for (int y = 1; y <= 8; y++)
            h.setBlock(new BlockPos(8, y, z), Blocks.STONE);
        AlligatorEntity a = spawn(h, new Vec3(5, 4, 6), true);
        Vec3 target = h.absoluteVec(new Vec3(11, 4, 6));
        h.runAtTickTime(5, () -> a.getNavigation().moveTo(target.x, target.y, target.z, 1));
        h.runAtTickTime(250, () -> {
            trace(a, "obstacle", 250, target);
            h.assertTrue(a.isAlive() && Math.abs(a.getY() - target.y) <= .5
                    && a.position().subtract(target).horizontalDistance() <= .8, "Open obstacle bypass failed");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 115)
    public static void blockedPathIsBounded(GameTestHelper h) {
        tank(h, true);
        for (int z = 1; z < 15; z++) for (int y = 1; y < 12; y++)
            h.setBlock(new BlockPos(8, y, z), Blocks.STONE);
        AlligatorEntity a = spawn(h, new Vec3(5, 4, 6), true);
        Vec3 target = h.absoluteVec(new Vec3(11, 4, 6));
        h.runAtTickTime(5, () -> a.getNavigation().moveTo(target.x, target.y, target.z, 1));
        h.runAtTickTime(105, () -> {
            var path = a.getNavigation().getPath();
            h.assertTrue(a.getNavigation().isDone() || path == null || !path.canReach(), "Blocked route remains deceptively reachable");
            h.assertTrue(Double.isFinite(a.getY()) && a.getX() < target.x, "Blocked route crossed solid wall");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 210)
    public static void verticalSteeringWithRandomLook(GameTestHelper h) {
        tank(h, true);
        AlligatorEntity a = spawn(h, new Vec3(6, 7, 6), true);
        a.goalSelector.addGoal(9, new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(a));
        Vec3 target = h.absoluteVec(new Vec3(6, 3, 6));
        h.runAtTickTime(5, () -> a.getNavigation().moveTo(target.x, target.y, target.z, 1));
        h.runAtTickTime(200, () -> {
            h.assertTrue(Math.abs(a.getY() - target.y) < .5 && a.position().subtract(target).horizontalDistance() < .8,
                    "Random look cancelled signed vertical steering");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 260)
    public static void groundStairs(GameTestHelper h) {
        tank(h, false);
        for (int z = 1; z < 15; z++) {
            h.setBlock(new BlockPos(7, 1, z), Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.EAST));
            for (int x = 8; x < 15; x++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            h.setBlock(new BlockPos(9, 2, z), Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.EAST));
            for (int x = 10; x < 15; x++) h.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
        }
        AlligatorEntity a = spawn(h, new Vec3(3, 1, 6), true);
        Vec3 target = h.absoluteVec(new Vec3(12, 3, 6));
        h.runAtTickTime(5, () -> a.getNavigation().moveTo(target.x - 1, target.y, target.z - 1, 0, 1));
        h.runAtTickTime(250, () -> {
            h.assertTrue(a.position().distanceTo(target) <= 1, "Supported stair traversal failed");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 65)
    public static void bubbleColumnsRetainEngineImpulse(GameTestHelper h) {
        tank(h, true);
        h.setBlock(new BlockPos(4, 0, 4), Blocks.SOUL_SAND);
        h.setBlock(new BlockPos(10, 0, 10), Blocks.MAGMA_BLOCK);
        for (int y = 1; y <= 8; y++) {
            h.setBlock(new BlockPos(4, y, 4), Blocks.BUBBLE_COLUMN.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.BubbleColumnBlock.DRAG_DOWN, false));
            h.setBlock(new BlockPos(10, y, 10), Blocks.BUBBLE_COLUMN.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.BubbleColumnBlock.DRAG_DOWN, true));
        }
        AlligatorEntity up = spawn(h, new Vec3(4.5, 3, 4.5), true);
        AlligatorEntity down = spawn(h, new Vec3(10.5, 6, 10.5), true);
        double upY = up.getY(), downY = down.getY();
        h.runAtTickTime(30, () -> {
            h.assertTrue(up.getY() > upY + .5, "Upward bubble impulse was lost");
            h.assertTrue(down.getY() < downY - .5, "Downward bubble impulse was lost");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 135)
    public static void waterBreathingEffectKeepsEngineAirSemantics(GameTestHelper h) {
        tank(h, true);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) h.setBlock(new BlockPos(x, 9, z), Blocks.STONE);
        AlligatorEntity a = spawn(h, new Vec3(6, 3, 6), false);
        a.setAirSupply(40);
        a.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WATER_BREATHING, 160));
        h.runAtTickTime(120, () -> {
            h.assertTrue(a.getAirSupply() >= 40 && a.getHealth() == a.getMaxHealth(), "Native water-breathing effect was overridden");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 65)
    public static void flowingWaterRetainsCurrent(GameTestHelper h) {
        tank(h, false);
        for (int x = 1; x < 15; x++) for (int z = 3; z <= 11; z++) {
            for (int y = 1; y <= 3; y++) {
                if (z == 3 || z == 11) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                else if (y < 3 || x <= 4) h.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                else if (x <= 11) h.setBlock(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, x - 4));
            }
        }
        AlligatorEntity a = spawn(h, new Vec3(6, 3, 7), true);
        double startX = a.getX();
        h.runAtTickTime(30, () -> {
            h.assertTrue(a.getX() > startX + .15, "Engine water current was erased");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 115)
    public static void narrowGapIsNotClaimedReachable(GameTestHelper h) {
        tank(h, true);
        for (int z = 1; z < 15; z++) if (z != 7)
            for (int y = 1; y < 12; y++) h.setBlock(new BlockPos(8, y, z), Blocks.STONE);
        AlligatorEntity a = spawn(h, new Vec3(5, 4, 6), true);
        Vec3 target = h.absoluteVec(new Vec3(11, 4, 6));
        h.runAtTickTime(5, () -> a.getNavigation().moveTo(target.x, target.y, target.z, 1));
        h.runAtTickTime(105, () -> {
            var path = a.getNavigation().getPath();
            h.assertTrue(path == null || !path.canReach(), "A 1.6-wide body path entered a one-block gap");
            h.assertTrue(a.getX() < h.absolutePos(new BlockPos(8, 4, 6)).getX(), "Body crossed blocked narrow gap");
            h.succeed();
        });
    }
}
