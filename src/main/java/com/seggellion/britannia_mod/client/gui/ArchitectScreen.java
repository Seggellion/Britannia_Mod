package com.seggellion.britannia_mod.client.gui.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.seggellion.britannia_mod.network.payload.BuyItemsC2SPayload;
import com.seggellion.britannia_mod.shop.Product;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.world.entity.player.Player;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;

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

import com.seggellion.britannia_mod.ModSounds;


import java.util.*;

public class ArchitectScreen extends Screen {
    private static final ResourceLocation BG =
        ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/buy_screen.png");

    private static final int GUI_W = 255, GUI_H = 250, ROW_H = 20;
    private static final int TEXT_PADDING = (int) (GUI_W * 0.15);
    private int guiLeft, guiTop;

    private final Player player;

    private final List<Product> catalog;
    private final Map<Product, Integer> cart = new HashMap<>();
    private int totalPrice = 0;
    private final int architectId;
    private int playerGold = 0; 
private static final float COLUMN_SCALE = 0.75f; // 15% narrower columns

    private final List<Button> plusButtons = new ArrayList<>();
    private final List<Button> minusButtons = new ArrayList<>();

    private long lastClickTime = 0;
    private Product lastClickedProduct = null;

    public ArchitectScreen(List<Product> catalog, int architectId, Player player) {
        super(Component.literal("Architect Vendor"));
        this.catalog = catalog;
        this.architectId = architectId;
        this.player = player;
    }

    @Override
    protected void init() {
        this.guiLeft = (width - GUI_W) / 2;
        this.guiTop = (height - GUI_H) / 2-10;

        playerGold = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem().equals(ItemRegistry.GOLD_COIN.get())) {
                playerGold += stack.getCount();
            }
        }

        rebuildRowButtons();

        int cartX = guiLeft + GUI_W / 2 + (int)(10 * COLUMN_SCALE);


        addRenderableWidget(
            Button.builder(Component.literal("Buy"), b -> sendPurchase())
                .bounds(cartX, guiTop + GUI_H - 29, 50, 18) // ← moved to cartX
                .build()
        );

    }

private void rebuildRowButtons() {
    plusButtons.forEach(this::removeWidget);
    minusButtons.forEach(this::removeWidget);
    plusButtons.clear();
    minusButtons.clear();

    int y = (guiTop + GUI_H / 2) + 25;

    for (Product p : catalog) {
        if (!cart.containsKey(p)) continue;

        int cartX = (guiLeft + GUI_W / 2 + (int)(10 * COLUMN_SCALE))-10;

        int buttonOffset = (int)(110 * COLUMN_SCALE);

        Button plus = Button.builder(Component.literal("+"), b -> modifyCart(p, +1))
            .bounds(cartX + buttonOffset, y + 4, 12, 12)
            .build();

        Button minus = Button.builder(Component.literal("–"), b -> modifyCart(p, -1))
            .bounds(cartX + buttonOffset + 16, y + 4, 12, 12)
            .build();
            
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
        rebuildRowButtons();
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
        BuyItemsC2SPayload.send(arr, totalPrice, architectId);
        cart.clear();
        totalPrice = 0;
        rebuildRowButtons();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int y = guiTop + 18;
        for (Product p : catalog) {
            int px = guiLeft + 8, py = y + 15;
            if (mouseX >= px && mouseX < px + 120 && mouseY >= py && mouseY < py + ROW_H) {
                long now = System.currentTimeMillis();
                if (lastClickedProduct == p && now - lastClickTime < 400) {
                    modifyCart(p, 1);
                    lastClickedProduct = null;
                } else {
                    lastClickedProduct = p;
                    lastClickTime = now;
                }
                break;
            }
            y += ROW_H;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
         updatePlayerGold();
        this.renderBackground(gg, mx, my, pt);
        this.renderBg(gg, pt, mx, my);
        super.render(gg, mx, my, pt);
    }

protected void renderBg(GuiGraphics gg, float pt, int mx, int my) {
    Minecraft.getInstance().getTextureManager().bindForSetup(BG);
    gg.blit(BG, guiLeft, guiTop, 0, 0, GUI_W, GUI_H);

    int textColor = 0x9f3215;

    if (catalog.isEmpty()) {
        gg.drawCenteredString(font, "Loading catalogue …",
            guiLeft + GUI_W / 2, guiTop + GUI_H / 2 - 4, textColor);
        return;
    }

    // === LEFT COLUMN: PRODUCT LIST ===
    int y = guiTop + 18+15;
    for (Product p : catalog) {
        ItemStack stack = p.stack();

        gg.renderItem(stack, guiLeft + 18, y+4);
        gg.renderItemDecorations(font, stack, guiLeft + 8, y);

        int textX = guiLeft + (int)(TEXT_PADDING * COLUMN_SCALE);
        gg.drawString(font, p.name(), textX + 20, y + 4, textColor, false);
        gg.drawString(font, p.price() + "g", textX + 20, y + 14, textColor, false);

        y += ROW_H;
    }

    // === RIGHT COLUMN: CART STARTING HALFWAY DOWN ===
    int cartY = guiTop + GUI_H / 2 + 28;
    int cartX = (guiLeft + GUI_W / 2 + (int)(10 * COLUMN_SCALE))-22;

    for (Product p : catalog) {
        Integer q = cart.get(p);
        if (q == null || q <= 0) continue;

        String text = "  " + q + "   " + p.name();
        gg.drawString(font, text, cartX-10, cartY + 4, textColor, false);

        cartY += ROW_H;
    }

    // === RIGHT COLUMN: TOTAL + GOLD + BUY BUTTON ===
    int bottomY = guiTop + GUI_H - 37;
    int totalX = cartX + 27;
    String summary =  totalPrice + "                 " + playerGold ;

    PoseStack pose = gg.pose();
    pose.pushPose();
    pose.translate(totalX, bottomY, 0);
    pose.scale(0.8f, 0.8f, 1.0f); 

    gg.drawString(font, summary, 0, 0, 0x000000, false);
    pose.popPose();

    }



    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // intentionally blank
    }

    private void updatePlayerGold() {
        playerGold = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem().equals(ItemRegistry.GOLD_COIN.get())) {
                playerGold += stack.getCount();
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
