package com.seggellion.britannia_mod.client.screen.guild;

import com.seggellion.britannia_mod.network.payload.GuildTrainingOpenS2CPayload.Offer;

/**
 * The text on one training button, and the reason it is disabled when it is.
 *
 * <p>Guildmaster milestone 6. A plain class rather than logic inside the screen, for the reason
 * that has governed every UI piece in this feature: Architecture Decision 0 means no harness can
 * construct a {@code Screen}, so anything worth asserting lives outside one — the same split
 * {@code BankDialogueLayout} and {@code ServiceNpcSpawnPresentation} use.
 *
 * <p>Fixed-point throughout. A skill value is an integer count of tenths everywhere in this
 * feature, and formatting it through a {@code float} here would be the one place a displayed value
 * could disagree with the price derived from the same number.
 */
public final class GuildTrainingOfferLabel {
    private GuildTrainingOfferLabel() {
    }

    /**
     * What the button reads. Purchasable offers show what the gold buys, so a player can see the
     * trade before committing; the rest say why they cannot be pressed.
     */
    public static String of(Offer offer) {
        StringBuilder text = new StringBuilder(64);
        text.append(offer.label()).append(' ').append(tenths(offer.currentTenths()));

        if (offer.atCap()) {
            return text.append(" - taught in full").toString();
        }
        if (offer.affordableGold() <= 0) {
            // Distinct from the cap: there is training to be had, the player just cannot pay for
            // it. Saying "taught in full" here would be a lie that discourages coming back.
            return text.append(" - not enough gold").toString();
        }
        return text
                .append(" -> ")
                .append(tenths(offer.currentTenths() + offer.affordableGold()))
                .append(" for ")
                .append(offer.affordableGold())
                .append(offer.affordableGold() == 1 ? " gold" : " gold")
                .toString();
    }

    /** {@code 127 -> "12.7"}, {@code 400 -> "40.0"}. Always one decimal, so a column lines up. */
    public static String tenths(int value) {
        return (value / 10) + "." + Math.abs(value % 10);
    }
}
