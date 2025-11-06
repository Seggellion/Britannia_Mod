package com.seggellion.britannia_mod.npc;

import com.seggellion.britannia_mod.shop.Product;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface NpcRoleHandler {
    void fetchCatalog(Player player, String city, Consumer<List<Product>> callback);
    void performTransaction(Player player, int entityId, Map<Product, Integer> cart, int totalPrice, Runnable onSuccess);
    String getActionLabel();
    ResourceLocation getBackground();
}
