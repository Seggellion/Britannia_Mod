package com.seggellion.britannia_mod.trader;

import com.seggellion.britannia_mod.network.ClientboundOpenNpcScreenPayload;
import com.seggellion.britannia_mod.npc.NpcType;
import net.minecraft.server.level.ServerPlayer;

public interface ITrader extends TraderCatalogProvider, TraderEconomyProvider {
    TraderDefinition getTraderDefinition();

    String getTraderCityName();

    int getId();

    @Override
    default String getTraderTypeId() {
        return getTraderDefinition().configKey();
    }

    @Override
    default String getTraderRoleTitle() {
        return getTraderDefinition().roleTitle();
    }

    @Override
    default String getEconomyRole() {
        return getTraderRoleTitle();
    }

    @Override
    default String getNpcType() {
        return getTraderDefinition().npcType();
    }

    default void openTraderCatalog(ServerPlayer player) {
        ClientboundOpenNpcScreenPayload.send(
                player,
                NpcType.TRADER,
                getTraderRoleTitle(),
                getTraderCityName(),
                getId()
        );
    }
}
