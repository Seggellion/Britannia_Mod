package com.seggellion.britannia_mod.client.screen.bank;

/**
 * Owner improvement (2026-08-04): the balance printed on each currency button, shortened so it
 * always fits the button frame -- "1k", "1.25k", never a nine-digit run.
 *
 * <p>The owner specified the thousands tier; the millions and billions tiers follow because the
 * requirement is "the text fits within the button frame" and a balance column is int32, so
 * 2,147,483,647 is a value this method WILL eventually be handed. Rules:
 *
 * <ul>
 *   <li>below 1,000: the plain number ("0", "999");</li>
 *   <li>then thousands/millions/billions with at most two decimals, trailing zeros trimmed
 *       ("1k", "1.25k", "999.99k", "2.14b");</li>
 *   <li>decimals are <b>truncated</b>, never rounded up -- a money display must not overstate
 *       what the player has (1,999 reads "1.99k", not "2k").</li>
 * </ul>
 *
 * <p>Plain and tested (Architecture Decision 0). This is display only; every real amount the
 * client sends remains an exact integer from the session.
 */
public final class BankBalanceAbbreviation {

    private BankBalanceAbbreviation() {
    }

    public static String abbreviate(int balance) {
        if (balance < 0) return "0"; // a negative balance cannot exist; never render one
        if (balance < 1_000) return String.valueOf(balance);
        if (balance < 1_000_000) return scaled(balance, 1_000) + "k";
        if (balance < 1_000_000_000) return scaled(balance, 1_000_000) + "m";
        return scaled(balance, 1_000_000_000) + "b";
    }

    /** {@code value/divisor} to two truncated decimals, trailing zeros (and a bare dot) trimmed. */
    private static String scaled(int value, int divisor) {
        long hundredths = (long) value * 100L / divisor; // truncation is the point
        long whole = hundredths / 100;
        long frac = hundredths % 100;
        if (frac == 0) return String.valueOf(whole);
        if (frac % 10 == 0) return whole + "." + (frac / 10);
        return whole + "." + (frac < 10 ? "0" + frac : frac);
    }
}
