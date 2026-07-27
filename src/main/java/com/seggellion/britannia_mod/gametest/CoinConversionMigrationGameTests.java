package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.economy.MerchantEconomyService;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Milestone 11 prerequisite: proves the {@link MerchantEconomyService#giveChange}/{@code
 * countCoins} migration onto {@code CoinConversion} is behavior-preserving against a real
 * {@link ServerPlayer} inventory -- not just "compiles" or "the pure-math unit tests pass".
 *
 * <p>{@code giveChange}/{@code countCoins} both take a real {@code ServerPlayer} and mutate/read
 * its actual inventory ({@link ItemRegistry#GOLD_COIN}/{@code SILVER_COIN}/{@code COPPER_COIN}
 * item stacks), so a real running server is required for the same reason
 * {@link BankItemEligibilityGameTests} needs one: these are this mod's own
 * {@code DeferredRegister}-registered items, not populated outside an actual mod-loading
 * lifecycle. Coin counts here are always kept modest enough (at most a few hundred coins per
 * denomination) that every stack fits in a freshly cleared inventory without ever needing to
 * drop an overflow item on the ground -- a drop would make {@link #countCoinsDirectly} miscount
 * against the real, in-inventory total these tests assert against.
 *
 * <p>Expected coin splits below are computed by hand, directly from the canonical 1 gold = 100
 * silver = 10,000 copper ratio -- the same literal arithmetic {@code giveChange}'s inline
 * division/remainder chain used before this migration -- not by calling {@code CoinConversion}
 * itself, so this is an independent check that the migration did not change real output.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class CoinConversionMigrationGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private CoinConversionMigrationGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void exactlyOneCopperProducesExactlyOneCopperCoin(GameTestHelper helper) {
        assertGiveChangeProducesExactCoins(helper, 1, 0, 0, 1);
    }

    @GameTest(template = TEMPLATE)
    public static void exactlyOneSilversWorthProducesExactlyOneSilverCoin(GameTestHelper helper) {
        assertGiveChangeProducesExactCoins(helper, 100, 0, 1, 0);
    }

    @GameTest(template = TEMPLATE)
    public static void exactlyOneGoldsWorthProducesExactlyOneGoldCoin(GameTestHelper helper) {
        assertGiveChangeProducesExactCoins(helper, 10_000, 1, 0, 0);
    }

    @GameTest(template = TEMPLATE)
    public static void zeroProducesNoCoinsAtAll(GameTestHelper helper) {
        assertGiveChangeProducesExactCoins(helper, 0, 0, 0, 0);
    }

    @GameTest(template = TEMPLATE)
    public static void justUnderASilverIsAllCopper(GameTestHelper helper) {
        assertGiveChangeProducesExactCoins(helper, 99, 0, 0, 99);
    }

    @GameTest(template = TEMPLATE)
    public static void justUnderAGoldIsMaximalSilverAndCopper(GameTestHelper helper) {
        assertGiveChangeProducesExactCoins(helper, 9_999, 0, 99, 99);
    }

    @GameTest(template = TEMPLATE)
    public static void aLargeRealisticAmountProducesTheHandComputedCoinMix(GameTestHelper helper) {
        // 2,345,678 copper = 234 gold, 56 silver, 78 copper -- computed by hand from the
        // canonical ratio, independently of CoinConversion itself.
        assertGiveChangeProducesExactCoins(helper, 2_345_678, 234, 56, 78);
    }

    @GameTest(template = TEMPLATE)
    public static void giveChangeThenCountCoinsRoundTripsForARangeOfRealisticAmounts(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        int[] amounts = {0, 1, 99, 100, 9_999, 10_000, 250_050, 2_345_678};
        for (int amount : amounts) {
            player.getInventory().clearContent();
            MerchantEconomyService.giveChange(player, amount);
            int recovered = MerchantEconomyService.countCoins(player);
            check(recovered == amount,
                "countCoins did not recover the original amount " + amount + " after giveChange (got " + recovered + ")");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void countCoinsWeightsEachDenominationByTheCentralizedRatio(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();

        // Inserted directly (not via giveChange), to check countCoins' own migrated math in
        // isolation: 2 gold + 3 silver + 4 copper = 2*10000 + 3*100 + 4 = 20,304 copper.
        player.getInventory().add(new ItemStack(ItemRegistry.GOLD_COIN.get(), 2));
        player.getInventory().add(new ItemStack(ItemRegistry.SILVER_COIN.get(), 3));
        player.getInventory().add(new ItemStack(ItemRegistry.COPPER_COIN.get(), 4));

        int total = MerchantEconomyService.countCoins(player);
        check(total == 20_304, "countCoins did not apply the centralized ratio correctly, got " + total);
        helper.succeed();
    }

    private static void assertGiveChangeProducesExactCoins(
            GameTestHelper helper, int amount, int expectedGold, int expectedSilver, int expectedCopper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();

        MerchantEconomyService.giveChange(player, amount);

        int actualGold = countCoinsDirectly(player, ItemRegistry.GOLD_COIN.get());
        int actualSilver = countCoinsDirectly(player, ItemRegistry.SILVER_COIN.get());
        int actualCopper = countCoinsDirectly(player, ItemRegistry.COPPER_COIN.get());

        check(actualGold == expectedGold,
            "amount " + amount + ": expected " + expectedGold + " gold, got " + actualGold);
        check(actualSilver == expectedSilver,
            "amount " + amount + ": expected " + expectedSilver + " silver, got " + actualSilver);
        check(actualCopper == expectedCopper,
            "amount " + amount + ": expected " + expectedCopper + " copper, got " + actualCopper);

        helper.succeed();
    }

    /**
     * Sums real inventory stack counts for {@code coinItem} directly, independent of {@code
     * MerchantEconomyService.countCoins} -- these tests must not validate the migrated
     * {@code giveChange} by cross-checking it against the also-migrated {@code countCoins}
     * alone, which could let a shared mistake in both pass unnoticed.
     */
    private static int countCoinsDirectly(ServerPlayer player, Item coinItem) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() == coinItem) total += stack.getCount();
        }
        return total;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
