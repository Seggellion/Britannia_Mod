// File: src/main/java/com/seggellion/britannia_mod/entity/EntityMerchant.java

package com.seggellion.britannia_mod.entity;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public abstract class EntityMerchant extends Villager {

    public EntityMerchant(EntityType<? extends Villager> entityType, Level level) {
        super(entityType, level);
        // Common merchant initialization
    }

    // Common methods for all merchants can be added here
}
