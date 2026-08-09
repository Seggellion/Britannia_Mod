package com.seggellion.britannia_mod.npc;

import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.BuyMerchantItemsC2SPayload;
import com.seggellion.britannia_mod.shop.Product;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class MerchantRoleHandler implements NpcRoleHandler {
    private final String role;
    private final String city;

    public MerchantRoleHandler(String role, String city) {
        this.role = role;
        this.city = city;
    }

    @Override
    public void fetchCatalog(Player player, String city, Consumer<List<Product>> callback) {
        callback.accept(List.of());
    }

    @Override
    public void performTransaction(Player player, int entityId, Map<Product, Integer> cart, int totalPrice, Runnable onSuccess) {
        NetworkHandler.sendToServer(new BuyMerchantItemsC2SPayload(city, role, entityId, toRequests(cart)));
        onSuccess.run();
    }

    @Override
    public String getActionLabel() {
        return "Buy";
    }

    @Override
    public ResourceLocation getBackground() {
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/buy_screen.png");
    }

    private java.util.List<BuyMerchantItemsC2SPayload.ItemRequest> toRequests(Map<Product, Integer> cart) {
        java.util.List<BuyMerchantItemsC2SPayload.ItemRequest> requests = new java.util.ArrayList<>();
        for (Map.Entry<Product, Integer> entry : cart.entrySet()) {
            requests.add(new BuyMerchantItemsC2SPayload.ItemRequest(
                    entry.getKey().itemId(),
                    entry.getKey().name(),
                    entry.getValue()
            ));
        }
        return requests;
    }
}
