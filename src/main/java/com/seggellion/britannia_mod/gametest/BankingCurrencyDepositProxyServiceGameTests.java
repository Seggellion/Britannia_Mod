package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceipt;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStatus;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStore;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import com.seggellion.britannia_mod.service.banking.BankingCancelResult;
import com.seggellion.britannia_mod.service.banking.BankingConfirmResult;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyDepositClientPort;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyDepositLocalRejectionReason;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyDepositPrepareRequest;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyDepositPrepareResult;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyDepositProxyService;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyDepositResult;
import com.seggellion.britannia_mod.service.banking.BankingOperationRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
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
 * Milestone 10 NeoForge Slice 1: the currency deposit path, exercised through {@link
 * BankingCurrencyDepositProxyService}'s test-support entry points with a per-method {@link
 * FakeClient} substituted -- mirroring {@link BankingDepositProxyServiceGameTests}' established
 * structure test-for-test where the flows correspond, because currency must get the identical
 * crash-safety guarantees the item deposit already proves, not a weaker version. The routing
 * layer (a real {@code BankDepositRequestC2SPayload} reaching this flow vs. the item flow) is
 * separately proven in {@link BankingTransferPacketServiceGameTests}.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankingCurrencyDepositProxyServiceGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BANK_TYPE_KEY = "bank_teller";
    private static final int SLOT = 0;
    private static final int OTHER_SLOT = 1;

    private BankingCurrencyDepositProxyServiceGameTests() {
    }

    // ---------- Happy path: a PARTIAL stack's exact live count, per the playbook ----------

    /**
     * Deliberately a partial stack (37 of a 99-max stack): the playbook's "partial stack
     * deposit" requirement means the exact live count flows through -- captured, sent to
     * prepare, and removed -- never a hardcoded or assumed-full quantity.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void happyPathDepositsExactPartialStackCountAndResolvesReceipt(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(ItemRegistry.GOLD_COIN.get(), 37));

        UUID operationId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingCurrencyDepositPrepareResult.Success(operationId));
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingCurrencyDepositProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyDepositResult> future =
                    BankingCurrencyDepositProxyService.triggerCurrencyDepositForTesting(player, teller, SLOT);

            helper.succeedWhen(() -> {
                check(future.isDone(), "currency deposit did not complete");
                BankingCurrencyDepositResult result = future.join();
                check(result instanceof BankingCurrencyDepositResult.Confirmed, "expected Confirmed, got " + result);
                check(((BankingCurrencyDepositResult.Confirmed) result).operationPublicId().equals(operationId),
                        "wrong operation id in result");

                check(player.getInventory().getItem(SLOT).isEmpty(), "the deposited coins were not removed from the slot");

                check(fake.prepareRequests.size() == 1, "expected exactly one prepare request");
                BankingCurrencyDepositPrepareRequest sent = fake.prepareRequests.get(0);
                check(sent.currencyKey().equals("gold"), "wrong currency key sent: " + sent.currencyKey());
                check(sent.amount() == 37, "the exact partial-stack count must be sent, got " + sent.amount());
                check(sent.playerUuid().equals(player.getUUID()), "wrong player uuid sent");

                check(fake.confirmRequests.size() == 1, "expected exactly one confirm request");
                check(fake.confirmRequests.get(0).operationPublicId().equals(operationId), "wrong operation id sent to confirm");

                check(!hasAnyReceiptFor(player.serverLevel(), operationId),
                        "the receipt was not resolved after a clean confirm");
                check(!BankingCurrencyDepositProxyService.isInFlightForTesting(player.getUUID(), SLOT),
                        "IN_FLIGHT was not cleared after the sequence completed");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Each remaining denomination maps to its own wire key, independently ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void silverAndCopperDenominationsMapToTheirOwnWireKeysIndependently(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(ItemRegistry.SILVER_COIN.get(), 99));
        player.getInventory().setItem(OTHER_SLOT, new ItemStack(ItemRegistry.COPPER_COIN.get(), 12));

        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(
                new BankingCurrencyDepositPrepareResult.Success(UUID.randomUUID()));
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingCurrencyDepositProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyDepositResult> silver =
                    BankingCurrencyDepositProxyService.triggerCurrencyDepositForTesting(player, teller, SLOT);
            CompletableFuture<BankingCurrencyDepositResult> copper =
                    BankingCurrencyDepositProxyService.triggerCurrencyDepositForTesting(player, teller, OTHER_SLOT);

            check(!(silver.isDone() && silver.join() instanceof BankingCurrencyDepositResult.LocalFailure),
                    "the first slot's deposit was incorrectly rejected as a duplicate");
            check(!(copper.isDone() && copper.join() instanceof BankingCurrencyDepositResult.LocalFailure),
                    "the second, different slot's deposit was incorrectly rejected as a duplicate");

            helper.succeedWhen(() -> {
                check(silver.isDone() && copper.isDone(), "both deposits did not complete");
                check(silver.join() instanceof BankingCurrencyDepositResult.Confirmed, "silver did not confirm: " + silver.join());
                check(copper.join() instanceof BankingCurrencyDepositResult.Confirmed, "copper did not confirm: " + copper.join());

                check(fake.prepareRequests.size() == 2, "expected exactly two prepare requests");
                check(fake.prepareRequests.stream().anyMatch(r -> r.currencyKey().equals("silver") && r.amount() == 99),
                        "no prepare carried (silver, 99): " + fake.prepareRequests);
                check(fake.prepareRequests.stream().anyMatch(r -> r.currencyKey().equals("copper") && r.amount() == 12),
                        "no prepare carried (copper, 12): " + fake.prepareRequests);

                check(player.getInventory().getItem(SLOT).isEmpty(), "the silver stack was not removed");
                check(player.getInventory().getItem(OTHER_SLOT).isEmpty(), "the copper stack was not removed");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Local rejection: zero Rails calls made ----------

    @GameTest(template = TEMPLATE)
    public static void emptySlotIsRejectedLocallyWithNoRailsCalls(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        // Slot left empty.

        assertLocalRejection(helper, player, teller, BankingCurrencyDepositLocalRejectionReason.EMPTY_SLOT);
    }

    /**
     * A direct call with a non-coin item must self-reject -- the currency flow can never be
     * misrouted into removing an ordinary item, even if some future caller bypassed the packet
     * router's own coin check.
     */
    @GameTest(template = TEMPLATE)
    public static void nonCurrencyItemIsRejectedLocallyWithNoRailsCalls(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND, 5));

        assertLocalRejection(helper, player, teller, BankingCurrencyDepositLocalRejectionReason.NOT_CURRENCY);
    }

    private static void assertLocalRejection(
            GameTestHelper helper, ServerPlayer player, ServiceNpcEntity teller, BankingCurrencyDepositLocalRejectionReason expected
    ) {
        FakeClient fake = new FakeClient();
        BankingCurrencyDepositProxyService.useClientForTesting(fake);
        try {
            CompletableFuture<BankingCurrencyDepositResult> future =
                    BankingCurrencyDepositProxyService.triggerCurrencyDepositForTesting(player, teller, SLOT);

            check(future.isDone(), "local rejection must complete synchronously");
            BankingCurrencyDepositResult result = future.join();
            check(result instanceof BankingCurrencyDepositResult.RejectedLocally, "expected RejectedLocally, got " + result);
            check(((BankingCurrencyDepositResult.RejectedLocally) result).reason() == expected,
                    "wrong local rejection reason: " + result);
            check(fake.prepareRequests.isEmpty() && fake.confirmRequests.isEmpty() && fake.cancelRequests.isEmpty(),
                    "a local rejection dispatched a Rails call");
            check(!BankingCurrencyDepositProxyService.isInFlightForTesting(player.getUUID(), SLOT),
                    "IN_FLIGHT was not cleared after a local rejection");
            cleanUp();
            helper.succeed();
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Removal failure: exact count revalidation ----------

    /**
     * The playbook's "revalidate the exact slot/item/count after prepare", proven on the count
     * axis specifically: same coin item, different count by removal time -- the mismatch must
     * cancel and remove nothing, exactly like the item flow's fingerprint mismatch.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void slotCountChangingBeforeRevalidationCancelsAndReportsRemovalFailed(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(ItemRegistry.GOLD_COIN.get(), 30));

        UUID operationId = UUID.randomUUID();
        CompletableFuture<BankingCurrencyDepositPrepareResult> pending = new CompletableFuture<>();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> pending;
        fake.cancelBehavior = () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        BankingCurrencyDepositProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyDepositResult> future =
                    BankingCurrencyDepositProxyService.triggerCurrencyDepositForTesting(player, teller, SLOT);

            // The count changes while prepare is still in flight (20 != the captured 30).
            player.getInventory().setItem(SLOT, new ItemStack(ItemRegistry.GOLD_COIN.get(), 20));
            pending.complete(new BankingCurrencyDepositPrepareResult.Success(operationId));

            helper.succeedWhen(() -> {
                check(future.isDone(), "currency deposit did not complete");
                BankingCurrencyDepositResult result = future.join();
                check(result instanceof BankingCurrencyDepositResult.RemovalFailed, "expected RemovalFailed, got " + result);
                check(((BankingCurrencyDepositResult.RemovalFailed) result).operationPublicId().equals(operationId),
                        "wrong operation id in RemovalFailed result");

                check(fake.cancelRequests.size() == 1, "cancel was not called after a count-revalidation mismatch");
                check(fake.cancelRequests.get(0).operationPublicId().equals(operationId), "wrong operation id sent to cancel");
                check(fake.confirmRequests.isEmpty(), "confirm must never be called after a revalidation mismatch");

                check(player.getInventory().getItem(SLOT).getCount() == 20,
                        "no coins may be removed on the removal-failed path");
                check(!hasAnyReceiptFor(player.serverLevel(), operationId),
                        "a receipt must never be written on the removal-failed path");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // Milestone 14 priority 2 (context enforcement, dimension 5): mirrors
    // slotCountChangingBeforeRevalidationCancelsAndReportsRemovalFailed exactly, above, just
    // with the player walking out of range during the prepare round trip instead of the coin
    // count changing -- both go through the identical cancel-and-report path.
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void playerWalkingOutOfRangeBeforeRevalidationCancelsAndReportsRemovalFailed(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(ItemRegistry.GOLD_COIN.get(), 30));

        UUID operationId = UUID.randomUUID();
        CompletableFuture<BankingCurrencyDepositPrepareResult> pending = new CompletableFuture<>();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> pending;
        fake.cancelBehavior = () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        BankingCurrencyDepositProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyDepositResult> future =
                    BankingCurrencyDepositProxyService.triggerCurrencyDepositForTesting(player, teller, SLOT);

            // The player walks far out of interaction range while prepare is still in flight.
            player.teleportTo(teller.getX() + 100.0, teller.getY(), teller.getZ());
            pending.complete(new BankingCurrencyDepositPrepareResult.Success(operationId));

            helper.succeedWhen(() -> {
                check(future.isDone(), "currency deposit did not complete");
                BankingCurrencyDepositResult result = future.join();
                check(result instanceof BankingCurrencyDepositResult.RemovalFailed, "expected RemovalFailed, got " + result);
                check(((BankingCurrencyDepositResult.RemovalFailed) result).operationPublicId().equals(operationId),
                        "wrong operation id in RemovalFailed result");

                check(fake.cancelRequests.size() == 1, "cancel was not called after the teller went out of range");
                check(fake.cancelRequests.get(0).operationPublicId().equals(operationId), "wrong operation id sent to cancel");
                check(fake.confirmRequests.isEmpty(), "confirm must never be called after the teller went out of range");

                check(player.getInventory().getItem(SLOT).getCount() == 30,
                        "no coins may be removed once the teller is out of range");
                check(!hasAnyReceiptFor(player.serverLevel(), operationId),
                        "a receipt must never be written on the removal-failed path");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Simulated crash: after removal, before confirmation ----------

    /**
     * The identical crash-safety guarantee the item deposit proves, for currency: the durable
     * receipt -- with {@code currencyAmount} finally carrying a real value, and {@code
     * itemPayload}/{@code bankItemPublicId} both absent -- survives on disk, still {@code
     * PENDING_LOCAL_ACTION}, discoverable by {@code scanUnresolved()}, with {@code IN_FLIGHT}
     * still held exactly as a real crash would leave it.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aReceiptWithCurrencyAmountSurvivesASimulatedCrashBetweenRemovalAndConfirmation(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(ItemRegistry.GOLD_COIN.get(), 25));

        UUID operationId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingCurrencyDepositPrepareResult.Success(operationId));
        // confirmBehavior deliberately left throwing -- confirm must never be reached by this path.
        BankingCurrencyDepositProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyDepositProxyService.PrepareAndRemoveOutcome> future =
                    BankingCurrencyDepositProxyService.prepareAndRemoveForTesting(player, teller, SLOT);

            helper.succeedWhen(() -> {
                check(future.isDone(), "prepareAndRemove did not complete");
                BankingCurrencyDepositProxyService.PrepareAndRemoveOutcome outcome = future.join();
                check(outcome instanceof BankingCurrencyDepositProxyService.PrepareAndRemoveOutcome.Removed,
                        "expected Removed, got " + outcome);
                BankingCurrencyDepositProxyService.PrepareAndRemoveOutcome.Removed removed =
                        (BankingCurrencyDepositProxyService.PrepareAndRemoveOutcome.Removed) outcome;
                check(removed.currencyKey().equals("gold") && removed.amount() == 25,
                        "wrong captured key/amount in Removed outcome: " + removed);

                // "crash" simulated here: confirm is never called.
                check(fake.confirmRequests.isEmpty(), "confirm must not have been called before the simulated crash");
                check(player.getInventory().getItem(SLOT).isEmpty(),
                        "the coins must genuinely be gone from the player's inventory after removal");

                BankTransferReceipt survived = findReceipt(player.serverLevel(), operationId);
                check(survived.currencyAmount() != null && survived.currencyAmount() == 25L,
                        "the on-disk receipt's currencyAmount did not match what was actually removed: " + survived.currencyAmount());
                check(survived.itemPayload() == null || survived.itemPayload().length == 0,
                        "a currency receipt must carry no item payload");
                check(survived.bankItemPublicId() == null,
                        "a currency receipt must carry no bank item public id");
                check(survived.status() == BankTransferReceiptStatus.PENDING_LOCAL_ACTION,
                        "confirm was never called, so the receipt must still be PENDING_LOCAL_ACTION");

                BankTransferReceiptStore.ScanResult scan = readFreshStore(player.serverLevel()).scanUnresolved();
                check(scan.pending().stream().anyMatch(r -> r.operationId().equals(operationId)),
                        "the surviving currency receipt must appear in scanUnresolved()'s pending bucket");

                // A real crash would not clean up IN_FLIGHT either -- prove that's still true.
                check(BankingCurrencyDepositProxyService.isInFlightForTesting(player.getUUID(), SLOT),
                        "a simulated crash must leave the slot marked in-flight, matching real crash semantics");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    /** The crash test's counterpart: the survived receipt is resolvable by resuming confirm after the "restart". */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aSurvivedCurrencyReceiptIsResolvableByResumingConfirmAfterTheSimulatedRestart(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        UUID operationId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingCurrencyDepositProxyService.useClientForTesting(fake);
        // No IN_FLIGHT entry is seeded -- a real restart's in-memory state is empty.

        try {
            CompletableFuture<BankingCurrencyDepositResult> resumed =
                    BankingCurrencyDepositProxyService.confirmCurrencyDepositForTesting(player, operationId);

            helper.succeedWhen(() -> {
                check(resumed.isDone(), "resumed confirm did not complete");
                check(resumed.join() instanceof BankingCurrencyDepositResult.Confirmed,
                        "resumed confirm did not report Confirmed: " + resumed.join());
                check(fake.confirmRequests.size() == 1
                                && fake.confirmRequests.get(0).operationPublicId().equals(operationId),
                        "resumed confirm did not target the correct operation id");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Startup reconciliation routes a currency receipt to the currency resume ----------

    /**
     * The discriminator branch this slice added to {@link
     * com.seggellion.britannia_mod.service.banking.BankTransferReconciliationService}: a DEPOSIT
     * receipt with {@code currencyAmount} present resumes through the currency confirm core,
     * never the item one. The item deposit client is deliberately left un-substituted with an
     * exploding default -- if routing regressed to the item branch, its confirm would throw and
     * fail this test loudly rather than passing by coincidence.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void startupReconciliationRoutesACurrencyDepositReceiptToTheCurrencyResumeAndResolvesIt(GameTestHelper helper) {
        installBankRegistry();
        ServerPlayer player = setUpPlayer(helper, spawnBankTeller(helper));
        ServerLevel level = player.serverLevel();
        MinecraftServer server = level.getServer();

        UUID operationId = UUID.randomUUID();
        com.seggellion.britannia_mod.bank.transfer.BankTransferReceipts.record(
                level, operationId, player.getUUID(),
                com.seggellion.britannia_mod.bank.transfer.BankTransferOperationType.DEPOSIT,
                null, 40L, null, System.currentTimeMillis()
        );
        BankTransferReceipt receipt = findReceipt(level, operationId);

        FakeClient fake = new FakeClient();
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingCurrencyDepositProxyService.useClientForTesting(fake);

        try {
            com.seggellion.britannia_mod.service.banking.BankTransferReconciliationService.reconcile(
                    server, new BankTransferReceiptStore.ScanResult(List.of(receipt), List.of(), List.of())
            );

            helper.succeedWhen(() -> {
                check(fake.confirmRequests.size() == 1
                                && fake.confirmRequests.get(0).operationPublicId().equals(operationId),
                        "the currency resume did not confirm the correct operation: " + fake.confirmRequests);
                check(!hasAnyReceiptFor(level, operationId),
                        "the currency receipt must be resolved after the resumed confirm succeeds");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Reconciliation required: coins never returned, receipt escalated ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void reconciliationRequiredNeverReturnsTheCoinsAndMarksTheReceiptReconciliationRequired(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(ItemRegistry.COPPER_COIN.get(), 60));

        UUID operationId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingCurrencyDepositPrepareResult.Success(operationId));
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.ReconciliationRequired());
        BankingCurrencyDepositProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyDepositResult> future =
                    BankingCurrencyDepositProxyService.triggerCurrencyDepositForTesting(player, teller, SLOT);

            helper.succeedWhen(() -> {
                check(future.isDone(), "currency deposit did not complete");
                BankingCurrencyDepositResult result = future.join();
                check(result instanceof BankingCurrencyDepositResult.ReconciliationRequired,
                        "expected the distinct ReconciliationRequired case, got " + result);

                check(player.getInventory().getItem(SLOT).isEmpty(),
                        "the coins must never be returned to the player on RECONCILIATION_REQUIRED");

                BankTransferReceipt receipt = findReceipt(player.serverLevel(), operationId);
                check(receipt.status() == BankTransferReceiptStatus.RECONCILIATION_REQUIRED,
                        "the receipt must be transitioned to RECONCILIATION_REQUIRED, got " + receipt.status());

                BankTransferReceiptStore.ScanResult scan = readFreshStore(player.serverLevel()).scanUnresolved();
                check(scan.reconciliationRequired().stream().anyMatch(r -> r.operationId().equals(operationId)),
                        "the receipt must appear in scanUnresolved()'s reconciliationRequired bucket");
                check(scan.pending().stream().noneMatch(r -> r.operationId().equals(operationId)),
                        "a reconciliation_required receipt must not also appear in the ordinary pending bucket");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Crash-window verification: the removal->receipt gap leaves only pre-removal disk state ----------

    /**
     * Pins the crash-window trace's finding in executable form. A process kill between the
     * in-memory {@code live.shrink(count)} and the receipt's forced flush cannot produce a
     * durably-removed-but-unreceipted state, because the receipt flush is the FIRST durable
     * write in the entire removal sequence: the shrink mutates only the in-memory inventory,
     * player NBT is persisted by a separate main-thread mechanism (autosave/disconnect/
     * shutdown) that cannot interleave inside the single uninterrupted {@code server.execute}
     * task the shrink and the receipt write both run in, and the receipt store's own
     * fsync-then-atomic-rename (M8) means a mid-flush kill leaves no torn file. The on-disk
     * state such a crash actually leaves is therefore exactly what this test constructs: the
     * coins present (the rollback the stale player NBT restores) and no receipt for the
     * operation.
     *
     * <p>What must then be true of recovery -- and is asserted here: startup reconciliation
     * finds nothing for that operation (no receipt means no resume, no confirm dispatch, no
     * balance credit) and never touches the player's coins. The abandoned Rails-side
     * "prepared" operation is not this process's to clean up: Rails' own Expire sweep ages it
     * to "expired" (a pure state transition for a currency deposit -- nothing was ever
     * reserved), already proven in Milestone 10 Rails Slice 1's own expire tests. Nothing is
     * lost, and nothing needs local reconciliation.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aCrashBeforeTheReceiptFlushLeavesPreRemovalStateThatNeedsNoLocalReconciliation(GameTestHelper helper) {
        installBankRegistry();
        ServerPlayer player = setUpPlayer(helper, spawnBankTeller(helper));
        ServerLevel level = player.serverLevel();
        MinecraftServer server = level.getServer();
        player.getInventory().setItem(SLOT, new ItemStack(ItemRegistry.GOLD_COIN.get(), 25));

        UUID operationId = UUID.randomUUID();

        // Any Rails dispatch at all fails this test loudly -- recovery must take NO action.
        FakeClient fake = new FakeClient();
        BankingCurrencyDepositProxyService.useClientForTesting(fake);

        try {
            // The disk truly holds no receipt for this operation (fresh read, not the live
            // in-memory instance), exactly as the mid-window kill leaves it.
            check(!hasAnyReceiptFor(level, operationId),
                    "precondition: no receipt may exist for an operation whose flush never completed");

            // What a real startup's scanUnresolved() would surface FOR THIS OPERATION is
            // nothing at all -- proven against the real store, then reconciled with exactly
            // that (scoped to this operation id per this codebase's own shared-store
            // convention, so concurrent tests' receipts cannot contaminate the assertion).
            BankTransferReceiptStore.ScanResult fullScan =
                    com.seggellion.britannia_mod.bank.transfer.BankTransferReceipts.scanUnresolved(level);
            List<BankTransferReceipt> pendingForOperation = fullScan.pending().stream()
                    .filter(r -> r.operationId().equals(operationId)).toList();
            List<BankTransferReceipt> escalatedForOperation = fullScan.reconciliationRequired().stream()
                    .filter(r -> r.operationId().equals(operationId)).toList();
            check(pendingForOperation.isEmpty() && escalatedForOperation.isEmpty(),
                    "a never-flushed receipt must be invisible to scanUnresolved()");

            com.seggellion.britannia_mod.service.banking.BankTransferReconciliationService.reconcile(
                    server, new BankTransferReceiptStore.ScanResult(pendingForOperation, escalatedForOperation, List.of())
            );

            check(fake.confirmRequests.isEmpty() && fake.prepareRequests.isEmpty() && fake.cancelRequests.isEmpty(),
                    "recovery dispatched a Rails call for an operation that has no receipt: " + fake.confirmRequests);
            ItemStack coins = player.getInventory().getItem(SLOT);
            check(coins.getItem() == ItemRegistry.GOLD_COIN.get() && coins.getCount() == 25,
                    "recovery must never touch the rolled-back coins");

            cleanUp();
            helper.succeed();
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Duplicate trigger: IN_FLIGHT dedup for the same slot ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aRepeatTriggerForTheSameSlotWhileOneIsInFlightDoesNotDispatchASecondPrepare(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(ItemRegistry.GOLD_COIN.get(), 10));

        AtomicInteger prepareDispatchCount = new AtomicInteger();
        CompletableFuture<BankingCurrencyDepositPrepareResult> pending = new CompletableFuture<>();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> {
            prepareDispatchCount.incrementAndGet();
            return pending;
        };
        BankingCurrencyDepositProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyDepositResult> first =
                    BankingCurrencyDepositProxyService.triggerCurrencyDepositForTesting(player, teller, SLOT);
            CompletableFuture<BankingCurrencyDepositResult> second =
                    BankingCurrencyDepositProxyService.triggerCurrencyDepositForTesting(player, teller, SLOT);

            check(prepareDispatchCount.get() == 1,
                    "a repeat trigger for the same slot dispatched a second prepare call (count=" + prepareDispatchCount.get() + ")");
            check(second.isDone() && second.join() instanceof BankingCurrencyDepositResult.LocalFailure,
                    "the duplicate trigger did not report a local failure immediately");

            pending.complete(new BankingCurrencyDepositPrepareResult.Rejected(
                    com.seggellion.britannia_mod.service.banking.BankingTransferOutcome.INVALID_AMOUNT, false));

            helper.succeedWhen(() -> {
                check(first.isDone(), "the original deposit did not complete");
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

    private static void cleanUp() {
        BankingCurrencyDepositProxyService.resetClientForTesting();
        BankingCurrencyDepositProxyService.resetInFlightTrackingForTesting();
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

    /** Per-method fake, mirroring {@link BankingDepositProxyServiceGameTests}' own FakeClient shape. */
    private static final class FakeClient implements BankingCurrencyDepositClientPort {
        java.util.function.Supplier<CompletableFuture<BankingCurrencyDepositPrepareResult>> prepareBehavior =
                () -> { throw new IllegalStateException("prepareCurrencyDeposit() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingConfirmResult>> confirmBehavior =
                () -> { throw new IllegalStateException("confirm() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingCancelResult>> cancelBehavior =
                () -> { throw new IllegalStateException("cancel() was not expected to be called in this test"); };

        final List<BankingCurrencyDepositPrepareRequest> prepareRequests = new CopyOnWriteArrayList<>();
        final List<BankingOperationRequest> confirmRequests = new CopyOnWriteArrayList<>();
        final List<BankingOperationRequest> cancelRequests = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<BankingCurrencyDepositPrepareResult> prepareCurrencyDeposit(
                MinecraftServer server, BankingCurrencyDepositPrepareRequest request
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
