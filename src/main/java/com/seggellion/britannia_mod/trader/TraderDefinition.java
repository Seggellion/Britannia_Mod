package com.seggellion.britannia_mod.trader;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.function.Supplier;

public record TraderDefinition(
        String configKey,
        String npcType,
        String roleTitle,
        Supplier<EntityType<? extends Mob>> entityTypeSupplier,
        TraderSpawnSettings spawnSettings,
        TraderAppearance appearance
) implements TraderCatalogProvider, TraderEconomyProvider, TraderSpawnProvider {
    @Override
    public String getTraderTypeId() {
        return configKey;
    }

    @Override
    public String getTraderRoleTitle() {
        return roleTitle;
    }

    @Override
    public String getEconomyRole() {
        return roleTitle;
    }

    @Override
    public String getNpcType() {
        return npcType;
    }

    @Override
    public EntityType<? extends Mob> entityType() {
        return entityTypeSupplier.get();
    }
}
