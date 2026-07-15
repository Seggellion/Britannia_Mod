package com.seggellion.britannia_mod.dialogue;

public record DialogueLayout(
        int portraitY,
        int textX,
        int textY,
        int maxTextWidth,
        int buttonStartX,
        int buttonStartY,
        int buttonWidth
) {
    public static final int TOP_SECTION_HEIGHT = 134;
    public static final int PORTRAIT_X = 30;
    public static final int PORTRAIT_LAYOUT_SIZE = 108;
    public static final int BUTTON_WIDTH = 140;
    public static final int BUTTON_GAP = 4;

    public static DialogueLayout calculate(
            int screenWidth,
            int textLineCount,
            int optionCount,
            int fontLineHeight,
            boolean hasProfessionLabel
    ) {
        int textX = PORTRAIT_X + PORTRAIT_LAYOUT_SIZE + 25;
        int buttonStartX = screenWidth - BUTTON_WIDTH - 20;
        int maxTextWidth = buttonStartX - textX - 20;
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
                BUTTON_WIDTH
        );
    }
}
