package com.seggellion.britannia_mod.client.screen.bank;

import java.util.Locale;

/**
 * Milestone 5: how a balance is worded.
 *
 * <p>Small, but it is the part of the Balance Screen worth testing. Plural selection is a real
 * source of embarrassing copy ("1 gold coins"), Minecraft's translation system has no plural rule
 * of its own -- the caller picks the key -- and the choice has to be made identically everywhere a
 * coin count is spoken. Doing it in one plain class keeps it out of the screen, where Architecture
 * Decision 0 says nothing can be tested.
 */
public final class BankBalanceCopy {

    private static final String ROOT = "screen.britannia_mod.bank.balance.";

    private BankBalanceCopy() {
    }

    /**
     * The three denominations, in the order they are always spoken: highest value first, matching
     * the balance line the legacy screen showed and the order design §11.2 lists.
     */
    public enum Denomination {
        GOLD("gold"),
        SILVER("silver"),
        COPPER("copper");

        private final String keySegment;

        Denomination(String keySegment) {
            this.keySegment = keySegment;
        }

        public String keySegment() {
            return keySegment;
        }
    }

    /**
     * The translation key for "{@code n} gold coins", picking singular or plural.
     *
     * <p>Only exactly one is singular. Zero takes the plural form, which is correct in English
     * ("0 gold coins") and is the case a naive {@code n < 2} check gets wrong.
     */
    public static String amountKey(Denomination denomination, int amount) {
        return ROOT + denomination.keySegment() + (amount == 1 ? ".one" : ".many");
    }

    /**
     * Which sentence frames the three amounts. An account holding nothing at all gets its own
     * line rather than "0 gold coins, 0 silver coins, and 0 copper coins", which is accurate and
     * reads like a fault.
     */
    public static String bodyKey(int gold, int silver, int copper) {
        return gold == 0 && silver == 0 && copper == 0 ? ROOT + "body_empty" : ROOT + "body";
    }

    /**
     * Group-separated, so a seven-figure copper balance is readable at a glance rather than a run
     * of digits. {@link Locale#ROOT} deliberately: this is the grouping the English strings in
     * {@code en_us.json} are written against, and a locale added later should bring its own
     * formatting decision with it rather than inherit the client's system locale silently.
     */
    public static String formatAmount(int amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }

    /**
     * Shown while Deposit All Coins exists but cannot yet do anything. Playbook Milestone 5 allows
     * the button to stay disabled until Milestone 6 and asks that the placeholder be clear; a
     * disabled button alone says "not now" without saying why.
     *
     * <p><b>Delete at Milestone 6</b>, along with the key in {@code en_us.json}.
     */
    public static final BankStatusPresenter.Status DEPOSIT_ALL_UNAVAILABLE =
            new BankStatusPresenter.Status(ROOT + "deposit_all_unavailable", BankStatusPresenter.Severity.INFORMATIONAL);
}
