package com.seggellion.britannia_mod.npc;

import com.seggellion.britannia_mod.api.RailsApi;
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
        RailsApi.fetchCatalog(city, role, callback);
    }

    @Override
    public void performTransaction(Player player, int entityId, Map<Product, Integer> cart, int totalPrice, Runnable onSuccess) {
        RailsApi.buyItems(city, role, entityId, cart, totalPrice, success -> {
            if (success) onSuccess.run();
        });
    }

    @Override
    public String getActionLabel() {
        return "Buy";
    }

    @Override
    public ResourceLocation getBackground() {
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/buy_screen.png");
    }
}
