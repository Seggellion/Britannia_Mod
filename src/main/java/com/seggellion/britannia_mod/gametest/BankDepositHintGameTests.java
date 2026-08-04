package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.client.screen.bank.BankDepositHint;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Milestone 11: the inventory-shading hint mirrors the server's deposit routing.
 *
 * <p>The rules themselves are exhaustively covered by {@code BankItemEligibilityGameTests} and the
 * router by {@code BankingTransferPacketServiceGameTests}; what this pins is the <b>disjunction</b>
 * -- that the hint says yes to exactly the three categories the router accepts, so a cell never
 * shades dark and then deposits, or shades bright and then rejects.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankDepositHintGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private BankDepositHintGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void theThreeDepositableCategoriesAllReadAsDepositable(GameTestHelper helper) {
        check(BankDepositHint.isDepositable(new ItemStack(Items.DIAMOND, 5)), "an ordinary eligible item");
        check(BankDepositHint.isDepositable(new ItemStack(ItemRegistry.GOLD_COIN.get(), 37)), "a bare coin stack");
        check(BankDepositHint.isDepositable(new ItemStack(ItemRegistry.BANK_CHEQUE.get())), "a bank cheque");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void aContainerHoldingCoinsReadsAsNotDepositable(GameTestHelper helper) {
        ItemStack shulkerWithCoin = new ItemStack(Items.SHULKER_BOX);
        shulkerWithCoin.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(
                new ItemStack(ItemRegistry.SILVER_COIN.get(), 5)
        )));

        // The CURRENCY carve-out: coins cannot be smuggled into item banking inside a container,
        // and the shading must say so before the player tries.
        check(!BankDepositHint.isDepositable(shulkerWithCoin), "a shulker box holding coins must shade ineligible");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void anEmptyStackIsNotDepositable(GameTestHelper helper) {
        check(!BankDepositHint.isDepositable(ItemStack.EMPTY), "empty is nothing to deposit");
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
