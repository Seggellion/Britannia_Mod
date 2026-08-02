package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.item.BankItemCodec;
import com.seggellion.britannia_mod.bank.item.BankItemFingerprint;
import com.seggellion.britannia_mod.bank.item.BankItemSchemaVersion;
import com.seggellion.britannia_mod.bank.item.BankItemWeight;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStore;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import com.seggellion.britannia_mod.service.banking.BankingCancelResult;
import com.seggellion.britannia_mod.service.banking.BankingConfirmResult;
import com.seggellion.britannia_mod.service.banking.BankingOperationRequest;
import com.seggellion.britannia_mod.service.banking.BankingTransferOutcome;
import com.seggellion.britannia_mod.service.banking.BankingWithdrawalAbortReason;
import com.seggellion.britannia_mod.service.banking.BankingWithdrawalClientPort;
import com.seggellion.britannia_mod.service.banking.BankingWithdrawalPrepareRequest;
import com.seggellion.britannia_mod.service.banking.BankingWithdrawalPrepareResult;
import com.seggellion.britannia_mod.service.banking.BankingWithdrawalProxyService;
import com.seggellion.britannia_mod.service.banking.BankingWithdrawalResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
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
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Milestone 9 NeoForge Slice 2: the withdrawal path, exercised entirely through {@link
 * BankingWithdrawalProxyService}'s test-support entry points -- no live screen/packet trigger
 * exists yet (Slice 3's job). Nothing here talks to a real Rails server: every test substitutes
 * a {@link FakeClient} via {@link BankingWithdrawalProxyService#useClientForTesting}, mirroring
 * {@link BankingDepositProxyServiceGameTests}' own established approach.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankingWithdrawalProxyServiceGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BANK_TYPE_KEY = "bank_teller";

    private BankingWithdrawalProxyServiceGameTests() {
    }

    // ---------- Happy path ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void happyPathEndsConfirmedWithItemInInventoryAndFingerprintMatch(GameTestHelper helper) {
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
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingWithdrawalPrepareResult.Success(
                operationId, bankItemId, BankItemSchemaVersion.CURRENT, payload, fingerprint, weight));
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingWithdrawalResult> future =
                    BankingWithdrawalProxyService.triggerWithdrawalForTesting(player, teller, bankItemId);

            helper.succeedWhen(() -> {
                check(future.isDone(), "withdrawal did not complete");
                BankingWithdrawalResult result = future.join();
                check(result instanceof BankingWithdrawalResult.Confirmed, "expected Confirmed, got " + result);
                BankingWithdrawalResult.Confirmed confirmed = (BankingWithdrawalResult.Confirmed) result;
                check(confirmed.operationPublicId().equals(operationId), "wrong operation id in result");
                check(confirmed.bankItemPublicId().equals(bankItemId), "wrong bank item id in result");

                ItemStack inSlot = findFirstNonEmpty(player.getInventory());
                check(inSlot != null && inSlot.getItem() == Items.DIAMOND && inSlot.getCount() == 5,
                        "the withdrawn item was not physically present in the player's inventory");
                String actualFingerprint = BankItemFingerprint.fingerprint(inSlot, registries);
                check(actualFingerprint.equals(fingerprint),
                        "reconstructed item's fingerprint did not exactly match what Rails sent");

                check(fake.prepareRequests.size() == 1, "expected exactly one prepare request");
                check(fake.prepareRequests.get(0).bankItemPublicId().equals(bankItemId), "wrong bank item id sent to prepare");

                check(fake.confirmRequests.size() == 1, "expected exactly one confirm request");
                check(fake.confirmRequests.get(0).operationPublicId().equals(operationId), "wrong operation id sent to confirm");
                check(fake.cancelRequests.isEmpty(), "cancel must never be called on the happy path");

                check(!hasAnyReceiptFor(player.serverLevel(), operationId), "the receipt was not resolved after a clean confirm");
                check(!BankingWithdrawalProxyService.isInFlightForTesting(bankItemId),
                        "IN_FLIGHT was not cleared after the sequence completed");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Full inventory: rejected by the pre-check before any receipt/risk ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void fullInventoryIsRejectedByPreCheckWithNoReceiptOrRailsRiskAndCancelCalled(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        HolderLookup.Provider registries = player.registryAccess();
        fillInventoryCompletely(player.getInventory());

        ItemStack toWithdraw = new ItemStack(Items.DIAMOND, 1);
        byte[] payload = BankItemCodec.serialize(toWithdraw.copy(), registries);
        String fingerprint = BankItemFingerprint.fingerprint(toWithdraw, registries);
        double weight = BankItemWeight.resolve(toWithdraw);

        UUID operationId = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingWithdrawalPrepareResult.Success(
                operationId, bankItemId, BankItemSchemaVersion.CURRENT, payload, fingerprint, weight));
        fake.cancelBehavior = () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        BankingWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingWithdrawalResult> future =
                    BankingWithdrawalProxyService.triggerWithdrawalForTesting(player, teller, bankItemId);

            helper.succeedWhen(() -> {
                check(future.isDone(), "withdrawal did not complete");
                BankingWithdrawalResult result = future.join();
                check(result instanceof BankingWithdrawalResult.Aborted, "expected Aborted, got " + result);
                BankingWithdrawalResult.Aborted aborted = (BankingWithdrawalResult.Aborted) result;
                check(aborted.reason() == BankingWithdrawalAbortReason.INSUFFICIENT_CAPACITY,
                        "wrong abort reason: " + aborted.reason());
                check(aborted.operationPublicId().equals(operationId), "wrong operation id in Aborted result");

                check(fake.cancelRequests.size() == 1, "cancel was not called after the pre-check rejected the withdrawal");
                check(fake.cancelRequests.get(0).operationPublicId().equals(operationId), "wrong operation id sent to cancel");
                check(fake.confirmRequests.isEmpty(), "confirm must never be called after a pre-check rejection");

                check(!hasAnyReceiptFor(player.serverLevel(), operationId),
                        "a receipt must never be written when the pre-check rejects the withdrawal");
                check(!containsItem(player.getInventory(), Items.DIAMOND),
                        "no diamond must have been conjured into the already-full inventory");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Insertion-failure-despite-passing-precheck: structurally unreachable, proven ----------

    /**
     * The same 10 adversarial inventory shapes both capacity-battery tests below run --
     * extracted once so the survival-mode test ({@link
     * #capacityPreCheckAgreesWithRealInsertionAcrossAdversarialInventoryShapes}) and the
     * creative-mode test ({@link #capacityPreCheckReasoningHoldsForCreativePlayersToo}) exercise
     * the exact same shapes rather than two independently-maintained lists that could drift.
     */
    private record CapacityScenario(int number, String description, Consumer<Inventory> setup, Supplier<ItemStack> candidate) {
    }

    private static final List<CapacityScenario> CAPACITY_SCENARIOS = List.of(
            new CapacityScenario(1, "completely empty inventory",
                    Inventory::clearContent,
                    () -> new ItemStack(Items.DIAMOND, 5)),
            new CapacityScenario(2, "completely full (36 main slots + offhand, non-matching item)",
                    inventory -> {
                        inventory.clearContent();
                        for (int i = 0; i < 36; i++) inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
                        inventory.offhand.set(0, new ItemStack(Items.COBBLESTONE, 64));
                    },
                    () -> new ItemStack(Items.DIAMOND, 1)),
            new CapacityScenario(3, "otherwise full, one slot has a partial matching stack with exactly enough headroom",
                    inventory -> {
                        inventory.clearContent();
                        inventory.setItem(0, new ItemStack(Items.DIAMOND, 55));
                        for (int i = 1; i < 36; i++) inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
                        inventory.offhand.set(0, new ItemStack(Items.COBBLESTONE, 64));
                    },
                    () -> new ItemStack(Items.DIAMOND, 9)),
            new CapacityScenario(4, "same shape, one item more than the partial stack's headroom",
                    inventory -> {
                        inventory.clearContent();
                        inventory.setItem(0, new ItemStack(Items.DIAMOND, 55));
                        for (int i = 1; i < 36; i++) inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
                        inventory.offhand.set(0, new ItemStack(Items.COBBLESTONE, 64));
                    },
                    () -> new ItemStack(Items.DIAMOND, 10)),
            new CapacityScenario(5, "main slots full, offhand empty -- an empty offhand slot is never a landing target",
                    inventory -> {
                        inventory.clearContent();
                        for (int i = 0; i < 36; i++) inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
                    },
                    () -> new ItemStack(Items.DIAMOND, 1)),
            new CapacityScenario(6, "main slots full, offhand already holds a matching, non-full stack",
                    inventory -> {
                        inventory.clearContent();
                        for (int i = 0; i < 36; i++) inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
                        inventory.offhand.set(0, new ItemStack(Items.DIAMOND, 63));
                    },
                    () -> new ItemStack(Items.DIAMOND, 1)),
            new CapacityScenario(7, "damaged item, exactly one free main slot",
                    inventory -> {
                        inventory.clearContent();
                        for (int i = 0; i < 35; i++) inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
                        inventory.offhand.set(0, new ItemStack(Items.COBBLESTONE, 64));
                    },
                    BankingWithdrawalProxyServiceGameTests::damagedPickaxe),
            new CapacityScenario(8, "damaged item, zero free main slots (a free offhand slot doesn't count)",
                    inventory -> {
                        inventory.clearContent();
                        for (int i = 0; i < 36; i++) inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
                    },
                    BankingWithdrawalProxyServiceGameTests::damagedPickaxe),
            new CapacityScenario(9, "capacity spread across several partially-filled matching stacks, summing to exactly enough",
                    inventory -> {
                        inventory.clearContent();
                        inventory.setItem(0, new ItemStack(Items.DIAMOND, 90));
                        inventory.setItem(1, new ItemStack(Items.DIAMOND, 90));
                        inventory.setItem(2, new ItemStack(Items.DIAMOND, 90));
                        for (int i = 3; i < 36; i++) inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
                        inventory.offhand.set(0, new ItemStack(Items.COBBLESTONE, 64));
                    },
                    () -> new ItemStack(Items.DIAMOND, 27)),
            new CapacityScenario(10, "same shape, one more than the summed headroom",
                    inventory -> {
                        inventory.clearContent();
                        inventory.setItem(0, new ItemStack(Items.DIAMOND, 90));
                        inventory.setItem(1, new ItemStack(Items.DIAMOND, 90));
                        inventory.setItem(2, new ItemStack(Items.DIAMOND, 90));
                        for (int i = 3; i < 36; i++) inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
                        inventory.offhand.set(0, new ItemStack(Items.COBBLESTONE, 64));
                    },
                    () -> new ItemStack(Items.DIAMOND, 28))
    );

    /**
     * Section A.6 asks whether "insertion fails despite a passing pre-check" is reachable, with
     * evidence either way. {@link BankingWithdrawalProxyService}'s own class docs conclude it is
     * not, in real operation: the pre-check and the real {@code Inventory#add} call run
     * back-to-back on the main server thread with no yield point between them, so nothing can
     * observe or mutate the inventory in between, and {@link
     * BankingWithdrawalProxyService#hasSufficientCapacity} implements the exact same
     * slot-selection rules {@code Inventory#add} itself uses. This test is that evidence, for a
     * survival player: it calls {@code hasSufficientCapacity} directly against {@link
     * #CAPACITY_SCENARIOS}, and for each one also performs the real {@code Inventory#add} call,
     * asserting the pre-check's prediction and the real outcome always agree -- in lieu of a
     * dedicated insertion-failure-path test, since no such path exists to exercise. See {@link
     * #capacityPreCheckReasoningHoldsForCreativePlayersToo} for the creative-player counterpart.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void capacityPreCheckAgreesWithRealInsertionAcrossAdversarialInventoryShapes(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        Inventory inventory = player.getInventory();

        for (CapacityScenario scenario : CAPACITY_SCENARIOS) {
            assertSurvivalScenario(inventory, scenario);
        }

        inventory.clearContent();
        cleanUp();
        helper.succeed();
    }

    /**
     * Verification-pass Part A: {@link BankingWithdrawalProxyService#hasSufficientCapacity}
     * takes only {@code (Inventory, ItemStack)} -- no {@code Player}, no game-mode signal at all
     * -- so by construction its prediction cannot differ between a creative/instabuild player and
     * a survival one given the same inventory shape. What genuinely differs is {@code
     * Inventory#add} itself: whenever the ordinary placement loop stops making progress while
     * {@code player.hasInfiniteMaterials()} is true, vanilla force-zeroes whatever count remains
     * (silent discard) instead of leaving a genuine leftover -- and this can fire after
     * <i>partial</i> genuine placement, not only when nothing fit at all: {@code
     * ItemStack#isEmpty()} reports the exact same "success" outcome whether the stack was fully,
     * genuinely placed, partially placed with the rest destroyed, or entirely destroyed. This
     * test runs the identical {@link #CAPACITY_SCENARIOS} battery against a creative/instabuild
     * player and asserts precisely: for every shape the pre-check predicts "sufficient" for, the
     * item is genuinely, fully placed (the inventory's own item count increases by the full
     * amount -- not merely {@code isEmpty()}, which the silent-discard branch can also satisfy);
     * for every shape it predicts "insufficient" for, calling {@code Inventory#add} directly
     * (bypassing production orchestration entirely) never results in the full amount being
     * genuinely placed -- proving the divergence is real, not hypothetical, and that avoiding it
     * depends entirely on production never calling {@code add()} after a false prediction, which
     * {@link #fullInventoryIsRejectedByPreCheckEvenForCreativePlayersWithNoItemDestroyed} proves
     * it doesn't, through the actual orchestrated path.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void capacityPreCheckReasoningHoldsForCreativePlayersToo(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getAbilities().instabuild = true;
        Inventory inventory = player.getInventory();

        for (CapacityScenario scenario : CAPACITY_SCENARIOS) {
            assertCreativeScenario(inventory, scenario);
        }

        inventory.clearContent();
        cleanUp();
        helper.succeed();
    }

    /**
     * The production-level counterpart to {@link #capacityPreCheckReasoningHoldsForCreativePlayersToo}:
     * a creative/instabuild player attempting a withdrawal against an already-full inventory,
     * driven through the real orchestrated {@link BankingWithdrawalProxyService#triggerWithdrawalForTesting}
     * sequence rather than calling {@code hasSufficientCapacity}/{@code Inventory#add} directly.
     * If production ever called {@code add()} after the pre-check predicted "insufficient," the
     * result here would not be {@code Aborted(INSUFFICIENT_CAPACITY)} (that reason is only ever
     * assigned strictly before {@code add()} is invoked) -- so asserting that exact result proves
     * {@code add()} was never reached, and the "no diamond conjured" check proves nothing was
     * silently destroyed either.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void fullInventoryIsRejectedByPreCheckEvenForCreativePlayersWithNoItemDestroyed(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        player.getAbilities().instabuild = true;
        HolderLookup.Provider registries = player.registryAccess();
        fillInventoryCompletely(player.getInventory());

        ItemStack toWithdraw = new ItemStack(Items.DIAMOND, 1);
        byte[] payload = BankItemCodec.serialize(toWithdraw.copy(), registries);
        String fingerprint = BankItemFingerprint.fingerprint(toWithdraw, registries);
        double weight = BankItemWeight.resolve(toWithdraw);

        UUID operationId = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingWithdrawalPrepareResult.Success(
                operationId, bankItemId, BankItemSchemaVersion.CURRENT, payload, fingerprint, weight));
        fake.cancelBehavior = () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        BankingWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingWithdrawalResult> future =
                    BankingWithdrawalProxyService.triggerWithdrawalForTesting(player, teller, bankItemId);

            helper.succeedWhen(() -> {
                check(future.isDone(), "withdrawal did not complete");
                BankingWithdrawalResult result = future.join();
                check(result instanceof BankingWithdrawalResult.Aborted, "expected Aborted, got " + result);
                check(((BankingWithdrawalResult.Aborted) result).reason() == BankingWithdrawalAbortReason.INSUFFICIENT_CAPACITY,
                        "wrong abort reason: " + result);

                check(fake.cancelRequests.size() == 1, "cancel was not called after the pre-check rejected the withdrawal");
                check(fake.confirmRequests.isEmpty(), "confirm must never be called after a pre-check rejection");
                check(!hasAnyReceiptFor(player.serverLevel(), operationId),
                        "a receipt must never be written when the pre-check rejects the withdrawal");
                check(!containsItem(player.getInventory(), Items.DIAMOND),
                        "no diamond must have been conjured -- and critically, none silently destroyed either: "
                                + "production must never call Inventory#add after a false pre-check prediction, "
                                + "even for a creative/instabuild player");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    private static void assertSurvivalScenario(Inventory inventory, CapacityScenario scenario) {
        scenario.setup().accept(inventory);
        ItemStack candidate = scenario.candidate().get();
        boolean predicted = BankingWithdrawalProxyService.hasSufficientCapacity(inventory, candidate);
        ItemStack toInsert = candidate.copy();
        inventory.add(toInsert);
        boolean actuallyFullyInserted = toInsert.isEmpty();
        check(predicted == actuallyFullyInserted,
                "scenario " + scenario.number() + " (" + scenario.description() + "): pre-check predicted " + predicted
                        + " but the real Inventory#add outcome was " + actuallyFullyInserted);
    }

    private static void assertCreativeScenario(Inventory inventory, CapacityScenario scenario) {
        scenario.setup().accept(inventory);
        ItemStack candidate = scenario.candidate().get();
        boolean predicted = BankingWithdrawalProxyService.hasSufficientCapacity(inventory, candidate);
        int before = totalCountOf(inventory, candidate.getItem());
        ItemStack toInsert = candidate.copy();
        inventory.add(toInsert);
        int after = totalCountOf(inventory, candidate.getItem());
        int genuinelyPlaced = after - before;

        check(toInsert.isEmpty(),
                "scenario " + scenario.number() + " (" + scenario.description() + "): for a creative/instabuild player, "
                        + "Inventory#add must always report the stack fully consumed (genuinely placed, or "
                        + "vanilla's own silent-discard branch) -- got a nonzero leftover instead");

        if (predicted) {
            check(genuinelyPlaced == candidate.getCount(),
                    "scenario " + scenario.number() + " (" + scenario.description() + "): pre-check predicted sufficient "
                            + "capacity, so the item must be genuinely placed, not silently discarded -- expected "
                            + candidate.getCount() + " more in the inventory, got " + genuinelyPlaced);
        } else {
            // Not necessarily 0: when partial headroom exists (e.g. one slot has room for 9 of
            // the 10 requested), the ordinary mechanism genuinely places what it can before the
            // final, un-placeable remainder hits vanilla's silent-discard branch -- so a
            // creative player can end up with SOME of the item genuinely stored and the rest
            // destroyed, both reported through the same toInsert.isEmpty()==true signal. That is
            // the real finding here, and the stronger one: isEmpty() cannot be trusted to mean
            // "fully, genuinely placed" for a creative/instabuild player at all -- not merely "0
            // placed vs. all placed," but silently short-changed by any amount up to the full
            // requested count. The only universally correct assertion is that it is not fully
            // genuinely placed.
            check(genuinelyPlaced < candidate.getCount(),
                    "scenario " + scenario.number() + " (" + scenario.description() + "): pre-check predicted insufficient "
                            + "capacity, so Inventory#add must not have genuinely, fully placed the item -- expected fewer "
                            + "than " + candidate.getCount() + " genuinely placed, got " + genuinelyPlaced
                            + " (isEmpty() reported success anyway, demonstrating that signal is unreliable for creative "
                            + "players)");
        }
    }

    private static int totalCountOf(Inventory inventory, net.minecraft.world.item.Item item) {
        int total = 0;
        for (ItemStack slot : inventory.items) {
            if (slot.getItem() == item) total += slot.getCount();
        }
        ItemStack offhandStack = inventory.offhand.get(0);
        if (offhandStack.getItem() == item) total += offhandStack.getCount();
        return total;
    }

    private static ItemStack damagedPickaxe() {
        ItemStack stack = new ItemStack(Items.IRON_PICKAXE);
        stack.setDamageValue(1);
        return stack;
    }

    // ---------- Local rejection: decode failure ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void corruptPayloadIsAbortedWithNoReceiptAndCancelCalled(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        UUID operationId = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingWithdrawalPrepareResult.Success(
                operationId, bankItemId, BankItemSchemaVersion.CURRENT, new byte[]{1, 2, 3}, "deadbeef", 1.0));
        fake.cancelBehavior = () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        BankingWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingWithdrawalResult> future =
                    BankingWithdrawalProxyService.triggerWithdrawalForTesting(player, teller, bankItemId);

            helper.succeedWhen(() -> {
                check(future.isDone(), "withdrawal did not complete");
                BankingWithdrawalResult result = future.join();
                check(result instanceof BankingWithdrawalResult.Aborted, "expected Aborted, got " + result);
                check(((BankingWithdrawalResult.Aborted) result).reason() == BankingWithdrawalAbortReason.DECODE_FAILED,
                        "wrong abort reason: " + result);

                check(fake.cancelRequests.size() == 1, "cancel was not called after a decode failure");
                check(fake.confirmRequests.isEmpty(), "confirm must never be called after a decode failure");
                check(!hasAnyReceiptFor(player.serverLevel(), operationId), "a receipt must never be written on a decode failure");
                check(player.getInventory().isEmpty(), "no item must have been conjured on a decode failure");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Local rejection: fingerprint mismatch ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void fingerprintMismatchIsAbortedWithNoReceiptAndCancelCalled(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        HolderLookup.Provider registries = player.registryAccess();

        ItemStack original = new ItemStack(Items.DIAMOND, 5);
        byte[] payload = BankItemCodec.serialize(original.copy(), registries);

        UUID operationId = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingWithdrawalPrepareResult.Success(
                operationId, bankItemId, BankItemSchemaVersion.CURRENT, payload,
                "0000000000000000000000000000000000000000000000000000000000000000", 1.0));
        fake.cancelBehavior = () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        BankingWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingWithdrawalResult> future =
                    BankingWithdrawalProxyService.triggerWithdrawalForTesting(player, teller, bankItemId);

            helper.succeedWhen(() -> {
                check(future.isDone(), "withdrawal did not complete");
                BankingWithdrawalResult result = future.join();
                check(result instanceof BankingWithdrawalResult.Aborted, "expected Aborted, got " + result);
                check(((BankingWithdrawalResult.Aborted) result).reason() == BankingWithdrawalAbortReason.FINGERPRINT_MISMATCH,
                        "wrong abort reason: " + result);

                check(fake.cancelRequests.size() == 1, "cancel was not called after a fingerprint mismatch");
                check(fake.confirmRequests.isEmpty(), "confirm must never be called after a fingerprint mismatch");
                check(!hasAnyReceiptFor(player.serverLevel(), operationId),
                        "a receipt must never be written on a fingerprint mismatch");
                check(player.getInventory().isEmpty(), "no item must have been conjured on a fingerprint mismatch");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // Milestone 14 priority 2 (context enforcement, dimension 5): mirrors
    // fingerprintMismatchIsAbortedWithNoReceiptAndCancelCalled exactly, above, just with the
    // player walking out of range during the prepare round trip (a pending future, completed
    // only after teleporting away) rather than a synchronously-mismatched fingerprint -- both
    // go through the identical abortAfterPrepare/cancel-and-report path.
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void playerWalkingOutOfRangeBeforeInsertionIsAbortedWithNoReceiptAndCancelCalled(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);
        HolderLookup.Provider registries = player.registryAccess();

        ItemStack original = new ItemStack(Items.DIAMOND, 5);
        byte[] payload = BankItemCodec.serialize(original.copy(), registries);
        String fingerprint = BankItemFingerprint.fingerprint(original.copy(), registries);

        UUID operationId = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        CompletableFuture<BankingWithdrawalPrepareResult> pending = new CompletableFuture<>();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> pending;
        fake.cancelBehavior = () -> CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        BankingWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingWithdrawalResult> future =
                    BankingWithdrawalProxyService.triggerWithdrawalForTesting(player, teller, bankItemId);

            // The player walks far out of interaction range while prepare is still in flight.
            player.teleportTo(teller.getX() + 100.0, teller.getY(), teller.getZ());
            pending.complete(new BankingWithdrawalPrepareResult.Success(
                    operationId, bankItemId, BankItemSchemaVersion.CURRENT, payload, fingerprint, 1.0));

            helper.succeedWhen(() -> {
                check(future.isDone(), "withdrawal did not complete");
                BankingWithdrawalResult result = future.join();
                check(result instanceof BankingWithdrawalResult.Aborted, "expected Aborted, got " + result);
                check(((BankingWithdrawalResult.Aborted) result).reason() == BankingWithdrawalAbortReason.TELLER_NO_LONGER_VALID,
                        "wrong abort reason: " + result);

                check(fake.cancelRequests.size() == 1, "cancel was not called after the teller went out of range");
                check(fake.confirmRequests.isEmpty(), "confirm must never be called after the teller went out of range");
                check(!hasAnyReceiptFor(player.serverLevel(), operationId),
                        "a receipt must never be written once the teller is out of range");
                check(player.getInventory().isEmpty(), "no item must have been conjured once the teller is out of range");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    // ---------- Simulated crash: after insertion, before confirmation ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aReceiptSurvivesASimulatedCrashBetweenInsertionAndConfirmation(GameTestHelper helper) {
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
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingWithdrawalPrepareResult.Success(
                operationId, bankItemId, BankItemSchemaVersion.CURRENT, payload, fingerprint, weight));
        // confirmBehavior deliberately left throwing -- confirm must never be reached by this path.
        BankingWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingWithdrawalProxyService.PrepareAndInsertOutcome> future =
                    BankingWithdrawalProxyService.prepareAndInsertForTesting(player, teller, bankItemId);

            helper.succeedWhen(() -> {
                check(future.isDone(), "prepareAndInsert did not complete");
                BankingWithdrawalProxyService.PrepareAndInsertOutcome outcome = future.join();
                check(outcome instanceof BankingWithdrawalProxyService.PrepareAndInsertOutcome.Inserted,
                        "expected Inserted, got " + outcome);

                check(fake.confirmRequests.isEmpty(), "confirm must not have been called before the simulated crash");

                ItemStack inSlot = findFirstNonEmpty(player.getInventory());
                check(inSlot != null && inSlot.getItem() == Items.DIAMOND && inSlot.getCount() == 5,
                        "the item must genuinely be present in the player's inventory after insertion");

                com.seggellion.britannia_mod.bank.transfer.BankTransferReceipt survived =
                        findReceipt(player.serverLevel(), operationId);
                check(java.util.Arrays.equals(survived.itemPayload(), payload),
                        "the on-disk receipt payload did not match the payload Rails sent");
                check(survived.status() == com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStatus.PENDING_LOCAL_ACTION,
                        "confirm was never called, so the receipt must still be PENDING_LOCAL_ACTION, not escalated");

                check(BankingWithdrawalProxyService.isInFlightForTesting(bankItemId),
                        "a simulated crash must leave the bank item marked in-flight, matching real crash semantics");

                cleanUp();
            });
        } catch (RuntimeException | Error propagate) {
            cleanUp();
            throw propagate;
        }
    }

    /** Counterpart to the crash test above -- resumes confirm from exactly the state a real restart would leave behind. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aSurvivedReceiptIsResolvableByResumingConfirmAfterTheSimulatedRestart(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        UUID operationId = UUID.randomUUID();
        UUID bankItemId = UUID.randomUUID();
        FakeClient fake = new FakeClient();
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());
        BankingWithdrawalProxyService.useClientForTesting(fake);
        // No IN_FLIGHT entry is seeded -- a real restart's in-memory state is empty, exactly
        // like resetInFlightTrackingForTesting() would leave it.

        try {
            CompletableFuture<BankingWithdrawalResult> resumed =
                    BankingWithdrawalProxyService.confirmWithdrawalForTesting(player, operationId, bankItemId);

            helper.succeedWhen(() -> {
                check(resumed.isDone(), "resumed confirm did not complete");
                check(resumed.join() instanceof BankingWithdrawalResult.Confirmed,
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

    // ---------- RECONCILIATION_REQUIRED: Rails' own independent expiry escalation ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void reconciliationRequiredNeverReclaimsTheItemAndMarksTheReceiptReconciliationRequired(GameTestHelper helper) {
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
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> CompletableFuture.completedFuture(new BankingWithdrawalPrepareResult.Success(
                operationId, bankItemId, BankItemSchemaVersion.CURRENT, payload, fingerprint, weight));
        // Rails has already independently escalated this operation on its own timer
        // (Expire#escalate_withdrawal) by the time confirm's response lands here.
        fake.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.ReconciliationRequired());
        BankingWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingWithdrawalResult> future =
                    BankingWithdrawalProxyService.triggerWithdrawalForTesting(player, teller, bankItemId);

            helper.succeedWhen(() -> {
                check(future.isDone(), "withdrawal did not complete");
                BankingWithdrawalResult result = future.join();
                check(result instanceof BankingWithdrawalResult.ReconciliationRequired,
                        "expected the distinct ReconciliationRequired case, got " + result);
                check(((BankingWithdrawalResult.ReconciliationRequired) result).operationPublicId().equals(operationId),
                        "wrong operation id in ReconciliationRequired result");

                ItemStack inSlot = findFirstNonEmpty(player.getInventory());
                check(inSlot != null && inSlot.getItem() == Items.DIAMOND && inSlot.getCount() == 5,
                        "the item must never be reclaimed from the player on RECONCILIATION_REQUIRED -- "
                                + "it is already, physically, theirs");

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

    // ---------- Duplicate/concurrent attempts for the same bank item: IN_FLIGHT dedup ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aRepeatTriggerForTheSameBankItemWhileOneIsInFlightDoesNotDispatchASecondPrepare(GameTestHelper helper) {
        installBankRegistry();
        ServiceNpcEntity teller = spawnBankTeller(helper);
        ServerPlayer player = setUpPlayer(helper, teller);

        UUID bankItemId = UUID.randomUUID();
        AtomicInteger prepareDispatchCount = new AtomicInteger();
        CompletableFuture<BankingWithdrawalPrepareResult> pending = new CompletableFuture<>();
        FakeClient fake = new FakeClient();
        fake.prepareBehavior = () -> {
            prepareDispatchCount.incrementAndGet();
            return pending;
        };
        BankingWithdrawalProxyService.useClientForTesting(fake);

        try {
            CompletableFuture<BankingWithdrawalResult> first =
                    BankingWithdrawalProxyService.triggerWithdrawalForTesting(player, teller, bankItemId);
            CompletableFuture<BankingWithdrawalResult> second =
                    BankingWithdrawalProxyService.triggerWithdrawalForTesting(player, teller, bankItemId);

            check(prepareDispatchCount.get() == 1,
                    "a repeat trigger for the same bank item dispatched a second prepare call (count="
                            + prepareDispatchCount.get() + ")");
            check(second.isDone() && second.join() instanceof BankingWithdrawalResult.LocalFailure,
                    "the duplicate trigger did not report a local failure immediately");

            pending.complete(new BankingWithdrawalPrepareResult.Rejected(BankingTransferOutcome.ITEM_NOT_AVAILABLE, false));

            helper.succeedWhen(() -> {
                check(first.isDone(), "the original withdrawal did not complete");
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
        // makeMockServerPlayerInLevel() hardcodes isCreative() to true, which leaves
        // abilities.instabuild also true -- and Inventory#add's real source (traced, not
        // assumed) has a distinct creative-only branch: when normal placement makes zero
        // progress, a creative/instabuild player still gets the stack silently zeroed out
        // (treated as fully consumed) rather than left as a genuine leftover. A real bank
        // customer withdrawing an item is never in that state, so every withdrawal test in
        // this file forces survival semantics here to match real production conditions --
        // otherwise the capacity pre-check's own predictions could never be honestly
        // cross-checked against Inventory#add's real, non-creative behavior.
        player.getAbilities().instabuild = false;
        player.getInventory().clearContent();
        player.teleportTo(teller.getX() + 1.0, teller.getY(), teller.getZ());
        return player;
    }

    private static void cleanUp() {
        BankingWithdrawalProxyService.resetClientForTesting();
        BankingWithdrawalProxyService.resetInFlightTrackingForTesting();
        ServiceNpcRegistryCache.clear();
    }

    private static ItemStack findFirstNonEmpty(Inventory inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) return stack;
        }
        return null;
    }

    private static boolean containsItem(Inventory inventory, net.minecraft.world.item.Item item) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item)) return true;
        }
        return false;
    }

    private static void fillInventoryCompletely(Inventory inventory) {
        for (int i = 0; i < 36; i++) inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        inventory.offhand.set(0, new ItemStack(Items.COBBLESTONE, 64));
    }

    /**
     * Reads the receipt store directly off disk, bypassing the live in-memory instance -- same
     * technique {@code BankingDepositProxyServiceGameTests} and {@code BankTransferReceiptGameTests}
     * both use, duplicated here rather than imported (each GameTest file in this codebase is
     * self-contained by established convention).
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
     * Scoped to one operation id, not the store's total contents -- see {@code
     * BankingDepositProxyServiceGameTests#hasAnyReceiptFor}'s own docs for why an unscoped
     * assertion against this single, server-wide, concurrently-shared store is unsafe.
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
     * independently -- mirrors {@code BankingDepositProxyServiceGameTests.FakeClient}.
     */
    private static final class FakeClient implements BankingWithdrawalClientPort {
        java.util.function.Supplier<CompletableFuture<BankingWithdrawalPrepareResult>> prepareBehavior =
                () -> { throw new IllegalStateException("prepareWithdrawal() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingConfirmResult>> confirmBehavior =
                () -> { throw new IllegalStateException("confirm() was not expected to be called in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingCancelResult>> cancelBehavior =
                () -> { throw new IllegalStateException("cancel() was not expected to be called in this test"); };

        final List<BankingWithdrawalPrepareRequest> prepareRequests = new CopyOnWriteArrayList<>();
        final List<BankingOperationRequest> confirmRequests = new CopyOnWriteArrayList<>();
        final List<BankingOperationRequest> cancelRequests = new CopyOnWriteArrayList<>();

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
            cancelRequests.add(request);
            return cancelBehavior.get();
        }
    }
}
