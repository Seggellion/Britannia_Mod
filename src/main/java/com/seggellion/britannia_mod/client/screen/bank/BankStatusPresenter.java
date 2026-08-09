package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload;

import javax.annotation.Nullable;

import java.util.Objects;

/**
 * Milestone 3: the single place that decides what a banking outcome says and how loudly.
 *
 * <h2>What this replaces</h2>
 * Six {@code Component.literal} constants on the legacy screen and four more on {@code
 * BankChequeIssuanceScreen}, with the two screens disagreeing about what {@code PENDING_DELIVERY}
 * meant -- the cheque screen had a real message for it, the bank screen rendered it as a
 * rejection. Design §15 wants one vocabulary across four screens, and §17 wants all of it
 * translatable.
 *
 * <h2>Severity is not just colour</h2>
 * Design §16 requires that colour never be the only indicator of state. Each {@link Severity}
 * therefore carries a marker width and a bold flag as well as a colour, so reconciliation-required
 * is distinguishable from an ordinary rejection on a monochrome display or to a colour-blind
 * player. {@link BankDialogueFrame} draws all three.
 *
 * <p>Plain data with no client types, so JUnit can assert the whole mapping (Decision 0).
 */
public final class BankStatusPresenter {

    private static final String ROOT = "screen.britannia_mod.bank.status.";

    private BankStatusPresenter() {
    }

    /** How prominently a status reads. See the class docs on why this is more than a colour. */
    public enum Severity {
        /** Neutral context. Not an outcome. */
        INFORMATIONAL(0xFF111111, 0xFFE8DCC8, 0, false),
        /** The player's input cannot be sent yet. Recoverable by editing the form. */
        VALIDATION(0xFFB8860B, 0xFFD9B052, 2, false),
        /** The operation completed. */
        SUCCESS(0xFF2E7D32, 0xFF6FBF73, 2, false),
        /** The server declined. Nothing happened; the player may try something else. */
        REJECTION(0xFFAA4444, 0xFFD07070, 3, false),
        /** Something is wrong that only staff can resolve. The player must not retry. */
        RECONCILIATION(0xFF8B0000, 0xFFE05555, 5, true);

        private final int color;
        private final int colorOnDark;
        private final int markerWidth;
        private final boolean bold;

        Severity(int color, int colorOnDark, int markerWidth, boolean bold) {
            this.color = color;
            this.colorOnDark = colorOnDark;
            this.markerWidth = markerWidth;
            this.bold = bold;
        }

        /** For parchment and other light grounds -- the dialogue family's screens. */
        public int color() {
            return color;
        }

        /**
         * For the Bank Box's dark panel. The parchment palette was chosen against a light ground
         * -- {@link #INFORMATIONAL}'s near-black would simply vanish on a dark one, and the
         * others lose most of their contrast. Same hues, lifted; the marker and bold signals are
         * unchanged, so severity still does not depend on colour alone (design §16).
         */
        public int colorOnDark() {
            return colorOnDark;
        }

        /** Width of the marker bar drawn left of the text. Zero draws none. */
        public int markerWidth() {
            return markerWidth;
        }

        public boolean bold() {
            return bold;
        }
    }

    /** A translation key plus how to render it. */
    public record Status(String translationKey, Severity severity) {
        public Status {
            Objects.requireNonNull(translationKey, "translationKey");
            Objects.requireNonNull(severity, "severity");
        }
    }

    // ---------- Server outcomes ----------

