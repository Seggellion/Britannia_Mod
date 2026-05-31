package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.trader.TraderDefinition;
import com.seggellion.britannia_mod.trader.TraderTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

public class EntityWoodMerchant extends AbstractTraderEntity {
    public EntityWoodMerchant(EntityType<? extends EntityWoodMerchant> entityType, Level level) {
        super(entityType, level);
    }

    public static EntityWoodMerchant create(EntityType<EntityWoodMerchant> type, Level level) {
        return new EntityWoodMerchant(type, level);
    }

    @Override
    public TraderDefinition getTraderDefinition() {
        return TraderTypes.byId(TraderTypes.WOOD);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return CitizenEntity.baseAttributes();
    }
}
