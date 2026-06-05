package com.seggellion.britannia_mod.merchant;

import com.seggellion.britannia_mod.trader.TraderAppearance;
import com.seggellion.britannia_mod.trader.TraderSpawnSettings;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.function.Supplier;

public record MerchantDefinition(
        String configKey,
        String npcType,
        String roleTitle,
        Supplier<EntityType<? extends Mob>> entityTypeSupplier,
        TraderSpawnSettings spawnSettings,
        TraderAppearance appearance
) {
    public EntityType<? extends Mob> entityType() {
        return entityTypeSupplier.get();
    }
}
