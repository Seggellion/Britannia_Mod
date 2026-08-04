package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.client.screen.bank.BankBalanceCopy.Denomination;
import com.seggellion.britannia_mod.economy.CoinConversion;
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
 * <h2>The floor is a coin count; the ceiling is a value</h2>
 * <b>The smallest cheque is {@value #MIN_UNITS} coins of whichever denomination funds it</b> --
 * 500 gold, 500 silver, or 500 copper (owner decision, superseding the value-denominated floor
 * recorded on 2026-08-03; see design §12.3.1). One sentence a player can hold in their head,
 * rather than a copper figure they must convert.
 *
 * <p>Gold is unchanged by this: 500 gold has always been the floor, because 500 gold is exactly
 * the 5 000 000 copper the previous absolute rule required. Only silver and copper gain lower
 * floors, and neither denomination existed before this epic.
 *
 * <p><b>The ceiling stays value-denominated, and that is structural rather than a policy choice.</b>
 * A cheque's amount is stored and debited as an int32 copper value, so the real limit is
 * {@link #MAX_COPPER}; a flat coin count would let a copper cheque overflow the column it lives
 * in. {@link #maximumIn} therefore differs per denomination -- 100 000 gold, 10 000 000 silver,
 * 1 000 000 000 copper -- because that is what fits.
 *
 * <h2>What it does not do</h2>
 * No packet, and no authority. Milestone 7 sends nothing at all; Milestone 8b adds the request.
 * Every check here is duplicated server-side by Rails' own validator, which is the one that
 * counts -- this only decides whether the button is pressable and what the player reads.
 */
public final class BankChequeForm {

    /**
     * The smallest cheque, counted in coins of the selected denomination -- not in value.
     *
     * <p>Milestone 8a must teach Rails the same rule. Its {@code BankCheque::MIN_AMOUNT} is
     * currently an absolute 5 000 000 copper, which happens to equal 500 gold and so already
     * agrees for gold, but would reject a 500-silver or 500-copper cheque. Nothing is sent until
     * 8b, so the two cannot disagree in flight; 8a is where Rails' floor becomes
     * denomination-aware and its absolute floor drops to the smallest legal cheque.
     */
    public static final int MIN_UNITS = 500;

    /**
     * The largest cheque by value. Structural: the amount is stored and debited as an int32
     * copper column, so this is a capacity limit rather than a product decision.
     */
    public static final int MAX_COPPER = BankingChequeIssuanceProxyService.MAX_AMOUNT_COPPER;

    private BankChequeForm() {
    }

    /** How many copper one unit of {@code denomination} is worth. */
    public static int copperPerUnit(Denomination denomination) {
        return switch (denomination) {
            case GOLD -> CoinConversion.COPPER_PER_GOLD;
            case SILVER -> CoinConversion.COPPER_PER_SILVER;
            case COPPER -> 1;
        };
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

    /** The largest cheque expressible in {@code denomination}. */
    public static int maximumIn(Denomination denomination) {
        return MAX_COPPER / copperPerUnit(denomination);
    }

    /**
     * The outcome of validating the form.
     *
     * <p>{@code copperAmount} is meaningful only when {@link #valid()}. It is what Milestone 8b
     * will send, already converted out of the player's chosen denomination -- the request carries
     * a copper value plus the funding denomination, never the raw typed number.
     */
    public record Validation(@Nullable BankStatusPresenter.Status error, int copperAmount, int enteredAmount) {

        public boolean valid() {
            return error == null;
        }

        static Validation ok(int copperAmount, int enteredAmount) {
            return new Validation(null, copperAmount, enteredAmount);
        }

        static Validation rejected(BankStatusPresenter.Status error) {
            return new Validation(Objects.requireNonNull(error, "error"), 0, 0);
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

        // The floor is a coin count, so it is checked against what the player typed rather than
        // against the converted value -- that is the whole point of the rule.
        if (entered < MIN_UNITS) {
            return Validation.rejected(BankStatusPresenter.AMOUNT_BELOW_MINIMUM);
        }

        long copper = entered * (long) copperPerUnit(denomination);
        // The ceiling is a value, and is checked on the long before any narrowing so an amount
        // that would overflow int is caught here rather than wrapping into a plausible small one.
        if (copper > MAX_COPPER) {
            return Validation.rejected(BankStatusPresenter.AMOUNT_TOO_LARGE);
        }

        if (entered > balance) {
            return Validation.rejected(BankStatusPresenter.INSUFFICIENT_BALANCE);
        }

        return Validation.ok((int) copper, (int) entered);
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
