package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.network.payload.BankAccountOpenedS2CPayload;
import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload;
import com.seggellion.britannia_mod.service.banking.BankItemSummary;

import javax.annotation.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Milestone 2: the one piece of client state the four rebuilt banking screens share -- the latest
 * authoritative account snapshot, which stored item is selected, whether a mutation is in flight,
 * and the last result that came back.
 *
 * <h2>Why this exists at all</h2>
 * Before this class, the account snapshot lived on the {@code BankScreen} instance itself, and
 * {@code ClientNetworkHandler#handleBankAccountOpened} consumed every refresh push by calling
 * {@code setScreen(new BankScreen(payload))}. That works for exactly one screen and stops working
 * the moment there are four: a refresh arrives whenever the server confirms a mutation, it does
 * not know or care which screen is open, and rebuilding the screen would throw away the player's
 * grid selection, scroll position and half-typed amount -- and bounce them out of the Bank Box
 * mid-session. Milestone 0's recon §4.1 has the full finding.
 *
 * <p>So the push updates <i>this</i>, and screens read from it. The handler no longer needs to
 * know what is on screen, and a screen no longer needs to survive a refresh to keep its state.
 *
 * <h2>Not an authority, deliberately</h2>
 * Everything here is presentation state (design §6.1). This class never computes a balance, a
 * weight, an eligibility verdict or a transaction outcome -- it only remembers what the server
 * last said. The server re-derives every authoritative fact from the live world on every request,
 * exactly as it did before this class existed; nothing about the trust boundary moves client-ward
 * because a cached copy now lives here.
 *
 * <p>It holds the whole {@link BankAccountOpenedS2CPayload} rather than exploding it into fields
 * on purpose: there is then no second copy to drift, and a field added to the payload later (
 * {@code item_key} at Milestone 10) reaches screens without touching this class.
 *
 * <h2>No revision, and why that is safe</h2>
 * Design §6 allows a "monotonically increasing revision ... when supported", and nothing supports
 * one -- {@code BankAccountOpenedS2CPayload} carries no revision, timestamp or sequence. Milestone
 * 1's D12 decided against adding one, for two reasons that hold as long as both stay true:
 *
 * <ul>
 *   <li>every push travels {@code PacketDistributor.sendToPlayer} on one ordered connection, so
 *       arrival order is send order -- there is no reordering to detect;</li>
 *   <li>every push is a complete snapshot re-fetched by {@code bank.open}, never a delta, so
 *       applying the latest wholesale is correct by construction. There is no partial merge that
 *       could be corrupted by getting the order wrong.</li>
 * </ul>
 *
 * If Milestone 18's multiplayer matrix ever demonstrates a real stale-state failure, a
 * discriminator becomes its own change with a stated reason. Not before.
 *
 * <h2>Threading</h2>
 * Client thread only. Every mutator here is reached from {@code ctx.enqueueWork}, which is the
 * same main-thread hop every other payload handler in {@code ClientNetworkHandler} already uses.
 */
public final class ClientBankingSession {

    /**
     * The one live session, or {@code null} when the player is not in a banking interaction.
     * Static because the refresh push has to land somewhere that outlives whichever screen
     * happens to be mounted -- that is the entire point of this class.
     */
    @Nullable
    private static ClientBankingSession active;

    private BankAccountOpenedS2CPayload snapshot;

    /**
     * The stored item the player has selected in the bank grid, by public id -- never by grid
     * position. A cell index would be wrong the instant a refresh reorders or removes a row;
     * the public id stays meaningful, and {@link #reconcileSelection} drops it when the item is
     * genuinely gone.
     */
    @Nullable
    private UUID selectedStoredItem;

    /**
     * The in-flight mutation, or {@code null}. One lock for the whole interaction, not one per
     * screen: design §10.9 requires that no second banking mutation may begin while one is
     * pending, and "second" spans screens -- a player must not be able to start a cheque while a
     * deposit is in flight by navigating away from the Bank Box.
     *
     * <p>Typed with the server's own {@link BankTransferResultS2CPayload.Operation} rather than a
     * parallel client enum, so there is no second vocabulary to keep in sync. It is coarser than
     * the UI: a currency deposit and an item deposit are both {@code DEPOSIT}, and a currency
     * withdrawal and a stored-item withdrawal are both {@code WITHDRAWAL} (Milestone 0 §3.3).
     * That costs nothing here, because the lock is global -- the operation is carried for display
     * and diagnostics, not to decide what the lock blocks.
     */
    @Nullable
    private BankTransferResultS2CPayload.Operation pending;

    /**
     * Milestone 17: when {@link #pending} was claimed, so silence can be named. After this many
     * milliseconds without an answer the screens show {@link BankStatusPresenter#UNCERTAIN}
     * rather than an indefinitely mute pending state. Ten seconds sits comfortably past the
     * server's own Rails transport timeouts -- an answer that has not arrived by then is not
     * merely slow.
     */
    public static final long UNCERTAIN_AFTER_MILLIS = 10_000L;

    private long pendingSinceMillis;

    /** Set with {@link #pending}; see {@link #beginPending(BankTransferResultS2CPayload.Operation, boolean)}. */
    private boolean pendingMovesCurrency;

    /** Test seam for {@link #isPendingUncertain} -- wall-clock by default. */
    private static java.util.function.LongSupplier clock = System::currentTimeMillis;

    /**
     * The last non-success outcome, kept so a screen can render it. A confirmed mutation never
     * produces one of these -- success arrives as a refresh push instead (see {@code
     * BankTransferResultS2CPayload}'s own class docs), which is why {@link #applyAccountOpened}
     * clears this.
     */
    @Nullable
    private BankTransferResultS2CPayload lastResult;

    private ClientBankingSession(BankAccountOpenedS2CPayload snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    // ---------- Lifecycle ----------

    /**
     * Consumes a {@code bank.open} result or a post-mutation refresh push -- the client has no way
     * to tell those apart, and deliberately does not need to.
     *
     * <p>A payload for a different teller starts a fresh session: walking from one bank to another
     * must not carry a selection or a pending lock across, because neither refers to anything in
     * the new account. A payload for the same teller refreshes in place, which is what keeps the
     * player's screen, selection and scroll position alive across a successful transaction.
     *
     * @return the live session, never {@code null}
     */
    public static ClientBankingSession applyAccountOpened(BankAccountOpenedS2CPayload payload) {
        Objects.requireNonNull(payload, "payload");
        ClientBankingSession session = active;
        if (session == null || session.snapshot.entityId() != payload.entityId()) {
            session = new ClientBankingSession(payload);
            active = session;
            return session;
        }
        session.snapshot = payload;
        // A refresh IS the success signal, so it resolves whatever was pending. Nothing else
        // clears it on the happy path -- BankTransferResultS2CPayload is never sent on a confirm.
        session.pending = null;
        // Design §15.2: a status message must not outlive the state it described.
        session.lastResult = null;
        session.reconcileSelection();
        return session;
    }

    /**
     * Consumes a rejection, reconciliation-required, or pending-delivery result.
     *
     * <p>Returns {@code false} when there is no live session -- the player closed banking while
     * the request was in flight. Design §5.3 is explicit that closing does not cancel a request
     * that already reached the server, and equally that the client must not invent a result after
     * closing; dropping it here is how both hold at once. The true state is whatever the next
     * {@code bank.open} says.
     */
    public static boolean applyTransferResult(BankTransferResultS2CPayload payload) {
        Objects.requireNonNull(payload, "payload");
        ClientBankingSession session = active;
        if (session == null) return false;
        session.pending = null;
        session.lastResult = payload;
        return true;
    }

    /**
     * Ends the banking interaction. Called when a screen closes for real (Escape), never on
     * navigation between banking screens -- Back keeps the same session by design (§5.1).
     */
    public static void close() {
        active = null;
    }

    /** The live session, or {@code null} when banking is closed. */
    @Nullable
    public static ClientBankingSession active() {
        return active;
    }

    public static boolean isOpen() {
        return active != null;
    }

    // ---------- Snapshot accessors ----------

    /** The whole latest payload, for callers that want a field this class does not name. */
    public BankAccountOpenedS2CPayload snapshot() {
        return snapshot;
    }

    public int tellerEntityId() {
        return snapshot.entityId();
    }

    public String tellerName() {
        return snapshot.tellerName();
    }

    public String tellerGender() {
        return snapshot.tellerGender();
    }

    /** {@code null} in global mode -- render nothing, not an empty label. */
    @Nullable
    public String cityDisplayName() {
        return snapshot.cityDisplayName();
    }

    public int goldBalance() {
        return snapshot.goldBalance();
    }

    public int silverBalance() {
        return snapshot.silverBalance();
    }

    public int copperBalance() {
        return snapshot.copperBalance();
    }

    public double currentWeight() {
        return snapshot.currentWeight();
    }

    public int weightLimit() {
        return snapshot.weightLimit();
    }

    /** Immutable -- {@link BankAccountOpenedS2CPayload} already copies on construction. */
    public List<BankItemSummary> bankItems() {
        return snapshot.bankItems();
    }

    // ---------- Selection ----------

    @Nullable
    public UUID selectedStoredItem() {
        return selectedStoredItem;
    }

    /**
     * Selects a stored item by public id. Ignores an id the account does not currently hold: the
     * only legitimate source is a cell the player clicked, so an unknown id means the view was
     * stale, and silently selecting it would arm Withdraw against something that is already gone.
     *
     * @return whether the selection changed
     */
    public boolean selectStoredItem(UUID publicId) {
        Objects.requireNonNull(publicId, "publicId");
        if (!holds(publicId)) return false;
        if (publicId.equals(selectedStoredItem)) return false;
        selectedStoredItem = publicId;
        return true;
    }

    public void clearSelection() {
        selectedStoredItem = null;
    }

    private boolean holds(UUID publicId) {
        for (BankItemSummary item : snapshot.bankItems()) {
            if (item.publicId().equals(publicId)) return true;
        }
        return false;
    }

    /**
     * Drops a selection whose item is no longer in the account -- it was withdrawn, by this player
     * or by another client holding the same account open. Playbook Milestone 15: "stale selection
     * clears after refresh when the item is gone."
     */
    private void reconcileSelection() {
        if (selectedStoredItem != null && !holds(selectedStoredItem)) {
            selectedStoredItem = null;
        }
    }

    // ---------- Pending ----------

    public boolean isMutationPending() {
        return pending != null;
    }

    @Nullable
    public BankTransferResultS2CPayload.Operation pendingOperation() {
        return pending;
    }

    /**
     * Claims the mutation lock before a request goes out.
     *
     * <p>Returns {@code false} if one is already in flight, and callers must then send nothing.
     * This is the client half of duplicate protection -- a disabled button is the visible half,
     * and the server's own guards are the half that actually matters (design §14.3). It is the
     * cheapest of the three and stops the common case: a double-clicked button, or a duplicate
     * mouse-release at the end of a drag.
     */
    public boolean beginPending(BankTransferResultS2CPayload.Operation operation) {
        return beginPending(operation, false);
    }

    /**
     * @param movesCurrency whether this request moves coins rather than an item -- the bit {@link
     *                      BankTransferResultS2CPayload.Operation} deliberately does not carry
     *                      (Milestone 2 made it coarse on purpose). Only the sender knows, so
     *                      only the sender can say; it is read back by {@link BankMutationCue}
     *                      when the refresh confirms success. Defaults false, which is correct
     *                      for every item path and irrelevant to the silent cheque ones.
     */
    public boolean beginPending(BankTransferResultS2CPayload.Operation operation, boolean movesCurrency) {
        Objects.requireNonNull(operation, "operation");
        if (pending != null) return false;
        pending = operation;
        pendingMovesCurrency = movesCurrency;
        pendingSinceMillis = clock.getAsLong();
        // Design §15.2: the previous outcome stops being relevant the moment a new one starts.
        lastResult = null;
        return true;
    }

    /** What the in-flight request said it was moving. Meaningless with nothing pending. */
    public boolean pendingMovesCurrency() {
        return pendingMovesCurrency;
    }

    /**
     * Whether the in-flight request has gone unanswered past {@link #UNCERTAIN_AFTER_MILLIS}.
     * Only ever names the silence -- the lock stays held, because nothing client-side can cancel
     * a request that may still land (design §5.3). Escape remains available throughout, and the
     * next {@code bank.open} shows the truth either way.
     */
    public boolean isPendingUncertain() {
        return pending != null && clock.getAsLong() - pendingSinceMillis >= UNCERTAIN_AFTER_MILLIS;
    }

    /**
     * Releases the lock without a server result. For the one case that produces no packet: a
     * request that failed to send. Not for cancelling an in-flight request -- nothing client-side
     * can cancel one (design §5.3).
     */
    public void abandonPending() {
        pending = null;
    }

    @Nullable
    public BankTransferResultS2CPayload lastResult() {
        return lastResult;
    }

    public void clearLastResult() {
        lastResult = null;
    }

    // ---------- Testing ----------

    /**
     * Drops the static session between tests, mirroring {@code
     * BankingTransferPacketService#resetResultSenderForTesting}'s established shape for
     * static client/server state in this codebase.
     */
    public static void resetForTesting() {
        active = null;
        clock = System::currentTimeMillis;
    }

    /** Substitutes the {@link #isPendingUncertain} clock. Undone by {@link #resetForTesting}. */
    public static void useClockForTesting(java.util.function.LongSupplier testClock) {
        clock = Objects.requireNonNull(testClock, "testClock");
    }
}
