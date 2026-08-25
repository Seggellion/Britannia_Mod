package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.currency.CoinSweep;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import com.seggellion.britannia_mod.service.banking.BankingCancelResult;
import com.seggellion.britannia_mod.service.banking.BankingConfirmResult;
import com.seggellion.britannia_mod.service.banking.BankingDepositAllCoinsClientPort;
import com.seggellion.britannia_mod.service.banking.BankingDepositAllCoinsLocalRejectionReason;
import com.seggellion.britannia_mod.service.banking.BankingDepositAllCoinsPrepareRequest;
import com.seggellion.britannia_mod.service.banking.BankingDepositAllCoinsPrepareResult;
import com.seggellion.britannia_mod.service.banking.BankingDepositAllCoinsProxyService;
import com.seggellion.britannia_mod.service.banking.BankingDepositAllCoinsResult;
import com.seggellion.britannia_mod.service.banking.BankingOperationRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Milestone 6b: Deposit All Coins, NeoForge half.
 *
 * <p>Covers the Playbook Milestone 6 cases that belong to this side of the boundary -- what the
 * sweep counts, what it refuses to count, and what happens to the coins across each way the
 * transaction can end. The cases that belong to Rails (persistence rejection, the balance
 * ceiling, duplicate confirm idempotency, expiry) are covered by 6a's own suite; asserting them
 * again through a fake client would only test the fake.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankingDepositAllCoinsProxyServiceGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BANK_TYPE_KEY = "bank_teller";

    private BankingDepositAllCoinsProxyServiceGameTests() {
    }

    // ---------- What the sweep counts ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void sweepsOneDenominationAndSendsTheOtherTwoAsZero(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        player.getInventory().setItem(0, new ItemStack(ItemRegistry.GOLD_COIN.get(), 12));

        CoinSweep sweep = CoinSweep.of(player.getInventory());
        check(sweep.gold() == 12, "expected 12 gold, got " + sweep.gold());
        check(sweep.silver() == 0, "silver should be zero");
        check(sweep.copper() == 0, "copper should be zero");
        check(!sweep.isEmpty(), "a sweep holding gold is not empty");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void totalsMultipleStacksOfTheSameDenominationAcrossSlots(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        player.getInventory().setItem(0, new ItemStack(ItemRegistry.COPPER_COIN.get(), 99));
        player.getInventory().setItem(7, new ItemStack(ItemRegistry.COPPER_COIN.get(), 99));
        player.getInventory().setItem(30, new ItemStack(ItemRegistry.COPPER_COIN.get(), 52));

        CoinSweep sweep = CoinSweep.of(player.getInventory());
        check(sweep.copper() == 250, "expected 250 copper, got " + sweep.copper());
        check(sweep.stacks().size() == 3, "expected three counted stacks, got " + sweep.stacks().size());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void totalsAllThreeDenominationsTogether(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        player.getInventory().setItem(0, new ItemStack(ItemRegistry.GOLD_COIN.get(), 3));
        player.getInventory().setItem(1, new ItemStack(ItemRegistry.SILVER_COIN.get(), 47));
        player.getInventory().setItem(2, new ItemStack(ItemRegistry.COPPER_COIN.get(), 92));

        CoinSweep sweep = CoinSweep.of(player.getInventory());
        check(sweep.gold() == 3 && sweep.silver() == 47 && sweep.copper() == 92,
                "wrong totals: " + sweep.gold() + "/" + sweep.silver() + "/" + sweep.copper());
        helper.succeed();
    }

    // ---------- What the sweep refuses to count ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void ordinaryItemsAreNeverSwept(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 64));
        player.getInventory().setItem(1, new ItemStack(Items.OAK_LOG, 12));

        CoinSweep sweep = CoinSweep.of(player.getInventory());
        check(sweep.isEmpty(), "ordinary items must not be swept");
        check(sweep.stacks().isEmpty(), "no stack should have been counted");
        helper.succeed();
    }

    /**
     * A shulker box full of coins is not a coin stack. {@code CurrencyItemRegistry} classifies by
     * top-level item identity only, deliberately, and Deposit All Coins inherits that: it must
     * never empty a container the player is using for storage.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void containersAreNeverSweptEvenWhenTheyHoldCoins(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        player.getInventory().setItem(0, new ItemStack(Items.SHULKER_BOX, 1));
        player.getInventory().setItem(1, new ItemStack(Items.CHEST, 1));

        CoinSweep sweep = CoinSweep.of(player.getInventory());
        check(sweep.isEmpty(), "a container must never be swept");
        helper.succeed();
    }

    /** Deposit All Coins deposits coins. It never quietly redeems a cheque the player was holding. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void bankChequesAreNeverSwept(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        player.getInventory().setItem(0, new ItemStack(ItemRegistry.BANK_CHEQUE.get(), 1));

        CoinSweep sweep = CoinSweep.of(player.getInventory());
        check(sweep.isEmpty(), "a bank cheque must never be swept");
        helper.succeed();
    }

    /**
     * Armor and offhand are outside the swept range. A player wearing or holding a coin keeps it.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void onlyTheMainInventoryAndHotbarAreSwept(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        player.getInventory().setItem(0, new ItemStack(ItemRegistry.GOLD_COIN.get(), 5));
        player.getInventory().offhand.set(0, new ItemStack(ItemRegistry.GOLD_COIN.get(), 64));

        CoinSweep sweep = CoinSweep.of(player.getInventory());
        check(sweep.gold() == 5, "the offhand stack must not be counted, got " + sweep.gold());
        for (CoinSweep.SweptStack swept : sweep.stacks()) {
            check(swept.slotIndex() < CoinSweep.SWEPT_SLOT_COUNT, "swept a slot outside the main inventory");
        }
        helper.succeed();
    }

    // ---------- No coins ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void anEmptyPurseIsRefusedLocallyWithoutContactingRails(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        FakeClient fake = new FakeClient();
        BankingDepositAllCoinsProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingDepositAllCoinsResult> future =
                    BankingDepositAllCoinsProxyService.triggerDepositAllCoinsForTesting(player, teller);

            helper.succeedWhen(() -> {
                check(future.isDone(), "the sweep did not complete");
                BankingDepositAllCoinsResult result = future.join();
                check(result instanceof BankingDepositAllCoinsResult.RejectedLocally,
                        "expected RejectedLocally, got " + result);
                check(((BankingDepositAllCoinsResult.RejectedLocally) result).reason()
                                == BankingDepositAllCoinsLocalRejectionReason.NO_COINS,
                        "expected NO_COINS");
                check(fake.prepareRequests.isEmpty(), "an empty sweep must not reach Rails");
            });
        } finally {
            BankingDepositAllCoinsProxyService.resetClientForTesting();
            BankingDepositAllCoinsProxyService.resetInFlightTrackingForTesting();
        }
    }

    // ---------- The happy path ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void happyPathSendsAllThreeTotalsAndRemovesEveryCountedStack(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(0, new ItemStack(ItemRegistry.GOLD_COIN.get(), 3));
        player.getInventory().setItem(4, new ItemStack(ItemRegistry.SILVER_COIN.get(), 47));
        player.getInventory().setItem(9, new ItemStack(ItemRegistry.COPPER_COIN.get(), 92));
        player.getInventory().setItem(5, new ItemStack(Items.DIAMOND, 8));

        UUID operationId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(
                new BankingDepositAllCoinsPrepareResult.Success(operationId));
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingDepositAllCoinsProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingDepositAllCoinsResult> future =
                    BankingDepositAllCoinsProxyService.triggerDepositAllCoinsForTesting(player, teller);

            helper.succeedWhen(() -> {
                check(future.isDone(), "the sweep did not complete");
                BankingDepositAllCoinsResult result = future.join();
                check(result instanceof BankingDepositAllCoinsResult.Confirmed, "expected Confirmed, got " + result);

                check(fake.prepareRequests.size() == 1, "expected exactly one prepare request");
                BankingDepositAllCoinsPrepareRequest sent = fake.prepareRequests.get(0);
                check(sent.gold() == 3, "wrong gold sent: " + sent.gold());
                check(sent.silver() == 47, "wrong silver sent: " + sent.silver());
                check(sent.copper() == 92, "wrong copper sent: " + sent.copper());

                check(player.getInventory().getItem(0).isEmpty(), "gold was not removed");
                check(player.getInventory().getItem(4).isEmpty(), "silver was not removed");
                check(player.getInventory().getItem(9).isEmpty(), "copper was not removed");
                check(player.getInventory().getItem(5).getCount() == 8, "the diamonds must be untouched");
            });
        } finally {
            BankingDepositAllCoinsProxyService.resetClientForTesting();
            BankingDepositAllCoinsProxyService.resetInFlightTrackingForTesting();
        }
    }

    // ---------- Concurrent inventory mutation ----------

    /**
     * A counted stack shrinking during the round trip must abort the whole sweep -- not remove
     * what is left. Rails has already prepared a credit for the original totals, so a partial
     * removal would pay the player for coins they still hold.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aCountedStackShrinkingMidFlightAbortsTheWholeSweepAndRemovesNothing(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(0, new ItemStack(ItemRegistry.GOLD_COIN.get(), 10));
        player.getInventory().setItem(1, new ItemStack(ItemRegistry.SILVER_COIN.get(), 20));

        UUID operationId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> {
            // Spend some of the gold between the sweep and the removal.
            player.getInventory().setItem(0, new ItemStack(ItemRegistry.GOLD_COIN.get(), 4));
            return CompletableFuture.completedFuture(new BankingDepositAllCoinsPrepareResult.Success(operationId));
        };
        fake.cancelBehavior = () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        BankingDepositAllCoinsProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingDepositAllCoinsResult> future =
                    BankingDepositAllCoinsProxyService.triggerDepositAllCoinsForTesting(player, teller);

            helper.succeedWhen(() -> {
                check(future.isDone(), "the sweep did not complete");
                BankingDepositAllCoinsResult result = future.join();
                check(result instanceof BankingDepositAllCoinsResult.RemovalFailed,
                        "expected RemovalFailed, got " + result);

                check(player.getInventory().getItem(0).getCount() == 4, "the remaining gold must be left alone");
                check(player.getInventory().getItem(1).getCount() == 20, "the silver must not be removed either");
                check(fake.cancelRequests.size() == 1, "the prepared operation must be cancelled");
                check(fake.confirmRequests.isEmpty(), "confirm must never be reached");
            });
        } finally {
            BankingDepositAllCoinsProxyService.resetClientForTesting();
            BankingDepositAllCoinsProxyService.resetInFlightTrackingForTesting();
        }
    }

    /**
     * Gaining coins mid-flight is not a failure. The extra coins simply are not part of this
     * operation -- requiring an exact match would abort a sweep because the player picked
     * something up, for no safety gain.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void gainingCoinsMidFlightStillSucceedsAndLeavesTheSurplus(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(0, new ItemStack(ItemRegistry.COPPER_COIN.get(), 10));

        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> {
            player.getInventory().setItem(0, new ItemStack(ItemRegistry.COPPER_COIN.get(), 25));
            return CompletableFuture.completedFuture(
                    new BankingDepositAllCoinsPrepareResult.Success(UUID.randomUUID()));
        };
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingDepositAllCoinsProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingDepositAllCoinsResult> future =
                    BankingDepositAllCoinsProxyService.triggerDepositAllCoinsForTesting(player, teller);

            helper.succeedWhen(() -> {
                check(future.isDone(), "the sweep did not complete");
                check(future.join() instanceof BankingDepositAllCoinsResult.Confirmed,
                        "expected Confirmed, got " + future.join());
                check(fake.prepareRequests.get(0).copper() == 10, "only the counted 10 should have been sent");
                check(player.getInventory().getItem(0).getCount() == 15,
                        "the 15 surplus coins must remain, got " + player.getInventory().getItem(0).getCount());
            });
        } finally {
            BankingDepositAllCoinsProxyService.resetClientForTesting();
            BankingDepositAllCoinsProxyService.resetInFlightTrackingForTesting();
        }
    }

    // ---------- Duplicate request ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aSecondSweepWhileOneIsInFlightIsRefusedWithoutTouchingTheInventory(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(0, new ItemStack(ItemRegistry.GOLD_COIN.get(), 5));

        FakeClient fake = new FakeClient();
        CompletableFuture<BankingDepositAllCoinsPrepareResult> held = new CompletableFuture<>();
        fake.prepareBehavior = () -> held;
        BankingDepositAllCoinsProxyService.useClientForTesting(fake);

        try {
            // The first sweep is left parked on an incomplete prepare, which is what holds the
            // in-flight entry while the duplicate is attempted.
            BankingDepositAllCoinsProxyService.triggerDepositAllCoinsForTesting(player, teller);
            CompletableFuture<BankingDepositAllCoinsResult> second =
                    BankingDepositAllCoinsProxyService.triggerDepositAllCoinsForTesting(player, teller);

            // Asserted synchronously rather than through succeedWhen: the duplicate is refused
            // before any await, and succeedWhen polls on later ticks -- by which time this
            // method's own finally block has already released the parked prepare.
            check(second.isDone(), "the duplicate should have been refused immediately");
            check(second.join() instanceof BankingDepositAllCoinsResult.LocalFailure,
                    "expected LocalFailure for the duplicate, got " + second.join());
            check(fake.prepareRequests.size() == 1, "the duplicate must not have sent a second prepare");
            check(player.getInventory().getItem(0).getCount() == 5, "nothing should have been removed yet");
            helper.succeed();
        } finally {
            held.complete(new BankingDepositAllCoinsPrepareResult.LocalFailure("test_teardown"));
            BankingDepositAllCoinsProxyService.resetClientForTesting();
            BankingDepositAllCoinsProxyService.resetInFlightTrackingForTesting();
        }
    }

    // ---------- Rails refusals ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aRailsRejectionAtPrepareLeavesEveryCoinInPlace(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(0, new ItemStack(ItemRegistry.GOLD_COIN.get(), 7));

        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(
                new BankingDepositAllCoinsPrepareResult.Rejected(
                        com.seggellion.britannia_mod.service.banking.BankingTransferOutcome.BALANCE_CAPACITY_EXCEEDED, false));
        BankingDepositAllCoinsProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingDepositAllCoinsResult> future =
                    BankingDepositAllCoinsProxyService.triggerDepositAllCoinsForTesting(player, teller);

            helper.succeedWhen(() -> {
                check(future.isDone(), "the sweep did not complete");
                check(future.join() instanceof BankingDepositAllCoinsResult.Rejected,
                        "expected Rejected, got " + future.join());
                check(player.getInventory().getItem(0).getCount() == 7,
                        "a prepare rejection must destroy nothing");
                check(fake.confirmRequests.isEmpty(), "confirm must never be reached");
            });
        } finally {
            BankingDepositAllCoinsProxyService.resetClientForTesting();
            BankingDepositAllCoinsProxyService.resetInFlightTrackingForTesting();
        }
    }

    /**
     * The coins are already gone by the time confirm answers, so a reconciliation-required result
     * must never read as ordinary success and must never put them back.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void reconciliationRequiredAtConfirmIsNotTreatedAsSuccess(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(0, new ItemStack(ItemRegistry.GOLD_COIN.get(), 6));

        UUID operationId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(
                new BankingDepositAllCoinsPrepareResult.Success(operationId));
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.ReconciliationRequired());
        BankingDepositAllCoinsProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingDepositAllCoinsResult> future =
                    BankingDepositAllCoinsProxyService.triggerDepositAllCoinsForTesting(player, teller);

            helper.succeedWhen(() -> {
                check(future.isDone(), "the sweep did not complete");
                check(future.join() instanceof BankingDepositAllCoinsResult.ReconciliationRequired,
                        "expected ReconciliationRequired, got " + future.join());
                check(player.getInventory().getItem(0).isEmpty(),
                        "the coins are gone by this point and must not be fabricated back");
            });
        } finally {
            BankingDepositAllCoinsProxyService.resetClientForTesting();
            BankingDepositAllCoinsProxyService.resetInFlightTrackingForTesting();
        }
    }

    // ---------- Milestone 6b-ii: what the client is actually told ----------

    /**
     * The whole point of 6b-ii's result vocabulary: "thy purse was empty" must not arrive as the
     * teller refusing the player. Exercised through the real packet handler, not the proxy
     * service, because the mapping being tested lives in the handler.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void anEmptyPurseReportsNothingToDepositRatherThanACleanRejection(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        FakeResultSender resultSender = new FakeResultSender();
        com.seggellion.britannia_mod.service.banking.BankingTransferPacketService
                .useResultSenderForTesting(resultSender);
        BankingDepositAllCoinsProxyService.useClientForTesting(new FakeClient());

        try {
            com.seggellion.britannia_mod.service.banking.BankingTransferPacketService.handleDepositAllCoins(
                    player,
                    new com.seggellion.britannia_mod.network.payload.BankDepositAllCoinsRequestC2SPayload(teller.getId())
            );

            helper.succeedWhen(() -> {
                check(resultSender.calls.size() == 1, "expected exactly one result, got " + resultSender.calls);
                var sent = resultSender.calls.get(0);
                check(sent.kind() == com.seggellion.britannia_mod.network.payload
                                .BankTransferResultS2CPayload.Kind.NOTHING_TO_DEPOSIT,
                        "expected NOTHING_TO_DEPOSIT, got " + sent.kind());
            });
        } finally {
            com.seggellion.britannia_mod.service.banking.BankingTransferPacketService.resetResultSenderForTesting();
            BankingDepositAllCoinsProxyService.resetClientForTesting();
            BankingDepositAllCoinsProxyService.resetInFlightTrackingForTesting();
        }
    }

    /** A Rails balance-ceiling refusal keeps its own identity too, rather than flattening. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aBalanceCeilingRefusalKeepsItsOwnKind(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(0, new ItemStack(ItemRegistry.GOLD_COIN.get(), 9));

        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(
                new BankingDepositAllCoinsPrepareResult.Rejected(
                        com.seggellion.britannia_mod.service.banking.BankingTransferOutcome.BALANCE_CAPACITY_EXCEEDED, false));
        BankingDepositAllCoinsProxyService.useClientForTesting(fake);

        FakeResultSender resultSender = new FakeResultSender();
        com.seggellion.britannia_mod.service.banking.BankingTransferPacketService
                .useResultSenderForTesting(resultSender);

        try {
            com.seggellion.britannia_mod.service.banking.BankingTransferPacketService.handleDepositAllCoins(
                    player,
                    new com.seggellion.britannia_mod.network.payload.BankDepositAllCoinsRequestC2SPayload(teller.getId())
            );

            helper.succeedWhen(() -> {
                check(resultSender.calls.size() == 1, "expected exactly one result, got " + resultSender.calls);
                check(resultSender.calls.get(0).kind() == com.seggellion.britannia_mod.network.payload
                                .BankTransferResultS2CPayload.Kind.BALANCE_CAPACITY_EXCEEDED,
                        "expected BALANCE_CAPACITY_EXCEEDED, got " + resultSender.calls.get(0).kind());
                check(player.getInventory().getItem(0).getCount() == 9, "nothing may be destroyed on a refusal");
            });
        } finally {
            com.seggellion.britannia_mod.service.banking.BankingTransferPacketService.resetResultSenderForTesting();
            BankingDepositAllCoinsProxyService.resetClientForTesting();
            BankingDepositAllCoinsProxyService.resetInFlightTrackingForTesting();
        }
    }

    // ---------- Helpers ----------

    private static final class FakeResultSender
            implements com.seggellion.britannia_mod.service.banking.BankingTransferPacketService.ResultSender {
        final List<com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload> calls =
                new CopyOnWriteArrayList<>();

        @Override
        public void send(
                ServerPlayer player,
                com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload.Operation operation,
                com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload.Kind kind
        ) {
            calls.add(new com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload(operation, kind));
        }
    }

    private static ServerPlayer freshPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        return player;
    }

    private static ServerPlayer setUpPlayer(GameTestHelper helper, ServiceNpcEntity teller) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.teleportTo(teller.getX() + 1.0, teller.getY(), teller.getZ());
        return player;
    }

    private static void installBankRegistry() {
        ServiceNpcTypeDefinition bankTeller = new ServiceNpcTypeDefinition(
                BANK_TYPE_KEY, "Bank Teller", "banker", "britannia_mod:service_npc",
                "bank_teller_default", List.of("bank.open"), true, true, 1
        );
        ServiceNpcRegistryCache.replace(
                new ServiceNpcRegistrySnapshot(1, 1, Map.of(), Map.of(bankTeller.key(), bankTeller), Map.of())
        );
    }

    private static ServiceNpcEntity spawnBankTeller(GameTestHelper helper) {
        ServiceNpcEntity npc = helper.spawn(EntityRegistry.SERVICE_NPC.get(), new BlockPos(1, 1, 1));
        npc.setWorldNpcPublicId(UUID.randomUUID());
        npc.setServiceNpcTypeKey(BANK_TYPE_KEY);
        return npc;
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

    private static final class FakeClient implements BankingDepositAllCoinsClientPort {
        java.util.function.Supplier<CompletableFuture<BankingDepositAllCoinsPrepareResult>> prepareBehavior =
                () -> { throw new IllegalStateException("prepareDepositAllCoins() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingConfirmResult>> confirmBehavior =
                () -> { throw new IllegalStateException("confirm() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingCancelResult>> cancelBehavior =
                () -> { throw new IllegalStateException("cancel() was not expected to be called in this test"); };

        final List<BankingDepositAllCoinsPrepareRequest> prepareRequests = new CopyOnWriteArrayList<>();
        final List<BankingOperationRequest> confirmRequests = new CopyOnWriteArrayList<>();
        final List<BankingOperationRequest> cancelRequests = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<BankingDepositAllCoinsPrepareResult> prepareDepositAllCoins(
                MinecraftServer server, BankingDepositAllCoinsPrepareRequest request
        ) {
            prepareRequests.add(request);
            return prepareBehavior.get();
        }

        @Override
        public CompletableFuture<BankingConfirmResult> confirm(MinecraftServer server, BankingOperationRequest request) {
            confirmRequests.add(request);
            return confirmBehavior.get();
        }

        @Override
        public CompletableFuture<BankingCancelResult> cancel(MinecraftServer server, BankingOperationRequest request) {
            cancelRequests.add(request);
            return cancelBehavior.get();
        }
    }
}
