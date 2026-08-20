package com.seggellion.britannia_mod.gametest;

import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.seggellion.britannia_mod.block.entity.LockableDoorBlockEntity;
import com.seggellion.britannia_mod.item.HouseKeyItem;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.BritanniaBlockSetTypes;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * A locked housing door cannot be opened by redstone. Anything.
 *
 * <p>This is a housing rule, not a structure one. The keep ships with pressure plates inside
 * its doorway and buttons on the wall either side -- they were its vanilla iron doors' opening
 * mechanism, and they are part of how the building was designed. Deleting them to make a lock
 * mean something would have been solving the wrong problem, and the next house authored with a
 * plate in the hall would have reopened it.
 *
 * <p>{@code DoorBlock} reaches the OPEN state from a signal in two places that matter here:
 * {@code neighborChanged} while the door stands, and {@code getStateForPlacement} when it is
 * put down. The third, {@code onExplosionHit}, is already closed to housing doors because both
 * set types declare {@code canOpenByWindCharge} false -- which is asserted below so it stays
 * that way. And a door already standing open when its house turns private is its own case:
 * nothing re-evaluates it, because the signal has not changed. The lock has.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class HouseDoorRedstoneGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** Door low enough that a plate can sit beside it and a button above that. */
    private static final BlockPos DOOR = new BlockPos(3, 1, 3);
    private static final BlockPos POWER = new BlockPos(4, 1, 3);
    private static final BlockPos LOT = new BlockPos(0, 1, 0);

    /* ------------------------------------------------------------------ */
    /*  The permitted case                                                 */
    /* ------------------------------------------------------------------ */

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void anunlockeddoorstillrespondstoredstone(GameTestHelper helper) {
        LockableDoorBlockEntity door = placeDoor(helper);
        door.setLocked(false);

        helper.setBlock(POWER, Blocks.REDSTONE_BLOCK);
        assertOpen(helper, true, "an unlocked door ignored a redstone signal");

        helper.setBlock(POWER, Blocks.AIR);
        assertOpen(helper, false, "an unlocked door stayed open after the signal was removed");

        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  The refused cases                                                  */
    /* ------------------------------------------------------------------ */

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void alockeddoorignoresanewredstonesignal(GameTestHelper helper) {
        LockableDoorBlockEntity door = placeDoor(helper);
        if (!door.isLocked()) throw new GameTestAssertException("a housing door should start locked");

        helper.setBlock(POWER, Blocks.REDSTONE_BLOCK);
        assertOpen(helper, false, "a redstone block opened a locked house door");

        // POWERED is still tracked, so the door behaves correctly the moment it is unlocked.
        BlockState state = helper.getLevel().getBlockState(helper.absolutePos(DOOR));
        if (!state.getValue(DoorBlock.POWERED)) {
            throw new GameTestAssertException(
                    "the locked door stopped tracking the signal, so unlocking it would leave the "
                    + "door out of step with the wiring around it");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void alockeddoorcannotbeopenedbyapressureplate(GameTestHelper helper) {
        placeDoor(helper);
        pressPlate(helper);
        assertOpen(helper, false, "a pressure plate opened a locked house door");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void alockeddoorcannotbeopenedbyabutton(GameTestHelper helper) {
        placeDoor(helper);
        pressButton(helper);
        assertOpen(helper, false, "a button opened a locked house door");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  The lock is authoritative, not merely consulted                    */
    /* ------------------------------------------------------------------ */

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void adoorstandingopenonasignalcloseswhenitislocked(GameTestHelper helper) {
        LockableDoorBlockEntity door = placeDoor(helper);
        door.setLocked(false);

        helper.setBlock(POWER, Blocks.REDSTONE_BLOCK);
        assertOpen(helper, true, "the door did not open while unlocked and powered");

        // The signal does not change here -- the lock does. Vanilla would leave the door open.
        door.setLocked(true);
        assertOpen(helper, false,
                "a door left standing open on a live signal when its house turned private; the "
                + "house is locked with its door hanging wide");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void unlockingahousehandsthedoorbacktoitswiring(GameTestHelper helper) {
        LockableDoorBlockEntity door = placeDoor(helper);

        helper.setBlock(POWER, Blocks.REDSTONE_BLOCK);
        assertOpen(helper, false, "a locked door opened on a signal");

        // Going public is what HousePrivacyHandler does to every door in the box.
        door.setLocked(false);
        assertOpen(helper, true,
                "an unlocked door sitting on a live signal stayed shut; redstone should behave "
                + "normally once the lock is out of the way");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Authorisation still works, signal or no signal                     */
    /* ------------------------------------------------------------------ */

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void thehousekeystillgovernsadoorthatispowered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID houseUuid = UUID.randomUUID();

        // A real private house, so the door takes the branch that consults a key rather than the
        // public branch. Without this the key is never looked at and the test proves nothing.
        helper.setBlock(LOT, BlockRegistry.HOUSE_LOT_BLOCK.get().defaultBlockState());
        if (!(level.getBlockEntity(helper.absolutePos(LOT)) instanceof HouseLotBlockEntity lot)) {
            throw new GameTestAssertException("the house lot grew no block entity");
        }
        lot.setHouseUuid(houseUuid);

        LockableDoorBlockEntity door = placeDoor(helper);
        BlockPos doorAbsolute = helper.absolutePos(DOOR);

        AABB structureBox = new AABB(helper.absolutePos(new BlockPos(0, 0, 0)))
                .minmax(new AABB(helper.absolutePos(new BlockPos(6, 4, 6))));
        AABB fullBox = new AABB(structureBox.minX, structureBox.minY - 10.0D, structureBox.minZ,
                structureBox.maxX, structureBox.maxY, structureBox.maxZ);
        StructureRecord record = new StructureRecord(UUID.randomUUID(), structureBox, fullBox,
                houseUuid, "small", "SMALL_BRICK", null, 0);
        StructureRegionManager.registerStructure(record);

        try {
            pressPlate(helper);
            assertOpen(helper, false, "the plate opened a locked door");

            ServerPlayer owner = FakePlayerFactory.get(
                    level, new GameProfile(UUID.randomUUID(), "housing-redstone-owner"));

            // Somebody else's key, on a door a plate is holding down: still shut.
            owner.setItemInHand(InteractionHand.MAIN_HAND, keyFor(UUID.randomUUID()));
            useDoor(level, owner, doorAbsolute);
            if (!door.isLocked()) {
                throw new GameTestAssertException("another house key unlocked this door");
            }
            assertOpen(helper, false, "the door opened for the wrong key while powered");

            // The right key. The lock comes off, and the wiring immediately takes over -- which is
            // the point: the key decides authorisation, redstone decides the door.
            owner.setItemInHand(InteractionHand.MAIN_HAND, keyFor(houseUuid));
            useDoor(level, owner, doorAbsolute);
            if (door.isLocked()) {
                throw new GameTestAssertException(
                        "the house key did not unlock a door that a pressure plate was holding");
            }
            assertOpen(helper, true,
                    "the door stayed shut after being unlocked on a live plate; once the lock is "
                    + "off, redstone should behave normally");

            // And locking it again with the key shuts it, plate or no plate.
            useDoor(level, owner, doorAbsolute);
            if (!door.isLocked()) {
                throw new GameTestAssertException("the key would not lock the door again");
            }
            assertOpen(helper, false,
                    "locking the door left it standing open because the plate was still pressed");
        } finally {
            StructureRegionManager.unregisterStructure(record);
        }
        helper.succeed();
    }

    /**
     * The third door-opening path, pinned rather than overridden.
     *
     * <p>{@code DoorBlock.onExplosionHit} blows a door open on a wind charge when its set type
     * allows it. Both housing set types say no, so there is nothing to override -- but that is a
     * one-word change away from being untrue, and it would be a silent one.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void housingdoorsetsdonotopenonawindcharge(GameTestHelper helper) {
        if (BritanniaBlockSetTypes.WOOD_DOOR.canOpenByWindCharge()
                || BritanniaBlockSetTypes.METAL_DOOR.canOpenByWindCharge()) {
            throw new GameTestAssertException(
                    "a housing door set type now opens on a wind charge, which walks straight past "
                    + "the lock; LockableDoorBlock needs to override onExplosionHit");
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Fixtures                                                           */
    /* ------------------------------------------------------------------ */

    private static LockableDoorBlockEntity placeDoor(GameTestHelper helper) {
        helper.setBlock(DOOR, BlockRegistry.LOCKABLE_WOOD_DOOR.get().defaultBlockState()
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(DOOR.above(), BlockRegistry.LOCKABLE_WOOD_DOOR.get().defaultBlockState()
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(DOOR))
                instanceof LockableDoorBlockEntity door)) {
            throw new GameTestAssertException("the lockable door grew no block entity");
        }
        return door;
    }

    /** A plate beside the door, stood on. */
    private static void pressPlate(GameTestHelper helper) {
        helper.setBlock(POWER, Blocks.STONE_PRESSURE_PLATE);
        helper.setBlock(POWER, Blocks.STONE_PRESSURE_PLATE.defaultBlockState()
                .setValue(PressurePlateBlock.POWERED, true));
    }

    /** A button beside the door, pushed. */
    private static void pressButton(GameTestHelper helper) {
        helper.setBlock(POWER, Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(ButtonBlock.FACE, AttachFace.FLOOR)
                .setValue(ButtonBlock.POWERED, true));
    }

    private static void assertOpen(GameTestHelper helper, boolean expected, String message) {
        BlockPos absolute = helper.absolutePos(DOOR);
        BlockState state = helper.getLevel().getBlockState(absolute);
        if (!(state.getBlock() instanceof DoorBlock)) {
            throw new GameTestAssertException("the door is gone: " + state);
        }
        if (state.getValue(DoorBlock.OPEN) != expected) {
            throw new GameTestAssertException(message + " (open=" + state.getValue(DoorBlock.OPEN)
                    + ", powered=" + state.getValue(DoorBlock.POWERED) + ")");
        }
    }

    private static void useDoor(ServerLevel level, ServerPlayer player, BlockPos door) {
        level.getBlockState(door).useWithoutItem(level, player,
                new BlockHitResult(Vec3.atCenterOf(door), Direction.NORTH, door, false));
    }

    private static ItemStack keyFor(UUID houseUuid) {
        return ((HouseKeyItem) ItemRegistry.HOUSE_KEY.get()).createKey(houseUuid);
    }
}
