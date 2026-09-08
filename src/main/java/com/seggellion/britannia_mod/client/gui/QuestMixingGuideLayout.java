package com.seggellion.britannia_mod.client.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Rowan farming questline M8 item 6: the visual mixing guide's rectangles, computed without a
 * client.
 *
 * <p>One row per step of a node's {@code help.guide}: what goes in the main hand, what goes in the
 * off hand, the gesture, what comes out, and what is handed back. The guide is presentation only --
 * it is opened by a choice carrying {@code "presentation": "help"}, which is rendered client-side
 * and never sent as a CHOOSE, so reading it cannot disturb the active objective.
 *
 * <h2>Two arrangements</h2>
 * A guide row is naturally wide: two input icons, a sentence, an arrow, an output icon and the
 * returned items. {@link RowStyle#INLINE} is that row. When the sentence column would fall below
 * {@link #MIN_GESTURE_WIDTH} the row switches to {@link RowStyle#WRAPPED}: icons on the first
 * line, the sentence wrapped underneath. Nothing is dropped and nothing is drawn outside the
 * panel at either size.
 *
 * <h2>What this deliberately does not model</h2>
 * The guide has no bucket row and no bucket column. The playbook is explicit that the mixing guide
 * does not mention the bucket, and the way to keep a thing off a screen is for the layout to have
 * nowhere to put it. {@code QuestMixingGuideLayoutTest} asserts the authored guide content the mod
 * feeds this class names no bucket.
 */
public record QuestMixingGuideLayout(
        ScreenRect screen,
        ScreenRect panel,
        ScreenRect title,
        ScreenRect body,
        int bodyWrapWidth,
        int bodyVisibleLines,
        ScreenRect list,
        List<Row> rows,
        int firstVisibleRow,
        int totalRowCount,
        int scrollMax,
        ScreenRect backButton,
        List<ScreenRect> focusRing
) {

    /** How one guide row arranges itself once the width is known. */
    public enum RowStyle {
        /** Icons and sentence on one line. */
        INLINE,
        /** Icons on the first line, sentence wrapped underneath. */
        WRAPPED
    }

    /**
     * One step of the guide.
     *
     * @param stepIndex       zero-based position in the guide
     * @param bounds          the whole row
     * @param mainHandIcon    the item held in the main hand
     * @param offHandIcon     the item held in the off hand, or empty when the step uses one hand
     * @param gesture         the sentence describing what the player does
     * @param gestureWrapWidth wrap width for {@code gesture}. Always positive.
     * @param arrow           the "produces" marker between the inputs and the result. M11 deferred
     *                        defect F13: this is now a line of text rather than a one-pixel rule,
     *                        so the marker carries a translatable symbol of its own
     * @param resultIcon      what the step produces
     * @param returnedMarker  the "handed back" marker between the result and the returned items,
     *                        so the two icon groups are told apart by a symbol and a caption rather
     *                        than by which side of the row they sit on. Empty when nothing is
     *                        returned, or when the row is too narrow to place it
     * @param returnedIcons   what comes back to the player -- both bowls, for the mixing step
     */
    public record Row(int stepIndex, RowStyle style, ScreenRect bounds, ScreenRect mainHandIcon,
                      ScreenRect offHandIcon, ScreenRect gesture, int gestureWrapWidth,
                      ScreenRect arrow, ScreenRect resultIcon, ScreenRect returnedMarker,
                      List<ScreenRect> returnedIcons, int hiddenReturned) {}

    // ---------------------------------------------------------------- constants

    public static final int ICON = QuestDialogueLayout.ICON_SIZE;
    public static final int ICON_GAP = QuestDialogueLayout.ICON_GAP;
    /**
     * Width of the two markers ("produces" and "handed back"). Widened from 10 in M11 so a
     * two-character symbol fits: the markers used to be one-pixel rules, and a rule cannot say
     * which of the two icon groups it belongs to.
     */
    public static final int ARROW_WIDTH = 12;
    public static final int COLUMN_GAP = 4;
    public static final int ROW_GAP = 3;
    public static final int ROW_PADDING = 2;

    public static final int PANEL_MAX_WIDTH = 340;
    public static final int PANEL_MAX_HEIGHT = 220;
    public static final int PANEL_MIN_WIDTH = 110;
    public static final int PANEL_MIN_HEIGHT = 70;
    public static final int PANEL_INSET = 6;

    public static final int BACK_BUTTON_WIDTH = 80;
    public static final int BACK_BUTTON_HEIGHT = 20;

    /** Below this the sentence column is unreadable inline and the row wraps instead. */
    public static final int MIN_GESTURE_WIDTH = 70;
    /** Body lines drawn above the guide before the space is given to the guide itself. */
    public static final int MAX_BODY_LINES = 4;

    // ---------------------------------------------------------------- factory

    /**
     * @param screenWidth     scaled screen width
     * @param screenHeight    scaled screen height
     * @param fontLineHeight  {@code font.lineHeight}
     * @param bodyLineCount   lines the intro body wraps to at {@link #bodyWrapWidth()}
     * @param offHandFlags    one entry per guide step: does the step use the off hand?
     * @param returnedCounts  one entry per guide step: how many items come back
     * @param scrollOffset    first visible guide row the caller wants; clamped to {@link #scrollMax()}
     */
    public static QuestMixingGuideLayout calculate(
            int screenWidth,
            int screenHeight,
            int fontLineHeight,
            int bodyLineCount,
            List<Boolean> offHandFlags,
            List<Integer> returnedCounts,
            int scrollOffset
    ) {
        int lineHeight = Math.max(1, fontLineHeight);
        ScreenRect screen = new ScreenRect(0, 0, Math.max(0, screenWidth), Math.max(0, screenHeight));
        int stepCount = offHandFlags == null ? 0 : offHandFlags.size();

        int margin = QuestDialogueLayout.margin(screenWidth);
        int panelWidth = clamp(screen.width() - (margin * 2),
                Math.min(PANEL_MIN_WIDTH, screen.width()), PANEL_MAX_WIDTH);
        int panelHeight = clamp(screen.height() - (margin * 2),
                Math.min(PANEL_MIN_HEIGHT, screen.height()), PANEL_MAX_HEIGHT);
        ScreenRect panel = new ScreenRect(
                Math.max(0, (screen.width() - panelWidth) / 2),
                Math.max(0, (screen.height() - panelHeight) / 2),
                panelWidth, panelHeight);

        int inset = panelWidth >= 180 ? PANEL_INSET : 3;
        ScreenRect content = panel.inset(inset);

        ScreenRect title = new ScreenRect(content.x(), content.y(), content.width(),
                Math.min(lineHeight, content.height()));

        int bodyTop = title.bottom() + ROW_GAP;
        int footerHeight = BACK_BUTTON_HEIGHT + ROW_GAP;
        int contentBottom = Math.max(content.y(), content.bottom() - footerHeight);

        int bodyWrapWidth = Math.max(1, content.width());
        int wantedBodyLines = clamp(Math.max(0, bodyLineCount), 0, MAX_BODY_LINES);
        int bodyRoom = Math.max(0, contentBottom - bodyTop);
        int bodyLines = Math.min(wantedBodyLines, bodyRoom / lineHeight);

        // The row arrangement depends only on the width -- the list is always the content's full
        // width -- so it can be settled before the split between the intro and the guide.
        RowStyle style = rowStyle(content.width(), maxOffHand(offHandFlags), maxReturned(returnedCounts));
        int rowHeight = style == RowStyle.INLINE
                ? ICON + (ROW_PADDING * 2)
                : ICON + lineHeight + ROW_GAP + (ROW_PADDING * 2);

        // Shed intro lines until at least one guide row fits. At 160x120 the real intro is four
        // lines, which left thirty units for a thirty-two-unit row: the loop below broke before
        // row zero, so the panel showed a title, an introduction, and no guide at all, while
        // scrolls() said it was scrollable and every offset produced the same empty list. A
        // shorter introduction is a smaller loss than the whole recipe.
        while (bodyLines > 0 && stepCount > 0
                && listTop(bodyTop, bodyLines, lineHeight) + rowHeight > contentBottom) {
            bodyLines--;
        }

        ScreenRect body = bodyLines > 0
                ? new ScreenRect(content.x(), bodyTop, bodyWrapWidth, bodyLines * lineHeight)
                : ScreenRect.EMPTY;

        int listTop = listTop(bodyTop, bodyLines, lineHeight);
        ScreenRect list = new ScreenRect(content.x(), listTop, content.width(),
                Math.max(0, contentBottom - listTop));

        ScreenRect backButton = new ScreenRect(
                content.centerX() - (Math.min(BACK_BUTTON_WIDTH, content.width()) / 2),
                Math.max(content.y(), content.bottom() - BACK_BUTTON_HEIGHT),
                Math.min(BACK_BUTTON_WIDTH, content.width()),
                Math.min(BACK_BUTTON_HEIGHT, content.height()));

        // ---- the scroll window ----
        int perScreen = Math.max(0, (list.height() + ROW_GAP) / (rowHeight + ROW_GAP));
        int scrollMax = Math.max(0, stepCount - Math.max(1, perScreen));
        int first = clamp(scrollOffset, 0, scrollMax);

        List<Row> rows = new ArrayList<>();
        int y = list.y();
        for (int index = first; index < stepCount; index++) {
            if (y + rowHeight > list.bottom()) break;
            rows.add(row(index, style, new ScreenRect(list.x(), y, list.width(), rowHeight),
                    lineHeight, isTrue(offHandFlags, index), count(returnedCounts, index)));
            y += rowHeight + ROW_GAP;
        }

        List<ScreenRect> focusRing = List.of(backButton);
        return new QuestMixingGuideLayout(screen, panel, title, body, bodyWrapWidth, bodyLines,
                list, List.copyOf(rows), first, stepCount, scrollMax, backButton, focusRing);
    }

    /**
     * The wrap width a caller should measure the intro body with before calling
     * {@link #calculate}.
     *
     * <p>The body wraps to the <b>panel's</b> content width, not the screen's. Measuring against
     * the screen produces a line count for a much wider column -- two lines where the panel needs
     * four -- and {@code bodyVisibleLines} then clips the text to the wrong number, silently
     * losing the end of the sentence. Two passes, the same shape
     * {@link QuestDialogueLayout#probeWrapWidth} uses, because the wrap width depends only on the
     * screen size.
     */
    public static int probeBodyWrapWidth(int screenWidth, int screenHeight, int fontLineHeight) {
        return calculate(screenWidth, screenHeight, fontLineHeight, 1, List.of(), List.of(), 0)
                .bodyWrapWidth();
    }

    // ---------------------------------------------------------------- helpers

    /** Top of the guide list, given how many intro lines are being kept. */
    private static int listTop(int bodyTop, int bodyLines, int lineHeight) {
        return bodyLines > 0 ? bodyTop + (bodyLines * lineHeight) + ROW_GAP : bodyTop;
    }

    private static RowStyle rowStyle(int listWidth, boolean anyOffHand, int maxReturned) {
        return inlineGestureWidth(listWidth, anyOffHand, maxReturned) >= MIN_GESTURE_WIDTH
                ? RowStyle.INLINE
                : RowStyle.WRAPPED;
    }

    /** What is left for the sentence once both inputs, the arrow, the result and the returns are placed. */
    private static int inlineGestureWidth(int listWidth, boolean anyOffHand, int maxReturned) {
        int inputs = ICON + (anyOffHand ? ICON_GAP + ICON : 0);
        // The returned group now costs its own marker as well as its icons (M11 F13).
        int outputs = ICON + (maxReturned > 0
                ? COLUMN_GAP + ARROW_WIDTH + COLUMN_GAP + (maxReturned * (ICON + ICON_GAP))
                : 0);
        return listWidth - (ROW_PADDING * 2) - inputs - COLUMN_GAP - ARROW_WIDTH - COLUMN_GAP - outputs
                - COLUMN_GAP;
    }

    private static Row row(int stepIndex, RowStyle style, ScreenRect bounds, int lineHeight,
                           boolean usesOffHand, int returnedCount) {
        int left = bounds.x() + ROW_PADDING;
        int iconTop = bounds.y() + ROW_PADDING;

        ScreenRect mainHand = new ScreenRect(left, iconTop, ICON, ICON);
        ScreenRect offHand = usesOffHand
                ? new ScreenRect(mainHand.right() + ICON_GAP, iconTop, ICON, ICON)
                : ScreenRect.EMPTY;
        int afterInputs = (usesOffHand ? offHand.right() : mainHand.right()) + COLUMN_GAP;

        // Outputs are anchored to the right edge so the arrow and the result never drift into the
        // returned items when the sentence is long.
        int returnedWidth = returnedCount > 0 ? returnedCount * (ICON + ICON_GAP) : 0;
        int rightEdge = bounds.right() - ROW_PADDING;
        int returnedLeft = Math.max(afterInputs, rightEdge - returnedWidth);
        List<ScreenRect> returned = new ArrayList<>();
        int drawn = 0;
        for (int i = 0; i < returnedCount; i++) {
            int x = returnedLeft + (i * (ICON + ICON_GAP));
            if (x + ICON > rightEdge) break;
            returned.add(new ScreenRect(x, iconTop, ICON, ICON));
            drawn++;
        }
        // M11 F13: the returned group gets a marker of its own, reserved before the result is
        // placed so the result can never end up where the marker has to go.
        int markerHeight = Math.min(lineHeight, ICON);
        int markerTop = iconTop + ((ICON - markerHeight) / 2);
        int returnedMarkerRight = returned.isEmpty() ? returnedLeft : returnedLeft - COLUMN_GAP;
        ScreenRect returnedMarker = !returned.isEmpty()
                        && returnedMarkerRight - ARROW_WIDTH >= afterInputs
                ? new ScreenRect(returnedMarkerRight - ARROW_WIDTH, markerTop, ARROW_WIDTH, markerHeight)
                : ScreenRect.EMPTY;

        int resultRight = returnedMarker.isEmpty()
                ? (returned.isEmpty() ? rightEdge : returnedLeft - COLUMN_GAP)
                : returnedMarker.x() - COLUMN_GAP;
        ScreenRect result = resultRight - ICON >= afterInputs
                ? new ScreenRect(resultRight - ICON, iconTop, ICON, ICON)
                : ScreenRect.EMPTY;
        int arrowRight = result.isEmpty() ? resultRight : result.x() - COLUMN_GAP;
        ScreenRect arrow = arrowRight - ARROW_WIDTH >= afterInputs
                ? new ScreenRect(arrowRight - ARROW_WIDTH, markerTop, ARROW_WIDTH, markerHeight)
                : ScreenRect.EMPTY;

        ScreenRect gesture;
        int gestureWrapWidth;
        if (style == RowStyle.INLINE) {
            int limit = arrow.isEmpty() ? rightEdge : arrow.x() - COLUMN_GAP;
            gestureWrapWidth = Math.max(1, limit - afterInputs);
            gesture = new ScreenRect(afterInputs, iconTop + ((ICON - lineHeight) / 2),
                    gestureWrapWidth, Math.min(lineHeight, ICON));
        } else {
            gestureWrapWidth = Math.max(1, bounds.width() - (ROW_PADDING * 2));
            gesture = new ScreenRect(left, iconTop + ICON + ROW_GAP, gestureWrapWidth, lineHeight);
        }

        return new Row(stepIndex, style, bounds, mainHand, offHand, gesture, gestureWrapWidth,
                arrow, result, returnedMarker, List.copyOf(returned),
                Math.max(0, returnedCount - drawn));
    }

    private static boolean maxOffHand(List<Boolean> flags) {
        if (flags == null) return false;
        for (Boolean flag : flags) if (Boolean.TRUE.equals(flag)) return true;
        return false;
    }

    private static int maxReturned(List<Integer> counts) {
        if (counts == null) return 0;
        int max = 0;
        for (Integer count : counts) if (count != null) max = Math.max(max, count);
        return max;
    }

    private static boolean isTrue(List<Boolean> flags, int index) {
        return flags != null && index >= 0 && index < flags.size()
                && Boolean.TRUE.equals(flags.get(index));
    }

    private static int count(List<Integer> counts, int index) {
        if (counts == null || index < 0 || index >= counts.size() || counts.get(index) == null) return 0;
        return Math.max(0, counts.get(index));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public boolean scrolls() {
        return scrollMax > 0;
    }
}
