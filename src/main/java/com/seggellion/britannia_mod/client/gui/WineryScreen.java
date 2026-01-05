package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.network.payload.BottlingPayload;
import com.seggellion.britannia_mod.block.entity.WineBarrelBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public class WineryScreen extends Screen {
    private final BlockPos barrelPos;
    
public static final List<String> WINERY_SUFFIXES = List.of(
    "Vineyards",
    "Cellars",
    "Winery",
    "Wines",
    "Vintners",
    "Family Vineyards",
    "Reserve",
    "Château",
    "Domaine",
    "Casa",
    "Cantina",
    "Tenuta",
    "Bodega",
    "Vigna",
    "Caves",
    "Heritage Collection",
    "Estates",
    "Estate Reserve",
    "Vineyard & Cellar",
    "Mountain Vineyards",
    "Hacienda",
    "Maison"
);

    // Inputs
    private String selectedSuffix = "Estates";
    private String selectedLabelColor = "red"; // Default color
    
    // Display Info
    private final String playerName;
    private final int currentYear;
private String grapeVariety = "Unknown";
private String grapeRegion = "Region";

public WineryScreen(BlockPos pos) {
        super(Component.literal("Bottle Wine"));
        this.barrelPos = pos;
        
        // 1. Get Vintner Name
        this.playerName = Minecraft.getInstance().player.getName().getString();
        
        // 2. Calculate Year
        long gameTime = Minecraft.getInstance().level.getGameTime();
        this.currentYear = (int) (gameTime / 24000L / 365L) + 1; 

        // 3. NEW: Fetch Grape Variety from the BlockEntity
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof WineBarrelBlockEntity wineBarrel) {
                // We access the getter you created in the BlockEntity
                this.grapeVariety = wineBarrel.getVariety();
                this.grapeRegion = wineBarrel.getRegion();
                // Optional: Capitalize the first letter if it's lowercase (e.g. "cabernet" -> "Cabernet")
                if (this.grapeVariety != null && !this.grapeVariety.isEmpty()) {
                     this.grapeVariety = this.grapeVariety.substring(0, 1).toUpperCase() + this.grapeVariety.substring(1);
                } else {
                    this.grapeVariety = "House Blend"; // Fallback if empty
                }
            
            }
        }
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 1. Suffix Selector (Dropdown)
        StringDropdownWidget suffixSelector = new StringDropdownWidget(
            centerX - 70, 
            centerY + 5, 
            140, 
            20, 
            selectedSuffix, // The currently saved value
            WINERY_SUFFIXES,
            (val) -> {
                this.selectedSuffix = val;
                // Optional: Trigger a packet sync here if this needs to save to the server immediately
            }
        );

        this.addRenderableWidget(suffixSelector);

        // 2. Label Color Selector
        this.addRenderableWidget(CycleButton.builder(Component::literal)
                // These values correspond to the texture variants you want (e.g. wine_bottle_green_blue)
                .withValues("red", "blue", "gold", "white", "brown", "pink", "silver", "black", "green", "orange", "blue", "yellow") 
                .withInitialValue(selectedLabelColor)
                .create(centerX - 70, centerY + 30, 140, 20, Component.literal("Label"), (btn, val) -> {
                    this.selectedLabelColor = (String) val;
                }));

        // 3. "Sign & Seal" Button
        this.addRenderableWidget(Button.builder(Component.literal("Sign & Seal"), button -> {
            sendPacket();
        }).bounds(centerX - 50, centerY + 60, 100, 20).build());
    }

    private void sendPacket() {
        // Send inputs: Suffix and Label Color
        PacketDistributor.sendToServer(new BottlingPayload(barrelPos, selectedSuffix, selectedLabelColor));
        this.onClose();
    }

@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Background Box
        graphics.fill(centerX - 90, centerY - 80, centerX + 90, centerY + 90, 0xCC000000);
        
        // Title
        graphics.drawCenteredString(this.font, this.title, centerX, centerY - 70, 0xFFD700); 

        // --- Info Display ---
        
        String previewName = playerName + " " + selectedSuffix;
        graphics.drawCenteredString(this.font, Component.literal(previewName), centerX, centerY - 50, 0xFFAA00); 

        // Region & Year
        graphics.drawString(this.font, "Region: " + grapeRegion, centerX - 70, centerY - 40, 0xAAAAAA, false);
        graphics.drawString(this.font, "Year: " + currentYear, centerX - 70, centerY -30, 0xAAAAAA, false);

        // NEW: Display Grape Variety
        // We place this under the Vintner name or near the region
        graphics.drawString(this.font, "Variety: " + grapeVariety, centerX - 70, centerY - 20, 0xCCFFCC, false); // Light green text


        super.render(graphics, mouseX, mouseY, partialTick);
    }
    
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // intentionally blank
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}