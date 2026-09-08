package com.seggellion.britannia_mod.client.gui;

import java.util.List;
import java.util.Locale;

/**
 * Rowan farming questline M8 item 10: every player-facing string the quest screens say, as
 * translation keys.
 *
 * <p>Plain data with no client types, the same shape as {@code BankStatusPresenter}, so JUnit can
 * walk {@link #ALL_KEYS} and assert that {@code en_us.json} defines every one of them. A key that
 * is only ever written inline in a screen is a key nothing can check.
 *
 * <h2>Symbols, not only colour</h2>
 * A done step and a pending step differ by a marker as well as a colour ({@link #PROGRESS_DONE}
 * and {@link #PROGRESS_PENDING}), and the three reward sections carry their own headings rather
 * than being told apart by tint. Colour is never the only channel.
 *
 * <h2>Item names</h2>
 * {@link #itemNameCandidates(String)} is how a reward icon gets a real name. The screens resolve
 * an item id through the item registry at runtime and fall back to {@link #ITEM_UNKNOWN} -- never
 * to the raw id. The pure candidate list exists so a test can prove that every id the authored
 * quest content names has a translation in {@code en_us.json}, which is what stops
 * {@code britannia_mod:gold_coin} appearing on screen as itself.
 */
public final class QuestScreenText {

    private QuestScreenText() {
    }

    private static final String SCREEN = "screen.britannia_mod.quest.";
    private static final String MESSAGE = "message.britannia_mod.quest.";

    // ---- dialogue frame -------------------------------------------------

    public static final String TITLE_FALLBACK = SCREEN + "title";
    /** "Quest %1$s of %2$s". */
    public static final String STAGE = SCREEN + "stage";
    public static final String STAGE_UNKNOWN = SCREEN + "stage.unknown";
    public static final String FAREWELL = SCREEN + "farewell";
    public static final String NEXT = SCREEN + "next";
    public static final String DIRECTIONS = SCREEN + "directions";
    public static final String SCROLL_HINT = SCREEN + "scroll_hint";

    // ---- reward and keep panels (item 2) --------------------------------

    public static final String REWARDS_ON_ACCEPT = SCREEN + "rewards.on_accept";
    public static final String REWARDS_ON_COMPLETE = SCREEN + "rewards.on_complete";
    public static final String REWARDS_KEEP = SCREEN + "rewards.keep";
    /** "%1$s (%2$s)" -- a section heading carrying its own total. */
    public static final String REWARDS_HEADING_COUNT = SCREEN + "rewards.heading_count";
    /** "+%s more" for items the grid had no room to draw. */
    public static final String REWARDS_MORE = SCREEN + "rewards.more";
    /** "%1$s x%2$s" -- an icon's tooltip. */
    public static final String REWARDS_TOOLTIP = SCREEN + "rewards.tooltip";

    // ---- journal (item 3) -----------------------------------------------

    public static final String JOURNAL_TITLE = SCREEN + "journal.title";
    /** "%1$s of %2$s" -- how many entries the panel is showing. */
    public static final String JOURNAL_COUNT = SCREEN + "journal.count";
    public static final String JOURNAL_EMPTY = SCREEN + "journal.empty";
    public static final String JOURNAL_CLOSE = SCREEN + "journal.close";
    public static final String JOURNAL_QUIT = SCREEN + "journal.quit";
    public static final String JOURNAL_OBJECTIVE = SCREEN + "journal.objective";
    public static final String JOURNAL_OBJECTIVE_NONE = SCREEN + "journal.objective.none";
    /** "+%s more steps" when a row cannot draw the whole ordered list. */
    public static final String JOURNAL_MORE_STEPS = SCREEN + "journal.more_steps";
    /** "Return to %s to claim your reward" -- the return-to-Rowan state. */
    public static final String JOURNAL_RETURN_TO = SCREEN + "journal.return_to";
    public static final String JOURNAL_GIVER_UNKNOWN = SCREEN + "journal.giver.unknown";

    // ---- quit confirmation (item 9) --------------------------------------

