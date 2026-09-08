package com.seggellion.britannia_mod.client.gui;

import com.seggellion.britannia_mod.client.render.PortraitDownloader;
import com.seggellion.britannia_mod.client.screen.DialoguePresentation;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Rowan farming questline M8: the drawing half of the quest screens.
 *
 * <p>Everything here takes a rectangle that {@link QuestDialogueLayout},
 * {@link QuestJournalLayout} or {@link QuestMixingGuideLayout} already computed and draws in it.
 * There is no arithmetic worth testing in this file, which is the point of the split -- the
 * arithmetic is in the layout records, where JUnit can reach it.
 *
 * <p>Two rules the methods here keep:
 * <ul>
 *   <li><b>Text is clipped by line, never by luck.</b> {@link #drawWrapped} splits with the font
 *       and draws only the lines that fit, so a long localized string stops at the bottom of its
 *       box instead of running through whatever is underneath.</li>
 *   <li><b>An item is named, never spelled.</b> {@link #itemName} goes through the item registry
 *       and falls back to a translated "unknown item", so a reward icon never shows
 *       {@code britannia_mod:gold_coin} to a player.</li>
 * </ul>
 */
public final class QuestScreenDraw {

    private QuestScreenDraw() {
    }

    public static final int TEXT_COLOR = DialoguePresentation.TEXT_COLOR;
    public static final int MUTED_COLOR = 0xFF5C321C;
    /**
     * The reward panel's ground.
     *
     * <p>The panel had none: its three headings and the "+n more" line were drawn straight onto the
     * 80%-opaque dim under the parchment, which puts {@link #HEADING_COLOR} (a near-black brown) on
     * a near-black background at roughly 1.6:1. The parchment's own tone, one step darker so the
     * panel still reads as a separate surface below the banner rather than as part of it.
     */
    public static final int REWARD_PANEL_COLOR = 0xF2D8CCB4;
    public static final int REWARD_PANEL_EDGE_COLOR = 0xFF8B6A45;
    public static final int HEADING_COLOR = 0xFF3D1B0F;
    public static final int DONE_COLOR = 0xFF2E7D32;
    public static final int PENDING_COLOR = 0xFF7A4B2C;
    public static final int CLAIM_COLOR = 0xFFB8860B;
    public static final int FOCUS_RING_COLOR = 0xFFB8860B;

    // ------------------------------------------------------------ text scale

    /**
     * How much larger dialogue text is drawn than Minecraft's own font.
     *
     * <p>The vanilla font is a fixed 8 pixels, which on a 1080p screen at the default GUI scale is
     * markedly smaller than the parchment it sits on. Nothing here changes the font: the glyphs go
     * through a scale transform, so they stay the same pixel art, only bigger.
     *
     * <p>The banner does not grow to match. {@link QuestDialogueLayout#PARCHMENT_MAX_HEIGHT} is
     * deliberately unchanged, so larger text means fewer lines fit and the body scrolls. The layout
     * already computes that ({@code visibleLines}, {@code scrollMax}) and the screen already drives
     * it with the wheel and the arrow keys, so bigger text costs a scroll, never a clipped sentence.
     *
     * <p>Every measurement a layout is given must be in these units, which is why
     * {@link #lineHeight(Font)} and {@link #width(Font, Component)} exist. A caller that reached for
     * {@code font.lineHeight} directly would reserve two thirds of the room it needs, and the last
     * line of every node would be cut in half.
     */
    public static final float TEXT_SCALE = 1.5f;

    /** The line height a layout must reserve per line of dialogue text. */
    public static int lineHeight(Font font) {
        return Math.max(1, Math.round(font.lineHeight * TEXT_SCALE));
    }

    /** What {@code component} occupies on screen once drawn at {@link #TEXT_SCALE}. */
    public static int width(Font font, Component component) {
        return Math.round(font.width(component) * TEXT_SCALE);
    }

    /** A screen-space width expressed in the font's own units, for {@code split} and {@code width}. */
    public static int fontUnits(int screenWidth) {
        return Math.max(1, (int) Math.floor(screenWidth / TEXT_SCALE));
    }

    /**
     * One line of text at {@link #TEXT_SCALE}.
     *
     * <p>Translates first and scales second, so glyphs land exactly on {@code (x, y)} rather than on
     * a rounded {@code (x / 1.5, y / 1.5)}, which would drift by up to a pixel per call and read as
     * a ragged left edge down a wrapped paragraph.
     */
    private static void drawScaled(GuiGraphics graphics, Font font, Component component,
                                   int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1f);
        graphics.drawString(font, component, 0, 0, color, false);
        graphics.pose().popPose();
    }

    /**
     * One line of dialogue text, for a screen drawing outside the blocks above.
     *
     * <p>Public so that a screen never reaches for {@code graphics.drawString} itself: mixing a
     * scaled paragraph with an unscaled footnote is exactly the inconsistency this file exists to
     * prevent.
     */
    public static void drawLine(GuiGraphics graphics, Font font, Component component,
                                int x, int y, int color) {
        drawScaled(graphics, font, component, x, y, color);
    }

    /** {@link #drawScaled} for a line the font has already split. */
    private static void drawScaled(GuiGraphics graphics, Font font, FormattedCharSequence line,
                                   int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1f);
        graphics.drawString(font, line, 0, 0, color, false);
        graphics.pose().popPose();
    }

    // ------------------------------------------------------------ text

    /** UO-styled translatable component. Every player-facing string on these screens goes through here. */
    public static MutableComponent text(String translationKey, Object... args) {
        return Component.translatable(translationKey, args).withStyle(DialoguePresentation.UO_STYLE);
    }

    /** UO-styled component around content the server authored, which is already a display string. */
    public static MutableComponent literal(String value) {
        return Component.literal(value == null ? "" : value).withStyle(DialoguePresentation.UO_STYLE);
    }

    /**
     * Draws {@code component} wrapped to {@code area}, starting at line {@code scroll}, and returns
     * how many lines the text wraps to in total.
     *
     * <p>The caller passes the same wrap width the layout computed, so the count it gets back is
     * the count the layout was told about. Never calls {@code drawWordWrap}: that draws every line
     * regardless of the box, which is how text ends up under the buttons.
     */
    public static int drawWrapped(GuiGraphics graphics, Font font, Component component,
                                  ScreenRect area, int wrapWidth, int scroll, int color) {
        // wrapWidth is screen space; the font wraps in its own units, TEXT_SCALE smaller.
        List<FormattedCharSequence> lines = font.split(component, fontUnits(wrapWidth));
        int step = lineHeight(font);
        int visible = Math.max(0, area.height() / step);
        int first = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - Math.max(1, visible))));
        for (int i = 0; i < visible && first + i < lines.size(); i++) {
            drawScaled(graphics, font, lines.get(first + i), area.x(), area.y() + (i * step), color);
        }
        return lines.size();
    }

    /** Truncates to {@code width} with an ellipsis. For single-line labels only. */
    public static Component fit(Font font, Component component, int width) {
        if (width <= 0) return Component.empty();
        if (width(font, component) <= width) return component;
        String plain = component.getString();
        int room = Math.max(0, fontUnits(width) - font.width("..."));
        return literal(font.plainSubstrByWidth(plain, room) + "...");
    }

    // ------------------------------------------------------------ items

    /**
     * An item id's real display name.
     *
     * <p>Goes through {@link BuiltInRegistries#ITEM}, so a block item gets its block name and a
     * plain item gets its item name without this class having to know which it is. An id the
     * registry does not know becomes {@link QuestScreenText#ITEM_UNKNOWN} -- a translated string,
     * not the id. Showing the id would be the raw-registry-key defect the acceptance list calls out.
     */
    public static Component itemName(String itemId) {
        Item item = resolveItem(itemId);
        return item == null
                ? text(QuestScreenText.ITEM_UNKNOWN)
                : item.getDescription().copy().withStyle(DialoguePresentation.UO_STYLE);
    }

    /** The stack to draw for an item id, or empty when the registry does not know it. */
    public static ItemStack itemStack(String itemId, int count) {
        Item item = resolveItem(itemId);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, count));
    }

    private static Item resolveItem(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        ResourceLocation id = ResourceLocation.tryParse(itemId.trim());
        if (id == null) return null;
        return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    /**
     * Draws one item icon, or a placeholder box when the id does not resolve.
     *
     * <p>A missing icon is drawn as an outline rather than skipped: a gap in the row would make
     * the count in the heading disagree with what is on screen.
     */
    public static void drawItemIcon(GuiGraphics graphics, Font font, ScreenRect at,
                                    String itemId, int count) {
        ItemStack stack = itemStack(itemId, count);
        if (stack.isEmpty()) {
            graphics.fill(at.x(), at.y(), at.right(), at.bottom(), 0x33000000);
            graphics.renderOutline(at.x(), at.y(), at.width(), at.height(), 0x66000000);
            return;
        }
        graphics.renderItem(stack, at.x(), at.y());
        if (count > 1) {
            graphics.renderItemDecorations(font, stack, at.x(), at.y());
        }
    }

    // ------------------------------------------------------------ dialogue frame

    /** The parchment banner, sized to the layout rather than to a constant. */
    public static void drawParchment(GuiGraphics graphics, QuestDialogueLayout layout) {
        graphics.fill(0, 0, layout.screen().width(), layout.screen().height(), 0xCC000000);
        ScreenRect parchment = layout.parchment();
        graphics.blit(DialoguePresentation.PAPER_BACKGROUND, parchment.x(), parchment.y(), 0, 0,
                parchment.width(), parchment.height(), parchment.width(), parchment.height());
    }

    /** Portrait, name and profession, when the layout kept the portrait column. */
    public static void drawPortrait(GuiGraphics graphics, Font font, QuestDialogueLayout layout,
                                    String npcName, String npcGender, String professionLabel) {
        QuestDialogueLayout.PortraitBlock block = layout.portrait();
        if (!block.visible() || npcName == null || npcName.isEmpty()) return;

        ResourceLocation portrait = PortraitDownloader.getPortrait(npcName, npcGender);
        ScreenRect at = block.bounds();
        graphics.blit(portrait, at.x(), at.y(),
                DialoguePresentation.PORTRAIT_CROP_MARGIN, DialoguePresentation.PORTRAIT_CROP_MARGIN,
                at.width(), at.height(),
                DialoguePresentation.PORTRAIT_TEXTURE_SIZE, DialoguePresentation.PORTRAIT_TEXTURE_SIZE);

        Component name = fit(font, literal(npcName), at.width() + 20);
        drawScaled(graphics, font, name, block.nameCenterX() - (width(font, name) / 2), block.nameY(),
                TEXT_COLOR);

        if (professionLabel != null && !professionLabel.isBlank()) {
            Component profession = fit(font, literal(professionLabel), at.width() + 20);
            drawScaled(graphics, font, profession,
                    block.nameCenterX() - (width(font, profession) / 2), block.professionY(),
                    MUTED_COLOR);
        }
    }

    /** The "Quest 3 of 5" line above the body. */
    public static void drawStageLine(GuiGraphics graphics, Font font, QuestDialogueLayout layout,
                                     ClientQuestEntry.Stage stage) {
        ScreenRect at = layout.stageLine();
        if (at.isEmpty()) return;
        Component line = stage != null && stage.known()
                ? text(QuestScreenText.STAGE, stage.index(), stage.count())
                : text(QuestScreenText.STAGE_UNKNOWN);
        drawScaled(graphics, font, fit(font, line, at.width()), at.x(), at.y(), HEADING_COLOR);
    }

    /**
     * The three labelled reward sections.
     *
     * <p>Each heading carries its own count, so a section clipped to one row of icons still says
     * how many items are really in it, and the sections are told apart by their words rather than
     * by a colour.
     */
    public static void drawRewardPanel(GuiGraphics graphics, Font font, QuestDialogueLayout layout,
                                       List<ClientQuestEntry.RewardItem> onAccept,
                                       List<ClientQuestEntry.RewardItem> onComplete,
                                       List<ClientQuestEntry.RewardItem> keep) {
        QuestDialogueLayout.RewardBlock block = layout.rewards();
        if (!block.visible()) return;

        // The panel gets a ground of its own before anything is written on it. Without one the
        // headings sat on the dim, which is the same reason the parchment exists for the body.
        ScreenRect panel = block.panel();
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), REWARD_PANEL_COLOR);
        graphics.renderOutline(panel.x(), panel.y(), panel.width(), panel.height(),
                REWARD_PANEL_EDGE_COLOR);

        for (QuestDialogueLayout.RewardBlock.Section section : block.sections()) {
            List<ClientQuestEntry.RewardItem> items = itemsFor(section.kind(), onAccept, onComplete, keep);
            Component heading = text(QuestScreenText.REWARDS_HEADING_COUNT,
                    text(QuestScreenText.sectionHeading(section.kind())), items.size());
            drawScaled(graphics, font, fit(font, heading, section.label().width()),
                    section.label().x(), section.label().y(), HEADING_COLOR);

            for (int i = 0; i < section.visibleIconCount() && i < items.size(); i++) {
                drawItemIcon(graphics, font, section.icon(i), items.get(i).id(), items.get(i).count());
            }
            if (section.overflowCount() > 0) {
                Component more = text(QuestScreenText.REWARDS_MORE, section.overflowCount());
                int y = section.icons().y() + QuestDialogueLayout.ICON_SIZE + 1;
                if (y + lineHeight(font) <= section.bounds().bottom()) {
                    drawScaled(graphics, font, fit(font, more, section.bounds().width()),
                            section.bounds().x(), y, MUTED_COLOR);
                }
            }
        }
    }

    /** The tooltip for whichever reward icon the mouse is over, or nothing. */
    public static void drawRewardTooltip(GuiGraphics graphics, Font font, QuestDialogueLayout layout,
                                         List<ClientQuestEntry.RewardItem> onAccept,
                                         List<ClientQuestEntry.RewardItem> onComplete,
                                         List<ClientQuestEntry.RewardItem> keep,
                                         int mouseX, int mouseY) {
        QuestDialogueLayout.RewardBlock block = layout.rewards();
        if (!block.visible()) return;

        for (QuestDialogueLayout.RewardBlock.Section section : block.sections()) {
            List<ClientQuestEntry.RewardItem> items = itemsFor(section.kind(), onAccept, onComplete, keep);
            for (int i = 0; i < section.visibleIconCount() && i < items.size(); i++) {
                ScreenRect icon = section.icon(i);
                if (mouseX < icon.x() || mouseX >= icon.right()
                        || mouseY < icon.y() || mouseY >= icon.bottom()) {
                    continue;
                }
                ClientQuestEntry.RewardItem item = items.get(i);
                graphics.renderTooltip(font, List.of(
                        text(QuestScreenText.REWARDS_TOOLTIP, itemName(item.id()), item.count())
                                .getVisualOrderText(),
                        text(QuestScreenText.sectionHeading(section.kind())).getVisualOrderText()),
                        mouseX, mouseY);
                return;
            }
        }
    }

    private static List<ClientQuestEntry.RewardItem> itemsFor(
            QuestDialogueLayout.SectionKind kind,
            List<ClientQuestEntry.RewardItem> onAccept,
            List<ClientQuestEntry.RewardItem> onComplete,
            List<ClientQuestEntry.RewardItem> keep) {
        return switch (kind) {
            case ON_ACCEPT -> onAccept == null ? List.of() : onAccept;
            case ON_COMPLETE -> onComplete == null ? List.of() : onComplete;
            case KEEP -> keep == null ? List.of() : keep;
        };
    }

    // ------------------------------------------------------------ shared bits

    /** A visible focus ring. Keyboard focus has to be seen, not only tracked. */
    public static void drawFocusRing(GuiGraphics graphics, ScreenRect at) {
        if (at.isEmpty()) return;
        graphics.renderOutline(at.x() - 1, at.y() - 1, at.width() + 2, at.height() + 2, FOCUS_RING_COLOR);
    }

    /** One ordered progress line: marker, then label. The marker is a symbol, not only a colour. */
    public static void drawProgressStep(GuiGraphics graphics, Font font, ScreenRect at,
                                        ClientQuestEntry.ProgressStep step) {
        if (at.isEmpty()) return;
        Component line = text(QuestScreenText.PROGRESS_LINE,
                text(QuestScreenText.progressMarker(step.done())), literal(step.label()));
        drawScaled(graphics, font, fit(font, line, at.width()), at.x(), at.y(),
                step.done() ? DONE_COLOR : PENDING_COLOR);
    }
}
