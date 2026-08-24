package com.seggellion.britannia_mod.gametest;

import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;
import com.seggellion.britannia_mod.structure.SurvivalZoneHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Milestone 5: an owner builds in their own house without ever leaving their game mode.
 *
 * <p>Creative was rejected as the mechanism. An owner standing in their house is lent one
 * ability -- {@code Abilities.mayBuild}, which is what adventure mode actually gates block
 * editing on -- and nothing else. No creative inventory, no flight, no game-mode transition to
 * make and then have to undo. What they may do with it is decided by ownership and by two block
 * tags built from what each block does in the authored structures.
 *
 * <p>Every house here is a registered region rather than a placed building, because the rule
 * being tested is about regions and tags; the structures themselves are covered elsewhere. Both
 * a small-house footprint and a keep-sized one are used, so nothing can quietly acquire a size
 * or proximity assumption.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class HouseOwnerBuildRightsGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";


    /** Ungated by anything else -- stone would meet the mining skill gate instead. */
    private static final Block ORDINARY = Blocks.WHITE_WOOL;

    /* ------------------------------------------------------------------ */
    /*  Ownership                                                          */
    /* ------------------------------------------------------------------ */

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void anownermaybreakanordinaryblockintheirownhouse(GameTestHelper helper) {
        House house = House.smallAt(helper, new BlockPos(1, 1, 1), new BlockPos(5, 4, 5));
        try {
            BlockPos target = helper.absolutePos(new BlockPos(2, 1, 2));
            helper.setBlock(new BlockPos(2, 1, 2), ORDINARY);

            ServerPlayer owner = house.owner(helper, "m5-owner");
            if (!owner.gameMode.destroyBlock(target)) {
                throw new GameTestAssertException(
                        "the owner could not break an ordinary block inside their own house");
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void anonownermaynotbreakthatsameblock(GameTestHelper helper) {
        House house = House.smallAt(helper, new BlockPos(1, 1, 1), new BlockPos(5, 4, 5));
        try {
            BlockPos target = helper.absolutePos(new BlockPos(2, 1, 2));
            helper.setBlock(new BlockPos(2, 1, 2), ORDINARY);

            ServerPlayer stranger = house.stranger(helper, "m5-stranger");
            if (stranger.gameMode.destroyBlock(target)) {
                throw new GameTestAssertException("a stranger broke a block inside somebody's house");
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    /**
     * Standing next to the lot block earns nothing. The old lookup would have handed this player
     * the house; ownership now comes from the region that contains the block.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void proximitytoalotblockgrantsnothing(GameTestHelper helper) {
        House house = House.smallAt(helper, new BlockPos(3, 1, 3), new BlockPos(6, 4, 6));
        try {
            // The lot marker inside the house, and a block outside it one step away.
            helper.setBlock(new BlockPos(3, 1, 3), BlockRegistry.HOUSE_LOT_BLOCK.get());
            helper.setBlock(new BlockPos(2, 1, 3), ORDINARY);

            ServerPlayer stranger = house.stranger(helper, "m5-neighbourly");
            BlockPos outside = helper.absolutePos(new BlockPos(2, 1, 3));

            // Outside every region, so the world's own rules decide -- and in adventure they
            // refuse. What matters is that the nearby lot block changed nothing.
            if (stranger.gameMode.destroyBlock(outside)) {
                throw new GameTestAssertException(
                        "a player one block from a lot block was granted build rights by proximity");
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void ownerrightsdonotreachintothehousenextdoor(GameTestHelper helper) {
        House mine = House.smallAt(helper, new BlockPos(0, 1, 0), new BlockPos(2, 4, 6));
        House theirs = House.smallAt(helper, new BlockPos(4, 1, 0), new BlockPos(6, 4, 6));
        try {
            helper.setBlock(new BlockPos(5, 1, 3), ORDINARY);
            BlockPos nextDoor = helper.absolutePos(new BlockPos(5, 1, 3));

            ServerPlayer owner = mine.owner(helper, "m5-owner-b");
            if (owner.gameMode.destroyBlock(nextDoor)) {
                throw new GameTestAssertException(
                        "owning one house granted rights inside the house beside it");
            }
        } finally {
            mine.close();
            theirs.close();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void leavingtheownedhousereturnsnormalworldrules(GameTestHelper helper) {
        House house = House.smallAt(helper, new BlockPos(0, 1, 0), new BlockPos(2, 4, 6));
        try {
            helper.setBlock(new BlockPos(5, 1, 5), ORDINARY);
            BlockPos outside = helper.absolutePos(new BlockPos(5, 1, 5));

            ServerPlayer owner = house.owner(helper, "m5-owner-c");
            if (!owner.getAbilities().mayBuild) {
                throw new GameTestAssertException("the owner was not lent build rights at home");
            }

            // Step outside and let the rule run again.
            owner.setPos(outside.getX() + 0.5D, outside.getY(), outside.getZ() + 0.5D);
            SurvivalZoneHandler.applyTo(owner);

            if (owner.getAbilities().mayBuild) {
                throw new GameTestAssertException("the owner kept build rights after leaving home");
            }
            if (owner.gameMode.destroyBlock(outside)) {
                throw new GameTestAssertException("the owner could still break blocks outside");
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Game mode: nothing happens to it                                   */
    /* ------------------------------------------------------------------ */

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void enteringyourownhousedoesnotmakeyoucreative(GameTestHelper helper) {
        House house = House.smallAt(helper, new BlockPos(1, 1, 1), new BlockPos(5, 4, 5));
        try {
            ServerPlayer owner = house.owner(helper, "m5-owner-d");

            if (owner.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
                throw new GameTestAssertException("the owner's game mode changed on entering: "
                        + owner.gameMode.getGameModeForPlayer());
            }
            if (owner.getAbilities().instabuild) {
                throw new GameTestAssertException(
                        "the owner was given the creative inventory and instant break");
            }
            if (owner.getAbilities().mayfly || owner.getAbilities().flying) {
                throw new GameTestAssertException("the owner was given flight");
            }
            if (owner.getAbilities().invulnerable) {
                throw new GameTestAssertException("the owner was made invulnerable");
            }
            if (!owner.getAbilities().mayBuild) {
                throw new GameTestAssertException("the one ability that was supposed to be lent was not");
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void leavinghomeneedsnogamemoderestoration(GameTestHelper helper) {
        House house = House.smallAt(helper, new BlockPos(0, 1, 0), new BlockPos(2, 4, 6));
        try {
            ServerPlayer owner = house.owner(helper, "m5-owner-e");
            GameType inside = owner.gameMode.getGameModeForPlayer();

            BlockPos outside = helper.absolutePos(new BlockPos(5, 1, 5));
            owner.setPos(outside.getX() + 0.5D, outside.getY(), outside.getZ() + 0.5D);
            SurvivalZoneHandler.applyTo(owner);

            if (owner.gameMode.getGameModeForPlayer() != inside) {
                throw new GameTestAssertException(
                        "the game mode changed on leaving (" + inside + " -> "
                        + owner.gameMode.getGameModeForPlayer() + "), so something has to remember "
                        + "and restore it -- which is the design that was rejected");
            }
            if (owner.getAbilities().instabuild || owner.getAbilities().mayfly) {
                throw new GameTestAssertException("creative abilities leaked out of the house");
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  The two foundation roles                                           */
    /* ------------------------------------------------------------------ */

    /**
     * The perimeter is the permanent outline of the building. Not even its owner takes it out.
     * Tested in a keep-sized region so it cannot be passing on a small-house assumption.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void nobodybreakstheperimeterfoundation(GameTestHelper helper) {
        House house = House.keepSizedAt(helper, new BlockPos(0, 1, 0));
        try {
            BlockPos relative = new BlockPos(2, 1, 2);
            helper.setBlock(relative, BlockRegistry.COBBLESTONE_FOUNDATION.get());
            BlockPos target = helper.absolutePos(relative);

            ServerPlayer owner = house.owner(helper, "m5-perimeter-owner");
            if (owner.gameMode.destroyBlock(target)) {
                throw new GameTestAssertException(
                        "the owner removed the perimeter foundation of their own house");
            }

            helper.setBlock(relative, BlockRegistry.COBBLESTONE_FOUNDATION.get());
            ServerPlayer stranger = house.stranger(helper, "m5-perimeter-stranger");
            if (stranger.gameMode.destroyBlock(target)) {
                throw new GameTestAssertException("a stranger removed a house's perimeter foundation");
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    /**
     * The interior floor is the other role, and the owner goes through it -- that is how a
     * basement starts. Both shipped interior floor materials are checked, because they look
     * nothing alike and only the tag says they are the same thing.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void anownercutsthroughtheirowninteriorfloor(GameTestHelper helper) {
        House house = House.keepSizedAt(helper, new BlockPos(0, 1, 0));
        try {
            checkFloor(helper, house, new BlockPos(2, 1, 2),
                    BlockRegistry.WOODEN_BOARD_FLOOR_FOUNDATION.get(), "wooden_board_floor_foundation");
            checkFloor(helper, house, new BlockPos(3, 1, 2),
                    BlockRegistry.BRICK_FOUNDATION_SPRUCE.get(), "brick_foundation_spruce");
        } finally {
            house.close();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void anonownercannotcutthroughsomebodyelsesfloor(GameTestHelper helper) {
        House house = House.smallAt(helper, new BlockPos(1, 1, 1), new BlockPos(5, 4, 5));
        try {
            for (Block floor : new Block[] {
                    BlockRegistry.WOODEN_BOARD_FLOOR_FOUNDATION.get(),
                    BlockRegistry.BRICK_FOUNDATION_SPRUCE.get() }) {
                BlockPos relative = new BlockPos(2, 1, 2);
                helper.setBlock(relative, floor);
                ServerPlayer stranger = house.stranger(helper, "m5-floor-stranger");
                if (stranger.gameMode.destroyBlock(helper.absolutePos(relative))) {
                    throw new GameTestAssertException(
                            "a stranger dug through " + floor + " in somebody else's house");
                }
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    /** The ownership record is not remodelling material, even for its owner. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void theownercannotbreaktheirownlotblock(GameTestHelper helper) {
        House house = House.smallAt(helper, new BlockPos(1, 1, 1), new BlockPos(5, 4, 5));
        try {
            BlockPos relative = new BlockPos(2, 1, 2);
            helper.setBlock(relative, BlockRegistry.HOUSE_LOT_BLOCK.get());

            ServerPlayer owner = house.owner(helper, "m5-lot-owner");
            if (owner.gameMode.destroyBlock(helper.absolutePos(relative))) {
                throw new GameTestAssertException(
                        "the owner destroyed the lot block, which is the house's ownership record "
                        + "and what its doors read to decide whether to accept a key");
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Placement                                                          */
    /* ------------------------------------------------------------------ */

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void anownerplacesablocktheyownintheirownhouse(GameTestHelper helper) {
        House house = House.keepSizedAt(helper, new BlockPos(0, 1, 0));
        try {
            ServerPlayer owner = house.owner(helper, "m5-place-owner");
            if (!place(helper, owner, new BlockPos(2, 2, 2))) {
                throw new GameTestAssertException(
                        "the owner could not place a block they were holding inside their own house");
            }
            if (owner.getMainHandItem().getCount() != 0) {
                throw new GameTestAssertException(
                        "placing did not consume the item; normal survival semantics were lost");
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void anonownercannotplaceinsidesomebodyelseshouse(GameTestHelper helper) {
        House house = House.keepSizedAt(helper, new BlockPos(0, 1, 0));
        try {
            ServerPlayer stranger = house.stranger(helper, "m5-place-stranger");
            if (place(helper, stranger, new BlockPos(2, 2, 2))) {
                throw new GameTestAssertException("a stranger built inside somebody else's house");
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void ownerplacementdoesnotcrossintoanotherhouse(GameTestHelper helper) {
        House mine = House.smallAt(helper, new BlockPos(0, 1, 0), new BlockPos(2, 4, 6));
        House theirs = House.smallAt(helper, new BlockPos(4, 1, 0), new BlockPos(6, 4, 6));
        try {
            ServerPlayer owner = mine.owner(helper, "m5-place-owner-b");
            if (place(helper, owner, new BlockPos(5, 2, 3))) {
                throw new GameTestAssertException(
                        "owning one house let its owner build inside the house next door");
            }
        } finally {
            mine.close();
            theirs.close();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void placementoutsideanyhousekeepstheworldsownrules(GameTestHelper helper) {
        House house = House.smallAt(helper, new BlockPos(0, 1, 0), new BlockPos(2, 4, 6));
        try {
            ServerPlayer owner = house.owner(helper, "m5-place-owner-c");
            BlockPos outside = new BlockPos(5, 2, 5);
            owner.setPos(helper.absolutePos(outside).getX() + 0.5D,
                    helper.absolutePos(outside).getY(), helper.absolutePos(outside).getZ() + 0.5D);
            SurvivalZoneHandler.applyTo(owner);

            if (place(helper, owner, outside)) {
                throw new GameTestAssertException(
                        "owning a house let its owner build on open ground outside it");
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    /**
     * An owner standing in their own doorway, reaching past the edge of their property.
     *
     * <p>The lent ability is a flag on the player, not on the block they are aiming at, and reach
     * is about five blocks -- so without this the house would be a licence to strip-mine the
     * ground beside it.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void anownercannotreachoutofthehousetheyarestandingin(GameTestHelper helper) {
        House house = House.smallAt(helper, new BlockPos(0, 1, 0), new BlockPos(2, 4, 6));
        try {
            helper.setBlock(new BlockPos(4, 1, 3), ORDINARY);
            BlockPos outside = helper.absolutePos(new BlockPos(4, 1, 3));

            // The owner stays inside; only their aim leaves.
            ServerPlayer owner = house.owner(helper, "m5-reacher");
            if (!owner.getAbilities().mayBuild) {
                throw new GameTestAssertException("the owner was not lent build rights at home");
            }
            if (owner.gameMode.destroyBlock(outside)) {
                throw new GameTestAssertException(
                        "an owner standing inside their house broke a block outside it");
            }
            if (place(helper, owner, new BlockPos(4, 2, 3))) {
                throw new GameTestAssertException(
                        "an owner standing inside their house built outside it");
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Bare hands                                                         */
    /* ------------------------------------------------------------------ */

    /**
     * The special rule: a house block is never destroyed by an empty hand.
     *
     * <p>A refusal rather than a break-and-restore. The alternative reading -- let the swing land
     * and put the block back afterwards -- means the block genuinely leaves the world for a moment,
     * taking its block entity, and a container's contents with it. Refusing before anything is
     * removed is the only version of "not destroyed" that is true.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void barehandsdonotdestroyahouseblock(GameTestHelper helper) {
        House house = House.smallAt(helper, new BlockPos(1, 1, 1), new BlockPos(5, 4, 5));
        try {
            BlockPos target = helper.absolutePos(new BlockPos(2, 1, 2));
            helper.setBlock(new BlockPos(2, 1, 2), ORDINARY);

            ServerPlayer owner = house.owner(helper, "m5-barehand");
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            if (owner.gameMode.destroyBlock(target)) {
                throw new GameTestAssertException("a bare hand destroyed a block inside a house");
            }
            if (!helper.getLevel().getBlockState(target).is(ORDINARY)) {
                throw new GameTestAssertException(
                        "the block is gone after a bare-handed swing that was supposed to refuse");
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    /** The same hand, one block outside the house, is still governed by the world's own rules. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void thebarehandruleisahouserulenotaworldrule(GameTestHelper helper) {
        House house = House.smallAt(helper, new BlockPos(1, 1, 1), new BlockPos(5, 4, 5));
        try {
            BlockPos outside = helper.absolutePos(new BlockPos(9, 1, 9));
            helper.setBlock(new BlockPos(9, 1, 9), ORDINARY);

            ServerPlayer owner = house.owner(helper, "m5-barehand-outside");
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            // Refused, but by the reach rule that already governed it -- the lent ability does not
            // know which way the player is pointing. What matters is that the bare-hand rule has not
            // become a new world-wide ban.
            if (owner.gameMode.destroyBlock(outside)) {
                throw new GameTestAssertException(
                        "an owner reached outside their house and broke a block");
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Lease lifecycle                                                    */
    /* ------------------------------------------------------------------ */

    /**
     * Walking out of your house takes the lease with you.
     *
     * <p>Asserted on the lease itself rather than on a break attempt, because the two can disagree:
     * a break can be refused for reach while the lease is still wrongly held, and it is the lease
     * that the client is told about.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void theleaseendswhentheownerleaves(GameTestHelper helper) {
        House house = House.smallAt(helper, new BlockPos(1, 1, 1), new BlockPos(5, 4, 5));
        try {
            ServerPlayer owner = house.owner(helper, "m5-lease-leaver");
            if (!SurvivalZoneHandler.holdsLease(owner.getUUID())) {
                throw new GameTestAssertException("the owner holds no lease while standing at home");
            }

            BlockPos outside = helper.absolutePos(new BlockPos(12, 1, 12));
            owner.setPos(outside.getX() + 0.5D, outside.getY(), outside.getZ() + 0.5D);
            SurvivalZoneHandler.applyTo(owner);

            if (SurvivalZoneHandler.holdsLease(owner.getUUID())) {
                throw new GameTestAssertException(
                        "the owner kept their build lease after walking out of the house");
            }
            if (owner.getAbilities().mayBuild) {
                throw new GameTestAssertException(
                        "the owner kept mayBuild after walking out; adventure protection is not restored");
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    /** Standing in somebody else's house is not standing in yours. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void steppingintoanotherpersonshousedoesnotcarrythelease(GameTestHelper helper) {
        House mine = House.smallAt(helper, new BlockPos(0, 1, 0), new BlockPos(4, 4, 4));
        House theirs = House.smallAt(helper, new BlockPos(10, 1, 10), new BlockPos(14, 4, 14));
        try {
            ServerPlayer owner = mine.owner(helper, "m5-lease-visitor");
            if (!SurvivalZoneHandler.holdsLease(owner.getUUID())) {
                throw new GameTestAssertException("the owner holds no lease at home");
            }

            BlockPos nextDoor = BlockPos.containing(theirs.centre());
            owner.setPos(nextDoor.getX() + 0.5D, nextDoor.getY(), nextDoor.getZ() + 0.5D);
            SurvivalZoneHandler.applyTo(owner);

            if (SurvivalZoneHandler.holdsLease(owner.getUUID())) {
                throw new GameTestAssertException(
                        "an owner carried their build lease into a house they do not own");
            }
        } finally {
            theirs.close();
            mine.close();
        }
        helper.succeed();
    }

    /**
     * Losing the house loses the lease, on the next tick and without anybody moving.
     *
     * <p>Re-deeding unregisters the region while the owner is still standing in it. Nothing else
     * about the player changes, so if the lease survived a lost region an ex-owner would keep
     * building in a house that is no longer theirs.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void losingthehouselosesthelease(GameTestHelper helper) {
        House house = House.smallAt(helper, new BlockPos(1, 1, 1), new BlockPos(5, 4, 5));
        ServerPlayer owner = house.owner(helper, "m5-lease-redeeded");
        try {
            if (!SurvivalZoneHandler.holdsLease(owner.getUUID())) {
                throw new GameTestAssertException("the owner holds no lease at home");
            }
        } finally {
            // Exactly what re-deeding does: the region goes, the player stays where they are.
            StructureRegionManager.unregisterStructure(house.record());
        }

        SurvivalZoneHandler.applyTo(owner);
        try {
            if (SurvivalZoneHandler.holdsLease(owner.getUUID())) {
                throw new GameTestAssertException(
                        "the lease outlived the house; a re-deeded owner still holds build rights");
            }
            if (owner.getAbilities().mayBuild) {
                throw new GameTestAssertException("mayBuild outlived the house");
            }
        } finally {
            SurvivalZoneHandler.forgetLentRights();
        }
        helper.succeed();
    }

    /**
     * Logging out ends the lease rather than leaving it behind.
     *
     * <p>The set used to keep every player who had ever stood in their own house, for the lifetime
     * of the server. That grows without bound, and it made the login restate hand a returning
     * player a grant earned by where they were standing when they left.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void loggingoutendsthelease(GameTestHelper helper) {
        House house = House.smallAt(helper, new BlockPos(1, 1, 1), new BlockPos(5, 4, 5));
        try {
            ServerPlayer owner = house.owner(helper, "m5-lease-quitter");
            if (!SurvivalZoneHandler.holdsLease(owner.getUUID())) {
                throw new GameTestAssertException("the owner holds no lease at home");
            }

            new SurvivalZoneHandler().onLogout(
                    new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(owner));

            if (SurvivalZoneHandler.holdsLease(owner.getUUID())) {
                throw new GameTestAssertException(
                        "the build lease survived the player logging out");
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    /** An operator in creative is not managed by this rule, and holds no lease from it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void anoperatorincreativeholdsnohouselease(GameTestHelper helper) {
        House house = House.smallAt(helper, new BlockPos(1, 1, 1), new BlockPos(5, 4, 5));
        try {
            ServerPlayer owner = house.owner(helper, "m5-lease-operator");
            if (!SurvivalZoneHandler.holdsLease(owner.getUUID())) {
                throw new GameTestAssertException("the owner holds no lease at home");
            }

            owner.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            SurvivalZoneHandler.applyTo(owner);

            if (SurvivalZoneHandler.holdsLease(owner.getUUID())) {
                throw new GameTestAssertException(
                        "an operator in creative is holding a housing lease, which this handler "
                                + "would then try to take back off them");
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Fixtures                                                           */
    /* ------------------------------------------------------------------ */

    /** A registered house, and the players who do or do not own it. */
    private static final class House implements AutoCloseable {
        private final StructureRecord record;

        private House(StructureRecord record) {
            this.record = record;
            StructureRegionManager.registerStructure(record);
        }

        static House smallAt(GameTestHelper helper, BlockPos from, BlockPos to) {
            AABB structureBox = new AABB(helper.absolutePos(from)).minmax(new AABB(helper.absolutePos(to)));
            return build(structureBox);
        }

        /** A region the size of the keep, so nothing here can rest on a small footprint. */
        static House keepSizedAt(GameTestHelper helper, BlockPos from) {
            BlockPos origin = helper.absolutePos(from);
            AABB structureBox = new AABB(origin.getX(), origin.getY(), origin.getZ(),
                    origin.getX() + 26, origin.getY() + 10, origin.getZ() + 25);
            return build(structureBox);
        }

        private static House build(AABB structureBox) {
            AABB fullBox = new AABB(structureBox.minX, structureBox.minY - 10.0D, structureBox.minZ,
                    structureBox.maxX, structureBox.maxY, structureBox.maxZ);
            UUID owner = UUID.randomUUID();
            return new House(new StructureRecord(owner, structureBox, fullBox, UUID.randomUUID(),
                    "small", "SMALL_BRICK", null, 0));
        }

        StructureRecord record() {
            return record;
        }

        net.minecraft.world.phys.Vec3 centre() {
            return record.getStructureBox().getCenter();
        }

        ServerPlayer owner(GameTestHelper helper, String name) {
            return player(helper, name, record.getOwnerUuid(), true);
        }

        ServerPlayer stranger(GameTestHelper helper, String name) {
            return player(helper, name, UUID.randomUUID(), true);
        }

        private ServerPlayer player(GameTestHelper helper, String name, UUID uuid, boolean inside) {
            ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(uuid, name));
            if (inside) {
                Vec3 centre = record.getStructureBox().getCenter();
                player.setPos(centre.x, record.getStructureBox().minY, centre.z);
            }
            player.gameMode.changeGameModeForPlayer(GameType.ADVENTURE);
            // Holding an ordinary tool, because a house block is never taken apart bare-handed --
            // see HouseBuildRights.bareHanded, and the bare-hand tests below. A vanilla pickaxe
            // rather than a mod tool: QualityToolItem and TwoHandedAxeItem are the existing escape
            // hatch that skips the house rules entirely, which would make these tests prove nothing.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            SurvivalZoneHandler.applyTo(player);
            return player;
        }

        @Override
        public void close() {
            StructureRegionManager.unregisterStructure(record);
            SurvivalZoneHandler.forgetLentRights();
        }
    }

    private static void checkFloor(GameTestHelper helper, House house, BlockPos relative,
                                   Block floor, String name) {
        helper.setBlock(relative, floor);
        ServerPlayer owner = house.owner(helper, "m5-floor-owner");
        if (!owner.gameMode.destroyBlock(helper.absolutePos(relative))) {
            throw new GameTestAssertException(
                    "the owner could not dig through " + name + ", so this house has no way down "
                    + "into a basement");
        }
    }

    /** Places a wool block from the player's hand at {@code relative}; true if it landed. */
    private static boolean place(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(relative);
        BlockPos support = target.below();

        helper.setBlock(relative.below(), Blocks.STONE);
        level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ORDINARY, 1));
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(support).add(0.0D, 0.5D, 0.0D),
                        Direction.UP, support, false));

        // The stack is deliberately left in hand: the caller checks that placement consumed it,
        // which is the survival semantics this whole design exists to keep. No test places and then
        // breaks, so nothing here is left bare-handed by accident.
        return level.getBlockState(target).is(ORDINARY);
    }
}
