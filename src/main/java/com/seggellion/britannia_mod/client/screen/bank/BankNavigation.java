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
        KEEP_CURRENT,
        /**
         * Milestone 17: a late refresh after the player closed banking. Dropped entirely -- do
         * not apply it to a session (there is none worth having) and above all do not open a
         * screen the player just dismissed. The next genuine open re-fetches everything.
         */
        DISCARD
    }

    /**
     * The Milestone 1 D9 rule, now complete.
     *
     * <p>Before this, the client consumed every account payload with
     * {@code setScreen(new BankScreen(payload))}, which is why a successful transaction discarded
     * grid selection, scroll position and a half-typed amount, and would have thrown a player out
     * of the Bank Box mid-drag once one existed.
     *
     * <p>The D9 note used to record one wart here: a request still in flight when the player
     * pressed Escape produced a refresh that found no banking screen and re-opened Main -- the
     * interface reappeared after they dismissed it. The payload could not distinguish a first
     * open from a late refresh, so the wart was preserved rather than guessed at. Milestone 17
     * gave the payload that one bit ({@code refresh}, stamped by the server, which alone knows
     * which flow built it), and the wart is closed: a refresh with banking closed is
     * {@link RefreshRoute#DISCARD}ED, exactly design §5.3's "the client must not invent state
     * after closing" applied to the success path. The ordered connection makes this safe against
     * the close-then-immediately-reopen race -- the late refresh (flagged) is discarded, the new
     * {@code bank.open} (unflagged) opens.
     */
    public static RefreshRoute refreshRoute(boolean bankingScreenOpen, boolean refresh) {
        if (bankingScreenOpen) return RefreshRoute.KEEP_CURRENT;
        return refresh ? RefreshRoute.DISCARD : RefreshRoute.OPEN_MAIN;
    }

    /**
     * Milestone 17, design §15.2's third clearing rule: navigating drops the status line. A
     * message describes the outcome of something done on the screen the player is leaving;
     * carrying it onto the next screen shows it beside controls it never referred to. Called by
     * every banking screen's navigation handler (Main's three destinations, each sub-screen's
     * Back) before the switch. The other two clearing rules already live where they belong --
     * {@code beginPending} and {@code applyAccountOpened}.
     */
    public static void beginNavigation(@Nullable ClientBankingSession session) {
        if (session != null) {
            session.clearLastResult();
        }
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
