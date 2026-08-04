package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.client.render.PortraitDownloader;
import com.seggellion.britannia_mod.client.screen.DialoguePresentation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Milestone 3: the shared parchment-and-portrait presentation for the Bank Main, Bank Balance and
 * Create Cheque screens.
 *
 * <h2>Composition, not inheritance</h2>
 * Design §7 prefers a reusable renderer over a deep base-screen hierarchy, and the three screens
 * genuinely differ -- one has three buttons and no form, one has two buttons and no form, one has
 * two buttons and a multi-row form. A base class would have to be parameterised into
 * incomprehensibility to cover that. Each screen owns its widgets and calls into this to draw the
 * shared parts.
 *
 * <p>This is a thin drawing layer over {@link BankDialogueLayout}, which owns every coordinate and
 * is separately tested. Nothing here computes a position.
 *
 * <h2>Relationship to {@code DialoguePresentation}</h2>
 * The parchment texture, the UO font style and the portrait lookup are reused as-is -- this epic
 * does not fork the project's dialogue look. What is not reused is {@code
 * DialoguePresentation.renderDialogue}: it takes a {@code DialogueViewModel}, whose fields are all
 * {@code String}, which cannot carry a translatable component. Drawing the same three elements
 * from {@link Component} arguments is what makes design §17's localization requirement reachable
 * without changing a record the quest system also depends on.
 */
public final class BankDialogueFrame {

    private BankDialogueFrame() {
    }

    /** The dimmed backdrop plus the parchment banner. Call from {@code renderBackground}. */
    public static void renderBackground(GuiGraphics graphics, int screenWidth, int screenHeight) {
        DialoguePresentation.renderPaperBackground(graphics, screenWidth, screenHeight);
    }

    /**
     * Portrait, banker name and body text.
     *
     * <p>The portrait is skipped entirely when {@link BankDialogueLayout#portraitVisible()} is
     * false -- at that width there is no room for it and a column of text both, and the text is
     * what carries meaning. It is also skipped when {@code tellerName} is blank, mirroring
     * {@code DialoguePresentation.renderDialogue}'s own guard: {@code PortraitDownloader} keys on
     * the name, so there is nothing to look up.
     */
    public static void renderHeader(
            GuiGraphics graphics,
            Font font,
            BankDialogueLayout layout,
            @Nullable String tellerName,
            @Nullable String tellerGender,
            Component body
    ) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(body, "body");

        boolean hasName = tellerName != null && !tellerName.isBlank();
        if (layout.portraitVisible() && hasName) {
            ResourceLocation portrait = PortraitDownloader.getPortrait(tellerName, tellerGender);
            graphics.blit(
                    portrait,
                    layout.portraitX(),
                    layout.portraitY(),
                    DialoguePresentation.PORTRAIT_CROP_MARGIN,
                    DialoguePresentation.PORTRAIT_CROP_MARGIN,
                    layout.portraitSize(),
                    layout.portraitSize(),
                    DialoguePresentation.PORTRAIT_TEXTURE_SIZE,
                    DialoguePresentation.PORTRAIT_TEXTURE_SIZE
            );
            Component name = DialoguePresentation.text(tellerName);
            graphics.drawString(
                    font, name,
                    layout.nameCenterX() - (font.width(name) / 2),
                    layout.nameY(),
                    DialoguePresentation.TEXT_COLOR,
                    false
            );
        }

        graphics.drawWordWrap(
                font,
                DialoguePresentation.text(body),
                layout.bodyX(),
                layout.bodyY(),
                layout.bodyMaxWidth(),
                DialoguePresentation.TEXT_COLOR
        );
    }

    /**
     * How many wrapped lines {@code body} needs at this width -- feed the result back into
     * {@link BankDialogueLayout#calculate} so the body block is centred against its real height.
     *
     * <p>Two passes are unavoidable: the wrap width depends on the layout and the layout depends
     * on the line count. Callers should compute a provisional layout with a line count of 1, ask
     * this, then recompute. {@code BankScreen} already does exactly this dance with
     * {@code DialogueLayout}.
     */
    public static int bodyLineCount(Font font, Component body, int maxWidth) {
        return font.split(DialoguePresentation.text(body), Math.max(1, maxWidth)).size();
    }

    /**
     * Draws a status line with its severity marker.
     *
     * <p>Three things vary with severity, only one of which is colour: the marker bar's width, the
     * text's weight, and the colour itself. Design §16 forbids colour being the sole indicator,
     * which matters most here -- a reconciliation warning that reads like an ordinary rejection is
     * the one status in this system with real consequences for getting wrong.
     *
     * <p>The text wraps. The single unwrapped {@code drawString} in {@code BankScreen} runs the
     * ~150-character reconciliation message off the right edge of the screen at most widths, which
     * Milestone 0 §3.5 recorded as the most visible defect in the current UI.
     */
    public static void renderStatus(
            GuiGraphics graphics,
            Font font,
            BankDialogueLayout layout,
            @Nullable BankStatusPresenter.Status status
    ) {
        renderStatus(graphics, font, layout.statusX(), layout.statusY(), layout.statusMaxWidth(), status);
    }

    /**
     * The same status rendering, positioned directly.
     *
     * <p>Milestone 9: the Bank Box is not a dialogue screen and has no {@link BankDialogueLayout},
     * but every banking screen should report an outcome the same way -- same severity marker, same
     * wrapping, same weight. Taking the three values this actually reads is better than
     * fabricating a layout record around them.
     */
    public static void renderStatus(
            GuiGraphics graphics,
            Font font,
            int statusX,
            int statusY,
            int statusMaxWidth,
            @Nullable BankStatusPresenter.Status status
    ) {
        renderStatus(graphics, font, statusX, statusY, statusMaxWidth, status, false);
    }

    /**
     * The same status rendering against a dark ground -- the Bank Box's panel. Only the palette
     * changes ({@link BankStatusPresenter.Severity#colorOnDark()}); marker, bold and wrapping are
     * identical, so an outcome reads the same way on every banking screen.
     */
    public static void renderStatusOnDark(
            GuiGraphics graphics,
            Font font,
            int statusX,
            int statusY,
            int statusMaxWidth,
            @Nullable BankStatusPresenter.Status status
    ) {
        renderStatus(graphics, font, statusX, statusY, statusMaxWidth, status, true);
    }

    private static void renderStatus(
            GuiGraphics graphics,
            Font font,
            int statusX,
            int statusY,
            int statusMaxWidth,
            @Nullable BankStatusPresenter.Status status,
            boolean onDark
    ) {
        if (status == null) return;

        BankStatusPresenter.Severity severity = status.severity();
        int color = onDark ? severity.colorOnDark() : severity.color();
        int markerWidth = severity.markerWidth();
        int textX = statusX + (markerWidth > 0 ? markerWidth + 4 : 0);
        int textWidth = Math.max(1, statusMaxWidth - (textX - statusX));

        Component message = Component.translatable(status.translationKey());
        if (severity.bold()) message = message.copy().withStyle(style -> style.withBold(true));
        Component styled = DialoguePresentation.text(message);

        List<net.minecraft.util.FormattedCharSequence> lines = font.split(styled, textWidth);
        if (markerWidth > 0) {
            int markerHeight = Math.max(font.lineHeight, lines.size() * font.lineHeight);
            graphics.fill(
                    statusX, statusY,
                    statusX + markerWidth, statusY + markerHeight,
                    color
            );
        }

        int y = statusY;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            graphics.drawString(font, line, textX, y, color, false);
            y += font.lineHeight;
        }
    }
}
