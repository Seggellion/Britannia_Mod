package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import com.seggellion.britannia_mod.event.ManagedResourceExplosionHandler;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.mining.MiningSkill;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.Resources;
import com.seggellion.britannia_mod.resource.deposit.DepositIdentity;
import com.seggellion.britannia_mod.resource.deposit.DepositInstance;
import com.seggellion.britannia_mod.resource.deposit.DepositLedger;
import com.seggellion.britannia_mod.resource.deposit.DepositRegistrar;
import com.seggellion.britannia_mod.resource.deposit.DepositSource;
import com.seggellion.britannia_mod.resource.placement.MaterializationService;
import com.seggellion.britannia_mod.resource.placement.PlacementPlanner;
import com.seggellion.britannia_mod.resource.placement.PlannedDeposit;
import com.seggellion.britannia_mod.resource.ResourceShape;
import com.seggellion.britannia_mod.resource.shape.ShapeRotation;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.PushReaction;
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

/**
 * Milestone 11: managed coal, end to end, and the policies it must not escape.
 *
 * <h2>What is different about coal</h2>
 * Every other Mining-governed resource mints a bespoke item — {@code PurityOreItem} for the metals,
 * {@code GradeStoneItem} for the rocks. Coal hands over ordinary {@code minecraft:coal}, because
 * "coal, 73% pure" is not something a furnace, a torch recipe or a campfire knows what to do with,
 * and the owner's requirement was that managed coal be usable as fuel anywhere coal already is.
 *
 * <p>That is the only thing about it that is new. Everything else — the pickaxe requirement, one
 * durability charge, the Mining award, the restoration debt, the six-hour cycle, the refusal of
 * automation and of Creative — is the platform behaving as it does for every other resource, which
 * is what these tests are for: coal is a data addition, and a data addition must not have quietly
 * acquired its own rules.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class ManagedCoalLifecycleGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos NODE = new BlockPos(1, 1, 1);

    private ManagedCoalLifecycleGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static ResourceDefinition coal() {
        return ResourceCatalog.instance().byPath("coal").orElseThrow();
    }

    private static Block coalBlock() {
        return Resources.block(coal().generation().orElseThrow().blockId());
    }

    private static ItemStack pickaxe() {
        return ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3);
    }

    private static ServerPlayer miner(ServerLevel level, ItemStack tool) {
        ServerPlayer player = ManagedResourceTestPlayers.survival(level, "coal-miner");
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        SkillManager.applyConfirmedValue(player, MiningSkill.SKILL_ID, 100.0f);
        return player;
    }

    private static List<ItemStack> takeDrops(ServerLevel level, BlockPos around) {
        List<ItemEntity> entities =
                level.getEntitiesOfClass(ItemEntity.class, new AABB(around).inflate(3.0D));
        List<ItemStack> stacks = entities.stream().map(ItemEntity::getItem).toList();
        entities.forEach(ItemEntity::discard);
        return stacks;
    }

    private static void breakThroughTheEventBus(ServerLevel level, BlockPos pos, ServerPlayer player) {
        BlockState state = level.getBlockState(pos);
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player);
        NeoForge.EVENT_BUS.post(event);
        if (!event.isCanceled()) {
            level.destroyBlock(pos, !player.isCreative(), player);
        }
    }

    /** A managed coal cell standing in approved host stone at the test node. */
    private static BlockPos plantCoal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = helper.absolutePos(NODE);
        level.setBlock(cell, Blocks.STONE.defaultBlockState(), 2);
        MaterializationService.materialize(level,
                PlacementPlanner.plan(coal(), level.dimension().location().toString(), cell,
                        4, ShapeRotation.XZ, 909L),
                List.of(cell), 8);
        check(level.getBlockState(cell).is(coalBlock()),
                "coal did not materialise into approved host stone");
        takeDrops(level, cell);
        return cell;
    }

    /* ------------------------------------------------------------------ */
    /*  The whole chain                                                    */
    /* ------------------------------------------------------------------ */

    /**
     * Rails row → stable identity → Layered plan → materialisation → managed block → pickaxe →
     * {@code minecraft:coal} → one durability → Mining award → debt → six hours → restoration →
     * the same deposit identity.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 260)
    public static void curatedCoalIsPlacedMinedAndComesBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = helper.absolutePos(NODE);
        String dimension = level.dimension().location().toString();
        ResourceDefinition resource = coal();
        Block block = coalBlock();
        check(resource.generation().orElseThrow().shape() == ResourceShape.LAYERED,
                "coal must use the Layered planner -- a seam, which is what coal geologically is");

        // A curated Rails row is the only thing that says a deposit exists. Its immutable
        // parameters derive the identity, and the identity derives the planner seed.
        CuratedDepositTestRows row = CuratedDepositTestRows.of("coal", cell, 6);
        long instanceId = row.identity(dimension);

        level.setBlock(cell, Blocks.STONE.defaultBlockState(), 2);
        PlannedDeposit deposit = row.plan(dimension);

        DepositLedger ledger = DepositLedger.get(level);
        DepositInstance instance = row.describe(dimension);
        check(ledger.register(instance).mayMaterialize(), "coal would not register");

        MaterializationService.materialize(level, deposit, List.of(cell), 8);
        check(level.getBlockState(cell).is(block), "coal did not materialise as " + block);

        takeDrops(level, cell);
        ServerPlayer miner = miner(level, pickaxe());
        float before = SkillManager.getSkill(miner, MiningSkill.SKILL_ID);
        breakThroughTheEventBus(level, cell, miner);

        List<ItemStack> drops = takeDrops(level, cell);
        check(drops.size() == 1, "coal yielded " + drops.size() + " stacks: " + drops);
        check(drops.get(0).is(Items.COAL),
                "coal yielded " + drops.get(0).getItem() + ", not minecraft:coal");
        check(drops.get(0).getCount() == 1,
                "coal yielded " + drops.get(0).getCount() + " items, not the configured 1");
        check(miner.getMainHandItem().getDamageValue() == 1,
                "coal cost " + miner.getMainHandItem().getDamageValue() + " durability, not one");
        check(SkillManager.getSkill(miner, MiningSkill.SKILL_ID) >= before,
                "mining coal must not reduce Mining");

        BrokenBlockDataStorage storage = BrokenBlockDataStorage.get(level);
        BrokenBlockData record = storage.getBrokenBlocks().get(cell);
        check(record != null, "coal was not enrolled for restoration");
        check(record.originalState.is(block), "coal would restore as " + record.originalState);

        long delay = Resources.regenerationMillis(record.originalState).orElseThrow();
        check(delay == 6L * 60L * 60L * 1000L,
                "coal's regeneration resolved to " + delay + "ms rather than the platform's 6 hours");

        storage.add(new BrokenBlockData(record.pos, record.originalState,
                record.brokenTime - delay - 1_000L, record.playerUUID,
                record.brokenTime - delay - 1_000L, record.instanceId, record.resourceId, 0L, 0));

        // Restoration runs on the scheduler's own bounded cadence -- a fixed number of debts per
        // pass, shared with every other test in this world -- so this waits for several passes rather
        // than one. The debt was backdated past its due time above, so only the queue is pending.
        helper.runAfterDelay(30L, () -> {
            check(level.getBlockState(cell).is(block), "coal did not come back");
            check(DepositLedger.get(level).byId(instanceId).isPresent(), "coal lost its ledger entry across restoration");
            helper.succeed();
        });
    }

    /* ------------------------------------------------------------------ */
    /*  Fuel                                                               */
    /* ------------------------------------------------------------------ */

    /**
     * The yield is Minecraft's own coal, so it burns wherever Minecraft's coal burns.
     *
     * <p>Deliberately asserted through {@code getBurnTime} rather than by registering anything: the
     * point of yielding the vanilla item is that no fuel registration is needed, and a test that
     * passed only because the mod had registered a burn time would be proving the opposite.
     *
     * <p>Britannia's own forge and blacksmithing stations consume no fuel at all — there is no
     * burn-time or fuel-slot logic in the mod — so this is vanilla-side capability only. Making
     * Britannia crafting require fuel would be a gameplay project of its own.
     */
    @GameTest(template = TEMPLATE)
    public static void managedCoalYieldsOrdinaryUsableFuel(GameTestHelper helper) {
        ResourceDefinition resource = coal();
        check(resource.yield().mode() == ResourceDefinition.Yield.Mode.ITEM,
                "coal must yield a configured item, not a purity or grade payload");
        check(resource.yield().itemId().orElseThrow().equals("minecraft:coal"),
                "coal must yield minecraft:coal, found " + resource.yield().itemId());

        ItemStack yield = Resources.yieldStack(resource);
        check(yield.is(Items.COAL), "the configured yield resolved to " + yield.getItem());

        int burn = yield.getBurnTime(RecipeType.SMELTING);
        check(burn > 0, "managed coal's yield must be a valid furnace fuel, burn time was " + burn);
        check(burn == new ItemStack(Items.COAL).getBurnTime(RecipeType.SMELTING),
                "managed coal's yield must burn exactly as ordinary coal does, found " + burn);
        System.out.println("M11 coal yield = " + yield.getItem() + ", burn time " + burn + " ticks");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Policy: coal inherits it, it does not get its own                  */
    /* ------------------------------------------------------------------ */

    /** Fortune must not multiply an economic yield (milestone 6). */
    @GameTest(template = TEMPLATE)
    public static void fortuneDoesNotAmplifyCoal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = plantCoal(helper);

        ItemStack tool = pickaxe();
        tool.enchant(level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.FORTUNE), 3);
        breakThroughTheEventBus(level, cell, miner(level, tool));

        List<ItemStack> drops = takeDrops(level, cell);
        check(drops.size() == 1, "Fortune produced " + drops.size() + " stacks of coal");
        check(drops.get(0).getCount() == 1,
                "Fortune III produced " + drops.get(0).getCount() + " coal; the configured yield is"
                        + " the authority and enchantments do not multiply it");
        helper.succeed();
    }

    /** Silk Touch must not make the deposit portable. */
    @GameTest(template = TEMPLATE)
    public static void silkTouchDoesNotYieldTheCoalBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = plantCoal(helper);

        ItemStack tool = pickaxe();
        tool.enchant(level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        breakThroughTheEventBus(level, cell, miner(level, tool));

        for (ItemStack drop : takeDrops(level, cell)) {
            check(!drop.is(BlockRegistry.COAL_ORE.get().asItem()),
                    "Silk Touch produced the managed coal block itself");
            check(drop.is(Items.COAL), "Silk Touch changed the yield to " + drop.getItem());
        }
        helper.succeed();
    }

    /** The extraction tool is the Britannia pickaxe, by tag, exactly as for the ores. */
    @GameTest(template = TEMPLATE)
    public static void onlyTheBritanniaPickaxeWorksCoal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        record Denied(String label, ItemStack tool) {
        }
        List<Denied> denied = List.of(
                new Denied("a vanilla iron pickaxe", new ItemStack(Items.IRON_PICKAXE)),
                new Denied("a vanilla diamond pickaxe", new ItemStack(Items.DIAMOND_PICKAXE)),
                new Denied("a Britannia shovel",
                        ToolRegistry.createShovel(UOMetalToolMaterial.IRON, 3)),
                new Denied("a Britannia two-handed axe",
                        new ItemStack(ItemRegistry.TWO_HANDED_AXE.get())),
                new Denied("a bare hand", ItemStack.EMPTY));

        for (Denied entry : denied) {
            BlockPos cell = plantCoal(helper);
            ServerPlayer player = miner(level, entry.tool());
            breakThroughTheEventBus(level, cell, player);
            List<ItemStack> drops = takeDrops(level, cell);
            check(drops.stream().noneMatch(drop -> drop.is(Items.COAL)),
                    entry.label() + " extracted coal; only the Britannia pickaxe tag may");
        }
        helper.succeed();
    }

    /** Automation is refused (milestone 6): a fake player extracts nothing. */
    @GameTest(template = TEMPLATE)
    public static void fakePlayersCannotWorkCoal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = plantCoal(helper);

        FakePlayer robot = FakePlayerFactory.getMinecraft(level);
        robot.setItemInHand(InteractionHand.MAIN_HAND, pickaxe());
        BlockState state = level.getBlockState(cell);
        NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, cell, state, robot));

        check(takeDrops(level, cell).stream().noneMatch(drop -> drop.is(Items.COAL)),
                "a fake player extracted coal");
        check(level.getBlockState(cell).is(coalBlock()),
                "a fake player depleted the coal cell");
        helper.succeed();
    }

    /**
     * Coal inherits the containment policies rather than needing its own.
     *
     * <p>Explosion protection is expressed once, as {@code family != STONE}, so a new Mining family
     * is covered by construction. Piston immunity comes from {@code BaseOreBlock}'s
     * {@code PushReaction.BLOCK} — a deposit's identity is its coordinates, and a piston shoving a
     * cell sideways would leave the restoration record pointing at empty ground.
     *
     * <p>Asserted rather than assumed, because "it should inherit that" is exactly the kind of
     * claim that turns out to have an exception.
     */
    @GameTest(template = TEMPLATE)
    public static void coalIsContainedAgainstExplosionsAndPistons(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = plantCoal(helper);

        check(ManagedResourceExplosionHandler.isProtectedFromExplosions(level, cell),
                "managed coal must be protected from explosions like every other managed resource");
        check(level.getBlockState(cell).getPistonPushReaction() == PushReaction.BLOCK,
                "managed coal must refuse to be pushed; a moved cell orphans its restoration record");

        // And the vanilla block, in a legacy chunk, must not have acquired any of that.
        BlockPos legacy = cell.above();
        level.setBlock(legacy, Blocks.COAL_ORE.defaultBlockState(), 2);
        check(!ManagedResourceExplosionHandler.isProtectedFromExplosions(level, legacy),
                "minecraft:coal_ore is ordinary decorative terrain and must carry no managed policy");
        helper.succeed();
    }

    /**
     * An ordinary Creative break is refused, and the coal survives.
     *
     * <p>A curated coal cell is sited by {@code /populateores} exactly as an ore vein is, and it
     * is a MINERAL — a deposit, not the world's crust. Letting a bare-handed creative click take
     * it would destroy it permanently and silently: nothing is minted, so nothing files the
     * restoration debt that would ever bring it back. So the break is refused and the cell stands.
     *
     * <p>The other half of the creative rule is unaffected and is asserted next door: attacking
     * with the Britannia pickaxe makes the same player a tester, and
     * {@link #creativeTesterWithThePickaxeExtractsTheCoal} takes this very cell through the whole
     * managed flow. What this test pins is that <em>administering</em> creative earns nothing and
     * destroys nothing.
     */
    @GameTest(template = TEMPLATE)
    public static void creativeBreakingIsRefusedAndTheCoalSurvives(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = plantCoal(helper);

        ServerPlayer operator = ManagedResourceTestPlayers.survival(level, "coal-operator");
        operator.setGameMode(GameType.CREATIVE);
        operator.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        breakThroughTheEventBus(level, cell, operator);

        check(level.getBlockState(cell).is(coalBlock()),
                "a Creative break destroyed a managed coal deposit; ordinary breaking is not"
                        + " administrator deposit removal, which is /manageddeposit remove or"
                        + " /populateores clear");
        check(takeDrops(level, cell).stream().noneMatch(drop -> drop.is(Items.COAL)),
                "a Creative break minted coal");
        check(BrokenBlockDataStorage.get(level).getBrokenBlocks().get(cell) == null,
                "a Creative break filed restoration debt");
        helper.succeed();
    }

    /**
     * A Creative player attacking with the Britannia pickaxe is a tester, and the managed flow
     * runs for them unchanged: coal in hand, the cell depleted, and the restoration debt on file.
     */
    @GameTest(template = TEMPLATE)
    public static void creativeTesterWithThePickaxeExtractsTheCoal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = plantCoal(helper);

        ServerPlayer tester = ManagedResourceTestPlayers.survival(level, "coal-tester");
        tester.setGameMode(GameType.CREATIVE);
        tester.setItemInHand(InteractionHand.MAIN_HAND, pickaxe());
        SkillManager.applyConfirmedValue(tester, MiningSkill.SKILL_ID, 100.0f);
        breakThroughTheEventBus(level, cell, tester);

        check(!level.getBlockState(cell).is(coalBlock()),
                "a creative tester's pickaxe did not extract the coal");
        check(takeDrops(level, cell).stream().anyMatch(drop -> drop.is(Items.COAL)),
                "a creative tester was not handed the coal");
        check(BrokenBlockDataStorage.get(level).getBrokenBlocks().get(cell) != null,
                "a creative tester's extraction filed no restoration debt");
        BrokenBlockDataStorage.get(level).remove(cell);
        helper.succeed();
    }
}
