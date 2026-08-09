package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.transfer.BankTransferOperationType;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceipt;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStatus;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStore;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceipts;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.seggellion.britannia_mod.service.banking.BankingCancelResult;
import com.seggellion.britannia_mod.service.banking.BankingConfirmResult;
import com.seggellion.britannia_mod.service.banking.BankingDepositClientPort;
import com.seggellion.britannia_mod.service.banking.BankingDepositPrepareRequest;
import com.seggellion.britannia_mod.service.banking.BankingDepositPrepareResult;
import com.seggellion.britannia_mod.service.banking.BankingDepositProxyService;
import com.seggellion.britannia_mod.service.banking.BankingDepositResult;
import com.seggellion.britannia_mod.service.banking.BankingOperationRequest;
import com.seggellion.britannia_mod.service.banking.BankingWithdrawalClientPort;
import com.seggellion.britannia_mod.service.banking.BankingWithdrawalPrepareRequest;
import com.seggellion.britannia_mod.service.banking.BankingWithdrawalPrepareResult;
import com.seggellion.britannia_mod.service.banking.BankingWithdrawalProxyService;
import com.seggellion.britannia_mod.service.banking.BankingWithdrawalResult;
import com.seggellion.britannia_mod.service.banking.BankTransferReconciliationService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Milestone 9 NeoForge Slice 3b: {@link BankTransferReconciliationService} exercised through its
 * real confirm-dispatch machinery ({@link BankingDepositProxyService#resumeConfirmDeposit} /
 * {@link BankingWithdrawalProxyService#resumeConfirmWithdrawal}, the exact same core the live
 * path already uses), with confirm calls observed through the same fake-client pattern
 * established in Slice 1/2/3a.
 *
 * <p>Every test here calls {@link BankTransferReconciliationService#reconcile} with a
 * <b>manually-constructed</b> {@link BankTransferReceiptStore.ScanResult} -- containing only
 * the receipt(s) that specific test itself created -- rather than {@link
 * BankTransferReconciliationService#runStartupReconciliation}'s real {@code scanUnresolved()}.
 * See {@code reconcile}'s own docs for why: {@link BankTransferReceiptStore} is a single,
 * server-wide store shared across up to 50 concurrently-batched GameTests, and several other
 * existing tests in this program (Slice 1/2's own simulated-crash tests) deliberately leave a
 * receipt {@code PENDING_LOCAL_ACTION} in that same shared store for their own later assertions.
 * Calling the real, unscoped {@code runStartupReconciliation} here would risk this file's own
 * fake client resuming -- and silently resolving -- another, unrelated test's in-flight receipt.
 * Passing a manually-built {@code ScanResult} sidesteps that entirely while still exercising
 * {@code resolve}/{@code escalateToReconciliationRequired} against the real store for this
 * test's own receipt, so outcomes remain independently verifiable via {@code find(operationId)}.
 * A separate, final test proves the real {@code runStartupReconciliation} wiring itself (that it
 * actually calls the real {@code scanUnresolved()}), with assertions scoped to its own operation
 * id rather than an exact/total count, exactly matching this program's own established
 * shared-store-safety convention.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankTransferReconciliationServiceGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BANK_TYPE_KEY = "bank_teller";
    private static final int SLOT = 0;

    private BankTransferReconciliationServiceGameTests() {
    }

    // ---------- Pending, genuinely never confirmed before the crash ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aPendingDepositNeverConfirmedBeforeTheCrashIsResumedAndResolves(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = server.overworld();
        UUID operationId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        BankTransferReceipt receipt = recordReceipt(
                level, operationId, playerUuid, BankTransferOperationType.DEPOSIT, new byte[]{1, 2, 3}, bankItemId, 1_000L
        );

        FakeDepositClient depositClient = new FakeDepositClient();
        depositClient.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingDepositProxyService.useClientForTesting(depositClient);

        try {
            BankTransferReconciliationService.reconcile(server, scanWithOnlyPending(receipt));

            helper.succeedWhen(() -> {
                check(depositClient.confirmRequests.size() == 1, "expected exactly one resumed confirm call");
                check(depositClient.confirmRequests.get(0).operationPublicId().equals(operationId), "wrong operation id resumed");
                check(depositClient.confirmRequests.get(0).playerUuid().equals(playerUuid),
                        "resumed confirm did not use the receipt's own stored player uuid");
                check(depositClient.prepareRequests.isEmpty(), "reconciliation must never re-attempt the risky action (prepare)");
                check(BankTransferReceiptStore.get(level).find(operationId) == null,
                        "the receipt was not resolved after a successful resumed confirm");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Pending, actually already confirmed before the crash (idempotent replay) ----------

    /**
     * Mechanically identical to the "never confirmed before the crash" case above -- Rails'
     * confirm idempotency (M8) is exactly what makes the two indistinguishable from this side,
     * and equally safe to resume automatically. Kept as its own distinct test (withdrawal,
     * rather than deposit, for genuinely separate coverage of both operation types resuming
     * correctly) because the task treats it as a separate real-world scenario worth its own
     * proof, even though the code path is deliberately shared.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aPendingWithdrawalAlreadyConfirmedBeforeTheCrashResolvesCleanlyOnResume(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = server.overworld();
        UUID operationId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        BankTransferReceipt receipt = recordReceipt(
                level, operationId, playerUuid, BankTransferOperationType.WITHDRAWAL, new byte[]{4, 5, 6}, bankItemId, 2_000L
        );

        FakeWithdrawalClient withdrawalClient = new FakeWithdrawalClient();
        // Rails' own idempotent replay of an already-confirmed operation returns CONFIRMED again.
        withdrawalClient.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingWithdrawalProxyService.useClientForTesting(withdrawalClient);

        try {
            BankTransferReconciliationService.reconcile(server, scanWithOnlyPending(receipt));

            helper.succeedWhen(() -> {
                check(withdrawalClient.confirmRequests.size() == 1, "expected exactly one resumed confirm call");
                check(withdrawalClient.confirmRequests.get(0).operationPublicId().equals(operationId), "wrong operation id resumed");
                check(BankTransferReceiptStore.get(level).find(operationId) == null,
                        "an idempotent-replay CONFIRMED must resolve the receipt exactly like a first-time confirm");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Pending, escalated on Rails' side since the crash ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aPendingReceiptEscalatedOnRailsSideSinceTheCrashIsEscalatedNotLeftPending(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = server.overworld();
        UUID operationId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        BankTransferReceipt receipt = recordReceipt(
                level, operationId, playerUuid, BankTransferOperationType.DEPOSIT, new byte[]{7, 8}, bankItemId, 3_000L
        );

        FakeDepositClient depositClient = new FakeDepositClient();
        depositClient.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.ReconciliationRequired());
        BankingDepositProxyService.useClientForTesting(depositClient);

        try {
            BankTransferReconciliationService.reconcile(server, scanWithOnlyPending(receipt));

            helper.succeedWhen(() -> {
                check(depositClient.confirmRequests.size() == 1, "expected exactly one resumed confirm call");
                BankTransferReceipt updated = BankTransferReceiptStore.get(level).find(operationId);
                check(updated != null, "the receipt must still exist -- escalated, not resolved");
                check(updated.status() == BankTransferReceiptStatus.RECONCILIATION_REQUIRED,
                        "the receipt was not escalated to RECONCILIATION_REQUIRED: " + updated.status());
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- reconciliationRequired entries: never touched ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aReconciliationRequiredEntryIsNeverResumedOnlyLogged(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = server.overworld();
        UUID operationId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        BankTransferReceipt receipt = recordReceipt(
                level, operationId, playerUuid, BankTransferOperationType.DEPOSIT, new byte[]{9}, bankItemId, 4_000L
        );
        BankTransferReceipts.escalateToReconciliationRequired(level, operationId);
        BankTransferReceipt escalated = BankTransferReceiptStore.get(level).find(operationId);

        FakeDepositClient depositClient = new FakeDepositClient();
        // confirmBehavior deliberately left throwing -- confirm must never be reached at all.
        BankingDepositProxyService.useClientForTesting(depositClient);

        try {
            BankTransferReconciliationService.reconcile(
                    server, new BankTransferReceiptStore.ScanResult(List.of(), List.of(escalated), List.of())
            );

            check(depositClient.confirmRequests.isEmpty(), "a reconciliation_required receipt must never be confirmed");
            check(depositClient.prepareRequests.isEmpty(), "a reconciliation_required receipt must never be prepared/re-attempted");
            BankTransferReceipt afterward = BankTransferReceiptStore.get(level).find(operationId);
            check(afterward != null && afterward.status() == BankTransferReceiptStatus.RECONCILIATION_REQUIRED,
                    "an already-escalated receipt must remain untouched");

            cleanUp();
            helper.succeed();
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- unreadable entries: never touched ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void anUnreadableEntryIsNeverParsedOrActedOnOnlyLogged(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BankTransferReceiptStore.UnreadableEntry entry =
                new BankTransferReceiptStore.UnreadableEntry("test_injected_corruption", new CompoundTag());

        FakeDepositClient depositClient = new FakeDepositClient();
        FakeWithdrawalClient withdrawalClient = new FakeWithdrawalClient();
        // Both fakes' confirm/prepare deliberately left throwing -- an unreadable entry exposes
        // no operationId/playerUuid/bankItemPublicId at all, so nothing here could construct a
        // confirm call from it even if the reconciliation loop tried to.
        BankingDepositProxyService.useClientForTesting(depositClient);
        BankingWithdrawalProxyService.useClientForTesting(withdrawalClient);

        try {
            BankTransferReconciliationService.reconcile(
                    server, new BankTransferReceiptStore.ScanResult(List.of(), List.of(), List.of(entry))
            );

            check(depositClient.confirmRequests.isEmpty(), "an unreadable entry must never trigger a deposit confirm");
            check(withdrawalClient.confirmRequests.isEmpty(), "an unreadable entry must never trigger a withdrawal confirm");

            cleanUp();
            helper.succeed();
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- All three buckets simultaneously: independent, no cross-contamination ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void allThreeBucketTypesPresentSimultaneouslyAreHandledIndependently(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = server.overworld();

        UUID pendingId = UUID.randomUUID();
        BankTransferReceipt pendingReceipt = recordReceipt(
                level, pendingId, UUID.randomUUID(), BankTransferOperationType.DEPOSIT, new byte[]{1}, UUID.randomUUID(), 5_000L
        );

        UUID reconciliationRequiredId = UUID.randomUUID();
        recordReceipt(
                level, reconciliationRequiredId, UUID.randomUUID(), BankTransferOperationType.DEPOSIT,
                new byte[]{2}, UUID.randomUUID(), 5_100L
        );
        BankTransferReceipts.escalateToReconciliationRequired(level, reconciliationRequiredId);
        BankTransferReceipt reconciliationRequiredReceipt = BankTransferReceiptStore.get(level).find(reconciliationRequiredId);

        BankTransferReceiptStore.UnreadableEntry unreadableEntry =
                new BankTransferReceiptStore.UnreadableEntry("test_injected_corruption_mixed", new CompoundTag());

        FakeDepositClient depositClient = new FakeDepositClient();
        depositClient.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingDepositProxyService.useClientForTesting(depositClient);

        try {
            BankTransferReconciliationService.reconcile(server, new BankTransferReceiptStore.ScanResult(
                    List.of(pendingReceipt), List.of(reconciliationRequiredReceipt), List.of(unreadableEntry)
            ));

            helper.succeedWhen(() -> {
                check(depositClient.confirmRequests.size() == 1,
                        "expected exactly one confirm call (for the pending receipt only), got " + depositClient.confirmRequests.size());
                check(depositClient.confirmRequests.get(0).operationPublicId().equals(pendingId),
                        "the confirm call resumed the wrong operation -- cross-contamination between buckets");

                check(BankTransferReceiptStore.get(level).find(pendingId) == null, "the pending receipt was not resolved");
                BankTransferReceipt stillEscalated = BankTransferReceiptStore.get(level).find(reconciliationRequiredId);
                check(stillEscalated != null && stillEscalated.status() == BankTransferReceiptStatus.RECONCILIATION_REQUIRED,
                        "the reconciliation_required receipt must remain untouched");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Rails unreachable during resume: stays pending, no crash ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void railsUnreachableDuringResumeLeavesTheReceiptPendingWithNoCrash(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = server.overworld();
        UUID operationId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        BankTransferReceipt receipt = recordReceipt(
                level, operationId, playerUuid, BankTransferOperationType.WITHDRAWAL, new byte[]{3}, bankItemId, 6_000L
        );

        FakeWithdrawalClient withdrawalClient = new FakeWithdrawalClient();
        withdrawalClient.confirmBehavior = () -> CompletableFuture.failedFuture(new java.io.UncheckedIOException(
                "simulated: Rails unreachable at startup", new java.io.IOException("connection refused")));
        BankingWithdrawalProxyService.useClientForTesting(withdrawalClient);

        try {
            // Must not throw/crash here, synchronously or asynchronously.
            BankTransferReconciliationService.reconcile(server, scanWithOnlyPending(receipt));

            helper.succeedWhen(() -> {
                check(withdrawalClient.confirmRequests.size() == 1, "expected exactly one attempted (failed) confirm call");
                BankTransferReceipt afterward = BankTransferReceiptStore.get(level).find(operationId);
                check(afterward != null && afterward.status() == BankTransferReceiptStatus.PENDING_LOCAL_ACTION,
                        "a failed resume attempt must leave the receipt pending, never escalate it just because the retry itself failed");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Real end-to-end wiring: runStartupReconciliation itself, scoped assertions ----------

    /**
     * The one test in this file that calls the real {@link
     * BankTransferReconciliationService#runStartupReconciliation} (not {@code reconcile} with a
     * manual scan) -- proving the real {@code scanUnresolved()} wiring itself works, not just
     * {@code reconcile}'s per-bucket logic in isolation. Assertions are scoped to this test's own
     * operation id (never an exact/total count) for the same shared-store-safety reason this
     * file's class docs explain.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void runStartupReconciliationEndToEndResumesARealPendingReceiptFoundByARealScan(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = server.overworld();
        UUID operationId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        recordReceipt(level, operationId, playerUuid, BankTransferOperationType.DEPOSIT, new byte[]{5, 5}, bankItemId, 7_000L);

        FakeDepositClient depositClient = new FakeDepositClient();
        // The real scanUnresolved() this test deliberately exercises could, in principle, also
        // pick up some other, unrelated, concurrently-running test's own pending receipt in this
        // same shared store -- responding Confirmed unconditionally would corrupt that test by
        // prematurely resolving its receipt. Scoped to this test's own operation id specifically;
        // anything else is left pending (a transport failure), never falsely resolved.
        BankingDepositProxyService.useClientForTesting(new BankingDepositClientPort() {
            @Override
            public CompletableFuture<BankingDepositPrepareResult> prepare(MinecraftServer s, BankingDepositPrepareRequest r) {
                return depositClient.prepare(s, r);
            }

            @Override
            public CompletableFuture<BankingConfirmResult> confirm(MinecraftServer s, BankingOperationRequest request) {
                depositClient.confirmRequests.add(request);
                if (request.operationPublicId().equals(operationId)) {
                    return CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
                }
                return CompletableFuture.completedFuture(new BankingConfirmResult.TransportFailure("unrelated_test_operation"));
            }

            @Override
            public CompletableFuture<BankingCancelResult> cancel(MinecraftServer s, BankingOperationRequest r) {
                return depositClient.cancel(s, r);
            }
        });

        try {
            BankTransferReconciliationService.runStartupReconciliation(server);

            helper.succeedWhen(() -> {
                check(depositClient.confirmRequests.stream().anyMatch(r -> r.operationPublicId().equals(operationId)),
                        "the real scanUnresolved()-driven reconciliation never resumed this test's own pending receipt");
                check(BankTransferReceiptStore.get(level).find(operationId) == null,
                        "this test's own receipt was not resolved by the real end-to-end reconciliation run");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Verification pass Part A: resume shares the live path's IN_FLIGHT dedup ----------

    /**
     * Proves the fix: without it, {@code resumeConfirmWithdrawal} never registered in {@link
     * BankingWithdrawalProxyService}'s own {@code IN_FLIGHT}, so a live player reconnecting
     * while their own resume was still genuinely in flight (confirm dispatched, not yet
     * answered) could trigger a brand-new withdrawal for the exact same bank item -- racing the
     * resume with zero local dedup (Rails' own row lock would eventually reject it, but only
     * after an avoidable round trip). The live trigger's {@code prepareBehavior} is left
     * throwing here specifically so any regression (dedup silently stops working) fails loudly
     * as "prepare() was not expected to be called", not just a quieter assertion failure.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void resumingAWithdrawalConfirmDedupesAgainstALiveWithdrawalTriggerForTheSameBankItem(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = server.overworld();
        UUID operationId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        recordReceipt(level, operationId, playerUuid, BankTransferOperationType.WITHDRAWAL, new byte[]{1}, bankItemId, 8_000L);

        CompletableFuture<BankingConfirmResult> pendingConfirm = new CompletableFuture<>();
        FakeWithdrawalClient withdrawalClient = new FakeWithdrawalClient();
        withdrawalClient.confirmBehavior = () -> pendingConfirm;
        BankingWithdrawalProxyService.useClientForTesting(withdrawalClient);

        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        try {
            CompletableFuture<BankingWithdrawalResult> resumeFuture =
                    BankingWithdrawalProxyService.resumeConfirmWithdrawal(server, playerUuid, operationId, bankItemId);

            check(withdrawalClient.confirmRequests.size() == 1, "the resume did not actually dispatch its confirm call");
            check(BankingWithdrawalProxyService.isInFlightForTesting(bankItemId),
                    "the resume did not register in IN_FLIGHT for the item it is resuming");

            CompletableFuture<BankingWithdrawalResult> liveFuture =
                    BankingWithdrawalProxyService.triggerWithdrawal(player, teller, bankItemId);

            check(liveFuture.isDone(), "a live re-trigger for the same item must be rejected synchronously by local dedup");
            check(liveFuture.join() instanceof BankingWithdrawalResult.LocalFailure,
                    "expected a clean local dedup rejection for the live trigger, got " + liveFuture.join());
            check(withdrawalClient.prepareRequests.isEmpty(),
                    "the live trigger must never reach prepare -- deduped locally before any Rails call");

            pendingConfirm.complete(new BankingConfirmResult.Confirmed());

            helper.succeedWhen(() -> {
                check(resumeFuture.isDone(), "the resume itself did not complete");
                check(resumeFuture.join() instanceof BankingWithdrawalResult.Confirmed,
                        "the resume did not confirm cleanly: " + resumeFuture.join());
                check(!BankingWithdrawalProxyService.isInFlightForTesting(bankItemId),
                        "IN_FLIGHT was not cleared after the resume completed");
                check(BankTransferReceiptStore.get(level).find(operationId) == null, "the resumed receipt was not resolved");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    /**
     * The deposit counterpart, proving the opposite: deposit's {@code IN_FLIGHT} is keyed by
     * {@code (playerId, slotIndex)}, and by the time a deposit reaches confirm the item is
     * already gone from every slot -- so a live deposit using that same slot index is depositing
     * a genuinely different item, not racing the resume. This is deliberately not blocked;
     * confirmed here directly rather than merely assumed from the key shape.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void resumingADepositConfirmDoesNotBlockAnUnrelatedLiveDepositTrigger(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = server.overworld();
        UUID operationId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        recordReceipt(level, operationId, playerUuid, BankTransferOperationType.DEPOSIT, new byte[]{2}, bankItemId, 9_000L);

        CompletableFuture<BankingConfirmResult> pendingResumeConfirm = new CompletableFuture<>();
        UUID liveOperationId = UUID.randomUUID();
        UUID liveBankItemId = UUID.randomUUID();
        FakeDepositClient depositClient = new FakeDepositClient();
        depositClient.confirmBehavior = () -> pendingResumeConfirm;
        depositClient.prepareBehavior = () ->
                CompletableFuture.completedFuture(new BankingDepositPrepareResult.Success(liveOperationId, liveBankItemId));
        BankingDepositProxyService.useClientForTesting(depositClient);

        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND, 1));

        try {
            CompletableFuture<BankingDepositResult> resumeFuture =
                    BankingDepositProxyService.resumeConfirmDeposit(server, playerUuid, operationId, bankItemId);
            check(depositClient.confirmRequests.size() == 1, "the resume did not actually dispatch its confirm call");

            // A live deposit trigger using SLOT while the unrelated resume above is still
            // genuinely in flight -- must proceed unblocked, reaching prepare like any ordinary
            // independent deposit would.
            BankingDepositProxyService.triggerDeposit(player, teller, SLOT);

            helper.succeedWhen(() -> {
                check(depositClient.prepareRequests.size() == 1,
                        "the live deposit trigger was blocked/deduped by the unrelated in-flight resume -- it must not be");
                pendingResumeConfirm.complete(new BankingConfirmResult.Confirmed());
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Verification pass Part B: resume is silent w.r.t. any player/connection ----------

    /**
     * {@code resumeConfirmDeposit}/{@code resumeConfirmWithdrawal} take {@code (MinecraftServer,
     * UUID playerUuid, ...)} -- neither method's signature ever accepts a {@code ServerPlayer}
     * at all, so neither can send an S2C payload, refresh a screen, or touch a connection object
     * even in principle. This test proves the claim empirically, not just by signature
     * inspection: {@code playerUuid} deliberately corresponds to no {@code ServerPlayer} this
     * server has ever created (not "offline" in some softer sense -- genuinely never existed),
     * and the resume still completes cleanly with no exception, leaving the receipt resolved so
     * the account is simply correct the next time this player does a real {@code bank.open}.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void resumingAConfirmCompletesSilentlyWithNoPlayerObjectEverTouched(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = server.overworld();
        UUID operationId = UUID.randomUUID();
        UUID playerUuidWithNoCorrespondingPlayerObject = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        recordReceipt(
                level, operationId, playerUuidWithNoCorrespondingPlayerObject, BankTransferOperationType.DEPOSIT,
                new byte[]{9, 9}, bankItemId, 10_000L
        );
        check(server.getPlayerList().getPlayer(playerUuidWithNoCorrespondingPlayerObject) == null,
                "test setup invalid: this uuid must not correspond to any real player on this server");

        FakeDepositClient depositClient = new FakeDepositClient();
        depositClient.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingDepositProxyService.useClientForTesting(depositClient);

        try {
            CompletableFuture<BankingDepositResult> resumeFuture = BankingDepositProxyService.resumeConfirmDeposit(
                    server, playerUuidWithNoCorrespondingPlayerObject, operationId, bankItemId
            );

            helper.succeedWhen(() -> {
                check(resumeFuture.isDone(), "the resume did not complete");
                check(resumeFuture.join() instanceof BankingDepositResult.Confirmed,
                        "resuming for a player with no live object at all must still resolve cleanly: " + resumeFuture.join());
                check(BankTransferReceiptStore.get(level).find(operationId) == null,
                        "the receipt must be resolved even though the owning player was never online during the resume");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Helpers ----------

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

    private static ServerPlayer setUpPlayer(GameTestHelper helper, ServiceNpcEntity teller) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.teleportTo(teller.getX() + 1.0, teller.getY(), teller.getZ());
        return player;
    }

    private static BankTransferReceipt recordReceipt(
            ServerLevel level, UUID operationId, UUID playerUuid, BankTransferOperationType type,
            byte[] payload, UUID bankItemId, long createdAt
    ) {
        BankTransferReceipts.record(level, operationId, playerUuid, type, payload, null, bankItemId, createdAt);
        return BankTransferReceiptStore.get(level).find(operationId);
    }

    private static BankTransferReceiptStore.ScanResult scanWithOnlyPending(BankTransferReceipt receipt) {
        return new BankTransferReceiptStore.ScanResult(List.of(receipt), List.of(), List.of());
    }

    private static void cleanUp() {
        BankingDepositProxyService.resetClientForTesting();
        BankingDepositProxyService.resetInFlightTrackingForTesting();
        BankingWithdrawalProxyService.resetClientForTesting();
        BankingWithdrawalProxyService.resetInFlightTrackingForTesting();
        ServiceNpcRegistryCache.clear();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class FakeDepositClient implements BankingDepositClientPort {
        java.util.function.Supplier<CompletableFuture<BankingDepositPrepareResult>> prepareBehavior =
                () -> { throw new IllegalStateException("prepare() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingConfirmResult>> confirmBehavior =
                () -> { throw new IllegalStateException("confirm() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingCancelResult>> cancelBehavior =
                () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());

        final List<BankingDepositPrepareRequest> prepareRequests = new CopyOnWriteArrayList<>();
        final List<BankingOperationRequest> confirmRequests = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<BankingDepositPrepareResult> prepare(MinecraftServer server, BankingDepositPrepareRequest request) {
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

        final List<BankingWithdrawalPrepareRequest> prepareRequests = new CopyOnWriteArrayList<>();
        final List<BankingOperationRequest> confirmRequests = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<BankingWithdrawalPrepareResult> prepareWithdrawal(
                MinecraftServer server, BankingWithdrawalPrepareRequest request
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
            return cancelBehavior.get();
        }
    }
}
