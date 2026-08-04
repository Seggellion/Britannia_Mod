package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.item.BankItemCodec;
import com.seggellion.britannia_mod.bank.item.BankItemFingerprint;
import com.seggellion.britannia_mod.bank.item.BankItemSchemaVersion;
import com.seggellion.britannia_mod.bank.item.BankItemWeight;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.network.payload.BankCurrencyWithdrawalRequestC2SPayload;
import com.seggellion.britannia_mod.network.payload.BankDepositRequestC2SPayload;
import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload;
import com.seggellion.britannia_mod.network.payload.BankWithdrawalRequestC2SPayload;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import com.seggellion.britannia_mod.quest.QuestRewardService;
import com.seggellion.britannia_mod.quest.network.QuestModels;
import com.seggellion.britannia_mod.service.banking.BankItemSummary;
import com.seggellion.britannia_mod.service.banking.BankingCancelResult;
import com.seggellion.britannia_mod.service.banking.BankingConfirmResult;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyDepositClientPort;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyWithdrawalClientPort;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyWithdrawalPrepareResult;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyWithdrawalProxyService;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyDepositPrepareRequest;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyDepositPrepareResult;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyDepositProxyService;
import com.seggellion.britannia_mod.service.banking.BankingDepositClientPort;
import com.seggellion.britannia_mod.service.banking.BankingDepositPrepareRequest;
import com.seggellion.britannia_mod.service.banking.BankingDepositPrepareResult;
import com.seggellion.britannia_mod.service.banking.BankingDepositProxyService;
import com.seggellion.britannia_mod.service.banking.BankingOpenAccount;
import com.seggellion.britannia_mod.service.banking.BankingOpenClient;
import com.seggellion.britannia_mod.service.banking.BankingOpenClientResult;
import com.seggellion.britannia_mod.service.banking.BankingOperationRequest;
import com.seggellion.britannia_mod.service.banking.BankingProxyService;
import com.seggellion.britannia_mod.service.banking.BankingTransferOutcome;
import com.seggellion.britannia_mod.service.banking.BankingTransferPacketService;
import com.seggellion.britannia_mod.service.banking.BankingWithdrawalClientPort;
import com.seggellion.britannia_mod.service.banking.BankingWithdrawalPrepareRequest;
import com.seggellion.britannia_mod.service.banking.BankingWithdrawalPrepareResult;
import com.seggellion.britannia_mod.service.banking.BankingWithdrawalProxyService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Milestone 9 NeoForge Slice 3a: {@link BankingTransferPacketService} exercised as a real
 * client would reach it -- through the actual {@link BankDepositRequestC2SPayload}/{@link
 * BankWithdrawalRequestC2SPayload} content, not {@link BankingDepositProxyService}'s or {@link
 * BankingWithdrawalProxyService}'s own test-support trigger methods directly. Every fake here
 * substitutes only the Rails-facing client ports (mirroring every earlier Slice's own
 * established pattern); nothing about the deposit/withdrawal proxies themselves is touched or
 * bypassed.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankingTransferPacketServiceGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BANK_TYPE_KEY = "bank_teller";
    private static final int SLOT = 0;

    private BankingTransferPacketServiceGameTests() {
    }

    // ---------- Full deposit through the real packet path ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void fullDepositThroughRealPacketPathEndsConfirmedAndRefreshesTheAccount(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND, 5));

        UUID operationId = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        FakeDepositClient depositClient = new FakeDepositClient();
        depositClient.prepareBehavior = () -> CompletableFuture.completedFuture(
                new BankingDepositPrepareResult.Success(operationId, bankItemId));
        depositClient.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingDepositProxyService.useClientForTesting(depositClient);

        java.util.concurrent.atomic.AtomicReference<List<?>> refreshedBankItems = new java.util.concurrent.atomic.AtomicReference<>();
        wireOpenRefresh(new BankingOpenAccount(UUID.randomUUID(), "global", null, 250, 5.0, 0, 0, 0, 2));
        BankingProxyService.useAccountScreenSenderForTesting((p, t, account, bankItems) -> refreshedBankItems.set(bankItems));
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            BankingTransferPacketService.handleDeposit(player, new BankDepositRequestC2SPayload(teller.getId(), SLOT));

            helper.succeedWhen(() -> {
                check(player.getInventory().getItem(SLOT).isEmpty(), "the deposited item was not removed from the slot");
                check(refreshedBankItems.get() != null, "a successful confirm did not trigger the bank.open refresh");
                check(resultSender.calls.isEmpty(),
                        "a clean confirm must never send a CLEAN_REJECTION/RECONCILIATION_REQUIRED result: " + resultSender.calls);
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Full withdrawal through the real packet path ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void fullWithdrawalThroughRealPacketPathEndsConfirmedAndRefreshesTheAccount(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        HolderLookup.Provider registries = player.registryAccess();

        ItemStack original = new ItemStack(Items.DIAMOND, 5);
        byte[] payload = BankItemCodec.serialize(original.copy(), registries);
        String fingerprint = BankItemFingerprint.fingerprint(original, registries);
        double weight = BankItemWeight.resolve(original);

        UUID operationId = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        FakeWithdrawalClient withdrawalClient = new FakeWithdrawalClient();
        withdrawalClient.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingWithdrawalPrepareResult.Success(
                operationId, bankItemId, BankItemSchemaVersion.CURRENT, payload, fingerprint, weight));
        withdrawalClient.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingWithdrawalProxyService.useClientForTesting(withdrawalClient);

        java.util.concurrent.atomic.AtomicBoolean refreshed = new java.util.concurrent.atomic.AtomicBoolean();
        wireOpenRefresh(new BankingOpenAccount(UUID.randomUUID(), "global", null, 250, 0.0, 0, 0, 0, 2));
        BankingProxyService.useAccountScreenSenderForTesting((p, t, account, bankItems) -> refreshed.set(true));
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            BankingTransferPacketService.handleWithdrawal(
                    player, new BankWithdrawalRequestC2SPayload(teller.getId(), bankItemId));

            helper.succeedWhen(() -> {
                ItemStack inSlot = findFirstNonEmpty(player);
                check(inSlot != null && inSlot.getItem() == Items.DIAMOND && inSlot.getCount() == 5,
                        "the withdrawn item was not physically present in the player's inventory");
                check(refreshed.get(), "a successful confirm did not trigger the bank.open refresh");
                check(resultSender.calls.isEmpty(), "a clean confirm must never send a result payload: " + resultSender.calls);
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Milestone 15: a full inventory is its own readable outcome ----------

    /**
     * The Milestone 0 §3.4 finding, closed. The server's handling was always right -- capacity
     * pre-check before any receipt, Rails reservation cancelled, nothing created or lost -- but
     * the abort reported as the generic CLEAN_REJECTION, indistinguishable from a dead teller.
     * It now reaches the client as INVENTORY_FULL: the most actionable rejection in the system.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void inventoryFullWithdrawalReportsItsOwnKindAndCancelsTheReservation(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        HolderLookup.Provider registries = player.registryAccess();

        // Every main slot holds a full stack of something the withdrawn diamond cannot merge
        // into, so hasSufficientCapacity's real arithmetic refuses -- no stubbed verdicts.
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }

        ItemStack original = new ItemStack(Items.DIAMOND, 5);
        byte[] payload = BankItemCodec.serialize(original.copy(), registries);
        String fingerprint = BankItemFingerprint.fingerprint(original, registries);
        double weight = BankItemWeight.resolve(original);

        UUID operationId = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        FakeWithdrawalClient withdrawalClient = new FakeWithdrawalClient();
        withdrawalClient.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingWithdrawalPrepareResult.Success(
                operationId, bankItemId, BankItemSchemaVersion.CURRENT, payload, fingerprint, weight));
        withdrawalClient.cancelBehavior = () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        BankingWithdrawalProxyService.useClientForTesting(withdrawalClient);

        java.util.concurrent.atomic.AtomicBoolean refreshed = new java.util.concurrent.atomic.AtomicBoolean();
        wireOpenRefresh(new BankingOpenAccount(UUID.randomUUID(), "global", null, 250, 0.0, 0, 0, 0, 2));
        BankingProxyService.useAccountScreenSenderForTesting((p, t, account, bankItems) -> refreshed.set(true));
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            BankingTransferPacketService.handleWithdrawal(
                    player, new BankWithdrawalRequestC2SPayload(teller.getId(), bankItemId));

            helper.succeedWhen(() -> {
                check(resultSender.calls.size() == 1, "expected exactly one result, got " + resultSender.calls);
                check(resultSender.calls.get(0).kind() == BankTransferResultS2CPayload.Kind.INVENTORY_FULL,
                        "expected INVENTORY_FULL, got " + resultSender.calls.get(0).kind());
                check(resultSender.calls.get(0).operation() == BankTransferResultS2CPayload.Operation.WITHDRAWAL,
                        "wrong operation");
                check(!refreshed.get(), "nothing changed, so nothing may refresh");
                check(withdrawalClient.cancelRequests.size() == 1,
                        "Rails' reservation must be released by exactly one cancel");
                check(player.getInventory().getItem(0).getItem() == Items.COBBLESTONE,
                        "the player's inventory must be untouched");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Milestone 16: currency withdrawal's two actionable refusals ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void currencyWithdrawalInsufficientBalanceReportsItsOwnKind(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        FakeCurrencyWithdrawalClient client = new FakeCurrencyWithdrawalClient();
        client.prepareBehavior = () -> CompletableFuture.completedFuture(
                new BankingCurrencyWithdrawalPrepareResult.Rejected(BankingTransferOutcome.INSUFFICIENT_BALANCE, false));
        BankingCurrencyWithdrawalProxyService.useClientForTesting(client);

        java.util.concurrent.atomic.AtomicBoolean refreshed = new java.util.concurrent.atomic.AtomicBoolean();
        BankingProxyService.useAccountScreenSenderForTesting((p, t, account, bankItems) -> refreshed.set(true));
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            BankingTransferPacketService.handleCurrencyWithdrawal(
                    player, new BankCurrencyWithdrawalRequestC2SPayload(teller.getId(), "gold", 500));

            helper.succeedWhen(() -> {
                check(resultSender.calls.size() == 1, "expected one result, got " + resultSender.calls);
                check(resultSender.calls.get(0).kind() == BankTransferResultS2CPayload.Kind.INSUFFICIENT_BALANCE,
                        "expected INSUFFICIENT_BALANCE, got " + resultSender.calls.get(0).kind());
                check(!refreshed.get(), "a refusal must not refresh");
                cleanUpCurrencyWithdrawal();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUpCurrencyWithdrawal();
            throw propagate;
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void currencyWithdrawalIntoAFullPackReportsInventoryFullWithoutARoundTrip(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }

        // The local pre-check must catch this before prepare is ever called; the fake's default
        // throwing behaviours are the assertion that no round trip was spent.
        FakeCurrencyWithdrawalClient client = new FakeCurrencyWithdrawalClient();
        BankingCurrencyWithdrawalProxyService.useClientForTesting(client);

        java.util.concurrent.atomic.AtomicBoolean refreshed = new java.util.concurrent.atomic.AtomicBoolean();
        BankingProxyService.useAccountScreenSenderForTesting((p, t, account, bankItems) -> refreshed.set(true));
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            BankingTransferPacketService.handleCurrencyWithdrawal(
                    player, new BankCurrencyWithdrawalRequestC2SPayload(teller.getId(), "copper", 40));

            helper.succeedWhen(() -> {
                check(resultSender.calls.size() == 1, "expected one result, got " + resultSender.calls);
                check(resultSender.calls.get(0).kind() == BankTransferResultS2CPayload.Kind.INVENTORY_FULL,
                        "expected INVENTORY_FULL, got " + resultSender.calls.get(0).kind());
                check(client.cancelRequests.isEmpty(), "nothing was prepared, so nothing should be cancelled");
                check(player.getInventory().getItem(0).getItem() == Items.COBBLESTONE,
                        "the player's inventory must be untouched");
                check(!refreshed.get(), "a refusal must not refresh");
                cleanUpCurrencyWithdrawal();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUpCurrencyWithdrawal();
            throw propagate;
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void currencyWithdrawalLosingCapacityMidFlightCancelsAndReportsInventoryFull(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        // The race the second capacity check exists for: the pack has room when prepare leaves,
        // and none by the time the coins come back. Holding the prepare future open makes the
        // ordering deterministic -- the fill below is guaranteed to land between the two checks.
        CompletableFuture<BankingCurrencyWithdrawalPrepareResult> pendingPrepare = new CompletableFuture<>();
        FakeCurrencyWithdrawalClient client = new FakeCurrencyWithdrawalClient();
        client.prepareBehavior = () -> pendingPrepare;
        client.cancelBehavior = () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        BankingCurrencyWithdrawalProxyService.useClientForTesting(client);

        java.util.concurrent.atomic.AtomicBoolean refreshed = new java.util.concurrent.atomic.AtomicBoolean();
        BankingProxyService.useAccountScreenSenderForTesting((p, t, account, bankItems) -> refreshed.set(true));
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            BankingTransferPacketService.handleCurrencyWithdrawal(
                    player, new BankCurrencyWithdrawalRequestC2SPayload(teller.getId(), "copper", 40));

            for (int slot = 0; slot < 36; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            pendingPrepare.complete(new BankingCurrencyWithdrawalPrepareResult.Success(UUID.randomUUID()));

            helper.succeedWhen(() -> {
                check(resultSender.calls.size() == 1, "expected one result, got " + resultSender.calls);
                check(resultSender.calls.get(0).kind() == BankTransferResultS2CPayload.Kind.INVENTORY_FULL,
                        "expected INVENTORY_FULL, got " + resultSender.calls.get(0).kind());
                check(client.cancelRequests.size() == 1,
                        "the reservation must be cancelled exactly once, got " + client.cancelRequests.size());
                check(player.getInventory().getItem(0).getItem() == Items.COBBLESTONE,
                        "the player's inventory must be untouched");
                check(!refreshed.get(), "an aborted withdrawal must not refresh");
                cleanUpCurrencyWithdrawal();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUpCurrencyWithdrawal();
            throw propagate;
        }
    }

    private static void cleanUpCurrencyWithdrawal() {
        BankingCurrencyWithdrawalProxyService.resetClientForTesting();
        BankingCurrencyWithdrawalProxyService.resetInFlightTrackingForTesting();
        BankingTransferPacketService.resetResultSenderForTesting();
        BankingProxyService.resetAccountScreenSenderForTesting();
        ServiceNpcRegistryCache.clear();
    }

    private static final class FakeCurrencyWithdrawalClient implements BankingCurrencyWithdrawalClientPort {
        java.util.function.Supplier<CompletableFuture<BankingCurrencyWithdrawalPrepareResult>> prepareBehavior =
                () -> { throw new IllegalStateException("prepareCurrencyWithdrawal() was not expected in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingConfirmResult>> confirmBehavior =
                () -> { throw new IllegalStateException("confirm() was not expected in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingCancelResult>> cancelBehavior =
                () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        final List<BankingOperationRequest> cancelRequests = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<BankingCurrencyWithdrawalPrepareResult> prepareCurrencyWithdrawal(
                MinecraftServer server, com.seggellion.britannia_mod.service.banking.BankingCurrencyWithdrawalPrepareRequest request
        ) {
            return prepareBehavior.get();
        }

        @Override
        public CompletableFuture<BankingConfirmResult> confirm(MinecraftServer server, BankingOperationRequest request) {
            return confirmBehavior.get();
        }

        @Override
        public CompletableFuture<BankingCancelResult> cancel(MinecraftServer server, BankingOperationRequest request) {
            cancelRequests.add(request);
            return cancelBehavior.get();
        }
    }

    // ---------- Milestone 17: the remaining refusals with a real answer ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void depositOfACoinHiddenInAContainerReportsIneligibleItem(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        // Top-level identity routes this to the ITEM path (a container holding coins is not a
        // coin stack -- the router's own documented boundary); the eligibility check then finds
        // the nested coin. The throwing fake proves the refusal spends no round trip.
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(net.minecraft.core.component.DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(
                        List.of(new ItemStack(ItemRegistry.GOLD_COIN.get(), 3))));
        player.getInventory().setItem(SLOT, shulker);

        BankingDepositProxyService.useClientForTesting(new FakeDepositClient());
        java.util.concurrent.atomic.AtomicBoolean refreshed = new java.util.concurrent.atomic.AtomicBoolean();
        BankingProxyService.useAccountScreenSenderForTesting((p, t, account, bankItems) -> refreshed.set(true));
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            BankingTransferPacketService.handleDeposit(player, new BankDepositRequestC2SPayload(teller.getId(), SLOT));

            helper.succeedWhen(() -> {
                check(resultSender.calls.size() == 1, "expected one result, got " + resultSender.calls);
                check(resultSender.calls.get(0).kind() == BankTransferResultS2CPayload.Kind.INELIGIBLE_ITEM,
                        "expected INELIGIBLE_ITEM, got " + resultSender.calls.get(0).kind());
                check(player.getInventory().getItem(SLOT).getItem() == Items.SHULKER_BOX,
                        "a refused item must stay in the slot");
                check(!refreshed.get(), "a refusal must not refresh");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void depositRefusedForVaultWeightReportsBankCapacityExceeded(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND, 5));

        FakeDepositClient depositClient = new FakeDepositClient();
        depositClient.prepareBehavior = () -> CompletableFuture.completedFuture(
                new BankingDepositPrepareResult.Rejected(BankingTransferOutcome.CAPACITY_EXCEEDED, false));
        BankingDepositProxyService.useClientForTesting(depositClient);

        java.util.concurrent.atomic.AtomicBoolean refreshed = new java.util.concurrent.atomic.AtomicBoolean();
        BankingProxyService.useAccountScreenSenderForTesting((p, t, account, bankItems) -> refreshed.set(true));
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            BankingTransferPacketService.handleDeposit(player, new BankDepositRequestC2SPayload(teller.getId(), SLOT));

            helper.succeedWhen(() -> {
                check(resultSender.calls.size() == 1, "expected one result, got " + resultSender.calls);
                check(resultSender.calls.get(0).kind() == BankTransferResultS2CPayload.Kind.BANK_CAPACITY_EXCEEDED,
                        "expected BANK_CAPACITY_EXCEEDED, got " + resultSender.calls.get(0).kind());
                check(player.getInventory().getItem(SLOT).getItem() == Items.DIAMOND,
                        "a prepare rejection must leave the item in the slot");
                check(!refreshed.get(), "a refusal must not refresh");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void withdrawalOfAnAlreadyGoneItemReportsStoredItemUnavailable(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        // The Milestone 18 concurrency case in miniature: the public id was real when this
        // client's grid drew it, and Rails says it is already gone.
        FakeWithdrawalClient withdrawalClient = new FakeWithdrawalClient();
        withdrawalClient.prepareBehavior = () -> CompletableFuture.completedFuture(
                new BankingWithdrawalPrepareResult.Rejected(BankingTransferOutcome.ITEM_NOT_FOUND, false));
        BankingWithdrawalProxyService.useClientForTesting(withdrawalClient);

        java.util.concurrent.atomic.AtomicBoolean refreshed = new java.util.concurrent.atomic.AtomicBoolean();
        BankingProxyService.useAccountScreenSenderForTesting((p, t, account, bankItems) -> refreshed.set(true));
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            BankingTransferPacketService.handleWithdrawal(
                    player, new BankWithdrawalRequestC2SPayload(teller.getId(), UUID.randomUUID()));

            helper.succeedWhen(() -> {
                check(resultSender.calls.size() == 1, "expected one result, got " + resultSender.calls);
                check(resultSender.calls.get(0).kind() == BankTransferResultS2CPayload.Kind.STORED_ITEM_UNAVAILABLE,
                        "expected STORED_ITEM_UNAVAILABLE, got " + resultSender.calls.get(0).kind());
                check(!refreshed.get(), "a refusal must not refresh");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Milestone 17: the payload knows why it was sent ----------

    /** Records the five-argument production form, which the four-argument lambdas cannot see. */
    private static final class RefreshFlagRecordingSender implements BankingProxyService.AccountScreenSender {
        final List<Boolean> flags = new CopyOnWriteArrayList<>();

        @Override
        public void send(ServerPlayer player, ServiceNpcEntity teller, BankingOpenAccount account,
                         List<BankItemSummary> bankItems) {
            throw new IllegalStateException("production always calls the five-argument form");
        }

        @Override
        public void send(ServerPlayer player, ServiceNpcEntity teller, BankingOpenAccount account,
                         List<BankItemSummary> bankItems, boolean refresh) {
            flags.add(refresh);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aConfirmedMutationsRefreshIsFlaggedAsARefresh(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND, 5));

        FakeDepositClient depositClient = new FakeDepositClient();
        depositClient.prepareBehavior = () -> CompletableFuture.completedFuture(
                new BankingDepositPrepareResult.Success(UUID.randomUUID(), UUID.randomUUID()));
        depositClient.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingDepositProxyService.useClientForTesting(depositClient);

        wireOpenRefresh(new BankingOpenAccount(UUID.randomUUID(), "global", null, 250, 5.0, 0, 0, 0, 2));
        RefreshFlagRecordingSender sender = new RefreshFlagRecordingSender();
        BankingProxyService.useAccountScreenSenderForTesting(sender);
        BankingTransferPacketService.useResultSenderForTesting(new FakeResultSender());

        try {
            BankingTransferPacketService.handleDeposit(player, new BankDepositRequestC2SPayload(teller.getId(), SLOT));

            helper.succeedWhen(() -> {
                check(sender.flags.size() == 1, "expected one account push, got " + sender.flags);
                check(sender.flags.get(0), "a post-mutation push must be flagged refresh=true, or a client"
                        + " that closed banking mid-flight will have the interface re-open uninvited");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aGenuineBankOpenIsNotFlaggedAsARefresh(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        wireOpenRefresh(new BankingOpenAccount(UUID.randomUUID(), "global", null, 250, 0.0, 0, 0, 0, 2));
        RefreshFlagRecordingSender sender = new RefreshFlagRecordingSender();
        BankingProxyService.useAccountScreenSenderForTesting(sender);

        try {
            BankingProxyService.handle(player, teller);

            helper.succeedWhen(() -> {
                check(sender.flags.size() == 1, "expected one account push, got " + sender.flags);
                check(!sender.flags.get(0), "a player-initiated open must be flagged refresh=false,"
                        + " or banking would never open at all");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Milestone 18: the security matrix's remaining rows ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void anUnsupportedCurrencyKeyIsRefusedLocallyWithNoRoundTrip(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        // The withdrawal packet's codec deliberately does not validate the key (unlike cheque
        // issuance's), so "platinum" genuinely reaches the server. It must die here, before any
        // Rails call -- the throwing fake is the proof that it does.
        BankingCurrencyWithdrawalProxyService.useClientForTesting(new FakeCurrencyWithdrawalClient());
        java.util.concurrent.atomic.AtomicBoolean refreshed = new java.util.concurrent.atomic.AtomicBoolean();
        BankingProxyService.useAccountScreenSenderForTesting((p, t, account, bankItems) -> refreshed.set(true));
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            BankingTransferPacketService.handleCurrencyWithdrawal(
                    player, new BankCurrencyWithdrawalRequestC2SPayload(teller.getId(), "platinum", 5));

            helper.succeedWhen(() -> {
                check(resultSender.calls.size() == 1, "expected one result, got " + resultSender.calls);
                check(resultSender.calls.get(0).kind() == BankTransferResultS2CPayload.Kind.CLEAN_REJECTION,
                        "an unknown denomination is a clean refusal, got " + resultSender.calls.get(0).kind());
                check(!refreshed.get(), "nothing changed, so nothing may refresh");
                cleanUpCurrencyWithdrawal();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUpCurrencyWithdrawal();
            throw propagate;
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void anItemSwappedAfterThePacketIsSentIsNeverTheOneDeposited(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND, 5));

        // The altered-item-after-drag row: prepare is held open, and the slot is swapped for
        // something far more valuable while the request is in flight. The fingerprint captured
        // at press time must refuse to match, so the swap is cancelled rather than banked.
        CompletableFuture<BankingDepositPrepareResult> pendingPrepare = new CompletableFuture<>();
        FakeDepositClient depositClient = new FakeDepositClient();
        depositClient.prepareBehavior = () -> pendingPrepare;
        depositClient.cancelBehavior = () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        BankingDepositProxyService.useClientForTesting(depositClient);

        java.util.concurrent.atomic.AtomicBoolean refreshed = new java.util.concurrent.atomic.AtomicBoolean();
        BankingProxyService.useAccountScreenSenderForTesting((p, t, account, bankItems) -> refreshed.set(true));
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            BankingTransferPacketService.handleDeposit(player, new BankDepositRequestC2SPayload(teller.getId(), SLOT));

            player.getInventory().setItem(SLOT, new ItemStack(Items.NETHERITE_INGOT, 64));
            pendingPrepare.complete(new BankingDepositPrepareResult.Success(UUID.randomUUID(), UUID.randomUUID()));

            helper.succeedWhen(() -> {
                check(resultSender.calls.size() == 1, "expected one result, got " + resultSender.calls);
                check(player.getInventory().getItem(SLOT).getItem() == Items.NETHERITE_INGOT,
                        "the swapped-in stack must still be in the player's inventory, untouched");
                check(player.getInventory().getItem(SLOT).getCount() == 64, "and at its full count");
                check(!refreshed.get(), "nothing was banked, so nothing may refresh");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aRealButOutOfRangeTellerDropsTheRequestEntirely(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND, 5));

        // Not a forged id -- a real, live teller the player has walked away from. Distance is
        // re-checked on every packet, not merely when the screen opened.
        player.teleportTo(teller.getX() + 500.0, teller.getY(), teller.getZ());

        BankingDepositProxyService.useClientForTesting(new FakeDepositClient());
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            BankingTransferPacketService.handleDeposit(player, new BankDepositRequestC2SPayload(teller.getId(), SLOT));

            check(resultSender.calls.isEmpty(),
                    "an out-of-range teller drops the request silently, like bank.open: " + resultSender.calls);
            check(!player.getInventory().getItem(SLOT).isEmpty(), "the item must not have been touched");

            cleanUp();
            helper.succeed();
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Milestone 18: concurrency ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void twoPlayersRacingOneStoredItemProduceExactlyOneWithdrawal(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer first = setUpPlayer(helper, teller);
        ServerPlayer second = setUpPlayer(helper, teller);
        HolderLookup.Provider registries = first.registryAccess();

        UUID bankItemId = UUID.randomUUID();
        ItemStack original = new ItemStack(Items.DIAMOND, 5);
        byte[] payload = BankItemCodec.serialize(original.copy(), registries);
        String fingerprint = BankItemFingerprint.fingerprint(original, registries);
        double weight = BankItemWeight.resolve(original);

        // Rails is the arbiter: the first prepare wins the row, the second is told it is gone.
        // The mod's job is to honour that answer per player and never conjure a second diamond.
        AtomicInteger prepares = new AtomicInteger();
        FakeWithdrawalClient withdrawalClient = new FakeWithdrawalClient();
        withdrawalClient.prepareBehavior = () -> prepares.incrementAndGet() == 1
                ? CompletableFuture.completedFuture(new BankingWithdrawalPrepareResult.Success(
                        UUID.randomUUID(), bankItemId, BankItemSchemaVersion.CURRENT, payload, fingerprint, weight))
                : CompletableFuture.completedFuture(new BankingWithdrawalPrepareResult.Rejected(
                        BankingTransferOutcome.ITEM_NOT_FOUND, false));
        withdrawalClient.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingWithdrawalProxyService.useClientForTesting(withdrawalClient);

        wireOpenRefresh(new BankingOpenAccount(UUID.randomUUID(), "global", null, 250, 0.0, 0, 0, 0, 2));
        BankingProxyService.useAccountScreenSenderForTesting((p, t, account, bankItems) -> { });
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            BankingTransferPacketService.handleWithdrawal(
                    first, new BankWithdrawalRequestC2SPayload(teller.getId(), bankItemId));
            BankingTransferPacketService.handleWithdrawal(
                    second, new BankWithdrawalRequestC2SPayload(teller.getId(), bankItemId));

            helper.succeedWhen(() -> {
                check(prepares.get() == 2, "both players must genuinely reach Rails, got " + prepares.get());
                int delivered = countDiamonds(first) + countDiamonds(second);
                check(delivered == 5, "exactly one diamond stack may exist across both players, found " + delivered);
                check(resultSender.calls.size() == 1, "the loser gets exactly one result, got " + resultSender.calls);
                check(resultSender.calls.get(0).kind() == BankTransferResultS2CPayload.Kind.STORED_ITEM_UNAVAILABLE,
                        "the loser must be told the item is gone, got " + resultSender.calls.get(0).kind());
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    private static int countDiamonds(ServerPlayer player) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() == Items.DIAMOND) total += stack.getCount();
        }
        return total;
    }

    // ---------- Clean rejection sends the result payload, never a refresh ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void depositCleanRejectionSendsResultPayloadAndNeverRefreshes(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        // SLOT left empty -- a real, local rejection (EMPTY_SLOT), matching Slice 1's own tests.

        FakeDepositClient depositClient = new FakeDepositClient();
        BankingDepositProxyService.useClientForTesting(depositClient);
        java.util.concurrent.atomic.AtomicBoolean refreshed = new java.util.concurrent.atomic.AtomicBoolean();
        BankingProxyService.useAccountScreenSenderForTesting((p, t, account, bankItems) -> refreshed.set(true));
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            BankingTransferPacketService.handleDeposit(player, new BankDepositRequestC2SPayload(teller.getId(), SLOT));

            check(resultSender.calls.size() == 1, "expected exactly one result payload for a synchronous local rejection");
            check(resultSender.calls.get(0).operation() == BankTransferResultS2CPayload.Operation.DEPOSIT,
                    "wrong operation in result payload");
            check(resultSender.calls.get(0).kind() == BankTransferResultS2CPayload.Kind.CLEAN_REJECTION,
                    "wrong kind in result payload: " + resultSender.calls.get(0).kind());
            check(!refreshed.get(), "a clean rejection must never trigger a bank.open refresh");
            check(depositClient.prepareRequests.isEmpty(), "prepare must never be called for a local (empty-slot) rejection");

            cleanUp();
            helper.succeed();
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Ineligible item: server-side rejection fires regardless of client-side gating ----------

    /**
     * Milestone 10 re-fixture: this test originally used a gold coin as its ineligible item --
     * valid when currency was a dead end, but that exact stack now legitimately routes to the
     * currency balance protocol (its own tests below), so keeping the coin here would have let
     * this test pass by coincidence (the un-substituted real currency client failing on
     * credentials) while its assertions claimed a local eligibility rejection that never ran.
     * A quest-bound item (ADR-013) is a genuinely ineligible fixture with no second protocol to
     * escape into, so the test's actual claim -- server-side gating fires regardless of what a
     * modified client sends -- stays honestly proven.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void ineligibleSlotIsRejectedServerSideEvenIfAClientSendsItAnyway(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        grantRealQuestReward(player);
        moveOnlyItemToSlot(player, SLOT);

        FakeDepositClient depositClient = new FakeDepositClient();
        BankingDepositProxyService.useClientForTesting(depositClient);
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            // A modified client could send this slot index regardless of any client-side eligibility
            // display -- the client's own graying-out is a UX nicety only, never the real boundary.
            BankingTransferPacketService.handleDeposit(player, new BankDepositRequestC2SPayload(teller.getId(), SLOT));

            check(resultSender.calls.size() == 1, "expected exactly one rejection result");
            // Milestone 17: the refusal keeps its identity now -- INELIGIBLE_ITEM, not the
            // generic rejection. The test's actual claim is unchanged: rejected, never accepted.
            check(resultSender.calls.get(0).kind() == BankTransferResultS2CPayload.Kind.INELIGIBLE_ITEM,
                    "an ineligible (quest-bound) item must be rejected as INELIGIBLE_ITEM, not silently accepted");
            check(depositClient.prepareRequests.isEmpty(), "prepare must never be called for an ineligible item -- rejected locally first");
            check(!player.getInventory().getItem(SLOT).isEmpty(),
                    "the ineligible item must remain untouched in the player's inventory");

            cleanUp();
            helper.succeed();
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Milestone 10: the deposit packet's currency/item routing ----------

    /**
     * A bare coin stack through the REAL packet path routes to the currency balance protocol:
     * the currency client's prepare fires with the exact key/count, the coins leave the
     * inventory, and a clean confirm triggers the same bank.open refresh item deposits use --
     * so the freshly-mutated gold/silver/copper balances reach the client. The ITEM deposit
     * client is deliberately left with its exploding default: if routing regressed to the item
     * path, this test fails loudly rather than passing by coincidence.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void coinStackThroughRealPacketPathRoutesToCurrencyProtocolAndRefreshesTheAccount(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(com.seggellion.britannia_mod.registry.ItemRegistry.GOLD_COIN.get(), 37));

        FakeDepositClient depositClient = new FakeDepositClient();
        BankingDepositProxyService.useClientForTesting(depositClient);
        FakeCurrencyDepositClient currencyClient = new FakeCurrencyDepositClient();
        currencyClient.prepareBehavior = () -> CompletableFuture.completedFuture(
                new BankingCurrencyDepositPrepareResult.Success(UUID.randomUUID()));
        currencyClient.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingCurrencyDepositProxyService.useClientForTesting(currencyClient);

        java.util.concurrent.atomic.AtomicBoolean refreshed = new java.util.concurrent.atomic.AtomicBoolean();
        wireOpenRefresh(new BankingOpenAccount(UUID.randomUUID(), "global", null, 250, 0.0, 37, 0, 0, 2));
        BankingProxyService.useAccountScreenSenderForTesting((p, t, account, bankItems) -> refreshed.set(true));
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            BankingTransferPacketService.handleDeposit(player, new BankDepositRequestC2SPayload(teller.getId(), SLOT));

            helper.succeedWhen(() -> {
                check(currencyClient.prepareRequests.size() == 1,
                        "the coin stack did not route to the currency protocol");
                check(currencyClient.prepareRequests.get(0).currencyKey().equals("gold")
                                && currencyClient.prepareRequests.get(0).amount() == 37,
                        "wrong key/amount routed: " + currencyClient.prepareRequests);
                check(depositClient.prepareRequests.isEmpty(),
                        "a coin stack must never reach the item deposit prepare");
                check(player.getInventory().getItem(SLOT).isEmpty(), "the deposited coins were not removed from the slot");
                check(refreshed.get(), "a successful currency confirm did not trigger the bank.open refresh");
                check(resultSender.calls.isEmpty(),
                        "a clean currency confirm must never send a rejection/reconciliation result: " + resultSender.calls);
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    /**
     * The routing's other polarity: an ordinary non-coin item through the same packet still
     * takes the Milestone 9 item path, completely unaffected by the currency branch -- the
     * currency client is left with its exploding default so any accidental currency routing
     * fails loudly.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void nonCoinItemThroughRealPacketPathStillRoutesToTheItemProtocolUnaffected(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND, 5));

        UUID operationId = UUID.randomUUID();
        FakeDepositClient depositClient = new FakeDepositClient();
        depositClient.prepareBehavior = () -> CompletableFuture.completedFuture(
                new BankingDepositPrepareResult.Success(operationId, UUID.randomUUID()));
        depositClient.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingDepositProxyService.useClientForTesting(depositClient);
        FakeCurrencyDepositClient currencyClient = new FakeCurrencyDepositClient();
        BankingCurrencyDepositProxyService.useClientForTesting(currencyClient);

        java.util.concurrent.atomic.AtomicBoolean refreshed = new java.util.concurrent.atomic.AtomicBoolean();
        wireOpenRefresh(new BankingOpenAccount(UUID.randomUUID(), "global", null, 250, 5.0, 0, 0, 0, 2));
        BankingProxyService.useAccountScreenSenderForTesting((p, t, account, bankItems) -> refreshed.set(true));
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            BankingTransferPacketService.handleDeposit(player, new BankDepositRequestC2SPayload(teller.getId(), SLOT));

            helper.succeedWhen(() -> {
                check(depositClient.prepareRequests.size() == 1,
                        "the ordinary item did not route to the item deposit protocol");
                check(currencyClient.prepareRequests.isEmpty(),
                        "an ordinary item must never reach the currency deposit prepare");
                check(player.getInventory().getItem(SLOT).isEmpty(), "the deposited item was not removed from the slot");
                check(refreshed.get(), "a successful item confirm did not trigger the bank.open refresh");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Malformed/malicious selection references ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void outOfRangePositiveSlotIndexIsRejectedCleanlyNotACrash(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        FakeDepositClient depositClient = new FakeDepositClient();
        BankingDepositProxyService.useClientForTesting(depositClient);
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            // Inventory#getItem safely returns EMPTY for a positive out-of-range index (traced,
            // not assumed -- see BankDepositRequestC2SPayload's own docs); this proves the real
            // handler reaches that same safe path without crashing, ending in an ordinary
            // empty-slot local rejection.
            BankingTransferPacketService.handleDeposit(player, new BankDepositRequestC2SPayload(teller.getId(), 999));

            check(resultSender.calls.size() == 1, "expected a clean rejection for an out-of-range slot index");
            check(resultSender.calls.get(0).kind() == BankTransferResultS2CPayload.Kind.CLEAN_REJECTION,
                    "wrong kind: " + resultSender.calls.get(0).kind());

            cleanUp();
            helper.succeed();
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void unknownBankItemPublicIdIsRejectedTheSameWayRailsAlreadyGuarantees(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        FakeWithdrawalClient withdrawalClient = new FakeWithdrawalClient();
        withdrawalClient.prepareBehavior = () -> CompletableFuture.completedFuture(
                new BankingWithdrawalPrepareResult.Rejected(BankingTransferOutcome.ITEM_NOT_FOUND, false));
        BankingWithdrawalProxyService.useClientForTesting(withdrawalClient);
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            BankingTransferPacketService.handleWithdrawal(
                    player, new BankWithdrawalRequestC2SPayload(teller.getId(), UUID.randomUUID()));

            helper.succeedWhen(() -> {
                check(resultSender.calls.size() == 1, "expected exactly one rejection result");
                // Milestone 17: ITEM_NOT_FOUND now keeps its identity as STORED_ITEM_UNAVAILABLE
                // rather than the generic rejection. The test's actual claim is unchanged:
                // rejected by Rails' own guarantee, nothing conjured.
                check(resultSender.calls.get(0).kind() == BankTransferResultS2CPayload.Kind.STORED_ITEM_UNAVAILABLE,
                        "an unknown bank item public id must reject as STORED_ITEM_UNAVAILABLE: " + resultSender.calls.get(0).kind());
                check(player.getInventory().isEmpty(), "no item must have been conjured for an unknown bank item id");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Stale/invalid teller entity id: silently dropped, mirroring bank.open ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aBogusEntityIdSilentlyDropsTheRequestJustLikeBankOpenDoes(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND, 1));

        FakeDepositClient depositClient = new FakeDepositClient();
        BankingDepositProxyService.useClientForTesting(depositClient);
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            int bogusEntityId = teller.getId() + 9999;
            BankingTransferPacketService.handleDeposit(player, new BankDepositRequestC2SPayload(bogusEntityId, SLOT));

            check(resultSender.calls.isEmpty(), "a bogus entity id must silently drop the request, not send any result");
            check(depositClient.prepareRequests.isEmpty(), "prepare must never be called for an unresolvable teller");
            check(!player.getInventory().getItem(SLOT).isEmpty(), "no item must have been touched for a dropped request");

            cleanUp();
            helper.succeed();
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- IN_FLIGHT dedup is enforced through the real entry point (no new flag) ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void inFlightDedupFromSlice2IsEnforcedThroughTheRealPacketHandler(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND, 1));

        AtomicInteger prepareDispatchCount = new AtomicInteger();
        CompletableFuture<BankingDepositPrepareResult> pending = new CompletableFuture<>();
        FakeDepositClient depositClient = new FakeDepositClient();
        depositClient.prepareBehavior = () -> {
            prepareDispatchCount.incrementAndGet();
            return pending;
        };
        BankingDepositProxyService.useClientForTesting(depositClient);
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            BankDepositRequestC2SPayload request = new BankDepositRequestC2SPayload(teller.getId(), SLOT);
            BankingTransferPacketService.handleDeposit(player, request);
            BankingTransferPacketService.handleDeposit(player, request);

            check(prepareDispatchCount.get() == 1,
                    "a repeat deposit request for the same slot dispatched a second prepare call (count="
                            + prepareDispatchCount.get() + ")");
            check(resultSender.calls.size() == 1,
                    "expected exactly one immediate clean-rejection result for the duplicate request");
            check(resultSender.calls.get(0).kind() == BankTransferResultS2CPayload.Kind.CLEAN_REJECTION,
                    "duplicate request's result kind was wrong: " + resultSender.calls.get(0).kind());

            pending.complete(new BankingDepositPrepareResult.Rejected(BankingTransferOutcome.CAPACITY_EXCEEDED, false));

            helper.succeedWhen(() -> {
                check(resultSender.calls.size() == 2, "the original request did not complete with its own result");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- RECONCILIATION_REQUIRED via the real packet path ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void reconciliationRequiredSendsItsOwnDistinctResultThroughTheRealPacketPath(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND, 5));

        UUID operationId = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        FakeDepositClient depositClient = new FakeDepositClient();
        depositClient.prepareBehavior = () -> CompletableFuture.completedFuture(
                new BankingDepositPrepareResult.Success(operationId, bankItemId));
        depositClient.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.ReconciliationRequired());
        BankingDepositProxyService.useClientForTesting(depositClient);
        java.util.concurrent.atomic.AtomicBoolean refreshed = new java.util.concurrent.atomic.AtomicBoolean();
        BankingProxyService.useAccountScreenSenderForTesting((p, t, account, bankItems) -> refreshed.set(true));
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            BankingTransferPacketService.handleDeposit(player, new BankDepositRequestC2SPayload(teller.getId(), SLOT));

            helper.succeedWhen(() -> {
                check(resultSender.calls.size() == 1, "expected exactly one result payload");
                check(resultSender.calls.get(0).operation() == BankTransferResultS2CPayload.Operation.DEPOSIT,
                        "wrong operation in RECONCILIATION_REQUIRED result");
                check(resultSender.calls.get(0).kind() == BankTransferResultS2CPayload.Kind.RECONCILIATION_REQUIRED,
                        "wrong kind: " + resultSender.calls.get(0).kind());
                check(!refreshed.get(), "RECONCILIATION_REQUIRED must never trigger the ordinary success refresh");
                check(player.getInventory().getItem(SLOT).isEmpty(),
                        "the item must remain removed from the player on RECONCILIATION_REQUIRED (deposit already committed)");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Helpers ----------

    private static void wireOpenRefresh(BankingOpenAccount account) {
        BankingProxyService.useClientForTesting(new BankingOpenClient(
                server -> Optional.of(com.seggellion.britannia_mod.server.auth.ServerCredentials.forGameTesting(
                        URI.create("http://127.0.0.1"), UUID.randomUUID())),
                (ignored, task) -> CompletableFuture.completedFuture(new BankingOpenClientResult.Success(account, List.of())),
                (uri, max) -> null
        ));
    }

    private static ServerPlayer setUpPlayer(GameTestHelper helper, ServiceNpcEntity teller) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.teleportTo(teller.getX() + 1.0, teller.getY(), teller.getZ());
        return player;
    }

    private static ItemStack findFirstNonEmpty(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) return stack;
        }
        return null;
    }

    private static void cleanUp() {
        BankingDepositProxyService.resetClientForTesting();
        BankingDepositProxyService.resetInFlightTrackingForTesting();
        BankingCurrencyDepositProxyService.resetClientForTesting();
        BankingCurrencyDepositProxyService.resetInFlightTrackingForTesting();
        BankingWithdrawalProxyService.resetClientForTesting();
        BankingWithdrawalProxyService.resetInFlightTrackingForTesting();
        BankingProxyService.resetClientForTesting();
        BankingProxyService.resetAccountScreenSenderForTesting();
        BankingProxyService.resetInFlightTrackingForTesting();
        BankingTransferPacketService.resetResultSenderForTesting();
        ServiceNpcRegistryCache.clear();
    }

    private static void grantRealQuestReward(ServerPlayer player) {
        QuestModels.ItemData reward = new QuestModels.ItemData();
        reward.id = "magic_ring"; // QuestRewardService's own special-cased id, guaranteed to resolve to a real item
        reward.count = 1;

        QuestModels.QuestResponse response = new QuestModels.QuestResponse();
        response.success = true;
        response.quest_id = 42L;
        response.questStateId = "gametest-packet-quest-state";
        response.granted_items = List.of(reward);

        QuestRewardService.apply(player, response);
    }

    private static void moveOnlyItemToSlot(ServerPlayer player, int slot) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && i != slot) {
                player.getInventory().setItem(slot, stack.copy());
                player.getInventory().setItem(i, ItemStack.EMPTY);
                return;
            }
            if (!stack.isEmpty() && i == slot) {
                return;
            }
        }
        throw new IllegalStateException("no item found to relocate");
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

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class FakeResultSender implements BankingTransferPacketService.ResultSender {
        final List<BankTransferResultS2CPayload> calls = new CopyOnWriteArrayList<>();

        @Override
        public void send(ServerPlayer player, BankTransferResultS2CPayload.Operation operation, BankTransferResultS2CPayload.Kind kind) {
            calls.add(new BankTransferResultS2CPayload(operation, kind));
        }
    }

    private static final class FakeDepositClient implements BankingDepositClientPort {
        java.util.function.Supplier<CompletableFuture<BankingDepositPrepareResult>> prepareBehavior =
                () -> { throw new IllegalStateException("prepare() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingConfirmResult>> confirmBehavior =
                () -> { throw new IllegalStateException("confirm() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingCancelResult>> cancelBehavior =
                () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());

        final List<BankingDepositPrepareRequest> prepareRequests = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<BankingDepositPrepareResult> prepare(MinecraftServer server, BankingDepositPrepareRequest request) {
            prepareRequests.add(request);
            return prepareBehavior.get();
        }

        @Override
        public CompletableFuture<BankingConfirmResult> confirm(MinecraftServer server, BankingOperationRequest request) {
            return confirmBehavior.get();
        }

        @Override
        public CompletableFuture<BankingCancelResult> cancel(MinecraftServer server, BankingOperationRequest request) {
            return cancelBehavior.get();
        }
    }

    private static final class FakeCurrencyDepositClient implements BankingCurrencyDepositClientPort {
        java.util.function.Supplier<CompletableFuture<BankingCurrencyDepositPrepareResult>> prepareBehavior =
                () -> { throw new IllegalStateException("prepareCurrencyDeposit() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingConfirmResult>> confirmBehavior =
                () -> { throw new IllegalStateException("confirm() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingCancelResult>> cancelBehavior =
                () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());

        final List<BankingCurrencyDepositPrepareRequest> prepareRequests = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<BankingCurrencyDepositPrepareResult> prepareCurrencyDeposit(
                MinecraftServer server, BankingCurrencyDepositPrepareRequest request
        ) {
            prepareRequests.add(request);
            return prepareBehavior.get();
        }

        @Override
        public CompletableFuture<BankingConfirmResult> confirm(MinecraftServer server, BankingOperationRequest request) {
            return confirmBehavior.get();
        }

        @Override
        public CompletableFuture<BankingCancelResult> cancel(MinecraftServer server, BankingOperationRequest request) {
            return cancelBehavior.get();
        }
    }

    private static final class FakeWithdrawalClient implements BankingWithdrawalClientPort {
        java.util.function.Supplier<CompletableFuture<BankingWithdrawalPrepareResult>> prepareBehavior =
                () -> { throw new IllegalStateException("prepareWithdrawal() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingConfirmResult>> confirmBehavior =
                () -> { throw new IllegalStateException("confirm() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingCancelResult>> cancelBehavior =
                () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        /** Milestone 15: the inventory-full test asserts the reservation is released exactly once. */
        final List<BankingOperationRequest> cancelRequests = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<BankingWithdrawalPrepareResult> prepareWithdrawal(
                MinecraftServer server, BankingWithdrawalPrepareRequest request
        ) {
            return prepareBehavior.get();
        }

        @Override
        public CompletableFuture<BankingConfirmResult> confirm(MinecraftServer server, BankingOperationRequest request) {
            return confirmBehavior.get();
        }

        @Override
        public CompletableFuture<BankingCancelResult> cancel(MinecraftServer server, BankingOperationRequest request) {
            cancelRequests.add(request);
            return cancelBehavior.get();
        }
    }
}
