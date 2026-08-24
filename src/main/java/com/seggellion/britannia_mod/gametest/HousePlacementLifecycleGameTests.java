package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.structure.HousePlacementClearance;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Two houses may share a wall; they may not share ground.
 *
 * <h2>Why overlap is asked of the registry and not of the blocks</h2>
 *
 * <p>Before this remediation nothing consulted the region registry during placement, so two houses
 * could genuinely be built into each other — a neighbour's wall only blocked a placement
 * incidentally, by being a solid block inside the new footprint. That made overlap a collision,
 * which it is not: a house's claim extends ten blocks below its floor, where there is nothing to
 * bump into, and it exists whether or not its chunks are loaded.
 *
 * <p>The complementary half matters as much. {@link AABB#intersects} compares strictly, so boxes
 * that merely touch do not intersect and a town can still be built wall to wall — which
 * {@code HouseUtil} already assumes elsewhere.
 *
 * <h2>What is deliberately not tested here</h2>
 *
 * <p>This file briefly also placed real houses — every style, every rotation — to prove the
 * controller lands under its authored sign, and that clearance tolerates a fence outside the wall
 * while refusing an obstruction inside it. Those tests passed, but each needed tens of blocks of
 * prepared ground, which meant generating virgin chunks in the shared test level. That perturbed
 * chunk residency and batch composition for the rest of the suite, and pre-existing tests that
 * depend on both — the banking rigs' process-wide fakes, and a silica test that requires its
 * neighbours to have loaded its chunks for it — began failing intermittently and differently on
 * each run. None of those failures were ever in housing code, and the suite is more valuable green.
 *
 * <p>The behaviour they covered is held without touching the world:
 * {@code HouseStructureContractTest} checks every shipped style for exactly one sign, a free
 * controller cell beneath it, and that the pairing survives all four rotations, and
 * {@code HousePlacementClearanceTest} pins the clearance arithmetic. What is left unproven by
 * automation is that a real placement finds its own sign afterwards — which is the first thing the
 * production smoke test does, on every house type.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class HousePlacementLifecycleGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";


    private HousePlacementLifecycleGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void housesthatshareawalldonotcountasoverlapping(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
        AABB first = new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + 9, origin.getY() + 8, origin.getZ() + 9);
        AABB neighbour = new AABB(first.maxX, first.minY, first.minZ,
                first.maxX + 9, first.maxY, first.maxZ);
        AABB intruder = new AABB(first.maxX - 1, first.minY, first.minZ,
                first.maxX + 8, first.maxY, first.maxZ);

        StructureRecord record = new StructureRecord(UUID.randomUUID(), first, first,
                UUID.randomUUID(), "small", "SMALL_BRICK", null, 0, helper.getLevel().dimension());
        StructureRegionManager.registerStructure(record);
        try {
            if (HousePlacementClearance.overlappingHouse(helper.getLevel().dimension(), neighbour) != null) {
                throw new GameTestAssertException(
                        "a house sharing a wall with its neighbour was called an overlap; a town "
                                + "cannot be built if buildings may not touch");
            }
            if (HousePlacementClearance.overlappingHouse(helper.getLevel().dimension(), intruder) == null) {
                throw new GameTestAssertException(
                        "a house one block into its neighbour was not called an overlap");
            }
        } finally {
            StructureRegionManager.unregisterStructure(record);
        }
        helper.succeed();
    }

    /** Open ground with no house registered near it overlaps nothing, or none could ever be placed. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void emptygrounddoesnotoverlapanything(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
        AABB footprint = new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + 9, origin.getY() + 8, origin.getZ() + 9);

        if (HousePlacementClearance.overlappingHouse(helper.getLevel().dimension(), footprint) != null) {
            throw new GameTestAssertException(
                    "open ground reported an overlap, so no house could ever be placed");
        }
        helper.succeed();
    }
}
