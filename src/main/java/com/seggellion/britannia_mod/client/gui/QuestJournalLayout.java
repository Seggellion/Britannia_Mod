package com.seggellion.britannia_mod.client.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Rowan farming questline M8: every rectangle in the quest journal, computed without a client.
 *
 * <p>Same split as {@link QuestDialogueLayout} and {@code BankDialogueLayout} -- the arithmetic is
 * here so JUnit can assert it, and {@link QuestJournalScreen} draws at the result.
 *
 * <h2>What changed about the journal</h2>
 * The journal was a fixed 370x260 panel with a fixed 62-unit row, centred on the screen. At 320x240
 * scaled units -- a 1280x1024 window at GUI scale 4 -- a 370-wide panel is wider than the screen
 * and a 260-tall panel is taller than it, so the panel and every row in it were drawn partly off
 * the edges. The panel is now clamped to the screen and the rows are measured from their content.
 *
 * <h2>Rows are measured, not assumed</h2>
 * M8 item 3 puts the quest number, the next action, the ordered progress list and the
 * return-to-Rowan state in each entry, and a five-step quest needs a taller row than a one-step
 * quest. Each row is therefore sized from its own step count rather than every row taking the
 * tallest row's height, and scrolling walks the measured heights.
 *
 * <h2>Invariants</h2>
 * <ol>
 *   <li>The panel is inside the screen at every size.</li>
 *   <li>Every row, and everything in it, is inside the list viewport.</li>
 *   <li>Text wrap widths are positive.</li>
 *   <li>The text column never overlaps the Quit button.</li>
 *   <li>{@link #focusRing()} is non-empty, deterministic, and every stop is a drawn control.</li>
 * </ol>
 */
public record QuestJournalLayout(
        ScreenRect screen,
        ScreenRect panel,
        ScreenRect header,
        ScreenRect list,
        List<Row> rows,
        int firstVisibleRow,
        int visibleRowCount,
        int totalRowCount,
        int scrollMax,
        ScreenRect closeButton,
        Confirmation confirmation,
        List<Focusable> focusRing
) {

    /** What a focus ring step points at. Ordering is the tab order. */
    public enum FocusKind { ROW_QUIT, CLOSE, CONFIRM_YES, CONFIRM_NO }

    /** One stop in the keyboard focus order. {@code index} is the row index for {@link FocusKind#ROW_QUIT}. */
    public record Focusable(FocusKind kind, int index, ScreenRect bounds) {}

    /**
     * One journal entry's rectangles.
     *
     * @param entryIndex     index into the caller's quest list
     * @param bounds         the whole row
     * @param stageLabel     "Quest 3 of 5"
     * @param title          the quest name
     * @param objective      the next action, wrapped to {@link #textWrapWidth}
     * @param progressRows   one rectangle per ordered progress step that is drawn
     * @param hiddenSteps    steps the row had no room for; named in text, never silently dropped
     * @param claimBadge     the return-to-Rowan state, when the quest is waiting to be claimed
     * @param quitButton     the per-row Quit control
     * @param textWrapWidth  wrap width for {@code title} and {@code objective}. Always positive.
     */
    public record Row(int entryIndex, ScreenRect bounds, ScreenRect stageLabel, ScreenRect title,
                      ScreenRect objective, List<ScreenRect> progressRows, int hiddenSteps,
                      ScreenRect claimBadge, ScreenRect quitButton, int textWrapWidth) {}

    /**
     * The Quit confirmation (M8 item 9). Quitting a quest is not undoable from the client, so the
     * Quit button arms this instead of sending; nothing leaves the screen until Confirm.
     */
    public record Confirmation(boolean active, int entryIndex, ScreenRect panel, ScreenRect message,
                               int messageWrapWidth, ScreenRect confirmButton, ScreenRect cancelButton) {
        public static Confirmation inactive() {
            return new Confirmation(false, -1, ScreenRect.EMPTY, ScreenRect.EMPTY, 1,
                    ScreenRect.EMPTY, ScreenRect.EMPTY);
        }
    }

    // ---------------------------------------------------------------- constants

    public static final int PANEL_MAX_WIDTH = 370;
    public static final int PANEL_MAX_HEIGHT = 260;
    public static final int PANEL_MIN_WIDTH = 120;
    public static final int PANEL_MIN_HEIGHT = 80;
    public static final int PANEL_INSET = 10;

    public static final int BUTTON_HEIGHT = 20;
    public static final int CLOSE_WIDTH = 80;
    public static final int QUIT_WIDTH = 46;
    /** Below this much text column beside a Quit button, the button moves under the text instead. */
    public static final int MIN_ROW_TEXT_WIDTH = 90;

    public static final int ROW_GAP = 4;
    public static final int ROW_PADDING = 4;
    public static final int STEP_INDENT = 8;
    /** Ordered progress steps drawn in one row before the rest are summarised as a count. */
    public static final int MAX_STEPS_PER_ROW = 6;

    // ---------------------------------------------------------------- factory

    /**
     * @param screenWidth        scaled screen width
     * @param screenHeight       scaled screen height
     * @param fontLineHeight     {@code font.lineHeight}
     * @param progressStepCounts one entry per quest, holding its ordered progress step count
     * @param claimPending       one entry per quest: is it waiting to be claimed at the giver?
     * @param scrollOffset       first visible row the caller wants; clamped to {@link #scrollMax()}
     * @param confirmingIndex    the quest index whose Quit is awaiting confirmation, or -1
     */
    public static QuestJournalLayout calculate(
            int screenWidth,
            int screenHeight,
            int fontLineHeight,
            List<Integer> progressStepCounts,
            List<Boolean> claimPending,
            int scrollOffset,
            int confirmingIndex
    ) {
        int lineHeight = Math.max(1, fontLineHeight);
        List<Integer> steps = progressStepCounts == null ? List.of() : progressStepCounts;
        ScreenRect screen = new ScreenRect(0, 0, Math.max(0, screenWidth), Math.max(0, screenHeight));

        int margin = QuestDialogueLayout.margin(screenWidth);
        int panelWidth = clamp(screen.width() - (margin * 2), Math.min(PANEL_MIN_WIDTH, screen.width()),
                PANEL_MAX_WIDTH);
        int panelHeight = clamp(screen.height() - (margin * 2), Math.min(PANEL_MIN_HEIGHT, screen.height()),
                PANEL_MAX_HEIGHT);
        ScreenRect panel = new ScreenRect(
                Math.max(0, (screen.width() - panelWidth) / 2),
                Math.max(0, (screen.height() - panelHeight) / 2),
                panelWidth, panelHeight);

        int inset = panelWidth >= 200 ? PANEL_INSET : 4;
        ScreenRect content = panel.inset(inset);

        int headerHeight = Math.min(lineHeight + 4, Math.max(0, content.height()));
        ScreenRect header = new ScreenRect(content.x(), content.y(), content.width(), headerHeight);

        int footerHeight = BUTTON_HEIGHT + ROW_GAP;
        int listTop = header.bottom() + ROW_GAP;
        int listHeight = Math.max(0, content.bottom() - footerHeight - listTop);
        ScreenRect list = new ScreenRect(content.x(), listTop, content.width(), listHeight);

        ScreenRect closeButton = new ScreenRect(
                content.centerX() - (Math.min(CLOSE_WIDTH, content.width()) / 2),
                Math.max(content.y(), content.bottom() - BUTTON_HEIGHT),
                Math.min(CLOSE_WIDTH, content.width()),
                Math.min(BUTTON_HEIGHT, content.height()));

        // ---- row heights, then the scroll window that shows the most rows ----
        int[] heights = new int[steps.size()];
        for (int i = 0; i < steps.size(); i++) {
            heights[i] = rowHeight(lineHeight, steps.get(i), isTrue(claimPending, i));
        }
        int scrollMax = scrollMax(heights, list.height());
        int first = clamp(scrollOffset, 0, scrollMax);

        List<Row> rows = new ArrayList<>();
        int y = list.y();
        for (int index = first; index < heights.length; index++) {
            if (y + heights[index] > list.bottom()) break;
            rows.add(row(index, new ScreenRect(list.x(), y, list.width(), heights[index]),
                    lineHeight, steps.get(index), isTrue(claimPending, index)));
            y += heights[index] + ROW_GAP;
        }
        // A list too short for even one whole row still shows one, clipped to the viewport, rather
        // than rendering an empty panel that looks broken.
        if (rows.isEmpty() && heights.length > 0 && list.height() > 0) {
            rows.add(row(first, new ScreenRect(list.x(), list.y(), list.width(), list.height()),
                    lineHeight, steps.get(first), isTrue(claimPending, first)));
        }

        Confirmation confirmation = confirmation(confirmingIndex, panel, lineHeight, inset);

        return new QuestJournalLayout(screen, panel, header, list, List.copyOf(rows), first,
                rows.size(), heights.length, scrollMax, closeButton, confirmation,
                focusRing(rows, closeButton, confirmation));
    }

    // ---------------------------------------------------------------- helpers

    /** Stage label, title, objective, the steps that fit, and the claim badge if there is one. */
    private static int rowHeight(int lineHeight, int stepCount, boolean claimPending) {
        int lines = 3 + Math.min(Math.max(0, stepCount), MAX_STEPS_PER_ROW) + (claimPending ? 1 : 0);
        return (lines * lineHeight) + (ROW_PADDING * 2);
    }

    private static Row row(int entryIndex, ScreenRect bounds, int lineHeight, int stepCount,
                           boolean claimPending) {
        ScreenRect inner = new ScreenRect(bounds.x() + ROW_PADDING, bounds.y() + ROW_PADDING,
                Math.max(0, bounds.width() - (ROW_PADDING * 2)),
                Math.max(0, bounds.height() - (ROW_PADDING * 2)));

        boolean quitBesideText = inner.width() - QUIT_WIDTH - ROW_GAP >= MIN_ROW_TEXT_WIDTH;
        int textWidth = quitBesideText
                ? Math.max(1, inner.width() - QUIT_WIDTH - ROW_GAP)
                : Math.max(1, inner.width());
        ScreenRect quit = quitBesideText
                ? new ScreenRect(inner.right() - QUIT_WIDTH, inner.y(),
                        QUIT_WIDTH, Math.min(BUTTON_HEIGHT, inner.height()))
                : new ScreenRect(inner.x(), Math.max(inner.y(), inner.bottom() - BUTTON_HEIGHT),
                        Math.min(QUIT_WIDTH, inner.width()), Math.min(BUTTON_HEIGHT, inner.height()));

        // When the Quit button sits under the text rather than beside it, the button -- not the
        // row -- is the bottom of the text column. Clipping to the row instead is how the title
        // ends up drawn through the button on a narrow journal.
        int textBottom = quitBesideText ? inner.bottom() : quit.y() - ROW_GAP;

        // Lines are laid out top-down and each is clipped to that limit, so a row squeezed by a
        // short viewport loses its tail rather than drawing past it.
        int cursor = inner.y();
        ScreenRect stageLabel = clipLine(inner, cursor, textWidth, lineHeight, textBottom);
        cursor += lineHeight;
        ScreenRect title = clipLine(inner, cursor, textWidth, lineHeight, textBottom);
        cursor += lineHeight;
        ScreenRect objective = clipLine(inner, cursor, textWidth, lineHeight, textBottom);
        cursor += lineHeight;

        int roomForSteps = textBottom;
        List<ScreenRect> progressRows = new ArrayList<>();
        int drawn = 0;
        int wanted = Math.min(Math.max(0, stepCount), MAX_STEPS_PER_ROW);
        for (int i = 0; i < wanted; i++) {
            if (cursor + lineHeight > roomForSteps) break;
            progressRows.add(new ScreenRect(inner.x() + STEP_INDENT, cursor,
                    Math.max(1, textWidth - STEP_INDENT), lineHeight));
            cursor += lineHeight;
            drawn++;
        }
        ScreenRect claimBadge = claimPending && cursor + lineHeight <= roomForSteps
                ? new ScreenRect(inner.x(), cursor, textWidth, lineHeight)
                : ScreenRect.EMPTY;

        return new Row(entryIndex, bounds, stageLabel, title, objective, List.copyOf(progressRows),
                Math.max(0, stepCount - drawn), claimBadge, quit, textWidth);
    }

    private static ScreenRect clipLine(ScreenRect inner, int top, int width, int lineHeight,
                                       int bottomLimit) {
        if (top + lineHeight > bottomLimit) return ScreenRect.EMPTY;
        return new ScreenRect(inner.x(), top, Math.max(1, width), lineHeight);
    }

    /**
     * The largest first-visible index. Found by filling the viewport from the last row backwards,
     * because the rows are different heights and "count minus visible" would be wrong.
     */
    private static int scrollMax(int[] heights, int viewportHeight) {
        if (heights.length == 0 || viewportHeight <= 0) return 0;
        int used = 0;
        int first = heights.length;
        for (int i = heights.length - 1; i >= 0; i--) {
            int needed = used == 0 ? heights[i] : used + ROW_GAP + heights[i];
            if (needed > viewportHeight) break;
            used = needed;
            first = i;
        }
        return Math.min(Math.max(0, first), Math.max(0, heights.length - 1));
    }

    private static Confirmation confirmation(int entryIndex, ScreenRect panel, int lineHeight, int inset) {
        if (entryIndex < 0) return Confirmation.inactive();
        int width = Math.max(1, panel.width() - (inset * 2));
        int height = Math.min(panel.height(), (lineHeight * 3) + BUTTON_HEIGHT + (ROW_GAP * 3));
        ScreenRect box = new ScreenRect(panel.x() + inset,
                Math.max(panel.y(), panel.centerY() - (height / 2)), width, height);
        ScreenRect message = new ScreenRect(box.x() + ROW_GAP, box.y() + ROW_GAP,
                Math.max(1, box.width() - (ROW_GAP * 2)),
                Math.max(lineHeight, box.height() - BUTTON_HEIGHT - (ROW_GAP * 3)));

        int buttonWidth = Math.max(1, (box.width() - (ROW_GAP * 3)) / 2);
        int buttonTop = Math.max(box.y(), box.bottom() - ROW_GAP - BUTTON_HEIGHT);
        int buttonHeight = Math.min(BUTTON_HEIGHT, Math.max(0, box.bottom() - buttonTop));
        return new Confirmation(true, entryIndex, box, message, message.width(),
                new ScreenRect(box.x() + ROW_GAP, buttonTop, buttonWidth, buttonHeight),
                new ScreenRect(box.x() + (ROW_GAP * 2) + buttonWidth, buttonTop, buttonWidth, buttonHeight));
    }

    /**
     * Tab order. While the confirmation is up it is the only thing focusable -- a dialogue the
     * player can tab out of and quit a second quest behind is not a confirmation.
     */
    private static List<Focusable> focusRing(List<Row> rows, ScreenRect closeButton,
                                             Confirmation confirmation) {
        List<Focusable> ring = new ArrayList<>();
        if (confirmation.active()) {
            ring.add(new Focusable(FocusKind.CONFIRM_NO, confirmation.entryIndex(), confirmation.cancelButton()));
            ring.add(new Focusable(FocusKind.CONFIRM_YES, confirmation.entryIndex(), confirmation.confirmButton()));
            return List.copyOf(ring);
        }
        for (Row row : rows) {
            if (!row.quitButton().isEmpty()) {
                ring.add(new Focusable(FocusKind.ROW_QUIT, row.entryIndex(), row.quitButton()));
            }
        }
        ring.add(new Focusable(FocusKind.CLOSE, -1, closeButton));
        return List.copyOf(ring);
    }

    private static boolean isTrue(List<Boolean> flags, int index) {
        return flags != null && index >= 0 && index < flags.size()
                && Boolean.TRUE.equals(flags.get(index));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ---------------------------------------------------------------- queries

    public boolean scrolls() {
        return scrollMax > 0;
    }

    public boolean isEmpty() {
        return totalRowCount == 0;
    }
}
