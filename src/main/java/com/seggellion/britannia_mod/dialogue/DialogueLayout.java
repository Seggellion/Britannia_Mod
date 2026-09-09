package com.seggellion.britannia_mod.dialogue;

/**
 * The shared service/legacy dialogue geometry.
 *
 * <h2>Rowan farming questline M11: the negative wrap width</h2>
 * Until this milestone this class computed
 * <pre>{@code   maxTextWidth = buttonStartX - textX - 20 = screenWidth - 343 }</pre>
 * and every consumer handed that straight to {@code GuiGraphics#drawWordWrap}. At 343 scaled units
 * it is zero and below that it is negative, and eight of the eighteen reachable window/scale
 * configurations sit in that band -- {@code Window#calculateScale} refuses to scale past a
 * 320x240 floor, but <b>Force Unicode Font</b> rounds an odd scale up to the next even one
 * <i>after</i> that check, so a 1024x768 window with it on is 256x192 units. See
 * {@code GuiScaleRuleTest} for the derivation.
 *
 * <p>M8 fixed the quest path by giving it {@code QuestDialogueLayout} and deliberately left this
 * class alone, because the quest milestone did not own the service screen's geometry. M11 owns it,
 * so the same two rules are applied here:
 * <ol>
 *   <li>the portrait column is <b>dropped</b> before the text column is squashed below
 *       {@link #MIN_TEXT_WIDTH}, and the button column narrows after that;</li>
 *   <li>{@link #maxTextWidth()} is clamped to a positive number, always. The floor is 1 rather than
 *       {@link #MIN_TEXT_WIDTH} for the reason {@code QuestDialogueLayout} gives: a cramped column
 *       is recoverable and a negative one is not.</li>
 * </ol>
 * Nothing changes at a width that was already comfortable: at 800 units with a portrait the numbers
 * are the pre-M11 163/640/457, which {@code ServiceDialogueControllerTest} pins.
 */
public record DialogueLayout(
        int portraitY,
        int textX,
        int textY,
        int maxTextWidth,
        int buttonStartX,
        int buttonStartY,
        int buttonWidth,
        boolean portraitVisible
) {
    public static final int TOP_SECTION_HEIGHT = 134;
    public static final int PORTRAIT_X = 30;
    public static final int PORTRAIT_LAYOUT_SIZE = 108;
    public static final int BUTTON_WIDTH = 140;
    public static final int BUTTON_GAP = 4;

    /** Outer gutter between the text column and the button column, and past the right edge. */
    public static final int GUTTER = 20;
    /**
     * Below this much room a side-by-side text column wraps to a couple of words a line, which is
     * worse than losing the portrait. The same 120 units {@code QuestDialogueLayout} uses.
     */
    public static final int MIN_TEXT_WIDTH = 120;
    /** The narrowest a button column is allowed to become before the text column gives way. */
    public static final int MIN_BUTTON_WIDTH = 64;

    /**
     * @param screenWidth        scaled screen width, as a {@code Screen} sees it
     * @param textLineCount      lines the body wraps to at {@link #maxTextWidth()}
     * @param optionCount        selectable options
     * @param fontLineHeight     {@code font.lineHeight}
     * @param hasProfessionLabel whether a profession line sits under the portrait's name
     * @param hasPortrait        whether this view has a portrait to draw at all. A view with no
     *                           quest giver (the Grabby confirmation) passes {@code false} and gets
     *                           the full width for its text rather than a column reserved for a
     *                           picture that is never drawn.
     */
    public static DialogueLayout calculate(
            int screenWidth,
            int textLineCount,
            int optionCount,
            int fontLineHeight,
            boolean hasProfessionLabel,
            boolean hasPortrait
    ) {
        int textXWithPortrait = PORTRAIT_X + PORTRAIT_LAYOUT_SIZE + 25;

        // Widest arrangement first, then degrade: portrait, then the button column, then clamp.
        int buttonWidth = BUTTON_WIDTH;
        int buttonStartX = screenWidth - buttonWidth - GUTTER;
        boolean portraitVisible = hasPortrait
                && buttonStartX - textXWithPortrait - GUTTER >= MIN_TEXT_WIDTH;
        int textX = portraitVisible ? textXWithPortrait : PORTRAIT_X;

        if (buttonStartX - textX - GUTTER < MIN_TEXT_WIDTH) {
            buttonWidth = clamp(screenWidth / 3, MIN_BUTTON_WIDTH, BUTTON_WIDTH);
            buttonStartX = screenWidth - buttonWidth - GUTTER;
        }
        // A screen too narrow even for that keeps the button on it; the text column then takes the
        // clamp below rather than running under the buttons.
        buttonStartX = Math.max(PORTRAIT_X, buttonStartX);
        int maxTextWidth = Math.max(1, buttonStartX - textX - GUTTER);

        int professionHeight = hasProfessionLabel ? fontLineHeight + 1 : 0;
        int portraitColumnHeight = PORTRAIT_LAYOUT_SIZE + 5 + fontLineHeight + professionHeight;
        int textColumnHeight = textLineCount * fontLineHeight;
        int buttonColumnHeight = optionCount > 0
                ? (optionCount * (20 + BUTTON_GAP)) - BUTTON_GAP
                : 20;

        int portraitY = Math.max(5, (TOP_SECTION_HEIGHT - portraitColumnHeight) / 2) + 15;
        int textY = Math.max(5, (TOP_SECTION_HEIGHT - textColumnHeight) / 2);
        int buttonStartY = Math.max(5, (TOP_SECTION_HEIGHT - buttonColumnHeight) / 2);

        return new DialogueLayout(
                portraitY,
                textX,
                textY,
                maxTextWidth,
                buttonStartX,
                buttonStartY,
                buttonWidth,
                portraitVisible
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
