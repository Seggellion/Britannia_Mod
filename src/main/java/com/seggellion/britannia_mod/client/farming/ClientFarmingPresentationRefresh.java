package com.seggellion.britannia_mod.client.farming;

import com.seggellion.britannia_mod.mixin.client.CreativeModeInventoryScreenAccessor;
import com.seggellion.britannia_mod.skill.ClientSkillTable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** Rebuilds viewer-dependent Creative search text after an authoritative skill revision. */
public final class ClientFarmingPresentationRefresh {
    private static long refreshedRevision = Long.MIN_VALUE;

    private ClientFarmingPresentationRefresh() {
    }

    public static void reset() {
        refreshedRevision = Long.MIN_VALUE;
    }

    public static void refreshIfChanged() {
        long revision = ClientSkillTable.revision();
        if (revision == refreshedRevision) {
            return;
        }
        refreshedRevision = revision;

        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            return;
        }
        connection.searchTrees().rebuildAfterLanguageChange();
        if (minecraft.screen instanceof CreativeModeInventoryScreen creativeScreen) {
            ((CreativeModeInventoryScreenAccessor) creativeScreen).britannia$refreshSearchResults();
        }
    }
}
