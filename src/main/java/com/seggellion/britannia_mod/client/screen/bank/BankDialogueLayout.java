package com.seggellion.britannia_mod.client.screen.bank;

/**
 * Milestone 3: where every rectangle on the Main, Balance and Create Cheque screens comes from.
 *
 * <h2>Why this is a separate class from the frame that draws it</h2>
 * The playbook asks Milestone 3 for layout tests at narrow and wide widths, at several GUI scales,
 * with wrapped body text, with three buttons and with two, and with form controls that do not
 * overlap anything. None of that needs a {@code Font}, a {@code GuiGraphics} or a running client
 * -- it is arithmetic. Architecture Decision 0 established that a {@code Screen} cannot be
 * instantiated by either test harness this project has, so anything worth testing lives outside
 * one. This is that split applied to layout: the numbers are computed here and asserted in JUnit,
 * and {@link BankDialogueFrame} does nothing but draw at them.
 *
 * <p>GUI scale needs no parameter. Minecraft applies it before a screen ever sees a coordinate:
 * {@code screenWidth}/{@code screenHeight} here are already scaled units, so "test at GUI scale 4"
 * and "test at a narrow width" are the same test with different numbers. The constants in
 * {@code BankDialogueLayoutTest} name the real window/scale combinations they stand for.
 *
 * <h2>The invariant</h2>
 * <b>The body text never overlaps the button column, at any width.</b> {@code DialogueLayout} did
 * not hold that line when this class was written -- its {@code maxTextWidth} was
 * {@code screenWidth - 343}, which goes negative below 343 units and hands a negative wrap width
 * to {@code drawWordWrap}. A 1024-wide window at GUI scale 4 is 256 units. This class drops the
 * portrait column before it lets that happen, and clamps to a positive width even then.
 *
 * <p>{@code DialogueLayout} was deliberately left alone rather than fixed <i>here</i>: it is shared
 * with the quest and service dialogue screens, and changing their geometry was not this epic's
 * business. The Rowan farming questline fixed it in its own milestones -- the quest path in M8 with
 * {@code QuestDialogueLayout}, the shared class itself in M11 -- by these same two rules. Nothing
 * about this class changed as a result; it still owns banking's numbers.
 */
