package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.transfer.BankTransferOperationType;
import com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceipt;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStatus;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStore;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceipts;
import com.seggellion.britannia_mod.component.BankChequeData;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import com.seggellion.britannia_mod.service.banking.BankTransferReconciliationService;
import com.seggellion.britannia_mod.service.banking.BankingCancelResult;
import com.seggellion.britannia_mod.service.banking.BankingChequeIssuanceClientPort;
import com.seggellion.britannia_mod.service.banking.BankingChequeIssuanceConfirmResult;
import com.seggellion.britannia_mod.service.banking.BankingChequeIssuanceLocalRejectionReason;
import com.seggellion.britannia_mod.service.banking.BankingChequeIssuancePrepareRequest;
import com.seggellion.britannia_mod.service.banking.BankingChequeIssuancePrepareResult;
import com.seggellion.britannia_mod.service.banking.BankingChequeIssuanceProxyService;
import com.seggellion.britannia_mod.service.banking.BankingChequeIssuanceResult;
import com.seggellion.britannia_mod.service.banking.BankingOperationRequest;
import com.seggellion.britannia_mod.service.banking.BankingTransferOutcome;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Milestone 11 NeoForge Slice 1: the bank cheque issuance path, exercised through {@link
 * BankingChequeIssuanceProxyService}'s test-support entry points with a per-method {@link
 * FakeClient} substituted -- mirroring {@code BankingCurrencyWithdrawalProxyServiceGameTests}'
 * established structure test-for-test wherever the flows correspond, and departing from it only
 * where {@link BankingChequeIssuanceProxyService}'s own confirm-before-insertion ordering makes
 * a genuinely different test necessary (see the forced-save-failure and crash-resume tests below).
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankingChequeIssuanceProxyServiceGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BANK_TYPE_KEY = "bank_teller";
    private static final int SAMPLE_AMOUNT_COPPER = 5_000_000; // 500 gold, the approved minimum

    private BankingChequeIssuanceProxyServiceGameTests() {
    }

    // ---------- Happy path ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void happyPathIssuesAChequeAndResolvesTheReceipt(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        UUID operationId = UUID.randomUUID();
        UUID chequeId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingChequeIssuancePrepareResult.Success(operationId));
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(
                new BankingChequeIssuanceConfirmResult.Confirmed(chequeId, SAMPLE_AMOUNT_COPPER));
        BankingChequeIssuanceProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingChequeIssuanceResult> future =
                    BankingChequeIssuanceProxyService.triggerChequeIssuanceForTesting(player, teller, SAMPLE_AMOUNT_COPPER);

            helper.succeedWhen(() -> {
                check(future.isDone(), "cheque issuance did not complete");
                BankingChequeIssuanceResult result = future.join();
                check(result instanceof BankingChequeIssuanceResult.Confirmed, "expected Confirmed, got " + result);
                BankingChequeIssuanceResult.Confirmed confirmed = (BankingChequeIssuanceResult.Confirmed) result;
                check(confirmed.operationPublicId().equals(operationId), "wrong operation id in result");
                check(confirmed.chequePublicId().equals(chequeId), "wrong cheque id in result");

                ItemStack stack = findChequeStack(player);
                check(stack != null, "expected exactly one bank cheque item in the inventory");
                BankChequeData data = stack.get(DataComponentRegistry.BANK_CHEQUE_DATA.get());
                check(data != null, "the inserted cheque must carry BankChequeData");
                check(data.chequeId().equals(chequeId), "the inserted cheque's chequeId did not match the real Rails-issued id");
                check(data.displayAmount() == SAMPLE_AMOUNT_COPPER, "the inserted cheque's display amount did not match");

                check(fake.prepareRequests.size() == 1, "expected exactly one prepare request");
                check(fake.prepareRequests.get(0).amount() == SAMPLE_AMOUNT_COPPER, "wrong amount sent at prepare");
                check(fake.confirmRequests.size() == 1, "expected exactly one confirm request");
                check(fake.cancelRequests.isEmpty(), "cancel must never be called on a clean happy path");

                check(!hasAnyReceiptFor(player.serverLevel(), operationId), "the receipt must be resolved after a clean confirm+delivery");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Local rejection: amount out of range ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void anAmountBelowTheApprovedMinimumIsRejectedLocallyWithZeroRailsCalls(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        FakeClient fake = new FakeClient();
        BankingChequeIssuanceProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingChequeIssuanceResult> future =
                    BankingChequeIssuanceProxyService.triggerChequeIssuanceForTesting(player, teller, SAMPLE_AMOUNT_COPPER - 10_000);

            helper.succeedWhen(() -> {
                check(future.isDone(), "cheque issuance did not complete");
                BankingChequeIssuanceResult result = future.join();
                check(result instanceof BankingChequeIssuanceResult.RejectedLocally, "expected RejectedLocally, got " + result);
                check(((BankingChequeIssuanceResult.RejectedLocally) result).reason() == BankingChequeIssuanceLocalRejectionReason.INVALID_AMOUNT,
                        "wrong local rejection reason: " + result);
                check(fake.prepareRequests.isEmpty(), "prepare must never be called for an out-of-range amount");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Full inventory: rejected locally before any Rails call ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void fullInventoryIsRejectedLocallyWithZeroRailsCallsAndNoPartialInsertion(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        fillInventoryCompletely(player);

        FakeClient fake = new FakeClient();
        BankingChequeIssuanceProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingChequeIssuanceResult> future =
                    BankingChequeIssuanceProxyService.triggerChequeIssuanceForTesting(player, teller, SAMPLE_AMOUNT_COPPER);

            helper.succeedWhen(() -> {
                check(future.isDone(), "cheque issuance did not complete");
                BankingChequeIssuanceResult result = future.join();
                check(result instanceof BankingChequeIssuanceResult.RejectedLocally, "expected RejectedLocally, got " + result);
                check(((BankingChequeIssuanceResult.RejectedLocally) result).reason() == BankingChequeIssuanceLocalRejectionReason.INSUFFICIENT_CAPACITY,
                        "wrong local rejection reason: " + result);

                check(fake.prepareRequests.isEmpty(), "prepare must never be called when the local capacity check rejects");
                check(fake.cancelRequests.isEmpty(), "cancel must never be called -- nothing was ever reserved");
                check(fake.confirmRequests.isEmpty(), "confirm must never be called");
                check(findChequeStack(player) == null, "no cheque must have been conjured into the already-full inventory");

                BankTransferReceiptStore.ScanResult scan = BankTransferReceipts.scanUnresolved(player.serverLevel());
                check(scan.pending().stream().noneMatch(r -> r.playerUuid().equals(player.getUUID()) && r.operationType() == BankTransferOperationType.CHEQUE_ISSUANCE),
                        "a receipt must never be written when the local capacity check rejects");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Forced-save failure: abort-and-restore the LOCAL insertion, receipt left pending ----------

    /**
     * Cheque issuance's own forced-save-failure policy is genuinely different from every prior
     * insertion-shaped flow's: {@link BankingWithdrawalProxyServiceGameTests}/{@code
     * BankingCurrencyWithdrawalProxyServiceGameTests}' equivalent tests prove an ESCALATION to
     * {@code reconciliation_required} (withdrawal can never cleanly abort once its receipt
     * exists, written before insertion). Here, confirm has already, irrevocably happened by the
     * time forceSave runs too -- but the physical insertion itself can still be undone locally,
     * and a later automatic retry is provably safe (redemption will be gated by Rails' own
     * single-use {@code BankCheque} state, not physical copy count -- see {@link
     * BankingChequeIssuanceProxyService}'s own class docs). This proves the actual, different
     * policy: the just-inserted cheque is removed back out (an honest "abort-and-restore" for
     * the local side), and the receipt is left {@code PENDING_LOCAL_ACTION} -- neither resolved
     * nor escalated -- via the same real seam {@code BankTransferPlayerDurabilityGameTests}
     * established ({@code useSaveDelegateForTesting}), not a new injection mechanism.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aForcedSaveFailureAfterInsertionAbortsAndRestoresLocallyLeavingTheReceiptPending(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        BankTransferPlayerDurability.useSaveDelegateForTesting(p -> { throw new RuntimeException("simulated forced-save failure"); });

        UUID operationId = UUID.randomUUID();
        UUID chequeId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingChequeIssuancePrepareResult.Success(operationId));
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(
                new BankingChequeIssuanceConfirmResult.Confirmed(chequeId, SAMPLE_AMOUNT_COPPER));
        BankingChequeIssuanceProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingChequeIssuanceResult> future =
                    BankingChequeIssuanceProxyService.triggerChequeIssuanceForTesting(player, teller, SAMPLE_AMOUNT_COPPER);

            helper.succeedWhen(() -> {
                check(future.isDone(), "the operation must complete even when the forced save throws internally");
                BankingChequeIssuanceResult result = future.join();
                check(result instanceof BankingChequeIssuanceResult.PendingDelivery,
                        "a detected forced-save failure must abort-and-restore locally and report PendingDelivery, got " + result);
                BankingChequeIssuanceResult.PendingDelivery pending = (BankingChequeIssuanceResult.PendingDelivery) result;
                check(pending.chequePublicId().equals(chequeId), "wrong cheque id in PendingDelivery result");

                check(findChequeStack(player) == null,
                        "the just-inserted cheque must have been removed back out (abort-and-restore) after the detected save failure");

                BankTransferReceiptStore.ScanResult scan = readFreshStore(player.serverLevel()).scanUnresolved();
                check(scan.pending().stream().anyMatch(r -> r.operationId().equals(operationId)),
                        "the receipt must remain an ordinary PENDING_LOCAL_ACTION candidate -- a later retry is safe for a cheque");
                check(scan.reconciliationRequired().stream().noneMatch(r -> r.operationId().equals(operationId)),
                        "the receipt must NOT be escalated -- unlike withdrawal, a cheque re-delivery attempt cannot double-spend value");

                check(fake.prepareRequests.size() == 1 && fake.confirmRequests.size() == 1,
                        "the normal prepare/confirm sequence must still run exactly once despite the save failure");

                BankTransferPlayerDurability.resetSaveDelegateForTesting();
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            BankTransferPlayerDurability.resetSaveDelegateForTesting();
            cleanUp();
            throw propagate;
        }
    }

    /**
     * Milestone 14 priority 2 (context enforcement, dimension 5): mirrors {@link
     * #aForcedSaveFailureAfterInsertionAbortsAndRestoresLocallyLeavingTheReceiptPending}'s own
     * PendingDelivery-checking shape, but for the earlier "teller no longer valid" case rather
     * than a forced-save failure -- confirm has already irrevocably happened (see class docs on
     * why there is no post-confirm Cancel here), so a stale teller at delivery time is reported
     * the same safe-to-retry-later way, not as a cancelled operation. Uses a pending confirm
     * future (the deposit/withdrawal flows' own established technique) to open a real window to
     * walk the player away between confirm being dispatched and it resolving.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void tellerNoLongerValidAtDeliveryTimeLeavesTheReceiptPendingWithNoInsertion(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        UUID operationId = UUID.randomUUID();
        UUID chequeId = UUID.randomUUID();
        CompletableFuture<BankingChequeIssuanceConfirmResult> pendingConfirm = new CompletableFuture<>();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingChequeIssuancePrepareResult.Success(operationId));
        fake.confirmBehavior = () -> pendingConfirm;
        BankingChequeIssuanceProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingChequeIssuanceResult> future =
                    BankingChequeIssuanceProxyService.triggerChequeIssuanceForTesting(player, teller, SAMPLE_AMOUNT_COPPER);

            // The player walks far out of interaction range while confirm is still in flight --
            // by the time it resolves, delivery must re-resolve the teller and find it gone.
            player.teleportTo(teller.getX() + 100.0, teller.getY(), teller.getZ());
            pendingConfirm.complete(new BankingChequeIssuanceConfirmResult.Confirmed(chequeId, SAMPLE_AMOUNT_COPPER));

            helper.succeedWhen(() -> {
                check(future.isDone(), "the operation must complete even when the teller goes out of range");
                BankingChequeIssuanceResult result = future.join();
                check(result instanceof BankingChequeIssuanceResult.PendingDelivery,
                        "a teller no longer in range at delivery time must report PendingDelivery, got " + result);
                BankingChequeIssuanceResult.PendingDelivery pending = (BankingChequeIssuanceResult.PendingDelivery) result;
                check(pending.chequePublicId().equals(chequeId), "wrong cheque id in PendingDelivery result");

                check(findChequeStack(player) == null,
                        "no cheque may ever be inserted once the teller is found out of range at delivery time");

                BankTransferReceiptStore.ScanResult scan = readFreshStore(player.serverLevel()).scanUnresolved();
                check(scan.pending().stream().anyMatch(r -> r.operationId().equals(operationId)),
                        "the receipt must remain an ordinary PENDING_LOCAL_ACTION candidate -- a later retry is safe for a cheque");
                check(scan.reconciliationRequired().stream().noneMatch(r -> r.operationId().equals(operationId)),
                        "the receipt must NOT be escalated -- Rails' confirm already succeeded and cannot be undone, "
                                + "but a later delivery attempt is always safe");

                check(fake.prepareRequests.size() == 1 && fake.confirmRequests.size() == 1,
                        "prepare/confirm must still run exactly once -- only delivery is affected by the stale teller");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Crash-recovery resume ----------

    /**
     * Simulates a crash between confirm succeeding and delivery ever being attempted --
     * {@link BankingChequeIssuanceProxyService#prepareAndConfirmForTesting} stops exactly there,
     * matching the real crash window this flow's own ordering creates (see the class's own
     * docs for why that window differs from every prior flow's "crash after insertion" one).
     * Resume must re-confirm (idempotent -- proven by asserting it is called again, safely) and
     * then successfully deliver the exact same, real cheque -- never a second, different one.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void crashAfterConfirmBeforeDeliveryIsResumedAndDeliversTheSameCheque(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        UUID operationId = UUID.randomUUID();
        UUID chequeId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingChequeIssuancePrepareResult.Success(operationId));
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(
                new BankingChequeIssuanceConfirmResult.Confirmed(chequeId, SAMPLE_AMOUNT_COPPER));
        BankingChequeIssuanceProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingChequeIssuanceProxyService.PrepareAndConfirmOutcome> future =
                    BankingChequeIssuanceProxyService.prepareAndConfirmForTesting(player, teller, SAMPLE_AMOUNT_COPPER);

            helper.succeedWhen(() -> {
                check(future.isDone(), "prepareAndConfirm did not complete");
                BankingChequeIssuanceProxyService.PrepareAndConfirmOutcome outcome = future.join();
                check(outcome instanceof BankingChequeIssuanceProxyService.PrepareAndConfirmOutcome.Confirmed,
                        "expected Confirmed, got " + outcome);

                // "crash" simulated here: delivery is never attempted.
                check(findChequeStack(player) == null, "no cheque must have been delivered before the simulated crash");

                BankTransferReceipt survived = findReceipt(player.serverLevel(), operationId);
                check(survived.currencyAmount() != null && survived.currencyAmount() == (long) SAMPLE_AMOUNT_COPPER,
                        "the on-disk receipt's currencyAmount did not match the requested amount: " + survived.currencyAmount());
                check(survived.bankItemPublicId() == null, "a cheque issuance receipt must carry no bank item public id");
                check(survived.status() == BankTransferReceiptStatus.PENDING_LOCAL_ACTION,
                        "delivery was never attempted, so the receipt must still be PENDING_LOCAL_ACTION");

                // A real crash would not clean up IN_FLIGHT either.
                check(BankingChequeIssuanceProxyService.isInFlightForTesting(player.getUUID()),
                        "a simulated crash must leave the player marked in-flight, matching real crash semantics");

                // Resume: simulate the restart (IN_FLIGHT cleared), then resume -- must re-confirm
                // (idempotent) and then deliver the same real cheque.
                BankingChequeIssuanceProxyService.resetInFlightTrackingForTesting();

                CompletableFuture<BankingChequeIssuanceResult> resumed =
                        BankingChequeIssuanceProxyService.resumeConfirmChequeIssuance(player.server, player.getUUID(), operationId);
                BankingChequeIssuanceResult resumedResult = resumed.join();
                check(resumedResult instanceof BankingChequeIssuanceResult.Confirmed,
                        "resumed cheque issuance did not report Confirmed: " + resumedResult);
                check(((BankingChequeIssuanceResult.Confirmed) resumedResult).chequePublicId().equals(chequeId),
                        "resume must deliver the exact same, already-confirmed cheque, not a different one");

                check(fake.confirmRequests.size() == 2 && fake.confirmRequests.get(1).operationPublicId().equals(operationId),
                        "resume must call confirm again (safe -- idempotent on Rails' side), targeting the correct operation");

                ItemStack delivered = findChequeStack(player);
                check(delivered != null, "the cheque must now be delivered after resume");
                BankChequeData data = delivered.get(DataComponentRegistry.BANK_CHEQUE_DATA.get());
                check(data != null && data.chequeId().equals(chequeId), "the delivered cheque must carry the correct, real chequeId");

                check(!hasAnyReceiptFor(player.serverLevel(), operationId), "the receipt must be resolved after a successful resumed delivery");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    /** The discriminator branch this slice added to {@code BankTransferReconciliationService}. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void startupReconciliationRoutesAChequeIssuanceReceiptToItsOwnResumeAndDeliversIt(GameTestHelper helper) {
        installBankRegistry();
        ServerPlayer player = setUpPlayer(helper, spawnBankTeller(helper));
        ServerLevel level = player.serverLevel();
        MinecraftServer server = level.getServer();

        UUID operationId = UUID.randomUUID();
        UUID chequeId = UUID.randomUUID();
        BankTransferReceipts.record(
                level, operationId, player.getUUID(), BankTransferOperationType.CHEQUE_ISSUANCE,
                null, (long) SAMPLE_AMOUNT_COPPER, null, System.currentTimeMillis()
        );
        BankTransferReceipt receipt = findReceipt(level, operationId);

        FakeClient fake = new FakeClient();
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(
                new BankingChequeIssuanceConfirmResult.Confirmed(chequeId, SAMPLE_AMOUNT_COPPER));
        BankingChequeIssuanceProxyService.useClientForTesting(fake);

        try {
            BankTransferReconciliationService.reconcile(
                    server, new BankTransferReceiptStore.ScanResult(List.of(receipt), List.of(), List.of())
            );

            helper.succeedWhen(() -> {
                check(fake.confirmRequests.size() == 1 && fake.confirmRequests.get(0).operationPublicId().equals(operationId),
                        "the cheque resume did not confirm the correct operation: " + fake.confirmRequests);
                check(!hasAnyReceiptFor(level, operationId), "the receipt must be resolved after a successful resumed delivery");
                check(findChequeStack(player) != null, "the cheque must have been opportunistically delivered since the player is online");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Deliver-on-login: closes the "resume can't reach an offline player" gap ----------

    /**
     * A cheque already confirmed on Rails (the real {@code BankCheque} exists) but never
     * delivered because the player was offline -- exactly {@link
     * BankingChequeIssuanceProxyService#prepareAndConfirmForTesting}'s own end state, reused
     * here rather than re-deriving it. Logging back in must re-run the exact same resume path
     * startup reconciliation already uses ({@link
     * BankTransferReconciliationService#deliverPendingChequesOnLogin}, which calls {@link
     * BankingChequeIssuanceProxyService#resumeConfirmChequeIssuance} directly) and actually
     * deliver the cheque.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void loggingBackInDeliversAChequeThatWasConfirmedWhileThePlayerWasOffline(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        UUID operationId = UUID.randomUUID();
        UUID chequeId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingChequeIssuancePrepareResult.Success(operationId));
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(
                new BankingChequeIssuanceConfirmResult.Confirmed(chequeId, SAMPLE_AMOUNT_COPPER));
        BankingChequeIssuanceProxyService.useClientForTesting(fake);

        // Reach "confirmed, not yet delivered" -- the same state a real offline-during-confirm
        // crash would leave -- then simulate the player having been offline: reset IN_FLIGHT
        // (matching every other crash-recovery test's own convention) without ever attempting
        // delivery. loginTriggered guards the login simulation to fire exactly once, since
        // succeedWhen's lambda below is retried from the top on every tick until it stops
        // throwing (this codebase's own established idiom for "wait for A, then do B once, then
        // check C" within one repeatedly-invoked block).
        java.util.concurrent.atomic.AtomicBoolean loginTriggered = new java.util.concurrent.atomic.AtomicBoolean(false);

        try {
            CompletableFuture<BankingChequeIssuanceProxyService.PrepareAndConfirmOutcome> prepared =
                    BankingChequeIssuanceProxyService.prepareAndConfirmForTesting(player, teller, SAMPLE_AMOUNT_COPPER);

            helper.succeedWhen(() -> {
                check(prepared.isDone(), "prepareAndConfirm did not complete");
                check(prepared.join() instanceof BankingChequeIssuanceProxyService.PrepareAndConfirmOutcome.Confirmed,
                        "expected the operation to reach Confirmed before simulating the login, got " + prepared.join());

                if (loginTriggered.compareAndSet(false, true)) {
                    check(findChequeStack(player) == null, "no cheque should exist yet, before the simulated login");
                    BankingChequeIssuanceProxyService.resetInFlightTrackingForTesting();
                    // The actual fix under test: log the player back in.
                    BankTransferReconciliationService.deliverPendingChequesOnLogin(player);
                }

                ItemStack delivered = findChequeStack(player);
                check(delivered != null, "the cheque must be delivered once the player logs back in");
                BankChequeData data = delivered.get(DataComponentRegistry.BANK_CHEQUE_DATA.get());
                check(data != null && data.chequeId().equals(chequeId), "the delivered cheque must carry the real, already-confirmed chequeId");

                check(fake.confirmRequests.size() == 2 && fake.confirmRequests.get(1).operationPublicId().equals(operationId),
                        "login delivery must re-confirm (safe -- idempotent) the correct operation");
                check(!hasAnyReceiptFor(player.serverLevel(), operationId), "the receipt must be resolved once login-delivery succeeds");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    /**
     * A receipt that already resolved (a normal, clean happy-path issuance) must not be
     * rediscovered or redelivered on a later login -- {@code scanUnresolved()} simply will not
     * find it (it was removed from the store the moment it resolved), so login-delivery has
     * nothing to act on and confirm must not be called again.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void loggingInAfterAnAlreadyResolvedIssuanceDoesNotRedeliverOrReconfirm(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        UUID operationId = UUID.randomUUID();
        UUID chequeId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingChequeIssuancePrepareResult.Success(operationId));
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(
                new BankingChequeIssuanceConfirmResult.Confirmed(chequeId, SAMPLE_AMOUNT_COPPER));
        BankingChequeIssuanceProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingChequeIssuanceResult> future =
                    BankingChequeIssuanceProxyService.triggerChequeIssuanceForTesting(player, teller, SAMPLE_AMOUNT_COPPER);

            helper.succeedWhen(() -> {
                check(future.isDone() && future.join() instanceof BankingChequeIssuanceResult.Confirmed,
                        "setup failed: expected a clean, fully-resolved issuance");
                check(findChequeStack(player) != null, "setup failed: the cheque must already be delivered");
                check(!hasAnyReceiptFor(player.serverLevel(), operationId), "setup failed: the receipt must already be resolved");
                check(fake.confirmRequests.size() == 1, "setup failed: expected exactly one confirm call from the original issuance");

                // The actual check under test: logging in again afterward must be a no-op.
                BankTransferReconciliationService.deliverPendingChequesOnLogin(player);

                check(fake.confirmRequests.size() == 1,
                        "login-delivery must not re-confirm an already-resolved operation -- confirm call count changed");
                check(countChequeStacks(player) == 1,
                        "login-delivery must not deliver a second, duplicate cheque for an already-resolved operation");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Reconciliation required ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void reconciliationRequiredEscalatesTheReceiptAndDeliversNoCheque(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        UUID operationId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingChequeIssuancePrepareResult.Success(operationId));
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingChequeIssuanceConfirmResult.ReconciliationRequired());
        BankingChequeIssuanceProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingChequeIssuanceResult> future =
                    BankingChequeIssuanceProxyService.triggerChequeIssuanceForTesting(player, teller, SAMPLE_AMOUNT_COPPER);

            helper.succeedWhen(() -> {
                check(future.isDone(), "cheque issuance did not complete");
                BankingChequeIssuanceResult result = future.join();
                check(result instanceof BankingChequeIssuanceResult.ReconciliationRequired,
                        "expected the distinct ReconciliationRequired case, got " + result);

                check(findChequeStack(player) == null, "no cheque may ever be delivered when Rails reports reconciliation_required");

                BankTransferReceipt receipt = findReceipt(player.serverLevel(), operationId);
                check(receipt.status() == BankTransferReceiptStatus.RECONCILIATION_REQUIRED,
                        "the receipt must be transitioned to RECONCILIATION_REQUIRED, got " + receipt.status());

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- IN_FLIGHT dedup ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aRepeatTriggerWhileOneIsInFlightDoesNotDispatchASecondPrepare(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        AtomicInteger prepareDispatchCount = new AtomicInteger();
        CompletableFuture<BankingChequeIssuancePrepareResult> pending = new CompletableFuture<>();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> {
            prepareDispatchCount.incrementAndGet();
            return pending;
        };
        BankingChequeIssuanceProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingChequeIssuanceResult> first =
                    BankingChequeIssuanceProxyService.triggerChequeIssuanceForTesting(player, teller, SAMPLE_AMOUNT_COPPER);
            check(BankingChequeIssuanceProxyService.isInFlightForTesting(player.getUUID()), "must be marked in-flight while prepare is pending");

            CompletableFuture<BankingChequeIssuanceResult> second =
                    BankingChequeIssuanceProxyService.triggerChequeIssuanceForTesting(player, teller, SAMPLE_AMOUNT_COPPER);

            check(prepareDispatchCount.get() == 1, "a repeat trigger dispatched a second prepare call (count=" + prepareDispatchCount.get() + ")");
            check(second.isDone() && second.join() instanceof BankingChequeIssuanceResult.LocalFailure,
                    "the duplicate trigger did not report a local failure immediately");

            pending.complete(new BankingChequeIssuancePrepareResult.Rejected(BankingTransferOutcome.INSUFFICIENT_BALANCE, false));

            helper.succeedWhen(() -> {
                check(first.isDone(), "the original issuance did not complete");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Helpers (self-contained per this codebase's GameTest convention) ----------

    private static ServerPlayer setUpPlayer(GameTestHelper helper, ServiceNpcEntity teller) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.teleportTo(teller.getX() + 1.0, teller.getY(), teller.getZ());
        return player;
    }

    private static void fillInventoryCompletely(ServerPlayer player) {
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        player.getInventory().offhand.set(0, new ItemStack(Items.COBBLESTONE, 64));
    }

    private static ItemStack findChequeStack(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == ItemRegistry.BANK_CHEQUE.get()) return stack;
        }
        return null;
    }

    private static int countChequeStacks(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() == ItemRegistry.BANK_CHEQUE.get()) count++;
        }
        return count;
    }

    private static void cleanUp() {
        BankingChequeIssuanceProxyService.resetClientForTesting();
        BankingChequeIssuanceProxyService.resetInFlightTrackingForTesting();
        ServiceNpcRegistryCache.clear();
    }

    private static BankTransferReceiptStore readFreshStore(ServerLevel level) {
        File dataFile = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data")
                .resolve(BankTransferReceiptStore.DATA_NAME + ".dat")
                .toFile();
        if (!dataFile.isFile()) return null;

        net.minecraft.nbt.CompoundTag outer;
        try (InputStream input = new FileInputStream(dataFile)) {
            outer = net.minecraft.nbt.NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        } catch (IOException exception) {
            throw new IllegalStateException("failed reading bank transfer receipt store file directly", exception);
        }
        return BankTransferReceiptStore.load(outer.getCompound("data"), level.registryAccess());
    }

    private static boolean hasAnyReceiptFor(ServerLevel level, UUID operationId) {
        BankTransferReceiptStore store = readFreshStore(level);
        return store != null && store.find(operationId) != null;
    }

    private static BankTransferReceipt findReceipt(ServerLevel level, UUID operationId) {
        BankTransferReceiptStore store = readFreshStore(level);
        BankTransferReceipt receipt = store == null ? null : store.find(operationId);
        if (receipt == null) throw new IllegalStateException("no receipt found for operation " + operationId);
        return receipt;
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

    /** Per-method fake, mirroring every sibling flow's own FakeClient shape. */
    private static final class FakeClient implements BankingChequeIssuanceClientPort {
        java.util.function.Supplier<CompletableFuture<BankingChequeIssuancePrepareResult>> prepareBehavior =
                () -> { throw new IllegalStateException("prepareChequeIssuance() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingChequeIssuanceConfirmResult>> confirmBehavior =
                () -> { throw new IllegalStateException("confirm() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingCancelResult>> cancelBehavior =
                () -> { throw new IllegalStateException("cancel() was not expected to be called in this test"); };

        final List<BankingChequeIssuancePrepareRequest> prepareRequests = new CopyOnWriteArrayList<>();
        final List<BankingOperationRequest> confirmRequests = new CopyOnWriteArrayList<>();
        final List<BankingOperationRequest> cancelRequests = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<BankingChequeIssuancePrepareResult> prepareChequeIssuance(
                MinecraftServer server, BankingChequeIssuancePrepareRequest request
        ) {
            prepareRequests.add(request);
            return prepareBehavior.get();
        }

        @Override
        public CompletableFuture<BankingChequeIssuanceConfirmResult> confirm(MinecraftServer server, BankingOperationRequest request) {
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
