package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.client.screen.bank.BankBalanceCopy.Denomination;

import javax.annotation.Nullable;

import java.util.Objects;

/**
 * Milestone 16: whether a currency withdrawal can be requested, and for how much.
 *
 * <p>Plain and tested (Architecture Decision 0); the screen enables each denomination button by
 * asking this against that denomination's balance, and sends exactly {@link Validation#amount()}.
 * Unlike the cheque form there is no floor beyond one coin and no ceiling beyond what an int can
 * carry -- withdrawing 3 copper for a market stall is a legitimate request, and the server
 * revalidates the balance regardless (design §9.7).
 *
 * <p>The digit rules are {@link BankAmountInput}'s, shared with the cheque form -- one definition
 * of what an amount field accepts.
 */
public final class BankCurrencyWithdrawalForm {

    /** The outcome. {@code amount} is meaningful only when {@link #valid()}. */
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

    private BankCurrencyWithdrawalForm() {
    }

    /**
     * @param rawAmount exactly what is in the field
     * @param balance   the account's balance in the denomination whose button is being judged --
     *                  affordability is per-denomination, which is why each of the three buttons
     *                  asks separately and can disagree
     */
    public static Validation validate(String rawAmount, int balance) {
        Objects.requireNonNull(rawAmount, "rawAmount");

        if (BankAmountInput.isBlank(rawAmount)) {
            return Validation.rejected(BankStatusPresenter.EMPTY_AMOUNT);
        }
        Long parsed = BankAmountInput.parse(rawAmount);
        if (parsed == null) {
            return Validation.rejected(BankStatusPresenter.INVALID_AMOUNT);
        }
        if (parsed == BankAmountInput.TOO_LARGE || parsed > Integer.MAX_VALUE) {
            return Validation.rejected(BankStatusPresenter.AMOUNT_TOO_LARGE);
        }
        if (parsed <= 0L) {
            return Validation.rejected(BankStatusPresenter.INVALID_AMOUNT);
        }
        if (parsed > balance) {
            return Validation.rejected(BankStatusPresenter.INSUFFICIENT_BALANCE);
        }
        return Validation.ok(parsed.intValue());
    }

    /** The session balance the button for {@code denomination} is judged against. */
    public static int balanceOf(ClientBankingSession session, Denomination denomination) {
        return BankChequeForm.balanceOf(session, denomination);
    }
}