    /** "%s will be removed from your journal. This cannot be undone." */
    public static final String QUIT_CONFIRM_MESSAGE = SCREEN + "quit.confirm.message";
    public static final String QUIT_CONFIRM_YES = SCREEN + "quit.confirm.yes";
    public static final String QUIT_CONFIRM_NO = SCREEN + "quit.confirm.no";

    // ---- help and the mixing guide (items 5 and 6) -----------------------

    public static final String HELP_TITLE_FALLBACK = SCREEN + "help.title";
    public static final String HELP_BACK = SCREEN + "help.back";
    public static final String GUIDE_MAIN_HAND = SCREEN + "guide.main_hand";
    public static final String GUIDE_OFF_HAND = SCREEN + "guide.off_hand";
    public static final String GUIDE_RESULT = SCREEN + "guide.result";
    public static final String GUIDE_RETURNED = SCREEN + "guide.returned";
    public static final String GUIDE_STEP = SCREEN + "guide.step";
    public static final String GUIDE_EMPTY = SCREEN + "guide.empty";

    // ---- progress markers -------------------------------------------------

    /** Drawn before a completed step. A symbol, so colour is never the only signal. */
    public static final String PROGRESS_DONE = SCREEN + "progress.done";
    /** Drawn before a step that is still pending. */
    public static final String PROGRESS_PENDING = SCREEN + "progress.pending";
    /** Separates the marker from the step's label: "%1$s %2$s". */
    public static final String PROGRESS_LINE = SCREEN + "progress.line";

    // ---- item naming ------------------------------------------------------

    public static final String ITEM_UNKNOWN = SCREEN + "item.unknown";

    // ---- quiet objective notice and attribution (item 4) -------------------

    public static final String OBJECTIVE_TOAST_TITLE = MESSAGE + "objective.title";
    /** The action-bar line: the step that just completed. */
    public static final String OBJECTIVE_ADVANCED = MESSAGE + "objective.advanced";
    /** "Return to %s to claim" -- shown once the last objective is done. */
    public static final String OBJECTIVE_READY_TO_CLAIM = MESSAGE + "objective.ready_to_claim";
    /** Used only when neither the response nor the journal names a quest giver. */
    public static final String GIVER_UNKNOWN = MESSAGE + "giver.unknown";

    // ---- keybinding prompts (item 7) ---------------------------------------

    /**
     * The bound key and what pressing it really does, with the player's current binding
     * substituted.
     *
     * <p>It said "Press %s to open your journal", which the key does not do:
     * {@code Keybinds.OPEN_SKILL_SCREEN} opens {@code MenuScreen}, and the journal is one click
     * further on, behind that screen's "Quests" link. Nothing on the route is renamed here -- the
     * sentence is.
     */
    public static final String KEY_OPEN_JOURNAL = MESSAGE + "key.open_journal";
    /** Shown when the player has cleared the binding: names the control, not a letter. */
    public static final String KEY_OPEN_JOURNAL_UNBOUND = MESSAGE + "key.open_journal.unbound";

    // ---- item 8: the situations that had no message ------------------------

    public static final String MIX_SWAP_HANDS = MESSAGE + "mix.swap_hands";
    public static final String MIX_WRONG_BOWL = MESSAGE + "mix.wrong_bowl";
    /** "Hold %s in your off hand." -- the half of a mix that is not in hand, named. */
    public static final String MIX_MISSING_OFF_HAND = MESSAGE + "mix.missing_off_hand";
    public static final String MIX_WRONG_DIRT = MESSAGE + "mix.wrong_dirt";
    public static final String WATER_FLOWING = MESSAGE + "water.flowing";
    /**
     * The one thing a refused water source actually knows: permission. There is deliberately no
     * companion "a bucket only fills at the Water Well" key -- {@code WaterSourceAccessPolicy} has
     * a single denial reason, so a second message could only be guessed from the item in hand, and
     * was wrong in both directions.
     */
    public static final String WATER_PROTECTED = MESSAGE + "water.protected";
    public static final String PENDING_CONFIRMATION = MESSAGE + "pending_confirmation";
    public static final String PLOT_OCCUPIED = MESSAGE + "plot.occupied";
    public static final String PLOT_EXPIRED = MESSAGE + "plot.expired";
    public static final String CROP_LOST = MESSAGE + "crop.lost";
    public static final String INVENTORY_FULL_DROPPED = MESSAGE + "inventory.full_dropped";

