package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.event.CustomBlockBreakHandler;
import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.mining.MiningProvenance;
import com.seggellion.britannia_mod.mining.MiningSkill;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.skill.SkillManager;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * End-to-end protection for the Mining half of the progression loop: dig through the real event
 * pipeline, and prove the resource is either properly extracted or properly left alone — never
 * quietly destroyed for nothing.
 *
 * <h2>What each test closes</h2>
 * <ul>
 *   <li><b>Skill gain through the production path.</b> The suite already proved
 *       {@code MiningSkill.awardForBreak} trains Mining when <em>called directly</em>
 *       ({@code MiningSkillGameTests.repeatedStoneActivationsTrainMining}), and that one real break
 *       awards <em>at most</em> one activation — an assertion that also passes when the answer is
 *       zero. Nothing asserted that a real dig awards anything at all, so the wiring between the
 *       break pipeline and the skill engine could be cut without a single test going red. That is
 *       the gap this class closes first.</li>
 *   <li><b>Managed cells are never annihilated.</b> These blocks ship no loot table, so any break
 *       that escapes the managed pipeline into the vanilla lifecycle deletes a sited deposit
 *       outright: no yield, and no restoration debt, so the six-hour restoration never brings it
 *       back either. The extraction handler is the authority for removing a managed cell, and a
 *       break it cannot commit must not happen at all.</li>
 *   <li><b>Player-placed construction is handed back.</b> Milestone 7 rules that a mineable the
 *       player placed breaks "with ordinary vanilla behaviour". For the project's own ore blocks
 *       that silently evaluated to annihilation, which in game reads exactly like the reported
 *       "I broke it and got nothing".</li>
 * </ul>
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class MiningProgressionPipelineGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BATCH = "mining_progression_pipeline";

    /**
     * Activations driven through the real dig path when proving Mining trains.
     *
     * <p>Stone at Mining 0.0 rolls {@code BASE_CHANCE} exactly (0.4252): the material factor is 1.0
     * because the miner has not outgrown the resource, and the remaining-skill term is 1.0 at zero.
     * Forty independent activations therefore all fail with probability {@code 0.5748^40}, about
     * 1.6e-10 — the same "deterministic in practice" argument the existing direct-call test makes,
     * now applied to the production entry point.
     */
    private static final int TRAINING_ACTIVATIONS = 40;

    private MiningProgressionPipelineGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** An ordinary named survival miner holding the project pickaxe. */
    private static ServerPlayer miner(GameTestHelper helper, String name, float miningSkill) {
        ServerPlayer player = ManagedResourceTestPlayers.survival(helper.getLevel(), name);
        player.getInventory().clearContent();
        giveFreshPickaxe(player);
        SkillManager.applyConfirmedValue(player, MiningSkill.SKILL_ID, miningSkill);
        return player;
    }

    /**
     * A pickaxe with full durability. Every committed extraction charges one point, and a tool
     * that breaks mid-run stops being an authorized extraction tool — which would silently end the
     * training loop and make this test measure tool durability instead of skill gain.
     */
    private static void giveFreshPickaxe(ServerPlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ToolRegistry.PICKAXE.get()));
    }

    private static float skillOf(ServerPlayer player) {
        return SkillManager.getSkill(player, MiningSkill.SKILL_ID);
    }

    private static List<ItemEntity> dropsNear(GameTestHelper helper, BlockPos absolute) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(3.0));
    }

    // ------------------------------------------------------- 1. the loop actually trains Mining

    /**
     * The headline invariant: legitimate digging, through the production entry point, moves the
     * Mining skill.
     *
     * <p>Drives {@code ServerPlayerGameMode.destroyBlock}, which raises the same
     * {@code BlockEvent.BreakEvent} a real swing raises, so the gate, the extraction handler, the
     * award and the skill engine are all the shipped ones on the shipped bus. Two alternating
     * positions keep every activation distinct for the (player, position, tick) duplicate guard,
     * which compares only against the immediately preceding activation.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void legitimateDiggingTrainsMiningThroughTheRealBreakPipeline(GameTestHelper helper) {
        ServerPlayer player = miner(helper, "miner-training", 0.0f);
        BlockPos firstRelative = new BlockPos(1, 1, 1);
        BlockPos secondRelative = new BlockPos(3, 1, 1);

        for (int i = 0; i < TRAINING_ACTIVATIONS; i++) {
            BlockPos relative = (i % 2 == 0) ? firstRelative : secondRelative;
            helper.setBlock(relative, Blocks.STONE);
            giveFreshPickaxe(player);
            player.gameMode.destroyBlock(helper.absolutePos(relative));
        }

        float gained = skillOf(player);
        check(gained > 0.0f,
                TRAINING_ACTIVATIONS + " real managed breaks must train Mining, got " + gained
                        + " -- the break pipeline is no longer reaching the skill engine");
        check(gained <= TRAINING_ACTIVATIONS * MiningSkill.GAIN_UNIT,
                "gain must never exceed one 0.1 unit per activation, got " + gained);
        helper.succeed();
    }

    /**
     * A committed extraction still yields its resource and exactly one skill evaluation, at a tier
     * the miner has genuinely earned. Guards the "no ore is awarded" half of the report.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void aQualifiedMinerReceivesTheOreExactlyOnce(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.SILVER_ORE.get());
        ServerPlayer player = miner(helper, "miner-qualified", 100.0f);

        player.gameMode.destroyBlock(absolute);

        helper.assertBlockNotPresent(BlockRegistry.SILVER_ORE.get(), relative);
        List<ItemEntity> ore = dropsNear(helper, absolute).stream()
                .filter(entity -> entity.getItem().getItem() instanceof PurityOreItem)
                .toList();
        check(ore.size() == 1, "one managed silver break must yield exactly one ore drop, got " + ore.size());
        check(ore.get(0).getItem().getCount() == 1,
                "the ore drop must be a single item, got " + ore.get(0).getItem().getCount());
        helper.succeed();
    }

    // -------------------------------------------- 2. a managed cell is never silently annihilated

    /**
     * A managed cell reached with a tool that cannot work it is left standing, whatever let the
     * attempt get this far.
     *
     * <p>Invoked directly rather than through the bus on purpose. An ordinary player never reaches
     * the extraction handler with the wrong tool — the gate answers WRONG_TOOL and cancels at HIGH,
     * and a cancelled event reaches no later listener — so the case that matters is the one where
     * something upstream <em>permitted</em> the break: an operator, whose bypass deliberately
     * outranks the tool check so a misplaced block can be removed. Before this guard the event fell
     * through to the vanilla lifecycle and, with no loot table on these blocks, deleted a planned
     * deposit that dropped nothing and filed no restoration debt.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void aManagedCellIsNotAnnihilatedByAnUnauthorizedTool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.VALORITE_ORE.get());
        BlockState state = level.getBlockState(absolute);

        ServerPlayer player = ManagedResourceTestPlayers.survival(level, "miner-wrong-tool");
        player.getInventory().clearContent();
        // A vanilla pickaxe: not the project's extraction tool for this resource.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        SkillManager.applyConfirmedValue(player, MiningSkill.SKILL_ID, 100.0f);

        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, absolute, state, player);
        new CustomBlockBreakHandler().onBlockBreak(event);

        check(event.isCanceled(),
                "a managed cell the extraction handler cannot commit must not fall through to the "
                        + "vanilla break lifecycle, where it would be destroyed for nothing");
        helper.assertBlockPresent(BlockRegistry.VALORITE_ORE.get(), relative);
        check(dropsNear(helper, absolute).isEmpty(), "a refused break must drop nothing");
        helper.succeed();
    }

    // ------------------------------------------------ 3. placed construction is returned, not lost

    /**
     * A mineable the player placed themselves comes back when they break it.
     *
     * <p>The place-break loop stays closed — no purity ore, no restoration debt, no Mining award —
     * because handing back the block the player already owned is not an extraction. What changes is
     * only that the block is no longer silently deleted: the project's ore blocks ship no loot
     * table, so "ordinary vanilla behaviour" used to mean annihilation.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void placedConstructionIsHandedBackAndTrainsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.VALORITE_ORE.get());
        MiningProvenance.markPlayerPlaced(level, absolute);

        ServerPlayer player = miner(helper, "miner-construction", 0.0f);
        player.gameMode.destroyBlock(absolute);

        List<ItemEntity> drops = dropsNear(helper, absolute);
        boolean returnedTheBlock = drops.stream().anyMatch(entity ->
                entity.getItem().getItem() == BlockRegistry.VALORITE_ORE.get().asItem());
        boolean mintedOre = drops.stream().anyMatch(entity ->
                entity.getItem().getItem() instanceof PurityOreItem);

        check(returnedTheBlock,
                "breaking your own placed block must hand it back, not delete it; drops were " + drops);
        check(!mintedOre, "placed construction must never mint purity ore -- that is the place-break loop");
        check(skillOf(player) == 0.0f,
                "placed construction must never train Mining, got " + skillOf(player));
        helper.succeed();
    }

    /**
     * The same rule for a vanilla block in the catalogue, which already had a working loot table:
     * it must keep dropping exactly what vanilla drops, and must not gain a second drop from the
     * hand-back path.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void placedVanillaConstructionIsNotDoubleDropped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, Blocks.IRON_ORE);
        MiningProvenance.markPlayerPlaced(level, absolute);

        ServerPlayer player = miner(helper, "miner-vanilla-construction", 0.0f);
        player.gameMode.destroyBlock(absolute);

        long ironDrops = dropsNear(helper, absolute).stream()
                .filter(entity -> entity.getItem().getItem() == Items.RAW_IRON
                        || entity.getItem().getItem() == Blocks.IRON_ORE.asItem())
                .count();
        check(ironDrops <= 1,
                "a vanilla block with its own loot table must not also be handed back, got "
                        + ironDrops + " drops");
        check(skillOf(player) == 0.0f, "placed construction must never train Mining");
        helper.succeed();
    }
}
