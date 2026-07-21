package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.item.BankItemFingerprint;
import com.seggellion.britannia_mod.bank.item.BankItemWeight;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStore;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.quest.QuestRewardService;
import com.seggellion.britannia_mod.quest.network.QuestModels;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.server.auth.ServerCredentials;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import com.seggellion.britannia_mod.service.banking.BankingCancelResult;
import com.seggellion.britannia_mod.service.banking.BankingConfirmResult;
import com.seggellion.britannia_mod.service.banking.BankingDepositClientPort;
import com.seggellion.britannia_mod.service.banking.BankingDepositLocalRejectionReason;
import com.seggellion.britannia_mod.service.banking.BankingDepositPrepareRequest;
import com.seggellion.britannia_mod.service.banking.BankingDepositPrepareResult;
import com.seggellion.britannia_mod.service.banking.BankingDepositProxyService;
import com.seggellion.britannia_mod.service.banking.BankingDepositResult;
import com.seggellion.britannia_mod.service.banking.BankingDepositStage;
import com.seggellion.britannia_mod.service.banking.BankingOperationRequest;
import com.seggellion.britannia_mod.service.banking.BankingTransferOutcome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Milestone 9 NeoForge Slice 1: the deposit path, exercised entirely through {@link
 * BankingDepositProxyService}'s test-support entry points -- no live screen/packet trigger
 * exists yet (Slice 3's job). Nothing here talks to a real Rails server: every test substitutes
 * a {@link FakeClient} via {@link BankingDepositProxyService#useClientForTesting}, mirroring
 * {@link BankingProxyServiceGameTests}' own established approach for bank.open.
 *
 * <p>Two things this file cannot prove, and does not claim to: (1) "the item is present in the
 * account's bank_items on next bank.open" and "zero ReconcileWeight drift" are Rails-side
 * facts -- this file proves the NeoForge side of that chain (the exact fingerprint/weight sent
 * to prepare matches what the original stack independently produces), and the Rails side is
 * separately proven in Milestone 9 Rails Slice 1's own HTTP-driven audit test
 * (docs/banking_item_transfer.md). (2) UNSUPPORTED_ORIGIN cannot be exercised end-to-end here
 * for the same structural reason {@link BankItemEligibilityGameTests} already accepts: no
 * foreign-mod item can be constructed in this test environment at all (no other mod is loaded,
 * and the item registry is frozen by the time any test runs).
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankingDepositProxyServiceGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BANK_TYPE_KEY = "bank_teller";
    private static final int SLOT = 0;
    private static final int OTHER_SLOT = 1;

    private BankingDepositProxyServiceGameTests() {
    }

    // ---------- Happy path ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void happyPathEndsConfirmedWithReceiptResolvedAndExactFingerprintSent(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        ItemStack original = new ItemStack(Items.DIAMOND, 5);
        player.getInventory().setItem(SLOT, original.copy());
        HolderLookup.Provider registries = player.registryAccess();
        String expectedFingerprint = BankItemFingerprint.fingerprint(original, registries);
        double expectedWeight = BankItemWeight.resolve(original);

        UUID operationId = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingDepositPrepareResult.Success(operationId, bankItemId));
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingDepositProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingDepositResult> future =
                    BankingDepositProxyService.triggerDepositForTesting(player, teller, SLOT);

            helper.succeedWhen(() -> {
                check(future.isDone(), "deposit did not complete");
                BankingDepositResult result = future.join();
                check(result instanceof BankingDepositResult.Confirmed,
                        "expected Confirmed, got " + result);
                BankingDepositResult.Confirmed confirmed = (BankingDepositResult.Confirmed) result;
                check(confirmed.operationPublicId().equals(operationId), "wrong operation id in result");
                check(confirmed.bankItemPublicId().equals(bankItemId), "wrong bank item id in result");

                check(player.getInventory().getItem(SLOT).isEmpty(), "the deposited item was not removed from the slot");

                check(fake.prepareRequests.size() == 1, "expected exactly one prepare request");
                BankingDepositPrepareRequest sent = fake.prepareRequests.get(0);
                check(sent.fingerprint().equals(expectedFingerprint),
                        "fingerprint sent to Rails did not match the original stack's own fingerprint");
                check(sent.weight() == expectedWeight, "weight sent to Rails did not match BankItemWeight.resolve()");
                check(sent.playerUuid().equals(player.getUUID()), "wrong player uuid sent");

                check(fake.confirmRequests.size() == 1, "expected exactly one confirm request");
                check(fake.confirmRequests.get(0).operationPublicId().equals(operationId), "wrong operation id sent to confirm");

                check(!hasAnyReceiptFor(player.serverLevel(), operationId),
                        "the receipt was not resolved after a clean confirm");

                check(!BankingDepositProxyService.isInFlightForTesting(player.getUUID(), SLOT),
                        "IN_FLIGHT was not cleared after the sequence completed");

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

        assertLocalRejection(helper, player, teller, BankingDepositLocalRejectionReason.EMPTY_SLOT);
    }

    @GameTest(template = TEMPLATE)
    public static void currencyIsRejectedLocallyWithNoRailsCalls(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(ItemRegistry.GOLD_COIN.get(), 3));

        assertLocalRejection(helper, player, teller, BankingDepositLocalRejectionReason.CURRENCY);
    }

    @GameTest(template = TEMPLATE)
    public static void questBoundItemIsRejectedLocallyWithNoRailsCalls(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().clearContent();
        grantRealQuestReward(player);
        // grantRealQuestReward's item lands wherever Inventory#add placed it; relocate to SLOT.
        moveOnlyItemToSlot(player, SLOT);

        assertLocalRejection(helper, player, teller, BankingDepositLocalRejectionReason.QUEST_BOUND);
    }

    @GameTest(template = TEMPLATE)
    public static void oversizedPayloadIsRejectedLocallyWithNoRailsCalls(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, oversizedStack());

        assertLocalRejection(helper, player, teller, BankingDepositLocalRejectionReason.PAYLOAD_TOO_LARGE);
    }

    @GameTest(template = TEMPLATE)
    public static void excessiveNestingIsRejectedLocallyWithNoRailsCalls(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, nestedShulkerBox(10, new ItemStack(Items.DIAMOND, 1)));

        assertLocalRejection(helper, player, teller, BankingDepositLocalRejectionReason.NESTING_TOO_DEEP);
    }

    private static void assertLocalRejection(
            GameTestHelper helper, ServerPlayer player, ServiceNpcEntity teller, BankingDepositLocalRejectionReason expected
    ) {
        FakeClient fake = new FakeClient();
        BankingDepositProxyService.useClientForTesting(fake);
        try {
            CompletableFuture<BankingDepositResult> future =
                    BankingDepositProxyService.triggerDepositForTesting(player, teller, SLOT);

            check(future.isDone(), "local rejection must complete synchronously");
            BankingDepositResult result = future.join();
            check(result instanceof BankingDepositResult.RejectedLocally,
                    "expected RejectedLocally, got " + result);
            check(((BankingDepositResult.RejectedLocally) result).reason() == expected,
                    "wrong local rejection reason: " + result);
            check(fake.prepareRequests.isEmpty() && fake.confirmRequests.isEmpty() && fake.cancelRequests.isEmpty(),
                    "a local rejection dispatched a Rails call");
            check(!BankingDepositProxyService.isInFlightForTesting(player.getUUID(), SLOT),
                    "IN_FLIGHT was not cleared after a local rejection");
            cleanUp();
            helper.succeed();
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Removal failure: slot changes between prepare-success and revalidation ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void slotContentsChangingBeforeRevalidationCancelsAndReportsRemovalFailed(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND, 5));

        UUID operationId = UUID.randomUUID();
        CompletableFuture<BankingDepositPrepareResult> pending = new CompletableFuture<>();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> pending;
        fake.cancelBehavior = () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        BankingDepositProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingDepositResult> future =
                    BankingDepositProxyService.triggerDepositForTesting(player, teller, SLOT);

            // The slot changes while prepare is still in flight -- the exact identity check
            // ServerEconomyService.reserveItems is documented to lack.
            player.getInventory().setItem(SLOT, new ItemStack(Items.EMERALD, 5));
            pending.complete(new BankingDepositPrepareResult.Success(operationId, UUID.randomUUID()));

            helper.succeedWhen(() -> {
                check(future.isDone(), "deposit did not complete");
                BankingDepositResult result = future.join();
                check(result instanceof BankingDepositResult.RemovalFailed,
                        "expected RemovalFailed, got " + result);
                check(((BankingDepositResult.RemovalFailed) result).operationPublicId().equals(operationId),
                        "wrong operation id in RemovalFailed result");

                check(fake.cancelRequests.size() == 1, "cancel was not called after a removal-revalidation mismatch");
                check(fake.cancelRequests.get(0).operationPublicId().equals(operationId), "wrong operation id sent to cancel");
                check(fake.confirmRequests.isEmpty(), "confirm must never be called after a removal-revalidation mismatch");

                check(player.getInventory().getItem(SLOT).getItem() == Items.EMERALD,
                        "the swapped-in item was incorrectly touched/removed");
                check(player.getInventory().getItem(SLOT).getCount() == 5, "no item was lost");

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

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aReceiptSurvivesASimulatedCrashBetweenRemovalAndConfirmation(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        ItemStack original = new ItemStack(Items.DIAMOND, 5);
        player.getInventory().setItem(SLOT, original.copy());

        UUID operationId = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingDepositPrepareResult.Success(operationId, bankItemId));
        // confirmBehavior deliberately left throwing -- confirm must never be reached by this path.
        BankingDepositProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingDepositProxyService.PrepareAndRemoveOutcome> future =
                    BankingDepositProxyService.prepareAndRemoveForTesting(player, teller, SLOT);

            helper.succeedWhen(() -> {
                check(future.isDone(), "prepareAndRemove did not complete");
                BankingDepositProxyService.PrepareAndRemoveOutcome outcome = future.join();
                check(outcome instanceof BankingDepositProxyService.PrepareAndRemoveOutcome.Removed,
                        "expected Removed, got " + outcome);

                // "crash" simulated here: confirm is never called.
                check(fake.confirmRequests.isEmpty(), "confirm must not have been called before the simulated crash");

                check(player.getInventory().getItem(SLOT).isEmpty(),
                        "the item must genuinely be gone from the player's inventory after removal");

                com.seggellion.britannia_mod.bank.transfer.BankTransferReceipt survived =
                        findReceipt(player.serverLevel(), operationId);
                check(java.util.Arrays.equals(survived.itemPayload(), fake.lastCapturedPayload()),
                        "the on-disk receipt payload did not match what was actually removed");
                check(survived.status() == com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStatus.PENDING_LOCAL_ACTION,
                        "confirm was never called, so the receipt must still be PENDING_LOCAL_ACTION, not escalated");

                // A real crash would not clean up IN_FLIGHT either -- prove that's still true.
                check(BankingDepositProxyService.isInFlightForTesting(player.getUUID(), SLOT),
                        "a simulated crash must leave the slot marked in-flight, matching real crash semantics");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    /**
     * The counterpart to {@link #aReceiptSurvivesASimulatedCrashBetweenRemovalAndConfirmation}:
     * proves the surviving receipt is actually resolvable by resuming confirm from exactly the
     * state a real restart would leave behind (a known operation/bank-item id pair, an
     * unresolved on-disk receipt, and no in-memory state), not just detectable by a scan. A
     * separate test rather than a continuation of the crash test itself -- chaining a second
     * {@code helper.succeedWhen} inside the first's callback does not match this codebase's own
     * established multi-stage pattern ({@code BankingProxyServiceGameTests}'
     * {@code dispatchNextNonOpenedOutcome} uses sequential {@code runAfterDelay} recursion
     * instead, precisely to avoid a{@code succeedWhen} completing the test before a nested one
     * has run).
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aSurvivedReceiptIsResolvableByResumingConfirmAfterTheSimulatedRestart(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        UUID operationId = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingDepositProxyService.useClientForTesting(fake);
        // No IN_FLIGHT entry is seeded -- a real restart's in-memory state is empty, exactly
        // like resetInFlightTrackingForTesting() would leave it.

        try {
            CompletableFuture<BankingDepositResult> resumed =
                    BankingDepositProxyService.confirmDepositForTesting(player, operationId, bankItemId);

            helper.succeedWhen(() -> {
                check(resumed.isDone(), "resumed confirm did not complete");
                check(resumed.join() instanceof BankingDepositResult.Confirmed,
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

    // ---------- Expired operation / late reconciliation ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void reconciliationRequiredNeverReturnsTheItemAndMarksTheReceiptReconciliationRequired(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND, 5));

        UUID operationId = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingDepositPrepareResult.Success(operationId, bankItemId));
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.ReconciliationRequired());
        BankingDepositProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingDepositResult> future =
                    BankingDepositProxyService.triggerDepositForTesting(player, teller, SLOT);

            helper.succeedWhen(() -> {
                check(future.isDone(), "deposit did not complete");
                BankingDepositResult result = future.join();
                check(result instanceof BankingDepositResult.ReconciliationRequired,
                        "expected the distinct ReconciliationRequired case, got " + result);
                check(((BankingDepositResult.ReconciliationRequired) result).operationPublicId().equals(operationId),
                        "wrong operation id in ReconciliationRequired result");

                check(player.getInventory().getItem(SLOT).isEmpty(),
                        "the item must never be returned to the player on RECONCILIATION_REQUIRED");

                // Not just "some unresolved receipt still exists" -- specifically marked
                // reconciliation_required, distinct from an ordinary pending retry candidate.
                com.seggellion.britannia_mod.bank.transfer.BankTransferReceipt receipt =
                        findReceipt(player.serverLevel(), operationId);
                check(receipt.status() == com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStatus.RECONCILIATION_REQUIRED,
                        "the receipt must be transitioned to RECONCILIATION_REQUIRED, not left as an ordinary pending entry: "
                                + receipt.status());

                BankTransferReceiptStore freshStore = readFreshStore(player.serverLevel());
                BankTransferReceiptStore.ScanResult scan = freshStore.scanUnresolved();
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

    // ---------- Duplicate prepare: IN_FLIGHT dedup for the same slot ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aRepeatTriggerForTheSameSlotWhileOneIsInFlightDoesNotDispatchASecondPrepare(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND, 5));

        AtomicInteger prepareDispatchCount = new AtomicInteger();
        CompletableFuture<BankingDepositPrepareResult> pending = new CompletableFuture<>();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> {
            prepareDispatchCount.incrementAndGet();
            return pending;
        };
        BankingDepositProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingDepositResult> first =
                    BankingDepositProxyService.triggerDepositForTesting(player, teller, SLOT);
            CompletableFuture<BankingDepositResult> second =
                    BankingDepositProxyService.triggerDepositForTesting(player, teller, SLOT);

            check(prepareDispatchCount.get() == 1,
                    "a repeat trigger for the same slot dispatched a second prepare call (count=" + prepareDispatchCount.get() + ")");
            check(second.isDone() && second.join() instanceof BankingDepositResult.LocalFailure,
                    "the duplicate trigger did not report a local failure immediately");

            pending.complete(new BankingDepositPrepareResult.Rejected(BankingTransferOutcome.CAPACITY_EXCEEDED, false));

            helper.succeedWhen(() -> {
                check(first.isDone(), "the original deposit did not complete");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Concurrent requests for different items: independently trackable ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void concurrentDepositsForDifferentSlotsDoNotCollide(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND, 1));
        player.getInventory().setItem(OTHER_SLOT, new ItemStack(Items.EMERALD, 2));

        UUID firstOperation = UUID.randomUUID();
        UUID secondOperation = UUID.randomUUID();
        CopyOnWriteArrayList<BankingDepositPrepareRequest> seen = new CopyOnWriteArrayList<>();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> {
            BankingDepositPrepareRequest request = fake.prepareRequests.get(fake.prepareRequests.size() - 1);
            seen.add(request);
            UUID id = request.fingerprint().equals(BankItemFingerprint.fingerprint(
                    new ItemStack(Items.DIAMOND, 1), player.registryAccess())) ? firstOperation : secondOperation;
            return CompletableFuture.completedFuture(new BankingDepositPrepareResult.Success(id, UUID.randomUUID()));
        };
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingDepositProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingDepositResult> first =
                    BankingDepositProxyService.triggerDepositForTesting(player, teller, SLOT);
            CompletableFuture<BankingDepositResult> second =
                    BankingDepositProxyService.triggerDepositForTesting(player, teller, OTHER_SLOT);

            check(!(first.isDone() && first.join() instanceof BankingDepositResult.LocalFailure),
                    "the first slot's deposit was incorrectly rejected as a duplicate");
            check(!(second.isDone() && second.join() instanceof BankingDepositResult.LocalFailure),
                    "the second, different slot's deposit was incorrectly rejected as a duplicate");

            helper.succeedWhen(() -> {
                check(first.isDone() && second.isDone(), "both concurrent deposits did not complete");
                check(first.join() instanceof BankingDepositResult.Confirmed, "first slot deposit did not confirm: " + first.join());
                check(second.join() instanceof BankingDepositResult.Confirmed, "second slot deposit did not confirm: " + second.join());
                BankingDepositResult.Confirmed firstResult = (BankingDepositResult.Confirmed) first.join();
                BankingDepositResult.Confirmed secondResult = (BankingDepositResult.Confirmed) second.join();
                check(!firstResult.operationPublicId().equals(secondResult.operationPublicId()),
                        "the two independent deposits were not independently trackable -- same operation id");
                check(seen.size() == 2, "expected exactly two independent prepare requests, got " + seen.size());

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Helpers ----------

    private static ServerPlayer setUpPlayer(GameTestHelper helper, ServiceNpcEntity teller) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.teleportTo(teller.getX() + 1.0, teller.getY(), teller.getZ());
        return player;
    }

    private static void cleanUp() {
        BankingDepositProxyService.resetClientForTesting();
        BankingDepositProxyService.resetInFlightTrackingForTesting();
        ServiceNpcRegistryCache.clear();
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

    private static ItemStack oversizedStack() {
        ItemStack stack = new ItemStack(Items.DIAMOND, 1);
        byte[] filler = new byte[400_000];
        new Random(42).nextBytes(filler); // random, not compressible away below the 256 KiB limit
        CompoundTag tag = new CompoundTag();
        tag.put("Filler", new ByteArrayTag(filler));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static ItemStack nestedShulkerBox(int depth, ItemStack innermost) {
        ItemStack current = innermost;
        for (int i = 0; i < depth; i++) {
            ItemStack box = new ItemStack(Items.SHULKER_BOX);
            box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(current)));
            current = box;
        }
        return current;
    }

    private static void grantRealQuestReward(ServerPlayer player) {
        QuestModels.ItemData reward = new QuestModels.ItemData();
        reward.id = "magic_ring"; // QuestRewardService's own special-cased id, guaranteed to resolve to a real item
        reward.count = 1;

        QuestModels.QuestResponse response = new QuestModels.QuestResponse();
        response.success = true;
        response.quest_id = 42L;
        response.questStateId = "gametest-quest-state";
        response.granted_items = List.of(reward);

        QuestRewardService.apply(player, response);
    }

    /**
     * Reads the receipt store directly off disk, bypassing the live in-memory instance --
     * identical technique to {@code BankTransferReceiptGameTests#readFreshFromDisk}, reused
     * here rather than re-derived (each GameTest file in this codebase is self-contained by
     * established convention, so this is duplicated, not imported).
     */
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

    /**
     * Scoped to one operation id, not the store's total contents -- via {@link
     * BankTransferReceiptStore#find}, which looks the receipt up directly regardless of which
     * status bucket it currently falls into (pending or reconciliation_required), rather than
     * {@code scanUnresolved()}'s two separate lists. {@link BankTransferReceiptStore} is a
     * single store shared by the entire server (its own docs: "single, server-wide store") --
     * the GameTest runner executes up to 50 tests concurrently per batch, and several other
     * tests in this codebase (e.g. {@code BankTransferReceiptGameTests}) write to this exact
     * same on-disk store at the same time. Asserting the store's total unresolved count, rather
     * than whether one specific operation's own receipt is present, is exactly the
     * unscoped-assertion-under-concurrency mistake this program has already been burned by
     * elsewhere -- confirmed directly here: an early version of this file asserted {@code
     * unresolved.size() == 1} and it failed nondeterministically from cross-test contamination,
     * not a real defect.
     */
    private static boolean hasAnyReceiptFor(ServerLevel level, UUID operationId) {
        BankTransferReceiptStore store = readFreshStore(level);
        return store != null && store.find(operationId) != null;
    }

    private static com.seggellion.britannia_mod.bank.transfer.BankTransferReceipt findReceipt(ServerLevel level, UUID operationId) {
        BankTransferReceiptStore store = readFreshStore(level);
        com.seggellion.britannia_mod.bank.transfer.BankTransferReceipt receipt = store == null ? null : store.find(operationId);
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

    /**
     * Records every request it receives and lets each of the three actions be configured
     * independently -- see {@link BankingDepositClientPort}'s own docs for why this needs a
     * per-method seam rather than {@link com.seggellion.britannia_mod.service.banking.BankingDepositClient}'s
     * shared single-{@code TransportSubmitter} one.
     */
    private static final class FakeClient implements BankingDepositClientPort {
        java.util.function.Supplier<CompletableFuture<BankingDepositPrepareResult>> prepareBehavior =
                () -> { throw new IllegalStateException("prepare() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingConfirmResult>> confirmBehavior =
                () -> { throw new IllegalStateException("confirm() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingCancelResult>> cancelBehavior =
                () -> { throw new IllegalStateException("cancel() was not expected to be called in this test"); };

        final List<BankingDepositPrepareRequest> prepareRequests = new CopyOnWriteArrayList<>();
        final List<BankingOperationRequest> confirmRequests = new CopyOnWriteArrayList<>();
        final List<BankingOperationRequest> cancelRequests = new CopyOnWriteArrayList<>();

        byte[] lastCapturedPayload() {
            return prepareRequests.get(prepareRequests.size() - 1).payload();
        }

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
            cancelRequests.add(request);
            return cancelBehavior.get();
        }
    }
}
