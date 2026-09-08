package com.seggellion.britannia_mod.client.gui;

import java.util.Locale;

/**
 * Rowan farming questline M8 item 7: instructions name the key the player actually has bound.
 *
 * <h2>Why this takes a string rather than a {@code KeyMapping}</h2>
 * The binding a player currently holds is exactly {@code KeyMapping#getKey()}'s
 * {@code InputConstants.Key#getName()} -- {@code "key.keyboard.o"} by default, {@code
 * "key.keyboard.j"} after a rebind, {@code "key.mouse.middle"} for a mouse button, and
 * {@code "key.keyboard.unknown"} when the player has cleared it. That name is also the translation
 * key Minecraft itself uses to display the key, which is what makes this a pure function: the
 * screen reads the one string off the live {@code KeyMapping} and everything after that is
 * arithmetic on text, testable with a rebound key and with no key at all.
 *
 * <p>Building the display through {@code KeyMapping#getTranslatedKeyMessage()} directly would have
 * been shorter and untestable -- it returns a {@code Component} built by a supplier that calls
 * into GLFW for scancode names, so no unit test in this project could construct one.
 *
 * <h2>The rule</h2>
 * A hardcoded letter in an instruction is wrong the moment anybody rebinds, and it is wrong for
 * every player who cleared the binding. {@link #openJournalMessageKey} therefore switches to a
 * sentence that names the <i>control</i> rather than a key when nothing is bound, so the player is
 * told where to go and not told to press a key that does nothing.
 */
public final class QuestKeyPrompt {

    private QuestKeyPrompt() {
    }

    /** What {@code InputConstants.Key#getName()} returns for a binding the player has cleared. */
    public static final String UNBOUND_KEY_NAME = "key.keyboard.unknown";

    /** The translation key naming the control itself, used when no key is bound. */
    public static final String OPEN_JOURNAL_BINDING = "key.britannia_mod.open_skills";

    /**
     * True when the binding names no key.
     *
     * <p>A blank name counts: it is not a key the player can press either, and telling somebody to
     * "press " is worse than telling them to bind something.
     */
    public static boolean isUnbound(String boundKeyName) {
        if (boundKeyName == null) return true;
        String trimmed = boundKeyName.trim();
        return trimmed.isEmpty() || trimmed.toLowerCase(Locale.ROOT).equals(UNBOUND_KEY_NAME);
    }

    /**
     * Which sentence to show for "open your journal", given the current binding.
     *
     * @param boundKeyName {@code KeyMapping#getKey().getName()} of the live binding
     */
    public static String openJournalMessageKey(String boundKeyName) {
        return isUnbound(boundKeyName)
                ? QuestScreenText.KEY_OPEN_JOURNAL_UNBOUND
                : QuestScreenText.KEY_OPEN_JOURNAL;
    }

    /**
     * The translation key to substitute into that sentence: the key's own name when one is bound,
     * and the control's name when none is.
     *
     * <p>Never a literal letter. This is the whole point of the class -- the returned key is
     * derived from the binding that was passed in, so a rebind changes the instruction with no
     * code change and no second place to update.
     */
    public static String openJournalArgumentKey(String boundKeyName) {
        return isUnbound(boundKeyName) ? OPEN_JOURNAL_BINDING : boundKeyName.trim();
    }
}
