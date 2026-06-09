package com.seggellion.britannia_mod.client.gui;

import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.ServerboundQuitQuestPayload;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;
import com.seggellion.britannia_mod.quest.ClientQuestTable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

public class QuestJournalScreen extends Screen {
    private static final int PANEL_W = 370;
    private static final int PANEL_H = 260;
    private static final int ROW_H = 62;
    private static final int ROW_GAP = 4;
    private static final int QUIT_W = 46;
    private static final int QUIT_H = 20;
    private static final DateTimeFormatter ACCEPTED_AT_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    // Define the path to your custom background texture
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/quest-journal.png");

    private int panelLeft;
    private int panelTop;
    private int scrollOffset;
    private List<ClientQuestEntry> quests = List.of();
    private String lastSignature = "";

    public QuestJournalScreen() {
        super(Component.literal("Quest Journal"));
    }

    @Override
    protected void init() {
        panelLeft = (this.width - PANEL_W) / 2;
        panelTop = (this.height - PANEL_H) / 2;
        refreshWidgets();
    }

    @Override
    public void tick() {
        String signature = signature(ClientQuestTable.snapshot());
        if (!signature.equals(lastSignature)) {
            refreshWidgets();
        }
    }

    private void refreshWidgets() {
        this.clearWidgets();
        this.quests = ClientQuestTable.snapshot();
        this.lastSignature = signature(this.quests);
        this.scrollOffset = Math.min(scrollOffset, maxScroll());

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(panelLeft + PANEL_W / 2 - 40, panelTop + PANEL_H - 28, 80, 20)
                .build());

        int rowTop = panelTop + 42;
        // Increased the right margin from 14 to 40 to account for the scroll edge
        int rowRight = panelLeft + PANEL_W - 40;
        int visibleRows = visibleRows();

        for (int slot = 0; slot < visibleRows; slot++) {
            int questIndex = scrollOffset + slot;
            if (questIndex >= quests.size()) break;

            ClientQuestEntry quest = quests.get(questIndex);
            int y = rowTop + slot * (ROW_H + ROW_GAP);
            Button quit = Button.builder(Component.literal("Quit"), b -> {
                        b.active = false;
                        NetworkHandler.sendToServer(new ServerboundQuitQuestPayload(quest.questStateId()));
                    })
                    .bounds(rowRight - QUIT_W, y + (ROW_H - QUIT_H) / 2, QUIT_W, QUIT_H)
                    .build();
            addRenderableWidget(quit);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        drawPanel(graphics);
        drawQuestRows(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPanel(GuiGraphics graphics) {
        // Draw the custom background image instead of filled rectangles
        graphics.blit(TEXTURE, panelLeft, panelTop, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
    }

    private void drawQuestRows(GuiGraphics graphics) {
        if (quests.isEmpty()) {
            Component empty = Component.literal("No active quests.");
            int x = panelLeft + (PANEL_W - font.width(empty)) / 2;
            graphics.drawString(font, empty, x, panelTop + 110, 0xFF6A3A22, false);
            return;
        }

        int rowTop = panelTop + 42;
        // Increased the left and right margins from 14 to 40 
        int rowLeft = panelLeft + 40;
        int rowRight = panelLeft + PANEL_W - 40;
        int textRight = rowRight - QUIT_W - 10;
        int textWidth = textRight - rowLeft;

        for (int slot = 0; slot < visibleRows(); slot++) {
            int questIndex = scrollOffset + slot;
            if (questIndex >= quests.size()) break;

            ClientQuestEntry quest = quests.get(questIndex);
            int y = rowTop + slot * (ROW_H + ROW_GAP);
            
            // Rendering the background highlight strip (optional: you can remove this if it clashes with the texture)
            graphics.fill(rowLeft, y, rowRight, y + ROW_H, 0x33A06B32);

            int lineY = y + 5;
            graphics.drawString(font, fit(quest.name(), textWidth), rowLeft + 6, lineY, 0xFF3D1B0F, false);
            lineY += 13;

            graphics.drawString(font, fit("Given by: " + questGiverName(quest), textWidth), rowLeft + 6, lineY, 0xFF4C2614, false);
            lineY += 13;

            if (hasText(quest.briefDescription())) {
                graphics.drawString(font, fit(quest.briefDescription(), textWidth), rowLeft + 6, lineY, 0xFF5C321C, false);
                lineY += 13;
            }

            String acceptedAt = formatAcceptedAt(quest.acceptedAt());
            if (hasText(acceptedAt)) {
                graphics.drawString(font, fit("Accepted: " + acceptedAt, textWidth), rowLeft + 6, lineY, 0xFF7A4B2C, false);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isInsidePanel(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        int next = scrollOffset - (int) Math.signum(scrollY);
        scrollOffset = Math.max(0, Math.min(maxScroll(), next));
        refreshWidgets();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !isInsidePanel(mouseX, mouseY)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isInsidePanel(double mouseX, double mouseY) {
        return mouseX >= panelLeft && mouseX <= panelLeft + PANEL_W
                && mouseY >= panelTop && mouseY <= panelTop + PANEL_H;
    }

    private int visibleRows() {
        return Math.max(1, (PANEL_H - 86) / (ROW_H + ROW_GAP));
    }

    private int maxScroll() {
        return Math.max(0, quests.size() - visibleRows());
    }

    private String fit(String text, int width) {
        String safeText = text == null ? "" : text;
        if (font.width(safeText) <= width) return safeText;
        return font.plainSubstrByWidth(safeText, Math.max(0, width - font.width("..."))) + "...";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String questGiverName(ClientQuestEntry quest) {
        if (quest == null || !hasText(quest.questGiverName())) return "Unknown";
        return quest.questGiverName();
    }

    private static String formatAcceptedAt(String raw) {
        if (!hasText(raw)) return "";
        String trimmed = raw.trim();
        try {
            return Instant.parse(trimmed)
                    .atZone(ZoneId.systemDefault())
                    .format(ACCEPTED_AT_FORMAT);
        } catch (DateTimeParseException ignored) {
            return trimmed;
        }
    }

    private static String signature(List<ClientQuestEntry> entries) {
        return entries.stream()
                .map(entry -> entry.questStateId()
                        + ":" + entry.questGiverName()
                        + ":" + entry.name()
                        + ":" + entry.briefDescription()
                        + ":" + entry.acceptedAt()
                        + ":" + entry.status())
                .collect(Collectors.joining("|"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
}
