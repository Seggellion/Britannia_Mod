package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.QuestGiverSpawnConfigC2SPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import java.util.List;

public class QuestGiverSpawnScreen extends Screen {
    private final BlockPos pos;
    private String npcName;
    private String cityName;
    private String customApiId; // NEW

    private EditBox cityNameBox;
    private EditBox customApiIdBox; // NEW
    private Button npcNameButton;

    // Added "Generic Combat"
    private final List<String> availableNpcs = List.of("Zorathiel", "Lord British", "Iolo", "Dupre", "Shamino", "Generic Escort", "Generic Combat");
    private int npcIdx = 0;

    // Notice the updated constructor
    public QuestGiverSpawnScreen(BlockPos pos, String npcName, String cityName, String customApiId) {
        super(Component.literal("Quest Giver Spawner"));
        this.pos = pos;
        this.npcName = npcName;
        this.cityName = cityName;
        this.customApiId = customApiId == null ? "" : customApiId;

        npcIdx = Math.max(0, availableNpcs.indexOf(npcName));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        npcNameButton = Button.builder(Component.literal(availableNpcs.get(npcIdx)), b -> {
            npcIdx = (npcIdx + 1) % availableNpcs.size();
            npcName = availableNpcs.get(npcIdx);
            b.setMessage(Component.literal(npcName));
            
            // Toggle visibility dynamically!
            customApiIdBox.visible = "Generic Combat".equals(npcName);
        }).bounds(cx - 110, cy - 60, 220, 20).build();
        addRenderableWidget(npcNameButton);

        // NEW: Custom API ID box
        customApiIdBox = new EditBox(this.font, cx - 110, cy - 30, 220, 20, Component.literal("Internal API ID"));
        customApiIdBox.setValue(customApiId);
        customApiIdBox.visible = "Generic Combat".equals(npcName); // Initial state
        addRenderableWidget(customApiIdBox);

        cityNameBox = new EditBox(this.font, cx - 110, cy, 220, 20, Component.literal("City Name"));
        cityNameBox.setValue(cityName);
        addRenderableWidget(cityNameBox);

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> saveAndClose())
                .bounds(cx - 110, cy + 40, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(cx + 10, cy + 40, 100, 20).build());
    }

    private void saveAndClose() {
        try {
            String newCity = cityNameBox.getValue().trim();
            String newCustomId = customApiIdBox.getValue().trim();
            // You will need to add this 4th parameter to your payload class!
            NetworkHandler.sendToServer(new QuestGiverSpawnConfigC2SPayload(pos, availableNpcs.get(npcIdx), newCity, newCustomId));
        } catch (Exception ignored) {
        } finally {
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float pt) {
        this.renderBackground(gg, mouseX, mouseY, pt);
        super.render(gg, mouseX, mouseY, pt);

        int cx = this.width / 2;
        int cy = this.height / 2;

        gg.drawCenteredString(this.font, "Configure Story NPC", this.width / 2, cy - 100, 0xFFFFFF);
        gg.drawString(this.font, "NPC Archetype", cx - 110, cy - 72, 0xFFFFFF);
        
        // Only draw the label if the box is visible
        if (customApiIdBox.visible) {
            gg.drawString(this.font, "Rails API Target (npc_name)", cx - 110, cy - 42, 0xFFFF55);
        }
        
        gg.drawString(this.font, "Assigned City (Economy)", cx - 110, cy - 12, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {}
    @Override
    public void onClose() { Minecraft.getInstance().setScreen(null); }
}