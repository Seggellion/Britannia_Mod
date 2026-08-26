package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.item.GradeStoneItem;
import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.mining.MiningProvenance;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The three owner-approved Mining invariants, exercised through the real break pipeline.
 *
 * <ol>
 *   <li><b>Creative-placed resource blocks are real resource nodes.</b> Creative placement is how an
 *       administrator manually sites a resource; the result must be a first-class node with the
 *       same gate, yield, depletion and restoration as a Rails-authored deposit. Survival placement
 *       stays construction, which is what stops trivially farmable stone becoming an infinite
 *       source of graded stone and Mining progression.</li>
 *   <li><b>Using the skill and succeeding with it are separate events.</b> A qualified attempt
 *       rolls once against the resource's MinSkill/MaxSkill window; failing that roll can still
 *       train, succeeding it need not. Below the hard requirement there is no roll at all, so an
 *       unqualified resource teaches nothing -- RunUO's own short-circuit.</li>
 *   <li><b>Hard requirements are absolute.</b> No game mode and no permission level may break a
 *       resource the miner is not skilled enough for.</li>
 * </ol>
 *
 * <p>Every actor here is constructed deliberately, including permission level. The previous
 * operator bypass survived a 890-test suite purely because {@code GameTestServer} hard-codes
 * {@code getOperatorUserPermissionLevel()} to 0, so the buggy actor could not be expressed.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class MiningApprovedInvariantsGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BATCH = "mining_approved_invariants";
    private static final float VALORITE_REQUIREMENT = 99.0f;

    /** At or above Valorite's MaxSkill (59 + 80): the one skill where extraction is certain. */
    private static final float VALORITE_CERTAIN = 139.0f;

    /** At or above ordinary stone's MaxSkill (0/0/100). */
    private static final float STONE_CERTAIN = 100.0f;

    private MiningApprovedInvariantsGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /**
     * A genuinely op'd player at the requested level.
     *
     * <p>{@code PlayerList.op} is unusable here: it stamps the entry with
     * {@code server.getOperatorUserPermissionLevel()}, which {@code GameTestServer} hard-codes to
     * 0. Writing the op-list entry directly works because {@code ServerPlayer.getPermissionLevel()}
     * reads {@code server.getProfilePermissions(profile)}, which returns the entry's own level.
     */
    private static void grantOperator(GameTestHelper helper, ServerPlayer player, int level) {
        helper.getLevel().getServer().getPlayerList().getOps()
                .add(new ServerOpListEntry(player.getGameProfile(), level, false));
    }

    private static ServerPlayer miner(
            GameTestHelper helper, String name, float mining, GameType mode, int opLevel) {
        ServerPlayer player = ManagedResourceTestPlayers.survival(helper.getLevel(), name);
        if (opLevel > 0) {
            grantOperator(helper, player, opLevel);
        }
        player.setGameMode(mode);
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

    /** Everything a refused attempt must leave untouched, asserted in one place. */
    private static void assertNothingHappened(
            GameTestHelper helper, BlockPos relative, BlockPos absolute, String who) {
        helper.assertBlockPresent(BlockRegistry.VALORITE_ORE.get(), relative);
        check(dropsNear(helper, absolute).isEmpty(), who + ": a refused break must drop nothing");
        check(!hasRestoreRecord(helper.getLevel(), absolute),
                who + ": a refused break must not enrol restoration debt");
    }

    // ------------------------------------------------------- 3. the hard requirement is absolute

    /** Creative, op level 4, Mining 0: still cannot take Valorite. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void creativeOperatorAtZeroMiningCannotBreakValorite(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.VALORITE_ORE.get());

        ServerPlayer admin = miner(helper, "inv-creative-op", 0.0f, GameType.CREATIVE, 4);
        check(admin.hasPermissions(4), "fixture must actually hold op level 4");

        admin.gameMode.destroyBlock(absolute);

        assertNothingHappened(helper, relative, absolute, "creative op");
        helper.succeed();
    }

    /** Adventure, Mining 0: same answer. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void anAdventureMinerAtZeroMiningCannotBreakValorite(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.VALORITE_ORE.get());

        ServerPlayer player = miner(helper, "inv-adventure", 0.0f, GameType.ADVENTURE, 0);
        player.gameMode.destroyBlock(absolute);

        assertNothingHappened(helper, relative, absolute, "adventure");
        helper.succeed();
    }

    // -------------------------------------- 1. creative placement makes a real resource node

    /**
     * The approved siting workflow end to end: an administrator places Valorite in creative, and a
     * qualified survival miner then works it as an ordinary deposit -- canonical ore, real
     * depletion, real restoration debt, and never the block item back.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void creativeSitedValoriteIsAFirstClassResourceNode(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);

        // Sited by an administrator rather than by Rails.
        helper.setBlock(relative, BlockRegistry.VALORITE_ORE.get());
        check(!MiningProvenance.isPlayerPlaced(level, absolute),
                "creative siting must not mark the node as construction");

        ServerPlayer master = miner(helper, "inv-sited-master", VALORITE_CERTAIN,
                GameType.SURVIVAL, 0);
        master.gameMode.destroyBlock(absolute);

        helper.assertBlockNotPresent(BlockRegistry.VALORITE_ORE.get(), relative);
        List<ItemEntity> drops = dropsNear(helper, absolute);
        check(drops.stream().noneMatch(entity ->
                        entity.getItem().getItem() == BlockRegistry.VALORITE_ORE.get().asItem()),
                "an admin-sited node must never hand back its own block item");
        check(drops.stream().filter(entity ->
                        entity.getItem().getItem() instanceof PurityOreItem).count() == 1,
                "an admin-sited node must yield exactly one canonical ore");
        check(hasRestoreRecord(level, absolute),
                "an admin-sited node must enrol restoration exactly like a Rails deposit");
        helper.succeed();
    }

    /**
     * And the same node still refuses an unqualified miner, which is the point of siting it as a
     * resource rather than as decoration.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void creativeSitedValoriteStillRefusesAnUnqualifiedMiner(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.VALORITE_ORE.get());

        ServerPlayer novice = miner(helper, "inv-sited-novice", 0.0f, GameType.SURVIVAL, 0);
        novice.gameMode.destroyBlock(absolute);

        assertNothingHappened(helper, relative, absolute, "novice on sited node");
        helper.succeed();
    }

    /**
     * Survival placement stays construction. This is the anti-duplication rule: ordinary stone is
     * trivially farmable, so a survival-placed block must never become a resource node that pays
     * out graded stone and Mining progression.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void survivalPlacedStoneIsConstructionNotAResourceNode(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, Blocks.STONE);

        // Stand in for the place event the same way production does.
        MiningProvenance.markPlayerPlaced(level, absolute);

        ServerPlayer builder = miner(helper, "inv-builder", 0.0f, GameType.SURVIVAL, 0);
        float before = SkillManager.getSkill(builder, MiningSkill.SKILL_ID);
        builder.gameMode.destroyBlock(absolute);

        check(dropsNear(helper, absolute).stream().noneMatch(entity ->
                        entity.getItem().getItem() instanceof GradeStoneItem),
                "construction must never yield graded stone");
        check(SkillManager.getSkill(builder, MiningSkill.SKILL_ID) == before,
                "construction must never train Mining");
        check(!hasRestoreRecord(level, absolute),
                "construction must never enrol restoration debt");
        helper.succeed();
    }

    // --------------------------------------------- 2. stone is a first-class Mining resource

    /** Stone pays the canonical graded item, with a genuinely random grade, and restores. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void stoneYieldsGradedStoneAndParticipatesInMining(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, Blocks.STONE);

        ServerPlayer quarrier = miner(helper, "inv-quarrier", STONE_CERTAIN, GameType.SURVIVAL, 0);
        quarrier.gameMode.destroyBlock(absolute);

        List<ItemEntity> graded = dropsNear(helper, absolute).stream()
                .filter(entity -> entity.getItem().getItem() instanceof GradeStoneItem).toList();
        check(graded.size() == 1, "stone must yield exactly one graded stone, got " + graded.size());

        ItemStack stack = graded.get(0).getItem();
        GradeStoneItem item = (GradeStoneItem) stack.getItem();
        int grade = item.getGradeValue(stack);
        check(grade >= 1 && grade <= 5, "grade must stay in its random 1-5 band, got " + grade);
        check(hasRestoreRecord(level, absolute), "stone extraction must enrol restoration");
        helper.succeed();
    }

    // ----------------------------------------- 2. trying trains, but only within reach

    /**
     * The approved rule, and a reversal of what this class asserted a revision ago: below the hard
     * requirement a resource teaches <b>nothing</b>.
     *
     * <p>This is RunUO's own short-circuit. {@code FinishHarvesting} reads
     * {@code skillBase >= resource.ReqSkill && from.CheckSkill(...)}, so an unqualified miner never
     * reaches {@code CheckSkill} and the resource cannot train them. An earlier revision granted a
     * tapered gain below the requirement; it is gone, along with the taper that made it safe. You
     * train on what you can actually work.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void belowTheRequirementAResourceTeachesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.VALORITE_ORE.get());

        // 98 is one point short of Valorite's requirement and deep inside its 59-139 window, so
        // nothing but the hard gate can be responsible for the refusal.
        ServerPlayer nearlyThere = miner(helper, "inv-98", 98.0f, GameType.SURVIVAL, 0);
        for (int i = 0; i < 50; i++) {
            MiningSkill.checkMiningAttempt(nearlyThere, level.getBlockState(absolute),
                    absolute.offset(i * 16, 0, 0));
        }
        check(SkillManager.getSkill(nearlyThere, MiningSkill.SKILL_ID) == 98.0f,
                "an unqualified miner must not train on the resource that refused them");
        helper.assertBlockPresent(BlockRegistry.VALORITE_ORE.get(), relative);
        helper.succeed();
    }

    /** The same rule for a beginner, across game modes and permission levels. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void beginnersLearnNothingFromValoriteInAnyMode(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.VALORITE_ORE.get());

        ServerPlayer survival = miner(helper, "inv-zero-surv", 0.0f, GameType.SURVIVAL, 0);
        ServerPlayer creativeOp = miner(helper, "inv-zero-cop", 0.0f, GameType.CREATIVE, 4);
        for (int i = 0; i < 40; i++) {
            MiningSkill.checkMiningAttempt(survival, level.getBlockState(absolute),
                    absolute.offset(i * 16, 0, 0));
            MiningSkill.checkMiningAttempt(creativeOp, level.getBlockState(absolute),
                    absolute.offset(i * 16, 0, 32));
        }
        check(SkillManager.getSkill(survival, MiningSkill.SKILL_ID) == 0.0f,
                "survival beginner trained on Valorite");
        check(SkillManager.getSkill(creativeOp, MiningSkill.SKILL_ID) == 0.0f,
                "creative op beginner trained on Valorite");
        helper.assertBlockPresent(BlockRegistry.VALORITE_ORE.get(), relative);
        helper.succeed();
    }

    /**
     * The reported live defect, through the production break path.
     *
     * <p>A miner at Mining 0.4 with the project pickaxe saw ordinary stone break, reappear, and
     * report "you fail to extract anything usable" -- because stone is 0/0/100 and the RunUO roll
     * gave them {@code (0.4 - 0) / 100}. Stone is the terrain a player tunnels through, not a vein
     * that may refuse them, so a qualified miner always excavates it.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void abeginnerExcavatesOrdinaryStoneAndIsPaidForIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, Blocks.STONE);

        ServerPlayer novice = miner(helper, "inv-dig-04", 0.4f, GameType.SURVIVAL, 0);
        novice.gameMode.destroyBlock(absolute);

        helper.assertBlockNotPresent(Blocks.STONE, relative);

        List<ItemEntity> drops = dropsNear(helper, absolute);
        List<ItemEntity> graded = drops.stream()
                .filter(entity -> entity.getItem().getItem() instanceof GradeStoneItem).toList();
        check(graded.size() == 1,
                "a qualified excavation must pay exactly one graded stone, got " + graded.size());
        check(drops.stream().noneMatch(entity ->
                        entity.getItem().getItem() == Blocks.STONE.asItem()
                                || entity.getItem().getItem() == Blocks.COBBLESTONE.asItem()),
                "the vanilla block item must never accompany the canonical reward");

        ItemStack stack = graded.get(0).getItem();
        int grade = ((GradeStoneItem) stack.getItem()).getGradeValue(stack);
        check(grade >= 1 && grade <= 5, "grade must stay in its random 1-5 band, got " + grade);

        check(hasRestoreRecord(level, absolute),
                "excavation must enrol exactly one restoration obligation");
        helper.succeed();
    }

    /** Mining 0 digs too: ordinary stone requires 0, so every player qualifies from the start. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void miningZeroStillExcavatesOrdinaryStone(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, Blocks.STONE);

        ServerPlayer novice = miner(helper, "inv-dig-zero", 0.0f, GameType.SURVIVAL, 0);
        novice.gameMode.destroyBlock(absolute);

        helper.assertBlockNotPresent(Blocks.STONE, relative);
        check(dropsNear(helper, absolute).stream()
                        .anyMatch(entity -> entity.getItem().getItem() instanceof GradeStoneItem),
                "Mining 0 must still be paid for the stone it removes");
        helper.succeed();
    }

    /**
     * Excavation does not depend on the gain roll. A miner far above the beginner ramp and at the
     * skill cap gains nothing from ordinary stone, and must still dig it out.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void stoneExcavatesEvenWhenTheGainRollGivesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, Blocks.STONE);

        // At 100 the gain chance is zero by construction, so this isolates extraction from gain.
        ServerPlayer master = miner(helper, "inv-dig-capped", 100.0f, GameType.SURVIVAL, 0);
        MiningSkill.AttemptResult result =
                MiningSkill.checkMiningAttempt(master, level.getBlockState(absolute), absolute);

        check(result.extracted(), "a capped miner must still excavate stone");
        check(result.skillGained() == 0.0f, "and must gain nothing from it");
        helper.succeed();
    }

    /**
     * The hard gate survives the correction: deterministic does not mean ungated. Volcanic rock
     * requires 45, and creative plus op level 4 does not change that.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void ahigherTierStoneStillRefusesBelowItsGate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.VOLCANIC_ROCK.get());

        ServerPlayer admin = miner(helper, "inv-volcanic-low", 44.9f, GameType.CREATIVE, 4);
        admin.gameMode.destroyBlock(absolute);

        helper.assertBlockPresent(BlockRegistry.VOLCANIC_ROCK.get(), relative);
        check(dropsNear(helper, absolute).isEmpty(), "a refused excavation must drop nothing");
        check(!hasRestoreRecord(level, absolute),
                "a refused excavation must not enrol restoration debt");
        check(SkillManager.getSkill(admin, MiningSkill.SKILL_ID) == 44.9f,
                "an unqualified resource must not train the miner");
        helper.succeed();
    }

    /** And at exactly its requirement it excavates, with no roll standing in the way. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void ahigherTierStoneExcavatesExactlyAtItsGate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.VOLCANIC_ROCK.get());

        ServerPlayer quarrier = miner(helper, "inv-volcanic-at", 45.0f, GameType.SURVIVAL, 0);
        quarrier.gameMode.destroyBlock(absolute);

        helper.assertBlockNotPresent(BlockRegistry.VOLCANIC_ROCK.get(), relative);
        check(dropsNear(helper, absolute).stream()
                        .anyMatch(entity -> entity.getItem().getItem() instanceof GradeStoneItem),
                "volcanic rock must pay graded stone at exactly its requirement");
        check(hasRestoreRecord(level, absolute), "and enrol its restoration");
        helper.succeed();
    }

    /**
     * At or above MaxSkill the check is a certainty with no RNG involved -- and a miner at the cap
     * has nothing left to learn, which proves success and gain really are independent outcomes.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void atMaxSkillExtractionIsCertainAndTeachesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, Blocks.STONE);

        ServerPlayer master = miner(helper, "inv-capped", 100.0f, GameType.SURVIVAL, 0);
        MiningSkill.AttemptResult result =
                MiningSkill.checkMiningAttempt(master, level.getBlockState(absolute), absolute);

        check(result.extracted(), "at or above MaxSkill extraction must be certain");
        check(result.skillGained() == 0.0f,
                "a capped miner gains nothing, so success does not imply gain");
        helper.succeed();
    }

    /** Structurally invalid activations never train, whatever they are aimed at. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void invalidActivationsNeverTrainMining(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);

        // Wrong tool on a real resource.
        helper.setBlock(relative, BlockRegistry.VALORITE_ORE.get());
        ServerPlayer wrongTool = miner(helper, "inv-wrongtool", 59.0f, GameType.SURVIVAL, 0);
        wrongTool.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
        wrongTool.gameMode.destroyBlock(absolute);
        check(SkillManager.getSkill(wrongTool, MiningSkill.SKILL_ID) == 59.0f,
                "the wrong tool must never train Mining");
        helper.assertBlockPresent(BlockRegistry.VALORITE_ORE.get(), relative);

        // A block that is not a Mining resource at all.
        BlockPos dirtRelative = new BlockPos(3, 1, 1);
        helper.setBlock(dirtRelative, Blocks.DIRT);
        ServerPlayer digger = miner(helper, "inv-dirt", 59.0f, GameType.SURVIVAL, 0);
        digger.gameMode.destroyBlock(helper.absolutePos(dirtRelative));
        check(SkillManager.getSkill(digger, MiningSkill.SKILL_ID) == 59.0f,
                "clicking dirt must never train Mining");
        helper.succeed();
    }
}
