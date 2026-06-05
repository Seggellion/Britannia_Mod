package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.trader.TraderDefinition;
import com.seggellion.britannia_mod.trader.TraderTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

public class ProduceTraderEntity extends AbstractTraderEntity {
    public ProduceTraderEntity(EntityType<? extends ProduceTraderEntity> type, Level level) {
        super(type, level);
    }

    public static ProduceTraderEntity create(EntityType<ProduceTraderEntity> type, Level level) {
        return new ProduceTraderEntity(type, level);
    }

    @Override
    public TraderDefinition getTraderDefinition() {
        return TraderTypes.byId(TraderTypes.PRODUCE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return CitizenEntity.baseAttributes();
    }
}
