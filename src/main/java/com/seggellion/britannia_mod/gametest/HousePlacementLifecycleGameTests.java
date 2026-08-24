package com.seggellion.britannia_mod.gametest;

import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.structure.HousePlacementClearance;
import com.seggellion.britannia_mod.structure.HouseSignBlock;
import com.seggellion.britannia_mod.structure.HouseStyle;
import com.seggellion.britannia_mod.structure.StructurePlacer;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;
import com.seggellion.britannia_mod.util.HouseUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Placing a real house, end to end, at every rotation.
 *
 * <h2>What only a real placement can show</h2>
 *
 * <p>Everything else about housing is tested against registered regions and hand-set blocks, which
 * is right for rules about ownership and tags. But three of the Patch 18 defects were about the
 * placement act itself, and none of them is visible without running it:
 *
 * <ul>
 *   <li>the controller was sited by arithmetic that disagreed with the authored sign, so three
 *       houses answered "Could not find the house controller";</li>
 *   <li>rotation moves the sign, and a controller derived with a hard-coded y could never follow
 *       it;</li>
 *   <li>clearance demanded a skirt of untouched flat ground that terrain rarely provides.</li>
 * </ul>
 *
 * <p>So these place the house, at all four rotations, and then ask the questions a player would:
 * is there a sign, is the controller under it, does the door's lookup find it, and is the region
 * registered facing the way the ghost was.
 *
 * <p>Each test owns its own patch of ground, lays it, and puts it back afterwards — the test level
 * is shared with every other concurrently batched GameTest, and a house left standing in it would
 * be somebody else's mysterious failure.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class HousePlacementLifecycleGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** How far from the GameTest grid the building sites sit. Far enough to touch nothing. */
    private static final int LANE_OFFSET = 4096;

    /** How far apart the lanes are. Comfortably wider than the largest pad, which is the castle's. */
    private static final int LANE_SPACING = 256;

    private HousePlacementLifecycleGameTests() {
    }

    // ------------------------------------------------------------------
    // Rotation, controller, and the pairing between them
    // ------------------------------------------------------------------

    /**
     * All four rotations place, and each one's controller is still under its own sign.
     *
     * <p>Run in one test rather than four so a single patch of ground is laid, used and cleaned up,
     * instead of four houses appearing in a level other tests are also using.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void everyrotationplacesahousewhosecontrollersitsunderitssign(GameTestHelper helper) {
        Ground ground = Ground.lay(helper, 0);
        try {
            for (int rotationDeg : new int[] { 0, 90, 180, 270 }) {
                ground.prepareFor(HouseStyle.SMALL_BRICK);
                StructureRecord record = ground.place(helper, HouseStyle.SMALL_BRICK, rotationDeg);

                if (record.getRotationDeg() != rotationDeg) {
                    throw new GameTestAssertException("the house was registered at "
                            + record.getRotationDeg() + " after being placed at " + rotationDeg
                            + "; the rotation the ghost showed is not the one that was built");
                }

                List<BlockPos> signs = ground.signsIn(helper, record);
                if (signs.size() != 1) {
                    throw new GameTestAssertException(
                            "a placed house should have exactly one sign, found " + signs.size()
                                    + " at rotation " + rotationDeg);
                }

                BlockPos controller = signs.get(0).below();
                if (!(helper.getLevel().getBlockEntity(controller) instanceof HouseLotBlockEntity lot)) {
                    throw new GameTestAssertException(
                            "no house controller beneath the sign at " + signs.get(0) + " (rotation "
                                    + rotationDeg + "). This is exactly the "
                                    + "\"Could not find the house controller\" defect.");
                }
                if (!record.getHouseUuid().equals(lot.getHouseUuid())) {
                    throw new GameTestAssertException(
                            "the controller under the sign belongs to a different house");
                }

                // And the lookup every door and management action actually uses.
                HouseLotBlockEntity resolved = HouseUtil.findLot(helper.getLevel(), signs.get(0));
                if (resolved == null || !record.getHouseUuid().equals(resolved.getHouseUuid())) {
                    throw new GameTestAssertException(
                            "HouseUtil.findLot did not resolve the house from its own sign at rotation "
                                    + rotationDeg);
                }
            }
        } finally {
            ground.close(helper);
        }
        helper.succeed();
    }

    /**
     * Every registered house style places and finds its controller.
     *
     * <p>The three that failed in production — villa, patio and keep — differ from the six small
     * houses precisely in where their sign is, so checking one style would have proved nothing.
     * The castle is included because it is the largest thing the clearance rule has to accept.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void everyhousestyleplacesandfindsitscontroller(GameTestHelper helper) {
        Ground ground = Ground.lay(helper, 1);
        try {
            for (HouseStyle style : HouseStyle.values()) {
                ground.prepareFor(style);
                StructureRecord record = ground.place(helper, style, 0);

                List<BlockPos> signs = ground.signsIn(helper, record);
                if (signs.size() != 1) {
                    throw new GameTestAssertException(
                            style + " placed " + signs.size() + " signs; exactly one is the contract");
                }
                if (!(helper.getLevel().getBlockEntity(signs.get(0).below())
                        instanceof HouseLotBlockEntity lot)
                        || !record.getHouseUuid().equals(lot.getHouseUuid())) {
                    throw new GameTestAssertException(
                            style + " has no controller beneath its sign at " + signs.get(0));
                }
            }
        } finally {
            ground.close(helper);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Clearance
    // ------------------------------------------------------------------

    /**
     * Decoration and terrain immediately outside the footprint do not refuse a placement.
     *
     * <p>This is the whole point of removing the skirt. A fence post, a flower and a raised block
     * one cell beyond the wall used to be three separate reasons a house could not be built, with
     * nothing telling the player which.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void obstaclesjustoutsidethefootprintdonotrefuseaplacement(GameTestHelper helper) {
        Ground ground = Ground.lay(helper, 2);
        try {
            ground.prepareFor(HouseStyle.SMALL_BRICK);
            // Where the house will stand, so the ring can be placed exactly one block outside it.
            StructureRecord probe = ground.place(helper, HouseStyle.SMALL_BRICK, 0);
            AABB footprint = probe.getStructureBox();
            ground.prepareFor(HouseStyle.SMALL_BRICK);

            List<BlockPos> ring = new ArrayList<>();
            int minX = (int) footprint.minX - 1;
            int maxX = (int) footprint.maxX;
            int minZ = (int) footprint.minZ - 1;
            int maxZ = (int) footprint.maxZ;
            int y = (int) footprint.minY;
            for (int x = minX; x <= maxX; x++) {
                ring.add(new BlockPos(x, y, minZ));
                ring.add(new BlockPos(x, y, maxZ));
            }
            for (int z = minZ; z <= maxZ; z++) {
                ring.add(new BlockPos(minX, y, z));
                ring.add(new BlockPos(maxX, y, z));
            }
            for (BlockPos post : ring) {
                helper.getLevel().setBlockAndUpdate(post, Blocks.OAK_FENCE.defaultBlockState());
            }
            // And a step of slope beneath the ring, which the old ground rule also refused.
            helper.getLevel().setBlockAndUpdate(new BlockPos(minX, y - 1, minZ),
                    Blocks.STONE.defaultBlockState());

            StructureRecord placed = ground.tryPlace(helper, HouseStyle.SMALL_BRICK, 0);
            if (placed == null) {
                throw new GameTestAssertException(
                        "a ring of fence one block outside the footprint refused the placement; the "
                                + "exterior skirt is back");
            }
            ground.forget(placed);
        } finally {
            ground.close(helper);
        }
        helper.succeed();
    }

    /** An obstruction inside the footprint volume still refuses, which is the half that must hold. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void anobstructioninsidethefootprintstillrefuses(GameTestHelper helper) {
        Ground ground = Ground.lay(helper, 3);
        try {
            ground.prepareFor(HouseStyle.SMALL_BRICK);
            StructureRecord probe = ground.place(helper, HouseStyle.SMALL_BRICK, 0);
            AABB footprint = probe.getStructureBox();
            ground.prepareFor(HouseStyle.SMALL_BRICK);

            BlockPos inside = new BlockPos(
                    (int) footprint.minX + 4, (int) footprint.minY + 2, (int) footprint.minZ + 4);
            helper.getLevel().setBlockAndUpdate(inside, Blocks.OBSIDIAN.defaultBlockState());

            if (ground.tryPlace(helper, HouseStyle.SMALL_BRICK, 0) != null) {
                throw new GameTestAssertException(
                        "a house was placed straight through an obsidian block standing in its own volume");
            }
        } finally {
            ground.close(helper);
        }
        helper.succeed();
    }

    /** Bad ground under the footprint still refuses: the foundation rule is not what was relaxed. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void anonfoundationgroundcellinsidethefootprintstillrefuses(GameTestHelper helper) {
        Ground ground = Ground.lay(helper, 4);
        try {
            ground.prepareFor(HouseStyle.SMALL_BRICK);
            StructureRecord probe = ground.place(helper, HouseStyle.SMALL_BRICK, 0);
            AABB footprint = probe.getStructureBox();
            ground.prepareFor(HouseStyle.SMALL_BRICK);

            helper.getLevel().setBlockAndUpdate(
                    new BlockPos((int) footprint.minX + 4, (int) footprint.minY - 1,
                            (int) footprint.minZ + 4),
                    Blocks.OBSIDIAN.defaultBlockState());

            if (ground.tryPlace(helper, HouseStyle.SMALL_BRICK, 0) != null) {
                throw new GameTestAssertException(
                        "a house was founded on obsidian; the ground rule is meant to be grass or sand");
            }
        } finally {
            ground.close(helper);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Overlap
    // ------------------------------------------------------------------

    /**
     * A second house on the same ground is refused, and a neighbour beside it is not.
     *
     * <p>This check did not exist before: nothing ever asked the region registry, and two houses
     * could genuinely be placed into each other. The skirt only ever prevented it by accident, by
     * having a solid wall in the way.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void asecondhouseoverlappingthefirstisrefused(GameTestHelper helper) {
        Ground ground = Ground.lay(helper, 5);
        try {
            ground.prepareFor(HouseStyle.SMALL_BRICK);
            StructureRecord first = ground.place(helper, HouseStyle.SMALL_BRICK, 0);

            // The same aim again: the second house would occupy the first one's ground exactly.
            if (ground.tryPlace(helper, HouseStyle.SMALL_BRICK, 0) != null) {
                throw new GameTestAssertException(
                        "a second house was placed overlapping the first");
            }

            // And the registry is what refused it, not the blocks: clear the building away, leave
            // the region registered, and the answer must not change.
            ground.clearVolume(helper, first.getStructureBox());
            if (ground.tryPlace(helper, HouseStyle.SMALL_BRICK, 0) != null) {
                throw new GameTestAssertException(
                        "with the first house's blocks removed but its region still registered, a "
                                + "second house was placed on top of it. Overlap must be a claim, "
                                + "not a collision.");
            }
        } finally {
            ground.close(helper);
        }
        helper.succeed();
    }

    /** Two boxes that merely touch do not overlap, so a town can still be built wall to wall. */
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

    /* ------------------------------------------------------------------ */
    /*  Fixture                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * A patch of buildable ground this test owns, and the bookkeeping to give it back.
     *
     * <p>Everything placed goes through {@link StructurePlacer} rather than being written directly,
     * because the placer is the thing under test — its clearance rule, its sign scan and its
     * controller siting are all what these tests are asking about.
     */
    private static final class Ground implements AutoCloseable {
        private final ServerLevel level;
        private final BlockPos centre;
        private final List<StructureRecord> placed = new ArrayList<>();

        private Ground(ServerLevel level, BlockPos centre) {
            this.level = level;
            this.centre = centre;
        }

        /**
         * A building site this test has to itself.
         *
         * <p>Emphatically not next to the test's own structure. A house needs tens of blocks of
         * prepared ground in every direction, and GameTest structures are laid out a few blocks
         * apart in a shared level — so a pad that size, written where the test stands, lands on top
         * of whatever else is running. That is not a theory: it turned unrelated banking and
         * deposit tests red, differently on each run, which is exactly what cross-test scribbling
         * looks like from the outside.
         *
         * <p>So each test gets a lane {@value #LANE_OFFSET} blocks away, {@value #LANE_SPACING}
         * apart from its neighbours, in ground nothing else uses. The lane is still derived from
         * this test's own position, so parallel runs of the same test do not share one either.
         */
        static Ground lay(GameTestHelper helper, int lane) {
            BlockPos site = helper.absolutePos(new BlockPos(3, 1, 3))
                    .offset(0, 0, LANE_OFFSET + lane * LANE_SPACING);
            return new Ground(helper.getLevel(), site);
        }

        /**
         * Empty ground, ready for one house of this style, with nothing owed from the last one.
         *
         * <p>Three things have to be true and each was got wrong once while writing these:
         *
         * <ul>
         *   <li>the grass sits at the centre's own y, not one below it. The placer aims a house one
         *       block <em>above</em> the builder's feet — {@code adjustedPos} is
         *       {@code doorTarget.above()} — so a builder standing at y lays a house based at y+1
         *       whose foundation rule reads y;</li>
         *   <li>regions from earlier placements are unregistered. Clearing the blocks is not
         *       enough: overlap is a claim on the registry, not a collision, so the second house in
         *       a test was refused by the very check the suite is here to confirm works;</li>
         *   <li>the pad is sized to the style. A square big enough for the castle, laid ten times,
         *       is a million block writes and a timeout.</li>
         * </ul>
         */
        void prepareFor(HouseStyle style) {
            for (StructureRecord record : placed) {
                StructureRegionManager.unregisterStructure(record);
            }
            placed.clear();

            // Symmetric, because rotation decides which way the house is thrown from the aim point:
            // at 0 it extends +z, at 180 it extends -z. A pad that only reached forwards left the
            // 180 case standing on nothing.
            int reach = Math.max(style.getWidth(), style.getDepth()) + 6;
            int height = style.getHeight() + 2;
            for (int x = -reach; x <= reach; x++) {
                for (int z = -reach; z <= reach; z++) {
                    level.setBlock(centre.offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    for (int y = 1; y <= height; y++) {
                        level.setBlock(centre.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }

        /** Places, and fails the test if the placer refused. */
        StructureRecord place(GameTestHelper helper, HouseStyle style, int rotationDeg) {
            StructureRecord record = tryPlace(helper, style, rotationDeg);
            if (record == null) {
                throw new GameTestAssertException(
                        style + " could not be placed on flat empty grass at rotation " + rotationDeg);
            }
            return record;
        }

        /** Places, and answers null if the placer refused. */
        StructureRecord tryPlace(GameTestHelper helper, HouseStyle style, int rotationDeg) {
            ServerPlayer builder = builder(helper);
            java.util.Set<UUID> before = registeredUuids();

            if (!StructurePlacer.placeStructure(
                    helper.getLevel(), centre, rotationDeg, style, builder)) {
                return null;
            }
            for (StructureRecord candidate : allRegistered()) {
                if (!before.contains(candidate.getHouseUuid())) {
                    placed.add(candidate);
                    return candidate;
                }
            }
            throw new GameTestAssertException(
                    style + " reported a successful placement but registered no region");
        }

        /** The signs standing inside a placed house's own box. */
        List<BlockPos> signsIn(GameTestHelper helper, StructureRecord record) {
            List<BlockPos> signs = new ArrayList<>();
            AABB box = record.getStructureBox();
            for (int x = (int) box.minX; x < (int) box.maxX; x++) {
                for (int y = (int) box.minY; y < (int) box.maxY; y++) {
                    for (int z = (int) box.minZ; z < (int) box.maxZ; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = helper.getLevel().getBlockState(pos);
                        if (state.getBlock() instanceof HouseSignBlock) {
                            signs.add(pos);
                        }
                    }
                }
            }
            return signs;
        }

        void clearVolume(GameTestHelper helper, AABB box) {
            for (int x = (int) box.minX; x < (int) box.maxX; x++) {
                for (int y = (int) box.minY; y < (int) box.maxY; y++) {
                    for (int z = (int) box.minZ; z < (int) box.maxZ; z++) {
                        helper.getLevel().setBlock(new BlockPos(x, y, z),
                                Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }

        /** Drops a record's registration without leaving it to the close-down sweep. */
        void forget(StructureRecord record) {
            StructureRegionManager.unregisterStructure(record);
            placed.remove(record);
        }

        void close(GameTestHelper helper) {
            for (StructureRecord record : placed) {
                StructureRegionManager.unregisterStructure(record);
            }
            placed.clear();
        }

        @Override
        public void close() {
            for (StructureRecord record : placed) {
                StructureRegionManager.unregisterStructure(record);
            }
            placed.clear();
        }

        private ServerPlayer builder(GameTestHelper helper) {
            ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "house-placer"));
            player.setPos(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5);
            // Facing south, so getDirection() is deterministic: the placer aims the house from it.
            player.setYRot(0.0F);
            player.setXRot(0.0F);
            player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            player.getInventory().clearContent();
            return player;
        }

        private static java.util.Set<UUID> registeredUuids() {
            java.util.Set<UUID> uuids = new java.util.HashSet<>();
            for (StructureRecord record : allRegistered()) {
                uuids.add(record.getHouseUuid());
            }
            return uuids;
        }

        private static List<StructureRecord> allRegistered() {
            List<StructureRecord> records = new ArrayList<>();
            for (List<StructureRecord> inChunk : StructureRegionManager.getChunkStructureMap().values()) {
                for (StructureRecord record : inChunk) {
                    if (!records.contains(record)) {
                        records.add(record);
                    }
                }
            }
            return records;
        }
    }
}
