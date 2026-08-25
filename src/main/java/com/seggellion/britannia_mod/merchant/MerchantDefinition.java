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
        TraderAppearance appearance,
        double minimumFoodSupply
) {
    public EntityType<? extends Mob> entityType() {
        return entityTypeSupplier.get();
    }

    /** Whether legacy spawn maintenance must consult the city's Rails food-supply reading. */
    public boolean requiresFoodSupply() {
        return minimumFoodSupply > 0.0D;
    }
}
