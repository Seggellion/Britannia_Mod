package com.seggellion.britannia_mod.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.LockableDoorBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionCodec;
import com.seggellion.britannia_mod.structure.StructureRegionManager;
import com.seggellion.britannia_mod.structure.StructureRegionRehydrator;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Housing Deed Milestone 2 -- the defect, in world, and the restore that answers it.
 *
 * <p>House regions lived only in a static map that nothing saved. After a restart the map was
 * empty, and two things followed that players actually felt:
 *
 * <ul>
 *   <li>{@code LockableDoorBlockEntity#getStructureLockId()} resolves the lock from whichever
 *       region encloses the door. With no region it returns null, so {@code LockableDoorBlock}
 *       never reaches the branch that would take a key -- and {@code locked} defaults to true
 *       and persists in block-entity NBT. The door is shut for good.</li>
 *   <li>{@code StructureProtectionHandler} lets a survival player break a block only inside a
 *       region they own. With no region the owner cannot dig their own basement, which is the
 *       defect as it was reported.</li>
 * </ul>
 *
 * <p>These tests do not clear the region map -- other GameTests batch alongside them and
 * register regions of their own. They do not need to: a house Rails has told nobody about is
 * already in exactly the state a restart leaves it in, so the "before" half is simply the
 * assertion made first.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class StructureRegionRehydrationGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** Matches StructureUtils: the region reaches ten blocks below the structure. */
    private static final double BASEMENT_DEPTH = 10.0D;

    /**
     * The block these tests dig, and it is a deliberate choice.
     *
     * <p>Stone is the obvious thing to put under a house and it is the wrong thing to test with:
     * {@code MiningGateHandler} cancels a stone break at HIGH priority for anyone without the
     * mining skill, so a fake player is refused for a reason that has nothing to do with houses.
     * That produced a red test where the region restore was working perfectly, and it would have
     * produced a green one in the "stranger is refused" direction for exactly the same wrong
     * reason. Wool is gated by nothing.
     */
    private static final Block DIGGABLE = Blocks.WHITE_WOOL;

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void arestoredregiongivesbackthedoorlockandtherighttodig(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos doorRelative = new BlockPos(1, 2, 1);
        BlockPos basementRelative = new BlockPos(1, 1, 1);

        BlockState doorLower = BlockRegistry.LOCKABLE_WOOD_DOOR.get().defaultBlockState()
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        helper.setBlock(doorRelative, doorLower);
        helper.setBlock(basementRelative, DIGGABLE);

        BlockPos doorAbsolute = helper.absolutePos(doorRelative);
        BlockPos basementAbsolute = helper.absolutePos(basementRelative);

        if (!(level.getBlockEntity(doorAbsolute) instanceof LockableDoorBlockEntity door)) {
            throw new GameTestAssertException("the lockable door grew no block entity");
        }

        UUID houseUuid = UUID.randomUUID();
        ServerPlayer owner = survivalPlayer(level, "housing-m2-owner", doorAbsolute);

        // --- as a restart leaves it: no region, so no lock and no rights ----------------
        if (door.getStructureLockId() != null) {
            throw new GameTestAssertException(
                    "a door with no enclosing region should resolve no lock at all");
        }
        if (owner.gameMode.destroyBlock(basementAbsolute)) {
            throw new GameTestAssertException(
                    "the owner dug beneath their house with no region registered, so this test cannot "
                    + "tell a restored region from an absent one");
        }

        // --- what the server does at boot -----------------------------------------------
        StructureRecord record = houseAround(doorAbsolute, houseUuid, owner.getUUID());
        StructureRegionRehydrator.Result result =
                StructureRegionRehydrator.apply(shardHouses(record, doorAbsolute, houseUuid, owner.getUUID()));
        try {
            if (result.registered() != 1) {
                throw new GameTestAssertException(
                        "restore registered " + result.registered() + " regions, expected 1");
            }

            // --- the door knows its house again -----------------------------------------
            UUID resolved = door.getStructureLockId();
            if (!houseUuid.equals(resolved)) {
                throw new GameTestAssertException(
                        "the door resolved lock " + resolved + " after restore, expected " + houseUuid);
            }

            // --- and the owner can dig underneath it -------------------------------------
            if (!owner.gameMode.destroyBlock(basementAbsolute)) {
                throw new GameTestAssertException(
                        "the owner still could not break a block beneath their own house after the "
                        + "region was restored. The region itself is fine -- owner matches, box "
                        + "contains the target -- so something other than StructureProtectionHandler "
                        + "cancelled the break; check the other BreakEvent listeners.");
            }
        } finally {
            StructureRegionManager.unregisterStructure(record);
        }

        helper.succeed();
    }

    /**
     * A restored region belongs to the player who owns it and to nobody else.
     *
     * <p>The stranger half of this is worthless on its own -- a refusal proves nothing when an
     * absent region refuses everybody. So the owner breaks an identical block in the same region
     * immediately afterwards. Only the pair together says the region came back and knows whose
     * it is.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void arestoredregionstillknowswhoseitis(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos strangerTargetRelative = new BlockPos(3, 1, 3);
        BlockPos ownerTargetRelative = new BlockPos(3, 1, 4);
        helper.setBlock(strangerTargetRelative, DIGGABLE);
        helper.setBlock(ownerTargetRelative, DIGGABLE);

        BlockPos strangerTarget = helper.absolutePos(strangerTargetRelative);
        BlockPos ownerTarget = helper.absolutePos(ownerTargetRelative);

        UUID houseUuid = UUID.randomUUID();
        ServerPlayer owner = survivalPlayer(level, "housing-m2-owner-b", ownerTarget);
        ServerPlayer stranger = survivalPlayer(level, "housing-m2-stranger", strangerTarget);

        // One region covering both targets, owned by `owner`.
        AABB structureBox = new AABB(strangerTarget).minmax(new AABB(ownerTarget));
        AABB fullBox = new AABB(
                structureBox.minX, structureBox.minY - BASEMENT_DEPTH, structureBox.minZ,
                structureBox.maxX, structureBox.maxY, structureBox.maxZ);
        StructureRecord record = new StructureRecord(owner.getUUID(), structureBox, fullBox,
                houseUuid, "small", "SMALL_BRICK", null, 90);

        StructureRegionRehydrator.apply(shardHouses(record, ownerTarget, houseUuid, owner.getUUID()));
        try {
            if (stranger.gameMode.destroyBlock(strangerTarget)) {
                throw new GameTestAssertException(
                        "a stranger broke a block inside a restored region they do not own");
            }
            if (!owner.gameMode.destroyBlock(ownerTarget)) {
                throw new GameTestAssertException(
                        "the owner could not break a block in their own restored region, so the "
                        + "stranger's refusal above proves nothing about ownership");
            }
        } finally {
            StructureRegionManager.unregisterStructure(record);
        }

        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Fixtures                                                           */
    /* ------------------------------------------------------------------ */

    private static ServerPlayer survivalPlayer(ServerLevel level, String name, BlockPos at) {
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        player.setPos(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        // Holding an ordinary tool: nothing in a house comes apart bare-handed. A vanilla pickaxe
        // rather than a mod tool, because QualityToolItem and TwoHandedAxeItem skip the house rules
        // entirely and would make the restored region prove nothing.
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
        return player;
    }

    /**
     * A one-block house around {@code anchor}, with the ten-block basement below it that
     * {@code StructureUtils.makeStructureBoxes} gives every real house.
     */
    private static StructureRecord houseAround(BlockPos anchor, UUID houseUuid, UUID ownerUuid) {
        AABB structureBox = new AABB(anchor);
        AABB fullBox = new AABB(
                structureBox.minX, structureBox.minY - BASEMENT_DEPTH, structureBox.minZ,
                structureBox.maxX, structureBox.maxY, structureBox.maxZ);

        return new StructureRecord(ownerUuid, structureBox, fullBox, houseUuid,
                "small", "SMALL_BRICK", null, 90);
    }

    /** The shard's houses exactly as {@code Api::HousesController#index} renders them. */
    private static JsonArray shardHouses(StructureRecord record, BlockPos origin,
                                         UUID houseUuid, UUID ownerUuid) {
        JsonObject row = new JsonObject();
        row.addProperty("uuid", houseUuid.toString());
        row.addProperty("owner_uuid", ownerUuid.toString());
        row.add("structure", StructureRegionCodec.encode(record, origin));

        JsonArray rows = new JsonArray();
        rows.add(row);
        return rows;
    }
}
