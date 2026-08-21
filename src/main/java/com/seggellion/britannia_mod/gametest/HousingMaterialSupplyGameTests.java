package com.seggellion.britannia_mod.gametest;

import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.deposit.ManagedDepositExtraction;
import com.seggellion.britannia_mod.deposit.ManagedDeposits;
import com.seggellion.britannia_mod.economy.ServerEconomyService;
import com.seggellion.britannia_mod.event.BlockRestoreHandler;
import com.seggellion.britannia_mod.item.GradeStoneItem;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.mining.Mineables;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.util.PickaxeMiningRules;

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
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The three material loops this pass added, in a world.
 *
 * <p>Sandstone is quarried, silica is dug and fired into raw glass, and limestone is fired into
 * plaster. Each ends in an item the economy can name, and each refuses the shortcuts — the vanilla
 * material, the wrong tool, the wrong rock.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class HousingMaterialSupplyGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos SPOT = new BlockPos(1, 1, 1);

    private HousingMaterialSupplyGameTests() {
    }

    /* ------------------------------------------------------------------ */
    /*  Sandstone                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * The authored quarry face is a Mining resource; the desert is not.
     *
     * <p>Sandstone goes through the Mining catalogue rather than the deposit catalogue because a
     * pickaxe works it, and the catalogue keys on block type. That is safe only because the block
     * is one the mod places: {@code minecraft:sandstone} generates by the thousand, and putting it
     * in the catalogue would let world generation decide the villa's material price.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void onlytheauthoredsandstonefaceismineable(GameTestHelper helper) {
        if (Mineables.resolve(BlockRegistry.SANDSTONE_DEPOSIT.get().defaultBlockState()).isEmpty()) {
            throw new GameTestAssertException("the sandstone deposit is not a Mining resource");
        }
        if (!PickaxeMiningRules.isAllowedStoneBlock(
                BlockRegistry.SANDSTONE_DEPOSIT.get().defaultBlockState())) {
            throw new GameTestAssertException("the pickaxe may not work the sandstone deposit");
        }
        for (var vanilla : List.of(Blocks.SANDSTONE, Blocks.SAND, Blocks.RED_SANDSTONE,
                Blocks.SMOOTH_SANDSTONE, Blocks.CHISELED_SANDSTONE)) {
            if (Mineables.resolve(vanilla.defaultBlockState()).isPresent()) {
                throw new GameTestAssertException(
                        vanilla + " is a Mining resource, so every desert is now a quarry");
            }
        }
        helper.succeed();
    }

    /**
     * Quarrying it hands over a graded stone that says sandstone, and the economy agrees.
     *
     * <p>Driven through the same managed break the ores use, so the drop, the restoration record
     * and the skill award are the existing flow rather than a second one written for sandstone.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void quarriedsandstonesellsassedimentarystone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(SPOT);
        helper.setBlock(SPOT, BlockRegistry.SANDSTONE_DEPOSIT.get());

        ServerPlayer quarryman = player(level, "sandstone-quarryman");
        quarryman.setItemInHand(InteractionHand.MAIN_HAND,
                ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3));
        level.getServer().getPlayerList();

        new com.seggellion.britannia_mod.event.CustomBlockBreakHandler().onBlockBreak(
                new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(
                        level, absolute, level.getBlockState(absolute), quarryman));

        List<ItemStack> dropped = dropsAround(level, absolute);
        ItemStack graded = dropped.stream()
                .filter(stack -> stack.getItem() instanceof GradeStoneItem)
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException(
                        "quarrying the face produced no graded stone: " + dropped));
        String stoneType = ((GradeStoneItem) graded.getItem()).getStoneType(graded);
        if (!"Sandstone".equalsIgnoreCase(stoneType)) {
            throw new GameTestAssertException("the face yielded " + stoneType + ", not sandstone");
        }

        JsonObject described = ServerEconomyService.describeSaleItem(graded);
        assertMember(described, "category", "stone");
        assertMember(described, "subcategory", "sedimentary");
        assertMember(described, "item_name", "sandstone");
        helper.succeed();
    }

    /** And the quarry face is enrolled for restoration like any other worked rock. */
    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void aquarriedsandstonefacecomesback(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(SPOT);
        helper.setBlock(SPOT, BlockRegistry.SANDSTONE_DEPOSIT.get());

        ServerPlayer quarryman = player(level, "sandstone-quarryman-b");
        quarryman.setItemInHand(InteractionHand.MAIN_HAND,
                ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3));
        new com.seggellion.britannia_mod.event.CustomBlockBreakHandler().onBlockBreak(
                new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(
                        level, absolute, level.getBlockState(absolute), quarryman));

        BrokenBlockDataStorage storage = BrokenBlockDataStorage.get(level);
        BrokenBlockData record = storage.getBrokenBlocks().get(absolute);
        if (record == null || !record.originalState.is(BlockRegistry.SANDSTONE_DEPOSIT.get())) {
            throw new GameTestAssertException(
                    "the quarried face was not enrolled for restoration, so it would never return");
        }

        level.getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2.0D))
                .forEach(ItemEntity::discard);
        storage.add(new BrokenBlockData(record.pos, record.originalState,
                record.brokenTime - BlockRestoreHandler.RESTORE_DELAY - 1_000L, record.playerUUID));

                // Milestone 4: the restoration pass runs on a cadence (once a second) rather than on every
        // tick, so a five-tick wait now outruns the scheduler. These are six-hour timers; a second
        // of latency is the whole point of not sweeping twenty times a second.
        helper.runAfterDelay(30L, () -> {
            helper.assertBlockPresent(BlockRegistry.SANDSTONE_DEPOSIT.get(), SPOT);
            helper.succeed();
        });
    }

    /* ------------------------------------------------------------------ */
    /*  Silica and raw glass                                               */
    /* ------------------------------------------------------------------ */

    /** The silica bed is a deposit; the beach is not. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void onlythemodsownbedissilica(GameTestHelper helper) {
        if (ManagedDeposits.resolve(
                BlockRegistry.SILICA_SAND_DEPOSIT.get().defaultBlockState()).isEmpty()) {
            throw new GameTestAssertException("the silica bed is not a managed deposit");
        }
        for (var vanilla : List.of(Blocks.SAND, Blocks.RED_SAND, Blocks.SANDSTONE)) {
            if (ManagedDeposits.resolve(vanilla.defaultBlockState()).isPresent()) {
                throw new GameTestAssertException(
                        vanilla + " is a managed deposit, so every beach is a glass mine");
            }
        }
        helper.succeed();
    }

    /** A shovel digs one silica sand out of it; a pickaxe does not. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void ashoveldigssilicaandapickaxedoesnot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(SPOT);
        helper.setBlock(SPOT, BlockRegistry.SILICA_SAND_DEPOSIT.get());
        ServerPlayer digger = player(level, "silica-digger");

        if (ManagedDepositExtraction.extract(level, absolute, digger,
                ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3))
                != ManagedDepositExtraction.Result.WRONG_TOOL) {
            throw new GameTestAssertException("a pickaxe worked the silica bed");
        }
        helper.assertBlockPresent(BlockRegistry.SILICA_SAND_DEPOSIT.get(), SPOT);

        if (ManagedDepositExtraction.extract(level, absolute, digger, shovel())
                != ManagedDepositExtraction.Result.EXTRACTED) {
            throw new GameTestAssertException("the authorized shovel was refused");
        }
        List<ItemStack> dropped = dropsAround(level, absolute);
        if (dropped.size() != 1 || !dropped.get(0).is(ItemRegistry.SILICA_SAND.get())
                || dropped.get(0).getCount() != 1) {
            throw new GameTestAssertException("one bed did not yield exactly one silica sand: " + dropped);
        }
        if (ManagedDeposits.resolve(level.getBlockState(absolute)).isPresent()) {
            throw new GameTestAssertException("the bed is still standing after being worked");
        }
        helper.succeed();
    }

    /**
     * Silica is not raw glass until it has been through a fire.
     *
     * <p>The recipe is looked up in the server's own recipe manager, so this is the conversion the
     * game would actually run in a furnace rather than a restatement of the JSON.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void silicasmeltsintorawglassandsandsdoesnot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack fired = smelt(level, new ItemStack(ItemRegistry.SILICA_SAND.get()));
        if (fired.isEmpty() || !fired.is(ItemRegistry.RAW_GLASS.get())) {
            throw new GameTestAssertException(
                    "firing silica sand produced " + fired + " rather than raw glass");
        }

        ItemStack fromBeachSand = smelt(level, new ItemStack(Items.SAND));
        if (fromBeachSand.is(ItemRegistry.RAW_GLASS.get())) {
            throw new GameTestAssertException(
                    "ordinary sand fires into raw glass, so every beach supplies the villa");
        }

        JsonObject described = ServerEconomyService.describeSaleItem(fired);
        assertMember(described, "category", "glass");
        assertMember(described, "subcategory", "raw");
        assertMember(described, "item_name", "raw_glass");

        JsonObject feedstock = ServerEconomyService.describeSaleItem(
                new ItemStack(ItemRegistry.SILICA_SAND.get()));
        assertMember(feedstock, "item_name", "sand");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Plaster                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * Limestone fires into plaster; every other rock in the quarry does not.
     *
     * <p>The distinction is the whole point. Every mined rock is one item wearing a different
     * stone type, so a recipe that could not read the type would have made cobblestone into
     * plaster and dissolved the commodity Rails insisted on keeping separate.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void onlylimestonefiresintoplaster(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        ItemStack plaster = smelt(level, gradedStone("Limestone"));
        if (plaster.isEmpty() || !plaster.is(ItemRegistry.PLASTER.get())) {
            throw new GameTestAssertException(
                    "firing limestone produced " + plaster + " rather than plaster");
        }

        for (String other : new String[] { "Stone", "Cobblestone", "Granite", "Sandstone" }) {
            ItemStack result = smelt(level, gradedStone(other));
            if (result.is(ItemRegistry.PLASTER.get())) {
                throw new GameTestAssertException(other + " fires into plaster; generic stone "
                        + "substitutes for the material the economy keeps separate");
            }
        }

        JsonObject described = ServerEconomyService.describeSaleItem(plaster);
        assertMember(described, "category", "stone");
        assertMember(described, "subcategory", "processed");
        assertMember(described, "item_name", "plaster");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Thatch                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * The straw a grain harvest already produced is thatch, and the grain is still grain.
     *
     * <p>Nothing was added to the world for this. {@code FarmingBlock} has popped a straw from
     * every grain-blade harvest all along; it simply had no economic identity, so the roofing
     * material of two houses fell on the floor as litter.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void strawsellsasthatchandwheatstillsellsasgrain(GameTestHelper helper) {
        JsonObject thatch = ServerEconomyService.describeSaleItem(
                new ItemStack(ItemRegistry.STRAW.get()));
        assertMember(thatch, "category", "textile");
        assertMember(thatch, "subcategory", "raw");
        assertMember(thatch, "item_name", "thatch");

        JsonObject wheat = ServerEconomyService.describeSaleItem(new ItemStack(Items.WHEAT));
        assertMember(wheat, "category", "grain");
        if ("thatch".equals(wheat.get("item_name").getAsString())) {
            throw new GameTestAssertException("wheat became thatch, so a city eats its roofs");
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Common stone                                                       */
    /* ------------------------------------------------------------------ */

    /**
     * Rubble fires into common stone, and mining still yields rubble.
     *
     * <p>Four deeds want {@code stone|common|stone} — 4288 in the Castle, 2451 in the Stone Keep —
     * and nothing produced it, because mining {@code minecraft:stone} deliberately gives rubble.
     * That rule is untouched. What is added is the step vanilla itself has always had between the
     * two, so a player who wants stone burns their cobblestone for it and a player who wants coin
     * sells the rubble as it is.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void rubblefiresintocommonstoneandminingstillyieldsrubble(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // What the pickaxe actually hands over, taken from the block rather than assumed.
        BlockPos absolute = helper.absolutePos(SPOT);
        helper.setBlock(SPOT, Blocks.STONE);
        ServerPlayer quarryman = player(level, "rubble-quarryman");
        quarryman.setItemInHand(InteractionHand.MAIN_HAND,
                ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3));
        new com.seggellion.britannia_mod.event.CustomBlockBreakHandler().onBlockBreak(
                new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(
                        level, absolute, level.getBlockState(absolute), quarryman));

        ItemStack rubble = dropsAround(level, absolute).stream()
                .filter(stack -> stack.getItem() instanceof GradeStoneItem)
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("mining stone produced no graded stone"));
        GradeStoneItem graded = (GradeStoneItem) rubble.getItem();
        if (!"Cobblestone".equalsIgnoreCase(graded.getStoneType(rubble))) {
            throw new GameTestAssertException("mining stone now yields "
                    + graded.getStoneType(rubble) + "; the rubble rule was changed");
        }
        JsonObject asRubble = ServerEconomyService.describeSaleItem(rubble);
        assertMember(asRubble, "subcategory", "rubble");
        assertMember(asRubble, "item_name", "cobblestone");

        // And the same stack, fired.
        ItemStack fired = smelt(level, rubble);
        if (fired.isEmpty() || !(fired.getItem() instanceof GradeStoneItem)) {
            throw new GameTestAssertException("firing rubble produced " + fired);
        }
        if (fired.getCount() != 1) {
            throw new GameTestAssertException("one rubble did not make one stone: " + fired.getCount());
        }
        if (!"Stone".equalsIgnoreCase(((GradeStoneItem) fired.getItem()).getStoneType(fired))) {
            throw new GameTestAssertException("firing rubble produced "
                    + ((GradeStoneItem) fired.getItem()).getStoneType(fired));
        }

        JsonObject asStone = ServerEconomyService.describeSaleItem(fired);
        assertMember(asStone, "category", "stone");
        assertMember(asStone, "subcategory", "common");
        assertMember(asStone, "item_name", "stone");
        helper.succeed();
    }

    /**
     * The grade the miner earned survives the fire.
     *
     * <p>A fixed grade in the recipe would have flattened the 1-5 roll into a number somebody
     * chose, and a missing one reads back as zero, which {@code EntityStoneMerchant} skips
     * entirely. The conversion changes what the stone is and nothing else.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void firingrubblekeepsitsgrade(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int grade = 1; grade <= 5; grade++) {
            ItemStack rubble = gradedStone("Cobblestone");
            ((GradeStoneItem) rubble.getItem()).setGradeValue(rubble, grade);

            ItemStack fired = smelt(level, rubble);
            if (fired.isEmpty()) {
                throw new GameTestAssertException("grade " + grade + " rubble did not smelt");
            }
            int kept = ((GradeStoneItem) fired.getItem()).getGradeValue(fired);
            if (kept != grade) {
                throw new GameTestAssertException(
                        "grade " + grade + " rubble fired into grade " + kept + " stone");
            }
        }
        helper.succeed();
    }

    /**
     * No other quarried stone takes this road.
     *
     * <p>The graded stone item is shared across every rock in the game, so a recipe that could not
     * read the type would have turned sandstone or limestone into generic common stone — and the
     * villa's sandstone and the plaster chain's limestone would both have leaked into it.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void noothergradedstonefiresintocommonstone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (String other : new String[] {
                "Sandstone", "Diorite", "Granite", "Limestone", "Andesite", "Blackrock" }) {
            ItemStack fired = smelt(level, gradedStone(other));
            if (fired.isEmpty()) continue;
            if (fired.getItem() instanceof GradeStoneItem produced
                    && "Stone".equalsIgnoreCase(produced.getStoneType(fired))) {
                throw new GameTestAssertException(
                        other + " fires into common stone, so any rock is every rock");
            }
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Iron                                                               */
    /* ------------------------------------------------------------------ */

    /**
     * An iron ingot says it is iron, and the Salvager may now hear it.
     *
     * <p>The ingot path is a branch of describeSaleItem rather than a row in the mapping table, so
     * it can only be exercised with the mod loaded. Iron was correctly classified and completely
     * unbuyable until Rails granted it to the Salvager; the mod side never changed, which is what
     * this pins.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void anironingotsellsasmetalingotsiron(GameTestHelper helper) {
        JsonObject described = ServerEconomyService.describeSaleItem(new ItemStack(Items.IRON_INGOT));
        assertMember(described, "category", "metal");
        assertMember(described, "subcategory", "ingots");
        assertMember(described, "material", "iron");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Fixtures                                                           */
    /* ------------------------------------------------------------------ */

    /** The result the server's recipe manager gives for smelting this stack, or EMPTY. */
    private static ItemStack smelt(ServerLevel level, ItemStack input) {
        Optional<net.minecraft.world.item.crafting.RecipeHolder<
                net.minecraft.world.item.crafting.SmeltingRecipe>> recipe =
                level.getServer().getRecipeManager()
                        .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level);
        return recipe.map(held -> held.value().assemble(new SingleRecipeInput(input),
                level.registryAccess())).orElse(ItemStack.EMPTY);
    }

    private static ItemStack gradedStone(String stoneType) {
        ItemStack stack = new ItemStack(ItemRegistry.GRADE_STONE_ITEM.get());
        ((GradeStoneItem) stack.getItem()).setStoneType(stack, stoneType);
        return stack;
    }

    private static ItemStack shovel() {
        return ToolRegistry.createShovel(UOMetalToolMaterial.IRON, 3);
    }

    /**
     * A named survival player. Milestone 6: this used to be a {@code FakePlayerFactory} player,
     * which meant these tests were asserting that automation could work a deposit — the very thing
     * the extraction policy now refuses. See {@link ManagedResourceTestPlayers}.
     */
    private static ServerPlayer player(ServerLevel level, String name) {
        ServerPlayer player = ManagedResourceTestPlayers.survival(level, name);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return player;
    }

    private static List<ItemStack> dropsAround(ServerLevel level, BlockPos absolute) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2.0D))
                .stream()
                .map(ItemEntity::getItem)
                .toList();
    }

    private static void assertMember(JsonObject described, String member, String expected) {
        if (!described.has(member) || !expected.equals(described.get(member).getAsString())) {
            throw new GameTestAssertException("expected " + member + '=' + expected
                    + " but the economy was told " + described);
        }
    }
}
