package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.CraftBlacksmithItemC2SPayload;
import com.seggellion.britannia_mod.skill.SkillManager;
import com.seggellion.britannia_mod.skill.crafting.CraftableDef;
import com.seggellion.britannia_mod.skill.crafting.CraftableRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import com.seggellion.britannia_mod.ModSounds;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.List;

public class BlacksmithyScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final String currentIngotId;
    
    // UI State
    private final List<CraftableDef> displayableCraftables = new ArrayList<>();
    private CraftableDef selectedDef = null;
    private Button craftButton;

    public BlacksmithyScreen(String currentIngotId) {
        super(Component.literal("Blacksmithing")); 
        this.currentIngotId = currentIngotId;
    }

    @Override
    protected void init() {
        super.init();
        
        displayableCraftables.clear();
        this.selectedDef = null; // Reset selection on init

        Player localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null) return;

        List<CraftableDef> allCraftables = CraftableRegistry.getAll();
        LOGGER.info("Filtering craftables for Ingot: {}", currentIngotId);

        for (CraftableDef def : allCraftables) {
            
            // 1. Material Check
            boolean hasCorrectMaterial = false;
            if (def.ingredients() != null) {
                for (var ingredient : def.ingredients()) {
                    String reqKey = ingredient.materialKey(); 
                    if (reqKey.equals("ingot") || reqKey.equals(this.currentIngotId)) { 
                        hasCorrectMaterial = true;
                        break;
                    }
                }
            }
            
            // 2. Skill Check
            boolean meetsSkillRequirements = true;
            if (def.skillRequirements() != null) {
                for (var skillReq : def.skillRequirements()) {
                    float playerSkill = SkillManager.getSkill(localPlayer.getUUID(), skillReq.skillKey());
                    if (playerSkill < skillReq.minValue()) {
                        meetsSkillRequirements = false;
                        break;
                    }
                }
            }
            
            if (hasCorrectMaterial && meetsSkillRequirements) {
                displayableCraftables.add(def);
            }
        }

        int cx = this.width / 2;
        int cy = this.height / 2;

        // Initialize the Craft button
        this.craftButton = Button.builder(Component.literal("Craft"), b -> {
            if (this.selectedDef != null) {
                // Send the network packet to the server to perform the craft
                Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(ModSounds.ANVIL.get(), 1.0F, 1.0F)
                );
                NetworkHandler.sendToServer(new CraftBlacksmithItemC2SPayload(this.selectedDef.id()));
            this.onClose();
            }
        }).bounds(cx - 40, cy + 90, 80, 20).build();
        
        this.craftButton.active = false; // Disabled until they click an item
        this.addRenderableWidget(this.craftButton);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Draw title
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 100, 0xFFFFFF);

        int startX = this.width / 2 - 100;
        int startY = this.height / 2 - 80;
        int spacing = 24; 
        
        int col = 0;
        int row = 0;

        ItemStack hoveredStack = null;
        CraftableDef hoveredDef = null;

        // Loop through our FILTERED list
        for (CraftableDef def : displayableCraftables) {
            int x = startX + (col * spacing);
            int y = startY + (row * spacing);

            // Draw a green border if this is the currently selected item
            if (def == this.selectedDef) {
                guiGraphics.fill(x - 2, y - 2, x + 18, y + 18, 0xFF00FF00); // Green outer
                guiGraphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF000000); // Black inner
            }

            // Fetch the display item
            ItemStack renderStack = new ItemStack(BuiltInRegistries.ITEM.get(def.resultItem()));

            // Render the 3D Item
            guiGraphics.renderItem(renderStack, x, y);

            // Hover state logic
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                guiGraphics.fill(x, y, x + 16, y + 16, 0x80FFFFFF); 
                hoveredStack = renderStack;
                hoveredDef = def;
            }

            // Grid wrap logic
            col++;
            if (col >= 8) {
                col = 0;
                row++;
            }
        }

        // Draw tooltips
        if (hoveredStack != null && hoveredDef != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(hoveredDef.displayName()).withStyle(ChatFormatting.YELLOW));

            if (hoveredDef.ingredients() != null) {
                for (var ingredient : hoveredDef.ingredients()) {
                    tooltip.add(Component.literal("Requires: " + ingredient.amount() + " items").withStyle(ChatFormatting.GRAY));
                }
            }

            if (hoveredDef.skillRequirements() != null) {
                for (var skillReq : hoveredDef.skillRequirements()) {
                    tooltip.add(Component.literal(skillReq.skillKey() + ": " + skillReq.minValue()).withStyle(ChatFormatting.DARK_AQUA));
                }
            }
            
            if (hoveredDef.exceptionalOnly()) {
                tooltip.add(Component.literal("Exceptional Quality Only").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            
            guiGraphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    // Intercept mouse clicks on the grid
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left click
            int startX = this.width / 2 - 100;
            int startY = this.height / 2 - 80;
            int spacing = 24; 
            
            int col = 0;
            int row = 0;

            for (CraftableDef def : displayableCraftables) {
                int x = startX + (col * spacing);
                int y = startY + (row * spacing);

                // If they clicked inside the 16x16 item box
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                    this.selectedDef = def;           // Set the selection
                    this.craftButton.active = true;   // Enable the craft button
                    
                    // Play vanilla UI click sound
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }

                col++;
                if (col >= 8) {
                    col = 0;
                    row++;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false; 
    }
}