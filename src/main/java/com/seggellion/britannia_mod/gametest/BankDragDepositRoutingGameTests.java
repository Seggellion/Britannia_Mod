package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.component.BankChequeData;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.network.payload.BankDepositRequestC2SPayload;
import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import com.seggellion.britannia_mod.service.banking.BankingCancelResult;
import com.seggellion.britannia_mod.service.banking.BankingChequeRedemptionClientPort;
import com.seggellion.britannia_mod.service.banking.BankingChequeRedemptionProxyService;
import com.seggellion.britannia_mod.service.banking.BankingChequeRedemptionRequest;
import com.seggellion.britannia_mod.service.banking.BankingChequeRedemptionResult;
import com.seggellion.britannia_mod.service.banking.BankingConfirmResult;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyDepositClientPort;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyDepositPrepareRequest;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyDepositPrepareResult;
import com.seggellion.britannia_mod.service.banking.BankingCurrencyDepositProxyService;
import com.seggellion.britannia_mod.service.banking.BankingDepositClientPort;
import com.seggellion.britannia_mod.service.banking.BankingDepositPrepareRequest;
import com.seggellion.britannia_mod.service.banking.BankingDepositPrepareResult;
import com.seggellion.britannia_mod.service.banking.BankingDepositProxyService;
import com.seggellion.britannia_mod.service.banking.BankingOperationRequest;
import com.seggellion.britannia_mod.service.banking.BankingProxyService;
import com.seggellion.britannia_mod.service.banking.BankingTransferOutcome;
import com.seggellion.britannia_mod.service.banking.BankingTransferPacketService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Milestone 14: the drag gesture changed only the UI; the routing did not move.
 *
 * <p>Every test here sends the exact packet a drag release sends -- {@code
 * BankDepositRequestC2SPayload(teller, slot)} through the real {@code
 * BankingTransferPacketService.handleDeposit} -- with all three protocol clients faked at once, so
 * each category can assert <b>exclusivity</b>: not just that the right protocol was engaged, but
 * that the other two were never touched. That second half is what a routing bug actually looks
 * like, and what the per-protocol suites cannot see from inside one protocol.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankDragDepositRoutingGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BANK_TYPE_KEY = "bank_teller";
    private static final int SLOT = 0;

    private BankDragDepositRoutingGameTests() {
    }

    // ---------- Category 1: ordinary bankable item ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void anOrdinaryItemEngagesTheItemProtocolAndNothingElse(GameTestHelper helper) {
        Rig rig = Rig.install(helper);
        rig.player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND, 5));
        rig.item.prepareBehavior = () -> CompletableFuture.completedFuture(
                new BankingDepositPrepareResult.Success(UUID.randomUUID(), UUID.randomUUID()));
        rig.item.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());

        rig.deposit(helper, () -> {
            check(rig.item.prepares.size() == 1, "the item protocol must be engaged exactly once");
            check(rig.currency.prepares.isEmpty(), "the currency protocol must never see an ordinary item");
            check(rig.cheque.redeems.isEmpty(), "redemption must never see an ordinary item");
            check(rig.refreshed.get(), "a confirmed deposit refreshes the account");
            check(rig.results.calls.isEmpty(), "a clean confirm sends no result payload -- the refresh is the signal");
        });
    }

    // ---------- Categories 2-4: each loose coin denomination ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aGoldStackRoutesToCurrencyWithTheGoldKey(GameTestHelper helper) {
        coinRoutes(helper, new ItemStack(ItemRegistry.GOLD_COIN.get(), 37), "gold", 37);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aSilverStackRoutesToCurrencyWithTheSilverKey(GameTestHelper helper) {
        coinRoutes(helper, new ItemStack(ItemRegistry.SILVER_COIN.get(), 99), "silver", 99);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aCopperStackRoutesToCurrencyWithTheCopperKey(GameTestHelper helper) {
        coinRoutes(helper, new ItemStack(ItemRegistry.COPPER_COIN.get(), 12), "copper", 12);
    }

    private static void coinRoutes(GameTestHelper helper, ItemStack coins, String expectedKey, int expectedAmount) {
        Rig rig = Rig.install(helper);
        rig.player.getInventory().setItem(SLOT, coins);
        rig.currency.prepareBehavior = () -> CompletableFuture.completedFuture(
                new BankingCurrencyDepositPrepareResult.Success(UUID.randomUUID()));
        rig.currency.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());

        rig.deposit(helper, () -> {
            check(rig.currency.prepares.size() == 1, "the currency protocol must be engaged exactly once");
            BankingCurrencyDepositPrepareRequest sent = rig.currency.prepares.get(0);
            check(expectedKey.equals(sent.currencyKey()), "wrong denomination key: " + sent.currencyKey());
            check(sent.amount() == expectedAmount, "wrong amount: " + sent.amount());
            check(rig.item.prepares.isEmpty(), "the item protocol must never see a bare coin stack");
            check(rig.cheque.redeems.isEmpty(), "redemption must never see a coin stack");
            check(rig.refreshed.get(), "a confirmed currency deposit refreshes the account");
        });
    }

    // ---------- Category 5: bank cheque ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aDepositedChequeIsStoredNeverAutoCashed(GameTestHelper helper) {
        // Milestone 17 gate corrective (owner override of ADR-016): this test asserted the exact
        // opposite until now. Dragging a cheque to the vault STORES it -- players keep cheques
        // in bank boxes -- and cashing is only ever the explicit double-click packet below.
        Rig rig = Rig.install(helper);
        rig.player.getInventory().setItem(SLOT, chequeStack(UUID.randomUUID()));
        rig.item.prepareBehavior = () -> CompletableFuture.completedFuture(
                new BankingDepositPrepareResult.Success(UUID.randomUUID(), UUID.randomUUID()));
        rig.item.confirmBehavior = () -> CompletableFuture.completedFuture(new BankingConfirmResult.Confirmed());

        rig.deposit(helper, () -> {
            check(rig.item.prepares.size() == 1, "a deposited cheque must be stored as an ordinary bank item");
            check(rig.cheque.redeems.isEmpty(), "a deposited cheque must NEVER be auto-cashed");
            check(rig.currency.prepares.isEmpty(), "a cheque is not a coin stack");
            check(rig.refreshed.get(), "a confirmed deposit refreshes the account");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aDoubleClickedChequeEntersRedemptionThroughItsOwnPacket(GameTestHelper helper) {
        Rig rig = Rig.install(helper);
        UUID chequeId = UUID.randomUUID();
        rig.player.getInventory().setItem(SLOT, chequeStack(chequeId));
        rig.cheque.redeemBehavior = () -> CompletableFuture.completedFuture(
                new BankingChequeRedemptionResult.Confirmed(chequeId));

        rig.redeem(helper, () -> {
            check(rig.cheque.redeems.size() == 1, "redemption must be engaged exactly once");
            check(chequeId.equals(rig.cheque.redeems.get(0).chequePublicId()), "the real cheque id must travel");
            check(rig.item.prepares.isEmpty(), "cashing must never store the cheque");
            check(rig.currency.prepares.isEmpty(), "a cheque is not a coin stack");
            check(rig.refreshed.get(), "a successful redemption refreshes the account");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void theRedemptionPacketAimedAtANonChequeRejectsCleanlyAndDisposesNothing(GameTestHelper helper) {
        // A modified client can aim the new packet anywhere; the proxy re-reads the live slot.
        Rig rig = Rig.install(helper);
        rig.player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND, 5));

        rig.redeem(helper, () -> {
            check(rig.cheque.redeems.isEmpty(), "no Rails call for a slot that is not a cheque");
            check(rig.item.prepares.isEmpty() && rig.currency.prepares.isEmpty(), "no other protocol touched");
            check(rig.results.sawExactly(BankTransferResultS2CPayload.Operation.CHEQUE_REDEMPTION,
                    BankTransferResultS2CPayload.Kind.CLEAN_REJECTION), "one clean rejection reaches the client");
            check(!rig.player.getInventory().getItem(SLOT).isEmpty(), "the diamond stays with the player");
        });
    }

    // ---------- Category 6: a container that merely contains coins ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aContainerOfCoinsNeverRoutesToCurrencyAndIsRejectedByTheItemPath(GameTestHelper helper) {
        Rig rig = Rig.install(helper);
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(
                new ItemStack(ItemRegistry.GOLD_COIN.get(), 64))));
        rig.player.getInventory().setItem(SLOT, shulker);

        rig.deposit(helper, () -> {
            // The whole of design §10.8's container rule in three assertions: top-level identity
            // routes it to the item path, whose CURRENCY carve-out rejects it locally -- so no
            // protocol's wire is ever touched and the box never becomes a balance.
            check(rig.currency.prepares.isEmpty(), "contents must never route the container to currency");
            check(rig.cheque.redeems.isEmpty(), "nor to redemption");
            check(rig.item.prepares.isEmpty(), "the item path rejects it locally, before any Rails call");
            // Milestone 17: the refusal keeps its identity -- INELIGIBLE_ITEM, not the generic
            // rejection. The routing claim above is unchanged.
            check(rig.results.sawExactly(BankTransferResultS2CPayload.Operation.DEPOSIT,
                    BankTransferResultS2CPayload.Kind.INELIGIBLE_ITEM), "one ineligible-item rejection reaches the client");
            check(!rig.player.getInventory().getItem(SLOT).isEmpty(), "the shulker stays with the player");
        });
    }

    // ---------- Category 7: ineligible item ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aQuestBoundItemIsRejectedWithoutTouchingAnyProtocol(GameTestHelper helper) {
        Rig rig = Rig.install(helper);
        ItemStack questBound = new ItemStack(Items.DIAMOND, 1);
        CompoundTag tag = new CompoundTag();
        tag.putString("quest_item", "a_real_quest_stamp");
        questBound.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        // Self-check the fixture: if the stamp key ever changes, fail here with a clear message
        // rather than passing vacuously against an eligible diamond.
        check(!com.seggellion.britannia_mod.client.screen.bank.BankDepositHint.isDepositable(questBound),
                "fixture error: this stack was supposed to be ineligible");
        rig.player.getInventory().setItem(SLOT, questBound);

        rig.deposit(helper, () -> {
            check(rig.item.prepares.isEmpty(), "rejected locally, before any Rails call");
            check(rig.currency.prepares.isEmpty() && rig.cheque.redeems.isEmpty(), "no other protocol touched");
            // Milestone 17: quest-bound is an eligibility refusal, so it reads INELIGIBLE_ITEM now.
            check(rig.results.sawExactly(BankTransferResultS2CPayload.Operation.DEPOSIT,
                    BankTransferResultS2CPayload.Kind.INELIGIBLE_ITEM), "one ineligible-item rejection reaches the client");
            check(!rig.player.getInventory().getItem(SLOT).isEmpty(), "the item stays with the player");
        });
    }

    // ---------- The cheque terminal states, each distinct ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void chequeNotFoundReachesTheClientAsItself(GameTestHelper helper) {
        chequeTerminalState(helper, BankingTransferOutcome.CHEQUE_NOT_FOUND,
                BankTransferResultS2CPayload.Kind.CHEQUE_NOT_FOUND);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void chequeAlreadyRedeemedReachesTheClientAsItself(GameTestHelper helper) {
        chequeTerminalState(helper, BankingTransferOutcome.CHEQUE_ALREADY_REDEEMED,
                BankTransferResultS2CPayload.Kind.CHEQUE_ALREADY_REDEEMED);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void chequeCancelledReachesTheClientAsItself(GameTestHelper helper) {
        chequeTerminalState(helper, BankingTransferOutcome.CHEQUE_CANCELLED,
                BankTransferResultS2CPayload.Kind.CHEQUE_CANCELLED);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void chequeVoidedReachesTheClientAsItself(GameTestHelper helper) {
        chequeTerminalState(helper, BankingTransferOutcome.CHEQUE_VOIDED,
                BankTransferResultS2CPayload.Kind.CHEQUE_VOIDED);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void chequeReconciliationRequiredKeepsItsSeverity(GameTestHelper helper) {
        Rig rig = Rig.install(helper);
        rig.player.getInventory().setItem(SLOT, chequeStack(UUID.randomUUID()));
        // Redemption is single-shot, so Rails' reconciliation outcome arrives as an ordinary
        // Rejected -- the shape this milestone's verification caught being softened to a clean
        // rejection before the mapping was fixed.
        rig.cheque.redeemBehavior = () -> CompletableFuture.completedFuture(
                new BankingChequeRedemptionResult.Rejected(BankingTransferOutcome.RECONCILIATION_REQUIRED, false));

        rig.redeem(helper, () -> check(
                rig.results.sawExactly(BankTransferResultS2CPayload.Operation.CHEQUE_REDEMPTION,
                        BankTransferResultS2CPayload.Kind.RECONCILIATION_REQUIRED),
                "reconciliation must never soften into an ordinary rejection: " + rig.results.calls));
    }

    private static void chequeTerminalState(
            GameTestHelper helper, BankingTransferOutcome railsOutcome, BankTransferResultS2CPayload.Kind expectedKind
    ) {
        Rig rig = Rig.install(helper);
        rig.player.getInventory().setItem(SLOT, chequeStack(UUID.randomUUID()));
        rig.cheque.redeemBehavior = () -> CompletableFuture.completedFuture(
                new BankingChequeRedemptionResult.Rejected(railsOutcome, false));

        rig.redeem(helper, () -> {
            check(rig.results.sawExactly(BankTransferResultS2CPayload.Operation.CHEQUE_REDEMPTION, expectedKind),
                    "expected " + expectedKind + ", got " + rig.results.calls);
            check(!rig.refreshed.get(), "a rejected redemption must not refresh as though something changed");
        });
    }

    // ---------- Cashing a cheque that is already in the vault ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aStoredChequeIsCashedByItsRowIdAndRefreshesTheAccount(GameTestHelper helper) {
        Rig rig = Rig.install(helper);
        UUID bankItemId = UUID.randomUUID();
        UUID chequeId = UUID.randomUUID();
        rig.storedCheque.behavior = () -> CompletableFuture.completedFuture(
                new BankingChequeRedemptionResult.Confirmed(chequeId));

        rig.redeemStored(helper, bankItemId, () -> {
            check(rig.storedCheque.requests.size() == 1, "the stored endpoint must be called exactly once");
            check(bankItemId.equals(rig.storedCheque.requests.get(0).bankItemPublicId()),
                    "the VAULT ROW's id must travel, never a grid position");
            check(rig.cheque.redeems.isEmpty(), "the pack-side endpoint must never be involved");
            check(rig.item.prepares.isEmpty() && rig.currency.prepares.isEmpty(), "no other protocol touched");
            check(rig.refreshed.get(), "a cashed cheque refreshes -- the row leaves the vault and the balance rises");
            check(rig.results.calls.isEmpty(), "a clean success sends no result payload; the refresh is the signal");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aStoredRowThatIsGoneReportsStoredItemUnavailable(GameTestHelper helper) {
        // Rails' answer when the row was already cashed, withdrawn, or never belonged to this
        // account -- the replay case, since stored redemption has no idempotency token.
        Rig rig = Rig.install(helper);
        rig.storedCheque.behavior = () -> CompletableFuture.completedFuture(
                new BankingChequeRedemptionResult.Rejected(BankingTransferOutcome.ITEM_NOT_FOUND, false));

        rig.redeemStored(helper, UUID.randomUUID(), () -> {
            check(rig.results.sawExactly(BankTransferResultS2CPayload.Operation.CHEQUE_REDEMPTION,
                    BankTransferResultS2CPayload.Kind.STORED_ITEM_UNAVAILABLE),
                    "expected STORED_ITEM_UNAVAILABLE, got " + rig.results.calls);
            check(!rig.refreshed.get(), "a refusal must not refresh");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aLegacyStoredChequeWithNoLinkReportsChequeNotFound(GameTestHelper helper) {
        // A cheque stored before the link existed. Rails cannot backfill it, so this is the
        // correct answer rather than a bug -- the player withdraws it and cashes it from the pack.
        Rig rig = Rig.install(helper);
        rig.storedCheque.behavior = () -> CompletableFuture.completedFuture(
                new BankingChequeRedemptionResult.Rejected(BankingTransferOutcome.CHEQUE_NOT_FOUND, false));

        rig.redeemStored(helper, UUID.randomUUID(), () -> check(
                rig.results.sawExactly(BankTransferResultS2CPayload.Operation.CHEQUE_REDEMPTION,
                        BankTransferResultS2CPayload.Kind.CHEQUE_NOT_FOUND),
                "expected CHEQUE_NOT_FOUND, got " + rig.results.calls));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aFullBalanceRefusesTheStoredCashingWithItsOwnKind(GameTestHelper helper) {
        // The ceiling guard that exists only on this endpoint (the pack-side one 503s instead).
        Rig rig = Rig.install(helper);
        rig.storedCheque.behavior = () -> CompletableFuture.completedFuture(
                new BankingChequeRedemptionResult.Rejected(BankingTransferOutcome.BALANCE_CAPACITY_EXCEEDED, false));

        rig.redeemStored(helper, UUID.randomUUID(), () -> check(
                rig.results.sawExactly(BankTransferResultS2CPayload.Operation.CHEQUE_REDEMPTION,
                        BankTransferResultS2CPayload.Kind.BALANCE_CAPACITY_EXCEEDED),
                "expected BALANCE_CAPACITY_EXCEEDED, got " + rig.results.calls));
    }

    // ---------- Rig ----------

    private static ItemStack chequeStack(UUID chequeId) {
        ItemStack cheque = new ItemStack(ItemRegistry.BANK_CHEQUE.get());
        cheque.set(DataComponentRegistry.BANK_CHEQUE_DATA.get(),
                new BankChequeData(chequeId, 500, "Britannia Bank", BankChequeData.DEFAULT_CURRENCY_KEY));
        return cheque;
    }

    /** All three protocols faked at once, plus the result and refresh observers, plus teardown. */
    private static final class Rig {
        final ServerPlayer player;
        final ServiceNpcEntity teller;
        final FakeItemClient item = new FakeItemClient();
        final FakeCurrencyClient currency = new FakeCurrencyClient();
        final FakeChequeClient cheque = new FakeChequeClient();
        final FakeStoredChequeClient storedCheque = new FakeStoredChequeClient();
        final FakeResultSender results = new FakeResultSender();
        final AtomicBoolean refreshed = new AtomicBoolean(false);

        private Rig(ServerPlayer player, ServiceNpcEntity teller) {
            this.player = player;
            this.teller = teller;
        }

        static Rig install(GameTestHelper helper) {
            ServiceNpcTypeDefinition bankTeller = new ServiceNpcTypeDefinition(
                    BANK_TYPE_KEY, "Bank Teller", "banker", "britannia_mod:service_npc",
                    "bank_teller_default", List.of("bank.open"), true, true, 1
            );
            ServiceNpcRegistryCache.replace(new ServiceNpcRegistrySnapshot(
                    1, 1, Map.of(), Map.of(bankTeller.key(), bankTeller), Map.of()));

            ServiceNpcEntity teller = helper.spawn(EntityRegistry.SERVICE_NPC.get(), new BlockPos(1, 1, 1));
            teller.setWorldNpcPublicId(UUID.randomUUID());
            teller.setServiceNpcTypeKey(BANK_TYPE_KEY);

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.getInventory().clearContent();
            player.teleportTo(teller.getX() + 1.0, teller.getY(), teller.getZ());

            Rig rig = new Rig(player, teller);
            BankingDepositProxyService.useClientForTesting(rig.item);
            BankingCurrencyDepositProxyService.useClientForTesting(rig.currency);
            BankingChequeRedemptionProxyService.useClientForTesting(rig.cheque);
            com.seggellion.britannia_mod.service.banking.BankingStoredChequeRedemptionProxyService
                    .useClientForTesting(rig.storedCheque);
            BankingTransferPacketService.useResultSenderForTesting(rig.results);
            // The refresh push re-runs bank.open's real fetch, so the open client is faked too --
            // otherwise a confirmed route stalls on a network call and "refreshed" never fires.
            BankingProxyService.useClientForTesting(new com.seggellion.britannia_mod.service.banking.BankingOpenClient(
                    server -> java.util.Optional.of(
                            com.seggellion.britannia_mod.server.auth.ServerCredentials.forGameTesting(
                                    java.net.URI.create("http://127.0.0.1"), UUID.randomUUID())),
                    (ignored, task) -> CompletableFuture.completedFuture(
                            new com.seggellion.britannia_mod.service.banking.BankingOpenClientResult.Success(
                                    new com.seggellion.britannia_mod.service.banking.BankingOpenAccount(
                                            UUID.randomUUID(), "global", null, 250, 0.0, 0, 0, 0, 2),
                                    List.of())),
                    (uri, max) -> null
            ));
            BankingProxyService.useAccountScreenSenderForTesting(
                    (p, t, account, bankItems) -> rig.refreshed.set(true));
            return rig;
        }

        /** Sends the drag's exact packet, then runs {@code assertions} under succeedWhen, then tears down. */
        void deposit(GameTestHelper helper, Runnable assertions) {
            try {
                BankingTransferPacketService.handleDeposit(
                        player, new BankDepositRequestC2SPayload(teller.getId(), SLOT));
                helper.succeedWhen(() -> {
                    assertions.run();
                    tearDown();
                });
            } catch (RuntimeException | Error propagate) {
                tearDown();
                throw propagate;
            }
        }

        /**
         * Milestone 17 gate corrective: sends the double-click's exact packet -- the explicit
         * redemption request that replaced the deposit packet's automatic cheque routing.
         */
        void redeem(GameTestHelper helper, Runnable assertions) {
            try {
                BankingTransferPacketService.handleChequeRedemption(
                        player, new com.seggellion.britannia_mod.network.payload.BankChequeRedemptionRequestC2SPayload(
                                teller.getId(), SLOT));
                helper.succeedWhen(() -> {
                    assertions.run();
                    tearDown();
                });
            } catch (RuntimeException | Error propagate) {
                tearDown();
                throw propagate;
            }
        }

        /** Sends the vault double-click's exact packet, naming the stored row by public id. */
        void redeemStored(GameTestHelper helper, UUID bankItemPublicId, Runnable assertions) {
            try {
                BankingTransferPacketService.handleStoredChequeRedemption(
                        player, new com.seggellion.britannia_mod.network.payload
                                .BankStoredChequeRedemptionRequestC2SPayload(teller.getId(), bankItemPublicId));
                helper.succeedWhen(() -> {
                    assertions.run();
                    tearDown();
                });
            } catch (RuntimeException | Error propagate) {
                tearDown();
                throw propagate;
            }
        }

        private void tearDown() {
            com.seggellion.britannia_mod.service.banking.BankingStoredChequeRedemptionProxyService
                    .resetClientForTesting();
            com.seggellion.britannia_mod.service.banking.BankingStoredChequeRedemptionProxyService
                    .resetInFlightTrackingForTesting();
            BankingDepositProxyService.resetClientForTesting();
            BankingDepositProxyService.resetInFlightTrackingForTesting();
            BankingCurrencyDepositProxyService.resetClientForTesting();
            BankingCurrencyDepositProxyService.resetInFlightTrackingForTesting();
            BankingChequeRedemptionProxyService.resetClientForTesting();
            BankingChequeRedemptionProxyService.resetInFlightTrackingForTesting();
            BankingTransferPacketService.resetResultSenderForTesting();
            BankingProxyService.resetClientForTesting();
            BankingProxyService.resetAccountScreenSenderForTesting();
            BankingProxyService.resetInFlightTrackingForTesting();
            ServiceNpcRegistryCache.clear();
        }
    }

    private static final class FakeResultSender implements BankingTransferPacketService.ResultSender {
        final List<BankTransferResultS2CPayload> calls = new CopyOnWriteArrayList<>();

        @Override
        public void send(ServerPlayer player, BankTransferResultS2CPayload.Operation operation,
                         BankTransferResultS2CPayload.Kind kind) {
            calls.add(new BankTransferResultS2CPayload(operation, kind));
        }

        boolean sawExactly(BankTransferResultS2CPayload.Operation operation, BankTransferResultS2CPayload.Kind kind) {
            return calls.size() == 1
                    && calls.get(0).operation() == operation
                    && calls.get(0).kind() == kind;
        }
    }

    private static final class FakeItemClient implements BankingDepositClientPort {
        java.util.function.Supplier<CompletableFuture<BankingDepositPrepareResult>> prepareBehavior =
                () -> { throw new IllegalStateException("the item protocol was not expected in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingConfirmResult>> confirmBehavior =
                () -> { throw new IllegalStateException("item confirm was not expected in this test"); };
        final List<BankingDepositPrepareRequest> prepares = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<BankingDepositPrepareResult> prepare(MinecraftServer server, BankingDepositPrepareRequest request) {
            prepares.add(request);
            return prepareBehavior.get();
        }

        @Override
        public CompletableFuture<BankingConfirmResult> confirm(MinecraftServer server, BankingOperationRequest request) {
            return confirmBehavior.get();
        }

        @Override
        public CompletableFuture<BankingCancelResult> cancel(MinecraftServer server, BankingOperationRequest request) {
            return CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        }
    }

    private static final class FakeCurrencyClient implements BankingCurrencyDepositClientPort {
        java.util.function.Supplier<CompletableFuture<BankingCurrencyDepositPrepareResult>> prepareBehavior =
                () -> { throw new IllegalStateException("the currency protocol was not expected in this test"); };
        java.util.function.Supplier<CompletableFuture<BankingConfirmResult>> confirmBehavior =
                () -> { throw new IllegalStateException("currency confirm was not expected in this test"); };
        final List<BankingCurrencyDepositPrepareRequest> prepares = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<BankingCurrencyDepositPrepareResult> prepareCurrencyDeposit(
                MinecraftServer server, BankingCurrencyDepositPrepareRequest request) {
            prepares.add(request);
            return prepareBehavior.get();
        }

        @Override
        public CompletableFuture<BankingConfirmResult> confirm(MinecraftServer server, BankingOperationRequest request) {
            return confirmBehavior.get();
        }

        @Override
        public CompletableFuture<BankingCancelResult> cancel(MinecraftServer server, BankingOperationRequest request) {
            return CompletableFuture.completedFuture(new BankingCancelResult.Cancelled());
        }
    }

    private static final class FakeChequeClient implements BankingChequeRedemptionClientPort {
        java.util.function.Supplier<CompletableFuture<BankingChequeRedemptionResult>> redeemBehavior =
                () -> { throw new IllegalStateException("redemption was not expected in this test"); };
        final List<BankingChequeRedemptionRequest> redeems = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<BankingChequeRedemptionResult> redeem(
                MinecraftServer server, BankingChequeRedemptionRequest request) {
            redeems.add(request);
            return redeemBehavior.get();
        }
    }

    private static final class FakeStoredChequeClient
            implements com.seggellion.britannia_mod.service.banking.BankingStoredChequeRedemptionClientPort {
        java.util.function.Supplier<CompletableFuture<BankingChequeRedemptionResult>> behavior =
                () -> { throw new IllegalStateException("stored redemption was not expected in this test"); };
        final List<com.seggellion.britannia_mod.service.banking.BankingStoredChequeRedemptionRequest> requests =
                new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<BankingChequeRedemptionResult> redeemStored(
                MinecraftServer server,
                com.seggellion.britannia_mod.service.banking.BankingStoredChequeRedemptionRequest request) {
            requests.add(request);
            return behavior.get();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
