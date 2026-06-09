package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.trader.TraderDefinition;
import com.seggellion.britannia_mod.trader.TraderTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

public class FurLeatherTraderEntity extends AbstractTraderEntity {
    public FurLeatherTraderEntity(EntityType<? extends FurLeatherTraderEntity> type, Level level) {
        super(type, level);
    }

    public static FurLeatherTraderEntity create(EntityType<FurLeatherTraderEntity> type, Level level) {
        return new FurLeatherTraderEntity(type, level);
    }

    @Override
    public TraderDefinition getTraderDefinition() {
        return TraderTypes.byId(TraderTypes.FUR_LEATHER);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return CitizenEntity.baseAttributes();
    }
}
