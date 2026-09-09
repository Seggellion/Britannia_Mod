package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.deposit.ManagedDepositExtraction;
import com.seggellion.britannia_mod.deposit.ManagedDeposits;
import com.seggellion.britannia_mod.item.GradeStoneItem;
import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.mining.MiningBreakGate;
import com.seggellion.britannia_mod.mining.MiningSkill;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.extraction.ManagedExtractionPolicy;
import com.seggellion.britannia_mod.skill.SkillManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.function.Consumer;

/**
 * OreVein milestone 6: what an enchantment, a machine or an administrator gets out of a managed
 * resource, proved by doing it rather than by reading the code.
 *
 * <h2>What these are actually testing</h2>
 * Three of the four policies here hold for structural reasons rather than because a branch enforces
 * them — the yield never consults a loot table, so Fortune has nothing to multiply; the blocks have
 * no loot table and no block item, so Silk Touch has nothing to take. A structural guarantee is the
 * strongest kind right up until somebody adds a loot table "so the block can be picked up in
 * creative", at which point it silently stops being true and nothing fails. So they are pinned by
 * behaviour, at the real event bus, with the real enchantments applied to the real tools.
 *
 * <p>The other two were genuine holes. A fake player is a {@code ServerPlayer}, so the sediment beds
 * — whose only actor check was {@code instanceof ServerPlayer} — were workable by automation. And a
 * creative operator holding a project pickaxe was running the entire managed ore flow: purity ore
 * dropped, restoration debt filed in their name. Both are closed, and both are pinned from the
 * denial side, which is the side that regresses quietly.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class ManagedExtractionPolicyGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos NODE = new BlockPos(1, 1, 1);

    private ManagedExtractionPolicyGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Rig                                                                */
    /* ------------------------------------------------------------------ */

    /** One managed resource under test, named the way the report names them. */
    private record Subject(String label, Block block, ItemStack tool, boolean depositCell,
                           Consumer<List<ItemStack>> yieldCheck) {
    }

    private static ItemStack pickaxe() {
        return ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3);
    }

    private static ItemStack shovel() {
        return ToolRegistry.createShovel(UOMetalToolMaterial.IRON, 3);
    }

    /** Clay: the sediment this whole milestone came out of. */
    private static Subject clay() {
        return new Subject("clay", BlockRegistry.CLAY_DEPOSIT.get(), shovel(), true, drops -> {
            check(drops.size() == 1, "clay yielded " + drops.size() + " stacks, not one: " + describe(drops));
            ItemStack only = drops.get(0);
            check(only.is(Items.CLAY_BALL), "clay yielded " + describe(drops) + ", not a clay ball");
            check(only.getCount() == 1, "clay yielded " + only.getCount() + " balls, not the configured one");
        });
    }

    /** Silica: the same shovel, a different tag, a different regeneration. */
    private static Subject silica() {
        return new Subject("silica", BlockRegistry.SILICA_SAND_DEPOSIT.get(), shovel(), true, drops -> {
            check(drops.size() == 1, "silica yielded " + drops.size() + " stacks, not one: " + describe(drops));
            check(drops.get(0).getCount() == 1,
                    "silica yielded " + drops.get(0).getCount() + " items, not the configured one");
            check(!drops.get(0).is(Items.SAND) && !drops.get(0).is(Items.CLAY_BALL),
                    "silica yielded the wrong material: " + describe(drops));
        });
    }

    /** A managed ore, which is the purity output mode. */
    private static Subject silverOre() {
        return new Subject("silver ore", BlockRegistry.SILVER_ORE.get(), pickaxe(), true, drops -> {
            check(drops.size() == 1, "silver yielded " + drops.size() + " stacks, not one: " + describe(drops));
            check(drops.get(0).getItem() instanceof PurityOreItem,
                    "silver yielded " + describe(drops) + ", not a purity ore");
            check(drops.get(0).getCount() == 1, "silver yielded more than one purity ore");
        });
    }

    /** A managed stone, which is the graded output mode. */
    private static Subject gradedStone() {
        return new Subject("stone", Blocks.STONE, pickaxe(), false, drops -> {
            check(drops.size() == 1, "stone yielded " + drops.size() + " stacks, not one: " + describe(drops));
            check(drops.get(0).getItem() instanceof GradeStoneItem,
                    "stone yielded " + describe(drops) + ", not a graded stone");
            check(drops.get(0).getCount() == 1, "stone yielded more than one graded stone");
        });
    }

    private static List<Subject> everyFamily() {
        return List.of(clay(), silica(), silverOre(), gradedStone());
    }

    private static String describe(List<ItemStack> drops) {
        if (drops.isEmpty()) {
            return "nothing";
        }
        StringBuilder text = new StringBuilder();
        for (ItemStack stack : drops) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(stack.getCount()).append('x').append(stack.getItem().getDescriptionId());
        }
        return text.toString();
    }

    private static ServerPlayer miner(GameTestHelper helper, ItemStack tool) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        // Pinned at the ceiling so the probabilistic Mining award cannot fire and make a yield or
        // durability assertion flaky. Skill gain is proved separately, from the denial side, where
        // the answer is deterministic.
        SkillManager.applyConfirmedValue(player, MiningSkill.SKILL_ID, 100.0f);
        return player;
    }

    private static void enchant(ItemStack stack, ServerLevel level, ResourceKey<Enchantment> which, int lvl) {
        Holder<Enchantment> holder = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(which);
        stack.enchant(holder, lvl);
    }

    /** Everything lying on the ground where the resource was, and then cleared away. */
    private static List<ItemStack> takeDrops(ServerLevel level, BlockPos absolute) {
        List<ItemEntity> entities =
                level.getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(3.0D));
        List<ItemStack> stacks = entities.stream().map(ItemEntity::getItem).toList();
        entities.forEach(ItemEntity::discard);
        return stacks;
    }

    private static int debtCount(ServerLevel level) {
        return BrokenBlockDataStorage.get(level).getBrokenBlocks().size();
    }

    private static boolean hasDebtAt(ServerLevel level, BlockPos absolute) {
        return BrokenBlockDataStorage.get(level).getBrokenBlocks().containsKey(absolute);
    }

    /**
     * Break through the real bus, so every registered handler sees the event in its real priority
     * order. Driving the services directly would prove the services correct and say nothing about
     * whether two of them commit the same extraction.
     */
    private static void breakThroughTheEventBus(ServerLevel level, BlockPos absolute, ServerPlayer player) {
        BlockState state = level.getBlockState(absolute);
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, absolute, state, player);
        NeoForge.EVENT_BUS.post(event);
        if (!event.isCanceled()) {
            // What vanilla would then do. If a managed resource ever reaches here, the test wants
            // the vanilla consequences visible rather than hidden.
            level.destroyBlock(absolute, !player.isCreative(), player);
        }
    }

    /** Set the resource up, run one break, and hand back what is on the floor. */
    private static List<ItemStack> extractOnce(GameTestHelper helper, Subject subject, ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, subject.block());
        takeDrops(level, absolute);
        breakThroughTheEventBus(level, absolute, player);
        return takeDrops(level, absolute);
    }

    /* ------------------------------------------------------------------ */
    /*  Fortune                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * Fortune buys nothing anywhere, and costs nothing either.
     *
     * <p>Run over all four families in one test because the assertion is identical and the failure
     * message names the family: four near-identical tests would be four places to forget to add the
     * fifth family.
     */
    @GameTest(template = TEMPLATE)
    public static void fortuneNeverAmplifiesAManagedYield(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);

        for (Subject subject : everyFamily()) {
            ItemStack tool = subject.tool();
            enchant(tool, level, Enchantments.FORTUNE, 3);
            ServerPlayer player = miner(helper, tool);

            int debtBefore = debtCount(level);
            List<ItemStack> drops = extractOnce(helper, subject, player);

            subject.yieldCheck().accept(drops);
            check(level.getBlockState(absolute).isAir(),
                    subject.label() + " was not depleted by a Fortune extraction");
            check(debtCount(level) == debtBefore + 1,
                    subject.label() + " with Fortune filed " + (debtCount(level) - debtBefore)
                            + " restoration debts, not exactly one");
            check(hasDebtAt(level, absolute),
                    subject.label() + " with Fortune filed its debt somewhere other than the worked cell");
            check(player.getMainHandItem().getDamageValue() == 1,
                    subject.label() + " with Fortune cost "
                            + player.getMainHandItem().getDamageValue() + " durability, not exactly one");
            BrokenBlockDataStorage.get(level).remove(absolute);
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Silk Touch                                                         */
    /* ------------------------------------------------------------------ */

    /**
     * Silk Touch cannot turn a managed resource into a portable block.
     *
     * <p>The invariant the owner cares about is duplication and relocation: a deposit that can be
     * carried away is a deposit the economy no longer controls. So this asserts the block item is
     * absent from the drops, not merely that the count was right.
     */
    @GameTest(template = TEMPLATE)
    public static void silkTouchNeverYieldsTheManagedBlockItself(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);

        for (Subject subject : everyFamily()) {
            ItemStack tool = subject.tool();
            enchant(tool, level, Enchantments.SILK_TOUCH, 1);
            ServerPlayer player = miner(helper, tool);

            int debtBefore = debtCount(level);
            List<ItemStack> drops = extractOnce(helper, subject, player);

            ItemStack asItem = new ItemStack(subject.block());
            for (ItemStack dropped : drops) {
                check(asItem.isEmpty() || !dropped.is(asItem.getItem()),
                        "Silk Touch handed over the " + subject.label()
                                + " block itself, which makes the deposit portable");
            }
            subject.yieldCheck().accept(drops);
            check(level.getBlockState(absolute).isAir(),
                    subject.label() + " was not depleted by a Silk Touch extraction");
            check(debtCount(level) == debtBefore + 1,
                    subject.label() + " with Silk Touch did not file exactly one restoration debt");
            check(player.getMainHandItem().getDamageValue() == 1,
                    subject.label() + " with Silk Touch did not cost exactly one durability");
            BrokenBlockDataStorage.get(level).remove(absolute);
        }
        helper.succeed();
    }

    /**
     * The structural reason Silk Touch cannot take a deposit bed: there is nothing to hand over.
     *
     * <p>Scoped to the sediment family deliberately. {@code ManagedDepositBlock} states that a bed
     * has no block item and no loot table, and that is the claim worth pinning, because a bed that
     * could be carried is a deposit the economy has lost track of. The managed <em>ores</em> do have
     * block items on purpose — an administrator places {@code britannia_mod:silver_ore} to build a
     * vein — and their protection is different: the break is always intercepted, and a player-placed
     * one is construction rather than a deposit, which is the M3/M7 provenance rule.
     */
    @GameTest(template = TEMPLATE)
    public static void noSedimentBedHasABlockItemToBeSilkTouchedInto(GameTestHelper helper) {
        List<ResourceDefinition> beds = ManagedDeposits.all();
        check(!beds.isEmpty(), "there are no sediment beds to check, so this proves nothing");
        for (ResourceDefinition definition : beds) {
            for (String blockId : definition.blockIds()) {
                Block block = com.seggellion.britannia_mod.resource.Resources.block(blockId);
                check(new ItemStack(block).isEmpty(),
                        blockId + " has a block item, so a deposit bed can be carried away");
            }
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Fake players                                                       */
    /* ------------------------------------------------------------------ */

    /**
     * Automation is refused everywhere, and the refusal costs it nothing to discover.
     *
     * <p>This is the hole the milestone was really about. A fake player is a {@code ServerPlayer};
     * the sediment path asked only that, so a machine holding a project shovel was an ordinary
     * customer of the extraction service. The assertion is deliberately the whole ledger — block,
     * yield, debt, durability, skill — because a partial denial that still files debt or still wears
     * the tool is its own bug.
     */
    @GameTest(template = TEMPLATE)
    public static void aFakePlayerExtractsNothingAndPaysNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        FakePlayer machine = FakePlayerFactory.getMinecraft(level);

        for (Subject subject : everyFamily()) {
            ItemStack tool = subject.tool();
            machine.getInventory().clearContent();
            machine.setItemInHand(InteractionHand.MAIN_HAND, tool);

            helper.setBlock(NODE, subject.block());
            takeDrops(level, absolute);
            int debtBefore = debtCount(level);

            breakThroughTheEventBus(level, absolute, machine);

            check(level.getBlockState(absolute).is(subject.block()),
                    "a fake player removed the " + subject.label() + " it was not allowed to work");
            check(takeDrops(level, absolute).isEmpty(),
                    "a fake player was paid for " + subject.label());
            check(debtCount(level) == debtBefore,
                    "a denied fake player filed restoration debt for " + subject.label());
            check(machine.getMainHandItem().getDamageValue() == 0,
                    "a denied fake player wore its tool on " + subject.label());
            check(MiningSkill.checkMiningAttempt(machine, level.getBlockState(absolute), absolute)
                            .skillGained() == 0.0f,
                    "a fake player was awarded Mining for " + subject.label());
        }
        helper.succeed();
    }

    /** And the policy types one as automation rather than as a person. */
    @GameTest(template = TEMPLATE)
    public static void neoforgesOwnFakePlayerIsWhatTheActorRuleRecognises(GameTestHelper helper) {
        FakePlayer machine = FakePlayerFactory.getMinecraft(helper.getLevel());
        check(ManagedExtractionPolicy.actorOf(machine) == ManagedExtractionPolicy.Actor.FAKE_PLAYER,
                "the actor rule did not recognise NeoForge's own fake player");
        check(ManagedExtractionPolicy.evaluate(machine)
                        == ManagedExtractionPolicy.Verdict.DENIED_FAKE_PLAYER,
                "a fake player was not denied");
        check(ManagedExtractionPolicy.evaluate(null)
                        == ManagedExtractionPolicy.Verdict.DENIED_NON_PLAYER,
                "an absent actor was not denied");
        check(!ManagedExtractionPolicy.mayExtract(machine), "a fake player may extract");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Creative and operators                                             */
    /* ------------------------------------------------------------------ */

    /**
     * A creative operator who is not attacking with the Britannia pickaxe is administering, and
     * every managed path stands aside: the block goes as any block goes in creative — sited
     * deposit and ambient rock alike — and nothing is paid, filed, worn, learned or said.
     *
     * <p>Driven bare-handed for every family and, for the beds, with their own shovel as well,
     * because the exception is one item wide: only the registered Britannia pickaxe makes a
     * creative player a tester, and the shovel is not it.
     *
     * <p>Both halves of the combined rule are asserted in the one sweep, because they differ only
     * in the block. Ambient crust breaks: a builder who cannot cut a cellar has not been given a
     * safety feature. A sited deposit cell does not: a vanilla break of one destroys the vein
     * permanently and silently, with no restoration debt filed to bring it back, so the break is
     * refused and removal stays an explicit command. What both halves share — and what this test
     * was always really about — is that neither pays: no yield, no debt, no skill, no tool wear,
     * and not even a Mining denial spoken on the way through.
     */
    @GameTest(template = TEMPLATE)
    public static void aCreativeOperatorNeitherEarnsNorDeletesADeposit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        ServerPlayer operator = miner(helper, ItemStack.EMPTY);
        operator.setGameMode(GameType.CREATIVE);

        for (Subject subject : everyFamily()) {
            List<ItemStack> hands = ManagedExtractionPolicy.isBritanniaPickaxe(subject.tool())
                    ? List.of(ItemStack.EMPTY)
                    : List.of(ItemStack.EMPTY, subject.tool());
            for (ItemStack hand : hands) {
                String label = subject.label() + (hand.isEmpty() ? " bare-handed" : " with its own tool");
                operator.setItemInHand(InteractionHand.MAIN_HAND, hand.copy());

                helper.setBlock(NODE, subject.block());
                takeDrops(level, absolute);
                int debtBefore = debtCount(level);
                float skillBefore = SkillManager.getSkill(operator, MiningSkill.SKILL_ID);

                breakThroughTheEventBus(level, absolute, operator);

                if (subject.depositCell()) {
                    check(level.getBlockState(absolute).is(subject.block()),
                            label + " was deleted by a creative click; a sited deposit comes back"
                                    + " from nothing, so removing one stays an explicit"
                                    + " /manageddeposit remove or /populateores clear");
                } else {
                    check(!level.getBlockState(absolute).is(subject.block()),
                            label + " did not break in creative; ambient crust must stay"
                                    + " terraformable and a managed path is still intercepting an"
                                    + " ordinary creative break");
                }
                List<ItemStack> drops = takeDrops(level, absolute);
                check(drops.isEmpty(),
                        label + " dropped something in creative: " + describe(drops));
                check(debtCount(level) == debtBefore,
                        label + " filed restoration debt for a creative removal, which is a player"
                                + " extraction transaction that never happened");
                check(operator.getMainHandItem().getDamageValue() == 0,
                        label + " wore the tool on a creative removal");
                check(SkillManager.getSkill(operator, MiningSkill.SKILL_ID) == skillBefore,
                        label + " moved Mining on a creative removal");
                check(MiningBreakGate.lastDenialKey(operator).isEmpty(),
                        label + " was refused or spoken to on the way through: "
                                + MiningBreakGate.lastDenialKey(operator));
            }
        }
        helper.succeed();
    }

    /**
     * The refusal is the whole transaction: nothing partial happens on the way to saying no.
     *
     * <p>Asserted through a second and third break as well, because the failure worth catching is
     * a guard that refuses the first click and then lets a repeat through — which is what a
     * refusal implemented as a one-shot flag, or as a cancel that some later listener re-enacts,
     * would look like.
     */
    @GameTest(template = TEMPLATE)
    public static void aRefusedCreativeBreakStaysRefusedAndLeavesNoTrace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, BlockRegistry.SILICA_SAND_DEPOSIT.get());

        ServerPlayer operator = miner(helper, shovel());
        operator.setGameMode(GameType.CREATIVE);
        int debtBefore = debtCount(level);
        float skillBefore = SkillManager.getSkill(operator, MiningSkill.SKILL_ID);

        for (int attempt = 1; attempt <= 3; attempt++) {
            breakThroughTheEventBus(level, absolute, operator);
            check(level.getBlockState(absolute).is(BlockRegistry.SILICA_SAND_DEPOSIT.get()),
                    "creative break attempt " + attempt + " removed the silica bed");
            check(takeDrops(level, absolute).isEmpty(),
                    "creative break attempt " + attempt + " produced a yield");
            check(debtCount(level) == debtBefore,
                    "creative break attempt " + attempt + " filed restoration debt");
            check(operator.getMainHandItem().getDamageValue() == 0,
                    "creative break attempt " + attempt + " wore the tool");
            check(SkillManager.getSkill(operator, MiningSkill.SKILL_ID) == skillBefore,
                    "creative break attempt " + attempt + " moved Mining");
        }
        helper.succeed();
    }

    /**
     * The two creative rules coexist, in one pass, for one player.
     *
     * <p>This is the reconciliation itself, stated as behaviour. The attacking-hand rule is broad
     * and about who is earning; the deposit protection is narrow and about what may be silently
     * destroyed. They read different facts — the hand and the block — so an administrator keeps
     * every affordance the hand rule gave them on ordinary blocks while a sited deposit is still
     * refused, and the same player picking the Britannia pickaxe up becomes a tester and extracts
     * that very deposit through the full ladder.
     *
     * <p>An uncatalogued block is included deliberately: the sweep above only exercises resources
     * the catalogue knows, and "a creative administrator can clear a misplaced ordinary block" has
     * to hold for the blocks that are not resources at all, which is nearly all of them.
     */
    @GameTest(template = TEMPLATE)
    public static void theDepositRefusalLeavesOrdinaryCreativeBreakingAlone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);

        ServerPlayer administrator = miner(helper, ItemStack.EMPTY);
        administrator.setGameMode(GameType.CREATIVE);
        int debtBefore = debtCount(level);

        // An ordinary block the catalogue has never heard of, and ambient crust that it has.
        // Both are the administrator's to clear.
        for (Block ordinary : List.of(Blocks.OAK_PLANKS, Blocks.STONE)) {
            helper.setBlock(NODE, ordinary);
            takeDrops(level, absolute);

            breakThroughTheEventBus(level, absolute, administrator);

            check(!level.getBlockState(absolute).is(ordinary),
                    "a creative administrator could not clear " + ordinary.getDescriptionId()
                            + "; the deposit refusal has spread to ordinary blocks");
            check(takeDrops(level, absolute).isEmpty(),
                    "clearing " + ordinary.getDescriptionId() + " in creative minted something");
        }

        // A sited deposit, in the same hand, on the same tick: refused.
        helper.setBlock(NODE, BlockRegistry.SILVER_ORE.get());
        takeDrops(level, absolute);

        breakThroughTheEventBus(level, absolute, administrator);

        check(level.getBlockState(absolute).is(BlockRegistry.SILVER_ORE.get()),
                "a creative administrator deleted a silver vein by clicking it");
        check(takeDrops(level, absolute).isEmpty(),
                "a refused creative break of silver minted something");
        check(debtCount(level) == debtBefore,
                "the ordinary breaks or the refused one filed restoration debt");

        // The same player, now a tester, takes that deposit through the whole ladder. The
        // affordance the local rule added is untouched by the protection this test is about.
        administrator.setItemInHand(InteractionHand.MAIN_HAND, pickaxe());

        breakThroughTheEventBus(level, absolute, administrator);

        check(level.getBlockState(absolute).isAir(),
                "a creative tester with the Britannia pickaxe could not extract the very deposit"
                        + " that was refused to them bare-handed");
        check(takeDrops(level, absolute).stream().anyMatch(s -> s.getItem() instanceof PurityOreItem),
                "a creative tester was not paid for the silver they extracted");
        check(hasDebtAt(level, absolute),
                "a creative tester's extraction filed no restoration debt, so the vein it removed"
                        + " would never come back");
        BrokenBlockDataStorage.get(level).remove(absolute);
        helper.succeed();
    }

    /**
     * A creative operator attacking with the Britannia pickaxe is a tester, and the managed flow
     * runs for them in full and unchanged: ore and stone are extracted exactly as for a survival
     * miner — yield, depletion, restoration debt — and a bed refuses the pickaxe as the wrong
     * tool and stands, because a tester is held to every rule rather than handed any bonus.
     */
    @GameTest(template = TEMPLATE)
    public static void aCreativeTesterWithThePickaxeRunsTheManagedFlow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        ServerPlayer tester = miner(helper, pickaxe());
        tester.setGameMode(GameType.CREATIVE);

        for (Subject subject : List.of(silverOre(), gradedStone())) {
            helper.setBlock(NODE, subject.block());
            takeDrops(level, absolute);
            int debtBefore = debtCount(level);

            breakThroughTheEventBus(level, absolute, tester);

            subject.yieldCheck().accept(takeDrops(level, absolute));
            check(level.getBlockState(absolute).isAir(),
                    "a creative tester's " + subject.label() + " extraction did not deplete the cell");
            check(debtCount(level) == debtBefore + 1 && hasDebtAt(level, absolute),
                    "a creative tester's " + subject.label()
                            + " extraction did not file exactly one restoration debt at the cell");
            BrokenBlockDataStorage.get(level).remove(absolute);
        }

        for (Subject subject : List.of(clay(), silica())) {
            helper.setBlock(NODE, subject.block());
            takeDrops(level, absolute);
            int debtBefore = debtCount(level);

            breakThroughTheEventBus(level, absolute, tester);

            check(level.getBlockState(absolute).is(subject.block()),
                    "the pickaxe worked a " + subject.label()
                            + " bed in creative; a tester is held to the tool rule like anyone");
            check(takeDrops(level, absolute).isEmpty(),
                    "a creative tester's pickaxe was paid for a " + subject.label() + " bed");
            check(debtCount(level) == debtBefore,
                    "a refused creative tester filed restoration debt for a " + subject.label() + " bed");
        }
        helper.succeed();
    }

    /** Provenance still wins: a builder may remove a managed block they placed themselves. */
    @GameTest(template = TEMPLATE)
    public static void aCreativeBuilderMayStillRemoveTheirOwnPlacedBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, BlockRegistry.SILVER_ORE.get());
        com.seggellion.britannia_mod.mining.MiningProvenance.markPlayerPlaced(level, absolute);

        ServerPlayer builder = miner(helper, pickaxe());
        builder.setGameMode(GameType.CREATIVE);

        breakThroughTheEventBus(level, absolute, builder);

        check(!level.getBlockState(absolute).is(BlockRegistry.SILVER_ORE.get()),
                "a creative builder could not remove a block they had placed themselves");
        check(takeDrops(level, absolute).stream().noneMatch(s -> s.getItem() instanceof PurityOreItem),
                "removing a player-placed block in creative produced managed yield");
        helper.succeed();
    }

    /**
     * The creative rule turns on the attacking hand alone, at both seams the handlers consult.
     *
     * <p>Without the Britannia pickaxe a creative player is a bypassing, non-earning actor: the
     * gate answers APPROVED_BYPASS and the policy DENIED_CREATIVE, whatever their skill and
     * whatever else they hold — a vanilla pickaxe, or the Britannia pickaxe itself in the
     * offhand. Attacking with the Britannia pickaxe they are a tester: the policy answers
     * ALLOWED, and the gate holds them to the ladder exactly as it holds a survival miner.
     */
    @GameTest(template = TEMPLATE)
    public static void theCreativeRuleTurnsOnTheAttackingHandAlone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(NODE, BlockRegistry.SILVER_ORE.get());
        BlockPos absolute = helper.absolutePos(NODE);
        BlockState silver = level.getBlockState(absolute);

        ServerPlayer operator = miner(helper, ItemStack.EMPTY);
        operator.setGameMode(GameType.CREATIVE);

        check(MiningBreakGate.evaluate(operator, silver, level, absolute).type()
                        == MiningBreakGate.ResultType.APPROVED_BYPASS,
                "a bare-handed creative player is not bypassing the gate");
        check(ManagedExtractionPolicy.evaluate(operator)
                        == ManagedExtractionPolicy.Verdict.DENIED_CREATIVE,
                "a bare-handed creative player is recognised as an earning actor");

        operator.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        check(MiningBreakGate.evaluate(operator, silver, level, absolute).type()
                        == MiningBreakGate.ResultType.APPROVED_BYPASS,
                "a vanilla pickaxe made a creative player a tester");

        operator.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        operator.setItemInHand(InteractionHand.OFF_HAND, pickaxe());
        check(MiningBreakGate.evaluate(operator, silver, level, absolute).type()
                        == MiningBreakGate.ResultType.APPROVED_BYPASS,
                "a Britannia pickaxe in the offhand made a creative player a tester");
        check(!ManagedExtractionPolicy.mayExtract(operator),
                "a Britannia pickaxe in the offhand made a creative player an earning actor");
        operator.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

        operator.setItemInHand(InteractionHand.MAIN_HAND, pickaxe());
        // miner() pins Mining at the ceiling, so the tester clears silver on skill.
        check(MiningBreakGate.evaluate(operator, silver, level, absolute).type()
                        == MiningBreakGate.ResultType.ELIGIBLE,
                "a creative tester at the ceiling did not clear silver on skill");
        check(ManagedExtractionPolicy.evaluate(operator) == ManagedExtractionPolicy.Verdict.ALLOWED,
                "a creative tester attacking with the Britannia pickaxe is not recognised as an"
                        + " earning actor, so the managed flow could never run for them");

        SkillManager.applyConfirmedValue(operator, MiningSkill.SKILL_ID, 54.9f);
        check(MiningBreakGate.evaluate(operator, silver, level, absolute).type()
                        == MiningBreakGate.ResultType.INSUFFICIENT_SKILL,
                "creative did not hold the tester to the ladder");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Cross-family tool negatives, through the real bus                  */
    /* ------------------------------------------------------------------ */

    /**
     * The wrong tool earns nothing and wears nothing, whichever direction it is wrong in.
     *
     * <p>{@code ResourceExtractionMatrixGameTests} already proves the authorization rule at the
     * service level. This proves the same refusals survive the whole event path, and adds the part
     * that matters for durability: a refused swing must not have cost the player anything.
     */
    @GameTest(template = TEMPLATE)
    public static void theWrongToolEarnsNothingAndWearsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);

        record Wrong(String label, Block block, ItemStack tool) {
        }
        List<Wrong> cases = List.of(
                new Wrong("clay with a pickaxe", BlockRegistry.CLAY_DEPOSIT.get(), pickaxe()),
                new Wrong("silica with a pickaxe", BlockRegistry.SILICA_SAND_DEPOSIT.get(), pickaxe()),
                new Wrong("silver ore with a shovel", BlockRegistry.SILVER_ORE.get(), shovel()),
                new Wrong("silver ore with a vanilla pickaxe", BlockRegistry.SILVER_ORE.get(),
                        new ItemStack(Items.DIAMOND_PICKAXE)),
                new Wrong("clay with a vanilla shovel", BlockRegistry.CLAY_DEPOSIT.get(),
                        new ItemStack(Items.DIAMOND_SHOVEL)),
                new Wrong("silver ore with a two-handed axe", BlockRegistry.SILVER_ORE.get(),
                        new ItemStack(com.seggellion.britannia_mod.registry.ItemRegistry.TWO_HANDED_AXE.get())),
                new Wrong("clay with a stick", BlockRegistry.CLAY_DEPOSIT.get(), new ItemStack(Items.STICK)));

        for (Wrong wrong : cases) {
            ServerPlayer player = miner(helper, wrong.tool());
            helper.setBlock(NODE, wrong.block());
            takeDrops(level, absolute);
            int debtBefore = debtCount(level);

            breakThroughTheEventBus(level, absolute, player);

            check(level.getBlockState(absolute).is(wrong.block()),
                    wrong.label() + " removed the resource anyway");
            check(takeDrops(level, absolute).isEmpty(), wrong.label() + " produced a yield");
            check(debtCount(level) == debtBefore, wrong.label() + " filed restoration debt");
            check(player.getMainHandItem().getDamageValue() == 0,
                    wrong.label() + " cost " + player.getMainHandItem().getDamageValue()
                            + " durability for a refusal");
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  One transaction, once                                              */
    /* ------------------------------------------------------------------ */

    /**
     * One legitimate break commits exactly one of everything.
     *
     * <p>Seven listeners are on {@code BreakEvent} and two of them act on managed resources, so
     * "does anything commit this twice" is a real question rather than a hypothetical one. Posting
     * to the bus rather than calling a handler is the point: this is the arrangement that would
     * actually double up.
     */
    @GameTest(template = TEMPLATE)
    public static void oneBreakCommitsExactlyOneOfEverything(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);

        for (Subject subject : everyFamily()) {
            ServerPlayer player = miner(helper, subject.tool());
            helper.setBlock(NODE, subject.block());
            takeDrops(level, absolute);
            int debtBefore = debtCount(level);

            breakThroughTheEventBus(level, absolute, player);

            List<ItemStack> drops = takeDrops(level, absolute);
            subject.yieldCheck().accept(drops);
            check(level.getBlockState(absolute).isAir(),
                    subject.label() + " did not reach exactly one depleted state");
            check(debtCount(level) - debtBefore == 1,
                    subject.label() + " filed " + (debtCount(level) - debtBefore) + " restoration debts");
            check(player.getMainHandItem().getDamageValue() == 1,
                    subject.label() + " cost " + player.getMainHandItem().getDamageValue()
                            + " durability for one extraction");
            BrokenBlockDataStorage.get(level).remove(absolute);
        }
        helper.succeed();
    }

    /**
     * A second break of the same cell adds nothing.
     *
     * <p>The realistic reentrancy shape here is not two handlers racing but the same handler being
     * reached twice for one dig — a held left click re-completing, or a duplicate event. The
     * depleted cell no longer resolves to a resource, so the second pass finds nothing to sell, and
     * the tool is not charged for the attempt.
     */
    @GameTest(template = TEMPLATE)
    public static void breakingTheDepletedCellAgainCommitsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);

        for (Subject subject : everyFamily()) {
            ServerPlayer player = miner(helper, subject.tool());
            helper.setBlock(NODE, subject.block());
            takeDrops(level, absolute);

            breakThroughTheEventBus(level, absolute, player);
            takeDrops(level, absolute);
            int debtAfterFirst = debtCount(level);
            int wearAfterFirst = player.getMainHandItem().getDamageValue();

            breakThroughTheEventBus(level, absolute, player);

            check(takeDrops(level, absolute).isEmpty(),
                    "a second break of the depleted " + subject.label() + " paid out again");
            check(debtCount(level) == debtAfterFirst,
                    "a second break of the depleted " + subject.label() + " filed another debt");
            check(player.getMainHandItem().getDamageValue() == wearAfterFirst,
                    "a second break of the depleted " + subject.label() + " charged the tool again");
            BrokenBlockDataStorage.get(level).remove(absolute);
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Regeneration timings are untouched by this milestone               */
    /* ------------------------------------------------------------------ */

    /** Silica still comes back in a day, and everything else in six hours. */
    @GameTest(template = TEMPLATE)
    public static void milestoneSixChangedNoRegenerationTiming(GameTestHelper helper) {
        check(ManagedDeposits.SILICA_SAND.regenerationHours() == 24,
                "silica is no longer on its approved 24-hour regeneration");
        check(ManagedDeposits.CLAY.regenerationHours() == 6,
                "clay is no longer on the historical six-hour regeneration");
        for (ResourceDefinition definition : com.seggellion.britannia_mod.resource.ResourceCatalog
                .instance().all()) {
            int hours = definition.regenerationHours();
            check(hours == 6 || definition == ManagedDeposits.SILICA_SAND,
                    definition.id() + " regenerates in " + hours
                            + " hours; only silica was approved to differ");
        }
        helper.succeed();
    }

    /** The sediment refusal reason is distinguishable, so operators can be told what happened. */
    @GameTest(template = TEMPLATE)
    public static void aDeniedActorIsReportedAsSuchRatherThanAsAWrongTool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, BlockRegistry.CLAY_DEPOSIT.get());

        FakePlayer machine = FakePlayerFactory.getMinecraft(level);
        machine.setItemInHand(InteractionHand.MAIN_HAND, shovel());

        ManagedDepositExtraction.Result result =
                ManagedDepositExtraction.extract(level, absolute, machine, machine.getMainHandItem());
        check(result == ManagedDepositExtraction.Result.DENIED_ACTOR,
                "a fake player holding the correct shovel was answered " + result);
        check(result.concernsADeposit(),
                "a denied actor must still count as concerning a deposit, or the break is not cancelled");
        check(!result.extracted(), "a denied actor was recorded as having extracted");
        helper.succeed();
    }
}
