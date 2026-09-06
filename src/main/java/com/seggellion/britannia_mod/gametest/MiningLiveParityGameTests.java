package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.mining.MiningSkill;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.skill.SkillManager;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Parity with what a real person actually does on a live server.
 *
 * <h2>Why this class exists</h2>
 * The owner reported "Mining 0.0 can still break Valorite" from a live client while the entire
 * GameTest suite insisted it could not. Both were telling the truth. Every existing suite builds a
 * <em>permission-0</em> player -- {@code ManagedResourceTestPlayers.survival} and
 * {@code makeMockServerPlayerInLevel} both do -- because the known harness trap was creative mode.
 * So nobody ever tested the one actor an owner testing their own server always is: an
 * <b>operator standing in survival</b>.
 *
 * <p>{@code MiningBreakGate} granted {@code APPROVED_BYPASS} to
 * {@code isAdministrator(creative, permissionLevel >= 2)}, so op alone waved the ladder aside. That
 * is the wrong axis -- op is an administrative capability, the ladder is gameplay progression --
 * and it was invisible to a suite in which no player is ever op. These tests make the operator a
 * first-class subject so that blind spot cannot reopen.
 *
 * <p>The second half locks the reward contract: a legitimate managed deposit must yield the
 * canonical {@link PurityOreItem} and must <b>never</b> hand back its own {@code BlockItem}, which
 * is what vanilla loot would produce if the managed handler ever stopped intercepting the break.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class MiningLiveParityGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BATCH = "mining_live_parity";

    /** Valorite's catalogue requirement; the ladder's top rung. */
    private static final float VALORITE_REQUIREMENT = 99.0f;

    /**
     * At or above Valorite's MaxSkill (59 + 80), where the extraction check is a certainty.
     * Qualifying at 99 only buys a coin toss, which is authentic RunUO and useless for a
     * deterministic assertion.
     */
    private static final float VALORITE_CERTAIN = 139.0f;

    private MiningLiveParityGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /**
     * A survival player who also holds operator permission -- the owner's own account.
     *
     * <p>Permission comes from the server's real op list, so {@code hasPermissions(2)} answers the
     * way it does in production rather than being stubbed.
     */
    private static ServerPlayer survivalOperator(GameTestHelper helper, String name, float mining) {
        ServerPlayer player = ManagedResourceTestPlayers.survival(helper.getLevel(), name);
        grantOperator(player, helper);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ToolRegistry.PICKAXE.get()));
        SkillManager.applyConfirmedValue(player, MiningSkill.SKILL_ID, mining);
        return player;
    }

    /**
     * Makes this player a real operator, the hard way.
     *
     * <p>{@code PlayerList.op} cannot be used: it stamps the entry with
     * {@code server.getOperatorUserPermissionLevel()}, and {@code GameTestServer} hard-codes that
     * to <b>0</b>. So inside a GameTest the ordinary way of making someone an operator silently
     * produces a permission-0 player. That is not a detail -- it is the structural reason the
     * operator bypass survived a 890-test suite: the harness makes the buggy actor unrepresentable
     * unless you go around it.
     *
     * <p>{@code ServerPlayer.getPermissionLevel()} reads
     * {@code server.getProfilePermissions(profile)}, which returns the op-list entry's OWN level,
     * so writing an entry at level 2 directly gives a genuinely op'd player.
     */
    private static void grantOperator(ServerPlayer player, GameTestHelper helper) {
        helper.getLevel().getServer().getPlayerList().getOps()
                .add(new ServerOpListEntry(player.getGameProfile(), 2, false));
    }

    private static ServerPlayer ordinaryMiner(GameTestHelper helper, String name, float mining) {
        ServerPlayer player = ManagedResourceTestPlayers.survival(helper.getLevel(), name);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ToolRegistry.PICKAXE.get()));
        SkillManager.applyConfirmedValue(player, MiningSkill.SKILL_ID, mining);
        return player;
    }

    private static List<ItemEntity> dropsNear(GameTestHelper helper, BlockPos absolute) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(3.0));
    }

    private static boolean hasRestoreRecord(ServerLevel level, BlockPos absolute) {
        return BrokenBlockDataStorage.get(level).getBrokenBlocks().containsKey(absolute);
    }

    // ------------------------------------------------- the reported live defect, now locked shut

    /**
     * The exact live report: Mining 0.0, holding op, in survival, swinging the correct pickaxe at a
     * genuine Valorite deposit. The block must survive.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void aSurvivalOperatorAtZeroMiningCannotBreakValorite(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.VALORITE_ORE.get());

        ServerPlayer operator = survivalOperator(helper, "live-op-zero", 0.0f);
        check(operator.hasPermissions(2), "fixture must actually hold operator permission");

        operator.gameMode.destroyBlock(absolute);

        helper.assertBlockPresent(BlockRegistry.VALORITE_ORE.get(), relative);
        check(dropsNear(helper, absolute).isEmpty(),
                "a denied operator break must drop nothing at all");
        check(!hasRestoreRecord(level, absolute),
                "a denied break must not enrol restoration debt");
        check(SkillManager.getSkill(operator, MiningSkill.SKILL_ID) == 0.0f,
                "a denied break must not train Mining");
        helper.succeed();
    }

    /** The same operator, now genuinely qualified, is served normally. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void aSurvivalOperatorAtTheRequirementMinesNormally(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.VALORITE_ORE.get());

        ServerPlayer operator = survivalOperator(helper, "live-op-master", VALORITE_CERTAIN);
        operator.gameMode.destroyBlock(absolute);

        helper.assertBlockNotPresent(BlockRegistry.VALORITE_ORE.get(), relative);
        long ore = dropsNear(helper, absolute).stream()
                .filter(entity -> entity.getItem().getItem() instanceof PurityOreItem).count();
        check(ore == 1, "a qualified operator must receive exactly one ore, got " + ore);
        helper.succeed();
    }

    // ----------------------------------------- the reward contract: ore item, never the BlockItem

    /**
     * The hard invariant. A legitimate managed Valorite deposit yields the canonical ore item
     * carrying its material and purity, and never its own {@code valorite_ore} BlockItem -- which
     * is exactly what vanilla loot would hand over if the managed handler stopped intercepting.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void aManagedDepositYieldsOreAndNeverItsOwnBlockItem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.VALORITE_ORE.get());

        ServerPlayer miner = ordinaryMiner(helper, "reward-contract", VALORITE_CERTAIN);
        miner.gameMode.destroyBlock(absolute);

        List<ItemEntity> drops = dropsNear(helper, absolute);
        boolean blockItem = drops.stream().anyMatch(entity ->
                entity.getItem().getItem() == BlockRegistry.VALORITE_ORE.get().asItem());
        check(!blockItem,
                "a managed deposit must NEVER drop its own BlockItem as the mining reward");

        List<ItemEntity> ore = drops.stream()
                .filter(entity -> entity.getItem().getItem() instanceof PurityOreItem).toList();
        check(ore.size() == 1, "exactly one canonical ore item expected, got " + ore.size());

        ItemStack stack = ore.get(0).getItem();
        PurityOreItem item = (PurityOreItem) stack.getItem();
        check("valorite ore".equalsIgnoreCase(item.getOreType(stack)),
                "ore must carry its material identity, got " + item.getOreType(stack));
        int purity = item.getPurity(stack);
        check(purity >= 1 && purity <= 5, "purity must be graded 1-5, got " + purity);

        // The whole lifecycle, committed exactly once.
        helper.assertBlockPresent(Blocks.AIR, relative);
        check(hasRestoreRecord(level, absolute),
                "a committed extraction must enrol exactly one restoration debt");
        helper.succeed();
    }

    /**
     * Wrong tool, right skill, real deposit: refused, and the cell is still standing. Before the
     * managed-cell guard this fell through to the vanilla lifecycle and, with no loot table on
     * these blocks, deleted the deposit outright.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void aVanillaToolNeitherMinesNorDestroysAManagedDeposit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.VALORITE_ORE.get());

        ServerPlayer miner = ordinaryMiner(helper, "wrong-tool-live", VALORITE_REQUIREMENT);
        miner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));

        miner.gameMode.destroyBlock(absolute);

        helper.assertBlockPresent(BlockRegistry.VALORITE_ORE.get(), relative);
        check(dropsNear(helper, absolute).isEmpty(), "a wrong-tool break must yield nothing");
        check(!hasRestoreRecord(level, absolute),
                "a wrong-tool refusal must not enrol restoration debt");
        helper.succeed();
    }
}
