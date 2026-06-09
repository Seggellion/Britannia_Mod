package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.trader.TraderDefinition;
import com.seggellion.britannia_mod.trader.TraderTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

public class FishTraderEntity extends AbstractTraderEntity {
    public FishTraderEntity(EntityType<? extends FishTraderEntity> type, Level level) {
        super(type, level);
    }

    public static FishTraderEntity create(EntityType<FishTraderEntity> type, Level level) {
        return new FishTraderEntity(type, level);
    }

    @Override
    public TraderDefinition getTraderDefinition() {
        return TraderTypes.byId(TraderTypes.FISH);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return CitizenEntity.baseAttributes();
    }
}
