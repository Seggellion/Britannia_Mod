package com.seggellion.britannia_mod.service.banking;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.bank.currency.CurrencyItemRegistry;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.network.payload.BankChequeIssuanceRequestC2SPayload;
import com.seggellion.britannia_mod.network.payload.BankChequeRedemptionRequestC2SPayload;
import com.seggellion.britannia_mod.network.payload.BankCurrencyWithdrawalRequestC2SPayload;
import com.seggellion.britannia_mod.network.payload.BankDepositAllCoinsRequestC2SPayload;
import com.seggellion.britannia_mod.network.payload.BankDepositRequestC2SPayload;
import com.seggellion.britannia_mod.network.payload.BankStoredChequeRedemptionRequestC2SPayload;
import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload;
import com.seggellion.britannia_mod.network.payload.BankWithdrawalRequestC2SPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Milestone 9 NeoForge Slice 3a: the one and only production entry point into {@link
 * BankingDepositProxyService#triggerDeposit} and {@link
 * BankingWithdrawalProxyService#triggerWithdrawal} -- reached from {@link
 * BankDepositRequestC2SPayload}/{@link BankWithdrawalRequestC2SPayload}, registered in {@code
 * NetworkHandler}. Structurally mirrors {@link BankingProxyService#handle}: the teller is
 * re-resolved fresh from the live world by entity id (never trusted from anything captured
 * earlier), exactly the same {@link BankingProxyService#resolve} fresh-revalidation this
 * codebase already uses for {@code bank.open} itself -- a stale, dead, moved, or
 * capability-revoked teller silently drops the request the same way an invalid {@code
 * bank.open} interaction already does, rather than inventing a new, differently-shaped failure
 * mode for this one packet type.
 *
 * <p>The client-supplied {@code entityId} is only ever a selection reference ("the player says
 * this is the teller they're talking to"), never trusted as fact -- {@link
 * BankingProxyService#resolve} independently re-checks liveness, distance, and {@code
 * bank.open} capability support every time, precisely the same trust model {@code
 * SellItemsC2SPayload}'s handling already established for trader entity ids.
 */
public final class BankingTransferPacketService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BankingTransferPacketService() {
    }

    /**
     * Test-only seam mirroring {@link BankingProxyService.AccountScreenSender} exactly: lets a
     * GameTest observe precisely which clean-rejection/reconciliation-required result would
     * have been sent, without needing a real client connection to capture the packet.
     */
    @FunctionalInterface
    public interface ResultSender {
        void send(ServerPlayer player, BankTransferResultS2CPayload.Operation operation, BankTransferResultS2CPayload.Kind kind);
    }

    private static ResultSender resultSender = BankTransferResultS2CPayload::send;

    public static void useResultSenderForTesting(ResultSender sender) {
        resultSender = sender;
    }

    public static void resetResultSenderForTesting() {
        resultSender = BankTransferResultS2CPayload::send;
    }

    /**
     * Milestone 11 Slice 2, amended by the Milestone 17 gate corrective: one deposit packet,
     * TWO protocols -- the routing decision is made HERE, server-side, from the live slot's own
     * contents, never from anything the client claimed. A bare coin stack ({@link
     * CurrencyItemRegistry#isCurrencyStack}: top-level item identity only -- a container holding
     * coins is not a coin stack) routes to the currency balance protocol; everything else,
     * <b>cheques now included</b>, takes the Milestone 9 item path. Cheques routed to redemption
     * here originally (ADR-016); the owner overrode that at the Milestone 17 gate so players can
     * store cheques in the Bank Box -- cashing arrives via {@link
     * BankChequeRedemptionRequestC2SPayload} instead, an explicit gesture. The item path's own
     * {@code CURRENCY} eligibility rejection is deliberately untouched underneath: if a coin
     * stack ever reached it directly (it cannot through this router), it still rejects cleanly
     * -- the redirect lives in this dispatch layer, not in a weakened eligibility rule.
     *
     * <p>The slot is read here purely to pick a protocol; whichever proxy service receives the
     * dispatch re-reads and re-validates the slot from scratch as its own step 1, so a swap
     * between this check and the proxy's capture resolves exactly like any other mid-flight
     * slot mutation: a clean local rejection or revalidation cancel, never a misrouted removal.
     */
    public static void handleDeposit(ServerPlayer player, BankDepositRequestC2SPayload payload) {
        ServiceNpcEntity teller = resolveTeller(player, payload.entityId());
        if (teller == null) return;

        ItemStack liveSlot = player.getInventory().getItem(payload.slotIndex());
        if (CurrencyItemRegistry.isCurrencyStack(liveSlot)) {
            handleCurrencyDeposit(player, teller, payload.slotIndex());
            return;
        }
        // Milestone 17 gate corrective (owner override of ADR-016): a deposited cheque is
        // STORED like any other item, never auto-cashed. Cashing is its own deliberate gesture
        // now, arriving via BankChequeRedemptionRequestC2SPayload -- intent travels in the
        // packet, because it can no longer be inferred from the slot's contents.

        MinecraftServer server = player.server;
        BankingDepositProxyService.triggerDeposit(player, teller, payload.slotIndex())
                .whenComplete((result, error) -> server.execute(() -> {
                    if (error != null || result == null) {
                        LOGGER.warn("banking deposit trigger for {} completed exceptionally", player.getStringUUID(), error);
                        resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.DEPOSIT,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                        return;
                    }
                    switch (result) {
                        case BankingDepositResult.Confirmed ignored -> refreshAccount(player, teller);
                        case BankingDepositResult.ReconciliationRequired ignored -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.DEPOSIT,
                                BankTransferResultS2CPayload.Kind.RECONCILIATION_REQUIRED
                        );
                        // Milestone 17: the two deposit refusals with a real answer keep their
                        // identity. An eligibility rejection means "the bank will not keep that",
                        // whatever the specific rule -- naming the rule (quest-bound, nested
                        // currency, foreign origin) would leak mechanism without adding action.
                        // EMPTY_SLOT is a stale view, not a verdict about an item, so it stays
                        // generic.
                        case BankingDepositResult.RejectedLocally rejectedLocally -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.DEPOSIT,
                                rejectedLocally.reason() == BankingDepositLocalRejectionReason.EMPTY_SLOT
                                        ? BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                                        : BankTransferResultS2CPayload.Kind.INELIGIBLE_ITEM
                        );
                        case BankingDepositResult.Rejected rejected -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.DEPOSIT,
                                rejected.outcome() == BankingTransferOutcome.CAPACITY_EXCEEDED
                                        ? BankTransferResultS2CPayload.Kind.BANK_CAPACITY_EXCEEDED
                                        : BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                        default -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.DEPOSIT,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                    }
                }));
    }

    /**
     * The currency side of {@link #handleDeposit}'s routing, mapped to the client identically
     * to an item deposit (same {@code Operation.DEPOSIT} result channel, same {@code
     * refreshAccount} on a clean confirm -- which re-runs {@code bank.open}'s real fetch, so the
     * freshly-mutated gold/silver/copper balances land on the client through the exact same
     * path every balance display already uses).
     */
    private static void handleCurrencyDeposit(ServerPlayer player, ServiceNpcEntity teller, int slotIndex) {
        MinecraftServer server = player.server;
        BankingCurrencyDepositProxyService.triggerCurrencyDeposit(player, teller, slotIndex)
                .whenComplete((result, error) -> server.execute(() -> {
                    if (error != null || result == null) {
                        LOGGER.warn("banking currency deposit trigger for {} completed exceptionally", player.getStringUUID(), error);
                        resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.DEPOSIT,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                        return;
                    }
                    switch (result) {
                        case BankingCurrencyDepositResult.Confirmed ignored -> refreshAccount(player, teller);
                        case BankingCurrencyDepositResult.ReconciliationRequired ignored -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.DEPOSIT,
                                BankTransferResultS2CPayload.Kind.RECONCILIATION_REQUIRED
                        );
                        default -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.DEPOSIT,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                    }
                }));
    }

    /**
     * Milestone 17 gate corrective: cheque redemption's packet entry point. Since the owner's
     * ADR-016 override, this is the ONLY road to cashing a cheque -- the deposit packet stores
     * cheques like any item. The proxy re-reads the live slot and rejects locally if it is not
     * actually a cheque, so a modified client aiming this at a diamond gets a clean rejection.
     */
    public static void handleChequeRedemption(ServerPlayer player, BankChequeRedemptionRequestC2SPayload payload) {
        ServiceNpcEntity teller = resolveTeller(player, payload.entityId());
        if (teller == null) return;
        handleChequeRedemption(player, teller, payload.slotIndex());
    }

    /**
     * Milestone 11 NeoForge Slice 2: bank cheque redemption's production flow. Originally the
     * cheque side of {@link #handleDeposit}'s routing (ADR-016); since the Milestone 17 gate
     * corrective it is reached only through the explicit redemption packet above. Unlike every
     * other dispatch method here, a {@code Rejected} result is not folded uniformly into
     * {@code CLEAN_REJECTION} -- Codex Prompt 11's own "clearly render
     * invalid/redeemed/cancelled outcomes" requirement means the four cheque-specific outcomes
     * each map to their own distinct {@link BankTransferResultS2CPayload.Kind}, so the client
     * can show a specific, honest message rather than one generic "something went wrong" line
     * for what are, in practice, the most common outcomes this endpoint actually returns.
     */
    public static void handleChequeRedemption(ServerPlayer player, ServiceNpcEntity teller, int slotIndex) {
        MinecraftServer server = player.server;
        BankingChequeRedemptionProxyService.triggerChequeRedemption(player, teller, slotIndex)
                .whenComplete((result, error) -> server.execute(() -> {
                    if (error != null || result == null) {
                        LOGGER.warn("banking cheque redemption trigger for {} completed exceptionally", player.getStringUUID(), error);
                        resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.CHEQUE_REDEMPTION,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                        return;
                    }
                    switch (result) {
                        case BankingChequeRedemptionResult.Confirmed ignored -> refreshAccount(player, teller);
                        case BankingChequeRedemptionResult.Rejected rejected -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.CHEQUE_REDEMPTION, chequeRedemptionKindFor(rejected.outcome())
                        );
                        default -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.CHEQUE_REDEMPTION,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                    }
                }));
    }

    /**
     * Cashing a cheque already stored in the vault -- the third of the three cheque paths
     * (docs/banking_bank_cheque_stored_redemption.md), reported on the same {@code
     * CHEQUE_REDEMPTION} channel as the pack-side one because to a player it is the same event.
     *
     * <p>Reuses {@link #chequeRedemptionKindFor} wholesale: Rails' own doc states this endpoint
     * introduces no new outcome string, and the two outcomes it adds to that path's vocabulary --
     * {@code ITEM_NOT_FOUND} (the row is gone or was never this account's) and {@code
     * BALANCE_CAPACITY_EXCEEDED} -- already have their own named kinds from Milestones 17 and 6b.
     */
    public static void handleStoredChequeRedemption(
            ServerPlayer player, BankStoredChequeRedemptionRequestC2SPayload payload
    ) {
        ServiceNpcEntity teller = resolveTeller(player, payload.entityId());
        if (teller == null) return;

        MinecraftServer server = player.server;
        BankingStoredChequeRedemptionProxyService
                .triggerStoredChequeRedemption(player, teller, payload.bankItemPublicId())
                .whenComplete((result, error) -> server.execute(() -> {
                    if (error != null || result == null) {
                        LOGGER.warn("banking stored cheque redemption for {} completed exceptionally",
                                player.getStringUUID(), error);
                        resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.CHEQUE_REDEMPTION,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                        return;
                    }
                    switch (result) {
                        case BankingChequeRedemptionResult.Confirmed ignored -> refreshAccount(player, teller);
                        case BankingChequeRedemptionResult.Rejected rejected -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.CHEQUE_REDEMPTION,
                                chequeRedemptionKindFor(rejected.outcome())
                        );
                        default -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.CHEQUE_REDEMPTION,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                    }
                }));
    }

    private static BankTransferResultS2CPayload.Kind chequeRedemptionKindFor(BankingTransferOutcome outcome) {
        return switch (outcome) {
            case CHEQUE_NOT_FOUND -> BankTransferResultS2CPayload.Kind.CHEQUE_NOT_FOUND;
            case CHEQUE_ALREADY_REDEEMED -> BankTransferResultS2CPayload.Kind.CHEQUE_ALREADY_REDEEMED;
            case CHEQUE_CANCELLED -> BankTransferResultS2CPayload.Kind.CHEQUE_CANCELLED;
            case CHEQUE_VOIDED -> BankTransferResultS2CPayload.Kind.CHEQUE_VOIDED;
            // Milestone 14's verification found this falling into the default below. Redemption
            // has no separate confirm, so Rails' reconciliation outcome arrives as an ordinary
            // Rejected -- but by this point the physical cheque is already disposed, so softening
            // it to "I can't complete that right now" tells a player to shrug at the one outcome
            // that needs staff. It keeps its severity.
            case RECONCILIATION_REQUIRED -> BankTransferResultS2CPayload.Kind.RECONCILIATION_REQUIRED;
            // The two outcomes stored redemption adds to this path's vocabulary. Neither is
            // reachable from the pack-side endpoint (it names a cheque, not a vault row, and has
            // no ceiling guard), so both arrive only from redeem_stored -- and both already have
            // a named kind that says the right thing: the row is gone, or the balance is full.
            case ITEM_NOT_FOUND, ITEM_NOT_AVAILABLE -> BankTransferResultS2CPayload.Kind.STORED_ITEM_UNAVAILABLE;
            case BALANCE_CAPACITY_EXCEEDED -> BankTransferResultS2CPayload.Kind.BALANCE_CAPACITY_EXCEEDED;
            // Every shared teller/account outcome (PLAYER_NOT_FOUND, TELLER_NOT_ASSIGNED, ...)
            // falls through here -- see BankingChequeRedemptionProxyService's own docs for why
            // these are deliberately NOT rendered as one of the four cheque-specific messages
            // above (they assert nothing about the cheque's own validity).
            default -> BankTransferResultS2CPayload.Kind.CLEAN_REJECTION;
        };
    }

    public static void handleWithdrawal(ServerPlayer player, BankWithdrawalRequestC2SPayload payload) {
        ServiceNpcEntity teller = resolveTeller(player, payload.entityId());
        if (teller == null) return;

        MinecraftServer server = player.server;
        BankingWithdrawalProxyService.triggerWithdrawal(player, teller, payload.bankItemPublicId())
                .whenComplete((result, error) -> server.execute(() -> {
                    if (error != null || result == null) {
                        LOGGER.warn("banking withdrawal trigger for {} completed exceptionally", player.getStringUUID(), error);
                        resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.WITHDRAWAL,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                        return;
                    }
                    switch (result) {
                        case BankingWithdrawalResult.Confirmed ignored -> refreshAccount(player, teller);
                        case BankingWithdrawalResult.ReconciliationRequired ignored -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.WITHDRAWAL,
                                BankTransferResultS2CPayload.Kind.RECONCILIATION_REQUIRED
                        );
                        // Milestone 15: an insufficient-capacity abort gets its own kind. The
                        // server side of this was always right -- pre-check before any receipt,
                        // Rails reservation cancelled, nothing created or lost -- but it reported
                        // as the generic rejection, indistinguishable from a dead teller (the
                        // Milestone 0 §3.4 finding). The other abort reasons stay generic: a
                        // decode failure or fingerprint mismatch asserts nothing the player can
                        // act on.
                        case BankingWithdrawalResult.Aborted aborted -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.WITHDRAWAL,
                                aborted.reason() == BankingWithdrawalAbortReason.INSUFFICIENT_CAPACITY
                                        ? BankTransferResultS2CPayload.Kind.INVENTORY_FULL
                                        : BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                        // Milestone 17: an item already gone (withdrawn seconds ago by another
                        // client on the same account -- the Milestone 18 concurrency case) says
                        // so, instead of reading like the teller refused the player. Every other
                        // Rails outcome stays generic.
                        case BankingWithdrawalResult.Rejected rejected -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.WITHDRAWAL,
                                rejected.outcome() == BankingTransferOutcome.ITEM_NOT_FOUND
                                        || rejected.outcome() == BankingTransferOutcome.ITEM_NOT_AVAILABLE
                                        ? BankTransferResultS2CPayload.Kind.STORED_ITEM_UNAVAILABLE
                                        : BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                        default -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.WITHDRAWAL,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                    }
                }));
    }

    /**
     * Milestone 10 Slice 2: currency withdrawal's own production entry point, mirroring {@link
     * #handleWithdrawal}'s shape exactly -- the same {@code Operation.WITHDRAWAL} result
     * channel, the same {@code refreshAccount} on a clean confirm. Unlike deposit's routing
     * (which decides currency-vs-item from the live slot's own contents), there is no ambiguity
     * to resolve here: this packet only ever means a currency withdrawal (an item withdrawal
     * always arrives via {@link BankWithdrawalRequestC2SPayload} instead), so there is no
     * routing decision to make -- straight to {@link
     * BankingCurrencyWithdrawalProxyService#triggerCurrencyWithdrawal}.
     */
    public static void handleCurrencyWithdrawal(ServerPlayer player, BankCurrencyWithdrawalRequestC2SPayload payload) {
        ServiceNpcEntity teller = resolveTeller(player, payload.entityId());
        if (teller == null) return;

        MinecraftServer server = player.server;
        BankingCurrencyWithdrawalProxyService.triggerCurrencyWithdrawal(player, teller, payload.currencyKey(), payload.amount())
                .whenComplete((result, error) -> server.execute(() -> {
                    if (error != null || result == null) {
                        LOGGER.warn("banking currency withdrawal trigger for {} completed exceptionally", player.getStringUUID(), error);
                        resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.WITHDRAWAL,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                        return;
                    }
                    switch (result) {
                        case BankingCurrencyWithdrawalResult.Confirmed ignored -> refreshAccount(player, teller);
                        case BankingCurrencyWithdrawalResult.ReconciliationRequired ignored -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.WITHDRAWAL,
                                BankTransferResultS2CPayload.Kind.RECONCILIATION_REQUIRED
                        );
                        // Milestone 16: the two refusals a player can act on keep their identity,
                        // exactly as Milestone 15 did for the item path. Everything else stays
                        // generic -- it asserts nothing actionable.
                        case BankingCurrencyWithdrawalResult.Rejected rejected -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.WITHDRAWAL,
                                rejected.outcome() == BankingTransferOutcome.INSUFFICIENT_BALANCE
                                        ? BankTransferResultS2CPayload.Kind.INSUFFICIENT_BALANCE
                                        : BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                        case BankingCurrencyWithdrawalResult.Aborted aborted -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.WITHDRAWAL,
                                aborted.reason() == BankingCurrencyWithdrawalAbortReason.INSUFFICIENT_CAPACITY
                                        ? BankTransferResultS2CPayload.Kind.INVENTORY_FULL
                                        : BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                        // A full pack is normally caught by the pre-prepare local check, not the
                        // post-prepare abort; both are the same fact and must read the same.
                        case BankingCurrencyWithdrawalResult.RejectedLocally rejectedLocally -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.WITHDRAWAL,
                                rejectedLocally.reason() == BankingCurrencyWithdrawalLocalRejectionReason.INSUFFICIENT_CAPACITY
                                        ? BankTransferResultS2CPayload.Kind.INVENTORY_FULL
                                        : BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                        default -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.WITHDRAWAL,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                    }
                }));
    }

    /**
     * Bank interface rebuild, Milestone 6b: Deposit All Coins' production entry point.
     *
     * <p>The packet carries only a teller entity id, so unlike {@link #handleDeposit} there is no
     * routing decision to make and nothing client-supplied to distrust beyond the teller
     * reference {@link #resolveTeller} already re-validates. The sweep itself happens inside
     * {@link BankingDepositAllCoinsProxyService}, against the live inventory.
     *
     * <p>Reported on the {@code Operation.DEPOSIT} channel, like the single-stack currency
     * deposit it generalises. A {@code NO_COINS} sweep is a real, readable outcome rather than a
     * silent no-op -- Milestone 6b's client half gives it its own message, because "nothing
     * happened" and "the teller refused you" must not look alike.
     */
    public static void handleDepositAllCoins(ServerPlayer player, BankDepositAllCoinsRequestC2SPayload payload) {
        ServiceNpcEntity teller = resolveTeller(player, payload.entityId());
        if (teller == null) return;

        MinecraftServer server = player.server;
        BankingDepositAllCoinsProxyService.triggerDepositAllCoins(player, teller)
                .whenComplete((result, error) -> server.execute(() -> {
                    if (error != null || result == null) {
                        LOGGER.warn("banking deposit-all-coins trigger for {} completed exceptionally", player.getStringUUID(), error);
                        resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.DEPOSIT,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                        return;
                    }
                    switch (result) {
                        case BankingDepositAllCoinsResult.Confirmed ignored -> refreshAccount(player, teller);
                        case BankingDepositAllCoinsResult.ReconciliationRequired ignored -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.DEPOSIT,
                                BankTransferResultS2CPayload.Kind.RECONCILIATION_REQUIRED
                        );
                        // An empty purse, caught locally before any network call.
                        case BankingDepositAllCoinsResult.RejectedLocally ignored -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.DEPOSIT,
                                BankTransferResultS2CPayload.Kind.NOTHING_TO_DEPOSIT
                        );
                        // Rails' own answer to the same two questions. NO_COINS reaches here only
                        // from a client that skipped the local check; the balance ceiling is
                        // genuinely only knowable server-side.
                        case BankingDepositAllCoinsResult.Rejected rejected -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.DEPOSIT,
                                depositAllCoinsKindFor(rejected.outcome())
                        );
                        default -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.DEPOSIT,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                    }
                }));
    }

    private static BankTransferResultS2CPayload.Kind depositAllCoinsKindFor(BankingTransferOutcome outcome) {
        return switch (outcome) {
            case NO_COINS -> BankTransferResultS2CPayload.Kind.NOTHING_TO_DEPOSIT;
            case BALANCE_CAPACITY_EXCEEDED -> BankTransferResultS2CPayload.Kind.BALANCE_CAPACITY_EXCEEDED;
            // Every shared teller/account outcome falls through, for the same reason cheque
            // redemption's mapping does: they assert nothing about the sweep itself.
            default -> BankTransferResultS2CPayload.Kind.CLEAN_REJECTION;
        };
    }

    /**
     * Milestone 11 NeoForge Slice 1: bank cheque issuance's own production entry point. Unlike
     * every prior confirm/reject dichotomy, this flow has a real third outcome -- {@link
     * BankingChequeIssuanceResult.PendingDelivery} -- reported as its own distinct {@link
     * BankTransferResultS2CPayload.Kind#PENDING_DELIVERY}, never folded into {@code
     * CLEAN_REJECTION} (nothing was actually rejected -- Rails already confirmed) or {@code
     * RECONCILIATION_REQUIRED} (this state auto-resolves on retry; that one never does). See
     * {@link BankingChequeIssuanceResult}'s own docs for the full reasoning.
     */
    public static void handleChequeIssuance(ServerPlayer player, BankChequeIssuanceRequestC2SPayload payload) {
        ServiceNpcEntity teller = resolveTeller(player, payload.entityId());
        if (teller == null) return;

        MinecraftServer server = player.server;
        BankingChequeIssuanceProxyService.triggerChequeIssuance(player, teller, payload.amount(), payload.currencyKey())
                .whenComplete((result, error) -> server.execute(() -> {
                    if (error != null || result == null) {
                        LOGGER.warn("banking cheque issuance trigger for {} completed exceptionally", player.getStringUUID(), error);
                        resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.CHEQUE_ISSUANCE,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                        return;
                    }
                    switch (result) {
                        case BankingChequeIssuanceResult.Confirmed ignored -> refreshAccount(player, teller);
                        case BankingChequeIssuanceResult.PendingDelivery ignored -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.CHEQUE_ISSUANCE,
                                BankTransferResultS2CPayload.Kind.PENDING_DELIVERY
                        );
                        case BankingChequeIssuanceResult.ReconciliationRequired ignored -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.CHEQUE_ISSUANCE,
                                BankTransferResultS2CPayload.Kind.RECONCILIATION_REQUIRED
                        );
                        default -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.CHEQUE_ISSUANCE,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                    }
                }));
    }

    /**
     * Same fresh, from-scratch teller resolution {@code bank.open} itself uses -- see {@link
     * BankingProxyService#resolve}'s own docs for exactly what "fresh" means here (never trusts
     * anything captured earlier). {@code entityId} is resolved to a live {@link Entity} first,
     * purely to translate the client's transient network id into an object {@code resolve} can
     * validate; {@code resolve} itself is what actually decides whether this request proceeds.
     */
    private static ServiceNpcEntity resolveTeller(ServerPlayer player, int entityId) {
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof ServiceNpcEntity candidate)) return null;
        BankingProxyService.ResolvedTeller resolved = BankingProxyService.resolve(player, candidate);
        return resolved == null ? null : candidate;
    }

    /**
     * On a clean confirm, re-runs {@code bank.open}'s own real fetch-and-send flow rather than
     * inventing a second, parallel "refresh" mechanism -- the freshly re-fetched account and
     * bank_items list this pushes to the client are exactly as correct as opening the screen
     * from scratch would be, satisfying Slice 3a's "refresh... rather than requiring the player
     * to close and reopen" without trusting any locally-computed delta.
     */
    private static void refreshAccount(ServerPlayer player, ServiceNpcEntity teller) {
        // Milestone 17: flagged as a refresh so a client that closed banking mid-flight discards
        // it instead of having the interface re-open uninvited (the BankNavigation D9 note).
        BankingProxyService.handle(player, teller, BankingProxyService.OpenPurpose.REFRESH);
    }
}
