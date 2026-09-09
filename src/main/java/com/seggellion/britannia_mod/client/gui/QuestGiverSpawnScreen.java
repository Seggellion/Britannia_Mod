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
    private String customApiId;
    private String gender;
    private int spawnRadius;

    private EditBox cityNameBox;
    private EditBox customApiIdBox;
    private EditBox spawnRadiusBox;
    private EditBox directionsBox;
    private Button npcNameButton;
    private Button genderButton;

    // Mirrored by QuestGiverSpawnBlockEntity.SUPPORTED_ARCHETYPES, which is the server's authority:
    // this list is client-only code and a unit test fails if the two ever drift apart.
    private final List<String> availableNpcs = List.of("Zorathiel", "Lord British", "Iolo", "Dupre", "Shamino", "Rowan", "Generic Escort", "Generic Combat");
    private int npcIdx = 0;

    public QuestGiverSpawnScreen(BlockPos pos, String npcName, String cityName, String customApiId, String gender, int spawnRadius) {
        super(Component.literal("Quest Giver Spawner"));
        this.pos = pos;
        this.npcName = npcName;
        this.cityName = cityName;
        this.customApiId = customApiId == null ? "" : customApiId;
        this.gender = (gender == null || gender.isEmpty()) ? "female" : gender.toLowerCase();
        this.spawnRadius = spawnRadius;

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
            
            customApiIdBox.visible = "Generic Combat".equals(npcName);
            genderButton.visible = !("Generic Escort".equals(npcName) || "Generic Combat".equals(npcName));
        }).bounds(cx - 110, cy - 60, 220, 20).build();
        addRenderableWidget(npcNameButton);

        customApiIdBox = new EditBox(this.font, cx - 110, cy - 30, 220, 20, Component.literal("Internal API ID"));
        customApiIdBox.setValue(customApiId);
        customApiIdBox.visible = "Generic Combat".equals(npcName);
        addRenderableWidget(customApiIdBox);

        cityNameBox = new EditBox(this.font, cx - 110, cy, 220, 20, Component.literal("City Name"));
        cityNameBox.setValue(cityName);
        addRenderableWidget(cityNameBox);

        String initialGenderText = "Gender: " + (this.gender.substring(0, 1).toUpperCase() + this.gender.substring(1));
        genderButton = Button.builder(Component.literal(initialGenderText), b -> {
            this.gender = this.gender.equals("female") ? "male" : "female";
            b.setMessage(Component.literal("Gender: " + (this.gender.substring(0, 1).toUpperCase() + this.gender.substring(1))));
        }).bounds(cx - 110, cy + 30, 220, 20).build();
        
        genderButton.visible = !("Generic Escort".equals(npcName) || "Generic Combat".equals(npcName));
        addRenderableWidget(genderButton);

        // New Radius Input Box
        spawnRadiusBox = new EditBox(this.font, cx - 110, cy + 60, 220, 20, Component.literal("Spawn Radius"));
        spawnRadiusBox.setValue(String.valueOf(this.spawnRadius));
        addRenderableWidget(spawnRadiusBox);

        // Per-spawner directions hint. It opens empty on purpose: the block's current hint is not
        // in the payload that opens this screen, so a blank box means "keep whatever is stored"
        // rather than "erase it". Typing a hint replaces it. The server bounds this too -- the
        // limit here only stops the box from producing a save the server would refuse.
        directionsBox = new EditBox(this.font, cx - 110, cy + 90, 220, 20, Component.literal("Local Directions"));
        directionsBox.setMaxLength(
                com.seggellion.britannia_mod.block.entity.QuestGiverSpawnBlockEntity.MAX_DIRECTIONS_LENGTH);
        directionsBox.setValue("");
        addRenderableWidget(directionsBox);

        // Shifted down to accommodate the new input boxes
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> saveAndClose())
                .bounds(cx - 110, cy + 120, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(cx + 10, cy + 120, 100, 20).build());
    }

    private void saveAndClose() {
        try {
            String newCity = cityNameBox.getValue().trim();
            String newCustomId = customApiIdBox.getValue().trim();
            int newRadius = 5; // Fallback
            
            try {
                newRadius = Integer.parseInt(spawnRadiusBox.getValue().trim());
            } catch (NumberFormatException e) {
                // If the user types text instead of a number, we gracefully fallback to 5
            }

            String newDirections = directionsBox.getValue().trim();

            NetworkHandler.sendToServer(new QuestGiverSpawnConfigC2SPayload(pos, availableNpcs.get(npcIdx), newCity, newCustomId, gender, newRadius, newDirections));
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
        
        if (customApiIdBox.visible) {
            gg.drawString(this.font, "Rails API Target (npc_name)", cx - 110, cy - 42, 0xFFFF55);
        }
        
        gg.drawString(this.font, "Assigned City (Economy)", cx - 110, cy - 12, 0xFFFFFF);
        gg.drawString(this.font, "Wander Radius (Blocks)", cx - 110, cy + 48, 0xFFFFFF);
        gg.drawString(this.font, "Local Directions (blank keeps current)", cx - 110, cy + 78, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {}
    @Override
    public void onClose() { Minecraft.getInstance().setScreen(null); }
}