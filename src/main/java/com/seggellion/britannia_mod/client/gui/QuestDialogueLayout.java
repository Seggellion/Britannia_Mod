package com.seggellion.britannia_mod.client.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Rowan farming questline M8: every rectangle on the quest dialogue screen, computed without a
 * client.
 *
 * <h2>Why this is a separate class from the frame that draws it</h2>
 * The same reason {@code BankDialogueLayout} is: a {@code Screen} cannot be instantiated by either
 * test harness this project has, so anything worth asserting has to live outside one. The numbers
 * are computed here and {@link QuestDialogueFrame} does nothing but draw at them.
 *
 * <p>GUI scale is not a parameter. Minecraft applies it before a screen sees a coordinate --
 * {@code Window#setGuiScale} divides the framebuffer by the scale and rounds <i>up</i> -- so
 * {@code screenWidth}/{@code screenHeight} here are already scaled units. "Test at GUI scale 4"
 * and "test at a narrow width" are the same test with different numbers.
 *
 * <h2>The defect this replaces</h2>
 * {@code com.seggellion.britannia_mod.dialogue.DialogueLayout}, which the quest dialogue screen
 * used until M8, computed
 * <pre>{@code   maxTextWidth = buttonStartX - textX - 20 = screenWidth - 343 }</pre>
 * and handed it straight to {@code GuiGraphics#drawWordWrap}. Below 343 scaled units that is
 * negative. {@code BankDialogueLayout} recorded the defect and deliberately left the class alone
 * because fixing it would have moved the quest and service screens' geometry; M8 owned the quest
 * screen's geometry, so the quest path computes its own numbers here.
 *
 * <p>M11 then fixed the shared class itself, by the same two rules this one follows: drop the
 * portrait column before squashing the text, and clamp the wrap width positive regardless. The
 * arithmetic quoted above is what that class <i>used to</i> compute, and
 * {@code ServiceDialogueLayoutTest} is the regression guard for it.
 *
 * <p>That band is reachable, not theoretical. {@code Window#calculateScale} refuses to scale up
 * past the point where the scaled size would drop below 320x240 -- but the <b>Force Unicode
 * Font</b> option rounds an odd scale up to the next even one <i>after</i> that check, which walks
 * straight through the floor. A 1024x768 window with Force Unicode on is 256x192 units, and a
 * 1280x1024 window at GUI scale 4 is 320x256 even with it off. Both produce a negative wrap width
 * in the legacy class. See {@code GuiScaleRuleTest} for the derivation.
 *
 * <h2>The invariants</h2>
 * <ol>
 *   <li>Every rectangle this returns is inside the screen, and every parchment rectangle is inside
 *       the parchment.</li>
 *   <li>{@link TextBlock#wrapWidth()} is always positive.</li>
 *   <li>The body column never overlaps the choice column or the reward panel.</li>
 *   <li>Choices that do not fit scroll; they never overflow the parchment.</li>
 *   <li>{@link #focusRing()} is non-empty and every entry is a real, visible control.</li>
 * </ol>
 * They are held by degrading the design rather than by shrinking everything: the portrait is
 * dropped first, then the side-by-side arrangement itself. See {@link Mode}.
 */
public record QuestDialogueLayout(
        Mode mode,
        ScreenRect screen,
        ScreenRect parchment,
        PortraitBlock portrait,
        ScreenRect stageLine,
        TextBlock body,
        ChoiceBlock choices,
        RewardBlock rewards,
        List<Focusable> focusRing
) {

    /** How the parchment's three columns are arranged once the width is known. */
    public enum Mode {
        /** Portrait, body and choices side by side. The design at its full size. */
        FULL,
        /** Portrait dropped; body and choices still side by side. */
        COMPACT,
        /** One column: body above the choices, both the full width of the parchment. */
        STACKED
    }

    /** What a focus ring step points at. Ordering is the tab order. */
    public enum FocusKind { CHOICE, REWARD_SECTION }

    /** One stop in the keyboard focus order. {@code index} is the index within its kind. */
    public record Focusable(FocusKind kind, int index, ScreenRect bounds) {}

    /** The quest giver's portrait column, or {@link #hidden()} when there is no room for it. */
    public record PortraitBlock(boolean visible, ScreenRect bounds, int nameCenterX, int nameY,
                                int professionY) {
        public static PortraitBlock hidden() {
            return new PortraitBlock(false, ScreenRect.EMPTY, 0, 0, 0);
        }
    }

    /**
     * A wrapped text column and how much of it is on screen.
     *
     * @param bounds        the drawable area
     * @param wrapWidth     the width handed to {@code drawWordWrap}. Always positive.
     * @param lineHeight    the font's line height, echoed so a caller need not carry it separately
     * @param totalLines    lines the text wraps to at {@code wrapWidth}
     * @param visibleLines  lines that fit in {@code bounds}. At least one.
     * @param scrollMax     the largest first-visible line index; zero when everything fits
     */
    public record TextBlock(ScreenRect bounds, int wrapWidth, int lineHeight, int totalLines,
                            int visibleLines, int scrollMax) {
        public boolean scrolls() {
            return scrollMax > 0;
        }

        /** Top of the line at {@code lineIndex}, counting from the first visible line. */
        public int lineY(int lineIndex) {
            return bounds.y() + (lineIndex * lineHeight);
        }
    }

    /**
     * The stack of selectable actions.
     *
     * @param bounds        the whole column, clipped to what is visible
     * @param visibleCount  how many buttons are drawn. At least one.
     * @param totalCount    how many the node offers
     * @param scrollMax     the largest first-visible choice index
     * @param scrollHint    where to say the column scrolls, or {@link ScreenRect#EMPTY} when it
     *                      does not. Reserved <b>before</b> the stack is sized, for the reason the
     *                      body's last line is: an affordance with nowhere to go is drawn outside
     *                      the parchment or not at all, and a hidden choice with no mark on screen
     *                      is a choice nobody finds.
     */
    public record ChoiceBlock(ScreenRect bounds, int itemWidth, int itemHeight, int gap,
                              int visibleCount, int totalCount, int scrollMax,
                              ScreenRect scrollHint) {
        public boolean scrolls() {
            return scrollMax > 0;
        }

        /** Bounds of the {@code slot}-th drawn button, counting from the top of the visible run. */
        public ScreenRect item(int slot) {
            return new ScreenRect(bounds.x(), bounds.y() + (slot * (itemHeight + gap)),
                    itemWidth, itemHeight);
        }
    }

    /**
     * The three labelled icon sections below the parchment: what the player gets on accepting, what
     * is waiting when the quest ends, and what they keep either way.
     *
     * <p>Hidden entirely rather than squeezed when the screen is too short for it -- the panel is
     * a preview, and a preview drawn over the choices is worse than no preview. The same
     * information is always in the journal.
     */
    public record RewardBlock(boolean visible, ScreenRect panel, List<Section> sections) {
        public static RewardBlock hidden() {
            return new RewardBlock(false, ScreenRect.EMPTY, List.of());
        }

        /**
         * @param kind             which of the three sections this is
         * @param bounds           the whole section including its label
         * @param label            where the section's heading is drawn
         * @param icons            the grid the item icons are laid out in
         * @param iconCount        how many items the section holds in total
         * @param visibleIconCount how many of them fit in {@code icons}; the rest are counted in
         *                         the heading rather than drawn outside the grid
         * @param perRow           icons that fit on one row of {@code icons}
         */
        public record Section(SectionKind kind, ScreenRect bounds, ScreenRect label,
                              ScreenRect icons, int iconCount, int visibleIconCount, int perRow) {

            /**
             * Items the grid had no room for. The heading carries the total either way, so a
             * clipped section still tells the player how much is really there -- a count in text,
             * not a truncated row of pictures that silently lies.
             */
            public int overflowCount() {
                return Math.max(0, iconCount - visibleIconCount);
            }

            /** Bounds of icon {@code index}, wrapping to a new row every {@link #perRow()}. */
            public ScreenRect icon(int index) {
                if (perRow <= 0 || index < 0 || index >= visibleIconCount) return ScreenRect.EMPTY;
                int column = index % perRow;
                int row = index / perRow;
                return new ScreenRect(
                        icons.x() + (column * (ICON_SIZE + ICON_GAP)),
                        icons.y() + (row * (ICON_SIZE + ICON_GAP)),
                        ICON_SIZE, ICON_SIZE
                );
            }
        }
    }

    /** The three reward sections, in the order they are drawn. */
    public enum SectionKind {
        /** Granted the moment the player accepts. */
        ON_ACCEPT,
        /** Waiting at the quest giver when the objectives are done. */
        ON_COMPLETE,
        /** Not taken back when the quest ends. */
        KEEP
    }

    // ---------------------------------------------------------------- constants

    /** The parchment banner at its full height, matching the existing dialogue art. */
    public static final int PARCHMENT_MAX_HEIGHT = 134;
    /** Below this the banner is not worth drawing content in, and the reward panel is dropped. */
    public static final int PARCHMENT_MIN_HEIGHT = 60;
    /** Breathing room inside the parchment. */
    public static final int PARCHMENT_INSET = 5;
    /** Vertical gap between the parchment and the reward panel. */
    public static final int SECTION_GAP = 4;

    /** The visible portrait square, after the existing 10px crop on each side of the 108px art. */
    public static final int PORTRAIT_SIZE = 88;
    /** Left offset of the portrait inside the outer margin, so a wide screen matches the legacy 30. */
    public static final int PORTRAIT_INDENT = 10;
    /** Gap between the portrait column and the body text. */
    public static final int PORTRAIT_TEXT_GAP = 20;

    public static final int CHOICE_HEIGHT = 20;
    public static final int CHOICE_GAP = 4;
    public static final int MAX_CHOICE_WIDTH = 140;
    public static final int MIN_CHOICE_WIDTH = 64;
    /** Gap between the body column and the choice column. */
    public static final int COLUMN_GAP = 10;

    /**
     * Below this much room a side-by-side body column wraps to two or three words per line, which
     * is worse than losing the arrangement. The portrait goes first, then the arrangement itself.
     *
     * <p>120 units is about twenty characters of the UO font. Chosen against the legacy class for
     * scale: at a 427-unit width {@code DialogueLayout} produced an 84-unit column, and at 342 it
     * produced a negative one.
     */
    public static final int MIN_BODY_WIDTH = 120;

    public static final int ICON_SIZE = 16;
    public static final int ICON_GAP = 2;
    /** Label plus one row of icons plus the section's own padding. */
    public static final int REWARD_MIN_HEIGHT = 38;
    public static final int REWARD_MAX_HEIGHT = 110;
    public static final int REWARD_SECTION_GAP = 6;

    // ---------------------------------------------------------------- factory

    /**
     * @param screenWidth      scaled screen width, as a {@code Screen} sees it
     * @param screenHeight     scaled screen height
     * @param fontLineHeight   {@code font.lineHeight}; the only client-derived number this needs
     * @param hasPortrait      whether the view has a quest giver to draw
     * @param hasProfession    whether a profession label sits under the name
     * @param hasStageLine     whether a "Quest 3 of 5" line is drawn above the body
     * @param choiceCount      selectable actions the node offers; 0 becomes a single farewell slot
     * @param bodyLineCount    lines the body wraps to. Callers measure this with
     *                         {@link #probeWrapWidth} first, then pass the count back here.
     * @param onAcceptIcons    items granted on accept
     * @param onCompleteIcons  items waiting on completion
     * @param keepIcons        items the player keeps regardless
     */
    public static QuestDialogueLayout calculate(
            int screenWidth,
            int screenHeight,
            int fontLineHeight,
            boolean hasPortrait,
            boolean hasProfession,
            boolean hasStageLine,
            int choiceCount,
            int bodyLineCount,
            int onAcceptIcons,
            int onCompleteIcons,
            int keepIcons
    ) {
        int lineHeight = Math.max(1, fontLineHeight);
        int margin = margin(screenWidth);
        ScreenRect screen = new ScreenRect(0, 0, Math.max(0, screenWidth), Math.max(0, screenHeight));

        // Never zero: a farewell button is still a control the focus ring has to land on.
        int totalChoices = Math.max(1, choiceCount);
        int totalRewardIcons = Math.max(0, onAcceptIcons) + Math.max(0, onCompleteIcons)
                + Math.max(0, keepIcons);

        // ---- vertical split: parchment on top, reward panel underneath ----
        // M11 deferred defect F9: the panel is FITTED to the icons it holds, not given every unit
        // left over. It used to take min(REWARD_MAX_HEIGHT, whatever remained), so a three-item
        // preview on a 1080-unit screen was a 110-unit band holding one 16-unit row of icons and 70
        // units of nothing -- and the dead space grew with the screen, because the parchment is
        // capped at PARCHMENT_MAX_HEIGHT and everything below it went to the panel.
        int panelWidth = Math.max(0, screenWidth - (margin * 2));
        int wantedRewardHeight = clamp(
                fittedRewardHeight(panelWidth, lineHeight, onAcceptIcons, onCompleteIcons, keepIcons),
                REWARD_MIN_HEIGHT, REWARD_MAX_HEIGHT);

        int parchmentHeight;
        RewardBlock rewards;
        int rewardCandidate = Math.min(PARCHMENT_MAX_HEIGHT,
                screenHeight - wantedRewardHeight - SECTION_GAP - margin);
        if (totalRewardIcons > 0 && rewardCandidate >= PARCHMENT_MIN_HEIGHT) {
            parchmentHeight = rewardCandidate;
            int panelTop = parchmentHeight + SECTION_GAP;
            // The candidate above already left room for the whole fitted height, so the min is a
            // floor against arithmetic rather than a squeeze.
            int panelHeight = Math.min(wantedRewardHeight, screenHeight - panelTop - margin);
            ScreenRect panel = new ScreenRect(margin, panelTop, panelWidth, panelHeight);
            rewards = rewardBlock(panel, lineHeight, onAcceptIcons, onCompleteIcons, keepIcons);
        } else {
            parchmentHeight = Math.max(1, Math.min(PARCHMENT_MAX_HEIGHT, screenHeight - margin));
            rewards = RewardBlock.hidden();
        }
        ScreenRect parchment = new ScreenRect(0, 0, screen.width(), parchmentHeight);
        ScreenRect content = new ScreenRect(margin, PARCHMENT_INSET,
                Math.max(0, screen.width() - (margin * 2)),
                Math.max(0, parchmentHeight - (PARCHMENT_INSET * 2)));

        // ---- horizontal split: pick the widest arrangement that still leaves a usable column ----
        int choiceWidth = clamp((screen.width() - (margin * 2)) / 3, MIN_CHOICE_WIDTH, MAX_CHOICE_WIDTH);
        int choiceX = Math.max(margin, screen.width() - margin - choiceWidth);

        int portraitX = margin + PORTRAIT_INDENT;
        int bodyXWithPortrait = portraitX + PORTRAIT_SIZE + PORTRAIT_TEXT_GAP;
        int widthWithPortrait = choiceX - COLUMN_GAP - bodyXWithPortrait;
        int widthWithoutPortrait = choiceX - COLUMN_GAP - margin;

        Mode mode;
        if (hasPortrait && widthWithPortrait >= MIN_BODY_WIDTH
                && PORTRAIT_SIZE + (PARCHMENT_INSET * 2) <= parchmentHeight) {
            mode = Mode.FULL;
        } else if (widthWithoutPortrait >= MIN_BODY_WIDTH) {
            mode = Mode.COMPACT;
        } else {
            mode = Mode.STACKED;
        }

        int stageHeight = hasStageLine ? lineHeight + 2 : 0;

        PortraitBlock portrait = PortraitBlock.hidden();
        if (mode == Mode.FULL) {
            int labelLines = 1 + (hasProfession ? 1 : 0);
            int columnHeight = PORTRAIT_SIZE + 3 + (labelLines * lineHeight);
            int portraitY = Math.max(PARCHMENT_INSET, (parchmentHeight - columnHeight) / 2);
            // A tall label block must not push the portrait through the bottom of the parchment.
            portraitY = Math.min(portraitY,
                    Math.max(PARCHMENT_INSET, parchmentHeight - PARCHMENT_INSET - columnHeight));
            int nameY = portraitY + PORTRAIT_SIZE + 3;
            portrait = new PortraitBlock(true,
                    new ScreenRect(portraitX, portraitY, PORTRAIT_SIZE, PORTRAIT_SIZE),
                    portraitX + (PORTRAIT_SIZE / 2), nameY,
                    hasProfession ? nameY + lineHeight + 1 : nameY);
        }

        int bodyX = mode == Mode.FULL ? bodyXWithPortrait : margin;
        int bodyRightLimit = mode == Mode.STACKED ? content.right() : choiceX - COLUMN_GAP;
        // The floor is 1, not MIN_BODY_WIDTH: a cramped column is recoverable, and text handed a
        // negative wrap width -- the legacy defect -- is not.
        int bodyWidth = Math.max(1, bodyRightLimit - bodyX);
        int bodyTop = content.y() + stageHeight;

        ScreenRect stageLine = hasStageLine
                ? new ScreenRect(bodyX, content.y(), bodyWidth, lineHeight)
                : ScreenRect.EMPTY;

        int bodyBottomLimit;
        ChoiceBlock choices;
        if (mode == Mode.STACKED) {
            // Choices sit under the body. Reserve one body line before giving the rest away, so a
            // stacked screen still says something rather than being all buttons.
            int roomForChoices = content.bottom() - (bodyTop + lineHeight + SECTION_GAP);
            int hint = hintHeight(roomForChoices, totalChoices, lineHeight);
            int visible = visibleChoices(roomForChoices - hint, totalChoices);
            int stackHeight = (visible * (CHOICE_HEIGHT + CHOICE_GAP)) - CHOICE_GAP;
            int choicesTop = Math.max(bodyTop + lineHeight + SECTION_GAP,
                    content.bottom() - stackHeight - hint);
            int stackedWidth = Math.max(MIN_CHOICE_WIDTH, content.width());
            ScreenRect bounds = new ScreenRect(content.x(), choicesTop, stackedWidth, stackHeight);
            choices = new ChoiceBlock(bounds, stackedWidth, CHOICE_HEIGHT, CHOICE_GAP, visible,
                    totalChoices, Math.max(0, totalChoices - visible),
                    scrollHint(bounds, hint, lineHeight, content.bottom()));
            bodyBottomLimit = choicesTop - SECTION_GAP;
        } else {
            int roomForChoices = content.height();
            int hint = hintHeight(roomForChoices, totalChoices, lineHeight);
            int visible = visibleChoices(roomForChoices - hint, totalChoices);
            int stackHeight = (visible * (CHOICE_HEIGHT + CHOICE_GAP)) - CHOICE_GAP;
            int choicesTop = clamp((parchmentHeight - stackHeight - hint) / 2,
                    content.y(), Math.max(content.y(), content.bottom() - stackHeight - hint));
            ScreenRect bounds = new ScreenRect(choiceX, choicesTop, choiceWidth, stackHeight);
            choices = new ChoiceBlock(bounds, choiceWidth, CHOICE_HEIGHT, CHOICE_GAP, visible,
                    totalChoices, Math.max(0, totalChoices - visible),
                    scrollHint(bounds, hint, lineHeight, content.bottom()));
            bodyBottomLimit = content.bottom();
        }

        int bodyHeight = Math.max(lineHeight, bodyBottomLimit - bodyTop);
        int visibleLines = Math.max(1, bodyHeight / lineHeight);
        int totalLines = Math.max(1, bodyLineCount);
        TextBlock body = new TextBlock(
                new ScreenRect(bodyX, bodyTop, bodyWidth, Math.min(bodyHeight, visibleLines * lineHeight)),
                bodyWidth, lineHeight, totalLines, Math.min(visibleLines, totalLines),
                Math.max(0, totalLines - visibleLines));

        return new QuestDialogueLayout(mode, screen, parchment, portrait, stageLine, body, choices,
                rewards, focusRing(choices, rewards));
    }

    /**
     * The wrap width a caller should measure the body with before calling {@link #calculate}.
     *
     * <p>Two passes, the same shape the screen already used: the wrap width depends only on the
     * width, and the line count depends on the wrap width, so measuring once with this and then
     * calculating once with the result is exact rather than iterative.
     */
    public static int probeWrapWidth(int screenWidth, int screenHeight, int fontLineHeight,
                                     boolean hasPortrait, int choiceCount) {
        return calculate(screenWidth, screenHeight, fontLineHeight, hasPortrait, false, true,
                choiceCount, 1, 0, 0, 0).body().wrapWidth();
    }

    /** Outer margin. Scales with the screen so a 160-unit window is not all margin. */
    public static int margin(int screenWidth) {
        return clamp(screenWidth / 32, 4, 20);
    }

    // ---------------------------------------------------------------- helpers

    /** How many whole buttons fit in {@code room}. Never zero: one control is always drawn. */
    private static int visibleChoices(int room, int totalChoices) {
        return clamp((room + CHOICE_GAP) / (CHOICE_HEIGHT + CHOICE_GAP), 1, totalChoices);
    }

    /**
     * The vertical space to hold back for the scroll affordance: one line and a unit of air, and
     * only when the column is going to scroll after holding it back.
     *
     * <p>Held back <i>before</i> the stack is sized rather than fitted around it afterwards. The
     * stack is centred in whatever room is left over, and after centring there is routinely less
     * than a line of it below the last button -- four units on a full-height parchment -- so an
     * affordance placed after the fact would land on the dark overlay under the banner or nowhere
     * at all.
     */
    private static int hintHeight(int room, int totalChoices, int lineHeight) {
        return visibleChoices(room, totalChoices) < totalChoices ? lineHeight + 1 : 0;
    }

    /**
     * Where the affordance goes: directly under the last button, inside the reserved space.
     *
     * <p>Empty when the reservation could not be honoured -- a parchment so short that the single
     * mandatory button already fills it. The screen draws nothing rather than drawing outside.
     */
    private static ScreenRect scrollHint(ScreenRect choices, int hintHeight, int lineHeight,
                                         int contentBottom) {
        if (hintHeight <= 0) return ScreenRect.EMPTY;
        int top = choices.bottom() + 1;
        if (top + lineHeight > contentBottom) return ScreenRect.EMPTY;
        return new ScreenRect(choices.x(), top, choices.width(), lineHeight);
    }

    /**
     * The height the reward panel actually needs: a heading, then as many whole rows of icons as
     * the fullest section wants at the width it will get. Zero when there is nothing to show.
     *
     * <p>Computed before the panel exists, which it can be because the only thing a section's width
     * depends on is the panel's <i>width</i> -- fixed by the screen and the margin -- and never on
     * its height. {@link #calculate} then clamps the answer between {@link #REWARD_MIN_HEIGHT} and
     * {@link #REWARD_MAX_HEIGHT}, so a section with forty items still stops at a bounded band and
     * counts the rest in its heading.
     */
    static int fittedRewardHeight(int panelWidth, int lineHeight, int onAccept, int onComplete,
                                  int keep) {
        int columns = (onAccept > 0 ? 1 : 0) + (onComplete > 0 ? 1 : 0) + (keep > 0 ? 1 : 0);
        if (columns == 0) return 0;
        int sectionWidth = Math.max(1,
                (panelWidth - (REWARD_SECTION_GAP * (columns - 1))) / columns);
        int perRow = (sectionWidth + ICON_GAP) / (ICON_SIZE + ICON_GAP);
        int mostIcons = Math.max(Math.max(onAccept, onComplete), keep);
        int rows = perRow <= 0 ? 1 : Math.max(1, ((mostIcons + perRow) - 1) / perRow);
        return lineHeight + 2 + (rows * (ICON_SIZE + ICON_GAP)) - ICON_GAP;
    }

    private static RewardBlock rewardBlock(ScreenRect panel, int lineHeight, int onAccept,
                                           int onComplete, int keep) {
        List<SectionKind> present = new ArrayList<>(3);
        List<Integer> counts = new ArrayList<>(3);
        if (onAccept > 0) { present.add(SectionKind.ON_ACCEPT); counts.add(onAccept); }
        if (onComplete > 0) { present.add(SectionKind.ON_COMPLETE); counts.add(onComplete); }
        if (keep > 0) { present.add(SectionKind.KEEP); counts.add(keep); }
        if (present.isEmpty()) return RewardBlock.hidden();

        int columns = present.size();
        int totalGap = REWARD_SECTION_GAP * (columns - 1);
        int sectionWidth = Math.max(1, (panel.width() - totalGap) / columns);
        List<RewardBlock.Section> sections = new ArrayList<>(columns);
        for (int i = 0; i < columns; i++) {
            int x = panel.x() + (i * (sectionWidth + REWARD_SECTION_GAP));
            ScreenRect bounds = new ScreenRect(x, panel.y(), sectionWidth, panel.height());
            ScreenRect label = new ScreenRect(x, panel.y(), sectionWidth, lineHeight);
            int iconTop = panel.y() + lineHeight + 2;
            int iconAreaHeight = Math.max(0, panel.bottom() - iconTop);
            ScreenRect icons = new ScreenRect(x, iconTop, sectionWidth, iconAreaHeight);
            // No max(1, ...) on either: a grid with no room for a whole icon holds none, and the
            // heading's count is what tells the player so. Rounding a zero up to one is how an
            // icon ends up drawn outside its own section.
            int perRow = (sectionWidth + ICON_GAP) / (ICON_SIZE + ICON_GAP);
            int rows = (iconAreaHeight + ICON_GAP) / (ICON_SIZE + ICON_GAP);
            int visible = Math.min(counts.get(i), Math.max(0, perRow * rows));
            sections.add(new RewardBlock.Section(present.get(i), bounds, label, icons,
                    counts.get(i), visible, perRow));
        }
        return new RewardBlock(true, panel, List.copyOf(sections));
    }

    /**
     * Tab order: every visible choice top to bottom, then each visible reward section.
     *
     * <p>Choices first because they are the only stops that do anything -- a reward section is a
     * focus stop so a keyboard-only player can read its tooltip, not so they can press it.
     */
    private static List<Focusable> focusRing(ChoiceBlock choices, RewardBlock rewards) {
        List<Focusable> ring = new ArrayList<>();
        for (int slot = 0; slot < choices.visibleCount(); slot++) {
            ring.add(new Focusable(FocusKind.CHOICE, slot, choices.item(slot)));
        }
        if (rewards.visible()) {
            for (int i = 0; i < rewards.sections().size(); i++) {
                ring.add(new Focusable(FocusKind.REWARD_SECTION, i, rewards.sections().get(i).bounds()));
            }
        }
        return List.copyOf(ring);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ---------------------------------------------------------------- queries

    /** The body column's right edge -- the boundary the choice column must not cross. */
    public int bodyRight() {
        return body().bounds().right();
    }

    public boolean portraitVisible() {
        return portrait().visible();
    }
}
