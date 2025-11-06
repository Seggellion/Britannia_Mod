package com.seggellion.britannia_mod.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

// Your mod classes
import com.seggellion.britannia_mod.api.RailsApi;
import com.seggellion.britannia_mod.shop.Product;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.npc.NpcRoleHandler;
import com.seggellion.britannia_mod.item.WeightedFishItem;
import com.seggellion.britannia_mod.npc.TraderRoleHandler;
import com.seggellion.britannia_mod.npc.SalvageTraderRoleHandler;
import com.seggellion.britannia_mod.npc.MerchantRoleHandler;
import com.seggellion.britannia_mod.npc.NpcType;
import com.seggellion.britannia_mod.item.MaterialQualityJewelryItem;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class NpcCatalogScreen extends Screen {
    private final NpcRoleHandler roleHandler;
    private final NpcType type;
    
    private static final int GUI_W = 255, GUI_H = 250, ROW_H = 20;
    private static final int TEXT_PADDING = (int) (GUI_W * 0.15);
    private static final float COLUMN_SCALE = 0.75f;

    private int guiLeft, guiTop;
    private final Player player;

    // Role/City context from packet
    private final String role;
    private final String city;
    private final int entityId;

    // Catalog is fetched asynchronously
    private List<Product> catalog = new ArrayList<>();
    private final Map<Product, Integer> cart = new HashMap<>();
    private int totalPrice = 0;
    private int playerGold = 0;

    private final List<Button> plusButtons = new ArrayList<>();
    private final List<Button> minusButtons = new ArrayList<>();

    private long lastClickTime = 0;
    private Product lastClickedProduct = null;
    private static final Logger LOGGER = LogUtils.getLogger();

    public NpcCatalogScreen(NpcType type, String role, String city, int entityId, Player player) {
        super(Component.literal((type == NpcType.TRADER ? "Sell" : "Buy") + " Vendor"));
        this.role = role;
        this.city = city;
        this.entityId = entityId;
        this.player = player;
        this.type = type;
    // Decide which handler to use (buy/sell behavior)
        switch (type) {
            case TRADER -> {
                if (role.toLowerCase(Locale.ROOT).contains("salvage")) {
                    this.roleHandler = new SalvageTraderRoleHandler(role, city);
                } else {
                    this.roleHandler = new TraderRoleHandler(role, city);
                }
            }
            case MERCHANT -> this.roleHandler = new MerchantRoleHandler(role, city);
            default -> throw new IllegalArgumentException("Unsupported NPC type: " + type);
        }

    }


    @Override
    protected void init() {
        this.guiLeft = (width - GUI_W) / 2;
        this.guiTop = (height - GUI_H) / 2 - 10;

        updatePlayerGold();
        rebuildRowButtons();

        int cartX = guiLeft + GUI_W / 2 + (int)(10 * COLUMN_SCALE);

      // Button text driven by handler (“Buy” for merchant, “Sell” for trader)
        addRenderableWidget(
            Button.builder(Component.literal(roleHandler.getActionLabel()), b -> sendTransaction())
                .bounds(cartX, guiTop + GUI_H - 29, 50, 18)
                .build()
        );
        LOGGER.info("Catalog Loaded!");
        // 🔥 Fetch catalog async from Rails
        fetchCatalogFromRails();
    }

    private void fetchCatalogFromRails() {
     roleHandler.fetchCatalog(player, city, fetched -> {
            this.catalog = fetched != null ? aggregateCatalog(fetched) : List.of();
            rebuildRowButtons();
        });
    }

private List<Product> aggregateCatalog(List<Product> fetched) {
    Map<String, Integer> counts = new HashMap<>();
    Map<String, Product> byId = new HashMap<>();

    for (Product p : fetched) {
        counts.merge(p.itemId(), 1, Integer::sum);
        byId.putIfAbsent(p.itemId(), p);
    }

    List<Product> unique = new ArrayList<>();
    for (Product p : byId.values()) {
        int max = counts.get(p.itemId());
        ItemStack stack = p.stack();

        // Attach max quantity using NeoForge 1.21.1 data components
        CompoundTag tag = new CompoundTag();
        tag.putInt("max_quantity", max);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        unique.add(new Product(p.itemId(), p.name(), p.price(), stack));
    }

    return unique;
}

private JsonArray collectPlayerFish() {
    JsonArray arr = new JsonArray();

    for (ItemStack stack : player.getInventory().items) {
        if (stack.getItem() instanceof WeightedFishItem fishItem) {
            JsonObject j = new JsonObject();
            j.addProperty("item_id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            j.addProperty("weight", fishItem.getWeight(stack)); // assuming your WeightedFishItem has this
            j.addProperty("quantity", stack.getCount());
            arr.add(j);
        }
    }

    return arr;
}


private JsonArray collectPlayerSalvageJewelry() {
    JsonArray arr = new JsonArray();

    for (ItemStack stack : player.getInventory().items) {
        if (stack.getItem() instanceof MaterialQualityJewelryItem jewelry) {
            MaterialQualityJewelryItem.UOMaterial mat = MaterialQualityJewelryItem.getMaterial(stack);

            if (mat == MaterialQualityJewelryItem.UOMaterial.COPPER ||
                mat == MaterialQualityJewelryItem.UOMaterial.SILVER ||
                mat == MaterialQualityJewelryItem.UOMaterial.GOLD) {

                JsonObject j = new JsonObject();
                j.addProperty("item_id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                j.addProperty("material", mat.id());
                j.addProperty("quality", MaterialQualityJewelryItem.getQuality(stack));
                j.addProperty("quantity", stack.getCount());
                arr.add(j);
            }
        }
    }

    return arr;
}


protected void renderBg(GuiGraphics gg, float pt, int mx, int my) {
       Minecraft.getInstance().getTextureManager().bindForSetup(roleHandler.getBackground());
        gg.blit(roleHandler.getBackground(), guiLeft, guiTop, 0, 0, GUI_W, GUI_H);

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
        gg.drawString(font, p.price() + "c", textX + 20, y + 14, textColor, false);

        y += ROW_H;
    }

    // === RIGHT COLUMN: CART STARTING HALFWAY DOWN ===
    int cartY = guiTop + GUI_H / 2 + 28;
    int cartX = (guiLeft + GUI_W / 2 + (int)(10 * COLUMN_SCALE))-22;


    // Only iterate through cart entries, not the entire catalog
    for (Map.Entry<Product, Integer> entry : cart.entrySet()) {
        Product p = entry.getKey();
        int q = entry.getValue();

        String text = "  " + q + "   " + p.name();
        gg.drawString(font, text, cartX - 10, cartY + 4, textColor, false);

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

    private void rebuildRowButtons() {
        plusButtons.forEach(this::removeWidget);
        minusButtons.forEach(this::removeWidget);
        plusButtons.clear();
        minusButtons.clear();


        int y = (guiTop + GUI_H / 2) + 25;

    for (Product p : cart.keySet()) {
            if (!cart.containsKey(p)) continue;

            int cartX = (guiLeft + GUI_W / 2 + (int)(10 * COLUMN_SCALE))-10;

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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // Right-click closes the screen
            if (button == 1) {
                Minecraft.getInstance().setScreen(null);
                return true; // Consume event so it doesn’t propagate
            }

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

        private int getMaxQuantity(Product p) {
            CustomData customData = p.stack().get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag tag = customData.getUnsafe(); // safe for reading
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

            totalPrice = cart.entrySet().stream()
                .mapToInt(e -> e.getKey().price() * e.getValue())
                .sum();

            rebuildRowButtons();
        }


    private void sendPurchase() {
        roleHandler.performTransaction(player, entityId, cart, totalPrice, () -> {
            cart.clear();
            totalPrice = 0;
            rebuildRowButtons();
        });
    }

        private void sendTransaction() {
        roleHandler.performTransaction(player, entityId, cart, totalPrice, () -> {
            cart.clear();
            totalPrice = 0;
            rebuildRowButtons();
            // ✅ Close the catalog screen after success
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().setScreen(null);
            });
        });
    }


    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        updatePlayerGold();
        this.renderBackground(gg, mx, my, pt);
        this.renderBg(gg, pt, mx, my);
        super.render(gg, mx, my, pt);
    }

    private void updatePlayerGold() {
        playerGold = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem().equals(ItemRegistry.COPPER_COIN.get())) {
                playerGold += stack.getCount();
            }
        }
    }


    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // intentionally blank
    }


    @Override
    public boolean isPauseScreen() { return false; }
}
