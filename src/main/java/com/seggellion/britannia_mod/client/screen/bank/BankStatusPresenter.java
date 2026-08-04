package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload;

import javax.annotation.Nullable;

import java.util.Objects;

/**
 * Milestone 3: the single place that decides what a banking outcome says and how loudly.
 *
 * <h2>What this replaces</h2>
 * Six {@code Component.literal} constants on {@code BankScreen} and four more on {@code
 * BankChequeIssuanceScreen}, with the two screens disagreeing about what {@code PENDING_DELIVERY}
 * means -- the cheque screen has a real message for it, the bank screen renders it as a rejection.
 * Design §15 wants one vocabulary across four screens, and §17 wants all of it translatable.
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
        INFORMATIONAL(0xFF111111, 0, false),
        /** The player's input cannot be sent yet. Recoverable by editing the form. */
        VALIDATION(0xFFB8860B, 2, false),
        /** The operation completed. */
        SUCCESS(0xFF2E7D32, 2, false),
        /** The server declined. Nothing happened; the player may try something else. */
        REJECTION(0xFFAA4444, 3, false),
        /** Something is wrong that only staff can resolve. The player must not retry. */
        RECONCILIATION(0xFF8B0000, 5, true);

        private final int color;
        private final int markerWidth;
        private final boolean bold;

        Severity(int color, int markerWidth, boolean bold) {
            this.color = color;
            this.markerWidth = markerWidth;
            this.bold = bold;
        }

        public int color() {
            return color;
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
            // retry resolves it. BankScreen renders this as a rejection today, which is wrong in
            // both directions -- it alarms the player and it hides that the cheque is real.
            case PENDING_DELIVERY -> new Status(ROOT + "pending_delivery", Severity.INFORMATIONAL);
            case CHEQUE_NOT_FOUND -> new Status(ROOT + "cheque_not_found", Severity.REJECTION);
            case CHEQUE_ALREADY_REDEEMED -> new Status(ROOT + "cheque_already_redeemed", Severity.REJECTION);
            case CHEQUE_CANCELLED -> new Status(ROOT + "cheque_cancelled", Severity.REJECTION);
            case CHEQUE_VOIDED -> new Status(ROOT + "cheque_voided", Severity.REJECTION);
        };
    }

    /** {@code null} when there is nothing to show. Convenience for reading straight off the session. */
    @Nullable
    public static Status forResult(@Nullable BankTransferResultS2CPayload payload) {
        return payload == null ? null : forResult(payload.operation(), payload.kind());
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
    public static final Status NO_DENOMINATION_SELECTED = new Status(ROOT + "no_denomination_selected", Severity.VALIDATION);
    public static final Status INSUFFICIENT_BALANCE = new Status(ROOT + "insufficient_balance", Severity.VALIDATION);
    public static final Status NOTHING_SELECTED = new Status(ROOT + "nothing_selected", Severity.INFORMATIONAL);
    public static final Status EMPTY_VAULT = new Status(ROOT + "empty_vault", Severity.INFORMATIONAL);
}
