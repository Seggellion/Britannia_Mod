package com.seggellion.britannia_mod.client.screen.bank;

import javax.annotation.Nullable;

/**
 * Milestone 16: the one definition of what an amount field accepts, shared by the cheque form and
 * the currency-withdrawal controls -- extracted from {@code BankChequeForm} rather than copied,
 * because two parsers is how "what may I type here" stops having one answer.
 *
 * <p>ASCII digits only, checked before parsing. {@code Long.parseLong} accepts any Unicode
 * decimal digit -- it routes through {@code Character.digit} -- so Arabic-Indic {@code ٥٠٠} would
 * parse as 500. The value would even be right, but a field whose accepted input depends on which
 * digit systems the JDK recognises is not a contract anyone can reason about. A leading minus
 * fails the digit check rather than parsing negative, which is why callers only ever see
 * non-negative results.
 */
public final class BankAmountInput {

    /** {@link #parse}'s answer for a run of digits too long even for a {@code long}. */
    public static final long TOO_LARGE = Long.MAX_VALUE;

    private BankAmountInput() {
    }

    /**
     * The typed amount as a non-negative number, {@link #TOO_LARGE} for a digit run past long
     * range (still a number the player typed -- "too large" is the honest report, not
     * "malformed"), or {@code null} for anything that is not a plain run of ASCII digits.
     * Whitespace around the digits is forgiven; anything else is not.
     */
    @Nullable
    public static Long parse(String rawAmount) {
        String trimmed = rawAmount.trim();
        if (trimmed.isEmpty()) return null;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < '0' || c > '9') return null;
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException pastLongRange) {
            return TOO_LARGE;
        }
    }

    public static boolean isBlank(String rawAmount) {
        return rawAmount.trim().isEmpty();
    }
}
