package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceipt;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStatus;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStore;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.mixin.PlayerListAccessorMixin;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import com.seggellion.britannia_mod.service.banking.BankingCancelResult;
import com.seggellion.britannia_mod.service.banking.BankingConfirmResult;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyWithdrawalAbortReason;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyWithdrawalClientPort;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyWithdrawalLocalRejectionReason;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyWithdrawalPrepareRequest;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyWithdrawalPrepareResult;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyWithdrawalProxyService;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyWithdrawalResult;
import com.seggellion.britannia_mod.service.banking.BankingOperationRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
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
 * Milestone 10 NeoForge Slice 2: the currency withdrawal path, exercised through {@link
 * BankingCurrencyWithdrawalProxyService}'s test-support entry points with a per-method {@link
 * FakeClient} substituted -- mirroring {@link BankingCurrencyDepositProxyServiceGameTests}'/
 * {@link BankingWithdrawalProxyServiceGameTests}' established structure test-for-test where the
 * flows correspond, because currency withdrawal must get the identical crash-safety and
 * durability-fix guarantees the other three flows already prove, not a weaker version.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankingCurrencyWithdrawalProxyServiceGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BANK_TYPE_KEY = "bank_teller";

    private BankingCurrencyWithdrawalProxyServiceGameTests() {
    }

    // ---------- Happy path: each denomination, full round trip ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void happyPathWithdrawsGoldAndResolvesReceipt(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        UUID operationId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingCurrencyWithdrawalPrepareResult.Success(operationId));
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingCurrencyWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyWithdrawalResult> future =
                    BankingCurrencyWithdrawalProxyService.triggerCurrencyWithdrawalForTesting(player, teller, "gold", 42);

            helper.succeedWhen(() -> {
                check(future.isDone(), "currency withdrawal did not complete");
                BankingCurrencyWithdrawalResult result = future.join();
                check(result instanceof BankingCurrencyWithdrawalResult.Confirmed, "expected Confirmed, got " + result);
                check(((BankingCurrencyWithdrawalResult.Confirmed) result).operationPublicId().equals(operationId),
                        "wrong operation id in result");

                ItemStack inSlot = findStack(player, ItemRegistry.GOLD_COIN.get());
                check(inSlot != null && inSlot.getCount() == 42, "expected 42 gold coins in the inventory, found " + inSlot);

                check(fake.prepareRequests.size() == 1, "expected exactly one prepare request");
                BankingCurrencyWithdrawalPrepareRequest sent = fake.prepareRequests.get(0);
                check(sent.currencyKey().equals("gold") && sent.amount() == 42, "wrong key/amount sent: " + sent);
                check(fake.confirmRequests.size() == 1, "expected exactly one confirm request");
                check(fake.cancelRequests.isEmpty(), "cancel must never be called on a clean happy path");

                check(!hasAnyReceiptFor(player.serverLevel(), operationId),
                        "the receipt must be resolved after a clean confirm");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    /**
     * Silver and copper each map to their own independent wire key, mirroring currency deposit's
     * own equivalent proof. Two separate tests, not one combined sequence: {@code
     * helper.succeedWhen}'s lambda is retried from the top on every tick until it stops throwing,
     * so it must never contain a non-idempotent side effect like triggering a second withdrawal
     * or clearing the registry cache mid-check -- exactly the trap a single combined
     * silver-then-copper test fell into (an early retry re-cleared the registry before the
     * second trigger could resolve its teller).
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void silverDenominationMapsToItsOwnWireKey(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        UUID silverOp = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingCurrencyWithdrawalPrepareResult.Success(silverOp));
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingCurrencyWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyWithdrawalResult> future =
                    BankingCurrencyWithdrawalProxyService.triggerCurrencyWithdrawalForTesting(player, teller, "silver", 15);

            helper.succeedWhen(() -> {
                check(future.isDone(), "silver withdrawal did not complete");
                check(future.join() instanceof BankingCurrencyWithdrawalResult.Confirmed,
                        "silver withdrawal did not confirm cleanly: " + future.join());
                check(fake.prepareRequests.get(0).currencyKey().equals("silver"), "wrong key sent for silver withdrawal");
                ItemStack silverStack = findStack(player, ItemRegistry.SILVER_COIN.get());
                check(silverStack != null && silverStack.getCount() == 15, "expected 15 silver coins, found " + silverStack);
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void copperDenominationMapsToItsOwnWireKey(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        UUID copperOp = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingCurrencyWithdrawalPrepareResult.Success(copperOp));
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingCurrencyWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyWithdrawalResult> future =
                    BankingCurrencyWithdrawalProxyService.triggerCurrencyWithdrawalForTesting(player, teller, "copper", 88);

            helper.succeedWhen(() -> {
                check(future.isDone(), "copper withdrawal did not complete");
                check(future.join() instanceof BankingCurrencyWithdrawalResult.Confirmed,
                        "copper withdrawal did not confirm cleanly: " + future.join());
                check(fake.prepareRequests.get(0).currencyKey().equals("copper"), "wrong key sent for copper withdrawal");
                ItemStack copperStack = findStack(player, ItemRegistry.COPPER_COIN.get());
                check(copperStack != null && copperStack.getCount() == 88, "expected 88 copper coins, found " + copperStack);
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- First capacity check: rejected locally, before any Rails call ----------

    /**
     * A genuinely full inventory (36 main slots + offhand, all non-matching) rejects before
     * prepare is ever called -- zero Rails calls, zero reservation, no partial insertion, no
     * world drop. Mirrors {@code BankingWithdrawalProxyServiceGameTests}'
     * {@code fullInventoryIsRejectedByPreCheckWithNoReceiptOrRailsRiskAndCancelCalled}, but proves
     * the FIRST (pre-prepare) check specifically: cancel must never be called either, since
     * nothing was ever reserved to cancel.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void fullInventoryIsRejectedLocallyWithZeroRailsCallsAndNoPartialInsertion(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        fillInventoryCompletely(player);

        FakeClient fake = new FakeClient();
        BankingCurrencyWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyWithdrawalResult> future =
                    BankingCurrencyWithdrawalProxyService.triggerCurrencyWithdrawalForTesting(player, teller, "gold", 1);

            helper.succeedWhen(() -> {
                check(future.isDone(), "currency withdrawal did not complete");
                BankingCurrencyWithdrawalResult result = future.join();
                check(result instanceof BankingCurrencyWithdrawalResult.RejectedLocally,
                        "expected RejectedLocally, got " + result);
                check(((BankingCurrencyWithdrawalResult.RejectedLocally) result).reason()
                                == BankingCurrencyWithdrawalLocalRejectionReason.INSUFFICIENT_CAPACITY,
                        "wrong local rejection reason: " + result);

                check(fake.prepareRequests.isEmpty(), "prepare must never be called when the first capacity check rejects");
                check(fake.cancelRequests.isEmpty(), "cancel must never be called -- nothing was ever reserved");
                check(fake.confirmRequests.isEmpty(), "confirm must never be called");

                check(findStack(player, ItemRegistry.GOLD_COIN.get()) == null,
                        "no gold must have been conjured into the already-full inventory");
                // The real (non-test-bypass) scan, which safely handles a store that may not
                // have been created yet -- unlike readFreshStore, which returns null for that
                // case (see aCrashBeforeTheReceiptFlush-style tests elsewhere for the same
                // reasoning).
                BankTransferReceiptStore.ScanResult scan =
                        com.seggellion.britannia_mod.bank.transfer.BankTransferReceipts.scanUnresolved(player.serverLevel());
                check(scan.pending().stream().noneMatch(r -> r.playerUuid().equals(player.getUUID())
                                && r.operationType() == com.seggellion.britannia_mod.bank.transfer.BankTransferOperationType.WITHDRAWAL),
                        "a receipt must never be written when the first capacity check rejects");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Second capacity check: catches a real race during the prepare round trip ----------

    /**
     * Proves the class docs' own two-checks reasoning is not just plausible but real: the
     * inventory has exactly enough room when the first check runs, then genuinely fills up
     * while prepare is still in flight (the real yield point the first check cannot see across),
     * and the second check -- run immediately before insertion -- catches it. Cancel is called
     * (Rails already reserved the amount), no receipt is written (the second check runs before
     * the receipt write), and no coins are inserted.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aRaceThatFillsTheInventoryDuringThePrepareRoundTripIsCaughtByTheSecondCapacityCheck(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        // Exactly one empty slot -- enough room for a single stack of gold when the FIRST check runs.
        for (int i = 0; i < 35; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        player.getInventory().offhand.set(0, new ItemStack(Items.COBBLESTONE, 64));

        UUID operationId = UUID.randomUUID();
        CompletableFuture<BankingCurrencyWithdrawalPrepareResult> pendingPrepare = new CompletableFuture<>();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> pendingPrepare;
        fake.cancelBehavior = () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        BankingCurrencyWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyWithdrawalResult> future =
                    BankingCurrencyWithdrawalProxyService.triggerCurrencyWithdrawalForTesting(player, teller, "gold", 30);

            // The real yield point: while prepare is still pending, the inventory genuinely
            // fills up -- exactly what could happen during a real network round trip.
            player.getInventory().setItem(35, new ItemStack(Items.COBBLESTONE, 64));
            pendingPrepare.complete(new BankingCurrencyWithdrawalPrepareResult.Success(operationId));

            helper.succeedWhen(() -> {
                check(future.isDone(), "currency withdrawal did not complete");
                BankingCurrencyWithdrawalResult result = future.join();
                check(result instanceof BankingCurrencyWithdrawalResult.Aborted, "expected Aborted, got " + result);
                BankingCurrencyWithdrawalResult.Aborted aborted = (BankingCurrencyWithdrawalResult.Aborted) result;
                check(aborted.reason() == BankingCurrencyWithdrawalAbortReason.INSUFFICIENT_CAPACITY,
                        "wrong abort reason: " + aborted.reason());
                check(aborted.operationPublicId().equals(operationId), "wrong operation id in Aborted result");

                check(fake.cancelRequests.size() == 1, "cancel must be called once the second check rejects post-prepare");
                check(fake.confirmRequests.isEmpty(), "confirm must never be called");
                check(!hasAnyReceiptFor(player.serverLevel(), operationId),
                        "no receipt may exist -- the second check runs before the receipt write");
                check(findStack(player, ItemRegistry.GOLD_COIN.get()) == null, "no gold must have been inserted");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // Milestone 14 priority 2 (context enforcement, dimension 5): mirrors
    // aRaceThatFillsTheInventoryDuringThePrepareRoundTripIsCaughtByTheSecondCapacityCheck
    // exactly, above, just with the player walking out of range during the prepare round trip
    // instead of the inventory filling up -- both are caught before insertion, cancel-and-report.
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void playerWalkingOutOfRangeDuringThePrepareRoundTripIsCaughtBeforeInsertion(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        UUID operationId = UUID.randomUUID();
        CompletableFuture<BankingCurrencyWithdrawalPrepareResult> pendingPrepare = new CompletableFuture<>();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> pendingPrepare;
        fake.cancelBehavior = () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        BankingCurrencyWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyWithdrawalResult> future =
                    BankingCurrencyWithdrawalProxyService.triggerCurrencyWithdrawalForTesting(player, teller, "gold", 30);

            // The player walks far out of interaction range while prepare is still in flight.
            player.teleportTo(teller.getX() + 100.0, teller.getY(), teller.getZ());
            pendingPrepare.complete(new BankingCurrencyWithdrawalPrepareResult.Success(operationId));

            helper.succeedWhen(() -> {
                check(future.isDone(), "currency withdrawal did not complete");
                BankingCurrencyWithdrawalResult result = future.join();
                check(result instanceof BankingCurrencyWithdrawalResult.Aborted, "expected Aborted, got " + result);
                BankingCurrencyWithdrawalResult.Aborted aborted = (BankingCurrencyWithdrawalResult.Aborted) result;
                check(aborted.reason() == BankingCurrencyWithdrawalAbortReason.TELLER_NO_LONGER_VALID,
                        "wrong abort reason: " + aborted.reason());
                check(aborted.operationPublicId().equals(operationId), "wrong operation id in Aborted result");

                check(fake.cancelRequests.size() == 1, "cancel must be called once the teller is found out of range");
                check(fake.confirmRequests.isEmpty(), "confirm must never be called");
                check(!hasAnyReceiptFor(player.serverLevel(), operationId),
                        "no receipt may exist -- the teller check runs before the receipt write");
                check(findStack(player, ItemRegistry.GOLD_COIN.get()) == null, "no gold must have been inserted");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- IN_FLIGHT dedup: same denomination blocked, different denominations independent ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aRepeatTriggerForTheSameDenominationWhileOneIsInFlightDoesNotDispatchASecondPrepare(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        AtomicInteger prepareDispatchCount = new AtomicInteger();
        CompletableFuture<BankingCurrencyWithdrawalPrepareResult> pending = new CompletableFuture<>();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> {
            prepareDispatchCount.incrementAndGet();
            return pending;
        };
        BankingCurrencyWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyWithdrawalResult> first =
                    BankingCurrencyWithdrawalProxyService.triggerCurrencyWithdrawalForTesting(player, teller, "gold", 5);
            CompletableFuture<BankingCurrencyWithdrawalResult> second =
                    BankingCurrencyWithdrawalProxyService.triggerCurrencyWithdrawalForTesting(player, teller, "gold", 5);

            check(prepareDispatchCount.get() == 1,
                    "a repeat trigger for the same denomination dispatched a second prepare call (count=" + prepareDispatchCount.get() + ")");
            check(second.isDone() && second.join() instanceof BankingCurrencyWithdrawalResult.LocalFailure,
                    "the duplicate trigger did not report a local failure immediately");

            pending.complete(new BankingCurrencyWithdrawalPrepareResult.Rejected(
                    com.seggellion.britannia_mod.service.banking.BankingTransferOutcome.INSUFFICIENT_BALANCE, false));

            helper.succeedWhen(() -> {
                check(first.isDone(), "the original withdrawal did not complete");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    /** Gold and silver are independent balances -- an in-flight gold withdrawal must never block a concurrent silver one. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aSimultaneousAttemptForADifferentDenominationIsNotBlockedByAnInFlightOne(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        // The static client is shared across every currencyKey -- a single Supplier can't tell
        // gold's request from silver's, so this fake distinguishes by inspecting its own
        // already-tracked prepareRequests (populated synchronously, before the behavior runs,
        // since every call here is on the same single-threaded GameTest tick).
        CompletableFuture<BankingCurrencyWithdrawalPrepareResult> pendingGold = new CompletableFuture<>();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> {
            BankingCurrencyWithdrawalPrepareRequest last = fake.prepareRequests.get(fake.prepareRequests.size() - 1);
            if (last.currencyKey().equals("gold")) return pendingGold;
            return CompletableFuture.completedFuture(new BankingCurrencyWithdrawalPrepareResult.Rejected(
                    com.seggellion.britannia_mod.service.banking.BankingTransferOutcome.INSUFFICIENT_BALANCE, false));
        };
        BankingCurrencyWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyWithdrawalResult> goldFuture =
                    BankingCurrencyWithdrawalProxyService.triggerCurrencyWithdrawalForTesting(player, teller, "gold", 5);
            check(BankingCurrencyWithdrawalProxyService.isInFlightForTesting(player.getUUID(), "gold"),
                    "gold must be marked in-flight while its prepare is pending");

            CompletableFuture<BankingCurrencyWithdrawalResult> silverFuture =
                    BankingCurrencyWithdrawalProxyService.triggerCurrencyWithdrawalForTesting(player, teller, "silver", 7);

            // Both prepareCurrencyWithdrawal calls happen synchronously as part of triggering
            // (before any server.execute-deferred continuation), so this is reliable immediately.
            check(fake.prepareRequests.stream().filter(r -> r.currencyKey().equals("gold")).count() == 1,
                    "gold prepare must have been dispatched exactly once");
            check(fake.prepareRequests.stream().filter(r -> r.currencyKey().equals("silver")).count() == 1,
                    "silver prepare must have been dispatched exactly once -- it must not have been blocked "
                            + "behind, or folded into, the still-pending gold prepare");

            pendingGold.complete(new BankingCurrencyWithdrawalPrepareResult.Rejected(
                    com.seggellion.britannia_mod.service.banking.BankingTransferOutcome.INSUFFICIENT_BALANCE, false));

            helper.succeedWhen(() -> {
                check(goldFuture.isDone(), "the gold withdrawal did not complete");
                check(silverFuture.isDone(), "the concurrent silver withdrawal did not complete");
                check(silverFuture.join() instanceof BankingCurrencyWithdrawalResult.Rejected,
                        "the concurrent silver withdrawal must have progressed to its own, independent Rails "
                                + "rejection (not a LocalFailure duplicate-of-gold rejection), got " + silverFuture.join());
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Hardening pass: cross-denomination capacity race for the same slot ----------

    /**
     * IN_FLIGHT deliberately allows gold and silver withdrawals to proceed concurrently (see the
     * class docs). This constructs the case that raises: an inventory with exactly one slot of
     * room, requested by two simultaneous, different-denomination withdrawals -- enough for
     * EITHER one alone, not both. Both individually pass their own first (pre-prepare) capacity
     * check, since neither has inserted anything yet at that point; both prepares are then
     * released "as simultaneously as this architecture allows" (both {@code complete()} calls
     * made back to back, with no tick boundary between them from this test's own perspective).
     *
     * <p>Traced, not assumed, why this resolves safely without any new lock: {@code
     * MinecraftServer} is a {@code ReentrantBlockableEventLoop}, itself a {@code
     * BlockableEventLoop} -- its real, decompiled {@code execute(Runnable)} enqueues onto a
     * single {@code pendingRunnables} queue (or runs inline if already on that thread), and
     * {@code pollTask()}/{@code doRunTask()} drain that queue one {@code Runnable} at a time,
     * running each fully to completion before the next is even dequeued. Both denominations'
     * post-prepare continuations (second capacity check, receipt write, insertion) are each one
     * such {@code Runnable}, entirely synchronous with no yield point inside them -- so whichever
     * one the server thread dequeues first runs its ENTIRE sequence, including the actual {@code
     * Inventory#add}, before the other one's {@code Runnable} is even removed from the queue.
     * There is no statement-level window in which both could observe the slot as still free.
     * This is the same "no yield point between adjacent statements" guarantee this program's own
     * durability-fix work already established for a single flow, here shown to generalize across
     * two independent flows sharing the same single-threaded executor -- not a new mechanism,
     * the same one, applied one level higher. Exactly one of the two must therefore win the
     * slot; the other's own second capacity check (which re-reads the ACTUAL, now-updated
     * inventory) correctly rejects. Run multiple times in this hardening pass with no flake
     * observed (see completion report), not asserted from a single pass.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void concurrentDifferentDenominationWithdrawalsCompetingForTheSameSingleSlotResolveSafely(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        // Exactly one empty slot -- room for ONE denomination's resulting stack, not both.
        for (int i = 0; i < 35; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        player.getInventory().offhand.set(0, new ItemStack(Items.COBBLESTONE, 64));

        UUID goldOp = UUID.randomUUID();
        UUID silverOp = UUID.randomUUID();
        CompletableFuture<BankingCurrencyWithdrawalPrepareResult> pendingGold = new CompletableFuture<>();
        CompletableFuture<BankingCurrencyWithdrawalPrepareResult> pendingSilver = new CompletableFuture<>();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> {
            BankingCurrencyWithdrawalPrepareRequest last = fake.prepareRequests.get(fake.prepareRequests.size() - 1);
            return last.currencyKey().equals("gold") ? pendingGold : pendingSilver;
        };
        fake.cancelBehavior = () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingCurrencyWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyWithdrawalResult> goldFuture =
                    BankingCurrencyWithdrawalProxyService.triggerCurrencyWithdrawalForTesting(player, teller, "gold", 50);
            CompletableFuture<BankingCurrencyWithdrawalResult> silverFuture =
                    BankingCurrencyWithdrawalProxyService.triggerCurrencyWithdrawalForTesting(player, teller, "silver", 50);

            // Both first (pre-prepare) capacity checks passed -- reliable synchronously, since
            // prepareCurrencyWithdrawal (and thus each first check before it) runs before any
            // server.execute deferral.
            check(fake.prepareRequests.size() == 2, "both first capacity checks must have passed, reaching prepare");

            pendingGold.complete(new BankingCurrencyWithdrawalPrepareResult.Success(goldOp));
            pendingSilver.complete(new BankingCurrencyWithdrawalPrepareResult.Success(silverOp));

            helper.succeedWhen(() -> {
                check(goldFuture.isDone(), "gold withdrawal did not complete");
                check(silverFuture.isDone(), "silver withdrawal did not complete");

                BankingCurrencyWithdrawalResult goldResult = goldFuture.join();
                BankingCurrencyWithdrawalResult silverResult = silverFuture.join();
                boolean goldWon = goldResult instanceof BankingCurrencyWithdrawalResult.Confirmed;
                boolean silverWon = silverResult instanceof BankingCurrencyWithdrawalResult.Confirmed;
                check(goldWon != silverWon, "expected exactly one winner, got gold=" + goldResult + " silver=" + silverResult);

                BankingCurrencyWithdrawalResult loserResult = goldWon ? silverResult : goldResult;
                check(loserResult instanceof BankingCurrencyWithdrawalResult.Aborted,
                        "the loser must cleanly abort via its own second capacity check, got " + loserResult);
                check(((BankingCurrencyWithdrawalResult.Aborted) loserResult).reason() == BankingCurrencyWithdrawalAbortReason.INSUFFICIENT_CAPACITY,
                        "wrong abort reason for the loser: " + loserResult);

                // No partial insertion, no double-insertion, no drop: exactly one denomination's
                // coins are actually present, in exactly the requested amount.
                ItemStack goldStack = findStack(player, ItemRegistry.GOLD_COIN.get());
                ItemStack silverStack = findStack(player, ItemRegistry.SILVER_COIN.get());
                if (goldWon) {
                    check(goldStack != null && goldStack.getCount() == 50, "the winner's coins must be fully present");
                    check(silverStack == null, "the loser's coins must never be partially inserted");
                } else {
                    check(silverStack != null && silverStack.getCount() == 50, "the winner's coins must be fully present");
                    check(goldStack == null, "the loser's coins must never be partially inserted");
                }

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Hardening pass: cross-flow race (item withdrawal vs. currency withdrawal) ----------

    /**
     * Part C of the hardening pass: item withdrawal ({@code bankItemPublicId}-keyed dedup) and
     * currency withdrawal ({@code (player, currencyKey)}-keyed dedup) are entirely separate
     * dedup mechanisms -- nothing today stops a player from having one of each in flight at
     * once, both competing for the same remaining inventory slots. This is the identical risk
     * shape as {@link #concurrentDifferentDenominationWithdrawalsCompetingForTheSameSingleSlotResolveSafely},
     * just spanning two proxy service classes instead of two denominations within one -- and
     * governed by the exact same structural guarantee (the same single-threaded {@code
     * MinecraftServer} task queue serializes both flows' post-prepare continuations regardless
     * of which proxy service they belong to), not a new mechanism. Proven directly rather than
     * inferred from Part A alone, since a cross-class interaction is exactly the kind of place
     * an unstated assumption could quietly fail.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void concurrentItemAndCurrencyWithdrawalsCompetingForTheSameSingleSlotResolveSafely(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        // Exactly one empty slot -- room for the withdrawn diamond OR the withdrawn gold, not both.
        for (int i = 0; i < 35; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        player.getInventory().offhand.set(0, new ItemStack(Items.COBBLESTONE, 64));

        net.minecraft.core.HolderLookup.Provider registries = player.registryAccess();
        ItemStack toWithdraw = new ItemStack(Items.DIAMOND, 1);
        byte[] payload = com.seggellion.britannia_mod.bank.item.BankItemCodec.serialize(toWithdraw.copy(), registries);
        String fingerprint = com.seggellion.britannia_mod.bank.item.BankItemFingerprint.fingerprint(toWithdraw, registries);
        double weight = com.seggellion.britannia_mod.bank.item.BankItemWeight.resolve(toWithdraw);

        UUID itemOp = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        UUID goldOp = UUID.randomUUID();

        CompletableFuture<com.seggellion.britannia_mod.service.banking.BankingWithdrawalPrepareResult> pendingItem = new CompletableFuture<>();
        com.seggellion.britannia_mod.service.banking.BankingWithdrawalClientPort itemFake =
                new com.seggellion.britannia_mod.service.banking.BankingWithdrawalClientPort() {
                    @Override
                    public CompletableFuture<com.seggellion.britannia_mod.service.banking.BankingWithdrawalPrepareResult> prepareWithdrawal(
                            MinecraftServer server, com.seggellion.britannia_mod.service.banking.BankingWithdrawalPrepareRequest request
                    ) {
                        return pendingItem;
                    }

                    @Override
                    public CompletableFuture<BankingConfirmResult> confirm(MinecraftServer server, BankingOperationRequest request) {
                        return CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
                    }

                    @Override
                    public CompletableFuture<BankingCancelResult> cancel(MinecraftServer server, BankingOperationRequest request) {
                        return CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
                    }
                };
        com.seggellion.britannia_mod.service.banking.BankingWithdrawalProxyService.useClientForTesting(itemFake);

        CompletableFuture<BankingCurrencyWithdrawalPrepareResult> pendingGold = new CompletableFuture<>();
        FakeClient currencyFake = new FakeClient();
        currencyFake.prepareBehavior = () -> pendingGold;
        currencyFake.cancelBehavior = () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        currencyFake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingCurrencyWithdrawalProxyService.useClientForTesting(currencyFake);

        try {
            CompletableFuture<com.seggellion.britannia_mod.service.banking.BankingWithdrawalResult> itemFuture =
                    com.seggellion.britannia_mod.service.banking.BankingWithdrawalProxyService.triggerWithdrawalForTesting(
                            player, teller, bankItemId);
            CompletableFuture<BankingCurrencyWithdrawalResult> goldFuture =
                    BankingCurrencyWithdrawalProxyService.triggerCurrencyWithdrawalForTesting(player, teller, "gold", 50);

            pendingItem.complete(new com.seggellion.britannia_mod.service.banking.BankingWithdrawalPrepareResult.Success(
                    itemOp, bankItemId, com.seggellion.britannia_mod.bank.item.BankItemSchemaVersion.CURRENT, payload, fingerprint, weight));
            pendingGold.complete(new BankingCurrencyWithdrawalPrepareResult.Success(goldOp));

            helper.succeedWhen(() -> {
                check(itemFuture.isDone(), "item withdrawal did not complete");
                check(goldFuture.isDone(), "currency withdrawal did not complete");

                boolean itemWon = itemFuture.join() instanceof com.seggellion.britannia_mod.service.banking.BankingWithdrawalResult.Confirmed;
                boolean goldWon = goldFuture.join() instanceof BankingCurrencyWithdrawalResult.Confirmed;
                check(itemWon != goldWon, "expected exactly one winner, got item=" + itemFuture.join() + " gold=" + goldFuture.join());

                boolean diamondPresent = findStack(player, Items.DIAMOND) != null;
                ItemStack goldStack = findStack(player, ItemRegistry.GOLD_COIN.get());
                if (itemWon) {
                    check(diamondPresent, "the winning item withdrawal's diamond must be present");
                    check(goldStack == null, "the losing currency withdrawal's gold must never be partially inserted");
                } else {
                    check(goldStack != null && goldStack.getCount() == 50, "the winning currency withdrawal's gold must be fully present");
                    check(!diamondPresent, "the losing item withdrawal's diamond must never be partially inserted");
                }

                com.seggellion.britannia_mod.service.banking.BankingWithdrawalProxyService.resetClientForTesting();
                com.seggellion.britannia_mod.service.banking.BankingWithdrawalProxyService.resetInFlightTrackingForTesting();
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            com.seggellion.britannia_mod.service.banking.BankingWithdrawalProxyService.resetClientForTesting();
            com.seggellion.britannia_mod.service.banking.BankingWithdrawalProxyService.resetInFlightTrackingForTesting();
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Hardening pass: capacity check correctness with an existing partial matching stack ----------

    /**
     * Part B of the hardening pass: {@link
     * com.seggellion.britannia_mod.service.banking.BankingWithdrawalProxyService#hasSufficientCapacity}'s
     * real source (traced, not assumed) already accounts for an existing partial matching stack
     * -- {@code available += perSlotCap - slot.getCount()} for any slot that is {@code
     * isStackable() && isSameItemSameComponents(...)} with the requested stack, exactly mirroring
     * what {@code Inventory#add}'s own {@code getSlotWithRemainingSpace}/{@code addResource} do.
     * Coins carry no components, so two stacks of the same denomination always match. This
     * proves that real behavior directly for currency's own usage: 40 gold already in one slot,
     * every other slot and offhand full, withdrawing 59 more (40 + 59 = 99, exactly the coin's
     * own max stack size) must succeed by merging into the existing stack -- not be falsely
     * rejected as if every withdrawn coin needed its own fresh empty slot.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void withdrawalMergesIntoAnExistingPartialMatchingCoinStackRatherThanNeedingAFreeSlot(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(0, new ItemStack(ItemRegistry.GOLD_COIN.get(), 40));
        for (int i = 1; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        player.getInventory().offhand.set(0, new ItemStack(Items.COBBLESTONE, 64));

        UUID operationId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingCurrencyWithdrawalPrepareResult.Success(operationId));
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingCurrencyWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyWithdrawalResult> future =
                    BankingCurrencyWithdrawalProxyService.triggerCurrencyWithdrawalForTesting(player, teller, "gold", 59);

            helper.succeedWhen(() -> {
                check(future.isDone(), "currency withdrawal did not complete");
                check(future.join() instanceof BankingCurrencyWithdrawalResult.Confirmed,
                        "expected the merge-into-existing-stack withdrawal to succeed, got " + future.join());
                ItemStack gold = findStack(player, ItemRegistry.GOLD_COIN.get());
                check(gold != null && gold.getCount() == 99,
                        "expected the existing 40-coin stack to merge with the withdrawn 59 into a single 99-coin stack, found " + gold);
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    /** The boundary case: one more than the existing stack's real headroom must be rejected, not falsely accepted. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void withdrawalOneCoinBeyondTheExistingStacksHeadroomIsRejectedNotFalselyAccepted(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(0, new ItemStack(ItemRegistry.GOLD_COIN.get(), 40));
        for (int i = 1; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        player.getInventory().offhand.set(0, new ItemStack(Items.COBBLESTONE, 64));

        FakeClient fake = new FakeClient();
        BankingCurrencyWithdrawalProxyService.useClientForTesting(fake);

        try {
            // 40 + 60 = 100, one more than the coin's own 99 max stack size, and no other slot
            // is free -- must reject at the first (pre-prepare) check, not attempt a partial fit.
            CompletableFuture<BankingCurrencyWithdrawalResult> future =
                    BankingCurrencyWithdrawalProxyService.triggerCurrencyWithdrawalForTesting(player, teller, "gold", 60);

            helper.succeedWhen(() -> {
                check(future.isDone(), "currency withdrawal did not complete");
                check(future.join() instanceof BankingCurrencyWithdrawalResult.RejectedLocally,
                        "expected a clean local rejection, got " + future.join());
                check(fake.prepareRequests.isEmpty(), "prepare must never be called for a request that cannot fit");
                ItemStack gold = findStack(player, ItemRegistry.GOLD_COIN.get());
                check(gold != null && gold.getCount() == 40, "the existing 40-coin stack must be untouched by a rejected withdrawal");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Crash after insertion, before confirm: receipt + on-disk durability + resume ----------

    /**
     * Simulates a crash between insertion and confirmation: {@link
     * BankingCurrencyWithdrawalProxyService#prepareAndInsertForTesting} runs steps 1-5 only and
     * deliberately never calls confirm. Proves three things a real crash at this exact point
     * would need to be recoverable: the durable receipt survives with the right {@code
     * currencyAmount}; the coins are ALREADY durably reflected in the player's own on-disk file
     * (the durability fix's own {@code forceSave}, proven via the same bypass-the-live-instance
     * disk-read technique {@code BankTransferPlayerDurabilityGameTests} established); and a
     * resumed confirm (simulating the restart) finds the receipt and confirms it rather than
     * re-attempting insertion.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aCrashAfterInsertionLeavesADurableReceiptAndOnDiskCoinsThenResumesToConfirm(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        UUID operationId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingCurrencyWithdrawalPrepareResult.Success(operationId));
        // confirmBehavior deliberately left throwing -- confirm must never be reached by this path.
        BankingCurrencyWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyWithdrawalProxyService.PrepareAndInsertOutcome> future =
                    BankingCurrencyWithdrawalProxyService.prepareAndInsertForTesting(player, teller, "gold", 33);

            helper.succeedWhen(() -> {
                check(future.isDone(), "prepareAndInsert did not complete");
                BankingCurrencyWithdrawalProxyService.PrepareAndInsertOutcome outcome = future.join();
                check(outcome instanceof BankingCurrencyWithdrawalProxyService.PrepareAndInsertOutcome.Inserted,
                        "expected Inserted, got " + outcome);

                // "crash" simulated here: confirm is never called.
                check(fake.confirmRequests.isEmpty(), "confirm must not have been called before the simulated crash");

                ItemStack live = findStack(player, ItemRegistry.GOLD_COIN.get());
                check(live != null && live.getCount() == 33, "the coins must genuinely be in the live inventory after insertion");

                BankTransferReceipt survived = findReceipt(player.serverLevel(), operationId);
                check(survived.currencyAmount() != null && survived.currencyAmount() == 33L,
                        "the on-disk receipt's currencyAmount did not match what was actually inserted: " + survived.currencyAmount());
                check(survived.bankItemPublicId() == null, "a currency withdrawal receipt must carry no bank item public id");
                check(survived.status() == BankTransferReceiptStatus.PENDING_LOCAL_ACTION,
                        "confirm was never called, so the receipt must still be PENDING_LOCAL_ACTION");

                // The durability fix's own proof: the player's ON-DISK file (never the live
                // in-memory instance) already reflects the inserted coins, forced there by
                // forceSave before this method ever returned.
                ServerPlayer reloaded = loadFreshFromDisk(player);
                ItemStack onDisk = findStack(reloaded, ItemRegistry.GOLD_COIN.get());
                check(onDisk != null && onDisk.getCount() == 33,
                        "the inserted coins must already be durable on disk immediately after prepareAndInsert -- found "
                                + onDisk + " instead");

                BankTransferReceiptStore.ScanResult scan = readFreshStore(player.serverLevel()).scanUnresolved();
                check(scan.pending().stream().anyMatch(r -> r.operationId().equals(operationId)),
                        "the surviving currency withdrawal receipt must appear in scanUnresolved()'s pending bucket");

                // A real crash would not clean up IN_FLIGHT either -- prove that's still true.
                check(BankingCurrencyWithdrawalProxyService.isInFlightForTesting(player.getUUID(), "gold"),
                        "a simulated crash must leave the denomination marked in-flight, matching real crash semantics");

                // Resume: simulate the restart (IN_FLIGHT cleared), then confirm through the
                // exact same core the live path uses -- must confirm, never re-insert.
                BankingCurrencyWithdrawalProxyService.resetInFlightTrackingForTesting();
                fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());

                CompletableFuture<BankingCurrencyWithdrawalResult> resumed =
                        BankingCurrencyWithdrawalProxyService.resumeConfirmCurrencyWithdrawal(
                                player.server, player.getUUID(), operationId);
                check(resumed.join() instanceof BankingCurrencyWithdrawalResult.Confirmed,
                        "resumed confirm did not report Confirmed: " + resumed.join());
                check(fake.confirmRequests.size() == 1 && fake.confirmRequests.get(0).operationPublicId().equals(operationId),
                        "resumed confirm did not target the correct operation id");
                check(!hasAnyReceiptFor(player.serverLevel(), operationId), "the receipt must be resolved after the resumed confirm succeeds");

                ItemStack stillLive = findStack(player, ItemRegistry.GOLD_COIN.get());
                check(stillLive != null && stillLive.getCount() == 33,
                        "resuming confirm must never re-attempt insertion -- the coin count must be unchanged");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    /**
     * The discriminator branch this slice added to {@code BankTransferReconciliationService}: a
     * WITHDRAWAL receipt with {@code currencyAmount} present resumes through the currency confirm
     * core, never the item one. The item withdrawal client is deliberately left un-substituted
     * with an exploding default -- if routing regressed to the item branch, its confirm would
     * throw and fail this test loudly rather than passing by coincidence.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void startupReconciliationRoutesACurrencyWithdrawalReceiptToTheCurrencyResumeAndResolvesIt(GameTestHelper helper) {
        installBankRegistry();
        ServerPlayer player = setUpPlayer(helper, spawnBankTeller(helper));
        ServerLevel level = player.serverLevel();
        MinecraftServer server = level.getServer();

        UUID operationId = UUID.randomUUID();
        com.seggellion.britannia_mod.bank.transfer.BankTransferReceipts.record(
                level, operationId, player.getUUID(),
                com.seggellion.britannia_mod.bank.transfer.BankTransferOperationType.WITHDRAWAL,
                null, 60L, null, System.currentTimeMillis()
        );
        BankTransferReceipt receipt = findReceipt(level, operationId);

        FakeClient fake = new FakeClient();
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingCurrencyWithdrawalProxyService.useClientForTesting(fake);

        try {
            com.seggellion.britannia_mod.service.banking.BankTransferReconciliationService.reconcile(
                    server, new BankTransferReceiptStore.ScanResult(List.of(receipt), List.of(), List.of())
            );

            helper.succeedWhen(() -> {
                check(fake.confirmRequests.size() == 1 && fake.confirmRequests.get(0).operationPublicId().equals(operationId),
                        "the currency resume did not confirm the correct operation: " + fake.confirmRequests);
                check(!hasAnyReceiptFor(level, operationId),
                        "the currency withdrawal receipt must be resolved after the resumed confirm succeeds");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Reconciliation required: coins never reclaimed, receipt escalated ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void reconciliationRequiredNeverReclaimsTheCoinsAndMarksTheReceiptReconciliationRequired(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        UUID operationId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingCurrencyWithdrawalPrepareResult.Success(operationId));
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.ReconciliationRequired());
        BankingCurrencyWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyWithdrawalResult> future =
                    BankingCurrencyWithdrawalProxyService.triggerCurrencyWithdrawalForTesting(player, teller, "copper", 20);

            helper.succeedWhen(() -> {
                check(future.isDone(), "currency withdrawal did not complete");
                BankingCurrencyWithdrawalResult result = future.join();
                check(result instanceof BankingCurrencyWithdrawalResult.ReconciliationRequired,
                        "expected the distinct ReconciliationRequired case, got " + result);

                ItemStack live = findStack(player, ItemRegistry.COPPER_COIN.get());
                check(live != null && live.getCount() == 20,
                        "the coins must never be reclaimed on RECONCILIATION_REQUIRED -- they are already, physically, the player's");

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

    // ---------- Forced-save failure: escalates to reconciliation_required, via the real seam ----------

    /**
     * Withdrawal's own forced-save-failure policy, per {@link BankTransferPlayerDurability}'s own
     * docs: unlike deposit's abort-and-restore, withdrawal cannot cleanly abort once the receipt
     * exists (it was written BEFORE insertion, per Section A.6) -- so a detected forceSave
     * failure here must escalate to {@code reconciliation_required} rather than let a clean Rails
     * confirm silently resolve away the one durable trace that doubt existed, exactly mirroring
     * item withdrawal's own identical policy. Proven with the same seam-based technique {@code
     * BankTransferPlayerDurabilityGameTests} established ({@code useSaveDelegateForTesting}), not
     * a real-game-state reproduction.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aForcedSaveFailureDuringCurrencyWithdrawalEscalatesToReconciliationRequired(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        BankTransferPlayerDurability.useSaveDelegateForTesting(p -> { throw new RuntimeException("simulated forced-save failure"); });

        UUID operationId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingCurrencyWithdrawalPrepareResult.Success(operationId));
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingCurrencyWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyWithdrawalResult> future =
                    BankingCurrencyWithdrawalProxyService.triggerCurrencyWithdrawalForTesting(player, teller, "silver", 18);

            helper.succeedWhen(() -> {
                check(future.isDone(), "the operation must complete even when the forced save throws internally");
                BankingCurrencyWithdrawalResult result = future.join();
                check(result instanceof BankingCurrencyWithdrawalResult.ReconciliationRequired,
                        "a detected forced-save failure must not let a clean Rails confirm silently resolve the receipt, got " + result);

                ItemStack live = findStack(player, ItemRegistry.SILVER_COIN.get());
                check(live != null && live.getCount() == 18,
                        "the coins must remain with the player -- withdrawal cannot cleanly abort once inserted");

                BankTransferReceiptStore.ScanResult scan = readFreshStore(player.serverLevel()).scanUnresolved();
                check(scan.reconciliationRequired().stream().anyMatch(r -> r.operationId().equals(operationId)),
                        "the receipt must be escalated to reconciliation_required, not silently resolved");
                check(scan.pending().stream().noneMatch(r -> r.operationId().equals(operationId)),
                        "the receipt must not remain in plain pending state either");

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

    private static ItemStack findStack(ServerPlayer player, net.minecraft.world.item.Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == item) return stack;
        }
        return null;
    }

    /**
     * Loads {@code original}'s just-saved on-disk data into a brand-new, otherwise-untouched
     * {@code ServerPlayer} via the real {@code PlayerDataStorage#load} -- never inspects the
     * live, in-memory {@code original} itself. Identical technique and identical hijack-guard to
     * {@code BankTransferPlayerDurabilityGameTests#loadFreshFromDisk}, duplicated here rather
     * than imported per this codebase's established self-contained-GameTest-file convention.
     */
    private static ServerPlayer loadFreshFromDisk(ServerPlayer original) {
        MinecraftServer server = original.server;
        com.mojang.authlib.GameProfile profile =
                new com.mojang.authlib.GameProfile(original.getUUID(), original.getGameProfile().getName());
        ServerPlayer fresh = new ServerPlayer(server, original.serverLevel(), profile, ClientInformation.createDefault());
        ((PlayerListAccessorMixin) server.getPlayerList()).britannia$playerIo().load(fresh);
        // See BankTransferPlayerDurabilityGameTests' own docs: constructing `fresh` re-points
        // PlayerList's UUID-keyed PlayerAdvancements cache at this disconnected instance; this
        // restores the binding to `original` immediately.
        server.getPlayerList().getPlayerAdvancements(original);
        return fresh;
    }

    private static void cleanUp() {
        BankingCurrencyWithdrawalProxyService.resetClientForTesting();
        BankingCurrencyWithdrawalProxyService.resetInFlightTrackingForTesting();
        ServiceNpcRegistryCache.clear();
    }

    private static BankTransferReceiptStore readFreshStore(ServerLevel level) {
        File dataFile = level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve(BankTransferReceiptStore.DATA_NAME + ".dat")
                .toFile();
        if (!dataFile.isFile()) return null;

        CompoundTag outer;
        try (InputStream input = new FileInputStream(dataFile)) {
            outer = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
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

    /** Per-method fake, mirroring {@code BankingCurrencyDepositProxyServiceGameTests}' own FakeClient shape. */
    private static final class FakeClient implements BankingCurrencyWithdrawalClientPort {
        java.util.function.Supplier<CompletableFuture<BankingCurrencyWithdrawalPrepareResult>> prepareBehavior =
                () -> { throw new IllegalStateException("prepareCurrencyWithdrawal() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingConfirmResult>> confirmBehavior =
                () -> { throw new IllegalStateException("confirm() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingCancelResult>> cancelBehavior =
                () -> { throw new IllegalStateException("cancel() was not expected to be called in this test"); };

        final List<BankingCurrencyWithdrawalPrepareRequest> prepareRequests = new CopyOnWriteArrayList<>();
        final List<BankingOperationRequest> confirmRequests = new CopyOnWriteArrayList<>();
        final List<BankingOperationRequest> cancelRequests = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<BankingCurrencyWithdrawalPrepareResult> prepareCurrencyWithdrawal(
                MinecraftServer server, BankingCurrencyWithdrawalPrepareRequest request
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
