package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.bank.currency.CurrencyItemRegistry;
import com.seggellion.britannia_mod.component.BankChequeData;

/**
 * Milestone 9 follow-on: what colour a bank cheque is drawn in.
 *
 * <p>A cheque uses the existing {@code deed_item} artwork -- it is a written instrument, and the
 * deed already looks like one -- tinted by the balance that funded it, so gold, silver and copper
 * cheques are distinguishable at a glance in a full inventory.
 *
 * <p>Plain and denomination-keyed rather than a switch inside the colour handler, so the mapping
 * is JUnit-testable and lives next to the rest of the banking presentation instead of in the
 * client registry alongside spawn eggs.
 *
 * <p>The tint is read from {@link BankChequeData#currencyKey()}, which is presentation only. A
 * modified component can change what a cheque <em>looks</em> like; it can never change what one is
 * worth, because redemption is decided by {@code chequeId} against Rails.
 */
public final class BankChequeTint {

    /** Warm yellow. */
    public static final int GOLD = 0xFFD24A;
    /** Neutral grey, deliberately lighter than the parchment so it reads as metal rather than dirt. */
    public static final int SILVER = 0xC8CDD4;
    /** Orange-brown. */
    public static final int COPPER = 0xC87A3C;

    /**
     * White, which multiplies to leave the texture untouched. Used for a key that is not one of
     * the three -- an untinted cheque is a better failure than a black one.
     */
    public static final int UNTINTED = 0xFFFFFF;

    private BankChequeTint() {
    }

    public static int forCurrencyKey(String currencyKey) {
        if (CurrencyItemRegistry.GOLD_KEY.equals(currencyKey)) return GOLD;
        if (CurrencyItemRegistry.SILVER_KEY.equals(currencyKey)) return SILVER;
        if (CurrencyItemRegistry.COPPER_KEY.equals(currencyKey)) return COPPER;
        return UNTINTED;
    }

    /** The tint for a cheque's data, or {@link #GOLD} when it carries none. */
    public static int forData(BankChequeData data) {
        return data == null ? GOLD : forCurrencyKey(data.currencyKey());
    }
}
