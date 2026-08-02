package com.seggellion.britannia_mod.client.farming;

import com.seggellion.britannia_mod.farming.FarmingPlantingItemPresentation;
import com.seggellion.britannia_mod.skill.ClientSkillTable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Client-only bridge from the synchronized local viewer snapshot to the pure presentation policy. */
public final class ClientFarmingPlantingItemPresentation {
    private ClientFarmingPlantingItemPresentation() {
    }

    public static FarmingPlantingItemPresentation.PresentationResult resolve(
            ItemStack stack,
            Component existingDisplayName,
            FarmingPlantingItemPresentation.Surface surface
    ) {
        return FarmingPlantingItemPresentation.resolve(
                ClientSkillTable.farmingPresentationState(), stack, existingDisplayName, surface
        );
    }
}
