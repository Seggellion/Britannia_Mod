package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.economy.EconomicVendorPurchaseService;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Vendor/Trader Milestone 14: proves the exact-denomination settlement rules
 * against a real {@link ServerPlayer} inventory (same rationale as {@link
 * CoinConversionMigrationGameTests}: coin items are this mod's own registered
 * items, so a running server is required).
 *
 * <p>The ratified currency rules say settlement happens in EXACTLY the quoted
 * denomination -- no automatic conversion between copper/silver/gold, ever. The
 * legacy {@code MerchantEconomyService.reserveCoins} deliberately does the
 * opposite (it values the whole inventory in copper and makes change), which is
 * why the economic path has its own reservation and why these tests assert the
 * OTHER denominations are untouched, not merely that the total value works out.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class EconomicVendorPurchaseGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private EconomicVendorPurchaseGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void reservationRemovesExactlyTheQuotedDenominationAndNothingElse(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(ItemRegistry.GOLD_COIN.get(), 3));
        player.getInventory().add(new ItemStack(ItemRegistry.SILVER_COIN.get(), 5));
        player.getInventory().add(new ItemStack(ItemRegistry.COPPER_COIN.get(), 7));

        check(EconomicVendorPurchaseService.reserveExactDenomination(player, "silver", 3),
                "reservation must succeed with 5 silver on hand");

        check(count(player, ItemRegistry.GOLD_COIN.get()) == 3, "gold must be untouched");
        check(count(player, ItemRegistry.SILVER_COIN.get()) == 2, "exactly 3 silver must be removed");
        check(count(player, ItemRegistry.COPPER_COIN.get()) == 7, "copper must be untouched");
        helper.succeed();
    }

    /**
     * The defining case: a player rich in gold but short one silver CANNOT pay a
     * silver price. Under the legacy copper-valuation reserve, 10 gold would
     * easily cover 5 silver; under exact-denomination settlement it must not.
     */
    @GameTest(template = TEMPLATE)
    public static void otherDenominationsNeverFundAShortfall(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(ItemRegistry.GOLD_COIN.get(), 10));
        player.getInventory().add(new ItemStack(ItemRegistry.SILVER_COIN.get(), 4));

        check(!EconomicVendorPurchaseService.reserveExactDenomination(player, "silver", 5),
                "4 silver must not cover a 5 silver price, regardless of gold on hand");

        check(count(player, ItemRegistry.GOLD_COIN.get()) == 10, "a failed reservation must remove nothing");
        check(count(player, ItemRegistry.SILVER_COIN.get()) == 4, "a failed reservation must remove nothing");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void reservationDrainsAcrossMultipleStacksOfTheSameDenomination(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        // Three separate part-stacks of copper: 40 + 40 + 40 = 120.
        for (int i = 0; i < 3; i++) {
            player.getInventory().add(new ItemStack(ItemRegistry.COPPER_COIN.get(), 40));
        }

        check(EconomicVendorPurchaseService.reserveExactDenomination(player, "copper", 100),
                "reservation must succeed across split stacks");
        check(count(player, ItemRegistry.COPPER_COIN.get()) == 20,
                "exactly 100 copper must be removed across the stacks");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void refundRestoresTheReservedDenominationExactly(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(ItemRegistry.SILVER_COIN.get(), 8));

        check(EconomicVendorPurchaseService.reserveExactDenomination(player, "silver", 8),
                "reservation must succeed");
        check(count(player, ItemRegistry.SILVER_COIN.get()) == 0, "all 8 silver reserved");

        // The Rails rejection path (quote_changed, outage) refunds in kind.
        EconomicVendorPurchaseService.refundDenomination(player, "silver", 8);
        check(count(player, ItemRegistry.SILVER_COIN.get()) == 8, "refund must restore exactly 8 silver");
        check(count(player, ItemRegistry.GOLD_COIN.get()) == 0, "refund must not mint other denominations");
        check(count(player, ItemRegistry.COPPER_COIN.get()) == 0, "refund must not mint other denominations");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void unknownDenominationsFailClosed(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(ItemRegistry.COPPER_COIN.get(), 50));

        check(!EconomicVendorPurchaseService.reserveExactDenomination(player, "platinum", 1),
                "an unrecognized denomination must never reserve anything");
        check(EconomicVendorPurchaseService.countDenomination(player, "platinum") == 0,
                "an unrecognized denomination counts as zero");
        check(count(player, ItemRegistry.COPPER_COIN.get()) == 50, "inventory must be untouched");
        helper.succeed();
    }

    private static int count(ServerPlayer player, Item coinItem) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() == coinItem) total += stack.getCount();
        }
        return total;
    }

    /**
     * Throws {@link GameTestAssertException}, never {@link IllegalStateException}. When a check runs
     * inside a {@code succeedWhen} or sequence callback -- directly or through any helper called
     * from one -- {@code GameTestSequence.tickAndContinue} swallows only that one type, which is how
     * a polled condition retries until it holds. {@code GameTestInfo} ticks its sequences outside
     * any try/catch, so anything else escapes into the server tick loop and crashes the whole
     * GameTest server, ending the run and every result in it.
     */
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
