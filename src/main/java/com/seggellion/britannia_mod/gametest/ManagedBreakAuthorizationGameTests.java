package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.event.CustomBlockBreakHandler;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.mining.MiningSkill;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.resource.extraction.ManagedBreakAuthorization;
import com.seggellion.britannia_mod.skill.SkillManager;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * The shared authorization boundary, and the architectural guarantee it exists to provide.
 *
 * <p>Three defects came from one fact: a handler that cancels a {@code BreakEvent} and mutates the
 * world is an authority, and a cancelled event never reaches anybody downstream — so
 * {@code StructureProtectionHandler} never ran for a break a managed handler had taken over. Each
 * was fixed by hand after the damage was found. {@link ManagedBreakAuthorization} is the shared
 * prerequisite every such handler now runs, and the point of these tests is that
 * <b>event ordering is no longer what makes the system safe.</b>
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class ManagedBreakAuthorizationGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos TARGET = new BlockPos(1, 1, 1);

    private ManagedBreakAuthorizationGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static StructureRecord houseAround(GameTestHelper helper, UUID owner, ServerLevel level) {
        AABB box = new AABB(helper.absolutePos(new BlockPos(0, 0, 0)))
                .minmax(new AABB(helper.absolutePos(new BlockPos(6, 5, 6))));
        return new StructureRecord(owner, box, box, UUID.randomUUID(),
                "small", "SMALL_BRICK", null, 0, level.dimension());
    }

    /* ------------------------------------------------------------------ */
    /*  The common decision                                                */
    /* ------------------------------------------------------------------ */

    @GameTest(template = TEMPLATE, batch = "managed_break_auth", timeoutTicks = 60)
    public static void openGroundIsAllowedForARealPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ManagedResourceTestPlayers.survival(level, "auth-open-ground");
        ManagedBreakAuthorization.Decision decision =
                ManagedBreakAuthorization.evaluate(level, helper.absolutePos(TARGET), player);
        check(decision.allowed(), "open ground must not be refused, was " + decision.reason());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "managed_break_auth", timeoutTicks = 60)
    public static void aStrangersHouseIsRefusedAndAnOwnersIsNot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(TARGET);
        ServerPlayer owner = ManagedResourceTestPlayers.survival(level, "auth-owner");
        ServerPlayer stranger = ManagedResourceTestPlayers.survival(level, "auth-stranger");

        StructureRecord house = houseAround(helper, owner.getUUID(), level);
        StructureRegionManager.registerStructure(house);
        try {
            check(ManagedBreakAuthorization.evaluate(level, absolute, stranger).reason()
                            == ManagedBreakAuthorization.Reason.HOUSE_PROTECTED,
                    "a stranger inside somebody's house must be refused");
            check(ManagedBreakAuthorization.evaluate(level, absolute, owner).allowed(),
                    "the owner of the house must not be refused inside it");
        } finally {
            StructureRegionManager.unregisterStructure(house);
        }
        helper.succeed();
    }

    /** Automation is refused for what it is, before the ground is ever consulted. */
    @GameTest(template = TEMPLATE, batch = "managed_break_auth", timeoutTicks = 60)
    public static void automationIsRefusedOnOpenGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer machine = FakePlayerFactory.get(level,
                new GameProfile(UUID.randomUUID(), "auth-quarry-mod"));
        ManagedBreakAuthorization.Decision decision =
                ManagedBreakAuthorization.evaluate(level, helper.absolutePos(TARGET), machine);
        check(decision.reason() == ManagedBreakAuthorization.Reason.AUTOMATION,
                "a fake player must be refused as automation, was " + decision.reason());
        check(!ManagedBreakAuthorization.evaluate(level, helper.absolutePos(TARGET), null).allowed(),
                "a non-player must be refused");
        helper.succeed();
    }

    /**
     * The location answer is about the location. Every tool, and no tool at all, must produce the
     * identical decision — a held item has never been a permission and must not become one.
     */
    @GameTest(template = TEMPLATE, batch = "managed_break_auth", timeoutTicks = 60)
    public static void theDecisionIsIndependentOfWhatIsHeld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(TARGET);
        StructureRecord house = houseAround(helper, UUID.randomUUID(), level);
        StructureRegionManager.registerStructure(house);
        try {
            ServerPlayer player = ManagedResourceTestPlayers.survival(level, "auth-hands");
            for (ItemStack tool : List.of(ItemStack.EMPTY,
                    ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3),
                    ToolRegistry.createShovel(UOMetalToolMaterial.IRON, 3),
                    new ItemStack(ItemRegistry.TWO_HANDED_AXE.get()))) {
                player.setItemInHand(InteractionHand.MAIN_HAND, tool);
                check(ManagedBreakAuthorization.evaluate(level, absolute, player).reason()
                                == ManagedBreakAuthorization.Reason.HOUSE_PROTECTED,
                        "holding " + tool.getItem() + " changed the location decision");
            }
        } finally {
            StructureRegionManager.unregisterStructure(house);
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Ordering no longer carries the security                            */
    /* ------------------------------------------------------------------ */

    /**
     * The architectural guarantee, demonstrated behaviourally rather than by reading source.
     *
     * <p>{@code CustomBlockBreakHandler} is invoked <em>directly</em>, with no bus, no priorities
     * and therefore no {@code MiningGateHandler} ahead of it and no
     * {@code StructureProtectionHandler} behind it. It used to depend entirely on those
     * neighbours; now it carries its own authorization, so it must refuse a stranger's house on
     * its own — and leave the block, the ledger and the tool untouched doing it.
     */
    @GameTest(template = TEMPLATE, batch = "managed_break_auth", timeoutTicks = 60)
    public static void theMutatingHandlerRefusesAloneWithNoOtherListener(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(TARGET);
        helper.setBlock(TARGET, BlockRegistry.SILVER_ORE.get());

        ServerPlayer stranger = ManagedResourceTestPlayers.survival(level, "solo-trespasser");
        stranger.setGameMode(GameType.ADVENTURE);
        stranger.setItemInHand(InteractionHand.MAIN_HAND,
                ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3));
        SkillManager.applyConfirmedValue(stranger, MiningSkill.SKILL_ID, 100.0f);

        StructureRecord house = houseAround(helper, UUID.randomUUID(), level);
        StructureRegionManager.registerStructure(house);
        try {
            BlockState state = level.getBlockState(absolute);
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, absolute, state, stranger);

            // Straight into the mutating handler. Nothing else runs.
            new CustomBlockBreakHandler().onBlockBreak(event);

            helper.assertBlockPresent(BlockRegistry.SILVER_ORE.get(), TARGET);
            check(level.getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(3.0D)).isEmpty(),
                    "the lone mutating handler minted a yield inside somebody's house");
            check(!BrokenBlockDataStorage.get(level).getBrokenBlocks().containsKey(absolute),
                    "the lone mutating handler enrolled restoration debt for a refused break");
            check(stranger.getMainHandItem().getDamageValue() == 0,
                    "the lone mutating handler charged durability for a refused break");
        } finally {
            StructureRegionManager.unregisterStructure(house);
        }
        helper.succeed();
    }

    /** The same handler, alone, on open ground: it must still do its job. */
    @GameTest(template = TEMPLATE, batch = "managed_break_auth", timeoutTicks = 60)
    public static void theMutatingHandlerStillExtractsAloneOnOpenGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(TARGET);
        helper.setBlock(TARGET, BlockRegistry.SILVER_ORE.get());

        ServerPlayer miner = ManagedResourceTestPlayers.survival(level, "solo-miner");
        miner.setGameMode(GameType.ADVENTURE);
        miner.setItemInHand(InteractionHand.MAIN_HAND,
                ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3));
        SkillManager.applyConfirmedValue(miner, MiningSkill.SKILL_ID, 100.0f);

        BlockState state = level.getBlockState(absolute);
        new CustomBlockBreakHandler().onBlockBreak(
                new BlockEvent.BreakEvent(level, absolute, state, miner));

        helper.assertBlockNotPresent(BlockRegistry.SILVER_ORE.get(), TARGET);
        check(!level.getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(3.0D)).isEmpty(),
                "open-ground extraction must still yield");
        helper.succeed();
    }
}