    /**
     * Every key above. The localization test walks this, so a key added to the class without a
     * line in {@code en_us.json} fails the build rather than reaching a player as its own name.
     */
    public static final List<String> ALL_KEYS = List.of(
            TITLE_FALLBACK, STAGE, STAGE_UNKNOWN, FAREWELL, NEXT, DIRECTIONS, SCROLL_HINT,
            REWARDS_ON_ACCEPT, REWARDS_ON_COMPLETE, REWARDS_KEEP, REWARDS_HEADING_COUNT, REWARDS_MORE,
            REWARDS_TOOLTIP,
            JOURNAL_TITLE, JOURNAL_COUNT, JOURNAL_EMPTY, JOURNAL_CLOSE, JOURNAL_QUIT,
            JOURNAL_OBJECTIVE, JOURNAL_OBJECTIVE_NONE, JOURNAL_MORE_STEPS, JOURNAL_RETURN_TO,
            JOURNAL_GIVER_UNKNOWN,
            QUIT_CONFIRM_MESSAGE, QUIT_CONFIRM_YES, QUIT_CONFIRM_NO,
            HELP_TITLE_FALLBACK, HELP_BACK, GUIDE_MAIN_HAND, GUIDE_OFF_HAND,
            GUIDE_RESULT, GUIDE_RETURNED, GUIDE_STEP, GUIDE_EMPTY,
            PROGRESS_DONE, PROGRESS_PENDING, PROGRESS_LINE, ITEM_UNKNOWN,
            OBJECTIVE_TOAST_TITLE, OBJECTIVE_ADVANCED, OBJECTIVE_READY_TO_CLAIM, GIVER_UNKNOWN,
            KEY_OPEN_JOURNAL, KEY_OPEN_JOURNAL_UNBOUND,
            MIX_SWAP_HANDS, MIX_WRONG_BOWL, MIX_MISSING_OFF_HAND, MIX_WRONG_DIRT, WATER_FLOWING,
            WATER_PROTECTED, PENDING_CONFIRMATION, PLOT_OCCUPIED, PLOT_EXPIRED, CROP_LOST,
            INVENTORY_FULL_DROPPED
    );

    /** The heading key for one reward section. */
    public static String sectionHeading(QuestDialogueLayout.SectionKind kind) {
        return switch (kind) {
            case ON_ACCEPT -> REWARDS_ON_ACCEPT;
            case ON_COMPLETE -> REWARDS_ON_COMPLETE;
            case KEEP -> REWARDS_KEEP;
        };
    }

    public static String progressMarker(boolean done) {
        return done ? PROGRESS_DONE : PROGRESS_PENDING;
    }

    /**
     * The translation keys an item id could be named by, most likely first.
     *
     * <p>Mirrors vanilla's {@code Item#getDescriptionId} / {@code Block#getDescriptionId}: the mod
     * awards both plain items and block items, and only the registry knows which a given id is, so
     * the pure form offers both and the test accepts either. Returns an empty list for an id it
     * cannot parse, which is the signal to use {@link #ITEM_UNKNOWN} -- the raw id is never a
     * label.
     */
    public static List<String> itemNameCandidates(String itemId) {
        if (itemId == null) return List.of();
        String trimmed = itemId.trim();
        if (trimmed.isEmpty()) return List.of();

        String namespace = "minecraft";
        String path = trimmed;
        int colon = trimmed.indexOf(':');
        if (colon >= 0) {
            namespace = trimmed.substring(0, colon).trim();
            path = trimmed.substring(colon + 1).trim();
        }
        if (namespace.isEmpty() || path.isEmpty()) return List.of();

        String flatPath = path.toLowerCase(Locale.ROOT).replace('/', '.');
        return List.of("item." + namespace + "." + flatPath, "block." + namespace + "." + flatPath);
    }
}
