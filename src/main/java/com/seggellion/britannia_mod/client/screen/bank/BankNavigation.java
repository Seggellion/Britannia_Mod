package com.seggellion.britannia_mod.client.screen.bank;

import javax.annotation.Nullable;

import java.util.Objects;

/**
 * Milestone 4: the navigation rules for the four-screen flow, kept out of any {@code Screen} so
 * they can be tested.
 *
 * <p>Architecture Decision 0 again: {@code ClientNetworkHandler} reaches for
 * {@code Minecraft.getInstance()} and cannot run in JUnit, so the decision it makes on every
 * refresh push -- the single most consequential rule in this epic -- would otherwise be verified
 * only by playing the game. The rule lives here as a function of one boolean; the handler just
 * asks and obeys.
 */
public final class BankNavigation {

    private static final String ACTION_ROOT = "screen.britannia_mod.bank.action.";
    private static final String MAIN_ROOT = "screen.britannia_mod.bank.main.";

    private BankNavigation() {
    }

    /** The three destinations the Bank Main Screen offers. */
    public enum Destination {
        BANK_BOX(ACTION_ROOT + "open_bank_box"),
        BALANCE(ACTION_ROOT + "balance"),
        CREATE_CHEQUE(ACTION_ROOT + "create_cheque");

        private final String labelKey;

        Destination(String labelKey) {
            this.labelKey = labelKey;
        }

        public String labelKey() {
            return labelKey;
        }
    }

    /** What to do with an incoming {@code BankAccountOpenedS2CPayload}. */
    public enum RefreshRoute {
        /** Banking is not on screen: open it at the Main screen. */
        OPEN_MAIN,
        /** The player is already in banking: leave their screen mounted, it re-reads the session. */
        KEEP_CURRENT
    }

    /**
     * The Milestone 1 D9 rule, entire.
     *
     * <p>Before this, the client consumed every account payload with
     * {@code setScreen(new BankScreen(payload))}, which is why a successful transaction discarded
     * grid selection, scroll position and a half-typed amount, and would have thrown a player out
     * of the Bank Box mid-drag once one existed.
     *
     * <p>A {@code bank.open} result and a post-mutation refresh are the same payload and cannot be
     * told apart client-side, so this keys off the only thing that distinguishes the two
     * situations that matter: whether banking is already on screen.
     *
     * <p>One consequence is worth naming. If a request is still in flight when the player presses
     * Escape, the refresh that follows finds no banking screen and re-opens Main -- the interface
     * reappears after they dismissed it. That is exactly what happens today (the legacy handler
     * called {@code setScreen} unconditionally), so it is preserved here rather than quietly
     * changed. Design §5.3 and Playbook Milestone 17 own "safe behaviour when a screen is closed
     * before result arrival"; suppressing it correctly needs to distinguish a first open from a
     * late refresh, which this payload does not currently allow.
     */
    public static RefreshRoute refreshRoute(boolean bankingScreenOpen) {
        return bankingScreenOpen ? RefreshRoute.KEEP_CURRENT : RefreshRoute.OPEN_MAIN;
    }

    /**
     * Whether a destination can be opened.
     *
     * <p>Design §8.3: buttons are disabled only when the destination cannot safely open. A
     * destination needs the session, and nothing else -- notably <b>not</b> the absence of a
     * pending mutation. Navigating is not mutating: the session outlives the screen, the pending
     * lock travels with it, and blocking navigation during a request would strand a player
     * staring at a screen until the server answered.
     */
    public static boolean canOpen(Destination destination, @Nullable ClientBankingSession session) {
        Objects.requireNonNull(destination, "destination");
        return session != null;
    }

    /**
     * Which greeting the Main screen shows. A named city is worth saying aloud -- it is the one
     * piece of context that makes the Bank of Britain feel like a different place from the Bank of
     * Trinsic -- but design §8.4 wants a short greeting, not an account dump, so this is as far as
     * it goes.
     */
    public static String greetingKey(@Nullable String cityDisplayName) {
        return cityDisplayName == null || cityDisplayName.isBlank()
                ? MAIN_ROOT + "greeting"
                : MAIN_ROOT + "greeting_city";
    }
}
