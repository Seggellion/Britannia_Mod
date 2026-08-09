package com.seggellion.britannia_mod.gametest;

import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.mixin.PlayerListAccessorMixin;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import com.seggellion.britannia_mod.service.banking.BankingCancelResult;
import com.seggellion.britannia_mod.service.banking.BankingConfirmResult;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyDepositClientPort;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyDepositPrepareRequest;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyDepositPrepareResult;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyDepositProxyService;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyDepositResult;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
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
 * The priority fix's own verification: {@link com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability}
 * forces exactly the transacting player's own data durably to disk before the risk window this
 * program's crash-window verification pass identified as still open (a lag between a receipt's
 * own fsync'd durability and the much slower, tick/disconnect/shutdown-driven durability of the
 * player's own inventory state). Each test here proves the SAME on-disk state a real process
 * kill would leave, read via the real vanilla deserialization path
 * ({@code PlayerDataStorage#load}, reached through {@link PlayerListAccessorMixin} exactly like
 * {@code BankTransferReceiptStore}'s own GameTests bypass their live in-memory instance) -- never
 * the live {@code ServerPlayer} object, which would trivially "pass" regardless of whether
 * anything was actually persisted.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankTransferPlayerDurabilityGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BANK_TYPE_KEY = "bank_teller";
    private static final int SLOT = 0;

    private BankTransferPlayerDurabilityGameTests() {
    }

    // ---------- Item deposit: removal is durable on disk immediately, before the receipt exists ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void itemDepositForcesRemovalDurablyToDiskBeforeTheReceiptIsWritten(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND, 5));

        UUID operationId = UUID.randomUUID();
        FakeDepositClient fake = new FakeDepositClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(
                new BankingDepositPrepareResult.Success(operationId, UUID.randomUUID()));
        BankingDepositProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingDepositProxyService.PrepareAndRemoveOutcome> future =
                    BankingDepositProxyService.prepareAndRemoveForTesting(player, teller, SLOT);

            helper.succeedWhen(() -> {
                check(future.isDone(), "prepareAndRemove did not complete");
                check(future.join() instanceof BankingDepositProxyService.PrepareAndRemoveOutcome.Removed,
                        "expected Removed, got " + future.join());

                ServerPlayer reloaded = loadFreshFromDisk(player);
                check(reloaded.getInventory().getItem(SLOT).isEmpty(),
                        "the removal must already be durable on disk immediately after prepareAndRemove -- "
                                + "found " + reloaded.getInventory().getItem(SLOT) + " instead");

                BankingDepositProxyService.resetClientForTesting();
                BankingDepositProxyService.resetInFlightTrackingForTesting();
                ServiceNpcRegistryCache.clear();
            });
        } catch (RuntimeException | Error propagate) {
            BankingDepositProxyService.resetClientForTesting();
            BankingDepositProxyService.resetInFlightTrackingForTesting();
            ServiceNpcRegistryCache.clear();
            throw propagate;
        }
    }

    // ---------- Item withdrawal: insertion is durable on disk immediately, before confirm ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void itemWithdrawalForcesInsertionDurablyToDiskBeforeConfirmIsDispatched(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        HolderLookup.Provider registries = player.registryAccess();
        ItemStack original = new ItemStack(Items.DIAMOND, 5);
        byte[] payload = com.seggellion.britannia_mod.bank.item.BankItemCodec.serialize(original.copy(), registries);
        String fingerprint = com.seggellion.britannia_mod.bank.item.BankItemFingerprint.fingerprint(original, registries);
        double weight = com.seggellion.britannia_mod.bank.item.BankItemWeight.resolve(original);

        UUID operationId = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        FakeWithdrawalClient fake = new FakeWithdrawalClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingWithdrawalPrepareResult.Success(
                operationId, bankItemId, com.seggellion.britannia_mod.bank.item.BankItemSchemaVersion.CURRENT, payload, fingerprint, weight));
        BankingWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingWithdrawalProxyService.PrepareAndInsertOutcome> future =
                    BankingWithdrawalProxyService.prepareAndInsertForTesting(player, teller, bankItemId);

            helper.succeedWhen(() -> {
                check(future.isDone(), "prepareAndInsert did not complete");
                check(future.join() instanceof BankingWithdrawalProxyService.PrepareAndInsertOutcome.Inserted,
                        "expected Inserted, got " + future.join());

                ServerPlayer reloaded = loadFreshFromDisk(player);
                boolean found = false;
                for (int i = 0; i < reloaded.getInventory().getContainerSize(); i++) {
                    ItemStack stack = reloaded.getInventory().getItem(i);
                    if (stack.getItem() == Items.DIAMOND && stack.getCount() == 5) {
                        found = true;
                        break;
                    }
                }
                check(found, "the inserted item must already be durable on disk immediately after prepareAndInsert");

                BankingWithdrawalProxyService.resetClientForTesting();
                BankingWithdrawalProxyService.resetInFlightTrackingForTesting();
                ServiceNpcRegistryCache.clear();
            });
        } catch (RuntimeException | Error propagate) {
            BankingWithdrawalProxyService.resetClientForTesting();
            BankingWithdrawalProxyService.resetInFlightTrackingForTesting();
            ServiceNpcRegistryCache.clear();
            throw propagate;
        }
    }

    // ---------- Currency deposit: removal is durable on disk immediately, before the receipt exists ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void currencyDepositForcesRemovalDurablyToDiskBeforeTheReceiptIsWritten(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getInventory().setItem(SLOT, new ItemStack(ItemRegistry.GOLD_COIN.get(), 40));

        UUID operationId = UUID.randomUUID();
        FakeCurrencyDepositClient fake = new FakeCurrencyDepositClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingCurrencyDepositPrepareResult.Success(operationId));
        BankingCurrencyDepositProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyDepositProxyService.PrepareAndRemoveOutcome> future =
                    BankingCurrencyDepositProxyService.prepareAndRemoveForTesting(player, teller, SLOT);

            helper.succeedWhen(() -> {
                check(future.isDone(), "prepareAndRemove did not complete");
                check(future.join() instanceof BankingCurrencyDepositProxyService.PrepareAndRemoveOutcome.Removed,
                        "expected Removed, got " + future.join());

                ServerPlayer reloaded = loadFreshFromDisk(player);
                check(reloaded.getInventory().getItem(SLOT).isEmpty(),
                        "the coin removal must already be durable on disk immediately after prepareAndRemove -- "
                                + "found " + reloaded.getInventory().getItem(SLOT) + " instead");

                BankingCurrencyDepositProxyService.resetClientForTesting();
                BankingCurrencyDepositProxyService.resetInFlightTrackingForTesting();
                ServiceNpcRegistryCache.clear();
            });
        } catch (RuntimeException | Error propagate) {
            BankingCurrencyDepositProxyService.resetClientForTesting();
            BankingCurrencyDepositProxyService.resetInFlightTrackingForTesting();
            ServiceNpcRegistryCache.clear();
            throw propagate;
        }
    }

    // ---------- A forced-save failure must not crash the operation, and must follow the reconsidered policy ----------
    //
    // BankTransferPlayerDurability.forceSave's own catch(RuntimeException) already mechanically
    // guarantees any underlying save failure is caught and turned into false -- that is a fact of
    // Java's type system, not something that needs a contrived real-game-state reproduction to
    // prove (two prior attempts at exactly that -- a throwing saveWithoutId override, then a
    // corrupted ServerStatsCounter encode -- were abandoned: the first never actually reached
    // forceSave's catch, and the second threw too early, at Stat construction rather than at
    // save time). What actually matters, and was never tested, is whether the CALLING code (the
    // three proxy services) correctly responds to forceSave returning false. useSaveDelegateForTesting
    // isolates exactly that boundary: it substitutes forceSave's own underlying save call with a
    // test double, the same seam pattern this codebase already uses for BankingDepositProxyService's
    // own client field. Each of the three flows below has a genuinely different, real policy
    // (abort for both deposits, escalate for withdrawal), so each is proven separately here.

    /**
     * Proves the seam itself works before any real test relies on it: installing a throwing
     * delegate makes {@code forceSave} return {@code false} -- not crash, not propagate an
     * unhandled exception. This tests {@code forceSave}'s own catch/return logic directly, so
     * unlike the two abandoned real-game-state attempts, it cannot fail for the wrong reason.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void forceSaveReturnsFalseWhenItsSaveDelegateThrows(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        try {
            com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability.useSaveDelegateForTesting(
                    p -> { throw new RuntimeException("simulated forced-save failure"); });

            boolean saved = com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability.forceSave(player);
            check(!saved, "expected forceSave to return false when its save delegate throws, but it returned true");

            helper.succeed();
        } finally {
            com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability.resetSaveDelegateForTesting();
        }
    }

    /**
     * Deposit (item and currency) can cleanly abort a detected forced-save failure: nothing
     * durable or Rails-facing exists yet at that point, so the item is restored and the prepared
     * operation is cancelled instead of ever writing a receipt or dispatching to Rails.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aForcedSaveFailureDuringItemDepositAbortsAndRestoresTheItem(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability.useSaveDelegateForTesting(
                p -> { throw new RuntimeException("simulated forced-save failure"); });
        player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND, 5));

        UUID operationId = UUID.randomUUID();
        FakeDepositClient fake = new FakeDepositClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(
                new BankingDepositPrepareResult.Success(operationId, UUID.randomUUID()));
        fake.cancelBehavior = () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        fake.confirmBehavior = () -> { throw new IllegalStateException(
                "confirm() must never be called once forceSave's detected failure aborts the deposit"); };
        BankingDepositProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingDepositResult> future =
                    BankingDepositProxyService.triggerDepositForTesting(player, teller, SLOT);

            helper.succeedWhen(() -> {
                check(future.isDone(), "the operation must complete even when the forced save throws internally");
                BankingDepositResult result = future.join();
                check(result instanceof BankingDepositResult.RemovalFailed,
                        "a detected forced-save failure must abort the deposit as a clean removal failure, got " + result);

                ItemStack inSlot = player.getInventory().getItem(SLOT);
                check(inSlot.getItem() == Items.DIAMOND && inSlot.getCount() == 5,
                        "the item must be restored to the player's live inventory after an aborted deposit, found " + inSlot);

                check(fake.prepareRequests.size() == 1, "prepare must still run exactly once");
                check(fake.cancelRequests.size() == 1, "the prepared operation must be cancelled exactly once on abort");
                check(fake.confirmRequests.isEmpty(), "confirm must never be dispatched for an aborted deposit");

                com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStore.ScanResult scan =
                        com.seggellion.britannia_mod.bank.transfer.BankTransferReceipts.scanUnresolved(player.serverLevel());
                check(scan.pending().stream().noneMatch(r -> r.operationId().equals(operationId))
                                && scan.reconciliationRequired().stream().noneMatch(r -> r.operationId().equals(operationId)),
                        "an aborted deposit must never leave a receipt behind");

                com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability.resetSaveDelegateForTesting();
                BankingDepositProxyService.resetClientForTesting();
                BankingDepositProxyService.resetInFlightTrackingForTesting();
                ServiceNpcRegistryCache.clear();
            });
        } catch (RuntimeException | Error propagate) {
            com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability.resetSaveDelegateForTesting();
            BankingDepositProxyService.resetClientForTesting();
            BankingDepositProxyService.resetInFlightTrackingForTesting();
            ServiceNpcRegistryCache.clear();
            throw propagate;
        }
    }

    /** Currency deposit's own abort policy is identical to item deposit's -- proven separately since it is a distinct code path. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aForcedSaveFailureDuringCurrencyDepositAbortsAndRestoresTheCoins(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability.useSaveDelegateForTesting(
                p -> { throw new RuntimeException("simulated forced-save failure"); });
        player.getInventory().setItem(SLOT, new ItemStack(ItemRegistry.SILVER_COIN.get(), 12));

        UUID operationId = UUID.randomUUID();
        FakeCurrencyDepositClient fake = new FakeCurrencyDepositClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingCurrencyDepositPrepareResult.Success(operationId));
        fake.cancelBehavior = () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        fake.confirmBehavior = () -> { throw new IllegalStateException(
                "confirm() must never be called once forceSave's detected failure aborts the deposit"); };
        BankingCurrencyDepositProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingCurrencyDepositResult> future =
                    BankingCurrencyDepositProxyService.triggerCurrencyDepositForTesting(player, teller, SLOT);

            helper.succeedWhen(() -> {
                check(future.isDone(), "the operation must complete even when the forced save throws internally");
                BankingCurrencyDepositResult result = future.join();
                check(result instanceof BankingCurrencyDepositResult.RemovalFailed,
                        "a detected forced-save failure must abort the currency deposit as a clean removal failure, got " + result);

                ItemStack inSlot = player.getInventory().getItem(SLOT);
                check(inSlot.getItem() == ItemRegistry.SILVER_COIN.get() && inSlot.getCount() == 12,
                        "the coins must be restored to the player's live inventory after an aborted deposit, found " + inSlot);

                check(fake.prepareRequests.size() == 1, "prepare must still run exactly once");
                check(fake.cancelRequests.size() == 1, "the prepared operation must be cancelled exactly once on abort");
                check(fake.confirmRequests.isEmpty(), "confirm must never be dispatched for an aborted deposit");

                com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStore.ScanResult scan =
                        com.seggellion.britannia_mod.bank.transfer.BankTransferReceipts.scanUnresolved(player.serverLevel());
                check(scan.pending().stream().noneMatch(r -> r.operationId().equals(operationId))
                                && scan.reconciliationRequired().stream().noneMatch(r -> r.operationId().equals(operationId)),
                        "an aborted deposit must never leave a receipt behind");

                com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability.resetSaveDelegateForTesting();
                BankingCurrencyDepositProxyService.resetClientForTesting();
                BankingCurrencyDepositProxyService.resetInFlightTrackingForTesting();
                ServiceNpcRegistryCache.clear();
            });
        } catch (RuntimeException | Error propagate) {
            com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability.resetSaveDelegateForTesting();
            BankingCurrencyDepositProxyService.resetClientForTesting();
            BankingCurrencyDepositProxyService.resetInFlightTrackingForTesting();
            ServiceNpcRegistryCache.clear();
            throw propagate;
        }
    }

    /**
     * Withdrawal cannot cleanly abort a detected forced-save failure -- its receipt already
     * durably exists before insertion (Section A.6), so undoing the insertion now would create a
     * fresh inconsistency rather than restore a clean prior state. Instead, a clean Rails
     * {@code Confirmed} must not be allowed to silently resolve (delete) the receipt: this proves
     * the operation instead completes as {@link BankingWithdrawalResult.ReconciliationRequired},
     * the item stays with the player, and the receipt is left escalated, not resolved, for a
     * later mandatory reconciliation pass.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aForcedSaveFailureDuringItemWithdrawalEscalatesToReconciliationRequired(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability.useSaveDelegateForTesting(
                p -> { throw new RuntimeException("simulated forced-save failure"); });
        HolderLookup.Provider registries = player.registryAccess();
        ItemStack original = new ItemStack(Items.DIAMOND, 5);
        byte[] payload = com.seggellion.britannia_mod.bank.item.BankItemCodec.serialize(original.copy(), registries);
        String fingerprint = com.seggellion.britannia_mod.bank.item.BankItemFingerprint.fingerprint(original, registries);
        double weight = com.seggellion.britannia_mod.bank.item.BankItemWeight.resolve(original);

        UUID operationId = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        FakeWithdrawalClient fake = new FakeWithdrawalClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingWithdrawalPrepareResult.Success(
                operationId, bankItemId, com.seggellion.britannia_mod.bank.item.BankItemSchemaVersion.CURRENT, payload, fingerprint, weight));
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingWithdrawalResult> future =
                    BankingWithdrawalProxyService.triggerWithdrawalForTesting(player, teller, bankItemId);

            helper.succeedWhen(() -> {
                check(future.isDone(), "the operation must complete even when the forced save throws internally");
                BankingWithdrawalResult result = future.join();
                check(result instanceof BankingWithdrawalResult.ReconciliationRequired,
                        "a detected forced-save failure must not let a clean Rails confirm silently resolve the receipt, got " + result);

                boolean found = false;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.getItem() == Items.DIAMOND && stack.getCount() == 5) {
                        found = true;
                        break;
                    }
                }
                check(found, "withdrawal cannot cleanly abort once the receipt exists -- the item must remain with the player");

                com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStore.ScanResult scan =
                        com.seggellion.britannia_mod.bank.transfer.BankTransferReceipts.scanUnresolved(player.serverLevel());
                check(scan.reconciliationRequired().stream().anyMatch(r -> r.operationId().equals(operationId)),
                        "the receipt must be escalated to reconciliation_required, not silently resolved");
                check(scan.pending().stream().noneMatch(r -> r.operationId().equals(operationId)),
                        "the receipt must not remain in plain pending state either");

                check(fake.prepareRequests.size() == 1 && fake.confirmRequests.size() == 1,
                        "the normal prepare/confirm sequence must still run exactly once despite the save failure");

                com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability.resetSaveDelegateForTesting();
                BankingWithdrawalProxyService.resetClientForTesting();
                BankingWithdrawalProxyService.resetInFlightTrackingForTesting();
                ServiceNpcRegistryCache.clear();
            });
        } catch (RuntimeException | Error propagate) {
            com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability.resetSaveDelegateForTesting();
            BankingWithdrawalProxyService.resetClientForTesting();
            BankingWithdrawalProxyService.resetInFlightTrackingForTesting();
            ServiceNpcRegistryCache.clear();
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

    /**
     * Loads {@code original}'s just-saved on-disk data into a brand-new, otherwise-untouched
     * {@code ServerPlayer} via the real {@code PlayerDataStorage#load} -- never inspects the
     * live, in-memory {@code original} itself, which would trivially reflect the removal/
     * insertion regardless of whether anything was ever actually persisted.
     */
    private static ServerPlayer loadFreshFromDisk(ServerPlayer original) {
        MinecraftServer server = original.server;
        GameProfile profile = new GameProfile(original.getUUID(), original.getGameProfile().getName());
        ServerPlayer fresh = new ServerPlayer(server, original.serverLevel(), profile, ClientInformation.createDefault());
        ((PlayerListAccessorMixin) server.getPlayerList()).britannia$playerIo().load(fresh);
        // ServerPlayer's own constructor calls PlayerList#getPlayerAdvancements(this), which caches
        // PlayerAdvancements by UUID and re-binds the cached tracker's internal player reference
        // (PlayerAdvancements#setPlayer) to whichever instance was constructed most recently for
        // that UUID -- traced directly from PlayerList#getPlayerAdvancements. Since `fresh` shares
        // `original`'s UUID, constructing it silently hijacks `original`'s own advancement tracker
        // to point at this disconnected, throwaway instance, so any later advancement trigger on
        // the still-live `original` (including ordinary per-tick triggers) would call back into a
        // ServerPlayer with a null connection. Re-fetching restores the binding to `original`.
        server.getPlayerList().getPlayerAdvancements(original);
        return fresh;
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

    private static final class FakeDepositClient implements BankingDepositClientPort {
        java.util.function.Supplier<CompletableFuture<BankingDepositPrepareResult>> prepareBehavior =
                () -> { throw new IllegalStateException("prepare() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingConfirmResult>> confirmBehavior =
                () -> { throw new IllegalStateException("confirm() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingCancelResult>> cancelBehavior =
                () -> { throw new IllegalStateException("cancel() was not expected to be called in this test"); };

        final List<BankingDepositPrepareRequest> prepareRequests = new CopyOnWriteArrayList<>();
        final List<BankingOperationRequest> cancelRequests = new CopyOnWriteArrayList<>();
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
            cancelRequests.add(request);
            return cancelBehavior.get();
        }
    }

    private static final class FakeWithdrawalClient implements BankingWithdrawalClientPort {
        java.util.function.Supplier<CompletableFuture<BankingWithdrawalPrepareResult>> prepareBehavior =
                () -> { throw new IllegalStateException("prepareWithdrawal() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingConfirmResult>> confirmBehavior =
                () -> { throw new IllegalStateException("confirm() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingCancelResult>> cancelBehavior =
                () -> { throw new IllegalStateException("cancel() was not expected to be called in this test"); };

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

    private static final class FakeCurrencyDepositClient implements BankingCurrencyDepositClientPort {
        java.util.function.Supplier<CompletableFuture<BankingCurrencyDepositPrepareResult>> prepareBehavior =
                () -> { throw new IllegalStateException("prepareCurrencyDeposit() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingConfirmResult>> confirmBehavior =
                () -> { throw new IllegalStateException("confirm() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingCancelResult>> cancelBehavior =
                () -> { throw new IllegalStateException("cancel() was not expected to be called in this test"); };

        final List<BankingCurrencyDepositPrepareRequest> prepareRequests = new CopyOnWriteArrayList<>();
        final List<BankingOperationRequest> cancelRequests = new CopyOnWriteArrayList<>();
        final List<BankingOperationRequest> confirmRequests = new CopyOnWriteArrayList<>();

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