public record BankDialogueLayout(
        boolean portraitVisible,
        int portraitX,
        int portraitY,
        int portraitSize,
        int nameCenterX,
        int nameY,
        int bodyX,
        int bodyY,
        int bodyMaxWidth,
        int buttonX,
        int buttonTopY,
        int buttonWidth,
        int buttonHeight,
        int buttonGap,
        int formX,
        int formY,
        int formWidth,
        int statusX,
        int statusY,
        int statusMaxWidth
) {
    /** The parchment banner's height. {@code renderPaperBackground} paints exactly this much. */
    public static final int HEADER_HEIGHT = 134;

    public static final int MARGIN = 20;
    public static final int PORTRAIT_X = 30;
    /** The visible portrait square after {@code DialoguePresentation}'s 10px crop on each side. */
    public static final int PORTRAIT_SIZE = 88;
    /** Gap between the portrait column and the body text. */
    public static final int PORTRAIT_TEXT_GAP = 25;
    /** Gap between the body text and the button column. */
    public static final int COLUMN_GAP = 20;

    public static final int BUTTON_HEIGHT = 20;
    public static final int BUTTON_GAP = 4;
    public static final int MAX_BUTTON_WIDTH = 140;
    public static final int MIN_BUTTON_WIDTH = 64;

    /**
     * Below this much room for body text, the portrait is dropped and the text takes the full
     * width. Chosen as roughly a dozen characters of the UO font -- narrower than this and
     * wrapping produces one word per line, which is worse than losing the portrait.
     */
    public static final int MIN_BODY_WIDTH = 80;

    public static final int FORM_ROW_HEIGHT = 20;
    public static final int FORM_ROW_GAP = 6;
    public static final int SECTION_GAP = 10;

    /**
     * @param screenWidth    scaled screen width, as a {@code Screen} sees it
     * @param screenHeight   scaled screen height
     * @param fontLineHeight {@code font.lineHeight}, the only client value this needs
     * @param buttonCount    right-hand action buttons; 0 is valid
     * @param bodyLineCount  wrapped body lines, from {@code font.split(...).size()}
     * @param formRowCount   centre form rows below the banner; 0 for Main and Balance
     */
    public static BankDialogueLayout calculate(
            int screenWidth,
            int screenHeight,
            int fontLineHeight,
            int buttonCount,
            int bodyLineCount,
            int formRowCount
    ) {
        int buttonWidth = clamp(
                (screenWidth - (MARGIN * 2)) / 3, MIN_BUTTON_WIDTH, MAX_BUTTON_WIDTH
        );
        int buttonX = Math.max(MARGIN, screenWidth - MARGIN - buttonWidth);

        // Would the portrait still leave a usable column of text? If not it goes, rather than
        // squeezing the text into nothing or letting it run under the buttons.
        int bodyXWithPortrait = PORTRAIT_X + PORTRAIT_SIZE + PORTRAIT_TEXT_GAP;
        boolean portraitVisible = (buttonX - COLUMN_GAP - bodyXWithPortrait) >= MIN_BODY_WIDTH;

        int bodyX = portraitVisible ? bodyXWithPortrait : MARGIN;
        // Never negative, and never past the button column: this is the invariant the class docs
        // describe, and the reason the floor is 1 rather than MIN_BODY_WIDTH. A cramped column is
        // recoverable; text drawn underneath the buttons is not.
        int bodyMaxWidth = Math.max(1, buttonX - COLUMN_GAP - bodyX);

        int portraitColumnHeight = PORTRAIT_SIZE + 5 + fontLineHeight;
        int portraitY = centreInHeader(portraitColumnHeight);
        int nameCenterX = PORTRAIT_X + (PORTRAIT_SIZE / 2);
        int nameY = portraitY + PORTRAIT_SIZE + 5;

        int bodyHeight = Math.max(fontLineHeight, bodyLineCount * fontLineHeight);
        int bodyY = centreInHeader(bodyHeight);

        int buttonColumnHeight = buttonCount > 0
                ? (buttonCount * (BUTTON_HEIGHT + BUTTON_GAP)) - BUTTON_GAP
                : 0;
        int buttonTopY = centreInHeader(buttonColumnHeight);

        int belowHeader = HEADER_HEIGHT + SECTION_GAP;
        int formX = MARGIN;
        int formY = belowHeader;
        int formWidth = Math.max(1, screenWidth - (MARGIN * 2));

        int formHeight = formRowCount > 0
                ? (formRowCount * (FORM_ROW_HEIGHT + FORM_ROW_GAP)) - FORM_ROW_GAP
                : 0;
        int statusY = formRowCount > 0 ? formY + formHeight + SECTION_GAP : belowHeader;
        int statusX = MARGIN;
        int statusMaxWidth = Math.max(1, screenWidth - (MARGIN * 2));

        return new BankDialogueLayout(
                portraitVisible,
                PORTRAIT_X, portraitY, PORTRAIT_SIZE,
                nameCenterX, nameY,
                bodyX, bodyY, bodyMaxWidth,
                buttonX, buttonTopY, buttonWidth, BUTTON_HEIGHT, BUTTON_GAP,
                formX, formY, formWidth,
                statusX, statusY, statusMaxWidth
        );
    }

    /** Top edge of the button at {@code index}, counting from zero. */
    public int buttonY(int index) {
        return buttonTopY + (index * (buttonHeight + buttonGap));
    }

    /** Right edge of the body text column -- the boundary the button column must not cross. */
    public int bodyRight() {
        return bodyX + bodyMaxWidth;
    }

    /**
     * Vertically centres a block of {@code contentHeight} inside the parchment banner, never
     * above a small top inset. Blocks taller than the banner start at the inset and overflow
     * downward rather than being pushed off the top edge.
     */
    private static int centreInHeader(int contentHeight) {
        return Math.max(5, (HEADER_HEIGHT - contentHeight) / 2);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