    /**
     * Maps a server result to what the player reads.
     *
     * <p>The four {@code CHEQUE_*} kinds are only ever sent for {@code CHEQUE_REDEMPTION}, and
     * {@code PENDING_DELIVERY} only for {@code CHEQUE_ISSUANCE} -- but the operation is taken as a
     * parameter rather than assumed, because a kind arriving on an operation that should not
     * produce it is exactly the case a generic fallback should cover rather than mis-label.
     */
    public static Status forResult(
            BankTransferResultS2CPayload.Operation operation,
            BankTransferResultS2CPayload.Kind kind
    ) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case CLEAN_REJECTION -> new Status(ROOT + "clean_rejection", Severity.REJECTION);
            case RECONCILIATION_REQUIRED -> new Status(ROOT + "reconciliation_required", Severity.RECONCILIATION);
            // Nothing was rejected: Rails confirmed and the item is simply not in hand yet, and a
            // retry resolves it. The legacy screen rendered this as a rejection, which was wrong
            // in both directions -- it alarmed the player and hid that the cheque was real.
            case PENDING_DELIVERY -> new Status(ROOT + "pending_delivery", Severity.INFORMATIONAL);
            case CHEQUE_NOT_FOUND -> new Status(ROOT + "cheque_not_found", Severity.REJECTION);
            case CHEQUE_ALREADY_REDEEMED -> new Status(ROOT + "cheque_already_redeemed", Severity.REJECTION);
            case CHEQUE_CANCELLED -> new Status(ROOT + "cheque_cancelled", Severity.REJECTION);
            case CHEQUE_VOIDED -> new Status(ROOT + "cheque_voided", Severity.REJECTION);
            // Nothing failed and nobody refused the player -- their purse was empty. Rendering
            // this as a rejection would tell them their bank turned them away.
            case NOTHING_TO_DEPOSIT -> new Status(ROOT + "nothing_to_deposit", Severity.INFORMATIONAL);
            // A refusal, but an actionable one: withdraw or spend and the deposit will fit.
            case BALANCE_CAPACITY_EXCEEDED -> new Status(ROOT + "balance_capacity_exceeded", Severity.REJECTION);
            // Milestone 15: the most actionable rejection there is -- drop something, try again.
            case INVENTORY_FULL -> new Status(ROOT + "inventory_full", Severity.REJECTION);
            // Milestone 16: the server truth behind what was previously only a client pre-check.
            // Deliberately the same sentence as the client-side INSUFFICIENT_BALANCE status --
            // the player should read one message for one fact, whichever side caught it first.
            case INSUFFICIENT_BALANCE -> new Status(ROOT + "insufficient_balance", Severity.REJECTION);
            // Milestone 17: the bank will not keep this item, whatever the specific rule. A
            // refusal on principle -- retrying changes nothing, which is itself the actionable
            // information.
            case INELIGIBLE_ITEM -> new Status(ROOT + "ineligible_item", Severity.REJECTION);
            // Milestone 17: the vault's weight ceiling. INVENTORY_FULL's deposit-side sibling,
            // equally actionable: withdraw something first.
            case BANK_CAPACITY_EXCEEDED -> new Status(ROOT + "bank_capacity_exceeded", Severity.REJECTION);
            // Milestone 17: the item is already gone -- most plausibly withdrawn moments ago from
            // another client. Informational like NOTHING_TO_DEPOSIT: nobody refused the player,
            // there is nothing to fix, and the refreshed vault is the answer.
            case STORED_ITEM_UNAVAILABLE -> new Status(ROOT + "stored_item_unavailable", Severity.INFORMATIONAL);
        };
    }

    /** {@code null} when there is nothing to show. Convenience for reading straight off the session. */
    @Nullable
    public static Status forResult(@Nullable BankTransferResultS2CPayload payload) {
        return payload == null ? null : forResult(payload.operation(), payload.kind());
    }

    /**
     * Milestone 17: everything a screen's status line should currently say, in one question.
     * The last server result wins; with none, a request that has been in flight past {@link
     * ClientBankingSession#UNCERTAIN_AFTER_MILLIS} shows {@link #UNCERTAIN} -- the playbook's
     * "pending state always resolves or transitions to an explicit uncertain state". The lock
     * stays held (nothing client-side can cancel an in-flight request, design §5.3); what
     * changes is only that the silence itself is named for the player.
     */
    @Nullable
    public static Status statusFor(@Nullable ClientBankingSession session) {
        if (session == null) return null;
        Status result = forResult(session.lastResult());
        if (result != null) return result;
        return session.isPendingUncertain() ? UNCERTAIN : null;
    }

    // ---------- Client-side validation and context ----------
    //
    // Design §15 lists outcome categories that are not wire kinds. Some never will be -- an empty
    // amount box is not worth a round trip. Others are server truths the wire cannot yet express:
    // INVENTORY_FULL arrives at Milestone 15, and INSUFFICIENT_BALANCE is currently only ever a
    // client prediction against a possibly-stale snapshot (Milestone 0 §4.2). Both are named here
    // so the screens share one vocabulary, and both move to forResult when the wire catches up.

    public static final Status EMPTY_AMOUNT = new Status(ROOT + "empty_amount", Severity.VALIDATION);
    public static final Status INVALID_AMOUNT = new Status(ROOT + "invalid_amount", Severity.VALIDATION);
    public static final Status AMOUNT_TOO_LARGE = new Status(ROOT + "amount_too_large", Severity.VALIDATION);
    /**
     * Milestone 7: the entered amount is a valid number but below the cheque minimum.
     *
     * <p>Its own message rather than sharing {@link #INVALID_AMOUNT}, because the number is not
     * invalid -- it is simply too small, and the player needs to be told what "too small" is in
     * the denomination they picked. The bounds are value-denominated, so the floor is 500 gold but
     * 5 000 000 copper (design §12.3.1); a message that did not name the figure would be
     * unactionable.
     */
    public static final Status AMOUNT_BELOW_MINIMUM = new Status(ROOT + "amount_below_minimum", Severity.VALIDATION);
    public static final Status NO_DENOMINATION_SELECTED = new Status(ROOT + "no_denomination_selected", Severity.VALIDATION);
    public static final Status INSUFFICIENT_BALANCE = new Status(ROOT + "insufficient_balance", Severity.VALIDATION);
    public static final Status NOTHING_SELECTED = new Status(ROOT + "nothing_selected", Severity.INFORMATIONAL);
    public static final Status EMPTY_VAULT = new Status(ROOT + "empty_vault", Severity.INFORMATIONAL);
    /**
     * Milestone 17: a request has been in flight long enough that silence needs naming -- design
     * §15's "connection or timeout uncertainty". Client-side by nature: it describes the absence
     * of an answer, so no wire kind can ever carry it. INFORMATIONAL, not a rejection -- nothing
     * was refused, and the honest content is "the truth arrives with the next refresh".
     */
    public static final Status UNCERTAIN = new Status(ROOT + "uncertain", Severity.INFORMATIONAL);
}
