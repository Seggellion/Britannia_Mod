package com.seggellion.britannia_mod.gametest;

import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.deposit.ManagedDepositExtraction;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * A managed deposit inside somebody's house is theirs.
 *
 * <h2>The defect</h2>
 * The clay pass reported it rather than fixing it: the deposit rule runs at HIGH priority so that
 * it lands before {@code CustomBlockBreakHandler}, which puts it ahead of
 * {@code StructureProtectionHandler} at NORMAL. Anything the deposit rule allowed was therefore
 * allowed before house protection had a chance to speak. An administrator placing a bed inside a
 * player's house — by accident or otherwise — would have handed every stranger a way to reach
 * inside it.
 *
 * <p>Priority was not the fix. Reordering would have traded one silent dependency for another, and
 * the left-click extraction path does not go through {@code BlockEvent.BreakEvent} at all, so no
 * ordering could have covered it. The extraction asks {@link com.seggellion.britannia_mod.structure.HouseBuildRights}
 * itself, which is the same authority the break and place rules already use — one copy of the
 * region logic, not two.
 *
 * <p>Written against the deposit catalogue rather than against clay, because the rule belongs to
 * every managed deposit; both the clay bed and the silica bed are exercised.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class ManagedDepositHouseProtectionGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos BED = new BlockPos(1, 1, 1);

    private ManagedDepositHouseProtectionGameTests() {
    }

    /** Outside any house, nothing changed: the tool decides and the bed is worked. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void adepositoutsideanyhouseworksasbefore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = placeBed(helper, BlockRegistry.CLAY_DEPOSIT.get());

        ManagedDepositExtraction.Result result = ManagedDepositExtraction.extract(
                level, absolute, player(level, "outside-digger"), shovel());
        if (result != ManagedDepositExtraction.Result.EXTRACTED) {
            throw new GameTestAssertException(
                    "a deposit standing in open country was refused: " + result);
        }
        helper.succeed();
    }

    /** Inside a house they own, the owner works it exactly as they would outside. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void theowneroftheirownhousemayworkadepositinit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = placeBed(helper, BlockRegistry.CLAY_DEPOSIT.get());
        ServerPlayer owner = player(level, "house-owner");
        StructureRecord house = houseAround(helper, owner.getUUID(), level);

        StructureRegionManager.registerStructure(house);
        try {
            ManagedDepositExtraction.Result result =
                    ManagedDepositExtraction.extract(level, absolute, owner, shovel());
            if (result != ManagedDepositExtraction.Result.EXTRACTED) {
                throw new GameTestAssertException(
                        "the owner was refused a deposit inside their own house: " + result);
            }
        } finally {
            StructureRegionManager.unregisterStructure(house);
        }
        helper.succeed();
    }

    /**
     * Inside somebody else's house, a stranger is refused — with the right tool in hand.
     *
     * <p>The tool is deliberately the authorized one. A refusal that only happened because the
     * stranger was holding the wrong thing would prove nothing about house protection.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void astrangerisrefusedadepositinsideafanotherhouse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = placeBed(helper, BlockRegistry.CLAY_DEPOSIT.get());
        ServerPlayer stranger = player(level, "house-stranger");
        StructureRecord house = houseAround(helper, UUID.randomUUID(), level);

        StructureRegionManager.registerStructure(house);
        try {
            ManagedDepositExtraction.Result result =
                    ManagedDepositExtraction.extract(level, absolute, stranger, shovel());
            if (result != ManagedDepositExtraction.Result.PROTECTED) {
                throw new GameTestAssertException(
                        "a stranger reached into somebody's house through a deposit: " + result);
            }
            helper.assertBlockPresent(BlockRegistry.CLAY_DEPOSIT.get(), BED);
        } finally {
            StructureRegionManager.unregisterStructure(house);
        }
        helper.succeed();
    }

    /** The same rule, for the silica bed: this is deposit policy, not a clay special case. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void thesameprotectioncoverseverymanageddeposit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = placeBed(helper, BlockRegistry.SILICA_SAND_DEPOSIT.get());
        ServerPlayer stranger = player(level, "silica-stranger");
        StructureRecord house = houseAround(helper, UUID.randomUUID(), level);

        StructureRegionManager.registerStructure(house);
        try {
            if (ManagedDepositExtraction.extract(level, absolute, stranger, shovel())
                    != ManagedDepositExtraction.Result.PROTECTED) {
                throw new GameTestAssertException("silica is not covered by house protection");
            }
            helper.assertBlockPresent(BlockRegistry.SILICA_SAND_DEPOSIT.get(), BED);
        } finally {
            StructureRegionManager.unregisterStructure(house);
        }
        helper.succeed();
    }

    /**
     * House protection is asked before the tool, so the refusal names the real reason.
     *
     * <p>A stranger with the wrong tool must be told it is not their house rather than that they
     * need a shovel — otherwise the message invites them to come back with one.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void thehousereasonoutranksthetoolreason(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = placeBed(helper, BlockRegistry.CLAY_DEPOSIT.get());
        ServerPlayer stranger = player(level, "wrong-tool-stranger");
        StructureRecord house = houseAround(helper, UUID.randomUUID(), level);

        StructureRegionManager.registerStructure(house);
        try {
            ManagedDepositExtraction.Result result = ManagedDepositExtraction.extract(
                    level, absolute, stranger, ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3));
            if (result != ManagedDepositExtraction.Result.PROTECTED) {
                throw new GameTestAssertException(
                        "the refusal was " + result + ", which tells a stranger to fetch a shovel");
            }
        } finally {
            StructureRegionManager.unregisterStructure(house);
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */

    private static BlockPos placeBed(GameTestHelper helper, net.minecraft.world.level.block.Block bed) {
        helper.setBlock(BED, bed);
        return helper.absolutePos(BED);
    }

    /** A registered house whose region contains the bed, owned by whoever is named. */
    private static StructureRecord houseAround(GameTestHelper helper, UUID owner, ServerLevel level) {
        AABB box = new AABB(helper.absolutePos(new BlockPos(0, 0, 0)))
                .minmax(new AABB(helper.absolutePos(new BlockPos(6, 5, 6))));
        return new StructureRecord(owner, box, box, UUID.randomUUID(),
                "small", "SMALL_BRICK", null, 0, level.dimension());
    }

    private static ItemStack shovel() {
        return ToolRegistry.createShovel(UOMetalToolMaterial.IRON, 3);
    }

    private static ServerPlayer player(ServerLevel level, String name) {
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return player;
    }
}
