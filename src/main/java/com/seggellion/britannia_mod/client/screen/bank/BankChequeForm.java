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

        if (BankAmountInput.isBlank(rawAmount)) {
            return Validation.rejected(BankStatusPresenter.EMPTY_AMOUNT);
        }

        // Milestone 16 extracted the digit rules into BankAmountInput, shared with the currency
        // controls -- one definition of "what may I type here". See that class for why ASCII-only
        // and why past-long-range reads as too large rather than malformed.
        Long parsed = BankAmountInput.parse(rawAmount);
        if (parsed == null) {
            return Validation.rejected(BankStatusPresenter.INVALID_AMOUNT);
        }
        if (parsed == BankAmountInput.TOO_LARGE) {
            return Validation.rejected(BankStatusPresenter.AMOUNT_TOO_LARGE);
        }
        final long entered = parsed;

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
