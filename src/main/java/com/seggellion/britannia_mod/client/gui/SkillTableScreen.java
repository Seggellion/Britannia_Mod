package com.seggellion.britannia_mod.client.gui;

import com.seggellion.britannia_mod.skill.ClientSkillTable;
import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class SkillTableScreen extends Screen {

    private static final ResourceLocation BG =
        ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/skill_screen.png");

    private static final int GUI_W = 250;
    private static final int GUI_H = 250;

    /* padding: 26 px vertically, 36 px horizontally (26 + 10) */
    private static final int PAD_V = 26;
    private static final int PAD_H = 36;
    private static final int ROW_H = 12;
    private static final int V_OFFSET = 50;        // extra drop for first row
    private static final int UP_SHIFT = 10;        // move whole GUI up 10 px

    /** Close button geometry, also the floor the scrolling rows must not paint through. */
    private static final int BUTTON_W = 80;
    private static final int BUTTON_H = 20;
    private static final int BUTTON_TOP = GUI_H - 28 - 5;
    private static final int LIST_TOP = PAD_V + V_OFFSET;
    private static final int LIST_BOTTOM_GAP = 4;

    /**
     * Rows are clipped to a whole number of row heights so the bottom row is never half-drawn and
     * so scrolling to the end lands on a fully visible final row.
     */
    private static final int LIST_H =
            ((BUTTON_TOP - LIST_BOTTOM_GAP - LIST_TOP) / ROW_H) * ROW_H;

    private static final int SCROLLBAR_W = 3;
    private static final int SCROLLBAR_GAP = 3;
    private static final int TEXT_COLOR = 0x9f3215;
    private static final int SCROLLBAR_TRACK = 0x30000000;
    private static final int SCROLLBAR_THUMB = 0xff9f3215;

    private static final SkillListViewport VIEWPORT = new SkillListViewport(ROW_H, LIST_H);

    private int guiLeft, guiTop;
    private int scroll;

    public SkillTableScreen() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        guiLeft = (width  - GUI_W) / 2;
        guiTop  = (height - GUI_H) / 2 - UP_SHIFT; // shift upward

        addRenderableWidget(
            Button.builder(Component.literal("Close"), b -> onClose())
                  .bounds(guiLeft + GUI_W / 2 - BUTTON_W / 2, guiTop + BUTTON_TOP, BUTTON_W, BUTTON_H)
                  .build()
        );

        // A resize must not leave the list parked past its new end.
        scroll = VIEWPORT.clampScroll(scroll, rows().size());
    }

    /**
     * Rows in a stable, human-meaningful order. The synced snapshot is an unordered map, so
     * without this the list silently reshuffled between syncs.
     */
    private List<Map.Entry<String, Float>> rows() {
        List<Map.Entry<String, Float>> ordered = new ArrayList<>(ClientSkillTable.snapshot().entrySet());
        ordered.sort(Comparator.comparing(entry -> displayName(entry.getKey())));
        return ordered;
    }

    private static String displayName(String slug) {
        String name = SkillManager.displayNameForSlug(slug);
        return name == null || name.isBlank() ? slug : name;
    }

    private int listTop() {
        return guiTop + LIST_TOP;
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        renderBackground(gg, mx, my, pt);
        drawPanel(gg);
        super.render(gg, mx, my, pt);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<Map.Entry<String, Float>> rows = rows();
        if (VIEWPORT.canScroll(rows.size())) {
            scroll = VIEWPORT.clampScroll(scroll - (int) Math.signum(scrollY) * ROW_H, rows.size());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void drawPanel(GuiGraphics gg) {
        Minecraft.getInstance().getTextureManager().bindForSetup(BG);
        gg.blit(BG, guiLeft, guiTop, 0, 0, GUI_W, GUI_H);

        List<Map.Entry<String, Float>> rows = rows();
        scroll = VIEWPORT.clampScroll(scroll, rows.size());

        int leftX  = guiLeft + PAD_H;
        int rightX = guiLeft + GUI_W - PAD_H;
        int top    = listTop();

        // Clip to the viewport so rows cannot paint over the frame, the title or the button.
        gg.enableScissor(leftX, top, rightX, top + LIST_H);
        int limit = VIEWPORT.visibleRowLimit(scroll, rows.size());
        for (int i = VIEWPORT.firstVisibleRow(scroll, rows.size()); i < limit; i++) {
            Map.Entry<String, Float> entry = rows.get(i);
            int y = VIEWPORT.rowTop(top, i, scroll);

            gg.drawString(font, displayName(entry.getKey()), leftX, y, TEXT_COLOR, false);

            String value = String.format("%.1f", entry.getValue());
            gg.drawString(font, value, rightX - font.width(value), y, TEXT_COLOR, false);
        }
        gg.disableScissor();

        drawScrollbar(gg, rightX, top, rows.size());
    }

    private void drawScrollbar(GuiGraphics gg, int rightX, int top, int rowCount) {
        if (!VIEWPORT.canScroll(rowCount)) {
            return;
        }
        int x = rightX + SCROLLBAR_GAP;
        gg.fill(x, top, x + SCROLLBAR_W, top + LIST_H, SCROLLBAR_TRACK);

        int contentHeight = VIEWPORT.contentHeight(rowCount);
        int thumbHeight = Math.max(ROW_H, LIST_H * LIST_H / contentHeight);
        int travel = LIST_H - thumbHeight;
        int thumbTop = top + (int) ((long) travel * scroll / VIEWPORT.maxScroll(rowCount));
        gg.fill(x, thumbTop, x + SCROLLBAR_W, thumbTop + thumbHeight, SCROLLBAR_THUMB);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float pt) {}
}
