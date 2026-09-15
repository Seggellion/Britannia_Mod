package com.seggellion.britannia_mod.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A dialogue choice, drawn with the same text as the parchment beside it.
 *
 * <h2>Why this exists</h2>
 * Vanilla {@code Button} draws its label with the font directly, so once the body of the dialogue
 * moved to {@link QuestScreenDraw#TEXT_SCALE} the buttons were the only text on the screen still at
 * 1x. Two sizes of prose in one panel reads as a mistake, and it is one.
 *
 * <p>Vanilla also handles a label wider than its button by <em>scrolling</em> it
 * ({@code renderScrollingString}), which is why a long question appeared to spill past both edges
 * and drift. On a parchment that reads as broken rather than as a nicety, so an over-long label is
 * given an ellipsis and stays put: {@link QuestScreenDraw#fit} already measures in scaled units, so
 * it truncates against the width the glyphs will really occupy.
 */
public class QuestChoiceButton extends Button {

    /** Breathing room either side, so an ellipsis never touches the border. */
    private static final int SIDE_PADDING = 6;

    public QuestChoiceButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The vanilla frame, hover state and disabled tint are all wanted; only the label changes.
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderString(GuiGraphics graphics, net.minecraft.client.gui.Font font, int color) {
        Component label = QuestScreenDraw.fit(font, getMessage(), Math.max(1, getWidth() - SIDE_PADDING));
        int textWidth = QuestScreenDraw.width(font, label);
        int x = getX() + Math.max(0, (getWidth() - textWidth) / 2);
        int y = getY() + Math.max(0, (getHeight() - QuestScreenDraw.lineHeight(font)) / 2);
        QuestScreenDraw.drawLine(graphics, font, label, x, y, color);
    }
}
