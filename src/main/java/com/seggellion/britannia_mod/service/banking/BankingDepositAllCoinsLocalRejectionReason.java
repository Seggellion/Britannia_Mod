package com.seggellion.britannia_mod.service.banking;

/**
 * Why a Deposit All Coins sweep was refused before any network call -- the bulk counterpart to
 * {@link BankingCurrencyDepositLocalRejectionReason}.
 *
 * <p>Only one reason exists, and that is the point. A single-denomination deposit can be refused
 * locally for an empty slot or a non-coin item because it names a slot the player picked; a sweep
 * names nothing and inspects everything, so the only way it can fail before contacting Rails is
 * by finding nothing to send.
 */
public enum BankingDepositAllCoinsLocalRejectionReason {
    /**
     * The sweep found no coins in the main inventory or hotbar.
     *
     * <p>Refused here rather than by asking Rails, even though Rails answers the same question
     * with its own {@code NO_COINS} outcome. Both exist on purpose: this one avoids a pointless
     * round trip for the common case of a player pressing the button with an empty purse, and
     * Rails' one is the authoritative guard against a modified client that skips this check.
     */
    NO_COINS
}
