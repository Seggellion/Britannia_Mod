package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.AutoClosingDoorBlock;
import com.seggellion.britannia_mod.block.TripleBlockPart;
import com.seggellion.britannia_mod.block.TripleMetalDoorBlock;
import com.seggellion.britannia_mod.block.entity.LockableDoorBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Dedicated-server tests for RunUO-style Britannia door timing and safety. */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class DoorAutoCloseGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos DOOR = new BlockPos(2, 1, 2);

    @GameTest(template = TEMPLATE, timeoutTicks = 430)
    public static void anunobstructeddoorclosesaftertwentyseconds(GameTestHelper helper) {
        placeTwoHighDoor(helper, BlockRegistry.WOOD_DOOR.get());
        helper.useBlock(DOOR, helper.makeMockPlayer(GameType.SURVIVAL));

        helper.startSequence()
                .thenIdle(AutoClosingDoorBlock.AUTO_CLOSE_DELAY_TICKS - 1)
                .thenExecute(() -> assertTwoHighOpen(helper, true,
                        "the door closed before the 20-second delay elapsed"))
                .thenIdle(2)
                .thenExecute(() -> assertTwoHighOpen(helper, false,
                        "the clear door did not close after the 20-second delay"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 630)
    public static void aplayerblocksthedooruntilthetensecondretry(GameTestHelper helper) {
        placeTwoHighDoor(helper, BlockRegistry.WOOD_DOOR.get());
        helper.useBlock(DOOR, helper.makeMockPlayer(GameType.SURVIVAL));

        ServerPlayer blocker = helper.makeMockServerPlayerInLevel();
        BlockPos absoluteDoor = helper.absolutePos(DOOR);
        blocker.teleportTo(absoluteDoor.getX() + 0.5D, absoluteDoor.getY(),
                absoluteDoor.getZ() + 0.9D);

        helper.startSequence()
                .thenIdle(AutoClosingDoorBlock.AUTO_CLOSE_DELAY_TICKS + 1)
                .thenExecute(() -> {
                    assertTwoHighOpen(helper, true, "the door closed through a player");
                    helper.getLevel().getServer().getPlayerList().remove(blocker);
                })
                .thenIdle(AutoClosingDoorBlock.AUTO_CLOSE_RETRY_TICKS - 2)
                .thenExecute(() -> assertTwoHighOpen(helper, true,
                        "the blocked-door retry ran before 10 seconds"))
                .thenIdle(2)
                .thenExecute(() -> assertTwoHighOpen(helper, false,
                        "the door did not close on the next retry after the player left"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 630)
    public static void amobblocksthedooruntilthetensecondretry(GameTestHelper helper) {
        placeTwoHighDoor(helper, BlockRegistry.WOOD_DOOR.get());
        helper.useBlock(DOOR, helper.makeMockPlayer(GameType.SURVIVAL));
        Cow blocker = helper.spawnWithNoFreeWill(EntityType.COW, DOOR);

        helper.startSequence()
                .thenIdle(AutoClosingDoorBlock.AUTO_CLOSE_DELAY_TICKS + 1)
                .thenExecute(() -> {
                    assertTwoHighOpen(helper, true, "the door closed through a living mob");
                    blocker.discard();
                })
                .thenIdle(AutoClosingDoorBlock.AUTO_CLOSE_RETRY_TICKS)
                .thenExecute(() -> assertTwoHighOpen(helper, false,
                        "the door did not close on the next retry after the mob left"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 630)
    public static void reopeninginvalidatestheoldopeningcycle(GameTestHelper helper) {
        placeTwoHighDoor(helper, BlockRegistry.WOOD_DOOR.get());
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.useBlock(DOOR, player);

        helper.startSequence()
                .thenIdle(100)
                .thenExecute(() -> helper.useBlock(DOOR, player)) // manual close: cancel
                .thenIdle(100)
                .thenExecute(() -> helper.useBlock(DOOR, player)) // reopen: a fresh 400 ticks
                .thenIdle(201)
                .thenExecute(() -> assertTwoHighOpen(helper, true,
                        "an obsolete tick closed the newly reopened door early"))
                .thenIdle(200)
                .thenExecute(() -> assertTwoHighOpen(helper, false,
                        "the reopened door did not close after its own 20-second delay"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 440)
    public static void activepowerholdsthedooropen(GameTestHelper helper) {
        placeTwoHighDoor(helper, BlockRegistry.WOOD_DOOR.get());
        helper.setBlock(DOOR.relative(Direction.EAST), Blocks.REDSTONE_BLOCK);
        assertTwoHighOpen(helper, true, "redstone did not open the door");

        helper.startSequence()
                .thenIdle(AutoClosingDoorBlock.AUTO_CLOSE_DELAY_TICKS + 1)
                .thenExecute(() -> assertTwoHighOpen(helper, true,
                        "automatic close overrode an active redstone signal"))
                .thenExecute(() -> helper.setBlock(DOOR.relative(Direction.EAST), Blocks.AIR))
                .thenIdle(2)
                .thenExecute(() -> assertTwoHighOpen(helper, false,
                        "the door did not return to normal redstone behavior when power ended"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 430)
    public static void lockabledoorsretainbothhalvesandautoclose(GameTestHelper helper) {
        placeTwoHighDoor(helper, BlockRegistry.LOCKABLE_WOOD_DOOR.get());
        BlockPos absoluteDoor = helper.absolutePos(DOOR);
        if (!(helper.getLevel().getBlockEntity(absoluteDoor)
                instanceof LockableDoorBlockEntity lock)) {
            throw new GameTestAssertException("the lockable door grew no lower block entity");
        }
        lock.setLocked(false);
        helper.useBlock(DOOR, helper.makeMockPlayer(GameType.SURVIVAL));

        helper.startSequence()
                .thenIdle(AutoClosingDoorBlock.AUTO_CLOSE_DELAY_TICKS + 1)
                .thenExecute(() -> assertTwoHighOpen(helper, false,
                        "the unlocked housing door did not auto-close both halves"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 430)
    public static void tripleheightdoorsautocloseasoneunit(GameTestHelper helper) {
        placeTripleDoor(helper);
        helper.useBlock(DOOR, helper.makeMockPlayer(GameType.SURVIVAL));
        assertTripleOpen(helper, true, "manual interaction did not open all three parts");

        helper.startSequence()
                .thenIdle(AutoClosingDoorBlock.AUTO_CLOSE_DELAY_TICKS + 1)
                .thenExecute(() -> assertTripleOpen(helper, false,
                        "automatic close did not close all three parts together"))
                .thenSucceed();
    }

    private static void placeTwoHighDoor(GameTestHelper helper, Block block) {
        BlockState lower = block.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.OPEN, false)
                .setValue(DoorBlock.POWERED, false);
        helper.setBlock(DOOR, lower);
        helper.setBlock(DOOR.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void placeTripleDoor(GameTestHelper helper) {
        TripleMetalDoorBlock block = (TripleMetalDoorBlock) BlockRegistry.IRON_FENCE_GATE.get();
        // TripleMetalDoorBlock checks its floor before accepting the lower part. The shared empty
        // template does not promise a sturdy block directly beneath every rotated test position.
        helper.setBlock(DOOR.below(), Blocks.STONE);
        BlockState state = block.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.OPEN, false)
                .setValue(DoorBlock.POWERED, false);
        helper.setBlock(DOOR, state.setValue(TripleMetalDoorBlock.TRIPLE_PART,
                TripleBlockPart.LOWER));
        helper.setBlock(DOOR.above(), state.setValue(TripleMetalDoorBlock.TRIPLE_PART,
                TripleBlockPart.MIDDLE));
        helper.setBlock(DOOR.above(2), state.setValue(TripleMetalDoorBlock.TRIPLE_PART,
                TripleBlockPart.UPPER));
    }

    private static void assertTwoHighOpen(GameTestHelper helper, boolean expected, String message) {
        assertPartOpen(helper, DOOR, expected, message + " (lower half)");
        assertPartOpen(helper, DOOR.above(), expected, message + " (upper half)");
    }

    private static void assertTripleOpen(GameTestHelper helper, boolean expected, String message) {
        for (int y = 0; y < 3; y++) {
            assertPartOpen(helper, DOOR.above(y), expected, message + " (part " + y + ")");
        }
    }

    private static void assertPartOpen(GameTestHelper helper, BlockPos relativePos,
                                       boolean expected, String message) {
        BlockState state = helper.getBlockState(relativePos);
        if (!(state.getBlock() instanceof DoorBlock)) {
            throw new GameTestAssertException(message + "; the door part is missing: " + state);
        }
        if (state.getValue(DoorBlock.OPEN) != expected) {
            throw new GameTestAssertException(message + "; open="
                    + state.getValue(DoorBlock.OPEN));
        }
    }
}
