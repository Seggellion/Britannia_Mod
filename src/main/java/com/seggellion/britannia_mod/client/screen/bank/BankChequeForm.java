package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.client.screen.bank.BankBalanceCopy.Denomination;
import com.seggellion.britannia_mod.service.banking.BankingChequeIssuanceProxyService;

import javax.annotation.Nullable;

import java.util.Objects;

/**
 * Milestone 7: everything the Create Cheque screen decides before it is allowed to send anything.
 *
 * <p>Plain, so it can be tested (Architecture Decision 0). The screen renders what this returns
 * and enables its Confirm button when this says the form is valid; it makes no judgement of its
 * own.
 *
 * <h2>Coins all the way through -- there is no conversion here</h2>
 * <b>A cheque is between {@value #MIN_UNITS} and {@value #MAX_UNITS} coins of one denomination</b>,
 * the same two numbers for gold, silver and copper. What the player types is what is sent, what is
 * stored, and what redemption pays back (Milestone 8c, ADR-027).
 *
 * <p>There used to be a copper conversion in this class, and the ceiling used to differ per
 * denomination because of it: the amount was a copper <i>value</i>, so five million gold needed
 * 50 000 000 000 and did not fit the column. Making the amount a coin count removed both the
 * conversion and the ceiling. Nothing in the cheque path multiplies by a denomination any more,
 * which is the point -- a conversion that exists is a conversion that can be applied twice, or
 * in the wrong direction.
 *
 * <h2>What it does not do</h2>
 * No packet, and no authority. Milestone 7 sends nothing at all; Milestone 8b adds the request.
 * Every check here is duplicated server-side by Rails' own validator, which is the one that
 * counts -- this only decides whether the button is pressable and what the player reads.
 */
public final class BankChequeForm {

    /** The smallest cheque, counted in coins of the selected denomination. Mirrors Rails' {@code BankCheque::MIN_AMOUNT}. */
    public static final int MIN_UNITS = 500;

    /** The largest cheque, in the same coins. Reachable in every denomination. Mirrors Rails' {@code BankCheque::MAX_AMOUNT}. */
    public static final int MAX_UNITS = BankingChequeIssuanceProxyService.MAX_COIN_COUNT;

    private BankChequeForm() {
    }

    /**
     * The smallest cheque in {@code denomination} -- {@value #MIN_UNITS} coins, whichever coin it
     * is. Takes the denomination anyway so callers read as asking a question rather than quoting
     * a constant, and so a future per-denomination floor would not change a single call site.
     */
    public static int minimumIn(Denomination denomination) {
        Objects.requireNonNull(denomination, "denomination");
        return MIN_UNITS;
    }

    /** The largest cheque in {@code denomination} -- the same {@value #MAX_UNITS} coins for all three. */
    public static int maximumIn(Denomination denomination) {
        Objects.requireNonNull(denomination, "denomination");
        return MAX_UNITS;
    }

    /**
     * The outcome of validating the form.
     *
     * <p>{@code amount} is the coin count, meaningful only when {@link #valid()}. It is exactly
     * what the player typed and exactly what gets sent -- there is deliberately no second,
     * converted figure alongside it. The previous version of this record carried both a copper
     * value and the typed number, and the screen picking the wrong one is precisely the class of
     * mistake Milestone 8c exists to remove.
     */
    public record Validation(@Nullable BankStatusPresenter.Status error, int amount) {

        public boolean valid() {
            return error == null;
        }

        static Validation ok(int amount) {
            return new Validation(null, amount);
        }

        static Validation rejected(BankStatusPresenter.Status error) {
            return new Validation(Objects.requireNonNull(error, "error"), 0);
        }
    }

    /**
     * Validates the typed amount against the selected denomination and the account's balance.
     *
     * <p>Order matters and is deliberate: shape problems first (nothing selected, nothing typed,
     * not a number), then range, then affordability. A player who types {@code abc} should be told
     * it is not a number, not that they cannot afford it.
     *
     * @param rawAmount    exactly what is in the text field, untrimmed
     * @param denomination the selected denomination, or {@code null} if none is selected yet
     * @param balance      the account's balance <em>in that denomination</em>; ignored when
     *                     {@code denomination} is {@code null}
     */
    public static Validation validate(String rawAmount, @Nullable Denomination denomination, int balance) {
        Objects.requireNonNull(rawAmount, "rawAmount");

        if (denomination == null) {
            return Validation.rejected(BankStatusPresenter.NO_DENOMINATION_SELECTED);
        }

        String trimmed = rawAmount.trim();
        if (trimmed.isEmpty()) {
            return Validation.rejected(BankStatusPresenter.EMPTY_AMOUNT);
        }

        // ASCII digits only, checked before parsing rather than left to Long.parseLong.
        //
        // parseLong accepts any Unicode decimal digit -- it routes through Character.digit -- so
        // Arabic-Indic "٥٠٠" parses happily as 500, as would a string mixing scripts. The value
        // would even be right, but a field whose accepted input depends on which digit systems
        // the JDK recognises is not a contract anyone can reason about, and it silently differs
        // from the plain-ASCII amounts every other banking field takes. Being explicit costs three
        // lines and makes "what may I type here" answerable.
        if (!isAsciiDigits(trimmed)) {
            return Validation.rejected(BankStatusPresenter.INVALID_AMOUNT);
        }

        final long entered;
        try {
            // Parsed as long so that a value above int range reports as too large rather than
            // as malformed -- "1000000000000" is a number, just not one we can issue.
            entered = Long.parseLong(trimmed);
        } catch (NumberFormatException notANumber) {
            // Reachable for a run of digits too long for a long, which isAsciiDigits allows.
            return Validation.rejected(BankStatusPresenter.AMOUNT_TOO_LARGE);
        }

        // Zero and negative are the same message: an amount has to be a positive count of coins.
        if (entered <= 0L) {
            return Validation.rejected(BankStatusPresenter.INVALID_AMOUNT);
        }

        // Both bounds are coin counts and so is what the player typed, so this is the whole of
        // the range check. No conversion, no per-denomination special case.
        if (entered < MIN_UNITS) {
            return Validation.rejected(BankStatusPresenter.AMOUNT_BELOW_MINIMUM);
        }
        if (entered > maximumIn(denomination)) {
            return Validation.rejected(BankStatusPresenter.AMOUNT_TOO_LARGE);
        }

        if (entered > balance) {
            return Validation.rejected(BankStatusPresenter.INSUFFICIENT_BALANCE);
        }

        return Validation.ok((int) entered);
    }

    /**
     * True for a non-empty run of {@code '0'}–{@code '9'} and nothing else -- no sign, no
     * separators, no other digit systems. A leading {@code '-'} therefore fails here rather than
     * parsing to a negative, which is why the zero-or-negative check below it only ever has to
     * catch a literal zero.
     */
    private static boolean isAsciiDigits(String value) {
        if (value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    /** The account's balance in {@code denomination}, read from the session. */
    public static int balanceOf(ClientBankingSession session, Denomination denomination) {
        Objects.requireNonNull(session, "session");
        return switch (denomination) {
            case GOLD -> session.goldBalance();
            case SILVER -> session.silverBalance();
            case COPPER -> session.copperBalance();
        };
    }
}
