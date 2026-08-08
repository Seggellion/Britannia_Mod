package com.seggellion.britannia_mod.client.screen.bank;

/**
 * Milestone 4: marks a screen as part of the banking interaction.
 *
 * <p>It exists for one decision. When a refresh push arrives, the client has to know whether the
 * player is already looking at banking -- if they are, the screen stays mounted and re-reads
 * {@link ClientBankingSession}; if they are not, banking opens at the Main screen. Without a
 * marker that question becomes a chain of {@code instanceof} checks against every banking screen,
 * which is a list somebody will forget to extend at Milestone 5, 7 or 9.
 *
 * <p>Deliberately empty. There is no shared behaviour worth hoisting here: design §7 prefers
 * composition, and {@link BankDialogueFrame} already carries what the dialogue screens share. This
 * is a type-level fact, not a base class.
 *
 * <p>{@code BankChequeIssuanceScreen} does <b>not</b> implement this yet. It still renders balances
 * from the payload captured in its constructor, so leaving it mounted across a refresh would show
 * the player stale numbers immediately after they changed them. Until Milestone 7 rebuilds it on
 * the session, being replaced by a freshly-read Main screen is the more honest outcome -- and it
 * matches what happens today.
 */
public interface BankingScreen {
}
