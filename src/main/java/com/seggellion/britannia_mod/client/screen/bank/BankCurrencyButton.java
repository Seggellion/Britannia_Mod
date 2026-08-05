package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.client.screen.DialoguePresentation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Owner improvement (2026-08-04): a currency withdrawal button that carries its denomination's
 * live balance on a second line -- "Gold" over "1.25k" -- so the player can see what the bank
 * holds without opening the Balance screen.
 *
 * <p>Vanilla {@link Button} centres one label vertically; this overrides {@link #renderWidget}'s
 * text half to stack two lines instead. The balance line is {@link BankBalanceAbbreviation}'s
 * output, refreshed each frame by the screen from the live session -- this class renders what it
 * is told and computes nothing (Architecture Decision 0 keeps the arithmetic in the plain,
 * tested class).
 */
public final class BankCurrencyButton extends Button {

    private String balanceText = "";

    public BankCurrencyButton(int x, int y, int width, int height, Component label, OnPress onPress) {
        super(x, y, width, height, DialoguePresentation.text(label), onPress, DEFAULT_NARRATION);
    }

    /** The pre-abbreviated balance line. The screen owns when and from what this is computed. */
    public void setBalanceText(String balanceText) {
        this.balanceText = balanceText == null ? "" : balanceText;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Vanilla's background/frame for all five widget states, then our own two-line text.
        // The message is deliberately not drawn by super: renderString is final in neither
        // direction we need, so the cheapest correct move is to blank the message during the
        // super call and draw both lines ourselves.
        Component label = getMessage();
        setMessage(Component.empty());
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        setMessage(label);

        Font font = Minecraft.getInstance().font;
        int color = this.active ? 0xFFFFFF : 0xA0A0A0;
        int centerX = getX() + (getWidth() / 2);
        int labelY = getY() + 4;
        graphics.drawCenteredString(font, label, centerX, labelY, color);
        if (!balanceText.isEmpty()) {
            graphics.drawCenteredString(font, DialoguePresentation.text(balanceText),
                    centerX, labelY + font.lineHeight + 1, color);
        }
    }
}
