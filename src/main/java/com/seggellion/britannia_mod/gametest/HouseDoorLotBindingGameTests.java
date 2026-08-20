package com.seggellion.britannia_mod.gametest;

import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.seggellion.britannia_mod.block.entity.LockableDoorBlockEntity;
import com.seggellion.britannia_mod.item.HouseKeyItem;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;
import com.seggellion.britannia_mod.util.HouseUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * A door belongs to the house that contains it, not to the nearest lot block.
 *
 * <p>The old lookup scanned four blocks either way in X and Z, two in Y, for any lot block at
 * all. Every door outside that cube -- three of the castle's four double doors, all three of
 * the villa's, one of the patio's -- found nothing, and a door that finds no lot never reaches
 * the branch that would take a key. It was refused while its own {@code Locked} flag said true,
 * which is what every shipped structure bakes into its NBT.
 *
 * <p>The template these run in is seven blocks across, which is enough: six blocks of
 * separation is already past the old cube. Real house dimensions are covered without a world in
 * {@code HouseRegionResolutionTest}, and against the actual shipped NBT in
 * {@code ShippedHouseDoorReachTest}. What this adds is the whole path -- region, lot, privacy,
 * lock, key -- end to end.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class HouseDoorLotBindingGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final double BASEMENT_DEPTH = 10.0D;

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void adoorbeyondtheoldscanradiusstilltakesitshousekey(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID houseUuid = UUID.randomUUID();

        BlockPos lotRelative = new BlockPos(0, 1, 0);
        BlockPos doorRelative = new BlockPos(6, 1, 6);   // six blocks out, past the old cube
        HouseLotBlockEntity lot = placeLot(helper, lotRelative, houseUuid);
        LockableDoorBlockEntity door = placeDoor(helper, doorRelative);

        BlockPos doorAbsolute = helper.absolutePos(doorRelative);
        StructureRecord record = regionSpanning(helper, houseUuid,
                new BlockPos(0, 0, 0), new BlockPos(6, 4, 6));
        StructureRegionManager.registerStructure(record);

        try {
            if (HouseUtil.findLot(level, doorAbsolute) != lot) {
                throw new GameTestAssertException(
                        "a door six blocks from its lot did not resolve its own house");
            }
            if (!houseUuid.equals(door.getStructureLockId())) {
                throw new GameTestAssertException("the door resolved no lock, or the wrong one");
            }
            if (!lot.isPrivate()) {
                throw new GameTestAssertException(
                        "a freshly placed lot should be private; the key path is only reached "
                        + "for a private house, so this test would prove nothing otherwise");
            }
            if (!door.isLocked()) {
                throw new GameTestAssertException("a lockable door should start locked");
            }

            ServerPlayer owner = FakePlayerFactory.get(
                    level, new GameProfile(UUID.randomUUID(), "housing-door-owner"));

            // Empty handed: refused, and still locked.
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            useDoor(level, owner, doorAbsolute);
            if (!door.isLocked()) {
                throw new GameTestAssertException("an empty-handed player unlocked the door");
            }

            // Somebody else's key: refused, and still locked.
            owner.setItemInHand(InteractionHand.MAIN_HAND, keyFor(UUID.randomUUID()));
            useDoor(level, owner, doorAbsolute);
            if (!door.isLocked()) {
                throw new GameTestAssertException("a key for another house opened this one");
            }

            // The right key: accepted.
            owner.setItemInHand(InteractionHand.MAIN_HAND, keyFor(houseUuid));
            useDoor(level, owner, doorAbsolute);
            if (door.isLocked()) {
                throw new GameTestAssertException(
                        "the house key did not unlock a door six blocks from its lot -- the door "
                        + "never reached the branch that consults a key");
            }

            // And it locks again, so the key governs both directions.
            useDoor(level, owner, doorAbsolute);
            if (!door.isLocked()) {
                throw new GameTestAssertException("the key would not lock the door again");
            }
        } finally {
            StructureRegionManager.unregisterStructure(record);
        }

        helper.succeed();
    }

    /**
     * Two houses built wall to wall, with the neighbour's lot block physically closer to the
     * door than the door's own. A proximity rule gets this wrong; containment cannot.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void adoordoesnotbindtoacloserlotinthehousenextdoor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID mine = UUID.randomUUID();
        UUID neighbour = UUID.randomUUID();

        HouseLotBlockEntity myLot = placeLot(helper, new BlockPos(0, 1, 0), mine);
        HouseLotBlockEntity theirLot = placeLot(helper, new BlockPos(3, 1, 0), neighbour);
        placeDoor(helper, new BlockPos(2, 1, 0));

        BlockPos doorAbsolute = helper.absolutePos(new BlockPos(2, 1, 0));

        // My house is x 0-2; theirs is x 3-6. The door is mine, but their lot is one block away
        // and mine is two.
        StructureRecord myHouse = regionSpanning(helper, mine,
                new BlockPos(0, 0, 0), new BlockPos(2, 4, 6));
        StructureRecord theirHouse = regionSpanning(helper, neighbour,
                new BlockPos(3, 0, 0), new BlockPos(6, 4, 6));
        StructureRegionManager.registerStructure(myHouse);
        StructureRegionManager.registerStructure(theirHouse);

        try {
            HouseLotBlockEntity resolved = HouseUtil.findLot(level, doorAbsolute);
            if (resolved == theirLot) {
                throw new GameTestAssertException(
                        "the door bound to the neighbour's lot because it was closer");
            }
            if (resolved != myLot) {
                throw new GameTestAssertException(
                        "the door resolved " + (resolved == null ? "no lot at all" : "an unknown lot"));
            }
        } finally {
            StructureRegionManager.unregisterStructure(myHouse);
            StructureRegionManager.unregisterStructure(theirHouse);
        }

        helper.succeed();
    }

    /**
     * The keep now uses lockable_metal_door, the same block the castle does, in place of the
     * vanilla iron doors it shipped with. A vanilla iron door has no block entity at all, so it
     * could never hold a lock, take a key or answer to the privacy system.
     *
     * <p>This walks the metal door through all four states the keep has to support: private and
     * locked refuses, the wrong key refuses, the right key opens the lock, and a house made
     * public opens without one.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void alockablemetaldoorbehaveslikeeveryotherhousedoor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID houseUuid = UUID.randomUUID();

        HouseLotBlockEntity lot = placeLot(helper, new BlockPos(0, 1, 0), houseUuid);

        // The keep's door sits two blocks back from its front wall; five blocks out here is
        // already past the cube the old lookup searched.
        BlockPos doorRelative = new BlockPos(5, 1, 5);
        helper.setBlock(doorRelative, BlockRegistry.LOCKABLE_METAL_DOOR.get().defaultBlockState()
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        BlockPos doorAbsolute = helper.absolutePos(doorRelative);
        if (!(level.getBlockEntity(doorAbsolute) instanceof LockableDoorBlockEntity door)) {
            throw new GameTestAssertException(
                    "lockable_metal_door grew no block entity -- this is exactly what a vanilla "
                    + "iron door does, and why the keep could not be locked");
        }

        StructureRecord record = regionSpanning(helper, houseUuid,
                new BlockPos(0, 0, 0), new BlockPos(6, 4, 6));
        StructureRegionManager.registerStructure(record);

        try {
            if (HouseUtil.findLot(level, doorAbsolute) != lot) {
                throw new GameTestAssertException("the metal door did not resolve its own house");
            }
            if (!houseUuid.equals(door.getStructureLockId())) {
                throw new GameTestAssertException("the metal door resolved no lock");
            }

            ServerPlayer owner = FakePlayerFactory.get(
                    level, new GameProfile(UUID.randomUUID(), "housing-keep-owner"));

            owner.setItemInHand(InteractionHand.MAIN_HAND, keyFor(UUID.randomUUID()));
            useDoor(level, owner, doorAbsolute);
            if (!door.isLocked()) {
                throw new GameTestAssertException("another house's key opened the metal door");
            }

            owner.setItemInHand(InteractionHand.MAIN_HAND, keyFor(houseUuid));
            useDoor(level, owner, doorAbsolute);
            if (door.isLocked()) {
                throw new GameTestAssertException("the house key did not unlock the metal door");
            }

            // Public: the privacy handler clears every lock in the box, and the door then opens
            // for anybody, with nothing in hand.
            lot.setPrivate(false);
            door.setLocked(false);
            ServerPlayer visitor = FakePlayerFactory.get(
                    level, new GameProfile(UUID.randomUUID(), "housing-keep-visitor"));
            visitor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            useDoor(level, visitor, doorAbsolute);
            if (!level.getBlockState(doorAbsolute).getValue(DoorBlock.OPEN)) {
                throw new GameTestAssertException(
                        "an unlocked door in a public house would not open for a visitor");
            }
        } finally {
            StructureRegionManager.unregisterStructure(record);
        }

        helper.succeed();
    }

    /* ------------------------------------------------------------------ */

    private static HouseLotBlockEntity placeLot(GameTestHelper helper, BlockPos relative, UUID houseUuid) {
        helper.setBlock(relative, BlockRegistry.HOUSE_LOT_BLOCK.get().defaultBlockState());
        BlockPos absolute = helper.absolutePos(relative);
        if (!(helper.getLevel().getBlockEntity(absolute) instanceof HouseLotBlockEntity lot)) {
            throw new GameTestAssertException("the house lot grew no block entity");
        }
        lot.setHouseUuid(houseUuid);
        return lot;
    }

    private static LockableDoorBlockEntity placeDoor(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, BlockRegistry.LOCKABLE_WOOD_DOOR.get().defaultBlockState()
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        BlockPos absolute = helper.absolutePos(relative);
        if (!(helper.getLevel().getBlockEntity(absolute) instanceof LockableDoorBlockEntity door)) {
            throw new GameTestAssertException("the lockable door grew no block entity");
        }
        return door;
    }

    /** A region covering the given relative corners, with the usual basement below it. */
    private static StructureRecord regionSpanning(GameTestHelper helper, UUID houseUuid,
                                                  BlockPos from, BlockPos to) {
        AABB structureBox = new AABB(helper.absolutePos(from)).minmax(new AABB(helper.absolutePos(to)));
        AABB fullBox = new AABB(
                structureBox.minX, structureBox.minY - BASEMENT_DEPTH, structureBox.minZ,
                structureBox.maxX, structureBox.maxY, structureBox.maxZ);
        return new StructureRecord(UUID.randomUUID(), structureBox, fullBox, houseUuid,
                "small", "SMALL_BRICK", null, 0);
    }

    private static ItemStack keyFor(UUID houseUuid) {
        return ((HouseKeyItem) ItemRegistry.HOUSE_KEY.get()).createKey(houseUuid);
    }

    private static void useDoor(ServerLevel level, ServerPlayer player, BlockPos door) {
        BlockState state = level.getBlockState(door);
        state.useWithoutItem(level, player,
                new BlockHitResult(Vec3.atCenterOf(door), Direction.NORTH, door, false));
    }
}
