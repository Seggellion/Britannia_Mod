package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.item.BankItemCodec;
import com.seggellion.britannia_mod.bank.item.BankItemFingerprint;
import com.seggellion.britannia_mod.bank.item.BankItemSchemaVersion;
import com.seggellion.britannia_mod.bank.item.BankItemWeight;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.network.payload.BankDepositRequestC2SPayload;
import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload;
import com.seggellion.britannia_mod.network.payload.BankWithdrawalRequestC2SPayload;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import com.seggellion.britannia_mod.service.banking.BankingCancelResult;
import com.seggellion.britannia_mod.service.banking.BankingConfirmResult;
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

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void ineligibleSlotIsRejectedServerSideEvenIfAClientSendsItAnyway(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(com.seggellion.britannia_mod.registry.ItemRegistry.GOLD_COIN.get(), 3));

        FakeDepositClient depositClient = new FakeDepositClient();
        BankingDepositProxyService.useClientForTesting(depositClient);
        FakeResultSender resultSender = new FakeResultSender();
        BankingTransferPacketService.useResultSenderForTesting(resultSender);

        try {
            // A modified client could send this slot index regardless of any client-side eligibility
            // display -- BankScreen's own graying-out is a UX nicety only, never the real boundary.
            BankingTransferPacketService.handleDeposit(player, new BankDepositRequestC2SPayload(teller.getId(), SLOT));

            check(resultSender.calls.size() == 1, "expected exactly one clean-rejection result");
            check(resultSender.calls.get(0).kind() == BankTransferResultS2CPayload.Kind.CLEAN_REJECTION,
                    "an ineligible (currency) item must be cleanly rejected, not silently accepted");
            check(depositClient.prepareRequests.isEmpty(), "prepare must never be called for an ineligible item -- rejected locally first");
            check(player.getInventory().getItem(SLOT).getItem() == com.seggellion.britannia_mod.registry.ItemRegistry.GOLD_COIN.get(),
                    "the ineligible item must remain untouched in the player's inventory");

            cleanUp();
            helper.succeed();
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
                check(resultSender.calls.size() == 1, "expected exactly one clean-rejection result");
                check(resultSender.calls.get(0).kind() == BankTransferResultS2CPayload.Kind.CLEAN_REJECTION,
                        "an unknown bank item public id must be cleanly rejected: " + resultSender.calls.get(0).kind());
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
        BankingWithdrawalProxyService.resetClientForTesting();
        BankingWithdrawalProxyService.resetInFlightTrackingForTesting();
        BankingProxyService.resetClientForTesting();
        BankingProxyService.resetAccountScreenSenderForTesting();
        BankingProxyService.resetInFlightTrackingForTesting();
        BankingTransferPacketService.resetResultSenderForTesting();
        ServiceNpcRegistryCache.clear();
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

    private static final class FakeWithdrawalClient implements BankingWithdrawalClientPort {
        java.util.function.Supplier<CompletableFuture<BankingWithdrawalPrepareResult>> prepareBehavior =
                () -> { throw new IllegalStateException("prepareWithdrawal() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingConfirmResult>> confirmBehavior =
                () -> { throw new IllegalStateException("confirm() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingCancelResult>> cancelBehavior =
                () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());

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
            return cancelBehavior.get();
        }
    }
}
