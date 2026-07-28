package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceipt;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStatus;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStore;
import com.seggellion.britannia_mod.component.BankChequeData;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import com.seggellion.britannia_mod.service.banking.BankTransferReconciliationService;
import com.seggellion.britannia_mod.service.banking.BankingChequeRedemptionClientPort;
import com.seggellion.britannia_mod.service.banking.BankingChequeRedemptionLocalRejectionReason;
import com.seggellion.britannia_mod.service.banking.BankingChequeRedemptionProxyService;
import com.seggellion.britannia_mod.service.banking.BankingChequeRedemptionRequest;
import com.seggellion.britannia_mod.service.banking.BankingChequeRedemptionResult;
import com.seggellion.britannia_mod.service.banking.BankingTransferOutcome;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Milestone 11 NeoForge Slice 2: the bank cheque redemption path, exercised through {@link
 * BankingChequeRedemptionProxyService}'s test-support entry points with a per-method {@link
 * FakeClient} substituted -- mirroring {@link BankingCurrencyDepositProxyServiceGameTests}'
 * established structure, the real structural precedent this flow's own ordering follows (see
 * {@link BankingChequeRedemptionProxyService}'s own class docs for why).
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankingChequeRedemptionProxyServiceGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BANK_TYPE_KEY = "bank_teller";
    private static final int SLOT = 0;
    private static final int OTHER_SLOT = 1;
    /** 500 gold in copper -- the approved minimum (ADR-018/019), used as a plausible real amount. */
    private static final long REAL_AMOUNT_COPPER = 500L * 10_000L;
    /** Deliberately wrong: a much smaller, obviously mismatched display figure the item's own tooltip would show. */
    private static final long MISMATCHED_DISPLAY_AMOUNT_COPPER = 1L * 10_000L;

    private BankingChequeRedemptionProxyServiceGameTests() {
    }

    // ---------- Happy path, plus the non-authoritative-display invariant ----------

    /**
     * The mandatory proof: a cheque whose displayed tooltip amount is deliberately wrong/
     * mismatched from anything real must still redeem correctly, because NeoForge never reads
     * that value for anything functional. Proven directly and structurally, not just by
     * assertion: {@link BankingChequeRedemptionRequest} has no amount field of any kind at the
     * type level, so the wire request this flow sends genuinely cannot carry the display amount
     * -- captured here and checked field-by-field. This is the actual, non-bypassable reason
     * "the display amount is read only for the confirmation prompt's UI text, never passed to or
     * trusted by anything that determines the credited value" holds, not merely a runtime
     * behavior that happens not to read it today.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void redemptionCreditsFromTheRealChequeIdentityNeverTheMismatchedDisplayAmount(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        UUID chequePublicId = UUID.randomUUID();
        // The stack's own display data claims a small, deliberately WRONG amount -- Rails' own
        // real backing value (REAL_AMOUNT_COPPER, never sent or known to this class at all) is
        // what the account would actually be credited, entirely server-side on Rails.
        player.getInventory().setItem(SLOT, buildChequeStack(chequePublicId, MISMATCHED_DISPLAY_AMOUNT_COPPER));

        FakeClient fake = new FakeClient();
        fake.redeemBehavior = () -> CompletableFuture.completedFuture(new BankingChequeRedemptionResult.Confirmed(chequePublicId));
        BankingChequeRedemptionProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingChequeRedemptionResult> future =
                    BankingChequeRedemptionProxyService.triggerChequeRedemptionForTesting(player, teller, SLOT);

            helper.succeedWhen(() -> {
                check(future.isDone(), "cheque redemption did not complete");
                BankingChequeRedemptionResult result = future.join();
                check(result instanceof BankingChequeRedemptionResult.Confirmed, "expected Confirmed, got " + result);
                check(((BankingChequeRedemptionResult.Confirmed) result).chequePublicId().equals(chequePublicId),
                        "wrong cheque id in result");

                check(player.getInventory().getItem(SLOT).isEmpty(), "the redeemed cheque was not removed from the slot");

                check(fake.redeemRequests.size() == 1, "expected exactly one redeem request");
                BankingChequeRedemptionRequest sent = fake.redeemRequests.get(0);
                // The structural proof: BankingChequeRedemptionRequest has no amount field at
                // all -- there is nothing here to assert "is not the display amount" against,
                // which IS the proof. Only identity fields exist, and they must be the REAL ones.
                check(sent.chequePublicId().equals(chequePublicId), "wrong cheque public id sent -- must be the real BankChequeData.chequeId(), not derived from displayAmount");
                check(sent.playerUuid().equals(player.getUUID()), "wrong player uuid sent");
                check(sent.worldNpcPublicId() != null, "wrong/missing world_npc_public_id sent");

                check(!hasAnyReceiptFor(player.serverLevel(), chequePublicId),
                        "the receipt was not resolved after a clean redemption");
                check(!BankingChequeRedemptionProxyService.isInFlightForTesting(player.getUUID(), SLOT),
                        "IN_FLIGHT was not cleared after the sequence completed");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Local rejections: zero Rails calls ----------

    @GameTest(template = TEMPLATE)
    public static void emptySlotIsRejectedLocallyWithNoRailsCalls(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        // Slot left empty.

        assertLocalRejection(helper, player, teller, BankingChequeRedemptionLocalRejectionReason.EMPTY_SLOT);
    }

    /**
     * A direct call against a non-cheque item must self-reject -- this flow can never be
     * misrouted into removing an ordinary item, even if some future caller bypassed the packet
     * router's own {@code ItemRegistry.BANK_CHEQUE} check.
     */
    @GameTest(template = TEMPLATE)
    public static void nonChequeItemIsRejectedLocallyWithNoRailsCalls(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND, 1));

        assertLocalRejection(helper, player, teller, BankingChequeRedemptionLocalRejectionReason.NOT_A_CHEQUE);
    }

    private static void assertLocalRejection(
            GameTestHelper helper, ServerPlayer player, ServiceNpcEntity teller, BankingChequeRedemptionLocalRejectionReason expected
    ) {
        FakeClient fake = new FakeClient();
        BankingChequeRedemptionProxyService.useClientForTesting(fake);
        try {
            CompletableFuture<BankingChequeRedemptionResult> future =
                    BankingChequeRedemptionProxyService.triggerChequeRedemptionForTesting(player, teller, SLOT);

            check(future.isDone(), "local rejection must complete synchronously");
            BankingChequeRedemptionResult result = future.join();
            check(result instanceof BankingChequeRedemptionResult.RejectedLocally, "expected RejectedLocally, got " + result);
            check(((BankingChequeRedemptionResult.RejectedLocally) result).reason() == expected,
                    "wrong local rejection reason: " + result);
            check(fake.redeemRequests.isEmpty(), "a local rejection dispatched a Rails call");
            check(!BankingChequeRedemptionProxyService.isInFlightForTesting(player.getUUID(), SLOT),
                    "IN_FLIGHT was not cleared after a local rejection");
            cleanUp();
            helper.succeed();
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- The four definitive cheque rejections: item stays removed, receipt resolved ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void chequeNotFoundRemovesTheItemAndResolvesTheReceipt(GameTestHelper helper) {
        assertDefinitiveRejectionRemovesItemAndResolvesReceipt(helper, BankingTransferOutcome.CHEQUE_NOT_FOUND);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void chequeAlreadyRedeemedRemovesTheItemAndResolvesTheReceipt(GameTestHelper helper) {
        assertDefinitiveRejectionRemovesItemAndResolvesReceipt(helper, BankingTransferOutcome.CHEQUE_ALREADY_REDEEMED);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void chequeCancelledRemovesTheItemAndResolvesTheReceipt(GameTestHelper helper) {
        assertDefinitiveRejectionRemovesItemAndResolvesReceipt(helper, BankingTransferOutcome.CHEQUE_CANCELLED);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void chequeVoidedRemovesTheItemAndResolvesTheReceipt(GameTestHelper helper) {
        assertDefinitiveRejectionRemovesItemAndResolvesReceipt(helper, BankingTransferOutcome.CHEQUE_VOIDED);
    }

    /**
     * The item-disposition decision proven directly: once ANY of the four definitive outcomes
     * comes back, the already-removed cheque item is never restored (it is proven worthless --
     * see {@link BankingChequeRedemptionProxyService}'s own docs), and the receipt is resolved
     * (removed from the store) since nothing further can or should ever happen for this cheque.
     */
    private static void assertDefinitiveRejectionRemovesItemAndResolvesReceipt(GameTestHelper helper, BankingTransferOutcome outcome) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        UUID chequePublicId = UUID.randomUUID();
        player.getInventory().setItem(SLOT, buildChequeStack(chequePublicId, REAL_AMOUNT_COPPER));

        FakeClient fake = new FakeClient();
        fake.redeemBehavior = () -> CompletableFuture.completedFuture(new BankingChequeRedemptionResult.Rejected(outcome, false));
        BankingChequeRedemptionProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingChequeRedemptionResult> future =
                    BankingChequeRedemptionProxyService.triggerChequeRedemptionForTesting(player, teller, SLOT);

            helper.succeedWhen(() -> {
                check(future.isDone(), "cheque redemption did not complete");
                BankingChequeRedemptionResult result = future.join();
                check(result instanceof BankingChequeRedemptionResult.Rejected, "expected Rejected, got " + result);
                check(((BankingChequeRedemptionResult.Rejected) result).outcome() == outcome, "wrong outcome in result: " + result);

                check(player.getInventory().getItem(SLOT).isEmpty(),
                        "the cheque item must remain removed -- it is proven worthless, never restored, for outcome " + outcome);
                check(!hasAnyReceiptFor(player.serverLevel(), chequePublicId),
                        "the receipt must be resolved (not left pending) for a definitive outcome " + outcome);

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- A non-definitive rejection: item stays removed, but the receipt is left pending ----------

    /**
     * The other half of the item-disposition/receipt-lifecycle split: a teller/account-context
     * rejection (here, {@code PLAYER_NOT_FOUND}) asserts nothing about the cheque's OWN
     * validity, so unlike the four cheque-specific outcomes above, the receipt must be left
     * exactly as {@code record()} wrote it -- still {@code PENDING_LOCAL_ACTION} -- so a later
     * resume can safely retry the identical, idempotent request.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aNonChequeSpecificRejectionRemovesTheItemButLeavesTheReceiptPendingForAResume(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        UUID chequePublicId = UUID.randomUUID();
        player.getInventory().setItem(SLOT, buildChequeStack(chequePublicId, REAL_AMOUNT_COPPER));

        FakeClient fake = new FakeClient();
        fake.redeemBehavior = () -> CompletableFuture.completedFuture(
                new BankingChequeRedemptionResult.Rejected(BankingTransferOutcome.PLAYER_NOT_FOUND, false));
        BankingChequeRedemptionProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingChequeRedemptionResult> future =
                    BankingChequeRedemptionProxyService.triggerChequeRedemptionForTesting(player, teller, SLOT);

            helper.succeedWhen(() -> {
                check(future.isDone(), "cheque redemption did not complete");
                BankingChequeRedemptionResult result = future.join();
                check(result instanceof BankingChequeRedemptionResult.Rejected, "expected Rejected, got " + result);

                check(player.getInventory().getItem(SLOT).isEmpty(),
                        "the cheque item must still be removed -- there is no post-dispatch restore path");

                BankTransferReceipt receipt = findReceipt(player.serverLevel(), chequePublicId);
                check(receipt.status() == BankTransferReceiptStatus.PENDING_LOCAL_ACTION,
                        "a non-cheque-specific rejection must leave the receipt pending for a resume, got " + receipt.status());

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    /**
     * The specific safety claim the item-disposition decision depends on, proven directly: once
     * a non-cheque-specific rejection has already removed the item and left the receipt pending
     * (the exact scenario the test above proves in isolation), a later resume -- driven through
     * {@link BankingChequeRedemptionProxyService#resumeRedeemCheque}, the same entry point
     * {@link BankTransferReconciliationService}'s own {@code CHEQUE_REDEMPTION} branch calls,
     * with no live player/inventory access at all -- must complete the redemption for real (the
     * fake client's own {@code Confirmed} response standing in for "Rails credited the account
     * and transitioned the BankCheque to redeemed", the same idiom {@code
     * aSurvivedCurrencyReceiptIsResolvableByResumingConfirmAfterTheSimulatedRestart} already
     * uses for an ordinary confirm), while never once attempting to restore, re-insert, or
     * re-locate any physical item anywhere in the player's inventory -- proven against the WHOLE
     * inventory, not just the original slot, so a bug that inserted into a different slot would
     * still be caught.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void aResumeAfterANonChequeSpecificRejectionCompletesRedemptionWithoutEverTouchingThePhysicalItemAgain(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        UUID chequePublicId = UUID.randomUUID();
        player.getInventory().setItem(SLOT, buildChequeStack(chequePublicId, REAL_AMOUNT_COPPER));

        AtomicInteger callCount = new AtomicInteger();
        FakeClient fake = new FakeClient();
        fake.redeemRequestHandler = request -> {
            if (callCount.incrementAndGet() == 1) {
                // The original attempt: a teller/account-context failure at dispatch, AFTER
                // removal has already happened -- unrelated to the cheque's own validity.
                return CompletableFuture.completedFuture(
                        new BankingChequeRedemptionResult.Rejected(BankingTransferOutcome.PLAYER_NOT_FOUND, false));
            }
            // The resumed attempt: Rails' own test double now reports the cheque genuinely
            // redeemed -- the account credited, the BankCheque transitioned, on the Rails side
            // of this test double.
            return CompletableFuture.completedFuture(new BankingChequeRedemptionResult.Confirmed(request.chequePublicId()));
        };
        BankingChequeRedemptionProxyService.useClientForTesting(fake);

        CompletableFuture<BankingChequeRedemptionResult> firstAttempt =
                BankingChequeRedemptionProxyService.triggerChequeRedemptionForTesting(player, teller, SLOT);
        // Set exactly once, from inside the poll below, the moment the first attempt's own
        // failure has been observed and asserted -- driving the two-stage sequence through a
        // single succeedWhen poll rather than nesting a second one (GameTestHelper#succeedWhen
        // succeeds the whole test the first time its callback returns without throwing, so a
        // naively nested second succeedWhen registered from inside the first would never
        // actually get ticked).
        AtomicReference<CompletableFuture<BankingChequeRedemptionResult>> resumed = new AtomicReference<>();

        try {
            helper.succeedWhen(() -> {
                check(firstAttempt.isDone(), "the first (rejected) redemption attempt did not complete");

                if (resumed.get() == null) {
                    BankingChequeRedemptionResult firstResult = firstAttempt.join();
                    check(firstResult instanceof BankingChequeRedemptionResult.Rejected,
                            "expected the first attempt to be Rejected, got " + firstResult);

                    // The starting condition this whole test depends on: the item is already
                    // gone, and the receipt is pending -- exactly the documented disposition.
                    check(isEmptyInventory(player), "no item may exist anywhere in the player's inventory after the first, rejected attempt");
                    BankTransferReceipt pendingReceipt = findReceipt(player.serverLevel(), chequePublicId);
                    check(pendingReceipt.status() == BankTransferReceiptStatus.PENDING_LOCAL_ACTION,
                            "the receipt must be left pending after a non-cheque-specific rejection");
                    check(pendingReceipt.worldNpcPublicId() != null, "the pending receipt must carry the teller identity needed to resume");

                    resumed.set(BankingChequeRedemptionProxyService.resumeRedeemCheque(
                            player.server, pendingReceipt.playerUuid(), pendingReceipt.operationId(), pendingReceipt.worldNpcPublicId()
                    ));
                }

                CompletableFuture<BankingChequeRedemptionResult> resumedFuture = resumed.get();
                check(resumedFuture.isDone(), "the resumed redemption did not complete");
                BankingChequeRedemptionResult result = resumedFuture.join();
                check(result instanceof BankingChequeRedemptionResult.Confirmed,
                        "the resume must complete the redemption for real, got " + result);
                check(((BankingChequeRedemptionResult.Confirmed) result).chequePublicId().equals(chequePublicId),
                        "the resume must redeem the exact same cheque, not a different one");

                check(fake.redeemRequests.size() == 2, "expected exactly two dispatched redeem requests (the original and the resume)");
                check(fake.redeemRequests.get(1).chequePublicId().equals(chequePublicId), "the resumed dispatch targeted the wrong cheque");
                check(fake.redeemRequests.get(1).worldNpcPublicId().equals(fake.redeemRequests.get(0).worldNpcPublicId()),
                        "the resume must reuse the exact same teller identity the original attempt captured, not a new/different one");

                // The actual safety claim under test: the resume never touches the player's
                // inventory again -- no restore, no re-insertion, no re-lookup of any physical
                // item for this cheque, on either the failing or the succeeding attempt.
                check(isEmptyInventory(player), "the resume must never restore or insert any physical item back into the player's inventory");
                check(!hasAnyReceiptFor(player.serverLevel(), chequePublicId), "the receipt must be resolved once the resumed redemption succeeds");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Forced-save failure: abort-and-restore (currency deposit's policy) ----------

    /**
     * Proves the abort-and-restore policy explicitly, using the exact real seam {@code
     * BankTransferPlayerDurabilityGameTests} established ({@code useSaveDelegateForTesting}),
     * not a new injection mechanism. Nothing has been dispatched to Rails at the point this
     * failure is detected, so the removal is undone and reported as a plain {@code
     * LocalFailure} -- never withdrawal's mandatory-escalate policy, and never a Rails cancel
     * call (there is nothing prepared to cancel).
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aForcedSaveFailureAbortsAndRestoresTheChequeLocallyWithNoRailsCallEverMade(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        UUID chequePublicId = UUID.randomUUID();
        player.getInventory().setItem(SLOT, buildChequeStack(chequePublicId, REAL_AMOUNT_COPPER));
        BankTransferPlayerDurability.useSaveDelegateForTesting(p -> { throw new RuntimeException("simulated forced-save failure"); });

        FakeClient fake = new FakeClient();
        BankingChequeRedemptionProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingChequeRedemptionResult> future =
                    BankingChequeRedemptionProxyService.triggerChequeRedemptionForTesting(player, teller, SLOT);

            helper.succeedWhen(() -> {
                check(future.isDone(), "the operation must complete even when the forced save throws internally");
                BankingChequeRedemptionResult result = future.join();
                check(result instanceof BankingChequeRedemptionResult.LocalFailure,
                        "a detected forced-save failure must abort-and-restore and report LocalFailure, got " + result);

                ItemStack restored = player.getInventory().getItem(SLOT);
                check(!restored.isEmpty() && restored.getItem() == ItemRegistry.BANK_CHEQUE.get(),
                        "the cheque must have been restored (grow(1)) back into the slot after the detected save failure");
                BankChequeData data = restored.get(DataComponentRegistry.BANK_CHEQUE_DATA.get());
                check(data != null && data.chequeId().equals(chequePublicId), "the restored cheque must be the exact same one, not a different stack");

                check(!hasAnyReceiptFor(player.serverLevel(), chequePublicId),
                        "no receipt may exist -- the failure was detected before the receipt was ever written");
                check(fake.redeemRequests.isEmpty(), "no Rails call may ever be dispatched once the forced save failed");

                BankTransferPlayerDurability.resetSaveDelegateForTesting();
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            BankTransferPlayerDurability.resetSaveDelegateForTesting();
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Crash recovery: removal survives, resume dispatches and resolves ----------

    /**
     * Simulates a crash between the physical removal (plus durable receipt) and dispatch ever
     * being attempted -- {@link BankingChequeRedemptionProxyService#captureAndRemoveForTesting}
     * stops exactly there. The surviving on-disk receipt must carry {@code worldNpcPublicId}
     * (this receipt type's own genuinely new field) and no item payload or currency amount.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aCrashBetweenRemovalAndDispatchSurvivesAsAPendingReceiptCarryingWorldNpcPublicId(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        UUID chequePublicId = UUID.randomUUID();
        player.getInventory().setItem(SLOT, buildChequeStack(chequePublicId, REAL_AMOUNT_COPPER));

        FakeClient fake = new FakeClient();
        // redeemBehavior deliberately left throwing -- dispatch must never be reached by this path.
        BankingChequeRedemptionProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingChequeRedemptionProxyService.CaptureOutcome> future =
                    BankingChequeRedemptionProxyService.captureAndRemoveForTesting(player, teller, SLOT);

            helper.succeedWhen(() -> {
                check(future.isDone(), "captureAndRemove did not complete");
                BankingChequeRedemptionProxyService.CaptureOutcome outcome = future.join();
                check(outcome instanceof BankingChequeRedemptionProxyService.CaptureOutcome.Removed,
                        "expected Removed, got " + outcome);
                BankingChequeRedemptionProxyService.CaptureOutcome.Removed removed =
                        (BankingChequeRedemptionProxyService.CaptureOutcome.Removed) outcome;
                check(removed.chequePublicId().equals(chequePublicId), "wrong captured cheque id in Removed outcome");

                check(fake.redeemRequests.isEmpty(), "dispatch must not have been called before the simulated crash");
                check(player.getInventory().getItem(SLOT).isEmpty(),
                        "the cheque must genuinely be gone from the player's inventory after removal");

                BankTransferReceipt survived = findReceipt(player.serverLevel(), chequePublicId);
                check(survived.worldNpcPublicId() != null, "a redemption receipt must carry worldNpcPublicId");
                check(survived.itemPayload() == null || survived.itemPayload().length == 0,
                        "a redemption receipt must carry no item payload");
                check(survived.currencyAmount() == null, "a redemption receipt must carry no currency amount");
                check(survived.status() == BankTransferReceiptStatus.PENDING_LOCAL_ACTION,
                        "dispatch was never called, so the receipt must still be PENDING_LOCAL_ACTION");

                BankTransferReceiptStore.ScanResult scan = readFreshStore(player.serverLevel()).scanUnresolved();
                check(scan.pending().stream().anyMatch(r -> r.operationId().equals(chequePublicId)),
                        "the surviving redemption receipt must appear in scanUnresolved()'s pending bucket");

                check(BankingChequeRedemptionProxyService.isInFlightForTesting(player.getUUID(), SLOT),
                        "a simulated crash must leave the slot marked in-flight, matching real crash semantics");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    /**
     * The crash test's counterpart, driven through the real {@link
     * BankTransferReconciliationService#reconcile} entry point (not a direct proxy-service
     * call) -- proving the {@code CHEQUE_REDEMPTION} branch this slice added actually resumes
     * and resolves correctly, with no live {@code ServerPlayer} touched at all (this class's own
     * "Crash recovery needs no on-login hook" docs).
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void startupReconciliationResumesARedemptionReceiptAndResolvesItOnSuccess(GameTestHelper helper) {
        installBankRegistry();
        ServerPlayer player = setUpPlayer(helper, spawnBankTeller(helper));
        ServerLevel level = player.serverLevel();
        MinecraftServer server = level.getServer();

        UUID chequePublicId = UUID.randomUUID();
        UUID worldNpcPublicId = UUID.randomUUID();
        com.seggellion.britannia_mod.bank.transfer.BankTransferReceipts.record(
                level, chequePublicId, player.getUUID(),
                com.seggellion.britannia_mod.bank.transfer.BankTransferOperationType.CHEQUE_REDEMPTION,
                null, null, null, worldNpcPublicId, System.currentTimeMillis()
        );
        BankTransferReceipt receipt = findReceipt(level, chequePublicId);

        FakeClient fake = new FakeClient();
        fake.redeemBehavior = () -> CompletableFuture.completedFuture(new BankingChequeRedemptionResult.Confirmed(chequePublicId));
        BankingChequeRedemptionProxyService.useClientForTesting(fake);

        try {
            BankTransferReconciliationService.reconcile(
                    server, new BankTransferReceiptStore.ScanResult(List.of(receipt), List.of(), List.of())
            );

            helper.succeedWhen(() -> {
                check(fake.redeemRequests.size() == 1, "the resume did not dispatch exactly one redeem request: " + fake.redeemRequests);
                BankingChequeRedemptionRequest sent = fake.redeemRequests.get(0);
                check(sent.chequePublicId().equals(chequePublicId), "the resume dispatched the wrong cheque id");
                check(sent.worldNpcPublicId().equals(worldNpcPublicId), "the resume did not reuse the receipt's own persisted worldNpcPublicId");
                check(sent.playerUuid().equals(player.getUUID()), "the resume dispatched the wrong player uuid");

                check(!hasAnyReceiptFor(level, chequePublicId), "the resumed receipt must be resolved after a successful redeem");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Duplicate trigger: IN_FLIGHT dedup for the same slot ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aRepeatTriggerForTheSameSlotWhileOneIsInFlightDoesNotDispatchASecondRedeem(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, buildChequeStack(UUID.randomUUID(), REAL_AMOUNT_COPPER));

        AtomicInteger redeemDispatchCount = new AtomicInteger();
        CompletableFuture<BankingChequeRedemptionResult> pending = new CompletableFuture<>();
        FakeClient fake = new FakeClient();
        fake.redeemBehavior = () -> {
            redeemDispatchCount.incrementAndGet();
            return pending;
        };
        BankingChequeRedemptionProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingChequeRedemptionResult> first =
                    BankingChequeRedemptionProxyService.triggerChequeRedemptionForTesting(player, teller, SLOT);
            CompletableFuture<BankingChequeRedemptionResult> second =
                    BankingChequeRedemptionProxyService.triggerChequeRedemptionForTesting(player, teller, SLOT);

            check(redeemDispatchCount.get() == 1,
                    "a repeat trigger for the same slot dispatched a second redeem call (count=" + redeemDispatchCount.get() + ")");
            check(second.isDone() && second.join() instanceof BankingChequeRedemptionResult.LocalFailure,
                    "the duplicate trigger did not report a local failure immediately");

            UUID confirmedId = fake.redeemRequests.get(0).chequePublicId();
            pending.complete(new BankingChequeRedemptionResult.Confirmed(confirmedId));

            helper.succeedWhen(() -> {
                check(first.isDone(), "the original redemption did not complete");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    /** A second, DIFFERENT slot must never be blocked by an unrelated in-flight redemption. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void differentSlotsRedeemIndependentlyWithoutDedupInterference(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        player.getInventory().setItem(SLOT, buildChequeStack(first, REAL_AMOUNT_COPPER));
        player.getInventory().setItem(OTHER_SLOT, buildChequeStack(second, REAL_AMOUNT_COPPER));

        FakeClient fake = new FakeClient();
        fake.redeemBehavior = () -> CompletableFuture.completedFuture(null);
        // Overridden per-call below via a request-tracking supplier since each call needs its own cheque id echoed back.
        fake.redeemRequestHandler = request ->
                CompletableFuture.completedFuture(new BankingChequeRedemptionResult.Confirmed(request.chequePublicId()));
        BankingChequeRedemptionProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingChequeRedemptionResult> firstFuture =
                    BankingChequeRedemptionProxyService.triggerChequeRedemptionForTesting(player, teller, SLOT);
            CompletableFuture<BankingChequeRedemptionResult> secondFuture =
                    BankingChequeRedemptionProxyService.triggerChequeRedemptionForTesting(player, teller, OTHER_SLOT);

            check(!(firstFuture.isDone() && firstFuture.join() instanceof BankingChequeRedemptionResult.LocalFailure),
                    "the first slot's redemption was incorrectly rejected as a duplicate");
            check(!(secondFuture.isDone() && secondFuture.join() instanceof BankingChequeRedemptionResult.LocalFailure),
                    "the second, different slot's redemption was incorrectly rejected as a duplicate");

            helper.succeedWhen(() -> {
                check(firstFuture.isDone() && secondFuture.isDone(), "both redemptions did not complete");
                check(firstFuture.join() instanceof BankingChequeRedemptionResult.Confirmed, "first did not confirm: " + firstFuture.join());
                check(secondFuture.join() instanceof BankingChequeRedemptionResult.Confirmed, "second did not confirm: " + secondFuture.join());
                check(fake.redeemRequests.size() == 2, "expected exactly two redeem requests");
                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Helpers (self-contained per this codebase's GameTest convention) ----------

    private static ItemStack buildChequeStack(UUID chequePublicId, long displayAmountCopper) {
        ItemStack stack = new ItemStack(ItemRegistry.BANK_CHEQUE.get());
        stack.set(DataComponentRegistry.BANK_CHEQUE_DATA.get(), new BankChequeData(chequePublicId, displayAmountCopper, "Britannia Bank"));
        return stack;
    }

    private static ServerPlayer setUpPlayer(GameTestHelper helper, ServiceNpcEntity teller) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.teleportTo(teller.getX() + 1.0, teller.getY(), teller.getZ());
        return player;
    }

    /** Whole-inventory emptiness check -- stronger than checking only the original slot, so a restore-into-a-different-slot bug would still be caught. */
    private static boolean isEmptyInventory(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (!player.getInventory().getItem(i).isEmpty()) return false;
        }
        return true;
    }

    private static void cleanUp() {
        BankingChequeRedemptionProxyService.resetClientForTesting();
        BankingChequeRedemptionProxyService.resetInFlightTrackingForTesting();
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

    /** Per-method fake, mirroring {@link BankingCurrencyDepositProxyServiceGameTests}' own FakeClient shape. */
    private static final class FakeClient implements BankingChequeRedemptionClientPort {
        java.util.function.Supplier<CompletableFuture<BankingChequeRedemptionResult>> redeemBehavior =
                () -> { throw new IllegalStateException("redeem() was not expected to be called in this test"); };
        /** Optional per-request handler for tests needing to echo the request's own cheque id back (overrides redeemBehavior when set). */
        java.util.function.Function<BankingChequeRedemptionRequest, CompletableFuture<BankingChequeRedemptionResult>> redeemRequestHandler;

        final List<BankingChequeRedemptionRequest> redeemRequests = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<BankingChequeRedemptionResult> redeem(MinecraftServer server, BankingChequeRedemptionRequest request) {
            redeemRequests.add(request);
            if (redeemRequestHandler != null) return redeemRequestHandler.apply(request);
            return redeemBehavior.get();
        }
    }
}
