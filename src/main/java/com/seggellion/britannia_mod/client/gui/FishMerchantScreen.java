// File: src/main/java/com/seggellion/britannia_mod/client/gui/FishMerchantScreen.java

package com.seggellion.britannia_mod.client.gui;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.trading.Merchant;

public class FishMerchantScreen extends MerchantScreen {

    public FishMerchantScreen(FishMerchantContainer container, Inventory playerInventory, Component title) {
        super(container, playerInventory, title);
    }

    // Custom rendering and GUI elements can be added here
}
