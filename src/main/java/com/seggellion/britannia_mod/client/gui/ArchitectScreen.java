package com.seggellion.britannia_mod.client.gui.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.seggellion.britannia_mod.network.payload.BuyItemsC2SPayload;
import com.seggellion.britannia_mod.shop.Product;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.*;

public class ArchitectScreen extends Screen {
    private static final ResourceLocation BG =
        ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/buy_screen.png");

    private static final int GUI_W = 238, GUI_H = 238, ROW_H = 20;

    private int guiLeft, guiTop;

    private final List<Product> catalog;
    private final Map<Product, Integer> cart = new HashMap<>();
    private int totalPrice = 0;
    private final int architectId;

    private final List<Button> plusButtons = new ArrayList<>();
    private final List<Button> minusButtons = new ArrayList<>();

public ArchitectScreen(List<Product> catalog, int architectId) {
        super(Component.literal("Architect Vendor"));
        this.catalog = catalog;
        this.architectId = architectId;

    }

    @Override
    protected void init() {
        this.guiLeft = (width - GUI_W) / 2;
        this.guiTop = (height - GUI_H) / 2;

        rebuildRowButtons();

        // BUY button
        addRenderableWidget(
            Button.builder(Component.literal("Buy"), b -> sendPurchase())
                .bounds(guiLeft + GUI_W - 60, guiTop + GUI_H - 24, 50, 18)
                .build()
        );
    }

    private void rebuildRowButtons() {
        plusButtons.forEach(this::removeWidget);
        minusButtons.forEach(this::removeWidget);
        plusButtons.clear();
        minusButtons.clear();

        int y = guiTop + 18;
        for (Product p : catalog) {
            Button plus = Button.builder(Component.literal("+"), b -> modifyCart(p, +1))
                .bounds(guiLeft + 140, y, 12, 12).build();
            Button minus = Button.builder(Component.literal("–"), b -> modifyCart(p, -1))
                .bounds(guiLeft + 156, y, 12, 12).build();
            addRenderableWidget(plus);
            addRenderableWidget(minus);
            plusButtons.add(plus);
            minusButtons.add(minus);
            y += ROW_H;
        }
    }

    private void modifyCart(Product p, int delta) {
        int q = cart.getOrDefault(p, 0) + delta;
        if (q <= 0) cart.remove(p); else cart.put(p, q);
        totalPrice = cart.entrySet().stream()
            .mapToInt(e -> e.getKey().price() * e.getValue())
            .sum();
    }

    private void sendPurchase() {
        JsonArray arr = new JsonArray();
        cart.forEach((prod, qty) -> {
            JsonObject j = new JsonObject();
            j.addProperty("item_id", prod.itemId());
            j.addProperty("item_name", prod.name());
            j.addProperty("quantity", qty);
            j.addProperty("price", prod.price());
            arr.add(j);
        });
        BuyItemsC2SPayload.send(arr, totalPrice,architectId);
        cart.clear();
        totalPrice = 0;
    }

@Override
public void render(GuiGraphics gg, int mx, int my, float pt) {
    this.renderBackground(gg, mx, my, pt);
    this.renderBg(gg, pt, mx, my);
    super.render(gg, mx, my, pt);
}


    protected void renderBg(GuiGraphics gg, float pt, int mx, int my) {
        Minecraft.getInstance().getTextureManager().bindForSetup(BG);
        gg.blit(BG, guiLeft, guiTop, 0, 0, GUI_W, GUI_H);

        if (catalog.isEmpty()) {
            gg.drawCenteredString(font, "Loading catalogue …",
                guiLeft + GUI_W / 2, guiTop + GUI_H / 2 - 4, 0xFFFFFF);
            return;
        }

        int y = guiTop + 18;
        for (Product p : catalog) {
            ItemStack stack = p.stack();

            gg.renderItem(stack, guiLeft + 8, y);
            gg.renderItemDecorations(font, stack, guiLeft + 8, y);

            gg.drawString(font, p.name(), guiLeft + 28, y + 4, 0xFFFFFF, false);
            gg.drawString(font, p.price() + "g", guiLeft + 28, y + 14, 0xAAAAAA, false);

            int q = cart.getOrDefault(p, 0);
            if (q > 0) gg.drawString(font, "x" + q, guiLeft + 175, y + 4, 0xFFD700, false);
            y += ROW_H;
        }

        gg.drawString(font, "Total: " + totalPrice + "g",
            guiLeft + 134, guiTop + GUI_H - 38, 0xFFFFFF, false);
    }
    
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // intentionally blank – skip blur shader
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
