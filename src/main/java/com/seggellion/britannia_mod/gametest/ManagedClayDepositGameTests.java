package com.seggellion.britannia_mod.gametest;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.deposit.ManagedDepositExtraction;
import com.seggellion.britannia_mod.deposit.ManagedDeposits;
import com.seggellion.britannia_mod.economy.CommodityMappings;
import com.seggellion.britannia_mod.economy.ServerEconomyService;
import com.seggellion.britannia_mod.event.BlockRestoreHandler;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;

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
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * The managed clay deposit, in a world.
 *
 * <p>The loop the owner asked for is: a bed is placed, a shovel works it, the player holds raw
 * clay, the bed is empty and stays empty, and the shared restoration store brings it back. Each
 * of those is a test here, along with the refusals — because a resource whose only control is
 * "nothing else happens to work" is not controlled.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class ManagedClayDepositGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos BED = new BlockPos(1, 1, 1);

    private ManagedClayDepositGameTests() {
    }

    /* ------------------------------------------------------------------ */
    /*  Identity                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * The bed is a deposit; the vanilla material it looks like is not.
     *
     * <p>This is the whole of the location-control argument. Supply is decided by where an
     * administrator put a {@code britannia_mod:clay_deposit}, and never by how much
     * {@code minecraft:clay} world generation happened to lay down.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void onlythemodsownbedisadeposit(GameTestHelper helper) {
        if (ManagedDeposits.resolve(
                BlockRegistry.CLAY_DEPOSIT.get().defaultBlockState()).isEmpty()) {
            throw new GameTestAssertException("the clay bed does not resolve as a managed deposit");
        }
        if (ManagedDeposits.resolve(Blocks.CLAY.defaultBlockState()).isPresent()) {
            throw new GameTestAssertException(
                    "a naturally generated clay block resolves as a managed deposit, so every "
                    + "river bed in the world is now economic supply");
        }
        if (ManagedDeposits.resolve(Blocks.DIRT.defaultBlockState()).isPresent()) {
            throw new GameTestAssertException("ordinary ground resolves as a deposit");
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Tools                                                              */
    /* ------------------------------------------------------------------ */

    /** The shovel works it, and the player is holding one clay ball afterwards. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void ashovelextractsonerawclay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = placeBed(helper);
        ServerPlayer digger = player(level, "clay-digger");

        ManagedDepositExtraction.Result result = ManagedDepositExtraction.extract(
                level, absolute, digger, shovel());
        if (result != ManagedDepositExtraction.Result.EXTRACTED) {
            throw new GameTestAssertException("the authorized shovel was refused: " + result);
        }

        List<ItemStack> dropped = dropsAround(level, absolute);
        if (dropped.size() != 1) {
            throw new GameTestAssertException(
                    "one bed produced " + dropped.size() + " drops; a managed resource yields once");
        }
        ItemStack yield = dropped.get(0);
        if (!yield.is(Items.CLAY_BALL)) {
            throw new GameTestAssertException(
                    "the bed yielded " + yield.getItem() + " rather than the item the economy prices");
        }
        if (yield.getCount() != 1) {
            throw new GameTestAssertException(
                    "the bed yielded " + yield.getCount() + " clay; one bed is one unit");
        }
        helper.succeed();
    }

    /** The pickaxe is not a clay tool, and the bed is untouched afterwards. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void apickaxedoesnotworkaclaybed(GameTestHelper helper) {
        refused(helper, ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3), "the pickaxe");
    }

    /** Neither is an axe. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void anaxedoesnotworkaclaybed(GameTestHelper helper) {
        refused(helper, new ItemStack(ItemRegistry.TWO_HANDED_AXE.get()), "the axe");
    }

    /** Nor a bare hand. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void abarehanddoesnotworkaclaybed(GameTestHelper helper) {
        refused(helper, ItemStack.EMPTY, "an empty hand");
    }

    /**
     * Nor a vanilla shovel.
     *
     * <p>The project's tool policy is a tag holding the mod's own tool, the way the skinning
     * knife and the grain blade already work. An iron shovel is a shovel; it is not an authorized
     * one, and nothing about being the right shape gets it past the tag.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void avanillashoveldoesnotworkaclaybed(GameTestHelper helper) {
        refused(helper, new ItemStack(Items.IRON_SHOVEL), "a vanilla shovel");
    }

    /* ------------------------------------------------------------------ */
    /*  Exhaustion                                                         */
    /* ------------------------------------------------------------------ */

    /**
     * Worked once, empty at once, and a second attempt yields nothing.
     *
     * <p>Exhaustion is not a flag: the bed is gone, so the rule resolves no deposit and there is
     * nothing to refuse. That is what makes it impossible to work around.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void aworkedbedisexhaustedandrefusesasecondgo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = placeBed(helper);
        ServerPlayer digger = player(level, "clay-digger-b");

        ManagedDepositExtraction.extract(level, absolute, digger, shovel());
        if (ManagedDeposits.resolve(level.getBlockState(absolute)).isPresent()) {
            throw new GameTestAssertException("the bed is still standing after being worked");
        }

        int before = dropsAround(level, absolute).size();
        ManagedDepositExtraction.Result second = ManagedDepositExtraction.extract(
                level, absolute, digger, shovel());
        if (second != ManagedDepositExtraction.Result.NOT_A_DEPOSIT) {
            throw new GameTestAssertException(
                    "an exhausted bed answered " + second + " rather than refusing");
        }
        if (dropsAround(level, absolute).size() != before) {
            throw new GameTestAssertException("an exhausted bed yielded clay a second time");
        }
        helper.succeed();
    }

    /** Working a bed does not disturb what is beside it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void extractiondoesnottouchneighbouringblocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = placeBed(helper);
        helper.setBlock(BED.east(), Blocks.WHITE_WOOL);
        helper.setBlock(BED.west(), BlockRegistry.CLAY_DEPOSIT.get());

        ManagedDepositExtraction.extract(level, absolute, player(level, "clay-digger-c"), shovel());

        helper.assertBlockPresent(Blocks.WHITE_WOOL, BED.east());
        helper.assertBlockPresent(BlockRegistry.CLAY_DEPOSIT.get(), BED.west());
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Regeneration                                                       */
    /* ------------------------------------------------------------------ */

    /**
     * The worked bed is recorded for restoration, and restoration puts that same bed back.
     *
     * <p>The six-hour delay is not waited out — the record's timestamp is aged instead, which is
     * the only part of the shared handler that depends on the clock — so what is proven is that
     * the bed is enrolled in the ordinary mechanism and that the mechanism returns a clay
     * deposit to the exact position it came from.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aworkedbedisrecordedandcomesback(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = placeBed(helper);
        ServerPlayer digger = player(level, "clay-digger-d");

        ManagedDepositExtraction.extract(level, absolute, digger, shovel());

        BrokenBlockDataStorage storage = BrokenBlockDataStorage.get(level);
        BrokenBlockData record = storage.getBrokenBlocks().get(absolute);
        if (record == null) {
            throw new GameTestAssertException(
                    "the worked bed was not enrolled for restoration, so it would never return");
        }
        if (!record.originalState.is(BlockRegistry.CLAY_DEPOSIT.get())) {
            throw new GameTestAssertException(
                    "restoration would return " + record.originalState.getBlock() + " instead of the bed");
        }
        if (!digger.getUUID().equals(record.playerUUID)) {
            throw new GameTestAssertException("the restoration record credits the wrong player");
        }
        if (System.currentTimeMillis() - record.brokenTime >= BlockRestoreHandler.RESTORE_DELAY) {
            throw new GameTestAssertException(
                    "a freshly worked bed is already due to restore, so nothing was ever depleted");
        }

        // The yield is picked up first, as a player would. The shared handler refuses to restore
        // into a cell an entity is standing in, and the clay the bed just dropped is lying in it.
        level.getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2.0D))
                .forEach(ItemEntity::discard);

        // Age the record past the delay and let the server's own BlockRestoreHandler run. Nothing
        // is simulated: this is the listener the live server ticks, restoring on its own terms
        // into a cell it has checked is free.
        storage.add(new BrokenBlockData(record.pos, record.originalState,
                record.brokenTime - BlockRestoreHandler.RESTORE_DELAY - 1_000L, record.playerUUID));

        helper.runAfterDelay(5L, () -> {
            helper.assertBlockPresent(BlockRegistry.CLAY_DEPOSIT.get(), BED);
            if (storage.getBrokenBlocks().containsKey(absolute)) {
                throw new GameTestAssertException(
                        "the restoration record survived the restore, so the bed would return twice");
            }
            helper.succeed();
        });
    }

    /* ------------------------------------------------------------------ */
    /*  Economy                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * What the bed hands the player is exactly the item the economy prices.
     *
     * <p>The commodity JSON itself is pinned in {@code ClayCommodityClassificationTest}, driven
     * through {@code describeSaleItem}. What can only be checked here is that the thing the
     * deposit actually produces in a world is that same item and not a look-alike.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void theextracteditemistheonetheeconomyprices(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = placeBed(helper);
        ManagedDepositExtraction.extract(level, absolute, player(level, "clay-seller"), shovel());

        List<ItemStack> dropped = dropsAround(level, absolute);
        if (dropped.isEmpty()) {
            throw new GameTestAssertException("nothing was extracted, so nothing can be sold");
        }
        ItemStack yield = dropped.get(0);
        if (!yield.is(ManagedDeposits.CLAY.extractedItem().get())) {
            throw new GameTestAssertException(
                    "the bed yielded " + yield.getItem() + ", not the catalogued commodity item");
        }
        if (CommodityMappings.forStack(yield).isEmpty()) {
            throw new GameTestAssertException(
                    "what the bed yields carries no commodity, so no trader could accept it");
        }

        // Through the real serializer, which is what the buyback quote and the settlement both
        // send. A mapping the serializer did not reach would price nothing.
        JsonObject described = ServerEconomyService.describeSaleItem(yield);
        assertMember(described, "category", "clay");
        assertMember(described, "subcategory", "raw");
        assertMember(described, "commodity_key", "clay");
        assertMember(described, "item_name", "clay");
        if (!described.has("weight") || described.get("weight").getAsDouble() != 1.0D) {
            throw new GameTestAssertException(
                    "one clay ball did not post one weight unit: " + described);
        }
        helper.succeed();
    }

    /**
     * A finished brick wall is still a wall.
     *
     * <p>Rails accepts {@code clay|raw|clay} and refuses {@code clay|processed|fired_brick}. The
     * mod must not blur the two by describing construction blocks as the material they are made
     * of, or a player could dismantle a house into city clay supply.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void finishedbrickandroofblocksarenotrawclay(GameTestHelper helper) {
        for (String id : new String[] {
                "britannia_mod:brick_wall_bottom", "britannia_mod:brick_wall_top",
                "britannia_mod:tile_roof", "britannia_mod:tile_roof_flat" }) {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.ResourceLocation.parse(id));
            if (item == null || item == Items.AIR) continue;
            JsonObject described = ServerEconomyService.describeSaleItem(new ItemStack(item));
            if (described.has("category") && "clay".equals(described.get("category").getAsString())) {
                throw new GameTestAssertException(id + " sells as raw clay, so a house is a clay mine");
            }
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Fixtures                                                           */
    /* ------------------------------------------------------------------ */

    private static void refused(GameTestHelper helper, ItemStack tool, String what) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = placeBed(helper);
        ServerPlayer digger = player(level, "clay-refused");

        ManagedDepositExtraction.Result result =
                ManagedDepositExtraction.extract(level, absolute, digger, tool);
        if (result != ManagedDepositExtraction.Result.WRONG_TOOL) {
            throw new GameTestAssertException(what + " was not refused: " + result);
        }
        helper.assertBlockPresent(BlockRegistry.CLAY_DEPOSIT.get(), BED);
        if (!dropsAround(level, absolute).isEmpty()) {
            throw new GameTestAssertException(what + " produced clay anyway");
        }
        helper.succeed();
    }

    private static BlockPos placeBed(GameTestHelper helper) {
        helper.setBlock(BED, BlockRegistry.CLAY_DEPOSIT.get());
        return helper.absolutePos(BED);
    }

    private static ItemStack shovel() {
        return ToolRegistry.createShovel(UOMetalToolMaterial.IRON, 3);
    }

    private static ServerPlayer player(ServerLevel level, String name) {
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return player;
    }

    private static void assertMember(JsonObject described, String member, String expected) {
        if (!described.has(member) || !expected.equals(described.get(member).getAsString())) {
            throw new GameTestAssertException("expected " + member + '=' + expected
                    + " but the economy was told " + described);
        }
    }

    /** Everything the extraction dropped, which for a managed resource is one stack or none. */
    private static List<ItemStack> dropsAround(ServerLevel level, BlockPos absolute) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2.0D))
                .stream()
                .map(ItemEntity::getItem)
                .toList();
    }

}
