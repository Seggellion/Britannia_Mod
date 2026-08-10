package com.seggellion.britannia_mod.client.screen.guild;

import com.seggellion.britannia_mod.network.payload.GuildTrainingOpenS2CPayload.Offer;
import net.minecraft.network.chat.Component;

/**
 * The text on one training button, and the reason it is disabled when it is.
 *
 * <p>Guildmaster milestone 7. Translatable rather than literal, matching the bank screens: a
 * missing key does not crash, it renders the raw key where a sentence should be, which is why
 * {@code GuildTranslationKeysTest} asserts every key here exists in the language file.
 *
 * <p>The diegetic <em>chat</em> messages elsewhere in this feature stay as literals on purpose —
 * {@code BankingProxyService} does the same for its three, and making the Guildmaster the only
 * translated one would be the real inconsistency.
 *
 * <p>A plain class rather than logic inside the screen, for the reason that has governed every UI
 * piece in this feature: Architecture Decision 0 means no harness can construct a {@code Screen},
 * so anything worth asserting lives outside one.
 */
public final class GuildTrainingOfferLabel {
    private static final String ROOT = "screen.britannia_mod.guild.offer.";

    /** {@code "Swordsmanship 12.7"} — already trained as far as this guild can take you. */
    public static final String KEY_AT_CAP = ROOT + "at_cap";
    /** Distinct from the cap: there is training to be had, the player just cannot pay for it. */
    public static final String KEY_NO_GOLD = ROOT + "no_gold";
    public static final String KEY_BUY = ROOT + "buy";

    private GuildTrainingOfferLabel() {
    }

    /**
     * What the button reads. Purchasable offers show what the gold buys, so a player can see the
     * trade before committing; the rest say which reason applies.
     *
     * <p>Saying "taught in full" when the truth is "you cannot afford it" would discourage a player
     * from ever coming back, so the two are separate keys rather than one vague one.
     */
    public static Component of(Offer offer) {
        String current = tenths(offer.currentTenths());
        if (offer.atCap()) {
            return Component.translatable(KEY_AT_CAP, offer.label(), current);
        }
        if (offer.affordableGold() <= 0) {
            return Component.translatable(KEY_NO_GOLD, offer.label(), current);
        }
        return Component.translatable(
                KEY_BUY,
                offer.label(),
                current,
                tenths(offer.currentTenths() + offer.affordableGold()),
                offer.affordableGold()
        );
    }

    /**
     * {@code 127 -> "12.7"}, {@code 400 -> "40.0"}. Always one decimal, so a column lines up.
     *
     * <p>Fixed-point, never {@code float} formatting: a skill value is an integer count of tenths
     * everywhere in this feature, and this is the one place a displayed value could otherwise
     * disagree with the price derived from the same number.
     */
    public static String tenths(int value) {
        return (value / 10) + "." + Math.abs(value % 10);
    }
}
