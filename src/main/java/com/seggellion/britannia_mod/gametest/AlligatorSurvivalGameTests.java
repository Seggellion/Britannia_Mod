package com.seggellion.britannia_mod.gametest;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.entity.AlligatorEntity;
import com.seggellion.britannia_mod.entity.CitizenEntity;
import com.seggellion.britannia_mod.entity.ai.AlligatorWaterGoals;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.Collection;

@GameTestHolder("britannia_alligator_idle")
@PrefixGameTestTemplate(false)
public final class AlligatorSurvivalGameTests {
    private static final String TEMPLATE = "alligator_tank";

    @GameTestGenerator
    public static Collection<TestFunction> idleSeeds() {
        var tests = new ArrayList<TestFunction>();
        for (int seed = 1800; seed < 1820; seed++) {
            int fixedSeed = seed;
            tests.add(new TestFunction("alligator-idle", "alligator.idle-seed-" + seed,
                    "britannia_alligator_idle:" + TEMPLATE, 1220, 0, true, h -> idle(h, fixedSeed)));
        }
        return tests;
    }

    private static void idle(GameTestHelper h, int seed) {
        AlligatorMovementGameTests.tank(h, true);
        AlligatorEntity a = AlligatorMovementGameTests.spawn(h, new Vec3(6, 8.3, 6), false);
        a.getRandom().setSeed(seed);
        double surfaceFeet = h.absoluteVec(new Vec3(6, 8.3, 6)).y;
        int[] counts = new int[6]; // surface, submerged, completed dives, stable breathing, selected dives, selected routes
        boolean[] dived = {false};
        Vec3[] selected = {null};
        h.onEachTick(() -> {
            if (h.getTick() <= 0 || h.getTick() > 1200) return;
            h.assertTrue(a.isAlive() && a.getHealth() == a.getMaxHealth(), "Idle drowned or lost health, seed=" + seed);
            if (a.isUnderWater()) { counts[1]++; counts[3] = 0; }
            else { counts[0]++; counts[3]++; }
            if (a.getY() < surfaceFeet - 1.5) dived[0] = true;
            if (dived[0] && counts[3] >= 20 && a.getAirSupply() > 100) { counts[2]++; dived[0] = false; }
            for (var goal : a.goalSelector.getAvailableGoals()) {
                if (goal.getGoal() instanceof AlligatorWaterGoals.IdleWater idle) {
                    Vec3 position = idle.plannedDestination();
                    if (position != null && !position.equals(selected[0])) {
                        selected[0] = position;
                        counts[5]++;
                        if (idle.plannedDive()) counts[4]++;
                        LogUtils.getLogger().info("ALLIGATOR idle-selection seed={} tick={} dive={} localDestination={}",
                                seed, h.getTick(), idle.plannedDive(), h.relativeVec(position));
                    }
                }
            }
        });
        h.runAtTickTime(1201, () -> {
            LogUtils.getLogger().info("ALLIGATOR idle-result seed={} ticks=1200 surface={} submerged={} completedDives={} selectedDives={} selectedRoutes={} air={} localPosition={}",
                    seed, counts[0], counts[1], counts[2], counts[4], counts[5], a.getAirSupply(), h.relativeVec(a.position()));
            h.assertTrue(counts[2] >= 1, "No physically completed safe dive, seed=" + seed);
            h.assertTrue(counts[0] > counts[1], "Idle surface preference failed, seed=" + seed);
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "alligator-survival", timeoutTicks = 210)
    public static void lowAirIdleRecovers(GameTestHelper h) { recover(h, false); }

    @GameTest(template = TEMPLATE, batch = "alligator-survival", timeoutTicks = 210)
    public static void lowAirPursuitRecoversAndKeepsTarget(GameTestHelper h) { recover(h, true); }

    private static void recover(GameTestHelper h, boolean pursuit) {
        AlligatorMovementGameTests.tank(h, true);
        AlligatorEntity a = AlligatorMovementGameTests.spawn(h, new Vec3(6, 3, 6), false);
        a.setAirSupply(120);
        CitizenEntity target = pursuit ? citizen(h, new Vec3(9, 3, 9)) : null;
        if (target != null) a.setTarget(target);
        int[] breathing = {0};
        h.onEachTick(() -> {
            h.assertTrue(a.isAlive() && a.getHealth() == a.getMaxHealth(), "Reachable recovery drowned");
            if (pursuit) h.assertTrue(a.getTarget() == target, "Air recovery cleared the ordinary target");
            if (!a.isUnderWater() && a.getAirSupply() > 120) breathing[0]++; else breathing[0] = 0;
            if (breathing[0] >= 20) {
                LogUtils.getLogger().info("ALLIGATOR recovery pursuit={} tick={} air={} localPosition={}",
                        pursuit, h.getTick(), a.getAirSupply(), h.relativeVec(a.position()));
                h.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = "alligator-survival", timeoutTicks = 150)
    public static void sealedRoofStillDrowns(GameTestHelper h) {
        AlligatorMovementGameTests.tank(h, true);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) h.setBlock(new BlockPos(x, 9, z), Blocks.STONE);
        AlligatorEntity a = AlligatorMovementGameTests.spawn(h, new Vec3(6, 3, 6), false);
        a.setAirSupply(40);
        h.runAtTickTime(120, () -> {
            h.assertTrue(a.getHealth() < a.getMaxHealth(), "Sealed roof granted water immunity or infinite air");
            h.assertTrue(a.isUnderWater() && a.getY() < h.absolutePos(new BlockPos(6, 9, 6)).getY(), "Recovery crossed sealed roof");
            LogUtils.getLogger().info("ALLIGATOR sealed-roof air={} health={} localPosition={}", a.getAirSupply(), a.getHealth(), h.relativeVec(a.position()));
            h.succeed();
        });
    }

    private static CitizenEntity citizen(GameTestHelper h, Vec3 local) {
        CitizenEntity c = h.spawn(EntityRegistry.TOWNSPERSON.get(), local);
        c.setNoAi(true); // The target is controlled; Alligator keeps its real AI.
        c.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
        c.setHealth(1024);
        return c;
    }

    @GameTest(template = TEMPLATE, batch = "alligator-survival", timeoutTicks = 265)
    public static void citizenPursuitChangesDepthAndKeepsMeleeCooldown(GameTestHelper h) {
        AlligatorMovementGameTests.tank(h, true);
        AlligatorEntity a = AlligatorMovementGameTests.spawn(h, new Vec3(4, 6, 4), false);
        CitizenEntity c = citizen(h, new Vec3(9, 3, 9));
        int[] hits = {0, -100};
        float[] health = {c.getHealth()};
        h.runAtTickTime(60, () -> { Vec3 p = h.absoluteVec(new Vec3(9, 5, 9)); c.setPos(p); c.setDeltaMovement(Vec3.ZERO); });
        h.onEachTick(() -> {
            c.setAirSupply(300); // Target longevity, never Alligator survival.
            if (c.getHealth() < health[0]) {
                h.assertTrue(h.getTick() - hits[1] >= 20, "Generic melee cooldown shortened");
                hits[0]++;
                hits[1] = (int)h.getTick();
                health[0] = c.getHealth();
            }
        });
        h.runAtTickTime(250, () -> {
            h.assertTrue(hits[0] >= 2, "Registered Citizen depth pursuit did not reach melee");
            h.assertTrue(a.getTarget() == c, "Citizen targeting contract changed");
            LogUtils.getLogger().info("ALLIGATOR combat hits={} air={} localPosition={}", hits[0], a.getAirSupply(), h.relativeVec(a.position()));
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "alligator-survival", timeoutTicks = 100)
    public static void playerAcquisitionAndRetaliationStayIntact(GameTestHelper h) {
        AlligatorMovementGameTests.tank(h, false);
        AlligatorEntity a = AlligatorMovementGameTests.spawn(h, new Vec3(4, 1, 4), false);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "alligator-test-player"), false);
        var player = new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                cookie.gameProfile(), cookie.clientInformation()) {
            @Override public boolean isCreative() { return false; }
            @Override public boolean isSpectator() { return false; }
        };
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setPos(h.absoluteVec(new Vec3(9, 1, 9)));
        h.runAtTickTime(50, () -> {
            LogUtils.getLogger().info("ALLIGATOR player-acquisition target={} visible={} canAttack={} invulnerable={} listed={}",
                    a.getTarget(), a.getSensing().hasLineOfSight(player), a.canAttack(player), player.isInvulnerable(), h.getLevel().players().contains(player));
            h.assertTrue(a.getTarget() == player, "Survival Player was not acquired");
            CitizenEntity c = citizen(h, new Vec3(8, 1, 8));
            a.hurt(h.getLevel().damageSources().mobAttack(c), 1);
            h.runAtTickTime(80, () -> {
                h.assertTrue(a.getTarget() == c, "HurtByTarget retaliation changed");
                player.discard();
                h.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = "alligator-survival", timeoutTicks = 625)
    public static void shallowWaterKeepsSupportedIdleRoutes(GameTestHelper h) {
        AlligatorMovementGameTests.tank(h, true);
        for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++)
            for (int y = 2; y <= 8; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        AlligatorEntity a = AlligatorMovementGameTests.spawn(h, new Vec3(6, 1, 6), false);
        Vec3 start = a.position();
        double[] farthest = {0};
        h.onEachTick(() -> {
            if (h.getTick() > 600) return;
            farthest[0] = Math.max(farthest[0], a.position().subtract(start).horizontalDistance());
            for (var goal : a.goalSelector.getAvailableGoals()) {
                if (goal.getGoal() instanceof AlligatorWaterGoals.IdleWater idle)
                    h.assertTrue(!idle.plannedDive(), "Shallow pond selected an impossible dive");
            }
        });
        h.runAtTickTime(601, () -> {
            h.assertTrue(farthest[0] > 2 && a.isAlive(), "Shallow idle did not use supported routes");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "alligator-survival", timeoutTicks = 625)
    public static void reducedMovementSpeedRejectsUnsafeOptionalDives(GameTestHelper h) {
        AlligatorMovementGameTests.tank(h, true);
        AlligatorEntity a = AlligatorMovementGameTests.spawn(h, new Vec3(6, 8.3, 6), false);
        a.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.03);
        a.getRandom().setSeed(1800);
        a.restrictTo(h.absolutePos(new BlockPos(7, 8, 7)), 6);
        Vec3 start = a.position();
        double[] farthest = {0};
        Vec3[] previous = {start}, selected = {null};
        double[] traveled = {0};
        int[] routes = {0};
        h.onEachTick(() -> {
            if (h.getTick() > 600) return;
            traveled[0] += a.position().subtract(previous[0]).horizontalDistance();
            previous[0] = a.position();
            farthest[0] = Math.max(farthest[0], a.position().subtract(start).horizontalDistance());
            if (h.getTick() % 100 == 0) LogUtils.getLogger().info("ALLIGATOR slowed tick={} position={} farthest={} target={} air={} done={} input={},{},{}",
                    h.getTick(), h.relativeVec(a.position()), farthest[0], a.getTarget(), a.getAirSupply(), a.getNavigation().isDone(), a.xxa, a.yya, a.zza);
            h.assertTrue(a.getHealth() == a.getMaxHealth(), "Slowed optional idle drowned");
            for (var goal : a.goalSelector.getAvailableGoals())
                if (goal.getGoal() instanceof AlligatorWaterGoals.IdleWater idle) {
                    h.assertTrue(!idle.plannedDive(), "Slowed entity selected an unaffordable dive");
                    Vec3 intent = idle.plannedDestination();
                    if (intent != null && !intent.equals(selected[0])) { routes[0]++; selected[0] = intent; }
                }
        });
        h.runAtTickTime(601, () -> {
            LogUtils.getLogger().info("ALLIGATOR slowed-result traveled={} farthest={} selectedRoutes={}", traveled[0], farthest[0], routes[0]);
            h.assertTrue(traveled[0] > 1 && routes[0] >= 4, "Unaffordable fourth-route dive starved supported surface movement");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "alligator-survival", timeoutTicks = 225)
    public static void groundedWadingOffersOrdinaryStroll(GameTestHelper h) {
        AlligatorMovementGameTests.tank(h, false);
        var shallow = Blocks.WATER.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 6);
        // Maintain a controlled shallow flowing volume; no Alligator inputs/velocity are rewritten.
        h.onEachTick(() -> {
            if (h.getTick() > 200) return;
            for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++) h.setBlock(new BlockPos(x, 1, z), shallow);
        });
        AlligatorEntity a = AlligatorMovementGameTests.spawn(h, new Vec3(6, 1, 6), false);
        a.setOnGround(true);
        a.restrictTo(h.absolutePos(new BlockPos(7, 1, 7)), 5);
        boolean[] offered = {false};
        Vec3 start = a.position();
        double[] farthest = {0};
        h.onEachTick(() -> {
            if (h.getTick() > 200) return;
            farthest[0] = Math.max(farthest[0], a.position().subtract(start).horizontalDistance());
            if (a.isInWater() && a.onGround() && !a.usesWaterMovement()) {
                for (var wrapped : a.goalSelector.getAvailableGoals()) {
                    if (wrapped.getGoal() instanceof net.minecraft.world.entity.ai.goal.RandomStrollGoal stroll) {
                        if (!wrapped.isRunning()) stroll.trigger();
                        if (wrapped.isRunning()) offered[0] = true;
                    }
                }
            }
        });
        h.runAtTickTime(201, () -> {
            LogUtils.getLogger().info("ALLIGATOR wading runningObserved={} farthest={} waterHeight={} ground={} waterMovement={}",
                    offered[0], farthest[0], a.getFluidHeight(net.minecraft.tags.FluidTags.WATER), a.onGround(), a.usesWaterMovement());
            h.assertTrue(offered[0] && farthest[0] > 1, "Grounded shallow wading suppressed ordinary stroll");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "alligator-survival", timeoutTicks = 120)
    public static void meleeRequiresSightAndResumesAfterPaneRemoval(GameTestHelper h) {
        AlligatorMovementGameTests.tank(h, true);
        AlligatorEntity a = AlligatorMovementGameTests.spawn(h, new Vec3(6.2, 3, 6.5), false);
        CitizenEntity c = citizen(h, new Vec3(7.9, 3, 6.5));
        a.setTarget(c);
        for (int z = 1; z < 15; z++) for (int y = 1; y < 12; y++)
            h.setBlock(new BlockPos(7, y, z), Blocks.GLASS_PANE);
        h.runAtTickTime(40, () -> {
            h.assertTrue(!a.getSensing().hasLineOfSight(c), "LOS fixture has no barrier");
            h.assertTrue(c.getHealth() == c.getMaxHealth(), "Melee damaged a target through a pane");
            for (int z = 1; z < 15; z++) for (int y = 1; y < 12; y++)
                h.setBlock(new BlockPos(7, y, z), y <= 8 ? Blocks.WATER : Blocks.AIR);
        });
        h.runAtTickTime(100, () -> {
            h.assertTrue(c.getHealth() < c.getMaxHealth(), "Melee did not resume with reach and sight available");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "alligator-survival", timeoutTicks = 170)
    public static void ordinarySightMemoryLosesAnOccludedCitizen(GameTestHelper h) {
        AlligatorMovementGameTests.tank(h, true);
        AlligatorEntity a = AlligatorMovementGameTests.spawn(h, new Vec3(4, 3, 4), false);
        CitizenEntity c = citizen(h, new Vec3(11.5, 3, 11.5));
        h.runAtTickTime(20, () -> {
            h.assertTrue(a.getTarget() == c, "Citizen was not normally acquired before occlusion");
            for (int x = 10; x <= 12; x++) for (int z = 10; z <= 12; z++)
                if (x != 11 || z != 11) for (int y = 1; y < 12; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        });
        h.runAtTickTime(150, () -> {
            h.assertTrue(a.getTarget() == null, "Ordinary sight-memory target loss changed");
            h.succeed();
        });
    }
}
