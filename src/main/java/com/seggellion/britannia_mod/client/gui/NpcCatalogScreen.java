package com.seggellion.britannia_mod.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.seggellion.britannia_mod.shop.Product;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.item.WeightedFishItem;
import com.seggellion.britannia_mod.item.WeightedWoodItem;
import com.seggellion.britannia_mod.npc.NpcRoleHandler;
import com.seggellion.britannia_mod.npc.NpcType;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class NpcCatalogScreen extends Screen {
    private final NpcRoleHandler roleHandler;
    private final NpcType type;
    
    // Increased row height slightly to comfortably fit 2 lines of wrapped text
    private static final int GUI_W = 255, GUI_H = 250, ROW_H = 28; 
    private static final float COLUMN_SCALE = 0.75f;

    // Viewport & Layout Constants
    private static final int LIST_TOP_OFFSET = 33;
    private static final int LIST_WIDTH = 100; 
    private static final int VIEWPORT_HEIGHT = 90; 

    private int guiLeft, guiTop;
    private final Player player;

    private final String role;
    private final String city;
    private final int entityId;
    private final boolean hasPreFetchedCatalog;

    private List<Product> catalog = new ArrayList<>();
    private final Map<Product, Integer> cart = new HashMap<>();
    
    // Wallet Cache
    private int playerGoldCount = 0;
    private int playerSilverCount = 0;
    private int playerCopperCount = 0;

    private final List<Button> plusButtons = new ArrayList<>();
    private final List<Button> minusButtons = new ArrayList<>();

    private long lastClickTime = 0;
    private Product lastClickedProduct = null;
    private static final Logger LOGGER = LogUtils.getLogger();

    // Scrolling Variables
    private float scrollOffset = 0;
    private boolean isScrolling = false;

    public NpcCatalogScreen(NpcType type, String role, String city, int entityId, Player player, NpcRoleHandler roleHandler, List<Product> preFetchedCatalog) {
        super(Component.literal((type == NpcType.TRADER ? "Sell" : "Buy") + " Vendor"));
        this.role = role;
        this.city = city;
        this.entityId = entityId;
        this.player = player;
        this.type = type;
        this.roleHandler = roleHandler;
        this.hasPreFetchedCatalog = preFetchedCatalog != null;
        if (preFetchedCatalog != null) {
            this.catalog = aggregateCatalog(preFetchedCatalog);
        }
    }

    @Override
    protected void init() {
        this.guiLeft = (width - GUI_W) / 2;
        this.guiTop = (height - GUI_H) / 2 - 10;

        updatePlayerWallet();
        rebuildRowButtons();

        int cartX = guiLeft + GUI_W / 2 + (int)(10 * COLUMN_SCALE);

        addRenderableWidget(
            Button.builder(Component.literal(roleHandler.getActionLabel()), b -> sendTransaction())
                .bounds(cartX, guiTop + GUI_H - 29, 50, 18)
                .build()
        );
        LOGGER.info("Catalog Loaded!");
        if (!hasPreFetchedCatalog) {
            fetchCatalogFromRails();
        }
    }

    private void fetchCatalogFromRails() {
        roleHandler.fetchCatalog(player, city, fetched -> {
            this.catalog = fetched != null ? aggregateCatalog(fetched) : List.of();
            // Reset scroll when catalog reloads
            this.scrollOffset = 0;
            rebuildRowButtons();
        });
    }

    private List<Product> aggregateCatalog(List<Product> fetched) {
        if (type == NpcType.MERCHANT) {
            return aggregateMerchantCatalog(fetched);
        }

        Map<String, Product> byId = new HashMap<>();

        for (Product p : fetched) {
            String compositeKey = p.itemId() + "::" + p.name();
            byId.putIfAbsent(compositeKey, p);
        }

        List<Product> unique = new ArrayList<>();
        
        for (Product p : byId.values()) {
            ItemStack templateStack = p.stack();
            if (templateStack.isEmpty()) continue;

            String displayName = p.name();
            
            // --- Metadata Parsing Logic (Wine) ---
            if (displayName.startsWith("DATA|")) {
                try {
                    String[] parts = displayName.substring(5).split("\\|");
                    if (parts.length >= 5) {
                        String winery = parts[0];
                        String grape = parts[1];
                        int year = Integer.parseInt(parts[2]);
                        int quality = Integer.parseInt(parts[3]);
                        String labelColor = parts[4];
                        
                        var reconstructedData = new com.seggellion.britannia_mod.component.WineData(
                            winery, grape, year, quality, "", labelColor
                        );
                        templateStack.set(com.seggellion.britannia_mod.registry.DataComponentRegistry.WINE_DATA, reconstructedData);
                        displayName = winery + " " + grape + " '" + year + " (Q" + quality + ")";
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to parse wine metadata", e);
                }
            }

            // --- Inventory Counting Logic ---
            boolean isWineProduct = templateStack.has(com.seggellion.britannia_mod.registry.DataComponentRegistry.WINE_DATA);
            int totalCount = 0;

            for (ItemStack invStack : player.getInventory().items) {
                if (invStack.isEmpty()) continue;
                if (!matchesProduct(invStack, templateStack, p, isWineProduct)) continue;
                totalCount += invStack.getCount();
            }

            if (totalCount > 0) {
                CompoundTag tag = new CompoundTag();
                tag.putInt("max_quantity", totalCount);
                templateStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                unique.add(new Product(p.itemId(), displayName, p.price(), p.currency(), templateStack));
            }
        }
        return unique;
    }

    private List<Product> aggregateMerchantCatalog(List<Product> fetched) {
        Map<String, Product> byId = new HashMap<>();
        for (Product p : fetched) {
            String compositeKey = p.itemId() + "::" + p.name();
            byId.putIfAbsent(compositeKey, p);
        }
        return new ArrayList<>(byId.values());
    }

    private boolean matchesProduct(ItemStack invStack, ItemStack templateStack, Product product, boolean isWineProduct) {
        if (invStack.getItem() != templateStack.getItem()) return false;

        if (isWineProduct) {
            if (!invStack.has(com.seggellion.britannia_mod.registry.DataComponentRegistry.WINE_DATA)) return false;
            var templateData = templateStack.get(com.seggellion.britannia_mod.registry.DataComponentRegistry.WINE_DATA);
            var invData = invStack.get(com.seggellion.britannia_mod.registry.DataComponentRegistry.WINE_DATA);

            return invData.wineryName().equals(templateData.wineryName()) &&
                    invData.grapeType().equals(templateData.grapeType()) &&
                    invData.year() == templateData.year() &&
                    invData.quality() == templateData.quality() &&
                    invData.labelColor().equalsIgnoreCase(templateData.labelColor());
        }

        if (invStack.getItem() instanceof WeightedWoodItem woodItem) {
            return productNameMatches(product.name(), woodItem.getWoodType(invStack), invStack);
        }

        if (invStack.getItem() instanceof WeightedFishItem fishItem) {
            return productNameMatches(product.name(), fishItem.getFishType(invStack), invStack);
        }

        return true;
    }

    private boolean productNameMatches(String requestedName, String stackName, ItemStack stack) {
        if (requestedName == null || requestedName.isBlank()) return true;
        if (requestedName.equalsIgnoreCase(stackName)) return true;

        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return requestedName.equalsIgnoreCase(path);
    }

    private String getCurrencySuffix(String currency) {
        if (currency == null) return "c";
        return switch (currency.toLowerCase()) {
            case "gold" -> "g";
            case "silver" -> "s";
            default -> "c";
        };
    }

    // --- SCROLLING LOGIC ---
    
    private int getMaxScroll() {

        return Math.max(0, (this.catalog.size() * ROW_H) - VIEWPORT_HEIGHT);
    }

    // [DIAGRAM: Scroll Logic] 
    // If you imagine the list as a long paper strip, 'scrollOffset' moves the viewport down.
    // 'scrollY' from the mouse is usually +1.0 (up) or -1.0 (down).
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {

        if (this.getMaxScroll() > 0) {
            float scrollAmount = (float) (scrollY * ROW_H / 2.0);
            this.scrollOffset = Mth.clamp(this.scrollOffset - scrollAmount, 0, this.getMaxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isScrolling && this.getMaxScroll() > 0) {
            int scrollBarH = (int) ((float) (VIEWPORT_HEIGHT * VIEWPORT_HEIGHT) / (this.catalog.size() * ROW_H));
            scrollBarH = Mth.clamp(scrollBarH, 32, VIEWPORT_HEIGHT);
            
            double maxScrollbarMovement = VIEWPORT_HEIGHT - scrollBarH;
            double scrollFactor = this.getMaxScroll() / maxScrollbarMovement;
            
            this.scrollOffset = Mth.clamp(this.scrollOffset + (float) (dragY * scrollFactor), 0, getMaxScroll());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isScrolling = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // --- RENDERING ---

    protected void renderBg(GuiGraphics gg, float pt, int mx, int my) {
        Minecraft.getInstance().getTextureManager().bindForSetup(roleHandler.getBackground());
        gg.blit(roleHandler.getBackground(), guiLeft, guiTop, 0, 0, GUI_W, GUI_H);

        int textColor = 0x9f3215;

        if (catalog.isEmpty()) {
            gg.drawCenteredString(font, "Loading catalogue ...",
                guiLeft + GUI_W / 2, guiTop + GUI_H / 2 - 4, textColor);
            return;
        }

        // === LEFT COLUMN: SCROLLABLE PRODUCT LIST ===
        int listX = guiLeft + 18;
        int listY = guiTop + LIST_TOP_OFFSET;
        
        // [FIX] Manual Scissor Enable
        enableScissor(listX, listY, listX + LIST_WIDTH + 35, listY + VIEWPORT_HEIGHT);

        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(0, -scrollOffset, 0);

        int y = listY;
        
        for (Product p : catalog) {
            // Optimization + Scissor Safety:
            // Only draw if the item is within the visible window (plus a small buffer)
            if (y + ROW_H > listY + scrollOffset && y < listY + VIEWPORT_HEIGHT + scrollOffset) {
                
                ItemStack stack = p.stack();
                
                // Draw Icon
                gg.renderItem(stack, listX, y + 2);
                gg.renderItemDecorations(font, stack, listX, y + 2);

                int textX = listX + 22; 

                // [FIX] Text Wrapping - Use split to limit lines
                // We manually get the lines so we can force it to stop at 2 lines
                var splitText = font.split(Component.literal(p.name()), LIST_WIDTH);
                
                int lineY = y + 2;
                for (int i = 0; i < Math.min(2, splitText.size()); i++) {
                    gg.drawString(font, splitText.get(i), textX, lineY, textColor, false);
                    lineY += 9;
                }
                
                // Draw Price below name
                String priceStr = p.price() + getCurrencySuffix(p.currency());
                int priceColor = 0x9f3215; 
                gg.drawString(font, priceStr, textX, y + 20, priceColor, false);
            }
            y += ROW_H;
        }

        pose.popPose();
        
        // [FIX] Manual Scissor Disable
        RenderSystem.disableScissor();

        // === SCROLLBAR (Visual) ===
        if (getMaxScroll() > 0) {
            int scrollBarH = (int) ((float) (VIEWPORT_HEIGHT * VIEWPORT_HEIGHT) / (this.catalog.size() * ROW_H));
            scrollBarH = Mth.clamp(scrollBarH, 32, VIEWPORT_HEIGHT);
            int scrollBarX = listX + LIST_WIDTH + 28;
            int scrollBarY = listY + (int) ((scrollOffset / getMaxScroll()) * (VIEWPORT_HEIGHT - scrollBarH));
            
            gg.fill(scrollBarX, listY, scrollBarX + 2, listY + VIEWPORT_HEIGHT, 0xFF000000); // Track
            gg.fill(scrollBarX, scrollBarY, scrollBarX + 2, scrollBarY + scrollBarH, 0xFF888888); // Handle
        }

        // === RIGHT COLUMN: CART ===
        int cartY = guiTop + GUI_H / 2 + 28;
        int cartX = (guiLeft + GUI_W / 2 + (int)(10 * COLUMN_SCALE)) - 22;

        for (Map.Entry<Product, Integer> entry : cart.entrySet()) {
            Product p = entry.getKey();
            int q = entry.getValue();

            String text = "  " + q + "   " + p.name();
            if (font.width(text) > 90) {
                text = font.substrByWidth(Component.literal(text), 85).getString() + "...";
            }
            gg.drawString(font, text, cartX - 10, cartY + 4, textColor, false);
            cartY += ROW_H;
        }

        // === BOTTOM ROW: TOTALS ===
        int bottomY = guiTop + GUI_H - 37;
        int totalX = cartX + 27;

        String cartTotal = calculateCartTotalDisplay();
        String walletTotal = formatWalletDisplay();

        pose.pushPose();
        pose.translate(totalX, bottomY, 0);
        pose.scale(0.8f, 0.8f, 1.0f); 

        gg.drawString(font, "Total: " + cartTotal, 0, 0, 0x000000, false);
        gg.drawString(font, "Wallet: " + walletTotal, 0, 10, 0x555555, false);
        
        pose.popPose();
    }
    
    /**
     * [FIX] Robust Scissor Helper
     * Calculates the raw window coordinates for glScissor, accounting for GUI Scale.
     * This fixes "bleeding" issues where GuiGraphics.enableScissor fails or is misconfigured.
     */
    private void enableScissor(int x, int y, int x2, int y2) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        int windowHeight = Minecraft.getInstance().getWindow().getHeight();
        
        // Calculate width and height
        int w = x2 - x;
        int h = y2 - y;

        // Convert to window coordinates (Raw Pixels)
        int sX = (int)(x * scale);
        int sY = (int)(windowHeight - (y + h) * scale); // GL Scissor Y is from BOTTOM
        int sW = (int)(w * scale);
        int sH = (int)(h * scale);
        
        // Apply Scissor directly to RenderSystem
        RenderSystem.enableScissor(sX, sY, sW, sH);
    }
    
    // ... [calculateCartTotalDisplay and formatWalletDisplay methods unchanged] ...
    private String calculateCartTotalDisplay() {
        int gold = 0, silver = 0, copper = 0;
        for (Map.Entry<Product, Integer> entry : cart.entrySet()) {
            Product p = entry.getKey();
            int qty = entry.getValue();
            String c = p.currency() == null ? "copper" : p.currency().toLowerCase();
            switch (c) {
                case "gold" -> gold += p.price() * qty;
                case "silver" -> silver += p.price() * qty;
                default -> copper += p.price() * qty;
            }
        }
        List<String> parts = new ArrayList<>();
        if (gold > 0) parts.add(gold + "g");
        if (silver > 0) parts.add(silver + "s");
        if (copper > 0) parts.add(copper + "c");
        if (parts.isEmpty()) return "0c";
        return String.join(" ", parts);
    }
    
    private String formatWalletDisplay() {
        List<String> parts = new ArrayList<>();
        if (playerGoldCount > 0) parts.add(playerGoldCount + "g");
        if (playerSilverCount > 0) parts.add(playerSilverCount + "s");
        if (playerCopperCount > 0) parts.add(playerCopperCount + "c");
        if (parts.isEmpty()) return "Empty";
        return String.join(" ", parts);
    }

    private void rebuildRowButtons() {
        plusButtons.forEach(this::removeWidget);
        minusButtons.forEach(this::removeWidget);
        plusButtons.clear();
        minusButtons.clear();

        int y = (guiTop + GUI_H / 2) + 25;

        for (Product p : cart.keySet()) {
            if (!cart.containsKey(p)) continue;

            int cartX = (guiLeft + GUI_W / 2 + (int)(10 * COLUMN_SCALE)) - 10;
            int buttonOffset = (int)(110 * COLUMN_SCALE);

            Button plus = Button.builder(Component.literal("+"), b -> modifyCart(p, +1))
                .bounds(cartX + buttonOffset, y + 4, 12, 12)
                .build();
            plus.active = cart.getOrDefault(p, 0) < getMaxQuantity(p);

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

    private void updatePlayerWallet() {
        playerGoldCount = 0;
        playerSilverCount = 0;
        playerCopperCount = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            if (stack.getItem() == ItemRegistry.GOLD_COIN.get()) playerGoldCount += stack.getCount();
            else if (stack.getItem() == ItemRegistry.SILVER_COIN.get()) playerSilverCount += stack.getCount();
            else if (stack.getItem() == ItemRegistry.COPPER_COIN.get()) playerCopperCount += stack.getCount();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
             Minecraft.getInstance().setScreen(null);
             return true;
        }

        int listX = guiLeft + 18;
        int listY = guiTop + LIST_TOP_OFFSET;
        int scrollBarX = listX + LIST_WIDTH + 28;
    
        // Check if mouse is on the scrollbar track
        if (mouseX >= scrollBarX && mouseX <= scrollBarX + 6 && 
            mouseY >= listY && mouseY < listY + VIEWPORT_HEIGHT) {
            this.isScrolling = true;
            return true;
        }
        this.isScrolling = false; 

        // Check if mouse is within the viewport
        if (mouseX >= listX && mouseX < listX + LIST_WIDTH + 30 &&
            mouseY >= listY && mouseY < listY + VIEWPORT_HEIGHT) {
            
            double absoluteY = mouseY - listY + scrollOffset;
            int index = (int) (absoluteY / ROW_H);

            if (index >= 0 && index < catalog.size()) {
                Product p = catalog.get(index);
                long now = System.currentTimeMillis();
                
                if (lastClickedProduct == p && now - lastClickTime < 400) {
                    modifyCart(p, 1);
                    lastClickedProduct = null;
                } else {
                    lastClickedProduct = p;
                    lastClickTime = now;
                }
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    private int getMaxQuantity(Product p) {
        CustomData customData = p.stack().get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.getUnsafe(); 
            if (tag.contains("max_quantity", Tag.TAG_INT)) {
                return tag.getInt("max_quantity");
            }
        }
        return 1;
    }

    private void modifyCart(Product p, int delta) {
        int current = cart.getOrDefault(p, 0);
        int maxQty = getMaxQuantity(p);
        int newQty = current + delta;
        if (newQty > maxQty) newQty = maxQty;
        if (newQty <= 0) cart.remove(p);
        else cart.put(p, newQty);
        rebuildRowButtons();
    }

    private void sendTransaction() {
        int approximateTotal = 0; 
        roleHandler.performTransaction(player, entityId, cart, approximateTotal, () -> {
            cart.clear();
            rebuildRowButtons();
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().setScreen(null);
            });
        });
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        updatePlayerWallet();
        this.renderBackground(gg, mx, my, pt);
        this.renderBg(gg, pt, mx, my);
        super.render(gg, mx, my, pt);
    }
    
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // intentionally blank
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
