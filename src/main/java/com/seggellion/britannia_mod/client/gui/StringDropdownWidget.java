package com.seggellion.britannia_mod.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import java.util.List;
import java.util.function.Consumer;

public class StringDropdownWidget extends AbstractWidget {
    private final List<String> options;
    private final Consumer<String> onSelect;
    private boolean isOpen = false;
    private String selectedValue;
private static final net.minecraft.resources.ResourceLocation WIDGETS_LOCATION = 
    net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/gui/widgets.png");
        // Scrolling variables
    private double scrollAmount = 0;
    private static final int ITEM_HEIGHT = 14;
    private static final int MAX_VISIBLE_ITEMS = 8; // How many items to show before scrolling

    public StringDropdownWidget(int x, int y, int width, int height, 
                                String initialValue, List<String> options, 
                                Consumer<String> onSelect) {
        super(x, y, width, height, Component.literal(initialValue));
        this.options = options;
        this.selectedValue = initialValue;
        this.onSelect = onSelect;
    }

@Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1. Render the main button background manually
        // Determine the texture state: 0=disabled, 1=idle, 2=hovered
        int i = !this.active ? 0 : (this.isHoveredOrFocused() ? 2 : 1);
        
        // Draw the left half of the button
        guiGraphics.blit(WIDGETS_LOCATION, this.getX(), this.getY(), 0, 46 + i * 20, this.width / 2, this.height);
        // Draw the right half of the button
        guiGraphics.blit(WIDGETS_LOCATION, this.getX() + this.width / 2, this.getY(), 200 - this.width / 2, 46 + i * 20, this.width / 2, this.height);
        
        // Render current selection text centered
        int color = this.active ? 0xFFFFFF : 0xA0A0A0;
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, this.selectedValue, 
                this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, color);

        // 2. Render the dropdown list if open
        if (isOpen) {
            // Render on top of everything (high Z-index)
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 100); 

            int listHeight = Math.min(options.size(), MAX_VISIBLE_ITEMS) * ITEM_HEIGHT;
            int listY = this.getY() + this.height;
            
            // Background for the list
            guiGraphics.fill(this.getX(), listY, this.getX() + this.width, listY + listHeight, 0xFF000000);
            guiGraphics.renderOutline(this.getX(), listY, this.width, listHeight, 0xFFFFFFFF);

            // Scissor test to clip text when scrolling
            guiGraphics.enableScissor(this.getX(), listY, this.getX() + this.width, listY + listHeight);

            int currentY = listY - (int) scrollAmount;
            
            for (String option : options) {
                // Determine if hovered
                boolean isHovered = mouseX >= this.getX() && mouseX <= this.getX() + this.width &&
                                    mouseY >= currentY && mouseY < currentY + ITEM_HEIGHT &&
                                    mouseY >= listY && mouseY <= listY + listHeight;

                if (isHovered) {
                    guiGraphics.fill(this.getX() + 1, currentY, this.getX() + this.width - 1, currentY + ITEM_HEIGHT, 0xFF555555);
                }

                guiGraphics.drawString(Minecraft.getInstance().font, option, 
                        this.getX() + 4, currentY + 3, 0xFFFFFF, false);
                
                currentY += ITEM_HEIGHT;
            }

            guiGraphics.disableScissor();
            guiGraphics.pose().popPose();
        }
    }
    
    @Override
    public void onClick(double mouseX, double mouseY) {
        // Toggle open state if clicking the main button
        if (clickedWidget(mouseX, mouseY)) {
            isOpen = !isOpen;
            if (isOpen) playDownSound(Minecraft.getInstance().getSoundManager());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isOpen) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // Calculate list bounds
        int listHeight = Math.min(options.size(), MAX_VISIBLE_ITEMS) * ITEM_HEIGHT;
        int listY = this.getY() + this.height;

        // Check if click is inside the dropdown list
        if (mouseX >= this.getX() && mouseX <= this.getX() + this.width &&
            mouseY >= listY && mouseY <= listY + listHeight) {
            
            // Calculate which item was clicked based on scroll
            double relativeY = mouseY - listY + scrollAmount;
            int index = (int) (relativeY / ITEM_HEIGHT);

            if (index >= 0 && index < options.size()) {
                select(options.get(index));
                playDownSound(Minecraft.getInstance().getSoundManager());
            }
            return true;
        } 
        // If clicked outside the widget while open, close it
        else if (!isHovered) {
            isOpen = false;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) { // 1.20.2+ might use scrollX, scrollY
        if (isOpen) {
            int maxScroll = Math.max(0, (options.size() * ITEM_HEIGHT) - (MAX_VISIBLE_ITEMS * ITEM_HEIGHT));
            this.scrollAmount = Mth.clamp(this.scrollAmount - (scrollY * ITEM_HEIGHT), 0, maxScroll);
            return true;
        }
        return false;
    }

    private void select(String value) {
        this.selectedValue = value;
        this.onSelect.accept(value);
        this.isOpen = false;
    }

    private boolean clickedWidget(double mouseX, double mouseY) {
        return mouseX >= this.getX() && mouseX <= this.getX() + this.width &&
               mouseY >= this.getY() && mouseY <= this.getY() + this.height;
    }

@Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        // 1. Check if mouse is over the main button (standard behavior)
        boolean overButton = super.isMouseOver(mouseX, mouseY);

        // 2. If open, also check if mouse is over the dropdown list
        if (isOpen) {
            int listHeight = Math.min(options.size(), MAX_VISIBLE_ITEMS) * ITEM_HEIGHT;
            boolean overList = mouseX >= this.getX() && mouseX < this.getX() + this.width &&
                               mouseY >= this.getY() + this.height && 
                               mouseY < this.getY() + this.height + listHeight;
            
            return overButton || overList;
        }

        return overButton;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        // Implement narration if needed for accessibility
    }
}