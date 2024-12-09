// File: src/main/java/com/seggellion/britannia_mod/client/gui/FishMerchantContainer.java

package com.seggellion.britannia_mod.client.gui;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.trading.Merchant;

public class FishMerchantContainer extends MerchantMenu {

    public FishMerchantContainer(int id, Inventory playerInventory, Merchant merchant) {
        super(id, playerInventory, merchant);
    }

    // Custom container logic can be added here
}
