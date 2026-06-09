package com.seggellion.britannia_mod.npc;

import com.google.gson.JsonArray;
import com.seggellion.britannia_mod.api.RailsApi;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.SellItemsC2SPayload;
import com.seggellion.britannia_mod.shop.Product;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class AbstractSellTraderRoleHandler implements NpcRoleHandler {
    protected final String role;
    protected final String city;

    protected AbstractSellTraderRoleHandler(String role, String city) {
        this.role = role;
        this.city = city;
    }

    @Override
    public void fetchCatalog(Player player, String city, Consumer<List<Product>> callback) {
        RailsApi.fetchTraderCatalog(city, role, collectSellableInventory(player), callback);
    }

    @Override
    public void performTransaction(Player player, int entityId,
                                   Map<Product, Integer> cart,
                                   int totalPrice,
                                   Runnable onSuccess) {
        NetworkHandler.sendToServer(new SellItemsC2SPayload(city, role, entityId, toRequests(player, cart)));
        onSuccess.run();
    }

    @Override
    public String getActionLabel() {
        return "Sell";
    }

    @Override
    public ResourceLocation getBackground() {
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/sell_screen.png");
    }

    protected abstract JsonArray collectSellableInventory(Player player);

    protected List<SellItemsC2SPayload.ItemRequest> toRequests(Player player, Map<Product, Integer> cart) {
        List<SellItemsC2SPayload.ItemRequest> requests = new ArrayList<>();
        for (Map.Entry<Product, Integer> entry : cart.entrySet()) {
            Product product = entry.getKey();
            CompoundTag tag = null;
            try {
                tag = (CompoundTag) product.stack().save(player.registryAccess());
            } catch (Exception ignored) {
            }
            requests.add(new SellItemsC2SPayload.ItemRequest(
                    product.itemId(),
                    product.name(),
                    entry.getValue(),
                    tag
            ));
        }
        return requests;
    }
}
